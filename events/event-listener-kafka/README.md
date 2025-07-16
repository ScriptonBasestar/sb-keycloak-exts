# Kafka Event Listener for Keycloak

Keycloak 이벤트를 Apache Kafka로 실시간 전송하는 이벤트 리스너 확장입니다.

## 개요

이 확장은 Keycloak에서 발생하는 사용자 이벤트(로그인, 로그아웃 등)와 관리 이벤트(사용자 생성, 설정 변경 등)를 Apache Kafka 토픽으로 자동 전송합니다.

### 주요 기능

- ✅ 사용자 이벤트 실시간 전송 (LOGIN, LOGOUT, REGISTER 등)
- ✅ 관리 이벤트 실시간 전송 (CREATE, UPDATE, DELETE 등)
- ✅ 이벤트 타입별 필터링 지원
- ✅ 토픽별 분리 전송 (사용자/관리 이벤트)
- ✅ JSON 형태 구조화된 데이터 전송
- ✅ 비동기 처리로 Keycloak 성능 영향 최소화
- ✅ 재시도 및 에러 처리

## 요구사항

- **Keycloak**: 23.x 이상
- **Kafka**: 2.8.x 이상  
- **Java**: 17 이상
- **Kotlin**: 1.9.x

## 빌드 방법

```bash
# 전체 프로젝트 빌드
./gradlew build

# event-listener-kafka 모듈만 빌드  
./gradlew :events:event-listener-kafka:build
```

빌드 후 `events/event-listener-kafka/build/libs/` 디렉터리에 JAR 파일이 생성됩니다.

## 설치 방법

### 1. JAR 파일 복사

```bash
# Keycloak providers 디렉터리에 복사
cp events/event-listener-kafka/build/libs/event-listener-kafka-*.jar $KEYCLOAK_HOME/providers/
```

### 2. Keycloak 재시작

```bash
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

## 설정 방법

### Realm 설정에서 Event Listener 추가

1. Keycloak Admin Console 접속
2. 해당 Realm 선택
3. **Events** → **Config** 이동  
4. **Event listeners** 드롭다운에서 `kafka-event-listener` 선택
5. **Save** 클릭

### Kafka 연결 설정

Realm의 **Attributes** 탭에서 다음 설정을 추가:

| 속성 키 | 설명 | 기본값 | 예시 |
|---------|------|---------|------|
| `kafka.bootstrap.servers` | Kafka 브로커 주소 | `localhost:9092` | `kafka1:9092,kafka2:9092` |
| `kafka.event.topic` | 사용자 이벤트 토픽 | `keycloak.events` | `my-realm.user.events` |
| `kafka.admin.event.topic` | 관리 이벤트 토픽 | `keycloak.admin.events` | `my-realm.admin.events` |
| `kafka.client.id` | Kafka 클라이언트 ID | `keycloak-event-listener` | `keycloak-prod-realm` |
| `kafka.enable.user.events` | 사용자 이벤트 활성화 | `true` | `true/false` |
| `kafka.enable.admin.events` | 관리 이벤트 활성화 | `true` | `true/false` |
| `kafka.included.event.types` | 포함할 이벤트 타입 | 모든 타입 | `LOGIN,LOGOUT,REGISTER` |

### 시스템 프로퍼티 설정 (선택사항)

```bash
# Keycloak 시작 시 JVM 옵션으로 설정 가능
-Dkafka.bootstrap.servers=kafka1:9092,kafka2:9092
-Dkafka.event.topic=keycloak.events
-Dkafka.admin.event.topic=keycloak.admin.events
```

## 이벤트 포맷

### 사용자 이벤트 (User Events)

```json
{
  "id": "uuid-string",
  "time": 1694123456789,
  "type": "LOGIN",
  "realmId": "my-realm",
  "clientId": "my-app",
  "userId": "user-uuid",
  "sessionId": "session-uuid",
  "ipAddress": "192.168.1.100",
  "details": {
    "username": "john.doe",
    "auth_method": "openid-connect",
    "auth_type": "code"
  }
}
```

### 관리 이벤트 (Admin Events)

```json
{
  "id": "uuid-string",
  "time": 1694123456789,
  "operationType": "CREATE",
  "realmId": "my-realm",
  "authDetails": {
    "realmId": "my-realm",
    "clientId": "admin-cli",
    "userId": "admin-uuid",
    "ipAddress": "192.168.1.10"
  },
  "resourcePath": "/users/user-uuid",
  "representation": "{\"username\":\"new.user\",...}"
}
```

## 지원하는 이벤트 타입

### 사용자 이벤트
- `LOGIN`, `LOGOUT`, `REGISTER`
- `LOGIN_ERROR`, `INVALID_USER_CREDENTIALS`
- `UPDATE_PROFILE`, `UPDATE_PASSWORD`
- `SEND_VERIFY_EMAIL`, `VERIFY_EMAIL`
- `SEND_RESET_PASSWORD`, `RESET_PASSWORD`

### 관리 이벤트  
- `CREATE`, `UPDATE`, `DELETE`
- `ACTION`

## 문제 해결

### 이벤트가 Kafka로 전송되지 않는 경우

1. **Event Listener 등록 확인**
   ```bash
   # Keycloak 로그에서 확인
   grep "kafka-event-listener" $KEYCLOAK_HOME/data/log/keycloak.log
   ```

2. **Kafka 연결 확인**
   ```bash
   # Kafka 브로커 접속 테스트
   kafka-console-consumer --bootstrap-server localhost:9092 --topic keycloak.events
   ```

3. **설정값 확인**
   - Realm Attributes에서 kafka.* 설정이 올바른지 확인
   - 토픽이 Kafka에 존재하는지 확인

### 성능 이슈

1. **Producer 설정 튜닝**
   - `batch.size`: 배치 크기 증가 (기본: 16384)
   - `linger.ms`: 배치 대기 시간 증가 (기본: 5)
   - `buffer.memory`: 버퍼 메모리 증가 (기본: 33554432)

2. **이벤트 필터링**
   - `kafka.included.event.types`로 필요한 이벤트만 전송
   - `kafka.enable.user.events` 또는 `kafka.enable.admin.events`로 타입별 비활성화

### 로그 레벨 설정

```xml
<!-- Keycloak 로그 설정 (standalone.xml) -->
<logger category="org.scriptonbasestar.kcexts.events.kafka">
    <level name="DEBUG"/>
</logger>
```

## 성능 튜닝 팁

### Kafka Producer 최적화
- **높은 처리량**: `batch.size=32768`, `linger.ms=10`
- **낮은 지연시간**: `batch.size=0`, `linger.ms=0`
- **안정성 우선**: `acks=all`, `retries=10`

### 토픽 설정
```bash
# 토픽 생성 시 파티션 수와 복제 인수 설정
kafka-topics --create --topic keycloak.events \\
  --partitions 6 --replication-factor 3 \\
  --bootstrap-server localhost:9092
```

### 이벤트 필터링 예시
```
# 로그인/로그아웃만 전송
kafka.included.event.types=LOGIN,LOGOUT

# 에러 이벤트만 전송  
kafka.included.event.types=LOGIN_ERROR,INVALID_USER_CREDENTIALS
```

## 라이센스

MIT License