# NATS Event Listener for Keycloak

Keycloak Event Listener that publishes events to NATS messaging system with subject-based routing.

## Features

- ✅ **User Events**: LOGIN, LOGOUT, REGISTER 등 모든 Keycloak 사용자 이벤트
- ✅ **Admin Events**: 사용자 생성, 설정 변경 등 관리자 이벤트
- ✅ **Subject-based Routing**: `{base}.{realm}.{eventType}` 형식의 subject 자동 생성
- ✅ **Event Filtering**: 이벤트 타입별 필터링 지원
- ✅ **Metrics Collection**: 성공/실패/지연시간 메트릭 수집
- ✅ **Automatic Reconnection**: 연결 끊김 시 자동 재연결
- ✅ **TLS Support**: 보안 연결 지원
- ✅ **Token/Username Authentication**: 다양한 인증 방식 지원

## Architecture

```
┌─────────────┐
│  Keycloak   │
│   Events    │
└──────┬──────┘
       │
       v
┌──────────────────┐
│ NATS Event       │
│ Listener         │
│ Provider         │
└────────┬─────────┘
         │
         v
┌────────────────────┐     ┌─────────────┐
│ NATS Connection    │────>│ NATS Server │
│ Manager            │     └─────────────┘
└────────────────────┘
         │
         v
┌────────────────────┐
│ Event Metrics      │
└────────────────────┘
```

## Subject Format

### User Events
```
{userEventSubject}.{realmId}.{eventType}
```

Example:
- `keycloak.events.user.master.LOGIN`
- `keycloak.events.user.myrealm.LOGOUT`
- `keycloak.events.user.prod.REGISTER`

### Admin Events
```
{adminEventSubject}.{realmId}.{operationType}
```

Example:
- `keycloak.events.admin.master.CREATE`
- `keycloak.events.admin.myrealm.UPDATE`
- `keycloak.events.admin.prod.DELETE`

## Configuration

### Keycloak Configuration (standalone.xml)

```xml
<spi name="eventsListener">
    <provider name="nats-event-listener" enabled="true">
        <properties>
            <property name="serverUrl" value="nats://localhost:4222"/>
            <property name="userEventSubject" value="keycloak.events.user"/>
            <property name="adminEventSubject" value="keycloak.events.admin"/>
            <property name="enableUserEvents" value="true"/>
            <property name="enableAdminEvents" value="true"/>
        </properties>
    </provider>
</spi>
```

### Configuration with Authentication

#### Token Authentication
```xml
<property name="serverUrl" value="nats://prod-server:4222"/>
<property name="token" value="your-auth-token"/>
<property name="useTls" value="true"/>
```

#### Username/Password Authentication
```xml
<property name="serverUrl" value="nats://prod-server:4222"/>
<property name="username" value="keycloak"/>
<property name="password" value="secret"/>
```

### Event Filtering
```xml
<property name="enableUserEvents" value="true"/>
<property name="enableAdminEvents" value="false"/>
<property name="includedEventTypes" value="LOGIN,LOGOUT,REGISTER"/>
```

### Connection Settings
```xml
<property name="connectionTimeout" value="30000"/>
<property name="maxReconnects" value="60"/>
<property name="reconnectWait" value="2000"/>
<property name="maxPingsOut" value="2"/>
```

## All Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `serverUrl` | `nats://localhost:4222` | NATS server URL |
| `username` | `null` | Username for authentication |
| `password` | `null` | Password for authentication |
| `token` | `null` | Token for authentication |
| `useTls` | `false` | Enable TLS connection |
| `userEventSubject` | `keycloak.events.user` | Base subject for user events |
| `adminEventSubject` | `keycloak.events.admin` | Base subject for admin events |
| `enableUserEvents` | `true` | Enable user event publishing |
| `enableAdminEvents` | `true` | Enable admin event publishing |
| `includedEventTypes` | `` (all) | Comma-separated list of event types to include |
| `connectionTimeout` | `60000` | Connection timeout (ms) |
| `maxReconnects` | `60` | Maximum reconnection attempts |
| `reconnectWait` | `2000` | Wait time between reconnections (ms) |
| `noEcho` | `false` | Disable echo for published messages |
| `maxPingsOut` | `2` | Maximum outstanding pings |

## Event Message Format

### User Event JSON
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "time": 1699012345678,
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "account",
  "userId": "user-123",
  "sessionId": "session-456",
  "ipAddress": "192.168.1.100",
  "details": {
    "username": "john.doe",
    "auth_method": "openid-connect"
  }
}
```

### Admin Event JSON
```json
{
  "id": "650e8400-e29b-41d4-a716-446655440000",
  "time": 1699012345678,
  "operationType": "CREATE",
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "admin-123",
    "ipAddress": "192.168.1.1"
  },
  "resourcePath": "users/user-456",
  "representation": "{\"username\":\"new.user\",\"email\":\"new.user@example.com\"}"
}
```

## NATS Consumer Example

### Go Consumer
```go
package main

import (
    "encoding/json"
    "log"
    "github.com/nats-io/nats.go"
)

type KeycloakEvent struct {
    ID        string            `json:"id"`
    Time      int64             `json:"time"`
    Type      string            `json:"type"`
    RealmID   string            `json:"realmId"`
    UserID    string            `json:"userId"`
    Details   map[string]string `json:"details"`
}

func main() {
    nc, _ := nats.Connect(nats.DefaultURL)
    defer nc.Close()

    // Subscribe to all user events in all realms
    nc.Subscribe("keycloak.events.user.>", func(m *nats.Msg) {
        var event KeycloakEvent
        json.Unmarshal(m.Data, &event)
        log.Printf("Received event: %s from user %s", event.Type, event.UserID)
    })

    // Subscribe only to LOGIN events in master realm
    nc.Subscribe("keycloak.events.user.master.LOGIN", func(m *nats.Msg) {
        var event KeycloakEvent
        json.Unmarshal(m.Data, &event)
        log.Printf("User logged in: %s", event.UserID)
    })

    select {}
}
```

### Node.js Consumer
```javascript
const NATS = require('nats');

const nc = await NATS.connect({ servers: 'nats://localhost:4222' });

// Subscribe to all user events
const sub = nc.subscribe('keycloak.events.user.>');
for await (const m of sub) {
  const event = JSON.parse(m.string());
  console.log(`Event: ${event.type}, User: ${event.userId}`);
}
```

## Metrics

메트릭은 자동으로 수집되며 다음 정보를 포함합니다:

- **totalSent**: 성공적으로 전송된 이벤트 수
- **totalFailed**: 실패한 이벤트 수
- **avgLatencyMs**: 평균 처리 지연시간 (밀리초)
- **errorsByType**: 에러 타입별 카운트
- **eventsByType**: 이벤트 타입별 카운트

## Deployment

### JAR 배포

1. 빌드:
```bash
./gradlew :events:event-listener-nats:build
```

2. JAR 파일 복사:
```bash
cp events/event-listener-nats/build/libs/event-listener-nats-*.jar \
   $KEYCLOAK_HOME/providers/
```

3. Keycloak 재시작

### Docker 배포

Dockerfile:
```dockerfile
FROM quay.io/keycloak/keycloak:26.0.7

COPY events/event-listener-nats/build/libs/event-listener-nats-*.jar \
     /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build
```

## Testing

### Unit Tests
```bash
./gradlew :events:event-listener-nats:test
```

### NATS Server로 통합 테스트

1. NATS 서버 시작:
```bash
docker run -p 4222:4222 nats:latest
```

2. Keycloak 설정 및 실행

3. 이벤트 구독 확인:
```bash
nats sub "keycloak.events.user.>" -s nats://localhost:4222
```

4. Keycloak에서 로그인/로그아웃 테스트

## Troubleshooting

### 연결 실패
```
ERROR: Failed to connect to NATS server
```
**해결**: NATS 서버 URL과 포트 확인

### 이벤트가 전송되지 않음
1. Keycloak Admin Console > Events > Config 확인
2. Event Listeners에 `nats-event-listener` 추가 확인
3. Save Events ON 확인

### Subject 구독 안 됨
- Wildcard 사용: `keycloak.events.user.>` (모든 realm, 모든 이벤트)
- 특정 realm: `keycloak.events.user.master.>`
- 특정 이벤트: `keycloak.events.user.*.LOGIN`

## Comparison with Other Messaging Systems

| Feature | NATS | Kafka | RabbitMQ |
|---------|------|-------|----------|
| **Subject/Topic Routing** | ✅ Built-in | ⚠️ Topic-based | ⚠️ Routing keys |
| **Wildcard Subscribe** | ✅ Native | ❌ No | ⚠️ Limited |
| **Setup Complexity** | ✅ Simple | ❌ Complex | ⚠️ Medium |
| **Performance** | ✅ High | ✅ High | ⚠️ Medium |
| **Message Persistence** | ⚠️ JetStream | ✅ Built-in | ✅ Built-in |
| **Lightweight** | ✅ Yes | ❌ No | ⚠️ Medium |

## Best Practices

1. **Use Subject Wildcards**: `keycloak.events.user.>` for flexible subscriptions
2. **Filter at Keycloak Level**: Use `includedEventTypes` to reduce traffic
3. **Monitor Metrics**: Check `totalFailed` for connection issues
4. **Use TLS in Production**: Always enable `useTls=true` for production
5. **Configure Reconnection**: Adjust `maxReconnects` and `reconnectWait` based on network

## License

Apache License 2.0
