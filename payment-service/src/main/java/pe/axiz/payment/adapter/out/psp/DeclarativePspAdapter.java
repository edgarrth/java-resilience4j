package pe.axiz.payment.adapter.out.psp;

import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.out.PaymentProviderPort;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;

@Component
public class DeclarativePspAdapter implements PaymentProviderPort {

    private final DeclarativeTimedPspCall declarativeTimedPspCall;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public DeclarativePspAdapter(
            DeclarativeTimedPspCall declarativeTimedPspCall,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.declarativeTimedPspCall = declarativeTimedPspCall;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    @Override
    public boolean supports(ExecutionStyle executionStyle) {
        return executionStyle == ExecutionStyle.DECLARATIVE;
    }

    @Override
    public PaymentProviderResult authorize(
            Payment payment,
            CompositionMode compositionMode,
            IsolationMode isolationMode) {
        Duration timeBudget = timeLimiterRegistry.timeLimiter(DeclarativeProtectedPspCall.INSTANCE)
                .getTimeLimiterConfig()
                .getTimeoutDuration();
        return declarativeTimedPspCall.execute(new PspInvocation(payment, timeBudget)).join();
    }
}
