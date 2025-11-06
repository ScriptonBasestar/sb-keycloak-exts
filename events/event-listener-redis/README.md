# Keycloak Redis Streams Event Listener

Redis Streams 기반 Keycloak 이벤트 리스너 구현체입니다.

## 특징

- **Redis Streams 통합**: Lettuce 클라이언트를 사용한 고성능 스트림 프로듀서
- **Resilience Patterns**: Circuit Breaker, Retry Policy, DLQ, Batch Processing 지원
- **Prometheus 메트릭**: 실시간 모니터링 및 관찰성
- **경량 구조**: 기존 Redis 인프라 활용 가능
- **실시간 이벤트**: 빠른 이벤트 전파 및 처리

## 사용 사례

- 기존 Redis를 세션 저장소로 사용하는 환경
- 경량 이벤트 스트리밍 필요
- 실시간 이벤트 동기화
- 개발/테스트 환경 (Kafka 없이)
- 마이크로서비스 간 빠른 이벤트 전파

## 의존성

- **Lettuce**: 6.5.2.RELEASE
- **Redis**: 6.0+
- **Keycloak**: 26.0.7

## 설정

### Keycloak 설정 (standalone.xml)

```xml
<spi name="eventsListener">
    <provider name="redis-event-listener" enabled="true">
        <properties>
            <property name="redisUri" value="redis://localhost:6379"/>
            <property name="userEventsStream" value="keycloak:events:user"/>
            <property name="adminEventsStream" value="keycloak:events:admin"/>
            <property name="streamMaxLength" value="10000"/>

            <!-- Resilience Patterns -->
            <property name="enableCircuitBreaker" value="true"/>
            <property name="circuitBreakerFailureThreshold" value="5"/>
            <property name="circuitBreakerOpenTimeoutSeconds" value="60"/>

            <property name="enableRetry" value="true"/>
            <property name="maxRetryAttempts" value="3"/>
            <property name="retryInitialDelayMs" value="100"/>
            <property name="retryMaxDelayMs" value="10000"/>

            <property name="enableDeadLetterQueue" value="true"/>
            <property name="dlqMaxSize" value="10000"/>
            <property name="dlqPersistToFile" value="false"/>
            <property name="dlqPath" value="./dlq/redis"/>

            <!-- Batch Processing -->
            <property name="enableBatching" value="false"/>
            <property name="batchSize" value="100"/>
            <property name="batchFlushIntervalMs" value="5000"/>

            <!-- Prometheus Metrics -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9096"/>
            <property name="enableJvmMetrics" value="true"/>
        </properties>
    </provider>
</spi>
```

> ℹ️ `redis.*` 키는 Realm Attribute → SPI Config Scope(`standalone.xml`, `keycloak.conf`) → JVM System Property 순으로 조회되므로 동일한 키를 원하는 환경 레벨에서 덮어쓸 수 있습니다.

### 환경 변수

```bash
# Redis 연결
-Dredis.uri=redis://localhost:6379
-Dredis.user.events.stream=keycloak:events:user
-Dredis.admin.events.stream=keycloak:events:admin
-Dredis.stream.max.length=10000

# 이벤트 필터링
-Dredis.enable.user.events=true
-Dredis.enable.admin.events=true
-Dredis.included.event.types=LOGIN,LOGOUT,REGISTER
```

## Redis Streams 소비 예제

### Python (redis-py)

```python
import redis
import json

r = redis.Redis(host='localhost', port=6379, decode_responses=True)

# 사용자 이벤트 소비
while True:
    messages = r.xread({'keycloak:events:user': '$'}, block=1000, count=10)
    for stream, entries in messages:
        for entry_id, fields in entries:
            event = json.loads(fields['data'])
            print(f"Event: {event['type']}, User: {event.get('userId')}")
```

### Node.js (ioredis)

```javascript
const Redis = require('ioredis');
const redis = new Redis();

async function consumeEvents() {
  let lastId = '$';

  while (true) {
    const results = await redis.xread('BLOCK', 1000, 'COUNT', 10, 'STREAMS', 'keycloak:events:user', lastId);

    if (results) {
      for (const [stream, messages] of results) {
        for (const [id, fields] of messages) {
          const event = JSON.parse(fields[fields.indexOf('data') + 1]);
          console.log(`Event: ${event.type}, User: ${event.userId}`);
          lastId = id;
        }
      }
    }
  }
}

consumeEvents();
```

### Go (go-redis)

```go
package main

import (
    "context"
    "encoding/json"
    "github.com/go-redis/redis/v8"
    "log"
)

func main() {
    ctx := context.Background()
    rdb := redis.NewClient(&redis.Options{
        Addr: "localhost:6379",
    })

    lastID := "$"
    for {
        streams, err := rdb.XRead(ctx, &redis.XReadArgs{
            Streams: []string{"keycloak:events:user", lastID},
            Count:   10,
            Block:   1000,
        }).Result()

        if err != nil {
            continue
        }

        for _, stream := range streams {
            for _, message := range stream.Messages {
                var event map[string]interface{}
                json.Unmarshal([]byte(message.Values["data"].(string)), &event)
                log.Printf("Event: %s, User: %v", event["type"], event["userId"])
                lastID = message.ID
            }
        }
    }
}
```

## 메트릭

Prometheus 메트릭은 `/metrics` 엔드포인트(포트: 9092)에서 확인 가능:

```
keycloak_events_total{listener="redis",type="LOGIN",realm="master"} 100
keycloak_events_failed_total{listener="redis",error="ConnectionTimeout"} 5
keycloak_circuit_breaker_state{listener="redis"} 0.0
keycloak_dlq_size{listener="redis"} 0
```

## 빌드

```bash
./gradlew :events:event-listener-redis:build
```

## 배포

```bash
cp events/event-listener-redis/build/libs/keycloak-redis-event-listener.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

## Redis Streams vs Kafka 비교

| 특징 | Redis Streams | Kafka |
|------|--------------|-------|
| **지연 시간** | < 1ms | ~5ms |
| **처리량** | 중간 (~10K/s) | 높음 (~100K/s) |
| **영속성** | 제한적 (메모리) | 강력 (디스크) |
| **운영 복잡도** | 낮음 | 높음 |
| **인프라 요구** | 기존 Redis 활용 | 별도 클러스터 |
| **적합 환경** | 경량, 실시간 | 대용량, 배치 |

## 문제 해결

### 연결 실패

```bash
# Redis 연결 확인
redis-cli ping

# 스트림 확인
redis-cli XLEN keycloak:events:user
```

### DLQ 모니터링

```bash
# DLQ 크기 확인
curl http://localhost:9092/metrics | grep keycloak_dlq_size
```

## 참고 문서

- [Redis Streams 공식 문서](https://redis.io/docs/data-types/streams/)
- [Lettuce 클라이언트](https://lettuce.io/)
- [Resilience Patterns 가이드](../RESILIENCE_PATTERNS.md)
- [Prometheus 메트릭 가이드](../event-listener-common/PROMETHEUS.md)
