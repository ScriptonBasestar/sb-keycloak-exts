# Manager 클래스명 통일 리팩토링 가이드

> **작업 일자**: 2025-11-06
> **상태**: 진행 중 (Common 인터페이스 및 Kafka 완료)
> **예상 소요**: 3-4시간 (나머지 5개 모듈)

---

## ✅ 완료된 작업

### 1. Common 모듈: EventConnectionManager 인터페이스 추가

**파일**: [events/event-listener-common/src/main/kotlin/org/scriptonbasestar/kcexts/events/common/connection/EventConnectionManager.kt](event-listener-common/src/main/kotlin/org/scriptonbasestar/kcexts/events/common/connection/EventConnectionManager.kt)

```kotlin
interface EventConnectionManager {
    fun send(destination: String, message: String): Boolean
    fun isConnected(): Boolean
    fun close()
}
```

### 2. Kafka 모듈: KafkaConnectionManager 생성

**파일**: [events/event-listener-kafka/src/main/kotlin/org/scriptonbasestar/kcexts/events/kafka/KafkaConnectionManager.kt](event-listener-kafka/src/main/kotlin/org/scriptonbasestar/kcexts/events/kafka/KafkaConnectionManager.kt)

- ✅ `EventConnectionManager` 인터페이스 구현
- ✅ 기존 `sendEvent()` 메서드 유지 (backward compatibility)
- ✅ 새로운 `send()` 표준 메서드 추가
- ⚠️ **아직 미완료**: Provider, Factory에서 사용하는 부분 업데이트 필요

---

## 🚧 남은 작업

### Phase 1: 각 모듈의 ConnectionManager 클래스 생성 (2시간)

#### A. Azure (1개 파일 생성)

**현재**: `AzureServiceBusSender`
**목표**: `AzureConnectionManager implements EventConnectionManager`

**작업**:
1. 새 파일 생성: `events/event-listener-azure/src/main/kotlin/org/scriptonbasestar/kcexts/events/azure/AzureConnectionManager.kt`
2. 기존 `AzureServiceBusSender` 로직 복사
3. `EventConnectionManager` 인터페이스 구현
4. 표준 `send()` 메서드 추가:
   ```kotlin
   override fun send(destination: String, message: String): Boolean {
       // destination 파싱: "queue:name" or "topic:name"
       if (destination.startsWith("queue:")) {
           sendToQueue(destination.removePrefix("queue:"), message, emptyMap())
       } else {
           sendToTopic(destination.removePrefix("topic:"), message, emptyMap())
       }
       return true
   }
   ```

#### B. Redis (1개 파일 생성)

**현재**: `RedisStreamProducer`
**목표**: `RedisConnectionManager implements EventConnectionManager`

**파일 위치**: `events/event-listener-redis/src/main/kotlin/org/scriptonbasestar/kcexts/events/redis/RedisConnectionManager.kt`

**작업**:
```kotlin
class RedisConnectionManager(config: RedisEventListenerConfig) : EventConnectionManager {
    // 기존 RedisStreamProducer 로직

    override fun send(destination: String, message: String): Boolean {
        // destination = stream key
        val fields = mapOf("message" to message)
        return sendEvent(destination, fields) != null
    }
}
```

#### C. AWS (1개 파일 생성)

**현재**: `AwsMessagePublisher`
**목표**: `AwsConnectionManager implements EventConnectionManager`

**파일 위치**: `events/event-listener-aws/src/main/kotlin/org/scriptonbasestar/kcexts/events/aws/AwsConnectionManager.kt`

**작업**:
```kotlin
class AwsConnectionManager(config: AwsEventListenerConfig) : EventConnectionManager {
    // 기존 AwsMessagePublisher 로직

    override fun send(destination: String, message: String): Boolean {
        // destination 파싱: "sqs:url" or "sns:arn"
        if (destination.startsWith("sqs:")) {
            publishToSqs(destination.removePrefix("sqs:"), message, emptyMap())
        } else {
            publishToSns(destination.removePrefix("sns:"), message, emptyMap())
        }
        return true
    }
}
```

#### D. NATS (변경 불필요!)

**현재**: `NatsConnectionManager` ✅
**상태**: 이미 표준에 가까움

**권장 작업**:
- `EventConnectionManager` 인터페이스 구현 추가만 하면 됨
- `publish()` → `send()` 메서드명 변경 또는 래퍼 추가

#### E. RabbitMQ (변경 불필요!)

**현재**: `RabbitMQConnectionManager` ✅
**상태**: 이미 표준에 가까움

**권장 작업**:
- `EventConnectionManager` 인터페이스 구현 추가만 하면 됨
- `sendMessage()` → `send()` 메서드명 변경 또는 래퍼 추가

---

### Phase 2: Provider/Factory 업데이트 (1.5시간)

각 모듈의 다음 파일들 수정:

#### 1. Factory 클래스
- `KafkaProducerManager` → `KafkaConnectionManager` 변경
- `AzureServiceBusSender` → `AzureConnectionManager` 변경
- 등등...

**예시 (KafkaEventListenerProviderFactory.kt)**:
```kotlin
// Before
private val producerManagers = ConcurrentHashMap<String, KafkaProducerManager>()

private fun getOrCreateProducerManager(config: KafkaEventListenerConfig): KafkaProducerManager {
    return producerManagers.computeIfAbsent(key) {
        KafkaProducerManager(config)
    }
}

// After
private val connectionManagers = ConcurrentHashMap<String, KafkaConnectionManager>()

private fun getOrCreateConnectionManager(config: KafkaEventListenerConfig): KafkaConnectionManager {
    return connectionManagers.computeIfAbsent(key) {
        KafkaConnectionManager(config)
    }
}
```

#### 2. Provider 클래스
- 필드명 변경: `producerManager` → `connectionManager`
- 생성자 파라미터 타입 변경
- 메서드 호출 부분 확인 (backward compatibility 유지)

**예시 (KafkaEventListenerProvider.kt)**:
```kotlin
// Before
class KafkaEventListenerProvider(
    private val producerManager: KafkaProducerManager,
    ...
)

// After
class KafkaEventListenerProvider(
    private val connectionManager: KafkaConnectionManager,
    ...
)

// 호출 부분은 기존 메서드 유지되므로 변경 불필요
connectionManager.sendEvent(topic, key, value)  // 기존 호출 그대로
```

---

### Phase 3: 기존 파일 삭제 (30분)

새 클래스가 정상 동작하면 기존 파일 삭제:

```bash
# 삭제할 파일 목록
rm events/event-listener-kafka/src/main/kotlin/.../KafkaProducerManager.kt
rm events/event-listener-azure/src/main/kotlin/.../sender/AzureServiceBusSender.kt
rm events/event-listener-redis/src/main/kotlin/.../producer/RedisStreamProducer.kt
rm events/event-listener-aws/src/main/kotlin/.../publisher/AwsMessagePublisher.kt
```

---

### Phase 4: 테스트 및 문서 업데이트 (1시간)

#### 테스트 수정
- 각 모듈의 테스트 파일에서 클래스명 변경
- Mock 객체 변경
- 테스트 실행 확인

#### README 업데이트
- 클래스명 언급 부분 업데이트
- 예제 코드 업데이트

#### 변경사항 커밋
```bash
git add events/
git commit -m "refactor(sonnet): standardize Manager class names to ConnectionManager

- Added EventConnectionManager interface in common module
- Renamed classes:
  - KafkaProducerManager → KafkaConnectionManager
  - AzureServiceBusSender → AzureConnectionManager
  - RedisStreamProducer → RedisConnectionManager
  - AwsMessagePublisher → AwsConnectionManager
  - NATS/RabbitMQ: Added interface implementation

All classes now implement EventConnectionManager with standard send() method.
Backward compatibility maintained through existing methods.

Issue: P1 - Critical
Consistency Score: 65/100 → 85/100 (+20)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 📝 체크리스트

### Common 모듈
- [x] EventConnectionManager 인터페이스 생성
- [x] ConnectionException 정의

### Kafka
- [x] KafkaConnectionManager 생성
- [ ] KafkaEventListenerProvider 업데이트
- [ ] KafkaEventListenerProviderFactory 업데이트
- [ ] KafkaProducerManager 삭제
- [ ] 테스트 수정

### Azure
- [ ] AzureConnectionManager 생성
- [ ] AzureEventListenerProvider 업데이트
- [ ] AzureEventListenerProviderFactory 업데이트
- [ ] sender/AzureServiceBusSender 삭제
- [ ] 테스트 수정

### NATS
- [ ] NatsConnectionManager에 EventConnectionManager 구현 추가
- [ ] publish() → send() 래퍼 추가
- [ ] 테스트 확인

### RabbitMQ
- [ ] RabbitMQConnectionManager에 EventConnectionManager 구현 추가
- [ ] sendMessage() → send() 래퍼 추가
- [ ] 테스트 확인

### Redis
- [ ] RedisConnectionManager 생성
- [ ] RedisEventListenerProvider 업데이트
- [ ] RedisEventListenerProviderFactory 업데이트
- [ ] producer/RedisStreamProducer 삭제
- [ ] 테스트 수정

### AWS
- [ ] AwsConnectionManager 생성
- [ ] AwsEventListenerProvider 업데이트
- [ ] AwsEventListenerProviderFactory 업데이트
- [ ] publisher/AwsMessagePublisher 삭제
- [ ] 테스트 수정

### 문서
- [ ] 각 모듈 README 업데이트
- [ ] CONSISTENCY-REVIEW-COMPLETED.md 업데이트
- [ ] 변경사항 커밋

---

## 🎯 예상 결과

### 일관성 점수 개선
| 항목 | 현재 | 목표 | 개선 |
|------|------|------|------|
| 클래스명 일관성 | 60% | 100% | +40% |
| 전체 일관성 점수 | 65/100 | 85/100 | +20점 |

### 최종 클래스 구조
```
모든 모듈:
- {Transport}ConnectionManager implements EventConnectionManager
  - send(destination, message): Boolean
  - isConnected(): Boolean
  - close()
```

---

## 💡 팁

### 안전한 리팩토링 순서
1. **새 클래스 먼저 생성** (기존 클래스와 공존)
2. **Factory/Provider에서 새 클래스 사용**
3. **테스트 실행 및 검증**
4. **기존 클래스 삭제**

### Backward Compatibility 유지
- 기존 메서드 (`sendEvent()`, `sendMessage()` 등) 삭제하지 않고 유지
- 새로운 `send()` 메서드는 추가로 제공
- 점진적 마이그레이션 가능

### 테스트 전략
```bash
# 각 모듈별로 개별 테스트
./gradlew :events:event-listener-kafka:test
./gradlew :events:event-listener-azure:test
# ...

# 전체 빌드
./gradlew :events:build
```

---

**다음 단계**: Kafka 모듈의 Provider/Factory 업데이트부터 시작하거나, 다른 모듈 우선 진행 가능
