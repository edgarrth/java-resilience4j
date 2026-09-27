package pe.axiz.payment.application.port.in;

import pe.axiz.payment.domain.model.PaymentResult;

public interface ProcessPaymentUseCase {

    PaymentResult process(ProcessPaymentCommand command);
}
