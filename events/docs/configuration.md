# Configuration Guide

Event Listener 모듈의 모든 설정 옵션을 설명합니다.

## Configuration Priority

설정값은 다음 우선순위로 로드됩니다:

```
1. Realm Attributes (최고 우선순위)
   ↓
2. System Properties (-D flags)
   ↓
3. Environment Variables
   ↓
4. Default Values (최저 우선순위)
```

## Common Configuration

모든 Transport에 공통으로 적용되는 설정입니다.

### Event Filtering

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableUserEvents` | boolean | `true` | 사용자 이벤트 활성화 |
| `enableAdminEvents` | boolean | `true` | 관리자 이벤트 활성화 |
| `includedEventTypes` | string | `*` (all) | 포함할 이벤트 타입 (쉼표 구분) |
| `excludedEventTypes` | string | - | 제외할 이벤트 타입 (쉼표 구분) |

**Example**:
```xml
<!-- LOGIN, LOGOUT, REGISTER만 전송 -->
<property name="includedEventTypes" value="LOGIN,LOGOUT,REGISTER"/>

<!-- 에러 이벤트 제외 -->
<property name="excludedEventTypes" value="LOGIN_ERROR,REGISTER_ERROR"/>
```

### Resilience Configuration

자세한 내용은 [Resilience Patterns](resilience.md) 참조.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enableCircuitBreaker` | boolean | `true` | Circuit Breaker 활성화 |
| `circuitBreakerFailureThreshold` | int | `5` | OPEN 전환 실패 횟수 |
| `circuitBreakerOpenTimeoutSeconds` | int | `60` | OPEN 상태 유지 시간 |
| `enableRetry` | boolean | `true` | 재시도 활성화 |
| `maxRetryAttempts` | int | `3` | 최대 재시도 횟수 |
| `retryInitialDelayMs` | long | `100` | 초기 대기 시간 |
| `retryMaxDelayMs` | long | `10000` | 최대 대기 시간 |
| `enableDeadLetterQueue` | boolean | `true` | DLQ 활성화 |
| `dlqMaxSize` | int | `10000` | DLQ 최대 크기 |
| `dlqPersistToFile` | boolean | `false` | 파일 영속화 |
| `dlqPath` | string | `./dlq/{transport}` | DLQ 저장 경로 |

## Transport-Specific Configuration

### Kafka

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bootstrapServers` | string | `localhost:9092` | Kafka 브로커 주소 |
| `clientId` | string | `keycloak-events` | Kafka 클라이언트 ID |
| `acks` | string | `1` | Acknowledgement 모드 (`0`, `1`, `all`) |
| `retries` | int | `0` | Kafka Producer 재시도 (Retry Policy 우선) |
| `compressionType` | string | `none` | 압축 타입 (`none`, `gzip`, `snappy`, `lz4`) |

#### Topic Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `eventTopic` | string | `keycloak-events` | 사용자 이벤트 토픽 |
| `adminEventTopic` | string | `keycloak-admin-events` | 관리자 이벤트 토픽 |
| `partitions` | int | `-1` | 파티션 수 (자동 할당) |
| `replicationFactor` | int | `1` | 복제 팩터 |

**Example**:
```xml
<spi name="eventsListener">
    <provider name="kafka-event-listener" enabled="true">
        <properties>
            <property name="bootstrapServers" value="kafka1:9092,kafka2:9092,kafka3:9092"/>
            <property name="eventTopic" value="keycloak-user-events"/>
            <property name="adminEventTopic" value="keycloak-admin-events"/>
            <property name="acks" value="all"/>
            <property name="compressionType" value="gzip"/>
        </properties>
    </provider>
</spi>
```

### RabbitMQ

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | string | `localhost` | RabbitMQ 호스트 |
| `port` | int | `5672` | RabbitMQ 포트 |
| `username` | string | `guest` | 사용자 이름 |
| `password` | string | `guest` | 비밀번호 |
| `virtualHost` | string | `/` | Virtual Host |
| `useSsl` | boolean | `false` | SSL 사용 여부 |

#### Exchange & Routing

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `exchangeName` | string | `keycloak-events` | Exchange 이름 |
| `exchangeType` | string | `topic` | Exchange 타입 |
| `exchangeDurable` | boolean | `true` | Exchange 영속성 |
| `userEventRoutingKey` | string | `keycloak.event.user` | 사용자 이벤트 라우팅 키 |
| `adminEventRoutingKey` | string | `keycloak.event.admin` | 관리자 이벤트 라우팅 키 |

**Example**:
```xml
<spi name="eventsListener">
    <provider name="rabbitmq-event-listener" enabled="true">
        <properties>
            <property name="host" value="rabbitmq.example.com"/>
            <property name="port" value="5672"/>
            <property name="username" value="keycloak"/>
            <property name="password" value="${RABBITMQ_PASSWORD}"/>
            <property name="virtualHost" value="/production"/>
            <property name="useSsl" value="true"/>
            <property name="exchangeName" value="keycloak"/>
            <property name="exchangeType" value="topic"/>
            <property name="userEventRoutingKey" value="keycloak.user.#"/>
        </properties>
    </provider>
</spi>
```

### NATS

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `serverUrl` | string | `nats://localhost:4222` | NATS 서버 URL |
| `username` | string | - | 사용자 이름 (선택) |
| `password` | string | - | 비밀번호 (선택) |
| `token` | string | - | 인증 토큰 (선택) |
| `useTls` | boolean | `false` | TLS 사용 여부 |

#### Subject Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `userEventSubject` | string | `keycloak.events.user` | 사용자 이벤트 Subject |
| `adminEventSubject` | string | `keycloak.events.admin` | 관리자 이벤트 Subject |

**Example**:
```xml
<spi name="eventsListener">
    <provider name="nats-event-listener" enabled="true">
        <properties>
            <property name="serverUrl" value="nats://nats.example.com:4222"/>
            <property name="token" value="${NATS_TOKEN}"/>
            <property name="useTls" value="true"/>
            <property name="userEventSubject" value="keycloak.user"/>
            <property name="adminEventSubject" value="keycloak.admin"/>
        </properties>
    </provider>
</spi>
```

### Redis Streams

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | string | `localhost` | Redis 호스트 |
| `port` | int | `6379` | Redis 포트 |
| `password` | string | - | 비밀번호 (선택) |
| `database` | int | `0` | 데이터베이스 번호 |
| `useSsl` | boolean | `false` | SSL 사용 여부 |

#### Stream Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `userEventStream` | string | `keycloak:events:user` | 사용자 이벤트 Stream |
| `adminEventStream` | string | `keycloak:events:admin` | 관리자 이벤트 Stream |
| `maxLen` | long | `10000` | Stream 최대 길이 |

**Example**:
```xml
<spi name="eventsListener">
    <provider name="redis-event-listener" enabled="true">
        <properties>
            <property name="host" value="redis.example.com"/>
            <property name="port" value="6379"/>
            <property name="password" value="${REDIS_PASSWORD}"/>
            <property name="database" value="1"/>
            <property name="userEventStream" value="keycloak:user-events"/>
            <property name="maxLen" value="50000"/>
        </properties>
    </provider>
</spi>
```

### Azure Service Bus

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connectionString` | string | *required* | Azure Service Bus 연결 문자열 |
| `useQueue` | boolean | `false` | Queue 사용 (true) vs Topic 사용 (false) |

#### Destination Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `userEventQueue` | string | `keycloak-user-events` | 사용자 이벤트 Queue (useQueue=true) |
| `adminEventQueue` | string | `keycloak-admin-events` | 관리자 이벤트 Queue (useQueue=true) |
| `userEventTopic` | string | `keycloak-user-events` | 사용자 이벤트 Topic (useQueue=false) |
| `adminEventTopic` | string | `keycloak-admin-events` | 관리자 이벤트 Topic (useQueue=false) |

**Example**:
```xml
<spi name="eventsListener">
    <provider name="azure-event-listener" enabled="true">
        <properties>
            <property name="connectionString"
                value="Endpoint=sb://namespace.servicebus.windows.net/;SharedAccessKeyName=..."/>
            <property name="useQueue" value="false"/>
            <property name="userEventTopic" value="keycloak-events"/>
            <property name="adminEventTopic" value="keycloak-admin-events"/>
        </properties>
    </provider>
</spi>
```

### AWS (SQS/SNS)

#### Connection Settings

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `region` | string | `us-east-1` | AWS 리전 |
| `accessKeyId` | string | - | AWS Access Key (선택, IAM Role 권장) |
| `secretAccessKey` | string | - | AWS Secret Key (선택) |
| `serviceType` | string | `SQS` | 서비스 타입 (`SQS` or `SNS`) |

#### Destination Settings (SQS)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `userEventQueueUrl` | string | *required* | 사용자 이벤트 SQS 큐 URL |
| `adminEventQueueUrl` | string | *required* | 관리자 이벤트 SQS 큐 URL |

#### Destination Settings (SNS)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `userEventTopicArn` | string | *required* | 사용자 이벤트 SNS Topic ARN |
| `adminEventTopicArn` | string | *required* | 관리자 이벤트 SNS Topic ARN |

**Example (SQS)**:
```xml
<spi name="eventsListener">
    <provider name="aws-event-listener" enabled="true">
        <properties>
            <property name="region" value="ap-northeast-2"/>
            <property name="serviceType" value="SQS"/>
            <property name="userEventQueueUrl"
                value="https://sqs.ap-northeast-2.amazonaws.com/123456789/keycloak-events"/>
            <property name="adminEventQueueUrl"
                value="https://sqs.ap-northeast-2.amazonaws.com/123456789/keycloak-admin"/>
        </properties>
    </provider>
</spi>
```

## Environment Variables

System Properties 대신 환경 변수를 사용할 수 있습니다:

```bash
# Kafka 예시
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
export KAFKA_EVENT_TOPIC=keycloak-events
export KAFKA_ENABLE_CIRCUIT_BREAKER=true

# RabbitMQ 예시
export RABBITMQ_HOST=rabbitmq.example.com
export RABBITMQ_PASSWORD=secret
export RABBITMQ_EXCHANGE_NAME=keycloak
```

**명명 규칙**:
- 소문자 → 대문자
- `.` → `_`
- 예: `kafka.bootstrap.servers` → `KAFKA_BOOTSTRAP_SERVERS`

## Realm Attributes

UI를 통해 Realm별로 다른 설정을 적용할 수 있습니다:

1. **Admin Console** → **Realm Settings** → **General** → **Attributes**
2. **Add attribute** 클릭
3. Key/Value 입력:
   - Key: `kafka.bootstrap.servers`
   - Value: `kafka.prod.example.com:9092`

**장점**:
- Keycloak 재시작 불필요
- Realm별 독립 설정
- UI 기반 관리

## Production Configuration Example

```xml
<spi name="eventsListener">
    <!-- Kafka Listener -->
    <provider name="kafka-event-listener" enabled="true">
        <properties>
            <!-- Connection -->
            <property name="bootstrapServers" value="${env.KAFKA_BROKERS:kafka1:9092,kafka2:9092,kafka3:9092}"/>
            <property name="clientId" value="keycloak-production"/>
            <property name="acks" value="all"/>
            <property name="compressionType" value="gzip"/>

            <!-- Topics -->
            <property name="eventTopic" value="keycloak.events.prod"/>
            <property name="adminEventTopic" value="keycloak.admin.prod"/>

            <!-- Event Filtering -->
            <property name="enableUserEvents" value="true"/>
            <property name="enableAdminEvents" value="true"/>
            <property name="excludedEventTypes" value="CODE_TO_TOKEN,REFRESH_TOKEN"/>

            <!-- Circuit Breaker -->
            <property name="enableCircuitBreaker" value="true"/>
            <property name="circuitBreakerFailureThreshold" value="5"/>
            <property name="circuitBreakerOpenTimeoutSeconds" value="60"/>

            <!-- Retry Policy -->
            <property name="enableRetry" value="true"/>
            <property name="maxRetryAttempts" value="3"/>
            <property name="retryInitialDelayMs" value="100"/>
            <property name="retryMaxDelayMs" value="10000"/>

            <!-- Dead Letter Queue -->
            <property name="enableDeadLetterQueue" value="true"/>
            <property name="dlqMaxSize" value="10000"/>
            <property name="dlqPersistToFile" value="true"/>
            <property name="dlqPath" value="/var/keycloak/dlq/kafka"/>

            <!-- Batch Processing (disabled for real-time) -->
            <property name="enableBatching" value="false"/>

            <!-- Prometheus -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9090"/>
        </properties>
    </provider>
</spi>
```

---

**See Also**:
- [Resilience Patterns](resilience.md) - Resilience 설정 상세
- [Monitoring Guide](monitoring.md) - 메트릭 및 알림
- [Architecture Overview](architecture.md) - 시스템 구조
