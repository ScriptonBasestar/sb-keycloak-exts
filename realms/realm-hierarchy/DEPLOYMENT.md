# Realm Hierarchy Manager - Deployment Guide

완전한 배포 가이드: Docker Compose를 사용한 로컬 개발 및 프로덕션 배포

## 목차

- [사전 요구사항](#사전-요구사항)
- [로컬 개발 환경](#로컬-개발-환경)
- [프로덕션 배포](#프로덕션-배포)
- [검증 및 테스트](#검증-및-테스트)
- [문제 해결](#문제-해결)

## 사전 요구사항

### 시스템 요구사항

| 구성 요소 | 최소 | 권장 |
|-----------|------|------|
| **Java** | OpenJDK 21+ | OpenJDK 21+ |
| **Keycloak** | 26.0.7+ | 26.0.7+ |
| **Memory** | 1GB | 2GB+ |
| **CPU** | 2 cores | 4+ cores |
| **Storage** | 500MB | 1GB+ |

### 빌드 도구

- Gradle 8.8+ (wrapper 포함)
- Docker 20.10+ (로컬 테스트용)
- Docker Compose 2.0+ (로컬 테스트용)

## 로컬 개발 환경

### 1. Shadow JAR 빌드

```bash
# 프로젝트 루트 디렉토리에서
cd /path/to/sb-keycloak-exts

# realm-hierarchy 모듈 빌드
./gradlew :realms:realm-hierarchy:shadowJar

# 빌드 결과 확인
ls -lh realms/realm-hierarchy/build/libs/
# realm-hierarchy-0.0.2-SNAPSHOT-all.jar (~23MB)
```

### 2. JAR 내용 검증

```bash
# SPI 등록 확인
jar tf realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar \
  | grep "META-INF/services"

# 필수 SPI 등록 확인:
# - org.keycloak.events.EventListenerProviderFactory
# - org.keycloak.services.resource.RealmResourceProviderFactory

# 클래스 파일 확인
jar tf realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar \
  | grep "RealmHierarchy" | head -10
```

### 3. Docker Compose로 로컬 배포

#### docker-compose.yml 생성

`docker/realm-hierarchy-test/docker-compose.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: keycloak-postgres
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - keycloak-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 10s
      timeout: 5s
      retries: 5

  keycloak:
    image: quay.io/keycloak/keycloak:26.0.7
    container_name: keycloak
    command:
      - start-dev
      - --features=preview
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HTTP_ENABLED: true
      KC_HOSTNAME_STRICT: false
      KC_LOG_LEVEL: INFO,org.scriptonbasestar:DEBUG
    ports:
      - "8080:8080"
    volumes:
      # Realm Hierarchy JAR 마운트
      - ../../realms/realm-hierarchy/build/libs/realm-hierarchy-0.0.2-SNAPSHOT-all.jar:/opt/keycloak/providers/realm-hierarchy.jar:ro
    networks:
      - keycloak-network
    depends_on:
      postgres:
        condition: service_healthy

networks:
  keycloak-network:
    driver: bridge

volumes:
  postgres_data:
```

#### 실행

```bash
# Docker Compose 디렉토리로 이동
cd docker/realm-hierarchy-test

# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f keycloak

# 필수 확인 로그:
# - "realm-hierarchy" provider loaded
# - "RealmHierarchyEventListenerFactory" registered
# - "RealmHierarchyResourceProviderFactory" registered
```

### 4. 배포 검증

#### Keycloak Admin Console 접속

1. 브라우저에서 http://localhost:8080 접속
2. Administration Console 클릭
3. 계정: `admin` / `admin`

#### Event Listener 확인

1. Realm 선택 (master)
2. **Events** → **Config** 이동
3. **Event Listeners** 드롭다운 확인
4. `realm-hierarchy`가 목록에 있는지 확인 ✅

#### REST API 확인

```bash
# Admin 토큰 획득
export ACCESS_TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  | jq -r '.access_token')

# Hierarchy API 테스트
curl -X GET "http://localhost:8080/realms/master/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Accept: application/json" | jq .

# 예상 응답:
# {
#   "realmId": "...",
#   "realmName": "master",
#   "parentRealmId": null,
#   "tier": "standard",
#   "depth": 0,
#   "path": "/",
#   ...
# }
```

### 5. 기능 테스트

#### 테스트 Realm 생성

```bash
# child-realm 생성
curl -X POST "http://localhost:8080/admin/realms" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "realm": "child-realm",
    "enabled": true
  }'

# 부모 Realm 설정
curl -X POST "http://localhost:8080/realms/child-realm/hierarchy/parent" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "parentRealmId": "master",
    "inheritIdp": true,
    "inheritRoles": true,
    "inheritAuthFlow": false
  }'

# 계층 구조 확인
curl -X GET "http://localhost:8080/realms/child-realm/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

#### Event Listener 테스트

```bash
# Event Listener 활성화
curl -X PUT "http://localhost:8080/admin/realms/master" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventsListeners": ["realm-hierarchy", "jboss-logging"]
  }'

# Master realm에 IdP 추가 (예: Google)
# → child-realm에 자동 상속되는지 확인

# Keycloak 로그 확인
docker-compose logs keycloak | grep "RealmHierarchy"
# 예상 로그:
# "Inheriting settings from parent realm: master -> child-realm"
# "Inherited IdP: google"
```

## 프로덕션 배포

### 1. JAR 배포

#### 수동 배포

```bash
# JAR 복사
scp realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar \
  user@keycloak-server:/opt/keycloak/providers/

# Keycloak 재빌드
ssh user@keycloak-server
cd /opt/keycloak
bin/kc.sh build

# Keycloak 재시작
systemctl restart keycloak
```

#### Docker 이미지 빌드

`Dockerfile`:

```dockerfile
FROM quay.io/keycloak/keycloak:26.0.7

# Realm Hierarchy JAR 복사
COPY realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar \
     /opt/keycloak/providers/realm-hierarchy.jar

# Keycloak 빌드
RUN /opt/keycloak/bin/kc.sh build

# 실행 명령어
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
```

빌드 및 실행:

```bash
# Docker 이미지 빌드
docker build -t keycloak-with-realm-hierarchy:0.0.2 .

# 실행
docker run -d \
  --name keycloak \
  -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  keycloak-with-realm-hierarchy:0.0.2 start-dev
```

### 2. Kubernetes 배포

#### ConfigMap으로 JAR 배포

`k8s/realm-hierarchy-configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-realm-hierarchy
  namespace: keycloak
binaryData:
  realm-hierarchy.jar: |
    <base64-encoded-jar-content>
```

JAR를 base64로 인코딩:

```bash
base64 -w 0 realms/realm-hierarchy/build/libs/realm-hierarchy-*-all.jar > realm-hierarchy.jar.b64
```

#### Deployment 수정

`k8s/keycloak-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
spec:
  template:
    spec:
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:26.0.7
        volumeMounts:
        - name: realm-hierarchy-jar
          mountPath: /opt/keycloak/providers/realm-hierarchy.jar
          subPath: realm-hierarchy.jar
      volumes:
      - name: realm-hierarchy-jar
        configMap:
          name: keycloak-realm-hierarchy
```

적용:

```bash
kubectl apply -f k8s/realm-hierarchy-configmap.yaml
kubectl apply -f k8s/keycloak-deployment.yaml
kubectl rollout restart deployment/keycloak -n keycloak
```

### 3. 프로덕션 설정

#### 환경 변수

```bash
# Keycloak 시작 시
KC_LOG_LEVEL=INFO,org.scriptonbasestar:INFO
KC_FEATURES=preview
```

#### Realm Attributes (권장)

프로덕션에서는 Realm Attributes를 통해 계층 구조를 관리하는 것이 좋습니다:

1. Admin Console → Realm → Attributes
2. 다음 속성 추가:
   ```
   hierarchy.tier=enterprise
   hierarchy.max_depth=3
   ```

## 검증 및 테스트

### 헬스 체크

```bash
# Keycloak 헬스 체크
curl http://localhost:8080/health

# Provider 로드 확인
curl -X GET "http://localhost:8080/realms/master/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 응답이 200이면 성공
```

### 모니터링

#### 로그 확인

```bash
# Docker Compose
docker-compose logs -f keycloak | grep -i "realm.*hierarchy"

# Kubernetes
kubectl logs -f deployment/keycloak -n keycloak | grep -i "realm.*hierarchy"

# 예상 로그:
# - "RealmHierarchyEventListenerFactory initialized"
# - "RealmHierarchyResourceProviderFactory registered"
```

#### 메트릭 (향후 지원 예정)

```bash
# Prometheus 메트릭
curl http://localhost:8080/metrics | grep realm_hierarchy
```

## 문제 해결

### JAR가 로드되지 않음

**증상**: Event Listener 목록에 `realm-hierarchy`가 없음

**해결**:
1. JAR 위치 확인: `/opt/keycloak/providers/`
2. 파일 권한 확인: `chmod 644 realm-hierarchy.jar`
3. Keycloak 재빌드: `bin/kc.sh build`
4. 로그 확인: `grep "realm-hierarchy" logs/keycloak.log`

### REST API 404 에러

**증상**: `/realms/{realm}/hierarchy` 호출 시 404

**해결**:
1. RealmResourceProviderFactory SPI 등록 확인
2. JAR 내 `META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory` 확인
3. Provider ID 확인: `hierarchy`

### Event Listener 동작 안함

**증상**: 부모 Realm 변경이 자식에 전파되지 않음

**해결**:
1. Events → Config에서 `realm-hierarchy` 활성화 확인
2. 상속 설정 확인: `inheritIdp: true` 등
3. 수동 동기화: `POST /realms/{realm}/hierarchy/synchronize`

### Docker Compose 시작 실패

**증상**: `docker-compose up` 실패

**해결**:
1. JAR 경로 확인: 상대 경로 또는 절대 경로
2. JAR 파일 존재 확인: `ls realms/realm-hierarchy/build/libs/`
3. PostgreSQL 헬스 체크: `docker-compose ps postgres`
4. 볼륨 권한 확인: `docker-compose exec keycloak ls -la /opt/keycloak/providers/`

## 성능 최적화

### 권장 사항

- **계층 깊이**: 3단계 이내
- **자식 Realm 수**: 부모당 100개 이하
- **동기화 빈도**: Event Listener 활성화 시 실시간, 그렇지 않으면 배치
- **메모리**: 자식 Realm 100개당 추가 256MB

### JVM 튜닝

```bash
# Keycloak 시작 시
JAVA_OPTS="-Xms2g -Xmx4g -XX:MetaspaceSize=256m"
```

## 추가 리소스

- [README.md](README.md) - 기능 및 사용법
- [../README.md](../README.md) - 전체 프로젝트 개요
- [CLAUDE.md](../../CLAUDE.md) - 개발자 가이드

## 지원

문제가 발생하면:
1. GitHub Issues: https://github.com/ScriptonBasestar/sb-keycloak-exts/issues
2. 로그 파일 첨부
3. 환경 정보 (Keycloak 버전, Java 버전, OS)
