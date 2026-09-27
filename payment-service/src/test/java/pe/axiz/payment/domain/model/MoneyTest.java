package pe.axiz.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizaMontoYMoneda() {
        Money money = new Money(new BigDecimal("10.125"), "pen");

        assertThat(money.amount()).isEqualByComparingTo("10.13");
        assertThat(money.currency()).isEqualTo("PEN");
    }

    @Test
    void rechazaMontoNoPositivo() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "PEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a cero");
    }
}
