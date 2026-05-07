# Ghatana Repository Coverage Governance

**Established:** 2026-04-23  
**Status:** ACTIVE  
**Scope:** All platform, shared-services, and product modules (225 Java + 68 TypeScript packages)

---

## Tiered Coverage Policy

All modules must achieve coverage thresholds based on their criticality tier. Coverage is measured across:
- **Java**: Line coverage via JaCoCo (Gradle task: `jacocoTestCoverageVerification`)
- **TypeScript**: Line coverage via Vitest (in `vitest.config.ts`)

### Tier Classification

| Tier | Coverage Target | Module Classification | Examples |
|------|-----------------|----------------------|----------|
| **P0** | **90%** | Mission-critical infra, auth, data, contracts | `platform:java:core`, `platform:java:security`, `platform:java:database`, `products:data-cloud:spi`, `products:aep:aep-operator-contracts` |
| **P1** | **80%** | Core services, domain logic, workflows | `platform:java:agent-core`, `platform:java:observability`, `products:data-cloud:platform-entity`, `products:aep:aep-engine` |
| **P2** | **70%** | Product features, adapters, integrations | `products:yappc:core:*`, `products:virtual-org:modules:*`, `products:tutorputor:*` |
| **P3** | **60%** | Tools, utilities, experimental | `platform:java:ds-cli`, `products:aep:ai-integration` |

---

## Module Classification (Initial)

### Platform Java (P0 = 19 modules, P1 = 14 modules, P2 = 3 modules)

**P0 (90%)**:
- `platform:java:core` — Core utilities, types, exceptions
- `platform:java:domain` — Domain value objects
- `platform:java:security` — Auth/tenant/policy enforcement
- `platform:java:database` — Database abstraction layer
- `platform:java:config` — Configuration validation
- `platform:java:identity` — Identity management
- `platform:java:cache` — Caching layer
- `platform:java:observability` — Metrics/logging infrastructure
- `platform:java:messaging` — Event/message abstractions
- `platform:java:testing` — Test utilities
- `platform:java:http` — HTTP server/client abstractions
- `platform:java:workflow` — Workflow orchestration
- `platform:java:agent-core` — Agent framework (P0 due to agent ecosystem criticality)
- `platform:java:audit` — Audit trail enforcement
- `platform:java:governance` — Governance rules
- `platform:java:policy-as-code` — Policy evaluation
- `platform:java:data-governance` — Data classification
- `platform:java:runtime` — Runtime bootstrapping
- `platform:java:tool-runtime` — Tool execution runtime

**P1 (80%)**:
- `platform:java:integration-tests` — Test fixtures
- `platform:java:ai-integration` — LLM gateway/contracts
- `platform:java:ds-cli` — CLI tools (lower priority)
- Other utility modules

**P2 (70%)**:
- Experimental/prototype modules

### Platform TypeScript (P0 = 15 packages, P1 = 25 packages, P2 = 28 packages)

**P0 (90%)**:
- `@ghatana/design-system` — UI primitives
- `@ghatana/tokens` — Design tokens
- `@ghatana/theme` — Theme system
- `@ghatana/forms` — Form primitives
- `@ghatana/state` — Jotai atoms (auth, tenant context)
- `@ghatana/config` — Environment validation
- `@ghatana/api` — API client
- `@ghatana/sso-client` — SSO authentication
- `@ghatana/events` — Event types
- `@ghatana/platform-events` — Event schemas
- `@ghatana/data-grid` — Data grid component
- `@ghatana/canvas` — Canvas/visualization core
- `@ghatana/code-editor` — Code editor component
- `@ghatana/realtime` — Real-time transport
- `@ghatana/platform-utils` — Shared utilities (cn, formatters)

**P1 (80%)**:
- `@ghatana/charts` — Charts component
- `@ghatana/wizard` — Multi-step wizard
- `@ghatana/i18n` — Internationalization
- `@ghatana/accessibility` — Accessibility audit components
- Domain-specific component libraries

**P2 (70%)**:
- Product-specific packages
- Experimental libraries

### Products (P1 = 80%, P2 = 70%)

**Data Cloud (P1 - 80%)**:
- `products:data-cloud:spi` — Shared API contracts
- `products:data-cloud:platform-entity` — Entity definitions
- `products:data-cloud:platform-api` — HTTP API layer
- Other core modules

**AEP (P1 - 80%)**:
- `products:aep:aep-operator-contracts` — Contract definitions
- `products:aep:aep-engine` — Core execution engine
- `products:aep:aep-agent-runtime` — Agent runtime
- Other infrastructure modules

**YAPPC (P2 - 70%)**:
- `products:yappc:core:*` — Product features

**Virtual Org, TutorPutor, Audio-Video, etc. (P2 - 70%)**:
- Product-specific modules

---

## Enforcement

### Java Enforcement (Gradle)

**Root `build.gradle.kts` defines thresholds**:
```kotlin
fun Project.javaCoverageThreshold(): Double = when {
    path.startsWith(":platform:java") && path.matches(P0_MODULES) -> 0.90
    path.startsWith(":platform:java") && path.matches(P1_MODULES) -> 0.80
    path.startsWith(":platform:java") && path.matches(P2_MODULES) -> 0.70
    path.startsWith(":products:data-cloud") -> 0.80
    path.startsWith(":products:data-cloud:planes:action") -> 0.80
    path.startsWith(":products") -> 0.70
    else -> 0.60
}
```

**Per-module `build.gradle.kts` applies threshold**:
```kotlin
tasks.named("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            element = "BUNDLE"
            includes = listOf("com.ghatana.*")
            
            limit {
                metric = "LINE"
                value = METRIC.PERCENTINT
                minimum = project.javaCoverageThreshold().toBigDecimal()
            }
        }
    }
}
```

**CI Task**: Root `build.gradle.kts` provides:
```bash
./gradlew :platform:java:core:jacocoTestCoverageVerification -Pcoverage
```

### TypeScript Enforcement (Vitest)

**Per-package `vitest.config.ts`**:
```typescript
export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      all: true,
      lines: TIER_THRESHOLD,      // 70, 80, or 90
      functions: TIER_THRESHOLD,
      statements: TIER_THRESHOLD,
      branches: TIER_THRESHOLD - 10,  // Branch coverage is harder; relax by 10%
    },
  },
});
```

**CI Task**:
```bash
pnpm run --filter @ghatana/design-system test:coverage
```

---

## Coverage Measurement & Reporting

### Generate Coverage Reports

**Java**:
```bash
./gradlew jacocoTestCoverageVerification -Pcoverage
# Reports: build/reports/jacoco/test/html/index.html per module
```

**TypeScript**:
```bash
pnpm run test:coverage --workspaces
# Reports: coverage/vitest per package
```

### Coverage Inventory (Manual - Week 1)

Create `/docs/COVERAGE_INVENTORY.md` listing:
- All 225 Java modules → tier + target + current status
- All 68 TypeScript packages → tier + target + current status
- Weekly snapshot (updated in CI)

### CI Enforcement

1. **Per-module checks**: JaCoCo + Vitest fail build if threshold not met
2. **Aggregate reporting**: Codecov/SonarQube consolidation (existing)
3. **Pre-commit hook**: Vitest coverage check for modified TS packages

---

## Implementation Timeline

**Week 1**: Establish this document + classify all modules (complete)  
**Week 2-3**: Update all platform:java:* modules to use tier-based thresholds  
**Week 4-5**: Update product modules to enforce thresholds  
**Week 6**: TypeScript packages implement tier-based thresholds  
**Week 7+**: Automate inventory/dashboard via CI

---

## Coverage Responsibilities

| Role | Responsibility |
|------|-----------------|
| **Module Owner** | Maintain coverage threshold; report gaps; add tests as needed |
| **Platform Lead** | Enforce platform module thresholds; coordinate tier migrations |
| **Product Lead** | Enforce product module thresholds; escalate tier misclassifications |
| **QA Lead** | Monitor trends; recommend tier adjustments; create shared fixtures |
| **CI/CD Lead** | Maintain coverage tools; enforce CI gates; aggregate reports |

---

## Waivers & Exceptions

Module can request tier demotion if:
1. **Genuinely not safety-critical**: Utility, experimental, or low-impact code
2. **Business justification**: Product roadmap priority, tech debt, planned deprecation
3. **Explicit approval**: Platform + Product lead sign-off required

Process:
1. Create COVERAGE_WAIVER_<MODULE>.md in `docs/`
2. Document reason, duration, and remediation plan
3. Link from COVERAGE_INVENTORY.md
4. Expires in 6 months; requires renewal or remediation

---

## Success Metrics

- [ ] All P0 modules ≥ 90% coverage (within 8 weeks)
- [ ] All P1 modules ≥ 80% coverage (within 12 weeks)
- [ ] All P2 modules ≥ 70% coverage (within 16 weeks)
- [ ] CI gates enforce on all new merges
- [ ] Weekly coverage trend report generated
- [ ] Zero regressions (coverage never decreases after enforcement)

---

**Next Review**: 2026-06-23 (12 weeks post-establishment)
