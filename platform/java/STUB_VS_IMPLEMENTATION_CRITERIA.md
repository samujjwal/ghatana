# Java Stub Module vs Implementation Decision Criteria

## Overview

This document defines the decision criteria for when to create a stub module versus a full implementation in `platform/java`. This ensures consistency across the platform and prevents accumulation of empty or placeholder modules.

## Background

During the platform folder audit (April 2025), 23 empty Java stub modules were removed because they provided no value and created maintenance overhead. Going forward, any new platform/java modules must follow these guidelines.

## Decision Criteria

### When to Create a Stub Module

A stub module is appropriate **only** when:

1. **Clear Implementation Plan Exists**
   - There is a documented roadmap or ADR (Architectural Decision Record)
   - The implementation timeline is defined (within 1-2 sprints)
   - The module has a designated owner

2. **Placeholder for Contract Definition**
   - The module defines shared interfaces, DTOs, or domain models
   - These contracts are needed by other modules before full implementation
   - Example: `platform/java:agent-core` might define `Agent` interface before full agent execution logic

3. **Dependency Constraints**
   - The module depends on external systems not yet available
   - Waiting for infrastructure (e.g., database schema, external API)
   - The stub provides type safety and compilation stability

4. **Minimum Viable Content**
   - At minimum, the stub must contain:
     - Package structure with at least one interface or class
     - Javadoc explaining the purpose and implementation plan
     - `@doc.*` tags on all public APIs
     - Basic unit tests for the contracts

### When to Create a Full Implementation

A full implementation is required when:

1. **Immediate Business Value**
   - The module is needed for a product feature in the current sprint
   - There is no dependency blocking implementation

2. **Shared Infrastructure**
   - The module provides reusable functionality for multiple products
   - Examples: HTTP client, database abstractions, security utilities

3. **Contract Alone Is Insufficient**
   - Just defining interfaces without behavior provides no value
   - Other modules cannot meaningfully use the stub

## Module Content Requirements

### Stub Module Minimum Content

```java
/**
 * @doc.type class
 * @doc.purpose Placeholder for [module purpose]
 * @doc.layer platform
 * @doc.pattern Stub
 * 
 * IMPLEMENTATION PLAN:
 * - Phase 1 (Sprint X): [specific features]
 * - Phase 2 (Sprint Y): [specific features]
 * 
 * OWNER: [team/person]
 */
package com.ghatana.platform.module;

/**
 * Placeholder interface defining the contract.
 * Full implementation planned for Sprint X.
 */
public interface ModuleContract {
    // Define minimal contract needed by dependents
}
```

### Full Implementation Requirements

- Complete implementation of all public APIs
- Comprehensive unit tests (>80% coverage)
- Integration tests where applicable
- Full Javadoc with `@doc.*` tags
- Error handling with platform error taxonomy
- Observability (metrics, logging, tracing)
- Security considerations addressed

## Examples

### Acceptable Stub

```java
// platform/java:agent-framework (acceptable stub)
// Purpose: Define agent contract before runtime implementation
package com.ghatana.platform.agent.framework;

/**
 * @doc.type interface
 * @doc.purpose Agent execution contract
 * @doc.layer platform
 * @doc.pattern Contract
 * 
 * IMPLEMENTATION: Planned for agent-core module
 */
public interface Agent {
    String getId();
    CompletableFuture<AgentResult> execute(AgentContext context);
}
```

### Unacceptable Stub

```java
// Empty package with no interfaces or classes
// Should be removed or not created
package com.ghatana.platform.module;
// No content - REMOVE THIS MODULE
```

## Governance

### Module Creation Checklist

Before creating a new `platform/java` module:

- [ ] Document the business requirement
- [ ] Create ADR explaining the module's purpose
- [ ] Define implementation timeline (if stub)
- [ ] Identify module owner
- [ ] Ensure minimum content requirements are met
- [ ] Add to platform/java README

### Module Review

- Platform team reviews all new module proposals
- Stubs are reviewed every 2 sprints for implementation progress
- Stubs without progress for >4 sprints are removed
- Full implementations require code review and test coverage validation

## Removed Modules (April 2025)

The following empty stub modules were removed during the audit:
- agent-dispatch
- agent-framework
- agent-learning
- agent-registry
- agent-resilience
- ai-api
- ai-experimental
- connectors
- event-cloud
- ingestion
- observability-clickhouse
- observability-http
- schema-registry
- workflow-jdbc
- workflow-runtime
- yaml-template

These modules can be recreated only if they meet the criteria in this document.

## References

- Platform folder audit: `docs/platform-folder-audit-4-21.md`
- Copilot instructions: `.github/copilot-instructions.md`
- ADR template: `docs/architecture/adr-template.md` (if exists)
