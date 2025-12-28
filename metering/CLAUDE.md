# Metering Module - CLAUDE.md

## 1. Overview

Standalone service for consuming Keycloak events from Kafka and storing usage metrics in time-series databases for analytics and visualization.

**Module**: `metering-service`

**Note**: This is NOT a Keycloak SPI extension. It's a standalone Kotlin application that runs separately from Keycloak.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Metering Service                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Kafka                                                         │
│   ├── keycloak.events                                          │
│   └── keycloak.admin.events                                    │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  KafkaEventConsumer                                     │  │
│   │  - Consumes events from Kafka topics                    │  │
│   │  - Deserializes JSON to KeycloakEvent/AdminEvent        │  │
│   └─────────────────────────────────────────────────────────┘  │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  EventProcessor                                         │  │
│   │  - Aggregates events by type, realm, client             │  │
│   │  - Calculates metrics (login count, error rate, etc.)   │  │
│   │  - Tracks user activity patterns                        │  │
│   └─────────────────────────────────────────────────────────┘  │
│        ↓                           ↓                            │
│   ┌──────────────────┐   ┌─────────────────────────┐           │
│   │ StorageBackend   │   │ MetricsExporter         │           │
│   │ ├─ InfluxDB      │   │ (Prometheus /metrics)   │           │
│   │ └─ TimescaleDB   │   │                         │           │
│   └──────────────────┘   └─────────────────────────┘           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Components

| Component | File | Purpose |
|-----------|------|---------|
| MeteringApplication | MeteringApplication.kt | Main entry point |
| MeteringConfig | config/MeteringConfig.kt | Configuration loader |
| KafkaEventConsumer | consumer/KafkaEventConsumer.kt | Kafka consumer |
| EventProcessor | processor/EventProcessor.kt | Event aggregation |
| StorageBackend | storage/StorageBackend.kt | Storage interface |
| InfluxDBStorage | storage/InfluxDBStorage.kt | InfluxDB implementation |
| MetricsExporter | metrics/MetricsExporter.kt | Prometheus exporter |
| UserEventMetric | model/UserEventMetric.kt | Metric data model |

---

## 4. Configuration

Uses Typesafe Config (HOCON format):

```hocon
# application.conf
metering {
  storage-type = "influxdb"  # or "timescaledb"
  prometheus-port = 9091

  kafka {
    bootstrap-servers = "localhost:9092"
    group-id = "keycloak-metering"
    topics = ["keycloak.events", "keycloak.admin.events"]
    auto-offset-reset = "earliest"
  }

  influxdb {
    url = "http://localhost:8086"
    token = "your-token"
    org = "keycloak"
    bucket = "metering"
  }
}
```

---

## 5. Metrics Collected

### Event Counters

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| keycloak_logins_total | Counter | realm, client | Total login events |
| keycloak_login_errors_total | Counter | realm, client, error | Failed logins |
| keycloak_registrations_total | Counter | realm | New user registrations |
| keycloak_logouts_total | Counter | realm | User logouts |

### Aggregated Metrics

| Metric | Type | Description |
|--------|------|-------------|
| keycloak_active_users | Gauge | Users active in last 24h |
| keycloak_login_rate | Gauge | Logins per minute |
| keycloak_error_rate | Gauge | Error percentage |

---

## 6. File Structure

```
metering/
├── CLAUDE.md
├── grafana/                         # Grafana dashboards
│   └── keycloak-metering.json
└── metering-service/
    └── src/main/kotlin/.../metering/
        ├── MeteringApplication.kt    # Main entry point
        ├── config/
        │   └── MeteringConfig.kt     # Configuration
        ├── consumer/
        │   └── KafkaEventConsumer.kt # Kafka consumer
        ├── processor/
        │   └── EventProcessor.kt     # Event aggregation
        ├── storage/
        │   ├── StorageBackend.kt     # Interface
        │   └── InfluxDBStorage.kt    # InfluxDB impl
        ├── metrics/
        │   └── MetricsExporter.kt    # Prometheus
        └── model/
            └── UserEventMetric.kt    # Data model
```

---

## 7. Build & Run

```bash
# Build
./gradlew :metering:metering-service:build

# Create fat JAR
./gradlew :metering:metering-service:shadowJar

# Run locally
java -jar metering/metering-service/build/libs/metering-service-all.jar

# Run with custom config
java -Dconfig.file=/path/to/application.conf \
  -jar metering-service-all.jar
```

---

## 8. Docker Deployment

```yaml
# docker-compose.yml
services:
  metering:
    image: keycloak-metering:latest
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - INFLUXDB_URL=http://influxdb:8086
      - INFLUXDB_TOKEN=your-token
    ports:
      - "9091:9091"  # Prometheus metrics
    depends_on:
      - kafka
      - influxdb
```

---

## 9. Grafana Integration

Pre-built dashboard available at `metering/grafana/keycloak-metering.json`.

Import to Grafana:
1. Dashboards → Import
2. Upload JSON file
3. Select InfluxDB and Prometheus data sources

---

## 10. Storage Backends

### InfluxDB (Implemented)

Time-series database optimized for metrics.

```kotlin
val storage = InfluxDBStorage(
    url = "http://localhost:8086",
    token = "your-token",
    org = "keycloak",
    bucket = "metering"
)
```

### TimescaleDB (Planned)

PostgreSQL extension for time-series data.

---

## 11. Integration with Event Listeners

Requires `event-listener-kafka` module to stream events to Kafka:

```
Keycloak → event-listener-kafka → Kafka → metering-service → InfluxDB
                                              ↓
                                         Prometheus → Grafana
```

---

## 12. Related Files

| File | Purpose |
|------|---------|
| `metering/metering-service/README.md` | Service setup guide |
| `metering/grafana/` | Grafana dashboards |
| `events/CLAUDE.md` | Event listener setup |
