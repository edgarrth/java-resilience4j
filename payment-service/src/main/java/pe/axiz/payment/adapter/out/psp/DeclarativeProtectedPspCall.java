package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

@Component
public class DeclarativeProtectedPspCall {

    static final String INSTANCE = "pspDeclarative";

    private final PspHttpClient pspHttpClient;

    public DeclarativeProtectedPspCall(PspHttpClient pspHttpClient) {
        this.pspHttpClient = pspHttpClient;
    }

    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    @RateLimiter(name = INSTANCE)
    @Bulkhead(name = INSTANCE, type = Bulkhead.Type.SEMAPHORE)
    public PaymentProviderResult execute(Payment payment) {
        var response = pspHttpClient.authorize(payment);
        return PaymentProviderResult.authorized(response.authorizationId(), response.replayed());
    }
}
