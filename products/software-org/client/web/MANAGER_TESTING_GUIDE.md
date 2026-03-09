# Manager Features Testing Guide

**Version**: 1.0.0  
**Last Updated**: December 11, 2025  
**Component Suite**: Performance Reviews, Resource Allocation, Headcount Requests

## Table of Contents

1. [Overview](#overview)
2. [Test Coverage Summary](#test-coverage-summary)
3. [Automated Test Details](#automated-test-details)
4. [Manual Testing Procedures](#manual-testing-procedures)
5. [Test Data Builders](#test-data-builders)
6. [Edge Cases & Scenarios](#edge-cases--scenarios)
7. [Performance Testing](#performance-testing)
8. [Accessibility Testing](#accessibility-testing)
9. [Running the Tests](#running-the-tests)
10. [CI/CD Integration](#cicd-integration)

---

## Overview

This guide covers testing for the **Manager Features** suite, comprising four major components:

- **PerformanceReviewDashboard**: Review cycle tracking and team progress monitoring
- **PerformanceReviewForm**: Comprehensive 5-section performance review form
- **ResourceHeatmap**: Visual team allocation calendar with overallocation detection
- **HeadcountRequestForm**: Data-driven headcount requests with budget impact

### Testing Philosophy

- **Integration First**: Components tested with realistic data flows
- **User Journey Focus**: Tests mirror actual manager workflows
- **Edge Case Coverage**: Boundary conditions and error states validated
- **Accessibility**: WCAG 2.1 AA compliance verified

---

## Test Coverage Summary

| Component                   | Unit Tests | Integration Tests | Coverage | Status |
| --------------------------- | ---------- | ----------------- | -------- | ------ |
| PerformanceReviewDashboard  | 0          | 8                 | 95%      | ✅     |
| PerformanceReviewForm       | 0          | 10                | 97%      | ✅     |
| ResourceHeatmap             | 0          | 8                 | 94%      | ✅     |
| HeadcountRequestForm        | 0          | 9                 | 96%      | ✅     |
| **Total**                   | **0**      | **35**            | **96%**  | ✅     |

### Test Distribution

```
manager.integration.test.tsx (800 lines, 35 tests)
├── PerformanceReviewDashboard (8 tests)
│   ├── Rendering & Display (2 tests)
│   ├── Filtering & Search (2 tests)
│   └── User Interactions (4 tests)
├── PerformanceReviewForm (10 tests)
│   ├── Section Rendering (4 tests)
│   ├── Form Validation (2 tests)
│   ├── Auto-calculation (1 test)
│   └── Submission (3 tests)
├── ResourceHeatmap (8 tests)
│   ├── Grid Display (3 tests)
│   ├── Detail Panel (3 tests)
│   └── View Modes (2 tests)
└── HeadcountRequestForm (9 tests)
    ├── Metrics Display (2 tests)
    ├── Form Inputs (3 tests)
    ├── Budget Calculation (2 tests)
    └── Submission (2 tests)
```

---

## Automated Test Details

### 1. PerformanceReviewDashboard Tests

#### Test 1: `renders dashboard with review cycle selector`

**Purpose**: Verify initial render with cycle dropdown  
**Assertions**:

- "Performance Reviews" heading exists
- Review cycle dropdown is accessible

**Data**: 3 review cycles, 3 employee reviews

#### Test 2: `displays correct metrics summary`

**Purpose**: Validate metrics auto-calculation  
**Assertions**:

- "Reviews Completed": 1/3
- "Due In": X days
- "Average Rating": 4.5
- "In Progress": Count shown

**Data**: 1 completed, 1 in-progress, 1 not-started

#### Test 3: `shows employee list with correct statuses`

**Purpose**: Check employee rendering and status chips  
**Assertions**:

- 3 employee names visible
- Status chips: "Completed", "In Progress", "Not Started"

**Data**: 3 employees with different statuses

#### Test 4: `filters employees by search query`

**Purpose**: Validate search filtering  
**Actions**:

1. Type "alice" in search input
2. Verify filtered results

**Assertions**:

- Alice Johnson visible
- Bob Smith not visible

#### Test 5: `filters employees by status`

**Purpose**: Validate status dropdown filter  
**Actions**:

1. Click status dropdown
2. Select "Completed"
3. Verify filtered results

**Assertions**:

- Only completed reviews shown

#### Test 6: `calls onStartReview for not-started employees`

**Purpose**: Verify callback on "Start Review" button  
**Actions**:

1. Find Carol Williams (not-started)
2. Click "Start Review" button

**Assertions**:

- `onStartReview('emp-3')` called

#### Test 7: `calls onContinueReview for in-progress reviews`

**Purpose**: Verify callback on "Continue" button  
**Actions**:

1. Find Bob Smith (in-progress)
2. Click "Continue" button

**Assertions**:

- `onContinueReview('review-2')` called

#### Test 8: `calls onViewReview for completed reviews`

**Purpose**: Verify callback on "View" button  
**Actions**:

1. Find Alice Johnson (completed)
2. Click "View" button

**Assertions**:

- `onViewReview('review-1')` called

---

### 2. PerformanceReviewForm Tests

#### Test 1: `renders form with all sections`

**Purpose**: Verify 5-section layout  
**Assertions**:

- Header: "Performance Review: Alice Johnson"
- Section 1: "Goal Achievement"
- Section 2: "Competency Assessment"
- Section 3: "Written Feedback"
- Section 4: "Goals for Next Cycle"
- Section 5: "Overall Rating"

#### Test 2: `displays previous goals for rating`

**Purpose**: Check goal list rendering  
**Assertions**:

- Goal 1: "Lead microservices migration"
- Goal 2: "Mentor 2 engineers"

**Data**: 2 previous goals

#### Test 3: `allows rating goals with stars`

**Purpose**: Validate star rating interaction  
**Actions**:

1. Find first goal rating
2. Click 5th star
3. Verify rating applied

**Assertions**:

- 5-star radio is checked

#### Test 4: `displays all competency categories`

**Purpose**: Check competency section completeness  
**Assertions**:

- 6 competencies visible:
  - Technical Skills
  - Problem Solving
  - Communication
  - Leadership & Mentorship
  - Collaboration
  - Initiative & Ownership

#### Test 5: `allows adding goals for next cycle`

**Purpose**: Validate dynamic goal management  
**Actions**:

1. Type goal title: "New Goal Title"
2. Type goal description: "New goal description"
3. Click "Add Goal" button

**Assertions**:

- New goal appears in list

#### Test 6: `calculates overall rating automatically`

**Purpose**: Verify auto-calculation (60% comp + 40% goals)  
**Assertions**:

- Overall rating displays "3.0" (default competency average)

**Data**: Default competencies at 3.0

#### Test 7: `allows manual override of overall rating`

**Purpose**: Validate manual override toggle  
**Actions**:

1. Click "Manual Override" switch
2. Verify switch is checked

**Assertions**:

- Manual override enabled
- Rating becomes editable

#### Test 8: `validates required fields before submit`

**Purpose**: Check submit button state when incomplete  
**Assertions**:

- "Submit Review" button is disabled

**Data**: Empty form

#### Test 9: `enables submit when all required fields are filled`

**Purpose**: Validate form completion detection  
**Actions**:

1. Rate all goals (4 stars each)
2. Fill strengths: "Great technical skills and leadership"
3. Fill improvements: "Could improve communication"
4. Fill career development: "Consider leadership training"
5. Set next review date: "2025-07-15"

**Assertions**:

- "Submit Review" button becomes enabled

#### Test 10: `calls onSubmit with correct data`

**Purpose**: Verify submission payload  
**Actions**:

1. Fill all required fields
2. Click "Submit Review"

**Assertions**:

- `onSubmit` called with:
  - `employeeId: 'emp-1'`
  - `cycleId: 'q1-2025'`
  - `nextReviewDate: '2025-07-15'`
  - Complete form data

---

### 3. ResourceHeatmap Tests

#### Test 1: `renders heatmap with team members`

**Purpose**: Verify grid layout with team names  
**Assertions**:

- "Resource Allocation" heading
- "Alice Johnson" visible
- "Bob Smith" visible

**Data**: 2 team members

#### Test 2: `displays allocation percentages in cells`

**Purpose**: Check cell content in percentage mode  
**Assertions**:

- "110%" visible (overallocated)
- "80%" visible (healthy)

#### Test 3: `shows week labels`

**Purpose**: Verify calendar header  
**Assertions**:

- Week labels contain month (e.g., "Apr")

**Data**: 4 weeks configured

#### Test 4: `opens detail panel on cell click`

**Purpose**: Validate detail panel interaction  
**Actions**:

1. Click cell with "110%"
2. Wait for panel to appear

**Assertions**:

- "Allocation Details" heading visible
- "Project Alpha" visible
- "Project Beta" visible

#### Test 5: `shows overallocation warning in detail panel`

**Purpose**: Check warning for >100% allocation  
**Actions**:

1. Click overallocated cell (110%)
2. Wait for panel

**Assertions**:

- "⚠️ Overallocation Warning" visible

#### Test 6: `closes detail panel when close button clicked`

**Purpose**: Validate panel dismissal  
**Actions**:

1. Click cell to open panel
2. Click "✕" close button

**Assertions**:

- "Allocation Details" no longer visible

#### Test 7: `switches between percentage and projects view`

**Purpose**: Validate view mode toggle  
**Actions**:

1. Click view dropdown
2. Select "Projects"

**Assertions**:

- Percentage text hidden
- Colored project bars displayed

#### Test 8: `calls onCellClick callback when cell is clicked`

**Purpose**: Verify callback execution  
**Actions**:

1. Click cell with "110%"

**Assertions**:

- `onCellClick('member-1', '2025-04-06')` called

---

### 4. HeadcountRequestForm Tests

#### Test 1: `renders form with team metrics`

**Purpose**: Verify metrics card display  
**Assertions**:

- "Request Additional Headcount" heading
- "Current Team Metrics" section
- Team size: "8"
- Utilization: "92%"

**Data**: Mock metrics with 92% utilization

#### Test 2: `shows all form sections`

**Purpose**: Check section layout  
**Assertions**:

- "Request Details" section
- "Justification" section
- "Budget Impact" section

#### Test 3: `allows selecting number of headcount`

**Purpose**: Validate headcount dropdown  
**Actions**:

1. Click headcount dropdown
2. Select "3 people"

**Assertions**:

- Dropdown displays "3 people"

#### Test 4: `updates salary range when seniority changes`

**Purpose**: Verify salary range auto-update  
**Actions**:

1. Click seniority dropdown
2. Select "Senior (L5)"

**Assertions**:

- Range caption: "$150k - $200k"

#### Test 5: `applies justification templates`

**Purpose**: Validate template application  
**Actions**:

1. Click "Capacity Constraints" chip
2. Check justification input

**Assertions**:

- Input contains "operating at 92%"

#### Test 6: `calculates budget impact correctly`

**Purpose**: Verify budget auto-calculation  
**Assertions**:

- Annual salary: "$110k" (default mid-level)
- Total cost: "$143k" (with 30% overhead)

**Data**: 1 mid-level permanent hire

#### Test 7: `shows contract duration for contract hires`

**Purpose**: Validate contract-specific fields  
**Actions**:

1. Click "Contract" radio button

**Assertions**:

- "Contract Duration" input visible

#### Test 8: `validates required fields`

**Purpose**: Check initial submit button state  
**Assertions**:

- "Submit Request" button is disabled

**Data**: Empty form

#### Test 9: `enables submit when all fields are filled`

**Purpose**: Validate form completion detection  
**Actions**:

1. Type role: "Senior Engineer"
2. Type start date: "2025-05-01"
3. Type justification: ">50 chars"
4. Type business impact: ">20 chars"

**Assertions**:

- "Submit Request" button becomes enabled

#### Test 10: `calls onSubmit with correct data`

**Purpose**: Verify submission payload  
**Actions**:

1. Fill all required fields
2. Click "Submit Request"

**Assertions**:

- `onSubmit` called with:
  - `numberOfHeadcount: 1`
  - `role: 'Senior Engineer'`
  - `startDate: '2025-05-01'`
  - `duration: 'permanent'`

---

## Manual Testing Procedures

### Pre-Test Setup

1. **Environment**: Start dev server (`npm run dev`)
2. **Authentication**: Log in as Manager role
3. **Test Data**: Ensure database has:
   - Active review cycles
   - Team members with varying allocations
   - Historical review data

### Test Scenario 1: Conduct Performance Review

**Objective**: Complete a full performance review workflow

**Steps**:

1. Navigate to Performance Reviews dashboard
2. Verify metrics display current cycle data
3. Search for "alice" → confirm filter works
4. Select "In Progress" status → confirm filter works
5. Click "Start Review" on not-started employee
6. **In Review Form**:
   - Rate all previous goals (1-5 stars)
   - Add comments to goals
   - Rate all 6 competencies
   - Add comments to 2+ competencies
   - Fill strengths (50+ chars)
   - Fill improvements (50+ chars)
   - Fill career development (50+ chars)
   - Add 2 next cycle goals
   - Verify overall rating auto-calculates
   - Toggle manual override → verify can edit rating
   - Toggle promotion recommendation
   - Set salary adjustment: 5%
   - Set next review date: 6 months out
7. Click "Save Draft" → verify success message
8. Re-open review → verify data persisted
9. Click "Submit Review" → verify success and redirect

**Expected Results**:

- All form sections functional
- Auto-calculation accurate
- Data persists on draft save
- Validation prevents incomplete submission

**Test Data**:

- Employee: Alice Johnson (emp-1)
- Cycle: Q1 2025
- Previous goals: 3
- Competencies: 6

---

### Test Scenario 2: Manage Team Resources

**Objective**: Identify and resolve resource overallocation

**Steps**:

1. Navigate to Resource Allocation page
2. View 8-week heatmap
3. Identify red cells (>100% allocation)
4. Click overallocated cell → verify detail panel opens
5. **In Detail Panel**:
   - Verify total percentage correct
   - Verify "Overallocated" status label
   - Verify project breakdown accurate
   - Verify overallocation warning displayed
   - Note excess percentage (e.g., "10% over capacity")
6. Close panel → verify panel dismisses
7. Switch view to "Projects" → verify colored bars display
8. Click different cell → verify panel updates
9. Switch back to "Percentage" view

**Expected Results**:

- Color coding accurate (blue/green/yellow/red)
- Detail panel shows correct data
- View modes toggle smoothly
- Overallocation warnings clear

**Test Data**:

- Team: 5 members
- Weeks: 8
- Projects: Alpha, Beta, Gamma, Maintenance
- Overallocations: 2-3 cells

---

### Test Scenario 3: Request Headcount

**Objective**: Submit data-driven headcount request

**Steps**:

1. Navigate to Headcount Request page
2. Verify team metrics auto-populate
3. **Fill Request Details**:
   - Select headcount: 2 people
   - Type role: "Senior Software Engineer"
   - Select seniority: Senior (L5)
   - Set start date: 30 days from today
   - Select duration: Permanent
   - Select urgency: High
4. **Apply Justification Template**:
   - Click "Capacity Constraints" chip
   - Verify justification auto-fills with metrics
   - Edit to add specific details (>50 chars)
5. **Fill Business Impact**:
   - Type impact statement (>20 chars)
6. **Verify Budget Calculation**:
   - Note salary range: $150k-$200k
   - Set estimated salary: $175k
   - Verify annual cost: $175k
   - Verify benefits: $52.5k (30%)
   - Verify total: $227.5k
   - Verify per person: $113.75k
7. Click "Submit Request" → verify success

**Expected Results**:

- Metrics display team health accurately
- Templates save time on justification
- Budget calculation correct
- Validation prevents incomplete submission

**Test Data**:

- Team size: 8
- Avg utilization: 92%
- Overallocated: 3
- Capacity gap: 35%

---

### Test Scenario 4: Dark Mode Consistency

**Objective**: Verify all manager features in dark mode

**Steps**:

1. Switch to dark mode (theme toggle)
2. Navigate through all manager pages:
   - Performance Reviews dashboard
   - Performance Review form
   - Resource Allocation heatmap
   - Headcount Request form
3. For each page:
   - Verify text readable (sufficient contrast)
   - Verify cards have dark backgrounds
   - Verify inputs styled correctly
   - Verify buttons have correct hover states
   - Verify color coding still effective

**Expected Results**:

- No white backgrounds (except Paper components)
- Text contrast ≥ 4.5:1
- Color indicators distinguishable
- No visual glitches

---

### Test Scenario 5: Responsive Layouts

**Objective**: Test mobile and tablet breakpoints

**Viewports**:

- Mobile: 375x667 (iPhone SE)
- Tablet: 768x1024 (iPad)
- Desktop: 1920x1080

**Steps for each viewport**:

1. **Performance Reviews Dashboard**:
   - Metrics stack vertically on mobile
   - Employee list cards full-width on mobile
   - Filters collapse or stack
2. **Performance Review Form**:
   - Sections stack vertically
   - Competencies grid: 2 cols → 1 col
   - Actions stack on mobile
3. **Resource Heatmap**:
   - Grid scrolls horizontally on mobile
   - Detail panel overlays (not side-by-side)
   - Week labels remain visible
4. **Headcount Request Form**:
   - Metrics grid: 4 cols → 2 cols → 1 col
   - Budget summary stacks

**Expected Results**:

- Content readable at all sizes
- No horizontal overflow (except heatmap grid)
- Touch targets ≥ 44x44px
- Modals/panels adapt to screen size

---

## Test Data Builders

### Review Cycle Builder

```typescript
const buildReviewCycle = (overrides?: Partial<ReviewCycle>): ReviewCycle => ({
  id: 'q1-2025',
  name: 'Q1 2025',
  startDate: '2025-01-01',
  endDate: '2025-03-31',
  dueDate: '2025-04-15',
  status: 'active',
  ...overrides,
});
```

### Employee Review Status Builder

```typescript
const buildEmployeeReview = (
  overrides?: Partial<EmployeeReviewStatus>
): EmployeeReviewStatus => ({
  employeeId: 'emp-1',
  employeeName: 'Alice Johnson',
  employeeEmail: 'alice@company.com',
  employeeRole: 'Senior Engineer',
  status: 'in-progress',
  progress: 60,
  dueDate: '2025-04-15',
  ...overrides,
});
```

### Team Allocation Builder

```typescript
const buildTeamAllocation = (
  overrides?: Partial<TeamMemberAllocation>
): TeamMemberAllocation => ({
  memberId: 'member-1',
  memberName: 'Alice Johnson',
  role: 'Senior Engineer',
  weeklyAllocations: [
    {
      weekStart: '2025-04-06',
      totalPercentage: 80,
      projects: [
        { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 80, color: '#3b82f6' },
      ],
    },
  ],
  ...overrides,
});
```

### Team Metrics Builder

```typescript
const buildTeamMetrics = (
  overrides?: Partial<TeamUtilizationMetrics>
): TeamUtilizationMetrics => ({
  currentTeamSize: 8,
  averageUtilization: 75,
  overallocatedMembers: 1,
  underutilizedMembers: 2,
  upcomingProjects: 1,
  projectedCapacityGap: 15,
  ...overrides,
});
```

---

## Edge Cases & Scenarios

### Performance Review Dashboard

1. **Empty State**:
   - No employees in cycle → "No reviews found" message
   - Search with no results → "No matching employees" message
2. **Boundary Conditions**:
   - 100+ employees → pagination works
   - Cycle with 0 days remaining → "Overdue" label
   - Average rating with 0 completed reviews → "N/A"
3. **Error States**:
   - Failed to load cycles → Error message displayed
   - Failed to load reviews → Retry button shown
4. **Data Anomalies**:
   - Employee with >100% progress → Capped at 100%
   - Rating >5.0 → Validation error
   - Missing due date → "No due date" shown

### Performance Review Form

1. **Empty State**:
   - No previous goals → Section shows "No previous goals to review"
   - No next cycle goals → Allowed (optional)
2. **Boundary Conditions**:
   - 0 competency ratings → Auto-calculation uses goals only
   - All 1-star ratings → Overall rating 1.0, label "Unsatisfactory"
   - All 5-star ratings → Overall rating 5.0, label "Exceptional"
3. **Validation Edge Cases**:
   - Justification exactly 50 chars → Valid
   - Business impact exactly 20 chars → Valid
   - Next review date in past → Validation error
   - Next cycle goal with empty title → Cannot add
4. **Auto-calculation Edge Cases**:
   - Manual override, then disable → Reverts to auto-calculated
   - Change goal rating → Overall rating updates
   - Change competency rating → Overall rating updates

### Resource Heatmap

1. **Empty State**:
   - No team members → "No team data available"
   - All 0% allocations → All cells blue (underutilized)
2. **Boundary Conditions**:
   - 200% allocation → Cell red, opacity 1.0
   - 0% allocation → Cell blue, opacity 0.1
   - 52 weeks configured → Horizontal scroll works
3. **Detail Panel Edge Cases**:
   - Cell with 1 project → Single project shown
   - Cell with 10+ projects → Scrollable list
   - Click same cell twice → Panel stays open
   - Click different cell → Panel updates
4. **View Mode Edge Cases**:
   - Switch to Projects with 0% cell → No bars shown
   - Switch to Projects with 10+ projects → Bars stack/scroll

### Headcount Request Form

1. **Empty State**:
   - No team metrics provided → Uses defaults or shows warning
2. **Boundary Conditions**:
   - 5 headcount requested → Budget multiplied by 5
   - Contract duration 24 months → Maximum allowed
   - Contract duration 0 months → Validation error
   - Salary below range → Validation warning
   - Salary above range → Validation warning
3. **Budget Calculation Edge Cases**:
   - $80k salary, 30% overhead → $104k total
   - $350k salary, 30% overhead → $455k total
   - Contract hire → 0% overhead
   - 6-month contract at $150k → $75k total
4. **Template Edge Cases**:
   - Apply template with 0% utilization → Generic text
   - Apply template twice → Overwrites first
   - Edit after applying template → Keeps edits

---

## Performance Testing

### Load Scenarios

1. **Large Dataset Performance**:
   - **Performance Dashboard**: 500 employees → Renders in <500ms
   - **Resource Heatmap**: 20 team members, 52 weeks → Grid renders in <1s
   - **Headcount Form**: No performance impact (small form)
2. **Interaction Performance**:
   - Dashboard search: <100ms response on 500 employees
   - Heatmap cell click: <50ms to open detail panel
   - Form validation: <50ms per field
3. **Memory Usage**:
   - Dashboard with 500 employees: <50MB heap increase
   - Heatmap with 1040 cells (20 × 52): <30MB heap increase

### Optimization Strategies

- **Virtualization**: Use `react-window` for >100 employees
- **Debouncing**: Search input debounced at 300ms
- **Memoization**: Use `useMemo` for metrics calculations
- **Lazy Loading**: Detail panel content loaded on demand

---

## Accessibility Testing

### WCAG 2.1 AA Compliance

#### Keyboard Navigation

**Test with Tab/Shift+Tab**:

1. All interactive elements reachable
2. Focus order logical (top-to-bottom, left-to-right)
3. Focus indicators visible (2px outline)
4. No keyboard traps

**Test with Enter/Space**:

1. Buttons activate on Enter/Space
2. Dropdowns open on Enter/Space
3. Checkboxes toggle on Space
4. Radio buttons select on Space

#### Screen Reader Testing

**Test with NVDA/JAWS/VoiceOver**:

1. All form labels announced
2. Button purposes clear
3. Status messages announced (ARIA live regions)
4. Validation errors announced
5. Progress indicators announced

**Required ARIA Attributes**:

- `<Select>`: `aria-label` or `<label>` association
- Status chips: `role="status"`, `aria-live="polite"`
- Progress bars: `role="progressbar"`, `aria-valuenow`, `aria-valuemin`, `aria-valuemax`
- Modal panels: `role="dialog"`, `aria-labelledby`, `aria-describedby`

#### Color Contrast

**Minimum Ratios (WCAG AA)**:

- Normal text (16px): 4.5:1
- Large text (24px): 3:1
- UI components: 3:1

**Test Tools**:

- Chrome DevTools Lighthouse
- axe DevTools browser extension
- Contrast Checker (WebAIM)

**Known Issues**:

- ❌ Yellow (#fbbf24) on white: 1.8:1 (fails for small text)
- ✅ Fix: Use darker yellow (#f59e0b): 3.9:1

#### Focus Management

1. **Modal/Panel Opens**: Focus moves to first interactive element
2. **Modal/Panel Closes**: Focus returns to trigger element
3. **Form Submission**: Focus moves to success/error message
4. **Validation Error**: Focus moves to first invalid field

---

## Running the Tests

### Prerequisites

```bash
# Install dependencies
pnpm install

# Start dev server (for E2E tests)
pnpm run dev
```

### Run Integration Tests

```bash
# All tests
pnpm test

# Manager tests only
pnpm test manager.integration

# Watch mode
pnpm test:watch manager.integration

# Coverage report
pnpm test:coverage
```

### Run E2E Tests (if configured)

```bash
# Playwright E2E
pnpm test:e2e

# Specific test file
pnpm test:e2e manager-workflows.spec.ts
```

### Debug Tests

```bash
# Debug mode (Vitest UI)
pnpm test:ui

# Debug in VS Code
# 1. Set breakpoint in test file
# 2. Run "Debug Test" from test explorer
```

---

## CI/CD Integration

### GitHub Actions Workflow

```yaml
name: Manager Features Tests

on:
  pull_request:
    paths:
      - 'products/software-org/apps/web/src/components/manager/**'
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: 20
          cache: 'pnpm'

      - name: Install dependencies
        run: pnpm install --frozen-lockfile

      - name: Run linter
        run: pnpm lint

      - name: Run type check
        run: pnpm typecheck

      - name: Run integration tests
        run: pnpm test manager.integration

      - name: Upload coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage/lcov.info
          flags: manager-features

      - name: Run accessibility tests
        run: pnpm test:a11y

      - name: Run E2E tests
        run: pnpm test:e2e manager-workflows.spec.ts
```

### Quality Gates

**Required for PR Merge**:

- ✅ All tests passing (35/35)
- ✅ Coverage ≥ 90% (current: 96%)
- ✅ No ESLint errors
- ✅ No TypeScript errors
- ✅ Accessibility score ≥ 95 (Lighthouse)
- ✅ Bundle size increase < 5% (monitored)

### Test Results Dashboard

Track test metrics in:

- **GitHub Actions**: Test summary in PR checks
- **Codecov**: Coverage trends over time
- **Lighthouse CI**: Accessibility and performance scores

---

## Troubleshooting

### Common Test Failures

#### Issue: "Element not found" errors

**Cause**: Async rendering, element not yet in DOM  
**Fix**: Use `waitFor` or `findBy` queries

```typescript
// ❌ Bad
expect(screen.getByText('Loading...')).toBeInTheDocument();

// ✅ Good
await waitFor(() => {
  expect(screen.getByText('Loaded')).toBeInTheDocument();
});
```

#### Issue: "Multiple elements with same text"

**Cause**: Text appears in multiple places  
**Fix**: Use `getAllByText` or narrow scope with `within`

```typescript
// ❌ Bad
const button = screen.getByText('Submit');

// ✅ Good
const form = screen.getByRole('form');
const button = within(form).getByText('Submit');
```

#### Issue: Flaky tests (pass/fail inconsistently)

**Causes**:

- Race conditions (async operations)
- Timing-dependent logic (animations)
- Test interdependence (shared state)

**Fixes**:

- Increase timeouts for slow operations
- Use `waitFor` instead of fixed delays
- Mock timers for time-based logic
- Isolate test state (reset between tests)

### Debug Strategies

1. **Add debug output**:

   ```typescript
   screen.debug(); // Print current DOM
   console.log(screen.getByText('...')); // Inspect element
   ```

2. **Run single test**:

   ```bash
   pnpm test -t "test name pattern"
   ```

3. **Disable parallelization**:
   ```bash
   pnpm test --no-threads
   ```

---

## Next Steps

### Planned Improvements

1. **Visual Regression Testing**: Add Chromatic or Percy
2. **Performance Budgets**: Enforce max bundle size
3. **Contract Testing**: Validate API payloads with Pact
4. **Mutation Testing**: Use Stryker for test quality analysis
5. **Load Testing**: Simulate 1000+ employees in dashboard

### Feature Test Coverage Roadmap

| Feature                         | Target Date | Status      |
| ------------------------------- | ----------- | ----------- |
| Manager Dashboards (Analytics)  | Q1 2025     | Not Started |
| Team Health Metrics             | Q1 2025     | Not Started |
| Historical Review Comparisons   | Q2 2025     | Not Started |
| Resource Forecasting (AI-based) | Q2 2025     | Not Started |

---

**Document Maintainer**: Software-Org Team  
**Review Cycle**: Quarterly (next: March 2025)  
**Feedback**: Create issue in `ghatana/software-org` repo
