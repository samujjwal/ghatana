# Duplicate Runtime Systems Report

> **Date**: 2026-03-27
> **Scope**: `products/yappc/frontend/web/src`
> **Purpose**: Identify duplicate runtime systems for consolidation

---

## Executive Summary

Multiple duplicate runtime systems have been identified across the YAPPC frontend codebase. The most significant duplication is in HTTP client implementations, with at least three separate HTTP client patterns in use. This creates maintenance burden, inconsistent behavior, and potential for bugs.

---

## 1. Duplicate HTTP Client Implementations

### 1.1 Canonical REST API Client (Fetch-Based)

**Location**: `lib/api/client.ts`

**Characteristics**:
- Uses custom fetch-based implementation
- Typed helpers: `get`, `post`, `patch`, `put`, `del`
- Auth integration via `authService`
- Error handling with `ApiRequestError`
- Documented as single canonical client for REST-owned surfaces

**Usage**: Workspaces, projects, auth, lifecycle, intent/shape, code-generation, code-associations, artifacts, telemetry

### 1.2 Dashboard API Clients (Axios-Based)

**Location**: `clients/dashboard/BaseDashboardApiClient.ts` + 10+ specialized clients

**Characteristics**:
- Uses `axios` HTTP library
- Base class with retry logic, exponential backoff
- Error handling and transformation
- Request/response logging
- Tenant context injection
- Mock mode for testing
- Zod schema validation

**Specialized Clients**:
- `VersionApiClient`
- `WorkspaceApiClient`
- `ArchitectureApiClient`
- `AuditApiClient`
- `AISuggestionsApiClient`
- `TaskApiClient`
- `WorkflowAgentApiClient`
- `AuthorizationApiClient`
- `RiskApiClient`
- `RequirementsApiClient`

**Overlap**: These clients duplicate HTTP functionality already present in the canonical client (retry, error handling, auth).

### 1.3 Canvas API Client (Fetch-Based, Separate Implementation)

**Location**: `services/canvas/api/CanvasAPIClient.ts`

**Characteristics**:
- Uses fetch with `parseJsonResponse` from `lib/http`
- Cookie-based authentication (`credentials: include`)
- Canvas-specific type definitions
- Separate from canonical client

**Overlap**: Duplicates fetch-based HTTP handling that should use the canonical client.

### 1.4 Other API Clients

**Locations**: 
- `lib/api/yappcAgentsClient.ts`
- `lib/api/yappcLifecycleClient.ts`
- `lib/api/yappcVectorClient.ts`
- `lib/api/scaffoldClient.ts`
- `lib/api/refactorerClient.ts`
- `lib/api/yappcWorkflowsClient.ts`
- `lib/api/yappcArtifactClient.ts`
- `services/aep/AepOrchestrationClient.ts`
- `services/ai/AIService.ts`
- `services/anomaly/AnomalyDetectionService.ts`

**Overlap**: Multiple client files that should potentially be consolidated into the canonical client pattern.

---

## 2. Preview Runtime Systems

### 2.1 Preview Builder Route

**Location**: `routes/preview-builder.tsx`

### 2.2 Project Preview Route

**Location**: `routes/app/project/preview.tsx`

### 2.3 Live Preview Panel

**Location**: `components/studio/LivePreviewPanel.tsx`

### 2.4 Preview Locale Fixtures

**Location**: `services/preview/PreviewLocaleFixtures.ts`

**Overlap**: Multiple preview implementations that may have duplicated logic for preview session management, token validation, and iframe communication.

---

## 3. Builder Model Systems

### 3.1 PageDesigner

**Location**: `components/canvas/page/PageDesigner.tsx`

### 3.2 Builder Document Adapter

**Location**: `components/canvas/page/builder-document-adapter.ts`

### 3.3 ImportOrchestrationService

**Location**: `services/canvas/ImportOrchestrationService.ts`

**Overlap**: Potential duplication in builder document transformation and orchestration logic.

---

## 4. Local Storage Fallback Patterns

### 4.1 SessionManager

**Location**: `services/session/SessionManager.ts`

### 4.2 OnboardingStatusService

**Location**: `services/onboarding/OnboardingStatusService.ts`

### 4.3 Canvas Backend Service

**Location**: `services/canvasBackend.ts`

**Overlap**: Multiple services implementing local storage fallback patterns that should be consolidated into a single pattern.

---

## 5. Route Model Systems

### 5.1 Main Routes Configuration

**Location**: `routes.ts`

### 5.2 Phase Cockpit Routes

**Location**: `routes/app/project/_phaseCockpit.tsx`

### 5.3 Phase Status Panels

**Location**: `routes/app/project/PhaseStatusPanels.tsx`

**Overlap**: Multiple route configuration systems that may have duplicated routing logic.

---

## Consolidation Recommendations

### Priority 1: HTTP Client Consolidation

**Action**: Consolidate all HTTP clients to use the canonical REST API client pattern.

**Steps**:
1. Migrate dashboard clients to use the canonical fetch-based client
2. Remove axios dependency if no longer needed after migration
3. Consolidate specialized clients into the canonical client structure
4. Ensure all retry, error handling, and auth logic is consistent

**Estimated Effort**: 5-7 days

### Priority 2: Preview Runtime Consolidation

**Action**: Consolidate preview session management into a single service.

**Steps**:
1. Create a unified `PreviewSessionService` that handles all preview-related logic
2. Migrate preview builder, project preview, and live preview to use the unified service
3. Ensure consistent token validation, origin checks, and iframe communication

**Estimated Effort**: 3-4 days

### Priority 3: Local Storage Fallback Consolidation

**Action**: Create a single local storage abstraction layer.

**Steps**:
1. Create `LocalStorageService` with consistent fallback patterns
2. Migrate SessionManager, OnboardingStatusService, and canvasBackend to use the unified service
3. Ensure all local storage operations have proper error handling and fallback logic

**Estimated Effort**: 2-3 days

### Priority 4: Builder Model Consolidation

**Action**: Consolidate builder document transformation logic.

**Steps**:
1. Review PageDesigner, builder-document-adapter, and ImportOrchestrationService for overlap
2. Create a unified `BuilderDocumentService` if appropriate
3. Ensure single source of truth for builder document transformations

**Estimated Effort**: 3-4 days

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Breaking changes during HTTP client consolidation | High | High | Create feature flags; migrate incrementally; comprehensive testing |
| Preview runtime consolidation affects existing features | Medium | Medium | Thorough E2E testing; gradual rollout with monitoring |
| Local storage consolidation loses data | Low | High | Implement migration path; backup existing data before migration |

---

## Next Actions

1. **Immediate**: Document the canonical HTTP client pattern and create migration guide
2. **Week 1**: Begin HTTP client consolidation with dashboard clients
3. **Week 2**: Consolidate preview runtime systems
4. **Week 3**: Consolidate local storage fallback patterns
5. **Week 4**: Consolidate builder model systems and verify no duplicates remain

---

## Historical Notes

This report was generated as part of TODO-053: "Validate no duplicate runtime systems remain - Check for duplicate API clients, preview runtimes, builder models, compiler/decompiler pipelines, local fallback paths, duplicate route models"

The consolidation work described above should be tracked as separate tasks in the project management system and prioritized according to the recommendations above.
