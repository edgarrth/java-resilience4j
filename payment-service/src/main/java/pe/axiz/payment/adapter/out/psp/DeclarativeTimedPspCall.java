package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

@Component
public class DeclarativeTimedPspCall {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeclarativeTimedPspCall.class);

    private final DeclarativeProtectedPspCall protectedPspCall;
    private final ExecutorService paymentVirtualThreadExecutor;

    public DeclarativeTimedPspCall(
            DeclarativeProtectedPspCall protectedPspCall,
            ExecutorService paymentVirtualThreadExecutor) {
        this.protectedPspCall = protectedPspCall;
        this.paymentVirtualThreadExecutor = paymentVirtualThreadExecutor;
    }

    @TimeLimiter(name = DeclarativeProtectedPspCall.INSTANCE, fallbackMethod = "fallback")
    public CompletableFuture<PaymentProviderResult> execute(Payment payment) {
        return CompletableFuture.supplyAsync(
                () -> protectedPspCall.execute(payment),
                paymentVirtualThreadExecutor);
    }

    public CompletableFuture<PaymentProviderResult> fallback(Payment payment, Throwable throwable) {
        Throwable cause = RootCause.unwrap(throwable);
        LOGGER.warn("Fallback declarativo para pago {} por {}: {}",
                payment.id(), cause.getClass().getSimpleName(), cause.getMessage());
        return CompletableFuture.completedFuture(PaymentProviderResult.notProcessed(
                "El pago no fue procesado. El flujo declarativo terminó por "
                        + cause.getClass().getSimpleName()));
    }
}
