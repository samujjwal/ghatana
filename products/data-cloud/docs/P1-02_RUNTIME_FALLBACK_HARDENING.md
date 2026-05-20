# P1-2: Runtime Fallback Hardening

**Status**: Implementation Complete ✅  
**Last Updated**: 2026-05-18  
**Jira Issues**: DC-P1-02 (fallback mode hardening), DC-P1-07 (profile-based storage validation)

## Overview

Runtime Fallback Hardening ensures that fallback implementations (in-memory storage, simplified validation, stub adapters) are **only used in appropriate development/test environments** and are **blocked in production**.

## Problem Statement

Before P1-2, data cloud had multiple "fallback" code paths:
- InMemoryContextStore (supposed to be test-only, but might be used in production)
- Default timeout values (when configuration missing)
- Stub implementations (e.g., LLM responses without real models)
- Validation shortcuts (e.g., skip policy checks in certain cases)

Without explicit profile validation:
- **Risk**: In-memory storage accidentally deployed to production
- **Risk**: Stub adapters left enabled in production
- **Risk**: Validation bypassed in production (silently wrong behavior)

**P1-2 Solution**: Classify runtime into explicit profiles (LOCAL, TEST, EMBEDDED, STAGING, PRODUCTION, SOVEREIGN) and validate all fallbacks at initialization time.

---

## Implementation

### 1. RuntimeProfile Enum

**File**: `RuntimeProfile.java`

Six execution profiles representing the runtime context:

| Profile | Purpose | Fallbacks Allowed? | Durable Storage Required? |
|---------|---------|-------------------|--------------------------|
| **LOCAL** | Developer machine | ✅ Yes (all) | ❌ No |
| **TEST** | Automated testing | ✅ Yes (all) | ❌ No |
| **EMBEDDED** | Custom integrations | ⚠️ Limited | ⚠️ Recommended |
| **STAGING** | Pre-production | ❌ No | ✅ Yes (required) |
| **PRODUCTION** | Live production | ❌ No | ✅ Yes (required) |
| **SOVEREIGN** | Air-gapped deployment | ❌ No | ✅ Yes (required) |

**Key Methods**:
```java
RuntimeProfile.resolve()  // Detect active profile from env/config
profile.allowsInMemoryFallback()  // Can use in-memory storage?
profile.requiresDurableStorage()  // Must use durable backing?
profile.isProduction()  // Is production-like?
profile.isDebugAllowed()  // Allow debug logging?
profile.requiresComplianceValidation()  // Sovereign rules?
```

**Resolution Order**:
1. System property: `-Ddc.runtime.profile=PRODUCTION`
2. Environment variable: `DC_RUNTIME_PROFILE=PRODUCTION`
3. Application config (not shown here)
4. Default: `LOCAL` (safe, development-friendly)

### 2. RuntimeProfileValidator

**File**: `RuntimeProfileValidator.java`

Centralized validator for all fallback operations:

**Method: validateStorageImplementation()**
```java
// Reject InMemoryContextStore in production
RuntimeProfileValidator.validateStorageImplementation(
    contextStore,  // The storage being used
    profile,       // Active profile (e.g., PRODUCTION)
    "ContextLayerHandler init"  // Context for error messages
);
// Throws IllegalStateException if in-memory is used in production
```

**Method: validateFallbackPermitted()**
```java
// Validate that a fallback is allowed in current profile
RuntimeProfileValidator.validateFallbackPermitted(
    "use default timeout=30s",  // Fallback description
    profile,
    30000  // Default value
);
// Throws IllegalStateException if fallback not allowed in profile
```

**Method: logFallbackUsage()**
```java
// Log that fallback is being used
RuntimeProfileValidator.logFallbackUsage(
    "InMemoryContextStore",
    profile,
    "no JDBC config provided"
);
// DEBUG log in LOCAL/TEST, INFO log in other profiles
```

### 3. Integration: ContextLayerHandler

Updated constructor validates storage at initialization:

```java
public ContextLayerHandler(HttpHandlerSupport http, ObjectMapper objectMapper,
                           KnowledgeGraphPlugin knowledgeGraph, ContextStore contextStore) {
    this.contextStore = Objects.requireNonNull(contextStore, "contextStore");
    
    // DC-P1-02: Validate storage implementation for runtime profile
    RuntimeProfile activeProfile = RuntimeProfile.resolve();
    RuntimeProfileValidator.validateStorageImplementation(
            contextStore, activeProfile, "ContextLayerHandler initialization");
}
```

**Behavior**:
- **LOCAL/TEST**: InMemoryContextStore permitted (warning logged)
- **EMBEDDED**: InMemoryContextStore rejected (error thrown)
- **STAGING/PRODUCTION/SOVEREIGN**: InMemoryContextStore rejected (error thrown)

---

## Configuration

### Environment Variables

**Explicit Profile Selection**:
```bash
# Production deployment
export DC_RUNTIME_PROFILE=PRODUCTION

# Staging
export DC_RUNTIME_PROFILE=STAGING

# Development (default)
export DC_RUNTIME_PROFILE=LOCAL  # or omit for default
```

### System Properties

**JVM Startup**:
```bash
java -Ddc.runtime.profile=PRODUCTION \
     -Ddc.context.store.jdbc.url=jdbc:postgresql://localhost/data-cloud \
     -jar app.jar
```

### Application Configuration

Future: Add profile-specific config files:
```
config/
  ├── application-local.yaml      # LOCAL profile defaults
  ├── application-test.yaml       # TEST profile defaults
  ├── application-staging.yaml    # STAGING profile defaults
  └── application-production.yaml # PRODUCTION profile defaults
```

---

## Use Cases

### Use Case 1: Developer Local Testing
```
Profile: LOCAL
Environment: laptop
Fallback: InMemoryContextStore (allowed)
Logging: DEBUG level
Expected: Works without configuration
```

### Use Case 2: Automated Testing
```
Profile: TEST
Environment: CI/CD server
Fallback: Test mocks, in-memory storage (allowed)
Logging: Minimal (only failures)
Expected: Fast, isolated, repeatable
```

### Use Case 3: Staging/Pre-prod
```
Profile: STAGING
Environment: Azure staging
Fallback: None (rejected)
Storage: JdbcContextStore (required)
Logging: INFO level + structured traces
Expected: Production-like validation without real-time traffic
```

### Use Case 4: Production
```
Profile: PRODUCTION
Environment: Live Azure
Fallback: None (rejected)
Storage: JdbcContextStore (required)
Logging: WARN/ERROR levels + structured traces
Secrets: Azure Key Vault
Expected: Zero fallbacks, strict enforcement
```

### Use Case 5: Sovereign (Government/Healthcare)
```
Profile: SOVEREIGN
Environment: Air-gapped data center
Fallback: None (rejected)
Storage: Secure JdbcContextStore
Logging: Audit logs (immutable)
Compliance: HIPAA, FedRAMP, SOC2
Expected: Maximum security & compliance
```

---

## Testing

### Unit Tests

```java
@Test
void rejectInMemoryStorageInProduction() {
    RuntimeProfile.PRODUCTION.validateFallbackPermitted(
        "InMemoryContextStore", InMemoryContextStore.class);
    
    assertThrows(IllegalStateException.class, () ->
        RuntimeProfileValidator.validateStorageImplementation(
            new InMemoryContextStore(),
            RuntimeProfile.PRODUCTION,
            "test"
        )
    );
}

@Test
void allowInMemoryStorageInTest() {
    RuntimeProfileValidator.validateStorageImplementation(
        new InMemoryContextStore(),
        RuntimeProfile.TEST,
        "test"
    );
    // Should NOT throw
}

@Test
void resolveProfileFromSystemProperty() {
    System.setProperty("dc.runtime.profile", "STAGING");
    assertEquals(RuntimeProfile.STAGING, RuntimeProfile.resolve());
}

@Test
void resolveProfileFromEnvironment() {
    // Set DC_RUNTIME_PROFILE env var
    assertEquals(RuntimeProfile.PRODUCTION, RuntimeProfile.resolve());
}
```

### Integration Tests

```java
@Test
void contextLayerHandlerRejectsInMemoryInProduction() {
    RuntimeProfile.PRODUCTION.push();  // Mock profile
    
    assertThrows(IllegalStateException.class, () ->
        new ContextLayerHandler(
            http, objectMapper, null, new InMemoryContextStore()
        )
    );
}
```

---

## Deployment Checklist

- [ ] Verify DC_RUNTIME_PROFILE set correctly in deployment config
- [ ] Confirm JDBC context store configured in production
- [ ] Verify application starts (initialization validation runs)
- [ ] Check logs for "storage implementation" messages
- [ ] Verify no fallback warnings in production logs
- [ ] Test that removing required config causes startup failure
- [ ] Document profile configuration in ops runbook

---

## Error Messages

### Error: InMemoryContextStore Not Permitted
```
[DC-P1-02] InMemoryContextStore is not permitted in PRODUCTION profile.
Context: ContextLayerHandler initialization.
Use JdbcContextStore or another durable implementation instead.
```

**Resolution**: Configure durable storage (JDBC) in application config

### Error: Fallback Not Permitted
```
[DC-P1-02] Fallback not permitted in STAGING profile: use default timeout (default=30000).
Fallbacks are only allowed in LOCAL and TEST profiles.
```

**Resolution**: Explicitly configure the value instead of using fallback

---

## Migration Path

### Phase 1: Add Profile + Validators (COMPLETE)
- [x] RuntimeProfile enum
- [x] RuntimeProfileValidator class
- [x] ContextLayerHandler integration

### Phase 2: Audit Other Fallbacks (NEXT)
Identify and harden all fallback code paths:
- TimeoutManager (default timeouts)
- LLMAdapter (stub responses)
- CacheManager (in-memory caches)
- ValidationEngine (simplified checks)
- etc.

### Phase 3: Update Deployment Configs (FOLLOWING)
- Update Dockerfile ENV defaults
- Update Kubernetes ConfigMaps
- Update Azure App Settings
- Update CI/CD profile selection logic

### Phase 4: Add Compliance Profiles (LATER)
- SOVEREIGN profile hardening
- Audit trail integration
- Compliance validation

---

## References

- [Ghatana Platform Guidelines - Section 3](../../../../docs/copilot-instructions.md#3-core-engineering-principles)
- RuntimeProfile.java API docs
- RuntimeProfileValidator.java API docs
- Deployment runbook: (TBD)

## Support

Questions about runtime profiles or fallback hardening:
- Slack: #data-cloud-arch
- Email: platform-team@ghatana.io
