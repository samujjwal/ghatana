# YAPPC Route Docs

> Auto-generated from `src/routes.ts` and `products/yappc/docs/api/route-manifest.yaml` on 2026-05-27.
> Run `node scripts/generate-route-inventory.mjs` to regenerate.

## Summary

| Source | Count |
| --- | ---: |
| Frontend mounted routes | 31 |
| API manifest operations | 131 |

## API Operations By Owner

| Owner | Operations |
| --- | ---: |
| `refactorer-api` | 12 |
| `scaffold-api` | 27 |
| `yappc-api` | 33 |
| `yappc-services` | 59 |

## Frontend Routes

| URL Path | Route File | Owner | Auth | Nav | Feature Flag | Expected User Actions |
| --- | --- | --- | --- | --- | --- | --- |
| `preview/builder` | `routes/preview-builder.tsx` | Preview runtime | Public iframe runtime | Hidden | None | Render generated builder preview |
| `/` | `routes/dashboard.tsx` | Lifecycle dashboard | Public shell entry | Primary entry | None | Open dashboard<br>Start or resume workspace flow |
| `login` | `routes/login.tsx` | Auth | Guest | Auth entry | None | Sign in<br>Recover authenticated session |
| `onboarding` | `routes/onboarding.tsx` | Onboarding | Authenticated | Post-login flow | None | Complete onboarding checklist<br>Choose workspace/project starting point |
| `workspaces` | `routes/app/workspaces.tsx` | Workspace | Authenticated | App shell | None | Create workspace<br>Select workspace |
| `projects` | `routes/app/projects.tsx` | Project | Authenticated | App shell | None | Create project<br>Open project |
| `profile` | `routes/profile.tsx` | User settings | Authenticated | User menu | None | View profile<br>Update profile settings |
| `settings` | `routes/settings.tsx` | Workspace settings | Authenticated | User menu | None | Open workspace settings<br>Update workspace preferences |
| `p/:projectId` | `routes/app/project/_shell.tsx` | Project shell | Authenticated project access | Project shell | Phase-specific flags | Navigate phase tabs<br>Open project settings<br>Open intent drawer |
| `p/:projectId` | `routes/app/project/index.tsx` | Project shell | Authenticated project access | Redirect/default | None | Redirect to Intent phase |
| `p/:projectId/intent` | `routes/app/project/intent.tsx` | Intent phase | Authenticated project access | Project phase tab | intent | Capture intent notes<br>Open intent workspace<br>Define requirements |
| `p/:projectId/shape` | `routes/app/project/shape.tsx` | Shape phase | Authenticated project access | Project phase tab | shape | Open canvas workspace<br>Review shape contract<br>Start builder review |
| `p/:projectId/validate` | `routes/app/project/validate.tsx` | Validate phase | Authenticated project access | Project phase tab | validate | Review approval gates<br>Approve lifecycle transition |
| `p/:projectId/generate` | `routes/app/project/generate.tsx` | Generate phase | Authenticated project access | Project phase tab | generate | Start generation<br>Review diff<br>Apply/reject/rollback generated changes |
| `p/:projectId/run` | `routes/app/project/run.tsx` | Run phase | Authenticated project access | Project phase tab | run | Start run workflow<br>Retry run<br>Rollback run<br>Promote run |
| `p/:projectId/observe` | `routes/app/project/observe.tsx` | Observe phase | Authenticated project access | Project phase tab | observe | Inspect preview diagnostics<br>Review runtime health<br>Review recommendations |
| `p/:projectId/learn` | `routes/app/project/learn.tsx` | Learn phase | Authenticated project access | Project phase tab | learn | Review learning evidence<br>Inspect agent governance state |
| `p/:projectId/evolve` | `routes/app/project/evolve.tsx` | Evolve phase | Authenticated project access | Project phase tab | evolve | Review evolution proposal<br>Inspect impact analysis<br>Approve or reject diff |
| `p/:projectId/settings` | `routes/app/project/settings.tsx` | Project settings | Authenticated project access | Project settings action | None | Update project metadata<br>Manage project access settings |
| `p/:projectId/canvas` | `routes/app/project/canvas.tsx` | Legacy Shape canvas | Authenticated project access | Deep link only | legacy route policy | Open compatibility canvas surface |
| `p/:projectId/preview` | `routes/app/project/preview.tsx` | Legacy preview | Authenticated project access | Deep link only | legacy route policy | Open compatibility preview surface |
| `p/:projectId/deploy` | `routes/app/project/deploy.tsx` | Legacy deploy | Authenticated project access | Deep link only | legacy route policy | Open compatibility deploy surface |
| `p/:projectId/lifecycle` | `routes/app/project/lifecycle.tsx` | Legacy lifecycle | Authenticated project access | Deep link only | legacy route policy | Open compatibility lifecycle explorer |
| `kernel-health` | `routes/app/kernel-health.tsx` | Kernel visibility | OWNER/ADMIN capability | Capability-gated app route | kernel visibility | Review ProductUnit health<br>Open product detail |
| `kernel-health/products/:productUnitId` | `routes/app/kernel-health-product.tsx` | Kernel visibility | OWNER/ADMIN capability | Kernel health detail | kernel visibility | Inspect lifecycle timeline<br>Inspect gates/artifacts/deployment details |
| `product-family` | `routes/app/product-family.tsx` | Product-family control plane | product-family:control-plane capability | Capability-gated app route | product family | Review assets/releases<br>Promote product-family asset |
| `admin/prompt-versions` | `routes/app/admin/prompt-versions.tsx` | Prompt admin | OWNER/ADMIN capability | Admin route | admin prompts | View prompt versions<br>Rollback prompt<br>Rebalance weights |
| `admin/ab-testing` | `routes/app/admin/ab-testing.tsx` | Experiment admin | OWNER/ADMIN capability | Admin route | admin experiments | Create experiment<br>Promote winner<br>Pause experiment |
| `admin/feature-flags` | `routes/app/admin/feature-flags.tsx` | Feature flag admin | OWNER/ADMIN capability | Admin route | admin feature flags | List flags<br>Update tenant flag<br>Review flag audit |
| `admin/observability` | `routes/app/admin/observability.tsx` | Admin observability | OWNER/ADMIN capability | Admin route | admin observability | Review SLO/cost/domain/OpenAPI release gates |
| `/*` | `routes/not-found.tsx` | Error handling | Public | Catch-all | None | Show not-found recovery navigation |

## API Manifest Operations

| Method | Path | Operation ID | Owner | Auth | Scopes | Boundary | Privacy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `POST` | `/api/v1/jobs` | `createRefactorJob` | `refactorer-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `DELETE` | `/api/v1/jobs/{jobId}` | `deleteRefactorJob` | `refactorer-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/jobs/{jobId}` | `getRefactorJob` | `refactorer-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/jobs/{jobId}/report` | `getRefactorJobReport` | `refactorer-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/jobs/{jobId}/runs` | `listRefactorJobRuns` | `refactorer-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/jobs/{jobId}/runs` | `createRefactorJobRun` | `refactorer-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/jobs/{jobId}/runs/{runId}` | `getRefactorJobRun` | `refactorer-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/jobs/{jobId}/runs/{runId}/logs` | `getRefactorJobRunLogs` | `refactorer-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/jobs/{jobId}/start` | `startRefactorJob` | `refactorer-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/jobs/{jobId}/stop` | `stopRefactorJob` | `refactorer-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/v1/config` | `getRefactorerConfig` | `refactorer-api` | public | `[]` | YAPPC | PUBLIC |
| `POST` | `/v1/diagnose` | `diagnoseRefactorer` | `refactorer-api` | required | `admin` | YAPPC | INTERNAL |
| `POST` | `/api/v1/dependencies/add-conflicts` | `addDependencyConflicts` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/dependencies/analyze/pack/{name}` | `analyzePackDependencies` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/dependencies/analyze/project` | `analyzeProjectDependencies` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/dependencies/conflicts` | `detectDependencyConflicts` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/packs` | `listPacks` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/{name}` | `getPack` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/{name}/templates` | `getPackTemplates` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/{name}/validate` | `validatePack` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/{name}/variables` | `getPackVariables` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/categories` | `getPackCategories` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/languages` | `getPackLanguages` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `GET` | `/api/v1/packs/platforms` | `getPackPlatforms` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `POST` | `/api/v1/packs/refresh` | `refreshPacks` | `scaffold-api` | required | `admin` | YAPPC | INTERNAL |
| `POST` | `/api/v1/scaffold/projects` | `createScaffoldProject` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/scaffold/projects/add-feature` | `addFeatureToProject` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/scaffold/projects/check-updates` | `checkScaffoldUpdates` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/scaffold/projects/export` | `exportScaffoldProject` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/scaffold/projects/features` | `getScaffoldFeatures` | `scaffold-api` | required | `project:read` | YAPPC | INTERNAL |
| `POST` | `/api/v1/scaffold/projects/import` | `importScaffoldProject` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/scaffold/projects/info` | `getScaffoldProjectInfo` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/scaffold/projects/preview-update` | `previewScaffoldUpdate` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/scaffold/projects/state` | `getScaffoldProjectState` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/scaffold/projects/update` | `updateScaffoldProject` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/scaffold/projects/validate` | `validateScaffoldProject` | `scaffold-api` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/templates/helpers` | `getTemplateHelpers` | `scaffold-api` | required | `workspace:read` | YAPPC | INTERNAL |
| `POST` | `/api/v1/templates/render` | `renderTemplate` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/templates/validate` | `validateTemplate` | `scaffold-api` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/agents` | `listAgents` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `GET` | `/api/v1/agents/{name}` | `getAgent` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `POST` | `/api/v1/agents/{name}/execute` | `executeAgent` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/v1/agents/{name}/health` | `agentHealth` | `yappc-api` | public | `[]` | DATA_CLOUD_AEP | PUBLIC |
| `GET` | `/api/v1/agents/by-capability/{capability}` | `agentsByCapability` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `GET` | `/api/v1/agents/capabilities` | `agentsCapabilities` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `POST` | `/api/v1/agents/copilot/chat` | `copilotChat` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/v1/agents/health` | `agentsHealth` | `yappc-api` | public | `[]` | DATA_CLOUD_AEP | PUBLIC |
| `POST` | `/api/v1/agents/predict` | `predictAgent` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `POST` | `/api/v1/agents/search` | `searchAgents` | `yappc-api` | required | `workspace:read` | DATA_CLOUD_AEP | INTERNAL |
| `POST` | `/api/v1/vector/index` | `vectorIndex` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `DELETE` | `/api/v1/vector/index/{id}` | `vectorIndexDelete` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/vector/index/batch` | `vectorIndexBatch` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/vector/rag` | `vectorRag` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/vector/rag/chat` | `vectorRagChat` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/vector/search` | `vectorSearch` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/vector/search/hybrid` | `vectorSearchHybrid` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/v1/vector/similar/{id}` | `vectorSimilar` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/v1/workflows` | `listWorkflows` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows` | `createWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `DELETE` | `/api/v1/workflows/{id}` | `deleteWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/v1/workflows/{id}` | `getWorkflow` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/cancel` | `cancelWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/pause` | `pauseWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/plans/generate` | `generateWorkflowPlan` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/resume` | `resumeWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/route` | `routeWorkflow` | `yappc-api` | required | `project:read` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/start` | `startWorkflow` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/steps/{stepId}/goto` | `gotoWorkflowStep` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{id}/steps/advance` | `advanceWorkflowStep` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `POST` | `/api/v1/workflows/{workflowId}/plans/{planId}/approve` | `approveWorkflowPlan` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | RESTRICTED |
| `POST` | `/api/v1/workflows/{workflowId}/plans/{planId}/reject` | `rejectWorkflowPlan` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | RESTRICTED |
| `PUT` | `/api/v1/workflows/{workflowId}/plans/{planId}/steps` | `updateWorkflowPlanSteps` | `yappc-api` | required | `project:write` | DATA_CLOUD_AEP | CONFIDENTIAL |
| `GET` | `/api/admin/ab-experiments` | `listAdminAbExperiments` | `yappc-services` | required | `admin` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/admin/ab-experiments` | `createAdminAbExperiment` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `POST` | `/api/admin/ab-experiments/{experimentId}/pause` | `pauseAdminAbExperiment` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `POST` | `/api/admin/ab-experiments/{experimentId}/promote` | `promoteAdminAbExperimentWinner` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `GET` | `/api/admin/feature-flags` | `listAdminFeatureFlags` | `yappc-services` | required | `admin` | YAPPC | CONFIDENTIAL |
| `PUT` | `/api/admin/feature-flags/{flagKey}` | `setAdminFeatureFlag` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `GET` | `/api/admin/feature-flags/{flagKey}/audit` | `listAdminFeatureFlagAudit` | `yappc-services` | required | `admin` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/admin/observability/release-gates` | `listAdminObservabilityReleaseGates` | `yappc-services` | required | `admin` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/admin/prompt-versions` | `listAdminPromptVersions` | `yappc-services` | required | `admin` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/admin/prompt-versions/{versionId}/rollback` | `rollbackAdminPromptVersion` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `PATCH` | `/api/admin/prompt-versions/weights` | `updateAdminPromptVersionWeights` | `yappc-services` | required | `admin` | YAPPC | RESTRICTED |
| `GET` | `/api/v1/capabilities` | `getCapabilities` | `yappc-services` | required | `workspace:read` | YAPPC | INTERNAL |
| `POST` | `/api/v1/capabilities` | `queryCapabilities` | `yappc-services` | required | `admin` | YAPPC | INTERNAL |
| `GET` | `/api/v1/dashboard/actions` | `getDashboardActions` | `yappc-services` | required | `workspace:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/dashboard/actions` | `requestDashboardActions` | `yappc-services` | required | `workspace:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/phase/packet` | `getPhasePacket` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/phase/packet` | `requestPhasePacket` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/preview/session/create` | `createPreviewSession` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/preview/session/validate` | `validatePreviewSession` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/graph/analyze` | `analyzeArtifactGraph` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/graph/ingest` | `ingestArtifactGraph` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/graph/merge` | `mergeArtifactGraph` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/graph/query` | `queryArtifactGraph` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/import-source` | `importSource` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/artifact/residual/analyze` | `analyzeResidual` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/evolve` | `evolveSystem` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/evolve/{proposalId}/approve` | `approveEvolutionProposal` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/evolve/{proposalId}/reject` | `rejectEvolutionProposal` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/evolve/with-constraints` | `evolveWithConstraints` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/generate` | `generateArtifacts` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/generate/artifacts/{id}` | `getArtifacts` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/generate/diff` | `generateDiff` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/generate/product-unit-intent` | `generateProductUnitIntent` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/info` | `serviceInfo` | `yappc-services` | public | `[]` | YAPPC | PUBLIC |
| `GET` | `/api/v1/yappc/intent/{id}` | `getIntent` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/intent/analyze` | `analyzeIntent` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/intent/capture` | `captureIntent` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/learn` | `learnFromRun` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/learn/with-context` | `learnWithContext` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/lifecycle/execute` | `executeLifecycle` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/observe` | `observeRun` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/product-family/assets` | `listProductFamilyAssets` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/product-family/assets/{assetId}/promotions` | `promoteProductFamilyAsset` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `GET` | `/api/v1/yappc/product-family/doc-truth` | `listProductFamilyDocTruthWarnings` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/product-family/kernel-timeline/{productUnitId}` | `getProductFamilyKernelTimeline` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/product-family/releases/{productKey}` | `getProductFamilyReleaseReadiness` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}` | `listProductFamilyReuseRecommendations` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/run` | `runArtifacts` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/run/promote` | `promoteRun` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/run/retry` | `retryRun` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/run/rollback` | `rollbackRun` | `yappc-services` | required | `project:write` | YAPPC | RESTRICTED |
| `POST` | `/api/v1/yappc/run/with-observation` | `runWithObservation` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/api/v1/yappc/shape/{id}` | `getShape` | `yappc-services` | required | `project:read` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/shape/derive` | `deriveShape` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/shape/model` | `modelShape` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/validate` | `validateArtifacts` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/validate/with-config` | `validateWithConfig` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `POST` | `/api/v1/yappc/validate/with-policy` | `validateWithPolicy` | `yappc-services` | required | `project:write` | YAPPC | CONFIDENTIAL |
| `GET` | `/health` | `liveness` | `yappc-services` | public | `[]` | YAPPC | PUBLIC |
