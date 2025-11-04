# Keycloak Event Listeners - Examples & Testing

이 디렉토리는 Keycloak Event Listeners의 설정 예제와 테스트 환경을 제공합니다.

## 📁 파일 구조

```
examples/
├── docker-compose.yml           # 전체 스택 Docker Compose
├── prometheus.yml               # Prometheus 설정
├── grafana-datasource.yml       # Grafana 데이터소스 설정
├── standalone-kafka.xml         # Kafka 리스너 Keycloak 설정
├── standalone-rabbitmq.xml      # RabbitMQ 리스너 Keycloak 설정
├── standalone-nats.xml          # NATS 리스너 Keycloak 설정
└── README.md                    # 이 파일
```

## 🚀 빠른 시작

### 1. 사전 준비

```bash
# Event listener JAR 파일 빌드
cd ..
./gradlew :events:event-listener-kafka:build
./gradlew :events:event-listener-rabbitmq:build
./gradlew :events:event-listener-nats:build

# examples 디렉토리로 이동
cd examples
```

### 2. Docker Compose로 전체 스택 실행

```bash
# 전체 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f keycloak

# 특정 서비스만 시작 (예: Kafka만)
docker-compose up -d postgres zookeeper kafka keycloak prometheus grafana
```

### 3. 서비스 접속

| 서비스 | URL | 계정 |
|--------|-----|------|
| Keycloak | http://localhost:8080 | admin / admin |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9000 | - |
| RabbitMQ Management | http://localhost:15672 | guest / guest |
| Kafka UI | http://localhost:8090 | - |
| NATS Monitoring | http://localhost:8222 | - |

### 4. Event Listener 설정

#### Keycloak Admin Console에서:

1. **Master Realm** 선택
2. **Realm Settings** → **Events** → **Event Config**
3. **Event Listeners** 드롭다운에서 원하는 리스너 선택:
   - `kafka-event-listener`
   - `rabbitmq-event-listener`
   - `nats-event-listener`
4. **Save**

#### 또는 환경 변수로 설정:

```yaml
# docker-compose.yml 에서
environment:
  # Kafka
  - KC_SPI_EVENTS_LISTENER_KAFKA_EVENT_LISTENER_ENABLED=true
  - KC_SPI_EVENTS_LISTENER_KAFKA_EVENT_LISTENER_BOOTSTRAP_SERVERS=kafka:9092

  # RabbitMQ
  - KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_ENABLED=true
  - KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_HOST=rabbitmq
```

## 🧪 테스트

### 1. 이벤트 생성

```bash
# Keycloak에 로그인하여 이벤트 생성
# 또는 REST API 사용

# 사용자 생성 (Admin Event 발생)
curl -X POST http://localhost:8080/admin/realms/master/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "enabled": true,
    "email": "test@example.com"
  }'

# 로그인 시도 (User Event 발생)
# Keycloak UI에서 로그인
```

### 2. Kafka에서 이벤트 확인

```bash
# Kafka 토픽 리스트 확인
docker exec -it examples_kafka_1 kafka-topics --list --bootstrap-server localhost:9092

# 이벤트 소비
docker exec -it examples_kafka_1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic keycloak-events \
  --from-beginning \
  --property print.key=true

# 예상 출력:
# master:LOGIN:user-123 {"id":"...","type":"LOGIN",...}
```

### 3. RabbitMQ에서 이벤트 확인

```bash
# RabbitMQ Management UI
# http://localhost:15672

# 또는 CLI로 확인
docker exec examples_rabbitmq_1 rabbitmqctl list_exchanges
docker exec examples_rabbitmq_1 rabbitmqctl list_queues
```

### 4. NATS에서 이벤트 확인

```bash
# NATS CLI 설치 필요
nats sub "keycloak.events.user.>"

# 또는 NATS Monitoring
# http://localhost:8222/varz
```

### 5. Prometheus 메트릭 확인

```bash
# Kafka 리스너 메트릭
curl http://localhost:9090/metrics | grep keycloak_events

# 주요 메트릭:
# keycloak_events_total
# keycloak_events_failed_total
# keycloak_circuit_breaker_state
# keycloak_dlq_size
# keycloak_batch_processor_buffer_size
```

### 6. Grafana 대시보드

1. http://localhost:3000 접속
2. 좌측 메뉴 **Dashboards** 선택
3. **Keycloak Event Listeners - Resilience Monitoring** 대시보드 확인

## 🔧 Resilience Patterns 테스트

### Circuit Breaker 테스트

```bash
# 1. Kafka 중지
docker-compose stop kafka

# 2. Keycloak에서 이벤트 생성 (5개 이상)
# Circuit Breaker가 OPEN 상태로 변경됨

# 3. Prometheus에서 확인
curl http://localhost:9090/metrics | grep keycloak_circuit_breaker_state
# keycloak_circuit_breaker_state{listener="kafka"} 1.0  # OPEN

# 4. Kafka 재시작
docker-compose start kafka

# 5. 60초 후 Circuit Breaker가 HALF_OPEN → CLOSED로 전환
```

### Retry Policy 테스트

```bash
# Keycloak 로그에서 재시도 확인
docker-compose logs -f keycloak | grep "Retry attempt"

# 예상 출력:
# Retry attempt 1 for event type=LOGIN, delay=100ms
# Retry attempt 2 for event type=LOGIN, delay=200ms
# Retry attempt 3 for event type=LOGIN, delay=400ms
```

### Dead Letter Queue 테스트

```bash
# DLQ 크기 확인
curl http://localhost:9090/metrics | grep keycloak_dlq_size

# DLQ 파일 확인 (파일 저장 활성화 시)
docker exec examples_keycloak_1 ls -lh /opt/keycloak/dlq/kafka/
docker exec examples_keycloak_1 cat /opt/keycloak/dlq/kafka/dlq-entry-*.json
```

### Batch Processing 테스트

```bash
# docker-compose.yml에서 배치 활성화
environment:
  - KC_SPI_EVENTS_LISTENER_KAFKA_EVENT_LISTENER_ENABLE_BATCHING=true
  - KC_SPI_EVENTS_LISTENER_KAFKA_EVENT_LISTENER_BATCH_SIZE=10

# 컨테이너 재시작
docker-compose restart keycloak

# 이벤트 10개 이상 빠르게 생성
# 로그에서 배치 처리 확인
docker-compose logs keycloak | grep "Flushing batch"
```

## 🐛 문제 해결

### Keycloak이 시작되지 않는 경우

```bash
# 로그 확인
docker-compose logs keycloak

# 일반적인 원인:
# 1. JAR 파일 경로 확인
ls -lh ../event-listener-*/build/libs/*.jar

# 2. 데이터베이스 연결 확인
docker-compose logs postgres

# 3. 포트 충돌 확인
netstat -tuln | grep -E '8080|9090|9091|9092'
```

### 메시지 브로커 연결 실패

```bash
# Kafka 상태 확인
docker-compose ps kafka
docker-compose logs kafka

# RabbitMQ 상태 확인
docker-compose ps rabbitmq
docker-compose logs rabbitmq

# NATS 상태 확인
docker-compose ps nats
docker-compose logs nats

# 네트워크 확인
docker network inspect examples_keycloak-network
```

### Prometheus가 메트릭을 수집하지 못하는 경우

```bash
# Prometheus targets 확인
curl http://localhost:9000/targets

# 메트릭 엔드포인트 직접 확인
curl http://localhost:9090/metrics
curl http://localhost:9091/metrics
curl http://localhost:9092/metrics
```

## 🧹 정리

```bash
# 모든 컨테이너 중지 및 삭제
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v

# 네트워크 정리
docker network prune
```

## 📚 추가 문서

- [Resilience Patterns 완전 가이드](../RESILIENCE_PATTERNS.md)
- [Prometheus 메트릭 가이드](../event-listener-common/PROMETHEUS.md)
- [Kafka Listener README](../event-listener-kafka/README.md)
- [RabbitMQ Listener README](../event-listener-rabbitmq/README.md)
- [NATS Listener README](../event-listener-nats/README.md)

## 💡 프로덕션 배포 팁

### 1. 리소스 할당

```yaml
# docker-compose.yml
services:
  keycloak:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 2. 로그 관리

```yaml
# 로그 로테이션 설정
services:
  keycloak:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 3. 헬스체크

```yaml
services:
  keycloak:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

### 4. 환경별 설정 분리

```bash
# 개발 환경
docker-compose -f docker-compose.yml up

# 프로덕션 환경
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up
```

## 🔗 유용한 링크

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [NATS Documentation](https://docs.nats.io/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
