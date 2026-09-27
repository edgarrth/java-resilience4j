package pe.axiz.payment.adapter.out.psp;

import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.axiz.payment.adapter.out.psp.dto.PspAuthorizationRequest;
import pe.axiz.payment.adapter.out.psp.dto.PspAuthorizationResponse;
import pe.axiz.payment.adapter.out.psp.dto.PspCapabilitiesResponse;
import pe.axiz.payment.domain.model.Payment;

@Component
public class PspHttpClient {

    private final RestClient restClient;

    public PspHttpClient(RestClient pspRestClient) {
        this.restClient = pspRestClient;
    }

    public PspCapabilitiesResponse capabilities() {
        try {
            PspCapabilitiesResponse response = restClient.get()
                    .uri("/v1/authorizations/capabilities")
                    .retrieve()
                    .body(PspCapabilitiesResponse.class);
            return Objects.requireNonNull(response, "El PSP respondió sin capacidades");
        } catch (ResourceAccessException exception) {
            throw new PspNetworkException("No fue posible consultar las capacidades del PSP", exception);
        } catch (RestClientException exception) {
            throw new PspTemporaryException("Falló la consulta de capacidades del PSP", exception);
        }
    }

    public PspAuthorizationResponse authorize(Payment payment) {
        PspAuthorizationRequest request = new PspAuthorizationRequest(
                payment.id(),
                payment.merchantId(),
                payment.money().amount(),
                payment.money().currency());
        try {
            PspAuthorizationResponse response = restClient.post()
                    .uri("/v1/authorizations")
                    .header("Idempotency-Key", payment.idempotencyKey())
                    .body(request)
                    .retrieve()
                    .body(PspAuthorizationResponse.class);
            return Objects.requireNonNull(response, "El PSP respondió sin cuerpo");
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            if (status.value() == 429 || status.is5xxServerError()) {
                throw new PspTemporaryException(
                        "El PSP devolvió un error temporal HTTP " + status.value(), exception);
            }
            throw new PspBusinessException(
                    "El PSP rechazó la solicitud con HTTP " + status.value(), exception);
        } catch (ResourceAccessException exception) {
            throw new PspNetworkException("No fue posible comunicarse con el PSP", exception);
        } catch (RestClientException exception) {
            throw new PspTemporaryException("Falló la invocación HTTP al PSP", exception);
        }
    }
}
