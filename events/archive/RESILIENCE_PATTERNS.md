# Resilience Patterns for Keycloak Event Listeners

This document describes the resilience patterns implemented in the Keycloak event listeners (Kafka, RabbitMQ, and NATS) to ensure reliable event processing in production environments.

## Overview

All event listeners now support the following resilience patterns:

1. **Circuit Breaker** - Prevents cascading failures
2. **Retry Policy** - Automatically retries failed operations with backoff
3. **Dead Letter Queue (DLQ)** - Stores failed events for manual inspection
4. **Batch Processing** - Aggregates events for improved throughput
5. **Prometheus Metrics** - Exports operational metrics for monitoring

## Architecture

```
Event → Circuit Breaker → Retry Policy → Message Broker
                ↓                ↓
         DLQ (on failure)   DLQ (on exhaustion)
```

### Batch Processing Flow

```
Event → Batch Buffer → (Size/Time Trigger) → Circuit Breaker → Retry → Broker
                                                      ↓
                                                     DLQ
```

## Configuration

### Kafka Event Listener

Add to `standalone.xml` or `standalone-ha.xml`:

```xml
<spi name="eventsListener">
    <provider name="kafka-event-listener" enabled="true">
        <properties>
            <!-- Kafka Connection -->
            <property name="bootstrapServers" value="localhost:9092"/>
            <property name="eventTopic" value="keycloak-events"/>
            <property name="adminEventTopic" value="keycloak-admin-events"/>
            <property name="clientId" value="keycloak-event-listener"/>

            <!-- Event Filtering -->
            <property name="enableUserEvents" value="true"/>
            <property name="enableAdminEvents" value="true"/>
            <property name="includedEventTypes" value="LOGIN,LOGIN_ERROR,REGISTER,LOGOUT"/>

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
            <property name="dlqPath" value="./dlq/kafka"/>

            <!-- Batch Processing -->
            <property name="enableBatching" value="false"/>
            <property name="batchSize" value="100"/>
            <property name="batchFlushIntervalMs" value="5000"/>

            <!-- Prometheus Metrics -->
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
            <!-- RabbitMQ Connection -->
            <property name="host" value="localhost"/>
            <property name="port" value="5672"/>
            <property name="username" value="guest"/>
            <property name="password" value="guest"/>
            <property name="virtualHost" value="/"/>
            <property name="useSsl" value="false"/>
            <property name="exchangeName" value="keycloak-events"/>
            <property name="exchangeType" value="topic"/>
            <property name="exchangeDurable" value="true"/>
            <property name="userEventRoutingKey" value="keycloak.event.user"/>
            <property name="adminEventRoutingKey" value="keycloak.event.admin"/>

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
            <property name="dlqPath" value="./dlq/rabbitmq"/>

            <!-- Batch Processing -->
            <property name="enableBatching" value="false"/>
            <property name="batchSize" value="100"/>
            <property name="batchFlushIntervalMs" value="5000"/>

            <!-- Prometheus Metrics -->
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
            <!-- NATS Connection -->
            <property name="serverUrl" value="nats://localhost:4222"/>
            <property name="username" value=""/>
            <property name="password" value=""/>
            <property name="token" value=""/>
            <property name="useTls" value="false"/>
            <property name="userEventSubject" value="keycloak.events.user"/>
            <property name="adminEventSubject" value="keycloak.events.admin"/>

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
            <property name="dlqPath" value="./dlq/nats"/>

            <!-- Batch Processing -->
            <property name="enableBatching" value="false"/>
            <property name="batchSize" value="100"/>
            <property name="batchFlushIntervalMs" value="5000"/>

            <!-- Prometheus Metrics -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9092"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

## Resilience Pattern Details

### 1. Circuit Breaker

Prevents cascading failures by stopping requests when failure rate exceeds threshold.

**States:**
- `CLOSED`: Normal operation, requests pass through
- `OPEN`: Failure threshold exceeded, requests fail immediately
- `HALF_OPEN`: Testing if service recovered, limited requests allowed

**Configuration:**
- `enableCircuitBreaker`: Enable/disable circuit breaker (default: true)
- `circuitBreakerFailureThreshold`: Number of failures before opening (default: 5)
- `circuitBreakerOpenTimeoutSeconds`: Time to wait before entering HALF_OPEN (default: 60)

**Behavior:**
- Opens after 5 consecutive failures
- Waits 60 seconds before trying again
- Requires 2 successes in HALF_OPEN to close

### 2. Retry Policy

Automatically retries failed operations with exponential backoff.

**Backoff Strategies:**
- `FIXED`: Constant delay between retries
- `LINEAR`: Linearly increasing delay
- `EXPONENTIAL`: Exponentially increasing delay (recommended)
- `EXPONENTIAL_JITTER`: Exponential with random jitter to prevent thundering herd

**Configuration:**
- `enableRetry`: Enable/disable retry (default: true)
- `maxRetryAttempts`: Maximum retry attempts (default: 3)
- `retryInitialDelayMs`: Initial delay in milliseconds (default: 100)
- `retryMaxDelayMs`: Maximum delay in milliseconds (default: 10000)

**Example Retry Sequence (Exponential, multiplier=2.0):**
1. Initial attempt fails
2. Wait 100ms, retry (attempt 1)
3. Wait 200ms, retry (attempt 2)
4. Wait 400ms, retry (attempt 3)
5. If still failing, add to DLQ

### 3. Dead Letter Queue (DLQ)

Stores events that failed after all retry attempts for manual inspection and reprocessing.

**Configuration:**
- `enableDeadLetterQueue`: Enable/disable DLQ (default: true)
- `dlqMaxSize`: Maximum number of events in memory (default: 10000)
- `dlqPersistToFile`: Persist DLQ to disk (default: false)
- `dlqPath`: File path for persisted DLQ (default: ./dlq/{listener-type})

**DLQ Entry Format:**
```json
{
  "id": "uuid",
  "eventType": "LOGIN",
  "eventData": "{...}",
  "realm": "master",
  "destination": "keycloak-events",
  "failureReason": "Connection refused",
  "attemptCount": 3,
  "timestamp": "2025-01-04T10:30:00Z",
  "metadata": {
    "key": "master:LOGIN:user123",
    "errorClass": "IOException"
  }
}
```

**Accessing DLQ:**
- In-memory: Via JMX or custom API endpoint
- Persisted: JSON files in `dlqPath` directory

**Reprocessing Failed Events:**
1. Fix the underlying issue (e.g., restore message broker)
2. Extract events from DLQ
3. Replay events manually or via reprocessing tool

### 4. Batch Processing

Aggregates multiple events before sending to improve throughput and reduce network overhead.

**Configuration:**
- `enableBatching`: Enable/disable batching (default: false)
- `batchSize`: Number of events per batch (default: 100)
- `batchFlushIntervalMs`: Maximum time to wait before flushing (default: 5000)

**Triggering Conditions:**
- Batch reaches `batchSize` events
- `batchFlushIntervalMs` time elapses
- Listener is shutting down

**Trade-offs:**
- **Pros**: Higher throughput, reduced network calls
- **Cons**: Increased latency (up to flush interval), potential data loss on crash

**Recommendation:**
- Disable for real-time requirements
- Enable for high-volume, latency-tolerant scenarios

### 5. Prometheus Metrics

Exports operational metrics for monitoring and alerting.

**Configuration:**
- `enablePrometheus`: Enable/disable metrics export (default: true if listener enabled)
- `prometheusPort`: HTTP port for metrics endpoint (default: 9090/9091/9092)
- `enableJvmMetrics`: Export JVM metrics (heap, GC, threads) (default: true)

**Metrics Endpoint:**
```
GET http://localhost:{prometheusPort}/metrics
```

**Key Metrics:**

| Metric | Type | Description |
|--------|------|-------------|
| `keycloak_events_total` | Counter | Total events processed |
| `keycloak_events_failed_total` | Counter | Total events failed |
| `keycloak_events_processing_duration_seconds` | Histogram | Event processing time |
| `keycloak_events_size_bytes` | Histogram | Event size distribution |
| `keycloak_circuit_breaker_state` | Gauge | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `keycloak_batch_processor_buffer_size` | Gauge | Current batch buffer size |
| `keycloak_dlq_size` | Gauge | Dead letter queue size |

**Labels:**
- `event_type`: Event type (LOGIN, LOGOUT, etc.)
- `realm`: Keycloak realm
- `destination`: Target topic/queue/subject
- `error_type`: Error class name (for failures)

## Monitoring and Alerting

### Prometheus Alert Rules

```yaml
groups:
  - name: keycloak_events
    interval: 30s
    rules:
      # Circuit breaker open
      - alert: CircuitBreakerOpen
        expr: keycloak_circuit_breaker_state{listener="kafka"} == 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Keycloak event listener circuit breaker is open"
          description: "Circuit breaker for {{ $labels.listener }} has been open for 2 minutes"

      # High failure rate
      - alert: HighEventFailureRate
        expr: |
          rate(keycloak_events_failed_total[5m])
          /
          rate(keycloak_events_total[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High event failure rate"
          description: "{{ $labels.listener }} failure rate: {{ $value | humanizePercentage }}"

      # DLQ filling up
      - alert: DeadLetterQueueFilling
        expr: keycloak_dlq_size > 5000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Dead letter queue filling up"
          description: "DLQ size: {{ $value }} events (threshold: 5000)"

      # DLQ nearly full
      - alert: DeadLetterQueueNearlyFull
        expr: keycloak_dlq_size > 9000
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Dead letter queue nearly full"
          description: "DLQ size: {{ $value }} events (max: 10000)"
```

### Grafana Dashboard

See `GRAFANA_DASHBOARD.json` for a pre-built dashboard with:
- Event throughput graphs
- Failure rate trends
- Circuit breaker state timeline
- DLQ size monitoring
- Processing latency percentiles
- JVM heap and GC metrics

## Best Practices

### Production Configuration

1. **Enable All Resilience Patterns:**
   ```xml
   <property name="enableCircuitBreaker" value="true"/>
   <property name="enableRetry" value="true"/>
   <property name="enableDeadLetterQueue" value="true"/>
   ```

2. **Tune Circuit Breaker for Your SLA:**
   - Fast fail: `circuitBreakerFailureThreshold="3"`, `circuitBreakerOpenTimeoutSeconds="30"`
   - Tolerant: `circuitBreakerFailureThreshold="10"`, `circuitBreakerOpenTimeoutSeconds="120"`

3. **Configure Appropriate Retry Delays:**
   - Network hiccups: `retryInitialDelayMs="50"`, `maxRetryAttempts="5"`
   - Database locks: `retryInitialDelayMs="500"`, `maxRetryAttempts="3"`

4. **Persist DLQ for Critical Events:**
   ```xml
   <property name="dlqPersistToFile" value="true"/>
   <property name="dlqPath" value="/var/keycloak/dlq/kafka"/>
   ```

5. **Enable Batching for High Volume:**
   ```xml
   <property name="enableBatching" value="true"/>
   <property name="batchSize" value="100"/>
   <property name="batchFlushIntervalMs" value="1000"/>
   ```

6. **Monitor with Prometheus and Grafana:**
   - Set up alerts for circuit breaker state
   - Track failure rates and DLQ size
   - Monitor processing latency percentiles

### Testing Resilience

#### Circuit Breaker Test

```bash
# Stop message broker
docker stop kafka

# Generate events (5+ failures will open circuit)
# Circuit opens after threshold

# Start message broker
docker start kafka

# Wait for circuit breaker timeout (60s default)
# Circuit enters HALF_OPEN, then CLOSED on success
```

#### Retry Policy Test

```bash
# Monitor retry attempts in logs
tail -f keycloak/standalone/log/server.log | grep "Retry attempt"

# Expected output:
# Retry attempt 1 for event type=LOGIN, delay=100ms, error=Connection refused
# Retry attempt 2 for event type=LOGIN, delay=200ms, error=Connection refused
# Retry attempt 3 for event type=LOGIN, delay=400ms, error=Connection refused
# All retry attempts exhausted for event type=LOGIN
```

#### DLQ Test

```bash
# Stop message broker to force failures
docker stop kafka

# Generate events until DLQ fills
# Check DLQ size via metrics
curl http://localhost:9090/metrics | grep keycloak_dlq_size

# Inspect DLQ files
ls -lh /var/keycloak/dlq/kafka/
cat /var/keycloak/dlq/kafka/dlq-entry-*.json
```

#### Batch Processing Test

```bash
# Enable batching with small size
# <property name="batchSize" value="10"/>
# <property name="batchFlushIntervalMs" value="5000"/>

# Generate 10+ events rapidly
# Check logs for batch processing
grep "Flushing batch" keycloak/standalone/log/server.log

# Expected: "Flushing batch of 10 items"
```

## Troubleshooting

### Circuit Breaker Stuck Open

**Symptoms:**
- All events rejected with "Circuit breaker is OPEN"
- Metrics show `keycloak_circuit_breaker_state{} = 1`

**Causes:**
- Message broker still unavailable
- Failure threshold too low for transient errors

**Solutions:**
1. Check message broker health
2. Increase `circuitBreakerFailureThreshold`
3. Increase `circuitBreakerOpenTimeoutSeconds`
4. Restart Keycloak to reset circuit breaker state

### DLQ Full

**Symptoms:**
- Events failing with "Dead letter queue is full"
- Metrics show `keycloak_dlq_size >= 10000`

**Causes:**
- Persistent message broker outage
- DLQ not being cleared

**Solutions:**
1. Increase `dlqMaxSize`
2. Enable `dlqPersistToFile` to offload to disk
3. Process and clear DLQ entries
4. Fix underlying message broker issues

### High Retry Overhead

**Symptoms:**
- Increased Keycloak latency
- High CPU usage

**Causes:**
- Too many retry attempts
- Retry delays too short

**Solutions:**
1. Reduce `maxRetryAttempts`
2. Increase `retryInitialDelayMs`
3. Enable circuit breaker to fail fast
4. Consider disabling retries if broker is chronically slow

### Batch Processing Latency

**Symptoms:**
- Events delayed by up to `batchFlushIntervalMs`

**Causes:**
- Batch size not reached before flush interval

**Solutions:**
1. Reduce `batchFlushIntervalMs` for lower latency
2. Reduce `batchSize` for lower event volumes
3. Disable batching for real-time requirements

## Migration Guide

### From Previous Version

If upgrading from a version without resilience patterns:

1. **Backup Configuration:**
   ```bash
   cp standalone.xml standalone.xml.bak
   ```

2. **Add Resilience Configuration:**
   - Add circuit breaker, retry, DLQ, and batch settings
   - Start with defaults, tune based on metrics

3. **Enable Prometheus:**
   ```xml
   <property name="enablePrometheus" value="true"/>
   ```

4. **Deploy and Monitor:**
   - Watch for errors in logs
   - Check metrics endpoint: `http://localhost:9090/metrics`
   - Set up Grafana dashboard

5. **Tune Configuration:**
   - Adjust thresholds based on observed behavior
   - Enable batching for high-volume realms

### Rolling Back

If issues occur:

1. **Disable New Features:**
   ```xml
   <property name="enableCircuitBreaker" value="false"/>
   <property name="enableRetry" value="false"/>
   <property name="enableBatching" value="false"/>
   ```

2. **Restart Keycloak:**
   ```bash
   systemctl restart keycloak
   ```

3. **Restore Backup (if needed):**
   ```bash
   cp standalone.xml.bak standalone.xml
   systemctl restart keycloak
   ```

## References

- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)
- [Dead Letter Queue](https://en.wikipedia.org/wiki/Dead_letter_queue)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
- [Keycloak Event Listener SPI](https://www.keycloak.org/docs/latest/server_development/#_events)
