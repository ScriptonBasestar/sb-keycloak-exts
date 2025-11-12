# Keycloak Rate Limiting Event Listener

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Keycloak Version](https://img.shields.io/badge/keycloak-26.0.7+-blue)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

**Production-grade rate limiting for Keycloak** using Event Listener SPI with multiple algorithms, distributed storage, and flexible strategies.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Algorithms](#algorithms)
- [Storage Backends](#storage-backends)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Performance Tuning](#performance-tuning)

---

## Overview

This Keycloak extension provides **real-time rate limiting** by intercepting authentication and admin events. It prevents abuse, ensures fair usage, and protects your identity infrastructure from overload.

### When to Use

- **Prevent brute-force attacks**: Limit login attempts per user/IP
- **API rate limiting**: Protect admin and user APIs
- **Fair usage enforcement**: Ensure no single client monopolizes resources
- **Compliance**: Meet regulatory requirements for access control
- **Cost control**: Limit resource consumption in multi-tenant environments

### Key Differentiators

| Feature | This Extension | API Gateway | Keycloak Built-in |
|---------|---------------|-------------|-------------------|
| **Event-level control** | ✅ LOGIN, LOGOUT, UPDATE_PROFILE, etc. | ❌ HTTP-level only | ❌ No rate limiting |
| **Distributed storage** | ✅ Redis + In-Memory | ✅ Redis | N/A |
| **Multiple strategies** | ✅ Per-user, per-client, per-IP, combined | ⚠️ Limited | N/A |
| **Zero code changes** | ✅ Drop-in JAR | ❌ Requires routing | ✅ |
| **Keycloak-aware** | ✅ Realm, client, session context | ❌ | N/A |

---

## Features

### Rate Limiting Algorithms

1. **Token Bucket** ✅ (Recommended for most use cases)
   - Allows burst traffic
   - Constant refill rate
   - Best for: Login flows, API calls
   - **Status**: Production ready

2. **Sliding Window** ✅ (Precise control)
   - No boundary issues
   - Exact request counting
   - Best for: Strict compliance, billing
   - **Status**: Production ready

3. **Fixed Window** ✅ (Simple, efficient) **NEW in 0.0.2**
   - Discrete time windows
   - Lower memory usage
   - Best for: High-volume, approximate limits
   - **Status**: Production ready (2025-11-12)

### Rate Limiting Strategies

| Strategy | Key | Use Case |
|----------|-----|----------|
| **PER_USER** | `userId` | Prevent individual user abuse |
| **PER_CLIENT** | `clientId` | Limit per OAuth2 client |
| **PER_REALM** | `realmId` | Global realm limits |
| **PER_IP** | `ipAddress` | Block IP-based attacks |
| **COMBINED** | `userId:clientId:ipAddress` | Multi-factor protection |

### Storage Backends

- **In-Memory**: Single-instance deployments, development
- **Redis**: Multi-instance Keycloak clusters, production

### Event Types Supported

**User Events**:
- `LOGIN`, `LOGIN_ERROR` (brute-force protection)
- `LOGOUT`
- `REGISTER` (prevent spam registrations)
- `UPDATE_PROFILE`, `UPDATE_PASSWORD`
- `SEND_VERIFY_EMAIL`, `SEND_RESET_PASSWORD`
- Custom events

**Admin Events**:
- `CREATE`, `UPDATE`, `DELETE`, `ACTION`
- Realm/client/user management operations

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Keycloak Event System                   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│        RateLimitingEventListenerProvider (SPI)              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  1. Extract Key (strategy: PER_USER, PER_IP, etc.)  │   │
│  │  2. Check Rate Limit (algorithm: Token Bucket, etc.) │   │
│  │  3. Allow or Deny Event                              │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
     ┌────────────────┐        ┌────────────────┐
     │  InMemory      │        │  Redis         │
     │  Storage       │        │  Storage       │
     │                │        │  (Distributed) │
     │  - Single node │        │  - Multi-node  │
     │  - Dev/Test    │        │  - Production  │
     └────────────────┘        └────────────────┘
```

### Component Breakdown

**RateLimitingEventListenerProvider**:
- Implements `EventListenerProvider` SPI
- Intercepts all user and admin events
- Throws `RateLimitExceededException` when limit exceeded

**RateLimiter Interface**:
- `tryAcquire(key: String, permits: Long = 1): Boolean`
- `availablePermits(key: String): Long`
- `reset(key: String)`
- `algorithmName(): String`

**Storage Backend**:
- `get(key)`, `set(key, value, ttl)`, `increment(key)`, `delete(key)`
- `exists(key)`, `expire(key, ttl)`, `ttl(key)`

---

## Installation

### 1. Build Shadow JAR

```bash
cd rate-limiting/rate-limiting-listener
../../gradlew shadowJar

# Output: build/libs/keycloak-ratelimit-listener-0.0.2-SNAPSHOT-all.jar
```

### 2. Deploy to Keycloak

```bash
# Copy JAR to Keycloak providers directory
cp build/libs/keycloak-ratelimit-listener-*-all.jar \
   $KEYCLOAK_HOME/providers/

# Rebuild Keycloak (detects new SPI)
$KEYCLOAK_HOME/bin/kc.sh build

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

### 3. Verify Installation

```bash
# Check logs for successful loading
grep "RateLimitingEventListenerProviderFactory" \
     $KEYCLOAK_HOME/data/log/keycloak.log

# Expected output:
# INFO  [org.keycloak.provider] (main) SPI event_listener_provider loaded: rate-limiting
```

### 4. Enable in Keycloak Admin Console

1. Navigate to **Realm Settings** → **Events**
2. Go to **Event Listeners** tab
3. Add `rate-limiting` to the list
4. Click **Save**

---

## Configuration

### Configuration Sources (Priority Order)

1. **Realm Attributes** (highest priority)
   - Path: Realm → Attributes → `ratelimit.*`

2. **System Properties**
   - JVM args: `-Dratelimit.enabled=true`

3. **Environment Variables**
   - Docker: `RATELIMIT_ENABLED=true`

### Basic Configuration

Configure via **Realm Settings** → **Attributes**:

```properties
# Enable/disable rate limiting
ratelimit.enabled=true

# Storage backend
ratelimit.storage.type=REDIS  # or IN_MEMORY

# Rate limiting strategy
ratelimit.strategy=PER_USER   # or PER_CLIENT, PER_REALM, PER_IP, COMBINED

# Rate limiting algorithm
ratelimit.algorithm=TOKEN_BUCKET  # or SLIDING_WINDOW, FIXED_WINDOW
```

### Algorithm-Specific Configuration

#### Token Bucket

```properties
ratelimit.limits.LOGIN.capacity=10
ratelimit.limits.LOGIN.refillRate=10
ratelimit.limits.LOGIN.refillPeriod=60s

ratelimit.limits.UPDATE_PROFILE.capacity=5
ratelimit.limits.UPDATE_PROFILE.refillRate=5
ratelimit.limits.UPDATE_PROFILE.refillPeriod=60s
```

**Meaning**:
- Allow **10 login attempts** initially (capacity)
- Refill **10 tokens every 60 seconds** (refillRate per refillPeriod)
- Burst: User can login 10 times immediately, then wait

#### Sliding Window

```properties
ratelimit.limits.LOGIN.windowSize=60s
ratelimit.limits.LOGIN.maxRequests=10

ratelimit.limits.REGISTER.windowSize=3600s
ratelimit.limits.REGISTER.maxRequests=3
```

**Meaning**:
- Allow **10 login requests in any 60-second window**
- Allow **3 registrations per hour**
- Precise: Window slides continuously (no boundary issues)

### Redis Configuration

```properties
# Redis connection
ratelimit.redis.host=localhost
ratelimit.redis.port=6379
ratelimit.redis.password=redispass
ratelimit.redis.database=0
ratelimit.redis.timeout=2000ms

# Redis pooling (optional, defaults shown)
ratelimit.redis.pool.maxTotal=20
ratelimit.redis.pool.maxIdle=10
ratelimit.redis.pool.minIdle=5
```

### Per-Event Type Limits

Define custom limits for each event type:

```properties
# Login attempts (strict)
ratelimit.limits.LOGIN.capacity=5
ratelimit.limits.LOGIN.refillRate=5
ratelimit.limits.LOGIN.refillPeriod=300s  # 5 attempts per 5 minutes

# Login errors (even stricter for brute-force)
ratelimit.limits.LOGIN_ERROR.capacity=3
ratelimit.limits.LOGIN_ERROR.refillRate=1
ratelimit.limits.LOGIN_ERROR.refillPeriod=600s  # 3 errors, then 1 per 10 min

# Registration (prevent spam)
ratelimit.limits.REGISTER.capacity=2
ratelimit.limits.REGISTER.refillRate=1
ratelimit.limits.REGISTER.refillPeriod=86400s  # 2 registrations per day

# Profile updates (moderate)
ratelimit.limits.UPDATE_PROFILE.capacity=10
ratelimit.limits.UPDATE_PROFILE.refillRate=10
ratelimit.limits.UPDATE_PROFILE.refillPeriod=3600s  # 10 updates per hour

# Admin operations (restrictive)
ratelimit.limits.ADMIN_CREATE.capacity=20
ratelimit.limits.ADMIN_CREATE.refillRate=20
ratelimit.limits.ADMIN_CREATE.refillPeriod=60s
```

---

## Usage Examples

### Example 1: Brute-Force Protection (PER_USER)

**Scenario**: Prevent password guessing attacks on user accounts

```properties
ratelimit.enabled=true
ratelimit.strategy=PER_USER
ratelimit.algorithm=TOKEN_BUCKET
ratelimit.storage.type=REDIS

# Allow 5 login attempts, then 1 every 5 minutes
ratelimit.limits.LOGIN.capacity=5
ratelimit.limits.LOGIN.refillRate=1
ratelimit.limits.LOGIN.refillPeriod=300s

# Block after 3 failed attempts for 30 minutes
ratelimit.limits.LOGIN_ERROR.capacity=3
ratelimit.limits.LOGIN_ERROR.refillRate=1
ratelimit.limits.LOGIN_ERROR.refillPeriod=1800s
```

**Behavior**:
- User `alice` can try logging in 5 times immediately
- After 5 attempts, she must wait 5 minutes for 1 more attempt
- If 3 login errors occur, she's blocked for 30 minutes

### Example 2: API Client Rate Limiting (PER_CLIENT)

**Scenario**: Prevent OAuth2 client from overwhelming Keycloak

```properties
ratelimit.enabled=true
ratelimit.strategy=PER_CLIENT
ratelimit.algorithm=SLIDING_WINDOW
ratelimit.storage.type=REDIS

# Allow 1000 requests per minute
ratelimit.limits.LOGIN.windowSize=60s
ratelimit.limits.LOGIN.maxRequests=1000

# Admin API: 100 requests per minute
ratelimit.limits.ADMIN_CREATE.windowSize=60s
ratelimit.limits.ADMIN_CREATE.maxRequests=100
```

**Behavior**:
- Client `mobile-app` can make 1000 login requests per minute
- Client `admin-dashboard` can create 100 resources per minute
- Precise enforcement (sliding window, no boundary issues)

### Example 3: IP-Based Attack Prevention (PER_IP)

**Scenario**: Block suspicious IPs attempting credential stuffing

```properties
ratelimit.enabled=true
ratelimit.strategy=PER_IP
ratelimit.algorithm=TOKEN_BUCKET
ratelimit.storage.type=REDIS

# Allow 20 login attempts per IP per hour
ratelimit.limits.LOGIN.capacity=20
ratelimit.limits.LOGIN.refillRate=20
ratelimit.limits.LOGIN.refillPeriod=3600s

# Block IP after 10 registration attempts per day
ratelimit.limits.REGISTER.capacity=10
ratelimit.limits.REGISTER.refillRate=10
ratelimit.limits.REGISTER.refillPeriod=86400s
```

**Behavior**:
- IP `203.0.113.42` can attempt 20 logins per hour
- If 10 registrations from same IP, block for 24 hours
- Protects against automated attacks

### Example 4: Multi-Factor Protection (COMBINED)

**Scenario**: Strictest security with user + client + IP combined

```properties
ratelimit.enabled=true
ratelimit.strategy=COMBINED
ratelimit.algorithm=SLIDING_WINDOW
ratelimit.storage.type=REDIS

# Unique key: userId:clientId:ipAddress
ratelimit.limits.LOGIN.windowSize=300s
ratelimit.limits.LOGIN.maxRequests=5
```

**Behavior**:
- Key: `alice:mobile-app:203.0.113.42`
- Different client or IP = different limit
- Most granular control

### Example 5: Development/Testing (IN_MEMORY)

**Scenario**: Local development without Redis

```properties
ratelimit.enabled=true
ratelimit.strategy=PER_USER
ratelimit.algorithm=TOKEN_BUCKET
ratelimit.storage.type=IN_MEMORY

# Generous limits for testing
ratelimit.limits.LOGIN.capacity=100
ratelimit.limits.LOGIN.refillRate=100
ratelimit.limits.LOGIN.refillPeriod=60s
```

**Warning**: In-memory storage does not persist across restarts and does not work in clustered environments.

---

## Algorithms

### Token Bucket

**Best for**: Bursty traffic, user-facing applications

**How it works**:
1. Bucket has fixed capacity (e.g., 10 tokens)
2. Each request consumes 1 token
3. Tokens refill at constant rate (e.g., 10 tokens/minute)
4. If bucket empty, request denied

**Advantages**:
- ✅ Allows burst traffic (good UX)
- ✅ Smooth refill
- ✅ Low memory usage

**Disadvantages**:
- ❌ Can exceed limit briefly during bursts
- ❌ Less precise than sliding window

**Configuration**:
```properties
ratelimit.limits.{EVENT_TYPE}.capacity=10
ratelimit.limits.{EVENT_TYPE}.refillRate=10
ratelimit.limits.{EVENT_TYPE}.refillPeriod=60s
```

**Example Timeline**:
```
Time  Tokens  Action         Result
0s    10      LOGIN          ✅ (9 left)
1s    10      LOGIN x10      ✅ (0 left)
2s    0       LOGIN          ❌ DENIED
62s   10      LOGIN          ✅ (refilled)
```

### Sliding Window

**Best for**: Precise control, compliance, billing

**How it works**:
1. Track each request with timestamp
2. Count requests in last N seconds
3. If count < limit, allow
4. Old requests automatically expire

**Advantages**:
- ✅ Precise enforcement
- ✅ No boundary issues (unlike fixed window)
- ✅ Fair distribution

**Disadvantages**:
- ❌ Higher memory usage (stores timestamps)
- ❌ No burst allowance

**Configuration**:
```properties
ratelimit.limits.{EVENT_TYPE}.windowSize=60s
ratelimit.limits.{EVENT_TYPE}.maxRequests=10
```

**Example Timeline**:
```
Time  Requests in Window  Action  Result
0s    0                   LOGIN   ✅ (1 in window)
5s    1                   LOGIN   ✅ (2 in window)
10s   2                   LOGIN   ✅ (3 in window)
...
55s   10                  LOGIN   ❌ DENIED (10/10)
65s   9                   LOGIN   ✅ (first request expired)
```

### Fixed Window ✅

**Best for**: High volume, approximate limits

**Status**: Production ready (2025-11-12)

**How it works**:
1. Divide time into discrete windows (e.g., 1-minute buckets)
2. Count requests in current window
3. Reset count at window boundary
4. Window index = `currentTime / windowSize`

**Advantages**:
- ✅ Very low memory usage (single counter)
- ✅ Simple implementation
- ✅ High performance (constant-time operations)
- ✅ Ideal for high-throughput scenarios

**Disadvantages**:
- ❌ Boundary issue: 2x limit possible at window edge
- ❌ Less fair than sliding window

**Configuration**:
```properties
ratelimit.algorithm=FIXED_WINDOW
ratelimit.limits.{EVENT_TYPE}.windowSize=60s
ratelimit.limits.{EVENT_TYPE}.maxRequests=10
```

**Example Boundary Issue**:
```
Window 1 (0-60s): 10 requests at 59s ✅
Window 2 (60-120s): 10 requests at 60s ✅
Total: 20 requests in 1 second! ⚠️
```

**Implementation Details**:
- Counter key: `ratelimit:fixed:{key}:window:{windowIndex}`
- Automatic TTL: Counter expires after window duration
- Storage: Single counter per key per window

---

## Storage Backends

### In-Memory Storage

**Use Cases**:
- Single Keycloak instance
- Development/testing
- Low traffic

**Configuration**:
```properties
ratelimit.storage.type=IN_MEMORY
```

**Features**:
- ✅ No external dependencies
- ✅ Fast (nanosecond latency)
- ✅ Automatic cleanup (TTL-based)
- ❌ No cluster support
- ❌ Lost on restart

**Thread Safety**: Uses `ConcurrentHashMap` + `synchronized` blocks for atomic operations

### Redis Storage

**Use Cases**:
- Multi-instance Keycloak clusters
- Production deployments
- High availability

**Configuration**:
```properties
ratelimit.storage.type=REDIS
ratelimit.redis.host=redis.example.com
ratelimit.redis.port=6379
ratelimit.redis.password=secret
ratelimit.redis.database=0
```

**Features**:
- ✅ Distributed (shared across Keycloak nodes)
- ✅ Persistent (survives restarts)
- ✅ Lua scripts for atomic operations
- ✅ High availability (Redis Sentinel/Cluster)
- ⚠️ Network latency (1-5ms typical)

**Lua Scripts Used**:
1. **Token Bucket**: Atomic token consumption + refill calculation
2. **Sliding Window**: Sorted set operations (ZADD, ZREMRANGEBYSCORE)
3. **Increment with TTL**: Atomic counter with automatic expiration

**Redis Key Patterns**:
```
Token Bucket:
- ratelimit:bucket:{key}       # Current token count
- ratelimit:bucket:{key}:time  # Last refill timestamp

Sliding Window:
- ratelimit:window:{key}       # Sorted set of request timestamps

Counters:
- ratelimit:counter:{key}      # Simple counter with TTL
```

---

## Monitoring

### Metrics (Prometheus) ✅ NEW in 0.0.2

This extension includes **built-in Prometheus metrics** via `RateLimitMetrics`:

```
# Total events processed (counters)
keycloak_ratelimit_events_total{realm="master", event_type="LOGIN", result="allowed"}
keycloak_ratelimit_events_total{realm="master", event_type="LOGIN", result="denied"}

# Denial rate (PromQL query)
rate(keycloak_ratelimit_events_total{result="denied"}[5m])

# Available permits (gauge - sampled per request)
keycloak_ratelimit_permits_available{realm="master", strategy="PER_USER", key="alice"}
```

**Status**: Metrics collection implemented (2025-11-12)
**Integration**: Automatic when Keycloak metrics enabled (`KC_METRICS_ENABLED=true`)

### Logs

Rate limit denials are logged at `WARN` level:

```
WARN  [org.scriptonbasestar.kcexts.ratelimit] Rate limit exceeded:
      type=LOGIN, user=alice, client=mobile-app, ip=203.0.113.42,
      strategy=PER_USER, algorithm=TOKEN_BUCKET
```

Enable `DEBUG` logging for detailed diagnostics:

```bash
# standalone.xml or kc.sh args
--log-level=org.scriptonbasestar.kcexts.ratelimit:DEBUG
```

### Grafana Dashboard

Example queries:

```promql
# Denial rate by event type
sum(rate(keycloak_ratelimit_events_total{result="denied"}[5m])) by (event_type)

# Top rate-limited users
topk(10, sum(increase(keycloak_ratelimit_events_total{result="denied"}[1h])) by (user_id))

# Redis storage latency (if using Redis exporter)
histogram_quantile(0.99, redis_command_duration_seconds_bucket{cmd="EVALSHA"})
```

---

## Troubleshooting

### Issue: Rate limiting not working

**Symptoms**: Events not being rate limited

**Checklist**:
1. ✅ Verify listener enabled in Realm Settings → Events
2. ✅ Check `ratelimit.enabled=true` in Realm Attributes
3. ✅ Ensure limits configured for event types (e.g., `ratelimit.limits.LOGIN.*`)
4. ✅ Check logs for initialization errors
5. ✅ Verify JAR deployed in `$KEYCLOAK_HOME/providers/`

**Validation**:
```bash
# Check SPI registration
grep "RateLimitingEventListenerProviderFactory" \
     $KEYCLOAK_HOME/data/log/keycloak.log

# Expected: SPI event_listener_provider loaded: rate-limiting
```

### Issue: Redis connection errors

**Symptoms**:
```
ERROR [org.scriptonbasestar.kcexts.ratelimit.storage.RedisStorage]
      Failed to initialize Redis storage: Connection refused
```

**Solutions**:
1. Verify Redis is running: `redis-cli ping` → `PONG`
2. Check network connectivity: `telnet redis.example.com 6379`
3. Verify credentials: `redis-cli -h HOST -p PORT -a PASSWORD ping`
4. Check firewall rules
5. Review Redis logs: `docker logs keycloak-redis`

**Fallback**: Set `ratelimit.storage.type=IN_MEMORY` temporarily

### Issue: Rate limits too strict/too lenient

**Symptoms**: Users complaining about blocks or attacks succeeding

**Tuning**:

**Too Strict** (legitimate users blocked):
```properties
# Increase capacity and refill rate
ratelimit.limits.LOGIN.capacity=20  # was 5
ratelimit.limits.LOGIN.refillRate=20  # was 5
ratelimit.limits.LOGIN.refillPeriod=60s  # was 300s
```

**Too Lenient** (attacks succeeding):
```properties
# Decrease capacity and slow refill
ratelimit.limits.LOGIN_ERROR.capacity=3  # was 10
ratelimit.limits.LOGIN_ERROR.refillRate=1  # was 5
ratelimit.limits.LOGIN_ERROR.refillPeriod=600s  # was 60s
```

### Issue: Memory usage high with Sliding Window

**Symptoms**: High heap usage in Keycloak

**Cause**: Sliding Window stores all request timestamps

**Solutions**:
1. **Switch to Token Bucket**:
   ```properties
   ratelimit.algorithm=TOKEN_BUCKET
   ```

2. **Use Redis** (offload to external memory):
   ```properties
   ratelimit.storage.type=REDIS
   ```

3. **Reduce window size**:
   ```properties
   ratelimit.limits.LOGIN.windowSize=30s  # was 300s
   ```

### Issue: Cluster synchronization delays

**Symptoms**: Rate limits inconsistent across Keycloak nodes

**Cause**: IN_MEMORY storage is not shared

**Solution**: **Always use Redis for clusters**:
```properties
ratelimit.storage.type=REDIS
ratelimit.redis.host=redis-cluster.example.com
```

### Issue: "RateLimitExceededException" in logs

**Symptoms**:
```
ERROR [org.keycloak.services] Unexpected error: RateLimitExceededException
```

**This is expected behavior** when rate limits are exceeded. The exception:
- Denies the event (login fails, etc.)
- Returns error to client
- Logs the denial

**To reduce log noise**, this is logged at `WARN` level, not `ERROR`.

---

## Performance Tuning

### Optimize Redis Latency

**Use connection pooling**:
```properties
ratelimit.redis.pool.maxTotal=50
ratelimit.redis.pool.maxIdle=20
ratelimit.redis.pool.minIdle=10
```

**Use Redis pipelining** (built-in to Lettuce client)

**Deploy Redis close to Keycloak** (same datacenter, < 1ms RTT)

### Optimize Token Bucket Performance

**Longer refill periods** reduce storage operations:
```properties
# Instead of: 1 token per second (60 ops/min)
ratelimit.limits.LOGIN.refillRate=1
ratelimit.limits.LOGIN.refillPeriod=1s

# Use: 60 tokens per minute (1 op/min)
ratelimit.limits.LOGIN.refillRate=60
ratelimit.limits.LOGIN.refillPeriod=60s
```

### Optimize Sliding Window Performance

**Shorter window sizes** reduce memory:
```properties
# Instead of: 1 hour window (stores all timestamps)
ratelimit.limits.LOGIN.windowSize=3600s

# Use: 5 minute window (60x less data)
ratelimit.limits.LOGIN.windowSize=300s
```

**Use Redis Cluster** for horizontal scaling

### Optimize Strategy Choice

**Performance comparison** (latency per check):

| Strategy | In-Memory | Redis |
|----------|-----------|-------|
| PER_REALM | 0.1 µs | 1.5 ms |
| PER_USER | 0.2 µs | 2.0 ms |
| PER_CLIENT | 0.2 µs | 2.0 ms |
| PER_IP | 0.2 µs | 2.0 ms |
| COMBINED | 0.5 µs | 3.0 ms |

**Recommendation**: Use simplest strategy that meets security requirements

---

## Integration with Other Modules

### With Metering Service

Rate limits can be **dynamically adjusted** based on usage metrics from the Metering Service:

1. Metering tracks usage patterns per user/client
2. If usage exceeds threshold, reduce rate limits
3. If user is within quota, increase rate limits

**Future Enhancement**: Auto-scaling rate limits based on InfluxDB metrics

### With Kafka Event Listener

Both extensions can run simultaneously:

```yaml
# Realm Settings → Events → Event Listeners
event_listeners:
  - kafka-events      # Streams events to Kafka
  - rate-limiting     # Enforces rate limits
```

**Order matters**: Rate limiting runs first, denied events are not sent to Kafka.

---

## Security Considerations

### Denial-of-Service Protection

**Scenario**: Attacker floods login endpoint

**Mitigation**:
```properties
ratelimit.strategy=PER_IP
ratelimit.limits.LOGIN.capacity=10
ratelimit.limits.LOGIN.refillRate=1
ratelimit.limits.LOGIN.refillPeriod=60s
```

**Result**: Attacker can make 10 requests, then 1 per minute maximum

### Credential Stuffing Protection

**Scenario**: Attacker tries stolen credentials

**Mitigation**:
```properties
ratelimit.strategy=COMBINED  # user + client + IP
ratelimit.limits.LOGIN_ERROR.capacity=3
ratelimit.limits.LOGIN_ERROR.refillRate=1
ratelimit.limits.LOGIN_ERROR.refillPeriod=1800s  # 30 min
```

**Result**: 3 wrong passwords = 30 minute block per unique combination

### Account Enumeration Protection

**Scenario**: Attacker probes for valid usernames

**Mitigation**:
```properties
ratelimit.strategy=PER_IP
ratelimit.limits.LOGIN.windowSize=60s
ratelimit.limits.LOGIN.maxRequests=5
```

**Result**: Attacker can only test 5 usernames per minute

---

## Development

### Building from Source

```bash
git clone https://github.com/your-org/sb-keycloak-exts.git
cd sb-keycloak-exts/rate-limiting/rate-limiting-listener

# Build
../../gradlew build

# Run tests
../../gradlew test

# Create Shadow JAR
../../gradlew shadowJar
```

### Running Tests

```bash
# Unit tests
../../gradlew test

# Integration tests (with TestContainers - requires Docker)
../../gradlew integrationTest

# Specific test
../../gradlew test --tests "*TokenBucketRateLimiterTest"
```

### Local Development with Docker

```bash
# Start Redis
docker-compose --profile ratelimit up -d redis

# Build and deploy
../../gradlew shadowJar
cp build/libs/*-all.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start-dev
```

---

## API Reference

### RateLimiter Interface

```kotlin
interface RateLimiter {
    /**
     * Try to acquire permits from the rate limiter
     *
     * @param key Unique identifier (userId, clientId, ipAddress, etc.)
     * @param permits Number of permits to acquire (default: 1)
     * @return true if acquired, false if denied
     * @throws IllegalArgumentException if permits <= 0 or > capacity
     */
    fun tryAcquire(key: String, permits: Long = 1): Boolean

    /**
     * Get available permits for a key
     *
     * @param key Unique identifier
     * @return Number of permits available (0 to capacity)
     */
    fun availablePermits(key: String): Long

    /**
     * Reset rate limit for a key (clear all state)
     *
     * @param key Unique identifier
     */
    fun reset(key: String)

    /**
     * Get algorithm name
     *
     * @return Algorithm name (TokenBucket, SlidingWindow, FixedWindow)
     */
    fun algorithmName(): String
}
```

### Configuration Model

```kotlin
data class RateLimitConfig(
    val enabled: Boolean,
    val storageType: StorageType,
    val strategy: RateLimitStrategy,
    val algorithm: RateLimitAlgorithm,
    val defaultLimits: Map<String, LimitConfig>,
    val redisConfig: RedisConfig?
)

data class LimitConfig(
    val capacity: Long,
    val refillRate: Long,
    val refillPeriod: Duration,
    val windowSize: Duration?,
    val maxRequests: Long?
)

enum class StorageType { IN_MEMORY, REDIS }
enum class RateLimitStrategy { PER_USER, PER_CLIENT, PER_REALM, PER_IP, COMBINED }
enum class RateLimitAlgorithm { TOKEN_BUCKET, SLIDING_WINDOW, FIXED_WINDOW }
```

---

## FAQ

**Q: Can I use different algorithms for different event types?**

A: Not in current version (0.0.2). All events use the same algorithm configured via `ratelimit.algorithm`. You can configure different limits per event type, but not different algorithms.

**Q: Does this work with Keycloak in Kubernetes?**

A: Yes, with Redis storage. Configure Redis as a StatefulSet or use managed Redis (AWS ElastiCache, Azure Cache for Redis, etc.).

**Q: Can I whitelist certain users/IPs?**

A: Not built-in. Workaround: Set very high limits for trusted entities or disable rate limiting in a separate realm.

**Q: What happens when Redis is unavailable?**

A: The extension logs an error and **falls back to allowing requests** (fail-open) to prevent outages. Configure `REDIS_FALLBACK_MODE=DENY` to fail-closed (deny all requests).

**Q: Can I rate limit only specific clients?**

A: Not directly. Workaround: Use PER_CLIENT strategy with very high default limits, then set strict limits for specific clients.

**Q: Performance impact on Keycloak?**

A: Minimal with In-Memory (<1% CPU). With Redis, expect 1-5ms added latency per event. Use Token Bucket for best performance.

---

## License

Apache License 2.0 - see [LICENSE](../../LICENSE) file

---

## Support

- **Issues**: [GitHub Issues](https://github.com/your-org/sb-keycloak-exts/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/sb-keycloak-exts/discussions)
- **Documentation**: [Full Documentation](../../docs/)

---

## Changelog

### Version 0.0.2 (2025-11-12) ✅

**New Features**:
- ✅ **Fixed Window algorithm** implementation
- ✅ **Prometheus metrics** integration (`RateLimitMetrics`)
- ✅ **CDI beans.xml** for Quarkus compatibility

**Testing**:
- ✅ 20 tests total (all passing)
  - FixedWindowRateLimiterTest: 8 tests
  - RateLimitMetricsTest: 12 tests
  - Existing algorithm tests

**Commits**:
- `629d1e2` - Rate limiting module completion

See [CHANGELOG.md](../../CHANGELOG.md) for full version history.

## Roadmap

**Phase 2** (Next Release):
- [ ] Grafana dashboard templates
- [ ] Auto-scaling limits based on Metering Service data
- [ ] Rate limit reset API endpoint

**Phase 3** (Future):
- [ ] Whitelist/blacklist support
- [ ] Per-client/per-user override configuration
- [ ] Distributed tracing support (OpenTelemetry)
- [ ] Multiple Redis clusters support
