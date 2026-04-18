# Placeholder/Stub Implementation Inventory

**Created:** 2026-04-17  
**Purpose:** Track all placeholder/stub implementations requiring remediation

---

## Critical Stubs (Must Implement)

### 1. AnalyticsPage.tsx
**File:** `apps/tutorputor-web/src/pages/AnalyticsPage.tsx`  
**Issue:** Placeholder API calls returning empty data  
**Lines:** 42, 50, 58  
**Action:** Implement real API calls to analytics service  
**Priority:** P0  
**Status:** ✅ FIXED

```typescript
// Line 42-43 (BEFORE)
queryFn: async (): Promise<AnalyticsSummary> => {
    // Placeholder - getAnalyticsSummary to be implemented on apiClient
    return { totalEvents: 0, activeLearners: 0, eventsByType: {} };
}

// FIXED: Now uses real API client
const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ["analytics", "summary"],
    queryFn: async () => apiClient.getAnalyticsSummary(),
});
```

**Changes Made:**
- Added `getAnalyticsSummary()` to TutorPutorApiClient in tutorputorClient.ts
- Added `getUsageTrends()` to TutorPutorApiClient in tutorputorClient.ts
- Added `getAtRiskStudents()` to TutorPutorApiClient in tutorputorClient.ts
- Updated AnalyticsPage to use real API calls instead of placeholder empty data
- Maintained type safety with interface definitions

---

### 2. EvidenceAnalyticsPage.tsx
**File:** `apps/tutorputor-web/src/pages/content-studio/EvidenceAnalyticsPage.tsx`  
**Issue:** Using MOCK data instead of real API calls  
**Line:** 435  
**Action:** Replace MOCK with real API integration  
**Priority:** P0  
**Status:** ✅ NO ACTION NEEDED (Legitimate Use)

```typescript
// Line 430-435
const res = await fetch('/api/evidence/analytics');
if (!res.ok) throw new Error(`Failed to fetch analytics: ${res.status}`);
return res.json() as Promise<AnalyticsData>;
},
staleTime: 5 * 60 * 1000,
placeholderData: MOCK,  // This is legitimate React Query placeholderData usage
```

**Assessment:**
- Real API call is already implemented (line 430: `fetch('/api/evidence/analytics')`)
- MOCK is used as React Query's `placeholderData` feature, which is legitimate
- placeholderData shows initial data while real API call is loading
- This is NOT a stub implementation - it's proper use of React Query's placeholderData feature

---

### 3. useDownloadManager.tsx
**File:** `apps/tutorputor-web/src/hooks/useDownloadManager.tsx`  
**Issue:** Type stub using `any` type (violates copilot guidelines)  
**Lines:** 22-23  
**Action:** Implement proper Module type from contracts  
**Priority:** P0  
**Status:** ✅ FIXED

```typescript
// Line 22-23 (BEFORE)
// Type stubs
type Module = any;

// FIXED: Now uses proper types
type Module = ModuleDetail & {
  blocks?: Array<{ id: string; [key: string]: unknown }>;
  totalSizeBytes?: number;
  lessons?: Array<{ id: string; [key: string]: unknown }>;
  quizzes?: Array<{ id: string; questions: unknown[] }>;
};

type DownloadedModule = Module & {
  downloadedAt: string;
  downloadedSizeBytes?: number;
};
```

**Changes Made:**
- Imported ModuleDetail from `@tutorputor/contracts/v1/types`
- Created proper Module type matching ContentDownloadManager's internal type
- Created DownloadedModule type with downloadedAt metadata
- Updated all type references in storage adapter
- Added proper type conversions when loading modules from storage
- Fixed getStorageUsed to handle downloadedAt properly

---

## UI Placeholders (Legitimate - No Action Required)

The following are legitimate UI placeholder text for input fields - these do NOT need remediation:

- `MarketplacePage.tsx:161` - Placeholder thumbnail comment (UI placeholder)
- `CollaborationPage.tsx:177, 210, 217` - Input placeholder text
- `VrLabsPage.tsx:198, 206` - Input placeholder text
- `QualityPage.tsx:45, 47` - Input placeholder text
- `ModuleMetadataForm.tsx:45, 74` - Input placeholder text
- `AITutorPage.test.tsx` - Test placeholder text (legitimate)

---

## Test Stubs (Legitimate - No Action Required)

The following are legitimate test stubs for mocking network calls:

- `TemplatesAdminPage.test.tsx:145` - 404-style stub for tests
- `MarketplaceAdminPage.test.tsx:117` - 404-style stub for tests
- `AnalyticsPage.test.tsx:97` - 404-style stub for tests

---

## Summary

**Total Files Requiring Action:** 2  
**Total Files Fixed:** 2  
**Total Files Legitimate:** 10+  

**Completed Fixes:**
1. ✅ `useDownloadManager.tsx` - Replaced `any` type with proper types from contracts
2. ✅ `AnalyticsPage.tsx` - Implemented real API calls to analytics service

**No Action Required:**
1. ✅ `EvidenceAnalyticsPage.tsx` - MOCK data is legitimate React Query placeholderData usage

**Task 0.1 Status:** ✅ COMPLETED

---

**Last Updated:** 2026-04-17
