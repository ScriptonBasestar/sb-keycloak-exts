# Keycloak Extensions Project - CLAUDE.md

## 1. Project Overview

### 1.1 Purpose and Type
This is a **Keycloak extensions project** providing SPI (Service Provider Interface) implementations in Kotlin for:
- **Identity Provider Extensions** (OAuth2 social login integrations)
- **Event Listener Extensions** (real-time event streaming to messaging systems)
- **Realm Management Extensions** (advanced realm hierarchy and configuration management)

**Target**: Keycloak 26.0.7+ | **Language**: Kotlin 2.2.21 | **Java**: JDK 21

### 1.2 Supported Social Login Providers

**Korean Providers:**
- Kakao (카카오) - OAuth2 IdP
- LINE (라인) - OAuth2 IdP
- Naver (네이버) - OAuth2 IdP

**Global Providers:**
- Google - OpenID Connect IdP
- GitHub - OAuth2 IdP

**Event Integration:**
- Kafka Event Listener - Streams user and admin events to Kafka topics
- RabbitMQ, NATS, Redis, MQTT, AWS, Azure - Multiple messaging system support

**Realm Management:**
- Realm Hierarchy Manager - Parent-child relationships with automatic configuration inheritance

### 1.3 Key Components

```
idps/                           # Identity Provider extensions
├── idp-kakao/                 # Kakao OAuth2 provider
├── idp-line/                  # LINE OAuth2 provider
├── idp-naver/                 # Naver OAuth2 provider
├── idp-google/                # Google OIDC provider
└── idp-github/                # GitHub OAuth2 provider

events/                         # Event Listener extensions
├── event-listener-kafka/      # Kafka event streaming
├── event-listener-rabbitmq/   # RabbitMQ integration
├── event-listener-nats/       # NATS integration
├── event-listener-redis/      # Redis Pub/Sub
├── event-listener-mqtt/       # MQTT broker integration
├── event-listener-aws/        # AWS SNS/SQS
└── event-listener-azure/      # Azure Service Bus/Event Grid

realms/                         # Realm Management extensions
└── realm-hierarchy/           # Realm hierarchy manager with REST API

themes/                         # Keycloak UI themes
└── corporate-clean/           # Enterprise login theme

.github/workflows/              # CI/CD automation
├── ci.yml                      # Build, test, lint, security
├── integration-tests.yml       # E2E tests with TestContainers
├── publish-snapshot-jar.yml    # Snapshot artifact publishing
└── publish-release-jar.yml     # Release artifact publishing
```

---

## 2. Build and Development Commands

### 2.1 Core Build Tasks

```bash
# Build entire project
./gradlew build

# Build specific provider
./gradlew :idps:idp-kakao:build
./gradlew :events:event-listener-kafka:build
./gradlew :realms:realm-hierarchy:build

# Create Shadow JAR (fat JAR for Keycloak deployment)
./gradlew shadowJar
./gradlew :idps:idp-kakao:shadowJar
./gradlew :realms:realm-hierarchy:shadowJar

# Production build with all checks
./gradlew clean build test shadowJar
```

### 2.2 Testing

```bash
# Run all tests
./gradlew test

# Run tests for specific provider
./gradlew :idps:idp-kakao:test

# Integration tests (with TestContainers - requires Docker)
./gradlew integrationTest
./gradlew :events:event-listener-kafka:integrationTest

# Test specific class
./gradlew :idps:idp-kakao:test --tests "*KakaoIdentityProviderTest"
```

### 2.3 Code Quality & Linting

```bash
# Kotlin linting (ktlint)
./gradlew ktlintCheck           # Check only
./gradlew ktlintFormat          # Auto-fix issues

# Static code analysis (Detekt)
./gradlew detekt                # Run Detekt

# Dependency vulnerability scanning
./gradlew dependencyCheckAnalyze

# Combined quality check
./gradlew check
```

### 2.4 Makefile Commands (Recommended)

```bash
make help                   # Show all available commands
make build                  # Full build
make test                   # Run tests
make lint                   # Code quality checks
make lint-fix               # Auto-fix lint issues
make shadow                 # Generate Shadow JARs
make dependency-check       # Security vulnerability scan
make clean                  # Clean build artifacts
make check                  # All quality checks
make ci                     # Full CI pipeline (clean lint build test)
make release                # Release build (ci + shadow + security)
make dev-setup              # Install Git hooks and setup environment
make status                 # Show project and tool versions
```

### 2.5 Local Development Setup

```bash
# Setup development environment with Git hooks
make dev-setup

# Auto-format code on commit
make install-hooks

# Start complete local stack (Keycloak + Kafka + PostgreSQL + monitoring)
docker-compose up -d

# With monitoring stack (Prometheus + Grafana)
docker-compose --profile monitoring up -d

# With logging stack (Elasticsearch + Kibana + Logstash)
docker-compose --profile logging up -d

# Full stack with all features
docker-compose --profile dev --profile monitoring --profile logging up -d
```

### 2.6 Keycloak Deployment

```bash
# Build all providers
./gradlew shadowJar

# Copy to Keycloak providers directory
cp idps/*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/
cp events/*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak with new providers
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

---

## 3. Architecture and Code Structure

### 3.1 Keycloak SPI Architecture

This project implements Keycloak's Service Provider Interface (SPI):

**Identity Providers SPI:**
```
org.keycloak.broker.provider.IdentityProviderFactory
  ↓
IdentityProviderFactory (creates provider instances)
  ↓
IdentityProvider (handles OAuth2 flow: authorization, token exchange, profile fetch)
  ↓
IdentityProviderMapper (maps external user attributes → Keycloak user attributes)
```

**Event Listeners SPI:**
```
org.keycloak.events.EventListenerProviderFactory
  ↓
EventListenerProvider (implements onEvent() and onAdminEvent() methods)
  ↓
Kafka Producer (sends events to Kafka topics)
```

### 3.2 OAuth2 Identity Provider Flow

Each OAuth2 provider (Kakao, LINE, Naver, Google, GitHub) implements:

1. **Provider Factory** - Creates provider instances
2. **Identity Provider** - Extends `AbstractOAuth2IdentityProvider`
3. **Configuration** - Stores client ID, scopes, endpoints
4. **User Attribute Mapper** - Maps OAuth2 response to Keycloak user attributes

Example: `idp-kakao` module structure:
```
src/main/kotlin/org/scriptonbasestar/kcexts/idp/kakao/
├── KakaoIdentityProvider.kt           # Core OAuth2 handler
├── KakaoIdentityProviderFactory.kt    # Factory (SPI entry point)
├── KakaoIdentityProviderConfig.kt     # Configuration properties
├── KakaoUserAttributeMapper.kt        # User profile mapping
└── KakaoConstant.kt                   # Constants (scopes, endpoints)

src/main/resources/META-INF/services/
└── org.keycloak.broker.social.SocialIdentityProvider  # SPI registration
```

### 3.3 Kafka Event Listener Architecture

Real-time event streaming to Kafka with metrics and error handling:

```
Keycloak Event System
  ↓
KafkaEventListenerProvider (implements EventListenerProvider)
  ↓
KafkaProducerManager (manages Kafka producer lifecycle)
  ↓
KafkaEventMetrics (Micrometer metrics collection)
  ↓
Event Model (KeycloakEvent, KeycloakAdminEvent) → JSON
  ↓
Kafka Topics (keycloak.events, keycloak.admin.events)
```

**Key Components:**

```
src/main/kotlin/org/scriptonbasestar/kcexts/events/kafka/
├── KafkaEventListenerProvider.kt       # Main event processor
├── KafkaEventListenerProviderFactory.kt # Factory (SPI entry point)
├── KafkaEventListenerConfig.kt         # Configuration from realm attributes
├── KafkaProducerManager.kt             # Producer lifecycle management
├── metrics/
│   ├── KafkaEventMetrics.kt            # Micrometer metrics
│   └── MetricsCollector.kt             # Metrics aggregation
└── model/
    ├── KeycloakEvent.kt                # User event model
    └── KeycloakAdminEvent.kt           # Admin event model

src/main/resources/META-INF/services/
└── org.keycloak.events.EventListenerProviderFactory  # SPI registration
```

### 3.4 Realm Hierarchy Architecture

Hierarchical realm management with configuration inheritance:

```
Keycloak Admin API
  ↓
RealmHierarchyResource (REST API)
  ├── RealmHierarchyStorage (Realm Attributes based persistence)
  └── InheritanceManager (inheritance logic)

Keycloak Event System
  ↓
RealmHierarchyEventListener
  ├── RealmHierarchyStorage
  └── InheritanceManager → Propagate changes to child realms
```

**Key Components:**

```
src/main/kotlin/org/scriptonbasestar/kcexts/realm/hierarchy/
├── RealmHierarchyEventListener.kt          # Event-driven synchronization
├── RealmHierarchyEventListenerFactory.kt   # Factory (SPI entry point)
├── model/
│   └── RealmHierarchyNode.kt               # Hierarchy metadata model
├── storage/
│   └── RealmHierarchyStorage.kt            # Realm Attributes CRUD
├── inheritance/
│   └── InheritanceManager.kt               # Inheritance logic (IdP, Role)
└── api/
    ├── RealmHierarchyResource.kt           # REST API endpoints
    ├── RealmHierarchyResourceProvider.kt   # Resource provider
    ├── RealmHierarchyResourceProviderFactory.kt  # Factory
    └── dto/                                 # Request/Response models

src/main/resources/META-INF/services/
├── org.keycloak.events.EventListenerProviderFactory        # Event listener SPI
└── org.keycloak.services.resource.RealmResourceProviderFactory  # REST API SPI
```

**Inheritance Features:**
- Identity Providers: Clone from parent with metadata tracking
- Realm Roles: Inherit with `hierarchy.inherited` attribute
- Authentication Flows: Planned for future release

**Storage Strategy:**
- Realm Attributes: JSON serialization, no DB schema changes
- Circular Reference Prevention: Detects A → B → A patterns
- Max Depth: 10 levels

### 3.5 Key Design Patterns

**1. SPI (Service Provider Interface)**
- Keycloak discovers and loads extensions via `META-INF/services/`
- Each module registers its factory class

**2. Factory Pattern**
- `*Factory` classes create provider instances
- Manages lifecycle and dependency injection

**3. Configuration Management**
- Properties loaded from Realm Attributes (admin UI)
- System properties as fallback
- Environment variables support

**4. Shadow JAR (Fat JAR)**
- Gradle Shadow plugin bundles all dependencies
- Keycloak providers directory deployment
- Conflict resolution for multiple providers

**5. Metrics & Observability**
- Micrometer metrics for monitoring
- Event-level metrics (count, latency, errors)
- Prometheus export support

### 3.5 Event Listener Configuration

Configuration is loaded from multiple sources (priority order):

1. **Realm Attributes** (highest priority)
   - Path: Realm → Attributes → kafka.*
   
2. **System Properties**
   - JVM startup args: `-Dkafka.bootstrap.servers=...`

3. **Environment Variables**
   - Docker: `KAFKA_BOOTSTRAP_SERVERS=...`

**Available Configuration Keys:**
```
kafka.bootstrap.servers            # Kafka broker addresses
kafka.event.topic                  # User event topic
kafka.admin.event.topic            # Admin event topic
kafka.client.id                    # Kafka client identifier
kafka.enable.user.events           # Enable/disable user events
kafka.enable.admin.events          # Enable/disable admin events
kafka.included.event.types         # Comma-separated event type filter
kafka.security.protocol            # PLAINTEXT, SSL, SASL_SSL
kafka.acks                         # Producer acks (0, 1, all)
kafka.retries                      # Producer retry count
kafka.batch.size                   # Batch size in bytes
kafka.linger.ms                    # Batch linger time
kafka.compression.type             # Compression type (gzip, snappy, lz4)
```

### 3.6 Dependency Structure

```
Root Project
├── idps/                    (shared IdP configuration)
│   ├── idp-kakao/          (independent provider)
│   ├── idp-line/           (independent provider)
│   ├── idp-naver/          (independent provider)
│   ├── idp-google/         (independent provider)
│   └── idp-github/         (independent provider)
└── events/                  (shared event listener configuration)
    └── event-listener-kafka/ (independent listener)

All modules inherit from:
- build.gradle (root) - plugins, quality tools
- idps/build.gradle - IdP-specific defaults
- events/build.gradle - Event listener-specific defaults
```

---

## 4. Important Files to Check

### 4.1 Configuration Files

**Version Management:**
- `/gradle/libs.versions.toml` - Centralized dependency versions
  - Keycloak 26.0.7
  - Kotlin 2.2.21
  - Gradle 9.2
  - Note: Detekt disabled (incompatible with Kotlin 2.2.21, awaiting Detekt 2.0.0)

**Build Configuration:**
- `build.gradle` - Root build configuration, plugins, quality tools
- `idps/build.gradle` - IdP module defaults (Shadow JAR, service bundling)
- `events/build.gradle` - Event listener defaults (TestContainers, Integration tests)
- `gradle.properties` - JVM settings, Kotlin config, parallel builds

**Gradle Tasks:**
- `gradle/publish.gradle` - Publishing configuration for artifacts

### 4.2 Development Environment

**Docker Compose Stack:**
- `docker-compose.yml` - Complete local development environment
  - **Core Services**: Keycloak (8080), PostgreSQL, Kafka, Zookeeper
  - **Optional Profiles**:
    - `dev` - Kafka UI (8081)
    - `monitoring` - Prometheus (9090), Grafana (3000)
    - `logging` - Elasticsearch (9200), Kibana (5601), Logstash

**Dockerfile:**
- `docker/Dockerfile` - Keycloak image with provider JAR mounting

**Scripts:**
- `docker/init-scripts/` - Database initialization
- `docker/monitoring/` - Prometheus, Grafana configuration
- `docker/logging/` - Logstash pipeline configuration

### 4.3 CI/CD Pipeline

**GitHub Actions Workflows:**

1. **ci.yml** (PR & Push validation)
   - Test: Java 17, 21
   - Build: Shadow JAR generation
   - Code Quality: ktlint, Detekt, dependency check
   - Artifact Upload

2. **integration-tests.yml**
   - TestContainers-based E2E tests
   - Kafka event listener verification
   - Profile-based execution

3. **publish-snapshot-jar.yml**
   - Publishes snapshots on develop branch push
   - Tag: `develop` or branch name

4. **publish-release-jar.yml**
   - Publishes releases on version tag push
   - Pattern: `v*.*.*`
   - Creates GitHub Release with artifacts

5. **release.yml**
   - Semantic versioning support
   - Changelog generation
   - Automated release notes

### 4.4 Code Quality Configuration

**KtLint:**
- Version 1.7.1
- Config: Root level (enforced on all modules)
- Reporters: PLAIN, CHECKSTYLE, SARIF
- Exclusions: generated/ and build/

**Detekt:**
- Version 1.23.7
- Config: `config/detekt/detekt.yml`
- Reports: HTML, XML, TXT
- Status: Disabled in Gradle builds (Kotlin 2.2.21 incompatibility)

**Dependency Check:**
- OWASP CVE scanning
- CVSS Threshold: 7.0 (fails build if exceeded)
- Suppression file: `config/dependency-check/suppressions.xml`

### 4.5 Project Documentation

**Root Documentation:**
- `README.md` - Project overview, setup, features
- `CHANGELOG.md` - Version history
- `SECURITY.md` - Security considerations, vulnerability reporting
- `COMPATIBILITY_MATRIX.md` - Keycloak version compatibility
- `PROJECT-STATUS.md` - Current project status

**Provider-Specific Documentation:**
- `idps/idp-*/README.md` - Provider setup guides (Kakao, LINE, Naver, Google, GitHub)
- `events/event-listener-kafka/README.md` - Kafka event listener configuration

**Operations Documentation:**
- `docs/INSTALLATION.md` - Deployment instructions
- `docs/CONFIGURATION.md` - Detailed configuration guide
- `docs/OPERATIONS.md` - Operational runbooks
- `docs/TROUBLESHOOTING.md` - Common issues and solutions

### 4.6 SPI Service Provider Files

**Identity Provider Registration:**
- Location: `src/main/resources/META-INF/services/org.keycloak.broker.social.SocialIdentityProvider`
- Content: FQCN of Factory class (one per line)
- Pattern: Used by all IdP modules

**Event Listener Registration:**
- Location: `src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory`
- Content: `org.scriptonbasestar.kcexts.events.kafka.KafkaEventListenerProviderFactory`

---

## 5. Special Keycloak-Specific Considerations

### 5.1 SPI Implementation Pattern

**Mandatory Interfaces:**

```kotlin
// Identity Provider
interface SocialIdentityProvider<C : IdentityProviderConfig> : IdentityProvider {
    // Keycloak calls these methods during OAuth2 flow
    fun fetchUserProfile(): User
    fun getProfileUrl(): String
}

// Event Listener
interface EventListenerProvider {
    fun onEvent(event: Event)
    fun onAdminEvent(event: AdminEvent, includeRepresentation: Boolean)
    fun close()
}
```

**Factory Registration:**

```kotlin
class KakaoIdentityProviderFactory : IdentityProviderFactory {
    override fun create(session: KeycloakSession, config: Config): IdentityProvider {
        // Create and return provider instance
    }
}
```

### 5.2 Configuration Handling

**Three-Level Configuration Hierarchy:**

```
Realm Attributes (highest)
  ↓ override
System Properties (-D flags)
  ↓ override
Environment Variables (lowest)
  ↓ fallback
Hardcoded Defaults
```

Example in Kafka listener:
```kotlin
// Load configuration
val bootstrapServers = realm.getAttribute("kafka.bootstrap.servers")
    ?: System.getProperty("kafka.bootstrap.servers")
    ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
    ?: "localhost:9092"
```

### 5.3 ClassLoader Isolation

**Critical Issue:**
- Keycloak isolates provider ClassLoaders
- Dependencies must be included via Shadow JAR
- No access to Keycloak's lib/ unless explicitly `compileOnly`

**Shadow JAR Configuration:**
```gradle
shadowJar {
    include(dependency('org.apache.kafka:kafka-clients'))
    include(dependency('com.fasterxml.jackson:...'))
}
```

### 5.4 Event Types and Admin Events

**User Event Types:**
```
LOGIN, LOGOUT, REGISTER, LOGIN_ERROR, UPDATE_PROFILE, 
UPDATE_PASSWORD, SEND_VERIFY_EMAIL, VERIFY_EMAIL, 
SEND_RESET_PASSWORD, RESET_PASSWORD, ...
```

**Admin Event Types:**
```
CREATE, UPDATE, DELETE, ACTION
```

**Event Model:**
```kotlin
data class KeycloakEvent(
    val id: String,                    // UUID
    val time: Long,                    // Timestamp
    val type: String,                  // Event type
    val realmId: String,               // Realm
    val clientId: String?,             // Client ID
    val userId: String?,               // User ID
    val sessionId: String?,            // Session ID
    val ipAddress: String?,            // Client IP
    val details: Map<String, String>?  // Event-specific data
)
```

### 5.5 Metrics and Observability

**Kafka Event Listener Metrics (Micrometer):**
```
keycloak.kafka.events.sent{event_type="LOGIN", realm="master"}
keycloak.kafka.events.failed{event_type="LOGIN_ERROR", error_type="KafkaException"}
keycloak.kafka.events.duration_ms{event_type="UPDATE_PROFILE"}
```

**Prometheus Scrape Endpoint:**
- URL: `http://keycloak:8080/metrics`
- Format: OpenMetrics / Prometheus
- Enabled: `KC_METRICS_ENABLED=true`

### 5.6 Testing Keycloak Extensions

**Unit Tests:**
- Use embedded Keycloak test fixtures
- Mock KeycloakSession and related objects
- No external dependencies required

**Integration Tests:**
- Use TestContainers for Kafka
- Spin up actual Keycloak instance
- Test full OAuth2/event flow

**Location:**
```
src/test/kotlin/                  # Unit tests
src/integrationTest/kotlin/       # E2E tests (Kafka listener only)
```

**TestContainers Usage:**
```gradle
testImplementation "org.testcontainers:kafka:${testcontainersVersion}"
testImplementation "org.testcontainers:postgresql:${testcontainersVersion}"
```

---

## 6. Development Workflow

### 6.1 Local Development with Docker Compose

```bash
# Start complete development environment
docker-compose up -d

# Verify services
docker-compose ps

# Access Keycloak Admin Console
# URL: http://localhost:8080
# User: admin / admin

# Monitor Kafka events
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic keycloak.events \
  --from-beginning
```

### 6.2 Adding a New OAuth2 Provider

**Template Structure:**
1. Create module: `idps/idp-{provider}/`
2. Copy from existing provider (e.g., idp-kakao)
3. Modify constants (endpoints, scopes)
4. Customize user attribute mapper
5. Register factory in META-INF/services
6. Add build.gradle entry in settings.gradle
7. Test with local Keycloak instance

### 6.3 Git Workflow

**Commit Message Format:**
```
feat(claude-opus): Add XYZ feature
fix(claude-opus): Fix ABC bug
chore(claude-opus): Update dependency
```

**Pre-commit Hooks:**
```bash
make install-hooks
# Auto-formats code before commit
```

**Branch Strategy:**
- Feature: `feature/description`
- Bugfix: `bugfix/description`
- Release: `release/x.y.z`
- Main branch: `master`
- Development: `develop`

### 6.4 Testing Before Commit

```bash
# Full validation
make ci

# Or step-by-step
./gradlew ktlintFormat  # Auto-fix code style
./gradlew test          # Run unit tests
./gradlew build         # Full build
```

---

## 7. Troubleshooting Common Issues

### 7.1 Build Issues

**Issue:** Detekt fails with "Unsupported Kotlin version"
```
Status: KNOWN - Detekt 1.23.7 incompatible with Kotlin 2.2.21
Solution: Run build with: ./gradlew build -x detekt
Wait for: Detekt 2.0.0 stable release
```

**Issue:** Shadow JAR missing dependencies
```
Check: idps/build.gradle includes all dependencies in shadowJar block
Add: include(dependency('group:artifact:version'))
Rebuild: ./gradlew clean shadowJar
```

**Issue:** Keycloak can't find provider
```
Verify:
1. JAR in $KEYCLOAK_HOME/providers/
2. shadowJar built (includes dependencies)
3. Keycloak restarted after JAR placement
4. Check logs: grep "KakaoIdentityProviderFactory" keycloak.log
```

### 7.2 Kafka Event Listener Issues

**Issue:** Events not reaching Kafka
```
Check:
1. Kafka broker running: docker-compose ps kafka
2. Configuration in Realm Attributes
3. Topics exist: kafka-topics --list --bootstrap-server localhost:9092
4. Producer connectivity: docker-compose logs keycloak | grep Kafka
```

**Issue:** Kafka broker authentication errors
```
Configure:
- kafka.security.protocol (PLAINTEXT, SASL_SSL)
- kafka.sasl.mechanism (if SASL enabled)
- SSL certificate paths (if SASL_SSL)
```

### 7.3 OAuth2 Provider Issues

**Issue:** "Invalid redirect URI" from provider
```
Verify:
1. Exact match between provider app settings and Keycloak URL
2. Format: https://keycloak.example.com/realms/{realm}/broker/{provider}/endpoint
3. HTTPS used (some providers require HTTPS)
```

**Issue:** User attributes not imported
```
Check:
1. Provider returns data in response
2. User Attribute Mapper configuration
3. OAuth2 scopes include permission for attributes
4. Logs: grep "KakaoUserAttributeMapper" keycloak.log
```

---

## 8. Performance Optimization

### 8.1 Kafka Producer Tuning

**For High Throughput:**
```properties
kafka.batch.size=32768
kafka.linger.ms=10
kafka.compression.type=snappy
```

**For Low Latency:**
```properties
kafka.batch.size=0
kafka.linger.ms=0
kafka.acks=1
```

**For High Availability:**
```properties
kafka.acks=all
kafka.retries=10
kafka.compression.type=gzip
```

### 8.2 Event Filtering

**Reduce Event Volume:**
```
kafka.included.event.types=LOGIN,LOGOUT,REGISTER
# Send only critical events
```

**Disable Event Types:**
```
kafka.enable.admin.events=false
# Disable if not needed
```

### 8.3 Gradle Build Optimization

Already configured in `gradle.properties`:
```properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
org.gradle.workers.max=4
junit.jupiter.execution.parallel.enabled=true
```

---

## 9. Deployment Considerations

### 9.1 Production Deployment Checklist

- [ ] All providers have been tested against production Keycloak version
- [ ] Kafka cluster is HA-configured (replication factor ≥ 2)
- [ ] Event topics have retention policy configured
- [ ] Monitoring and alerting set up (Prometheus/Grafana)
- [ ] Database backups configured
- [ ] Security scanning passed (OWASP dependency check)
- [ ] All secrets (Client IDs, API keys) managed via Vault/Secrets Manager
- [ ] Load testing completed for event listener
- [ ] Disaster recovery procedure documented
- [ ] Rollback procedure tested

### 9.2 Container Deployment

**Dockerfile Customization:**
- Located: `docker/Dockerfile`
- JAR placement: `/opt/keycloak/providers/`
- Provider JARs mounted as Docker volume or COPY'd in base image

**Kubernetes Deployment:**
```yaml
# providers ConfigMap or InitContainer
# Place all *-all.jar files before Keycloak startup
```

---

## 10. Repository Structure Summary

```
sb-keycloak-exts/
├── README.md                           # Project overview
├── CHANGELOG.md                        # Version history
├── SECURITY.md                         # Security reporting
├── COMPATIBILITY_MATRIX.md             # Version compatibility
├── PROJECT-STATUS.md                   # Current status
├── Makefile                            # Convenient dev commands
│
├── gradle/
│   └── libs.versions.toml              # Centralized versions
│
├── build.gradle                        # Root configuration
├── settings.gradle                     # Module includes
├── gradle.properties                   # JVM/build settings
│
├── idps/                               # Identity Providers
│   ├── build.gradle                    # IdP defaults
│   ├── idp-kakao/
│   ├── idp-line/
│   ├── idp-naver/
│   ├── idp-google/
│   └── idp-github/
│
├── events/                             # Event Listeners
│   ├── build.gradle                    # Event listener defaults
│   └── event-listener-kafka/
│       ├── src/main/kotlin/
│       ├── src/test/kotlin/
│       ├── src/integrationTest/kotlin/ # TestContainers tests
│       └── README.md
│
├── .github/
│   └── workflows/
│       ├── ci.yml                      # Build/test/lint
│       ├── integration-tests.yml       # E2E tests
│       ├── publish-snapshot-jar.yml    # Snapshot publishing
│       ├── publish-release-jar.yml     # Release publishing
│       └── release.yml                 # Changelog automation
│
├── docker/
│   ├── Dockerfile                      # Keycloak image
│   ├── init-scripts/                   # Database setup
│   ├── monitoring/                     # Prometheus/Grafana
│   └── logging/                        # ELK stack
│
├── docker-compose.yml                  # Complete local stack
│
├── config/
│   ├── detekt/detekt.yml              # Static analysis config
│   └── dependency-check/               # CVE scanning config
│
├── docs/
│   ├── INSTALLATION.md                # Deployment guide
│   ├── CONFIGURATION.md               # Setup details
│   ├── OPERATIONS.md                  # Operational runbooks
│   └── TROUBLESHOOTING.md             # Common issues
│
└── tasks/                              # Task tracking
    ├── qa/                             # QA test plans
    ├── done/                           # Completed tasks
    └── plan/                           # Roadmap
```

---

## 11. Quick Reference

### Common Tasks

```bash
# Build everything
make build

# Run all quality checks
make check

# Setup local development
make dev-setup && docker-compose up -d

# Deploy to Keycloak
./gradlew shadowJar
cp idps/*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/

# Monitor Kafka events
docker-compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic keycloak.events
```

### Key Gradle Tasks

| Task | Purpose |
|------|---------|
| `build` | Compile, test, package all modules |
| `test` | Run unit tests |
| `integrationTest` | Run E2E tests (TestContainers) |
| `shadowJar` | Create fat JAR for deployment |
| `ktlintCheck` | Check code style |
| `ktlintFormat` | Auto-fix code style |
| `detekt` | Run static analysis |
| `dependencyCheckAnalyze` | CVE/vulnerability scan |
| `check` | Run all quality checks |

### Environment Files

| File | Purpose |
|------|---------|
| `gradle.properties` | JVM memory, Gradle parallelization |
| `gradle/libs.versions.toml` | Dependency versions |
| `config/detekt/detekt.yml` | Code analysis rules |
| `.github/workflows/ci.yml` | CI/CD pipeline |
| `docker-compose.yml` | Local development stack |

