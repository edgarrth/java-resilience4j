package pe.axiz.payment.adapter.out.psp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PSP HTTP real y programable para probar los adaptadores con el cliente, los timeouts y la
 * serialización de verdad. Cada llamada consume el siguiente paso del guion; sin guion responde OK.
 */
final class FakePsp implements AutoCloseable {

    enum Kind { OK, STATUS, ABORT }

    record Step(Kind kind, int status, long delayMs) {

        static Step ok() {
            return new Step(Kind.OK, 200, 0);
        }

        static Step okAfter(Duration delay) {
            return new Step(Kind.OK, 200, delay.toMillis());
        }

        static Step status(int status) {
            return new Step(Kind.STATUS, status, 0);
        }

        static Step statusAfter(int status, Duration delay) {
            return new Step(Kind.STATUS, status, delay.toMillis());
        }

        /** Cierra la conexión sin responder, como un reset después de enviar el request. */
        static Step abort() {
            return new Step(Kind.ABORT, 0, 0);
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Queue<Step> script = new ConcurrentLinkedQueue<>();
    private final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
    private final AtomicInteger authorizationsSent = new AtomicInteger();
    private final AtomicInteger finishedCalls = new AtomicInteger();
    private volatile Step defaultStep = Step.ok();

    private FakePsp(HttpServer server) {
        this.server = server;
        server.createContext("/v1/authorizations", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    static FakePsp start() {
        try {
            return new FakePsp(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void reset() {
        script.clear();
        idempotencyKeys.clear();
        authorizationsSent.set(0);
        finishedCalls.set(0);
        defaultStep = Step.ok();
    }

    void script(Step... steps) {
        script.addAll(List.of(steps));
    }

    void always(Step step) {
        defaultStep = step;
    }

    int calls() {
        return idempotencyKeys.size();
    }

    List<String> idempotencyKeys() {
        return List.copyOf(idempotencyKeys);
    }

    /** Autorizaciones que el PSP llegó a confirmar, aunque el cliente ya no esperara la respuesta. */
    int authorizationsSent() {
        return authorizationsSent.get();
    }

    void awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (finishedCalls.get() < calls() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            Step step = Objects.requireNonNullElse(script.poll(), defaultStep);
            sleep(step.delayMs());
            switch (step.kind()) {
                case ABORT -> throw new IOException("Conexión cerrada por el PSP falso");
                case STATUS -> send(exchange, step.status(),
                        "{\"status\":" + step.status() + ",\"error\":\"FAKE\",\"message\":\"falla simulada\"}");
                case OK -> {
                    authorizationsSent.incrementAndGet();
                    send(exchange, 200, """
                            {"authorizationId":"auth-fake-%d","merchantId":"merchant-test","amount":10.00,
                             "currency":"PEN","status":"AUTHORIZED","replayed":false,"message":"ok",
                             "callNumber":%d,"scenario":"fake","createdAt":"2026-01-01T00:00:00Z"}
                            """.formatted(calls(), calls()));
                }
            }
        } finally {
            finishedCalls.incrementAndGet();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sleep(long millis) {
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
