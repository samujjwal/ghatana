# YAPPC API Reference

Generated from `docs/api/route-manifest.yaml` and `docs/api/openapi.yaml`.
Do not hand-edit route rows; run `python products/yappc/scripts/generate-api-reference.py`.

## Summary

- Manifest routes: 131
- OpenAPI routes: 261
- Missing in OpenAPI: 0
- OpenAPI-only routes outside manifest table: 130
- Operation ID mismatches: 0

## Parity

Every route-manifest path/method/operationId is represented in OpenAPI.

OpenAPI also contains routes outside the route-manifest table. They are not rendered as canonical YAPPC manifest routes here.

## Routes

| Method | Path | Operation | Owner | Auth | Scopes | Privacy | Boundary | OpenAPI Tags | Summary |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/health` | `liveness` | yappc-services | public | - | PUBLIC | YAPPC | health | Liveness probe |
| GET | `/api/v1/yappc/info` | `serviceInfo` | yappc-services | public | - | PUBLIC | YAPPC | lifecycle | Service metadata |
| GET | `/api/v1/yappc/product-family/releases/{productKey}` | `getProductFamilyReleaseReadiness` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | product-family | Get product release readiness |
| GET | `/api/v1/yappc/product-family/assets` | `listProductFamilyAssets` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | product-family | List reusable product-family assets |
| POST | `/api/v1/yappc/product-family/assets/{assetId}/promotions` | `promoteProductFamilyAsset` | yappc-services | required | project:write | RESTRICTED | YAPPC | product-family | Promote a reusable product-family asset |
| GET | `/api/v1/yappc/product-family/doc-truth` | `listProductFamilyDocTruthWarnings` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | product-family | List doc, registry, and code truth warnings |
| GET | `/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}` | `listProductFamilyReuseRecommendations` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | product-family | List guided reusable-asset recommendations |
| GET | `/api/v1/yappc/product-family/kernel-timeline/{productUnitId}` | `getProductFamilyKernelTimeline` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | product-family | Get Kernel lifecycle timeline and rollback visibility |
| GET | `/api/admin/observability/release-gates` | `listAdminObservabilityReleaseGates` | yappc-services | required | admin | CONFIDENTIAL | YAPPC | admin | List admin release-gate observability evidence |
| GET | `/api/admin/feature-flags` | `listAdminFeatureFlags` | yappc-services | required | admin | CONFIDENTIAL | YAPPC | admin | List tenant feature flags |
| PUT | `/api/admin/feature-flags/{flagKey}` | `setAdminFeatureFlag` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Set tenant feature flag |
| GET | `/api/admin/feature-flags/{flagKey}/audit` | `listAdminFeatureFlagAudit` | yappc-services | required | admin | CONFIDENTIAL | YAPPC | admin | List tenant feature flag audit log |
| GET | `/api/admin/ab-experiments` | `listAdminAbExperiments` | yappc-services | required | admin | CONFIDENTIAL | YAPPC | admin | List prompt/model A/B experiments |
| POST | `/api/admin/ab-experiments` | `createAdminAbExperiment` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Create prompt/model A/B experiment |
| POST | `/api/admin/ab-experiments/{experimentId}/promote` | `promoteAdminAbExperimentWinner` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Promote A/B experiment winner |
| POST | `/api/admin/ab-experiments/{experimentId}/pause` | `pauseAdminAbExperiment` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Pause A/B experiment |
| GET | `/api/admin/prompt-versions` | `listAdminPromptVersions` | yappc-services | required | admin | CONFIDENTIAL | YAPPC | admin | List prompt versions |
| POST | `/api/admin/prompt-versions/{versionId}/rollback` | `rollbackAdminPromptVersion` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Roll back to prompt version |
| PATCH | `/api/admin/prompt-versions/weights` | `updateAdminPromptVersionWeights` | yappc-services | required | admin | RESTRICTED | YAPPC | admin | Update prompt version weights |
| POST | `/api/v1/yappc/intent/capture` | `captureIntent` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | intent | Capture product intent |
| POST | `/api/v1/yappc/intent/analyze` | `analyzeIntent` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | intent | Analyze captured intent |
| GET | `/api/v1/yappc/intent/{id}` | `getIntent` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | intent | Get a captured intent by ID |
| POST | `/api/v1/yappc/shape/derive` | `deriveShape` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | shape | Derive system shape from intent |
| POST | `/api/v1/yappc/shape/model` | `modelShape` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | shape | Generate system model (domain entities, APIs) |
| GET | `/api/v1/yappc/shape/{id}` | `getShape` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | shape | Get a shape record by ID |
| POST | `/api/v1/yappc/validate` | `validateArtifacts` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | validate | Run default validation suite |
| POST | `/api/v1/yappc/validate/with-config` | `validateWithConfig` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | validate | Run validation with a custom config |
| POST | `/api/v1/yappc/validate/with-policy` | `validateWithPolicy` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | validate | Run validation against a named policy |
| POST | `/api/v1/yappc/generate` | `generateArtifacts` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | generate | Generate project artifacts (code, config, CI) |
| POST | `/api/v1/yappc/generate/product-unit-intent` | `generateProductUnitIntent` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | generate | Generate Kernel ProductUnitIntent from saved generate state |
| POST | `/api/v1/yappc/generate/diff` | `generateDiff` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | generate | Regenerate artifacts with a change diff |
| GET | `/api/v1/yappc/generate/artifacts/{id}` | `getArtifacts` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | generate | Get generated artifacts for a run |
| POST | `/api/v1/yappc/run` | `runArtifacts` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | run | Execute a YAPPC run |
| POST | `/api/v1/yappc/run/with-observation` | `runWithObservation` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | run | Execute a run with embedded observation collection |
| POST | `/api/v1/yappc/run/retry` | `retryRun` | yappc-services | required | project:write | RESTRICTED | YAPPC | run | Retry a failed run with a new run specification |
| POST | `/api/v1/yappc/run/rollback` | `rollbackRun` | yappc-services | required | project:write | RESTRICTED | YAPPC | run | Rollback the last executed run |
| POST | `/api/v1/yappc/run/promote` | `promoteRun` | yappc-services | required | project:write | RESTRICTED | YAPPC | run | Promote the last validated run to the next environment |
| POST | `/api/v1/yappc/observe` | `observeRun` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | observe | Collect an observation for a run |
| POST | `/api/v1/yappc/learn` | `learnFromRun` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | learn | Analyze a completed run and record learning signals |
| POST | `/api/v1/yappc/learn/with-context` | `learnWithContext` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | learn | Analyze with explicit contextual metadata |
| POST | `/api/v1/yappc/evolve` | `evolveSystem` | yappc-services | required | project:write | RESTRICTED | YAPPC | evolve | Propose evolutions based on accumulated learning signals |
| POST | `/api/v1/yappc/evolve/with-constraints` | `evolveWithConstraints` | yappc-services | required | project:write | RESTRICTED | YAPPC | evolve | Propose evolutions within explicit architectural constraints |
| POST | `/api/v1/yappc/evolve/{proposalId}/approve` | `approveEvolutionProposal` | yappc-services | required | project:write | RESTRICTED | YAPPC | evolve | Approve an evolution proposal and hand off to validate/generate/run |
| POST | `/api/v1/yappc/evolve/{proposalId}/reject` | `rejectEvolutionProposal` | yappc-services | required | project:write | RESTRICTED | YAPPC | evolve | Reject an evolution proposal |
| POST | `/api/v1/yappc/lifecycle/execute` | `executeLifecycle` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | lifecycle | Execute the full YAPPC lifecycle pipeline (intent → shape → generate → validate → run) |
| POST | `/api/v1/yappc/artifact/graph/ingest` | `ingestArtifactGraph` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | artifact-graph | Ingest artifacts into the knowledge graph |
| POST | `/api/v1/yappc/artifact/graph/analyze` | `analyzeArtifactGraph` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | artifact-graph | Analyze the artifact knowledge graph |
| POST | `/api/v1/yappc/artifact/graph/merge` | `mergeArtifactGraph` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | artifact-graph | Merge two artifact graphs |
| POST | `/api/v1/yappc/artifact/graph/query` | `queryArtifactGraph` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | artifact-graph | Query the artifact knowledge graph |
| POST | `/api/v1/yappc/artifact/residual/analyze` | `analyzeResidual` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | artifact-graph | Analyze residual artifacts not yet incorporated into the graph |
| POST | `/api/v1/yappc/artifact/import-source` | `importSource` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | artifact-graph | Start a governed source import job |
| POST | `/api/v1/preview/session/create` | `createPreviewSession` | yappc-services | required | project:write | CONFIDENTIAL | YAPPC | preview | Create a preview session for artifact inspection |
| POST | `/api/v1/preview/session/validate` | `validatePreviewSession` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | preview | Validate a preview session token |
| POST | `/api/v1/capabilities` | `queryCapabilities` | yappc-services | required | admin | INTERNAL | YAPPC | auth | Query user capabilities |
| GET | `/api/v1/capabilities` | `getCapabilities` | yappc-services | required | workspace:read | INTERNAL | YAPPC | auth | Get user capabilities |
| POST | `/api/v1/phase/packet` | `requestPhasePacket` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | lifecycle | Request phase cockpit packet |
| GET | `/api/v1/phase/packet` | `getPhasePacket` | yappc-services | required | project:read | CONFIDENTIAL | YAPPC | lifecycle | Get phase cockpit packet |
| POST | `/api/v1/dashboard/actions` | `requestDashboardActions` | yappc-services | required | workspace:read | CONFIDENTIAL | YAPPC | lifecycle | Request dashboard actions |
| GET | `/api/v1/dashboard/actions` | `getDashboardActions` | yappc-services | required | workspace:read | CONFIDENTIAL | YAPPC | lifecycle | Get dashboard actions |
| POST | `/api/v1/vector/search` | `vectorSearch` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Semantic vector search |
| POST | `/api/v1/vector/search/hybrid` | `vectorSearchHybrid` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Hybrid keyword + semantic vector search |
| GET | `/api/v1/vector/similar/{id}` | `vectorSimilar` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Find documents similar to a stored document |
| POST | `/api/v1/vector/index` | `vectorIndex` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Index a single document |
| POST | `/api/v1/vector/index/batch` | `vectorIndexBatch` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Batch-index multiple documents |
| DELETE | `/api/v1/vector/index/{id}` | `vectorIndexDelete` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Remove a document from the vector index |
| POST | `/api/v1/vector/rag` | `vectorRag` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Retrieval-augmented generation (single turn) |
| POST | `/api/v1/vector/rag/chat` | `vectorRagChat` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | vector | Retrieval-augmented generation (multi-turn chat) |
| GET | `/api/v1/agents` | `listAgents` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | List all registered agents |
| GET | `/api/v1/agents/health` | `agentsHealth` | yappc-api | public | - | PUBLIC | DATA_CLOUD_AEP | agents | Health summary for all agents |
| GET | `/api/v1/agents/capabilities` | `agentsCapabilities` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | List all declared agent capabilities |
| GET | `/api/v1/agents/by-capability/{capability}` | `agentsByCapability` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | Find agents matching a capability |
| GET | `/api/v1/agents/{name}` | `getAgent` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | Get agent descriptor by name |
| GET | `/api/v1/agents/{name}/health` | `agentHealth` | yappc-api | public | - | PUBLIC | DATA_CLOUD_AEP | agents | Health status for a specific agent |
| POST | `/api/v1/agents/{name}/execute` | `executeAgent` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | agents | Execute a specific agent by name |
| POST | `/api/v1/agents/copilot/chat` | `copilotChat` | yappc-api | required | workspace:read | CONFIDENTIAL | DATA_CLOUD_AEP | agents | Natural-language copilot chat backed by agent routing |
| POST | `/api/v1/agents/search` | `searchAgents` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | Search agents by capability or metadata |
| POST | `/api/v1/agents/predict` | `predictAgent` | yappc-api | required | workspace:read | INTERNAL | DATA_CLOUD_AEP | agents | Run a prediction via the best-matching agent |
| POST | `/api/v1/workflows` | `createWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Create a workflow |
| GET | `/api/v1/workflows` | `listWorkflows` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | List all workflows |
| GET | `/api/v1/workflows/{id}` | `getWorkflow` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Get a workflow by ID |
| DELETE | `/api/v1/workflows/{id}` | `deleteWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Delete a workflow |
| POST | `/api/v1/workflows/{id}/start` | `startWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Start a workflow run |
| POST | `/api/v1/workflows/{id}/pause` | `pauseWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Pause a running workflow |
| POST | `/api/v1/workflows/{id}/resume` | `resumeWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Resume a paused workflow |
| POST | `/api/v1/workflows/{id}/cancel` | `cancelWorkflow` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Cancel a workflow run |
| POST | `/api/v1/workflows/{id}/steps/advance` | `advanceWorkflowStep` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Advance workflow to the next step |
| POST | `/api/v1/workflows/{id}/steps/{stepId}/goto` | `gotoWorkflowStep` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Jump to a specific step in the workflow |
| POST | `/api/v1/workflows/{id}/plans/generate` | `generateWorkflowPlan` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Generate an execution plan for a workflow |
| POST | `/api/v1/workflows/{workflowId}/plans/{planId}/approve` | `approveWorkflowPlan` | yappc-api | required | project:write | RESTRICTED | DATA_CLOUD_AEP | workflows | Approve a generated workflow plan |
| POST | `/api/v1/workflows/{workflowId}/plans/{planId}/reject` | `rejectWorkflowPlan` | yappc-api | required | project:write | RESTRICTED | DATA_CLOUD_AEP | workflows | Reject a generated workflow plan |
| PUT | `/api/v1/workflows/{workflowId}/plans/{planId}/steps` | `updateWorkflowPlanSteps` | yappc-api | required | project:write | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Update steps in a workflow plan |
| POST | `/api/v1/workflows/{id}/route` | `routeWorkflow` | yappc-api | required | project:read | CONFIDENTIAL | DATA_CLOUD_AEP | workflows | Route a workflow to an appropriate execution engine |
| GET | `/api/v1/packs` | `listPacks` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | List all available scaffold packs |
| GET | `/api/v1/packs/languages` | `getPackLanguages` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | List supported programming languages |
| GET | `/api/v1/packs/categories` | `getPackCategories` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | List pack categories |
| GET | `/api/v1/packs/platforms` | `getPackPlatforms` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | List supported target platforms |
| POST | `/api/v1/packs/refresh` | `refreshPacks` | scaffold-api | required | admin | INTERNAL | YAPPC | scaffold-packs | Refresh the pack registry from remote sources |
| GET | `/api/v1/packs/{name}` | `getPack` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | Get pack descriptor by name |
| GET | `/api/v1/packs/{name}/validate` | `validatePack` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | Validate a pack's integrity and schema |
| GET | `/api/v1/packs/{name}/templates` | `getPackTemplates` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | List templates provided by a pack |
| GET | `/api/v1/packs/{name}/variables` | `getPackVariables` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-packs | Get variable schema for a pack |
| POST | `/api/v1/scaffold/projects` | `createScaffoldProject` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-projects | Create a new scaffold project |
| POST | `/api/v1/scaffold/projects/add-feature` | `addFeatureToProject` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-projects | Add a feature to an existing scaffold project |
| POST | `/api/v1/scaffold/projects/update` | `updateScaffoldProject` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-projects | Update scaffold project configuration |
| GET | `/api/v1/scaffold/projects/info` | `getScaffoldProjectInfo` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-projects | Get current scaffold project info |
| GET | `/api/v1/scaffold/projects/state` | `getScaffoldProjectState` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-projects | Get full scaffold project state |
| GET | `/api/v1/scaffold/projects/validate` | `validateScaffoldProject` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-projects | Validate the current scaffold project |
| GET | `/api/v1/scaffold/projects/check-updates` | `checkScaffoldUpdates` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-projects | Check for available scaffold pack updates |
| POST | `/api/v1/scaffold/projects/preview-update` | `previewScaffoldUpdate` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-projects | Preview changes from applying a scaffold update |
| GET | `/api/v1/scaffold/projects/features` | `getScaffoldFeatures` | scaffold-api | required | project:read | INTERNAL | YAPPC | scaffold-projects | List features in the current scaffold project |
| POST | `/api/v1/scaffold/projects/export` | `exportScaffoldProject` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-projects | Export scaffold project state |
| POST | `/api/v1/scaffold/projects/import` | `importScaffoldProject` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-projects | Import scaffold project state |
| POST | `/api/v1/templates/render` | `renderTemplate` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-templates | Render a template with provided variables |
| GET | `/api/v1/templates/helpers` | `getTemplateHelpers` | scaffold-api | required | workspace:read | INTERNAL | YAPPC | scaffold-templates | List available template helper functions |
| POST | `/api/v1/templates/validate` | `validateTemplate` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-templates | Validate a template definition |
| GET | `/api/v1/dependencies/analyze/pack/{name}` | `analyzePackDependencies` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-dependencies | Analyze dependencies for a pack |
| POST | `/api/v1/dependencies/analyze/project` | `analyzeProjectDependencies` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-dependencies | Analyze dependencies for a project |
| POST | `/api/v1/dependencies/conflicts` | `detectDependencyConflicts` | scaffold-api | required | project:read | CONFIDENTIAL | YAPPC | scaffold-dependencies | Check for dependency conflicts |
| POST | `/api/v1/dependencies/add-conflicts` | `addDependencyConflicts` | scaffold-api | required | project:write | CONFIDENTIAL | YAPPC | scaffold-dependencies | Check for conflicts introduced by adding new dependencies |
| POST | `/api/v1/jobs` | `createRefactorJob` | refactorer-api | required | project:write | CONFIDENTIAL | YAPPC | refactorer | Create and submit a refactoring job |
| GET | `/api/v1/jobs/{jobId}` | `getRefactorJob` | refactorer-api | required | project:read | CONFIDENTIAL | YAPPC | refactorer | Get job status |
| DELETE | `/api/v1/jobs/{jobId}` | `deleteRefactorJob` | refactorer-api | required | project:write | CONFIDENTIAL | YAPPC | refactorer | Cancel a refactoring job |
| GET | `/api/v1/jobs/{jobId}/report` | `getRefactorJobReport` | refactorer-api | required | project:read | CONFIDENTIAL | YAPPC | refactorer | Get the full report for a completed job |
| POST | `/api/v1/jobs/{jobId}/start` | `startRefactorJob` | refactorer-api | required | project:write | CONFIDENTIAL | YAPPC | refactorer | Start a paused job (not yet implemented — returns 501) |
| POST | `/api/v1/jobs/{jobId}/stop` | `stopRefactorJob` | refactorer-api | required | project:write | CONFIDENTIAL | YAPPC | refactorer | Stop a running job (not yet implemented — returns 501) |
| POST | `/api/v1/jobs/{jobId}/runs` | `createRefactorJobRun` | refactorer-api | required | project:write | CONFIDENTIAL | YAPPC | refactorer | Create a new run for an existing job |
| GET | `/api/v1/jobs/{jobId}/runs` | `listRefactorJobRuns` | refactorer-api | required | project:read | CONFIDENTIAL | YAPPC | refactorer | List runs for a job |
| GET | `/api/v1/jobs/{jobId}/runs/{runId}` | `getRefactorJobRun` | refactorer-api | required | project:read | CONFIDENTIAL | YAPPC | refactorer | Get a specific run |
| GET | `/api/v1/jobs/{jobId}/runs/{runId}/logs` | `getRefactorJobRunLogs` | refactorer-api | required | project:read | CONFIDENTIAL | YAPPC | refactorer | Stream logs for a specific job run |
| POST | `/v1/diagnose` | `diagnoseRefactorer` | refactorer-api | required | admin | INTERNAL | YAPPC | refactorer | Diagnose repository or workspace issues |
| GET | `/v1/config` | `getRefactorerConfig` | refactorer-api | public | - | PUBLIC | YAPPC | refactorer | Get refactorer configuration |
