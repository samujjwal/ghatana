# Boundary-Only Pages Analysis

**Task:** DC-P1-2: Remove or demote boundary-only pages from product framing until APIs exist
**Priority:** High
**Status:** Analysis Complete

## Definition

Boundary-only pages are UI pages that exist without corresponding backend APIs, using mock/preview data or UnsupportedSurfaceBoundary wrappers. These pages should be removed from primary product framing or clearly marked as preview/coming soon.

## Analysis Results

### Identified Boundary-Only Pages

#### 1. DataFabricPage.tsx
- **Location:** `products/data-cloud/ui/src/pages/DataFabricPage.tsx`
- **Status:** Boundary-only (preview data)
- **Evidence:** 
  - Uses `PREVIEW_FABRIC_METRICS` hardcoded data (line 55-104)
  - Wrapped with `UnsupportedSurfaceBoundary` component (line 405-411)
  - No real backend API integration for fabric metrics
- **Recommendation:** Demote to preview status with clear "Coming Soon" labeling

#### 2. DataConnectorsPage.tsx
- **Location:** `products/data-cloud/ui/src/features/data-fabric/components/DataConnectorsPage.tsx`
- **Status:** Has backend API integration
- **Evidence:**
  - Uses `dataConnectorApi.getAll()` (line 52)
  - Uses `dataConnectorApi.delete()` (line 73)
  - Uses `dataConnectorApi.triggerSync()` (line 88)
  - Uses `dataConnectorApi.getSyncStatistics()` (line 91)
- **Recommendation:** Keep as-is (has real API backing)

#### 3. StorageProfilesPage.tsx
- **Location:** `products/data-cloud/ui/src/features/data-fabric/components/StorageProfilesPage.tsx`
- **Status:** Has backend API integration
- **Evidence:**
  - Uses `storageProfileApi.getAll()` (line 52)
  - Uses `storageProfileApi.delete()` (line 73)
  - Uses `storageProfileApi.setDefault()` (line 88)
- **Recommendation:** Keep as-is (has real API backing)

### Pages with Backend API Integration (Verified)

From grep results, these pages use `/api/v1` endpoints:
- WorkflowsPage.tsx
- EntityBrowserPage.tsx
- InsightsPage.tsx
- ExecutionMonitor.tsx (workflow component)

### Pages Requiring Further Investigation

Remaining pages from the 19 found:
- CollectionDataPage.tsx
- AgentPluginManagerPage.tsx
- AlertsPage.tsx
- ContextExplorerPage.tsx
- CreateCollectionPage.tsx
- EditCollectionPage.tsx
- EventExplorerPage.tsx
- MemoryPlaneViewerPage.tsx
- OperationsConsolePage.tsx
- PluginDetailsPage.tsx
- PluginsPage.tsx
- SettingsPage.tsx
- SqlWorkspacePage.tsx

## Action Plan

### Immediate Actions

1. **Demote DataFabricPage to preview status:**
   - Add prominent "Preview" or "Coming Soon" banner
   - Update navigation to show this page as experimental
   - Add clear messaging that this uses demo data
   - Consider hiding from default navigation until backend API exists

2. **Investigate remaining pages:**
   - Check each of the 12 unverified pages for API integration
   - Identify any other boundary-only pages
   - Document findings

3. **Update product documentation:**
   - Update README.md to reflect current UI capabilities
   - Clearly mark preview/boundary-only features
   - Update navigation documentation

### Implementation Steps

#### Step 1: Add Preview Banner to DataFabricPage
```typescript
// Add to DataFabricPage.tsx header
<div className="px-6 py-2 bg-amber-50 border-b border-amber-200 text-sm text-amber-800">
  <strong>Preview:</strong> This page uses demo data. Real fabric metrics API coming soon.
</div>
```

#### Step 2: Update Navigation
- Add `preview: true` flag to DataFabricPage route
- Show preview badge in navigation menu
- Consider moving to "Experimental" section in nav

#### Step 3: Document in README
Add to README.md UI Surface Truth table:
| Surface | Runtime truth | Notes |
| Data Fabric | Preview/demo only | Uses hardcoded metrics; backend API in development |

#### Step 4: Investigate Remaining Pages
For each of the 12 unverified pages:
1. Check for API client usage
2. Check for UnsupportedSurfaceBoundary wrappers
3. Check for hardcoded/mock data
4. Document findings

## Success Criteria

- [ ] DataFabricPage clearly marked as preview
- [ ] Navigation reflects preview status
- [ ] README updated with accurate product truth
- [ ] All pages classified as either API-backed or boundary-only
- [ ] No boundary-only pages in primary product framing without clear labels

## Timeline

- Step 1-2 (DataFabricPage demotion): 1 day
- Step 3 (Documentation): 0.5 day
- Step 4 (Remaining page investigation): 1-2 days

**Total:** 2-3 days
