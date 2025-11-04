# Keycloak RabbitMQ Event Listener

RabbitMQ 기반의 Keycloak 이벤트 리스너 구현체입니다. Keycloak에서 발생하는 사용자 이벤트와 관리자 이벤트를 RabbitMQ로 전송합니다.

## 주요 기능

- **사용자 이벤트 전송**: 로그인, 로그아웃, 회원가입 등 사용자 관련 이벤트를 RabbitMQ로 전송
- **관리자 이벤트 전송**: Realm, Client, User 등의 관리 작업 이벤트를 RabbitMQ로 전송
- **유연한 라우팅**: Topic Exchange를 사용한 동적 라우팅 키 구성
- **연결 관리**: 자동 재연결 및 네트워크 복구 기능
- **메트릭 수집**: 이벤트 전송 성공/실패 통계 수집
- **Publisher Confirms**: 메시지 전송 확인 기능 (선택적)

## 아키텍처

```
Keycloak Events
      ↓
RabbitMQEventListenerProvider
      ↓
RabbitMQConnectionManager
      ↓
RabbitMQ Exchange → Queues
```

### 주요 컴포넌트

- **RabbitMQEventListenerProviderFactory**: Keycloak SPI 구현, 팩토리 패턴
- **RabbitMQEventListenerProvider**: 이벤트 처리 및 RabbitMQ 전송
- **RabbitMQConnectionManager**: RabbitMQ 연결 및 채널 관리
- **RabbitMQEventMetrics**: 이벤트 메트릭 수집 및 통계

## 설치

### 1. JAR 빌드

```bash
./gradlew :events:event-listener-rabbitmq:shadowJar
```

생성된 JAR 파일:
```
events/event-listener-rabbitmq/build/libs/keycloak-rabbitmq-event-listener.jar
```

### 2. Keycloak 배포

#### Docker 환경
```bash
# JAR 파일을 Keycloak providers 디렉토리로 복사
cp events/event-listener-rabbitmq/build/libs/keycloak-rabbitmq-event-listener.jar \
   /opt/keycloak/providers/
```

#### Standalone 환경
```bash
cp events/event-listener-rabbitmq/build/libs/keycloak-rabbitmq-event-listener.jar \
   $KEYCLOAK_HOME/providers/
```

### 3. Keycloak 재시작

```bash
# Docker
docker restart keycloak

# Standalone
$KEYCLOAK_HOME/bin/kc.sh start
```

## 설정

### Keycloak 설정 파일

`conf/keycloak.conf` 또는 `standalone.xml`에 다음 설정 추가:

```properties
# RabbitMQ Connection
spi-events-listener-rabbitmq-event-listener-host=localhost
spi-events-listener-rabbitmq-event-listener-port=5672
spi-events-listener-rabbitmq-event-listener-username=guest
spi-events-listener-rabbitmq-event-listener-password=guest
spi-events-listener-rabbitmq-event-listener-virtualHost=/

# Exchange Configuration
spi-events-listener-rabbitmq-event-listener-exchangeName=keycloak-events
spi-events-listener-rabbitmq-event-listener-exchangeType=topic
spi-events-listener-rabbitmq-event-listener-exchangeDurable=true

# Routing Keys
spi-events-listener-rabbitmq-event-listener-userEventRoutingKey=keycloak.events.user
spi-events-listener-rabbitmq-event-listener-adminEventRoutingKey=keycloak.events.admin

# Event Filtering
spi-events-listener-rabbitmq-event-listener-enableUserEvents=true
spi-events-listener-rabbitmq-event-listener-enableAdminEvents=true
spi-events-listener-rabbitmq-event-listener-includedEventTypes=LOGIN,LOGOUT,REGISTER

# Connection Settings
spi-events-listener-rabbitmq-event-listener-connectionTimeout=60000
spi-events-listener-rabbitmq-event-listener-requestedHeartbeat=60
spi-events-listener-rabbitmq-event-listener-networkRecoveryInterval=5000
spi-events-listener-rabbitmq-event-listener-automaticRecoveryEnabled=true

# Publisher Confirms (Optional)
spi-events-listener-rabbitmq-event-listener-publisherConfirms=false
spi-events-listener-rabbitmq-event-listener-publisherConfirmTimeout=5000
```

### 환경 변수 설정

```bash
export KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_HOST=rabbitmq.example.com
export KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_PORT=5672
export KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_USERNAME=keycloak
export KC_SPI_EVENTS_LISTENER_RABBITMQ_EVENT_LISTENER_PASSWORD=secret
```

### Realm 이벤트 설정

Keycloak Admin Console에서:

1. Realm Settings → Events
2. Event Listeners에 `rabbitmq-event-listener` 추가
3. Save Users Events: ON
4. Save Admin Events: ON

## 라우팅 키 구조

### 사용자 이벤트
```
{userEventRoutingKey}.{realmId}.{eventType}
예: keycloak.events.user.master.LOGIN
```

### 관리자 이벤트
```
{adminEventRoutingKey}.{realmId}.{operationType}
예: keycloak.events.admin.master.CREATE
```

## 메시지 포맷

### 사용자 이벤트 메시지
```json
{
  "id": "uuid",
  "time": 1234567890,
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "account",
  "userId": "user-uuid",
  "sessionId": "session-uuid",
  "ipAddress": "192.168.1.1",
  "details": {
    "username": "john.doe",
    "auth_method": "openid-connect"
  }
}
```

### 관리자 이벤트 메시지
```json
{
  "id": "uuid",
  "time": 1234567890,
  "operationType": "CREATE",
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "admin-uuid",
    "ipAddress": "192.168.1.1"
  },
  "resourcePath": "users/user-uuid",
  "representation": "{...}"
}
```

## 모니터링

### 메트릭 확인

메트릭은 로그를 통해 확인 가능:

```
RabbitMQ Event Metrics - Sent: 1234, Failed: 5, Bytes: 567890
```

### 연결 상태 확인

```bash
# RabbitMQ Management UI
http://localhost:15672

# CLI
rabbitmqctl list_connections
rabbitmqctl list_channels
```

## 트러블슈팅

### 연결 실패
```
원인: RabbitMQ 서버에 연결할 수 없음
해결: host, port, username, password 설정 확인
```

### 이벤트 누락
```
원인: includedEventTypes 필터링
해결: includedEventTypes 설정 확인 또는 비활성화
```

### 메시지 전송 실패
```
원인: Exchange나 권한 문제
해결: RabbitMQ에 Exchange가 생성되었는지, 사용자 권한 확인
```

## 성능 최적화

### Publisher Confirms 활성화
메시지 전송 확인이 필요한 경우:
```properties
spi-events-listener-rabbitmq-event-listener-publisherConfirms=true
```

### 연결 풀 최적화
```properties
spi-events-listener-rabbitmq-event-listener-connectionTimeout=30000
spi-events-listener-rabbitmq-event-listener-requestedHeartbeat=30
```

## 보안

### SSL/TLS 연결
```properties
spi-events-listener-rabbitmq-event-listener-useSsl=true
```

### 강력한 인증
- RabbitMQ 사용자별 권한 설정
- Virtual Host 분리
- 강력한 비밀번호 사용

## 라이선스

이 프로젝트는 Apache License 2.0 하에 배포됩니다.

## 관련 문서

- [Keycloak Event Listener SPI](https://www.keycloak.org/docs/latest/server_development/#_events)
- [RabbitMQ Java Client](https://www.rabbitmq.com/java-client.html)
- [Event Listener Common Module](../event-listener-common/README.md)
