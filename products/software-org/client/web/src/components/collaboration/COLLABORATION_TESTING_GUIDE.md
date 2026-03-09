# Collaboration Features Testing Guide

## Overview

This guide provides comprehensive testing coverage for all collaboration components:
- **ProjectWorkspace**: Collaborative project management with task boards
- **ApprovalWorkflow**: Multi-level approval request system
- **TeamCalendar**: Team calendar with meetings and events

**Total Test Coverage**: 43 integration tests across 3 components

---

## Test Execution

### Run All Collaboration Tests
```bash
npm test -- collaboration.integration.test.tsx
```

### Run Specific Component Tests
```bash
# ProjectWorkspace only
npm test -- collaboration.integration.test.tsx -t "ProjectWorkspace"

# ApprovalWorkflow only
npm test -- collaboration.integration.test.tsx -t "ApprovalWorkflow"

# TeamCalendar only
npm test -- collaboration.integration.test.tsx -t "TeamCalendar"
```

### Run with Coverage
```bash
npm test -- --coverage collaboration.integration.test.tsx
```

---

## ProjectWorkspace Component (16 Tests)

### Rendering Tests (5 tests)

#### ✅ Test 1: Render project header with status and priority
**Purpose**: Verify project metadata displays correctly
```typescript
it('should render project header with status and priority')
```
**What it tests**:
- Project title visible
- Status chip present (Active/On Hold/Completed)
- Priority chip present (Low/Medium/High)

**Manual verification**:
- [ ] Project title is prominent and readable
- [ ] Status chip uses correct color (green=Active, yellow=On Hold, gray=Completed)
- [ ] Priority chip uses correct color (green=Low, orange=Medium, red=High)

---

#### ✅ Test 2: Render project owner and team information
**Purpose**: Verify team metadata displays
```typescript
it('should render project owner and team information')
```
**What it tests**:
- Owner name visible
- Team member count shown

**Manual verification**:
- [ ] Owner avatar and name display correctly
- [ ] Team count matches actual members
- [ ] Hover shows full team member list

---

#### ✅ Test 3: Render project timeline and progress
**Purpose**: Verify timeline and progress tracking
```typescript
it('should render project timeline and progress')
```
**What it tests**:
- Start date visible
- End date visible
- Progress percentage shown

**Manual verification**:
- [ ] Dates format correctly (e.g., "Jan 1, 2024")
- [ ] Progress bar fills proportionally
- [ ] Progress percentage text matches bar fill

---

#### ✅ Test 4: Render all navigation tabs
**Purpose**: Verify tab navigation structure
```typescript
it('should render all navigation tabs')
```
**What it tests**:
- Board tab present
- List tab present
- Activity tab present
- Files tab present

**Manual verification**:
- [ ] All tabs are clickable
- [ ] Active tab is highlighted
- [ ] Tab order is logical (Board → List → Activity → Files)

---

#### ✅ Test 5: Render task counts in tabs
**Purpose**: Verify tab badges show correct counts
```typescript
it('should render task counts in tabs')
```
**What it tests**:
- Task count badges visible
- Count matches actual tasks

**Manual verification**:
- [ ] Badge numbers update when tasks change
- [ ] Badge styling is consistent
- [ ] Zero counts are handled appropriately

---

### Board View Tests (5 tests)

#### ✅ Test 6: Render all kanban columns
**Purpose**: Verify kanban board structure
```typescript
it('should render all kanban columns')
```
**What it tests**:
- To Do column exists
- In Progress column exists
- Review column exists
- Done column exists

**Manual verification**:
- [ ] Columns are evenly spaced
- [ ] Column titles are clear
- [ ] Columns maintain order on resize

---

#### ✅ Test 7: Display task cards with correct information
**Purpose**: Verify task card content
```typescript
it('should display task cards with correct information')
```
**What it tests**:
- Task titles visible
- Multiple tasks render correctly

**Manual verification**:
- [ ] Task cards are readable
- [ ] Long titles truncate appropriately
- [ ] Cards have consistent sizing

---

#### ✅ Test 8: Show priority chips on task cards
**Purpose**: Verify priority indicators
```typescript
it('should show priority chips on task cards')
```
**What it tests**:
- Priority chips render
- Multiple priorities supported

**Manual verification**:
- [ ] High priority is red
- [ ] Medium priority is orange
- [ ] Low priority is green
- [ ] Chip size is appropriate

---

#### ✅ Test 9: Display assignee avatars on task cards
**Purpose**: Verify assignee visualization
```typescript
it('should display assignee avatars on task cards')
```
**What it tests**:
- Avatars render on cards
- Multiple avatars supported

**Manual verification**:
- [ ] Avatar shows user photo or initials
- [ ] Hover shows full name
- [ ] Unassigned tasks show placeholder

---

#### ✅ Test 10: Show comment and attachment counts
**Purpose**: Verify metadata badges
```typescript
it('should show comment and attachment counts')
```
**What it tests**:
- Comment count visible
- Attachment count visible

**Manual verification**:
- [ ] Icons are recognizable (💬 for comments, 📎 for attachments)
- [ ] Zero counts are hidden or grayed out
- [ ] Counts update in real-time

---

### Task Creation Tests (3 tests)

#### ✅ Test 11: Open create task dialog
**Purpose**: Verify task creation UX
```typescript
it('should open create task dialog when clicking "New Task" button')
```
**What it tests**:
- New Task button exists
- Dialog opens on click
- Dialog has correct title

**Manual verification**:
- [ ] Button is prominent and accessible
- [ ] Dialog animates smoothly
- [ ] Dialog can be closed with Esc key

---

#### ✅ Test 12: Form fields in create dialog
**Purpose**: Verify form completeness
```typescript
it('should have all required form fields in create dialog')
```
**What it tests**:
- Title field exists
- Description field exists
- Status field exists
- Priority field exists

**Manual verification**:
- [ ] Field labels are clear
- [ ] Required fields are marked with *
- [ ] Placeholder text is helpful

---

#### ✅ Test 13: Call onCreateTask callback
**Purpose**: Verify task creation integration
```typescript
it('should call onCreateTask callback when submitting form')
```
**What it tests**:
- Callback fires on submit
- Correct data passed to callback

**Manual verification**:
- [ ] Form validates before submit
- [ ] Success message appears
- [ ] Dialog closes after creation
- [ ] New task appears in correct column

---

### Task Details Tests (3 tests)

#### ✅ Test 14: Open task detail dialog
**Purpose**: Verify task detail UX
```typescript
it('should open task detail dialog when clicking a task card')
```
**What it tests**:
- Task cards are clickable
- Dialog opens with task data

**Manual verification**:
- [ ] Click anywhere on card opens dialog
- [ ] Dialog shows correct task
- [ ] Dialog is responsive

---

#### ✅ Test 15: Display task description
**Purpose**: Verify task content display
```typescript
it('should display task description in detail dialog')
```
**What it tests**:
- Full description visible
- Formatting preserved

**Manual verification**:
- [ ] Long descriptions scroll
- [ ] Markdown renders if supported
- [ ] Links are clickable

---

#### ✅ Test 16: Allow adding comments
**Purpose**: Verify comment functionality
```typescript
it('should allow adding comments to tasks')
```
**What it tests**:
- Comment input exists
- onAddComment callback fires

**Manual verification**:
- [ ] Comment textarea expands
- [ ] Submit button enables when text entered
- [ ] New comment appears immediately
- [ ] User avatar shows in comment

---

## ApprovalWorkflow Component (14 tests)

### Rendering Tests (4 tests)

#### ✅ Test 17: Render page header and title
**Purpose**: Verify page structure
```typescript
it('should render page header and title')
```
**What it tests**:
- Page title visible
- Header structure correct

**Manual verification**:
- [ ] Title is prominent (h4 size)
- [ ] Subtitle explains purpose
- [ ] Header spacing is appropriate

---

#### ✅ Test 18: Render all status tabs
**Purpose**: Verify tab navigation
```typescript
it('should render all status tabs')
```
**What it tests**:
- Pending tab exists
- Approved tab exists
- Rejected tab exists
- My Requests tab exists

**Manual verification**:
- [ ] Tab order is logical
- [ ] Active tab is highlighted
- [ ] Tabs are keyboard navigable

---

#### ✅ Test 19: Display request counts in tabs
**Purpose**: Verify tab badges
```typescript
it('should display request counts in tabs')
```
**What it tests**:
- Count badges visible
- Counts match filtered requests

**Manual verification**:
- [ ] Badge colors match status (warning=pending, success=approved, error=rejected)
- [ ] Counts update on status changes
- [ ] Zero counts handled gracefully

---

#### ✅ Test 20: Render new request button
**Purpose**: Verify request creation entry point
```typescript
it('should render new request button')
```
**What it tests**:
- Button exists
- Button is accessible

**Manual verification**:
- [ ] Button is prominent (primary color)
- [ ] Button position is consistent
- [ ] Button is always visible

---

### Request List Tests (7 tests)

#### ✅ Test 21: Display pending approval requests
**Purpose**: Verify request card rendering
```typescript
it('should display pending approval requests')
```
**What it tests**:
- Request cards render
- Request titles visible

**Manual verification**:
- [ ] Cards are easy to scan
- [ ] Most important info is prominent
- [ ] Card spacing is comfortable

---

#### ✅ Test 22: Show request type and status chips
**Purpose**: Verify request metadata badges
```typescript
it('should show request type and status chips')
```
**What it tests**:
- Type chip renders (Budget/Headcount/Promotion)
- Status chip renders (Pending/Approved/Rejected)

**Manual verification**:
- [ ] Chip colors are meaningful
- [ ] Chips are small but readable
- [ ] Multiple chips don't overlap

---

#### ✅ Test 23: Display requester information
**Purpose**: Verify requester visibility
```typescript
it('should display requester information')
```
**What it tests**:
- Requester name shown
- Avatar present

**Manual verification**:
- [ ] Avatar shows photo or initials
- [ ] Name is clickable (potential profile link)
- [ ] Timestamp shows when requested

---

#### ✅ Test 24: Show approval progress bar
**Purpose**: Verify progress visualization
```typescript
it('should show approval progress bar')
```
**What it tests**:
- Progress bars render
- Multiple bars supported

**Manual verification**:
- [ ] Progress fills left-to-right
- [ ] Color changes based on status (blue=in progress, green=approved, red=rejected)
- [ ] Bar animates smoothly

---

#### ✅ Test 25: Display approval level information
**Purpose**: Verify level tracking
```typescript
it('should display approval level information')
```
**What it tests**:
- Current level shown
- Total levels shown
- Format is "Level X of Y"

**Manual verification**:
- [ ] Level text is near progress bar
- [ ] Level updates as approvals happen
- [ ] Final level shows completion

---

#### ✅ Test 26: Show approver chips with status
**Purpose**: Verify approver chain visibility
```typescript
it('should show approver chips with status')
```
**What it tests**:
- Approver chips render
- Level shown in chip (L1, L2, L3)

**Manual verification**:
- [ ] Approved approvers are green
- [ ] Pending approvers are outlined
- [ ] Rejected approvers are red
- [ ] Chips show in sequential order

---

#### ✅ Test 27: Display action required badge
**Purpose**: Verify user action prompts
```typescript
it('should display action required badge for pending approvals')
```
**What it tests**:
- Badge appears for current user's pending approvals
- Badge is prominent (warning color)

**Manual verification**:
- [ ] Badge is highly visible
- [ ] Badge only shows for user's actions
- [ ] Badge disappears after action taken

---

### Request Details Tests (3 tests)

#### ✅ Test 28: Open detail dialog
**Purpose**: Verify detail view access
```typescript
it('should open detail dialog when clicking view details')
```
**What it tests**:
- View Details button exists
- Dialog opens on click

**Manual verification**:
- [ ] Dialog is modal (blocks background)
- [ ] Dialog is centered
- [ ] Dialog is scrollable if content is long

---

#### ✅ Test 29: Display full request description
**Purpose**: Verify complete information display
```typescript
it('should display full request description')
```
**What it tests**:
- Full description visible (not truncated)
- Description formatting preserved

**Manual verification**:
- [ ] Description is readable
- [ ] Line breaks are preserved
- [ ] Long text scrolls

---

#### ✅ Test 30: Display approval chain as stepper
**Purpose**: Verify approval flow visualization
```typescript
it('should display approval chain as stepper')
```
**What it tests**:
- Stepper component renders
- All approvers shown in order

**Manual verification**:
- [ ] Stepper is vertical
- [ ] Completed steps are green
- [ ] Active step is highlighted
- [ ] Future steps are gray
- [ ] Lines connect steps

---

## TeamCalendar Component (13 tests)

### Rendering Tests (3 tests)

#### ✅ Test 31: Render page header and title
**Purpose**: Verify page structure
```typescript
it('should render page header and title')
```
**What it tests**:
- Title visible
- Subtitle visible

**Manual verification**:
- [ ] Title is "Team Calendar"
- [ ] Subtitle explains purpose
- [ ] Header spacing is consistent

---

#### ✅ Test 32: Render view selector dropdown
**Purpose**: Verify view switching control
```typescript
it('should render view selector dropdown')
```
**What it tests**:
- Dropdown exists
- Default is "Week" view

**Manual verification**:
- [ ] Dropdown shows current view
- [ ] All options visible (Month/Week/Day)
- [ ] Selection changes view immediately

---

#### ✅ Test 33: Render new event button
**Purpose**: Verify event creation entry point
```typescript
it('should render new event button')
```
**What it tests**:
- Button exists
- Button is clickable

**Manual verification**:
- [ ] Button is prominent
- [ ] Button stays visible when scrolling
- [ ] Button color is primary

---

### Quick Stats Tests (4 tests)

#### ✅ Test 34: Display upcoming events count
**Purpose**: Verify stats card accuracy
```typescript
it('should display upcoming events count')
```
**What it tests**:
- Stat card renders
- Count is accurate

**Manual verification**:
- [ ] Count updates in real-time
- [ ] Card is clickable (filters view)
- [ ] Label is clear

---

#### ✅ Test 35: Display today's meetings count
**Purpose**: Verify daily summary
```typescript
it('should display today meetings count')
```
**What it tests**:
- Today's count shown
- Count filters to current date

**Manual verification**:
- [ ] Count resets at midnight
- [ ] Time zone is correct
- [ ] Past meetings don't count

---

#### ✅ Test 36: Display pending invitations count
**Purpose**: Verify action items tracking
```typescript
it('should display pending invitations count')
```
**What it tests**:
- Pending count accurate
- Filters by user's status

**Manual verification**:
- [ ] Count decreases after RSVP
- [ ] Only user's invitations count
- [ ] Badge is warning color

---

#### ✅ Test 37: Display scheduling conflicts count
**Purpose**: Verify conflict detection
```typescript
it('should display scheduling conflicts count')
```
**What it tests**:
- Conflict detection works
- Count shows overlapping events

**Manual verification**:
- [ ] Overlapping times detected
- [ ] All-day events handled
- [ ] Badge is error color

---

### Event List Tests (6 tests)

#### ✅ Test 38: Display calendar events
**Purpose**: Verify event rendering
```typescript
it('should display calendar events')
```
**What it tests**:
- Events render
- Multiple events supported

**Manual verification**:
- [ ] Events are chronologically ordered
- [ ] Date headers are clear
- [ ] Events group by date

---

#### ✅ Test 39: Show event type chips
**Purpose**: Verify event categorization
```typescript
it('should show event type chips')
```
**What it tests**:
- Type chips render (1:1/Team Meeting/All Hands/Interview/Training)
- Colors distinguish types

**Manual verification**:
- [ ] 1:1 is green
- [ ] Team Meeting is blue
- [ ] All Hands is purple
- [ ] Interview is orange
- [ ] Training is red

---

#### ✅ Test 40: Display event times and duration
**Purpose**: Verify time information
```typescript
it('should display event times and duration')
```
**What it tests**:
- Start time shown
- End time shown
- Duration calculated (e.g., "30m", "1h 30m")

**Manual verification**:
- [ ] Times use 12-hour format with AM/PM
- [ ] Duration is accurate
- [ ] Timezone shown if ambiguous

---

#### ✅ Test 41: Show virtual meeting indicator
**Purpose**: Verify meeting type visibility
```typescript
it('should show virtual meeting indicator')
```
**What it tests**:
- Virtual icon/text shown
- Distinguishes from physical

**Manual verification**:
- [ ] Virtual meetings show 💻 icon
- [ ] Physical meetings show 📍 icon
- [ ] Icons are consistent

---

#### ✅ Test 42: Display recurring event indicator
**Purpose**: Verify recurrence visibility
```typescript
it('should display recurring event indicator')
```
**What it tests**:
- Recurring badge shows
- Pattern described

**Manual verification**:
- [ ] Badge says "Recurring"
- [ ] Pattern shows in detail (e.g., "Weekly on Monday")
- [ ] Color is distinct

---

#### ✅ Test 43: Show join button for virtual meetings
**Purpose**: Verify meeting access
```typescript
it('should show join button for virtual meetings')
```
**What it tests**:
- Join button appears for virtual events
- Button is clickable

**Manual verification**:
- [ ] Button opens meeting link in new tab
- [ ] Button is disabled for past meetings
- [ ] Button is prominent near start time

---

## Manual Testing Checklist

### Cross-Component Tests

#### Integration Flow 1: Project to Approval
- [ ] Create project task requiring budget approval
- [ ] Submit approval request from project
- [ ] Approve in approval workflow
- [ ] Verify project task updates

#### Integration Flow 2: Calendar to Project
- [ ] Schedule project meeting in calendar
- [ ] Link meeting to project tasks
- [ ] Verify attendees can access project
- [ ] Update task status from meeting notes

#### Integration Flow 3: Approval to Calendar
- [ ] Submit headcount approval
- [ ] Schedule interview after approval
- [ ] Track hiring progress
- [ ] Complete process end-to-end

### Accessibility Tests

#### Keyboard Navigation
- [ ] All components fully navigable with Tab
- [ ] Enter/Space triggers buttons
- [ ] Escape closes dialogs
- [ ] Arrow keys work in dropdowns/lists

#### Screen Reader
- [ ] All images have alt text
- [ ] Form fields have labels
- [ ] Status changes announced
- [ ] Loading states communicated

#### Color Contrast
- [ ] Text readable on all backgrounds
- [ ] Dark mode colors meet WCAG AA
- [ ] Status colors distinguishable
- [ ] Focus indicators visible

### Responsive Design Tests

#### Mobile (< 640px)
- [ ] Kanban columns stack vertically
- [ ] Forms are single column
- [ ] Buttons remain tappable (44px min)
- [ ] Dialogs fill screen appropriately

#### Tablet (640px - 1024px)
- [ ] Two-column layouts work
- [ ] Tables scroll horizontally
- [ ] Touch targets appropriate
- [ ] Sidebars collapse gracefully

#### Desktop (> 1024px)
- [ ] Full multi-column layouts
- [ ] Hover states work
- [ ] Modals are centered and sized well
- [ ] Content doesn't stretch excessively

### Performance Tests

#### Load Time
- [ ] Initial render < 1 second
- [ ] Large lists virtualized
- [ ] Images lazy loaded
- [ ] Dialogs open instantly

#### Interactions
- [ ] Button clicks respond < 100ms
- [ ] Animations are smooth (60fps)
- [ ] No layout shifts during load
- [ ] Optimistic UI updates

### Edge Cases

#### Empty States
- [ ] No tasks: Shows helpful message
- [ ] No approvals: Prompts to create
- [ ] No events: Suggests scheduling
- [ ] Filtered to zero: Clear filter option

#### Error Handling
- [ ] Network failures show retry
- [ ] Invalid form data shows errors
- [ ] Permission denied explained
- [ ] Conflicts clearly stated

#### Data Limits
- [ ] 100+ tasks: Pagination works
- [ ] 50+ approvers: List scrolls
- [ ] 200+ events: Performance OK
- [ ] Long text: Truncates/scrolls

---

## Test Coverage Summary

### ProjectWorkspace: 16 tests
- ✅ 5 Rendering tests
- ✅ 5 Board view tests
- ✅ 3 Task creation tests
- ✅ 3 Task detail tests

### ApprovalWorkflow: 14 tests
- ✅ 4 Rendering tests
- ✅ 7 Request list tests
- ✅ 3 Request detail tests

### TeamCalendar: 13 tests
- ✅ 3 Rendering tests
- ✅ 4 Quick stats tests
- ✅ 6 Event list tests

**Total: 43 integration tests**

---

## Quality Gates

All quality gates must pass before deployment:

### Automated Tests
- [ ] All 43 integration tests pass
- [ ] Code coverage > 80%
- [ ] No console errors in tests
- [ ] No TypeScript errors
- [ ] ESLint passes with zero warnings

### Manual Tests
- [ ] All 60+ manual checks completed
- [ ] Accessibility audit passes
- [ ] Responsive design verified
- [ ] Performance benchmarks met
- [ ] Edge cases handled gracefully

### User Acceptance
- [ ] Product owner approves functionality
- [ ] Design review passes
- [ ] Security review complete
- [ ] Documentation up to date
- [ ] Release notes prepared

---

## Common Issues & Solutions

### Issue 1: Tests fail with "Not wrapped in act(...)"
**Solution**: Ensure all async operations use `await` and `waitFor`
```typescript
await waitFor(() => {
  expect(screen.getByText('Expected text')).toBeInTheDocument();
});
```

### Issue 2: Dialog tests fail to find elements
**Solution**: Check that dialog is actually open before querying
```typescript
fireEvent.click(screen.getByRole('button', { name: /open/i }));
expect(screen.getByRole('dialog')).toBeInTheDocument();
```

### Issue 3: Mock callbacks not firing
**Solution**: Verify mock is passed as prop and not overwritten
```typescript
const onSubmit = vi.fn();
render(<Component onSubmit={onSubmit} />);
// Don't create new mock inside test
```

### Issue 4: Avatar images fail to load in tests
**Solution**: Mock image loading or use test utils
```typescript
// In test setup
global.Image = class extends Image {
  constructor() {
    super();
    setTimeout(() => this.onload?.(), 0);
  }
};
```

### Issue 5: Date/time tests fail in CI
**Solution**: Mock Date to ensure consistent timezone
```typescript
vi.useFakeTimers();
vi.setSystemTime(new Date('2024-01-15T12:00:00Z'));
```

---

## Future Enhancements

### Test Coverage Improvements
- [ ] Add visual regression tests (Chromatic/Percy)
- [ ] Add E2E tests (Playwright/Cypress)
- [ ] Add performance tests (Lighthouse CI)
- [ ] Add A/B testing framework

### Component Enhancements
- [ ] Real-time collaboration (WebSockets)
- [ ] Drag-and-drop task reordering
- [ ] Advanced filtering and search
- [ ] Export/import functionality
- [ ] Mobile native apps

### Integration Opportunities
- [ ] Slack/Teams notifications
- [ ] Email digests
- [ ] Calendar sync (Google/Outlook)
- [ ] Jira/Asana integration
- [ ] Analytics and reporting

---

**Last Updated**: Session 10
**Test Suite Version**: 1.0.0
**Maintained By**: Software-Org Team
