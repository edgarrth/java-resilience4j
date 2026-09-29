package pe.axiz.payment.adapter.out.psp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.axiz.payment.adapter.out.psp.FakePsp.Step;
import pe.axiz.payment.domain.model.Money;
import pe.axiz.payment.domain.model.Payment;

class PspHttpClientTest {

    private static final Payment PAYMENT = new Payment(
            UUID.randomUUID(), "merchant-test", new Money(new BigDecimal("10.00"), "PEN"), "idem-test");

    @Test
    void conexionRechazadaSignificaQueElRequestNuncaLlegoAlPsp() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        PspHttpClient client = client("http://localhost:" + closedPort);

        assertThatThrownBy(() -> client.authorize(PAYMENT))
                .isInstanceOfSatisfying(PspNetworkException.class, exception ->
                        assertThat(exception.requestMayHaveReachedPsp()).isFalse());
    }

    @Test
    void conexionCortadaDespuesDeEnviarPudoLlegarAlPsp() {
        try (FakePsp psp = FakePsp.start()) {
            psp.always(Step.abort());
            PspHttpClient client = client(psp.baseUrl());

            assertThatThrownBy(() -> client.authorize(PAYMENT))
                    .isInstanceOfSatisfying(PspNetworkException.class, exception ->
                            assertThat(exception.requestMayHaveReachedPsp()).isTrue());
        }
    }

    @Test
    void rechazoDeNegocioConservaElStatusHttp() {
        try (FakePsp psp = FakePsp.start()) {
            psp.always(Step.status(409));
            PspHttpClient client = client(psp.baseUrl());

            assertThatThrownBy(() -> client.authorize(PAYMENT))
                    .isInstanceOfSatisfying(PspBusinessException.class, exception ->
                            assertThat(exception.status()).isEqualTo(409));
        }
    }

    private static PspHttpClient client(String baseUrl) {
        return new PspHttpClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build());
    }
}
