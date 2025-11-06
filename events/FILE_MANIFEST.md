# Events Listener Modules - File Manifest & Structure

## Module File Organization

### 1. event-listener-kafka
```
event-listener-kafka/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/kafka/
│   ├── KafkaEventListenerProviderFactory.kt        (214 lines, 88 KB)
│   ├── KafkaEventListenerProvider.kt               (259 lines, 9 KB)
│   ├── KafkaEventListenerConfig.kt                 (42 lines, 1.4 KB)
│   ├── KafkaEventMessage.kt                        (13 lines, 0.4 KB)
│   ├── KafkaProducerManager.kt                     (TBD - Connection management)
│   └── metrics/
│       └── KafkaEventMetrics.kt                    (TBD - Metrics collection)
├── src/test/kotlin/
│   ├── KafkaEventListenerProviderSimpleTest.kt
│   ├── KafkaEventListenerIntegrationTest.kt
│   └── metrics/
│       └── KafkaEventMetricsTest.kt
├── src/integrationTest/kotlin/
│   ├── KafkaEventListenerIntegrationTest.kt
│   ├── KafkaPerformanceTest.kt
│   └── testcontainers/
│       ├── KeycloakTestContainer.kt
│       ├── KafkaTestContainer.kt
│       └── BaseIntegrationTest.kt
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
│       └── Content: org.scriptonbasestar.kcexts.events.kafka.KafkaEventListenerProviderFactory
├── build.gradle                                     (149 lines)
└── README.md                                        (Comprehensive docs)
```
**TOTAL: 9 Kotlin files, 1 SPI file, 1 build config, 1 README**

### 2. event-listener-azure
```
event-listener-azure/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/azure/
│   ├── AzureEventListenerProviderFactory.kt        (255 lines, 9.5 KB)
│   ├── AzureEventListenerProvider.kt               (349 lines, 13 KB)
│   ├── AzureEventMessage.kt                        (Transport-specific model)
│   ├── config/
│   │   └── AzureEventListenerConfig.kt            (Configuration loading)
│   ├── sender/
│   │   └── AzureServiceBusSender.kt               (Azure Service Bus integration)
│   └── metrics/
│       └── AzureEventMetrics.kt                   (Metrics collection)
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
├── build.gradle                                     (150 lines)
└── README.md                                        (Comprehensive docs)
```
**TOTAL: 5 main Kotlin files, 0 test files, 1 SPI file**

### 3. event-listener-nats
```
event-listener-nats/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/nats/
│   ├── NatsEventListenerProviderFactory.kt         (219 lines, 8.2 KB)
│   ├── NatsEventListenerProvider.kt                (259 lines, 9.5 KB)
│   ├── NatsEventMessage.kt                        (Transport-specific model)
│   ├── NatsEventListenerConfig.kt                  (Factory methods pattern)
│   ├── NatsConnectionManager.kt                    (NATS connection/publishing)
│   └── metrics/
│       └── NatsEventMetrics.kt                    (Metrics collection)
├── src/test/kotlin/
│   ├── NatsEventListenerProviderTest.kt
│   ├── NatsEventListenerConfigTest.kt
│   └── metrics/
│       └── NatsEventMetricsTest.kt
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
├── build.gradle
└── README.md
```
**TOTAL: 8 Kotlin files (5 main, 3 test), 1 SPI file**

### 4. event-listener-rabbitmq
```
event-listener-rabbitmq/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/rabbitmq/
│   ├── RabbitMQEventListenerProviderFactory.kt     (214 lines, 8 KB)
│   ├── RabbitMQEventListenerProvider.kt            (Transport-specific provider)
│   ├── RabbitMQEventMessage.kt                     (Transport-specific model)
│   ├── RabbitMQEventListenerConfig.kt              (Factory methods pattern)
│   ├── RabbitMQConnectionManager.kt                (RabbitMQ connection/publishing)
│   └── metrics/
│       └── RabbitMQEventMetrics.kt                (Metrics collection)
├── src/test/kotlin/
│   ├── RabbitMQEventListenerProviderTest.kt
│   ├── RabbitMQEventListenerConfigTest.kt
│   └── metrics/
│       └── RabbitMQEventMetricsTest.kt
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
├── build.gradle
└── README.md
```
**TOTAL: 8 Kotlin files (5 main, 3 test), 1 SPI file**

### 5. event-listener-redis
```
event-listener-redis/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/redis/
│   ├── RedisEventListenerProviderFactory.kt        (219 lines, 8.2 KB)
│   ├── RedisEventListenerProvider.kt               (Transport-specific provider)
│   ├── RedisEventMessage.kt                        (Transport-specific model)
│   ├── config/
│   │   └── RedisEventListenerConfig.kt            (Constructor pattern)
│   ├── producer/
│   │   └── RedisStreamProducer.kt                 (Redis stream operations)
│   └── metrics/
│       └── RedisEventMetrics.kt                   (Metrics collection)
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
├── build.gradle
└── README.md
```
**TOTAL: 5 main Kotlin files, 0 test files, 1 SPI file**

### 6. event-listener-aws
```
event-listener-aws/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/aws/
│   ├── AwsEventListenerProviderFactory.kt          (161 lines, 6 KB)
│   ├── AwsEventListenerProvider.kt                 (Transport-specific provider)
│   ├── AwsEventMessage.kt                          (Transport-specific model)
│   ├── config/
│   │   └── AwsEventListenerConfig.kt              (Constructor pattern)
│   ├── publisher/
│   │   └── AwsMessagePublisher.kt                 (AWS SQS/SNS operations)
│   └── metrics/
│       └── AwsEventMetrics.kt                     (Metrics collection)
├── src/main/resources/META-INF/services/
│   └── org.keycloak.events.EventListenerProviderFactory
├── build.gradle
└── README.md
```
**TOTAL: 5 main Kotlin files, 0 test files, 1 SPI file**

### 7. event-listener-common (Shared Library)
```
event-listener-common/
├── src/main/kotlin/org/scriptonbasestar/kcexts/events/common/
│   ├── config/
│   │   └── ConfigLoader.kt                        (Unified config loading)
│   ├── model/
│   │   ├── KeycloakEvent.kt                       (User event model)
│   │   ├── KeycloakAdminEvent.kt                 (Admin event model)
│   │   └── EventMeta.kt                           (Shared metadata)
│   ├── metrics/
│   │   ├── EventMetrics.kt                        (Base metrics interface)
│   │   └── PrometheusMetricsExporter.kt          (Prometheus export)
│   ├── resilience/
│   │   ├── CircuitBreaker.kt                      (Circuit breaker pattern)
│   │   ├── CircuitBreakerOpenException.kt         (Exception)
│   │   ├── RetryPolicy.kt                         (Retry with backoff)
│   │   └── RetryExhaustedException.kt             (Exception)
│   ├── batch/
│   │   └── BatchProcessor.kt<T>                   (Batch processing)
│   └── dlq/
│       └── DeadLetterQueue.kt                     (DLQ management)
├── src/test/kotlin/
│   ├── resilience/
│   │   ├── CircuitBreakerTest.kt
│   │   └── RetryPolicyTest.kt
│   ├── batch/
│   │   └── BatchProcessorTest.kt
│   └── dlq/
│       └── DeadLetterQueueTest.kt
├── build.gradle                                   (33 lines, minimal)
└── README.md
```
**TOTAL: 11 main Kotlin files, 6 test files**

## Key Files Analysis

### Factory Pattern Implementation (All Modules)
- **File**: `{Transport}EventListenerProviderFactory.kt`
- **Key Methods**:
  - `init(Config.Scope)` - Initialize shared resources (metrics, resilience patterns)
  - `create(KeycloakSession)` - Create provider instance
  - `postInit(KeycloakSessionFactory)` - Post-initialization hook
  - `close()` - Cleanup resources
  - `getId()` - Returns unique identifier (e.g., "kafka-event-listener")
- **Shared Initialization** (all modules):
  - PrometheusMetricsExporter (if enabled)
  - EventMetrics instance
  - CircuitBreaker instance
  - RetryPolicy instance
  - DeadLetterQueue instance
  - BatchProcessor instance

### Provider Pattern Implementation (All Modules)
- **File**: `{Transport}EventListenerProvider.kt`
- **Key Methods**:
  - `onEvent(Event)` - Handle user events
  - `onEvent(AdminEvent, Boolean)` - Handle admin events
  - `close()` - Cleanup
- **Common Flow**:
  1. Check if event type enabled
  2. Check destination configured
  3. Build KeycloakEvent/KeycloakAdminEvent model
  4. Serialize to JSON
  5. Call `sendEventWithResilience()`
  6. Record metrics
  7. Handle exceptions and DLQ

### Configuration Pattern
**Constructor Pattern** (Kafka, Azure, Redis, AWS):
- Direct property initialization in constructor
- Uses ConfigLoader utility
- Example: `KafkaEventListenerConfig(session, configScope)`

**Factory Method Pattern** (NATS, RabbitMQ):
- Static factory methods
- Two variants: `fromRuntime()`, `fromInit()`
- Example: `NatsEventListenerConfig.fromRuntime(session, configScope)`

### SPI Registration
- **File**: `META-INF/services/org.keycloak.events.EventListenerProviderFactory`
- **Content**: Single line with FQCN of factory class
- **Example**: `org.scriptonbasestar.kcexts.events.kafka.KafkaEventListenerProviderFactory`

## Statistics by Module

| Module | Main | Test | Integration | Total | Lines (Approx) |
|--------|------|------|-------------|-------|---|
| Kafka | 5 | 3 | 4 | 12 | 1,500+ |
| Azure | 5 | 0 | 0 | 5 | 700+ |
| NATS | 5 | 3 | 0 | 8 | 900+ |
| RabbitMQ | 5 | 3 | 0 | 8 | 900+ |
| Redis | 5 | 0 | 0 | 5 | 700+ |
| AWS | 5 | 0 | 0 | 5 | 700+ |
| Common | 11 | 6 | 0 | 17 | 1,200+ |
| **TOTAL** | **41** | **15** | **4** | **60** | **6,500+** |

## Build Artifact Names

| Module | shadowJar Name | File Format |
|--------|---|---|
| Kafka | `keycloak-kafka-event-listener` | `-all.jar` |
| Azure | `keycloak-azure-event-listener` | `-all.jar` |
| NATS | `keycloak-nats-event-listener` | `-all.jar` |
| RabbitMQ | `keycloak-rabbitmq-event-listener` | `-all.jar` |
| Redis | `keycloak-redis-event-listener` | `-all.jar` |
| AWS | `keycloak-aws-event-listener` | `-all.jar` |
| Common | N/A (library) | `.jar` |

