# title: OAuth2 Provider 기능 고도화 QA 시나리오

## related_tasks
- /tasks/done/github-oauth2-enhancement.md
- /tasks/done/google-oauth2-enhancement.md

## purpose
GitHub 및 Google OAuth2 Provider의 고도화된 기능들이 다양한 시나리오에서 정상 작동하는지 확인

## scenario

### 1. GitHub OAuth2 고급 기능 테스트
1. **GitHub Enterprise Server 연동 테스트**
   - Keycloak 관리자 콘솔에서 GitHub IDP 설정
   - Enterprise Server URL 설정
   - 조직 멤버십 검증 기능 활성화
   - 테스트 사용자로 로그인 시도
   - 조직 멤버십에 따른 접근 제어 확인

2. **PKCE 보안 기능 검증**
   - 브라우저 개발자 도구에서 OAuth2 플로우 확인
   - code_challenge/code_verifier 파라미터 존재 확인
   - CSRF 공격 시뮬레이션 테스트
   - State 파라미터 변조 테스트

3. **GitHub SSH Key 검증**
   - SSH Key가 등록된 사용자 로그인
   - SSH Key가 없는 사용자 로그인 시도
   - 2FA 활성화된 사용자 로그인
   - 속성 매핑에서 SSH Key 정보 확인

### 2. Google OAuth2 고급 기능 테스트
1. **Google Workspace 도메인 제한**
   - 허용된 도메인 사용자 로그인
   - 제한된 도메인 사용자 로그인 시도
   - HD 파라미터 검증 확인
   - 도메인 제한 우회 시도

2. **Google People API 통합**
   - 고해상도 프로필 이미지 로드 확인
   - 조직 정보 매핑 검증
   - 언어/지역 설정 자동 적용
   - Google Groups 멤버십 동기화

3. **ID Token 검증 강화**
   - JWT 서명 검증 확인
   - Audience 검증 테스트
   - 만료된 토큰 처리 확인
   - 위조된 토큰 탐지 테스트

### 3. 통합 시나리오 테스트
1. **다중 IDP 동시 사용**
   - 같은 이메일로 GitHub/Google 동시 로그인
   - 계정 연결 및 분리 기능
   - 속성 충돌 해결 확인
   - 세션 관리 정상 동작

2. **엔터프라이즈 기능 검증**
   - 조직/팀 구조 자동 매핑
   - 실시간 계정 상태 동기화
   - Audit 로그 수집 확인
   - 권한 기반 접근 제어

## expected_result
- **GitHub OAuth2**: 모든 고급 기능이 정상 동작하며 보안 요구사항 충족
- **Google OAuth2**: Workspace 통합 및 확장 속성이 정확히 매핑
- **보안**: PKCE, ID Token 검증 등 최신 보안 표준 준수
- **속성 매핑**: 조직/팀 정보가 Keycloak 그룹에 정확히 반영
- **에러 처리**: 인증 실패 시 적절한 에러 메시지 표시
- **성능**: 로그인 플로우가 2초 이내 완료
- **로그**: 모든 인증 이벤트가 Audit 로그에 기록

## tags
[qa], [oauth2], [manual], [integration], [security]