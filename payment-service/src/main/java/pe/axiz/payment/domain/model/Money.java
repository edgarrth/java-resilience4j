package pe.axiz.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "El monto es obligatorio");
        Objects.requireNonNull(currency, "La moneda es obligatoria");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("La moneda debe tener tres letras ISO-4217");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = normalizedCurrency;
    }
}
