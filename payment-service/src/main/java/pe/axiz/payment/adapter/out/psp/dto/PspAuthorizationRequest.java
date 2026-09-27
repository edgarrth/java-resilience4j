package pe.axiz.payment.adapter.out.psp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PspAuthorizationRequest(
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        String currency) {
}
