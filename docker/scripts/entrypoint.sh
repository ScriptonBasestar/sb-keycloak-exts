#!/bin/bash

# Keycloak with Kafka Event Listener - Entrypoint Script
# Processes configuration templates and starts Keycloak

set -euo pipefail

# Colors for logging
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[ENTRYPOINT]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[ENTRYPOINT]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[ENTRYPOINT]${NC} $1"
}

log_error() {
    echo -e "${RED}[ENTRYPOINT]${NC} $1"
}

# Function to substitute environment variables in template files
process_template() {
    local template_file="$1"
    local output_file="$2"
    
    if [[ -f "$template_file" ]]; then
        log_info "Processing template: $template_file -> $output_file"
        envsubst < "$template_file" > "$output_file"
        log_success "Template processed successfully"
    else
        log_warning "Template file not found: $template_file"
    fi
}

# Function to validate Kafka connectivity
validate_kafka_connection() {
    local bootstrap_servers="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
    local max_attempts=30
    local attempt=1
    
    log_info "Validating Kafka connectivity to: $bootstrap_servers"
    
    while [[ $attempt -le $max_attempts ]]; do
        if timeout 5 bash -c "</dev/tcp/${bootstrap_servers%%:*}/${bootstrap_servers##*:}" 2>/dev/null; then
            log_success "Kafka connection validated successfully"
            return 0
        fi
        
        log_warning "Kafka connection attempt $attempt/$max_attempts failed, retrying in 5 seconds..."
        sleep 5
        ((attempt++))
    done
    
    log_error "Failed to connect to Kafka after $max_attempts attempts"
    if [[ "${KAFKA_REQUIRED:-true}" == "true" ]]; then
        exit 1
    else
        log_warning "Kafka connection validation disabled, continuing anyway"
    fi
}

# Function to create Kafka topics if needed
create_kafka_topics() {
    local bootstrap_servers="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
    local user_topic="${KAFKA_USER_EVENTS_TOPIC:-keycloak.user.events}"
    local admin_topic="${KAFKA_ADMIN_EVENTS_TOPIC:-keycloak.admin.events}"
    
    if [[ "${KAFKA_AUTO_CREATE_TOPICS:-false}" == "true" ]]; then
        log_info "Auto-creating Kafka topics..."
        
        # This would require kafka-topics.sh to be available
        # For now, we'll just log the intention
        log_info "Would create topics: $user_topic, $admin_topic"
        log_warning "Topic auto-creation requires Kafka admin tools (not implemented in this image)"
    fi
}

# Function to wait for database connectivity
wait_for_database() {
    local db_type="${KC_DB:-dev-file}"
    
    if [[ "$db_type" != "dev-file" ]] && [[ -n "${KC_DB_URL_HOST:-}" ]]; then
        local db_host="${KC_DB_URL_HOST}"
        local db_port="${KC_DB_URL_PORT:-5432}"
        local max_attempts=30
        local attempt=1
        
        log_info "Waiting for database connectivity to: $db_host:$db_port"
        
        while [[ $attempt -le $max_attempts ]]; do
            if timeout 5 bash -c "</dev/tcp/$db_host/$db_port" 2>/dev/null; then
                log_success "Database connection validated successfully"
                return 0
            fi
            
            log_warning "Database connection attempt $attempt/$max_attempts failed, retrying in 5 seconds..."
            sleep 5
            ((attempt++))
        done
        
        log_error "Failed to connect to database after $max_attempts attempts"
        exit 1
    else
        log_info "Using dev-file database, skipping database connectivity check"
    fi
}

# Function to verify plugin installation
verify_plugin_installation() {
    local plugin_dir="/opt/keycloak/providers"
    local plugin_pattern="keycloak-kafka-event-listener*.jar"
    
    log_info "Verifying Kafka Event Listener plugin installation..."
    
    if ls $plugin_dir/$plugin_pattern 1> /dev/null 2>&1; then
        local plugin_count=$(ls $plugin_dir/$plugin_pattern | wc -l)
        log_success "Found $plugin_count Kafka Event Listener plugin(s)"
        
        # List the plugins for verification
        ls -la $plugin_dir/$plugin_pattern | while read -r line; do
            log_info "Plugin: $line"
        done
    else
        log_error "Kafka Event Listener plugin not found in $plugin_dir"
        log_error "Expected pattern: $plugin_pattern"
        exit 1
    fi
}

# Function to set JVM options
configure_jvm() {
    local jvm_opts="${JVM_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200}"
    
    log_info "Configuring JVM options: $jvm_opts"
    export JAVA_OPTS="$jvm_opts $JAVA_OPTS"
    
    # Additional JVM options for Kafka integration
    export JAVA_OPTS="$JAVA_OPTS -Djava.net.preferIPv4Stack=true"
    export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote=true"
    export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.port=9010"
    export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
    export JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote.ssl=false"
    
    log_success "JVM configured successfully"
}

# Function to setup monitoring
setup_monitoring() {
    if [[ "${KC_METRICS_ENABLED:-false}" == "true" ]]; then
        log_info "Metrics enabled - Prometheus metrics will be available at /metrics"
    fi
    
    if [[ "${KC_HEALTH_ENABLED:-false}" == "true" ]]; then
        log_info "Health checks enabled - Health endpoint will be available at /health"
    fi
}

# Main entrypoint logic
main() {
    log_info "Starting Keycloak with Kafka Event Listener..."
    log_info "Version: 0.0.2-SNAPSHOT"
    log_info "Keycloak Admin: ${KEYCLOAK_ADMIN:-admin}"
    
    # Configure JVM
    configure_jvm
    
    # Process configuration templates
    process_template "/opt/keycloak/conf/keycloak.conf.template" "/opt/keycloak/conf/keycloak.conf"
    process_template "/opt/keycloak/conf/kafka/kafka-config.properties.template" "/opt/keycloak/conf/kafka/kafka-config.properties"
    
    # Verify plugin installation
    verify_plugin_installation
    
    # Setup monitoring
    setup_monitoring
    
    # Wait for dependencies if needed
    if [[ "${WAIT_FOR_DEPENDENCIES:-true}" == "true" ]]; then
        wait_for_database
        
        if [[ "${KAFKA_ENABLED:-true}" == "true" ]]; then
            validate_kafka_connection
            create_kafka_topics
        fi
    fi
    
    # Build Keycloak if needed
    if [[ "${KC_BUILD:-true}" == "true" ]]; then
        log_info "Building Keycloak with custom providers..."
        /opt/keycloak/bin/kc.sh build
        log_success "Keycloak build completed"
    fi
    
    # Log final configuration summary
    log_info "=== Configuration Summary ==="
    log_info "Kafka Bootstrap Servers: ${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
    log_info "User Events Topic: ${KAFKA_USER_EVENTS_TOPIC:-keycloak.user.events}"
    log_info "Admin Events Topic: ${KAFKA_ADMIN_EVENTS_TOPIC:-keycloak.admin.events}"
    log_info "User Events Enabled: ${KAFKA_USER_EVENTS_ENABLED:-true}"
    log_info "Admin Events Enabled: ${KAFKA_ADMIN_EVENTS_ENABLED:-true}"
    log_info "Health Checks: ${KC_HEALTH_ENABLED:-false}"
    log_info "Metrics: ${KC_METRICS_ENABLED:-false}"
    log_info "============================="
    
    # Start Keycloak
    log_success "Starting Keycloak server..."
    exec /opt/keycloak/bin/kc.sh "$@"
}

# Handle different commands
case "${1:-start}" in
    "build")
        log_info "Building Keycloak..."
        exec /opt/keycloak/bin/kc.sh build
        ;;
    "start")
        main "$@"
        ;;
    "start-dev")
        log_info "Starting Keycloak in development mode..."
        export KC_DB=dev-file
        export KC_HTTP_ENABLED=true
        export KAFKA_REQUIRED=false
        main "$@"
        ;;
    *)
        log_info "Executing custom command: $*"
        exec "$@"
        ;;
esac