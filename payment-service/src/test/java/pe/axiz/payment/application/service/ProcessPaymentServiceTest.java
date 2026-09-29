package pe.axiz.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.in.ProcessPaymentCommand;
import pe.axiz.payment.application.port.out.PaymentProviderPort;
import pe.axiz.payment.application.port.out.PaymentProviderResult;
import pe.axiz.payment.domain.model.Payment;
import pe.axiz.payment.domain.model.PaymentStatus;

class ProcessPaymentServiceTest {

    @Test
    void delegaEnElPuertoQueSoportaElEstiloSeleccionado() {
        PaymentProviderPort provider = new PaymentProviderPort() {
            @Override
            public boolean supports(ExecutionStyle executionStyle) {
                return executionStyle == ExecutionStyle.PROGRAMMATIC;
            }

            @Override
            public PaymentProviderResult authorize(
                    Payment payment,
                    CompositionMode compositionMode,
                    IsolationMode isolationMode) {
                return PaymentProviderResult.authorized("auth-test", false);
            }
        };
        ProcessPaymentService service = new ProcessPaymentService(List.of(provider));

        var result = service.process(new ProcessPaymentCommand(
                "merchant-test",
                new BigDecimal("20.00"),
                "PEN",
                "idem-test",
                ExecutionStyle.PROGRAMMATIC,
                CompositionMode.PER_REQUEST,
                IsolationMode.SEMAPHORE));

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(result.providerReference()).isEqualTo("auth-test");
    }

    @Test
    void laMismaClaveDeIdempotenciaProduceElMismoPaymentId() {
        List<Payment> received = new ArrayList<>();
        PaymentProviderPort provider = new PaymentProviderPort() {
            @Override
            public boolean supports(ExecutionStyle executionStyle) {
                return true;
            }

            @Override
            public PaymentProviderResult authorize(
                    Payment payment,
                    CompositionMode compositionMode,
                    IsolationMode isolationMode) {
                received.add(payment);
                return PaymentProviderResult.authorized("auth-test", received.size() > 1);
            }
        };
        ProcessPaymentService service = new ProcessPaymentService(List.of(provider));

        var first = service.process(command("idem-repeat"));
        var second = service.process(command("idem-repeat"));
        var other = service.process(command("idem-other"));

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(other.paymentId()).isNotEqualTo(first.paymentId());
    }

    private static ProcessPaymentCommand command(String idempotencyKey) {
        return new ProcessPaymentCommand(
                "merchant-test",
                new BigDecimal("20.00"),
                "PEN",
                idempotencyKey,
                ExecutionStyle.PROGRAMMATIC,
                CompositionMode.PER_REQUEST,
                IsolationMode.SEMAPHORE);
    }
}
