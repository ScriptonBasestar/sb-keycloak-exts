# Keycloak Event Extensions

Keycloak에서 발생하는 사용자/관리자 이벤트를 다양한 메시징 및 스트리밍 인프라로 전달하기 위한 확장 모듈 모음입니다. `events/` 이하 모듈들은 공통 코어(`event-listener-common`) 위에서 작동하며, 각 환경(AWS, Azure, Kafka, Redis, RabbitMQ, NATS 등)에 맞는 어댑터를 제공합니다.

## 디렉터리 구조
- `event-listener-common/` — 모든 리스너가 공유하는 모델, 직렬화, 설정 로더, 회복력(resilience) 도구 모음
- `event-listener-aws/` — AWS SQS/SNS 연동 리스너
- `event-listener-azure/` — Azure Service Bus Queue/Topic 연동 리스너
- `event-listener-kafka/` — Apache Kafka 전송 리스너
- `event-listener-nats/` — NATS & JetStream 전송 리스너
- `event-listener-rabbitmq/` — RabbitMQ (AMQP 0.9.1) 전송 리스너
- `event-listener-redis/` — Redis Streams 기반 경량 리스너
- `examples/` — Docker Compose 스택, 프로메테우스 설정, DLQ 재처리 스크립트 등 학습/테스트 자산
- `grafana-dashboard.json` — 운영 메트릭 모니터링을 위한 Grafana 대시보드 정의
- `IMPLEMENTATION_SUMMARY.md`, `RESILIENCE_PATTERNS.md` — 자세한 구현 및 운용 가이드

## 공통 아키텍처
모든 모듈은 Keycloak SPI의 EventListenerProvider/Factory 인터페이스를 구현하며 다음과 같은 흐름을 공유합니다:

1. **이벤트 수집** — Keycloak이 `onEvent` 또는 `onAdminEvent` 호출
2. **필터링 & 직렬화** — `event-listener-common`의 모델/직렬화 계층이 이벤트를 공통 JSON 스키마로 변환
3. **Resilience 파이프라인** — Circuit Breaker → Retry Policy → Dead Letter Queue → Batch Processor 순서로 이벤트 안정성을 확보
4. **전송 어댑터** — 각 모듈이 대상 시스템 API(Kafka Producer, AWS SDK, Lettuce 등)를 호출
5. **메트릭 수집** — 공통 Metrics 인터페이스로 Prometheus 노출 및 Grafana 대시보드 유입

### 공통 모듈의 역할 (`event-listener-common`)
- **모델 & 직렬화**: `KeycloakEvent`, `KeycloakAdminEvent`, `EventMeta` 등 일관된 JSON 스키마를 제공
- **ConfigLoader**: Realm Attributes → SPI Config.Scope → System Property 순의 계층형 설정 조회를 구현해, 동일 코드로 멀티 환경을 지원하며 모든 이벤트 모듈이 `prefix.key` 형태(`kafka.bootstrap.servers`, `azure.use.queue` 등)의 동일 키 네이밍을 따릅니다.
- **Resilience 컴포넌트**: CircuitBreaker, RetryPolicy, DeadLetterQueue, BatchProcessor를 개별 모듈에서 재사용
- **Metrics 인터페이스**: 각각의 어댑터가 `EventMetrics`를 구현하여 Prometheus 지표와 Grafana 대시보드에 반영
- **테스트 자산**: 공통 모듈의 단위 테스트로 각 패턴의 신뢰성을 검증

## 모듈별 하이라이트
| 모듈 | 대상 시스템 | 추천 시나리오 | 설계 포인트 |
| --- | --- | --- | --- |
| `event-listener-aws` | AWS SQS / SNS | AWS 네이티브 인프라, Lambda/EventBridge 연계 | SDK v2 기반 다중 전송(SQS+SNS) 및 IAM/Instance Profile 지원 |
| `event-listener-azure` | Azure Service Bus Queue / Topic | Azure Functions, Logic Apps 파이프라인 | Queue/Topic 선택 사용, Managed Identity 인증 내장 |
| `event-listener-kafka` | Apache Kafka | 대규모 스트리밍, 마이크로서비스 이벤트 버스 | 토픽 분리(사용자/관리), 고성능 배치 & Prometheus 지표 |
| `event-listener-nats` | NATS & JetStream | 경량 메시징, 분산 제어평면 | JetStream Ack/재전송 전략, 저지연 전송에 특화 |
| `event-listener-rabbitmq` | RabbitMQ (AMQP) | 기존 MQ 인프라, 라우팅 키 기반 경로 | Exchange/Queue 바인딩 구성, Confirm 모드/Publisher Acks |
| `event-listener-redis` | Redis Streams | 단일 Redis 활용 환경, 저비용 실시간 | Lettuce 기반 경량 프로듀서, Stream MaxLen 관리 |
| `event-listener-common` | 공통 레이어 | 모든 리스너 | 공통 스키마/회복력/메트릭 제공으로 유지보수성 확보 |

### AWS SQS/SNS 리스너
- **이중 채널**: SQS와 SNS를 병렬로 사용해 DLQ 및 Fan-out 패턴을 동시에 충족
- **클라우드 네이티브**: IAM Role, Instance Profile로 자격 증명 관리 없이 운영 가능
- **Serverless 친화**: Lambda 트리거, EventBridge 규칙과 결합하기 쉬운 JSON 페이로드 구조

### Azure Service Bus 리스너
- **하이브리드 토폴로지**: Queue/Topic을 동시에 지원하여 메시징 전략을 선택적 구성
- **Managed Identity**: Key Vault 없이도 리스너에서 Azure AD 기반 인증 처리
- **연속 모니터링**: Service Bus 연결 상태를 Prometheus 지표로 노출해 장애 감지 시간 단축

### Kafka 리스너
- **고처리량 최적화**: 배치 처리 및 백프레셔 제어로 대규모 Realm 환경에서도 안정적
- **토픽 분리 설계**: 사용자 이벤트와 관리자 이벤트를 별도 토픽으로 분리해 보안/주제 관리 용이
- **운영 표준화**: Kafka Connect, Flink 등 다운스트림 도구들이 이해하기 쉬운 JSON 스키마 유지

### NATS 리스너
- **저지연 전송**: NATS의 빠른 퍼블/서브 모델에 맞춰 이벤트를 즉시 브로드캐스트
- **JetStream 연계**: 영속성이 필요한 경우, JetStream Ack 및 Retention 설정과 연동되는 메타데이터 포함
- **경량 배포**: 바이너리와 설정만으로 컨테이너/에이전트 환경에 손쉽게 배치

### RabbitMQ 리스너
- **AMQP 호환**: Exchange 타입(Direct/Topic/Fanout)과 라우팅 키를 세밀하게 제어 가능
- **확정 전송**: Publisher Confirm, Mandatory 플래그 등 MQ 운영 패턴을 반영
- **엔터프라이즈 적합성**: 기존 MQ 운영팀의 감시/보안 정책과 자연스럽게 통합

### Redis Streams 리스너
- **경량 인프라**: 추가 브로커 없이 Redis만으로 실시간 이벤트 전달
- **원활한 소비자 그룹**: Stream ID & Consumer Group 기능으로 멀티 컨슈머 처리 지원
- **저비용 DLQ 전략**: Redis Stream 자체의 maxlen, DLQ 파일 옵션으로 실패 이벤트 관리

## 회복력 & 관찰성
- **Resilience Patterns**: 세부 동작과 튜닝 가이드는 `events/RESILIENCE_PATTERNS.md` 참고
- **Prometheus 메트릭**: 모든 모듈에서 공통 포맷으로 메트릭을 노출 (`events/examples/prometheus.yml`)
- **Grafana 대시보드**: `events/grafana-dashboard.json`을 가져오면 이벤트 처리량, 실패율, Circuit 상태 등을 즉시 관찰 가능
- **DLQ 운영**: 실패 이벤트를 파일 또는 메모리로 축적하고, `events/examples/dlq-reprocess.sh` 스크립트로 재처리

## 빌드 & 배포
```bash
# 전체 이벤트 모듈 빌드
./gradlew build

# 특정 모듈만 빌드 (예: Kafka)
./gradlew :events:event-listener-kafka:build
```
생성된 JAR는 각 모듈의 `build/libs/`에 위치하며 Keycloak `providers/` 디렉터리에 배치 후 `kc.sh build` → `kc.sh start` 순으로 반영합니다.

## 추가 자료
- `events/IMPLEMENTATION_SUMMARY.md` — 최신 구현 현황과 리스너별 적용 범위
- `events/RESILIENCE_PATTERNS.md` — 회복력 패턴 심층 설명 및 운영 팁
- `events/examples/README.md` — Docker Compose 기반 샘플 환경, 테스트 시나리오
- `events/event-listener-*/README.md` — 각 모듈의 상세 설정, 예제 코드, 권장 운영 가이드

공통 구조를 이해한 뒤 각 모듈 README를 참고하면 환경별 설정과 운영 전략을 빠르게 수립할 수 있습니다.
