# Troubleshooting Guide - Keycloak Kafka Event Listener

This guide provides solutions for common issues encountered when using the Keycloak Kafka Event Listener.

## Quick Diagnostics

### Health Check Commands

```bash
# Check Keycloak health
curl -f http://localhost:8080/health

# Check Kafka connectivity
kafka-topics.sh --list --bootstrap-server localhost:9092

# Check plugin metrics
curl http://localhost:9090/metrics | grep keycloak_kafka

# Check Keycloak logs
tail -f $KEYCLOAK_HOME/data/log/keycloak.log | grep -i kafka
```

### System Status Overview

```bash
# Quick system check script
./scripts/health-check.sh

# Performance benchmark
./scripts/performance-benchmark.sh
```

## Common Issues

### 1. Plugin Installation Issues

#### Issue: Plugin Not Loading
**Symptoms:**
- Plugin not visible in Keycloak Admin Console
- No Kafka-related logs in Keycloak startup
- Events not being sent to Kafka

**Diagnosis:**
```bash
# Check if JAR file exists
ls -la $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar

# Verify JAR integrity
jar -tf $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar | grep -i spi

# Check Keycloak build logs
$KEYCLOAK_HOME/bin/kc.sh build --verbose 2>&1 | grep -i kafka
```

**Solutions:**
1. **Verify JAR placement:**
   ```bash
   cp keycloak-kafka-event-listener-*.jar $KEYCLOAK_HOME/providers/
   chmod 644 $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar
   ```

2. **Rebuild Keycloak:**
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build --verbose
   ```

3. **Check dependencies:**
   ```bash
   # Ensure required Kafka client libraries are included
   jar -tf $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar | grep kafka
   ```

#### Issue: ClassNotFoundException
**Symptoms:**
- Java ClassNotFoundException in logs
- Plugin fails to initialize

**Solutions:**
1. **Check Shadow JAR configuration:**
   ```bash
   # Verify all dependencies are included
   ./gradlew :events:event-listener-kafka:dependencies
   ```

2. **Rebuild with all dependencies:**
   ```bash
   ./gradlew :events:event-listener-kafka:shadowJar
   ```

### 2. Kafka Connectivity Issues

#### Issue: Connection Refused
**Symptoms:**
- `Connection to node -1 could not be established`
- Events not reaching Kafka topics
- High connection error metrics

**Diagnosis:**
```bash
# Test Kafka connectivity
telnet localhost 9092

# Check Kafka broker status
kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# Verify network connectivity
netstat -tuln | grep 9092
```

**Solutions:**
1. **Verify Kafka configuration:**
   ```properties
   # keycloak.conf
   kafka.bootstrap.servers=localhost:9092
   kafka.security.protocol=PLAINTEXT
   ```

2. **Check firewall settings:**
   ```bash
   # Allow Kafka port
   sudo ufw allow 9092
   ```

3. **Validate DNS resolution:**
   ```bash
   nslookup kafka-broker-hostname
   ```

#### Issue: Authentication Failures
**Symptoms:**
- SASL authentication failed
- SSL handshake errors
- Authentication timeout

**Diagnosis:**
```bash
# Test SASL authentication
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic test --producer.config client.properties

# Check SSL certificates
openssl s_client -connect kafka:9093 -cert client.pem -key client-key.pem
```

**Solutions:**
1. **SASL Configuration:**
   ```properties
   kafka.security.protocol=SASL_SSL
   kafka.sasl.mechanism=SCRAM-SHA-256
   kafka.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="user" password="password";
   ```

2. **SSL Configuration:**
   ```properties
   kafka.ssl.truststore.location=/path/to/kafka.client.truststore.jks
   kafka.ssl.truststore.password=truststore_password
   kafka.ssl.keystore.location=/path/to/kafka.client.keystore.jks
   kafka.ssl.keystore.password=keystore_password
   ```

### 3. Event Processing Issues

#### Issue: Events Not Sent
**Symptoms:**
- User actions don't generate Kafka events
- Empty Kafka topics
- Zero metrics in monitoring

**Diagnosis:**
```bash
# Check event listener configuration
curl -s http://localhost:8080/admin/realms/master | jq '.eventsListeners'

# Monitor Kafka consumer
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic keycloak.user.events --from-beginning

# Check circuit breaker status
curl http://localhost:9090/metrics | grep circuit_breaker
```

**Solutions:**
1. **Enable event listener in Keycloak:**
   - Admin Console → Events → Config
   - Add `kafka-event-listener` to Event Listeners
   - Save configuration

2. **Verify topic configuration:**
   ```bash
   # Create topics if missing
   kafka-topics.sh --create --bootstrap-server localhost:9092 \
     --topic keycloak.user.events --partitions 3 --replication-factor 1
   ```

3. **Check event filtering:**
   ```properties
   # Ensure events are not filtered out
   kafka.included.event.types=LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE
   kafka.user.events.enabled=true
   ```

#### Issue: Duplicate Events
**Symptoms:**
- Same events appearing multiple times in Kafka
- High event volume metrics
- Consumer receiving duplicates

**Solutions:**
1. **Enable idempotent producer:**
   ```properties
   kafka.enable.idempotence=true
   kafka.acks=all
   kafka.max.in.flight.requests.per.connection=1
   ```

2. **Check event listener registration:**
   ```bash
   # Ensure listener is registered only once
   grep -r "kafka-event-listener" $KEYCLOAK_HOME/conf/
   ```

### 4. Performance Issues

#### Issue: High Latency
**Symptoms:**
- Slow user authentication
- High event processing time metrics
- Timeouts in application logs

**Diagnosis:**
```bash
# Check performance metrics
curl http://localhost:9090/metrics | grep keycloak_kafka_event_processing_duration

# Monitor JVM performance
jstat -gc $(pgrep -f keycloak) 5s

# Check Kafka producer metrics
curl http://localhost:9090/metrics | grep kafka_producer
```

**Solutions:**
1. **Optimize Kafka producer settings:**
   ```properties
   # Low latency profile
   kafka.batch.size=1024
   kafka.linger.ms=0
   kafka.compression.type=none
   kafka.acks=1
   ```

2. **Tune JVM settings:**
   ```bash
   export JAVA_OPTS="$JAVA_OPTS -Xms2g -Xmx4g -XX:+UseG1GC"
   ```

3. **Enable connection pooling:**
   ```properties
   kafka.connection.pool.enabled=true
   kafka.connection.pool.size=5
   ```

#### Issue: Memory Leaks
**Symptoms:**
- Increasing memory usage over time
- OutOfMemoryError in logs
- Garbage collection warnings

**Solutions:**
1. **Monitor memory usage:**
   ```bash
   # Check heap usage
   jstat -gccapacity $(pgrep -f keycloak)
   
   # Generate heap dump if needed
   jcmd $(pgrep -f keycloak) GC.run_finalization
   ```

2. **Optimize producer lifecycle:**
   ```properties
   kafka.producer.close.timeout.ms=10000
   kafka.connection.max.idle.ms=300000
   ```

### 5. Security Issues

#### Issue: SSL/TLS Handshake Failures
**Symptoms:**
- SSL handshake failed errors
- Connection drops during TLS negotiation
- Certificate validation errors

**Diagnosis:**
```bash
# Test SSL connection
openssl s_client -connect kafka:9093 -verify_return_error

# Check certificate validity
openssl x509 -in client.crt -text -noout

# Verify certificate chain
openssl verify -CAfile ca-cert.pem client.crt
```

**Solutions:**
1. **Update certificates:**
   ```bash
   # Import certificates to truststore
   keytool -import -alias kafka -file kafka-cert.pem -keystore truststore.jks
   ```

2. **Configure SSL properly:**
   ```properties
   kafka.ssl.endpoint.identification.algorithm=
   kafka.ssl.truststore.type=JKS
   kafka.ssl.keystore.type=JKS
   ```

#### Issue: Credential Encryption Errors
**Symptoms:**
- Failed to decrypt credentials
- ENC() notation not working
- Authentication failures

**Solutions:**
1. **Verify master key:**
   ```bash
   # Check master key configuration
   grep encryption.master.key $KEYCLOAK_HOME/conf/keycloak.conf
   ```

2. **Re-encrypt credentials:**
   ```bash
   # Use encryption utility
   java -cp keycloak-kafka-event-listener-*.jar \
     org.scriptonbasestar.kcexts.events.kafka.security.SecretEncryptionManager \
     encrypt "your-password"
   ```

### 6. Monitoring and Metrics Issues

#### Issue: Missing Metrics
**Symptoms:**
- No metrics in Prometheus
- Empty Grafana dashboards
- Monitoring endpoints not responding

**Diagnosis:**
```bash
# Check metrics endpoint
curl http://localhost:9090/metrics

# Verify Prometheus configuration
curl http://prometheus:9090/api/v1/targets

# Check scraping status
curl http://prometheus:9090/api/v1/query?query=up
```

**Solutions:**
1. **Enable metrics in configuration:**
   ```properties
   metrics.enabled=true
   metrics.port=9090
   metrics.host=0.0.0.0
   ```

2. **Verify Prometheus scraping:**
   ```yaml
   # prometheus.yml
   scrape_configs:
     - job_name: 'keycloak-kafka'
       static_configs:
         - targets: ['localhost:9090']
   ```

### 7. Container and Deployment Issues

#### Issue: Docker Container Startup Failures
**Symptoms:**
- Container exits immediately
- Health check failures
- Initialization errors

**Diagnosis:**
```bash
# Check container logs
docker logs keycloak-kafka

# Inspect container configuration
docker inspect keycloak-kafka

# Check resource usage
docker stats keycloak-kafka
```

**Solutions:**
1. **Verify resource limits:**
   ```yaml
   # docker-compose.yml
   services:
     keycloak:
       image: scriptonbasestar/keycloak-kafka-event-listener:0.0.2
       mem_limit: 2g
       cpu_limit: 1.0
   ```

2. **Check environment variables:**
   ```bash
   # Verify required environment variables
   docker exec keycloak-kafka env | grep KAFKA
   ```

#### Issue: Kubernetes Pod CrashLoops
**Symptoms:**
- Pod restarts continuously
- CrashLoopBackOff status
- Application startup failures

**Solutions:**
1. **Check pod logs:**
   ```bash
   kubectl logs -f deployment/keycloak-kafka
   kubectl describe pod keycloak-kafka-xxx
   ```

2. **Verify resource requests:**
   ```yaml
   # deployment.yaml
   resources:
     requests:
       memory: "1Gi"
       cpu: "500m"
     limits:
       memory: "2Gi"
       cpu: "1000m"
   ```

## Debugging Tools and Commands

### Log Analysis

```bash
# Real-time log monitoring
tail -f $KEYCLOAK_HOME/data/log/keycloak.log | grep -E "(ERROR|WARN|kafka)"

# Search for specific patterns
grep -n "KafkaEventListener" $KEYCLOAK_HOME/data/log/keycloak.log

# Analyze error patterns
awk '/ERROR.*kafka/ {print $0}' $KEYCLOAK_HOME/data/log/keycloak.log | sort | uniq -c
```

### Performance Analysis

```bash
# JVM performance monitoring
jstat -gc $(pgrep -f keycloak) 5s 10

# Thread dump analysis
jstack $(pgrep -f keycloak) > thread_dump.txt

# Memory analysis
jmap -histo $(pgrep -f keycloak) | head -20
```

### Network Diagnostics

```bash
# Test Kafka connectivity
nc -zv kafka-host 9092

# Monitor network traffic
tcpdump -i any -n port 9092

# Check DNS resolution
dig kafka-host
```

## Emergency Procedures

### Circuit Breaker Emergency

If the circuit breaker is stuck open:

```bash
# Force circuit breaker reset
curl -X POST http://localhost:9090/admin/circuit-breaker/reset

# Check current state
curl http://localhost:9090/admin/circuit-breaker/status
```

### High Memory Usage

```bash
# Force garbage collection
jcmd $(pgrep -f keycloak) GC.run

# Generate heap dump for analysis
jcmd $(pgrep -f keycloak) GC.heap_dump /tmp/keycloak-heap.hprof

# Restart with memory profiling
export JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/"
```

### Kafka Topic Recovery

```bash
# Reset consumer group offset
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group keycloak-consumer --reset-offsets --to-earliest --topic keycloak.user.events --execute

# Recreate corrupted topics
kafka-topics.sh --delete --bootstrap-server localhost:9092 --topic keycloak.user.events
kafka-topics.sh --create --bootstrap-server localhost:9092 \
  --topic keycloak.user.events --partitions 3 --replication-factor 1
```

## Support and Further Help

### Log Files Location

- **Keycloak Logs:** `$KEYCLOAK_HOME/data/log/keycloak.log`
- **Application Logs:** `/opt/keycloak/logs/application.log`
- **Kafka Logs:** `$KAFKA_HOME/logs/server.log`
- **Container Logs:** `docker logs container-name`

### Configuration Files

- **Main Config:** `$KEYCLOAK_HOME/conf/keycloak.conf`
- **Docker Config:** `/opt/keycloak/conf/keycloak.conf`
- **Kafka Properties:** `/opt/keycloak/conf/kafka.properties`

### Metrics and Monitoring

- **Metrics Endpoint:** `http://localhost:9090/metrics`
- **Health Check:** `http://localhost:8080/health`
- **Admin API:** `http://localhost:8080/admin/`

### Getting Help

1. **Check Documentation:**
   - [Installation Guide](INSTALLATION.md)
   - [Configuration Reference](CONFIGURATION.md)
   - [Security Guide](../SECURITY.md)

2. **Enable Debug Logging:**
   ```properties
   # Add to keycloak.conf
   log-level=DEBUG
   log=console,file
   ```

3. **Collect Diagnostic Information:**
   ```bash
   # Run diagnostic script
   ./scripts/collect-diagnostics.sh
   ```

4. **Report Issues:**
   - GitHub Issues: [sb-keycloak-exts/issues](https://github.com/scriptonbasestar/sb-keycloak-exts/issues)
   - Include logs, configuration, and error messages
   - Provide environment details (OS, Java version, Keycloak version)

---

For additional support, please refer to the [Configuration Reference](CONFIGURATION.md) and [Security Guide](../SECURITY.md).