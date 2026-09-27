package pe.axiz.psp.adapter.in.rest;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.axiz.psp.application.PspSimulatorService;

@RestController
@RequestMapping("/v1/authorizations")
public class PspController {

    private final PspSimulatorService service;

    public PspController(PspSimulatorService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public ProviderCapabilitiesResponse capabilities() {
        return new ProviderCapabilitiesResponse(
                "AXIZ-PSP-SIM",
                List.of("PEN", "USD"),
                true,
                Instant.now());
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponse> authorize(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizationRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key es obligatorio");
        }
        var result = service.authorize(
                idempotencyKey,
                request.merchantId(),
                request.amount(),
                request.currency());
        return ResponseEntity.ok(AuthorizationResponse.from(result));
    }
}
