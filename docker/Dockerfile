# Keycloak with Kafka Event Listener
# Based on official Keycloak image with custom event listener plugin

FROM quay.io/keycloak/keycloak:26.3.1

# Metadata
LABEL name="keycloak-kafka-event-listener" \
      description="Keycloak with Kafka Event Listener plugin" \
      version="0.0.2-SNAPSHOT" \
      maintainer="ScriptonBaseStar" \
      org.opencontainers.image.source="https://github.com/scriptonbasestar/sb-keycloak-exts"

# Switch to root for package installation
USER root

# Install required packages for debugging and monitoring
RUN microdnf update -y && \
    microdnf install -y \
    curl \
    wget \
    jq \
    procps \
    && microdnf clean all

# Create necessary directories
RUN mkdir -p /opt/keycloak/providers/backup \
    && mkdir -p /opt/keycloak/conf/kafka \
    && mkdir -p /opt/keycloak/data/kafka-events

# Copy the Kafka Event Listener plugin
COPY events/event-listener-kafka/build/libs/keycloak-kafka-event-listener-*.jar /opt/keycloak/providers/

# Copy configuration templates
COPY docker/config/keycloak.conf.template /opt/keycloak/conf/
COPY docker/config/kafka-config.properties.template /opt/keycloak/conf/kafka/

# Copy health check script
COPY docker/scripts/health-check.sh /opt/keycloak/bin/
COPY docker/scripts/entrypoint.sh /opt/keycloak/bin/

# Set correct permissions
RUN chown -R keycloak:keycloak /opt/keycloak && \
    chmod +x /opt/keycloak/bin/health-check.sh && \
    chmod +x /opt/keycloak/bin/entrypoint.sh

# Switch back to keycloak user
USER keycloak

# Environment variables for configuration
ENV KEYCLOAK_ADMIN=admin \
    KEYCLOAK_ADMIN_PASSWORD=admin \
    KC_DB=dev-file \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_PROXY=edge \
    KC_HOSTNAME_STRICT=false \
    KC_HOSTNAME_STRICT_HTTPS=false \
    KC_HTTP_ENABLED=true \
    KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
    KAFKA_USER_EVENTS_TOPIC=keycloak.user.events \
    KAFKA_ADMIN_EVENTS_TOPIC=keycloak.admin.events \
    KAFKA_USER_EVENTS_ENABLED=true \
    KAFKA_ADMIN_EVENTS_ENABLED=true \
    KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
    KAFKA_MAX_POLL_RECORDS=500 \
    KAFKA_BATCH_SIZE=16384 \
    KAFKA_LINGER_MS=5 \
    KAFKA_BUFFER_MEMORY=33554432 \
    KAFKA_RETRIES=3 \
    KAFKA_ACKS=1 \
    JVM_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD /opt/keycloak/bin/health-check.sh

# Expose ports
EXPOSE 8080 8443 9000

# Use custom entrypoint for configuration
ENTRYPOINT ["/opt/keycloak/bin/entrypoint.sh"]

# Default command
CMD ["start"]