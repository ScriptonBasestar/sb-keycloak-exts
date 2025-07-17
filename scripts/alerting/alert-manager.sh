#!/bin/bash

# Keycloak Kafka Event Listener - Alert Manager Script
# Manages alerting configuration, testing, and troubleshooting

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
CONFIG_DIR="$PROJECT_ROOT/config/alerting"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
METRICS_URL="${METRICS_URL:-http://localhost:9090}"

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

# Display usage information
usage() {
    cat << EOF
Keycloak Kafka Event Listener Alert Manager

Usage: $0 [COMMAND] [OPTIONS]

Commands:
    status          Show current alert status and statistics
    test            Test alert configuration and channels
    rules           Manage alert rules
    suppress        Suppress alerts temporarily
    incidents       View and manage incidents
    channels        Test notification channels
    validate        Validate alert configuration
    metrics         Show alert-related metrics
    history         View alert history
    help            Show this help message

Options:
    --config FILE   Use custom configuration file
    --env ENV       Environment (development, staging, production)
    --verbose       Enable verbose output
    --dry-run       Show what would be done without executing

Examples:
    $0 status
    $0 test --channel slack
    $0 suppress --rule "HighErrorRate" --duration 30m
    $0 incidents --active
    $0 validate --config custom-alerts.yaml

EOF
}

# Check if required tools are available
check_prerequisites() {
    local required_tools=("curl" "jq" "yq")
    
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "Required tool not found: $tool"
            exit 1
        fi
    done
    
    # Check if Keycloak is accessible
    if ! curl -sf "$KEYCLOAK_URL/health" > /dev/null; then
        log_error "Keycloak not accessible at $KEYCLOAK_URL"
        exit 1
    fi
    
    # Check if metrics endpoint is accessible
    if ! curl -sf "$METRICS_URL/metrics" > /dev/null; then
        log_error "Metrics endpoint not accessible at $METRICS_URL"
        exit 1
    fi
}

# Show current alert status
show_status() {
    log_info "Checking alert system status..."
    
    # Check alert manager status
    local alert_status
    if alert_status=$(curl -s "$METRICS_URL/api/v1/query?query=keycloak_kafka_alert_manager_status" | jq -r '.data.result[0].value[1]' 2>/dev/null); then
        if [ "$alert_status" = "1" ]; then
            log_success "Alert Manager: RUNNING"
        else
            log_error "Alert Manager: STOPPED"
        fi
    else
        log_warning "Alert Manager status unknown"
    fi
    
    # Show active alerts
    echo
    log_info "Active Alerts:"
    if ! curl -s "$METRICS_URL/api/v1/query?query=keycloak_kafka_active_alerts" | jq -r '.data.result[] | "\(.metric.alertname): \(.value[1])"' 2>/dev/null; then
        echo "No active alerts found"
    fi
    
    # Show alert statistics
    echo
    log_info "Alert Statistics:"
    local total_alerts
    total_alerts=$(curl -s "$METRICS_URL/api/v1/query?query=keycloak_kafka_alerts_total" | jq -r '.data.result[0].value[1]' 2>/dev/null || echo "0")
    echo "Total alerts generated: $total_alerts"
    
    local notifications_sent
    notifications_sent=$(curl -s "$METRICS_URL/api/v1/query?query=keycloak_kafka_notifications_sent_total" | jq -r '.data.result[0].value[1]' 2>/dev/null || echo "0")
    echo "Total notifications sent: $notifications_sent"
    
    local suppressed_alerts
    suppressed_alerts=$(curl -s "$METRICS_URL/api/v1/query?query=keycloak_kafka_alerts_suppressed_total" | jq -r '.data.result[0].value[1]' 2>/dev/null || echo "0")
    echo "Suppressed alerts: $suppressed_alerts"
}

# Test alert configuration
test_alerts() {
    local channel="${1:-all}"
    
    log_info "Testing alert configuration..."
    
    # Validate configuration file
    if ! validate_config "$CONFIG_DIR/alert-rules.yaml"; then
        log_error "Configuration validation failed"
        return 1
    fi
    
    log_success "Configuration validation passed"
    
    # Test notification channels
    case "$channel" in
        "all")
            test_email_channel
            test_slack_channel
            test_webhook_channel
            test_pagerduty_channel
            ;;
        "email")
            test_email_channel
            ;;
        "slack")
            test_slack_channel
            ;;
        "webhook")
            test_webhook_channel
            ;;
        "pagerduty")
            test_pagerduty_channel
            ;;
        *)
            log_error "Unknown channel: $channel"
            return 1
            ;;
    esac
}

# Test email notification channel
test_email_channel() {
    log_info "Testing email notification channel..."
    
    # Create test alert payload
    local test_payload
    test_payload=$(cat << 'EOF'
{
    "alert": {
        "id": "test-alert-001",
        "rule": {
            "name": "TestAlert",
            "description": "Test alert for email notification",
            "severity": "INFO",
            "metric": "TEST"
        },
        "currentValue": 42.0,
        "threshold": 40.0,
        "triggeredAt": "2024-01-01T12:00:00Z",
        "status": "FIRING"
    },
    "type": "TRIGGERED"
}
EOF
    )
    
    # Send test notification
    if curl -s -X POST "$METRICS_URL/admin/alerts/test/email" \
        -H "Content-Type: application/json" \
        -d "$test_payload" | grep -q "success"; then
        log_success "Email channel test passed"
    else
        log_error "Email channel test failed"
    fi
}

# Test Slack notification channel
test_slack_channel() {
    log_info "Testing Slack notification channel..."
    
    local webhook_url
    webhook_url=$(yq eval '.notification_channels.slack.webhook_url' "$CONFIG_DIR/alert-rules.yaml")
    
    if [ "$webhook_url" = "null" ] || [ -z "$webhook_url" ]; then
        log_warning "Slack webhook URL not configured"
        return 0
    fi
    
    # Test Slack webhook
    local test_message='{"text": "Test message from Keycloak Kafka Alert Manager"}'
    
    if curl -s -X POST "$webhook_url" \
        -H "Content-Type: application/json" \
        -d "$test_message" | grep -q "ok"; then
        log_success "Slack channel test passed"
    else
        log_error "Slack channel test failed"
    fi
}

# Test webhook notification channel
test_webhook_channel() {
    log_info "Testing webhook notification channel..."
    
    local webhook_url
    webhook_url=$(yq eval '.notification_channels.webhook.url' "$CONFIG_DIR/alert-rules.yaml")
    
    if [ "$webhook_url" = "null" ] || [ -z "$webhook_url" ]; then
        log_warning "Webhook URL not configured"
        return 0
    fi
    
    # Create test webhook payload
    local test_payload
    test_payload=$(cat << 'EOF'
{
    "version": "1.0",
    "timestamp": 1640995200000,
    "event": {
        "type": "alert.test",
        "source": "keycloak-kafka-event-listener",
        "environment": "test"
    },
    "alert": {
        "id": "test-webhook-001",
        "name": "WebhookTest",
        "description": "Test webhook notification",
        "severity": "INFO",
        "status": "FIRING"
    }
}
EOF
    )
    
    if curl -s -X POST "$webhook_url" \
        -H "Content-Type: application/json" \
        -d "$test_payload" \
        --connect-timeout 10 \
        --max-time 30; then
        log_success "Webhook channel test passed"
    else
        log_error "Webhook channel test failed"
    fi
}

# Test PagerDuty notification channel
test_pagerduty_channel() {
    log_info "Testing PagerDuty notification channel..."
    
    local integration_key
    integration_key=$(yq eval '.notification_channels.pagerduty.integration_key' "$CONFIG_DIR/alert-rules.yaml")
    
    if [ "$integration_key" = "null" ] || [ -z "$integration_key" ]; then
        log_warning "PagerDuty integration key not configured"
        return 0
    fi
    
    # Create test PagerDuty event
    local test_event
    test_event=$(cat << EOF
{
    "routing_key": "$integration_key",
    "event_action": "trigger",
    "dedup_key": "test-alert-$(date +%s)",
    "payload": {
        "summary": "Test alert from Keycloak Kafka Alert Manager",
        "source": "keycloak-kafka-event-listener",
        "severity": "info",
        "component": "test",
        "custom_details": {
            "description": "This is a test alert to verify PagerDuty integration"
        }
    },
    "client": "Keycloak Kafka Alert Manager",
    "client_url": "https://example.com"
}
EOF
    )
    
    if curl -s -X POST "https://events.pagerduty.com/v2/enqueue" \
        -H "Content-Type: application/json" \
        -d "$test_event" | grep -q "success"; then
        log_success "PagerDuty channel test passed"
    else
        log_error "PagerDuty channel test failed"
    fi
}

# Validate alert configuration
validate_config() {
    local config_file="${1:-$CONFIG_DIR/alert-rules.yaml}"
    
    log_info "Validating configuration file: $config_file"
    
    if [ ! -f "$config_file" ]; then
        log_error "Configuration file not found: $config_file"
        return 1
    fi
    
    # Check YAML syntax
    if ! yq eval '.' "$config_file" > /dev/null 2>&1; then
        log_error "Invalid YAML syntax in configuration file"
        return 1
    fi
    
    # Validate required sections
    local required_sections=("alerting" "notification_channels" "alert_rules")
    
    for section in "${required_sections[@]}"; do
        if ! yq eval ".$section" "$config_file" > /dev/null 2>&1; then
            log_error "Missing required section: $section"
            return 1
        fi
    done
    
    # Validate alert rules
    local rule_count
    rule_count=$(yq eval '.alert_rules | length' "$config_file")
    
    if [ "$rule_count" -eq 0 ]; then
        log_warning "No alert rules defined"
    else
        log_info "Found $rule_count alert rules"
        
        # Check each rule for required fields
        for i in $(seq 0 $((rule_count - 1))); do
            local rule_name
            rule_name=$(yq eval ".alert_rules[$i].name" "$config_file")
            
            local required_fields=("name" "metric" "condition" "threshold" "severity")
            for field in "${required_fields[@]}"; do
                if [ "$(yq eval ".alert_rules[$i].$field" "$config_file")" = "null" ]; then
                    log_error "Rule '$rule_name' missing required field: $field"
                    return 1
                fi
            done
        done
    fi
    
    log_success "Configuration validation completed successfully"
    return 0
}

# Suppress alerts
suppress_alerts() {
    local rule_name="$1"
    local duration="$2"
    
    log_info "Suppressing alert rule: $rule_name for $duration"
    
    # Convert duration to minutes
    local duration_minutes
    case "$duration" in
        *m) duration_minutes="${duration%m}" ;;
        *h) duration_minutes=$((${duration%h} * 60)) ;;
        *d) duration_minutes=$((${duration%d} * 1440)) ;;
        *) 
            log_error "Invalid duration format. Use 30m, 2h, or 1d"
            return 1
            ;;
    esac
    
    # Send suppression request
    if curl -s -X POST "$METRICS_URL/admin/alerts/suppress" \
        -H "Content-Type: application/json" \
        -d "{\"ruleName\": \"$rule_name\", \"durationMinutes\": $duration_minutes}" | grep -q "success"; then
        log_success "Alert rule '$rule_name' suppressed for $duration"
    else
        log_error "Failed to suppress alert rule '$rule_name'"
        return 1
    fi
}

# Show incidents
show_incidents() {
    local filter="${1:-all}"
    
    log_info "Showing incidents (filter: $filter)..."
    
    case "$filter" in
        "active")
            curl -s "$METRICS_URL/admin/incidents/active" | jq -r '.[] | "ID: \(.id), Status: \(.status), Severity: \(.severity), Title: \(.title)"'
            ;;
        "all")
            curl -s "$METRICS_URL/admin/incidents" | jq -r '.[] | "ID: \(.id), Status: \(.status), Severity: \(.severity), Title: \(.title)"'
            ;;
        *)
            # Show specific incident
            curl -s "$METRICS_URL/admin/incidents/$filter" | jq '.'
            ;;
    esac
}

# Show alert metrics
show_metrics() {
    log_info "Alert-related metrics:"
    echo
    
    # Alert manager metrics
    echo "Alert Manager Metrics:"
    curl -s "$METRICS_URL/metrics" | grep "keycloak_kafka_alert" | head -20
    
    echo
    echo "Incident Response Metrics:"
    curl -s "$METRICS_URL/metrics" | grep "keycloak_kafka_incident" | head -10
    
    echo
    echo "Notification Metrics:"
    curl -s "$METRICS_URL/metrics" | grep "keycloak_kafka_notification" | head -10
}

# Show alert history
show_history() {
    local hours="${1:-24}"
    
    log_info "Alert history for the last $hours hours:"
    
    # Calculate timestamp for query
    local start_time
    start_time=$(date -d "$hours hours ago" -u +%Y-%m-%dT%H:%M:%SZ)
    
    # Query alert history
    curl -s "$METRICS_URL/api/v1/query_range" \
        -G \
        --data-urlencode "query=keycloak_kafka_alerts_total" \
        --data-urlencode "start=$start_time" \
        --data-urlencode "end=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --data-urlencode "step=1h" | \
        jq -r '.data.result[0].values[] | "\(.[0] | strftime("%Y-%m-%d %H:%M:%S")): \(.[1]) alerts"'
}

# Restart alert manager
restart_alert_manager() {
    log_info "Restarting alert manager..."
    
    if curl -s -X POST "$METRICS_URL/admin/alerts/restart" | grep -q "success"; then
        log_success "Alert manager restarted successfully"
    else
        log_error "Failed to restart alert manager"
        return 1
    fi
}

# Reload configuration
reload_config() {
    log_info "Reloading alert configuration..."
    
    if curl -s -X POST "$METRICS_URL/admin/alerts/reload" | grep -q "success"; then
        log_success "Configuration reloaded successfully"
    else
        log_error "Failed to reload configuration"
        return 1
    fi
}

# Main function
main() {
    local command="${1:-help}"
    local dry_run=false
    local verbose=false
    local config_file="$CONFIG_DIR/alert-rules.yaml"
    local environment="production"
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --config)
                config_file="$2"
                shift 2
                ;;
            --env)
                environment="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --verbose)
                verbose=true
                shift
                ;;
            --help)
                usage
                exit 0
                ;;
            *)
                if [ -z "${command_set:-}" ]; then
                    command="$1"
                    command_set=true
                fi
                shift
                ;;
        esac
    done
    
    # Set environment variables
    export ENVIRONMENT="$environment"
    export DRY_RUN="$dry_run"
    export VERBOSE="$verbose"
    
    # Check prerequisites
    check_prerequisites
    
    # Execute command
    case "$command" in
        "status")
            show_status
            ;;
        "test")
            local channel="${2:-all}"
            test_alerts "$channel"
            ;;
        "rules")
            local action="${2:-list}"
            case "$action" in
                "list")
                    yq eval '.alert_rules[] | .name' "$config_file"
                    ;;
                "validate")
                    validate_config "$config_file"
                    ;;
                *)
                    log_error "Unknown rules action: $action"
                    exit 1
                    ;;
            esac
            ;;
        "suppress")
            local rule_name="${2:-}"
            local duration="${3:-1h}"
            if [ -z "$rule_name" ]; then
                log_error "Rule name required for suppression"
                exit 1
            fi
            suppress_alerts "$rule_name" "$duration"
            ;;
        "incidents")
            local filter="${2:-all}"
            show_incidents "$filter"
            ;;
        "channels")
            local channel="${2:-all}"
            test_alerts "$channel"
            ;;
        "validate")
            validate_config "$config_file"
            ;;
        "metrics")
            show_metrics
            ;;
        "history")
            local hours="${2:-24}"
            show_history "$hours"
            ;;
        "restart")
            restart_alert_manager
            ;;
        "reload")
            reload_config
            ;;
        "help")
            usage
            ;;
        *)
            log_error "Unknown command: $command"
            usage
            exit 1
            ;;
    esac
}

# Check if script is being sourced or executed
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi