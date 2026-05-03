# ADR-001: Use ActiveJ Event Loop for DMOS HTTP and Async Execution

**Date**: 2026-05-01  
**Status**: Accepted  
**Deciders**: Platform Engineering, DMOS Product Team  
**Ticket**: DMOS-R0-002 (Product Module Skeleton)

---

## Context

DMOS (Digital Marketing Operating System) requires a non-blocking HTTP server and async execution model that integrates cleanly with the Ghatana platform's existing ActiveJ-based infrastructure. The product must handle concurrent connector operations, durable workflow steps, and high-frequency intake processing without blocking the application thread.

Alternative options considered:
1. **Spring Boot + Tomcat** — blocking I/O, significant additional dependency weight
2. **Vert.x** — event loop model but not aligned with the rest of the Ghatana platform
3. **ActiveJ** — already used across `platform/java/*` modules, native event loop, `Promise<T>` async model

---

## Decision

DMOS uses **ActiveJ** for HTTP routing (`RoutingServlet`, `AsyncServlet`) and async execution (`Promise<T>`). This follows the established platform standard used by all other Ghatana Java products (Data Cloud, AEP, PHR, Finance).

All service methods return `Promise<T>`. No blocking calls on the event loop. Blocking I/O (if needed) is wrapped with `Promise.ofBlocking(...)`.

---

## Consequences

**Positive:**
- Consistent async model across all Ghatana Java products
- No additional dependency weight or version conflicts
- Test harness (`EventloopTestBase`, `runPromise(...)`) already available in `platform:java:testing`
- Unified observability and error propagation pattern

**Negative / Trade-offs:**
- Developers unfamiliar with event loop model must learn the promise chain pattern
- Debugging async stack traces requires structured logging discipline
- Cannot mix blocking code accidentally — requires team awareness

**Mitigations:**
- `dmos-quality-gates.gradle.kts` enforces no-mock and no-stub policies
- ADR-002 documents structured logging requirements
- Platform testing utilities (`EventloopTestBase`) ease test authoring

---

## Compliance

All DMOS servlets and application services follow this pattern. See:
- `DmosStrategyServlet.java` — route pattern
- `WebsiteAuditServiceImpl.java` — async service pattern
- `EventloopTestBase` in `platform/java/testing` — test pattern
