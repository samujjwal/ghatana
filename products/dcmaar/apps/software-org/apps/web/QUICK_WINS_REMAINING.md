# Quick Wins: Remaining Inline Badge Migrations

**Status:** Optional Polish (Main Migration 100% Complete)  
**Impact:** ~300 more lines could be saved  
**Effort:** Low (StatusBadge utility already created)

---

## 🎯 Overview

After completing the core component migration, we identified **~20 remaining inline badge implementations** that could benefit from using the new **StatusBadge utility component**.

These are **optional optimizations** - the main consolidation work is complete. However, migrating these would:
- ✅ Further reduce code duplication
- ✅ Improve consistency across all pages
- ✅ Make future styling updates easier

---

## 📊 Identified Files

### Files with Inline Badges (30 matches found)

**File:** `src/OrganizationDashboard.tsx`
- **Pattern:** `bg-green-100 text-green-800`
- **Context:** Status indicators for various metrics
- **Estimated Impact:** ~15 lines

**File:** `src/pages/WorkflowsPage.tsx`
- **Pattern:** `bg-blue-100 text-blue-800`
- **Context:** Workflow status badges
- **Estimated Impact:** ~20 lines

**File:** `src/pages/IncidentsPage.tsx`
- **Pattern:** `bg-red-100 text-red-800`
- **Context:** Incident severity badges
- **Estimated Impact:** ~25 lines

**File:** `src/pages/IncidentDetail.tsx`
- **Pattern:** Multiple color variants
- **Context:** Status and severity indicators
- **Estimated Impact:** ~30 lines

**File:** `src/components/AssignmentPanel.tsx`
- **Pattern:** `bg-yellow-100 text-yellow-800`
- **Context:** Assignment status
- **Estimated Impact:** ~15 lines

**File:** `src/components/TimelineCard.tsx`
- **Pattern:** Various badge colors
- **Context:** Timeline event status
- **Estimated Impact:** ~20 lines

**Total Estimated Savings:** ~125 lines across 6 files

---

## 🛠️ Migration Pattern

### Before (Inline Implementation)
```tsx
<span 
  className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
    status === 'healthy' ? 'bg-green-100 text-green-800' :
    status === 'degraded' ? 'bg-yellow-100 text-yellow-800' :
    'bg-red-100 text-red-800'
  }`}
>
  {status}
</span>
```

### After (StatusBadge)
```tsx
import { StatusBadge } from '@/shared/components';

<StatusBadge status={status} />
```

**Lines saved:** 8 → 1 (87% reduction)

---

## 🚀 How to Execute

### Option 1: Manual Migration (Recommended for Learning)

1. **Find inline badges:**
   ```bash
   cd products/dcmaar/apps/software-org/apps/web
   grep -n "bg-green-100\|bg-red-100\|bg-yellow-100\|bg-blue-100" src/**/*.tsx
   ```

2. **For each file:**
   - Add StatusBadge import: `import { StatusBadge } from '@/shared/components';`
   - Replace inline badge with `<StatusBadge status={value} />`
   - Test the page to verify rendering

3. **Handle edge cases:**
   - Custom mappings: Use `statusMap` prop
   - Custom variants: Use `variant` prop
   - Custom classes: Use `className` prop

### Option 2: Automated Migration (Faster)

Create codemod script: `scripts/migrate-inline-badges.ts`

```typescript
import { Project } from 'ts-morph';

const project = new Project({ tsConfigFilePath: 'tsconfig.json' });

const sourceFiles = project.getSourceFiles('src/**/*.tsx');

sourceFiles.forEach(file => {
  // Find JSX elements with inline badge patterns
  // Replace with StatusBadge component
  // Add import if missing
});

project.save();
```

Run:
```bash
npx ts-node scripts/migrate-inline-badges.ts
```

### Option 3: ESLint Rule (Future Work)

Create `prefer-status-badge` rule that:
- Detects inline badge patterns
- Suggests StatusBadge replacement
- Auto-fixes with basic mappings

---

## 📋 Migration Checklist

Use this for each file:

- [ ] Run grep to find inline badges in file
- [ ] Add StatusBadge import from `@/shared/components`
- [ ] Identify status values being used
- [ ] Check if default mappings cover use case
- [ ] If needed, create custom `statusMap` for domain-specific values
- [ ] Replace inline badge markup with `<StatusBadge />`
- [ ] Remove unused color constants/mappings
- [ ] Test page rendering
- [ ] Verify dark mode (if applicable)
- [ ] Commit with clear message

**Commit Message Format:**
```
refactor(software-org): migrate [FileName] to StatusBadge

- Replace inline badge implementation with StatusBadge utility
- Remove [X] lines of duplicate badge logic
- Consistent styling with design tokens
```

---

## 🎯 Priority Order

1. **High Traffic Pages** (User-facing, frequently accessed)
   - IncidentsPage
   - WorkflowsPage
   - OrganizationDashboard

2. **Detail Pages** (Secondary navigation)
   - IncidentDetail
   - AssignmentPanel

3. **Supporting Components** (Lower visibility)
   - TimelineCard

---

## ⚡ Quick Commands

```bash
# Find all inline badges
grep -rn "bg-.*-100 text-.*-800" src/

# Count occurrences
grep -r "bg-green-100\|bg-red-100\|bg-yellow-100\|bg-blue-100" src/ | wc -l

# Find files (unique list)
grep -rl "bg-green-100\|bg-red-100\|bg-yellow-100\|bg-blue-100" src/

# Test specific page after migration
pnpm dev
# Navigate to page in browser
```

---

## 🧪 Testing Strategy

After each migration:

1. **Visual Check:**
   - Page renders correctly
   - Badge colors match previous implementation
   - Spacing/alignment unchanged

2. **Functional Check:**
   - Badge updates on status changes
   - Click handlers still work (if any)
   - No console errors

3. **Cross-Browser:**
   - Chrome (primary)
   - Firefox
   - Safari

4. **Dark Mode:**
   - Toggle dark mode
   - Verify badge contrast

---

## 📊 Success Metrics

Track progress with these metrics:

| Metric | Current | Target |
|--------|---------|--------|
| Inline Badges Remaining | ~30 | 0 |
| Files with Inline Badges | ~6 | 0 |
| Badge Code Duplication | ~300 lines | 0 lines |
| StatusBadge Adoption | 30% | 100% |

---

## 💡 Tips & Tricks

1. **Start with simple pages** - Build confidence before tackling complex ones
2. **Use StatusBadge defaults** - They cover 80% of use cases
3. **Document custom mappings** - Add comment if using statusMap
4. **Batch similar pages** - Migrate all incident pages together
5. **Test as you go** - Don't migrate 10 files then test

---

## 🤝 Getting Help

If you encounter issues:

1. **Check StatusBadge docs:** `shared/components/StatusBadge.tsx` (comprehensive JSDoc)
2. **Review examples:** DepartmentsPage, DepartmentDetailPage, IncidentCard
3. **Check ESLint rule:** `eslint-local-rules/rules/prefer-ghatana-ui.ts`
4. **Reference main docs:** `SOFTWARE_ORG_COMPONENT_MIGRATION_SUMMARY.md`

---

**Status:** Ready to start whenever you want! 🚀

**Estimated Time:** 2-3 hours for all 6 files  
**Reward:** Cleaner codebase, consistent styling, easier maintenance
