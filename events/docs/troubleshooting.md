# Troubleshooting Guide

Event Listener 모듈에서 발생하는 일반적인 문제와 해결 방법을 설명합니다.

## Quick Diagnostics

### Step 1: Check Event Listener Status

```bash
# Keycloak 로그 확인
tail -f /opt/keycloak/data/log/keycloak.log | grep EventListener

# 정상 출력 예시:
# INFO  [o.s.k.e.k.KafkaEventListenerProviderFactory] Kafka event listener initialized
# INFO  [o.s.k.e.k.KafkaEventListenerProvider] Event sent successfully: LOGIN
```

### Step 2: Check Circuit Breaker State

```bash
curl http://localhost:9090/metrics | grep circuit_breaker_state

# 0 = CLOSED (정상)
# 1 = OPEN (장애)
# 2 = HALF_OPEN (복구 시도 중)
```

### Step 3: Check DLQ Size

```bash
curl http://localhost:9090/metrics | grep dlq_size

# > 0 이면 실패한 이벤트 존재
# > 5000 이면 조치 필요
# > 9000 이면 긴급 조치 필요
```

## Common Issues

### 1. "Circuit breaker is OPEN" Error

**증상**:
```
ERROR [o.s.k.e.k.KafkaEventListenerProvider] Circuit breaker is OPEN, sending event to DLQ
```

**원인**:
- 메시징 시스템(Kafka, RabbitMQ 등) 연결 불가
- 5회 연속 전송 실패로 Circuit Breaker가 OPEN 상태로 전환

**해결**:

1. **메시징 시스템 상태 확인**:
```bash
# Kafka
docker ps | grep kafka
kafka-topics.sh --bootstrap-server localhost:9092 --list

# RabbitMQ
rabbitmqctl status
rabbitmqadmin list exchanges
```

2. **연결 정보 확인**:
```bash
# Kafka 예시
grep "bootstrapServers" standalone.xml
telnet localhost 9092
```

3. **Circuit Breaker 수동 리셋** (재시작):
```bash
systemctl restart keycloak
```

4. **임계값 조정** (자주 OPEN 되는 경우):
```xml
<!-- 더 관대하게 -->
<property name="circuitBreakerFailureThreshold" value="10"/>
<property name="circuitBreakerOpenTimeoutSeconds" value="120"/>
```

### 2. "Dead Letter Queue is full" Error

**증상**:
```
ERROR [o.s.k.e.c.d.DeadLetterQueue] DLQ is full (10000/10000), dropping event
```

**원인**:
- 메시징 시스템 장기 장애
- DLQ가 처리되지 않고 계속 쌓임

**해결**:

1. **DLQ 크기 확인**:
```bash
curl http://localhost:9090/metrics | grep dlq_size
```

2. **DLQ 파일 확인** (`dlqPersistToFile=true` 인 경우):
```bash
ls -lh /var/keycloak/dlq/kafka/
cat /var/keycloak/dlq/kafka/dlq-entry-*.json | jq .
```

3. **메시징 시스템 복구 후 DLQ 재처리**:
```bash
# 수동 재전송 스크립트 (예시)
for file in /var/keycloak/dlq/kafka/*.json; do
  jq -r '.eventData' "$file" | \
    kafka-console-producer --broker-list localhost:9092 --topic keycloak-events
  rm "$file"
done
```

4. **DLQ 크기 증가** (임시 방편):
```xml
<property name="dlqMaxSize" value="50000"/>
<property name="dlqPersistToFile" value="true"/>
<property name="dlqPath" value="/var/keycloak/dlq/kafka"/>
```

### 3. "Connection refused" Error

**증상**:
```
ERROR [o.s.k.e.k.KafkaConnectionManager] Failed to send event: Connection refused
```

**원인**:
- 메시징 시스템이 실행되지 않음
- 잘못된 호스트/포트 설정
- 방화벽 차단

**해결**:

1. **메시징 시스템 실행 확인**:
```bash
# Kafka
docker ps | grep kafka
netstat -tlnp | grep 9092

# RabbitMQ
docker ps | grep rabbitmq
netstat -tlnp | grep 5672
```

2. **연결 테스트**:
```bash
# Kafka
telnet localhost 9092

# RabbitMQ
telnet localhost 5672
```

3. **설정 확인**:
```bash
grep -A 5 "kafka-event-listener" standalone.xml
```

4. **방화벽 확인**:
```bash
# Linux
sudo iptables -L | grep 9092

# 포트 열기
sudo ufw allow 9092/tcp
```

### 4. "All retry attempts exhausted" Error

**증상**:
```
WARN [o.s.k.e.c.r.RetryPolicy] All retry attempts exhausted for event type=LOGIN
```

**원인**:
- 일시적 장애가 재시도 횟수 내에 복구되지 않음
- 재시도 간격이 너무 짧음

**해결**:

1. **재시도 설정 확인**:
```bash
grep -E "maxRetryAttempts|retryInitialDelayMs|retryMaxDelayMs" standalone.xml
```

2. **재시도 횟수 증가**:
```xml
<property name="maxRetryAttempts" value="5"/>
<property name="retryInitialDelayMs" value="200"/>
<property name="retryMaxDelayMs" value="30000"/>
```

3. **백오프 전략 변경**:
```xml
<!-- 더 완만하게 증가 -->
<property name="retryBackoffStrategy" value="LINEAR"/>

<!-- 랜덤 지터로 부하 분산 -->
<property name="retryBackoffStrategy" value="EXPONENTIAL_JITTER"/>
```

### 5. "No events being sent" Issue

**증상**:
- Keycloak에서 로그인/로그아웃 발생
- 메트릭에서 `keycloak_events_total` 증가 없음
- 메시징 시스템에 메시지 도착 안 함

**원인**:
- Event Listener가 비활성화됨
- 이벤트 필터링으로 모든 이벤트 제외
- Realm에서 Event Listener 미설정

**해결**:

1. **SPI 활성화 확인**:
```xml
<provider name="kafka-event-listener" enabled="true">
```

2. **Realm 설정 확인**:
   - Admin Console → Realm → Events → Event Listeners
   - `kafka-event-listener` 체크되어 있는지 확인

3. **이벤트 필터 확인**:
```xml
<property name="enableUserEvents" value="true"/>
<property name="enableAdminEvents" value="true"/>
<!-- includedEventTypes가 너무 제한적이지 않은지 확인 -->
```

4. **로그 확인**:
```bash
tail -f keycloak.log | grep "Event sent\|Event filtered"
```

### 6. High Memory Usage

**증상**:
- Keycloak JVM 메모리 사용량 지속 증가
- OutOfMemoryError 발생

**원인**:
- DLQ가 메모리에 너무 많은 이벤트 보관
- Batch 버퍼가 flush되지 않고 계속 쌓임

**해결**:

1. **메모리 사용량 확인**:
```bash
curl http://localhost:9090/metrics | grep jvm_memory_used_bytes
curl http://localhost:9090/metrics | grep dlq_size
curl http://localhost:9090/metrics | grep batch_processor_buffer_size
```

2. **DLQ 파일 영속화**:
```xml
<property name="dlqPersistToFile" value="true"/>
<property name="dlqPath" value="/var/keycloak/dlq/kafka"/>
```

3. **Batch 크기 감소**:
```xml
<property name="batchSize" value="50"/>
<property name="batchFlushIntervalMs" value="1000"/>
```

4. **JVM 힙 증가**:
```bash
# standalone.conf
JAVA_OPTS="$JAVA_OPTS -Xms2g -Xmx4g"
```

### 7. Slow Event Processing

**증상**:
- P95 레이턴시 > 1초
- 사용자 로그인 지연 체감

**원인**:
- 메시징 시스템 응답 느림
- Batch 처리로 인한 flush interval 대기
- 과도한 재시도

**해결**:

1. **레이턴시 확인**:
```bash
curl http://localhost:9090/metrics | \
  grep keycloak_events_processing_duration_seconds | \
  grep quantile
```

2. **Batch 비활성화** (실시간 요구사항):
```xml
<property name="enableBatching" value="false"/>
```

3. **재시도 간격 단축**:
```xml
<property name="retryInitialDelayMs" value="50"/>
<property name="retryMaxDelayMs" value="1000"/>
```

4. **메시징 시스템 성능 튜닝**:
```bash
# Kafka 예시
compression.type=gzip
linger.ms=0
acks=1
```

## Debugging Tips

### Enable Debug Logging

`standalone.xml`:
```xml
<logger category="org.scriptonbasestar.kcexts.events">
    <level name="DEBUG"/>
</logger>
```

**Output**:
```
DEBUG [o.s.k.e.k.KafkaEventListenerProvider] Processing event: LOGIN
DEBUG [o.s.k.e.c.r.CircuitBreaker] Circuit breaker state: CLOSED
DEBUG [o.s.k.e.k.KafkaConnectionManager] Sending to topic: keycloak-events
DEBUG [o.s.k.e.k.KafkaEventMetrics] Recorded sent event: LOGIN
```

### Inspect Event Details

```bash
# Keycloak 로그에서 이벤트 JSON 추출
tail -f keycloak.log | grep "eventData" | jq .

# DLQ 파일 분석
cat /var/keycloak/dlq/kafka/dlq-entry-*.json | \
  jq -r '[.eventType, .failureReason] | @csv'
```

### Test Event Sending Manually

```bash
# Kafka
echo '{"type":"LOGIN","userId":"test"}' | \
  kafka-console-producer --broker-list localhost:9092 --topic keycloak-events

# RabbitMQ
rabbitmqadmin publish exchange=keycloak-events routing_key=keycloak.event.user \
  payload='{"type":"LOGIN","userId":"test"}'
```

## Performance Tuning

### High Throughput (> 10K events/sec)

```xml
<!-- Batch 활성화 -->
<property name="enableBatching" value="true"/>
<property name="batchSize" value="500"/>
<property name="batchFlushIntervalMs" value="1000"/>

<!-- Circuit Breaker 완화 -->
<property name="circuitBreakerFailureThreshold" value="10"/>

<!-- 재시도 감소 -->
<property name="maxRetryAttempts" value="2"/>
```

### Low Latency (< 10ms)

```xml
<!-- Batch 비활성화 -->
<property name="enableBatching" value="false"/>

<!-- 재시도 간격 단축 -->
<property name="retryInitialDelayMs" value="10"/>
<property name="retryMaxDelayMs" value="100"/>

<!-- Fast Fail -->
<property name="circuitBreakerFailureThreshold" value="3"/>
<property name="circuitBreakerOpenTimeoutSeconds" value="30"/>
```

### Reliability (Zero Data Loss)

```xml
<!-- DLQ 파일 영속화 -->
<property name="dlqPersistToFile" value="true"/>
<property name="dlqMaxSize" value="100000"/>

<!-- 적극적인 재시도 -->
<property name="maxRetryAttempts" value="10"/>
<property name="retryMaxDelayMs" value="60000"/>

<!-- Kafka acks=all -->
<property name="acks" value="all"/>
```

## Getting Help

### Collect Diagnostic Information

```bash
#!/bin/bash
# diagnostic-report.sh

REPORT_DIR="diagnostic-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$REPORT_DIR"

# Keycloak 로그
tail -1000 /opt/keycloak/data/log/keycloak.log > "$REPORT_DIR/keycloak.log"

# 메트릭
curl http://localhost:9090/metrics > "$REPORT_DIR/metrics.txt"

# 설정
grep -A 50 "kafka-event-listener" standalone.xml > "$REPORT_DIR/config.xml"

# DLQ (있는 경우)
ls -lh /var/keycloak/dlq/ > "$REPORT_DIR/dlq-files.txt"

# 압축
tar -czf "$REPORT_DIR.tar.gz" "$REPORT_DIR"
echo "Report created: $REPORT_DIR.tar.gz"
```

### Support Channels

- **GitHub Issues**: [sb-keycloak-exts/issues](https://github.com/scriptonbasestar/sb-keycloak-exts/issues)
- **Documentation**: [docs/](.)
- **Slack**: #keycloak-extensions

---

**See Also**:
- [Monitoring Guide](monitoring.md) - 메트릭 및 알림
- [Resilience Patterns](resilience.md) - Circuit Breaker, DLQ 상세
- [Configuration Guide](configuration.md) - 설정 옵션
