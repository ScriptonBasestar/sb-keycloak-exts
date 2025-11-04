# Event Listener Common Module

Keycloak Event Listener 구현을 위한 공통 모듈입니다. 여러 메시징 시스템(Kafka, RabbitMQ 등)을 지원하는 이벤트 리스너에서 공유하는 모델과 인터페이스를 제공합니다.

## 개요

이 모듈은 Keycloak 이벤트 리스너를 구현할 때 필요한 공통 컴포넌트를 제공하여 코드 중복을 방지하고 일관성을 유지합니다.

## 주요 컴포넌트

### 1. Event Models (`model/`)

Keycloak 이벤트를 JSON으로 직렬화하기 위한 데이터 모델입니다.

#### KeycloakEvent
사용자 이벤트(로그인, 로그아웃, 회원가입 등)를 표현하는 모델입니다.

```kotlin
data class KeycloakEvent(
    @JsonProperty("id") val id: String,
    @JsonProperty("time") val time: Long,
    @JsonProperty("type") val type: String,
    @JsonProperty("realmId") val realmId: String,
    @JsonProperty("clientId") val clientId: String?,
    @JsonProperty("userId") val userId: String?,
    @JsonProperty("sessionId") val sessionId: String?,
    @JsonProperty("ipAddress") val ipAddress: String?,
    @JsonProperty("details") val details: Map<String, String>?,
)
```

**사용 예시:**
```kotlin
val keycloakEvent = KeycloakEvent(
    id = UUID.randomUUID().toString(),
    time = event.time,
    type = event.type.name,
    realmId = event.realmId,
    clientId = event.clientId,
    userId = event.userId,
    sessionId = event.sessionId,
    ipAddress = event.ipAddress,
    details = event.details,
)

val json = objectMapper.writeValueAsString(keycloakEvent)
```

#### KeycloakAdminEvent
관리자 이벤트(사용자 생성, 설정 변경 등)를 표현하는 모델입니다.

```kotlin
data class KeycloakAdminEvent(
    @JsonProperty("id") val id: String,
    @JsonProperty("time") val time: Long,
    @JsonProperty("operationType") val operationType: String,
    @JsonProperty("realmId") val realmId: String,
    @JsonProperty("authDetails") val authDetails: AuthDetails,
    @JsonProperty("resourcePath") val resourcePath: String?,
    @JsonProperty("representation") val representation: String?,
)
```

#### AuthDetails
관리자 이벤트의 인증 정보를 표현하는 모델입니다.

```kotlin
data class AuthDetails(
    @JsonProperty("realmId") val realmId: String?,
    @JsonProperty("clientId") val clientId: String?,
    @JsonProperty("userId") val userId: String?,
    @JsonProperty("ipAddress") val ipAddress: String?,
)
```

### 2. Metrics Interface (`metrics/`)

이벤트 리스너의 메트릭 수집을 위한 공통 인터페이스입니다.

#### EventMetrics
```kotlin
interface EventMetrics {
    fun recordEventSent(eventType: String, realm: String, destination: String, sizeBytes: Int)
    fun recordEventFailed(eventType: String, realm: String, destination: String, errorType: String)
    fun startTimer(): TimerSample
    fun stopTimer(sample: TimerSample, eventType: String)
    fun getMetricsSummary(): MetricsSummary
}
```

**구현 예시:**
```kotlin
class KafkaEventMetrics : EventMetrics {
    private val eventsSent = AtomicLong(0)
    private val eventsFailed = AtomicLong(0)

    override fun recordEventSent(eventType: String, realm: String, destination: String, sizeBytes: Int) {
        eventsSent.incrementAndGet()
        logger.trace("Event sent: $eventType")
    }

    override fun recordEventFailed(eventType: String, realm: String, destination: String, errorType: String) {
        eventsFailed.incrementAndGet()
        logger.warn("Event failed: $eventType - $errorType")
    }

    override fun getMetricsSummary(): MetricsSummary {
        return MetricsSummary(
            totalSent = eventsSent.get(),
            totalFailed = eventsFailed.get(),
            avgLatencyMs = calculateAvgLatency(),
            errorsByType = getErrorsByType(),
            eventsByType = getEventsByType()
        )
    }
}
```

#### MetricsSummary
메트릭 요약 정보를 담는 데이터 클래스입니다.

```kotlin
data class MetricsSummary(
    val totalSent: Long,
    val totalFailed: Long,
    val avgLatencyMs: Double,
    val errorsByType: Map<String, Long>,
    val eventsByType: Map<String, Long>,
)
```

#### TimerSample
이벤트 처리 시간 측정을 위한 타이머 샘플입니다.

```kotlin
data class TimerSample(
    val startTime: Long = System.nanoTime(),
)
```

## 의존성

```gradle
dependencies {
    // Keycloak SPI (provided by Keycloak at runtime)
    compileOnly libs.keycloak.server.spi
    compileOnly libs.keycloak.server.spi.private

    // JSON Processing
    implementation libs.bundles.jackson

    // Logging
    implementation libs.bundles.logging
}
```

## 새로운 이벤트 리스너 구현 가이드

### 1. 프로젝트 구조 생성

```
events/
└── event-listener-{system}/
    ├── src/
    │   ├── main/
    │   │   ├── kotlin/
    │   │   │   └── org/scriptonbasestar/kcexts/events/{system}/
    │   │   │       ├── {System}EventListenerProviderFactory.kt
    │   │   │       ├── {System}EventListenerProvider.kt
    │   │   │       ├── {System}EventListenerConfig.kt
    │   │   │       ├── {System}ConnectionManager.kt
    │   │   │       └── metrics/
    │   │   │           └── {System}EventMetrics.kt
    │   │   └── resources/
    │   │       └── META-INF/
    │   │           └── services/
    │   │               └── org.keycloak.events.EventListenerProviderFactory
    │   └── test/
    │       └── kotlin/
    └── build.gradle
```

### 2. build.gradle 설정

```gradle
dependencies {
    // Common Event Listener Module
    implementation project(':events:event-listener-common')

    // Messaging System Client
    implementation libs.{system}.client

    // JSON Processing
    implementation libs.bundles.jackson
    implementation libs.bundles.logging

    // Test Dependencies
    testImplementation libs.bundles.testing
    testImplementation libs.testcontainers.{system}
}
```

### 3. EventMetrics 구현

```kotlin
class {System}EventMetrics : EventMetrics {
    private val eventsSent = ConcurrentHashMap<String, AtomicLong>()
    private val eventsFailed = ConcurrentHashMap<String, AtomicLong>()
    private val eventDurations = ConcurrentHashMap<String, AtomicLong>()

    override fun recordEventSent(
        eventType: String,
        realm: String,
        destination: String,
        sizeBytes: Int
    ) {
        val key = "$eventType:$realm:$destination"
        eventsSent.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    override fun recordEventFailed(
        eventType: String,
        realm: String,
        destination: String,
        errorType: String
    ) {
        val key = "$eventType:$realm:$destination:$errorType"
        eventsFailed.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    override fun startTimer(): TimerSample = TimerSample(System.nanoTime())

    override fun stopTimer(sample: TimerSample, eventType: String) {
        val duration = System.nanoTime() - sample.startTime
        eventDurations.computeIfAbsent(eventType) { AtomicLong(0) }.addAndGet(duration)
    }

    override fun getMetricsSummary(): MetricsSummary {
        val totalSent = eventsSent.values.sumOf { it.get() }
        val totalFailed = eventsFailed.values.sumOf { it.get() }
        val avgLatency = if (eventDurations.isNotEmpty()) {
            eventDurations.values.sumOf { it.get() } / eventDurations.size / 1_000_000.0
        } else 0.0

        return MetricsSummary(
            totalSent = totalSent,
            totalFailed = totalFailed,
            avgLatencyMs = avgLatency,
            errorsByType = eventsFailed.mapValues { it.value.get() },
            eventsByType = eventsSent.mapValues { it.value.get() }
        )
    }
}
```

### 4. EventListenerProvider 구현

```kotlin
class {System}EventListenerProvider(
    private val session: KeycloakSession,
    private val config: {System}EventListenerConfig,
    private val connectionManager: {System}ConnectionManager,
    private val metrics: {System}EventMetrics
) : EventListenerProvider {

    private val logger = Logger.getLogger({System}EventListenerProvider::class.java)
    private val objectMapper = ObjectMapper()

    override fun onEvent(event: Event) {
        if (!config.enableUserEvents) return

        val timerSample = metrics.startTimer()
        try {
            val keycloakEvent = KeycloakEvent(
                id = UUID.randomUUID().toString(),
                time = event.time,
                type = event.type.name,
                realmId = event.realmId,
                clientId = event.clientId,
                userId = event.userId,
                sessionId = event.sessionId,
                ipAddress = event.ipAddress,
                details = event.details
            )

            val json = objectMapper.writeValueAsString(keycloakEvent)
            connectionManager.send(json)

            metrics.recordEventSent(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.destination,
                sizeBytes = json.toByteArray().size
            )
            metrics.stopTimer(timerSample, event.type.name)
        } catch (e: Exception) {
            metrics.recordEventFailed(
                eventType = event.type.name,
                realm = event.realmId ?: "unknown",
                destination = config.destination,
                errorType = e.javaClass.simpleName
            )
            logger.error("Failed to process user event", e)
        }
    }

    override fun onEvent(event: AdminEvent, includeRepresentation: Boolean) {
        // Similar implementation for admin events
    }

    override fun close() {
        logger.debug("{System}EventListenerProvider closed")
    }
}
```

### 5. SPI 등록

`src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory` 파일 생성:

```
org.scriptonbasestar.kcexts.events.{system}.{System}EventListenerProviderFactory
```

## JSON 직렬화 예시

### User Event JSON
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "time": 1699012345678,
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "account",
  "userId": "user-123",
  "sessionId": "session-456",
  "ipAddress": "192.168.1.100",
  "details": {
    "username": "john.doe",
    "auth_method": "openid-connect"
  }
}
```

### Admin Event JSON
```json
{
  "id": "650e8400-e29b-41d4-a716-446655440000",
  "time": 1699012345678,
  "operationType": "CREATE",
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "admin-123",
    "ipAddress": "192.168.1.1"
  },
  "resourcePath": "users/user-456",
  "representation": "{\"username\":\"new.user\",\"email\":\"new.user@example.com\"}"
}
```

## 베스트 프랙티스

1. **에러 처리**: 항상 try-catch로 감싸고, Keycloak 동작에 영향을 주지 않도록 예외를 삼킵니다.
2. **메트릭 수집**: EventMetrics 인터페이스를 구현하여 성공/실패를 추적합니다.
3. **비동기 처리**: 가능한 경우 메시지 전송을 비동기로 처리합니다.
4. **연결 관리**: Connection pooling과 재연결 로직을 구현합니다.
5. **테스트**: 단위 테스트와 통합 테스트를 작성합니다 (TestContainers 권장).

## 예제 구현

- **Kafka Event Listener**: `events/event-listener-kafka`
- **RabbitMQ Event Listener**: `events/event-listener-rabbitmq`

## 라이선스

Apache License 2.0
