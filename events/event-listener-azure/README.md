# Keycloak Azure Service Bus Event Listener

Azure Service Bus 기반 Keycloak 이벤트 리스너 구현체입니다.

## 특징
- **다중 프로토콜 지원**: Queue, Topic 기반 메시지 발행
- **Resilience 패턴**: Circuit Breaker, Retry Policy, Dead Letter Queue, Batch Processing
- **관찰성 강화**: Prometheus 메트릭 및 연결 상태 모니터링
- **유연한 인증**: 연결 문자열/Managed Identity 모두 지원
- **일관된 이벤트 스키마**: 공통 모듈을 활용한 Keycloak 이벤트 직렬화

## 사용 사례
- Azure Service Bus 를 사용하는 엔터프라이즈 환경
- Keycloak 이벤트를 마이크로서비스로 전달
- 운영 환경에서 고가용성과 모니터링이 필요한 경우
- Azure Functions, Logic Apps 등과 연동한 자동화 파이프라인

## 의존성
- **Azure Service Bus**: Standard 이상
- **Azure Identity**: Managed Identity 또는 서비스 주체
- **Keycloak**: 26.0.7

## 설정

### Keycloak 설정 (standalone.xml)
```xml
<spi name="eventsListener">
    <provider name="azure-event-listener" enabled="true">
        <properties>
            <!-- Service Bus 연결 -->
            <property name="azure.use.queue" value="true"/>
            <property name="azure.queue.user.events" value="keycloak-user-events"/>
            <property name="azure.queue.admin.events" value="keycloak-admin-events"/>

            <property name="azure.use.topic" value="false"/>
            <property name="azure.topic.user.events" value="keycloak-user-events"/>
            <property name="azure.topic.admin.events" value="keycloak-admin-events"/>

            <!-- 인증 -->
            <property name="azure.use.managed.identity" value="false"/>
            <property name="azure.servicebus.connection.string" value="Endpoint=sb://..."/>
            <property name="azure.servicebus.namespace" value="your-namespace.servicebus.windows.net"/>
            <property name="azure.managed.identity.client.id" value="00000000-0000-0000-0000-000000000000"/>

            <!-- 이벤트 필터링 -->
            <property name="azure.enable.user.events" value="true"/>
            <property name="azure.enable.admin.events" value="true"/>
            <property name="azure.included.event.types" value="LOGIN,LOGOUT,REGISTER"/>

            <!-- Resilience -->
            <property name="circuitBreakerFailureThreshold" value="5"/>
            <property name="circuitBreakerOpenTimeoutSeconds" value="60"/>
            <property name="maxRetryAttempts" value="3"/>
            <property name="retryInitialDelayMs" value="100"/>
            <property name="retryMaxDelayMs" value="10000"/>

            <!-- Dead Letter Queue -->
            <property name="dlqMaxSize" value="10000"/>
            <property name="dlqPersistToFile" value="false"/>
            <property name="dlqPath" value="./dlq/azure"/>

            <!-- Batch Processing -->
            <property name="enableBatching" value="false"/>
            <property name="batchSize" value="100"/>
            <property name="batchFlushIntervalMs" value="5000"/>

            <!-- Prometheus Metrics -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9094"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

### 환경 변수 예시
```bash
# Service Bus 연결 문자열 방식
-Dazure.servicebus.connection.string="Endpoint=sb://..."

# Managed Identity 방식
-Dazure.use.managed.identity=true
-Dazure.servicebus.namespace=your-namespace.servicebus.windows.net
-Dazure.managed.identity.client.id=<user-assigned-id>

# 이벤트 필터링
-Dazure.enable.user.events=true
-Dazure.enable.admin.events=true
-Dazure.included.event.types=LOGIN,LOGOUT,REGISTER
```

## 배포
```bash
./gradlew :events:event-listener-azure:productionBuild
```

생성된 `keycloak-azure-event-listener-<version>.jar` 파일과 체크섬을 Keycloak `providers/` 디렉터리에 배치하면 됩니다.
