package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.out.PaymentProviderPort;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

@Component
public class ProgrammaticPspAdapter implements PaymentProviderPort {

    private static final String PER_ATTEMPT = "pspProgrammaticAttempt";
    private static final String PER_REQUEST = "pspProgrammaticRequest";
    private static final String PROVIDER_RATE_LIMITER = "pspProvider";

    private final PspHttpClient pspHttpClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ExecutorService paymentVirtualThreadExecutor;
    private final ScheduledExecutorService resilienceScheduler;

    public ProgrammaticPspAdapter(
            PspHttpClient pspHttpClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            TimeLimiterRegistry timeLimiterRegistry,
            ExecutorService paymentVirtualThreadExecutor,
            ScheduledExecutorService resilienceScheduler) {
        this.pspHttpClient = pspHttpClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.threadPoolBulkheadRegistry = threadPoolBulkheadRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.paymentVirtualThreadExecutor = paymentVirtualThreadExecutor;
        this.resilienceScheduler = resilienceScheduler;
    }

    @Override
    public boolean supports(ExecutionStyle executionStyle) {
        return executionStyle == ExecutionStyle.PROGRAMMATIC;
    }

    @Override
    public PaymentProviderResult authorize(
            Payment payment,
            CompositionMode compositionMode,
            IsolationMode isolationMode) {

        String instanceName = compositionMode == CompositionMode.PER_ATTEMPT
                ? PER_ATTEMPT
                : PER_REQUEST;

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);
        PspInvocation invocation = new PspInvocation(
                payment, timeLimiter.getTimeLimiterConfig().getTimeoutDuration());

        // Mismo orden en ambas modalidades de aislamiento: cada intento físico pasa por
        // Bulkhead y Rate Limiter, y solo cambia la posición del Circuit Breaker respecto de Retry.
        Supplier<CompletionStage<PaymentProviderResult>> physicalAttempt = isolationMode == IsolationMode.THREAD_POOL
                ? threadPoolIsolatedAttempt(invocation, instanceName)
                : semaphoreIsolatedAttempt(invocation, instanceName);

        var decorated = Decorators.ofCompletionStage(physicalAttempt);
        decorated = compositionMode == CompositionMode.PER_ATTEMPT
                ? decorated.withCircuitBreaker(circuitBreaker).withRetry(retry, resilienceScheduler)
                : decorated.withRetry(retry, resilienceScheduler).withCircuitBreaker(circuitBreaker);

        return decorated
                .withTimeLimiter(timeLimiter, resilienceScheduler)
                .withFallback(invocation::fallback)
                .get()
                .toCompletableFuture()
                .join();
    }

    private Supplier<CompletionStage<PaymentProviderResult>> semaphoreIsolatedAttempt(
            PspInvocation invocation,
            String instanceName) {

        return Decorators.ofCompletionStage(() -> CompletableFuture.supplyAsync(
                        () -> invocation.attempt(pspHttpClient), paymentVirtualThreadExecutor))
                .withRateLimiter(providerRateLimiter())
                .withBulkhead(bulkheadRegistry.bulkhead(instanceName))
                .decorate();
    }

    private Supplier<CompletionStage<PaymentProviderResult>> threadPoolIsolatedAttempt(
            PspInvocation invocation,
            String instanceName) {

        return Decorators.ofSupplier(() -> invocation.attempt(pspHttpClient))
                .withRateLimiter(providerRateLimiter())
                .withThreadPoolBulkhead(threadPoolBulkheadRegistry.bulkhead(instanceName))
                .decorate();
    }

    private RateLimiter providerRateLimiter() {
        return rateLimiterRegistry.rateLimiter(PROVIDER_RATE_LIMITER);
    }
}
