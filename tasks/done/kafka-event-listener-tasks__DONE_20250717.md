# Kafka Event Listener 구현 작업 목록

## 진행 상황
- ✅ **완료**: events 모듈 디렉토리 구조 생성
- ✅ **완료**: Phase 3 - Kafka Event Listener 구현
- ✅ **완료**: 6개 작업 모두 완료

## 남은 작업 목록

### 1. KafkaEventListenerProvider 핵심 클래스 구현 ✅
**ID**: `96b568c6-621e-4604-a1ae-ce81714dec27`  
**상태**: 완료  
**의존성**: 없음

**설명**: EventListenerProvider 인터페이스를 구현하여 Keycloak 이벤트를 캡처하고 Kafka로 전송하는 핵심 로직 구현

**구현 가이드**:
```kotlin
class KafkaEventListenerProvider(
    private val session: KeycloakSession,
    private val config: KafkaEventListenerConfig,
    private val producerManager: KafkaProducerManager
) : EventListenerProvider {
    
    override fun onEvent(event: Event) {
        // 1. 이벤트 필터링 (config의 includedEventTypes 확인)
        // 2. KeycloakEvent 모델로 변환
        // 3. Jackson으로 JSON 직렬화
        // 4. Kafka로 비동기 발행
    }
    
    override fun onEvent(event: AdminEvent, includeRepresentation: Boolean) {
        // AdminEvent 처리 로직
    }
    
    override fun close() {
        // 리소스 정리
    }
}
```

**검증 기준**:
- EventListenerProvider 인터페이스 모든 메서드 구현
- 이벤트 필터링 로직 동작
- JSON 직렬화 성공
- Kafka 발행 로직 포함

---

### 2. KafkaEventListenerProviderFactory 구현 ✅ 
**ID**: `c83f3d35-c6c9-4f34-986d-3a87463c204a`  
**상태**: 완료  
**의존성**: Task 1 (KafkaEventListenerProvider)

**설명**: Provider 인스턴스를 생성하고 관리하는 Factory 클래스 구현. Keycloak SPI 시스템과 통합을 담당

**구현 가이드**:
```kotlin
class KafkaEventListenerProviderFactory : EventListenerProviderFactory {
    private var producerManager: KafkaProducerManager? = null
    
    override fun create(session: KeycloakSession): EventListenerProvider {
        val config = KafkaEventListenerConfig(session)
        return KafkaEventListenerProvider(session, config, getProducerManager(config))
    }
    
    override fun init(config: Config.Scope) {
        // Kafka 설정 초기화
    }
    
    override fun postInit(factory: KeycloakSessionFactory) {
        // 추가 초기화 로직
    }
    
    override fun close() {
        producerManager?.close()
    }
    
    override fun getId(): String = "kafka-event-listener"
}
```

**검증 기준**:
- EventListenerProviderFactory 인터페이스 구현
- Provider 인스턴스 생성 성공
- 설정 초기화 로직 동작
- 리소스 정리 메서드 구현

---

### 3. Kafka 설정 및 Producer 관리 구현 ✅
**ID**: `36e23ca0-0e83-4ba0-bd19-171a8cb37622`  
**상태**: 완료  
**의존성**: 없음

**설명**: KafkaEventListenerConfig 클래스로 설정 관리, KafkaProducerManager로 Kafka Producer 라이프사이클 관리

**구현 가이드**:
```kotlin
// KafkaEventListenerConfig.kt
class KafkaEventListenerConfig(session: KeycloakSession) {
    val bootstrapServers: String
    val eventTopic: String
    val adminEventTopic: String
    val clientId: String
    val includedEventTypes: Set<EventType>
    // Keycloak realm 설정에서 읽기
}

// KafkaProducerManager.kt  
class KafkaProducerManager(config: KafkaEventListenerConfig) {
    private val producer: KafkaProducer<String, String>
    
    fun sendEvent(topic: String, key: String, value: String) {
        producer.send(ProducerRecord(topic, key, value)) { metadata, exception ->
            // 콜백 처리
        }
    }
}
```

**검증 기준**:
- Keycloak 설정에서 값 읽기 성공
- Kafka Producer 초기화 성공
- 비동기 메시지 발행 동작
- 에러 처리 및 재시도 로직

---

### 4. 이벤트 데이터 모델 및 META-INF 설정 ✅
**ID**: `c238326a-0c44-4fe2-a600-cbb6f4e4d210`  
**상태**: 완료  
**의존성**: Task 2 (KafkaEventListenerProviderFactory)

**설명**: Keycloak 이벤트를 표현하는 데이터 모델 클래스 생성 및 SPI 서비스 등록을 위한 META-INF 설정

**구현 가이드**:
```kotlin
// model/KeycloakEvent.kt
data class KeycloakEvent(
    val id: String,
    val time: Long,
    val type: String,
    val realmId: String,
    val clientId: String?,
    val userId: String?,
    val sessionId: String?,
    val ipAddress: String?,
    val details: Map<String, String>?
)

// model/KeycloakAdminEvent.kt
data class KeycloakAdminEvent(
    val id: String,
    val time: Long,
    val operationType: String,
    val realmId: String,
    val authDetails: AuthDetails,
    val resourcePath: String?,
    val representation: String?
)
```

**META-INF 설정**:
- 파일 위치: `META-INF/services/org.keycloak.events.EventListenerProviderFactory`
- 내용: `org.scriptonbasestar.kcexts.events.kafka.KafkaEventListenerProviderFactory`

**검증 기준**:
- 데이터 모델이 Keycloak 이벤트 구조와 일치
- JSON 직렬화 가능
- META-INF 파일 올바른 위치에 생성
- Factory 클래스 경로 정확

---

### 5. 테스트 코드 작성 ✅
**ID**: `df5b2b07-6ee2-4817-88fb-57c639869ef6`  
**상태**: 완료  
**의존성**: Task 1, Task 3

**설명**: 단위 테스트와 통합 테스트 작성. TestContainers를 활용한 Kafka 통합 테스트 포함

**구현 가이드**:
```kotlin
// KafkaEventListenerProviderTest.kt
class KafkaEventListenerProviderTest {
    @Test
    fun `should send event to Kafka when user login event occurs`() {
        // Given: Mock 설정
        // When: onEvent 호출
        // Then: Kafka 메시지 발행 검증
    }
}

// KafkaEventListenerIntegrationTest.kt
@Testcontainers
class KafkaEventListenerIntegrationTest {
    @Container
    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
    
    @Test
    fun `should publish event to Kafka topic`() {
        // TestContainers Kafka로 실제 발행 테스트
    }
}
```

**검증 기준**:
- 모든 public 메서드에 대한 테스트 존재
- 정상/에러 케이스 모두 커버
- TestContainers Kafka 정상 동작
- 테스트 커버리지 80% 이상

---

### 6. README 문서 및 설정 가이드 작성 ✅
**ID**: `cd6832eb-0c2a-4711-b98b-2ce142c3a58e`  
**상태**: 완료  
**의존성**: Task 4 (이벤트 데이터 모델)

**설명**: Kafka Event Listener 설치, 설정, 사용 방법을 설명하는 상세한 문서 작성

**문서 구성**:
1. 개요 및 기능 설명
2. 요구사항 (Keycloak 버전, Kafka 버전)
3. 빌드 방법
4. 설치 방법 (JAR 복사)
5. Keycloak 설정 방법:
   - Realm 설정에서 Event Listener 추가
   - Kafka 연결 파라미터 설정
6. 설정 옵션 상세 설명
7. 이벤트 포맷 예시
8. 문제 해결 가이드
9. 성능 튜닝 팁

**검증 기준**:
- 설치 과정이 명확하게 설명됨
- 모든 설정 옵션 문서화
- 실제 사용 예시 포함
- 트러블슈팅 섹션 포함

## 작업 실행 순서

### 병렬 실행 가능
- Task 1 (Provider 구현)
- Task 3 (Config/Producer 관리)

### 순차 실행 필요
1. Task 1 → Task 2 (Factory)
2. Task 2 → Task 4 (데이터 모델)
3. Task 1 + Task 3 → Task 5 (테스트)
4. Task 4 → Task 6 (문서)

## 다음 단계
모든 작업 완료 후 Phase 4 (TestContainers 테스트 환경 구축)로 진행