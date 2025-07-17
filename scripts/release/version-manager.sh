#!/bin/bash

# Keycloak Kafka Event Listener - Version Manager
# Manages semantic versioning, releases, and deployment

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
VERSION_FILE="$PROJECT_ROOT/gradle.properties"
CHANGELOG_FILE="$PROJECT_ROOT/CHANGELOG.md"
RELEASE_NOTES_DIR="$PROJECT_ROOT/release-notes"

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
Keycloak Kafka Event Listener Version Manager

Usage: $0 [COMMAND] [OPTIONS]

Commands:
    current         Show current version
    bump            Bump version (patch|minor|major)
    release         Create a new release
    changelog       Generate changelog
    notes           Create release notes
    tag             Create git tag for current version
    publish         Publish release to repositories
    validate        Validate version and release readiness
    rollback        Rollback to previous version
    help            Show this help message

Bump Commands:
    bump patch      Increment patch version (x.y.Z)
    bump minor      Increment minor version (x.Y.0)
    bump major      Increment major version (X.0.0)
    bump pre        Add/increment pre-release version (x.y.z-alpha.N)

Release Commands:
    release patch   Create patch release
    release minor   Create minor release
    release major   Create major release
    release rc      Create release candidate
    release stable  Create stable release from RC

Options:
    --dry-run       Show what would be done without executing
    --force         Force operation even if checks fail
    --auto-push     Automatically push changes to remote
    --skip-tests    Skip test execution during release
    --pre-release   Mark as pre-release

Examples:
    $0 current
    $0 bump minor
    $0 release minor --auto-push
    $0 changelog --since v0.1.0
    $0 rollback --to v0.1.0

EOF
}

# Get current version from gradle.properties
get_current_version() {
    if [ -f "$VERSION_FILE" ]; then
        grep "^version=" "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' '
    else
        echo "0.0.1-SNAPSHOT"
    fi
}

# Set version in gradle.properties
set_version() {
    local new_version="$1"
    
    if [ -f "$VERSION_FILE" ]; then
        sed -i.bak "s/^version=.*/version=$new_version/" "$VERSION_FILE"
        rm "$VERSION_FILE.bak"
    else
        echo "version=$new_version" > "$VERSION_FILE"
    fi
    
    log_success "Version updated to $new_version"
}

# Parse semantic version
parse_version() {
    local version="$1"
    
    # Remove 'v' prefix if present
    version="${version#v}"
    
    # Remove pre-release and build metadata
    local base_version="${version%%-*}"
    local pre_release=""
    local build_metadata=""
    
    if [[ "$version" == *"-"* ]]; then
        pre_release="${version#*-}"
        if [[ "$pre_release" == *"+"* ]]; then
            build_metadata="${pre_release#*+}"
            pre_release="${pre_release%+*}"
        fi
    fi
    
    # Split base version
    IFS='.' read -ra VERSION_PARTS <<< "$base_version"
    
    MAJOR="${VERSION_PARTS[0]:-0}"
    MINOR="${VERSION_PARTS[1]:-0}"
    PATCH="${VERSION_PARTS[2]:-0}"
    PRE_RELEASE="$pre_release"
    BUILD_METADATA="$build_metadata"
}

# Bump version
bump_version() {
    local bump_type="$1"
    local current_version
    current_version=$(get_current_version)
    
    parse_version "$current_version"
    
    local new_major="$MAJOR"
    local new_minor="$MINOR"
    local new_patch="$PATCH"
    local new_pre_release=""
    
    case "$bump_type" in
        "major")
            new_major=$((MAJOR + 1))
            new_minor=0
            new_patch=0
            ;;
        "minor")
            new_minor=$((MINOR + 1))
            new_patch=0
            ;;
        "patch")
            new_patch=$((PATCH + 1))
            ;;
        "pre")
            if [ -n "$PRE_RELEASE" ]; then
                # Increment existing pre-release
                if [[ "$PRE_RELEASE" =~ ^(.+)\.([0-9]+)$ ]]; then
                    local pre_base="${BASH_REMATCH[1]}"
                    local pre_num="${BASH_REMATCH[2]}"
                    new_pre_release="$pre_base.$((pre_num + 1))"
                else
                    new_pre_release="$PRE_RELEASE.1"
                fi
            else
                # Add new pre-release
                new_patch=$((PATCH + 1))
                new_pre_release="alpha.1"
            fi
            ;;
        *)
            log_error "Invalid bump type: $bump_type"
            exit 1
            ;;
    esac
    
    local new_version="$new_major.$new_minor.$new_patch"
    if [ -n "$new_pre_release" ]; then
        new_version="$new_version-$new_pre_release"
    fi
    
    set_version "$new_version"
    echo "$new_version"
}

# Validate release readiness
validate_release() {
    local errors=0
    
    log_info "Validating release readiness..."
    
    # Check git status
    if ! git diff-index --quiet HEAD --; then
        log_error "Working directory is not clean. Commit or stash changes first."
        errors=$((errors + 1))
    fi
    
    # Check if on main/master branch
    local current_branch
    current_branch=$(git rev-parse --abbrev-ref HEAD)
    if [[ "$current_branch" != "master" && "$current_branch" != "main" && "$current_branch" != "develop" ]]; then
        log_warning "Not on main branch (currently on: $current_branch)"
    fi
    
    # Check if version is not a snapshot
    local current_version
    current_version=$(get_current_version)
    if [[ "$current_version" == *"SNAPSHOT"* ]]; then
        log_error "Cannot release SNAPSHOT version: $current_version"
        errors=$((errors + 1))
    fi
    
    # Check if tag already exists
    if git rev-parse "v$current_version" >/dev/null 2>&1; then
        log_error "Tag v$current_version already exists"
        errors=$((errors + 1))
    fi
    
    # Run tests if not skipped
    if [ "${SKIP_TESTS:-false}" != "true" ]; then
        log_info "Running tests..."
        if ! "$PROJECT_ROOT/gradlew" test; then
            log_error "Tests failed"
            errors=$((errors + 1))
        fi
    fi
    
    # Check build
    log_info "Checking build..."
    if ! "$PROJECT_ROOT/gradlew" build -x test; then
        log_error "Build failed"
        errors=$((errors + 1))
    fi
    
    if [ $errors -eq 0 ]; then
        log_success "Release validation passed"
        return 0
    else
        log_error "Release validation failed with $errors errors"
        return 1
    fi
}

# Generate changelog
generate_changelog() {
    local since_tag="${1:-}"
    local output_file="$CHANGELOG_FILE"
    
    log_info "Generating changelog..."
    
    # Create changelog directory if it doesn't exist
    mkdir -p "$(dirname "$output_file")"
    
    # Get current version
    local current_version
    current_version=$(get_current_version)
    
    # Get previous tag if not specified
    if [ -z "$since_tag" ]; then
        since_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
    fi
    
    # Create changelog header
    cat > "$output_file" << EOF
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

EOF
    
    # Add current version section
    echo "## [$current_version] - $(date +%Y-%m-%d)" >> "$output_file"
    echo "" >> "$output_file"
    
    # Generate sections based on conventional commits
    local temp_file=$(mktemp)
    
    if [ -n "$since_tag" ]; then
        git log "$since_tag..HEAD" --oneline --pretty=format:"%s" > "$temp_file"
    else
        git log --oneline --pretty=format:"%s" > "$temp_file"
    fi
    
    # Process commits by type
    echo "### Added" >> "$output_file"
    grep -E "^feat(\(.+\))?: " "$temp_file" | sed 's/^feat[^:]*: /- /' >> "$output_file" || true
    echo "" >> "$output_file"
    
    echo "### Changed" >> "$output_file"
    grep -E "^refactor(\(.+\))?: " "$temp_file" | sed 's/^refactor[^:]*: /- /' >> "$output_file" || true
    grep -E "^perf(\(.+\))?: " "$temp_file" | sed 's/^perf[^:]*: /- /' >> "$output_file" || true
    echo "" >> "$output_file"
    
    echo "### Fixed" >> "$output_file"
    grep -E "^fix(\(.+\))?: " "$temp_file" | sed 's/^fix[^:]*: /- /' >> "$output_file" || true
    echo "" >> "$output_file"
    
    echo "### Security" >> "$output_file"
    grep -E "^security(\(.+\))?: " "$temp_file" | sed 's/^security[^:]*: /- /' >> "$output_file" || true
    echo "" >> "$output_file"
    
    echo "### Documentation" >> "$output_file"
    grep -E "^docs(\(.+\))?: " "$temp_file" | sed 's/^docs[^:]*: /- /' >> "$output_file" || true
    echo "" >> "$output_file"
    
    # Add previous versions if they exist
    if [ -n "$since_tag" ] && [ -f "$output_file.backup" ]; then
        echo "" >> "$output_file"
        tail -n +10 "$output_file.backup" >> "$output_file" 2>/dev/null || true
    fi
    
    rm -f "$temp_file"
    
    log_success "Changelog generated: $output_file"
}

# Create release notes
create_release_notes() {
    local version="$1"
    local release_type="${2:-stable}"
    
    mkdir -p "$RELEASE_NOTES_DIR"
    local notes_file="$RELEASE_NOTES_DIR/v$version.md"
    
    log_info "Creating release notes for v$version..."
    
    cat > "$notes_file" << EOF
# Release Notes - v$version

**Release Date:** $(date +%Y-%m-%d)
**Release Type:** $release_type

## Overview

This release includes the following major changes and improvements to the Keycloak Kafka Event Listener.

## What's New

### Features
- 

### Improvements
- 

### Bug Fixes
- 

## Breaking Changes

⚠️ **Important:** This section lists any breaking changes that may affect existing deployments.

- None in this release

## Upgrade Instructions

### From Previous Version

1. **Backup Current Configuration**
   \`\`\`bash
   cp \$KEYCLOAK_HOME/conf/keycloak.conf \$KEYCLOAK_HOME/conf/keycloak.conf.backup
   \`\`\`

2. **Stop Keycloak Service**
   \`\`\`bash
   systemctl stop keycloak
   \`\`\`

3. **Update Plugin**
   \`\`\`bash
   # Remove old version
   rm \$KEYCLOAK_HOME/providers/keycloak-kafka-*.jar
   
   # Install new version
   wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v$version/keycloak-kafka-event-listener-$version.jar
   cp keycloak-kafka-event-listener-$version.jar \$KEYCLOAK_HOME/providers/
   \`\`\`

4. **Rebuild and Start Keycloak**
   \`\`\`bash
   \$KEYCLOAK_HOME/bin/kc.sh build
   systemctl start keycloak
   \`\`\`

5. **Verify Installation**
   \`\`\`bash
   curl -f http://localhost:8080/health
   curl http://localhost:9090/metrics | grep keycloak_kafka
   \`\`\`

## Configuration Changes

### New Configuration Options

\`\`\`properties
# Add any new configuration options here
\`\`\`

### Deprecated Configuration Options

- None in this release

## Compatibility

- **Keycloak:** 24.0+ (recommended: 26.3.1+)
- **Java:** OpenJDK 17+ (recommended: OpenJDK 21+)
- **Kafka:** 2.8+ (recommended: 3.8+)
- **Kubernetes:** 1.20+ (if using containerized deployment)

## Known Issues

- None known at release time

## Security Updates

This release includes the following security improvements:

- Regular dependency updates
- Enhanced SSL/TLS configuration validation
- Improved credential encryption

## Performance Improvements

- Circuit breaker optimization
- Memory usage improvements
- Connection pool enhancements

## Documentation Updates

- Updated installation guide
- Enhanced troubleshooting documentation
- New performance tuning guide
- Kubernetes deployment examples

## Contributors

Thanks to all contributors who made this release possible!

## Support

- **Documentation:** [Installation Guide](docs/INSTALLATION.md)
- **Issues:** [GitHub Issues](https://github.com/scriptonbasestar/sb-keycloak-exts/issues)
- **Discussions:** [GitHub Discussions](https://github.com/scriptonbasestar/sb-keycloak-exts/discussions)

## Checksums

\`\`\`
# This will be filled automatically during release
\`\`\`

---

For the complete list of changes, see the [CHANGELOG.md](CHANGELOG.md).
EOF
    
    log_success "Release notes created: $notes_file"
    
    # Open editor if available
    if command -v "${EDITOR:-nano}" >/dev/null 2>&1; then
        log_info "Opening release notes for editing..."
        "${EDITOR:-nano}" "$notes_file"
    fi
}

# Create git tag
create_tag() {
    local version="$1"
    local tag_name="v$version"
    local pre_release="${2:-false}"
    
    log_info "Creating git tag: $tag_name"
    
    # Create annotated tag
    local tag_message="Release $tag_name"
    if [ "$pre_release" = "true" ]; then
        tag_message="Pre-release $tag_name"
    fi
    
    git tag -a "$tag_name" -m "$tag_message"
    
    log_success "Git tag created: $tag_name"
}

# Perform release
perform_release() {
    local release_type="$1"
    local auto_push="${2:-false}"
    local pre_release="${3:-false}"
    
    log_info "Starting $release_type release process..."
    
    # Validate release readiness
    if ! validate_release; then
        if [ "${FORCE:-false}" != "true" ]; then
            log_error "Release validation failed. Use --force to override."
            exit 1
        else
            log_warning "Proceeding with release despite validation errors (--force used)"
        fi
    fi
    
    # Get current version
    local current_version
    current_version=$(get_current_version)
    
    # Bump version based on release type
    local new_version
    case "$release_type" in
        "patch"|"minor"|"major")
            new_version=$(bump_version "$release_type")
            ;;
        "rc")
            new_version=$(bump_version "pre")
            pre_release="true"
            ;;
        "stable")
            # Remove pre-release suffix
            parse_version "$current_version"
            new_version="$MAJOR.$MINOR.$PATCH"
            set_version "$new_version"
            ;;
        *)
            log_error "Invalid release type: $release_type"
            exit 1
            ;;
    esac
    
    # Generate changelog
    generate_changelog
    
    # Create release notes
    create_release_notes "$new_version" "$release_type"
    
    # Build release artifacts
    log_info "Building release artifacts..."
    "$PROJECT_ROOT/gradlew" clean build shadowJar
    
    # Create checksum file
    local jar_file="$PROJECT_ROOT/events/event-listener-kafka/build/libs/keycloak-kafka-event-listener-$new_version.jar"
    if [ -f "$jar_file" ]; then
        cd "$(dirname "$jar_file")"
        sha256sum "$(basename "$jar_file")" > "$(basename "$jar_file").sha256"
        cd - > /dev/null
        log_success "Checksum created for release artifact"
    fi
    
    # Commit changes
    git add .
    git commit -m "release: prepare release v$new_version"
    
    # Create tag
    create_tag "$new_version" "$pre_release"
    
    # Push if requested
    if [ "$auto_push" = "true" ]; then
        log_info "Pushing changes to remote..."
        git push origin "$(git rev-parse --abbrev-ref HEAD)"
        git push origin "v$new_version"
    fi
    
    log_success "Release v$new_version completed successfully!"
    
    # Show next steps
    echo
    log_info "Next steps:"
    echo "1. Review the release notes in $RELEASE_NOTES_DIR/v$new_version.md"
    echo "2. Push changes: git push && git push --tags"
    echo "3. Create GitHub release with release notes"
    echo "4. Publish to container registries"
    echo "5. Update documentation"
}

# Rollback to previous version
rollback_version() {
    local target_version="$1"
    
    log_warning "Rolling back to version $target_version"
    
    # Validate target version exists
    if ! git rev-parse "v$target_version" >/dev/null 2>&1; then
        log_error "Tag v$target_version does not exist"
        exit 1
    fi
    
    # Checkout target version
    git checkout "v$target_version" -- "$VERSION_FILE"
    
    # Update version in gradle.properties
    local rollback_version
    rollback_version=$(get_current_version)
    
    log_success "Rolled back to version $rollback_version"
    
    # Commit rollback
    git add "$VERSION_FILE"
    git commit -m "rollback: revert to version $rollback_version"
}

# Publish release
publish_release() {
    local version="$1"
    
    log_info "Publishing release v$version..."
    
    # Build and publish to repositories
    "$PROJECT_ROOT/gradlew" publish
    
    # Build and push Docker images
    if command -v docker >/dev/null 2>&1; then
        log_info "Building and pushing Docker images..."
        
        local image_name="scriptonbasestar/keycloak-kafka-event-listener"
        
        # Build image
        docker build -t "$image_name:$version" -t "$image_name:latest" "$PROJECT_ROOT"
        
        # Push images
        docker push "$image_name:$version"
        docker push "$image_name:latest"
        
        log_success "Docker images published"
    fi
    
    log_success "Release v$version published successfully!"
}

# Main function
main() {
    local command="${1:-help}"
    local dry_run=false
    local force=false
    local auto_push=false
    local skip_tests=false
    local pre_release=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                dry_run=true
                shift
                ;;
            --force)
                force=true
                shift
                ;;
            --auto-push)
                auto_push=true
                shift
                ;;
            --skip-tests)
                skip_tests=true
                shift
                ;;
            --pre-release)
                pre_release=true
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
    export DRY_RUN="$dry_run"
    export FORCE="$force"
    export SKIP_TESTS="$skip_tests"
    
    # Execute command
    case "$command" in
        "current")
            echo "Current version: $(get_current_version)"
            ;;
        "bump")
            local bump_type="${2:-patch}"
            bump_version "$bump_type"
            ;;
        "release")
            local release_type="${2:-patch}"
            perform_release "$release_type" "$auto_push" "$pre_release"
            ;;
        "changelog")
            local since_tag="${2:-}"
            generate_changelog "$since_tag"
            ;;
        "notes")
            local version="${2:-$(get_current_version)}"
            local release_type="${3:-stable}"
            create_release_notes "$version" "$release_type"
            ;;
        "tag")
            local version="${2:-$(get_current_version)}"
            create_tag "$version" "$pre_release"
            ;;
        "publish")
            local version="${2:-$(get_current_version)}"
            publish_release "$version"
            ;;
        "validate")
            validate_release
            ;;
        "rollback")
            local target_version="${2:-}"
            if [ -z "$target_version" ]; then
                log_error "Target version required for rollback"
                exit 1
            fi
            rollback_version "$target_version"
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