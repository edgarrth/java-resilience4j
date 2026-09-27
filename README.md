# Resilience4j Advanced Payment Processing PoC

Esta PoC la armé para probar resiliencia de verdad sobre un flujo de autorización de pagos. No es un ejemplo de `@CircuitBreaker` aislado: la idea es poder provocar fallas HTTP y de red, comparar órdenes de composición, saturar recursos, observar métricas y validar cómo se recupera el servicio.

El servicio principal es un microservicio REST con DDD + arquitectura hexagonal. El PSP externo está simulado en un segundo módulo para poder controlar sus fallas. Toxiproxy queda entre ambos cuando quiero introducir problemas de red reales.

## 1. Qué quiero demostrar

La parte importante del repositorio es el comportamiento técnico de Resilience4j:

| Capacidad | Qué pruebo en la PoC |
| --- | --- |
| Circuit Breaker | Estados `CLOSED`, `OPEN`, `HALF_OPEN`, `FORCED_OPEN`, `METRICS_ONLY` y `DISABLED`, failure rate y slow-call rate |
| Retry | Reintentos selectivos para fallas temporales con exponential backoff + jitter |
| Rate Limiter | Protección de la cuota del PSP y rechazo rápido cuando se agotan permisos |
| Semaphore Bulkhead | Límite de concurrencia sin crear otro pool de threads |
| ThreadPool Bulkhead | Aislamiento con pool y cola propios para comparar el modelo de ejecución |
| Time Limiter | Presupuesto máximo de tiempo para la operación hacia el PSP |
| Fallback | Respuesta degradada segura: nunca invento una autorización si el PSP no confirmó el cobro |
| Cache | Cacheo metadatos del proveedor, no autorizaciones de pago |
| Decorators | Composición programática para cambiar el orden de los patrones |
| Annotations/AOP | Alternativa declarativa para comparar con la composición programática |
| Event Publisher | Journal en memoria con los eventos emitidos por cada componente de resiliencia |
| Micrometer | Métricas técnicas de Resilience4j y del PSP simulado |
| Prometheus + Grafana | Scraping y dashboard listo para observar los experimentos |
| Toxiproxy | Latencia, timeout, TCP reset y caída total del enlace |
| Virtual Threads | El servicio usa virtual threads para trabajo bloqueante y el scheduler de timeouts queda separado |
| Idempotencia | Todos los retries reutilizan el mismo `Idempotency-Key`; el PSP no crea dos autorizaciones para la misma clave |

No uso PostgreSQL, Kafka, MongoDB, Neo4j, Qdrant, KurrentDB, InfluxDB ni Drools porque ninguno es necesario para demostrar este caso técnico. Meterlos haría la PoC más grande sin mejorar la prueba de resiliencia. Tampoco uso Flyway porque no existe una base de datos que versionar.

## 2. Stack y versiones

El proyecto está fijado a versiones actuales y compatibles con el objetivo de Java 25:

| Tecnología | Versión usada |
| --- | --- |
| Java | 25 |
| Maven | 3.9.16 |
| Spring Boot | 4.1.1 |
| Spring Framework | 7.0.9, administrado por Spring Boot 4.1.1 |
| Resilience4j | 2.4.0 |
| Resilience4j Spring Boot | `resilience4j-spring-boot4` 2.4.0 |
| Caffeine JCache | 3.3.0 |
| Prometheus | 3.15.0 |
| Grafana | 13.2.2 |
| Toxiproxy | 2.12.0 |

El `pom.xml` raíz usa Maven Enforcer para rechazar una ejecución que no esté en Java 25 y Maven 3.9.16+. Resilience4j se versiona explícitamente porque la integración usada es la específica para Spring Boot 4.

## 3. Caso de uso funcional

El cliente solicita una autorización de pago. `payment-service` crea el agregado de pago y llama al PSP externo usando una clave de idempotencia proporcionada por el cliente.

El PSP puede estar saludable, lento, responder 429/503, fallar algunas veces o quedar inaccesible. La PoC aplica las políticas de resiliencia y devuelve:

- `200 AUTHORIZED` únicamente cuando existe confirmación del PSP.
- `503 NOT_PROCESSED` cuando se activa el fallback. Esto significa que el sistema no afirma que el pago fue autorizado.

La idempotencia es parte central del ejemplo: un retry no genera un nuevo intento lógico de cobro. Todas las llamadas físicas del mismo request conservan el mismo `Idempotency-Key`.

## 4. Arquitectura

```mermaid
flowchart LR
    C[Cliente] -->|REST + Idempotency-Key| P[Payment Service :8080]

    subgraph Payment Service
      API[REST Adapter]
      UC[Application Use Case]
      DOM[Domain]
      PORT[PaymentProviderPort]
      RES[Resilience4j\nCB + Retry + RateLimiter\nBulkhead + TimeLimiter + Fallback]
      CACHE[Resilience4j Cache\nsolo metadatos del PSP]
      OBS[Actuator + Micrometer\nEvent Journal]
      API --> UC --> DOM
      UC --> PORT --> RES
      API --> CACHE
      RES --> OBS
    end

    RES -->|HTTP| T[Toxiproxy :8666]
    T -->|TCP| PSP[PSP Simulator :8081]
    CACHE -->|HTTP directo vía mismo cliente| T

    PSP -->|métricas| PR[Prometheus :9090]
    OBS -->|métricas| PR
    PR --> G[Grafana :3000]
```

Para ejecutar desde el IDE, `payment-service` apunta por defecto a `http://localhost:8666`, que es el puerto expuesto por Toxiproxy. Toxiproxy reenvía al PSP que corre en el host por `8081`.

## 5. DDD y arquitectura hexagonal

El dominio se mantiene independiente de Spring y Resilience4j:

```text
payment-service
└── pe.axiz.payment
    ├── domain
    │   └── model
    │       ├── Money
    │       ├── Payment
    │       ├── PaymentResult
    │       └── PaymentStatus
    ├── application
    │   ├── port
    │   │   ├── in
    │   │   └── out
    │   └── service
    ├── adapter
    │   ├── in/rest
    │   └── out/psp
    ├── cache
    ├── config
    └── resilience
```

`ProcessPaymentUseCase` es el puerto de entrada. `PaymentProviderPort` es el puerto de salida. Las dos implementaciones del puerto son `ProgrammaticPspAdapter` y `DeclarativePspAdapter`; el dominio no sabe que existe Resilience4j ni HTTP.

## 6. Estructura del repositorio

```text
resilience4j-payment-poc/
├── README.md                         documentación única del repositorio
├── pom.xml                           agregador Maven y validación de Java/Maven
├── payment-service/                  microservicio de autorización de pagos
├── psp-simulator/                    PSP controlable para generar fallas
├── datasets/                         escenarios y pagos de prueba
├── infrastructure/                   Docker Compose, observabilidad y Toxiproxy
│   ├── docker-compose.yml
│   ├── prometheus/
│   ├── grafana/
│   ├── toxiproxy/
│   ├── requests/                     requests HTTP y JSON de request/response
│   └── scripts/                      inyección de fallas de red
├── scripts/                          utilidades del proyecto
└── .github/workflows/ci.yml          build reproducible con Temurin 25
```

## 7. Código principal

### 7.1 `ProgrammaticPspAdapter`

Es el núcleo técnico de la PoC. Construye los decorators en código y permite cambiar dos dimensiones desde el request:

- `composition=PER_ATTEMPT`: el Circuit Breaker queda dentro de Retry y registra cada intento físico.
- `composition=PER_REQUEST`: Retry queda dentro del Circuit Breaker y este registra el resultado del request lógico.
- `isolation=SEMAPHORE`: usa `Bulkhead` por semáforo.
- `isolation=THREAD_POOL`: usa `ThreadPoolBulkhead` con pool y cola propios.

La diferencia de composición es intencional:

```text
PER_ATTEMPT
Fallback(TimeLimiter(Retry(CircuitBreaker(Bulkhead(RateLimiter(PSP))))))

PER_REQUEST
Fallback(TimeLimiter(CircuitBreaker(Retry(Bulkhead(RateLimiter(PSP))))))
```

En ambos casos el Rate Limiter está dentro de Retry para que cada intento físico consuma permiso del proveedor. Eso evita que un request con tres retries se contabilice falsamente como un solo uso de cuota.

### 7.2 `DeclarativeProtectedPspCall` y `DeclarativeTimedPspCall`

Muestran el equivalente con anotaciones `@Retry`, `@CircuitBreaker`, `@RateLimiter`, `@Bulkhead` y `@TimeLimiter`.

`TimeLimiter` está en otro bean a propósito. Así la llamada pasa por el proxy AOP de Spring y evito el problema clásico de self-invocation.

### 7.3 `PspHttpClient`

Usa `RestClient` de Spring Framework 7 y el HTTP Client del JDK. Tiene connect/read timeout y clasifica errores:

- HTTP `429` y `5xx`: `PspTemporaryException`, elegibles para Retry y Circuit Breaker.
- errores de red: `PspNetworkException`, elegibles para Retry y Circuit Breaker.
- otros `4xx`: `PspBusinessException`, no se reintentan y no deben degradar el Circuit Breaker.

### 7.4 `PspSimulatorService`

Permite cargar escenarios sin reiniciar el servicio. Los escenarios deterministas (`fail-first-two`, `unstable-every-two`, `unavailable`) son los que uso para pruebas repetibles. También existe `random-40` para experimentar con un patrón no determinista.

La idempotencia se implementa en memoria por `Idempotency-Key`. Si una autorización ya fue creada, una llamada posterior devuelve la misma referencia y `replayed=true`.

### 7.5 `ProviderCapabilitiesCacheService`

Demuestra `resilience4j-cache` con JCache/Caffeine. Cachea durante 30 segundos las capacidades del PSP. No cacheo el resultado de una autorización de pago: para eso uso idempotencia, porque cachear el resultado de una operación monetaria puede ocultar el estado real de una transacción.

### 7.6 `ResilienceEventJournal`

Mantiene los últimos 500 eventos emitidos por Circuit Breaker, Retry, Rate Limiter, Bulkhead, ThreadPoolBulkhead y TimeLimiter. Sirve para inspeccionar la secuencia exacta de un experimento sin depender del log.

## 8. Configuración de resiliencia

La configuración está en `payment-service/src/main/resources/application.yml`.

### Circuit Breaker

- ventana: 10 llamadas, count-based.
- mínimo: 5 llamadas.
- failure rate: 50%.
- slow-call rate: 50%.
- slow call: más de 800 ms.
- `OPEN`: 5 s.
- `HALF_OPEN`: 3 llamadas de prueba.
- transición automática a `HALF_OPEN`.

### Retry

- máximo 3 intentos.
- espera inicial 200 ms.
- exponential backoff x2.
- máximo 2 s.
- jitter 50%.
- solo reintenta errores temporales/de red.

### Rate Limiter

- 10 permisos por segundo.
- espera máxima por permiso: 100 ms.

### Semaphore Bulkhead

- 4 ejecuciones concurrentes.
- sin cola de espera: cuando no hay permiso falla rápido.

### ThreadPool Bulkhead

- 2 threads core.
- 4 threads máximo.
- cola de 4.

### Time Limiter

- 1500 ms por operación lógica.

Estos valores son chicos a propósito para que los cambios de estado se puedan ver rápido en una laptop. En producción tendrían que salir de SLOs, latencias reales, capacidad del proveedor y pruebas de carga.

## 9. Endpoints

Los ordeno como normalmente los uso al ejecutar la PoC.

| Orden | Método y endpoint | Funcional | Técnico |
| ---: | --- | --- | --- |
| 1 | `POST :8081/admin/scenarios/import` | Carga escenarios del PSP | Inicializa el dataset de fallas |
| 2 | `POST :8081/admin/reset` | Limpia autorizaciones del simulador | Reinicia contadores para una prueba repetible |
| 3 | `POST :8081/admin/scenarios/{name}/activate` | Cambia el comportamiento del PSP | Inyecta 429/503, latencia o fallas deterministas |
| 4 | `GET :8081/admin/scenarios` | Lista escenarios | Confirma cuál está activo |
| 5 | `POST :8080/api/v1/payments/programmatic` | Autoriza un pago | Ejecuta decorators y permite elegir composición/aislamiento |
| 6 | `POST :8080/api/v1/payments/declarative` | Autoriza un pago | Ejecuta annotations/AOP |
| 7 | `GET :8080/internal/resilience/snapshot` | No tiene uso funcional | Snapshot de métricas internas de los componentes |
| 8 | `GET :8080/internal/resilience/events` | No tiene uso funcional | Muestra la secuencia de eventos Resilience4j |
| 9 | `DELETE :8080/internal/resilience/events` | No tiene uso funcional | Limpia el journal antes de un experimento |
| 10 | `POST :8080/internal/resilience/circuit-breakers/{name}/reset` | No tiene uso funcional | Lleva el CB al estado normal inicial |
| 11 | `POST :8080/internal/resilience/circuit-breakers/{name}/force-open` | No tiene uso funcional | Prueba `FORCED_OPEN` |
| 12 | `POST :8080/internal/resilience/circuit-breakers/{name}/metrics-only` | No tiene uso funcional | Prueba `METRICS_ONLY` |
| 13 | `POST :8080/internal/resilience/circuit-breakers/{name}/disable` | No tiene uso funcional | Prueba `DISABLED` |
| 14 | `GET :8080/api/v1/payments/provider-capabilities` | Consulta capacidades del PSP | Demuestra Resilience4j Cache |
| 15 | `GET :8080/api/v1/payments/provider-capabilities/cache-metrics` | No tiene uso funcional | Muestra hit/miss del cache |
| 16 | `GET :8081/admin/stats` | Estado del simulador | Confirma llamadas, fallas, replays y autorizaciones reales |
| 17 | `GET :8081/admin/authorizations` | Lista autorizaciones | Permite comprobar que Retry no generó doble cobro |
| 18 | `GET :8080/actuator/prometheus` | No tiene uso funcional | Métricas para Prometheus |

Los nombres válidos para administrar Circuit Breaker son:

```text
pspProgrammaticAttempt
pspProgrammaticRequest
pspDeclarative
```

## 10. Request y response principal

Request:

```http
POST /api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE
Idempotency-Key: payment-001
Content-Type: application/json
```

```json
{
  "merchantId": "merchant-demo",
  "amount": 149.90,
  "currency": "PEN"
}
```

Autorizado:

```json
{
  "paymentId": "11111111-2222-3333-4444-555555555555",
  "status": "AUTHORIZED",
  "providerReference": "auth-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "replayed": false,
  "detail": "Pago autorizado por el PSP"
}
```

Fallback seguro:

```json
{
  "paymentId": "11111111-2222-3333-4444-555555555555",
  "status": "NOT_PROCESSED",
  "providerReference": null,
  "replayed": false,
  "detail": "El pago no fue procesado. La protección de resiliencia terminó la llamada por TimeoutException"
}
```

También dejé requests para IntelliJ/VS Code en `infrastructure/requests/` y ejemplos JSON en `infrastructure/requests/examples/`.

## 11. Requisitos

Para correr todo desde el IDE:

```text
JDK 25
Maven 3.9.16 o superior dentro de la rama 3.9.x
Docker con Docker Compose v2
curl
```

Los puertos usados son:

```text
8080 payment-service
8081 psp-simulator
8666 Toxiproxy -> PSP
8474 Toxiproxy API
9090 Prometheus
3000 Grafana
```

## 12. Levantar infraestructura

Desde la raíz:

```bash
cd infrastructure
docker compose up
```

No uso `-d` para que se pueda ver el estado de la infraestructura mientras hago las pruebas. Tampoco configuré health checks HTTP periódicos en Docker Compose, así que los logs no quedan llenándose con requests de liveness.

La infraestructura levanta solamente:

```text
Toxiproxy
Prometheus
Grafana
```

Prometheus apunta a los servicios que se ejecutan desde el IDE en el host.

Grafana queda en `http://localhost:3000` con usuario `admin` y password `admin`. El datasource y el dashboard `Axiz - Payment Resilience` se provisionan automáticamente.

## 13. Ejecutar los servicios

Primero el PSP:

```bash
mvn -pl psp-simulator spring-boot:run
```

Luego el servicio de pagos en otra terminal:

```bash
mvn -pl payment-service spring-boot:run
```

El flujo queda:

```text
payment-service :8080
       |
       v
Toxiproxy :8666
       |
       v
psp-simulator :8081
```

Si quiero saltar Toxiproxy temporalmente:

```bash
PSP_BASE_URL=http://localhost:8081 mvn -pl payment-service spring-boot:run
```

## 14. Cargar datasets

Con ambos servicios arriba:

Linux/macOS/Git Bash:

```bash
./datasets/load.sh
```

PowerShell:

```powershell
./datasets/load.ps1
```

El script:

1. carga `datasets/scenarios.json` en el PSP;
2. activa `healthy`;
3. limpia contadores y autorizaciones previas.

`datasets/payments.jsonl` contiene pagos de ejemplo. Se pueden ejecutar todos con:

```bash
./scripts/run-dataset-payments.sh
```

## 15. Pruebas por curl

Antes de cada bloque conviene dejar el enlace de red limpio:

```bash
./infrastructure/scripts/network-reset.sh
```

### Test 1: camino saludable

Activo PSP saludable:

```bash
curl -X POST http://localhost:8081/admin/scenarios/healthy/activate
```

Autorizo:

```bash
curl -i -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: healthy-001' \
  -d '{"merchantId":"merchant-demo","amount":149.90,"currency":"PEN"}'
```

Qué estoy comprobando: el request atraviesa RateLimiter, Bulkhead, Retry, Circuit Breaker y TimeLimiter sin activar protección. Debe responder `200` y `AUTHORIZED`.

### Test 2: Retry con backoff + idempotencia

Reinicio el simulador y hago que las dos primeras llamadas físicas fallen:

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/fail-first-two/activate
```

Ejecuto un solo pago:

```bash
curl -i -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: retry-001' \
  -d '{"merchantId":"merchant-demo","amount":99.90,"currency":"PEN"}'
```

Reviso estadísticas:

```bash
curl http://localhost:8081/admin/stats
curl http://localhost:8081/admin/authorizations
curl http://localhost:8080/internal/resilience/events?limit=50
```

Qué estoy comprobando: deben existir tres llamadas físicas pero una sola autorización. Retry usa la misma clave de idempotencia en todos los intentos.

### Test 3: comparar `PER_ATTEMPT` vs `PER_REQUEST`

Primero limpio ambos breakers y el PSP:

```bash
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticAttempt/reset
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/reset
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/fail-first-two/activate
```

Con Circuit Breaker por intento:

```bash
curl -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_ATTEMPT&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-attempt-001' \
  -d '{"merchantId":"merchant-demo","amount":50.00,"currency":"PEN"}'
```

Consulto snapshot:

```bash
curl http://localhost:8080/internal/resilience/snapshot
```

Repito el reset y pruebo `PER_REQUEST`:

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/fail-first-two/activate
curl -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-request-001' \
  -d '{"merchantId":"merchant-demo","amount":50.00,"currency":"PEN"}'
```

Qué estoy comprobando: `pspProgrammaticAttempt` ve los intentos que fallaron antes del éxito. `pspProgrammaticRequest` ve principalmente el resultado lógico una vez agotado/terminado Retry. Este test es el motivo por el que mantengo una variante programática además de annotations.

### Test 4: abrir Circuit Breaker y observar recuperación

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/reset
curl -X POST http://localhost:8081/admin/scenarios/unavailable/activate
```

Genero varias operaciones con claves distintas:

```bash
for i in {1..8}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST \
    'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: cb-$i" \
    -d '{"merchantId":"merchant-demo","amount":10.00,"currency":"PEN"}'
done
```

```bash
curl http://localhost:8080/internal/resilience/snapshot
```

Qué estoy comprobando: luego del mínimo de llamadas y con failure rate suficiente, el breaker pasa a `OPEN` y empieza a rechazar sin tocar el PSP.

Ahora recupero el PSP:

```bash
curl -X POST http://localhost:8081/admin/scenarios/healthy/activate
```

Espero algo más de 5 segundos y envío tres pagos nuevos. El breaker tiene permiso para probar el backend en `HALF_OPEN`; si las llamadas salen bien vuelve a `CLOSED`.

### Test 5: slow calls y TimeLimiter

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/slow-2s/activate
```

```bash
curl -i -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: timeout-001' \
  -d '{"merchantId":"merchant-demo","amount":20.00,"currency":"PEN"}'
```

Qué estoy comprobando: el TimeLimiter está en 1500 ms y el PSP tarda 2 s. El cliente recibe `503 NOT_PROCESSED`; no devuelvo una autorización falsa.

Para revisar eventos:

```bash
curl http://localhost:8080/internal/resilience/events?limit=100
```

### Test 6: Rate Limiter

Dejo el PSP saludable y mando un burst. Cada intento físico consume permiso:

```bash
curl -X POST http://localhost:8081/admin/scenarios/healthy/activate
seq 1 25 | xargs -P 25 -I {} curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: rate-{}' \
  -d '{"merchantId":"merchant-demo","amount":10.00,"currency":"PEN"}'
```

Qué estoy comprobando: con 10 permisos/segundo y 100 ms de espera, parte del burst debe ser rechazado/protegido en lugar de dejar crecer una cola sin límite.

### Test 7: Semaphore Bulkhead

Uso un PSP lento pero por debajo del read timeout y envío concurrencia:

```bash
curl -X POST http://localhost:8081/admin/scenarios/slow-2s/activate
seq 1 12 | xargs -P 12 -I {} curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: semaphore-{}' \
  -d '{"merchantId":"merchant-demo","amount":10.00,"currency":"PEN"}'
```

Qué estoy comprobando: solo 4 ejecuciones pueden ocupar el segmento protegido a la vez. El resto falla rápido por `BulkheadFullException` y termina en fallback.

### Test 8: ThreadPool Bulkhead

```bash
seq 1 12 | xargs -P 12 -I {} curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=THREAD_POOL' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: threadpool-{}' \
  -d '{"merchantId":"merchant-demo","amount":10.00,"currency":"PEN"}'
```

```bash
curl http://localhost:8080/internal/resilience/snapshot
```

Qué estoy comprobando: el aislamiento ahora tiene pool de 2-4 threads y cola de 4. El snapshot permite ver tamaño del pool y profundidad/capacidad de la cola.

### Test 9: falla HTTP 429

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/rate-limited/activate
curl -i -X POST \
  'http://localhost:8080/api/v1/payments/programmatic?composition=PER_REQUEST&isolation=SEMAPHORE' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: psp-429-001' \
  -d '{"merchantId":"merchant-demo","amount":10.00,"currency":"PEN"}'
```

Qué estoy comprobando: 429 se considera temporal, participa de Retry y del Circuit Breaker.

### Test 10: latencia real de red con Toxiproxy

Primero PSP saludable:

```bash
curl -X POST http://localhost:8081/admin/scenarios/healthy/activate
./infrastructure/scripts/network-latency.sh 1200 100
```

Ejecuto un pago y reviso eventos/métricas. La latencia no está dentro del PSP: Toxiproxy la agrega en el trayecto TCP.

Limpio:

```bash
./infrastructure/scripts/network-reset.sh
```

### Test 11: timeout de red

```bash
./infrastructure/scripts/network-timeout.sh 4000
```

Ejecuto un pago normal. El TimeLimiter debe terminar antes de que el enlace entregue la respuesta.

```bash
./infrastructure/scripts/network-reset.sh
```

### Test 12: TCP reset

```bash
./infrastructure/scripts/network-reset-peer.sh 0
```

Ejecuto un pago y verifico que el error de transporte se clasifica como falla de red y es elegible para Retry.

```bash
./infrastructure/scripts/network-reset.sh
```

### Test 13: caída total del enlace

```bash
./infrastructure/scripts/network-down.sh
```

Hago varios pagos con claves diferentes y observo Retry + Circuit Breaker. Luego recupero el proxy:

```bash
./infrastructure/scripts/network-up.sh
./infrastructure/scripts/network-reset.sh
```

### Test 14: estados especiales del Circuit Breaker

Fuerzo apertura:

```bash
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/force-open
```

El siguiente request se corta aunque el PSP esté saludable.

Modo solo métricas:

```bash
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/metrics-only
```

Registra métricas pero no abre por threshold.

Deshabilitado:

```bash
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/disable
```

Para volver al flujo normal:

```bash
curl -X POST http://localhost:8080/internal/resilience/circuit-breakers/pspProgrammaticRequest/reset
```

### Test 15: implementación declarativa

```bash
curl -X POST http://localhost:8081/admin/reset
curl -X POST http://localhost:8081/admin/scenarios/fail-first-two/activate
curl -i -X POST \
  http://localhost:8080/api/v1/payments/declarative \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: declarative-001' \
  -d '{"merchantId":"merchant-demo","amount":30.00,"currency":"PEN"}'
```

Qué estoy comprobando: el mismo problema se resuelve con annotations/AOP. Para experimentar con órdenes arbitrarios prefiero el endpoint programático porque el chain queda explícito en código.

### Test 16: Cache de capacidades del PSP

Primera consulta:

```bash
curl http://localhost:8080/api/v1/payments/provider-capabilities
curl http://localhost:8080/api/v1/payments/provider-capabilities/cache-metrics
```

Segunda consulta antes de 30 segundos:

```bash
curl http://localhost:8080/api/v1/payments/provider-capabilities
curl http://localhost:8080/api/v1/payments/provider-capabilities/cache-metrics
```

Qué estoy comprobando: la primera llamada es miss y consulta el PSP; la segunda reutiliza el valor. El campo `generatedAt` permite ver que la respuesta es la misma. Este patrón está limitado a metadata que tolera staleness.

## 16. Observabilidad

Prometheus:

```text
http://localhost:9090
```

Grafana:

```text
http://localhost:3000
admin / admin
```

Dashboard provisionado:

```text
Axiz / Axiz - Payment Resilience
```

Algunas métricas útiles:

```text
resilience4j_circuitbreaker_state
resilience4j_circuitbreaker_failure_rate
resilience4j_circuitbreaker_slow_call_rate
resilience4j_retry_calls
resilience4j_ratelimiter_available_permissions
resilience4j_bulkhead_available_concurrent_calls
resilience4j_timelimiter_calls_total
psp_simulator_requests_total
```

Además del dashboard, para una inspección rápida uso:

```bash
curl http://localhost:8080/internal/resilience/snapshot
curl http://localhost:8080/internal/resilience/events?limit=100
```

## 17. Tests automatizados

El repositorio incluye tests de:

- invariantes del dominio `Money`;
- selección del puerto hexagonal;
- diferencia real entre `CircuitBreaker` por intento y por request;
- idempotencia del PSP;
- escenario determinista `fail-first-two`.

Ejecución:

```bash
mvn clean verify
```

Si no quiero depender del JDK/Maven instalado localmente, dejé una verificación reproducible con Docker:

```bash
./scripts/verify-build.sh
```

Ese script usa `maven:3.9.16-eclipse-temurin-25` y ejecuta `mvn -B clean verify` dentro del contenedor.

También existe CI en `.github/workflows/ci.yml` usando Temurin 25 y Maven.

## 18. Build de imágenes de los módulos

Los Dockerfile son opcionales; la forma recomendada para explorar la PoC es correr los dos módulos desde el IDE y dejar infraestructura en Docker Compose.

Si quiero construir una imagen:

```bash
docker build -f payment-service/Dockerfile -t axiz/payment-service:local .
docker build -f psp-simulator/Dockerfile -t axiz/psp-simulator:local .
```

## 19. Decisiones que mantendría en un servicio real

- La idempotencia tendría almacenamiento durable y una política explícita de expiración; en esta PoC es memoria para no agregar una base que no aporta al objetivo técnico.
- Circuit Breaker, Retry y timeout se calibran con SLOs y métricas reales, no copiando los números de esta demo.
- No reintento errores de negocio.
- Un retry de una operación de pago siempre conserva la misma clave idempotente.
- El fallback nunca devuelve `AUTHORIZED` por inferencia.
- El Rate Limiter considera llamadas físicas, no solo requests lógicos.
- Un Bulkhead se dimensiona por dependencia; no compartiría el mismo pool entre proveedores no relacionados.
- Cache solo se usa para datos que toleran staleness. No se usa como mecanismo de idempotencia.
- La observabilidad de los componentes forma parte de la implementación, no se deja para el final.

## 20. Qué no pretende resolver esta PoC

No es un payment engine completo. No incluye ledger, conciliación, settlement, antifraude, persistencia, outbox ni mensajería porque desviarían el foco. El caso funcional existe para que las decisiones de resiliencia tengan consecuencias reales, pero el objetivo del repositorio es poder estudiar y comparar patrones de fault tolerance de forma reproducible.
