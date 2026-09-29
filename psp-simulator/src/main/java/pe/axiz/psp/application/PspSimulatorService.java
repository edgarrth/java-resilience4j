package pe.axiz.psp.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import pe.axiz.psp.domain.Authorization;
import pe.axiz.psp.domain.Scenario;

@Service
public class PspSimulatorService {

    private final Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final Map<String, Authorization> authorizationsByIdempotencyKey = new ConcurrentHashMap<>();
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong newAuthorizations = new AtomicLong();
    private final AtomicLong idempotentReplays = new AtomicLong();
    private final AtomicLong simulatedFailures = new AtomicLong();
    private final AtomicLong idempotencyConflicts = new AtomicLong();
    private final MeterRegistry meterRegistry;
    private volatile String activeScenario = "healthy";

    public PspSimulatorService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        scenarios.put("healthy", Scenario.healthy());
    }

    public AuthorizationResult authorize(
            String idempotencyKey,
            String merchantId,
            BigDecimal amount,
            String currency) {

        long callNumber = totalCalls.incrementAndGet();
        Authorization existing = authorizationsByIdempotencyKey.get(idempotencyKey);
        if (existing != null) {
            ensureSameOperation(existing, merchantId, amount, currency);
            idempotentReplays.incrementAndGet();
            counter("replayed").increment();
            return new AuthorizationResult(existing, true, callNumber, activeScenario);
        }

        Scenario scenario = activeScenario();
        applyLatency(scenario);
        if (mustFail(scenario, callNumber)) {
            simulatedFailures.incrementAndGet();
            counter("failed").increment();
            throw new SimulatedPspException(
                    scenario.failureStatus(),
                    scenario.retryAfterSeconds(),
                    "Falla simulada por el escenario '" + scenario.name() + "' en la llamada " + callNumber);
        }

        Authorization created = new Authorization(
                "auth-" + UUID.randomUUID(),
                merchantId,
                amount,
                currency.toUpperCase(),
                idempotencyKey,
                Instant.now());
        Authorization winner = authorizationsByIdempotencyKey.putIfAbsent(idempotencyKey, created);
        boolean replayed = winner != null;
        Authorization result = replayed ? winner : created;
        if (replayed) {
            ensureSameOperation(winner, merchantId, amount, currency);
            idempotentReplays.incrementAndGet();
            counter("replayed").increment();
        } else {
            newAuthorizations.incrementAndGet();
            counter("authorized").increment();
        }
        return new AuthorizationResult(result, replayed, callNumber, scenario.name());
    }

    public void importScenarios(Collection<Scenario> imported) {
        if (imported == null || imported.isEmpty()) {
            throw new IllegalArgumentException("Debe enviarse al menos un escenario");
        }
        imported.forEach(scenario -> scenarios.put(scenario.name(), scenario));
        scenarios.putIfAbsent("healthy", Scenario.healthy());
    }

    public Scenario activate(String name) {
        Scenario scenario = scenarios.get(name);
        if (scenario == null) {
            throw new IllegalArgumentException("No existe el escenario: " + name);
        }
        activeScenario = name;
        return scenario;
    }

    public Scenario activeScenario() {
        Scenario scenario = scenarios.get(activeScenario);
        if (scenario == null) {
            activeScenario = "healthy";
            return scenarios.computeIfAbsent("healthy", ignored -> Scenario.healthy());
        }
        return scenario;
    }

    public List<Scenario> scenarios() {
        return scenarios.values().stream()
                .sorted(Comparator.comparing(Scenario::name))
                .toList();
    }

    public List<Authorization> authorizations() {
        return new ArrayList<>(authorizationsByIdempotencyKey.values()).stream()
                .sorted(Comparator.comparing(Authorization::createdAt))
                .toList();
    }

    public Map<String, Object> stats() {
        return Map.of(
                "activeScenario", activeScenario,
                "totalCalls", totalCalls.get(),
                "newAuthorizations", newAuthorizations.get(),
                "idempotentReplays", idempotentReplays.get(),
                "simulatedFailures", simulatedFailures.get(),
                "idempotencyConflicts", idempotencyConflicts.get(),
                "storedAuthorizations", authorizationsByIdempotencyKey.size());
    }

    public void reset() {
        totalCalls.set(0);
        newAuthorizations.set(0);
        idempotentReplays.set(0);
        simulatedFailures.set(0);
        idempotencyConflicts.set(0);
        authorizationsByIdempotencyKey.clear();
    }

    private void ensureSameOperation(
            Authorization existing,
            String merchantId,
            BigDecimal amount,
            String currency) {

        if (!existing.sameOperation(merchantId, amount, currency)) {
            idempotencyConflicts.incrementAndGet();
            counter("idempotency_conflict").increment();
            throw new IdempotencyConflictException(
                    "La Idempotency-Key '" + existing.idempotencyKey()
                            + "' ya fue usada para otro comercio, importe o moneda");
        }
    }

    private boolean mustFail(Scenario scenario, long callNumber) {
        if (callNumber <= scenario.failFirstCalls()) {
            return true;
        }
        if (scenario.failureEveryN() > 0 && callNumber % scenario.failureEveryN() == 0) {
            return true;
        }
        return scenario.failureRatePercent() > 0
                && ThreadLocalRandom.current().nextInt(100) < scenario.failureRatePercent();
    }

    private void applyLatency(Scenario scenario) {
        long jitter = scenario.jitterMs() == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(scenario.jitterMs() + 1);
        long delay = scenario.fixedLatencyMs() + jitter;
        if (delay == 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SimulatedPspException(503, 1, "La llamada simulada fue interrumpida");
        }
    }

    private Counter counter(String result) {
        return Counter.builder("psp_simulator_requests")
                .description("Resultado de las solicitudes recibidas por el PSP simulado")
                .tag("result", result)
                .register(meterRegistry);
    }

    public record AuthorizationResult(
            Authorization authorization,
            boolean replayed,
            long callNumber,
            String scenario) {
    }
}
