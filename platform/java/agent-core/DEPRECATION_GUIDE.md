# platform/java/agent-core — Deprecation Migration Guide

This document catalogues **all deprecated APIs** in `agent-core`, their
replacement, and the planned removal timeline. Resolves audit finding LOW-004.

---

## Deprecated APIs

### 1. `AgentType.LLM` (enum constant)

| Item | Value |
|------|-------|
| **Since** | 2.0.0 |
| **For removal** | Yes |
| **Target removal** | 3.0.0 |

**Problem:** `LLM` as a top-level agent type mixes the _processing model_
(probabilistic) with the _implementation technology_ (LLM). This prevents
using the same `PROBABILISTIC` type for non-LLM models.

**Migration:**
```java
// Before
AgentDescriptor.builder()
    .type(AgentType.LLM)
    .build();

// After
AgentDescriptor.builder()
    .type(AgentType.PROBABILISTIC)
    .subtype(ProbabilisticSubtype.LLM.name())
    .build();
```

**How to find usages:**
```bash
grep -r "AgentType.LLM\|AgentType\.LLM" products/ platform/
```

---

### 2. `Agent` interface (`com.ghatana.agent.Agent`)

| Item | Value |
|------|-------|
| **Since** | 2.0.0 |
| **For removal** | Yes |
| **Target removal** | 3.0.0 |

**Problem:** The legacy `Agent` interface returns `AgentCapabilities` (also
deprecated) and lacks typed I/O generics. It cannot express SLAs, determinism,
or state mutability.

**Migration:** Implement `TypedAgent<I, O>` instead, and use `AgentDescriptor`
for metadata.

```java
// Before
public class MyAgent implements Agent { ... }

// After
public class MyAgent extends BaseAgent<MyInput, MyOutput> {
    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
            .agentId("my-agent")
            .type(AgentType.DETERMINISTIC)
            .build();
    }
    @Override
    protected Promise<AgentResult<MyOutput>> processTyped(MyInput input, AgentContext ctx) { ... }
}
```

---

### 3. `AgentCapabilities` record

| Item | Value |
|------|-------|
| **Since** | 2.0.0 |
| **For removal** | Yes |
| **Target removal** | 3.0.0 |

**Problem:** Flat capability metadata without type, SLA, or determinism fields.
Produced by the deprecated `Agent` interface.

**Migration:** Use `AgentDescriptor` which carries full agent identity and
capability metadata. For backward compatibility, `AgentDescriptor.toCapabilities()`
produces an `AgentCapabilities` if a legacy interface is temporarily needed.

---

### 4. `PlannerRegistry` — tenant-unaware overloads

| Item | Value |
|------|-------|
| **Since** | 2.3.0 |
| **For removal** | Yes |
| **Target removal** | 3.0.0 |

The following method overloads in `PlannerRegistry` are deprecated because they
operate without a `tenantId`, violating the platform multi-tenancy requirement:

| Deprecated method | Replacement |
|-------------------|-------------|
| `register(String agentId, BaseAgent agent)` | `register(String tenantId, String agentId, BaseAgent agent)` |
| `lookup(String agentId)` | `lookup(String tenantId, String agentId)` |
| `getAgents()` | `getAgents(String tenantId)` |

**Migration:**
```java
// Before
registry.register("my-agent", agentInstance);
Optional<BaseAgent<?,?>> agent = registry.lookup("my-agent");

// After
registry.register(tenantId, "my-agent", agentInstance);
Optional<BaseAgent<?,?>> agent = registry.lookup(tenantId, "my-agent");
```

---

## Removal Timeline

| API | Target version | Owner |
|-----|---------------|-------|
| `AgentType.LLM` | 3.0.0 | Platform |
| `Agent` interface | 3.0.0 | Platform |
| `AgentCapabilities` record | 3.0.0 | Platform |
| `PlannerRegistry` tenant-unaware overloads | 3.0.0 | AEP team |

Before removal:
1. Audit all `@Deprecated` usage across `products/` with `./gradlew checkstyleMain`
2. Update each callsite using the migration notes above
3. Remove from baseline once all callsites migrated
4. Add ArchUnit rule preventing re-introduction

---

## Build Warning

To surface deprecated usage as compiler warnings in your module, ensure your
`build.gradle.kts` enables `Xlint:deprecation`:

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}
```
