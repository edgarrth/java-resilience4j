package pe.axiz.payment.adapter.out.psp;

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

    public DeclarativePspAdapter(DeclarativeTimedPspCall declarativeTimedPspCall) {
        this.declarativeTimedPspCall = declarativeTimedPspCall;
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
        return declarativeTimedPspCall.execute(payment).join();
    }
}
