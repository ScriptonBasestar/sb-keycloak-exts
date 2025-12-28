# Event Listeners Module - CLAUDE.md

## 1. Overview

Keycloak event streaming extensions for multiple messaging systems. Built on a shared common module with resilience patterns.

**Integrations**: Kafka, RabbitMQ, NATS, Redis, MQTT, AWS (SNS/SQS), Azure (Service Bus/Event Grid)

---

## 2. Architecture

```
                    ┌─────────────────────────────────────────────────────┐
                    │              event-listener-common                  │
                    │  ┌──────────┐ ┌─────────────┐ ┌──────────────────┐ │
                    │  │ Config   │ │ Resilience  │ │ Metrics          │ │
                    │  │ Loader   │ │ ├Circuit    │ │ ├EventMetrics    │ │
                    │  │          │ │ │ Breaker   │ │ ├Prometheus      │ │
                    │  │          │ │ └Retry      │ │ └ Exporter       │ │
                    │  └──────────┘ └─────────────┘ └──────────────────┘ │
                    │  ┌──────────┐ ┌─────────────┐ ┌──────────────────┐ │
                    │  │ Batch    │ │ Dead Letter │ │ Event Models     │ │
                    │  │ Processor│ │ Queue       │ │ ├KeycloakEvent   │ │
                    │  │          │ │             │ │ └KeycloakAdmin   │ │
                    │  └──────────┘ └─────────────┘ └──────────────────┘ │
                    └─────────────────────────────────────────────────────┘
                                          ▲
                    ┌─────────────────────┴─────────────────────┐
                    │                                           │
            ┌───────┴───────┐   ┌───────┴───────┐   ┌───────┴───────┐
            │ event-kafka   │   │ event-rabbitmq│   │ event-nats    │ ...
            │ ├Factory      │   │ ├Factory      │   │ ├Factory      │
            │ ├Provider     │   │ ├Provider     │   │ ├Provider     │
            │ ├Config       │   │ ├Config       │   │ ├Config       │
            │ └Connection   │   │ └Connection   │   │ └Connection   │
            └───────────────┘   └───────────────┘   └───────────────┘
```

---

## 3. Common Module Components

Located in `event-listener-common/src/main/kotlin/.../events/common/`:

| Component | File | Purpose |
|-----------|------|---------|
| ConfigLoader | config/ConfigLoader.kt | Unified config from realm attrs → system props → defaults |
| CircuitBreaker | resilience/CircuitBreaker.kt | Fail-fast when broker unavailable |
| RetryPolicy | resilience/RetryPolicy.kt | Exponential backoff for transient failures |
| BatchProcessor | batch/BatchProcessor.kt | Batch events before sending |
| DeadLetterQueue | dlq/DeadLetterQueue.kt | Failed event persistence |
| EventMetrics | metrics/EventMetrics.kt | Micrometer metrics collection |
| PrometheusExporter | metrics/PrometheusMetricsExporter.kt | Prometheus HTTP endpoint |
| KeycloakEvent | model/KeycloakEvent.kt | User event model |
| KeycloakAdminEvent | model/KeycloakAdminEvent.kt | Admin event model |

---

## 4. Configuration Hierarchy

Priority order (highest to lowest):

```
1. Realm Attributes    ← Runtime, per-realm (Admin Console → Realm → Attributes)
2. Config.Scope        ← Init-time, global (keycloak.conf or SPI config)
3. System Properties   ← JVM startup (-Dkafka.bootstrapServers=...)
4. Default Values      ← Hardcoded fallbacks
```

**ConfigLoader Usage**:
```kotlin
val config = ConfigLoader.forRuntime(session, initConfigScope, "kafka")
val bootstrapServers = config.getString("bootstrapServers", "localhost:9092")
val batchSize = config.getInt("batchSize", 100)
val enableRetry = config.getBoolean("enableRetry", true)
```

---

## 5. Adding a New Messaging System

### Step 1: Create Module

```bash
mkdir -p events/event-listener-newbroker/src/main/kotlin/.../events/newbroker
mkdir -p events/event-listener-newbroker/src/main/resources/META-INF/services
```

### Step 2: Add to settings.gradle

```gradle
include ':events:event-listener-newbroker'
```

### Step 3: Create build.gradle

```gradle
dependencies {
    implementation project(':events:event-listener-common')
    bundleLib 'com.newbroker:client:1.0.0'  // Broker client library
}
```

### Step 4: Implement Core Classes

**1. Config** (`NewBrokerEventListenerConfig.kt`):
```kotlin
class NewBrokerEventListenerConfig(
    session: KeycloakSession,
    initConfigScope: Config.Scope?
) {
    private val loader = ConfigLoader.forRuntime(session, initConfigScope, "newbroker")

    val host: String = loader.getString("host", "localhost")
    val port: Int = loader.getInt("port", 5672)
    val topic: String = loader.getString("eventTopic", "keycloak.events")
    val enableUserEvents: Boolean = loader.getBoolean("enableUserEvents", true)
    val enableAdminEvents: Boolean = loader.getBoolean("enableAdminEvents", true)
}
```

**2. Connection Manager** (`NewBrokerConnectionManager.kt`):
```kotlin
class NewBrokerConnectionManager(private val config: NewBrokerEventListenerConfig)
    : EventConnectionManager {  // Implement common interface

    private var client: NewBrokerClient? = null

    fun connect() {
        client = NewBrokerClient(config.host, config.port)
    }

    fun sendEvent(topic: String, key: String, value: String) {
        client?.publish(topic, key, value)
    }

    override fun close() {
        client?.close()
    }
}
```

**3. Provider** (`NewBrokerEventListenerProvider.kt`):
```kotlin
class NewBrokerEventListenerProvider(
    private val session: KeycloakSession,
    private val config: NewBrokerEventListenerConfig,
    private val connectionManager: NewBrokerConnectionManager,
    private val metrics: EventMetrics,
    private val circuitBreaker: CircuitBreaker,
    private val retryPolicy: RetryPolicy,
    private val dlq: DeadLetterQueue
) : EventListenerProvider {

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) return

        val keycloakEvent = KeycloakEvent.fromEvent(event, session)
        val json = jacksonObjectMapper().writeValueAsString(keycloakEvent)

        circuitBreaker.execute {
            retryPolicy.execute {
                connectionManager.sendEvent(config.topic, event.userId ?: "", json)
                metrics.recordEventSent(event.type.name)
            }
        }
    }

    override fun onAdminEvent(event: AdminEvent, includeRepresentation: Boolean) {
        if (!config.enableAdminEvents) return
        // Similar implementation
    }

    override fun close() {}
}
```

**4. Factory** (`NewBrokerEventListenerProviderFactory.kt`):
```kotlin
class NewBrokerEventListenerProviderFactory : EventListenerProviderFactory {
    private lateinit var initConfigScope: Config.Scope
    private lateinit var metrics: EventMetrics
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var retryPolicy: RetryPolicy
    private lateinit var dlq: DeadLetterQueue

    override fun create(session: KeycloakSession): EventListenerProvider {
        val config = NewBrokerEventListenerConfig(session, initConfigScope)
        val connectionManager = NewBrokerConnectionManager(config)
        return NewBrokerEventListenerProvider(
            session, config, connectionManager, metrics, circuitBreaker, retryPolicy, dlq
        )
    }

    override fun init(config: Config.Scope) {
        initConfigScope = config
        // Initialize resilience components (see Kafka example for full impl)
        circuitBreaker = CircuitBreaker(name = "newbroker", failureThreshold = 5, ...)
        retryPolicy = RetryPolicy(maxAttempts = 3, ...)
        dlq = DeadLetterQueue(maxSize = 10000, ...)
        metrics = EventMetrics()
    }

    override fun postInit(factory: KeycloakSessionFactory) {}
    override fun close() { /* cleanup */ }
    override fun getId(): String = "newbroker-event-listener"
}
```

### Step 5: Register SPI

Create: `src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory`
```
org.scriptonbasestar.kcexts.events.newbroker.NewBrokerEventListenerProviderFactory
```

---

## 6. Event Models

### KeycloakEvent (User Events)

```kotlin
data class KeycloakEvent(
    val id: String,                    // UUID
    val time: Long,                    // Timestamp (epoch ms)
    val type: String,                  // LOGIN, LOGOUT, REGISTER, etc.
    val realmId: String,               // Realm identifier
    val realmName: String,             // Realm display name
    val clientId: String?,             // Client ID
    val userId: String?,               // User ID
    val sessionId: String?,            // Session ID
    val ipAddress: String?,            // Client IP
    val details: Map<String, String>?  // Event-specific data
)
```

### KeycloakAdminEvent (Admin Events)

```kotlin
data class KeycloakAdminEvent(
    val id: String,
    val time: Long,
    val realmId: String,
    val authRealmId: String?,
    val authClientId: String?,
    val authUserId: String?,
    val operationType: String,         // CREATE, UPDATE, DELETE, ACTION
    val resourceType: String,          // USER, REALM, CLIENT, etc.
    val resourcePath: String,
    val representation: String?,       // JSON representation of changed resource
    val error: String?
)
```

---

## 7. Resilience Patterns

### Circuit Breaker

Prevents cascade failures when broker is unavailable:

```kotlin
val circuitBreaker = CircuitBreaker(
    name = "kafka-sender",
    failureThreshold = 5,       // Open after 5 consecutive failures
    successThreshold = 2,       // Close after 2 successes in half-open
    openTimeout = Duration.ofSeconds(60)  // Try again after 60s
)

circuitBreaker.execute {
    connectionManager.send(...)  // May throw
}
```

### Retry Policy

Exponential backoff for transient failures:

```kotlin
val retryPolicy = RetryPolicy(
    maxAttempts = 3,
    initialDelay = Duration.ofMillis(100),
    maxDelay = Duration.ofMillis(10000),
    backoffStrategy = BackoffStrategy.EXPONENTIAL,
    multiplier = 2.0
)

retryPolicy.execute {
    connectionManager.send(...)  // Retries on failure
}
```

### Dead Letter Queue

Stores failed events for later recovery:

```kotlin
val dlq = DeadLetterQueue(
    maxSize = 10000,
    persistToFile = true,
    persistencePath = "./dlq/kafka"
)

dlq.add(
    eventType = "LOGIN",
    eventData = jsonPayload,
    realm = "master",
    destination = "keycloak.events",
    failureReason = "Broker unavailable",
    attemptCount = 3
)
```

---

## 8. Build Commands

```bash
# Build all event listeners
./gradlew :events:build

# Build specific listener
./gradlew :events:event-listener-kafka:build

# Create Shadow JAR
./gradlew :events:event-listener-kafka:shadowJar

# Run tests
./gradlew :events:test

# Integration tests (requires Docker)
./gradlew :events:event-listener-kafka:integrationTest
```

---

## 9. Testing

### Unit Tests
- Mock `KeycloakSession`, `Event`, `AdminEvent`
- Test event serialization
- Verify configuration loading

### Integration Tests (TestContainers)
```kotlin
@Testcontainers
class KafkaEventListenerIntegrationTest {
    @Container
    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))

    @Test
    fun `should send event to Kafka`() {
        // Full E2E test with real Kafka
    }
}
```

---

## 10. Kafka-Specific Configuration

Reference implementation with all available options:

| Key | Default | Description |
|-----|---------|-------------|
| kafka.bootstrapServers | localhost:9092 | Kafka broker addresses |
| kafka.eventTopic | keycloak.events | User event topic |
| kafka.adminEventTopic | keycloak.admin.events | Admin event topic |
| kafka.clientId | keycloak-event-listener | Producer client ID |
| kafka.enableUserEvents | true | Send user events |
| kafka.enableAdminEvents | true | Send admin events |
| kafka.acks | all | Producer acknowledgments |
| kafka.retries | 3 | Producer retry count |
| kafka.batchSize | 16384 | Batch size in bytes |
| kafka.lingerMs | 1 | Batch linger time |
| kafka.compressionType | none | Compression (gzip/snappy/lz4) |
| kafka.enablePrometheus | false | Enable Prometheus metrics |
| kafka.prometheusPort | 9090 | Prometheus HTTP port |
| kafka.enableCircuitBreaker | true | Enable circuit breaker |
| kafka.enableRetry | true | Enable retry policy |
| kafka.enableBatching | false | Enable batch processing |

---

## 11. Metrics

### Available Metrics

| Metric | Type | Labels |
|--------|------|--------|
| keycloak_events_sent_total | Counter | event_type, realm |
| keycloak_events_failed_total | Counter | event_type, realm, error_type |
| keycloak_event_processing_duration_ms | Histogram | event_type |
| keycloak_circuit_breaker_state | Gauge | name, state |
| keycloak_dlq_size | Gauge | destination |

### Prometheus Endpoint

When enabled (`enablePrometheus=true`):
```
http://keycloak:9090/metrics
```

---

## 12. Deployment

```bash
# Build all
./gradlew :events:shadowJar

# Deploy
cp events/event-listener-*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/

# Enable in Keycloak
# Realm → Events → Event Listeners → Add "kafka-event-listener"

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

---

## 13. Related Files

| File | Purpose |
|------|---------|
| `events/build.gradle` | Shared build configuration |
| `events/README.md` | Module overview |
| `events/docs/` | Architecture documentation |
| `events/grafana-dashboard.json` | Pre-built Grafana dashboard |
| `events/examples/` | Docker Compose examples |
| Root `CLAUDE.md` Section 3.3-3.5 | Event listener architecture |
