# Resilience Patterns

프로덕션 환경에서 안정적인 이벤트 처리를 위한 Resilience Patterns 가이드입니다.

## Overview

모든 Event Listener는 4가지 핵심 Resilience Pattern을 지원합니다:

```
Event → Circuit Breaker → Retry Policy → Transport
            ↓                  ↓
        Fail Fast           DLQ (Failure)
```

## 1. Circuit Breaker (장애 전파 방지)

### 동작 원리

Circuit Breaker는 3가지 상태로 장애를 관리합니다:

```
CLOSED (정상)
   ↓ (5회 연속 실패)
OPEN (차단)
   ↓ (60초 대기)
HALF_OPEN (테스트)
   ↓ (2회 성공) or (실패 시)
CLOSED             OPEN
```

### 설정

```xml
<property name="enableCircuitBreaker" value="true"/>
<property name="circuitBreakerFailureThreshold" value="5"/>
<property name="circuitBreakerSuccessThreshold" value="2"/>
<property name="circuitBreakerOpenTimeoutSeconds" value="60"/>
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enableCircuitBreaker` | `true` | Circuit Breaker 활성화 |
| `circuitBreakerFailureThreshold` | `5` | OPEN 상태로 전환할 실패 횟수 |
| `circuitBreakerSuccessThreshold` | `2` | CLOSED 상태로 복귀할 성공 횟수 |
| `circuitBreakerOpenTimeoutSeconds` | `60` | OPEN 상태 유지 시간 (초) |

### 동작 예시

```
시간 00:00 - CLOSED 상태
00:01 - 이벤트 전송 실패 (1/5)
00:02 - 이벤트 전송 실패 (2/5)
00:03 - 이벤트 전송 실패 (3/5)
00:04 - 이벤트 전송 실패 (4/5)
00:05 - 이벤트 전송 실패 (5/5) → OPEN 상태
00:06 ~ 01:05 - 모든 이벤트 즉시 실패 (DLQ 전송)
01:05 - HALF_OPEN 상태 (테스트 시작)
01:06 - 이벤트 전송 성공 (1/2)
01:07 - 이벤트 전송 성공 (2/2) → CLOSED 상태
```

### 장점

- ✅ **Fast Fail**: 장애 발생 시 즉시 실패, 리소스 낭비 방지
- ✅ **자동 복구**: 일정 시간 후 자동으로 재시도
- ✅ **장애 격리**: 메시징 시스템 장애가 Keycloak 성능에 영향 최소화

## 2. Retry Policy (자동 재시도)

### 백오프 전략

#### 1. EXPONENTIAL (권장)
```
Attempt 1: 100ms
Attempt 2: 200ms (100ms × 2)
Attempt 3: 400ms (200ms × 2)
Attempt 4: 800ms (400ms × 2)
Attempt 5: 1,600ms (800ms × 2)
Max: 10,000ms
```

#### 2. EXPONENTIAL_JITTER (대량 재시도 시)
```
Attempt 1: 50-150ms (random)
Attempt 2: 100-300ms (random)
Attempt 3: 200-600ms (random)
```
**목적**: Thundering Herd 방지

#### 3. LINEAR
```
Attempt 1: 100ms
Attempt 2: 200ms (100ms + 100ms)
Attempt 3: 300ms (200ms + 100ms)
```

#### 4. FIXED
```
Attempt 1: 100ms
Attempt 2: 100ms
Attempt 3: 100ms
```

### 설정

```xml
<property name="enableRetry" value="true"/>
<property name="maxRetryAttempts" value="3"/>
<property name="retryInitialDelayMs" value="100"/>
<property name="retryMaxDelayMs" value="10000"/>
<property name="retryBackoffStrategy" value="EXPONENTIAL"/>
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enableRetry` | `true` | 재시도 활성화 |
| `maxRetryAttempts` | `3` | 최대 재시도 횟수 |
| `retryInitialDelayMs` | `100` | 초기 대기 시간 (ms) |
| `retryMaxDelayMs` | `10000` | 최대 대기 시간 (ms) |
| `retryBackoffStrategy` | `EXPONENTIAL` | 백오프 전략 |

### 시나리오별 권장 설정

#### 네트워크 일시 장애
```xml
<property name="maxRetryAttempts" value="5"/>
<property name="retryInitialDelayMs" value="50"/>
<property name="retryBackoffStrategy" value="EXPONENTIAL_JITTER"/>
```

#### 데이터베이스 Lock
```xml
<property name="maxRetryAttempts" value="3"/>
<property name="retryInitialDelayMs" value="500"/>
<property name="retryBackoffStrategy" value="LINEAR"/>
```

#### 외부 API Rate Limit
```xml
<property name="maxRetryAttempts" value="10"/>
<property name="retryInitialDelayMs" value="1000"/>
<property name="retryMaxDelayMs" value="30000"/>
<property name="retryBackoffStrategy" value="EXPONENTIAL"/>
```

## 3. Dead Letter Queue (DLQ)

### 목적

모든 재시도 실패 후 이벤트를 안전하게 보관하여 데이터 손실을 방지합니다.

### 설정

```xml
<property name="enableDeadLetterQueue" value="true"/>
<property name="dlqMaxSize" value="10000"/>
<property name="dlqPersistToFile" value="true"/>
<property name="dlqPath" value="/var/keycloak/dlq/kafka"/>
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enableDeadLetterQueue` | `true` | DLQ 활성화 |
| `dlqMaxSize` | `10000` | 메모리 내 최대 이벤트 수 |
| `dlqPersistToFile` | `false` | 파일로 영속화 여부 |
| `dlqPath` | `./dlq/{transport}` | DLQ 파일 저장 경로 |

### DLQ 엔트리 형식

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "LOGIN",
  "eventData": "{\"type\":\"LOGIN\",\"realmId\":\"master\",\"userId\":\"user123\"}",
  "realm": "master",
  "destination": "keycloak-events",
  "failureReason": "Connection refused: localhost:9092",
  "attemptCount": 3,
  "timestamp": "2025-01-07T10:30:00Z",
  "metadata": {
    "errorClass": "java.net.ConnectException",
    "lastAttempt": "2025-01-07T10:30:05Z"
  }
}
```

### DLQ 재처리

#### 1. 파일에서 읽기
```bash
ls /var/keycloak/dlq/kafka/
cat /var/keycloak/dlq/kafka/dlq-entry-*.json | jq .
```

#### 2. 수동 재전송 (예시)
```bash
#!/bin/bash
# reprocess-dlq.sh

DLQ_DIR="/var/keycloak/dlq/kafka"

for file in $DLQ_DIR/dlq-entry-*.json; do
  EVENT_DATA=$(jq -r '.eventData' "$file")
  DESTINATION=$(jq -r '.destination' "$file")

  # Kafka로 재전송
  echo "$EVENT_DATA" | kafka-console-producer \
    --broker-list localhost:9092 \
    --topic "$DESTINATION"

  # 성공 시 파일 삭제
  rm "$file"
done
```

## 4. Batch Processing (선택적)

### 동작 원리

여러 이벤트를 모아서 한 번에 전송하여 네트워크 오버헤드를 줄입니다.

```
Event 1 ─┐
Event 2 ─┤
Event 3 ─┼→ Batch Buffer → (Size or Time Trigger) → Send Batch
Event 4 ─┤
Event 5 ─┘
```

### 설정

```xml
<property name="enableBatching" value="false"/>
<property name="batchSize" value="100"/>
<property name="batchFlushIntervalMs" value="5000"/>
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `enableBatching` | `false` | 배치 처리 활성화 |
| `batchSize` | `100` | 배치당 최대 이벤트 수 |
| `batchFlushIntervalMs` | `5000` | 최대 대기 시간 (ms) |

### Trade-offs

| | Batch Disabled | Batch Enabled |
|---|----------------|---------------|
| **Latency** | < 10ms | Up to 5s |
| **Throughput** | 1K-5K events/s | 10K-50K events/s |
| **Data Loss Risk** | Low | Medium (crash 시) |
| **Use Case** | 실시간 요구사항 | 대량 이벤트 처리 |

### 권장 설정

#### 실시간 알림
```xml
<property name="enableBatching" value="false"/>
```

#### 대량 분석
```xml
<property name="enableBatching" value="true"/>
<property name="batchSize" value="500"/>
<property name="batchFlushIntervalMs" value="1000"/>
```

## Testing Resilience

### Circuit Breaker 테스트

```bash
# 1. Kafka 중지
docker stop kafka

# 2. 로그인 5회 → Circuit OPEN
# 3. 상태 확인
curl http://localhost:9090/metrics | grep circuit_breaker_state

# 4. Kafka 재시작
docker start kafka

# 5. 60초 대기 → HALF_OPEN → CLOSED
```

### Retry Policy 테스트

```bash
# 로그 모니터링
tail -f keycloak.log | grep "Retry attempt"

# 예상 출력:
# Retry attempt 1/3, delay=100ms, error=Connection refused
# Retry attempt 2/3, delay=200ms, error=Connection refused
# Retry attempt 3/3, delay=400ms, error=Connection refused
# All retries exhausted, sending to DLQ
```

### DLQ 테스트

```bash
# DLQ 크기 확인
curl http://localhost:9090/metrics | grep dlq_size

# DLQ 파일 확인
ls -lh /var/keycloak/dlq/kafka/
cat /var/keycloak/dlq/kafka/dlq-entry-*.json | jq .
```

## Monitoring

### Key Metrics

| Metric | Alert Threshold | Action |
|--------|-----------------|--------|
| `circuit_breaker_state` | `== 1` (OPEN) | Check broker health |
| `events_failed_total` rate | `> 10%` | Investigate errors |
| `dlq_size` | `> 5000` | Process DLQ, increase capacity |
| `retry_attempts_total` | Increasing | Review retry config |

### Prometheus Alert Example

```yaml
- alert: CircuitBreakerOpen
  expr: keycloak_circuit_breaker_state > 0
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Event listener circuit breaker is open"

- alert: DLQFilling
  expr: keycloak_dlq_size > 5000
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: "DLQ size exceeds 5000 events"
```

## Best Practices

### ✅ Do

1. **항상 DLQ 활성화**: 데이터 손실 방지
2. **Circuit Breaker 기본값 사용**: 대부분의 경우 적절함
3. **파일 영속화 (프로덕션)**: `dlqPersistToFile=true`
4. **메트릭 모니터링**: Prometheus + Grafana 설정
5. **정기 DLQ 점검**: 주 1회 이상

### ❌ Don't

1. **모든 패턴 비활성화**: 장애 시 이벤트 손실
2. **Retry 무한 시도**: `maxRetryAttempts=999` 같은 설정 금지
3. **Batch + 실시간**: 레이턴시 요구사항과 충돌
4. **DLQ 방치**: 크기 제한 없이 무한 증가

---

**See Also**:
- [Configuration Guide](configuration.md) - 상세 설정 옵션
- [Monitoring Guide](monitoring.md) - 메트릭 및 알림
- [Troubleshooting](troubleshooting.md) - 일반적인 문제 해결
