package pe.axiz.payment.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import pe.axiz.payment.adapter.in.rest.dto.PaymentRequest;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.domain.model.PaymentResult;
import pe.axiz.payment.domain.model.PaymentStatus;

class PaymentControllerTest {

    @ParameterizedTest
    @CsvSource({
            "AUTHORIZED, OK",
            "UNKNOWN, ACCEPTED",
            "REJECTED, UNPROCESSABLE_CONTENT",
            "IDEMPOTENCY_CONFLICT, CONFLICT",
            "NOT_PROCESSED, SERVICE_UNAVAILABLE"
    })
    void cadaEstadoTieneSuPropioCodigoHttp(PaymentStatus status, HttpStatus expected) {
        PaymentController controller = new PaymentController(command ->
                new PaymentResult(UUID.randomUUID(), status, null, false, "detalle"));

        var response = controller.processProgrammatic(
                "idem-test",
                CompositionMode.PER_REQUEST,
                IsolationMode.SEMAPHORE,
                new PaymentRequest("merchant-test", new BigDecimal("10.00"), "PEN"));

        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody().status()).isEqualTo(status);
    }
}
