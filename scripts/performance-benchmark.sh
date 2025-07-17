#!/bin/bash

# Keycloak Kafka Event Listener - Performance Benchmark Script
# Comprehensive performance testing suite

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$PROJECT_ROOT/benchmark-results/$(date +%Y%m%d_%H%M%S)"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

# Test scenarios
SCENARIOS=(
    "baseline:100:30"           # 100 events/sec for 30 seconds
    "moderate:500:60"           # 500 events/sec for 60 seconds  
    "high_load:1000:120"        # 1000 events/sec for 120 seconds
    "burst:2000:30"             # 2000 events/sec for 30 seconds (burst test)
    "sustained:750:300"         # 750 events/sec for 5 minutes (sustained test)
    "ramp_up:100-1500:180"      # Ramp from 100 to 1500 events/sec over 3 minutes
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create results directory
mkdir -p "$RESULTS_DIR"
cd "$RESULTS_DIR"

# Functions

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check required tools
    local required_tools=("curl" "jq" "ab" "kafka-console-consumer.sh" "python3")
    
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "Required tool not found: $tool"
            exit 1
        fi
    done
    
    # Check Keycloak availability
    if ! curl -s -f "$KEYCLOAK_URL/health" > /dev/null; then
        log_error "Keycloak not available at $KEYCLOAK_URL"
        exit 1
    fi
    
    # Check Kafka availability
    if ! timeout 10 bash -c "</dev/tcp/${KAFKA_BOOTSTRAP_SERVERS%%:*}/${KAFKA_BOOTSTRAP_SERVERS##*:}"; then
        log_error "Kafka not available at $KAFKA_BOOTSTRAP_SERVERS"
        exit 1
    fi
    
    # Check Prometheus availability (optional)
    if ! curl -s -f "$PROMETHEUS_URL/api/v1/query?query=up" > /dev/null; then
        log_warning "Prometheus not available at $PROMETHEUS_URL - metrics collection disabled"
        PROMETHEUS_AVAILABLE=false
    else
        PROMETHEUS_AVAILABLE=true
    fi
    
    log_success "Prerequisites check passed"
}

setup_test_environment() {
    log_info "Setting up test environment..."
    
    # Create test realm and users if needed
    setup_keycloak_test_realm
    
    # Start Kafka consumer for event monitoring
    start_kafka_consumer
    
    # Initialize metrics collection
    if [ "$PROMETHEUS_AVAILABLE" = true ]; then
        setup_metrics_collection
    fi
    
    log_success "Test environment ready"
}

setup_keycloak_test_realm() {
    log_info "Setting up Keycloak test realm..."
    
    # Get admin token
    local admin_token
    admin_token=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=admin" \
        -d "password=admin" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" | jq -r '.access_token')
    
    if [ "$admin_token" = "null" ] || [ -z "$admin_token" ]; then
        log_error "Failed to get admin token"
        exit 1
    fi
    
    # Create test realm
    curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
        -H "Authorization: Bearer $admin_token" \
        -H "Content-Type: application/json" \
        -d '{
            "realm": "performance-test",
            "enabled": true,
            "loginWithEmailAllowed": true,
            "registrationAllowed": true,
            "eventsEnabled": true,
            "eventsListeners": ["kafka-event-listener"],
            "adminEventsEnabled": true,
            "adminEventsDetailsEnabled": true
        }' || log_warning "Test realm may already exist"
    
    # Create test client
    curl -s -X POST "$KEYCLOAK_URL/admin/realms/performance-test/clients" \
        -H "Authorization: Bearer $admin_token" \
        -H "Content-Type: application/json" \
        -d '{
            "clientId": "performance-test-client",
            "enabled": true,
            "directAccessGrantsEnabled": true,
            "standardFlowEnabled": true,
            "publicClient": true
        }' || log_warning "Test client may already exist"
    
    # Create test users
    for i in {1..100}; do
        curl -s -X POST "$KEYCLOAK_URL/admin/realms/performance-test/users" \
            -H "Authorization: Bearer $admin_token" \
            -H "Content-Type: application/json" \
            -d "{
                \"username\": \"testuser$i\",
                \"email\": \"testuser$i@example.com\",
                \"enabled\": true,
                \"credentials\": [{
                    \"type\": \"password\",
                    \"value\": \"password123\",
                    \"temporary\": false
                }]
            }" > /dev/null 2>&1 || true
    done
    
    log_success "Keycloak test realm configured"
}

start_kafka_consumer() {
    log_info "Starting Kafka consumer for event monitoring..."
    
    # Start consumer in background
    kafka-console-consumer.sh \
        --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
        --topic keycloak.user.events \
        --from-beginning > "user_events.log" 2>&1 &
    
    KAFKA_CONSUMER_PID=$!
    
    # Also consume admin events
    kafka-console-consumer.sh \
        --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
        --topic keycloak.admin.events \
        --from-beginning > "admin_events.log" 2>&1 &
    
    KAFKA_ADMIN_CONSUMER_PID=$!
    
    log_success "Kafka consumers started (PIDs: $KAFKA_CONSUMER_PID, $KAFKA_ADMIN_CONSUMER_PID)"
}

setup_metrics_collection() {
    log_info "Setting up metrics collection..."
    
    cat > "collect_metrics.py" << 'EOF'
#!/usr/bin/env python3
import requests
import json
import time
import sys
from datetime import datetime

prometheus_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:9090"
interval = int(sys.argv[2]) if len(sys.argv) > 2 else 10
duration = int(sys.argv[3]) if len(sys.argv) > 3 else 300

metrics_queries = [
    "rate(keycloak_kafka_events_sent_total[1m])",
    "rate(keycloak_kafka_events_failed_total[1m])",
    "histogram_quantile(0.95, rate(keycloak_kafka_event_processing_duration_bucket[1m]))",
    "histogram_quantile(0.99, rate(keycloak_kafka_event_processing_duration_bucket[1m]))",
    "keycloak_kafka_connection_status",
    "keycloak_kafka_producer_queue_size",
    "rate(jvm_gc_collection_seconds_sum[1m])",
    "jvm_memory_used_bytes{area=\"heap\"}/jvm_memory_max_bytes{area=\"heap\"}*100"
]

def collect_metrics():
    results = []
    timestamp = datetime.now().isoformat()
    
    for query in metrics_queries:
        try:
            response = requests.get(f"{prometheus_url}/api/v1/query", 
                                  params={"query": query}, timeout=5)
            if response.status_code == 200:
                data = response.json()
                results.append({
                    "timestamp": timestamp,
                    "query": query,
                    "result": data.get("data", {}).get("result", [])
                })
        except Exception as e:
            print(f"Error collecting metric {query}: {e}", file=sys.stderr)
    
    return results

def main():
    start_time = time.time()
    all_metrics = []
    
    while time.time() - start_time < duration:
        metrics = collect_metrics()
        all_metrics.extend(metrics)
        
        with open("metrics.json", "w") as f:
            json.dump(all_metrics, f, indent=2)
        
        time.sleep(interval)
    
    print(f"Metrics collection completed. {len(all_metrics)} data points collected.")

if __name__ == "__main__":
    main()
EOF
    
    chmod +x "collect_metrics.py"
    log_success "Metrics collection script ready"
}

run_performance_test() {
    local scenario="$1"
    local rate="$2"
    local duration="$3"
    
    log_info "Running performance test: $scenario (rate: $rate events/sec, duration: ${duration}s)"
    
    local test_start=$(date +%s)
    local result_file="result_${scenario}.json"
    
    # Start metrics collection if available
    local metrics_pid=""
    if [ "$PROMETHEUS_AVAILABLE" = true ]; then
        python3 collect_metrics.py "$PROMETHEUS_URL" 5 "$duration" &
        metrics_pid=$!
    fi
    
    # Run the actual performance test based on scenario type
    if [[ "$rate" == *"-"* ]]; then
        # Ramp test
        run_ramp_test "$scenario" "$rate" "$duration"
    else
        # Steady rate test
        run_steady_rate_test "$scenario" "$rate" "$duration"
    fi
    
    local test_end=$(date +%s)
    local test_duration=$((test_end - test_start))
    
    # Wait for metrics collection to complete
    if [ -n "$metrics_pid" ]; then
        wait "$metrics_pid"
    fi
    
    # Analyze results
    analyze_test_results "$scenario" "$test_duration"
    
    log_success "Test completed: $scenario"
}

run_steady_rate_test() {
    local scenario="$1"
    local rate="$2"
    local duration="$3"
    
    local total_requests=$((rate * duration))
    local concurrency=$((rate / 10)) # Adjust concurrency based on rate
    concurrency=$((concurrency > 1 ? concurrency : 1))
    concurrency=$((concurrency < 50 ? concurrency : 50)) # Cap at 50
    
    log_info "Running steady rate test: $total_requests requests, concurrency: $concurrency"
    
    # Create test script for authentication requests
    cat > "auth_test_${scenario}.sh" << EOF
#!/bin/bash
for i in \$(seq 1 $total_requests); do
    user_id=\$(((\$i % 100) + 1))
    curl -s -X POST "$KEYCLOAK_URL/realms/performance-test/protocol/openid-connect/token" \\
        -H "Content-Type: application/x-www-form-urlencoded" \\
        -d "username=testuser\$user_id" \\
        -d "password=password123" \\
        -d "grant_type=password" \\
        -d "client_id=performance-test-client" > /dev/null &
    
    # Control rate
    if (( \$i % $rate == 0 )); then
        sleep 1
        wait
    fi
done
wait
EOF
    
    chmod +x "auth_test_${scenario}.sh"
    
    # Run the test
    time "./auth_test_${scenario}.sh" > "test_output_${scenario}.log" 2>&1
}

run_ramp_test() {
    local scenario="$1"
    local rate_range="$2"
    local duration="$3"
    
    local start_rate=${rate_range%-*}
    local end_rate=${rate_range#*-}
    local steps=10
    local step_duration=$((duration / steps))
    
    log_info "Running ramp test: $start_rate to $end_rate events/sec over ${duration}s"
    
    for i in $(seq 0 $((steps - 1))); do
        local current_rate=$((start_rate + (end_rate - start_rate) * i / (steps - 1)))
        log_info "Ramp step $((i + 1))/$steps: $current_rate events/sec"
        
        run_steady_rate_test "${scenario}_step_$((i + 1))" "$current_rate" "$step_duration"
        
        sleep 2 # Brief pause between steps
    done
}

analyze_test_results() {
    local scenario="$1"
    local duration="$2"
    
    log_info "Analyzing results for $scenario..."
    
    # Count events in Kafka logs
    local user_events_count=0
    local admin_events_count=0
    
    if [ -f "user_events.log" ]; then
        user_events_count=$(wc -l < "user_events.log")
    fi
    
    if [ -f "admin_events.log" ]; then
        admin_events_count=$(wc -l < "admin_events.log")
    fi
    
    # Calculate metrics
    local total_events=$((user_events_count + admin_events_count))
    local events_per_second=$(echo "scale=2; $total_events / $duration" | bc -l)
    
    # Create results summary
    cat > "summary_${scenario}.json" << EOF
{
    "scenario": "$scenario",
    "duration_seconds": $duration,
    "total_events": $total_events,
    "user_events": $user_events_count,
    "admin_events": $admin_events_count,
    "events_per_second": $events_per_second,
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
    
    log_success "Analysis complete for $scenario: $total_events events ($events_per_second events/sec)"
}

generate_report() {
    log_info "Generating performance report..."
    
    cat > "performance_report.md" << 'EOF'
# Keycloak Kafka Event Listener - Performance Test Report

## Test Summary

**Test Date:** $(date)
**Test Duration:** $(cat summary_*.json | jq -s 'map(.duration_seconds) | add') seconds total
**Test Environment:**
- Keycloak URL: $(echo $KEYCLOAK_URL)
- Kafka Bootstrap Servers: $(echo $KAFKA_BOOTSTRAP_SERVERS)

## Test Scenarios

EOF
    
    # Add scenario results
    for result_file in summary_*.json; do
        if [ -f "$result_file" ]; then
            local scenario=$(jq -r '.scenario' "$result_file")
            local total_events=$(jq -r '.total_events' "$result_file")
            local events_per_second=$(jq -r '.events_per_second' "$result_file")
            local duration=$(jq -r '.duration_seconds' "$result_file")
            
            cat >> "performance_report.md" << EOF

### $scenario

- **Duration:** ${duration}s
- **Total Events:** $total_events
- **Events/Second:** $events_per_second
- **User Events:** $(jq -r '.user_events' "$result_file")
- **Admin Events:** $(jq -r '.admin_events' "$result_file")

EOF
        fi
    done
    
    # Add metrics analysis if available
    if [ -f "metrics.json" ]; then
        cat >> "performance_report.md" << 'EOF'

## Metrics Analysis

### Event Processing Performance
- See metrics.json for detailed Prometheus metrics

### System Resource Usage
- Memory usage patterns
- GC performance
- Connection pool statistics

EOF
    fi
    
    cat >> "performance_report.md" << 'EOF'

## Recommendations

Based on the test results:

1. **Throughput Optimization:** 
2. **Latency Optimization:**
3. **Resource Usage:**
4. **Scaling Considerations:**

## Files Generated

- `performance_report.md` - This report
- `summary_*.json` - Individual test summaries
- `metrics.json` - Prometheus metrics (if available)
- `user_events.log` - Kafka user events log
- `admin_events.log` - Kafka admin events log

EOF
    
    log_success "Performance report generated: performance_report.md"
}

cleanup() {
    log_info "Cleaning up test environment..."
    
    # Kill background processes
    if [ -n "${KAFKA_CONSUMER_PID:-}" ]; then
        kill "$KAFKA_CONSUMER_PID" 2>/dev/null || true
    fi
    
    if [ -n "${KAFKA_ADMIN_CONSUMER_PID:-}" ]; then
        kill "$KAFKA_ADMIN_CONSUMER_PID" 2>/dev/null || true
    fi
    
    # Remove test scripts
    rm -f auth_test_*.sh
    
    log_success "Cleanup completed"
}

# Main execution
main() {
    log_info "Starting Keycloak Kafka Event Listener Performance Benchmark"
    log_info "Results will be saved to: $RESULTS_DIR"
    
    # Set trap for cleanup
    trap cleanup EXIT
    
    check_prerequisites
    setup_test_environment
    
    # Run all test scenarios
    for scenario_config in "${SCENARIOS[@]}"; do
        IFS=':' read -r scenario rate duration <<< "$scenario_config"
        run_performance_test "$scenario" "$rate" "$duration"
        
        # Brief pause between tests
        sleep 10
    done
    
    generate_report
    
    log_success "Performance benchmark completed successfully!"
    log_info "Results available in: $RESULTS_DIR"
}

# Check if script is being sourced or executed
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi