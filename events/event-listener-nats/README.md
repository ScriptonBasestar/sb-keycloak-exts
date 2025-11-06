# NATS Event Listener for Keycloak

Keycloak 이벤트를 NATS subject 기반 메시징으로 전달하는 모듈입니다. `event-listener-common`에서 제공하는 이벤트 표준화, 회복 탄력성(resilience), 메트릭 수집 기능 위에 NATS 전용 연결 관리와 subject 전략을 덧붙였습니다.

## Module Overview
- Keycloak의 User/Admin 이벤트를 JSON으로 직렬화하고 NATS subject로 publish 합니다.
- `event-listener-common`의 `CircuitBreaker`, `RetryPolicy`, `DeadLetterQueue`, `BatchProcessor`, `EventMetrics`를 재사용하여 모든 메시징 모듈이 동일한 운영 특성을 갖습니다.
- 단일 Keycloak 인스턴스에서 여러 Realm 이벤트를 처리할 수 있도록 subject는 `{base}.{realm}.{eventType}` 패턴을 따릅니다.
- Connection pooling과 자동 재연결을 통해 고가용성 NATS 클러스터에도 안정적으로 연결됩니다.

## Why We Provide a Dedicated NATS Module
| 요구사항 | NATS가 제공하는 특성 | 모듈 설계 반영 |
|----------|---------------------|---------------|
| 초경량, 낮은 지연 | 단일 바이너리, in-memory 라우팅 | 최소한의 직렬화와 즉시 publish 방식 |
| 동적 subject 라우팅 | 와일드카드 subject (`foo.*.bar`) | Realm/이벤트 타입별 subject 자동 생성 |
| 단순 운영 | JetStream 없이도 사용 가능 | 외부 브로커 의존성 낮추기, 연결 실패 시 빠른 재시도 |
| 유연한 인증 | 토큰, Basic Auth, TLS | `nats.server.url`, `nats.token`, `nats.username/password`, `nats.use.tls` 옵션 제공 |

## Design Rationale
### Shared abstractions
- **이벤트 표준화**: 모든 모듈은 `KeycloakEvent`/`KeycloakAdminEvent` 모델을 사용해 소비자 애플리케이션이 동일 JSON 구조를 소비할 수 있게 합니다.
- **회복 탄력성**: 공통 `CircuitBreaker`와 `RetryPolicy`를 사용하여 전송 실패 시 빠르게 복구하거나 Dead Letter Queue로 우회합니다.
- **운영 모니터링**: `EventMetrics` 인터페이스 구현체(`NatsEventMetrics`)가 Prometheus exporter와 연동되며 전체 이벤트 흐름을 관측할 수 있습니다.

### NATS-specific decisions
- **Connection Manager**: 서버 URL 별로 하나의 `NatsConnectionManager`를 유지하여 재연결 시 오버헤드를 줄이고, NATS의 빠른 핸드셰이크 특성을 살립니다.
- **noEcho 강제 적용**: Keycloak이 publish한 이벤트가 다시 자기 자신에게 전달되는 것을 방지하기 위해 NATS 클라이언트는 항상 `noEcho()` 모드로 연결됩니다.
- **Subject 설계**: `nats.subject.user` / `nats.subject.admin` 값을 기반으로 realm과 이벤트 타입을 suffix로 붙여 운영 중인 모든 Realm을 단일 구독으로 모니터링할 수 있습니다.
- **배치 처리 옵션**: NATS는 단일 메시지 전송에 최적화되어 있지만, 대량 이벤트 처리 시 네트워크 효율을 위해 `BatchProcessor`를 선택적으로 활성화할 수 있도록 했습니다.

## Event Flow
```
┌────────────┐   ① 이벤트 발생    ┌─────────────────────────┐
│  Keycloak  │ ───────────────▶  │ NatsEventListenerProvider │
└────────────┘                   │ - 공통 모델 변환          │
                                 │ - subject 생성            │
                                 └─────────────┬────────────┘
                                               │ ② 회복 탄력성 파이프라인
                                               ▼
                                 ┌─────────────────────────┐
                                 │ CircuitBreaker / Retry │
                                 │ DeadLetterQueue / Batch │
                                 └─────────────┬──────────┘
                                               │ ③ publish
                                               ▼
                                 ┌─────────────────────────┐
                                 │ NatsConnectionManager   │
                                 └─────────────┬──────────┘
                                               │
                                               ▼
                                         ┌────────┐
                                         │ NATS   │
                                         └────────┘
```

## Message Model & Subject Template
### User events
- Subject pattern: `{nats.subject.user}.{realmId}.{eventType}`
- Payload example:
```json
{
  "id": "d48c8a3a-8100-4d0e-8cee-6f1f15f61310",
  "time": 1730182435021,
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "account-console",
  "userId": "a2b8c3",
  "sessionId": "7d6f1f",
  "ipAddress": "192.168.10.12",
  "details": {
    "authMethod": "openid-connect"
  }
}
```

### Admin events
- Subject pattern: `{nats.subject.admin}.{realmId}.{operationType}`
- Payload example:
```json
{
  "id": "3b9c9f04-4e4a-4aa0-9f2a-31a0e7f4c41d",
  "time": 1730182439012,
  "operationType": "CREATE",
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "0f1aa21",
    "ipAddress": "192.168.10.10"
  },
  "resourcePath": "users/0f1aa21",
  "representation": "{...}"
}
```

## Configuration
### Quick start (Keycloak `standalone.xml`)
```xml
<spi name="eventsListener">
  <provider name="nats-event-listener" enabled="true">
    <properties>
      <property name="nats.server.url" value="nats://localhost:4222"/>
      <property name="nats.subject.user" value="keycloak.events.user"/>
      <property name="nats.subject.admin" value="keycloak.events.admin"/>
    </properties>
  </provider>
</spi>
```

### Core connection
| Property | Default | Notes |
|----------|---------|-------|
| `nats.server.url` | `nats://localhost:4222` | 단일 서버 혹은 `nats://host1:4222,nats://host2:4222` 형태 |
| `nats.connection.timeout.ms` | `60000` | ms 기준 연결 타임아웃 |
| `nats.max.reconnects` | `60` | 재연결 시도 횟수, `-1`이면 무한 |
| `nats.reconnect.wait.ms` | `2000` | 재연결 사이 지연(ms) |
| `nats.max.pings.out` | `2` | heartbeat 실패 허용 횟수 |
| `nats.use.tls` | `false` | TLS 필요 시 `true` |
| `nats.no.echo` | `true` (강제) | 클라이언트에서 항상 활성화되므로 설정값과 무관하게 적용됩니다. |

### Authentication
| Property | Description |
|----------|-------------|
| `nats.token` | NATS 토큰 기반 인증 |
| `nats.username` / `nats.password` | Basic 인증 (둘 다 지정 시 적용) |

### Subjects & Filtering
| Property | Default | Description |
|----------|---------|-------------|
| `nats.subject.user` | `keycloak.events.user` | Realm/이벤트 suffix가 뒤따릅니다. |
| `nats.subject.admin` | `keycloak.events.admin` | Realm/operation suffix가 뒤따릅니다. |
| `nats.enable.user.events` | `true` | 사용자 이벤트 전송 토글 |
| `nats.enable.admin.events` | `true` | 관리자 이벤트 전송 토글 |
| `nats.included.event.types` | (all) | `LOGIN,LOGOUT` 형태의 화이트리스트 |

### Resilience & Delivery Safety
| Property | Default | Purpose |
|----------|---------|---------|
| `enableCircuitBreaker` | `true` | 빠른 실패 차단, Keycloak thread 보호 |
| `circuitBreakerFailureThreshold` | `5` | 연속 실패 허용 횟수 |
| `circuitBreakerOpenTimeoutSeconds` | `60` | open 상태 유지 시간 |
| `enableRetry` | `true` | 지수 백오프 기반 재시도 |
| `maxRetryAttempts` | `3` | 재시도 횟수 |
| `retryInitialDelayMs` | `100` | 첫 지연(ms) |
| `retryMaxDelayMs` | `10000` | 최장 지연(ms) |
| `enableDeadLetterQueue` | `true` | 실패 메시지 보관 |
| `dlqMaxSize` | `10000` | 메모리 내 최대 보관 수 |
| `dlqPersistToFile` | `false` | 파일 기반 저장 여부 |
| `dlqPath` | `./dlq/nats` | DLQ 파일 경로 (활성화 시) |

### Throughput tuning
| Property | Default | Description |
|----------|---------|-------------|
| `enableBatching` | `false` | 배치 처리 토글 |
| `batchSize` | `100` | 배치 최대 메시지 수 |
| `batchFlushIntervalMs` | `5000` | 배치 flush 주기(ms) |

### Observability
| Property | Default | Description |
|----------|---------|-------------|
| `enablePrometheus` | `false` | 내장 Prometheus HTTP exporter |
| `prometheusPort` | `9095` | exporter 바인딩 포트 |
| `enableJvmMetrics` | `true` | JVM 메트릭 포함 여부 |

## Metrics & Monitoring
- `NatsEventMetrics`는 전송 성공/실패, 이벤트 타입별 카운트, 평균 지연 시간을 메모리에 집계합니다.
- Prometheus exporter를 활성화하면 `/metrics` 엔드포인트에서 `keycloak_events_sent_total`, `keycloak_events_failed_total`, `keycloak_event_duration_seconds` 지표를 제공합니다.
- Dead Letter Queue는 실패한 이벤트 전체 페이로드와 실패 원인을 저장하므로 운영 중 원인 분석이 가능합니다.

## Deployment
### Build & install JAR
```bash
./gradlew :events:event-listener-nats:build
cp events/event-listener-nats/build/libs/event-listener-nats-*.jar \
   $KEYCLOAK_HOME/providers/
```
Keycloak을 재시작하면 SPI가 로드됩니다.

### Docker layer
```dockerfile
FROM quay.io/keycloak/keycloak:26.0.7
COPY events/event-listener-nats/build/libs/event-listener-nats-*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
```

## Testing & Local Verification
1. NATS 서버 실행: `docker run -p 4222:4222 nats:latest`
2. Keycloak에서 `nats-event-listener` SPI 활성화
3. `nats sub "keycloak.events.user.>" -s nats://localhost:4222`로 수신 확인
4. 테스트 사용자 로그인/로그아웃으로 이벤트 흐름 검증
5. 단위 테스트: `./gradlew :events:event-listener-nats:test`

## Operational Tips
- subject 와일드카드(`keycloak.events.user.>`)를 활용하면 Realm 추가 시 코드 수정 없이 구독할 수 있습니다.
- 대량 이벤트가 예상되면 Keycloak 클러스터별 subject prefix를 다르게 구성하여 소비자 간 트래픽을 분산하세요.
- `totalFailed`와 Dead Letter Queue 파일을 주기적으로 확인해 네트워크 혹은 인증 문제를 조기에 발견합니다.
- 프로덕션 환경에서는 TLS(`useTls=true`)와 인증 토큰을 반드시 사용하고, NATS 서버 측에서도 계정 권한을 최소화하세요.

## Comparison with Other Messaging Modules
| 구분 | NATS | Kafka Listener | RabbitMQ Listener |
|------|------|----------------|-------------------|
| 메시징 패턴 | Subject (pub/sub) | Topic (log 기반) | Routing key / Queue |
| 주 사용처 | 저지연 이벤트 fan-out, 경량 마이크로서비스 | 대용량 이벤트 스트리밍, 순차 처리 | 워크큐, 보장된 전달 |
| 연결 관리 | 단일 커넥션, 자동 재연결 | Producer/Consumer 클라이언트 분리 | 채널 다중화 |
| 모듈 설계 포인트 | subject 생성 로직, noEcho, 빠른 재시도 | 파티션/오프셋 관리, 배치 producer | Exchange 타입 선택, 큐 선언 |
| 권장 소비자 | 실시간 알림, 세션 동기화, 경량 함수형 서비스 | 감사 로그, 데이터 파이프라인 | 백오피스 작업, 지연 허용 작업 |

## License
Apache License 2.0
