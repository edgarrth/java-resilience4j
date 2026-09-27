package pe.axiz.payment.application.port.out;

import pe.axiz.payment.domain.model.PaymentStatus;

public record PaymentProviderResult(
        PaymentStatus status,
        String providerReference,
        boolean replayed,
        String detail) {

    public static PaymentProviderResult authorized(String providerReference, boolean replayed) {
        return new PaymentProviderResult(
                PaymentStatus.AUTHORIZED,
                providerReference,
                replayed,
                replayed ? "Respuesta idempotente reutilizada por el PSP" : "Pago autorizado por el PSP");
    }

    public static PaymentProviderResult notProcessed(String detail) {
        return new PaymentProviderResult(PaymentStatus.NOT_PROCESSED, null, false, detail);
    }
}
