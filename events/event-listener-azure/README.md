# Keycloak Azure Service Bus Event Listener

Azure Service Bus Queue/Topic 로 Keycloak 사용자/관리자 이벤트를 전송하는 확장 모듈입니다. `event-listener-common`이 제공하는 공통 직렬화, 회복력(resilience), 메트릭 계층 위에 Azure Service Bus 전용 어댑터를 얹어, 클라우드 네이티브한 인증과 전송 특성에 최적화되어 있습니다.

## Azure Service Bus 특성과 모듈 설계 배경
- **Queue/Topic 이중화**: Service Bus는 Queue(점대점)와 Topic(퍼블/서브)을 동시에 운용할 수 있습니다. 모듈은 두 경로를 모두 활성화할 수 있도록 설계되어, 운영 중 Queue → Topic 전환이나 병행 Fan-out이 가능합니다.
- **Managed Identity 우선 전략**: 클라우드 운영 시 비밀(Secret) 전달 부담을 줄이기 위해 Managed Identity 인증을 1순위로 지원하며, 동일 Keycloak 팩토리 내에서 자격 증명 조합별로 `AzureServiceBusSender`를 재사용해 연결을 최소화합니다.
- **Throttling 대비 회복력**: Azure Service Bus는 429/503 응답으로 속도 제한을 알립니다. `CircuitBreaker + RetryPolicy` 조합으로 일시 장애를 흡수하고, 실패한 이벤트는 공통 `DeadLetterQueue`로 적재해 재처리 흐름을 유지합니다.
- **메시지 속성 기반 라우팅**: 이벤트 JSON 외에 `eventType`, `realmId`, `userId` 등 application properties를 세팅해 Service Bus Subscription Rule, Logic Apps 조건 분기를 바로 적용할 수 있습니다.
- **배치 전송 옵션**: 고처리량 환경에 대비해 `BatchProcessor`를 활성화하면 동일 자격의 이벤트를 묶어 전송합니다. Service Bus SDK가 멱등성을 책임지므로 모듈은 순서 보존 대신 안정적인 전송에 초점을 맞춥니다.

## 처리 흐름
1. Keycloak에서 `Event` 또는 `AdminEvent`가 발생하면 Provider가 공통 모델(`KeycloakEvent`, `KeycloakAdminEvent`)로 직렬화합니다.
2. 모듈은 Realm Attribute → System Property 순으로 설정을 읽어 Queue/Topic 목적지를 결정하고, 포함시킬 이벤트 타입을 필터링합니다.
3. 전송 전 `CircuitBreaker`가 열린 상태인지 검사하고, 닫혀 있다면 `RetryPolicy`에 따라 Service Bus 전송을 재시도합니다.
4. 전송 중 예외가 발생하면 이벤트를 `DeadLetterQueue`에 저장하고, Prometheus 메트릭으로 실패 건을 기록합니다.
5. 성공 시 Azure 전용 메트릭(`AzureEventMetrics`)이 처리량, 목적지 유형, 연결 상태를 누적해 Grafana, Alert 룰과 연동할 수 있도록 합니다.

## 주요 기능 요약
- **전송 경로 선택**: `azure.use.queue`와 `azure.use.topic`을 조합해 Queue, Topic, 혹은 이중화 구성이 가능합니다. 사용자/관리자 이벤트에 서로 다른 엔드포인트를 부여할 수도 있습니다.
- **고신뢰 전송**: 재시도/회로 차단/잠재적 배치 전송 기능을 공통 모듈에서 그대로 가져와 Service Bus 특유의 잠깐의 네트워크 이상/Throttle을 견딜 수 있습니다.
- **관찰성**: Prometheus 노출 포트를 모듈 팩토리 단위로 열어 `azure_event_sent_total`, `azure_connection_up` 등 지표를 확인합니다. 메트릭이 비활성화 되어도 로그 기반 요약이 남습니다.
- **구성 단순화**: Keycloak Realm Attribute, `standalone.xml`, JVM 시스템 속성 중 편한 채널을 골라 동일 키로 설정할 수 있습니다. Realm 단위 상이한 Namespace나 Queue 이름을 지정할 때 유용합니다.

## 사용 사례
- Azure Functions, Logic Apps, Event Grid 등 Service Bus 기반 다운스트림과 연계하는 인증/가입/관리 이벤트 파이프라인.
- 운영 팀이 Queue/Topic을 혼합해 DLQ, 속도 제한, Subscription Rule을 직접 제어하고 싶은 엔터프라이즈 Keycloak 배포.
- 규제 준수 환경에서 Managed Identity를 활용해 비밀 관리 비용을 절감하고 싶은 경우.

## 메시지 구조
- **Payload**: `KeycloakEvent`, `KeycloakAdminEvent` JSON. 공통 스키마로 모든 모듈과 동일하게 유지됩니다.
- **Application Properties**
  - `eventType`: 사용자 이벤트 타입 혹은 `ADMIN_<OPERATION>`
  - `realmId`, `userId`: 다운스트림 필터링과 감사 추적을 위한 기본 키
  - 추가 정보가 필요하면 DLQ에 기록된 메타데이터를 참고하거나, 공통 모듈에서 커스터마이징 가능합니다.

## 의존성
- **Azure Service Bus**: Standard 계층 이상(Topic/Subscription, DLQ 기능 사용 시 필수)
- **Azure Identity**: Managed Identity(Default) 또는 서비스 주체
- **Keycloak**: 26.0.7 (해당 버전의 SPI에 맞춰 빌드)

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

> Managed Identity를 사용할 때는 Keycloak이 실행되는 VM/컨테이너가 Service Bus에 대해 `Azure Service Bus Data Sender` 역할을 가져야 하며, Queue/Topic이 미리 준비되어 있어야 합니다.

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

## 운영 팁
- **Namespace 분리**: 여러 Realm이 하나의 Keycloak 인스턴스를 공유한다면 Realm Attribute로 Queue/Topic 이름을 분기해 편성합니다.
- **Circuit Breaker 튜닝**: Azure의 기본 단위는 초당 요청 수를 기준으로 Throttle이 걸립니다. `circuitBreakerFailureThreshold`를 3~5 사이에서 조정하여 빠르게 열고, `retryInitialDelayMs`를 100ms 이상으로 설정하면 429 재시도에 유리합니다.
- **배치 모드 활용**: 초당 수백 건 이상의 로그인 이벤트가 발생하는 환경에서는 `enableBatching=true`, `batchSize=100`, `batchFlushIntervalMs=1000` 정도의 값으로 throughput을 늘릴 수 있습니다. 배치 처리는 목적지별로 그룹화되어 순서를 보장하지 않으므로, 순서가 중요한 경우 비활성화해야 합니다.
- **DLQ 모니터링**: 공통 Dead Letter Queue는 파일 또는 메모리로 적재합니다. `events/examples/dlq-reprocess.sh`를 활용해 실패 이벤트를 재전송할 수 있으므로 주기적으로 확인하세요.
- **메트릭/로그 수집**: Prometheus를 비활성화하더라도 로그에 요약치가 남습니다(`AzureEventMetrics.logMetricsSummary`). 운영 중 지표 이상을 감지하면 `sender.isHealthy()` 상태를 확인해 네트워크/권한 문제를 진단합니다.

## 배포
```bash
./gradlew :events:event-listener-azure:productionBuild
```

생성된 `keycloak-azure-event-listener-<version>.jar` 파일과 체크섬을 Keycloak `providers/` 디렉터리에 배치하면 됩니다.
