# Identity Providers Module - CLAUDE.md

## 1. Overview

OAuth2/OIDC Identity Provider extensions for Keycloak. Each provider follows a consistent SPI pattern.

**Providers**: Kakao, Naver, LINE, Google, GitHub

---

## 2. Module Structure

Each IdP module contains 5 core files:

```
idp-{provider}/
└── src/main/kotlin/.../idp/{provider}/
    ├── {Provider}Constant.kt           # Endpoints, scopes, provider ID
    ├── {Provider}IdentityProvider.kt   # OAuth2 flow handler
    ├── {Provider}IdentityProviderConfig.kt  # Configuration
    ├── {Provider}IdentityProviderFactory.kt # SPI factory (entry point)
    └── {Provider}UserAttributeMapper.kt     # User attribute mapping
```

**SPI Registration**: `src/main/resources/META-INF/org.keycloak.broker.social.SocialIdentityProviderFactory`

---

## 3. Adding a New Provider

### Step 1: Create Module

```bash
# Copy template
cp -r idp-kakao idp-newprovider
# Update settings.gradle to include new module
```

### Step 2: Implement Core Classes

**1. Constants** (`{Provider}Constant.kt`):
```kotlin
object NewProviderConstant {
    val providerId = "newprovider"
    val providerName = "New Provider"
    val authUrl = "https://provider.com/oauth/authorize"
    val tokenUrl = "https://provider.com/oauth/token"
    val profileUrl = "https://api.provider.com/v1/user/me"
    val defaultScope = "profile email"
}
```

**2. Config** (`{Provider}IdentityProviderConfig.kt`):
```kotlin
class NewProviderIdentityProviderConfig : OAuth2IdentityProviderConfig {
    constructor() : super() {
        this.alias = NewProviderConstant.providerId
        this.authorizationUrl = NewProviderConstant.authUrl
        this.tokenUrl = NewProviderConstant.tokenUrl
        this.userInfoUrl = NewProviderConstant.profileUrl
        this.defaultScope = NewProviderConstant.defaultScope
    }
    constructor(model: IdentityProviderModel) : super(model) { /* same */ }
}
```

**3. Provider** (`{Provider}IdentityProvider.kt`):
```kotlin
class NewProviderIdentityProvider(
    session: KeycloakSession,
    config: NewProviderIdentityProviderConfig
) : AbstractOAuth2IdentityProvider<NewProviderIdentityProviderConfig>(session, config),
    SocialIdentityProvider<NewProviderIdentityProviderConfig> {

    override fun getDefaultScopes(): String = NewProviderConstant.defaultScope
}
```

**4. Factory** (`{Provider}IdentityProviderFactory.kt`):
```kotlin
class NewProviderIdentityProviderFactory :
    AbstractIdentityProviderFactory<NewProviderIdentityProvider>(),
    SocialIdentityProviderFactory<NewProviderIdentityProvider> {

    override fun create(session: KeycloakSession, model: IdentityProviderModel) =
        NewProviderIdentityProvider(session, NewProviderIdentityProviderConfig(model))
    override fun getId() = NewProviderConstant.providerId
    override fun getName() = NewProviderConstant.providerName
    override fun createConfig() = NewProviderIdentityProviderConfig()
}
```

**5. Mapper** (`{Provider}UserAttributeMapper.kt`):
```kotlin
class NewProviderUserAttributeMapper : AbstractJsonUserAttributeMapper() {
    override fun getId() = "newprovider-user-attribute-mapper"
    override fun getCompatibleProviders() = arrayOf(NewProviderConstant.providerId)
}
```

### Step 3: Register SPI

Create: `src/main/resources/META-INF/org.keycloak.broker.social.SocialIdentityProviderFactory`
```
org.scriptonbasestar.kcexts.idp.newprovider.NewProviderIdentityProviderFactory
```

### Step 4: Add to settings.gradle

```gradle
include ':idps:idp-newprovider'
```

---

## 4. Build Commands

```bash
# Build all IdPs
./gradlew :idps:build

# Build specific provider
./gradlew :idps:idp-kakao:build

# Create Shadow JAR (fat JAR for deployment)
./gradlew :idps:idp-kakao:shadowJar

# Run tests
./gradlew :idps:test
```

---

## 5. Dependencies

Defined in parent `idps/build.gradle`:

| Dependency | Scope | Purpose |
|------------|-------|---------|
| keycloak-services | compileOnly | Keycloak SPI interfaces |
| keycloak-server-spi | compileOnly | SPI base classes |
| jackson-* | bundleLib | JSON parsing |
| slf4j-api | bundleLib | Logging |

---

## 6. Testing Strategy

### Unit Tests
- Mock `KeycloakSession` and `IdentityProviderModel`
- Test user attribute mapping
- Verify scope configuration

### Manual Testing
1. Deploy JAR to Keycloak providers/
2. Add IdP in Realm → Identity Providers
3. Test login flow with provider

---

## 7. Common Pitfalls

| Issue | Cause | Solution |
|-------|-------|----------|
| "Invalid redirect URI" | Mismatch in provider app | Use exact: `https://{host}/realms/{realm}/broker/{provider}/endpoint` |
| User attributes missing | Scope permissions | Add required scopes in Constant |
| Provider not visible | SPI not registered | Check META-INF file exists and FQCN is correct |
| ClassNotFoundException | Missing dependency | Add to bundleLib in build.gradle |

---

## 8. Provider-Specific Notes

| Provider | Auth URL | Special Notes |
|----------|----------|---------------|
| Kakao | kauth.kakao.com | Uses `profile_image`, `profile_nickname` scopes |
| Naver | nid.naver.com | Returns nested `response` object |
| LINE | access.line.me | Requires channel access token |
| Google | accounts.google.com | Full OIDC support |
| GitHub | github.com | OAuth2 only, no OIDC |

---

## 9. Deployment

```bash
# Build all
./gradlew :idps:shadowJar

# Deploy
cp idps/idp-*/build/libs/*-all.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

---

## 10. Related Files

| File | Purpose |
|------|---------|
| `idps/build.gradle` | Shared build configuration |
| `idps/idp-*/README.md` | Provider-specific setup guides |
| Root `CLAUDE.md` Section 3.2 | OAuth2 IdP architecture overview |
