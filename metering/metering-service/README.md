# Keycloak Metering Service

**Status**: 🚧 Phase 1 - MVP Development

Standalone microservice that consumes Keycloak events from Kafka and stores usage metrics in a time-series database for analytics and billing purposes.

## Overview

The Metering Service provides:
- ✅ Real-time usage tracking from Kafka events
- ✅ Time-series storage (InfluxDB)
- ✅ Prometheus metrics export
- ✅ Grafana dashboard integration
- 🚧 Multi-realm usage aggregation
- 🚧 Rate limiting integration (Phase 2)

## Architecture

```
┌─────────────────┐
│   Keycloak      │
│ event-listener  │
└────────┬────────┘
         │ Events
         ▼
┌─────────────────┐
│      Kafka      │
│  (Topics: user  │
│   & admin)      │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│   Metering Service      │
│  ┌──────────────────┐   │
│  │ Kafka Consumer   │   │
│  └─────────┬────────┘   │
│            │            │
│  ┌─────────▼────────┐   │
│  │ Event Processor  │   │
│  └─────────┬────────┘   │
│            │            │
│  ┌─────────▼────────┐   │
│  │ InfluxDB Storage │   │
│  └──────────────────┘   │
│                         │
│  ┌──────────────────┐   │
│  │ Prometheus       │   │
│  │ Metrics (:9091)  │   │
│  └──────────────────┘   │
└─────────────────────────┘
         │
         ▼
┌─────────────────┐
│    InfluxDB     │
│  (Time-series)  │
└─────────────────┘
         │
         ▼
┌─────────────────┐
│     Grafana     │
│  (Dashboards)   │
└─────────────────┘
```

## Quick Start

### 1. Prerequisites

- Keycloak with Kafka event listener enabled
- Kafka broker running
- InfluxDB instance (or use Docker Compose)
- Java 17+ / Kotlin

### 2. Build

```bash
# From project root
./gradlew :metering:metering-service:build

# Create standalone JAR
./gradlew :metering:metering-service:shadowJar
```

### 3. Configuration

Create `application.conf` or use environment variables:

```hocon
metering {
  storage {
    type = "influxdb"  # or "timescaledb" (Phase 2)
  }

  kafka {
    bootstrap-servers = "localhost:9092"
    event-topic = "keycloak.events"
    admin-event-topic = "keycloak.admin.events"
    group-id = "keycloak-metering-service"
  }

  influxdb {
    url = "http://localhost:8086"
    database = "keycloak_metrics"
    # username = "admin"  # Optional
    # password = "secret"
    retention-policy = "autogen"
    batch-size = 1000
    flush-interval-ms = 5000
  }

  prometheus {
    port = 9091
  }
}
```

**Environment Variables**:
```bash
METERING_STORAGE_TYPE=influxdb
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092
KAFKA_EVENT_TOPIC=keycloak.events
INFLUXDB_URL=http://influxdb:8086
INFLUXDB_DATABASE=keycloak_metrics
PROMETHEUS_PORT=9091
```

### 4. Run

```bash
# Using Shadow JAR
java -jar metering/metering-service/build/libs/keycloak-metering-service-*-all.jar

# Or with Gradle
./gradlew :metering:metering-service:run
```

### 5. Verify

```bash
# Check health
curl http://localhost:9091/health

# Check Prometheus metrics
curl http://localhost:9091/metrics

# Check InfluxDB data
influx -database keycloak_metrics -execute 'SELECT * FROM keycloak_user_events LIMIT 10'
```

## Metrics Schema

### InfluxDB Measurement: `keycloak_user_events`

**Tags** (indexed):
- `event_type` - Event type (LOGIN, LOGOUT, REGISTER, etc.)
- `realm_id` - Keycloak realm ID
- `client_id` - Client application ID
- `user_id` - User ID
- `success` - Event success (true/false)
- `ip_address` - Client IP address

**Fields** (values):
- `count` - Always 1 (for counting)
- `session_id` - Session identifier
- `detail_*` - Additional event details

### Prometheus Metrics

```
# Total events processed
keycloak_metering_events_total

# Events by type, realm, client
keycloak_user_events_total{event_type="LOGIN", realm_id="master", client_id="webapp", success="true"}

# Processing errors
keycloak_metering_errors_total{event_type="LOGIN", error_type="JsonProcessingException"}
```

## Use Cases

### 1. Usage Analytics

Query login patterns:
```sql
SELECT COUNT(*) FROM keycloak_user_events
WHERE event_type='LOGIN'
AND time > now() - 24h
GROUP BY time(1h), realm_id
```

### 2. Billing & Metering

Calculate monthly active users per realm:
```sql
SELECT COUNT(DISTINCT user_id) FROM keycloak_user_events
WHERE event_type='LOGIN'
AND time > now() - 30d
GROUP BY realm_id
```

### 3. Security Monitoring

Detect failed login attempts:
```sql
SELECT COUNT(*) FROM keycloak_user_events
WHERE event_type='LOGIN_ERROR'
AND time > now() - 1h
GROUP BY time(5m), ip_address
```

## Grafana Dashboards

### Available Dashboards

1. **User Activity** (`grafana/user-activity.json`)
   - Total logins per hour/day
   - Active users
   - Login success rate

2. **Realm Overview** (`grafana/realm-overview.json`)
   - Per-realm usage
   - Top clients
   - Geographic distribution

3. **Security** (`grafana/security.json`)
   - Failed login attempts
   - Password reset requests
   - Suspicious activity alerts

### Import Dashboards

```bash
# Using Grafana HTTP API
curl -X POST http://admin:admin@localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @metering/grafana/user-activity.json
```

## Performance Tuning

### Kafka Consumer

**High Throughput**:
```hocon
kafka {
  max-poll-records = 1000
  enable-auto-commit = false  # Manual commit for reliability
}
```

**Low Latency**:
```hocon
kafka {
  max-poll-records = 100
  enable-auto-commit = true
}
```

### InfluxDB

**Write Optimization**:
```hocon
influxdb {
  batch-size = 5000          # Larger batches
  flush-interval-ms = 10000  # Longer intervals
}
```

**Query Optimization**:
- Use continuous queries for downsampling
- Create appropriate retention policies
- Index frequently queried tags

## Deployment

### Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine

COPY metering-service-*-all.jar /app/metering-service.jar

EXPOSE 9091

ENTRYPOINT ["java", "-jar", "/app/metering-service.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak-metering-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: metering-service
  template:
    metadata:
      labels:
        app: metering-service
    spec:
      containers:
      - name: metering-service
        image: your-registry/keycloak-metering-service:latest
        ports:
        - containerPort: 9091
          name: metrics
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: INFLUXDB_URL
          value: "http://influxdb:8086"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

## Troubleshooting

### Events Not Being Consumed

1. **Check Kafka connectivity**:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic keycloak.events --from-beginning
   ```

2. **Verify consumer group**:
   ```bash
   kafka-consumer-groups --bootstrap-server localhost:9092 \
     --describe --group keycloak-metering-service
   ```

3. **Check service logs**:
   ```bash
   grep "Kafka consumer" metering-service.log
   ```

### InfluxDB Write Failures

1. **Check database exists**:
   ```bash
   influx -execute 'SHOW DATABASES'
   ```

2. **Test connection**:
   ```bash
   curl http://localhost:8086/ping
   ```

3. **Check retention policies**:
   ```bash
   influx -database keycloak_metrics -execute 'SHOW RETENTION POLICIES'
   ```

### High Memory Usage

- Reduce `kafka.max-poll-records`
- Decrease `influxdb.batch-size`
- Increase `influxdb.flush-interval-ms`
- Add JVM heap limits: `-Xmx512m`

## Roadmap

### Phase 1 (Current) - MVP
- ✅ Kafka event consumption
- ✅ InfluxDB storage
- ✅ Prometheus metrics
- 🚧 Basic Grafana dashboards
- 🚧 Unit tests

### Phase 2
- TimescaleDB support
- Admin event processing
- Custom aggregations
- Rate limiting integration
- Advanced dashboards
- API for custom queries

### Phase 3
- Multi-tenancy support
- Billing APIs
- Cost optimization recommendations
- Anomaly detection
- Data export (CSV, JSON)

## Contributing

See main project [CONTRIBUTING.md](../../CONTRIBUTING.md)

## License

MIT License - See [LICENSE](../../LICENSE)
