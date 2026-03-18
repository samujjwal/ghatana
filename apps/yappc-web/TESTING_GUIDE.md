# Canvas UX Testing Guide

## 🧪 Manual Testing Checklist

### Prerequisites

- [ ] Build completed: `npm run build` or `./gradlew build`
- [ ] Development server running: `npm run dev`
- [ ] Browser DevTools open (to check console errors)

---

## Test Suite 1: Empty State UX

### Test 1.1: Empty State Renders Correctly

**Steps:**

1. Navigate to canvas with no components
2. Verify empty state displays with:
   - Compact header (~35% of viewport height)
   - "Build Your Canvas" heading
   - 3 CTA buttons: [Get Started] [Templates] [AI ✨]
   - Grid of 8 feature preview cards
   - Canvas grid visible in background

**Expected:**

- ✅ No full-screen modal blocking view
- ✅ Grid dots visible (50px spacing, subtle grey)
- ✅ Preview cards in 3-column layout (desktop)
- ✅ All text readable, icons clear

**Screenshot:** `empty-state-initial.png`

---

### Test 1.2: Feature Card Interaction

**Steps:**

1. Hover over each feature preview card
2. Click a feature card

**Expected:**

- ✅ Card elevates on hover (scale 1.02, shadow)
- ✅ Clicking card triggers component addition (check console log)
- ✅ Smooth transition (0.2s ease-in-out)

---

### Test 1.3: Empty State Disappears

**Steps:**

1. Add first component to canvas
2. Verify empty state is hidden

**Expected:**

- ✅ Empty state disappears when `nodes.length > 0`
- ✅ Canvas grid remains visible
- ✅ Toolbar updates (history controls appear)

---

## Test Suite 2: Component Palette

### Test 2.1: Default Expanded Categories

**Steps:**

1. Open component palette (left sidebar)
2. Check which categories are expanded

**Expected:**

- ✅ **Architecture** expanded
- ✅ **Pages** expanded
- ✅ Process collapsed (›)
- ✅ UI Components collapsed (›)
- ✅ Infrastructure collapsed (›)

**Screenshot:** `palette-default-state.png`

---

### Test 2.2: Icon Sizes

**Steps:**

1. Inspect palette component icons
2. Measure avatar size in DevTools

**Expected:**

- ✅ Avatar size: 48x48 pixels
- ✅ Icon font size: 1.5em (~24px)
- ✅ Icons clearly visible and well-spaced

---

### Test 2.3: Recently Used Section

**Steps:**

1. Start with empty canvas
2. Add component "Frontend" from palette
3. Add component "Database" from palette
4. Check top of palette for Recently Used section

**Expected:**

- ✅ "Recently Used 🔥" section appears at top
- ✅ Shows last 2 components added (Database, Frontend)
- ✅ Section has light blue background (`primary.50`)
- ✅ Badge shows count: "2"
- ✅ Badge pulses (opacity animation)

**Test variations:**

- Add 6 components → verify only 5 shown (max limit)
- Add same component twice → verify no duplicates

**Screenshot:** `recently-used-section.png`

---

### Test 2.4: Palette Item Hover Animation

**Steps:**

1. Hover over any palette component item
2. Observe animation

**Expected:**

- ✅ Item slides right 4px
- ✅ Item scales to 1.02
- ✅ Shadow appears (elevation 2)
- ✅ Background changes to `action.hover`
- ✅ Smooth transition (0.2s)

---

### Test 2.5: Accordion Expansion

**Steps:**

1. Click collapsed category (e.g., "Process")
2. Observe expansion animation

**Expected:**

- ✅ Smooth expansion (0.3s ease-in-out)
- ✅ Background changes to `action.hover` when expanded
- ✅ Icon rotates (› → ▼)
- ✅ Items slide down smoothly

---

## Test Suite 3: Progressive Toolbar

### Test 3.1: History Controls Hidden (Empty)

**Steps:**

1. Start with empty canvas (no components)
2. Inspect toolbar

**Expected:**

- ✅ Undo button (↶) **NOT visible**
- ✅ Redo button (↷) **NOT visible**
- ✅ Mode dropdown visible
- ✅ Level dropdown visible
- ✅ AI button visible

**Screenshot:** `toolbar-empty-state.png`

---

### Test 3.2: History Controls Appear (After Edit)

**Steps:**

1. Add first component to canvas
2. Inspect toolbar

**Expected:**

- ✅ Undo button (↶) **NOW visible**
- ✅ Redo button (↷) **NOW visible**
- ✅ Buttons positioned before Mode dropdown

**Screenshot:** `toolbar-with-history.png`

---

### Test 3.3: History Controls Functionality

**Steps:**

1. Add component A
2. Add component B
3. Click Undo
4. Verify component B removed
5. Click Redo
6. Verify component B restored

**Expected:**

- ✅ Undo/Redo work as expected
- ✅ Progressive disclosure doesn't break functionality

---

## Test Suite 4: FAB Quick Actions

### Test 4.1: FAB Hidden When Empty

**Steps:**

1. Navigate to empty canvas
2. Check bottom-right corner

**Expected:**

- ✅ FAB **NOT visible** when `nodes.length === 0`
- ✅ Empty state handles initial actions

---

### Test 4.2: FAB Appears After First Component

**Steps:**

1. Add first component
2. Check bottom-right corner

**Expected:**

- ✅ FAB appears at bottom-right
- ✅ Positioned 24px from edges (80px on mobile)
- ✅ Primary color button with "+" icon

**Screenshot:** `fab-positioned.png`

---

### Test 4.3: Speed Dial Opens

**Steps:**

1. Click FAB
2. Verify speed dial opens upward

**Expected:**

- ✅ 6 action buttons appear above FAB:
  1. AI Suggest (secondary, AutoAwesome icon)
  2. Architecture Template (secondary, Architecture icon)
  3. Add Integration (warning, Cable icon)
  4. Add Frontend (info, Computer icon)
  5. Add Database (success, Storage icon)
  6. Add API (primary, Api icon)
- ✅ Tooltips show on hover
- ✅ Each button has correct color

**Screenshot:** `fab-speed-dial-open.png`

---

### Test 4.4: Quick Action Triggers

**Steps:**

1. Open speed dial
2. Click "Add API" button
3. Check console log

**Expected:**

- ✅ Console shows: `Quick add: api`
- ✅ Speed dial closes after selection
- ✅ Animation smooth

**Repeat for all 6 actions**

---

## Test Suite 5: Status Bar Visual Hierarchy

### Test 5.1: Phase Dot Sizes

**Steps:**

1. Navigate to canvas in "Validate" phase
2. Inspect phase dots in status bar

**Expected:**

- ✅ **Current phase (Validate):**
  - Size: 3.5px × 3.5px
  - Scale: 1.25
  - Ring: 4px primary.300
  - Shadow: lg
  - Color: primary.600
- ✅ **Completed phases (Intent, Shape):**
  - Size: 2px × 2px
  - Opacity: 0.6
  - Scale: 0.9
  - Color: green.500
- ✅ **Future phases (Generate, Run, etc.):**
  - Size: 2px × 2px
  - Opacity: 0.4
  - Scale: 0.85
  - Color: grey.300

**Screenshot:** `status-bar-hierarchy.png`

---

### Test 5.2: Phase Navigation

**Steps:**

1. Click different phase dots
2. Verify current phase updates

**Expected:**

- ✅ Visual hierarchy updates correctly
- ✅ New current phase emphasizes
- ✅ Transitions smooth

---

## Test Suite 6: Canvas Grid Background

### Test 6.1: Grid Visibility

**Steps:**

1. Zoom canvas to 100%
2. Check background pattern

**Expected:**

- ✅ Subtle dot grid visible
- ✅ Dot spacing: 50px
- ✅ Dot size: 2px
- ✅ Dot color: rgba(0, 0, 0, 0.05) (very light grey)

---

### Test 6.2: Grid at Different Zoom Levels

**Steps:**

1. Zoom in (150%)
2. Zoom out (50%)

**Expected:**

- ✅ Grid scales proportionally
- ✅ Always visible but subtle
- ✅ Doesn't interfere with components

**Screenshot:** `grid-background.png`

---

## Test Suite 7: Animations & Polish

### Test 7.1: Recently Used Badge Pulse

**Steps:**

1. Add components to create Recently Used section
2. Observe badge animation

**Expected:**

- ✅ Badge pulses: opacity 1.0 → 0.7 → 1.0
- ✅ Duration: 2 seconds per cycle
- ✅ Infinite loop
- ✅ Smooth easing

---

### Test 7.2: Palette Item Drag Animation

**Steps:**

1. Click and hold palette item
2. Drag item

**Expected:**

- ✅ Cursor changes to "grabbing"
- ✅ Item scales to 0.98 (active state)
- ✅ Release scales back to 1.0

---

### Test 7.3: All Hover States

**Test each component:**

- [ ] Palette items
- [ ] Empty state feature cards
- [ ] FAB buttons
- [ ] Toolbar buttons
- [ ] Status bar phase dots

**Expected:**

- ✅ All have hover feedback
- ✅ Transitions smooth (0.2-0.3s)
- ✅ No jank or flicker

---

## Test Suite 8: Responsive Design

### Test 8.1: Mobile Layout (<768px)

**Steps:**

1. Resize browser to mobile width (375px)
2. Check layout

**Expected:**

- ✅ Empty state cards stack vertically (1 column)
- ✅ Palette icons still 48px (no downsizing)
- ✅ Toolbar collapses to menu (hamburger)
- ✅ FAB positioned 80px from bottom (above nav)
- ✅ Status bar remains visible

**Screenshot:** `mobile-layout.png`

---

### Test 8.2: Tablet Layout (768px-1024px)

**Steps:**

1. Resize browser to tablet width (768px)

**Expected:**

- ✅ Empty state cards: 2-column grid
- ✅ Palette remains full width
- ✅ Toolbar shows most controls
- ✅ FAB positioned normally

**Screenshot:** `tablet-layout.png`

---

### Test 8.3: Desktop Layout (>1024px)

**Steps:**

1. Resize browser to desktop width (1440px)

**Expected:**

- ✅ Empty state cards: 3-column grid
- ✅ Palette fixed left
- ✅ Toolbar full controls
- ✅ All animations smooth

---

## Test Suite 9: Accessibility

### Test 9.1: Keyboard Navigation

**Steps:**

1. Tab through interface
2. Verify focus indicators

**Expected:**

- ✅ All interactive elements focusable
- ✅ Focus order logical
- ✅ Focus indicators visible
- ✅ Enter/Space activates buttons

---

### Test 9.2: Screen Reader

**Steps:**

1. Enable screen reader (NVDA/JAWS)
2. Navigate canvas UI

**Expected:**

- ✅ All buttons have aria-labels
- ✅ Component descriptions read correctly
- ✅ State changes announced

---

### Test 9.3: Color Contrast

**Steps:**

1. Check contrast ratios in DevTools

**Expected:**

- ✅ All text meets WCAG AA (4.5:1)
- ✅ Icons meet contrast requirements
- ✅ Interactive states distinguishable

---

## Test Suite 10: Performance

### Test 10.1: Animation Performance

**Steps:**

1. Open DevTools Performance tab
2. Record while interacting with palette

**Expected:**

- ✅ 60fps maintained during animations
- ✅ No layout thrashing
- ✅ No memory leaks

---

### Test 10.2: Large Component Lists

**Steps:**

1. Search palette to filter components
2. Expand/collapse categories rapidly

**Expected:**

- ✅ No lag or stuttering
- ✅ Smooth animations maintained

---

## 🐛 Bug Report Template

If you find issues, use this template:

```markdown
### Bug: [Short description]

**Steps to Reproduce:**

1.
2.
3.

**Expected Behavior:**

**Actual Behavior:**

**Screenshots:**

**Environment:**

- Browser:
- OS:
- Screen Size:
- Canvas State: (empty/with components)

**Console Errors:**
```

```

---

## ✅ Sign-Off Checklist

After completing all tests:

- [ ] All 10 test suites passed
- [ ] Screenshots captured for documentation
- [ ] No console errors or warnings
- [ ] Performance acceptable (60fps)
- [ ] Accessibility verified
- [ ] Mobile/tablet/desktop tested
- [ ] Browser compatibility checked (Chrome, Firefox, Safari)
- [ ] Ready for production deployment

**Tester Name:** _________________
**Date:** _________________
**Build Version:** _________________

---

## 📋 Quick Test (5 minutes)

**For rapid verification:**

1. [ ] Empty state renders (no modal)
2. [ ] Palette shows Architecture & Pages expanded
3. [ ] Icons are 48px (large and clear)
4. [ ] Add component → Recently Used appears
5. [ ] History controls hidden → appear after first edit
6. [ ] FAB appears after first component
7. [ ] Status bar: current phase emphasized
8. [ ] All hover animations work
9. [ ] Mobile layout responsive
10. [ ] No console errors

**Pass/Fail:** _________________
```
