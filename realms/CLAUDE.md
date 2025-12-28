# Realm Management Module - CLAUDE.md

## 1. Overview

Hierarchical realm management with automatic configuration inheritance. Enables parent-child realm relationships with IdP, Role, and AuthFlow propagation.

**Module**: `realm-hierarchy`

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Realm Hierarchy System                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Keycloak Admin API                                           │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  RealmHierarchyResource (REST API)                      │  │
│   │  POST /realms/{realm}/hierarchy/parent                  │  │
│   │  PUT  /realms/{realm}/hierarchy/inheritance             │  │
│   │  POST /realms/{realm}/hierarchy/synchronize             │  │
│   └─────────────────────────────────────────────────────────┘  │
│        ↓                           ↓                            │
│   ┌──────────────────┐   ┌─────────────────────────┐           │
│   │ RealmHierarchy   │   │ InheritanceManager      │           │
│   │ Storage          │   │ ├─ inheritIdP()         │           │
│   │ (Realm Attrs)    │   │ ├─ inheritRoles()       │           │
│   │                  │   │ └─ inheritAuthFlow()    │           │
│   └──────────────────┘   └─────────────────────────┘           │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│   Event-Driven Sync                                            │
│        ↓                                                        │
│   ┌─────────────────────────────────────────────────────────┐  │
│   │  RealmHierarchyEventListener                            │  │
│   │  Listens: REALM_UPDATE, IDP_CREATE, ROLE_CREATE         │  │
│   │  Auto-propagates changes to child realms                │  │
│   └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. REST API Endpoints

**Base Path**: `/realms/{realm}/hierarchy`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Get current realm's hierarchy info |
| GET | `/path` | Get full path from current realm to root |
| POST | `/parent` | Set parent realm |
| PUT | `/inheritance` | Update inheritance settings |
| POST | `/synchronize` | Force sync all child realms |
| DELETE | `/` | Remove hierarchy (detach from parent) |

### Example Requests

**Set Parent Realm**:
```bash
POST /realms/child-realm/hierarchy/parent
{
  "parentRealmId": "parent-realm-id",
  "inheritIdp": true,
  "inheritAuthFlow": false,
  "inheritRoles": true
}
```

**Update Inheritance**:
```bash
PUT /realms/child-realm/hierarchy/inheritance
{
  "inheritIdp": true,
  "inheritAuthFlow": true,
  "inheritRoles": false
}
```

---

## 4. Inheritance Features

| Feature | Status | Description |
|---------|--------|-------------|
| Identity Providers | ✅ Implemented | Clone IdPs with `hierarchy.inherited` metadata |
| Realm Roles | ✅ Implemented | Inherit roles with source tracking |
| Auth Flows | 🚧 Planned | Complex due to nested executions |

### Inheritance Metadata

Inherited resources are marked with special attributes:

```kotlin
// Identity Provider config
idp.config["hierarchy.inherited"] = "true"
idp.config["hierarchy.source_realm"] = "parent-realm-name"

// Role attributes
role.setAttribute("hierarchy.inherited", listOf("true"))
role.setAttribute("hierarchy.source_realm", listOf("parent-realm-name"))
```

---

## 5. Storage Strategy

**No Database Changes**: All hierarchy data stored in Realm Attributes as JSON.

```kotlin
// Stored in realm.attributes["hierarchy.node"]
data class RealmHierarchyNode(
    val realmId: String,
    val realmName: String,
    val parentRealmId: String?,
    val tier: Int,              // 0 = root, 1 = first child, etc.
    val depth: Int,             // Distance from root
    val path: String,           // "/root/parent/child"
    val inheritIdp: Boolean,
    val inheritAuthFlow: Boolean,
    val inheritRoles: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
```

### Safety Features

- **Circular Reference Prevention**: Detects A → B → A patterns before saving
- **Max Depth**: 10 levels (configurable)
- **Orphan Protection**: Child realms detached when parent deleted

---

## 6. File Structure

```
realm-hierarchy/
└── src/main/kotlin/.../realm/hierarchy/
    ├── RealmHierarchyEventListener.kt          # Event-driven sync
    ├── RealmHierarchyEventListenerFactory.kt   # SPI factory
    ├── api/
    │   ├── RealmHierarchyResource.kt           # REST endpoints
    │   ├── RealmHierarchyResourceProvider.kt   # Resource provider
    │   ├── RealmHierarchyResourceProviderFactory.kt  # SPI factory
    │   └── dto/                                 # Request/Response DTOs
    │       ├── HierarchyResponse.kt
    │       ├── SetParentRequest.kt
    │       └── UpdateInheritanceRequest.kt
    ├── inheritance/
    │   └── InheritanceManager.kt               # Inheritance logic
    ├── model/
    │   └── RealmHierarchyNode.kt               # Hierarchy data model
    └── storage/
        └── RealmHierarchyStorage.kt            # Realm Attributes CRUD

src/main/resources/META-INF/services/
├── org.keycloak.events.EventListenerProviderFactory
└── org.keycloak.services.resource.RealmResourceProviderFactory
```

---

## 7. Build Commands

```bash
# Build
./gradlew :realms:realm-hierarchy:build

# Create Shadow JAR
./gradlew :realms:realm-hierarchy:shadowJar

# Run tests
./gradlew :realms:realm-hierarchy:test

# Deploy
cp realms/realm-hierarchy/build/libs/*-all.jar $KEYCLOAK_HOME/providers/
```

---

## 8. Configuration

Enable via Keycloak admin console:
1. Realm → Events → Event Listeners → Add `realm-hierarchy-listener`
2. Access REST API at `/realms/{realm}/hierarchy`

---

## 9. Use Cases

### Multi-Tenant SaaS

```
master (root)
├── tenant-a (tier 1)
│   ├── tenant-a-dev (tier 2)
│   └── tenant-a-prod (tier 2)
└── tenant-b (tier 1)
    └── tenant-b-prod (tier 2)
```

### Enterprise SSO

```
corporate (root) - All IdPs defined here
├── division-1 (inherits IdPs)
└── division-2 (inherits IdPs)
```

---

## 10. Related Files

| File | Purpose |
|------|---------|
| `realms/realm-hierarchy/README.md` | Module setup guide |
| Root `CLAUDE.md` Section 3.4 | Realm hierarchy architecture |
