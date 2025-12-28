# Self-Service Module - CLAUDE.md

## 1. Overview

REST API for user self-service operations including registration, profile management, password changes, consent management (GDPR), and account deletion.

**Module**: `self-service-api`

---

## 2. REST API Endpoints

**Base Path**: `/realms/{realm}/self-service`

### Registration

| Method | Path | Description |
|--------|------|-------------|
| POST | `/registration` | Register new user with email verification |
| GET | `/registration/verify` | Verify email with token |
| POST | `/registration/resend` | Resend verification email |

### Profile

| Method | Path | Description |
|--------|------|-------------|
| GET | `/profile` | Get current user's profile |
| PUT | `/profile` | Update profile |
| GET | `/profile/attributes` | Get custom attributes |

### Password

| Method | Path | Description |
|--------|------|-------------|
| GET | `/password/policy` | Get realm password policy |
| POST | `/password/change` | Change password |
| POST | `/password/reset/request` | Request password reset email |
| POST | `/password/reset/confirm` | Confirm password reset with token |

### Consent (GDPR)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/consents` | Get all recorded consents |
| POST | `/consents` | Record new consent |
| DELETE | `/consents/{type}` | Revoke consent |

### Account Deletion

| Method | Path | Description |
|--------|------|-------------|
| POST | `/deletion/request` | Request account deletion (30-day grace) |
| POST | `/deletion/cancel` | Cancel pending deletion |
| GET | `/deletion/status` | Check deletion status |

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Self-Service API                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   SelfServiceResourceProviderFactory (SPI Entry Point)         │
│        ↓                                                        │
│   SelfServiceResourceProvider                                  │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  Resource Handlers                                      │  │
│   │  ├── RegistrationResource                               │  │
│   │  │   └── RegistrationWorkflow (email verification)      │  │
│   │  ├── ProfileResource                                    │  │
│   │  ├── PasswordResource                                   │  │
│   │  ├── ConsentResource                                    │  │
│   │  └── DeletionResource                                   │  │
│   └─────────────────────────────────────────────────────────┘  │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  Support Services                                       │  │
│   │  ├── EmailNotificationService                           │  │
│   │  └── User Attribute Management                          │  │
│   └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. File Structure

```
self-service/
└── self-service-api/
    └── src/main/kotlin/.../selfservice/
        ├── SelfServiceResourceProvider.kt         # Main resource provider
        ├── SelfServiceResourceProviderFactory.kt  # SPI factory
        ├── model/
        │   └── ApiResponse.kt                     # Common response model
        ├── registration/
        │   ├── RegistrationResource.kt            # Registration endpoints
        │   ├── RegistrationModels.kt              # DTOs
        │   └── RegistrationWorkflow.kt            # Email verification logic
        ├── profile/
        │   ├── ProfileResource.kt                 # Profile endpoints
        │   └── ProfileModels.kt                   # DTOs
        ├── password/
        │   ├── PasswordResource.kt                # Password endpoints
        │   └── PasswordModels.kt                  # DTOs
        ├── consent/
        │   └── ConsentResource.kt                 # GDPR consent endpoints
        ├── deletion/
        │   └── DeletionResource.kt                # Account deletion endpoints
        └── notification/
            └── EmailNotificationService.kt        # Email sender

src/main/resources/META-INF/services/
└── org.keycloak.services.resource.RealmResourceProviderFactory
```

---

## 5. Authentication

All endpoints (except registration) require a valid access token:

```bash
# Get access token
curl -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}"

# Call self-service API
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "${KEYCLOAK_URL}/realms/${REALM}/self-service/profile"
```

---

## 6. Registration Flow

```
1. POST /registration
   ├── Validate request (email, password policy)
   ├── Create user (enabled=false)
   ├── Generate verification token
   └── Send verification email

2. GET /registration/verify?token=xxx
   ├── Validate token (not expired, not used)
   ├── Enable user account
   └── Invalidate token

3. Login available after verification
```

---

## 7. Account Deletion Flow (GDPR Compliant)

```
1. POST /deletion/request
   ├── Mark account for deletion
   ├── Set deletion date (now + 30 days)
   ├── Send confirmation email
   └── User can still login during grace period

2. POST /deletion/cancel (within 30 days)
   ├── Remove deletion marker
   └── Account continues normally

3. Scheduled job (after 30 days)
   ├── Hard delete user data
   ├── Anonymize audit logs
   └── Remove from all realms
```

---

## 8. Consent Management (GDPR)

Store and track user consents:

```kotlin
data class ConsentRecord(
    val type: String,           // "terms", "privacy", "marketing"
    val version: String,        // "v1.2"
    val grantedAt: Long,        // Timestamp
    val ipAddress: String,      // For audit
    val userAgent: String       // For audit
)
```

Stored in user attributes:
```
user.attributes["consent.terms"] = "v1.2:1703836800000"
user.attributes["consent.privacy"] = "v1.0:1703836800000"
```

---

## 9. Example Requests

### Register User

```bash
POST /realms/myrealm/self-service/registration
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "consents": ["terms:v1.0", "privacy:v1.0"]
}
```

### Update Profile

```bash
PUT /realms/myrealm/self-service/profile
Authorization: Bearer ${TOKEN}
{
  "firstName": "John",
  "lastName": "Smith",
  "attributes": {
    "phone": "+1234567890"
  }
}
```

### Change Password

```bash
POST /realms/myrealm/self-service/password/change
Authorization: Bearer ${TOKEN}
{
  "currentPassword": "OldPass123!",
  "newPassword": "NewPass456!"
}
```

---

## 10. Build & Deploy

```bash
# Build
./gradlew :self-service:self-service-api:build

# Create Shadow JAR
./gradlew :self-service:self-service-api:shadowJar

# Deploy
cp self-service/self-service-api/build/libs/*-all.jar \
   $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

---

## 11. Configuration

Configure email for verification/notifications in Keycloak:
1. Realm → Realm Settings → Email
2. Set SMTP server details
3. Enable "User Registration" if needed

---

## 12. Related Files

| File | Purpose |
|------|---------|
| `self-service/self-service-api/README.md` | Module setup guide |
| Root `CLAUDE.md` Section 1.3 | Project overview |
