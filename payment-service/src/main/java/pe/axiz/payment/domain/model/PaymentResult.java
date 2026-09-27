package pe.axiz.payment.domain.model;

import java.util.UUID;

public record PaymentResult(
        UUID paymentId,
        PaymentStatus status,
        String providerReference,
        boolean replayed,
        String detail) {
}
