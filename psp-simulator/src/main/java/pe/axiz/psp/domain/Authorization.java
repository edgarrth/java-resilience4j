package pe.axiz.psp.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Authorization(
        String authorizationId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Instant createdAt) {
}
