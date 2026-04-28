# YAPPC Persistence Ownership Matrix

> **Purpose:** Declare per-entity persistence ownership to prevent duplicate models and ensure clear architectural boundaries.
> **Last Updated:** 2026-04-27
> **Related Finding:** F-Y008 — Two persistence stacks (JDBC + Prisma)

## Ownership Principles

1. **Single Source of Truth:** Each entity has exactly one persistence layer owner.
2. **No Cross-Stack Duplication:** An entity owned by JDBC must not have a duplicate Prisma model, and vice versa.
3. **Clear Boundaries:** API layers must use the appropriate persistence stack based on entity ownership.
4. **Migration Path:** If an entity needs to change ownership, it must go through a documented migration process.

## Entity Ownership Declaration

### Prisma-Owned Entities (Node/TypeScript)

These entities are managed by Prisma and accessed via the TypeScript API layer:

| Entity | Module | Purpose | API Layer |
|--------|--------|---------|-----------|
| User | frontend/apps/api | User identity and profile management | REST/GraphQL (Node) |
| UserSession | frontend/apps/api | JWT refresh token session management | REST (Node) |
| Workspace | frontend/apps/api | Organizational workspace container | REST/GraphQL (Node) |
| WorkspaceMember | frontend/apps/api | Workspace membership and roles | REST/GraphQL (Node) |
| Project | frontend/apps/api | Project container with ownership model | REST/GraphQL (Node) |
| WorkspaceProject | frontend/apps/api | Project inclusion in workspaces (read-only) | REST/GraphQL (Node) |
| Requirement | frontend/apps/api | Requirement lifecycle with versioning | REST/GraphQL (Node) |
| RequirementVersion | frontend/apps/api | Immutable requirement version snapshots | REST/GraphQL (Node) |
| TraceLink | frontend/apps/api | Requirement traceability links | REST/GraphQL (Node) |
| ApprovalRequest | frontend/apps/api | Human review gate for policy-sensitive actions | REST/GraphQL (Node) |
| PolicyDecision | frontend/apps/api | Structured policy evaluation outcomes | REST/GraphQL (Node) |
| AgentRun | frontend/apps/api | AI agent execution progress tracking | REST/GraphQL (Node) |
| ExportArtifact | frontend/apps/api | Project export job tracking | REST/GraphQL (Node) |
| CanvasDocument | frontend/apps/api | Canvas diagram container | REST/GraphQL (Node) |
| CanvasVersion | frontend/apps/api | Canvas version history | REST/GraphQL (Node) |
| Page | frontend/apps/api | Page builder page entity | REST/GraphQL (Node) |
| Workflow | frontend/apps/api | Software engineering workflow container | REST/GraphQL (Node) |
| WorkflowContributor | frontend/apps/api | Workflow contributor tracking | REST/GraphQL (Node) |
| WorkflowAudit | frontend/apps/api | Workflow audit trail | REST/GraphQL (Node) |
| WorkflowTemplate | frontend/apps/api | Workflow template definitions | REST/GraphQL (Node) |
| Phase | frontend/apps/api | DevSecOps lifecycle phase container | REST/GraphQL (Node) |
| Milestone | frontend/apps/api | Phase milestone tracking | REST/GraphQL (Node) |
| Sprint | frontend/apps/api | Time-boxed iteration for planning | REST/GraphQL (Node) |
| Item | frontend/apps/api | Work item tracking (feature, task, bug) | REST/GraphQL (Node) |
| ItemOwner | frontend/apps/api | Work item ownership tracking | REST/GraphQL (Node) |
| ItemTag | frontend/apps/api | Work item tagging | REST/GraphQL (Node) |
| Artifact | frontend/apps/api | Work item artifact tracking | REST/GraphQL (Node) |
| ItemIntegration | frontend/apps/api | Work item integrations | REST/GraphQL (Node) |
| ItemDependency | frontend/apps/api | Work item dependencies | REST/GraphQL (Node) |
| AIInsight | frontend/apps/api | AI-generated insights | REST/GraphQL (Node) |
| VectorEmbedding | frontend/apps/api | Vector embeddings for search | REST/GraphQL (Node) |
| ItemEmbedding | frontend/apps/api | Item-specific embeddings | REST/GraphQL (Node) |
| ItemComment | frontend/apps/api | Work item comments | REST/GraphQL (Node) |
| LifecycleArtifact | frontend/apps/api | Lifecycle phase artifacts | REST/GraphQL (Node) |
| LifecycleItem | frontend/apps/api | Lifecycle phase items | REST/GraphQL (Node) |
| LifecycleAIInsight | frontend/apps/api | AI insights for lifecycle | REST/GraphQL (Node) |
| LifecycleActivityLog | frontend/apps/api | Lifecycle activity logging | REST/GraphQL (Node) |
| LifecycleExecutionResult | frontend/apps/api | Lifecycle execution results | REST/GraphQL (Node) |
| UserAIPreferences | frontend/apps/api | User AI preference settings | REST/GraphQL (Node) |
| AIGeneratedPlan | frontend/apps/api | AI-generated workflow plans | REST/GraphQL (Node) |
| PhaseKPI | frontend/apps/api | Phase KPI tracking | REST/GraphQL (Node) |
| PhasePrediction | frontend/apps/api | AI predictions for phases | REST/GraphQL (Node) |

### JDBC-Owned Entities (Java)

These entities are managed by JDBC and accessed via the Java API layer:

| Entity | Module | Purpose | API Layer |
|--------|--------|---------|-----------|
| ApprovalRequest | core/services-lifecycle | Human approval workflow state | ActiveJ HTTP (Java) |
| AuditLog | core/services-lifecycle | Audit trail for lifecycle events | ActiveJ HTTP (Java) |
| DlqEntry | core/services-lifecycle | Dead letter queue entries | ActiveJ HTTP (Java) |
| WorkflowState | core/services-lifecycle | Workflow execution state machine | ActiveJ HTTP (Java) |
| CostTracking | core/ai | AI cost tracking and budgeting | ActiveJ HTTP (Java) |
| KGNode | core/knowledge-graph | Knowledge graph nodes | ActiveJ HTTP (Java) |
| KGEdge | core/knowledge-graph | Knowledge graph edges | ActiveJ HTTP (Java) |
| ArtifactModelVersion | core/yappc-services | Artifact version tracking | ActiveJ HTTP (Java) |
| ArtifactGraph | core/yappc-services | Artifact dependency graph | ActiveJ HTTP (Java) |
| SecretAccessLog | core/services-platform | Secret access audit log | ActiveJ HTTP (Java) |
| KeyRotation | core/services-platform | Cryptographic key rotation | ActiveJ HTTP (Java) |

### Overlapping Entities (Require Resolution)

These entities currently have models in both stacks and require consolidation:

| Entity | Current State | Resolution Plan | Priority |
|--------|---------------|-----------------|----------|
| ApprovalRequest | Both Prisma and JDBC | Consolidate to Prisma (primary UI surface) | High |
| Workflow | Both Prisma and JDBC | Consolidate to Prisma (AI workflow UI) | High |
| AuditLog | Both Prisma and JDBC | Consolidate to Prisma (unified audit) | High |

## Architectural Boundaries

### When to Use Prisma (Node/TypeScript)
- **UI-facing entities:** Entities primarily accessed through the web UI
- **GraphQL/REST APIs:** Entities exposed through the Node API layer
- **Frontend-heavy workflows:** Entities with heavy frontend interaction
- **Real-time collaboration:** Entities requiring real-time updates (WebSocket, SSE)
- **Developer Experience:** Entities where TypeScript type safety is beneficial

### When to Use JDBC (Java)
- **Backend services:** Entities primarily accessed through Java services
- **ActiveJ HTTP endpoints:** Entities exposed through the Java HTTP layer
- **Workflow orchestration:** Entities used in workflow state machines
- **High-throughput processing:** Entities requiring high-performance batch processing
- **Platform integration:** Entities that integrate with platform-level services

## Enforcement Rules

### ArchUnit Rules (Java)
Add to `build-logic/conventions/src/main/java/archunit/`:

```java
@ArchTest
static final ArchRule java_entities_must_not_duplicate_prisma_models =
    classes().that()
        .resideInAPackage("..domain..")
        .and().areRecords()
        .should()
        .haveNameNotMatchingAny(getPrismaEntityNames());
```

### Prisma Rules (TypeScript)
Add to `eslint.config.mjs`:

```javascript
// Prevent adding models that duplicate Java entities
{
  group: ['@ghatana/yappc-jdbc-entities'],
  message: 'This entity already exists in Java JDBC stack. Use the Java entity instead.',
}
```

## Migration Process

To migrate an entity from one stack to another:

1. **Create migration plan:** Document the reason, affected APIs, and rollback strategy.
2. **Add compatibility layer:** Create a temporary bridge between the old and new stack.
3. **Migrate data:** Move data from old to new persistence layer.
4. **Update APIs:** Switch API consumers to use the new stack.
5. **Remove old model:** Delete the old model after verification.
6. **Update documentation:** Reflect the change in this ownership matrix.

## References

- **Finding:** F-Y008 — Two persistence stacks (JDBC + Prisma)
- **Prisma Schema:** `frontend/apps/api/prisma/schema.prisma`
- **JDBC Migrations:** `platform/src/main/resources/db/migration/`
- **API Architecture:** `docs/api/API_OWNERSHIP_MATRIX.md`
