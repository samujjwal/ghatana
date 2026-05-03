# ADR-002: In-Memory Doubles Over Mocking Frameworks in DMOS Tests

**Date**: 2026-05-01  
**Status**: Accepted  
**Deciders**: Platform Engineering, DMOS Product Team  
**Ticket**: DMOS-R0-006 (CI Quality Gates)

---

## Context

Automated test quality is a first-class concern in DMOS. Test theatre (tests that always pass regardless of production code behavior) silently erodes confidence. Mockito-based tests have historically led to:
- Object-literal test patterns that never call production code
- Tests that pass even when implementations are deleted
- Overly coupled tests that break on internal refactoring, not on behavior regression

---

## Decision

DMOS tests use **in-memory doubles** (hand-coded `Fake*` inner classes implementing the same interface as the real subject) instead of Mockito or other mocking frameworks.

The `validateNoMockingFrameworkUsage` Gradle task (in `dmos-quality-gates.gradle.kts`) enforces this by failing the build if `import org.mockito`, `@Mock`, `Mockito.`, or `mock(` appear in test source.

---

## Consequences

**Positive:**
- Tests are readable, deterministic, and stable
- Fake implementations verify the full contract, not just individual invocations
- `dmos-quality-gates.gradle.kts` enforces the policy automatically in CI

**Negative / Trade-offs:**
- Writing fake implementations takes slightly more effort per test class
- Large interfaces may require many methods in the fake

**Mitigations:**
- Interfaces are kept small (single-responsibility)
- Inner `Fake*` classes are co-located in the test file to minimize boilerplate

---

## Compliance

See `DmosStrategyServletTest.java`, `DmosWebsiteAuditServletTest.java`, and all other `*Test.java` files in `dm-api/src/test/` for the established pattern.
