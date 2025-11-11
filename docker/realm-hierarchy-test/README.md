# Realm Hierarchy - Local Test Environment

Docker Compose를 사용한 Realm Hierarchy Manager 로컬 테스트 환경

## 사전 요구사항

- Docker 20.10+
- Docker Compose 2.0+
- jq (테스트 스크립트용)
- curl

## 빠른 시작

### 1. JAR 빌드

```bash
# 프로젝트 루트 디렉토리에서
cd /path/to/sb-keycloak-exts

# Shadow JAR 빌드
./gradlew :realms:realm-hierarchy:shadowJar

# 빌드 확인
ls -lh realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar
```

### 2. 서비스 시작

```bash
# 이 디렉토리로 이동
cd docker/realm-hierarchy-test

# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f keycloak
```

### 3. 배포 검증

서비스가 완전히 시작될 때까지 1-2분 대기 후:

```bash
# 자동 테스트 실행
./test-deployment.sh
```

또는 수동으로 확인:

```bash
# Keycloak Admin Console
open http://localhost:8080

# 계정: admin / admin

# Admin Console → Events → Config → Event Listeners
# "realm-hierarchy"가 목록에 있는지 확인
```

## 서비스 구성

| 서비스 | 포트 | 용도 |
|--------|------|------|
| PostgreSQL | 5432 | Keycloak 데이터베이스 |
| Keycloak | 8080 | Keycloak 서버 |

## 환경 변수

docker-compose.yml에서 설정 가능:

```yaml
environment:
  KEYCLOAK_ADMIN: admin          # Admin 계정
  KEYCLOAK_ADMIN_PASSWORD: admin # Admin 비밀번호
  KC_LOG_LEVEL: INFO,org.scriptonbasestar:DEBUG  # 로그 레벨
```

## 테스트 시나리오

### 자동 테스트 (test-deployment.sh)

```bash
./test-deployment.sh
```

테스트 항목:
1. ✅ Keycloak 가용성
2. ✅ Admin 토큰 획득
3. ✅ Hierarchy API (GET /hierarchy)
4. ✅ Event Listener 등록 확인
5. ✅ 테스트 Realm 생성
6. ✅ 부모 Realm 설정
7. ✅ 계층 구조 검증
8. ✅ 경로 API 테스트
9. ✅ 동기화 API 테스트
10. ✅ 정리

### 수동 테스트

#### 1. Admin 토큰 획득

```bash
export ACCESS_TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  | jq -r '.access_token')
```

#### 2. Hierarchy API 테스트

```bash
# GET hierarchy
curl -X GET "http://localhost:8080/realms/master/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  | jq .

# Expected:
# {
#   "realmId": "...",
#   "realmName": "master",
#   "parentRealmId": null,
#   "depth": 0,
#   "path": "/",
#   ...
# }
```

#### 3. 자식 Realm 생성 및 계층 설정

```bash
# Create child realm
curl -X POST "http://localhost:8080/admin/realms" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "realm": "child-realm",
    "enabled": true
  }'

# Set parent
curl -X POST "http://localhost:8080/realms/child-realm/hierarchy/parent" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "parentRealmId": "master",
    "inheritIdp": true,
    "inheritRoles": true,
    "inheritAuthFlow": false
  }'

# Verify
curl -X GET "http://localhost:8080/realms/child-realm/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  | jq .
```

## 문제 해결

### JAR가 로드되지 않음

```bash
# JAR 파일 확인
docker-compose exec keycloak ls -la /opt/keycloak/providers/

# 예상 출력:
# -rw-r--r-- 1 root root 23M Nov 11 08:00 realm-hierarchy.jar
```

### Keycloak 로그 확인

```bash
# 전체 로그
docker-compose logs keycloak

# RealmHierarchy 관련 로그만
docker-compose logs keycloak | grep -i "realmhierarchy"

# 예상 로그:
# "RealmHierarchyEventListenerFactory initialized"
# "RealmHierarchyResourceProviderFactory registered"
```

### 서비스 재시작

```bash
# 모든 서비스 재시작
docker-compose restart

# Keycloak만 재시작
docker-compose restart keycloak

# 전체 재생성
docker-compose down -v
docker-compose up -d
```

### 데이터베이스 초기화

```bash
# 볼륨 포함 완전 삭제
docker-compose down -v

# 재시작
docker-compose up -d
```

## 서비스 중지

```bash
# 서비스 중지 (데이터 유지)
docker-compose stop

# 서비스 삭제 (데이터 유지)
docker-compose down

# 서비스 및 데이터 완전 삭제
docker-compose down -v
```

## 고급 설정

### 커스텀 로그 레벨

docker-compose.yml 수정:

```yaml
environment:
  KC_LOG_LEVEL: DEBUG,org.scriptonbasestar:TRACE
```

### 메모리 제한

docker-compose.yml에 추가:

```yaml
services:
  keycloak:
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
```

### 포트 변경

docker-compose.yml 수정:

```yaml
ports:
  - "8081:8080"  # 호스트 포트 8081로 변경
```

## 다음 단계

성공적으로 테스트를 완료했다면:

1. [DEPLOYMENT.md](../../realms/realm-hierarchy/DEPLOYMENT.md) - 프로덕션 배포 가이드
2. [README.md](../../realms/realm-hierarchy/README.md) - 기능 및 사용법
3. 실제 환경에 배포

## 지원

문제가 발생하면:
- GitHub Issues: https://github.com/ScriptonBasestar/sb-keycloak-exts/issues
- 로그 파일 첨부
- `docker-compose logs keycloak` 출력 포함
