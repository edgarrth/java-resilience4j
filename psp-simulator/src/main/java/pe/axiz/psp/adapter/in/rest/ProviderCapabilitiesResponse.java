package pe.axiz.psp.adapter.in.rest;

import java.time.Instant;
import java.util.List;

public record ProviderCapabilitiesResponse(
        String provider,
        List<String> supportedCurrencies,
        boolean idempotencySupported,
        Instant generatedAt) {
}
