package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.out.PaymentProviderResult;

@Component
public class DeclarativeTimedPspCall {

    private final DeclarativeProtectedPspCall protectedPspCall;
    private final ExecutorService paymentVirtualThreadExecutor;

    public DeclarativeTimedPspCall(
            DeclarativeProtectedPspCall protectedPspCall,
            ExecutorService paymentVirtualThreadExecutor) {
        this.protectedPspCall = protectedPspCall;
        this.paymentVirtualThreadExecutor = paymentVirtualThreadExecutor;
    }

    @TimeLimiter(name = DeclarativeProtectedPspCall.INSTANCE, fallbackMethod = "fallback")
    public CompletableFuture<PaymentProviderResult> execute(PspInvocation invocation) {
        return CompletableFuture.supplyAsync(
                () -> protectedPspCall.execute(invocation),
                paymentVirtualThreadExecutor);
    }

    public CompletableFuture<PaymentProviderResult> fallback(PspInvocation invocation, Throwable throwable) {
        return CompletableFuture.completedFuture(invocation.fallback(throwable));
    }
}
