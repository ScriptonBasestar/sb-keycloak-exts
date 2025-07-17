# Operations Runbook - Keycloak Kafka Event Listener

This runbook provides operational procedures and best practices for managing the Keycloak Kafka Event Listener in production environments.

## Daily Operations

### Health Checks

#### System Health Verification
```bash
#!/bin/bash
# Daily health check script

echo "=== Keycloak Kafka Event Listener Health Check ==="
echo "Date: $(date)"
echo

# 1. Check Keycloak health
echo "1. Checking Keycloak health..."
if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    echo "✅ Keycloak is healthy"
else
    echo "❌ Keycloak health check failed"
fi

# 2. Check Kafka connectivity
echo "2. Checking Kafka connectivity..."
if kafka-topics.sh --list --bootstrap-server localhost:9092 >/dev/null 2>&1; then
    echo "✅ Kafka is accessible"
else
    echo "❌ Kafka connectivity failed"
fi

# 3. Check plugin metrics
echo "3. Checking plugin metrics..."
if curl -sf http://localhost:9090/metrics | grep -q keycloak_kafka; then
    echo "✅ Plugin metrics available"
else
    echo "❌ Plugin metrics not available"
fi

# 4. Check event flow
echo "4. Checking event flow..."
EVENTS_SENT=$(curl -s http://localhost:9090/metrics | grep keycloak_kafka_events_sent_total | tail -1 | awk '{print $2}')
if [ "$EVENTS_SENT" -gt 0 ]; then
    echo "✅ Events are being sent (Total: $EVENTS_SENT)"
else
    echo "⚠️  No events sent recently"
fi

# 5. Check error rates
echo "5. Checking error rates..."
EVENTS_FAILED=$(curl -s http://localhost:9090/metrics | grep keycloak_kafka_events_failed_total | tail -1 | awk '{print $2}')
ERROR_RATE=$(echo "scale=2; $EVENTS_FAILED * 100 / ($EVENTS_SENT + $EVENTS_FAILED)" | bc -l 2>/dev/null || echo "0")
if (( $(echo "$ERROR_RATE < 1" | bc -l) )); then
    echo "✅ Error rate acceptable ($ERROR_RATE%)"
else
    echo "❌ High error rate: $ERROR_RATE%"
fi

echo
echo "=== Health Check Complete ==="
```

#### Performance Monitoring
```bash
# Performance monitoring script
#!/bin/bash

echo "=== Performance Metrics ==="

# Event processing latency
LATENCY_P95=$(curl -s http://localhost:9090/api/v1/query?query=histogram_quantile\(0.95,rate\(keycloak_kafka_event_processing_duration_bucket\[5m\]\)\) | jq -r '.data.result[0].value[1]')
echo "Event Processing Latency (P95): ${LATENCY_P95}ms"

# Throughput
THROUGHPUT=$(curl -s http://localhost:9090/api/v1/query?query=rate\(keycloak_kafka_events_sent_total\[5m\]\) | jq -r '.data.result[0].value[1]')
echo "Event Throughput: ${THROUGHPUT} events/sec"

# Memory usage
MEMORY_USAGE=$(curl -s http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes\{area=\"heap\"\}/jvm_memory_max_bytes\{area=\"heap\"\}*100 | jq -r '.data.result[0].value[1]')
echo "Memory Usage: ${MEMORY_USAGE}%"

# Circuit breaker status
CB_STATUS=$(curl -s http://localhost:9090/metrics | grep keycloak_kafka_circuit_breaker_state | awk '{print $2}')
case "$CB_STATUS" in
    0) echo "Circuit Breaker: CLOSED (Normal)" ;;
    1) echo "Circuit Breaker: OPEN (Failing)" ;;
    2) echo "Circuit Breaker: HALF_OPEN (Testing)" ;;
    *) echo "Circuit Breaker: Unknown state" ;;
esac
```

### Log Monitoring

#### Daily Log Analysis
```bash
# Log analysis script
#!/bin/bash

LOG_FILE="$KEYCLOAK_HOME/data/log/keycloak.log"
TODAY=$(date +%Y-%m-%d)

echo "=== Daily Log Analysis for $TODAY ==="

# Error count
ERROR_COUNT=$(grep "$TODAY" "$LOG_FILE" | grep -c "ERROR.*kafka")
echo "Error count: $ERROR_COUNT"

# Warning count
WARN_COUNT=$(grep "$TODAY" "$LOG_FILE" | grep -c "WARN.*kafka")
echo "Warning count: $WARN_COUNT"

# Connection issues
CONN_ISSUES=$(grep "$TODAY" "$LOG_FILE" | grep -c "connection.*failed\|timeout")
echo "Connection issues: $CONN_ISSUES"

# Recent errors (last 10)
echo
echo "Recent errors:"
grep "$TODAY" "$LOG_FILE" | grep "ERROR.*kafka" | tail -10
```

### Capacity Planning

#### Resource Usage Trends
```bash
# Resource usage monitoring
#!/bin/bash

echo "=== Resource Usage Trends ==="

# Disk usage
DISK_USAGE=$(df -h $KEYCLOAK_HOME | tail -1 | awk '{print $5}')
echo "Disk usage: $DISK_USAGE"

# CPU usage
CPU_USAGE=$(top -bn1 | grep "$(pgrep -f keycloak)" | awk '{print $9}')
echo "CPU usage: ${CPU_USAGE}%"

# Memory usage
MEMORY_USAGE=$(ps -p $(pgrep -f keycloak) -o %mem --no-headers)
echo "Memory usage: ${MEMORY_USAGE}%"

# Kafka topic sizes
echo "Topic sizes:"
kafka-run-class.sh kafka.tools.GetOffsetShell \
    --broker-list localhost:9092 \
    --topic keycloak.user.events \
    --time -1 | awk -F: '{sum += $3} END {print "keycloak.user.events: " sum " messages"}'
```

## Weekly Operations

### Performance Review

#### Weekly Performance Report
```bash
#!/bin/bash
# Weekly performance analysis

WEEK_START=$(date -d "7 days ago" +%Y-%m-%d)
WEEK_END=$(date +%Y-%m-%d)

echo "=== Weekly Performance Report ($WEEK_START to $WEEK_END) ==="

# Generate performance metrics
curl -G http://localhost:9090/api/v1/query_range \
    --data-urlencode "query=rate(keycloak_kafka_events_sent_total[1h])" \
    --data-urlencode "start=${WEEK_START}T00:00:00Z" \
    --data-urlencode "end=${WEEK_END}T23:59:59Z" \
    --data-urlencode "step=1h" > weekly_throughput.json

# Calculate averages
python3 -c "
import json
with open('weekly_throughput.json') as f:
    data = json.load(f)
values = [float(point[1]) for point in data['data']['result'][0]['values']]
print(f'Average throughput: {sum(values)/len(values):.2f} events/sec')
print(f'Peak throughput: {max(values):.2f} events/sec')
print(f'Min throughput: {min(values):.2f} events/sec')
"
```

### Maintenance Tasks

#### Log Rotation
```bash
#!/bin/bash
# Log rotation script

LOG_DIR="$KEYCLOAK_HOME/data/log"
ARCHIVE_DIR="$LOG_DIR/archive/$(date +%Y/%m)"

mkdir -p "$ARCHIVE_DIR"

# Rotate Keycloak logs
if [ -f "$LOG_DIR/keycloak.log" ]; then
    gzip -c "$LOG_DIR/keycloak.log" > "$ARCHIVE_DIR/keycloak-$(date +%Y%m%d).log.gz"
    > "$LOG_DIR/keycloak.log"  # Truncate current log
fi

# Remove logs older than 90 days
find "$LOG_DIR/archive" -name "*.gz" -mtime +90 -delete

echo "Log rotation completed"
```

#### Configuration Backup
```bash
#!/bin/bash
# Configuration backup script

BACKUP_DIR="/backup/keycloak-kafka/$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"

# Backup configuration files
cp "$KEYCLOAK_HOME/conf/keycloak.conf" "$BACKUP_DIR/"
cp -r "$KEYCLOAK_HOME/ssl/" "$BACKUP_DIR/" 2>/dev/null || true

# Backup database configuration (if applicable)
mysqldump -u keycloak -p keycloak > "$BACKUP_DIR/keycloak_db.sql" 2>/dev/null || true

# Create backup manifest
cat > "$BACKUP_DIR/manifest.txt" << EOF
Backup Date: $(date)
Keycloak Version: $(cat $KEYCLOAK_HOME/version.txt 2>/dev/null || echo "Unknown")
Plugin Version: $(jar -tf $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar | grep META-INF/MANIFEST.MF | xargs jar -xf $KEYCLOAK_HOME/providers/keycloak-kafka-*.jar && grep Implementation-Version META-INF/MANIFEST.MF 2>/dev/null || echo "Unknown")
Configuration Files:
$(ls -la "$BACKUP_DIR")
EOF

echo "Configuration backup completed: $BACKUP_DIR"
```

## Monthly Operations

### Security Review

#### Security Audit Checklist
```bash
#!/bin/bash
# Monthly security audit

echo "=== Monthly Security Audit ==="

# 1. Check certificate expiration
echo "1. Checking SSL certificates..."
if [ -f "$KEYCLOAK_HOME/ssl/client.crt" ]; then
    CERT_EXPIRY=$(openssl x509 -in "$KEYCLOAK_HOME/ssl/client.crt" -noout -enddate | cut -d= -f2)
    DAYS_UNTIL_EXPIRY=$(( ($(date -d "$CERT_EXPIRY" +%s) - $(date +%s)) / 86400 ))
    if [ $DAYS_UNTIL_EXPIRY -lt 30 ]; then
        echo "❌ Certificate expires in $DAYS_UNTIL_EXPIRY days"
    else
        echo "✅ Certificate valid for $DAYS_UNTIL_EXPIRY days"
    fi
fi

# 2. Check encrypted credentials
echo "2. Checking credential encryption..."
ENCRYPTED_CREDS=$(grep -c "ENC(" "$KEYCLOAK_HOME/conf/keycloak.conf")
if [ $ENCRYPTED_CREDS -gt 0 ]; then
    echo "✅ Found $ENCRYPTED_CREDS encrypted credentials"
else
    echo "⚠️  No encrypted credentials found"
fi

# 3. Check access logs for suspicious activity
echo "3. Checking for suspicious activity..."
FAILED_AUTHS=$(grep "$(date +%Y-%m)" "$KEYCLOAK_HOME/data/log/keycloak.log" | grep -c "authentication failed")
echo "Failed authentications this month: $FAILED_AUTHS"

# 4. Verify TLS configuration
echo "4. Verifying TLS configuration..."
if grep -q "kafka.security.protocol=SSL\|kafka.security.protocol=SASL_SSL" "$KEYCLOAK_HOME/conf/keycloak.conf"; then
    echo "✅ TLS enabled for Kafka communication"
else
    echo "⚠️  TLS not configured for Kafka"
fi
```

### Capacity Planning Review

#### Monthly Capacity Report
```bash
#!/bin/bash
# Monthly capacity analysis

echo "=== Monthly Capacity Analysis ==="

# Disk growth trend
MONTH_START=$(date -d "1 month ago" +%Y-%m-%d)
CURRENT_USAGE=$(df -h $KEYCLOAK_HOME | tail -1 | awk '{print $3}')
echo "Current disk usage: $CURRENT_USAGE"

# Memory trends
MEMORY_SAMPLES=$(find /var/log/system-metrics -name "memory-*.log" -mtime -30 | wc -l)
if [ $MEMORY_SAMPLES -gt 0 ]; then
    AVG_MEMORY=$(awk '{sum+=$1} END {print sum/NR}' /var/log/system-metrics/memory-*.log)
    echo "Average memory usage this month: ${AVG_MEMORY}%"
fi

# Event volume trends
TOTAL_EVENTS=$(curl -s http://localhost:9090/api/v1/query?query=keycloak_kafka_events_sent_total | jq -r '.data.result[0].value[1]')
echo "Total events processed: $TOTAL_EVENTS"

# Recommendations
echo
echo "Capacity Recommendations:"
if (( $(echo "$AVG_MEMORY > 80" | bc -l) )); then
    echo "- Consider increasing memory allocation"
fi
if [ "$CURRENT_USAGE" -gt "70%" ]; then
    echo "- Consider disk cleanup or expansion"
fi
```

## Incident Response

### Escalation Procedures

#### Level 1: Service Degradation
```bash
# Service degradation response
#!/bin/bash

echo "=== Level 1 Incident Response ==="

# 1. Quick diagnosis
./scripts/health-check.sh

# 2. Check circuit breaker
CB_STATE=$(curl -s http://localhost:9090/metrics | grep circuit_breaker_state | awk '{print $2}')
if [ "$CB_STATE" = "1" ]; then
    echo "Circuit breaker is OPEN - attempting reset"
    curl -X POST http://localhost:9090/admin/circuit-breaker/reset
fi

# 3. Check backpressure
BP_ACTIVE=$(curl -s http://localhost:9090/metrics | grep backpressure_active | awk '{print $2}')
if [ "$BP_ACTIVE" = "1" ]; then
    echo "Backpressure is active - checking queue"
    curl -s http://localhost:9090/metrics | grep backpressure_queue_size
fi

# 4. Restart services if needed
read -p "Restart Keycloak service? (y/N): " -n 1 -r
if [[ $REPLY =~ ^[Yy]$ ]]; then
    systemctl restart keycloak
fi
```

#### Level 2: Service Outage
```bash
# Service outage response
#!/bin/bash

echo "=== Level 2 Incident Response ==="

# 1. Emergency diagnostics
./scripts/collect-diagnostics.sh

# 2. Check infrastructure
ping kafka-broker
nslookup kafka-broker

# 3. Verify configuration
diff "$KEYCLOAK_HOME/conf/keycloak.conf" "/backup/keycloak-kafka/latest/keycloak.conf"

# 4. Emergency procedures
echo "Starting emergency procedures..."

# Force circuit breaker reset
curl -X POST http://localhost:9090/admin/circuit-breaker/reset

# Drain backpressure buffer
curl -X POST http://localhost:9090/admin/backpressure/drain

# Restart with safe mode
export KAFKA_CIRCUIT_BREAKER_ENABLED=false
systemctl restart keycloak
```

### Recovery Procedures

#### Database Recovery
```bash
#!/bin/bash
# Database recovery script

echo "=== Database Recovery ==="

# 1. Stop Keycloak
systemctl stop keycloak

# 2. Backup current state
mysqldump -u keycloak -p keycloak > "/backup/emergency-$(date +%Y%m%d%H%M%S).sql"

# 3. Restore from backup
read -p "Enter backup file to restore: " BACKUP_FILE
if [ -f "$BACKUP_FILE" ]; then
    mysql -u keycloak -p keycloak < "$BACKUP_FILE"
    echo "Database restored from $BACKUP_FILE"
else
    echo "Backup file not found"
    exit 1
fi

# 4. Restart Keycloak
systemctl start keycloak

# 5. Verify recovery
sleep 30
./scripts/health-check.sh
```

#### Configuration Recovery
```bash
#!/bin/bash
# Configuration recovery script

echo "=== Configuration Recovery ==="

# 1. Backup current configuration
cp "$KEYCLOAK_HOME/conf/keycloak.conf" "$KEYCLOAK_HOME/conf/keycloak.conf.emergency"

# 2. Restore known good configuration
LATEST_BACKUP=$(ls -t /backup/keycloak-kafka/*/keycloak.conf | head -1)
if [ -f "$LATEST_BACKUP" ]; then
    cp "$LATEST_BACKUP" "$KEYCLOAK_HOME/conf/keycloak.conf"
    echo "Configuration restored from $LATEST_BACKUP"
else
    echo "No backup configuration found"
    exit 1
fi

# 3. Validate configuration
./scripts/validate-configuration.sh

# 4. Restart service
systemctl restart keycloak
```

## Deployment Procedures

### Rolling Updates

#### Blue-Green Deployment
```bash
#!/bin/bash
# Blue-green deployment script

CURRENT_COLOR=${1:-blue}
NEW_COLOR=${2:-green}

echo "=== Blue-Green Deployment: $CURRENT_COLOR -> $NEW_COLOR ==="

# 1. Deploy to green environment
docker-compose -f docker-compose-${NEW_COLOR}.yml up -d

# 2. Health check new environment
sleep 60
if ! curl -sf http://${NEW_COLOR}-keycloak:8080/health; then
    echo "Health check failed for $NEW_COLOR environment"
    exit 1
fi

# 3. Switch load balancer
echo "Switching traffic to $NEW_COLOR environment"
nginx -s reload  # Assumes nginx configuration switch

# 4. Monitor for 10 minutes
for i in {1..10}; do
    echo "Monitoring minute $i/10..."
    sleep 60
    if ! curl -sf http://${NEW_COLOR}-keycloak:8080/health; then
        echo "Health check failed - rolling back"
        nginx -s reload  # Switch back
        exit 1
    fi
done

# 5. Shutdown old environment
echo "Shutting down $CURRENT_COLOR environment"
docker-compose -f docker-compose-${CURRENT_COLOR}.yml down

echo "Deployment completed successfully"
```

### Rollback Procedures

#### Emergency Rollback
```bash
#!/bin/bash
# Emergency rollback script

PREVIOUS_VERSION=${1:-previous}

echo "=== Emergency Rollback to $PREVIOUS_VERSION ==="

# 1. Stop current services
systemctl stop keycloak

# 2. Restore previous JAR
if [ -f "$KEYCLOAK_HOME/providers/backup/keycloak-kafka-${PREVIOUS_VERSION}.jar" ]; then
    cp "$KEYCLOAK_HOME/providers/backup/keycloak-kafka-${PREVIOUS_VERSION}.jar" \
       "$KEYCLOAK_HOME/providers/keycloak-kafka-event-listener.jar"
else
    echo "Previous version not found"
    exit 1
fi

# 3. Restore configuration
if [ -f "/backup/keycloak-kafka/${PREVIOUS_VERSION}/keycloak.conf" ]; then
    cp "/backup/keycloak-kafka/${PREVIOUS_VERSION}/keycloak.conf" \
       "$KEYCLOAK_HOME/conf/keycloak.conf"
fi

# 4. Rebuild and restart
$KEYCLOAK_HOME/bin/kc.sh build
systemctl start keycloak

# 5. Verify rollback
sleep 30
./scripts/health-check.sh
```

## Automation Scripts

### Monitoring Automation

#### Automated Alerting
```bash
#!/bin/bash
# Automated monitoring and alerting

ALERT_THRESHOLD_ERROR_RATE=5
ALERT_THRESHOLD_LATENCY=1000
ALERT_THRESHOLD_MEMORY=90

# Check error rate
ERROR_RATE=$(curl -s http://localhost:9090/api/v1/query?query=rate\(keycloak_kafka_events_failed_total\[5m\]\)*100 | jq -r '.data.result[0].value[1]')
if (( $(echo "$ERROR_RATE > $ALERT_THRESHOLD_ERROR_RATE" | bc -l) )); then
    echo "ALERT: High error rate detected: $ERROR_RATE%" | mail -s "Keycloak Kafka Alert" admin@company.com
fi

# Check latency
LATENCY=$(curl -s http://localhost:9090/api/v1/query?query=histogram_quantile\(0.95,rate\(keycloak_kafka_event_processing_duration_bucket\[5m\]\)\) | jq -r '.data.result[0].value[1]')
if (( $(echo "$LATENCY > $ALERT_THRESHOLD_LATENCY" | bc -l) )); then
    echo "ALERT: High latency detected: ${LATENCY}ms" | mail -s "Keycloak Kafka Alert" admin@company.com
fi

# Check memory usage
MEMORY_USAGE=$(curl -s http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes\{area=\"heap\"\}/jvm_memory_max_bytes\{area=\"heap\"\}*100 | jq -r '.data.result[0].value[1]')
if (( $(echo "$MEMORY_USAGE > $ALERT_THRESHOLD_MEMORY" | bc -l) )); then
    echo "ALERT: High memory usage: ${MEMORY_USAGE}%" | mail -s "Keycloak Kafka Alert" admin@company.com
fi
```

## Contact Information

### On-Call Escalation

1. **Level 1 Support**: `support-l1@company.com`
2. **Level 2 Support**: `support-l2@company.com`
3. **Engineering Team**: `eng-team@company.com`
4. **Emergency Contact**: `+1-555-EMERGENCY`

### External Dependencies

- **Kafka Team**: `kafka-team@company.com`
- **Infrastructure Team**: `infra-team@company.com`
- **Security Team**: `security-team@company.com`

---

This runbook should be regularly updated and tested to ensure procedures remain current and effective.