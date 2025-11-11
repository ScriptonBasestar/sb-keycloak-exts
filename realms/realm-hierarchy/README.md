# Realm Hierarchy Manager for Keycloak

Keycloak Realm 간 계층 구조 및 설정 상속을 지원하는 확장 모듈입니다.

## 개요

이 확장은 Keycloak의 Realm을 계층적으로 구성하고, 부모 Realm의 설정을 자식 Realm에 자동으로 상속할 수 있도록 지원합니다. 대규모 멀티테넌트 환경이나 엔터프라이즈 조직 구조에서 Realm 관리를 간소화합니다.

### 주요 기능

- ✅ **계층적 Realm 구조**: 부모-자식 관계로 Realm 조직화
- ✅ **설정 자동 상속**:
  - Identity Provider (OAuth2/OIDC 소셜 로그인)
  - Realm Roles
  - Authentication Flows (향후 지원 예정)
- ✅ **실시간 동기화**: Event Listener를 통한 자동 전파
- ✅ **순환 참조 방지**: 무한 루프 감지 및 차단
- ✅ **REST API**: 계층 구조 관리용 엔드포인트 제공
- ✅ **메타데이터 저장**: Realm Attributes를 활용한 경량 저장

## Realm Hierarchy 특성과 설계 배경

### 계층 구조를 선택한 이유
- **멀티테넌트 SaaS 환경**: 기업 고객별로 독립된 Realm을 생성하되, 공통 IdP나 Role은 상속받아 관리 부담을 줄입니다.
- **대규모 조직**: 본사-지사-부서 구조를 Realm 계층으로 표현하고, 상위 조직의 정책을 하위 조직에 자동 적용할 수 있습니다.
- **개발/스테이징/프로덕션 분리**: 환경별 Realm을 계층화하여 공통 설정은 상속받고, 환경별 차이만 오버라이드할 수 있습니다.

### 모듈 구조와 설계 의도
- **Realm Attributes 기반 저장**: 별도 DB 테이블 없이 Keycloak 내장 Realm Attributes를 JSON 직렬화하여 저장, 운영 복잡도를 최소화했습니다.
- **Event-Driven 동기화**: `RealmHierarchyEventListener`가 REALM, IDENTITY_PROVIDER, REALM_ROLE 이벤트를 감지하여 자식 Realm에 자동 전파합니다.
- **InheritanceManager 패턴**: 상속 로직을 별도 클래스로 분리하여 IdP/Role/AuthFlow별 상속 전략을 독립적으로 구현할 수 있습니다.
- **상속 메타데이터 추적**: `hierarchy.inherited`, `hierarchy.source_realm` 속성으로 상속된 설정을 추적하여 오버라이드 방지 및 업데이트 전략을 구현합니다.
- **Tier 개념 도입**: `standard`, `enterprise`, `premium` 등 계층 티어로 기능 제한이나 라이선스 정책을 구현할 수 있습니다.
- **REST API 제공**: `/realms/{realm}/hierarchy` 엔드포인트로 Admin Console 없이도 계층 구조를 프로그래밍 방식으로 관리할 수 있습니다.

## 요구사항

- **Keycloak**: 26.0.7 이상
- **Java**: 21 이상
- **Kotlin**: 2.2.21

## 빌드 방법

```bash
# 전체 프로젝트 빌드
./gradlew build

# realm-hierarchy 모듈만 빌드
./gradlew :realms:realm-hierarchy:build

# Shadow JAR 생성 (배포용)
./gradlew :realms:realm-hierarchy:shadowJar
```

빌드 후 `realms/realm-hierarchy/build/libs/` 디렉터리에 JAR 파일이 생성됩니다.

## 설치 방법

### 1. JAR 파일 복사

```bash
# Keycloak providers 디렉터리에 복사
cp realms/realm-hierarchy/build/libs/realm-hierarchy-all.jar $KEYCLOAK_HOME/providers/
```

### 2. Keycloak 재빌드 및 재시작

```bash
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

### 3. 설치 확인

Admin Console에서 Events → Config → Event Listeners에 `realm-hierarchy`가 표시되는지 확인합니다.

## 사용 방법

### 1. REST API를 통한 계층 구조 설정

#### 부모 Realm 설정

```bash
curl -X POST "http://localhost:8080/realms/child-realm/hierarchy/parent" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "parentRealmId": "parent-realm-id",
    "inheritIdp": true,
    "inheritRoles": true,
    "inheritAuthFlow": false
  }'
```

#### 계층 구조 조회

```bash
curl -X GET "http://localhost:8080/realms/child-realm/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

응답 예시:
```json
{
  "realmId": "child-realm-id",
  "realmName": "child-realm",
  "parentRealmId": "parent-realm-id",
  "parentRealmName": "parent-realm",
  "tier": "standard",
  "depth": 1,
  "path": "/parent-realm/child-realm",
  "inheritIdp": true,
  "inheritAuthFlow": false,
  "inheritRoles": true,
  "children": []
}
```

#### 전체 경로 조회 (루트까지)

```bash
curl -X GET "http://localhost:8080/realms/child-realm/hierarchy/path" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

#### 상속 설정 업데이트

```bash
curl -X PUT "http://localhost:8080/realms/child-realm/hierarchy/inheritance" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "inheritIdp": true,
    "inheritRoles": false,
    "inheritAuthFlow": false
  }'
```

#### 계층 구조 동기화

부모 Realm의 변경사항을 모든 하위 Realm에 전파:

```bash
curl -X POST "http://localhost:8080/realms/parent-realm/hierarchy/synchronize" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

#### 계층 구조 제거

```bash
curl -X DELETE "http://localhost:8080/realms/child-realm/hierarchy" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 2. Event Listener를 통한 자동 동기화

#### Realm에 Event Listener 추가

1. Keycloak Admin Console 접속
2. 해당 Realm 선택
3. **Events** → **Config** 이동
4. **Event Listeners** 섹션에서 `realm-hierarchy` 선택 및 저장

#### 지원 이벤트

Event Listener는 다음 이벤트를 감지하여 자동으로 하위 Realm에 전파합니다:

- **REALM**: Realm 설정 변경
- **IDENTITY_PROVIDER**: IdP 추가/수정/삭제
- **REALM_ROLE**: Role 추가/수정/삭제

### 3. 계층 구조 설계 예시

#### 멀티테넌트 SaaS

```
master-realm (공통 IdP: Google, GitHub)
├── enterprise-tenant-1
│   ├── team-a
│   └── team-b
└── enterprise-tenant-2
    ├── department-1
    └── department-2
```

설정:
- `enterprise-tenant-1`은 `master-realm`의 IdP를 상속받음
- `team-a`, `team-b`는 `enterprise-tenant-1`의 설정을 상속받음

#### 개발/스테이징/프로덕션 환경

```
base-realm (공통 Role: user, admin)
├── dev-realm (inheritRoles: true)
├── staging-realm (inheritRoles: true)
└── prod-realm (inheritRoles: true, inheritIdp: true)
```

설정:
- 모든 환경은 공통 Role을 상속받음
- 프로덕션만 추가로 IdP 설정도 상속받음

## 설정 상세

### Realm Attributes 저장 포맷

계층 구조 정보는 다음과 같이 Realm Attributes에 JSON으로 저장됩니다:

```json
{
  "hierarchy.node": "{\"realmId\":\"child-realm-id\",\"realmName\":\"child-realm\",\"parentRealmId\":\"parent-realm-id\",\"tier\":\"standard\",\"depth\":1,\"path\":\"/parent-realm/child-realm\",\"inheritIdp\":true,\"inheritAuthFlow\":false,\"inheritRoles\":true,\"createdAt\":1704067200000,\"updatedAt\":1704067200000}"
}
```

### 상속 가능한 설정

#### Identity Provider
- 부모 Realm의 모든 IdP가 자식 Realm에 복제됨
- 상속된 IdP는 `hierarchy.inherited=true` 속성으로 표시됨
- 이미 존재하는 IdP는 덮어쓰지 않음 (Alias 기준)

#### Realm Role
- 부모 Realm의 모든 Role이 자식 Realm에 복제됨
- 상속된 Role은 `hierarchy.inherited=true` 속성으로 표시됨
- 이미 존재하는 Role은 덮어쓰지 않음 (Name 기준)

#### Authentication Flow
- 향후 지원 예정 (복잡한 엔티티 관계로 인해 현재는 미구현)

### 제약사항

- **최대 계층 깊이**: 10단계
- **순환 참조 방지**: A → B → A 형태의 구조는 차단됨
- **상속 방향**: 부모 → 자식만 가능 (역방향 불가)
- **수동 동기화 필요**: Event Listener 비활성화 시 `/synchronize` API 호출 필요

## 아키텍처

### 주요 컴포넌트

```
RealmHierarchyEventListenerFactory
  └── RealmHierarchyEventListener
        ├── RealmHierarchyStorage (Realm Attributes 기반 CRUD)
        └── InheritanceManager (상속 로직)

RealmHierarchyResourceProviderFactory
  └── RealmHierarchyResourceProvider
        └── RealmHierarchyResource (REST API)
              ├── RealmHierarchyStorage
              └── InheritanceManager
```

### 데이터 모델

```kotlin
data class RealmHierarchyNode(
    val realmId: String,
    val realmName: String,
    val parentRealmId: String? = null,
    val tier: String = "standard",
    val depth: Int = 0,
    val path: String = "/",
    val inheritIdp: Boolean = false,
    val inheritAuthFlow: Boolean = false,
    val inheritRoles: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

## REST API 명세

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/realms/{realm}/hierarchy` | 현재 Realm의 계층 정보 조회 |
| GET | `/realms/{realm}/hierarchy/path` | 루트까지의 전체 경로 조회 |
| POST | `/realms/{realm}/hierarchy/parent` | 부모 Realm 설정 |
| PUT | `/realms/{realm}/hierarchy/inheritance` | 상속 설정 업데이트 |
| POST | `/realms/{realm}/hierarchy/synchronize` | 하위 Realm 동기화 |
| DELETE | `/realms/{realm}/hierarchy` | 계층 구조 제거 |

## 테스트

```bash
# Unit 테스트
./gradlew :realms:realm-hierarchy:test

# 전체 빌드 + 테스트
./gradlew :realms:realm-hierarchy:build
```

테스트 커버리지:
- ✅ RealmHierarchyNode (4 tests)
- ✅ InheritanceManager (8 tests)
- ✅ RealmHierarchyEventListener (7 tests)

## 문제 해결

### Event Listener가 동작하지 않음

**증상**: 부모 Realm의 변경이 자식 Realm에 전파되지 않음

**해결**:
1. Events → Config에서 `realm-hierarchy` Event Listener 활성화 확인
2. Keycloak 로그 확인: `grep "RealmHierarchyEventListener" keycloak.log`
3. 수동 동기화 실행: `POST /realms/{realm}/hierarchy/synchronize`

### 순환 참조 에러

**증상**: "Circular reference detected" 에러 발생

**원인**: A → B → A 형태의 순환 구조 감지

**해결**: 계층 구조를 트리 형태로 재설계

### 상속된 설정이 업데이트되지 않음

**증상**: 부모 Realm의 IdP 변경이 자식 Realm에 반영되지 않음

**해결**:
1. Event Listener 활성화 확인
2. 상속 설정 확인: `GET /realms/{realm}/hierarchy`
3. `inheritIdp: true`인지 확인
4. 수동 동기화: `POST /realms/parent-realm/hierarchy/synchronize`

## 성능 고려사항

- **Event Listener 오버헤드**: Realm 설정 변경 시 하위 Realm 수에 비례하여 처리 시간 증가
- **권장 구조**: 계층 깊이 3단계 이내, 자식 Realm 수 100개 이하
- **배치 동기화**: 대량 변경 시 Event Listener 비활성화 후 `/synchronize` API 일괄 호출 권장

## 라이선스

Apache License 2.0

Copyright 2025 ScriptonBasestar

## 기여

이슈 제보 및 Pull Request 환영합니다.
- GitHub: https://github.com/ScriptonBasestar/sb-keycloak-exts

## 참고 자료

- [Keycloak Server Development Guide](https://www.keycloak.org/docs/latest/server_development/)
- [Keycloak Server SPI Documentation](https://www.keycloak.org/docs-api/latest/javadocs/)
