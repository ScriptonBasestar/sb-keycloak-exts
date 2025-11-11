# Self-Service API - Testing Guide

## Overview

This module uses a **two-tier testing strategy** following the project's standard pattern established in the event-listener modules:

1. **Unit Tests** (`src/test/kotlin`) - Fast, isolated, Mockito-based tests
2. **Integration Tests** (`src/integrationTest/kotlin`) - TestContainers-based E2E tests (future work)

## Test Framework & Dependencies

### Core Testing Stack
- **JUnit 5 (Jupiter)** - Test framework
- **Mockito Kotlin** - Mocking library (project standard)
- **Kotlin Test** - Kotlin-specific assertions
- **RESTEasy** - JAX-RS implementation for Response building

### Why Mockito (Not MockK)?
This project uses **Mockito** to maintain consistency with other modules (event-listener-kafka, event-listener-common). All test fixtures and patterns follow the same approach.

## Running Tests

### Run All Unit Tests
```bash
./gradlew :self-service:self-service-api:test
```

### Run Specific Test Class
```bash
./gradlew :self-service:self-service-api:test --tests "*RegistrationWorkflowTest"
./gradlew :self-service:self-service-api:test --tests "*PasswordResourceTest"
```

### Run Specific Test Method
```bash
./gradlew :self-service:self-service-api:test \
  --tests "*.RegistrationWorkflowTest.register should create user successfully"
```

### Run with Coverage
```bash
./gradlew :self-service:self-service-api:test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### Future: Integration Tests (TestContainers)
```bash
./gradlew :self-service:self-service-api:integrationTest
```

## Test Structure

### Test Fixtures
All reusable mock builders are centralized in:
```
src/test/kotlin/org/scriptonbasestar/kcexts/selfservice/test/SelfServiceTestFixtures.kt
```

Following the pattern from `event-listener-common`, this provides:
- `createMockSession()` - KeycloakSession with context, realm, users
- `createMockRealm()` - RealmModel with password policy
- `createMockUser()` - UserModel with credentials, attributes
- `createMockUserProvider()` - UserProvider for user lookup

### Test Suites

#### 1. RegistrationWorkflowTest (13 tests)
Tests for user registration and email verification:

**Registration Validation:**
- ✅ Successful user creation
- ✅ Duplicate username detection
- ✅ Duplicate email detection
- ✅ Username length validation (min 3 characters)
- ✅ Email format validation
- ✅ Password length validation (min 8 characters)
- ✅ Password confirmation matching

**Email Verification:**
- ✅ Successful email verification
- ✅ Invalid token handling
- ✅ Expired token handling

**Resend Verification:**
- ✅ New token generation
- ✅ Email not found handling
- ✅ Already verified email handling

#### 2. PasswordResourceTest (3 tests)
Tests for password management:

**Password Policy:**
- ✅ Default policy retrieval
- ✅ Realm-configured policy retrieval

**Password Change:**
- ✅ Authentication requirement validation

**Note:** Full password change flow testing requires Keycloak internal types (`ClientConnection`, `SubjectCredentialManager`) that are complex to mock. These are better suited for integration tests with actual Keycloak instances.

## Test Coverage

### Current Status
- **Total Tests**: 16
- **Passing**: 16 (100%)
- **Coverage**: Business logic, validation, error handling

### Coverage Breakdown
| Module | Tests | Status |
|--------|-------|--------|
| RegistrationWorkflow | 13 | ✅ Complete |
| PasswordResource | 3 | ⚠️ Simplified (integration tests recommended) |
| ProfileResource | 0 | ⏳ Pending |
| ConsentResource | 0 | ⏳ Pending |
| AccountDeletionResource | 0 | ⏳ Pending |

## Writing New Tests

### Example: Unit Test Pattern

```kotlin
package org.scriptonbasestar.kcexts.selfservice.myfeature

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.models.*
import org.mockito.kotlin.*
import org.scriptonbasestar.kcexts.selfservice.test.SelfServiceTestFixtures

class MyFeatureTest {
    private lateinit var session: KeycloakSession
    private lateinit var realm: RealmModel
    private lateinit var users: UserProvider
    private lateinit var user: UserModel

    @BeforeEach
    fun setup() {
        realm = SelfServiceTestFixtures.createMockRealm()
        users = SelfServiceTestFixtures.createMockUserProvider()
        session = SelfServiceTestFixtures.createMockSession(realm = realm, users = users)
        user = SelfServiceTestFixtures.createMockUser()
    }

    @Test
    fun `should perform feature successfully`() {
        // Given
        whenever(users.getUserById(any(), eq("test-user-id"))).thenReturn(user)

        // When
        val result = myFeature.doSomething("test-user-id")

        // Then
        assertNotNull(result)
        verify(users).getUserById(realm, "test-user-id")
    }
}
```

### Example: Integration Test Pattern (Future)

```kotlin
package org.scriptonbasestar.kcexts.selfservice.myfeature

import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class MyFeatureIntegrationTest {
    @Container
    val keycloak = GenericContainer("quay.io/keycloak/keycloak:26.0.7")
        .withExposedPorts(8080)
        .withEnv("KEYCLOAK_ADMIN", "admin")
        .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")

    @Test
    fun `should perform E2E flow`() {
        // Test with actual Keycloak instance
    }
}
```

## Test Fixtures Usage

### Basic Mock Creation
```kotlin
// Minimal setup
val session = SelfServiceTestFixtures.createMockSession()
val user = SelfServiceTestFixtures.createMockUser()
```

### Custom Configuration
```kotlin
// Custom realm with password policy
val passwordPolicy = mock<PasswordPolicy>()
whenever(passwordPolicy.hashAlgorithm).thenReturn("pbkdf2-sha256")
val realm = SelfServiceTestFixtures.createMockRealm(
    name = "custom-realm",
    passwordPolicy = passwordPolicy
)

// Custom user
val user = SelfServiceTestFixtures.createMockUser(
    username = "customuser",
    email = "custom@example.com",
    isEmailVerified = true
)
```

### Builder Pattern
```kotlin
val user = SelfServiceTestFixtures.createMockUser {
    username = "testuser"
    email = "test@example.com"
    isEnabled = true
    isEmailVerified = false
    attributes["customAttr"] = listOf("value1", "value2")
}
```

## Common Mock Scenarios

### Mock User Lookup
```kotlin
whenever(users.getUserByUsername(any(), eq("testuser"))).thenReturn(user)
whenever(users.getUserByEmail(any(), eq("test@example.com"))).thenReturn(user)
whenever(users.getUserById(any(), eq("user-id"))).thenReturn(user)
```

### Mock User Creation
```kotlin
whenever(users.addUser(any(), eq("newuser"))).thenReturn(user)
```

### Mock User Search
```kotlin
whenever(users.searchForUserByUserAttributeStream(any(), eq("attrName"), eq("value")))
    .thenReturn(Stream.of(user))
```

### Mock Password Operations
```kotlin
val credentialManager = mock<SubjectCredentialManager>()
whenever(user.credentialManager()).thenReturn(credentialManager)
whenever(credentialManager.updateCredential(any())).thenReturn(true)
whenever(credentialManager.isValid(any<CredentialInput>())).thenReturn(true)
```

## Troubleshooting

### Issue: "Unresolved reference: any"
**Solution:** Import Mockito Kotlin extensions:
```kotlin
import org.mockito.kotlin.*
```

### Issue: "Cannot infer type for type parameter"
**Solution:** Specify type explicitly:
```kotlin
// Instead of: any()
any<CredentialInput>()
```

### Issue: "ClassNotFoundException: RuntimeDelegate"
**Solution:** Add RESTEasy dependency to `build.gradle`:
```gradle
testImplementation 'org.jboss.resteasy:resteasy-core:6.2.7.Final'
```

### Issue: "NullPointerException on context.getUri()"
**Solution:** Use `SelfServiceTestFixtures.createMockSession()` which includes all required mocks (URI, connection, email provider)

### Issue: Complex Keycloak Internal Types
**Strategy:**
- Unit tests: Focus on business logic validation
- Integration tests: Test complex authentication flows with actual Keycloak

## Best Practices

### ✅ DO
- Use `SelfServiceTestFixtures` for all mock creation
- Follow Mockito patterns (consistent with project)
- Test one behavior per test method
- Use descriptive test names with backticks
- Verify important interactions with `verify()`
- Add comments explaining complex mocking

### ❌ DON'T
- Don't use MockK (project standard is Mockito)
- Don't mock Keycloak internal types unnecessarily
- Don't test Keycloak's internal logic
- Don't create duplicate mock builders
- Don't write overly complex unit tests

## Future Enhancements

### Priority 1: Integration Tests
- [ ] TestContainers setup for Keycloak
- [ ] E2E registration flow test
- [ ] E2E password change test
- [ ] E2E email verification test

### Priority 2: Additional Unit Tests
- [ ] ProfileResource tests
- [ ] ConsentResource tests
- [ ] AccountDeletionResource tests
- [ ] Email template rendering tests

### Priority 3: Advanced Testing
- [ ] Performance tests
- [ ] Load tests with Gatling
- [ ] Security tests (injection, XSS)
- [ ] Concurrency tests

## References

### Project Testing Patterns
- `/events/event-listener-kafka/src/test/kotlin/` - Mockito unit test examples
- `/events/event-listener-common/src/test/kotlin/` - Shared test fixtures
- `/events/event-listener-kafka/src/integrationTest/kotlin/` - TestContainers integration tests

### External Documentation
- [Mockito Kotlin](https://github.com/mockito/mockito-kotlin)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Keycloak Testing Guide](https://www.keycloak.org/docs/latest/server_development/#_testsuite)
- [TestContainers](https://www.testcontainers.org/)

## Test Execution in CI/CD

### GitHub Actions Workflow
Tests are automatically run on:
- Pull requests
- Push to `develop` and `master` branches
- Release tag creation

### CI Test Command
```bash
./gradlew :self-service:self-service-api:test \
  --no-daemon \
  --stacktrace \
  --info
```

### Test Reports
- HTML Report: `build/reports/tests/test/index.html`
- JUnit XML: `build/test-results/test/`
- Coverage: `build/reports/jacoco/test/html/index.html`

---

**Last Updated**: 2025-01-11
**Test Status**: ✅ 16/16 passing (100%)
**Framework**: Mockito + JUnit 5 + Kotlin Test
