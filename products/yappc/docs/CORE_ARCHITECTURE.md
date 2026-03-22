# YAPPC Core Module Architecture

**Product:** YAPPC (Yet Another Platform Product Composer)  
**Created:** 2026-03-21  
**Phase:** BDY-5 (Boundary Implementation Plan)  
**Status:** ✅ Well-decomposed — 18 Gradle submodules covering 5 domain concerns

---

## Overview

The `products/yappc/core/` directory houses all core domain logic for YAPPC. It is composed of **18 Gradle submodules** organised into 5 domain clusters. The audit (GHATANA_MONOREPO_BOUNDARY_AUDIT_COMPLETE.md) initially flagged this as a "god module" but verification showed it is **already well-split**.

This document serves to:
1. Make the architecture visible and discoverable
2. Define the allowed dependency topology between submodules
3. Prevent structure regression over time

---

## Module Map

### 1. Foundation Layer (`domain`, `spi`, `framework`)

These modules form the dependency base — everything else can depend on them, but they must not depend on each other circularly.

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `domain` | `:products:yappc:core:domain` | 74 | Value objects, domain events, core abstractions |
| `spi` | `:products:yappc:core:spi` | 55 | Unified Plugin SPI + Client API (merged from yappc-plugin-spi + yappc-client-api) |
| `framework` | `:products:yappc:core:framework` | 25 | Framework entry points (merged from framework-api + framework-core) |

**Dependency rules:**
- `domain` → platform only (`platform:java:domain`, `platform:java:core`)
- `spi` → `framework` (compileOnly), `platform:java:plugin`
- `framework` → `domain`, platform modules

---

### 2. AI & Knowledge Layer (`ai`, `knowledge-graph`)

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `ai` | `:products:yappc:core:ai` | 119 | AI integration, LLM orchestration, prompt management |
| `knowledge-graph` | `:products:yappc:core:knowledge-graph` | 13 | Graph knowledge store, entity resolution |

**Dependency rules:**
- `ai` → `domain`, `spi`, `platform:java:ai-integration`
- `knowledge-graph` → `domain`, `spi`, `platform:java:plugin`
- Neither may import from `agents`, `scaffold`, `refactorer`, or `lifecycle`

---

### 3. Agent Execution Layer (`agents/*`)

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `agents` (parent) | `:products:yappc:core:agents` | 459 total | Aggregate of agents sub-tree |
| `agents/specialists` | `:products:yappc:core:agents:specialists` | 324 | Domain-specific agent implementations |
| `agents/runtime` | `:products:yappc:core:agents:runtime` | 60 | Agent execution runtime, context management |
| `agents/workflow` | `:products:yappc:core:agents:workflow` | 59 | Workflow orchestration for multi-agent pipelines |

**Dependency rules:**
- `agents/runtime` → `domain`, `spi`, `platform:java:agent-core`
- `agents/specialists` → `agents/runtime`, `ai`, `domain`, `platform:java:agent-core`
- `agents/workflow` → `agents/runtime`, `platform:java:workflow`
- **CRITICAL:** `agents/specialists` must NOT import from `scaffold` or `refactorer`

---

### 4. Scaffolding Layer (`scaffold/*`)

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `scaffold` (parent) | `:products:yappc:core:scaffold` | 324 total | Project scaffolding system |
| `scaffold/api` | `:products:yappc:core:scaffold:api` | 71 | Public scaffolding API (contracts) |
| `scaffold/core` | `:products:yappc:core:scaffold:core` | 249 | Scaffolding engine implementation |
| `scaffold/packs` | `:products:yappc:core:scaffold:packs` | 4 | Built-in scaffold packs |

**Dependency rules:**
- `scaffold/api` → `domain`, `spi` only (stable public contract layer)
- `scaffold/core` → `scaffold/api`, `ai`, `platform:java:yaml-template`
- `scaffold/packs` → `scaffold/api` only
- **CRITICAL:** Scaffold must NOT import from `agents` or `refactorer`

---

### 5. Refactoring Layer (`refactorer/*`)

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `refactorer/api` | `:products:yappc:core:refactorer:api` | 116 | Refactoring API (symbols, source model) |
| `refactorer/engine` | `:products:yappc:core:refactorer:engine` | 111 | AST transformation engine |

**Dependency rules:**
- `refactorer/api` → `domain` only
- `refactorer/engine` → `refactorer/api`, `ai`
- **CRITICAL:** Refactorer must NOT import from `agents` or `scaffold`

---

### 6. Lifecycle & CLI (`lifecycle`, `cli-tools`)

| Module | Gradle Path | Main Files | Purpose |
|--------|-------------|-----------|---------|
| `lifecycle` | `:products:yappc:core:lifecycle` | 83 | YAPPC session lifecycle, project state management |
| `cli-tools` | `:products:yappc:core:cli-tools` | 6 | CLI commands and tooling |

**Dependency rules:**
- `lifecycle` → `agents`, `scaffold`, `refactorer/api`, `domain`, platform
- `cli-tools` → `lifecycle`, `scaffold/api` (thin CLI wrapper)

---

## Allowed Dependency Matrix

```
platform/* ←────── all modules depend on platform (downward)
     ↑
 [foundation]
  domain  spi  framework
     ↑
  [ai/knowledge]     
   ai  knowledge-graph
     ↑
  [agent execution]
   agents/runtime → agents/specialists → agents/workflow
     ↑
  [scaffolding]
   scaffold/api → scaffold/core → scaffold/packs
     ↑
  [refactoring]
   refactorer/api → refactorer/engine
     ↑
  [lifecycle/cli]
   lifecycle → cli-tools
```

**Forbidden cross-layer imports:**
- `agents/specialists` → `scaffold` (no)
- `agents/specialists` → `refactorer` (no)
- `scaffold` → `agents` (no)
- `refactorer` → `scaffold` (no)
- `refactorer` → `agents` (no)

---

## Size Reference (2026-03-21)

| Module | Source Files | Status |
|--------|-------------|--------|
| `agents/specialists` | 324 | ✅ Large but focussed on agent implementations |
| `scaffold/core` | 249 | ✅ Large but focussed on scaffolding engine |
| `refactorer/api` | 116 | ✅ OK |
| `refactorer/engine` | 111 | ✅ OK |
| `ai` | 119 | ✅ OK |
| `agents/runtime` | 60 | ✅ OK |
| `lifecycle` | 83 | ✅ OK |

All modules are below the 400-file alert threshold (BDY-6-3).

---

## ArchUnit Enforcement (BDY-5-5)

Add the following ArchUnit test to `products/yappc/core/agents/specialists/src/test/java/...` to prevent scaffold/refactorer imports:

```java
@AnalyzeClasses(packages = "com.ghatana.yappc.agents.specialists")
class YappcAgentSpecialistsBoundaryTest {

    @ArchTest
    static final ArchRule no_scaffold_imports = noClasses()
        .that().resideInAPackage("com.ghatana.yappc.agents.specialists..")
        .should().dependOnClassesThat().resideInAPackage("com.ghatana.yappc.scaffold..")
        .because("Agent specialists must not depend on scaffold — they are different concerns. "
               + "See products/yappc/docs/CORE_ARCHITECTURE.md");

    @ArchTest
    static final ArchRule no_refactorer_imports = noClasses()
        .that().resideInAPackage("com.ghatana.yappc.agents.specialists..")
        .should().dependOnClassesThat().resideInAPackage("com.ghatana.yappc.refactorer..")
        .because("Agent specialists must not depend on refactorer engine.");
}
```

---

## Maintenance Guidelines

1. **Adding a new main module under `core/`:** Requires YAPPC Team + Architecture Board approval
2. **Adding a sub-module:** YAPPC Team approval, document purpose and allowed dependencies here
3. **Exceeding 400 files in a module:** Triggers mandatory split review (BDY-6-3 CI gate)
4. **Cross-concern imports:** Any import violating the Allowed Dependency Matrix requires an explicit ADR
