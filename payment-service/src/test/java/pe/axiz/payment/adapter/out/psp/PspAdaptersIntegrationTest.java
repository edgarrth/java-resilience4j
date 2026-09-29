package pe.axiz.payment.adapter.out.psp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.axiz.payment.adapter.out.psp.FakePsp.Step;
import pe.axiz.payment.application.port.in.CompositionMode;
import pe.axiz.payment.application.port.in.ExecutionStyle;
import pe.axiz.payment.application.port.in.IsolationMode;
import pe.axiz.payment.application.port.in.ProcessPaymentCommand;
import pe.axiz.payment.application.port.in.ProcessPaymentUseCase;
import pe.axiz.payment.domain.model.PaymentResult;
import pe.axiz.payment.domain.model.PaymentStatus;

/**
 * Prueba los adaptadores reales (decorators, AOP, configuración de application.yml y cliente HTTP)
 * contra un PSP HTTP falso. El Rate Limiter se amplía para que la cuota no interfiera con el resto.
 */
@SpringBootTest(properties = "resilience4j.ratelimiter.instances.pspProvider.limitForPeriod=1000")
class PspAdaptersIntegrationTest {

    private static final FakePsp PSP = FakePsp.start();

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @DynamicPropertySource
    static void pspBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("psp.base-url", PSP::baseUrl);
    }

    @AfterAll
    static void stopPsp() {
        PSP.close();
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        PSP.awaitIdle(Duration.ofSeconds(5));
        PSP.reset();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    record Route(ExecutionStyle style, CompositionMode composition, IsolationMode isolation, String circuitBreaker) {

        @Override
        public String toString() {
            return style == ExecutionStyle.DECLARATIVE ? "DECLARATIVE" : composition + "/" + isolation;
        }
    }

    static Stream<Route> routes() {
        return Stream.of(
                new Route(ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_ATTEMPT, IsolationMode.SEMAPHORE,
                        "pspProgrammaticAttempt"),
                new Route(ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_ATTEMPT, IsolationMode.THREAD_POOL,
                        "pspProgrammaticAttempt"),
                new Route(ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_REQUEST, IsolationMode.SEMAPHORE,
                        "pspProgrammaticRequest"),
                new Route(ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_REQUEST, IsolationMode.THREAD_POOL,
                        "pspProgrammaticRequest"),
                new Route(ExecutionStyle.DECLARATIVE, CompositionMode.PER_REQUEST, IsolationMode.SEMAPHORE,
                        "pspDeclarative"));
    }

    @ParameterizedTest
    @EnumSource(IsolationMode.class)
    void perAttemptRegistraCadaIntentoFisicoEnElCircuitBreaker(IsolationMode isolation) {
        PSP.script(Step.status(503), Step.status(503));

        PaymentResult result = pay("per-attempt-" + isolation, new Route(
                ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_ATTEMPT, isolation, "pspProgrammaticAttempt"));

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(PSP.calls()).isEqualTo(3);
        assertThat(PSP.idempotencyKeys()).containsOnly("per-attempt-" + isolation);
        var metrics = circuitBreakerRegistry.circuitBreaker("pspProgrammaticAttempt").getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(IsolationMode.class)
    void perRequestRegistraSoloElResultadoLogicoEnElCircuitBreaker(IsolationMode isolation) {
        PSP.script(Step.status(503), Step.status(503));

        PaymentResult result = pay("per-request-" + isolation, new Route(
                ExecutionStyle.PROGRAMMATIC, CompositionMode.PER_REQUEST, isolation, "pspProgrammaticRequest"));

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(PSP.calls()).isEqualTo(3);
        assertThat(PSP.idempotencyKeys()).containsOnly("per-request-" + isolation);
        var metrics = circuitBreakerRegistry.circuitBreaker("pspProgrammaticRequest").getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void declarativoUsaElOrdenPorDefectoConRetryPorFueraDelCircuitBreaker() {
        PSP.script(Step.status(503), Step.status(503));

        PaymentResult result = pay("declarative-order", routes()
                .filter(route -> route.style() == ExecutionStyle.DECLARATIVE)
                .findFirst()
                .orElseThrow());

        assertThat(result.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(PSP.calls()).isEqualTo(3);
        var metrics = circuitBreakerRegistry.circuitBreaker("pspDeclarative").getMetrics();
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void timeoutConIntentoEnVueloEsUnknownPorqueElPspPuedeAutorizarDespues(Route route)
            throws InterruptedException {
        PSP.script(Step.okAfter(Duration.ofSeconds(2)));

        PaymentResult result = pay("late-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(result.providerReference()).isNull();
        PSP.awaitIdle(Duration.ofSeconds(3));
        assertThat(PSP.authorizationsSent())
                .as("el PSP autorizó después de que el TimeLimiter respondió al cliente")
                .isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void reintentosPosterioresAlTimeoutNoSalenAlPsp(Route route) throws InterruptedException {
        PSP.script(Step.statusAfter(503, Duration.ofMillis(1700)));

        PaymentResult result = pay("orphan-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        PSP.awaitIdle(Duration.ofSeconds(3));
        // Tras la falla en 1700 ms, Retry programa otro intento; el deadline lo descarta antes de salir.
        Thread.sleep(1000);
        assertThat(PSP.calls()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void rechazoDeNegocioNoSeReintentaNiDegradaElCircuitBreaker(Route route) {
        PSP.always(Step.status(402));

        PaymentResult result = pay("declined-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(PSP.calls()).isEqualTo(1);
        assertThat(circuitBreakerRegistry.circuitBreaker(route.circuitBreaker()).getMetrics()
                .getNumberOfFailedCalls()).isZero();
    }

    @ParameterizedTest
    @MethodSource("routes")
    void conflictoDeIdempotenciaSeInformaSinReintentar(Route route) {
        PSP.always(Step.status(409));

        PaymentResult result = pay("conflict-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.IDEMPOTENCY_CONFLICT);
        assertThat(PSP.calls()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void pspQueRespondeErrorTemporalTerminaEnNotProcessed(Route route) {
        PSP.always(Step.status(503));

        PaymentResult result = pay("unavailable-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.NOT_PROCESSED);
        assertThat(PSP.calls()).isEqualTo(3);
        assertThat(PSP.idempotencyKeys()).containsOnly("unavailable-" + route);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void conexionCortadaTrasEnviarElRequestEsUnknown(Route route) {
        PSP.always(Step.abort());

        PaymentResult result = pay("reset-" + route, route);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(PSP.calls()).isEqualTo(3);
    }

    @Test
    void todasLasModalidadesConsumenLaMismaCuotaDelProveedor() {
        routes().forEach(route -> assertThat(pay("quota-" + route, route).status())
                .isEqualTo(PaymentStatus.AUTHORIZED));

        assertThat(rateLimiterRegistry.getAllRateLimiters())
                .extracting(RateLimiter::getName)
                .containsExactly("pspProvider");
    }

    private PaymentResult pay(String idempotencyKey, Route route) {
        return processPaymentUseCase.process(new ProcessPaymentCommand(
                "merchant-test",
                new BigDecimal("10.00"),
                "PEN",
                idempotencyKey,
                route.style(),
                route.composition(),
                route.isolation()));
    }
}
