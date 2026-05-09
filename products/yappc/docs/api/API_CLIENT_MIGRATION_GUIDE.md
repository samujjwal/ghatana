# API Client Migration Guide

## Overview

This guide helps developers migrate from the monolithic `yappcApi` client to the new domain-scoped API clients.

## Why This Migration?

The monolithic `yappcApi` client has grown to include many domains, some of which are not mounted in the route manifest (latent APIs). This refactoring:

- Splits the client by mounted product domains for better organization
- Moves latent/unmounted APIs behind explicit feature modules with `@experimental` tags
- Enforces client usage with lint rules to prevent raw `fetch()` calls
- Provides better type safety and clearer API boundaries

## New Client Structure

### Domain-Scoped Clients

| Client | Purpose | Domains |
|--------|---------|---------|
| `yappcLifecycleClient` | Core lifecycle APIs | intent, shape, validate, generate, run, observe, learn, evolve, lifecycle |
| `yappcArtifactClient` | Artifact graph and preview sessions | artifacts, pageArtifacts, sourceImports, codeAssociations, gates, previewSessions |
| `yappcWorkflowsClient` | Workflow engine | workflows |
| `yappcVectorClient` | Vector search and RAG | vector |
| `yappcAgentsClient` | Agent registry and execution | agents |
| `scaffoldClient` | Scaffold packs, projects, templates, dependencies | packs, scaffoldProjects, templates, dependencies |
| `refactorerClient` | Refactoring jobs | jobs, diagnostics |

### Latent APIs Module

The `latentApis` module consolidates all APIs that are NOT in the route manifest. These are marked as `@experimental` and should only be used with explicit feature flags.

**Latent domains:** billing, operations, collaboration, settings, anomalies, canvas, errorReporting, personas, phases, telemetry, audit, userData, results, rateLimit

## Migration Steps

### Step 1: Identify Current Usage

Search your codebase for `yappcApi` imports:

```bash
grep -r "yappcApi" src/
```

### Step 2: Map to Domain-Scoped Client

Determine which domain-scoped client contains the APIs you're using:

- **Lifecycle APIs** (intent, shape, validate, generate, run, observe, learn, evolve) → `yappcLifecycleClient`
- **Artifact APIs** (artifacts, pageArtifacts, sourceImports, codeAssociations, gates, previewSessions) → `yappcArtifactClient`
- **Workflow APIs** (workflows) → `yappcWorkflowsClient`
- **Vector APIs** (vector) → `yappcVectorClient`
- **Agent APIs** (agents) → `yappcAgentsClient`
- **Scaffold APIs** (packs, scaffoldProjects, templates, dependencies) → `scaffoldClient`
- **Refactorer APIs** (jobs, diagnostics) → `refactorerClient`
- **Latent APIs** (billing, operations, collaboration, etc.) → `latentApis` (use with feature flags only)

### Step 3: Update Imports

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';
```

**After:**
```typescript
import { yappcLifecycleClient } from '@/lib/api/yappcLifecycleClient';
import { yappcArtifactClient } from '@/lib/api/yappcArtifactClient';
import { yappcWorkflowsClient } from '@/lib/api/yappcWorkflowsClient';
```

### Step 4: Update Method Calls

**Before:**
```typescript
await yappcApi.lifecycle.phases();
await yappcApi.intent.capture({ text, projectId });
await yappcApi.artifacts.list({ projectId });
await yappcApi.workflows.start(templateId, tenantId);
```

**After:**
```typescript
await yappcLifecycleClient.lifecycle.phases();
await yappcLifecycleClient.intent.capture({ text, projectId });
await yappcArtifactClient.artifacts.list({ projectId });
await yappcWorkflowsClient.workflows.start(templateId, tenantId);
```

### Step 5: Handle Latent APIs

If you're using latent APIs (not in route manifest), use them with explicit feature flags:

```typescript
import { latentApis } from '@/lib/api/latentApis';

if (featureFlags.enableBilling) {
  await latentApis.billing.getSummary();
}
```

### Step 6: Test

Run your tests to ensure all API calls still work correctly:

```bash
pnpm test
```

### Step 7: Clean Up

Remove old `yappcApi` imports after migration is complete.

## Common Migration Patterns

### Pattern 1: Multiple APIs from Same Domain

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

await yappcApi.lifecycle.phases();
await yappcApi.intent.capture({ text, projectId });
await yappcApi.generate.run({ projectId, phase });
```

**After:**
```typescript
import { yappcLifecycleClient } from '@/lib/api/yappcLifecycleClient';

await yappcLifecycleClient.lifecycle.phases();
await yappcLifecycleClient.intent.capture({ text, projectId });
await yappcLifecycleClient.generate.run({ projectId, phase });
```

### Pattern 2: APIs from Multiple Domains

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';

await yappcApi.artifacts.list({ projectId });
await yappcApi.workflows.start(templateId, tenantId);
```

**After:**
```typescript
import { yappcArtifactClient } from '@/lib/api/yappcArtifactClient';
import { yappcWorkflowsClient } from '@/lib/api/yappcWorkflowsClient';

await yappcArtifactClient.artifacts.list({ projectId });
await yappcWorkflowsClient.workflows.start(templateId, tenantId);
```

### Pattern 3: Destructured Imports

**Before:**
```typescript
import { yappcApi } from '@/lib/api/client';
const { lifecycle, intent, generate } = yappcApi;
```

**After:**
```typescript
import { yappcLifecycleClient } from '@/lib/api/yappcLifecycleClient';
const { lifecycle, intent, generate } = yappcLifecycleClient;
```

## Rollback Plan

If issues arise during migration:

1. Revert to using `yappcApi` from the original `client.ts`
2. Report issues to the team for investigation
3. The original `client.ts` remains available as a fallback during the transition period

## Lint Rules

The following ESLint rules enforce proper API client usage:

- **no-raw-fetch**: Disallows direct `fetch()` calls, requires typed API clients
- **no-graphql-in-rest-client**: Prevents GraphQL-owned domains from being called via REST client
- **no-latent-api-in-production**: Prevents latent API usage without feature flags

## Contract Tests

Contract tests verify API parity between:
- Client and route manifest
- Client and OpenAPI specification
- Route manifest and OpenAPI specification

Run contract tests with:

```bash
pnpm test apiContract
```

## Timeline

- **Phase 1-3**: Domain-scoped clients created and lint rules added (✅ Complete)
- **Phase 4**: Documentation and migration guide (🔄 In Progress)
- **Migration Period**: 6 months grace period for codebase migration
- **Deprecation**: Original `client.ts` marked as deprecated after migration period
- **Removal**: Original `client.ts` removed 6 months after deprecation

## Support

If you encounter issues during migration:

1. Check this guide for common patterns
2. Review the API_CLIENT_REFACTORING_PLAN.md for detailed implementation
3. Consult the team for complex migration scenarios
4. Use the rollback plan if needed

## Checklist

- [ ] Identify all `yappcApi` imports in your codebase
- [ ] Map each API to its domain-scoped client
- [ ] Update imports to use domain-scoped clients
- [ ] Update method calls to use new client structure
- [ ] Handle latent APIs with feature flags
- [ ] Run tests to verify functionality
- [ ] Remove old `yappcApi` imports
- [ ] Run lint rules to verify compliance
- [ ] Run contract tests to verify parity
