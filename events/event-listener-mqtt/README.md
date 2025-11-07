# Keycloak MQTT Event Listener

MQTT event listener for Keycloak that publishes user and admin events to an MQTT broker.

## Features

- **Multiple MQTT Versions**: Supports MQTT 3.1.1 and MQTT 5.0
- **Configurable QoS**: Quality of Service levels 0, 1, 2
- **TLS/SSL Support**: Secure communication with client certificates
- **Automatic Reconnection**: Resilient connection management
- **Last Will and Testament**: Notify subscribers when Keycloak disconnects
- **Hierarchical Topics**: Structured topic names for easy filtering
- **Metrics Collection**: Built-in metrics with Prometheus export
- **Event Filtering**: Include specific event types

## Installation

### 1. Build Shadow JAR

```bash
./gradlew :events:event-listener-mqtt:shadowJar
```

### 2. Deploy to Keycloak

```bash
cp events/event-listener-mqtt/build/libs/keycloak-mqtt-event-listener-all.jar \
   $KEYCLOAK_HOME/providers/
```

### 3. Rebuild Keycloak

```bash
$KEYCLOAK_HOME/bin/kc.sh build
```

### 4. Configure Event Listener

Add the MQTT event listener to your realm:

**Admin Console** → **Realm Settings** → **Events** → **Event Listeners**

Select `mqtt` from the dropdown.

## Configuration

### Basic Configuration

Configure via **Realm Attributes** in Keycloak Admin Console:

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.broker.url` | MQTT broker URL | `tcp://localhost:1883` |
| `mqtt.client.id` | Unique client identifier | `keycloak-{uuid}` |
| `mqtt.username` | MQTT username (optional) | - |
| `mqtt.password` | MQTT password (optional) | - |

### MQTT Protocol Settings

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.mqtt.version` | MQTT version (3.1.1 or 5) | `3.1.1` |
| `mqtt.clean.session` | Clean session flag | `true` |
| `mqtt.automatic.reconnect` | Auto-reconnect on disconnect | `true` |
| `mqtt.connection.timeout.seconds` | Connection timeout | `30` |
| `mqtt.keep.alive.interval.seconds` | Keep-alive interval | `60` |
| `mqtt.max.inflight` | Max in-flight messages | `10` |

### Topic Configuration

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.topic.user.event` | User event base topic | `keycloak/events/user` |
| `mqtt.topic.admin.event` | Admin event base topic | `keycloak/events/admin` |
| `mqtt.topic.prefix` | Topic prefix (optional) | `` |

**Topic Structure**: `{prefix}/{base-topic}/{realm}/{event-type}`

**Example**:
- User login: `production/keycloak/events/user/master/LOGIN`
- Admin create: `production/keycloak/events/admin/master/CREATE`

### QoS and Reliability

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.qos` | Quality of Service (0, 1, 2) | `1` |
| `mqtt.retained` | Retain messages | `false` |

**QoS Levels**:
- **0**: At most once (fire and forget)
- **1**: At least once (acknowledged delivery)
- **2**: Exactly once (guaranteed delivery)

### TLS/SSL Configuration

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.use.tls` | Enable TLS/SSL | `false` |
| `mqtt.tls.ca.cert.path` | CA certificate path | - |
| `mqtt.tls.client.cert.path` | Client certificate path | - |
| `mqtt.tls.client.key.path` | Client private key path | - |

**Example**:
```
mqtt.broker.url = ssl://mqtt-broker.example.com:8883
mqtt.use.tls = true
mqtt.tls.ca.cert.path = /path/to/ca.crt
mqtt.tls.client.cert.path = /path/to/client.crt
mqtt.tls.client.key.path = /path/to/client.key
```

### Last Will and Testament

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.enable.last.will` | Enable Last Will | `false` |
| `mqtt.last.will.topic` | Last Will topic | - |
| `mqtt.last.will.message` | Last Will message (JSON) | - |
| `mqtt.last.will.qos` | Last Will QoS | `1` |
| `mqtt.last.will.retained` | Last Will retained flag | `true` |

**Example**:
```
mqtt.enable.last.will = true
mqtt.last.will.topic = keycloak/status/{clientId}
mqtt.last.will.message = {"status":"disconnected","timestamp":1234567890}
mqtt.last.will.qos = 1
mqtt.last.will.retained = true
```

### Event Filtering

| Attribute | Description | Default |
|-----------|-------------|---------|
| `mqtt.enable.user.events` | Enable user events | `true` |
| `mqtt.enable.admin.events` | Enable admin events | `true` |
| `mqtt.included.event.types` | Comma-separated event types | All types |

**Example**:
```
mqtt.included.event.types = LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE
```

## MQTT Broker Compatibility

Tested with:
- **Eclipse Mosquitto** 2.0+ (open source)
- **EMQ X** 5.0+ (high performance)
- **HiveMQ** 4.0+ (enterprise)
- **AWS IoT Core** (managed, MQTT 3.1.1)
- **Azure IoT Hub** (managed, MQTT 3.1.1 subset)

## Example Configurations

### Development (Local Mosquitto)

```properties
mqtt.broker.url = tcp://localhost:1883
mqtt.client.id = keycloak-dev
mqtt.qos = 1
mqtt.enable.user.events = true
mqtt.enable.admin.events = true
```

### Production (TLS with Authentication)

```properties
mqtt.broker.url = ssl://mqtt.example.com:8883
mqtt.client.id = keycloak-prod-01
mqtt.username = keycloak-service
mqtt.password = ${vault.mqtt_password}
mqtt.use.tls = true
mqtt.qos = 1
mqtt.retained = false
mqtt.topic.prefix = production
mqtt.automatic.reconnect = true
mqtt.connection.timeout.seconds = 30
mqtt.keep.alive.interval.seconds = 60
mqtt.enable.last.will = true
mqtt.last.will.topic = keycloak/status/keycloak-prod-01
mqtt.last.will.message = {"status":"disconnected"}
mqtt.included.event.types = LOGIN,LOGOUT,REGISTER,UPDATE_PROFILE
```

## Event Message Format

### User Event (JSON)

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "time": 1640995200000,
  "type": "LOGIN",
  "realmId": "master",
  "clientId": "account",
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "session-123",
  "ipAddress": "192.168.1.100",
  "details": {
    "username": "john.doe",
    "auth_method": "openid-connect"
  }
}
```

### Admin Event (JSON)

```json
{
  "id": "660f9511-f3ac-52e5-b827-557766551111",
  "time": 1640995200000,
  "operationType": "CREATE",
  "realmId": "master",
  "authDetails": {
    "realmId": "master",
    "clientId": "admin-cli",
    "userId": "admin-user-id",
    "ipAddress": "192.168.1.100"
  },
  "resourcePath": "users/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "representation": "{...}"
}
```

## Subscribing to Events

### Using Mosquitto Client

```bash
# Subscribe to all user events in all realms
mosquitto_sub -h localhost -t "keycloak/events/user/+/+"

# Subscribe to login events only
mosquitto_sub -h localhost -t "keycloak/events/user/+/LOGIN"

# Subscribe to all events in master realm
mosquitto_sub -h localhost -t "keycloak/events/+/master/+"

# With authentication
mosquitto_sub -h mqtt.example.com -p 8883 \
  -u keycloak-service -P password \
  --cafile ca.crt \
  -t "production/keycloak/events/#"
```

### Using Python (Paho MQTT)

```python
import paho.mqtt.client as mqtt
import json

def on_connect(client, userdata, flags, rc):
    print(f"Connected with result code {rc}")
    client.subscribe("keycloak/events/user/+/LOGIN")

def on_message(client, userdata, msg):
    event = json.loads(msg.payload.decode())
    print(f"User {event['userId']} logged in to {event['realmId']}")

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message

client.connect("localhost", 1883, 60)
client.loop_forever()
```

## Metrics

### Prometheus Metrics

Enable Prometheus metrics export:

```properties
mqtt.enable.prometheus = true
mqtt.prometheus.port = 9090
mqtt.enable.jvm.metrics = true
```

**Available Metrics**:
- `mqtt.events.sent.total{event_type, realm, qos}`
- `mqtt.events.failed.total{event_type, realm, error_type}`
- `mqtt.publish.latency.seconds{event_type}`
- `mqtt.connection.status{client_id}`
- `mqtt.reconnects.total`
- `mqtt.messages.by.qos{qos}`
- `mqtt.retained.messages.total`

### Metrics Endpoint

```bash
curl http://localhost:9090/metrics
```

## Troubleshooting

### Connection Issues

**Problem**: Failed to connect to MQTT broker

**Solutions**:
1. Check broker URL format: `tcp://host:port` or `ssl://host:port`
2. Verify broker is running: `mosquitto -v` or check broker logs
3. Test connection: `mosquitto_pub -h host -p port -t test -m "hello"`
4. Check firewall rules and network connectivity

### Authentication Failures

**Problem**: Connection refused with authentication error

**Solutions**:
1. Verify username and password are correct
2. Check broker ACLs (Access Control Lists)
3. For TLS: verify certificate paths and permissions
4. Check broker authentication logs

### Messages Not Published

**Problem**: Events not appearing on MQTT topics

**Solutions**:
1. Verify event listener is enabled in realm settings
2. Check event type filtering: `mqtt.included.event.types`
3. Subscribe with wildcard: `keycloak/events/#`
4. Check Keycloak logs for errors
5. Verify topic structure matches subscription

### Performance Issues

**Problem**: High latency or message loss

**Solutions**:
1. Increase `mqtt.max.inflight` for higher throughput
2. Use QoS 0 for non-critical events
3. Enable connection pooling (multiple client IDs)
4. Check broker capacity and performance
5. Monitor metrics for bottlenecks

## Security Recommendations

1. **Always use TLS in production** (`ssl://` URL)
2. **Enable client certificate authentication**
3. **Configure broker ACLs** to limit publish permissions
4. **Use strong passwords** or token-based authentication
5. **Rotate credentials regularly** via Keycloak vault
6. **Monitor connection attempts** for unauthorized access
7. **Use retained messages sparingly** to avoid stale data

## License

This project is licensed under the Apache License 2.0.

## Support

For issues and questions:
- GitHub Issues: https://github.com/scriptonbasestar/sb-keycloak-exts
- Documentation: See project README.md
