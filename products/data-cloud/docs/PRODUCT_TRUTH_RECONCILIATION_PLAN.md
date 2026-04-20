# Product Truth Reconciliation Plan

**Task:** DC-P1-1: Reconcile product truth across docs, route matrix, and UI boundaries
**Priority:** High
**Status:** Implementation Plan

## Current State

Multiple sources of truth exist:
- Documentation (README.md, DEVELOPER_MANUAL.md, USER_MANUAL.md, REST_API_DOCUMENTATION.md)
- Route matrix (OpenAPI spec in api/openapi.yaml)
- UI boundaries (navigation, pages, components)

Potential discrepancies may exist between these sources.

## Target State

Single canonical source of truth with:
- Documentation matching implementation
- Route matrix matching documentation
- UI boundaries matching available routes
- Clear indication of preview/experimental features

## Reconciliation Process

### Phase 1: Audit Documentation

**Files to Review:**
1. README.md - Product overview and capabilities
2. DEVELOPER_MANUAL.md - Developer guidance
3. USER_MANUAL.md - User workflows
4. REST_API_DOCUMENTATION.md - Human-readable API docs
5. OpenAPI spec (api/openapi.yaml) - Machine-readable API contract

**Audit Checklist:**
- [ ] Documented capabilities match implementation
- [ ] Documented routes exist in OpenAPI spec
- [ ] Documented features are actually functional
- [ ] Preview features are clearly marked
- [ ] Configuration variables are accurate
- [ ] Deployment modes are accurately described

### Phase 2: Audit Route Matrix

**OpenAPI Spec Review:**
- List all routes in OpenAPI spec
- Verify each route has corresponding implementation
- Verify route descriptions match implementation behavior
- Check for deprecated or removed routes

**Implementation Review:**
- List all implemented routes in launcher
- Compare against OpenAPI spec
- Identify undocumented routes
- Identify documented but unimplemented routes

### Phase 3: Audit UI Boundaries

**Navigation Review:**
- List all navigation items
- Verify each has corresponding route
- Check for orphaned navigation items
- Check for missing navigation for available routes

**Page Review:**
- List all pages
- Verify each page has backend API integration
- Identify boundary-only pages (using preview data)
- Identify pages without API backing

### Phase 4: Reconciliation Actions

**For Discrepancies Found:**

1. **Documentation ahead of implementation:**
   - Mark as "Coming Soon" or "Preview"
   - Add clear timeline if known
   - Update to match current reality

2. **Implementation ahead of documentation:**
   - Document the feature
   - Update route matrix
   - Update UI navigation

3. **Orphaned navigation/pages:**
   - Remove from navigation if no API
   - Mark as preview if coming soon
   - Hide from default navigation

4. **Undocumented features:**
   - Add documentation
   - Update OpenAPI spec
   - Update user manual

## Implementation Plan

### Step 1: Create Audit Matrix

Create a reconciliation spreadsheet/table:

| Feature | Documentation | OpenAPI | Implementation | UI | Status | Action |
|---------|--------------|---------|----------------|-----|--------|--------|
| Entity CRUD | ✓ | ✓ | ✓ | ✓ | Consistent | None |
| Data Fabric | Preview | ✗ | ✗ | ✓ | Inconsistent | Demote UI |
| ... | ... | ... | ... | ... | ... | ... |

### Step 2: Execute Audit

Run through each phase systematically:
1. Document all findings
2. Classify discrepancies
3. Prioritize fixes

### Step 3: Apply Fixes

Fix in order of priority:
1. High: User-facing discrepancies that cause confusion
2. Medium: Developer-facing discrepancies
3. Low: Internal documentation inconsistencies

### Step 4: Update Sources

Update all sources to match canonical truth:
- Update documentation
- Update OpenAPI spec
- Update UI navigation/labels
- Add preview badges where appropriate

## Success Criteria

- [ ] All documented features have corresponding implementation
- [ ] All implemented features are documented
- [ ] UI navigation matches available routes
- [ ] No boundary-only pages in primary navigation without clear labels
- [ ] OpenAPI spec matches implementation
- [ ] Preview features clearly marked across all sources
- [ ] Single source of truth established

## Timeline

- Phase 1 (Documentation audit): 1-2 days
- Phase 2 (Route matrix audit): 1 day
- Phase 3 (UI boundaries audit): 1-2 days
- Phase 4 (Reconciliation): 2-3 days

**Total:** 5-8 days

## Output Artifacts

- Reconciliation audit matrix
- Updated documentation files
- Updated OpenAPI spec
- Updated UI navigation configuration
- Summary report of changes made
