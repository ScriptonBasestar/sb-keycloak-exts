# Events Module 일관성 검토 프롬프트 및 체크리스트

## 개요
`events/` 디렉토리의 7개 모듈(Kafka, Azure, NATS, RabbitMQ, Redis, AWS, Common)이 **구조적 일관성**을 유지하도록 검토하기 위한 체계적 프롬프트 및 체크리스트입니다.

---

## Phase 1: 초기 분석 (읽기 전용)

### 1.1 디렉토리 구조 및 파일 조직 검토

**검토 항목:**
```
□ 각 모듈이 다음 표준 구조를 따르는가?
  ├── src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
  │   ├── {Transport}EventListenerProviderFactory.kt
  │   ├── {Transport}EventListenerProvider.kt
  │   ├── {Transport}EventListenerConfig.kt
  │   ├── {Transport}EventMessage.kt
  │   ├── {Transport}{Manager|Producer|Sender}.kt (선택사항)
  │   ├── metrics/{Transport}EventMetrics.kt
  │   ├── config/ (config 클래스들)
  │   ├── sender/ (sender 클래스들)
  │   └── model/ (message 모델들)
  ├── src/test/kotlin/
  ├── src/integrationTest/kotlin/
  ├── build.gradle
  └── README.md

□ 불필요한 파일이나 구조가 있는가?
□ README.md가 모든 모듈에 존재하는가?
```

**프롬프트:**
```
각 event-listener-* 모듈의 디렉토리 구조를 검토하고,
다음을 확인해주세요:

1. 표준 패키지 구조를 따르는가?
   src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/

2. 각 모듈별 내부 구조의 차이점은?
   - config/, sender/, model/ 같은 서브패키지의 사용 유무
   - 클래스 파일들이 루트에 있는지 vs 서브패키지에 있는지

3. 테스트 구조:
   - src/test/kotlin/ 구조는 main과 동일한가?
   - src/integrationTest/kotlin/은 어느 모듈만 가지고 있는가?

4. 비표준 파일/폴더는?
```

---

### 1.2 클래스명 패턴 검토

**검토 항목:**
```
□ Factory 클래스명 패턴 확인
  예: KafkaEventListenerProviderFactory vs AzureEventListenerProviderFactory
  패턴: {Transport}EventListenerProviderFactory ✓

□ Provider 클래스명 패턴
  예: KafkaEventListenerProvider vs AzureEventListenerProvider
  패턴: {Transport}EventListenerProvider ✓

□ Config 클래스명 패턴
  예: KafkaEventListenerConfig vs AzureEventListenerConfig
  패턴: {Transport}EventListenerConfig ✓

□ Message/Event Model 클래스명
  예: KafkaEventMessage vs AzureEventMessage
  패턴: {Transport}EventMessage ✓

□ Manager/Producer/Sender 클래스명 (불일치 주의!)
  - Kafka: KafkaProducerManager ❌ (다른 패턴)
  - Azure: AzureServiceBusSender ❌ (다른 패턴)
  - NATS: NatsConnectionManager ❌ (다른 패턴)
  - RabbitMQ: RabbitMQConnectionManager
  - Redis: RedisConnectionManager
  - AWS: AwsEventPublisher

  ⚠️ 표준화 필요: {Transport}ConnectionManager or {Transport}MessageSender

□ Metrics 클래스명
  패턴: {Transport}EventMetrics ✓

□ Exception/Error 클래스명
  존재 여부 및 패턴 확인
```

**프롬프트:**
```
모든 event-listener-* 모듈의 클래스명을 비교하여:

1. 표준화된 패턴을 확인:
   - Factory: {Transport}EventListenerProviderFactory
   - Provider: {Transport}EventListenerProvider
   - Config: {Transport}EventListenerConfig
   - Message: {Transport}EventMessage

2. 비표준 클래스명 목록:
   - Manager/Producer/Sender 클래스의 명명 규칙이 일관성 있는가?
   - 현재 상황: Manager, Producer, Sender, Publisher, ConnectionManager 혼용
   - 제안: {Transport}ConnectionManager 또는 {Transport}MessageSender로 통일

3. Metrics, Exception, Utility 클래스 명명 규칙

4. 각 모듈별 추가 클래스들의 목적과 패턴
```

---

### 1.3 핵심 클래스 구성 검토

**검토 항목:**
```
모듈별 필수/선택 클래스:

필수:
□ EventListenerProviderFactory (✓ 모두 존재)
□ EventListenerProvider 구현체 (✓ 모두 존재)
□ EventListenerConfig (✓ 모두 존재)
□ EventMessage 모델 (✓ 모두 존재)

선택 (Transport 특화):
□ Manager/Sender/Producer (모두 존재)
□ Metrics (모두 존재)

거의 없음:
□ Exception 클래스 (kafka만 KafkaEventException 등)
□ Health Check (없음)
□ Circuit Breaker (없음)

다양함:
□ Resilience 패턴 (config/resilience/ 파일들)
□ 배치 처리 (일부 모듈만)
```

**프롬프트:**
```
각 event-listener-* 모듈이 다음 클래스들을 가지고 있는지 확인:

필수 구현:
1. {Transport}EventListenerProviderFactory extends EventListenerProviderFactory
2. {Transport}EventListenerProvider extends EventListenerProvider
3. {Transport}EventListenerConfig
4. {Transport}EventMessage (data class)

선택 구현 (Transport 특화):
5. {Transport}ConnectionManager / Sender / Producer
6. {Transport}EventMetrics extends EventMetrics (또는 유사)
7. {Transport}Exception / Error classes

고급 기능 (선택):
8. Resilience 구현 (Circuit Breaker, Retry, DLQ 등)
9. Health Check
10. 배치 처리

각 모듈별로:
- 어떤 클래스들이 존재하는가?
- 같은 용도의 클래스가 이름만 다른가? (표준화 필요)
- 한 모듈만 가진 독특한 클래스는? (정당한가?)
```

---

## Phase 2: 구체적 검토 항목

### 2.1 build.gradle 검토

**검토 항목:**
```
□ 의존성 선언
  - 각 모듈이 transport-specific 의존성만 추가했는가?
  - 공통 의존성(Keycloak, logging, metrics)은 공유되는가?
  - event-listener-common 의존성 포함 여부?

□ Shadow JAR 설정
  - 모든 모듈이 shadowJar block을 가지는가?
  - 포함/제외 규칙이 일관성 있는가?

□ Test 설정
  - integrationTest sourceSet 정의 여부
  - TestContainers 의존성 선택적 포함 여부

□ 플러그인 설정
  - 각 모듈이 동일한 플러그인을 적용하는가?
```

**프롬프트:**
```
모든 event-listener-* 모듈의 build.gradle을 검토:

1. 의존성 구조:
   - Keycloak SPI 버전 일관성
   - Kotlin 버전, Coroutines 버전
   - logging (SLF4J), metrics (Micrometer)
   - Transport 라이브러리 버전

2. Shadow JAR 설정 비교:
   - include() 규칙이 모두 동일한가?
   - mainClassName, manifest 설정이 있는가?

3. 테스트 설정:
   - 어느 모듈이 integrationTest를 정의했는가?
   - TestContainers 사용 여부
   - 테스트 프레임워크 (JUnit, Kotest 등)

4. 플러그인 일관성:
   - 모두 동일한 플러그인을 사용하는가?
   - plugins block 구조가 표준화되어 있는가?
```

---

### 2.2 SPI 등록 및 Factory 검토

**검토 항목:**
```
□ META-INF/services 파일 존재
  - 모든 모듈이 이 파일을 가지는가?
  - 파일 경로: src/main/resources/META-INF/services/

□ 등록된 Factory 클래스명
  - 파일 내용이 올바른 FQCN을 가지는가?
  - 예: org.scriptonbasestar.kcexts.events.kafka.KafkaEventListenerProviderFactory

□ Factory 메서드 구현
  - create() 메서드 반환 타입
  - 의존성 주입 (KeycloakSession, EventListenerProvider)
  - 초기화 로직 (Config 로드 등)
```

**프롬프트:**
```
SPI 등록 및 Factory 패턴 검토:

1. META-INF/services 파일:
   - 위치: src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory
   - 내용이 정확한 FQCN을 포함하는가?

2. Factory 클래스 구현:
   - EventListenerProviderFactory 인터페이스 구현 방식
   - id() 메서드 반환값 (transport 이름)
   - create() 메서드의 의존성 처리
   - getConfigProperties() 정의 여부

3. 각 모듈의 Factory 메서드 구현 차이:
   - Config 로드 방식 (생성자 vs 팩토리 메서드)
   - KeycloakSession 사용 방식
   - Realm Attributes 접근 방식
```

---

### 2.3 Config 클래스 검토

**검토 항목:**
```
□ Config 로딩 패턴
  패턴 1: Factory에서 생성 후 Provider 생성자에 전달
  패턴 2: Factory에서 Config 클래스 반환

  현재:
  - Kafka, Azure, NATS, RabbitMQ, Redis: 생성자 전달 패턴
  - AWS: 팩토리 메서드? (확인 필요)

  ⚠️ 일관성 필요!

□ 설정 소스 우선순위
  - Realm Attributes (highest)
  - System Properties (-D flags)
  - Environment Variables
  - Default values (lowest)

□ 필수 vs 선택 설정
  - 필수 설정 (예: bootstrap servers)
  - 선택 설정 (예: batch size, timeout)

□ 설정 검증
  - 필수 설정 존재 여부 확인
  - 값 유효성 검증 (포트, 크기 등)
```

**프롬프트:**
```
Config 클래스 패턴 검토:

1. 설정 로딩 패턴 통일:
   현재 불일치:
   - 대부분: Factory에서 Config 생성 후 Provider 생성자로 전달
   - 일부: 다른 방식?

   권장: 모두 {Transport}EventListenerConfig 팩토리 메서드 사용

2. 설정 소스 우선순위:
   각 모듈이 동일한 순서를 따르는가?
   1. Realm.getAttribute("key")
   2. System.getProperty("key")
   3. System.getenv("key")
   4. default value

3. 설정 캐싱:
   Config를 캐시하는가? 매번 로드하는가?

4. 필수/선택 설정 명시:
   README에서 명확히 구분되는가?
```

---

### 2.4 EventMessage 모델 검토

**검토 항목:**
```
□ 데이터 클래스 구조
  - Keycloak Event 정보 모두 포함?
  - JSON 직렬화 지원 (Jackson annotations)?

□ 필드 구성
  공통:
  - id, time, type, realmId, clientId, userId, sessionId
  - ipAddress, details, representation

  선택:
  - batch ID, correlation ID (선택적)

□ Serialization 라이브러리
  - Jackson (@JsonProperty 등)
  - Kotlinx Serialization
  - Custom serialization

□ 필터링/변환
  - Sensitive 필드 제거
  - 형식 변환 (timestamp 포맷 등)
```

**프롬프트:**
```
EventMessage 모델 검토:

1. 데이터 구조 비교:
   각 {Transport}EventMessage가 다음을 포함하는가?
   - Keycloak Event 모든 필드
   - Timestamp/format 일관성
   - Transport-specific 추가 필드?

2. Serialization:
   JSON 직렬화 방식:
   - Jackson 어노테이션 사용 (표준)
   - Kotlinx Serialization 사용
   - Custom 직렬화

   일관성이 있는가?

3. 필터링/민감 데이터:
   - 민감한 정보 제거 로직
   - 필드 변환 로직
   - 각 모듈별 차이
```

---

### 2.5 Manager/Producer/Sender 클래스 검토

**검토 항목:**
```
⚠️ 심각한 불일치 발견!

클래스명 혼용:
- Kafka: KafkaProducerManager
- Azure: AzureServiceBusSender
- NATS: NatsConnectionManager
- RabbitMQ: RabbitMQConnectionManager
- Redis: RedisConnectionManager
- AWS: AwsEventPublisher / AwsMessageProducer
- Common: (abstract base 없음)

해결 필요:
□ 표준 클래스명 결정: {Transport}ConnectionManager? {Transport}MessageSender?
□ 표준 인터페이스 정의: ConnectionManager? EventSender?
□ Common 모듈에서 기본 구현 제공?

책임:
□ 연결 생성/종료
□ 메시지 전송
□ 에러 처리
□ 생명주기 관리 (close())
□ 재시도 로직
```

**프롬프트:**
```
Manager/Producer/Sender 클래스 표준화:

현재 상황:
- Kafka: KafkaProducerManager (메시지 전송 관리)
- Azure: AzureServiceBusSender (직접 전송)
- NATS: NatsConnectionManager (연결 관리)
- RabbitMQ: RabbitMQConnectionManager (연결 관리)
- Redis: RedisConnectionManager (연결 관리)
- AWS: AwsEventPublisher + AwsMessageProducer (역할 분리)

문제점:
1. 이름 규칙 불일치
2. 책임 범위 불명확
3. 공통 인터페이스 없음

해결책 선택:
옵션 A: {Transport}ConnectionManager (연결 중심)
  - open(), close()
  - connect(), disconnect()
  - send(message) 위임

옵션 B: {Transport}MessageSender (메시지 중심)
  - send(message)
  - sendAsync(message)
  - close()

옵션 C: 이중 구조 (연결 + 발신자)
  - {Transport}ConnectionManager: 연결 생명주기
  - {Transport}MessageSender: 메시지 전송 로직

추천: 어느 옵션이 현재 구현과 가장 잘 맞는가?
그리고 common 모듈에서 기본 클래스/인터페이스 제공?
```

---

### 2.6 Metrics 클래스 검토

**검토 항목:**
```
□ 클래스명: {Transport}EventMetrics ✓ (일관성 있음)

□ 메트릭 종류
  공통:
  - keycloak.events.sent (Counter)
  - keycloak.events.failed (Counter)
  - keycloak.events.duration (Timer)

  추가:
  - 큐 크기, 배치 크기 등 (선택적)

□ Micrometer 통합
  - MeterRegistry 사용
  - Tagging 전략
  - Prometheus 내보내기

□ 메트릭 수집 포인트
  - 전송 전, 후
  - 에러 발생 시
  - 배치 완료 시
```

**프롬프트:**
```
Metrics 구현 검토:

1. 메트릭 항목 일관성:
   모든 모듈이 다음을 제공하는가?
   - keycloak.events.sent (성공 카운트)
   - keycloak.events.failed (실패 카운트)
   - keycloak.events.duration_ms (전송 시간)

2. Tagging 전략:
   - event_type, realm, transport, error_type 등
   - 각 모듈이 동일한 태그를 사용하는가?

3. Micrometer 통합:
   - MeterRegistry 주입 방식
   - Prometheus 내보내기

4. 추가 메트릭:
   - 배치 크기, 큐 크기, 재시도 횟수 등
   - 필요한가? 선택적인가?
```

---

### 2.7 README.md 검토

**검토 항목:**
```
□ 문서 구조 (표준화)
  1. Overview / 소개
  2. Features / 특징
  3. Configuration / 설정
  4. Usage / 사용법
  5. Monitoring / 모니터링
  6. Troubleshooting / 문제해결
  7. Examples / 예제

□ 설정 문서
  - 필수/선택 설정 명시
  - 설정 우선순위 설명
  - Realm Attributes 예제

□ 모니터링
  - Prometheus 메트릭
  - Health check
  - 로그 포맷

□ 예제
  - Docker Compose 설정
  - Realm 초기화 스크립트
  - 모니터링 대시보드

□ 문서 품질
  - 최신 정보인가?
  - 깨진 링크가 있는가?
```

**프롬프트:**
```
README.md 표준화:

1. 문서 구조 확인:
   각 모듈의 README가 다음 섹션을 포함하는가?
   - Overview
   - Features
   - Configuration (필수 설정 명시)
   - Usage / Setup
   - Monitoring / Metrics
   - Troubleshooting
   - Examples

2. 설정 문서 일관성:
   - 필수 vs 선택 설정 구분
   - 예제 설정값 제공

3. 추가 관계 문서:
   - Common 모듈과의 관계
   - 다른 transport와의 선택 기준
```

---

## Phase 3: 발견된 불일치 및 우선순위

### 3.1 CRITICAL - 즉시 수정 필요

```
1️⃣ Config 로딩 패턴 불일치
   문제: 4개 vs 2개 모듈이 다른 패턴 사용
   영향: 새로운 모듈 추가 시 혼동
   수정:
     - 표준 패턴 결정
     - 모든 모듈 일관성 맞추기
     - Common 모듈에서 공통 로직 제공

2️⃣ Manager/Producer/Sender 이름 불일치
   문제: 5가지 다른 이름 사용
   영향: 코드 리뷰, 문서화, 유지보수 어려움
   수정:
     - 표준 클래스명 결정
     - 모든 모듈의 클래스명 통일
     - 인터페이스 정의 (Common 모듈)

3️⃣ Prometheus 포트 충돌 (NATS & Redis)
   문제: 둘 다 9092로 설정
   영향: 컨테이너 환경에서 포트 충돌
   수정:
     - 각 모듈별 고유 포트 할당
     - 문서에서 명시
```

### 3.2 HIGH - 다음 릴리스에서 수정

```
4️⃣ 테스트 커버리지 부족
   문제: Azure, Redis, AWS 테스트 전무 (3/7 모듈)
   영향: 통합 테스트 신뢰성 저하
   수정:
     - 각 모듈에 최소 단위 테스트 추가
     - IntegrationTest 구현

5️⃣ 서브디렉토리 구조 비표준화
   문제: config/, sender/, model/ 사용 유무 제각각
   영향: 네비게이션 어려움
   수정:
     - 표준 디렉토리 구조 결정
     - 모든 모듈 통일
```

### 3.3 MEDIUM - 곧

```
6️⃣ Exception/Error 클래스 부족
   문제: 대부분 없음
   영향: 에러 처리 불명확
   수정:
     - {Transport}EventException 정의
     - Common 모듈에서 기본 제공

7️⃣ 공통 설정 스키마 부재
   문제: 각 모듈의 설정 문서가 개별적
   영향: 설정 배우기 어려움
   수정:
     - 중앙집중식 설정 문서
     - JSON Schema 파일
```

---

## Phase 4: 검토 프롬프트 템플릿

### 4.1 코드 리뷰용 프롬프트

```
새로운 event-listener-{transport} 모듈을 검토해주세요.

일관성 체크리스트:

✅ 구조 (src/main/kotlin 패키지)
- [ ] Factory: {Transport}EventListenerProviderFactory
- [ ] Provider: {Transport}EventListenerProvider
- [ ] Config: {Transport}EventListenerConfig
- [ ] Message: {Transport}EventMessage
- [ ] Manager: {Transport}ConnectionManager (또는 표준명)
- [ ] Metrics: {Transport}EventMetrics
- [ ] Subdirectories: config/, sender/, model/ (필요시)

✅ Factory & SPI
- [ ] META-INF/services/ 파일 정확함
- [ ] Factory.id() = "transport명"
- [ ] Factory.create() 메서드 표준 구현
- [ ] Config 로딩: Realm → System → Env → Default

✅ EventListenerProvider
- [ ] onEvent(Event) 구현
- [ ] onAdminEvent(AdminEvent) 구현
- [ ] close() 메서드 정리 로직
- [ ] 에러 처리 (Exception 정의)

✅ Metrics
- [ ] keycloak.events.sent (Counter)
- [ ] keycloak.events.failed (Counter)
- [ ] keycloak.events.duration_ms (Timer)
- [ ] 적절한 tagging

✅ build.gradle
- [ ] Shadow JAR 설정 표준
- [ ] 선택적 TestContainers
- [ ] 플러그인 일치

✅ 문서
- [ ] README.md 표준 구조
- [ ] 필수/선택 설정 명시
- [ ] 예제 설정값

✅ 테스트
- [ ] 최소 단위 테스트
- [ ] IntegrationTest 기초 (필수)
```

### 4.2 리팩토링용 프롬프트

```
event-listener-{transport} 모듈을 표준화 리팩토링:

1단계: 클래스명 정규화
- [ ] Manager 클래스명을 {Transport}ConnectionManager로 통일
- [ ] 필요시 인터페이스 추출

2단계: Config 로딩 표준화
- [ ] Factory에서 Config.fromRealmAndEnv() 호출
- [ ] Realm → System → Env → Default 순서 엄격히 준수

3단계: 서브디렉토리 정리
- [ ] config/: Config 관련
- [ ] model/: EventMessage, 도메인 모델
- [ ] (선택) sender/: 전송 로직
- [ ] metrics/: Metrics 클래스

4단계: 테스트 추가
- [ ] src/test/kotlin: 최소 단위 테스트
- [ ] src/integrationTest/kotlin: TestContainers 기반

5단계: 문서 표준화
- [ ] README.md 구조 맞추기
- [ ] Prometheus 포트 명시

6단계: build.gradle 정렬
- [ ] 의존성 순서 정렬
- [ ] Shadow JAR 설정 표준화
```

---

## 부록: 검토 체크리스트 요약

### 최소 검토 항목 (빠른 검토용)

```
□ 디렉토리 구조: src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
□ 클래스명:
  - {Transport}EventListenerProviderFactory
  - {Transport}EventListenerProvider
  - {Transport}EventListenerConfig
  - {Transport}EventMessage
  - {Transport}ConnectionManager (또는 표준)
  - {Transport}EventMetrics
□ SPI: META-INF/services/org.keycloak.events.EventListenerProviderFactory
□ Config: Realm → System → Env → Default 순서
□ Test: 최소 단위 테스트 + IntegrationTest 기초
□ README: 표준 구조 (Overview, Config, Usage, Monitoring)
□ build.gradle: shadowJar, 플러그인 표준화
```

### 심화 검토 항목 (철저한 검토용)

1. **인터페이스 일관성**: 모든 Manager/Sender 클래스가 공통 인터페이스 구현?
2. **에러 처리**: Exception 클래스 정의 및 처리 방식
3. **생명주기**: open()/close() 메서드 구현 및 호출 시점
4. **배치 처리**: 배치 크기, 타임아웃, 재시도 전략
5. **Resilience**: Circuit Breaker, Retry, DLQ 구현
6. **모니터링**: Prometheus 메트릭, Health Check
7. **성능**: 타임아웃, 스레드풀, 최적화

---

## 다음 단계

1. **Phase 1-2 검토**: 현재 문서 읽기
2. **Phase 3 우선순위**: CRITICAL부터 수정
3. **Phase 4 프롬프트**: 코드 리뷰/리팩토링 실행
4. **검증**: 표준화 완료 후 체크리스트 확인
