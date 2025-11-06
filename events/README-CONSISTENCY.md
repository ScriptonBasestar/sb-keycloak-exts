# Events Module 일관성 검토 가이드

> **목적**: events/ 디렉토리의 **6개 transport 모듈**(Kafka, Azure, NATS, RabbitMQ, Redis, AWS) + **1개 공통 라이브러리**(Common)가 **구조적/명명적 일관성**을 유지하도록 하기 위한 검토 및 개선 가이드

---

## 📋 빠른 요약

### 현재 상태
- ✅ **SPI 패턴**: 모두 올바르게 구현
- ✅ **기본 클래스명**: Factory, Provider, Config, Message 일관성 있음
- ⚠️ **Manager/Sender 클래스**: 5가지 다른 이름 혼용 (불일치)
- ⚠️ **디렉토리 구조**: 서브디렉토리 사용 일관성 없음
- ❌ **테스트 커버리지**: 3개 모듈이 테스트 전무 (Azure, Redis, AWS)
- ❌ **포트 충돌**: NATS & Redis 둘 다 9092 포트 사용

### 우선순위별 해결책
| 우선 | 항목 | 영향 | 소요시간 |
|------|------|------|---------|
| 🔴 P1 | Manager/Sender 클래스명 표준화 | 코드 리뷰/유지보수 | 4시간 |
| 🔴 P1 | Config 로딩 패턴 통일 | 새 모듈 추가 시 혼동 | 3시간 |
| 🔴 P1 | Prometheus 포트 충돌 해결 | 컨테이너 배포 실패 | 30분 |
| 🟡 P2 | Azure/Redis/AWS 테스트 추가 | 통합 테스트 신뢰성 | 6시간 |
| 🟡 P2 | 디렉토리 구조 표준화 | 새 개발자 온보딩 | 2시간 |
| 🟢 P3 | README 문서 구조 통일 | 학습 곡선 | 3시간 |

---

## 🚀 시작하기

### 1단계: 검토 항목 확인 (10분)
```bash
cat 00-consistency-review-checklist.md
# → 검토할 항목 목록 읽기
```

### 2단계: AI 프롬프트로 분석 (30분)
```bash
# 다음 중 하나 선택하여 Claude와 함께 실행
cat 01-ai-review-prompts.md | head -50

# 추천: "프롬프트 1: 전체 모듈 구조 비교" 먼저 실행
```

### 3단계: 현황 리포트 읽기 (15분)
```bash
cat DETAILED_COMPARISON.md      # 가장 상세한 분석
cat COMPARISON_SUMMARY.txt       # 빠른 요약
```

### 4단계: 우선순위별 개선 (진행 중)
```bash
# P1 항목부터 차례대로 처리
# 각 항목별로 해당 프롬프트 사용
```

---

## 📊 검토 항목 요약

### A. 디렉토리 구조
```
목표: 모든 모듈이 동일한 구조 따르기
현황: 서브디렉토리(config/, sender/, metrics/) 사용 불일치
표준: src/main/kotlin/org/scriptonbasestar/kcexts/events/{transport}/
      ├── {Transport}EventListenerProviderFactory.kt (필수)
      ├── {Transport}EventListenerProvider.kt       (필수)
      ├── {Transport}EventListenerConfig.kt         (필수)
      ├── {Transport}EventMessage.kt                (필수)
      ├── {Transport}ConnectionManager.kt           (필수)
      └── metrics/                                   (필수)
          └── {Transport}EventMetrics.kt
```

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 4

---

### B. 클래스명 패턴

#### ✅ 일관성 있는 부분
| 용도 | 패턴 | 상태 |
|------|------|------|
| Factory | `{Transport}EventListenerProviderFactory` | ✅ 완벽 |
| Provider | `{Transport}EventListenerProvider` | ✅ 완벽 |
| Config | `{Transport}EventListenerConfig` | ✅ 완벽 |
| Message | `{Transport}EventMessage` | ✅ 완벽 |
| Metrics | `{Transport}EventMetrics` | ✅ 완벽 |

#### ⚠️ 불일치 부분 (긴급 해결 필요)
| 모듈 | 현재명 | 문제 |
|------|--------|------|
| Kafka | `KafkaProducerManager` | "Producer"라는 이름이 Kafka 특화 |
| Azure | `AzureServiceBusSender` | "ServiceBusSender" 너무 구체적 |
| NATS | `NatsConnectionManager` | "Connection" vs "Producer" 역할 혼동 |
| RabbitMQ | `RabbitMQConnectionManager` | 동일한 혼동 |
| Redis | `RedisConnectionManager` | 동일한 혼동 |
| AWS | `AwsEventPublisher` + `AwsMessageProducer` | 역할 분리? 이중 정의? |

**표준안**: 모두 `{Transport}ConnectionManager` 또는 `{Transport}MessageSender`로 통일

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 3

---

### C. Config 로딩 패턴

**목표**: 설정 로딩 우선순위 표준화

**현황**: 대부분 정확하나, 일부 불일치 가능

**표준 우선순위** (Keycloak 권장):
```
1. Realm Attributes (realm.getAttribute("key"))  [최고]
2. System Properties (System.getProperty("key"))
3. Environment Variables (System.getenv("KEY"))
4. Default values                                [최저]

예시:
val bootstrapServers = realm.getAttribute("kafka.bootstrap.servers")
    ?: System.getProperty("kafka.bootstrap.servers")
    ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
    ?: "localhost:9092"  // default
```

**검토 항목**:
- [ ] 모든 모듈이 동일한 우선순위 순서 따르는가?
- [ ] 필수 설정에 대한 검증 로직이 있는가?
- [ ] Config 클래스가 불변(immutable)인가?

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 2

---

### D. 필수 클래스 구성

**모든 모듈이 가져야 할 클래스** (6개):

```
1️⃣ {Transport}EventListenerProviderFactory
   - EventListenerProviderFactory 구현
   - SPI 진입점
   - Factory.id() = "{transport명}"

2️⃣ {Transport}EventListenerProvider
   - EventListenerProvider 구현
   - onEvent(), onAdminEvent() 메서드
   - close() 메서드

3️⃣ {Transport}EventListenerConfig
   - 설정 로드 및 저장
   - 필수/선택 설정 분리
   - 유효성 검증

4️⃣ {Transport}EventMessage
   - Data class (또는 record)
   - Keycloak Event 필드 포함
   - JSON 직렬화 지원 (Jackson)

5️⃣ {Transport}ConnectionManager
   - 메시지 전송 담당
   - open() / close() 생명주기
   - send() / sendAsync() 메서드

6️⃣ {Transport}EventMetrics (in metrics/)
   - Micrometer 통합
   - keycloak.events.sent counter
   - keycloak.events.failed counter
   - keycloak.events.duration_ms timer
```

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 1

---

### E. build.gradle 표준화

**공통 항목**:
```gradle
// 1. 의존성 (모든 모듈 공통)
compileOnly "org.keycloak:keycloak-core:26.0.7"
implementation project(":events:event-listener-common")
implementation "org.slf4j:slf4j-api"
implementation "io.micrometer:micrometer-core"

// 2. Transport-specific (각 모듈마다)
implementation "org.apache.kafka:kafka-clients:${kafkaVersion}"

// 3. Shadow JAR (모든 모듈)
shadowJar {
    // include transport dependencies
}

// 4. 플러그인 (모든 모듈)
plugins {
    id "java"
    id "org.jetbrains.kotlin.jvm"
    id "com.github.johnrengelman.shadow"
}
```

**검토 항목**:
- [ ] 모든 모듈의 Keycloak 버전 일치 (26.0.7)
- [ ] Kotlin 버전 일치 (2.2.21)
- [ ] Shadow JAR 설정 일관성
- [ ] 테스트 의존성 (JUnit, MockK, TestContainers)

---

### F. SPI 등록 (META-INF/services)

**파일 위치**:
```
src/main/resources/META-INF/services/
  org.keycloak.events.EventListenerProviderFactory
```

**파일 내용**:
```
org.scriptonbasestar.kcexts.events.{transport}.{Transport}EventListenerProviderFactory
```

**검토 항목**:
- [ ] 파일 존재 여부
- [ ] FQCN이 정확한가?
- [ ] 파일이 shadowJar에 포함되는가?

---

### G. 테스트 구조

**현황**:
| 모듈 | Unit | Integration | 상태 |
|------|------|-------------|------|
| Kafka | ✅ | ✅ | 완벽 |
| Azure | ❌ | ❌ | 개선 필요 |
| NATS | ✅ | ❌ | 부분 |
| RabbitMQ | ✅ | ❌ | 부분 |
| Redis | ❌ | ❌ | 개선 필요 |
| AWS | ✅ | ❌ | 부분 |
| Common | ? | ? | 미정 |

**표준 테스트 세트**:
```
src/test/kotlin/.../{transport}/
├── {Transport}EventListenerProviderFactoryTest.kt
├── {Transport}EventListenerProviderTest.kt
├── {Transport}EventListenerConfigTest.kt
└── metrics/
    └── {Transport}EventMetricsTest.kt

src/integrationTest/kotlin/.../{transport}/
└── {Transport}EventListenerIntegrationTest.kt
```

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 6

---

### H. README.md 문서 구조

**표준 섹션** (모든 모듈이 가져야 함):

```markdown
# {Transport} Event Listener

## Overview
- 모듈의 목적
- 대상 사용자

## Features
- 주요 특징
- Resilience 패턴

## Configuration
- 필수/선택 설정 테이블
- 설정 로딩 우선순위 설명
- Realm Attributes 예제

## Usage / Setup
- Docker 배포
- Realm 초기화

## Monitoring
- Prometheus 메트릭
- Health Check

## Performance Tuning
- 성능 최적화 팁

## Troubleshooting
- 일반적 오류 및 해결책

## Examples
- 완전한 사용 예제
```

**검토 프롬프트**: `01-ai-review-prompts.md` 프롬프트 5

---

## 🔍 상세 검토 문서

다음 파일들에서 더 깊은 분석 정보를 얻을 수 있습니다:

| 파일 | 내용 | 용도 |
|------|------|------|
| `00-consistency-review-checklist.md` | 검토 항목 체크리스트 | Phase별 검토 |
| `01-ai-review-prompts.md` | AI 프롬프트 7개 | Claude와 협업 분석 |
| `DETAILED_COMPARISON.md` | 12가지 차원의 상세 분석 | 깊이 있는 이해 |
| `COMPARISON_SUMMARY.txt` | ASCII 형식 빠른 요약 | 스크린샷, 보고 |
| `FILE_MANIFEST.md` | 파일 구조 및 통계 | 네비게이션 |
| `ANALYSIS_INDEX.md` | 문서 네비게이션 가이드 | 문서 사용법 |

---

## ⏱️ 실행 계획

### Phase 1: 분석 (1-2시간)
```
1. 00-consistency-review-checklist.md 읽기
2. 01-ai-review-prompts.md의 프롬프트 1 실행
3. DETAILED_COMPARISON.md 읽기
```

### Phase 2: 우선순위 결정 (30분)
```
1. 현황 분석 결과 검토
2. 팀과 함께 P1 항목 선정
3. 일정 및 담당자 배정
```

### Phase 3: P1 항목 해결 (진행 중)
```
🔴 Manager/Sender 클래스명 표준화
   → 프롬프트 3 사용하여 표준 결정
   → 모든 모듈 일괄 리팩토링
   → 기간: 4시간, 담당자: ?

🔴 Config 로딩 패턴 통일
   → 프롬프트 2 사용하여 표준 결정
   → 표준 구현 (Common 모듈)
   → 모든 모듈 리팩토링
   → 기간: 3시간, 담당자: ?

🔴 Prometheus 포트 충돌 해결
   → README 및 docker-compose 수정
   → 기간: 30분, 담당자: ?
```

### Phase 4: P2 항목 해결 (다음 스프린트)
```
🟡 테스트 커버리지 추가
🟡 디렉토리 구조 표준화
```

### Phase 5: P3 항목 해결 (점진적)
```
🟢 README 문서 통일
🟢 더 나은 예제 및 가이드 작성
```

---

## 📝 체크리스트

### 초기 검토용
```
□ 00-consistency-review-checklist.md 읽음
□ 01-ai-review-prompts.md의 프롬프트 1 실행
□ DETAILED_COMPARISON.md 검토
□ 현재 일관성 수준 파악 (1-10 점수 기준)
□ P1 항목 3가지 식별
```

### 개선 추적용
```
□ Manager/Sender 클래스명 표준화 완료
  - [ ] 표준 클래스명 결정
  - [ ] Common 모듈 인터페이스 정의
  - [ ] 모든 모듈 리팩토링
  - [ ] 테스트 통과

□ Config 로딩 패턴 통일 완료
  - [ ] 표준 패턴 결정
  - [ ] Common 모듈 구현
  - [ ] 모든 모듈 적용
  - [ ] 테스트 통과

□ Prometheus 포트 충돌 해결
  - [ ] 각 모듈별 포트 할당
  - [ ] 문서 업데이트
  - [ ] docker-compose 업데이트

□ 테스트 커버리지 추가 (P2)
  - [ ] Azure 테스트 추가
  - [ ] Redis 테스트 추가
  - [ ] AWS 테스트 추가
  - [ ] 커버리지 70% 이상 달성

□ 디렉토리 구조 표준화 (P2)
  - [ ] 표준 구조 결정
  - [ ] 모든 모듈 리팩토링
  - [ ] 문서 업데이트
```

---

## 🤝 팀 협업

### 추천하는 진행 방식

**1주차**:
- 월: 분석 (Phase 1)
- 화: 우선순위 결정 (Phase 2)
- 수-금: P1 첫 항목 시작 (Manager/Sender)

**2주차**:
- P1 항목 계속 진행

**3주차**:
- P1 항목 마무리
- P2 항목 시작

### 담당자 배정 (예시)

| 항목 | 담당자 | 기간 |
|------|--------|------|
| Manager/Sender 표준화 | 개발자 A | 4시간 |
| Config 패턴 통일 | 개발자 B | 3시간 |
| 포트 충돌 해결 | 개발자 C | 30분 |
| 테스트 추가 | 개발자 A+B | 6시간 |
| 디렉토리 구조 | 개발자 C | 2시간 |

---

## 📚 추가 참고

### 기존 분석 문서
- `DETAILED_COMPARISON.md` - 가장 상세한 분석
- `COMPARISON_SUMMARY.txt` - ASCII 형식 요약
- `FILE_MANIFEST.md` - 파일 구조 참조
- `ANALYSIS_INDEX.md` - 문서 네비게이션

### 프로젝트 문서
- `README.md` (events 루트) - 모듈 개요
- `RESILIENCE_PATTERNS.md` - 복원력 패턴
- `IMPLEMENTATION_SUMMARY.md` - 구현 요약
- 각 모듈의 `README.md` - 모듈별 가이드

---

## ❓ FAQ

**Q: 얼마나 시간이 걸릴까?**
A: 분석만 1-2시간, 개선은 우선순위별로 12-18시간 (팀 작업 기준)

**Q: 기존 코드는 깨질까?**
A: 클래스명 변경만으로는 외부 영향 없음 (META-INF 등록만 변경)

**Q: 왜 지금 해야 할까?**
A: 새 transport 추가할 때마다 같은 혼동이 반복되기 때문

**Q: 어디서 시작할까?**
A: `01-ai-review-prompts.md` 프롬프트 1 실행 후 결과 검토

---

## 🎯 성공 기준

| 항목 | 현재 | 목표 | 달성 방법 |
|------|------|------|----------|
| 클래스명 일관성 | 60% | 100% | 표준화 리팩토링 |
| Config 패턴 일관성 | 85% | 100% | 공통 구현 제공 |
| 테스트 커버리지 | 50% (3/6) | 70% | 부족 모듈 테스트 추가 |
| 디렉토리 구조 | 40% | 100% | 일괄 정리 |
| README 구조 | 70% | 100% | 템플릿 적용 |
| **전체 일관성** | **60/100** | **90/100** | 위 항목들 완료 |

---

## 📞 연락처 및 질문

문서나 프롬프트에 대한 질문은:
1. `ANALYSIS_INDEX.md` 참고
2. 해당 상세 분석 문서 검토
3. 팀과 함께 프롬프트 실행

---

**Last Updated**: 2025-11-06
**Document Status**: Ready for Review
**Next Step**: Execute Prompt 1 from `01-ai-review-prompts.md`
