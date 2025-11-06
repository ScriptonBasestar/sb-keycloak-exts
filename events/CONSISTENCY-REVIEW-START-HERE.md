# 📋 Events Module 일관성 검토 – 시작하기

> **이 파일부터 시작하세요!**
>
> events/ 디렉토리의 **6개 transport 모듈** + **1개 공통 라이브러리** 일관성 검토를 위한 **네비게이션 가이드**
>
> - **Transports**: Kafka, Azure, NATS, RabbitMQ, Redis, AWS
> - **Common**: 공유 구현 및 유틸리티

---

## 🎯 5분 만에 파악하기

### 현재 상태

**일관성 점수: 60/100** ⚠️

| 항목 | 상태 | 영향도 |
|------|------|--------|
| 클래스명 (Factory, Provider, Config, Message) | ✅ 완벽 | 낮음 |
| Manager/Sender/Producer 클래스명 | ⚠️ 혼용 | **높음** |
| Config 로딩 패턴 | ⚠️ 불일치 | **높음** |
| 디렉토리 구조 | ⚠️ 비표준 | 중간 |
| 테스트 커버리지 | ❌ 부족 (50% - 3/6 transports) | **높음** |
| 포트 설정 | ❌ 충돌 (NATS/Redis) | 높음 |
| README 구조 | ⚠️ 비표준 | 낮음 |

### 즉시 해결 필요 (P1)

```
🔴 1. Manager 클래스명 불일치
   - Kafka: KafkaProducerManager
   - Azure: AzureServiceBusSender
   - NATS: NatsConnectionManager
   → 표준: {Transport}ConnectionManager 또는 {Transport}MessageSender로 통일
   → 소요: 4시간, 영향: 높음

🔴 2. Config 로딩 패턴 불일치
   - 일부 모듈: Realm → System → Env 순서 불명확
   → 표준: 공통 구현을 Common 모듈에서 제공
   → 소요: 3시간, 영향: 높음

🔴 3. Prometheus 포트 충돌
   - NATS & Redis 둘 다 9092 사용
   → 각 모듈별 고유 포트 할당
   → 소요: 30분, 영향: 높음
```

---

## 📚 문서 구조

### 1️⃣ **지금 읽어야 할 문서** (이미 읽는 중)

**`CONSISTENCY-REVIEW-START-HERE.md`** (이 파일)
- 5분 요약
- 문서 네비게이션
- 빠른 시작 가이드

---

### 2️⃣ **상황 파악 문서** (15분)

**`README-CONSISTENCY.md`** ⭐ **추천**
- 현황 요약
- 검토 항목별 상세 설명
- 우선순위별 개선 계획
- 실행 체크리스트

👉 **다음으로 이것을 읽으세요**

---

### 3️⃣ **상세 분석 문서** (30분)

**`00-consistency-review-checklist.md`**
- Phase별 검토 항목
- 각 항목의 상세 설명
- 검토용 프롬프트 제공

**`DETAILED_COMPARISON.md`** (가장 기술적)
- 12가지 차원의 분석
- 모듈별 상세 비교
- 우선순위 매트릭스

**`COMPARISON_SUMMARY.txt`** (빠른 참고)
- ASCII 형식 요약
- 표, 통계
- 스크린샷용

---

### 4️⃣ **AI 협업 프롬프트** (실행)

**`01-ai-review-prompts.md`** ⭐ **중요**
- 7개의 실행 가능한 프롬프트
- Claude와 함께 분석하기
- 단계별 가이드

**사용 방법**:
```bash
# 프롬프트 1: 전체 모듈 구조 비교 (초기 분석)
Claude에게 01-ai-review-prompts.md의 "프롬프트 1"을 복사해서 붙여넣고 실행

# 결과 검토
→ 현황 파악 완료

# 다음 프롬프트 진행 (2, 3, 4, ...)
```

---

### 5️⃣ **참고 문서**

**`FILE_MANIFEST.md`**
- 파일 구조 참조
- 모듈별 파일 목록

**`ANALYSIS_INDEX.md`**
- 전체 문서 색인
- 용도별 문서 가이드

---

## 🚀 빠른 시작 (30분)

### Step 1: 현황 파악 (10분)
```
✅ CONSISTENCY-REVIEW-START-HERE.md 읽기 (지금 하는 중)
→ README-CONSISTENCY.md 읽기
```

### Step 2: AI 분석 실행 (15분)
```
✅ 01-ai-review-prompts.md 에서 "프롬프트 1" 복사
→ Claude에 붙여넣고 실행
→ 결과 검토 (JSON 또는 테이블 형식)
```

### Step 3: 결과 검토 (5분)
```
✅ AI 분석 결과와 이 문서의 발견 사항 비교
→ P1 항목 우선순위 확인
→ 팀과 일정 조율
```

---

## 📖 문서 선택 가이드

### 내가 할 일은?

**"빠르게 현황만 파악하고 싶어"**
```
1. CONSISTENCY-REVIEW-START-HERE.md (이 파일) ✅
2. README-CONSISTENCY.md (우선순위 섹션)
3. 끝!
```

**"상세하게 이해하고 싶어"**
```
1. README-CONSISTENCY.md (전체)
2. 00-consistency-review-checklist.md
3. DETAILED_COMPARISON.md
```

**"AI와 함께 분석하고 싶어"** ⭐
```
1. README-CONSISTENCY.md (개요)
2. 01-ai-review-prompts.md (프롬프트 1 실행)
3. 결과 바탕으로 다음 프롬프트 진행
```

**"리팩토링 계획을 세우고 싶어"**
```
1. README-CONSISTENCY.md (전체)
2. DETAILED_COMPARISON.md (불일치 섹션)
3. 01-ai-review-prompts.md (관련 프롬프트 선택)
4. 팀과 논의하여 일정 수립
```

**"새로운 transport를 추가하고 싶어"**
```
1. 01-ai-review-prompts.md (프롬프트 7: 신규 transport 가이드)
2. 00-consistency-review-checklist.md (체크리스트)
3. 표준 구현 진행
```

---

## 🎬 예제: 30분 안에 끝내기

### Timeline

```
00:00-05:00  CONSISTENCY-REVIEW-START-HERE.md (이 파일)
             → 현황 파악, P1 항목 이해

05:00-10:00  README-CONSISTENCY.md 의 "우선순위별 해결책" 섹션
             → 각 항목별 상세 이해

10:00-15:00  01-ai-review-prompts.md 에서 프롬프트 1 준비
             → 복사, 형식 이해

15:00-30:00  Claude AI와 함께 프롬프트 1 실행
             → 분석 결과 획득
             → 팀과 공유할 내용 정리

결과:
- ✅ 현황 파악 완료
- ✅ P1 항목 식별 및 우선순위 확정
- ✅ 팀과 공유 가능한 데이터 확보
```

---

## 📋 전체 문서 맵

```
📂 events/
│
├── 📌 START HERE
│   └── CONSISTENCY-REVIEW-START-HERE.md  ← 지금 읽는 중 👈
│
├── 📖 QUICK START
│   └── README-CONSISTENCY.md             ← 다음으로 읽기 ⭐
│
├── 🔍 DETAILED ANALYSIS
│   ├── 00-consistency-review-checklist.md
│   ├── DETAILED_COMPARISON.md
│   ├── COMPARISON_SUMMARY.txt
│   ├── FILE_MANIFEST.md
│   └── ANALYSIS_INDEX.md
│
├── 🤖 AI COLLABORATION
│   └── 01-ai-review-prompts.md          ← 프롬프트 사용 ⭐
│
└── 📚 EXISTING DOCS
    ├── README.md
    ├── RESILIENCE_PATTERNS.md
    ├── IMPLEMENTATION_SUMMARY.md
    └── grafana-dashboard.json
```

---

## ✅ 실행 체크리스트

### Phase 1: 분석 (60분)
```
□ CONSISTENCY-REVIEW-START-HERE.md 읽음
□ README-CONSISTENCY.md 읽음
□ 01-ai-review-prompts.md 프롬프트 1 실행
□ AI 분석 결과 검토
□ DETAILED_COMPARISON.md 읽음
→ 현황 이해 완료 ✅
```

### Phase 2: 계획 (30분)
```
□ P1 항목 3개 재확인 (Manager, Config, Port)
□ 팀 회의 일정 잡음
□ 담당자 배정 준비
□ 일정 산출 (12-18시간 예상)
→ 실행 계획 수립 완료 ✅
```

### Phase 3: 실행 (진행 중)
```
□ P1-1: Manager/Sender 클래스명 표준화
□ P1-2: Config 로딩 패턴 통일
□ P1-3: Prometheus 포트 충돌 해결
□ P2 항목 진행
→ 개선 완료 예정
```

---

## 🎯 목표 (완료 후)

### 현재 vs 목표

| 항목 | 현재 | 목표 |
|------|------|------|
| 클래스명 일관성 | 80% | 100% |
| Config 패턴 일관성 | 85% | 100% |
| 테스트 커버리지 | 50% (3/6) | 70% |
| 디렉토리 구조 | 40% | 100% |
| README 구조 | 70% | 100% |
| **전체 점수** | **60/100** | **90/100** |

---

## 💬 자주 묻는 질문

**Q1: 지금 뭘 해야 하나?**
> 1. README-CONSISTENCY.md 읽기 (10분)
> 2. 01-ai-review-prompts.md 프롬프트 1 실행 (15분)
> 3. 결과 검토 (5분)

**Q2: 전체 분석이 다 필요한가?**
> 아니요. README-CONSISTENCY.md만으로도 충분합니다.
> 상세 분석은 "깊이 알고 싶을 때만" 읽으세요.

**Q3: 개선하려면 얼마나 걸릴까?**
> P1 항목 3개: 7-8시간 (팀 작업)
> P2 항목 2개: 8시간 (다음 스프린트)
> P3 항목: 3시간 (점진적)

**Q4: 지금 바로 리팩토링해야 하나?**
> P1 항목 (Manager, Config, Port)은 빨리 할수록 좋습니다.
> P2, P3는 점진적으로 진행 가능합니다.

**Q5: 어디가 가장 문제인가?**
> 1. Manager/Sender 클래스명 (새 개발자가 혼동)
> 2. Config 패턴 (새 모듈 추가할 때마다 반복)
> 3. 포트 충돌 (배포 환경 문제)

---

## 🔗 다음 단계

### 지금 바로
```
→ README-CONSISTENCY.md 열기
```

### 10분 후
```
→ 01-ai-review-prompts.md 에서 프롬프트 1 복사
→ Claude에 붙여넣기
```

### 30분 후
```
→ AI 분석 결과 검토
→ DETAILED_COMPARISON.md 와 비교
→ 팀과 공유
```

### 1시간 후
```
→ 팀 회의에서 P1 항목 우선순위 확정
→ 담당자 배정 및 일정 수립
→ 첫 번째 항목 시작 (Manager 클래스명)
```

---

## 📞 문서 피드백

이 문서들에 대한 질문이나 개선 사항:
1. 각 문서 상단의 "다음 단계" 섹션 확인
2. 팀과 함께 프롬프트 실행
3. Claude와 상호작용하며 개선점 도출

---

## 📊 문서 정보

| 파일명 | 크기 | 읽는 시간 | 목적 |
|--------|------|----------|------|
| CONSISTENCY-REVIEW-START-HERE.md | 10KB | 5분 | 네비게이션 |
| README-CONSISTENCY.md | 14KB | 15분 | 현황 및 계획 |
| 00-consistency-review-checklist.md | 20KB | 20분 | 체크리스트 |
| 01-ai-review-prompts.md | 25KB | 30분 | AI 협업 |
| DETAILED_COMPARISON.md | 20KB | 30분 | 상세 분석 |
| COMPARISON_SUMMARY.txt | 20KB | 10분 | 빠른 참고 |
| FILE_MANIFEST.md | 11KB | 10분 | 파일 구조 |
| ANALYSIS_INDEX.md | 9KB | 5분 | 색인 |

**총량**: 129KB, 약 90분 읽기 시간
**핵심**: 30분이면 충분 (START HERE + README-CONSISTENCY + 프롬프트 1)

---

## 🎉 완료 후

모든 P1 항목을 해결한 후:
- ✅ 일관성 점수 80/100 달성
- ✅ 새 모듈 추가 가이드라인 정립
- ✅ 팀의 개발 속도 향상
- ✅ 코드 리뷰 시간 단축

---

**🚀 지금 바로 시작하세요!**

**다음: README-CONSISTENCY.md 열기**

---

Generated: 2025-11-06
Status: Ready to Review
Author: Claude AI Analysis System
