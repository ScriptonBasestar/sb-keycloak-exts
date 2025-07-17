# Keycloak Kafka Event Listener - Compatibility Matrix

## Supported Versions

### Keycloak Compatibility

| Event Listener Version | Keycloak Version | Java Version | Status | Notes |
|----------------------|------------------|--------------|--------|-------|
| 0.0.2-SNAPSHOT       | 26.3.1          | 21+          | ✅ Tested | Current development version |
| 0.0.2-SNAPSHOT       | 25.x.x          | 21+          | ⚠️ Compatible | Should work but not tested |
| 0.0.2-SNAPSHOT       | 24.x.x          | 17+          | ⚠️ Compatible | May require Java 17 |
| 0.0.2-SNAPSHOT       | 23.x.x          | 17+          | ❌ Not Supported | Breaking changes in SPI |

### Kafka Compatibility

| Event Listener Version | Kafka Client Version | Kafka Server Version | Status |
|----------------------|---------------------|---------------------|--------|
| 0.0.2-SNAPSHOT       | 3.8.1              | 3.8.x               | ✅ Tested |
| 0.0.2-SNAPSHOT       | 3.8.1              | 3.7.x               | ✅ Compatible |
| 0.0.2-SNAPSHOT       | 3.8.1              | 3.6.x               | ✅ Compatible |
| 0.0.2-SNAPSHOT       | 3.8.1              | 2.8.x               | ⚠️ Limited |

### JVM Compatibility

| Java Version | Support Status | Notes |
|-------------|---------------|-------|
| OpenJDK 21  | ✅ Recommended | Primary development and testing platform |
| OpenJDK 17  | ✅ Supported | Minimum required version |
| OpenJDK 11  | ❌ Not Supported | EOL, security issues |
| Oracle JDK  | ✅ Compatible | Commercial license required |

## Installation Requirements

### Prerequisites

1. **Keycloak Server**: Version 24.0+ with admin privileges
2. **Java Runtime**: OpenJDK 17+ or Oracle JDK 17+
3. **Kafka Cluster**: Version 2.8+ (3.6+ recommended)
4. **Network Access**: Keycloak → Kafka connectivity

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| RAM | 512MB | 1GB+ |
| CPU | 1 core | 2+ cores |
| Disk | 100MB | 500MB+ |
| Network | 1Mbps | 10Mbps+ |

## Feature Compatibility

### Event Types

| Feature | Keycloak 24.x | Keycloak 25.x | Keycloak 26.x |
|---------|---------------|---------------|---------------|
| User Events | ✅ | ✅ | ✅ |
| Admin Events | ✅ | ✅ | ✅ |
| Custom Events | ⚠️ | ✅ | ✅ |
| Error Events | ✅ | ✅ | ✅ |

### Configuration Options

| Configuration | Support Level | Notes |
|---------------|---------------|-------|
| Topic Mapping | Full | All versions |
| Event Filtering | Full | All versions |
| SSL/TLS | Full | Kafka 2.8+ |
| SASL Authentication | Full | Kafka 2.8+ |
| Dead Letter Queue | Partial | Future enhancement |

## Upgrade Path

### From Previous Versions

```bash
# Check current version
ls $KEYCLOAK_HOME/providers/

# Backup existing configuration
cp $KEYCLOAK_HOME/conf/keycloak.conf keycloak.conf.backup

# Remove old version
rm $KEYCLOAK_HOME/providers/keycloak-kafka-event-listener-*.jar

# Install new version
cp keycloak-kafka-event-listener-0.0.2-SNAPSHOT.jar $KEYCLOAK_HOME/providers/

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

### Configuration Migration

| Version Change | Required Actions |
|----------------|------------------|
| 0.0.1 → 0.0.2 | Update JAR file only |
| Major versions | Review configuration changes |

## Testing Matrix

### Automated Testing

| Test Suite | Keycloak 24 | Keycloak 25 | Keycloak 26 |
|------------|-------------|-------------|-------------|
| Unit Tests | ✅ | ✅ | ✅ |
| Integration Tests | ⚠️ | ⚠️ | ✅ |
| Performance Tests | ❌ | ❌ | ✅ |
| Security Tests | ❌ | ❌ | ✅ |

### Manual Testing

- ✅ User login/logout events
- ✅ Admin operations
- ✅ Error handling
- ⚠️ High-load scenarios
- ❌ Multi-realm environments

## Known Issues

### Current Limitations

1. **Dead Letter Queue**: Not yet implemented
2. **Batch Processing**: Limited batch size support
3. **Monitoring**: Basic metrics only
4. **Multi-tenancy**: Single realm focus

### Workarounds

| Issue | Workaround | Timeline |
|-------|------------|----------|
| Large event batches | Increase Kafka buffer size | v0.1.0 |
| Connection failures | Manual restart required | v0.1.0 |
| Memory leaks | Monitor heap usage | v0.1.0 |

## Support Policy

### Version Support

- **Current Version**: Full support with updates
- **Previous Version**: Security fixes only
- **Legacy Versions**: Community support only

### Update Schedule

- **Patch Releases**: Monthly
- **Minor Releases**: Quarterly
- **Major Releases**: Annually

## Getting Help

### Documentation

- [Installation Guide](docs/INSTALLATION.md)
- [Configuration Reference](docs/CONFIGURATION.md)
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md)

### Support Channels

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Community support
- **Documentation**: FAQ and guides

---

*Last updated: 2025-07-17*  
*Version: 0.0.2-SNAPSHOT*