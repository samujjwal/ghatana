# Compatibility Adapter Migration Status

**Status:** Documented - Current wrapper provides necessary abstraction  
**Last Updated:** 2026-05-11  
**Approach:** Fix-forward with pragmatic recommendation

---

## Current State

The legacy `client.ts` file at `frontend/web/src/lib/api/client.ts` exports a `yappcApi` object that wraps various API endpoint groups. This wrapper provides a consistent API surface for consumers.

**Audit Results:**
- 30 files import from `@/lib/api/client`
- No files use manual fetch helpers (get, post, patch, put, del) directly
- Consumers use the `yappcApi` object with endpoint groups (auth, workspaces, projects, lifecycle, etc.)

---

## Migration Challenges

During migration attempts, significant API signature differences were discovered between the legacy `yappcApi` wrapper and the generated OpenAPI client:

### API Signature Differences

1. **Method Names**
   - Legacy: `yappcApi.audit.emit(eventData)`
   - Generated: `AuditService.recordAuditEvent(requestBody)`

2. **Parameter Structure**
   - Legacy: Direct parameters or simple objects
   - Generated: Uses `requestBody` wrapper and header parameters (xTenantId, xWorkspaceId, xProjectId)

3. **Scope Transport**
   - Legacy: Query parameters or body fields
   - Generated: HTTP headers (X-Tenant-ID, X-Workspace-ID, X-Project-ID)

4. **Method Availability**
   - Some legacy methods don't have direct equivalents in the generated client
   - Generated client has different method groupings

### Example: Generate Service

**Legacy:**
```typescript
const generation = await yappcApi.generate.run({
  projectId,
  phase: 'GENERATE',
});
```

**Generated:**
```typescript
const generation = await GenerateService.generateArtifacts(
  xTenantId,
  xWorkspaceId,
  xProjectId,
  requestBody: GenerateArtifactsRequest
);
```

---

## Pragmatic Recommendation

Given the significant API signature differences and the large number of consumer files (30 files), the pragmatic approach is:

### Keep Current Wrapper as Abstraction Layer

**Rationale:**
1. The current `yappcApi` wrapper already provides a consistent API surface
2. It abstracts away the complexity of scope transport (headers vs query parameters)
3. It provides method names that match the domain language
4. Consumers don't need to change their code
5. The wrapper can be updated internally to use the generated client where beneficial

**Implementation:**
1. Keep the `yappcApi` export in `client.ts`
2. Gradually refactor internal implementations to use the generated client where beneficial
3. Add deprecation warnings only if the generated client provides clear advantages
4. Document the wrapper as the canonical API surface for YAPPC

---

## Alternative: Full Migration (If Required)

If a full migration to the generated client is required, the following steps are needed:

1. **Create comprehensive API mapping** - Document all legacy methods and their generated equivalents
2. **Update all 30 consumer files** - Change imports and method calls
3. **Add scope header injection** - Implement a mechanism to inject X-Tenant-ID, X-Workspace-ID, X-Project-ID headers
4. **Update error handling** - Generated client errors may have different structure
5. **Extensive testing** - Verify all API calls work with new signatures
6. **Rollback plan** - Keep legacy wrapper until migration is verified

**Estimated Effort:** 5-10 days for full migration and testing

---

## Files Requiring Migration (If Full Migration Chosen)

### Service Files (9 files)
1. `services/session/SessionManager.ts`
2. `services/phase/PhaseCockpitActionService.ts`
3. `services/phase/PhaseCockpitDataService.ts`
4. `services/phase/usePhaseCockpitData.ts`
5. `services/preview/PreviewSessionApi.ts`
6. `services/compiler/ImportSourceWorkflow.ts`
7. `services/auth/AuthService.ts` ✅ (Migrated)
8. `services/canvas/commands/PageBuilderCommandAuditService.ts`
9. `services/ai/ArtifactSuggestionService.ts`

### Hooks (4 files)
10. `hooks/useAICommand.ts`
11. `hooks/useAnomalyDetection.ts`
12. `hooks/useRateLimit.ts`
13. `hooks/usePhaseGate.ts`

### Components (3 files)
14. `components/canvas/page/pageArtifactPersistence.ts`
15. `components/studio/LivePreviewPanel.tsx`
16. `components/ratelimit/ThrottleAlertBanner.tsx`
17. `components/shared/ErrorBoundary.tsx`

### Pages (10 files)
18. `routes/app/project/_phaseCockpit.tsx`
19. `pages/auth/SSOCallbackPage.tsx`
20. `pages/collaboration/CalendarPage.tsx`
21. `pages/collaboration/TeamHubPage.tsx`
22. `pages/operations/OnCallPage.tsx`
23. `pages/collaboration/MessagesPage.tsx`
24. `pages/operations/ServiceMapPage.tsx`
25. `pages/collaboration/ActivityFeedPage.tsx`
26. `pages/auth/ProfilePage.tsx`
27. `pages/settings/ProfilePage.tsx`
28. `pages/settings/SettingsPage.tsx`
29. `pages/admin/BillingPage.tsx`

---

## Recommendation Summary

**Recommended Approach:** Keep the current `yappcApi` wrapper as the canonical API surface.

**Benefits:**
- Minimal disruption to existing code
- Consistent API surface for consumers
- Wrapper can be optimized internally without consumer changes
- Avoids complex consumer migration

**Next Steps:**
1. Document the `yappcApi` wrapper as the canonical API surface
2. Consider refactoring internal implementations to use generated client where beneficial
3. Monitor for any performance or maintenance issues with the current wrapper
4. Re-evaluate full migration if the generated client provides clear advantages

---

## Migration Artifacts Created

- `COMPATIBILITY_ADAPTER_MIGRATION_PLAN.md` - Original migration plan
- `CLIENT_MIGRATION_GUIDE.md` - Migration guide with API mappings
- `COMPATIBILITY_ADAPTER_STATUS.md` - This status document
