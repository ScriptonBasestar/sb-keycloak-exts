# Monitoring Guide

Event Listener 모듈의 Prometheus 메트릭 및 Grafana 대시보드 가이드입니다.

## Prometheus Metrics

### Metrics Endpoint

각 Event Listener는 독립적인 Prometheus 엔드포인트를 노출합니다:

```
GET http://localhost:{port}/metrics
```

**기본 포트**:
- Kafka: `9090`
- RabbitMQ: `9091`
- NATS: `9092`
- Redis: `9093`
- Azure: `9094`
- AWS: `9095`

### Core Event Metrics

#### keycloak_events_total
**Type**: Counter
**Description**: 총 처리된 이벤트 수

**Labels**:
- `event_type`: 이벤트 타입 (LOGIN, LOGOUT, etc)
- `realm`: Keycloak Realm
- `destination`: 대상 Topic/Queue/Subject

**Example**:
```
keycloak_events_total{event_type="LOGIN",realm="master",destination="keycloak-events"} 15234
keycloak_events_total{event_type="LOGOUT",realm="master",destination="keycloak-events"} 8521
```

#### keycloak_events_failed_total
**Type**: Counter
**Description**: 실패한 이벤트 수

**Labels**:
- `event_type`: 이벤트 타입
- `realm`: Keycloak Realm
- `destination`: 대상
- `error_type`: 에러 클래스명

**Example**:
```
keycloak_events_failed_total{event_type="LOGIN",error_type="ConnectException"} 42
```

#### keycloak_events_processing_duration_seconds
**Type**: Histogram
**Description**: 이벤트 처리 시간 분포

**Buckets**: 0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0

**Example**:
```
keycloak_events_processing_duration_seconds_bucket{le="0.01"} 12500
keycloak_events_processing_duration_seconds_bucket{le="0.1"} 14800
keycloak_events_processing_duration_seconds_sum 145.6
keycloak_events_processing_duration_seconds_count 15000
```

### Resilience Metrics

#### keycloak_circuit_breaker_state
**Type**: Gauge
**Description**: Circuit Breaker 상태

**Values**:
- `0` = CLOSED (정상)
- `1` = OPEN (차단)
- `2` = HALF_OPEN (테스트)

**Example**:
```
keycloak_circuit_breaker_state{transport="kafka"} 0
```

#### keycloak_dlq_size
**Type**: Gauge
**Description**: Dead Letter Queue 크기

**Example**:
```
keycloak_dlq_size{transport="kafka"} 142
```

#### keycloak_retry_attempts_total
**Type**: Counter
**Description**: 재시도 시도 횟수

**Labels**:
- `attempt`: 재시도 횟수 (1, 2, 3)
- `success`: 재시도 성공 여부

**Example**:
```
keycloak_retry_attempts_total{attempt="1",success="true"} 234
keycloak_retry_attempts_total{attempt="2",success="false"} 45
```

#### keycloak_batch_processor_buffer_size
**Type**: Gauge
**Description**: 현재 Batch 버퍼 크기

**Example**:
```
keycloak_batch_processor_buffer_size{transport="kafka"} 23
```

### JVM Metrics (Optional)

`enableJvmMetrics=true` 설정 시 추가 메트릭:

- `jvm_memory_used_bytes`
- `jvm_memory_max_bytes`
- `jvm_gc_collection_seconds`
- `jvm_threads_current`
- `jvm_threads_daemon`

## Prometheus Configuration

### Scrape Config

`prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'keycloak-events'
    static_configs:
      - targets:
          - 'localhost:9090'  # Kafka
          - 'localhost:9091'  # RabbitMQ
          - 'localhost:9092'  # NATS
    metrics_path: '/metrics'
    scrape_interval: 15s
```

### Alert Rules

`alerts.yml`:
```yaml
groups:
  - name: keycloak_events
    interval: 30s
    rules:
      # Circuit Breaker OPEN
      - alert: CircuitBreakerOpen
        expr: keycloak_circuit_breaker_state == 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Circuit breaker is OPEN"
          description: "{{ $labels.transport }} circuit breaker has been open for 2 minutes"

      # High Failure Rate
      - alert: HighEventFailureRate
        expr: |
          (
            rate(keycloak_events_failed_total[5m])
            /
            rate(keycloak_events_total[5m])
          ) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High event failure rate"
          description: "Failure rate: {{ $value | humanizePercentage }} in last 5 minutes"

      # DLQ Filling Up
      - alert: DLQFillingUp
        expr: keycloak_dlq_size > 5000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Dead Letter Queue filling up"
          description: "DLQ size: {{ $value }} events (threshold: 5000)"

      # DLQ Nearly Full
      - alert: DLQNearlyFull
        expr: keycloak_dlq_size > 9000
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Dead Letter Queue nearly full"
          description: "DLQ size: {{ $value }} events (max: 10000)"

      # No Events Processed
      - alert: NoEventsProcessed
        expr: increase(keycloak_events_total[5m]) == 0
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "No events processed in 15 minutes"
          description: "Event listener may be stuck or disabled"

      # Slow Event Processing
      - alert: SlowEventProcessing
        expr: |
          histogram_quantile(0.95,
            rate(keycloak_events_processing_duration_seconds_bucket[5m])
          ) > 1.0
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Slow event processing detected"
          description: "P95 latency: {{ $value }}s (threshold: 1s)"
```

## Grafana Dashboard

### Dashboard Import

1. **Grafana UI** → **Dashboards** → **Import**
2. Upload `grafana-dashboard.json`
3. Select Prometheus data source
4. Click **Import**

### Key Panels

#### 1. Event Throughput
```promql
# Events per second
rate(keycloak_events_total[1m])
```

#### 2. Failure Rate
```promql
# Failure percentage
(
  rate(keycloak_events_failed_total[5m])
  /
  rate(keycloak_events_total[5m])
) * 100
```

#### 3. Circuit Breaker State
```promql
# Timeline of state changes
keycloak_circuit_breaker_state
```

#### 4. DLQ Size
```promql
# Current DLQ size
keycloak_dlq_size
```

#### 5. Processing Latency (P50, P95, P99)
```promql
# P50
histogram_quantile(0.50,
  rate(keycloak_events_processing_duration_seconds_bucket[5m])
)

# P95
histogram_quantile(0.95,
  rate(keycloak_events_processing_duration_seconds_bucket[5m])
)

# P99
histogram_quantile(0.99,
  rate(keycloak_events_processing_duration_seconds_bucket[5m])
)
```

#### 6. Top Error Types
```promql
# Error count by type
topk(10,
  sum by (error_type) (
    rate(keycloak_events_failed_total[5m])
  )
)
```

### Dashboard Variables

- `$transport`: Kafka, RabbitMQ, NATS, etc
- `$realm`: Keycloak realm name
- `$event_type`: LOGIN, LOGOUT, etc

## Health Checks

### Liveness Probe

Circuit Breaker가 OPEN 상태가 아닌지 확인:

```bash
#!/bin/bash
# health-check.sh

STATUS=$(curl -s http://localhost:9090/metrics | \
  grep 'keycloak_circuit_breaker_state' | \
  awk '{print $2}')

if [ "$STATUS" == "1" ]; then
  echo "Circuit breaker is OPEN"
  exit 1
else
  echo "OK"
  exit 0
fi
```

### Readiness Probe

DLQ가 가득 차지 않았는지 확인:

```bash
#!/bin/bash
# readiness-check.sh

DLQ_SIZE=$(curl -s http://localhost:9090/metrics | \
  grep 'keycloak_dlq_size' | \
  awk '{print $2}')

if [ "$DLQ_SIZE" -gt 9000 ]; then
  echo "DLQ nearly full: $DLQ_SIZE"
  exit 1
else
  echo "OK"
  exit 0
fi
```

## Monitoring Best Practices

### ✅ Do

1. **알림 설정**: Circuit Breaker OPEN, DLQ 크기 증가
2. **대시보드 공유**: 팀 전체가 접근 가능하도록
3. **추세 분석**: 주간/월간 이벤트 처리량 분석
4. **SLA 모니터링**: P95 레이턴시 < 100ms 유지

### ❌ Don't

1. **메트릭 과다 수집**: 모든 이벤트 타입별 메트릭 불필요
2. **알림 피로**: 임계값을 너무 낮게 설정
3. **대시보드 방치**: 정기적으로 업데이트 필요

## Troubleshooting with Metrics

### 시나리오 1: 이벤트 처리 중단

**증상**:
```promql
increase(keycloak_events_total[5m]) == 0
```

**원인**:
- Keycloak Event Listener 비활성화
- 모든 이벤트가 필터링됨
- Circuit Breaker OPEN 상태

**확인**:
```bash
# Circuit Breaker 상태
curl http://localhost:9090/metrics | grep circuit_breaker_state

# DLQ 크기 (OPEN 시 증가)
curl http://localhost:9090/metrics | grep dlq_size
```

### 시나리오 2: 높은 실패율

**증상**:
```promql
rate(keycloak_events_failed_total[5m]) / rate(keycloak_events_total[5m]) > 0.1
```

**원인**:
- 메시징 시스템 장애
- 네트워크 문제
- 설정 오류

**확인**:
```bash
# 에러 타입별 분류
curl http://localhost:9090/metrics | \
  grep keycloak_events_failed_total | \
  grep -o 'error_type="[^"]*"' | \
  sort | uniq -c

# 재시도 통계
curl http://localhost:9090/metrics | grep retry_attempts_total
```

### 시나리오 3: 느린 처리

**증상**:
```promql
histogram_quantile(0.95, rate(keycloak_events_processing_duration_seconds_bucket[5m])) > 1.0
```

**원인**:
- 메시징 시스템 응답 지연
- Batch 처리 활성화 (flush interval 대기)
- 네트워크 레이턴시

**확인**:
```bash
# Batch 버퍼 크기
curl http://localhost:9090/metrics | grep batch_processor_buffer_size

# P50, P95, P99 레이턴시
curl http://localhost:9090/metrics | \
  grep keycloak_events_processing_duration_seconds
```

---

**See Also**:
- [Resilience Patterns](resilience.md) - Circuit Breaker, DLQ 설명
- [Troubleshooting](troubleshooting.md) - 일반적인 문제 해결
- [Configuration Guide](configuration.md) - Prometheus 설정
