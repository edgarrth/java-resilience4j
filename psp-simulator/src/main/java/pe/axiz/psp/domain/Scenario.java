package pe.axiz.psp.domain;

import java.util.Objects;

public record Scenario(
        String name,
        long fixedLatencyMs,
        long jitterMs,
        int failFirstCalls,
        int failureEveryN,
        int failureRatePercent,
        int failureStatus,
        int retryAfterSeconds) {

    public Scenario {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del escenario es obligatorio");
        }
        if (fixedLatencyMs < 0 || jitterMs < 0) {
            throw new IllegalArgumentException("La latencia no puede ser negativa");
        }
        if (failFirstCalls < 0 || failureEveryN < 0) {
            throw new IllegalArgumentException("Los contadores de falla no pueden ser negativos");
        }
        if (failureRatePercent < 0 || failureRatePercent > 100) {
            throw new IllegalArgumentException("failureRatePercent debe estar entre 0 y 100");
        }
        if (failureStatus < 400 || failureStatus > 599) {
            throw new IllegalArgumentException("failureStatus debe ser un HTTP 4xx o 5xx");
        }
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException("retryAfterSeconds no puede ser negativo");
        }
        name = Objects.requireNonNull(name).trim();
    }

    public static Scenario healthy() {
        return new Scenario("healthy", 50, 0, 0, 0, 0, 503, 1);
    }
}
