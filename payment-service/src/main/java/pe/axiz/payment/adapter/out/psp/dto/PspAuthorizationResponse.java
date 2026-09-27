package pe.axiz.payment.adapter.out.psp.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PspAuthorizationResponse(
        String authorizationId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String status,
        boolean replayed,
        String message,
        long callNumber,
        String scenario,
        Instant createdAt) {
}
