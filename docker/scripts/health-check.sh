#!/bin/bash

# Health Check Script for Keycloak with Kafka Event Listener
# Validates Keycloak and Kafka connectivity

set -euo pipefail

# Configuration
KEYCLOAK_HOST="${KEYCLOAK_HOST:-localhost}"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-8080}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
HEALTH_CHECK_TIMEOUT="${HEALTH_CHECK_TIMEOUT:-10}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "[HEALTH] $1"
}

log_success() {
    echo -e "${GREEN}[HEALTH]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[HEALTH]${NC} $1"
}

log_error() {
    echo -e "${RED}[HEALTH]${NC} $1"
}

# Function to check Keycloak health endpoint
check_keycloak_health() {
    local url="http://${KEYCLOAK_HOST}:${KEYCLOAK_PORT}/health"
    
    log_info "Checking Keycloak health endpoint: $url"
    
    if curl -f -s --max-time "$HEALTH_CHECK_TIMEOUT" "$url" > /dev/null 2>&1; then
        log_success "Keycloak health check passed"
        return 0
    else
        log_error "Keycloak health check failed"
        return 1
    fi
}

# Function to check Keycloak readiness
check_keycloak_readiness() {
    local url="http://${KEYCLOAK_HOST}:${KEYCLOAK_PORT}/health/ready"
    
    log_info "Checking Keycloak readiness endpoint: $url"
    
    if curl -f -s --max-time "$HEALTH_CHECK_TIMEOUT" "$url" > /dev/null 2>&1; then
        log_success "Keycloak readiness check passed"
        return 0
    else
        log_error "Keycloak readiness check failed"
        return 1
    fi
}

# Function to check Keycloak admin console
check_keycloak_admin() {
    local url="http://${KEYCLOAK_HOST}:${KEYCLOAK_PORT}/admin/"
    
    log_info "Checking Keycloak admin console: $url"
    
    # Check if admin console is accessible (should return 200 or redirect)
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$HEALTH_CHECK_TIMEOUT" "$url" 2>/dev/null || echo "000")
    
    if [[ "$http_code" == "200" ]] || [[ "$http_code" == "3"* ]]; then
        log_success "Keycloak admin console is accessible (HTTP $http_code)"
        return 0
    else
        log_error "Keycloak admin console check failed (HTTP $http_code)"
        return 1
    fi
}

# Function to check Kafka connectivity
check_kafka_connectivity() {
    local bootstrap_servers="$KAFKA_BOOTSTRAP_SERVERS"
    local kafka_host="${bootstrap_servers%%:*}"
    local kafka_port="${bootstrap_servers##*:}"
    
    log_info "Checking Kafka connectivity: $kafka_host:$kafka_port"
    
    if timeout "$HEALTH_CHECK_TIMEOUT" bash -c "</dev/tcp/$kafka_host/$kafka_port" 2>/dev/null; then
        log_success "Kafka connectivity check passed"
        return 0
    else
        log_warning "Kafka connectivity check failed (this may be expected in some environments)"
        return 1
    fi
}

# Function to check Java process
check_java_process() {
    log_info "Checking Java process..."
    
    if pgrep -f "keycloak" > /dev/null; then
        local java_pids=$(pgrep -f "keycloak")
        log_success "Keycloak Java process is running (PIDs: $java_pids)"
        return 0
    else
        log_error "Keycloak Java process not found"
        return 1
    fi
}

# Function to check memory usage
check_memory_usage() {
    log_info "Checking memory usage..."
    
    local memory_info
    if command -v free >/dev/null 2>&1; then
        memory_info=$(free -h | grep "^Mem:")
        log_info "Memory usage: $memory_info"
    elif [[ -f /proc/meminfo ]]; then
        local mem_total=$(grep MemTotal /proc/meminfo | awk '{print $2}')
        local mem_available=$(grep MemAvailable /proc/meminfo | awk '{print $2}')
        local mem_used=$((mem_total - mem_available))
        local mem_usage_percent=$((mem_used * 100 / mem_total))
        
        log_info "Memory usage: ${mem_usage_percent}% (${mem_used}KB/${mem_total}KB)"
        
        # Warning if memory usage is very high
        if [[ $mem_usage_percent -gt 90 ]]; then
            log_warning "High memory usage detected: ${mem_usage_percent}%"
        fi
    else
        log_warning "Unable to check memory usage"
    fi
}

# Function to check disk space
check_disk_space() {
    log_info "Checking disk space..."
    
    if command -v df >/dev/null 2>&1; then
        local disk_usage=$(df -h /opt/keycloak 2>/dev/null | tail -1)
        log_info "Disk usage: $disk_usage"
        
        # Extract usage percentage
        local usage_percent=$(echo "$disk_usage" | awk '{print $5}' | sed 's/%//')
        if [[ -n "$usage_percent" ]] && [[ "$usage_percent" -gt 85 ]]; then
            log_warning "High disk usage detected: ${usage_percent}%"
        fi
    else
        log_warning "Unable to check disk space"
    fi
}

# Function to check plugin installation
check_plugin_installation() {
    log_info "Checking Kafka Event Listener plugin..."
    
    local plugin_dir="/opt/keycloak/providers"
    local plugin_pattern="keycloak-kafka-event-listener*.jar"
    
    if ls $plugin_dir/$plugin_pattern 1> /dev/null 2>&1; then
        local plugin_files=$(ls $plugin_dir/$plugin_pattern)
        log_success "Kafka Event Listener plugin found: $plugin_files"
        return 0
    else
        log_error "Kafka Event Listener plugin not found in $plugin_dir"
        return 1
    fi
}

# Main health check logic
main() {
    local exit_code=0
    local checks_passed=0
    local checks_total=0
    
    log_info "=== Keycloak Health Check ==="
    log_info "Timestamp: $(date)"
    log_info "Health check timeout: ${HEALTH_CHECK_TIMEOUT}s"
    log_info "============================"
    
    # Critical checks (must pass)
    critical_checks=(
        "check_java_process"
        "check_plugin_installation"
        "check_keycloak_health"
    )
    
    # Optional checks (warnings only)
    optional_checks=(
        "check_keycloak_readiness"
        "check_keycloak_admin"
        "check_kafka_connectivity"
        "check_memory_usage"
        "check_disk_space"
    )
    
    # Run critical checks
    log_info "Running critical health checks..."
    for check in "${critical_checks[@]}"; do
        ((checks_total++))
        if $check; then
            ((checks_passed++))
        else
            exit_code=1
        fi
        echo
    done
    
    # Run optional checks
    log_info "Running optional health checks..."
    for check in "${optional_checks[@]}"; do
        ((checks_total++))
        if $check; then
            ((checks_passed++))
        fi
        echo
    done
    
    # Summary
    log_info "=== Health Check Summary ==="
    log_info "Checks passed: $checks_passed/$checks_total"
    
    if [[ $exit_code -eq 0 ]]; then
        log_success "Overall health check: PASSED"
    else
        log_error "Overall health check: FAILED"
    fi
    
    log_info "============================"
    
    exit $exit_code
}

# Handle different health check modes
case "${1:-full}" in
    "quick")
        log_info "Running quick health check..."
        check_java_process && check_keycloak_health
        ;;
    "kafka")
        log_info "Running Kafka connectivity check..."
        check_kafka_connectivity
        ;;
    "plugin")
        log_info "Running plugin installation check..."
        check_plugin_installation
        ;;
    "full"|"")
        main
        ;;
    *)
        echo "Usage: $0 [quick|kafka|plugin|full]"
        echo "  quick  - Check Java process and Keycloak health endpoint only"
        echo "  kafka  - Check Kafka connectivity only"
        echo "  plugin - Check plugin installation only"
        echo "  full   - Run all health checks (default)"
        exit 1
        ;;
esac