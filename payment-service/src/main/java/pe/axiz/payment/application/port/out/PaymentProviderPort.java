package pe.axiz.payment.application.port.out;

import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.domain.model.Payment;

public interface PaymentProviderPort {

    boolean supports(ExecutionStyle executionStyle);

    PaymentProviderResult authorize(
            Payment payment,
            CompositionMode compositionMode,
            IsolationMode isolationMode);
}
