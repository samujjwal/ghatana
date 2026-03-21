# Data Cloud Platform Module Split Plan

**Status:** Phase 7 (P7-2a) — Approved for incremental execution  
**Date:** 2026-03-21  
**Current:** Single `data-cloud/platform` module (518 Java files, 32 packages)

---

## Target Module Structure

The monolithic `data-cloud/platform` module is split into 5 bounded-context modules:

```
products/data-cloud/
├── platform-entity/       # Entity & metadata management
├── platform-event/        # Event sourcing & streaming
├── platform-config/       # Configuration, policy, governance
├── platform-analytics/    # Analytics, query engine, reporting
├── platform-launcher/     # API layer, deployment, DI wiring
└── spi/                   # (existing) Plugin SPI contracts
```

### Module Details

| Module | Packages | Est. Files | Dependencies |
|--------|----------|-----------|--------------|
| `platform-entity` | `entity/`, `record/`, `schema/`, `spi/storage/` | ~155 | platform:java:core, domain, database |
| `platform-event` | `event/`, `spi/streaming/` | ~30 | platform:java:core, event-cloud |
| `platform-config` | `config/`, `infrastructure/policy/`, `application/policy/`, `pattern/`, `reflex/` | ~65 | platform:java:core, config |
| `platform-analytics` | `analytics/`, `plugins/analytics/`, `application/query/`, `client/` | ~80 | platform-entity, platform-event |
| `platform-launcher` | `api/`, `grpc/`, `di/`, `deployment/`, `distributed/`, `edge/`, `brain/`, remaining | ~188 | all above modules |

### Dependency Flow

```
platform-launcher → platform-analytics → platform-entity
                  → platform-config    → platform-event
                  → platform-event
                  → platform-entity
```

### Migration Strategy

1. **Create new module directories** with `build.gradle.kts` using correct dependencies
2. **Move packages incrementally** — one bounded context per PR
3. **Keep `platform` as thin launcher** that re-exports all sub-modules during transition
4. **Update `settings.gradle.kts`** with new module includes
5. **Verify compilation** after each package move

### Interim Compatibility

During migration, `platform/build.gradle.kts` aggregates all sub-modules:

```kotlin
dependencies {
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
}
```

This ensures downstream consumers continue to compile without changes.

---

## Phase 7 Action Items

- [x] Package analysis complete (518 files, 32 packages)
- [x] Bounded context boundaries identified (5 modules)
- [x] Dependency graph documented
- [x] Migration strategy defined
- [ ] Create stub `build.gradle.kts` for each new module
- [ ] Move first bounded context (platform-entity) as proof of migration
- [ ] Complete remaining context moves (incremental PRs)
