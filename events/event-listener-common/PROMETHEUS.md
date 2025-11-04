# Prometheus Metrics for Keycloak Event Listeners

Keycloak 이벤트 리스너의 Prometheus 메트릭 수집 및 노출 가이드입니다.

## Overview

모든 이벤트 리스너 (Kafka, RabbitMQ, NATS)는 Prometheus 메트릭을 지원합니다.

### Available Metrics

#### Counters
```
keycloak_events_sent_total{event_type, realm, destination, listener_type}
- 성공적으로 전송된 이벤트 수

keycloak_events_failed_total{event_type, realm, destination, error_type, listener_type}
- 실패한 이벤트 수
```

#### Histograms
```
keycloak_event_processing_duration_seconds{event_type, listener_type}
- 이벤트 처리 지연시간 (초)
- Buckets: 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0

keycloak_event_size_bytes{event_type, listener_type}
- 이벤트 메시지 크기 (바이트)
- Buckets: 100, 250, 500, 1000, 2500, 5000, 10000, 25000
```

#### Gauges
```
keycloak_event_listener_connections{listener_type, status}
- 메시징 시스템 연결 상태
- status: connected, disconnected
```

#### JVM Metrics (Optional)
```
jvm_memory_*
jvm_threads_*
jvm_gc_*
process_*
```

## Configuration

### Kafka Event Listener

```xml
<spi name="eventsListener">
    <provider name="kafka-event-listener" enabled="true">
        <properties>
            <!-- Kafka settings -->
            <property name="bootstrapServers" value="localhost:9092"/>
            <property name="eventTopic" value="keycloak-events"/>

            <!-- Prometheus settings -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9090"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

### RabbitMQ Event Listener

```xml
<spi name="eventsListener">
    <provider name="rabbitmq-event-listener" enabled="true">
        <properties>
            <!-- RabbitMQ settings -->
            <property name="host" value="localhost"/>
            <property name="port" value="5672"/>

            <!-- Prometheus settings -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9091"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

### NATS Event Listener

```xml
<spi name="eventsListener">
    <provider name="nats-event-listener" enabled="true">
        <properties>
            <!-- NATS settings -->
            <property name="serverUrl" value="nats://localhost:4222"/>

            <!-- Prometheus settings -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9092"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `enablePrometheus` | `false` | Enable Prometheus metrics exporter |
| `prometheusPort` | `9090` | HTTP port for Prometheus scraping |
| `enableJvmMetrics` | `true` | Include JVM metrics (memory, GC, threads) |

## Prometheus Configuration

### prometheus.yml

```yaml
scrape_configs:
  - job_name: 'keycloak-kafka-events'
    static_configs:
      - targets: ['localhost:9090']
        labels:
          service: 'keycloak'
          listener: 'kafka'

  - job_name: 'keycloak-rabbitmq-events'
    static_configs:
      - targets: ['localhost:9091']
        labels:
          service: 'keycloak'
          listener: 'rabbitmq'

  - job_name: 'keycloak-nats-events'
    static_configs:
      - targets: ['localhost:9092']
        labels:
          service: 'keycloak'
          listener: 'nats'
```

## Example Queries

### Event Success Rate
```promql
# Overall success rate
sum(rate(keycloak_events_sent_total[5m])) /
(sum(rate(keycloak_events_sent_total[5m])) + sum(rate(keycloak_events_failed_total[5m]))) * 100
```

### Events Per Second by Type
```promql
sum(rate(keycloak_events_sent_total[1m])) by (event_type, listener_type)
```

### Average Processing Latency
```promql
histogram_quantile(0.95,
  sum(rate(keycloak_event_processing_duration_seconds_bucket[5m])) by (le, listener_type)
)
```

### Failed Events by Error Type
```promql
sum(rate(keycloak_events_failed_total[5m])) by (error_type, listener_type)
```

### Connection Status
```promql
keycloak_event_listener_connections{status="connected"}
```

### Message Size Distribution
```promql
histogram_quantile(0.99,
  sum(rate(keycloak_event_size_bytes_bucket[5m])) by (le, event_type)
)
```

## Grafana Dashboard

### Sample Dashboard Panels

#### Events Sent (Counter)
```json
{
  "targets": [{
    "expr": "sum(rate(keycloak_events_sent_total[5m])) by (listener_type)"
  }]
}
```

#### Processing Latency (Heatmap)
```json
{
  "targets": [{
    "expr": "sum(rate(keycloak_event_processing_duration_seconds_bucket[5m])) by (le)",
    "format": "heatmap"
  }]
}
```

#### Error Rate (Graph)
```json
{
  "targets": [{
    "expr": "sum(rate(keycloak_events_failed_total[5m])) by (error_type)"
  }]
}
```

## Troubleshooting

### Metrics endpoint not accessible

**Check Prometheus exporter status:**
```bash
curl http://localhost:9090/metrics
```

**Expected output:**
```
# HELP keycloak_events_sent_total Total number of events successfully sent
# TYPE keycloak_events_sent_total counter
keycloak_events_sent_total{event_type="LOGIN",realm="master",destination="events",listener_type="kafka"} 142.0
...
```

### Port already in use

```
ERROR: Failed to start Prometheus metrics exporter on port 9090
```

**Solution:** Change `prometheusPort` to an available port

### No metrics data

1. Verify `enablePrometheus=true` in configuration
2. Check Keycloak logs for Prometheus initialization
3. Ensure events are being generated
4. Verify Prometheus is scraping the endpoint

## Docker Compose Example

```yaml
version: '3.8'

services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.0.7
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    volumes:
      - ./event-listener-kafka.jar:/opt/keycloak/providers/event-listener-kafka.jar
    ports:
      - "8080:8080"
      - "9090:9090"  # Prometheus metrics

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9091:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
```

## Best Practices

1. **Separate Ports**: Use different ports for each listener type
2. **Enable JVM Metrics**: Helps diagnose memory/thread issues
3. **Set Appropriate Scrape Interval**: 15-30s is recommended
4. **Use Labels Wisely**: Add custom labels in Prometheus config, not in metrics
5. **Monitor Both Success and Failure**: Track both metrics for complete picture
6. **Alert on High Error Rates**: Set up alerts for `keycloak_events_failed_total`

## Performance Impact

- **CPU**: < 1% overhead
- **Memory**: ~20MB for Prometheus client + metrics
- **Network**: Minimal (only during scrape)
- **Latency**: < 1ms per event

Prometheus metrics collection has negligible impact on event processing performance.
