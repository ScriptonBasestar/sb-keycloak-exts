# Keycloak Realm Hierarchy Manager

Keycloak Realm 계층 구조 관리 확장 모듈

## 개요 (Overview)

Keycloak은 기본적으로 Realm 간 계층 구조를 지원하지 않습니다. 이 확장 모듈은 Realm 간 부모-자식 관계를 설정하고, 상위 Realm의 설정을 하위 Realm이 상속받을 수 있도록 합니다.

This extension provides hierarchical structure management for Keycloak Realms, allowing parent-child relationships and configuration inheritance between Realms.

## 주요 기능 (Features)

### 1. Realm 계층 구조 메타데이터 관리
- Realm 간 부모-자식 관계 정의
- 계층 깊이 및 경로 추적
- 순환 참조 방지

### 2. 설정 상속 (Configuration Inheritance)
- Identity Provider 상속
- Authentication Flow 상속
- Client 템플릿 상속
- Role 및 Group 상속

### 3. REST API 제공
- 계층 구조 조회/수정 API
- 상속 설정 관리 API
- 계층 구조 검증 API

## 사용 사례 (Use Cases)

### 시나리오 1: 엔터프라이즈 SaaS
```
Master Realm
└── Enterprise-A Realm (parent)
    ├── Subsidiary-1 Realm
    ├── Subsidiary-2 Realm
    └── Branch-X Realm
```

### 시나리오 2: 대규모 조직
```
Corporate Realm (parent)
├── Department-Sales Realm
├── Department-Engineering Realm
│   ├── Team-Backend Realm
│   └── Team-Frontend Realm
└── Department-HR Realm
```

### 시나리오 3: 멀티테넌트 플랫폼
```
Platform Realm (parent)
├── Tenant-A Realm (tier: enterprise)
│   └── Tenant-A-Dev Realm
├── Tenant-B Realm (tier: business)
└── Tenant-C Realm (tier: basic)
```

## 설치 (Installation)

### 1. JAR 파일 빌드

```bash
./gradlew :realms:realm-hierarchy:shadowJar
```

### 2. Keycloak에 배포

```bash
cp realms/realm-hierarchy/build/libs/realm-hierarchy-all.jar \
   $KEYCLOAK_HOME/providers/

$KEYCLOAK_HOME/bin/kc.sh build
```

### 3. Keycloak 재시작

```bash
$KEYCLOAK_HOME/bin/kc.sh start
```

## 설정 (Configuration)

### Realm 계층 구조 생성

Keycloak Admin Console에서:

1. **Realm Settings** → **Attributes** 이동
2. 다음 속성 추가:
   ```
   parent_realm_id: <부모 Realm ID>
   hierarchy_tier: enterprise|business|basic
   inherit_idp: true|false
   inherit_auth_flow: true|false
   ```

### REST API로 설정

```bash
# 계층 구조 조회
GET /realms/{realm}/hierarchy

# 부모 Realm 설정
POST /realms/{realm}/hierarchy/parent
{
  "parentRealmId": "enterprise-a",
  "inheritSettings": true
}

# 상속 설정 조회
GET /realms/{realm}/hierarchy/inheritance
```

## 아키텍처 (Architecture)

### 데이터베이스 스키마

```sql
CREATE TABLE realm_hierarchy (
    realm_id VARCHAR(36) PRIMARY KEY,
    parent_realm_id VARCHAR(36),
    tier VARCHAR(50),
    depth INTEGER,
    path VARCHAR(500),
    inherit_idp BOOLEAN DEFAULT false,
    inherit_auth_flow BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (realm_id) REFERENCES realm(id),
    FOREIGN KEY (parent_realm_id) REFERENCES realm(id)
);

CREATE INDEX idx_realm_parent ON realm_hierarchy(parent_realm_id);
CREATE INDEX idx_realm_path ON realm_hierarchy(path);
```

### 주요 컴포넌트

```
realm-hierarchy/
├── provider/
│   ├── HierarchicalRealmProvider.kt       # Realm SPI 구현
│   └── HierarchicalRealmProviderFactory.kt
├── storage/
│   ├── RealmHierarchyStorage.kt           # DB 접근 레이어
│   └── RealmHierarchyEntity.kt            # 데이터 모델
├── inheritance/
│   ├── IdentityProviderInheritance.kt     # IdP 상속 로직
│   └── AuthFlowInheritance.kt             # Auth Flow 상속 로직
└── api/
    └── RealmHierarchyResource.kt          # REST API
```

## 제약사항 (Limitations)

1. **완전한 격리 불가**: 같은 데이터베이스 사용
2. **성능 고려**: 깊은 계층 구조는 성능 영향
3. **순환 참조 방지**: 부모-자식 관계 검증 필수
4. **마이그레이션**: 기존 Realm에 적용 시 수동 작업 필요

## 개발 (Development)

### 빌드

```bash
./gradlew :realms:realm-hierarchy:build
```

### 테스트

```bash
./gradlew :realms:realm-hierarchy:test
```

### 로컬 개발

```bash
# Docker Compose로 Keycloak 실행
docker-compose up -d keycloak

# JAR 자동 빌드 및 배포
./gradlew :realms:realm-hierarchy:shadowJar
cp realms/realm-hierarchy/build/libs/realm-hierarchy-all.jar \
   docker/keycloak/providers/
docker-compose restart keycloak
```

## 라이선스 (License)

Apache License 2.0

Copyright 2025 ScriptonBasestar

## 참고 자료 (References)

- [Keycloak Server Development Guide](https://www.keycloak.org/docs/latest/server_development/)
- [Keycloak Server SPI](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/models/RealmProvider.html)
