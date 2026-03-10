# Platform Mapping: YAPPC / AEP / Data Layer / Runtime

This document maps the agent catalog into an implementation-oriented platform split.

Goal:
- YAPPC = product/workspace/project-facing platform and lifecycle application layer
- AEP = agent execution platform / orchestration / memory / tooling runtime
- Data Cloud / data layer = data, telemetry, search, analytics, embeddings, historical evidence
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

### AEP Responsibilities
AEP should own:
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

AEP examples:
- LifecycleOrchestratorAgent
- RequirementsOrchestrator
- ToolSelectionTaskAgent
- MemoryWriteTaskAgent
- ReflectionTaskAgent
- SafetyGuardrailTaskAgent
- ResultAggregationTaskAgent

### Data Cloud / Data Layer Responsibilities
Data layer should own:
- raw and curated data storage
- vector embeddings
- search indexes
- telemetry / analytics pipelines
- event storage
- experimental data
- cost and usage history
- historical requirement/spec/version evidence
- audit evidence storage
- model evaluation datasets

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

### AEP Modules
- services/aep-runtime
- libs/aep/agent-registry
- libs/aep/execution-engine
- libs/aep/tool-registry
- libs/aep/memory
- libs/aep/reflection
- libs/aep/evaluation
- libs/aep/policy-guardrails
- libs/aep/contracts

### Data / Search / AI Modules
- services/data-cloud
- libs/data/events
- libs/data/search
- libs/data/embeddings
- libs/data/analytics
- libs/data/telemetry
- libs/data/evaluation-datasets

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

### Agents that belong mostly in AEP
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

Reason:
These are runtime, orchestration, cognition, memory, and execution concerns.

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

### Agents that belong mostly in Data Cloud
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
These are data-heavy, analytics-heavy, or retrieval-heavy processing responsibilities.

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

### Strong candidates for AEP persistence
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

### Strong candidates for data platform persistence
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

### AEP APIs should expose
- submit agent task
- inspect agent run
- stream agent progress
- retrieve execution trace
- register/unregister tool
- query memory summaries
- run evaluation
- fetch guardrail result

### Data APIs should expose
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

### AEP-owned event families
- agent.*
- tool.*
- memory.*
- reflection.*
- evaluation.*
- policy.*

### Data-owned event families
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

### AEP admin / debug UI surfaces
- agent run viewer
- execution trace viewer
- tool call inspector
- memory viewer
- evaluation dashboards
- policy decision logs
- cost / token dashboards

### Data / analytics UI surfaces
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
4. AEP receives orchestration request
5. RequirementsOrchestrator delegates:
   - RequirementCaptureTaskAgent
   - RequirementNormalizationTaskAgent
   - AcceptanceCriteriaTaskAgent
   - RequirementToStoryTraceTaskAgent
6. AEP stores execution trace and optional memory artifacts
7. Data layer updates search index / telemetry / analytics
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

### In AEP
- structured agent execution contracts
- tool schema validation
- model usage budgets
- memory retention policy
- safety classification before tool execution
- execution traceability

### In Data Layer
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
Implement in AEP:
- agent registry
- execution engine
- requirements orchestration
- tool registry
- run tracing
- safety guardrails

### Third
Implement in data layer:
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