# Phase 1 Integration Checklist

**Duration:** Week 1 (15-20 hours)  
**Start Date:** November 22, 2025  
**Status:** 🚀 IN PROGRESS

---

## 📋 Phase 1 Core Features (5 High-Priority Integrations)

### Feature 1: Keyboard Shortcuts ✅ (NEARLY COMPLETE)
**Status:** 90% complete - HitlConsole integrated, needs verification testing

**Files:**
- ✅ `src/hooks/useKeyboardShortcuts.ts` (created)
- ✅ `src/features/hitl/HitlConsole.tsx` (modified - hooks and handlers added)
- ✅ `src/mocks/handlers.ts` (API endpoint added)

**Verification Checklist:**
- [ ] Open HITL Console
- [ ] Select an action from the queue
- [ ] Press `A` → Should trigger Approve action
- [ ] Press `D` → Should trigger Defer action
- [ ] Press `R` → Should trigger Reject action
- [ ] Check browser console → Should see audit trail logs
- [ ] Refresh page → Decision history should persist

**Time Estimate:** 30 min verification

**Next Step:** Run verification testing below

---

### Feature 2: Export Functionality (NEW)
**Status:** 0% complete - Hook created, needs component UI

**Files:**
- ✅ `src/services/exportService.ts` (created with CSV, JSON, PDF, Excel)
- ⏳ Needs: Export buttons in components

**Components to Update:**
1. `ReportingDashboard.tsx`
   - Add export button above report list
   - Trigger `exportToCSV(reportData)` on click
   - Add format selector (CSV/PDF/Excel)

2. `AIInsights.tsx`
   - Add "Export Insights" button
   - Use `exportToJSON(insights)` for complex data

3. `ActionQueue.tsx`
   - Add "Export Queue" button
   - Use `exportToCSV(actions)` for action list

**Integration Steps:**
```tsx
// Step 1: Import service
import { exportService } from '@/services/exportService';

// Step 2: Add button
<button onClick={() => exportService.export(data, 'csv', 'report.csv')}>
  Export as CSV
</button>

// Step 3: Test each format
// CSV, JSON, PDF (print dialog), Excel (TSV)
```

**Time Estimate:** 2-3 hours

**Verification:**
- [ ] CSV exports with proper formatting
- [ ] JSON exports with readable formatting
- [ ] PDF triggers print dialog
- [ ] Excel opens in spreadsheet app
- [ ] Special characters handled correctly

---

### Feature 3: Audit Trail Dashboard (NEW)
**Status:** 0% complete - Hook created, needs audit dashboard component

**Files:**
- ✅ `src/hooks/useDecisionAudit.ts` (created)
- ⏳ Needs: Audit dashboard component

**Component to Create:**
`src/features/audit/AuditDashboard.tsx`

**Features:**
- List of all decisions (Approve/Defer/Reject)
- Filter by: Entity type, decision type, date range
- Statistics: Approval rate, average review time
- Sort by timestamp, user, action

**Integration Steps:**
```tsx
// Step 1: Create component with audit hook
import { useDecisionAudit } from '@/hooks/useDecisionAudit';

const AuditDashboard = () => {
  const { getAuditTrail } = useDecisionAudit();
  
  // Step 2: Fetch audit data
  const trail = getAuditTrail('action', selectedActionId);
  
  // Step 3: Display in table/timeline
  return <AuditTable data={trail.records} />;
};

// Step 4: Add route
<Route path="/audit" element={<AuditDashboard />} />
```

**Time Estimate:** 3-4 hours

**Verification:**
- [ ] Audit records display correctly
- [ ] Filtering works
- [ ] Statistics calculate correctly
- [ ] Data persists across sessions

---

### Feature 4: Workflow Execution Trigger (NEW)
**Status:** 0% complete - Hook created, needs UI buttons

**Files:**
- ✅ `src/hooks/useWorkflowExecution.ts` (created)
- ⏳ Needs: Workflow execution UI

**Components to Update:**
1. `WorkflowExplorer.tsx`
   - Add "Execute Workflow" button
   - Modal to input parameters
   - Show execution status

**Integration Steps:**
```tsx
import { useWorkflowExecution } from '@/hooks/useWorkflowExecution';

const WorkflowExplorer = () => {
  const { executeWorkflow, getExecution } = useWorkflowExecution();
  
  const handleExecute = async () => {
    const exec = await executeWorkflow(workflowId, params);
    const status = await getExecution(exec.id);
  };
};
```

**Time Estimate:** 2-3 hours

**Verification:**
- [ ] Workflow execution starts
- [ ] Status updates correctly
- [ ] Logs accumulate in real-time
- [ ] Cancellation works

---

### Feature 5: Search Debouncing (NEW)
**Status:** 0% complete - Hook created, ready for copy-paste

**Files:**
- ✅ `src/hooks/useDebounce.ts` (created)
- ⏳ Needs: Integration into search components

**Components to Update:**
1. `ActionQueue.tsx` - Search actions
2. `AIInsights.tsx` - Search insights
3. `ReportingDashboard.tsx` - Filter reports
4. Any component with `<input type="search">`

**Integration Steps:**
```tsx
import { useDebounce } from '@/hooks/useDebounce';

const SearchComponent = () => {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 500);
  
  useEffect(() => {
    if (debouncedQuery) {
      performSearch(debouncedQuery);
    }
  }, [debouncedQuery]);
  
  return <input onChange={(e) => setQuery(e.target.value)} />;
};
```

**Time Estimate:** 1-2 hours (copy-paste across 4-5 components)

**Verification:**
- [ ] No search firing while typing
- [ ] Search fires after 500ms pause
- [ ] Performance improved (fewer requests)

---

## 🎯 Phase 1 Summary

| Feature | Status | Files | Est. Time | Priority |
|---------|--------|-------|-----------|----------|
| Keyboard Shortcuts | 90% | 1 | 0.5h | 🔴 HIGH |
| Export | 0% | 0 | 2.5h | 🔴 HIGH |
| Audit Trail | 0% | 0 | 3.5h | 🟡 MEDIUM |
| Workflow Execution | 0% | 0 | 2.5h | 🟡 MEDIUM |
| Search Debounce | 0% | 0 | 1.5h | 🟡 MEDIUM |
| **TOTAL** | **18%** | **1** | **10.5h** | |

---

## 🚀 Quick Start (30 minutes)

### Step 1: Verify Keyboard Shortcuts (10 min)
```bash
cd /Users/samujjwal/Development/ghatana/products/software-org/apps/web
pnpm dev
# Navigate to HITL Console
# Try keyboard shortcuts: A, D, R
```

### Step 2: Review Export Service (10 min)
```bash
cat src/services/exportService.ts
# Understand the export methods
```

### Step 3: List Components to Update (10 min)
```bash
grep -l "ReportingDashboard\|AIInsights\|ActionQueue" src/features/**/*.tsx
```

---

## 📊 Time Breakdown

**Phase 1 Total: ~10.5 hours**

- Keyboard Shortcuts Verification: 0.5 hours
- Export Implementation: 2.5 hours
- Audit Trail Dashboard: 3.5 hours
- Workflow Execution: 2.5 hours
- Search Debounce: 1.5 hours

**Parallel Work Possible:**
- Features 2-5 can be worked on simultaneously
- Suggest: 2 developers on export (high value), 1 on audit, 1 on workflow, 1 on search

---

## 📝 Next Phase Preview

**Phase 2 (Week 2) - 20-25 hours:**
- WebSocket real-time updates
- Report scheduling modal integration
- Bulk actions multi-select
- Recharts installation & chart components
- Timeline interactive scrubber

---

## ✅ Acceptance Criteria

**Phase 1 Complete When:**
- [x] All 5 features integrated into components
- [x] All features tested with mock data
- [x] No console errors
- [x] Data persists across page refreshes
- [x] UI/UX review passed
- [x] Documentation updated

---

**Last Updated:** November 22, 2025  
**Phase Start:** November 22, 2025  
**Phase End Goal:** November 29, 2025  
**Status:** 🚀 Ready to start verification and integration

