package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.out.PaymentProviderPort;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

@Component
public class ProgrammaticPspAdapter implements PaymentProviderPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgrammaticPspAdapter.class);
    private static final String PER_ATTEMPT = "pspProgrammaticAttempt";
    private static final String PER_REQUEST = "pspProgrammaticRequest";

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
        return isolationMode == IsolationMode.THREAD_POOL
                ? authorizeWithThreadPool(payment, compositionMode, instanceName)
                : authorizeWithSemaphore(payment, compositionMode, instanceName);
    }

    private PaymentProviderResult authorizeWithSemaphore(
            Payment payment,
            CompositionMode compositionMode,
            String instanceName) {

        Supplier<CompletionStage<PaymentProviderResult>> source = () -> CompletableFuture.supplyAsync(
                () -> invokePsp(payment), paymentVirtualThreadExecutor);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(instanceName);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);

        CompletionStage<PaymentProviderResult> stage = compositionMode == CompositionMode.PER_ATTEMPT
                ? Decorators.ofCompletionStage(source)
                        .withRateLimiter(rateLimiter)
                        .withBulkhead(bulkhead)
                        .withCircuitBreaker(circuitBreaker)
                        .withRetry(retry, resilienceScheduler)
                        .withTimeLimiter(timeLimiter, resilienceScheduler)
                        .withFallback(this::fallback)
                        .get()
                : Decorators.ofCompletionStage(source)
                        .withRateLimiter(rateLimiter)
                        .withBulkhead(bulkhead)
                        .withRetry(retry, resilienceScheduler)
                        .withCircuitBreaker(circuitBreaker)
                        .withTimeLimiter(timeLimiter, resilienceScheduler)
                        .withFallback(this::fallback)
                        .get();

        return stage.toCompletableFuture().join();
    }

    private PaymentProviderResult authorizeWithThreadPool(
            Payment payment,
            CompositionMode compositionMode,
            String instanceName) {

        Supplier<PaymentProviderResult> source = () -> invokePsp(payment);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
        Retry retry = retryRegistry.retry(instanceName);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(instanceName);
        ThreadPoolBulkhead bulkhead = threadPoolBulkheadRegistry.bulkhead(instanceName);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);

        CompletionStage<PaymentProviderResult> stage = compositionMode == CompositionMode.PER_ATTEMPT
                ? Decorators.ofSupplier(source)
                        .withRateLimiter(rateLimiter)
                        .withCircuitBreaker(circuitBreaker)
                        .withRetry(retry)
                        .withThreadPoolBulkhead(bulkhead)
                        .withTimeLimiter(timeLimiter, resilienceScheduler)
                        .withFallback(this::fallback)
                        .get()
                : Decorators.ofSupplier(source)
                        .withRateLimiter(rateLimiter)
                        .withRetry(retry)
                        .withCircuitBreaker(circuitBreaker)
                        .withThreadPoolBulkhead(bulkhead)
                        .withTimeLimiter(timeLimiter, resilienceScheduler)
                        .withFallback(this::fallback)
                        .get();

        return stage.toCompletableFuture().join();
    }

    private PaymentProviderResult invokePsp(Payment payment) {
        var response = pspHttpClient.authorize(payment);
        return PaymentProviderResult.authorized(response.authorizationId(), response.replayed());
    }

    private PaymentProviderResult fallback(Throwable throwable) {
        Throwable cause = RootCause.unwrap(throwable);
        LOGGER.warn("Fallback controlado para PSP por {}: {}",
                cause.getClass().getSimpleName(), cause.getMessage());
        return PaymentProviderResult.notProcessed(
                "El pago no fue procesado. La protección de resiliencia terminó la llamada por "
                        + cause.getClass().getSimpleName());
    }
}
