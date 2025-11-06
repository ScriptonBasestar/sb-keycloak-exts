# Common Test Utilities

**패키지**: `org.scriptonbasestar.kcexts.events.common.test`

이 패키지는 모든 Event Listener 모듈에서 공통으로 사용할 수 있는 테스트 유틸리티를 제공합니다.

---

## 📦 제공 유틸리티

### 1. KeycloakEventTestFixtures

Keycloak User Event 및 Admin Event를 쉽게 생성하기 위한 fixture 클래스입니다.

#### 사용 예시

**기본 User Event 생성:**
```kotlin
val event = KeycloakEventTestFixtures.createUserEvent()
// type = LOGIN, realmId = "test-realm", userId = "test-user" 등 기본값 사용
```

**커스텀 User Event 생성 (Builder 패턴):**
```kotlin
val event = KeycloakEventTestFixtures.createUserEvent {
    type = EventType.REGISTER
    realmId = "production-realm"
    userId = "user-123"
    details = mapOf("email" to "user@example.com")
}
```

**기본 Admin Event 생성:**
```kotlin
val adminEvent = KeycloakEventTestFixtures.createAdminEvent()
// operationType = CREATE, realmId = "test-realm" 등 기본값 사용
```

**커스텀 Admin Event 생성:**
```kotlin
val adminEvent = KeycloakEventTestFixtures.createAdminEvent {
    operationType = OperationType.UPDATE
    resourcePath = "users/user-id"
    representation = """{"username":"newname"}"""
}
```

**공통 이벤트 타입 반복 테스트:**
```kotlin
KeycloakEventTestFixtures.CommonEventTypes.USER_EVENTS.forEach { eventType ->
    val event = KeycloakEventTestFixtures.createUserEvent(type = eventType)
    // 각 이벤트 타입에 대해 테스트
}
```

#### 제공 메서드

| 메서드 | 설명 |
|--------|------|
| `createUserEvent()` | 기본값으로 User Event 생성 |
| `createUserEvent(builder)` | Builder로 User Event 생성 |
| `createAdminEvent()` | 기본값으로 Admin Event 생성 |
| `createAdminEvent(builder)` | Builder로 Admin Event 생성 |
| `CommonEventTypes.USER_EVENTS` | 일반적인 User Event 타입 목록 |
| `CommonEventTypes.ADMIN_EVENTS` | 일반적인 Admin Event 타입 목록 |

---

### 2. MockConnectionManagerFactory

다양한 시나리오의 Mock ConnectionManager를 생성하는 팩토리 클래스입니다.

#### 사용 예시

**성공하는 ConnectionManager:**
```kotlin
val manager = MockConnectionManagerFactory.createSuccessful()
val result = manager.send("topic", "message")
assert(result == true)
```

**실패하는 ConnectionManager:**
```kotlin
val manager = MockConnectionManagerFactory.createFailing("Connection timeout")
// send() 호출 시 RuntimeException 발생
```

**불안정한 ConnectionManager (처음 몇 번 실패 후 성공):**
```kotlin
val manager = MockConnectionManagerFactory.createFlaky(failureCount = 2)
// 처음 2번 실패 → 3번째 호출부터 성공
```

**메시지 캡처 ConnectionManager:**
```kotlin
val capturedMessages = mutableListOf<Pair<String, String>>()
val manager = MockConnectionManagerFactory.createCapturing(capturedMessages)

manager.send("dest1", "msg1")
manager.send("dest2", "msg2")

assert(capturedMessages.size == 2)
assert(capturedMessages[0].first == "dest1")
```

**느린 ConnectionManager (지연 시뮬레이션):**
```kotlin
val manager = MockConnectionManagerFactory.createSlow(delayMs = 100)
// 각 send() 호출마다 100ms 지연
```

**커스텀 동작 ConnectionManager:**
```kotlin
val manager = MockConnectionManagerFactory.createCustom { destination, message ->
    when (destination) {
        "valid" -> true
        "invalid" -> false
        else -> throw IllegalArgumentException("Unknown destination")
    }
}
```

#### 제공 메서드

| 메서드 | 설명 |
|--------|------|
| `createSuccessful()` | 항상 성공하는 Mock |
| `createFailing(errorMessage)` | 항상 실패하는 Mock |
| `createFlaky(failureCount)` | N번 실패 후 성공하는 Mock |
| `createCapturing(list)` | 메시지를 리스트에 캡처하는 Mock |
| `createSlow(delayMs)` | 지연을 시뮬레이션하는 Mock |
| `createCustom(sendBehavior)` | 커스텀 동작을 정의하는 Mock |

---

### 3. TestConfigurationBuilders

공통 컴포넌트(CircuitBreaker, RetryPolicy, DeadLetterQueue 등)를 테스트용 기본값으로 쉽게 생성합니다.

#### 사용 예시

**CircuitBreaker 생성:**
```kotlin
val circuitBreaker = TestConfigurationBuilders.createCircuitBreaker(
    name = "test-cb",
    failureThreshold = 5,
    openTimeout = Duration.ofSeconds(30)
)
```

**RetryPolicy 생성 (retry 없음 - 테스트용):**
```kotlin
val retryPolicy = TestConfigurationBuilders.createRetryPolicy(
    maxAttempts = 1  // 재시도 없음
)
```

**DeadLetterQueue 생성:**
```kotlin
val deadLetterQueue = TestConfigurationBuilders.createDeadLetterQueue(
    maxSize = 10,
    persistToFile = false
)
```

**BatchProcessor 생성:**
```kotlin
val batchProcessor = TestConfigurationBuilders.createBatchProcessor<MyEventMessage>(
    batchSize = 10,
    flushInterval = Duration.ofSeconds(5)
)
```

**전체 테스트 환경 한 번에 생성:**
```kotlin
val env = TestConfigurationBuilders.createTestEnvironment()
// env.circuitBreaker, env.retryPolicy, env.deadLetterQueue 모두 사용 가능
```

#### 제공 메서드

| 메서드 | 설명 |
|--------|------|
| `createCircuitBreaker()` | CircuitBreaker 생성 |
| `createRetryPolicy()` | RetryPolicy 생성 (기본: 재시도 없음) |
| `createDeadLetterQueue()` | DeadLetterQueue 생성 |
| `createBatchProcessor()` | BatchProcessor 생성 |
| `createTestEnvironment()` | 전체 환경 한 번에 생성 |

---

### 4. MetricsAssertions

메트릭 검증을 위한 공통 Assertion 헬퍼입니다.

#### 사용 예시

**성공 메트릭 검증:**
```kotlin
val summary = metrics.getMetricsSummary()
MetricsAssertions.assertSuccessfulMetrics(
    summary.totalSent,
    summary.totalFailed,
    minSuccessCount = 10
)
```

**실패 메트릭 검증:**
```kotlin
MetricsAssertions.assertFailedMetrics(
    summary.totalSent,
    summary.totalFailed,
    minFailureCount = 1
)
```

**레이턴시 범위 검증:**
```kotlin
MetricsAssertions.assertLatencyWithinRange(
    averageLatencyMs = 150.0,
    maxAcceptableMs = 1000
)
```

**이벤트 처리율 검증:**
```kotlin
MetricsAssertions.assertEventRateWithinRange(
    eventsPerSecond = 500.0,
    minExpectedRate = 100.0,
    maxExpectedRate = 10000.0
)
```

#### 제공 메서드

| 메서드 | 설명 |
|--------|------|
| `assertSuccessfulMetrics()` | 성공적인 이벤트 처리 검증 |
| `assertFailedMetrics()` | 실패한 이벤트 처리 검증 |
| `assertMetricsSummary()` | 메트릭 요약 검증 |
| `assertLatencyWithinRange()` | 레이턴시 범위 검증 |
| `assertEventRateWithinRange()` | 이벤트 처리율 검증 |

---

## 🎯 마이그레이션 가이드

### Before (기존 테스트 패턴)

```kotlin
class MyEventListenerProviderTest {
    private lateinit var connectionManager: MyConnectionManager

    @BeforeEach
    fun setup() {
        // 30+ 줄의 mock 설정
        connectionManager = mock()
        whenever(connectionManager.send(any(), any())).thenReturn(true)
        whenever(connectionManager.isConnected()).thenReturn(true)

        // CircuitBreaker 설정
        circuitBreaker = CircuitBreaker(
            name = "test",
            failureThreshold = 5,
            successThreshold = 1,
            openTimeout = Duration.ofSeconds(30)
        )

        // RetryPolicy 설정
        retryPolicy = RetryPolicy(
            maxAttempts = 1,
            initialDelay = Duration.ZERO,
            maxDelay = Duration.ofMillis(10),
            backoffStrategy = RetryPolicy.BackoffStrategy.FIXED
        )

        // ... 더 많은 설정
    }

    @Test
    fun `should process user event`() {
        // Event mock 생성 (10+ 줄)
        val event = mock<Event>()
        whenever(event.type).thenReturn(EventType.LOGIN)
        whenever(event.time).thenReturn(System.currentTimeMillis())
        whenever(event.realmId).thenReturn("test-realm")
        whenever(event.clientId).thenReturn("test-client")
        whenever(event.userId).thenReturn("test-user")
        whenever(event.sessionId).thenReturn("test-session")
        whenever(event.ipAddress).thenReturn("192.168.1.1")
        whenever(event.details).thenReturn(emptyMap())

        // 테스트 실행
        provider.onEvent(event)

        // 검증
        verify(connectionManager, times(1)).send(any(), any())
    }
}
```

### After (공통 유틸리티 사용)

```kotlin
import org.scriptonbasestar.kcexts.events.common.test.*

class MyEventListenerProviderTest {
    private lateinit var connectionManager: MyConnectionManager

    @BeforeEach
    fun setup() {
        // 3줄로 축약
        connectionManager = MockConnectionManagerFactory.createSuccessful()
        val env = TestConfigurationBuilders.createTestEnvironment()

        // env.circuitBreaker, env.retryPolicy, env.deadLetterQueue 사용
    }

    @Test
    fun `should process user event`() {
        // Event 생성 (1줄)
        val event = KeycloakEventTestFixtures.createUserEvent()

        // 테스트 실행
        provider.onEvent(event)

        // 검증
        verify(connectionManager, times(1)).send(any(), any())
    }
}
```

**코드 감소**: ~50줄 → ~15줄 (약 70% 감소)

---

## 📊 적용 효과

### Before (기존 테스트)
- **NATS 테스트**: 231줄 (mock 생성 코드 50줄 포함)
- **RabbitMQ 테스트**: 296줄 (mock 생성 코드 60줄 포함)
- **중복 코드**: 각 모듈마다 `createMockUserEvent()`, `createMockAdminEvent()` 반복

### After (유틸리티 적용)
- **Mock 생성 코드**: 1-2줄로 축약
- **테스트 가독성**: 크게 향상 (핵심 로직만 집중)
- **유지보수성**: 공통 변경 시 1곳만 수정

---

## 🚀 사용 권장 사항

1. **새로운 Event Listener 모듈 테스트 작성 시**: 이 유틸리티를 적극 활용
2. **기존 테스트 리팩토링**: 점진적으로 공통 유틸리티로 마이그레이션
3. **ExampleRefactoredTest 참조**: 모든 사용 패턴이 포함된 예제 확인

---

## 📚 관련 문서

- [ExampleRefactoredTest.kt](./ExampleRefactoredTest.kt) - 전체 사용 예제
- [Keycloak Event Listener Testing Guide](../../../../../../../docs/testing/event-listener-testing.md) - 추가 테스트 가이드 (예정)

---

**작성일**: 2025-01-06
**버전**: 1.0.0
**작성자**: Claude Code (Sonnet 4.5)
