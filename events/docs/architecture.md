# Architecture Overview

Keycloak Event Listener 모듈의 전체 아키텍처를 설명합니다.

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Keycloak Core                          │
│  (User Login, Admin Actions, Realm Events)                 │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
         ┌───────────────────────────────┐
         │   EventListenerProvider SPI   │
         └───────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
┌───────────────┐              ┌───────────────┐
│  User Events  │              │ Admin Events  │
│  (LOGIN, etc) │              │ (CREATE, etc) │
└───────┬───────┘              └───────┬───────┘
        │                              │
        └──────────────┬───────────────┘
                       ▼
           ┌───────────────────────┐
           │   Event Filtering     │
           │  (Type, Realm, etc)   │
           └───────────┬───────────┘
                       ▼
           ┌───────────────────────┐
           │   Circuit Breaker     │
           │   (Fail Fast)         │
           └───────────┬───────────┘
                       ▼
           ┌───────────────────────┐
           │   Retry Policy        │
           │ (Exponential Backoff) │
           └───────────┬───────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
    ┌─────────┐             ┌─────────────────┐
    │   DLQ   │             │ Connection Mgr  │
    │ (Failed)│             │  (Send Success) │
    └─────────┘             └────────┬────────┘
                                     ▼
                     ┌───────────────────────────┐
                     │  Message Broker/Queue     │
                     │ (Kafka, RabbitMQ, etc)    │
                     └───────────────────────────┘
```

## Component Layers

### 1. SPI Layer (Keycloak Integration)

**책임**: Keycloak과의 인터페이스 제공

**주요 클래스**:
- `{Transport}EventListenerProviderFactory` - SPI 진입점, 라이프사이클 관리
- `{Transport}EventListenerProvider` - 이벤트 수신 및 처리
- `EventListenerProviderFactory` (Keycloak Interface)

**호출 흐름**:
```kotlin
Keycloak → onEvent(Event) → EventListenerProvider
         → onAdminEvent(AdminEvent, includeRepresentation)
```

### 2. Configuration Layer

**책임**: 다층 설정 로딩 및 검증

**설정 우선순위**:
1. **Realm Attributes** (최고 우선순위)
2. **System Properties** (`-D` 플래그)
3. **Environment Variables**
4. **Default Values** (최저 우선순위)

**주요 클래스**:
- `{Transport}EventListenerConfig` - Transport별 설정
- `ConfigLoader` (Common) - 공통 설정 로더

**예시**:
```kotlin
val bootstrapServers =
    realm.getAttribute("kafka.bootstrap.servers")
    ?: System.getProperty("kafka.bootstrap.servers")
    ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
    ?: "localhost:9092"
```

### 3. Resilience Layer (event-listener-common)

**책임**: 장애 복구 및 안정성 보장

#### Circuit Breaker
- **상태**: CLOSED → OPEN → HALF_OPEN
- **목적**: 장애 전파 방지, Fast Fail
- **구현**: `CircuitBreaker.kt`

#### Retry Policy
- **전략**: FIXED, LINEAR, EXPONENTIAL, EXPONENTIAL_JITTER
- **목적**: 일시적 장애 자동 복구
- **구현**: `RetryPolicy.kt`

#### Dead Letter Queue (DLQ)
- **저장**: 메모리 + 선택적 파일 영속화
- **목적**: 실패 이벤트 보관 및 재처리
- **구현**: `DeadLetterQueue.kt`

#### Batch Processor
- **트리거**: 크기 기반 + 시간 기반
- **목적**: 처리량 향상, 네트워크 오버헤드 감소
- **구현**: `BatchProcessor.kt`

### 4. Transport Layer

**책임**: 실제 메시징 시스템 연동

**표준 인터페이스**:
```kotlin
interface EventConnectionManager {
    fun send(destination: String, message: String): Boolean
    fun isConnected(): Boolean
    fun close()
}
```

**구현체**:
- `KafkaConnectionManager` - Kafka Producer API
- `AzureConnectionManager` - Azure Service Bus SDK
- `NatsConnectionManager` - NATS JetStream
- `RabbitMQConnectionManager` - AMQP 0.9.1
- `RedisConnectionManager` - Lettuce (Redis Streams)
- `AwsConnectionManager` - AWS SDK (SQS/SNS)

### 5. Observability Layer

**책임**: 모니터링 및 메트릭 수집

**메트릭 인터페이스**:
```kotlin
interface EventMetrics {
    fun recordSent(eventType: String, destination: String)
    fun recordFailed(eventType: String, destination: String, error: String)
    fun recordDuration(eventType: String, durationMs: Long)
}
```

**Prometheus Metrics**:
- `keycloak_events_total` - 총 처리 이벤트
- `keycloak_events_failed_total` - 실패 이벤트
- `keycloak_circuit_breaker_state` - Circuit Breaker 상태
- `keycloak_dlq_size` - DLQ 크기

## Data Flow

### User Event Flow

```
1. User Login → Keycloak generates Event
2. EventListenerProvider.onEvent(event) called
3. Filter by event type (LOGIN, LOGOUT, etc)
4. Serialize to JSON: KeycloakEvent model
5. Circuit Breaker check
   - CLOSED → proceed
   - OPEN → fail fast, add to DLQ
6. Retry Policy execution
   - Attempt 1: send to broker
   - On failure: wait backoff delay, retry
   - Max attempts exhausted → DLQ
7. ConnectionManager.send(destination, json)
8. Metrics.recordSent() or recordFailed()
```

### Admin Event Flow

```
1. Admin creates User → Keycloak generates AdminEvent
2. EventListenerProvider.onAdminEvent(event, includeRep)
3. Filter by operation type (CREATE, UPDATE, DELETE)
4. Serialize to JSON (with optional representation)
5. Same resilience flow as User Event (steps 5-8)
```

## Module Structure

```
events/
├── event-listener-common/          # 공통 라이브러리
│   ├── config/                     # ConfigLoader
│   ├── model/                      # KeycloakEvent, EventMeta
│   ├── resilience/                 # CircuitBreaker, RetryPolicy
│   ├── dlq/                        # DeadLetterQueue
│   ├── batch/                      # BatchProcessor
│   ├── metrics/                    # EventMetrics interface
│   └── connection/                 # EventConnectionManager interface
│
├── event-listener-kafka/           # Kafka Transport
│   ├── KafkaEventListenerProviderFactory.kt
│   ├── KafkaEventListenerProvider.kt
│   ├── KafkaEventListenerConfig.kt
│   ├── KafkaConnectionManager.kt
│   └── metrics/KafkaEventMetrics.kt
│
├── event-listener-rabbitmq/        # RabbitMQ Transport
├── event-listener-nats/            # NATS Transport
├── event-listener-redis/           # Redis Transport
├── event-listener-azure/           # Azure Service Bus Transport
└── event-listener-aws/             # AWS SQS/SNS Transport
```

## Design Principles

### 1. Separation of Concerns
- **SPI Layer**: Keycloak 통합만
- **Resilience Layer**: 장애 복구만
- **Transport Layer**: 메시징 시스템 연동만

### 2. Dependency Injection
- Factory에서 모든 의존성 생성
- Provider는 의존성만 받아 사용

### 3. Fail-Safe Defaults
- 모든 resilience 패턴 기본 활성화
- 장애 시 이벤트 손실 방지 (DLQ)

### 4. Observability First
- 모든 작업 메트릭 수집
- Circuit Breaker 상태 노출
- DLQ 크기 모니터링

### 5. Testability
- 공통 테스트 유틸리티 제공
- Mock 기반 단위 테스트
- TestContainers 통합 테스트

## Performance Characteristics

### Latency

| Scenario | Latency | Notes |
|----------|---------|-------|
| **Normal** | < 10ms | Circuit CLOSED, no retries |
| **Retry** | 100ms - 10s | Exponential backoff |
| **Circuit OPEN** | < 1ms | Fail fast, no broker call |
| **Batch** | Up to flush interval | Default 5s max |

### Throughput

| Config | Events/sec | Notes |
|--------|------------|-------|
| **No Batch** | 1,000 - 5,000 | Direct send per event |
| **Batch 100** | 10,000 - 50,000 | Batch aggregation |
| **Circuit OPEN** | ∞ | No actual send, DLQ only |

### Memory Usage

| Component | Memory | Notes |
|-----------|--------|-------|
| **DLQ** | ~1KB per event | Max 10,000 events default |
| **Batch Buffer** | ~1KB per event | Max 100 events default |
| **Connection Pool** | ~10MB | Per transport |

## Failure Modes

### Broker Unavailable
1. Circuit Breaker opens after 5 failures
2. All events go to DLQ
3. Periodic retry via HALF_OPEN state

### Slow Broker
1. Retry policy increases delays
2. Events accumulate in batch buffer
3. DLQ fills if timeouts persist

### Memory Pressure
1. DLQ reaches max size (10,000)
2. Old events persisted to disk
3. New events rejected if disk full

---

**See Also**:
- [Configuration Guide](configuration.md)
- [Resilience Patterns](resilience.md)
- [Monitoring Guide](monitoring.md)
