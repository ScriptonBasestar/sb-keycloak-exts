# Events Module AI 검토 프롬프트 모음

이 파일은 Claude AI와 함께 events 모듈 일관성을 검토하기 위한 **실제 사용 가능한 프롬프트**를 제공합니다.

> **참고**: Phase별 검토 체크리스트와 함께 사용하려면 [`00-consistency-review-checklist.md`](./00-consistency-review-checklist.md) 참조

---

## 프롬프트 1: 전체 모듈 구조 비교 (초기 분석)

### 언제 사용하는가?
- 새로운 transport 추가 전 현황 파악
- 일관성 문제 진단
- 리팩토링 계획 수립

### 프롬프트

```
events/ 디렉토리의 다음 7개 모듈을 분석해주세요:
- event-listener-kafka
- event-listener-azure
- event-listener-nats
- event-listener-rabbitmq
- event-listener-redis
- event-listener-aws
- event-listener-common

## 분석 범위

### 1. 디렉토리 구조
각 모듈의 디렉토리 구조를 비교:
- src/main/kotlin의 패키지 계층
- config/, sender/, model/, metrics/ 같은 서브디렉토리 사용 여부
- 테스트 구조 (src/test/, src/integrationTest/)

### 2. 핵심 클래스명 패턴
다음 클래스들이 일관되게 명명되었는지 확인:
- Factory 클래스: {Transport}EventListenerProviderFactory?
- Provider 클래스: {Transport}EventListenerProvider?
- Config 클래스: {Transport}EventListenerConfig?
- Message 클래스: {Transport}EventMessage?
- Manager/Sender/Producer 클래스: 이름 규칙이 일관성 있는가?
- Metrics 클래스: {Transport}EventMetrics?

**특히**: Manager, Producer, Sender, ConnectionManager, Publisher 등
여러 이름이 섞여있는지 확인해주세요.

### 3. 필수 vs 선택 클래스
각 모듈이 다음 클래스들을 가지고 있는지 확인:

필수 (모든 모듈이 가져야):
- EventListenerProviderFactory
- EventListenerProvider 구현
- EventListenerConfig
- EventMessage 모델

선택 (Transport 특화):
- Connection/Producer/Sender 관리자 클래스
- Metrics 클래스
- Exception/Error 클래스

### 4. build.gradle 비교
- 의존성 구조: 공통 vs Transport-specific
- Shadow JAR 설정의 일관성
- 테스트 설정 (integrationTest 정의 여부)

### 5. SPI 등록 (META-INF/services)
- 모든 모듈이 정확한 파일을 가지고 있는가?
- 등록된 Factory FQCN이 정확한가?

## 요청 사항

각 항목별로:
1. **공통점**: 일관되게 구현된 부분 (표로)
2. **차이점**: 모듈별 차이 (이유 포함)
3. **불일치**: 표준화가 필요한 부분 (우선순위 포함)
4. **누락**: 일부 모듈만 가진 특수 기능

마지막으로:
**총평**: 현재 일관성 수준 (1-10 점수) 및 즉시 수정 필요 사항 Top 5

JSON 형식 답변 권장:
{
  "structure_consistency": { score, details },
  "class_naming": { score, inconsistencies },
  "required_classes": { present, missing },
  "critical_issues": [ { issue, impact, priority } ]
}
```

---

## 프롬프트 2: Config 로딩 패턴 분석 및 표준화

### 언제 사용하는가?
- Config 클래스 리팩토링 시
- 새 설정 항목 추가 시
- 설정 문서 작성 시

### 프롬프트

```
event-listener-* 모듈들의 Config 클래스 로딩 패턴을 분석해주세요.

## 현재 구현 비교

각 모듈의 다음을 확인:

1. **Config 생성 위치**
   - Factory.create() 메서드 내?
   - Factory의 별도 메서드?
   - Config 팩토리 메서드?

2. **Config 로딩 순서** (우선순위)
   Keycloak의 표준 설정 우선순위:
   1. Realm Attributes (realm-level 설정)
   2. System Properties (JVM -D flag)
   3. Environment Variables
   4. Default values

   각 모듈이 이 순서를 정확히 따르는가?

3. **코드 예시**
   각 모듈의 Config 클래스 로딩 로직을 보여주세요.
   특히:
   - realm.getAttribute() vs realm.getAttribute("key") 호출
   - System.getProperty() 호출
   - System.getenv() 호출
   - 기본값 처리

## 분석 요청

### 패턴 분류
현재 구현을 다음처럼 분류:
- **패턴 A**: Factory.create()에서 직접 생성
- **패턴 B**: Factory 팩토리 메서드 사용
- **패턴 C**: Provider 생성자에서 로드
- (기타)

### 문제점 식별
1. 각 패턴의 장단점
2. Realm vs System vs Env 우선순위가 올바른 모듈?
3. 설정 검증 (필수값 확인)이 있는 모듈?
4. 설정 캐싱 방식의 차이?

### 표준안 제시
다음 중 가장 적합한 표준을 추천해주세요:

옵션 1: "팩토리 메서드 기반"
```kotlin
// 권장 구조
object {Transport}EventListenerConfig {
    fun from(realm: RealmModel): {Transport}EventListenerConfig {
        val key = realm.getAttribute("kafka.bootstrap.servers")
            ?: System.getProperty("kafka.bootstrap.servers")
            ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
            ?: "localhost:9092"
        return {Transport}EventListenerConfig(key, ...)
    }
}
```

옵션 2: "생성자 기반"
```kotlin
class {Transport}EventListenerConfig(realm: RealmModel) {
    val bootstrapServers = loadFromPriority("bootstrap.servers", ...)
}
```

옵션 3: "Factory 분리"
```kotlin
class {Transport}ConfigFactory(realm: RealmModel) {
    fun createConfig(): {Transport}EventListenerConfig { ... }
}
```

### 최종 요청

1. **추천 패턴**: 어느 옵션이 가장 일관성 있고 유지보수하기 쉬운가?
2. **필수 설정**: 모든 모듈이 공통으로 로드해야 할 설정?
3. **검증 로직**: Common 모듈에서 제공할 공통 검증?
4. **마이그레이션 계획**: 현재 코드를 표준으로 변환하는 단계?
```

---

## 프롬프트 3: Manager/Sender/Producer 클래스 표준화

### 언제 사용하는가?
- 클래스명 통일 시
- 새 transport 추가 시
- 리팩토링 계획 수립 시

### 프롬프트

```
event-listener-* 모듈의 "연결/메시지 관리자" 클래스들을 분석해주세요.

## 현재 상황 (불일치 확인됨)

각 모듈에서 메시지 전송을 담당하는 클래스:

| 모듈 | 클래스명 | 책임 |
|------|---------|------|
| Kafka | KafkaProducerManager | 생산자 관리 |
| Azure | AzureServiceBusSender | 직접 전송 |
| NATS | NatsConnectionManager | 연결 관리 |
| RabbitMQ | RabbitMQConnectionManager | 연결 관리 |
| Redis | RedisConnectionManager | 연결 관리 |
| AWS | AwsEventPublisher + AwsMessageProducer | 역할 분리 |
| Common | (기본 구현 없음) | |

## 문제점

1. **이름 규칙 불일치**
   - Manager vs Sender vs Producer vs Publisher
   - 같은 역할을 다르게 명명

2. **책임 범위 불명확**
   - ConnectionManager: 연결 생명주기만?
   - Producer: 메시지 생성 + 전송?
   - Sender: 메시지 전송만?

3. **공통 인터페이스 부재**
   - 각 클래스가 독립적으로 구현
   - 다형성 활용 불가

## 분석 요청

### 1. 각 모듈의 책임 분석

각 클래스의 메서드들을 나열하고:
- open() / close() - 연결 생명주기
- connect() / disconnect() - 연결 상태
- send() / sendAsync() - 메시지 전송
- retry() - 재시도 로직
- (기타 특화 메서드)

### 2. 책임 패턴 분류

현재 구현이 다음 중 어느 패턴을 따르는가?

패턴 1: **ConnectionManager**
```kotlin
interface ConnectionManager {
    fun open()
    fun close()
    fun isConnected(): Boolean
}
```

패턴 2: **MessageSender**
```kotlin
interface MessageSender {
    fun send(message: EventMessage): Result
    fun sendAsync(message: EventMessage): CompletableFuture<Result>
}
```

패턴 3: **Producer**
```kotlin
interface EventProducer {
    fun produce(event: Event): EventMessage
    fun send(message: EventMessage): Result
    fun close()
}
```

패턴 4: **분리 구조**
```kotlin
interface ConnectionManager { ... }
interface MessageSender { ... }
// 한 클래스가 둘 다 구현?
```

### 3. 표준안 결정

다음 옵션 중 추천:

**옵션 A: 통합된 Manager**
```kotlin
interface {Transport}Manager {
    fun open()
    fun close()
    fun send(message: EventMessage): Result

    // 필요시
    fun sendAsync(message: EventMessage): CompletableFuture<Result>
    fun retry(message: EventMessage, attempt: Int): Result
}

class KafkaManager(config: KafkaConfig) : {Transport}Manager { ... }
class AzureManager(config: AzureConfig) : {Transport}Manager { ... }
```

**옵션 B: 분리 구조**
```kotlin
interface {Transport}Connection {
    fun open()
    fun close()
    fun isConnected(): Boolean
}

interface {Transport}Sender {
    fun send(message: EventMessage): Result
    fun sendAsync(message: EventMessage): CompletableFuture<Result>
}

class {Transport}ConnectionManager : {Transport}Connection { ... }
class {Transport}MessageSender : {Transport}Sender { ... }
```

**옵션 C: Adapter 패턴**
```kotlin
// 각 transport별 구현 (다양한 내부 구조 허용)
// 단, EventListenerProvider가 사용하는 공통 인터페이스 제공

interface EventMessageSender {
    fun send(event: Event): Boolean  // EventListenerProvider가 호출
    fun close()
}
```

### 4. Common 모듈의 역할

다음을 Common에서 제공할지 결정:
- [ ] 추상 기본 클래스 (AbstractConnectionManager)
- [ ] 공통 인터페이스 (EventMessageSender)
- [ ] 공통 설정 클래스
- [ ] 재시도/에러 처리 유틸리티

### 최종 요청

1. **추천 옵션**: A, B, C 중 현재 구현에 가장 잘 맞는 것?
2. **인터페이스 정의**: {Transport}Manager의 최종 메서드 시그니처?
3. **공통 클래스**: Common 모듈에서 제공할 내용?
4. **마이그레이션**: 기존 KafkaProducerManager, AzureServiceBusSender 등을 표준으로 변환하는 단계?

```

---

## 프롬프트 4: 디렉토리 구조 표준화

### 언제 사용하는가?
- 새 transport 추가 전 구조 정의
- 코드 정리/리팩토링 시
- 개발자 가이드 작성 시

### 프롬프트

```
event-listener-* 모듈의 디렉토리 구조를 표준화해주세요.

## 현재 상황

모듈별로 src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/에서:

| 구조 | Kafka | Azure | NATS | RabbitMQ | Redis | AWS |
|------|-------|-------|------|----------|-------|-----|
| 루트 레벨 클래스 | Y | Y | Y | Y | Y | Y |
| config/ | N | Y | N | N | N | Y |
| sender/ | N | Y | N | N | N | N |
| model/ | N | N | N | N | N | N |
| metrics/ | Y | Y | Y | Y | Y | Y |

(Y = 있음, N = 없음)

## 문제점

1. **서브디렉토리 사용 불일치**
   - 대부분은 클래스를 루트에 배치
   - 일부는 config/, sender/ 서브디렉토리 사용
   - 새 개발자는 어디에 파일을 만들어야 할지 불명확

2. **metrics/ 서브디렉토리는 공통**
   - 모두 metrics/ 사용 ✓
   - 이것을 표준으로 삼을 수 있음

## 제안 구조

### 옵션 1: 최소한 구조 (현재 Kafka 방식)
```
src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
├── {Transport}EventListenerProviderFactory.kt
├── {Transport}EventListenerProvider.kt
├── {Transport}EventListenerConfig.kt
├── {Transport}EventMessage.kt
├── {Transport}ConnectionManager.kt (또는 유사)
├── {Transport}Exception.kt (필요시)
└── metrics/
    └── {Transport}EventMetrics.kt
```

**장점**: 간단, 파일 개수 적음
**단점**: 클래스가 많아지면 구분 어려움

### 옵션 2: 기능별 구조 (현재 Azure 방식)
```
src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
├── {Transport}EventListenerProviderFactory.kt
├── {Transport}EventListenerProvider.kt
├── config/
│   └── {Transport}EventListenerConfig.kt
├── model/
│   ├── {Transport}EventMessage.kt
│   └── (기타 도메인 모델)
├── sender/
│   └── {Transport}ConnectionManager.kt
├── exception/
│   └── {Transport}Exception.kt
└── metrics/
    └── {Transport}EventMetrics.kt
```

**장점**: 명확한 구조, 확장성 좋음
**단점**: 간단한 transport에는 과도할 수 있음

### 옵션 3: 하이브리드 (권장)
```
src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
├── {Transport}EventListenerProviderFactory.kt
├── {Transport}EventListenerProvider.kt
├── {Transport}EventListenerConfig.kt
├── {Transport}EventMessage.kt
├── config/ (필요시만)
│   └── (추가 설정 클래스)
├── metrics/
│   └── {Transport}EventMetrics.kt
└── (기타 필요시 서브디렉토리)
```

**규칙**:
- 필수 4개 클래스는 루트 (Factory, Provider, Config, Message)
- metrics/는 항상 존재
- ConnectionManager는 루트 또는 필요시 sender/
- 추가 클래스는 기능별 서브디렉토리 (config/, exception/ 등)

## 테스트 구조 (공통)

모든 모듈이 다음을 따라야:
```
src/test/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
├── {Transport}EventListenerProviderTest.kt
├── {Transport}EventListenerConfigTest.kt
├── {Transport}EventMessageTest.kt (필요시)
├── metrics/
│   └── {Transport}EventMetricsTest.kt
└── (기타 테스트)

src/integrationTest/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
├── {Transport}EventListenerIntegrationTest.kt
└── testcontainers/ (TestContainers 설정)
    └── {Transport}TestContainer.kt (필요시)
```

## 요청 사항

1. **구조 선택**: 옵션 1, 2, 3 중 추천?
2. **상세 규칙**: 어떤 클래스는 어느 디렉토리에 배치?
3. **예외 케이스**: AWS처럼 여러 클래스가 필요한 경우 구조?
4. **마이그레이션 계획**: 기존 모듈들을 표준으로 변환하는 순서?

```

---

## 프롬프트 5: README.md 표준화

### 언제 사용하는가?
- 문서 구조 통일 시
- 사용자 문서 개선 시
- 새 transport 추가 시

### 프롬프트

```
event-listener-* 모듈의 README.md를 표준화해주세요.

## 현재 상황

각 모듈의 README 구조와 내용을 비교하고,
다음을 확인:

1. **공통 섹션**: 모든 README에 있어야 하는가?
2. **문서 품질**: 내용 완성도, 최신 정보, 예제 포함도
3. **설정 문서**: 필수/선택 설정 명시, 기본값 제공
4. **모니터링**: Prometheus 메트릭, Health Check 문서

## 권장 구조

### 1. Overview (소개)
- 모듈의 목적
- 대상 사용자
- 주요 특징

### 2. Features (특징)
- Transport별 고유 특징
- Resilience 패턴 (Circuit Breaker, Retry, DLQ 등)
- 성능 특성

### 3. Configuration (설정)
**필수 설정**:
- 설정명: bootstrap.servers, host, connection.string 등
- 필수 여부
- 기본값
- 설명
- 예제값

표 형식:
| 설정명 | 필수 | 기본값 | 설명 |
|-------|------|-------|------|
| kafka.bootstrap.servers | Y | - | Kafka broker 주소 |
| kafka.event.topic | N | keycloak.events | 이벤트 토픽명 |

**설정 로딩 순서**:
```
1. Realm Attributes (highest)
2. System Properties (-D flag)
3. Environment Variables
4. Default values (lowest)

예: kafka.bootstrap.servers
→ realm.getAttribute("kafka.bootstrap.servers")
→ System.getProperty("kafka.bootstrap.servers")
→ System.getenv("KAFKA_BOOTSTRAP_SERVERS")
→ "localhost:9092"
```

**Realm 설정 예제**:
```
{realm의 Attributes에 추가:
- kafka.bootstrap.servers: localhost:9092
- kafka.event.topic: keycloak.events
- kafka.compression.type: gzip
}
```

### 4. Usage / Setup (사용법)
- Docker 설정 예제
- Realm 초기화 스크립트
- JAR 배포

### 5. Monitoring (모니터링)
**Prometheus 메트릭**:
```
keycloak.events.sent{transport="kafka", event_type="LOGIN"}
keycloak.events.failed{transport="kafka", error_type="KafkaException"}
keycloak.events.duration_ms{transport="kafka"}
```

**Health Check** (있으면):
```
GET /health/kafka
```

**로그 레벨**:
```
org.scriptonbasestar.kcexts.events.kafka: DEBUG
```

### 6. Performance Tuning (선택)
- 배치 크기, 타임아웃 최적화
- 특정 transport에 맞는 튜닝

### 7. Troubleshooting (문제해결)
- 일반적인 오류 및 해결책
- 로그 분석 방법

### 8. Examples (예제)
- Docker Compose 설정
- 모니터링 대시보드 설정

## 요청 사항

1. **표준 구조 확정**: 위 8개 섹션을 모두 포함해야 하는가?
2. **각 섹션 가이드**: 각 섹션의 상세 작성 가이드라인?
3. **표 형식**: 설정 문서의 표 형식 표준화?
4. **예제 제공**: 각 transport별 완성된 예제 README?

```

---

## 프롬프트 6: 테스트 커버리지 개선

### 언제 사용하는가?
- 새 transport 추가 시
- 테스트 커버리지 부족한 모듈 개선 시
- CI/CD 품질 기준 설정 시

### 프롬프트

```
event-listener-* 모듈의 테스트 커버리지를 분석하고 개선 계획을 세워주세요.

## 현재 상황

테스트 현황:
| 모듈 | Unit Tests | Integration Tests | 예상 커버리지 |
|------|------------|------------------|-------------|
| Kafka | Y | Y | 높음 |
| Azure | N | N | 매우 낮음 |
| NATS | Y | N | 낮음 |
| RabbitMQ | Y | N | 중간 |
| Redis | N | N | 매우 낮음 |
| AWS | Y | N | 낮음 |
| Common | ? | ? | ? |

## 권장 테스트 구조

### Unit Tests (src/test/kotlin/)

모든 모듈이 최소한 이정도는 작성:

1. **Factory 테스트**
```kotlin
class {Transport}EventListenerProviderFactoryTest {
    fun testFactory_CreateProvider()
    fun testFactory_GetId()
    fun testFactory_GetConfigProperties()
}
```

2. **Config 테스트**
```kotlin
class {Transport}EventListenerConfigTest {
    fun testConfig_LoadFromRealm()
    fun testConfig_LoadFromSystemProperty()
    fun testConfig_LoadFromEnvironment()
    fun testConfig_Defaults()
    fun testConfig_Validation_MissingRequired()
}
```

3. **Provider 테스트** (mocked dependencies)
```kotlin
class {Transport}EventListenerProviderTest {
    fun testProvider_OnEvent()
    fun testProvider_OnAdminEvent()
    fun testProvider_Close()
    fun testProvider_ErrorHandling()
}
```

4. **Metrics 테스트**
```kotlin
class {Transport}EventMetricsTest {
    fun testMetrics_CountSent()
    fun testMetrics_CountFailed()
    fun testMetrics_TimerDuration()
}
```

### Integration Tests (src/integrationTest/kotlin/)

필수 (TestContainers 사용):

```kotlin
class {Transport}EventListenerIntegrationTest {
    // TestContainers setup
    fun testIntegration_EventSentToTransport()
    fun testIntegration_EventRetrievedFromQueue()
    fun testIntegration_Reconnection()
    fun testIntegration_HighThroughput() (성능 테스트)
}
```

## 요청 사항

1. **Unit Tests 기본 세트**: 모든 모듈이 작성해야 할 최소 테스트?
2. **Integration Tests**: TestContainers 설정 및 예제?
3. **Mock 전략**: Unit tests에서 의존성을 mock하는 방식?
4. **CI 기준**: 최소 커버리지 기준 (예: 70%)?

```

---

## 프롬프트 7: 새로운 Transport 추가 가이드

### 언제 사용하는가?
- 새 transport 추가 시
- 템플릿/보일러플레이트 생성 시
- 개발 가이드 작성 시

### 프롬프트

```
event-listener-{새로운-transport} 모듈 생성 가이드를 작성해주세요.

## 요구사항

새로운 transport (예: event-listener-pubsub)를 추가할 때
일관성 있게 구현하기 위한 단계별 가이드.

## Step 1: 프로젝트 구조 생성

```
event-listener-{transport}/
├── build.gradle
├── README.md
├── src/
│   ├── main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
│   │   ├── {Transport}EventListenerProviderFactory.kt
│   │   ├── {Transport}EventListenerProvider.kt
│   │   ├── {Transport}EventListenerConfig.kt
│   │   ├── {Transport}EventMessage.kt
│   │   ├── {Transport}ConnectionManager.kt
│   │   ├── exception/
│   │   │   └── {Transport}Exception.kt (필요시)
│   │   └── metrics/
│   │       └── {Transport}EventMetrics.kt
│   ├── main/resources/
│   │   └── META-INF/services/
│   │       └── org.keycloak.events.EventListenerProviderFactory
│   ├── test/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
│   │   ├── {Transport}EventListenerProviderFactoryTest.kt
│   │   ├── {Transport}EventListenerProviderTest.kt
│   │   ├── {Transport}EventListenerConfigTest.kt
│   │   └── metrics/
│   │       └── {Transport}EventMetricsTest.kt
│   └── integrationTest/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
│       └── {Transport}EventListenerIntegrationTest.kt
```

## Step 2: build.gradle 템플릿

다음을 포함해야 함:
- Transport 라이브러리 의존성
- event-listener-common 의존성
- Shadow JAR 설정
- integrationTest 소스셋
- 테스트 의존성

```gradle
dependencies {
    // Keycloak SPI (provided)
    compileOnly "org.keycloak:keycloak-core:${keycloakVersion}"
    compileOnly "org.keycloak:keycloak-server-spi-private:${keycloakVersion}"

    // Common module
    implementation project(":events:event-listener-common")

    // Transport-specific
    implementation "com.pubsub:google-cloud-pubsub:${pubsubVersion}"

    // Testing
    testImplementation "org.junit.jupiter:junit-jupiter:${junitVersion}"
    testImplementation "io.mockk:mockk:${mockkVersion}"

    integrationTestImplementation "org.testcontainers:testcontainers:${testcontainersVersion}"
}

shadowJar {
    include(dependency('com.pubsub:google-cloud-pubsub'))
}
```

## Step 3: 필수 클래스 구현

### 3.1 Factory (SPI 진입점)
```kotlin
class {Transport}EventListenerProviderFactory : EventListenerProviderFactory {
    override fun id(): String = "{transport}"

    override fun create(session: KeycloakSession): EventListenerProvider {
        // Config 로드
        // ConnectionManager 생성
        // Provider 반환
    }

    override fun getConfigProperties(): Map<String, String> {
        // 필수 설정 명시
    }
}
```

### 3.2 Provider (핵심 로직)
```kotlin
class {Transport}EventListenerProvider(
    private val config: {Transport}EventListenerConfig,
    private val connectionManager: {Transport}ConnectionManager,
    private val metrics: {Transport}EventMetrics
) : EventListenerProvider {

    override fun onEvent(event: Event) {
        // 1. Event → Message 변환
        // 2. 메트릭 기록
        // 3. 전송
        // 4. 에러 처리
    }

    override fun onAdminEvent(event: AdminEvent, includeRepresentation: Boolean) {
        // 마찬가지
    }

    override fun close() {
        connectionManager.close()
    }
}
```

### 3.3 Config (설정 로드)
```kotlin
class {Transport}EventListenerConfig(
    realm: RealmModel
) {
    val bootstrapServers = realm.getAttribute("kafka.bootstrap.servers")
        ?: System.getProperty("kafka.bootstrap.servers")
        ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
        ?: "localhost:9092"

    // (기타 설정들)

    init {
        // 필수 설정 검증
        require(bootstrapServers.isNotEmpty()) { "Missing bootstrapServers" }
    }
}
```

### 3.4 Message Model (직렬화)
```kotlin
data class {Transport}EventMessage(
    val id: String,
    val time: Long,
    val type: String,
    val realmId: String,
    val clientId: String?,
    val userId: String?,
    val details: Map<String, String>?
) {
    companion object {
        fun from(event: Event): {Transport}EventMessage { ... }
    }
}
```

### 3.5 ConnectionManager (연결 관리)
```kotlin
class {Transport}ConnectionManager(config: {Transport}EventListenerConfig) {
    fun open() { ... }
    fun close() { ... }
    fun send(message: {Transport}EventMessage): Boolean { ... }
}
```

### 3.6 Metrics (모니터링)
```kotlin
class {Transport}EventMetrics(registry: MeterRegistry) {
    private val sentCounter = Counter.builder("keycloak.events.sent")
        .tag("transport", "kafka")
        .register(registry)

    fun recordSent() { sentCounter.increment() }
    // (기타 메트릭들)
}
```

## Step 4: SPI 등록

META-INF/services 파일 작성:
```
# src/main/resources/META-INF/services/
# org.keycloak.events.EventListenerProviderFactory

org.scriptonbasestar.kcexts.events.{transport}.{Transport}EventListenerProviderFactory
```

## Step 5: 테스트 작성

- Unit tests: Config, Message, Metrics 테스트
- Integration tests: TestContainers + 실제 전송

## Step 6: 문서 작성

README.md:
- Overview, Features
- Configuration 테이블
- 사용 예제
- Troubleshooting

## 체크리스트

- [ ] 디렉토리 구조 표준 준수
- [ ] 클래스명 패턴 일관성
- [ ] SPI 등록 파일 생성
- [ ] build.gradle 작성
- [ ] 필수 클래스 6개 구현
- [ ] 테스트 작성
- [ ] README.md 표준 구조
- [ ] CI 통과 확인

```

---

## 빠른 사용 가이드

### 초기 분석 (15분)
```bash
프롬프트 1: 전체 모듈 구조 비교
→ 현황 파악, 불일치 식별
```

### 리팩토링 계획 수립 (30분)
```bash
프롬프트 2: Config 로딩 패턴
프롬프트 3: Manager/Sender 표준화
프롬프트 4: 디렉토리 구조
→ 우선순위별 개선 계획 도출
```

### 신규 모듈 추가 (2시간)
```bash
프롬프트 7: 새로운 Transport 추가 가이드
→ 단계별 구현, 검증
```

### 정기 검수 (1시간/월)
```bash
프롬프트 1: 전체 검토 (변경 사항 확인)
프롬프트 6: 테스트 커버리지 확인
→ 지속적 개선
```

---

## 프롬프트 사용 팁

1. **명확한 맥락 제공**: 모듈별로 분석 대상을 명확히
2. **비교 요청**: "다른 모듈과 비교하면?" 형태로 질문
3. **표 형식 요청**: JSON, 테이블로 구조화된 응답 받기
4. **구체적 권장**: "옵션 A, B, C 중 추천?" 형태로 결정 받기
5. **마이그레이션 계획**: "어떻게 현재 코드를 변환하는가?" 단계별 요청
