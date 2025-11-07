# Quick Start Guide

5분 안에 Keycloak Event Listener를 설정하고 실행하는 가이드입니다.

## Prerequisites

- Keycloak 26.0.7+
- Java 21+
- 메시징 시스템 (Kafka, RabbitMQ, NATS 중 하나)

## Step 1: Download JAR

```bash
# GitHub Releases에서 다운로드
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.3/event-listener-kafka-all.jar

# Keycloak providers 디렉토리로 복사
cp event-listener-kafka-all.jar $KEYCLOAK_HOME/providers/
```

## Step 2: Configure Keycloak

`$KEYCLOAK_HOME/conf/keycloak.conf`:

```properties
# Kafka Event Listener
spi-events-listener-kafka-bootstrap-servers=localhost:9092
spi-events-listener-kafka-event-topic=keycloak-events
spi-events-listener-kafka-enable-user-events=true
spi-events-listener-kafka-enable-admin-events=true
```

**또는** `standalone.xml` (Wildfly/Legacy):

```xml
<spi name="eventsListener">
    <provider name="kafka-event-listener" enabled="true">
        <properties>
            <property name="bootstrapServers" value="localhost:9092"/>
            <property name="eventTopic" value="keycloak-events"/>
            <property name="enableUserEvents" value="true"/>
            <property name="enableAdminEvents" value="true"/>
        </properties>
    </provider>
</spi>
```

## Step 3: Build Keycloak

```bash
cd $KEYCLOAK_HOME
bin/kc.sh build
```

## Step 4: Enable in Realm

1. **Admin Console** 접속
2. **Realm Settings** → **Events** → **Event Listeners**
3. **kafka-event-listener** 체크
4. **Save**

## Step 5: Test

### 5.1 Start Kafka (Docker)

```bash
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_ENABLE_KRAFT=yes \
  -e KAFKA_CFG_NODE_ID=1 \
  -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  bitnami/kafka:latest
```

### 5.2 Start Keycloak

```bash
bin/kc.sh start-dev
```

### 5.3 Consume Events

```bash
# 이벤트 수신 대기
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic keycloak-events \
  --from-beginning
```

### 5.4 Trigger Event

1. Keycloak Admin Console에서 로그아웃
2. 다시 로그인
3. Kafka Consumer에서 이벤트 확인:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "security-admin-console",
  "userId": "admin",
  "ipAddress": "127.0.0.1",
  "timestamp": 1704614400000,
  "details": {
    "username": "admin",
    "auth_method": "openid-connect"
  }
}
```

## Next Steps

### Monitor with Prometheus

```bash
# 메트릭 확인
curl http://localhost:9090/metrics | grep keycloak_events

# 예상 출력:
# keycloak_events_total{event_type="LOGIN"} 1
# keycloak_circuit_breaker_state 0
# keycloak_dlq_size 0
```

### Enable Resilience Features

```properties
# Circuit Breaker (장애 전파 방지)
spi-events-listener-kafka-enable-circuit-breaker=true
spi-events-listener-kafka-circuit-breaker-failure-threshold=5

# Retry Policy (자동 재시도)
spi-events-listener-kafka-enable-retry=true
spi-events-listener-kafka-max-retry-attempts=3

# Dead Letter Queue (실패 이벤트 보관)
spi-events-listener-kafka-enable-dead-letter-queue=true
spi-events-listener-kafka-dlq-persist-to-file=true
spi-events-listener-kafka-dlq-path=/var/keycloak/dlq/kafka
```

### Configure for Production

```properties
# Kafka Cluster
spi-events-listener-kafka-bootstrap-servers=kafka1:9092,kafka2:9092,kafka3:9092

# Topics
spi-events-listener-kafka-event-topic=prod-keycloak-events
spi-events-listener-kafka-admin-event-topic=prod-keycloak-admin

# Reliability
spi-events-listener-kafka-acks=all
spi-events-listener-kafka-compression-type=gzip

# Event Filtering
spi-events-listener-kafka-included-event-types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE
```

## Alternative Transports

### RabbitMQ

```bash
# JAR 다운로드
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.3/event-listener-rabbitmq-all.jar
cp event-listener-rabbitmq-all.jar $KEYCLOAK_HOME/providers/

# 설정
spi-events-listener-rabbitmq-host=localhost
spi-events-listener-rabbitmq-port=5672
spi-events-listener-rabbitmq-username=guest
spi-events-listener-rabbitmq-password=guest
spi-events-listener-rabbitmq-exchange-name=keycloak-events
```

### NATS

```bash
# JAR 다운로드
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.3/event-listener-nats-all.jar
cp event-listener-nats-all.jar $KEYCLOAK_HOME/providers/

# 설정
spi-events-listener-nats-server-url=nats://localhost:4222
spi-events-listener-nats-user-event-subject=keycloak.events.user
spi-events-listener-nats-admin-event-subject=keycloak.events.admin
```

### Redis Streams

```bash
# JAR 다운로드
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.3/event-listener-redis-all.jar
cp event-listener-redis-all.jar $KEYCLOAK_HOME/providers/

# 설정
spi-events-listener-redis-host=localhost
spi-events-listener-redis-port=6379
spi-events-listener-redis-user-event-stream=keycloak:events:user
```

## Docker Compose Example

`docker-compose.yml`:

```yaml
version: '3'
services:
  kafka:
    image: bitnami/kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_ENABLE_KRAFT: "yes"
      KAFKA_CFG_PROCESS_ROLES: "broker,controller"
      KAFKA_CFG_LISTENERS: "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_CFG_ADVERTISED_LISTENERS: "PLAINTEXT://localhost:9092"

  keycloak:
    image: quay.io/keycloak/keycloak:26.0.7
    ports:
      - "8080:8080"
    volumes:
      - ./event-listener-kafka-all.jar:/opt/keycloak/providers/event-listener-kafka-all.jar
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
      KC_SPI_EVENTS_LISTENER_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      KC_SPI_EVENTS_LISTENER_KAFKA_EVENT_TOPIC: keycloak-events
    command: start-dev
    depends_on:
      - kafka

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

`prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'keycloak-events'
    static_configs:
      - targets: ['keycloak:9090']
```

## Troubleshooting

### Events not appearing?

```bash
# 1. Check Keycloak logs
docker logs keycloak | grep EventListener

# 2. Check Realm settings
# Admin Console → Realm → Events → Event Listeners → kafka-event-listener 체크 확인

# 3. Check Kafka connection
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# 4. Check metrics
curl http://localhost:9090/metrics | grep keycloak_events_total
```

### Circuit Breaker OPEN?

```bash
# Kafka 상태 확인
docker ps | grep kafka

# 메트릭 확인
curl http://localhost:9090/metrics | grep circuit_breaker_state

# Kafka 재시작
docker restart kafka

# Keycloak 재시작 (Circuit Breaker 리셋)
docker restart keycloak
```

---

**See Also**:
- [Configuration Guide](configuration.md) - 전체 설정 옵션
- [Architecture Overview](architecture.md) - 시스템 구조
- [Troubleshooting](troubleshooting.md) - 상세 문제 해결
