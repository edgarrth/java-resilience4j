package pe.axiz.payment.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CompositionOrderTest {

    @Test
    void circuitBreakerInternoMideCadaIntentoDelRetry() {
        AtomicInteger calls = new AtomicInteger();
        CircuitBreaker circuitBreaker = circuitBreaker("per-attempt");
        Retry retry = retry("per-attempt");
        Supplier<String> source = flakySource(calls);

        String result = Decorators.ofSupplier(source)
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .get();

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void circuitBreakerExternoMideElResultadoLogicoDelRetry() {
        AtomicInteger calls = new AtomicInteger();
        CircuitBreaker circuitBreaker = circuitBreaker("per-request");
        Retry retry = retry("per-request");
        Supplier<String> source = flakySource(calls);

        String result = Decorators.ofSupplier(source)
                .withRetry(retry)
                .withCircuitBreaker(circuitBreaker)
                .get();

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    private CircuitBreaker circuitBreaker(String name) {
        var config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .build();
        return CircuitBreaker.of(name, config);
    }

    private Retry retry(String name) {
        var config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(IllegalStateException.class)
                .build();
        return Retry.of(name, config);
    }

    private Supplier<String> flakySource(AtomicInteger calls) {
        return () -> {
            if (calls.incrementAndGet() <= 2) {
                throw new IllegalStateException("falla temporal");
            }
            return "ok";
        };
    }
}
