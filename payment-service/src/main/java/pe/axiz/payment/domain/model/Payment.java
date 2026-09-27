package pe.axiz.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Payment(
        UUID id,
        String merchantId,
        Money money,
        String idempotencyKey) {

    public Payment {
        Objects.requireNonNull(id, "El identificador del pago es obligatorio");
        Objects.requireNonNull(money, "El importe es obligatorio");
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("El merchantId es obligatorio");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
    }
}
