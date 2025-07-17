# Configuration Reference - Keycloak Kafka Event Listener

This document provides comprehensive configuration options for the Keycloak Kafka Event Listener.

## Configuration Overview

The plugin supports configuration through:
1. **Keycloak Configuration File** (`keycloak.conf`)
2. **Environment Variables**
3. **System Properties**
4. **Runtime Configuration** (Admin API)

## Basic Configuration

### Required Settings

```properties
# Kafka connection (required)
kafka.bootstrap.servers=localhost:9092

# Topic configuration (required)
kafka.user.events.topic=keycloak.user.events
kafka.admin.events.topic=keycloak.admin.events

# Event types to enable (required)
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
```

### Minimal Configuration Example

```properties
# keycloak.conf - Minimal setup
kafka.bootstrap.servers=localhost:9092
kafka.user.events.topic=keycloak.user.events
kafka.admin.events.topic=keycloak.admin.events
kafka.user.events.enabled=true
kafka.admin.events.enabled=false
```

## Connection Configuration

### Kafka Broker Settings

```properties
# Bootstrap servers (comma-separated list)
kafka.bootstrap.servers=broker1:9092,broker2:9092,broker3:9092

# Connection timeout
kafka.connection.timeout.ms=30000

# Request timeout
kafka.request.timeout.ms=30000

# Retry configuration
kafka.retries=3
kafka.retry.backoff.ms=100

# Reconnection settings
kafka.reconnect.backoff.ms=50
kafka.reconnect.backoff.max.ms=1000
```

### Connection Pool Configuration

```properties
# Enable connection pooling
kafka.connection.pool.enabled=true

# Pool size settings
kafka.connection.pool.initial.size=3
kafka.connection.pool.max.size=10

# Connection timeouts
kafka.connection.pool.timeout.ms=30000
kafka.connection.pool.idle.timeout.ms=300000

# Thread pool settings
kafka.connection.pool.core.threads=2
kafka.connection.pool.max.threads=8
kafka.connection.pool.keep.alive.ms=60000
kafka.connection.pool.queue.capacity=100
```

## Topic Configuration

### User Events

```properties
# User events topic
kafka.user.events.topic=keycloak.user.events

# Enable/disable user events
kafka.user.events.enabled=true

# Event filtering
kafka.included.event.types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE,UPDATE_PASSWORD,LOGIN_ERROR,LOGOUT_ERROR

# Excluded events (takes precedence over included)
kafka.excluded.event.types=UPDATE_TOTP,REMOVE_TOTP

# Include event details
kafka.user.events.include.details=true

# Include client information
kafka.user.events.include.client.info=true

# Include user details
kafka.user.events.include.user.details=false
```

### Admin Events

```properties
# Admin events topic
kafka.admin.events.topic=keycloak.admin.events

# Enable/disable admin events
kafka.admin.events.enabled=true

# Operation filtering
kafka.included.admin.operations=CREATE,UPDATE,DELETE,ACTION

# Resource type filtering
kafka.included.admin.resource.types=USER,REALM,CLIENT,GROUP,ROLE

# Include representation data
kafka.admin.events.include.representation=false

# Include error details
kafka.admin.events.include.error.details=true
```

### Topic Management

```properties
# Auto-create topics (not recommended for production)
kafka.auto.create.topics=false

# Topic creation settings (if auto-create enabled)
kafka.topic.partitions=3
kafka.topic.replication.factor=1
kafka.topic.retention.ms=604800000

# Topic configuration overrides
kafka.topic.config.cleanup.policy=delete
kafka.topic.config.compression.type=lz4
```

## Security Configuration

### SSL/TLS Configuration

```properties
# Security protocol
kafka.security.protocol=SSL

# SSL settings
kafka.ssl.protocol=TLSv1.2
kafka.ssl.enabled.protocols=TLSv1.2,TLSv1.3

# Truststore configuration
kafka.ssl.truststore.location=/path/to/kafka.client.truststore.jks
kafka.ssl.truststore.password=ENC(encrypted_truststore_password)
kafka.ssl.truststore.type=JKS

# Keystore configuration (for client authentication)
kafka.ssl.keystore.location=/path/to/kafka.client.keystore.jks
kafka.ssl.keystore.password=ENC(encrypted_keystore_password)
kafka.ssl.keystore.type=JKS
kafka.ssl.key.password=ENC(encrypted_key_password)

# Hostname verification
kafka.ssl.endpoint.identification.algorithm=https

# SSL provider
kafka.ssl.provider=SunJSSE
```

### SASL Authentication

```properties
# SASL/SSL
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-256

# SASL/SCRAM configuration
kafka.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="kafka-user" password="ENC(encrypted_password)";

# SASL/PLAIN configuration
kafka.sasl.mechanism=PLAIN
kafka.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="kafka-user" password="ENC(encrypted_password)";

# SASL/GSSAPI (Kerberos) configuration
kafka.sasl.mechanism=GSSAPI
kafka.sasl.kerberos.service.name=kafka
kafka.sasl.jaas.config=com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab="/path/to/kafka.keytab" principal="kafka-user@REALM.COM";
```

### Credential Encryption

```properties
# Enable credential encryption
encryption.enabled=true

# Master encryption key (base64 encoded)
encryption.master.key=base64_encoded_32_byte_key

# Encryption algorithm
encryption.algorithm=AES/GCM/NoPadding

# Key derivation
encryption.key.derivation.iterations=100000
encryption.salt=base64_encoded_salt
```

## Performance Configuration

### Producer Settings

```properties
# Batch settings
kafka.batch.size=16384
kafka.linger.ms=5

# Buffer settings
kafka.buffer.memory=33554432
kafka.max.block.ms=60000

# Compression
kafka.compression.type=snappy

# Acknowledgment settings
kafka.acks=1
kafka.enable.idempotence=false

# In-flight requests
kafka.max.in.flight.requests.per.connection=5

# Partitioning
kafka.partitioner.class=org.apache.kafka.clients.producer.internals.DefaultPartitioner
```

### Performance Profiles

#### High Throughput Profile
```properties
# Optimized for maximum throughput
kafka.batch.size=65536
kafka.linger.ms=20
kafka.buffer.memory=67108864
kafka.compression.type=lz4
kafka.max.in.flight.requests.per.connection=5
kafka.acks=1
```

#### Low Latency Profile
```properties
# Optimized for minimum latency
kafka.batch.size=1024
kafka.linger.ms=0
kafka.compression.type=none
kafka.max.in.flight.requests.per.connection=1
kafka.acks=1
```

#### Balanced Profile
```properties
# Balanced performance and reliability
kafka.batch.size=16384
kafka.linger.ms=5
kafka.compression.type=snappy
kafka.max.in.flight.requests.per.connection=3
kafka.acks=1
```

#### Reliability Profile
```properties
# Maximum reliability and durability
kafka.acks=all
kafka.retries=2147483647
kafka.enable.idempotence=true
kafka.max.in.flight.requests.per.connection=1
kafka.delivery.timeout.ms=120000
```

### Circuit Breaker Configuration

```properties
# Enable circuit breaker
kafka.circuit.breaker.enabled=true

# Failure threshold (percentage)
kafka.circuit.breaker.failure.threshold=0.5

# Minimum calls before opening
kafka.circuit.breaker.minimum.calls=10

# Open state timeout
kafka.circuit.breaker.open.timeout.ms=60000

# Half-open success threshold
kafka.circuit.breaker.half.open.success.threshold=3

# Sliding window size
kafka.circuit.breaker.sliding.window.ms=60000
```

### Backpressure Configuration

```properties
# Enable backpressure management
kafka.backpressure.enabled=true

# Concurrent operations limit
kafka.backpressure.max.concurrent.operations=50

# Buffer size
kafka.backpressure.buffer.size=1000

# Backpressure threshold (percentage)
kafka.backpressure.threshold=80.0

# Operation timeout
kafka.backpressure.operation.timeout.ms=5000

# Strategy: BUFFER, DROP_OLDEST, DROP_NEWEST, REJECT, PRIORITY_BASED
kafka.backpressure.strategy=BUFFER
```

## Monitoring Configuration

### Metrics Collection

```properties
# Enable metrics
metrics.enabled=true

# Metrics endpoint
metrics.port=9090
metrics.host=0.0.0.0
metrics.path=/metrics

# Metrics format (prometheus, json)
metrics.format=prometheus

# Collection interval
metrics.collection.interval.ms=10000

# Metrics retention
metrics.retention.ms=3600000
```

### Prometheus Integration

```properties
# Prometheus configuration
metrics.prometheus.enabled=true
metrics.prometheus.port=9090
metrics.prometheus.host=0.0.0.0

# Custom labels
metrics.prometheus.labels.application=keycloak-kafka
metrics.prometheus.labels.environment=production
metrics.prometheus.labels.instance=${HOSTNAME}

# Metric naming
metrics.prometheus.prefix=keycloak_kafka_
metrics.prometheus.include.jvm.metrics=true
```

### JMX Configuration

```properties
# JMX metrics
metrics.jmx.enabled=true
metrics.jmx.domain=org.scriptonbasestar.kcexts.kafka

# JMX registry
metrics.jmx.registry.name=kafka-event-listener
```

## Logging Configuration

### Log Levels

```properties
# Plugin logging level
log.level.kafka.event.listener=INFO

# Component-specific levels
log.level.kafka.producer=WARN
log.level.kafka.circuit.breaker=INFO
log.level.kafka.backpressure=DEBUG
log.level.kafka.security=INFO
```

### Log Format

```properties
# Log format
log.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n

# Include MDC
log.include.mdc=true

# Log file settings
log.file.path=/opt/keycloak/logs/kafka-events.log
log.file.max.size=100MB
log.file.max.history=30
```

### Structured Logging

```properties
# JSON logging
log.json.enabled=true
log.json.include.timestamp=true
log.json.include.level=true
log.json.include.thread=true
log.json.include.logger=true
log.json.include.mdc=true
```

## Environment-Specific Configurations

### Development Environment

```properties
# Development settings
kafka.bootstrap.servers=localhost:9092
kafka.security.protocol=PLAINTEXT
kafka.user.events.enabled=true
kafka.admin.events.enabled=false
kafka.included.event.types=LOGIN,LOGOUT
kafka.circuit.breaker.enabled=false
kafka.backpressure.enabled=false
metrics.enabled=false
log.level.kafka.event.listener=DEBUG
```

### Testing Environment

```properties
# Testing settings
kafka.bootstrap.servers=kafka-test:9092
kafka.security.protocol=PLAINTEXT
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
kafka.included.event.types=LOGIN,LOGOUT,REGISTER
kafka.circuit.breaker.enabled=true
kafka.backpressure.enabled=true
metrics.enabled=true
log.level.kafka.event.listener=INFO
```

### Staging Environment

```properties
# Staging settings
kafka.bootstrap.servers=kafka-staging:9093
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-256
kafka.ssl.truststore.location=/opt/keycloak/ssl/truststore.jks
kafka.ssl.truststore.password=ENC(staging_password)
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
kafka.circuit.breaker.enabled=true
kafka.backpressure.enabled=true
metrics.enabled=true
log.level.kafka.event.listener=INFO
```

### Production Environment

```properties
# Production settings
kafka.bootstrap.servers=kafka-prod1:9093,kafka-prod2:9093,kafka-prod3:9093
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-512
kafka.ssl.truststore.location=/opt/keycloak/ssl/prod-truststore.jks
kafka.ssl.truststore.password=ENC(prod_truststore_password)
kafka.ssl.keystore.location=/opt/keycloak/ssl/prod-keystore.jks
kafka.ssl.keystore.password=ENC(prod_keystore_password)
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
kafka.included.event.types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE,UPDATE_PASSWORD,LOGIN_ERROR
kafka.included.admin.operations=CREATE,UPDATE,DELETE
kafka.circuit.breaker.enabled=true
kafka.backpressure.enabled=true
kafka.connection.pool.enabled=true
metrics.enabled=true
encryption.enabled=true
log.level.kafka.event.listener=WARN
```

## Configuration Validation

### Required Configuration Check

```bash
# Validation script
./scripts/validate-configuration.sh

# Manual validation
grep -E "^kafka\.(bootstrap\.servers|.*\.topic|.*\.enabled)" $KEYCLOAK_HOME/conf/keycloak.conf
```

### Configuration Testing

```bash
# Test Kafka connectivity
kafka-topics.sh --list --bootstrap-server $(grep bootstrap.servers keycloak.conf | cut -d= -f2)

# Validate SSL configuration
openssl s_client -connect kafka:9093 -cert client.pem -key client-key.pem

# Test authentication
kafka-console-producer.sh --bootstrap-server kafka:9093 --topic test --producer.config client.properties
```

## Configuration Management

### Environment Variables

All configuration properties can be set via environment variables using uppercase and underscores:

```bash
# Example environment variables
export KAFKA_BOOTSTRAP_SERVERS="kafka1:9092,kafka2:9092"
export KAFKA_SECURITY_PROTOCOL="SASL_SSL"
export KAFKA_USER_EVENTS_ENABLED="true"
export METRICS_ENABLED="true"
```

### Docker Configuration

```yaml
# docker-compose.yml
version: '3.8'
services:
  keycloak-kafka:
    image: scriptonbasestar/keycloak-kafka-event-listener:0.0.2
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - KAFKA_USER_EVENTS_ENABLED=true
      - METRICS_ENABLED=true
    volumes:
      - ./config/keycloak.conf:/opt/keycloak/conf/keycloak.conf:ro
      - ./ssl:/opt/keycloak/ssl:ro
```

### Kubernetes Configuration

```yaml
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-kafka-config
data:
  keycloak.conf: |
    kafka.bootstrap.servers=kafka:9092
    kafka.user.events.enabled=true
    metrics.enabled=true

---
# Secret for sensitive data
apiVersion: v1
kind: Secret
metadata:
  name: keycloak-kafka-secret
type: Opaque
data:
  kafka.ssl.truststore.password: base64_encoded_password
  kafka.ssl.keystore.password: base64_encoded_password
```

## Configuration Reference Tables

### Connection Properties

| Property | Default | Description |
|----------|---------|-------------|
| `kafka.bootstrap.servers` | - | Kafka broker addresses (required) |
| `kafka.security.protocol` | `PLAINTEXT` | Security protocol |
| `kafka.connection.timeout.ms` | `30000` | Connection timeout |
| `kafka.request.timeout.ms` | `30000` | Request timeout |
| `kafka.retries` | `3` | Number of retries |

### Topic Properties

| Property | Default | Description |
|----------|---------|-------------|
| `kafka.user.events.topic` | - | User events topic (required) |
| `kafka.admin.events.topic` | - | Admin events topic (required) |
| `kafka.user.events.enabled` | `false` | Enable user events |
| `kafka.admin.events.enabled` | `false` | Enable admin events |

### Performance Properties

| Property | Default | Description |
|----------|---------|-------------|
| `kafka.batch.size` | `16384` | Batch size in bytes |
| `kafka.linger.ms` | `5` | Time to wait for batch |
| `kafka.compression.type` | `snappy` | Compression algorithm |
| `kafka.acks` | `1` | Acknowledgment mode |

### Security Properties

| Property | Default | Description |
|----------|---------|-------------|
| `kafka.ssl.truststore.location` | - | Truststore file path |
| `kafka.ssl.truststore.password` | - | Truststore password |
| `kafka.sasl.mechanism` | - | SASL mechanism |
| `encryption.enabled` | `false` | Enable credential encryption |

---

For more detailed information, see:
- [Installation Guide](INSTALLATION.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Security Guide](../SECURITY.md)