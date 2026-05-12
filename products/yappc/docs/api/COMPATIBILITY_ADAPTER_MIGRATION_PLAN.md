# Phase 12.1-2: Compatibility Adapter Migration Plan

**Status:** Pending Consumer Migration  
**Last Updated:** 2026-05-11

---

## Current State

The `client.ts` file at `frontend/web/src/lib/api/client.ts` remains a monolithic adapter layer that:
- Provides manual fetch helpers (get, post, patch, put, del)
- Maintains backward compatibility with existing code
- Delegates to OpenAPI-generated client services where available
- Handles authentication mode differences (cookie-session vs token-based)

According to Phase 1.2, this file should have been split into:
- `generatedClientAdapter.ts` - Direct delegation to generated client
- `legacyClientAdapter.ts` - Backward compatibility layer
- `scopeHeaders.ts` - Scope header management
- `errorMapper.ts` - Error mapping utilities
- `index.ts` - Public API exports

---

## Migration Requirements

### Before Compatibility Adapters Can Be Removed

1. **Consumer Code Migration**
   - All direct imports from `client.ts` must be updated to use generated client
   - All manual fetch helpers (get, post, patch, etc.) must be replaced with generated client calls
   - Custom error handling must be migrated to use generated client error types
   - Scope header management must be moved to generated client configuration

2. **Generated Client Coverage**
   - All endpoint groups must have generated client coverage:
     - Auth
     - Workspaces
     - Projects
     - Lifecycle
     - Phase packet
     - Dashboard actions
     - Generate/diff/review
     - Preview session
     - Artifact import/review
     - Audit/telemetry

3. **Breaking Change Coordination**
   - Coordinate with all consuming teams (web, mobile, extensions)
   - Provide migration guide and deprecation timeline
   - Monitor usage metrics to ensure migration completion

---

## Migration Steps

### Step 1: Audit Current Usage

```bash
# Find all imports of client.ts
grep -r "from '@/lib/api/client'" frontend/web/src/

# Find all manual fetch helper usage
grep -r "client\.\(get\|post\|patch\|put\|del\)" frontend/web/src/
```

### Step 2: Provide Migration Guide

Create `docs/api/CLIENT_MIGRATION_GUIDE.md` with:
- Mapping from old client methods to generated client methods
- Examples of common migration patterns
- Error handling migration guide
- Scope header configuration guide

### Step 3: Update Consumers

For each consumer:
1. Replace imports from `client.ts` with generated client imports
2. Replace manual fetch helpers with generated client methods
3. Update error handling to use generated error types
4. Configure scope headers in generated client

### Step 4: Deprecation Period

- Add deprecation warnings to `client.ts` methods
- Set deprecation timeline (e.g., 30 days)
- Monitor usage to ensure migration progress

### Step 5: Remove Compatibility Adapters

Once all consumers have migrated:
1. Remove manual fetch helpers (get, post, patch, put, del)
2. Remove legacy adapter layer
3. Remove scopeHeaders.ts (if still separate)
4. Remove errorMapper.ts (if still separate)
5. Keep only `generatedClientAdapter.ts` as the main client
6. Update all imports to use generated client directly

---

## Acceptance Criteria

- [ ] All consumers migrated to generated client
- [ ] Zero imports from legacy client.ts methods
- [ ] Zero usage of manual fetch helpers
- [ ] All endpoint groups have generated client coverage
- [ ] Migration guide published and communicated
- [ ] Deprecation period completed
- [ ] Compatibility adapters removed
- [ ] Tests updated to use generated client
- [ ] Documentation updated

---

## Rollback Plan

If issues arise after removal:
1. Restore compatibility adapters from git history
2. Revert consumer migrations
3. Investigate and fix root cause
4. Re-attempt migration after fixes

---

## Monitoring

Track the following metrics during migration:
- Usage count of legacy client.ts methods
- Usage count of generated client methods
- Error rates before/after migration
- Performance impact (latency, bundle size)

---

## Estimated Timeline

- **Audit**: 1 day
- **Migration Guide**: 2 days
- **Consumer Migration**: 5-10 days (depending on consumer count)
- **Deprecation Period**: 30 days
- **Removal**: 1 day

**Total Estimated Time:** 39-44 days

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Consumers unable to migrate in timeline | Extend deprecation period, provide additional support |
| Breaking changes in generated client | Version generated client, maintain compatibility layer |
| Performance regression | Benchmark before/after, optimize as needed |
| Increased bundle size | Code splitting, tree shaking verification |

---

## Next Steps

1. Complete audit of current usage
2. Create migration guide
3. Coordinate with consumer teams
4. Begin consumer migration
5. Monitor progress and extend timeline if needed
6. Remove compatibility adapters after migration complete
