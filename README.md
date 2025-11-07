# Keycloak Extensions - Korean Social Identity Providers

말 그대로 Keycloak의 확장 기능!!! Keycloak Extensions for Korean Social Logins!

## 프로젝트 개요 (Overview)

이 프로젝트는 한국의 주요 소셜 로그인 서비스들을 Keycloak과 통합할 수 있는 Identity Provider 확장을 제공합니다.

This project provides Keycloak identity provider extensions for popular Korean social login services and international OAuth2 providers.

### 지원하는 소셜 로그인 (Supported Providers)

#### 한국 소셜 로그인 서비스
- **Kakao (카카오)** - 한국에서 가장 인기 있는 메시징 플랫폼
- **LINE (라인)** - 한국과 일본에서 널리 사용되는 메시징 앱
- **Naver (네이버)** - 한국의 대표적인 검색 엔진 및 웹 포털

#### 글로벌 소셜 로그인 서비스
- **Google** - 구글 계정으로 로그인 (OpenID Connect 지원)
- **GitHub** - 깃허브 계정으로 로그인

## 주요 기능 (Features)

- 각 플랫폼과의 완전한 OAuth2 통합
- 자동 사용자 속성 매핑
- 프로필 이미지, 이메일 등 사용자 속성 지원
- 커스텀 속성 매퍼 지원
- 포괄적인 에러 처리로 프로덕션 환경 대응
- 모든 컴포넌트에 대한 단위 테스트
- GitHub Actions CI/CD 파이프라인 지원

## 요구사항 (Requirements)

- Keycloak 26.3.1 이상
- Java 21 이상
- Gradle 8.8 (wrapper 포함)

## 빠른 시작 (Quick Start)

### 프로젝트 빌드

1. 저장소 클론:
   ```bash
   git clone https://github.com/yourusername/sb-keycloak-exts.git
   cd sb-keycloak-exts
   ```

2. 모든 provider 빌드:
   ```bash
   ./gradlew shadowJar
   ```

3. JAR 파일 생성 위치:
   - `idps/idp-kakao/build/libs/idp-kakao-*-all.jar`
   - `idps/idp-line/build/libs/idp-line-*-all.jar`
   - `idps/idp-naver/build/libs/idp-naver-*-all.jar`
   - `idps/idp-google/build/libs/idp-google-*-all.jar`
   - `idps/idp-github/build/libs/idp-github-*-all.jar`

### 설치

1. 원하는 provider JAR를 Keycloak에 복사:
   ```bash
   cp idps/idp-*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/
   ```

2. Keycloak 재시작:
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh start
   ```

## 설정 가이드 (Configuration Guide)

### 1. 소셜 애플리케이션 설정

각 소셜 플랫폼에서 애플리케이션을 생성해야 합니다:

#### 한국 소셜 플랫폼
- **Kakao**: [Kakao Developers Console](https://developers.kakao.com/)
- **LINE**: [LINE Developers Console](https://developers.line.biz/console/)
- **Naver**: [Naver Developers Console](https://developers.naver.com/apps/)

#### 글로벌 소셜 플랫폼
- **Google**: [Google Cloud Console](https://console.cloud.google.com/)
- **GitHub**: [GitHub Developer Settings](https://github.com/settings/developers)

자세한 설정 방법은 각 provider의 README를 참조하세요:
- [Kakao 설정 가이드](idps/idp-kakao/README.md)
- [LINE 설정 가이드](idps/idp-line/README.md)
- [Naver 설정 가이드](idps/idp-naver/README.md)
- [Google 설정 가이드](idps/idp-google/README.md)
- [GitHub 설정 가이드](idps/idp-github/README.md)

### 2. Keycloak 관리 콘솔 설정

1. Keycloak 관리 콘솔 로그인
2. Realm 선택
3. **Identity Providers** 메뉴로 이동
4. **Add provider** 드롭다운 클릭
5. 원하는 provider 선택 (Kakao, LINE, Naver, Google, 또는 GitHub)
6. Provider 설정 입력

### 3. Redirect URI 설정

각 provider는 다음 형식의 redirect URI를 설정해야 합니다:

```
https://your-keycloak-domain.com/realms/{realm}/broker/{provider}/endpoint
```

`{provider}`는 `kakao`, `line`, `naver`, `google`, 또는 `github` 중 하나입니다.

## 테스트 (Testing)

### 단위 테스트 (Unit Tests)

모든 단위 테스트 실행:
```bash
./gradlew test
```

특정 provider 테스트:
```bash
./gradlew :idps:idp-kakao:test
./gradlew :idps:idp-line:test
./gradlew :idps:idp-naver:test
./gradlew :idps:idp-google:test
./gradlew :idps:idp-github:test
```

### 통합 테스트 (Integration Tests)

Event Listener 모듈들은 TestContainers 기반 통합 테스트를 제공합니다:

**통합 테스트 실행 (Docker 필요):**
```bash
# Kafka 통합 테스트
./gradlew :events:event-listener-kafka:integrationTest

# RabbitMQ 통합 테스트
./gradlew :events:event-listener-rabbitmq:integrationTest

# Redis 통합 테스트
./gradlew :events:event-listener-redis:integrationTest

# NATS 통합 테스트
./gradlew :events:event-listener-nats:integrationTest
```

**주의사항:**
- 통합 테스트는 Docker가 실행 중이어야 합니다
- TestContainers가 자동으로 컨테이너를 시작/중지합니다
- CI/CD 파이프라인에서는 릴리즈 시에만 자동 실행됩니다
- 수동 실행 시: GitHub Actions → integration-tests 워크플로우 → "Run workflow" 클릭

### 테스트 커버리지

**Identity Providers (단위 테스트):**
- OAuth2 플로우 테스트
- 사용자 프로필 매핑 테스트
- 에러 처리 시나리오 테스트
- JSON 파싱 및 데이터 변환 테스트

**Event Listeners (통합 테스트):**
- 컨테이너 시작 및 연결 확인
- 메시지 발행/구독 테스트
- Keycloak Realm 설정 검증
- 성능 테스트 (메시지 처리량)
- Keycloak 이벤트 → 메시징 시스템 E2E 테스트

## 프로젝트 구조 (Project Structure)

```
sb-keycloak-exts/
├── .github/
│   └── workflows/         # GitHub Actions 워크플로우
│       ├── ci.yml         # CI 파이프라인
│       └── release.yml    # 릴리즈 자동화
├── build.gradle           # 루트 빌드 설정
├── gradle.properties      # Gradle 속성
├── settings.gradle        # 멀티 모듈 설정
└── idps/                  # Identity providers 모듈
    ├── build.gradle       # IDP 모듈 설정
    ├── idp-kakao/         # Kakao provider
    │   ├── src/
    │   │   ├── main/      # 소스 코드
    │   │   └── test/      # 테스트 코드
    │   └── README.md      # Kakao 설정 가이드
    ├── idp-line/          # LINE provider
    │   ├── src/
    │   │   ├── main/      # 소스 코드
    │   │   └── test/      # 테스트 코드
    │   └── README.md      # LINE 설정 가이드
    ├── idp-naver/         # Naver provider
    │   ├── src/
    │   │   ├── main/      # 소스 코드
    │   │   └── test/      # 테스트 코드
    │   └── README.md      # Naver 설정 가이드
    ├── idp-google/        # Google provider
    │   ├── src/
    │   │   ├── main/      # 소스 코드
    │   │   └── test/      # 테스트 코드
    │   └── README.md      # Google 설정 가이드
    └── idp-github/        # GitHub provider
        ├── src/
        │   ├── main/      # 소스 코드
        │   └── test/      # 테스트 코드
        └── README.md      # GitHub 설정 가이드
```

## 문제 해결 (Troubleshooting)

### 일반적인 문제

1. **Provider가 Keycloak에 나타나지 않음**
   - JAR 파일이 올바른 디렉토리에 있는지 확인
   - Keycloak 로그에서 로딩 오류 확인
   - shadowJar로 빌드했는지 확인

2. **인증 실패**
   - Client ID와 Secret이 올바른지 확인
   - Redirect URI 설정 확인
   - 소셜 플랫폼에서 필요한 권한이 활성화되었는지 확인

## 중장기 개선 방향 (Future Improvements)

1. **CI/CD 파이프라인** ✅ 완료
   - GitHub Actions 워크플로우 구성
   - 자동 테스트 및 배포
   - 다중 Java 버전 테스트 (17, 21)

2. **추가 IDP 구현** 🔄 진행중
   - Google ✅ 완료
   - GitHub ✅ 완료
   - Discord (https://github.com/wadahiro/keycloak-discord)
   - Apple (https://github.com/klausbetz/apple-identity-provider-keycloak)
   - 공통 코드 추출하여 중복 제거

3. **이벤트 리스너**
   - Kafka (https://github.com/softwarefactory-project/keycloak-event-listener-mqtt)
   - RabbitMQ (https://github.com/aznamier/keycloak-event-listener-rabbitmq)
   - 로그인 이벤트 스트리밍

4. **보안 및 모니터링**
   - 로깅 프레임워크 추가
   - 메트릭 수집 기능

5. **테마 지원**
   - Keycloakify 통합 (https://www.keycloakify.dev/)
   - 한국어 UI 개선

## 버전 관리 (Versioning)

이 프로젝트는 [Semantic Versioning](https://semver.org/)을 따릅니다.

## 릴리즈 (Release)

릴리즈는 GitHub의 태그 기반으로 자동화되어 있습니다:

```bash
git tag v1.0.0
git push origin v1.0.0
```

태그 푸시 시 GitHub Actions가 자동으로:
- 빌드 및 테스트 실행
- Shadow JAR 생성
- GitHub Release 생성
- JAR 파일 업로드

## 라이선스 (License)

이 프로젝트는 MIT 라이선스로 배포됩니다.

## 기여하기 (Contributing)

기여를 환영합니다! Pull Request를 보내주세요.

## 참고 자료 (References)

- [Keycloak Extensions](https://www.keycloak.org/extensions.html)
- [Keycloak Server Developer Guide](https://www.keycloak.org/docs/latest/server_development/)