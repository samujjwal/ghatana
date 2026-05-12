# Scope Transport Migration Plan

**Status:** In Progress  
**Phase:** 2.1-2  
**Last Updated:** 2026-05-11

---

## Current State

### Mixed Conventions Found

The codebase currently uses mixed conventions for scope transport:

1. **Header-based scope transport** (Current primary method):
   - `X-Workspace-ID` header used extensively in:
     - Generated API client (all services)
     - Backend controllers (GenerationApiController, RunApiController, PageArtifactController)
     - Frontend API routes (import-review, lifecycle-execution, lifecycle, registry-candidates, source-imports)
     - E2E tests
   - `X-Organization-ID` header used in some places

2. **Query parameter scope transport**:
   - `workspaceId` query parameter used in:
     - Frontend apps API routes (projects.ts multiple endpoints)
     - PhasePacketController.java
     - CapabilityController.java
     - Mock API server

3. **Scoped request helpers**:
   - `client.ts` has scoped GET/POST/PATCH/PUT helpers
   - `legacyClientAdapter.ts` has similar helpers
   - These helpers may not map to actual resource identity

### Canonical Scope Transport (Target State)

According to Phase 2.1 requirements:

1. **Path parameters**: When scope is part of the route
   - Example: `/api/v1/workspaces/{workspaceId}/projects/{projectId}`
   - Used for primary resource identity

2. **Query parameters**: For read routes where scope is optional or for filtering
   - Example: `/api/v1/projects?workspaceId={workspaceId}`
   - Used for optional scope or filtering

3. **Headers**: Only for cross-cutting scope
   - Example: `X-Tenant-ID` for tenant context across all requests
   - NOT for primary resource identity (workspaceId, projectId should be in path)

4. **Body**: Only for controller-level validation after authorization
   - Scope in request body is not extracted at auth filter level
   - Used for validation after authorization succeeds

---

## Migration Strategy

### Phase 1: OpenAPI Specification Updates

**Target:** Update `docs/api/openapi.yaml` to use path parameters instead of headers

**Affected Endpoints:**
- All endpoints currently using `X-Workspace-ID` header
- All endpoints using `workspaceId` query parameter when it should be a path parameter

**Changes Required:**
1. Remove `X-Workspace-ID` from header parameters
2. Add `workspaceId` as path parameter to routes that operate within a workspace
3. Add `projectId` as path parameter to routes that operate on a specific project
4. Keep headers only for cross-cutting scope (e.g., `X-Tenant-ID`, `X-Correlation-ID`)

**Example Migration:**

**Before:**
```yaml
/api/v1/generate:
  get:
    parameters:
      - name: X-Workspace-ID
        in: header
        required: true
        schema:
          type: string
```

**After:**
```yaml
/api/v1/workspaces/{workspaceId}/generate:
  get:
    parameters:
      - name: workspaceId
        in: path
        required: true
        schema:
          type: string
```

### Phase 2: Regenerate API Client

**Target:** Regenerate frontend API client from updated OpenAPI spec

**Actions:**
1. Run OpenAPI generator to update `frontend/web/src/clients/generated/api/`
2. Verify all generated services use path parameters
3. Update TypeScript types if needed

### Phase 3: Backend Controller Updates

**Target:** Update Java controllers to extract scope from path parameters

**Affected Controllers:**
- `GenerationApiController.java`
- `RunApiController.java`
- `PageArtifactController.java`
- `PhasePacketController.java`
- `CapabilityController.java`

**Changes Required:**
1. Update route path patterns to include path parameters
2. Remove header extraction logic for workspaceId/projectId
3. Extract scope from path parameters using ActiveJ routing
4. Update RequestScopeContext usage

**Example Migration:**

**Before:**
```java
String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-Id"));
```

**After:**
```java
String workspaceId = request.getPathParameter("workspaceId");
```

### Phase 4: Frontend Route Updates

**Target:** Update frontend API routes to use path parameters

**Affected Files:**
- `frontend/apps/api/src/routes/import-review.ts`
- `frontend/apps/api/src/routes/lifecycle-execution.ts`
- `frontend/apps/api/src/routes/lifecycle.ts`
- `frontend/apps/api/src/routes/registry-candidates.ts`
- `frontend/apps/api/src/routes/source-imports.ts`
- `frontend/apps/api/src/routes/projects.ts`

**Changes Required:**
1. Update route patterns to include path parameters
2. Remove header extraction logic
3. Extract scope from request params
4. Update middleware to use path parameters

### Phase 5: Frontend Component Updates

**Target:** Update React components to use path parameters

**Affected Files:**
- Components calling API with workspaceId/projectId
- Update to use path-based API calls
- Remove header-based scope passing

### Phase 6: Test Updates

**Target:** Update all tests to use new path-based scope transport

**Affected Tests:**
- Backend controller tests
- Frontend API route tests
- E2E tests
- Integration tests

---

## Acceptance Criteria

- [ ] OpenAPI spec uses path parameters for resource identity
- [ ] Headers used only for cross-cutting scope (tenant, correlation)
- [ ] Generated API client uses path parameters
- [ ] All backend controllers extract scope from path parameters
- [ ] All frontend API routes use path parameters
- [ ] All frontend components use path-based API calls
- [ ] All tests updated and passing
- [ ] No mixed conventions remain
- [ ] Scope query helpers removed or updated to map to actual resource identity

---

## Risks and Mitigations

### Risk 1: Breaking Existing Integrations
**Mitigation:** This is a breaking change. Coordinate with all consumers. Provide migration guide and deprecation period.

### Risk 2: Large Surface Area
**Mitigation:** Implement in phases with thorough testing at each phase. Use feature flags if needed for gradual rollout.

### Risk 3: Test Coverage
**Mitigation:** Ensure comprehensive test coverage before and after migration. Add contract tests to prevent regression.

---

## Dependencies

- Phase 2.1-1 (Canonical scope transport definition) - COMPLETED
- Phase 2.1-3 (RequestScopeContext DTO) - COMPLETED
- Phase 1.1 (OpenAPI parity validation) - COMPLETED

---

## Next Steps

1. **Immediate:** Document all endpoints that need migration
2. **Phase 1:** Update OpenAPI specification
3. **Phase 2:** Regenerate API client
4. **Phase 3:** Update backend controllers
5. **Phase 4:** Update frontend API routes
6. **Phase 5:** Update frontend components
7. **Phase 6:** Update tests
8. **Final:** Remove deprecated header-based scope transport

---

**Note:** This is a significant migration that affects the entire API surface. Proceed with caution and thorough testing at each phase.
