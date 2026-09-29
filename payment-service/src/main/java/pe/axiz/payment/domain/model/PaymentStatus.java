package pe.axiz.payment.domain.model;

public enum PaymentStatus {
    AUTHORIZED,
    REJECTED,
    IDEMPOTENCY_CONFLICT,
    NOT_PROCESSED,
    UNKNOWN
}
