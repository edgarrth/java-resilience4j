package pe.axiz.payment.adapter.out.psp.dto;

import java.time.Instant;
import java.util.List;

public record PspCapabilitiesResponse(
        String provider,
        List<String> supportedCurrencies,
        boolean idempotencySupported,
        Instant generatedAt) {
}
