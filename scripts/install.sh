#!/bin/bash

# Keycloak Kafka Event Listener - Installation Script
# Version: 0.0.2-SNAPSHOT
# Author: ScriptonBaseStar

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PLUGIN_NAME="keycloak-kafka-event-listener"
PLUGIN_VERSION="0.0.2-SNAPSHOT"
PLUGIN_JAR="${PLUGIN_NAME}-${PLUGIN_VERSION}.jar"
GITHUB_REPO="scriptonbasestar/sb-keycloak-exts"

# Functions
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

check_requirements() {
    log_info "Checking system requirements..."
    
    # Check if running as appropriate user
    if [[ $EUID -eq 0 ]]; then
        log_warning "Running as root. Consider using keycloak user."
    fi
    
    # Check Java version
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1-2)
        log_info "Java version: $JAVA_VERSION"
        
        if [[ $(echo "$JAVA_VERSION >= 17" | bc -l) -eq 0 ]]; then
            log_error "Java 17+ is required. Current version: $JAVA_VERSION"
            exit 1
        fi
    else
        log_error "Java not found. Please install Java 17+."
        exit 1
    fi
    
    # Check Keycloak installation
    if [[ -z "${KEYCLOAK_HOME:-}" ]]; then
        log_error "KEYCLOAK_HOME environment variable not set."
        echo "Please set KEYCLOAK_HOME to your Keycloak installation directory."
        exit 1
    fi
    
    if [[ ! -d "$KEYCLOAK_HOME" ]]; then
        log_error "Keycloak directory not found: $KEYCLOAK_HOME"
        exit 1
    fi
    
    if [[ ! -d "$KEYCLOAK_HOME/providers" ]]; then
        log_error "Keycloak providers directory not found: $KEYCLOAK_HOME/providers"
        exit 1
    fi
    
    log_success "System requirements check passed"
}

backup_existing() {
    log_info "Checking for existing installation..."
    
    local existing_jars=$(find "$KEYCLOAK_HOME/providers" -name "${PLUGIN_NAME}*.jar" 2>/dev/null || true)
    
    if [[ -n "$existing_jars" ]]; then
        local backup_dir="$KEYCLOAK_HOME/providers/backup/$(date +%Y%m%d_%H%M%S)"
        mkdir -p "$backup_dir"
        
        echo "$existing_jars" | while read -r jar; do
            if [[ -f "$jar" ]]; then
                log_info "Backing up existing plugin: $(basename "$jar")"
                mv "$jar" "$backup_dir/"
            fi
        done
        
        log_success "Existing plugins backed up to: $backup_dir"
    else
        log_info "No existing installation found"
    fi
}

download_plugin() {
    log_info "Downloading plugin..."
    
    local temp_dir=$(mktemp -d)
    local download_url=""
    local jar_file=""
    
    # Check if local JAR file exists
    if [[ -f "./$PLUGIN_JAR" ]]; then
        log_info "Using local JAR file: ./$PLUGIN_JAR"
        jar_file="./$PLUGIN_JAR"
    elif [[ -f "./events/event-listener-kafka/build/libs/$PLUGIN_JAR" ]]; then
        log_info "Using built JAR file: ./events/event-listener-kafka/build/libs/$PLUGIN_JAR"
        jar_file="./events/event-listener-kafka/build/libs/$PLUGIN_JAR"
    else
        # Try to download from GitHub releases
        download_url="https://github.com/${GITHUB_REPO}/releases/download/v${PLUGIN_VERSION}/${PLUGIN_JAR}"
        jar_file="$temp_dir/$PLUGIN_JAR"
        
        log_info "Downloading from: $download_url"
        
        if command -v curl &> /dev/null; then
            curl -L -o "$jar_file" "$download_url" || {
                log_error "Failed to download plugin from GitHub releases"
                log_info "Please build the plugin locally using: ./gradlew :events:event-listener-kafka:build"
                exit 1
            }
        elif command -v wget &> /dev/null; then
            wget -O "$jar_file" "$download_url" || {
                log_error "Failed to download plugin from GitHub releases"
                log_info "Please build the plugin locally using: ./gradlew :events:event-listener-kafka:build"
                exit 1
            }
        else
            log_error "Neither curl nor wget found. Cannot download plugin."
            exit 1
        fi
    fi
    
    # Verify JAR file
    if [[ ! -f "$jar_file" ]]; then
        log_error "Plugin JAR file not found: $jar_file"
        exit 1
    fi
    
    # Verify JAR integrity
    if ! jar -tf "$jar_file" > /dev/null 2>&1; then
        log_error "Invalid JAR file: $jar_file"
        exit 1
    fi
    
    # Copy to providers directory
    log_info "Installing plugin to: $KEYCLOAK_HOME/providers/"
    cp "$jar_file" "$KEYCLOAK_HOME/providers/"
    
    # Verify checksum if available
    if [[ -f "${jar_file}.sha256" ]]; then
        log_info "Verifying checksum..."
        if command -v sha256sum &> /dev/null; then
            (cd "$(dirname "$jar_file")" && sha256sum -c "$(basename "${jar_file}.sha256")")
            log_success "Checksum verification passed"
        else
            log_warning "sha256sum not available, skipping checksum verification"
        fi
    fi
    
    # Cleanup
    [[ -d "$temp_dir" ]] && rm -rf "$temp_dir"
    
    log_success "Plugin installed successfully"
}

configure_keycloak() {
    log_info "Configuring Keycloak..."
    
    local config_file="$KEYCLOAK_HOME/conf/keycloak.conf"
    local backup_config="${config_file}.backup.$(date +%Y%m%d_%H%M%S)"
    
    # Backup existing configuration
    if [[ -f "$config_file" ]]; then
        cp "$config_file" "$backup_config"
        log_info "Configuration backed up to: $backup_config"
    fi
    
    # Add basic Kafka configuration if not present
    if ! grep -q "kafka.bootstrap.servers" "$config_file" 2>/dev/null; then
        log_info "Adding basic Kafka configuration..."
        cat >> "$config_file" << EOF

# Kafka Event Listener Configuration
# Configure these values according to your Kafka setup
# kafka.bootstrap.servers=localhost:9092
# kafka.user.events.topic=keycloak.user.events
# kafka.admin.events.topic=keycloak.admin.events
# kafka.user.events.enabled=true
# kafka.admin.events.enabled=true

EOF
        log_warning "Basic Kafka configuration added to keycloak.conf"
        log_warning "Please uncomment and configure the Kafka settings before starting Keycloak"
    fi
}

build_keycloak() {
    log_info "Building Keycloak with new provider..."
    
    cd "$KEYCLOAK_HOME"
    
    # Run Keycloak build
    if [[ -x "./bin/kc.sh" ]]; then
        ./bin/kc.sh build
        log_success "Keycloak build completed"
    else
        log_error "Keycloak build script not found: $KEYCLOAK_HOME/bin/kc.sh"
        exit 1
    fi
}

verify_installation() {
    log_info "Verifying installation..."
    
    # Check if JAR file exists in providers
    if [[ -f "$KEYCLOAK_HOME/providers/$PLUGIN_JAR" ]]; then
        log_success "Plugin JAR found in providers directory"
    else
        log_error "Plugin JAR not found in providers directory"
        exit 1
    fi
    
    # Check JAR content
    local spi_file="META-INF/services/org.keycloak.events.EventListenerProviderFactory"
    if jar -tf "$KEYCLOAK_HOME/providers/$PLUGIN_JAR" | grep -q "$spi_file"; then
        log_success "SPI configuration found in JAR"
    else
        log_warning "SPI configuration not found in JAR"
    fi
    
    log_success "Installation verification completed"
}

print_next_steps() {
    log_success "Installation completed successfully!"
    echo
    echo "Next steps:"
    echo "1. Configure Kafka settings in $KEYCLOAK_HOME/conf/keycloak.conf"
    echo "2. Start Keycloak: $KEYCLOAK_HOME/bin/kc.sh start"
    echo "3. Enable the event listener in Keycloak Admin Console:"
    echo "   - Go to Events → Config"
    echo "   - Add 'kafka-events' to Event Listeners"
    echo "   - Save configuration"
    echo
    echo "For detailed configuration, see:"
    echo "- COMPATIBILITY_MATRIX.md"
    echo "- Configuration examples in docs/"
    echo
    echo "Troubleshooting:"
    echo "- Check Keycloak logs: $KEYCLOAK_HOME/data/log/"
    echo "- Verify Kafka connectivity"
    echo "- Review configuration settings"
}

# Main installation process
main() {
    echo "========================================"
    echo "Keycloak Kafka Event Listener Installer"
    echo "Version: $PLUGIN_VERSION"
    echo "========================================"
    echo
    
    check_requirements
    backup_existing
    download_plugin
    configure_keycloak
    build_keycloak
    verify_installation
    print_next_steps
}

# Handle script arguments
case "${1:-install}" in
    "install")
        main
        ;;
    "uninstall")
        log_info "Uninstalling Kafka Event Listener..."
        rm -f "$KEYCLOAK_HOME/providers/${PLUGIN_NAME}"*.jar
        cd "$KEYCLOAK_HOME" && ./bin/kc.sh build
        log_success "Plugin uninstalled. Restart Keycloak to complete removal."
        ;;
    "verify")
        verify_installation
        ;;
    "help"|"-h"|"--help")
        echo "Usage: $0 [install|uninstall|verify|help]"
        echo
        echo "Commands:"
        echo "  install    - Install the Kafka Event Listener (default)"
        echo "  uninstall  - Remove the Kafka Event Listener"
        echo "  verify     - Verify installation"
        echo "  help       - Show this help message"
        ;;
    *)
        log_error "Unknown command: $1"
        echo "Use '$0 help' for usage information."
        exit 1
        ;;
esac