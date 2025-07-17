# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.2] - 2024-12-19

### Added

#### Keycloak Kafka Event Listener
- **Event Streaming**: Complete Kafka integration for Keycloak events
  - Real-time user events (login, logout, registration, profile updates)
  - Admin events (user management, realm configuration changes)
  - Configurable event filtering and routing
  - JSON serialization with Jackson

- **Performance & Reliability**
  - Circuit breaker pattern for fault tolerance
  - Backpressure management for flow control
  - Connection pooling for optimal resource usage
  - Performance tuning profiles (throughput, latency, balanced, reliability)
  - JVM optimization with G1GC and memory tuning

- **Monitoring & Observability**
  - Comprehensive Prometheus metrics integration
  - Grafana dashboard templates
  - ELK Stack configuration for log analysis
  - Health checks and readiness probes
  - Performance benchmarking tools

- **Security & Authentication**
  - SSL/TLS encryption for Kafka connections
  - SASL authentication support (SCRAM-SHA-256/512, PLAIN, GSSAPI)
  - Credential encryption with AES-256-GCM
  - Certificate management and validation
  - Security audit logging

- **Alerting & Incident Response**
  - Multi-channel notification system (Email, Slack, Webhook, PagerDuty)
  - Automated incident response and remediation
  - Prometheus AlertManager integration
  - Circuit breaker and backpressure automated recovery
  - Memory cleanup and scaling automation

- **Production Deployment**
  - Docker container with health checks
  - Kubernetes Helm Chart with best practices
  - ConfigMaps and Secrets management
  - Horizontal Pod Autoscaler configuration
  - Network policies and RBAC

- **Documentation & Operations**
  - Comprehensive installation guide
  - Configuration reference with examples
  - Troubleshooting guide with common issues
  - Operations runbook for daily/weekly/monthly tasks
  - Performance tuning recommendations

#### Development & Testing
- **TestContainers Integration**
  - Automated integration testing with Kafka and Keycloak
  - Docker-based test environments
  - Performance and load testing suites
  - GitHub Actions CI/CD pipeline

- **Release Management**
  - Semantic versioning with automated bumping
  - Automated changelog generation
  - Multi-platform Docker images (AMD64, ARM64)
  - GitHub Releases with artifacts and checksums
  - Helm Chart packaging and publishing

### Changed
- Updated Keycloak compatibility to 24.0+ (recommended: 26.3.1+)
- Enhanced Gradle build configuration with Shadow JAR
- Improved error handling and logging throughout the codebase
- Optimized dependency management and security updates

### Security
- Implemented credential encryption for sensitive configuration
- Added SSL/TLS certificate validation and rotation
- Enhanced authentication mechanisms for Kafka connections
- Security scanning with Trivy and OWASP Dependency Check

### Fixed
- Resolved MockK and JUnit dependency conflicts in tests
- Fixed Gradle bundle configuration for TestContainers
- Corrected Docker image build process for multi-platform support
- Fixed memory leaks in connection pool management

## [0.0.1] - 2024-01-01

### Added
- Initial project structure
- Basic Keycloak Identity Provider extensions
  - Kakao Identity Provider
  - LINE Identity Provider  
  - Naver Identity Provider
- Gradle multi-module build configuration
- Basic CI/CD pipeline with GitHub Actions

### Documentation
- Initial README.md
- Basic build and installation instructions
- Identity provider configuration examples

---

## Upgrade Notes

### From 0.0.1 to 0.0.2

This is a major update that introduces the Keycloak Kafka Event Listener alongside the existing Identity Provider extensions.

#### Breaking Changes
- **Java Version**: Now requires OpenJDK 17+ (recommended: OpenJDK 21+)
- **Keycloak Version**: Now requires Keycloak 24.0+ (recommended: 26.3.1+)
- **Configuration**: New configuration format for Kafka integration

#### New Dependencies
- Apache Kafka Client 3.8+
- Jackson for JSON serialization
- Micrometer for metrics
- TestContainers for integration testing

#### Migration Steps
1. **Update Java Version**
   ```bash
   # Ensure Java 17+ is installed
   java -version
   ```

2. **Backup Configuration**
   ```bash
   cp $KEYCLOAK_HOME/conf/keycloak.conf $KEYCLOAK_HOME/conf/keycloak.conf.backup
   ```

3. **Install New Plugin**
   ```bash
   # Remove old plugins if any
   rm $KEYCLOAK_HOME/providers/keycloak-*-0.0.1.jar
   
   # Install new version
   cp keycloak-kafka-event-listener-0.0.2.jar $KEYCLOAK_HOME/providers/
   ```

4. **Update Configuration**
   ```properties
   # Add Kafka configuration to keycloak.conf
   kafka.bootstrap.servers=localhost:9092
   kafka.user.events.topic=keycloak.user.events
   kafka.admin.events.topic=keycloak.admin.events
   kafka.user.events.enabled=true
   kafka.admin.events.enabled=true
   ```

5. **Rebuild and Restart**
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   systemctl restart keycloak
   ```

6. **Verify Installation**
   ```bash
   curl -f http://localhost:8080/health
   curl http://localhost:9090/metrics | grep keycloak_kafka
   ```

#### New Features Available After Upgrade
- Real-time event streaming to Kafka
- Advanced monitoring and alerting
- Performance optimization tools
- Automated incident response
- Production-ready containerization

For detailed migration instructions, see the [Installation Guide](docs/INSTALLATION.md).