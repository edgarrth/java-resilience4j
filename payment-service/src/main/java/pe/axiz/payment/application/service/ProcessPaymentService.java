package pe.axiz.payment.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.axiz.payment.application.port.in.ProcessPaymentCommand;
import pe.axiz.payment.application.port.in.ProcessPaymentUseCase;
import pe.axiz.payment.application.port.out.PaymentProviderPort;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Money;
import pe.axiz.payment.domain.model.Payment;
import pe.axiz.payment.domain.model.PaymentResult;

@Service
public class ProcessPaymentService implements ProcessPaymentUseCase {

    private final List<PaymentProviderPort> paymentProviders;

    public ProcessPaymentService(List<PaymentProviderPort> paymentProviders) {
        this.paymentProviders = List.copyOf(paymentProviders);
    }

    @Override
    public PaymentResult process(ProcessPaymentCommand command) {
        Payment payment = new Payment(
                UUID.randomUUID(),
                command.merchantId(),
                new Money(command.amount(), command.currency()),
                command.idempotencyKey());

        PaymentProviderPort provider = paymentProviders.stream()
                .filter(candidate -> candidate.supports(command.executionStyle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No existe un adaptador para " + command.executionStyle()));

        PaymentProviderResult providerResult = provider.authorize(
                payment,
                command.compositionMode(),
                command.isolationMode());

        return new PaymentResult(
                payment.id(),
                providerResult.status(),
                providerResult.providerReference(),
                providerResult.replayed(),
                providerResult.detail());
    }
}
