# plugin-reference-impl

> **Reference implementation template.** Copy this folder to `platform-plugins/plugin-<your-name>/` and adapt to your plugin's domain.

This template demonstrates the **three canonical plugin patterns** used across the Ghatana platform:

| Pattern | What it shows |
|---------|--------------|
| **Pattern 1 — Policy Evaluation** | Deterministic, synchronous rule checks inside an async shell |
| **Pattern 2 — Event-Driven** | Idempotent event handler registered on the kernel event bus |
| **Pattern 3 — Human Approval** | Escalation to `plugin-human-approval` for regulated exceptions |

---

## File Structure

```
plugin-reference-impl/
├── build.gradle.kts                                  # Gradle module using id("java-module")
├── README.md                                         # This file
└── src/
    ├── main/java/com/ghatana/plugin/dataretention/
    │   ├── DataRetentionPlugin.java                  # Plugin interface — adapt this first
    │   └── impl/
    │       └── DefaultDataRetentionPlugin.java       # Implementation extending PluginObservability
    └── test/java/com/ghatana/plugin/dataretention/
        └── impl/
            └── DefaultDataRetentionPluginTest.java   # Tests: EventloopTestBase + Mockito
```

---

## How to Adapt

### Step 1 — Copy and rename

```bash
cp -r templates/plugin-reference-impl/ platform-plugins/plugin-<your-name>/
```

Replace all occurrences of `DataRetention` / `data-retention` with your plugin concept:

| Template | Your value |
|----------|-----------|
| `DataRetentionPlugin` | `<YourDomain>Plugin` |
| `DefaultDataRetentionPlugin` | `Default<YourDomain>Plugin` |
| `plugin-dataretention` (OTel ID) | `plugin-<your-name>` |
| package `com.ghatana.plugin.dataretention` | `com.ghatana.plugin.<yourname>` |

### Step 2 — Define your domain records

In your plugin interface, replace `DataRecord`, `DataCreatedEvent`, etc. with your own
domain value types. Keep them as Java `record`s — they are immutable, serializable,
and carry no framework coupling.

### Step 3 — Wire the build

Add to `settings.gradle.kts`:

```kotlin
include(":platform-plugins:plugin-<your-name>")
```

### Step 4 — Register with the kernel

Add your plugin to the kernel plugin registry in `platform-kernel/kernel-core`.

### Step 5 — Write tests first

Copy `DefaultDataRetentionPluginTest` and replace domain types. All tests must:

- Extend `EventloopTestBase`
- Use `runPromise(() -> ...)` — never `.getResult()` directly
- Mock only `PluginContext` (and any injected dependencies)
- Include a **Tenant Isolation** nested class
- Have at least one test per Pattern

---

## Pattern Reference

### Pattern 1 — Policy Evaluation

```java
@Override
public Promise<RetentionDecision> evaluateRetentionPolicy(String tenantId, DataRecord record) {
    try (var span = startSpan("evaluate-retention-policy", tenantId, null, record.dataClassification())) {
        // Pure deterministic logic — no I/O, no side effects
        if (record.legalHold()) {
            return Promise.of(new RetentionDecision(Decision.LEGAL_HOLD, ...));
        }
        // ... threshold check
        return Promise.of(new RetentionDecision(Decision.COMPLIANT, ...));
    }
}
```

Key rules:
- Use `startSpan(...)` from `PluginObservability` — do NOT create OTel spans manually
- Keep the logic deterministic — same input always produces same output
- Call `recordMetric()` before returning to track invocation counts

### Pattern 2 — Event-Driven

```java
@Override
public Promise<Void> handleDataCreated(DataCreatedEvent event) {
    try (var span = startSpan("handle-data-created", event.tenantId(), event.actorId(), null)) {
        // Safe to call multiple times — idempotent storage via map.put(id, record)
        tenantRecords
            .computeIfAbsent(event.tenantId(), k -> new ConcurrentHashMap<>())
            .put(event.record().recordId(), event.record());
        recordMetric();
        return Promise.complete();
    }
}
```

Key rules:
- Event handlers MUST be idempotent — the event bus delivers at-least-once
- Never block the event loop — all I/O must be async
- Use `ConcurrentHashMap` keyed by tenant ID for in-memory state

### Pattern 3 — Human Approval

```java
@Override
public Promise<ApprovalOutcome> requestRetentionException(...) {
    try (var span = startSpan("request-retention-exception", tenantId, requestor, ...)) {
        return humanApprovalPlugin.createApprovalRequest(
            ApprovalRequest.builder()
                .approvalId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .type("RETENTION_EXCEPTION")
                .reason(reason)
                .requestor(requestor)
                .build()
        ).map(result -> result.approved() ? ApprovalOutcome.APPROVED : ApprovalOutcome.REJECTED);
    }
}
```

Key rules:
- Inject `HumanApprovalPlugin` as a constructor parameter
- Never block waiting for human input — the Promise suspends until the workflow completes
- Always record an audit trail entry alongside the approval request

---

## Observability

All plugin operations are automatically traced and metered through `PluginObservability`:

- **Span name**: `plugin.<pluginId>.<operationName>` (e.g. `plugin.data-retention.evaluate-retention-policy`)
- **Metric**: `plugin.<pluginId>.invocations.total` incremented by `recordMetric()`
- **Tenant context**: added to span attributes when you pass `tenantId`
- **Data classification**: added to span attributes when you pass the classification string
- **Correlation ID**: propagated automatically via `CorrelationIdContext`

---

## Testing Conventions

```java
@DisplayName("DefaultDataRetentionPlugin")
@ExtendWith(MockitoExtension.class)
class DefaultDataRetentionPluginTest extends EventloopTestBase {

    @Mock PluginContext mockContext;
    private DefaultDataRetentionPlugin plugin;

    @BeforeEach void setUp() {
        plugin = new DefaultDataRetentionPlugin();
        lenient().when(mockContext.pluginId()).thenReturn("data-retention");
        runPromise(() -> plugin.initialize(mockContext).then(v -> plugin.start()));
    }

    @Test void exampleTest() {
        SomeResult result = runPromise(() -> plugin.someOperation("tenant-x", input));
        assertThat(result.field()).isEqualTo(expectedValue);
    }
}
```

- `lenient().when(...)` prevents `UnnecessaryStubbingException` for stubs not used in every test
- `runPromise(...)` executes the Promise on the test event loop and returns the result synchronously
- Tenant isolation tests live in a `@Nested` class named `TenantIsolation`

---

## Related Docs

- [Plugin Author Onboarding Guide](../../docs/PLUGIN_AUTHOR_ONBOARDING_GUIDE.md)
- [OTel Observability Conventions](../../docs/OTEL_OBSERVABILITY_CONVENTIONS.md)
- [Module Archive Plan](../../docs/MODULE_ARCHIVE_PLAN.md)
- [Status Board](../../docs/STATUS_BOARD.md)
