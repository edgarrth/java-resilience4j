package pe.axiz.payment.adapter.in.rest.dto;

import java.util.UUID;
import pe.axiz.payment.domain.model.PaymentResult;
import pe.axiz.payment.domain.model.PaymentStatus;

public record PaymentResponse(
        UUID paymentId,
        PaymentStatus status,
        String providerReference,
        boolean replayed,
        String detail) {

    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(
                result.paymentId(),
                result.status(),
                result.providerReference(),
                result.replayed(),
                result.detail());
    }
}
