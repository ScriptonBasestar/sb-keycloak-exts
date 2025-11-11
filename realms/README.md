# Keycloak Realm Management Extensions

Keycloak Realm 관리 확장 모듈 모음

## 개요 (Overview)

이 디렉토리는 Keycloak의 Realm 관리와 관련된 확장 기능들을 포함합니다. Keycloak은 기본적으로 Realm을 독립적으로 관리하지만, 이 확장들을 통해 고급 Realm 관리 기능을 제공합니다.

This directory contains Keycloak extensions for advanced Realm management capabilities.

## 포함된 모듈 (Modules)

### 1. [realm-hierarchy](realm-hierarchy/)

Realm 계층 구조 관리 확장

**주요 기능:**
- Realm 간 부모-자식 관계 설정
- 상위 Realm 설정 상속 (IdP, Auth Flow, Roles 등)
- REST API를 통한 계층 구조 관리
- 계층별 권한 위임

**사용 사례:**
- 엔터프라이즈 SaaS (대기업 → 자회사 → 지사)
- 대규모 조직 (본사 → 부서 → 팀)
- 멀티테넌트 플랫폼 (플랫폼 → 고객 → 환경)

## 빌드 (Build)

### 전체 빌드

```bash
./gradlew :realms:build
```

### 특정 모듈 빌드

```bash
./gradlew :realms:realm-hierarchy:shadowJar
```

## 설치 (Installation)

### 1. JAR 파일 생성

```bash
./gradlew :realms:shadowJar
```

### 2. Keycloak에 배포

```bash
cp realms/*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

## 향후 계획 (Roadmap)

### 계획 중인 모듈:

#### realm-template
- Realm 템플릿 기능
- 사전 구성된 Realm 설정을 빠르게 적용
- 업계별 템플릿 제공 (금융, 의료, 게임 등)

#### realm-sync
- Multi-Keycloak 인스턴스 간 Realm 동기화
- DR(재해 복구) 시나리오 지원
- Active-Active Realm 복제

#### realm-lifecycle
- Realm 생명주기 관리
- 자동 프로비저닝/디프로비저닝
- 사용량 기반 Realm 정리

#### realm-backup
- Realm 설정 백업/복원
- 스케줄링 백업
- S3/MinIO/Azure Blob 통합

## 아키텍처 (Architecture)

### 디렉토리 구조

```
realms/
├── build.gradle                    # 공통 Gradle 설정
├── README.md                       # 이 파일
│
├── realm-hierarchy/               # Realm 계층 구조 관리
│   ├── build.gradle
│   ├── README.md
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
│
├── realm-template/                # (미래) Realm 템플릿
├── realm-sync/                    # (미래) Realm 동기화
└── realm-lifecycle/               # (미래) Realm 생명주기 관리
```

### 공통 의존성

모든 Realm 관리 모듈은 다음을 공유합니다:

- Keycloak Core/Services API
- Jackson (JSON 처리)
- Kotlin Standard Library
- JUnit 5 (테스트)

## 개발 가이드 (Development Guide)

### 새로운 Realm 확장 추가

1. **디렉토리 생성**
   ```bash
   mkdir -p realms/realm-myextension/src/{main,test}/{kotlin,resources}
   ```

2. **build.gradle 생성**
   ```gradle
   dependencies {
       // 추가 의존성
   }

   shadowJar {
       archiveBaseName.set('realm-myextension')
       archiveClassifier.set('all')
   }
   ```

3. **settings.gradle에 등록**
   ```gradle
   include 'realms:realm-myextension'
   ```

4. **SPI 구현**
   - `RealmProvider` 또는 `EventListenerProvider` 구현
   - `META-INF/services/` 등록

### 테스트

```bash
# 단위 테스트
./gradlew :realms:realm-hierarchy:test

# 통합 테스트
./gradlew :realms:realm-hierarchy:integrationTest
```

## 라이선스 (License)

Apache License 2.0

Copyright 2025 ScriptonBasestar

## 참고 자료 (References)

- [Keycloak Server Development](https://www.keycloak.org/docs/latest/server_development/)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/)
- [Keycloak SPI Documentation](https://www.keycloak.org/docs/latest/server_development/#_providers)
