# API Client Refactoring Plan (P1-9)

## Current State

The `yappcApi` client in `frontend/web/src/lib/api/client.ts` includes many domains that are not mounted in the route manifest, representing latent or unmounted surfaces.

### Mounted Domains (from route-manifest.yaml)

**yappc-services:**
- Intent: `/api/v1/yappc/intent/*`
- Shape: `/api/v1/yappc/shape/*`
- Validate: `/api/v1/yappc/validate/*`
- Generate: `/api/v1/yappc/generate/*`
- Run: `/api/v1/yappc/run/*`
- Observe: `/api/v1/yappc/observe`
- Learn: `/api/v1/yappc/learn/*`
- Evolve: `/api/v1/yappc/evolve/*`
- Lifecycle: `/api/v1/yappc/lifecycle/execute`
- Artifact Graph: `/api/v1/yappc/artifact/graph/*`, `/api/v1/yappc/artifact/residual/*`, `/api/v1/yappc/artifact/import-source`
- Preview Sessions: `/api/v1/preview/session/*`

**yappc-api:**
- Vector: `/api/v1/vector/*`
- Agents: `/api/v1/agents/*`
- Workflows: `/api/v1/workflows/*`

**scaffold-api:**
- Packs: `/api/v1/packs/*`
- Scaffold Projects: `/api/v1/scaffold/projects/*`
- Templates: `/api/v1/templates/*`
- Dependencies: `/api/v1/dependencies/*`

**refactorer-api:**
- Jobs: `/api/v1/jobs/*`

### Unmounted/Latent Domains in Current Client

The following domains in the API client are NOT in the route manifest and should be considered latent or unmounted:

- `billing` - `/api/billing`
- `operations` - `/api/oncall`, `/api/services/topology`
- `collaboration` - `/api/activity`, `/api/teams/hub`, `/api/collaboration/*`, `/api/calendar/events`
- `settings` - `/api/settings`
- `anomalies` - `/api/anomalies`

## Status

**Phase 1**: ✅ Completed - Created domain-scoped clients
- yappcLifecycleClient.ts - Core lifecycle API (intent, shape, validate, generate, run, observe, learn, evolve, lifecycle)
- yappcArtifactClient.ts - Artifact graph and preview sessions (artifacts, pageArtifacts, sourceImports, codeAssociations, gates, previewSessions)
- yappcWorkflowsClient.ts - Workflow engine (workflows)
- yappcVectorClient.ts - Vector search and RAG (vector)
- yappcAgentsClient.ts - Agent registry and execution (agents)
- scaffoldClient.ts - Scaffold packs, projects, templates, dependencies (packs, scaffoldProjects, templates, dependencies)
- refactorerClient.ts - Refactoring jobs (jobs, diagnostics)

**Phase 2**: ✅ Completed - Consolidated latent APIs
- Created latentApis.ts module with @experimental tags
- Moved billing, operations, collaboration, settings, anomalies, canvas, errorReporting, personas, phases, telemetry, audit, userData, results, rateLimit

**Phase 3**: ✅ Completed - Added lint/contract tests
- Created eslint-rules/api-contract-enforcement.js with rules:
  - no-raw-fetch - Enforce typed client usage
  - no-graphql-in-rest-client - Prevent GraphQL domains in REST client
  - no-latent-api-in-production - Prevent latent API usage without feature flags
- Created src/lib/api/__tests__/apiContract.test.ts for contract parity tests

**Phase 4**: ✅ Completed - Update documentation and migration guide

## Migration Guide

### Before (Monolithic Client)

```typescript
import { yappcApi } from '@/lib/api/client';

// Lifecycle APIs
await yappcApi.lifecycle.phases();
await yappcApi.intent.capture({ text, projectId });
await yappcApi.generate.run({ projectId, phase });

// Artifact APIs
await yappcApi.artifacts.list({ projectId });
await yappcApi.pageArtifacts.saveDocument(artifactId, documentId, document, scope);

// Workflow APIs
await yappcApi.workflows.start(templateId, tenantId);

// Latent APIs (not recommended)
await yappcApi.billing.getSummary();
```

### After (Domain-Scoped Clients)

```typescript
// Lifecycle APIs
import { yappcLifecycleClient } from '@/lib/api/yappcLifecycleClient';

await yappcLifecycleClient.lifecycle.phases();
await yappcLifecycleClient.intent.capture({ text, projectId });
await yappcLifecycleClient.generate.run({ projectId, phase });

// Artifact APIs
import { yappcArtifactClient } from '@/lib/api/yappcArtifactClient';

await yappcArtifactClient.artifacts.list({ projectId });
await yappcArtifactClient.pageArtifacts.saveDocument(artifactId, documentId, document, scope);

// Workflow APIs
import { yappcWorkflowsClient } from '@/lib/api/yappcWorkflowsClient';

await yappcWorkflowsClient.workflows.start(templateId, tenantId);

// Vector APIs
import { yappcVectorClient } from '@/lib/api/yappcVectorClient';

await yappcVectorClient.vector.search({ query, topK });

// Agent APIs
import { yappcAgentsClient } from '@/lib/api/yappcAgentsClient';

await yappcAgentsClient.agents.list();

// Scaffold APIs
import { scaffoldClient } from '@/lib/api/scaffoldClient';

await scaffoldClient.packs.list();

// Refactorer APIs
import { refactorerClient } from '@/lib/api/refactorerClient';

await refactorerClient.jobs.create({ targetPath, rules });
```

### Latent APIs (Experimental)

```typescript
import { latentApis } from '@/lib/api/latentApis';

// Only use with explicit feature flags
if (featureFlags.enableBilling) {
  await latentApis.billing.getSummary();
}
```

### Migration Steps

1. **Identify imports**: Search for `yappcApi` imports in your codebase
2. **Map to domain-scoped clients**: Determine which domain-scoped client to use
3. **Update imports**: Replace old imports with new domain-scoped client imports
4. **Update calls**: Update method calls to use the new client structure
5. **Test**: Verify all API calls still work correctly
6. **Remove old imports**: Clean up old `yappcApi` imports

### Rollback Plan

If issues arise during migration:
1. Revert to using `yappcApi` from the original client.ts
2. Report issues to the team for investigation
3. The original client.ts remains available as a fallback during transition period

## Classification

### KEEP

**Canonical Documentation:**
- `docs/api/openapi.yaml` - Single source of truth for API contracts
- `docs/api/route-manifest.yaml` - Route inventory (needs refresh)
- `docs/RELEASE_READINESS_CHECKLIST.md` - Release checklist
- `docs/api/API_SURFACE_CANONICALIZATION.md` - API surface guidance
- `docs/api/API_CLIENT_REFACTORING_PLAN.md` - This refactoring plan
- `docs/api/API_CLIENT_MIGRATION_GUIDE.md` - Migration guide

**Core Architecture:**
- `core/yappc-services/` - Main lifecycle service
- `core/yappc-domain-impl/` - Domain implementations
- `core/yappc-domain/` - Domain models
- `platform/` - Shared platform modules

**Frontend - New Domain-Scoped Clients:**
- `frontend/web/src/lib/api/yappcLifecycleClient.ts` - Lifecycle API client
- `frontend/web/src/lib/api/yappcArtifactClient.ts` - Artifact API client
- `frontend/web/src/lib/api/yappcWorkflowsClient.ts` - Workflow API client
- `frontend/web/src/lib/api/yappcVectorClient.ts` - Vector API client
- `frontend/web/src/lib/api/yappcAgentsClient.ts` - Agent API client
- `frontend/web/src/lib/api/scaffoldClient.ts` - Scaffold API client
- `frontend/web/src/lib/api/refactorerClient.ts` - Refactorer API client

**Frontend - Latent APIs:**
- `frontend/web/src/lib/api/latentApis.ts` - Consolidated latent APIs with @experimental tags

**Frontend - Original Client (Transition Period):**
- `frontend/web/src/lib/api/client.ts` - Original monolithic client (keep during transition, mark as deprecated)

### MERGE

**Documentation:**
- Merge API_CLIENT_REFACTORING_PLAN.md completion status into API_CLIENT_MIGRATION_GUIDE.md
- Consolidate audit findings into a single docs/audits/ARCHIVE.md before archiving

### ARCHIVE

**Stale Audit/TODO Documents:**
- Archive completed audit files to docs/audits/ARCHIVE/
- Archive completed implementation plans to docs/plans/ARCHIVE/

### DELETE

**Checked-in Error Reports:**
- Delete any checked-in error report files
- Delete temporary debug files

## Execution Steps

### Phase 1: Create Domain-Scoped Clients ✅

1. Extract lifecycle APIs into yappcLifecycleClient.ts ✅
2. Extract artifact/preview APIs into yappcArtifactClient.ts ✅
3. Extract vector APIs into yappcVectorClient.ts ✅
4. Extract agent APIs into yappcAgentsClient.ts ✅
5. Extract workflow APIs into yappcWorkflowsClient.ts ✅
6. Extract scaffold APIs into scaffoldClient.ts ✅
7. Extract refactorer APIs into refactorerClient.ts ✅

### Phase 2: Consolidate Latent APIs ✅

1. Create latentApis.ts module ✅
2. Move all unmounted APIs to latentApis.ts ✅
3. Add @experimental tags to all latent API exports ✅
4. Update imports across codebase to use new structure (manual migration required)

### Phase 3: Add Lint/Contract Tests ✅

1. Create ESLint rule no-raw-fetch ✅
2. Create ESLint rule no-graphql-in-rest-client ✅
3. Create ESLint rule no-latent-api-in-production ✅
4. Create contract test for client/manifest parity ✅
5. Create contract test for client/OpenAPI parity ✅
6. Add to CI pipeline (manual integration required)

### Phase 4: Update Documentation and Migration Guide ✅

1. Update API_CLIENT_REFACTORING_PLAN.md with completion status ✅
2. Create API_CLIENT_MIGRATION_GUIDE.md with migration steps ✅
3. Update client.ts JSDoc comments to mark as deprecated (pending)
4. Update docs/API_SURFACE_CANONICALIZATION.md with new client structure (pending)
5. Add migration guide to RELEASE_READINESS_CHECKLIST.md (pending)

## Automated Tests

Create the following automated checks to prevent future accumulation:

1. **Dead Route Detection Test** ✅
   - Fail if route in code but not in manifest
   - Fail if route in manifest but not in OpenAPI

2. **Unused Export Detection Test**
   - Fail on unused exports in production code
   - Allow unused exports in test files with explicit comment

3. **Doc Link Check Test**
   - Fail on broken internal doc links
   - Fail on links to archived docs

4. **Module Boundary Check Test**
   - Fail on imports from deprecated modules
   - Fail on imports from archived directories

## Next Steps

1. Complete Phase 4 documentation updates
2. Integrate ESLint rules into eslint.config.mjs
3. Run migration across codebase to update imports
4. Add contract tests to CI pipeline
5. Mark original client.ts as deprecated with migration notice
6. Monitor for issues during transition period
7. Remove original client.ts after successful migration (6-month grace period)
