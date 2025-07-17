# Installation Guide - Keycloak Kafka Event Listener

This guide provides detailed instructions for installing and configuring the Keycloak Kafka Event Listener in various environments.

## Prerequisites

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **Java** | OpenJDK 17+ | OpenJDK 21+ |
| **Keycloak** | 24.0+ | 26.3.1+ |
| **Kafka** | 2.8+ | 3.8+ |
| **Memory** | 512MB | 1GB+ |
| **CPU** | 1 core | 2+ cores |
| **Storage** | 100MB | 500MB+ |

### Network Requirements

- Keycloak → Kafka: Port 9092 (or custom port)
- Monitoring → Keycloak: Port 9090 (metrics endpoint)
- Optional: SSL/TLS ports for secure communication

## Quick Start

### 1. Download and Install

```bash
# Download the latest release
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.2/keycloak-kafka-event-listener-0.0.2-SNAPSHOT.jar

# Or build from source
git clone https://github.com/scriptonbasestar/sb-keycloak-exts.git
cd sb-keycloak-exts
./gradlew :events:event-listener-kafka:shadowJar

# Copy JAR to Keycloak providers directory
cp events/event-listener-kafka/build/libs/keycloak-kafka-event-listener-*.jar $KEYCLOAK_HOME/providers/
```

### 2. Configure Keycloak

Add the following to `$KEYCLOAK_HOME/conf/keycloak.conf`:

```properties
# Kafka Event Listener Configuration
kafka.bootstrap.servers=localhost:9092
kafka.user.events.topic=keycloak.user.events
kafka.admin.events.topic=keycloak.admin.events
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
```

### 3. Build and Start Keycloak

```bash
# Build Keycloak with the new provider
$KEYCLOAK_HOME/bin/kc.sh build

# Start Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

### 4. Enable Event Listener

1. Access Keycloak Admin Console
2. Go to **Events** → **Config**
3. Add `kafka-event-listener` to **Event Listeners**
4. Save configuration

## Detailed Installation

### Method 1: Automated Installation

Use the provided installation script:

```bash
# Make script executable
chmod +x scripts/install.sh

# Run installation
./scripts/install.sh
```

The script will:
- Validate system requirements
- Download/copy the plugin JAR
- Configure Keycloak
- Build and verify installation

### Method 2: Manual Installation

#### Step 1: Prepare Environment

```bash
# Set environment variables
export KEYCLOAK_HOME=/opt/keycloak
export KAFKA_HOME=/opt/kafka

# Verify Keycloak installation
$KEYCLOAK_HOME/bin/kc.sh --version

# Verify Kafka installation
$KAFKA_HOME/bin/kafka-topics.sh --version
```

#### Step 2: Install Plugin

```bash
# Create backup directory
mkdir -p $KEYCLOAK_HOME/providers/backup

# Backup existing plugins (if any)
cp $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar $KEYCLOAK_HOME/providers/backup/ 2>/dev/null || true

# Install new plugin
cp keycloak-kafka-event-listener-*.jar $KEYCLOAK_HOME/providers/

# Verify installation
ls -la $KEYCLOAK_HOME/providers/keycloak-kafka-*
```

#### Step 3: Configure Kafka Topics

```bash
# Create required topics
$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic keycloak.user.events \
  --partitions 3 \
  --replication-factor 1

$KAFKA_HOME/bin/kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic keycloak.admin.events \
  --partitions 3 \
  --replication-factor 1

# Verify topics
$KAFKA_HOME/bin/kafka-topics.sh --list \
  --bootstrap-server localhost:9092
```

## Configuration

### Basic Configuration

Edit `$KEYCLOAK_HOME/conf/keycloak.conf`:

```properties
# Required - Kafka Connection
kafka.bootstrap.servers=localhost:9092

# Required - Topic Configuration
kafka.user.events.topic=keycloak.user.events
kafka.admin.events.topic=keycloak.admin.events

# Required - Enable Event Types
kafka.user.events.enabled=true
kafka.admin.events.enabled=true

# Optional - Event Filtering
kafka.included.event.types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE,UPDATE_PASSWORD
kafka.included.admin.operations=CREATE,UPDATE,DELETE,ACTION

# Optional - Performance Tuning
kafka.batch.size=16384
kafka.linger.ms=5
kafka.acks=1
kafka.retries=3
```

### Advanced Configuration

#### Security Configuration

```properties
# SSL/TLS Configuration
kafka.security.protocol=SSL
kafka.ssl.truststore.location=/path/to/kafka.client.truststore.jks
kafka.ssl.truststore.password=ENC(encrypted_password)
kafka.ssl.keystore.location=/path/to/kafka.client.keystore.jks
kafka.ssl.keystore.password=ENC(encrypted_password)

# SASL Authentication
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-256
kafka.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="user" password="ENC(encrypted_password)";
```

#### Performance Configuration

```properties
# High Throughput Profile
kafka.batch.size=65536
kafka.linger.ms=20
kafka.buffer.memory=67108864
kafka.compression.type=lz4
kafka.max.in.flight.requests.per.connection=5

# Low Latency Profile
kafka.batch.size=1024
kafka.linger.ms=0
kafka.compression.type=none
kafka.max.in.flight.requests.per.connection=1

# Reliability Profile
kafka.acks=all
kafka.retries=2147483647
kafka.enable.idempotence=true
kafka.max.in.flight.requests.per.connection=1
```

#### Monitoring Configuration

```properties
# Enable metrics
metrics.enabled=true
metrics.port=9090
metrics.host=0.0.0.0

# Enable secret encryption
encryption.enabled=true
encryption.master.key=base64_encoded_key
```

### Environment-Specific Configurations

#### Development Environment

```properties
# keycloak.conf for development
kafka.bootstrap.servers=localhost:9092
kafka.security.protocol=PLAINTEXT
kafka.user.events.enabled=true
kafka.admin.events.enabled=false
kafka.included.event.types=LOGIN,LOGOUT
metrics.enabled=false
encryption.enabled=false
```

#### Production Environment

```properties
# keycloak.conf for production
kafka.bootstrap.servers=kafka-cluster:9093
kafka.security.protocol=SASL_SSL
kafka.sasl.mechanism=SCRAM-SHA-512
kafka.ssl.truststore.location=/opt/keycloak/ssl/kafka.truststore.jks
kafka.ssl.truststore.password=ENC(prod_truststore_password)
kafka.user.events.enabled=true
kafka.admin.events.enabled=true
kafka.included.event.types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE,UPDATE_PASSWORD,LOGIN_ERROR
kafka.included.admin.operations=CREATE,UPDATE,DELETE
metrics.enabled=true
metrics.port=9090
encryption.enabled=true
```

## Container Deployment

### Docker Deployment

#### Using Docker Compose

```bash
# Clone repository
git clone https://github.com/scriptonbasestar/sb-keycloak-exts.git
cd sb-keycloak-exts

# Start services
docker-compose up -d

# Check status
docker-compose ps
```

#### Custom Docker Image

```dockerfile
FROM quay.io/keycloak/keycloak:26.3.1

# Copy plugin
COPY keycloak-kafka-event-listener-*.jar /opt/keycloak/providers/

# Copy configuration
COPY keycloak.conf /opt/keycloak/conf/

# Build Keycloak
RUN /opt/keycloak/bin/kc.sh build

# Start Keycloak
CMD ["/opt/keycloak/bin/kc.sh", "start"]
```

### Kubernetes Deployment

#### Using Helm

```bash
# Add Helm repository (if published)
helm repo add scriptonbasestar https://scriptonbasestar.github.io/helm-charts

# Install with custom values
helm install keycloak-kafka scriptonbasestar/keycloak-kafka-event-listener \
  --values values-production.yaml

# Or install from local chart
helm install keycloak-kafka ./k8s/helm \
  --values ./k8s/helm/values-production.yaml
```

#### Manual Kubernetes Deployment

```yaml
# keycloak-kafka-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak-kafka
spec:
  replicas: 2
  selector:
    matchLabels:
      app: keycloak-kafka
  template:
    metadata:
      labels:
        app: keycloak-kafka
    spec:
      containers:
      - name: keycloak
        image: scriptonbasestar/keycloak-kafka-event-listener:0.0.2
        ports:
        - containerPort: 8080
        - containerPort: 9090
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: KC_DB
          value: "postgres"
        - name: KC_DB_URL_HOST
          value: "postgres"
        configMap:
        - name: keycloak-kafka-config
          mountPath: /opt/keycloak/conf/keycloak.conf
          subPath: keycloak.conf
```

## Verification

### 1. Check Plugin Installation

```bash
# Verify JAR file
ls -la $KEYCLOAK_HOME/providers/keycloak-kafka-*

# Check Keycloak logs
tail -f $KEYCLOAK_HOME/data/log/keycloak.log | grep -i kafka

# Verify plugin registration
curl -s http://localhost:8080/health | jq .
```

### 2. Test Event Generation

```bash
# Test user authentication
curl -X POST http://localhost:8080/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli"
```

### 3. Verify Kafka Events

```bash
# Monitor user events
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic keycloak.user.events \
  --from-beginning

# Monitor admin events
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic keycloak.admin.events \
  --from-beginning
```

### 4. Check Metrics

```bash
# Check metrics endpoint
curl http://localhost:9090/metrics | grep keycloak_kafka

# Verify Prometheus scraping (if configured)
curl http://prometheus:9090/api/v1/query?query=keycloak_kafka_events_sent_total
```

## Troubleshooting

### Common Issues

#### 1. Plugin Not Loading

**Symptoms:**
- Plugin not listed in Keycloak logs
- Events not sent to Kafka

**Solutions:**
```bash
# Check JAR file integrity
jar -tf $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar | grep -i spi

# Verify file permissions
ls -la $KEYCLOAK_HOME/providers/

# Check Keycloak build process
$KEYCLOAK_HOME/bin/kc.sh build --verbose
```

#### 2. Kafka Connection Failed

**Symptoms:**
- Connection timeout errors
- Events not appearing in Kafka topics

**Solutions:**
```bash
# Test Kafka connectivity
telnet localhost 9092

# Check Kafka broker status
$KAFKA_HOME/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Verify topic existence
$KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

#### 3. Permission Issues

**Symptoms:**
- Authentication failures
- SSL handshake errors

**Solutions:**
```bash
# Check Kafka ACLs
$KAFKA_HOME/bin/kafka-acls.sh --list --bootstrap-server localhost:9092

# Verify SSL certificates
openssl s_client -connect kafka:9093 -cert client.pem -key client-key.pem

# Check SASL configuration
cat $KEYCLOAK_HOME/conf/keycloak.conf | grep sasl
```

### Log Analysis

#### Keycloak Logs

```bash
# General plugin logs
grep "KafkaEventListener" $KEYCLOAK_HOME/data/log/keycloak.log

# Error logs
grep "ERROR" $KEYCLOAK_HOME/data/log/keycloak.log | grep -i kafka

# Connection logs
grep "connection" $KEYCLOAK_HOME/data/log/keycloak.log | grep -i kafka
```

#### Kafka Logs

```bash
# Producer logs
grep "producer" $KAFKA_HOME/logs/server.log

# Authentication logs
grep "authentication" $KAFKA_HOME/logs/server.log

# Connection logs
grep "connection" $KAFKA_HOME/logs/server.log
```

## Performance Tuning

### JVM Configuration

```bash
# Add to $KEYCLOAK_HOME/conf/keycloak.conf
export JAVA_OPTS="$JAVA_OPTS -Xms1g -Xmx2g"
export JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
export JAVA_OPTS="$JAVA_OPTS -XX:+UnlockExperimentalVMOptions"
```

### Kafka Producer Tuning

```properties
# High throughput
kafka.batch.size=65536
kafka.linger.ms=20
kafka.compression.type=lz4

# Low latency
kafka.batch.size=1024
kafka.linger.ms=0
kafka.compression.type=none

# Balanced
kafka.batch.size=16384
kafka.linger.ms=5
kafka.compression.type=snappy
```

## Maintenance

### Regular Tasks

1. **Monitor disk usage** for Kafka logs
2. **Check metrics** for performance anomalies
3. **Review security logs** for authentication issues
4. **Update dependencies** regularly
5. **Backup configurations** before changes

### Upgrade Process

1. **Backup current installation**
2. **Test in staging environment**
3. **Schedule maintenance window**
4. **Follow upgrade procedure**
5. **Verify functionality**

---

For additional help, see:
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Configuration Reference](CONFIGURATION.md)
- [Security Guide](../SECURITY.md)