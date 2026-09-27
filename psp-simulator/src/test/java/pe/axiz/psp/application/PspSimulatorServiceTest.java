package pe.axiz.psp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.axiz.psp.domain.Scenario;

class PspSimulatorServiceTest {

    private PspSimulatorService service;

    @BeforeEach
    void setUp() {
        service = new PspSimulatorService(new SimpleMeterRegistry());
    }

    @Test
    void unaMismaClaveNoCreaDosAutorizaciones() {
        var first = service.authorize("idem-1", "merchant", new BigDecimal("10.00"), "PEN");
        var second = service.authorize("idem-1", "merchant", new BigDecimal("10.00"), "PEN");

        assertThat(first.authorization().authorizationId())
                .isEqualTo(second.authorization().authorizationId());
        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(service.authorizations()).hasSize(1);
    }

    @Test
    void failFirstTwoEsDeterminista() {
        service.importScenarios(List.of(new Scenario(
                "fail-first-two", 0, 0, 2, 0, 0, 503, 1)));
        service.activate("fail-first-two");

        assertThatThrownBy(() -> service.authorize(
                "idem-1", "merchant", new BigDecimal("10.00"), "PEN"))
                .isInstanceOf(SimulatedPspException.class);
        assertThatThrownBy(() -> service.authorize(
                "idem-1", "merchant", new BigDecimal("10.00"), "PEN"))
                .isInstanceOf(SimulatedPspException.class);

        var result = service.authorize("idem-1", "merchant", new BigDecimal("10.00"), "PEN");
        assertThat(result.replayed()).isFalse();
        assertThat(result.callNumber()).isEqualTo(3);
    }
}
