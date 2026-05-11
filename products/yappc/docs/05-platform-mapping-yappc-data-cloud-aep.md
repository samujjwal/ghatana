# Platform Mapping: YAPPC / Data Cloud+AEP

This document maps the agent catalog into an implementation-oriented platform split.

**IMPORTANT**: AEP and Data Cloud are now merged into one platform product. YAPPC integrates with the merged Data Cloud+AEP platform product through typed contracts.

Goal:
- YAPPC = product/workspace/project-facing platform and lifecycle application layer
- Data Cloud+AEP = merged intelligence, agent execution, data, retrieval, memory, telemetry, evidence, policy, and evaluation platform product
- Shared packages = domain types, schemas, contracts, event definitions, UI system, policy artifacts

---

## 1. Responsibility Split

### YAPPC Core Responsibilities
YAPPC should own:
- workspace and project management
- user identity within workspace context
- persona and role assignment
- requirements lifecycle
- spec authoring and export
- UX flows and canvas-based experience
- project planning, backlog, and sprint visualization
- architecture/design document management
- audit log views and activity feeds
- diagrams and traceability views
- human-agent interaction surfaces
- workflow UI for approve / reject / refine / promote

YAPPC examples:
- Workspace module
- Project module
- Requirement module
- Versioning module
- Diagram module
- Audit viewer
- Search UI
- Prompt / plan / confirm / generate / preview / download flow

### Data Cloud+AEP Responsibilities
Data Cloud+AEP should own:
- agent registry
- agent routing and orchestration
- tool registry and invocation
- multi-agent delegation
- memory management (working, episodic, semantic)
- reflection / self-critique / evaluation flywheel
- structured execution contracts
- safety and policy checks for agent runs
- agent observability
- execution traces
- parallel and chained task execution
- reusable agent runtime abstractions
- raw and curated data storage
- vector embeddings
- search indexes
- telemetry / analytics pipelines
- event storage
- experimental data
- cost and usage history
- historical project/evidence retrieval
- audit evidence storage
- model evaluation datasets
- feedback/evaluation loops
- drift detection

Data Cloud+AEP examples:
- LifecycleOrchestratorAgent
- RequirementsOrchestrator
- ToolSelectionTaskAgent
- MemoryWriteTaskAgent
- ReflectionTaskAgent
- SafetyGuardrailTaskAgent
- ResultAggregationTaskAgent
- SearchIndexBuildTaskAgent
- DataValidationTaskAgent
- TelemetryInstrumentationTaskAgent
- DriftDetectionTaskAgent

### Shared Packages Responsibilities
Shared packages should own:
- domain models
- event schemas
- API/GraphQL contracts
- plugin contracts
- Prisma generated types or schema utilities
- UI design tokens / shared components
- observability conventions
- policy schemas
- agent execution contract types

---

## 2. Recommended Module Mapping

### YAPPC App Modules
- apps/yappc-web
- apps/yappc-desktop
- services/yappc-api

### YAPPC Core Domain Modules
- libs/domain/workspace
- libs/domain/project
- libs/domain/requirement
- libs/domain/versioning
- libs/domain/diagram
- libs/domain/audit
- libs/domain/planning
- libs/domain/traceability
- libs/domain/plugin-management

### Data Cloud+AEP Modules
- services/data-cloud-aep-runtime
- libs/data-cloud-aep/agent-registry
- libs/data-cloud-aep/execution-engine
- libs/data-cloud-aep/tool-registry
- libs/data-cloud-aep/memory
- libs/data-cloud-aep/reflection
- libs/data-cloud-aep/evaluation
- libs/data-cloud-aep/policy-guardrails
- libs/data-cloud-aep/contracts
- libs/data-cloud-aep/events
- libs/data-cloud-aep/search
- libs/data-cloud-aep/embeddings
- libs/data-cloud-aep/analytics
- libs/data-cloud-aep/telemetry
- libs/data-cloud-aep/evaluation-datasets

### Shared Technical Modules
- libs/shared/types
- libs/shared/events
- libs/shared/graphql
- libs/shared/ui
- libs/shared/security
- libs/shared/observability
- libs/shared/plugin-contracts

---

## 3. Agent Placement Guidance

### Agents that belong mostly in Data Cloud+AEP
- LifecycleOrchestratorAgent
- DiscoveryOrchestrator
- RequirementsOrchestrator
- ArchitectureOrchestrator
- QAOrchestrator
- AgentSelectionTaskAgent
- ToolSelectionTaskAgent
- MemoryWriteTaskAgent
- MemoryRetrievalTaskAgent
- ReflectionTaskAgent
- SelfCritiqueTaskAgent
- ResultAggregationTaskAgent
- SafetyGuardrailTaskAgent
- SearchIndexBuildTaskAgent
- DataValidationTaskAgent
- TelemetryInstrumentationTaskAgent
- FeatureAdoptionTaskAgent
- FunnelAnalysisTaskAgent
- DriftDetectionTaskAgent
- EmbeddingRefreshTaskAgent
- SemanticCachingTaskAgent
- UsageAnalyticsCapabilityAgent

Reason:
These are runtime, orchestration, cognition, memory, execution, data-heavy, analytics-heavy, or retrieval-heavy processing responsibilities.

### Agents that belong mostly in YAPPC business services
- RequirementCaptureTaskAgent
- RequirementApprovalTaskAgent
- RequirementVersioningTaskAgent
- SprintScopeTaskAgent
- AuditLogModuleImplementationTaskAgent
- DiagramModuleImplementationTaskAgent
- WorkspaceManagementImplementationTaskAgent
- ProjectManagementImplementationTaskAgent
- ExportModuleImplementationTaskAgent

Reason:
These are lifecycle/business-domain features tightly coupled to workspace/project/product context.


---

## 4. Prisma / Database Mapping

### Strong candidates for YAPPC Prisma models
- User
- Workspace
- WorkspaceMember
- PersonaAssignment
- Project
- Requirement
- RequirementVersion
- VersionSnapshot
- Diagram
- AuditLog
- AIChat
- ExportArtifact
- TraceLink
- Sprint
- Epic
- Story
- ApprovalRequest
- ActivityFeedItem

### Strong candidates for Data Cloud+AEP persistence
- AgentDefinition
- AgentRun
- AgentRunStep
- ToolInvocation
- MemoryRecord
- ReflectionRecord
- EvaluationResult
- PolicyDecision
- ExecutionTrace
- DelegationRecord
- RetryRecord
- SafetyIncident
- EventEnvelope
- SearchDocument
- EmbeddingRecord
- AnalyticsEvent
- MetricSnapshot
- ExperimentRun
- FeedbackRecord
- CostUsageRecord
- DatasetArtifact
- DriftReport

---

## 5. GraphQL / API Boundary Guidance

### YAPPC GraphQL should expose
- workspace queries/mutations
- project queries/mutations
- requirement CRUD
- requirement versions
- spec export
- planning entities
- diagrams
- audit views
- traceability views
- approval flows
- user workspace context

### Data Cloud+AEP APIs should expose
- submit agent task
- inspect agent run
- stream agent progress
- retrieve execution trace
- register/unregister tool
- query memory summaries
- run evaluation
- fetch guardrail result
- search
- analytics queries
- telemetry summaries
- embedding freshness status
- experiment results
- feedback summaries

---

## 6. Event Ownership Guidance

### YAPPC-owned event families
- workspace.*
- project.*
- requirement.*
- spec.*
- sprint.*
- story.*
- approval.*
- diagram.*
- audit_log.*

### Data Cloud+AEP-owned event families
- agent.*
- tool.*
- memory.*
- reflection.*
- evaluation.*
- policy.*
- analytics.*
- telemetry.*
- embedding.*
- search.*
- experiment.*
- feedback.*

---

## 7. UI Surface Mapping

### YAPPC UI surfaces
- dashboard
- workspace switcher
- project list
- search-first landing page
- requirements canvas/editor
- AI chat / assistant side panel
- backlog/planning board
- architecture/design views
- traceability graph
- diagrams and flows
- audit/activity feed
- preview/export screens

### Data Cloud+AEP admin / debug UI surfaces
- agent run viewer
- execution trace viewer
- tool call inspector
- memory viewer
- evaluation dashboards
- policy decision logs
- cost / token dashboards
- usage analytics
- adoption dashboards
- search quality dashboards
- feedback cluster dashboards
- experiment analytics
- drift / model quality dashboards

---

## 8. Suggested End-to-End Runtime Flow

1. User enters requirement or product idea in YAPPC UI
2. YAPPC creates project-scoped request
3. YAPPC emits requirement.submitted
4. Data Cloud+AEP receives orchestration request through typed contracts
5. RequirementsOrchestrator delegates:
   - RequirementCaptureTaskAgent
   - RequirementNormalizationTaskAgent
   - AcceptanceCriteriaTaskAgent
   - RequirementToStoryTraceTaskAgent
6. Data Cloud+AEP stores execution trace and optional memory artifacts
7. Data Cloud+AEP updates search index / telemetry / analytics
8. YAPPC presents structured output, confidence, diffs, and approval actions
9. Human approves, edits, or rejects
10. YAPPC persists approved requirement/version/audit record
11. Downstream story/test/architecture agents are triggered

---

## 9. Recommended Guardrails

### In YAPPC
- role-based access on every mutation
- workspace scoping
- human approval for high-impact actions
- immutable audit trail for critical lifecycle events

### In Data Cloud+AEP
- structured agent execution contracts
- tool schema validation
- model usage budgets
- memory retention policy
- safety classification before tool execution
- execution traceability
- PII classification and redaction
- retention classes
- tenant-safe indexing
- analytics access scope
- evaluation dataset provenance

---

## 10. Practical Build Sequence

### First
Implement in YAPPC:
- Workspace
- Project
- Requirement
- Versioning
- AuditLog
- AIChat
- basic traceability

### Second
Implement in Data Cloud+AEP:
- agent registry
- execution engine
- requirements orchestration
- tool registry
- run tracing
- safety guardrails
- event store
- search index
- embeddings
- analytics telemetry
- evaluation dataset store

### Fourth
Connect lifecycle flywheel:
- requirement → story
- story → implementation
- implementation → test
- test → release
- release → telemetry
- telemetry → feedback
- feedback → enhancement