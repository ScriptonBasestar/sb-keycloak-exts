# Keycloak Extensions Makefile
# 프로젝트 편의 명령어들을 제공합니다.

.PHONY: help lint format lint-fix build test clean check install-hooks themes

# 기본 타겟
.DEFAULT_GOAL := help

# 도움말 출력
help:
	@echo "Available commands:"
	@echo "  make lint        - 코드 린트 체크 (ktlint + detekt)"
	@echo "  make format      - 코드 자동 포맷팅 (ktlint)"
	@echo "  make lint-fix    - 린트 체크 + 자동 수정"
	@echo "  make build       - 전체 프로젝트 빌드"
	@echo "  make test        - 테스트 실행"
	@echo "  make clean       - 빌드 아티팩트 정리"
	@echo "  make check       - 모든 품질 검사 (lint + test)"
	@echo "  make install-hooks - Git pre-commit 훅 설치"
	@echo "  make shadow      - Shadow JAR 생성"
	@echo "  make dependency-check - 의존성 보안 검사"
	@echo "  make themes      - 테마 JAR 빌드"
	@echo "  make themes-deploy - 로컬 Keycloak에 테마 배포"

# 코드 포맷팅
format:
	@echo "🔧 코드 포맷팅 실행 중..."
	./gradlew ktlintFormat

# 린트 체크 (오류 시 종료)
lint:
	@echo "🔍 코드 린트 체크 실행 중..."
	./gradlew ktlintCheck detekt

# 린트 체크 + 자동 수정
lint-fix:
	@echo "🔧 린트 체크 및 자동 수정 실행 중..."
	./gradlew ktlintFormat detekt

# 전체 빌드
build:
	@echo "🔨 프로젝트 빌드 실행 중..."
	./gradlew build

# 테스트 실행
test:
	@echo "🧪 테스트 실행 중..."
	./gradlew test

# 통합 테스트 실행
integration-test:
	@echo "🧪 통합 테스트 실행 중..."
	./gradlew integrationTest

# 빌드 아티팩트 정리
clean:
	@echo "🧹 빌드 아티팩트 정리 중..."
	./gradlew clean

# 모든 품질 검사 실행
check:
	@echo "✅ 모든 품질 검사 실행 중..."
	./gradlew check

# Shadow JAR 생성
shadow:
	@echo "📦 Shadow JAR 생성 중..."
	./gradlew shadowJar

# 의존성 보안 검사
dependency-check:
	@echo "🔒 의존성 보안 검사 실행 중..."
	./gradlew dependencyCheckAnalyze

# 테마 빌드
themes:
	@echo "🎨 테마 JAR 빌드 중..."
	./gradlew :themes:buildThemes
	@echo "✅ 테마 빌드 완료:"
	@ls -lh themes/build/libs/*.jar

# 테마 로컬 배포
themes-deploy:
	@echo "🚀 로컬 Keycloak에 테마 배포 중..."
	./gradlew :themes:deployThemesLocal
	@echo "✅ 테마 배포 완료. 다음 명령어를 실행하세요:"
	@echo "  cd \$$KEYCLOAK_HOME"
	@echo "  ./bin/kc.sh build"
	@echo "  ./bin/kc.sh start"

# Git pre-commit 훅 설치
install-hooks:
	@echo "⚙️ Git pre-commit 훅 설치 중..."
	./gradlew addKtlintCheckGitPreCommitHook

# 개발 환경 설정
dev-setup: install-hooks
	@echo "🚀 개발 환경 설정 완료"
	@echo "다음 명령어들을 사용할 수 있습니다:"
	@echo "  make format  - 코드 포맷팅"
	@echo "  make lint    - 코드 린트 체크"
	@echo "  make build   - 프로젝트 빌드"
	@echo "  make test    - 테스트 실행"

# CI/CD용 명령어
ci: clean lint build test
	@echo "🎯 CI/CD 파이프라인 완료"

# 릴리즈용 명령어
release: clean lint build test shadow dependency-check
	@echo "🚀 릴리즈 준비 완료"
	@echo "Generated JAR files:"
	@find . -name "*.jar" -path "*/build/libs/*" -type f

# 프로젝트 상태 확인
status:
	@echo "📊 프로젝트 상태:"
	@echo "Gradle version: $(shell ./gradlew --version | grep Gradle | cut -d' ' -f2)"
	@echo "Java version: $(shell java -version 2>&1 | head -n1 | cut -d'"' -f2)"
	@echo "Kotlin version: $(shell ./gradlew -q kotlinVersion 2>/dev/null || echo 'Unknown')"
	@echo "Project version: $(shell ./gradlew -q printVersion 2>/dev/null || echo 'Unknown')"