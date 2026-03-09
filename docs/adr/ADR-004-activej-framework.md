# ADR-004: ActiveJ as Core Async and DI Framework

**Status:** Accepted  
**Date:** 2026-01-10  
**Decision Makers:** Platform Team  
**Phase:** 0 — Architecture  

## Context

The platform needed an async execution model and dependency injection framework. The codebase must support:
- High-throughput event processing (100K+ events/sec target)
- Non-blocking I/O throughout the pipeline
- Lightweight DI without heavyweight containers (Spring, Guice)
- Consistent async primitive across all modules

## Decision

Use **ActiveJ 6.0** as the core framework for:

1. **Async execution**: `io.activej.promise.Promise<T>` as the universal async return type
2. **Dependency injection**: `io.activej.inject.module.AbstractModule` for wiring
3. **Event loop**: `io.activej.eventloop.Eventloop` for non-blocking I/O
4. **HTTP**: `io.activej.http.AsyncServlet` for API endpoints (with `RBACFilter` wrapping)

All agent processing methods return `Promise<AgentResult<O>>`. All DataCloudClient methods return `Promise<T>`. Pipeline execution returns `Promise<PipelineExecutionResult>`.

42 ActiveJ modules wire the platform:
- Platform: `ObservabilityModule`, `AiPlatformObservabilityModule`
- AEP: `AepCoreModule`, `AepPatternModule`, `AepObservabilityModule`, etc. (14 modules)
- Data-Cloud: `DataCloudCoreModule`, `DataCloudStorageModule`, etc. (5 modules)
- Security-Gateway: `SecurityConfigurationModule`, `KeyManagerModule`, etc. (6 modules)

## Rationale

- **Single async primitive** (`Promise`) eliminates impedance mismatch between layers
- **Lightweight DI** — ActiveJ modules are plain Java classes with `@Provides` methods; no annotation scanning, no proxy generation
- **Performance** — ActiveJ's Eventloop is single-threaded per core, avoiding lock contention
- **Test-friendly** — `Promise.of()` and `Promise.ofException()` enable synchronous testing; `Eventloop.create().withCurrentThread()` for async tests

## Consequences

- All team members must understand `Promise` composition (`map`, `mapException`, `then`, `whenComplete`)
- `Promise.ofException(e).getResult()` returns `null` (does NOT throw) — a common testing pitfall
- Thread-local context (TenantContext) does not automatically propagate across Eventloop tasks — explicit transfer required
- No `@Inject` annotation scanning — all wiring is explicit in module `configure()` methods
- Third-party libraries that return `CompletableFuture` require bridging via `Promise.ofFuture()`

## Alternatives Considered

1. **Spring Boot** — rejected; too heavyweight for event-processing hot paths, startup overhead
2. **Google Guice** — rejected; ActiveJ DI is simpler and already adopted
3. **Project Reactor (Mono/Flux)** — rejected; would introduce a parallel async primitive
4. **CompletableFuture** — rejected; less composable than ActiveJ Promise, no built-in retry/timeout
