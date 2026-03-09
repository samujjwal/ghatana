# Budget Planning Manual QA Testing Guide

**Feature**: Budget Planning UI (Owner Journey 2.4)  
**Last Updated**: 2025-01-XX  
**Status**: Ready for Testing

---

## 1. Test Environment Setup

### Prerequisites
- [ ] Backend server running (`pnpm dev` or `pnpm start`)
- [ ] Database seeded with Budget data (run `pnpm db:seed`)
- [ ] Browser with dev tools open
- [ ] Test user with Owner or Manager role

### Test Data Verification
Run in Prisma Studio or DB client:
```sql
SELECT * FROM "Budget" WHERE year = 2025 AND quarter = 'Q1';
SELECT * FROM "Department" LIMIT 5;
```

Expected:
- 2+ Budget records (Engineering, Operations)
- 3+ Departments available

---

## 2. Page Load & Initial State

### Test 2.1: Page Navigation
- [ ] Navigate to `/budget/planning`
- [ ] Page loads without errors
- [ ] Header displays "💰 Budget Planning"
- [ ] Description visible: "Plan and allocate budgets across departments..."

### Test 2.2: Period Selector
- [ ] Year dropdown shows 2024, 2025, 2026
- [ ] Quarter dropdown shows Annual, Q1, Q2, Q3, Q4
- [ ] Default: 2025 Q1
- [ ] Changing year triggers data reload
- [ ] Changing quarter triggers data reload

### Test 2.3: Budget Limit Input
- [ ] Total Budget Limit input is visible
- [ ] Default value: $5,000,000
- [ ] Can edit value (e.g., change to $6,000,000)
- [ ] Value persists during session

---

## 3. Budget Summary Cards

### Test 3.1: Card Rendering
- [ ] 4 cards displayed in grid
- [ ] Card 1: Total Allocated
- [ ] Card 2: Total Spent
- [ ] Card 3: Remaining
- [ ] Card 4: Variance

### Test 3.2: Metrics Display
- [ ] Total Allocated shows correct sum (e.g., $4,100,000)
- [ ] Department count correct (e.g., "2 departments • 2025 Q1")
- [ ] Total Spent shows sum with percentage (e.g., "65.9%")
- [ ] Progress bar reflects spend percentage
- [ ] Remaining shows difference (allocated - spent)
- [ ] Variance shows forecast vs actual with +/- sign

### Test 3.3: Color Coding
- [ ] Progress bar green when <75% spent
- [ ] Progress bar yellow when 75-90% spent
- [ ] Progress bar red when >90% spent
- [ ] Positive variance = green text
- [ ] Negative variance = red text

### Test 3.4: Hover Effects
- [ ] Cards scale up on hover (scale-105)
- [ ] Smooth transition
- [ ] No layout shift

---

## 4. Allocation Table

### Test 4.1: Table Structure
- [ ] Table displays 7 columns:
  - Department
  - Current
  - Spent
  - Proposed
  - Change %
  - Justification
  - Details
- [ ] All departments from budget data visible
- [ ] Table is responsive (scrolls horizontally on mobile)

### Test 4.2: Current & Spent Columns
- [ ] Current allocation displays correctly (e.g., $2,000,000)
- [ ] Spent amount displays correctly (e.g., $1,500,000)
- [ ] Spent percentage calculated correctly (e.g., "75.0%")
- [ ] Percentage color coding:
  - Green: <75%
  - Yellow: 75-90%
  - Red: >90%

### Test 4.3: Proposed Allocation Editing
- [ ] Proposed input is editable
- [ ] Can clear and type new value
- [ ] Value updates in state
- [ ] Change % recalculates immediately
- [ ] Totals in footer update immediately

### Test 4.4: Change % Calculation
- [ ] Change % = (proposed - current) / current * 100
- [ ] Green for positive changes
- [ ] Red for negative changes
- [ ] Gray for zero change

### Test 4.5: Justification Field
- [ ] Textarea is editable
- [ ] Can type multi-line text
- [ ] Value persists during session
- [ ] Placeholder text visible when empty

### Test 4.6: Category Breakdown (Expandable Rows)
- [ ] "▶ Show" button visible for each department
- [ ] Clicking "Show" expands row
- [ ] Button changes to "▼ Hide"
- [ ] Category breakdown table displays:
  - Headcount
  - Infrastructure
  - Tools
  - Training
  - Other
- [ ] Each category has editable input
- [ ] Category Total displays sum
- [ ] Category Total matches Proposed Allocated (or shows warning)

### Test 4.7: Category Mismatch Warning
- [ ] Edit proposed allocation to $3,000,000
- [ ] Keep categories unchanged (sum = $2,500,000)
- [ ] Yellow warning appears: "⚠️ Categories: $2,500,000"
- [ ] Fix category totals to match → warning disappears

### Test 4.8: Table Footer
- [ ] "Total (X departments)" displays department count
- [ ] Current Total = sum of all current allocations
- [ ] Spent Total = sum of all spent amounts
- [ ] Proposed Total = sum of all proposed allocations
- [ ] Change % = (proposedTotal - currentTotal) / currentTotal * 100
- [ ] Budget Limit displays set limit

### Test 4.9: Over Budget Warning
- [ ] Set Total Budget Limit to $3,000,000
- [ ] Ensure Proposed Total > $3,000,000
- [ ] Red "Over Budget" warning appears
- [ ] Shows amount over budget (e.g., "Over Budget: $1,100,000")

---

## 5. Data Loading

### Test 5.1: Existing Budget Plan
- [ ] Navigate to `/budget/planning?year=2025&quarter=Q1`
- [ ] If plan exists in DB, loads correctly
- [ ] Departments populate from API response
- [ ] Loading spinner shows briefly
- [ ] No errors in console

### Test 5.2: No Existing Plan (Mock Data)
- [ ] Navigate to `/budget/planning?year=2026&quarter=Q1` (future period)
- [ ] API returns 404
- [ ] Mock data initializes (3 departments)
- [ ] Engineering: $2.5M proposed
- [ ] Operations: $1.6M proposed
- [ ] Sales: $1.2M proposed

### Test 5.3: Error Handling
- [ ] Stop backend server
- [ ] Refresh page
- [ ] Error message displays (or loading state persists)
- [ ] Restart server
- [ ] Retry query (or refresh) → data loads

---

## 6. Save Draft Functionality

### Test 6.1: Save New Draft
- [ ] Load page with no existing plan (e.g., 2026 Q2)
- [ ] Edit proposed allocations
- [ ] Click "Save Draft"
- [ ] Loading state shows on button
- [ ] Success alert appears
- [ ] Query invalidates and refetches
- [ ] Status badge changes to "draft"

### Test 6.2: Update Existing Draft
- [ ] Load page with existing draft
- [ ] Change proposed allocation
- [ ] Click "Save Draft"
- [ ] API called with PUT method
- [ ] Success alert appears
- [ ] Changes persist

### Test 6.3: Network Inspection (Save)
- [ ] Open Network tab
- [ ] Click "Save Draft"
- [ ] Request to `/api/v1/budgets`
- [ ] Method: POST (new) or PUT (update)
- [ ] Body contains:
  - year
  - quarter
  - budgets array
  - status: "draft"
- [ ] Response 200/201
- [ ] Response JSON has `id` and `message`

---

## 7. Submit for Approval Functionality

### Test 7.1: Validation - Over Budget
- [ ] Set Proposed Total > Budget Limit
- [ ] Click "Submit for Approval"
- [ ] Alert appears: "Proposed budget ($X) exceeds budget limit ($Y)"
- [ ] Submission does not proceed

### Test 7.2: Validation - Missing Justifications
- [ ] Clear justification field for one department
- [ ] Click "Submit for Approval"
- [ ] Alert appears: "Please provide justifications for all budget changes"
- [ ] Submission does not proceed

### Test 7.3: Successful Submission
- [ ] Ensure all validations pass:
  - Proposed ≤ Budget Limit
  - All justifications provided
- [ ] Click "Submit for Approval"
- [ ] Confirm dialog appears
- [ ] Click "OK"
- [ ] Loading state shows
- [ ] Two API calls:
  1. Save draft (if needed)
  2. Create approval
- [ ] Success alert: "Budget plan submitted for approval"
- [ ] Navigate to `/approvals` (or approval detail page)

### Test 7.4: Network Inspection (Submit)
- [ ] Open Network tab
- [ ] Click "Submit for Approval"
- [ ] Request 1: `/api/v1/budgets` (if not saved)
- [ ] Request 2: `/api/v1/approvals`
  - Method: POST
  - Body contains:
    - type: "budget"
    - title: "Budget Plan 2025 Q1"
    - data: { year, quarter, budgets, totalBudgetLimit }
    - priority: "high"
    - dueDate: (2 weeks from now)
- [ ] Response 200/201

---

## 8. Readonly Mode (Approved Budget)

### Test 8.1: Load Approved Budget
- [ ] Manually set budget status to "approved" in DB:
  ```sql
  UPDATE "Budget" SET status = 'approved' WHERE year = 2025 AND quarter = 'Q1';
  ```
- [ ] Refresh page
- [ ] Info banner displays: "This budget plan is approved and cannot be edited"
- [ ] All inputs disabled
- [ ] "Save Draft" button hidden
- [ ] "Submit for Approval" button hidden

### Test 8.2: Status Badge
- [ ] Draft: gray badge
- [ ] Submitted: yellow badge
- [ ] Approved: green badge
- [ ] Active: blue badge

---

## 9. Responsive Design

### Test 9.1: Desktop (>1024px)
- [ ] Summary cards: 4 columns
- [ ] Allocation table: all columns visible
- [ ] No horizontal scroll (unless table content wide)
- [ ] Inputs and buttons properly sized

### Test 9.2: Tablet (768px - 1024px)
- [ ] Summary cards: 2 columns
- [ ] Allocation table: horizontal scroll enabled
- [ ] Page layout intact

### Test 9.3: Mobile (<768px)
- [ ] Summary cards: 1 column, stacked
- [ ] Allocation table: horizontal scroll
- [ ] Inputs touch-friendly (min height 44px)
- [ ] Buttons full-width or appropriately sized

---

## 10. Dark Mode

### Test 10.1: Toggle Dark Mode
- [ ] Enable dark mode (system preference or toggle)
- [ ] Page background: dark gray
- [ ] Cards: dark background with lighter borders
- [ ] Text: white/light gray
- [ ] Inputs: dark background, light text
- [ ] No readability issues

### Test 10.2: Color Contrast
- [ ] Green/yellow/red indicators visible
- [ ] Progress bar colors visible
- [ ] Hover effects visible
- [ ] All text meets WCAG AA standards

---

## 11. Integration with Approval Workflow

### Test 11.1: Approval Creation
- [ ] Submit budget plan
- [ ] Navigate to `/approvals` (or approval detail)
- [ ] New approval record exists
- [ ] Type: "budget"
- [ ] Title: "Budget Plan 2025 Q1"
- [ ] Data field contains budget plan JSON
- [ ] Status: "PENDING"

### Test 11.2: Approval Actions
- [ ] Approve budget in approval workflow
- [ ] Navigate back to `/budget/planning`
- [ ] Status changed to "approved"
- [ ] Readonly mode active

---

## 12. Performance & Usability

### Test 12.1: Load Time
- [ ] Page loads in <2 seconds
- [ ] No layout shift (CLS)
- [ ] Smooth animations

### Test 12.2: Real-time Calculations
- [ ] Edit proposed allocation
- [ ] Metrics update immediately (no lag)
- [ ] Footer totals update immediately
- [ ] Change % recalculates instantly

### Test 12.3: Keyboard Navigation
- [ ] Tab through inputs in logical order
- [ ] Can edit all fields via keyboard
- [ ] Can submit via Enter key (in inputs)
- [ ] Focus indicators visible

### Test 12.4: Accessibility
- [ ] All inputs have labels (aria-label or <label>)
- [ ] Buttons have accessible names
- [ ] Error messages announced by screen readers
- [ ] Color not sole means of conveying info

---

## 13. Edge Cases

### Test 13.1: Zero Values
- [ ] Set proposed allocation to 0
- [ ] Change % = -100%
- [ ] No division-by-zero errors

### Test 13.2: Very Large Numbers
- [ ] Set budget limit to $999,999,999
- [ ] Set proposed allocation to $100,000,000
- [ ] Numbers format correctly with commas
- [ ] No overflow errors

### Test 13.3: Negative Values
- [ ] Try entering negative number in proposed allocation
- [ ] Input validation prevents or handles gracefully

### Test 13.4: Non-numeric Input
- [ ] Type "abc" in proposed allocation
- [ ] Input validation prevents or shows error

### Test 13.5: Concurrent Edits
- [ ] Open page in two tabs
- [ ] Edit in Tab 1, save
- [ ] Edit in Tab 2, save
- [ ] Latest save wins (or conflict resolution)

---

## 14. Browser Compatibility

### Test 14.1: Chrome/Edge
- [ ] All features work
- [ ] No console errors
- [ ] UI renders correctly

### Test 14.2: Firefox
- [ ] All features work
- [ ] No console errors
- [ ] UI renders correctly

### Test 14.3: Safari
- [ ] All features work
- [ ] No console errors
- [ ] UI renders correctly
- [ ] Date pickers work (if any)

---

## 15. API Integration Tests

### Test 15.1: GET /api/v1/budgets
```bash
curl "http://localhost:3000/api/v1/budgets?year=2025&quarter=Q1"
```
- [ ] Returns budget plan JSON
- [ ] Contains budgets array
- [ ] Each budget has department info

### Test 15.2: POST /api/v1/budgets
```bash
curl -X POST http://localhost:3000/api/v1/budgets \
  -H "Content-Type: application/json" \
  -d '{
    "year": 2026,
    "quarter": "Q1",
    "budgets": [
      {
        "departmentId": "dept-eng",
        "allocated": 3000000,
        "forecasted": 3200000,
        "categories": {...},
        "notes": "Test"
      }
    ],
    "status": "draft"
  }'
```
- [ ] Returns 201 Created
- [ ] Returns budget plan ID

### Test 15.3: PUT /api/v1/budgets
```bash
curl -X PUT http://localhost:3000/api/v1/budgets \
  -H "Content-Type: application/json" \
  -d '{
    "id": "plan-id",
    "budgets": [...],
    "status": "draft"
  }'
```
- [ ] Returns 200 OK
- [ ] Updates budgets in DB

### Test 15.4: GET /api/v1/budgets/forecast
```bash
curl "http://localhost:3000/api/v1/budgets/forecast?departmentId=dept-eng&months=12"
```
- [ ] Returns forecast data
- [ ] Contains historical and forecast arrays
- [ ] Includes avgSpend and avgGrowth

---

## 16. Automated Test Coverage

### Test 16.1: Run Integration Tests
```bash
cd products/software-org/apps/web
pnpm test budget.integration.test.tsx
```
- [ ] All tests pass
- [ ] No console warnings
- [ ] Coverage >80%

### Test 16.2: Test Suites
- [ ] BudgetSummaryCards (5 tests)
- [ ] AllocationTable (10 tests)
- [ ] BudgetPlanningPage (10 tests)
- [ ] Workflow (5 tests)
- [ ] Total: 30+ tests

---

## 17. Documentation Verification

### Test 17.1: Code Documentation
- [ ] All components have JSDoc comments
- [ ] @doc.type, @doc.purpose, @doc.layer, @doc.pattern tags present
- [ ] Public functions documented

### Test 17.2: README / User Guide
- [ ] Feature described in product docs
- [ ] Screenshots or mockups available
- [ ] User workflow documented

---

## Summary Checklist

**Total Test Cases**: ~150

**Critical Paths**:
- [x] Page loads correctly
- [x] Allocation editing works
- [x] Category breakdown works
- [x] Save draft works
- [x] Submit for approval works
- [x] Validation works
- [x] Readonly mode works
- [x] Dark mode works
- [x] Responsive design works

**Sign-off**:
- QA Engineer: ________________
- Product Owner: ________________
- Date: ________________

---

## Known Issues / Future Enhancements

1. **Forecasting UI**: Currently only backend API; consider adding chart visualization
2. **Multi-year Planning**: Support planning multiple years at once
3. **Historical Comparison**: Side-by-side comparison with previous periods
4. **Export**: Export budget plan to Excel/CSV
5. **Notifications**: Email notifications when budget approval status changes

---

**End of Testing Guide**
