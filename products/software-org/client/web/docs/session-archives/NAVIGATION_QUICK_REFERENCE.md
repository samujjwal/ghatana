# Navigation System - Quick Reference

## What's New?

### 🏠 Home Button
- **Location**: Top-left of every page
- **Function**: Quick access to home from anywhere
- **Keyboard**: Tab + Enter

### ← Back Button  
- **Location**: Next to home button
- **Function**: Go to previous page (like browser back)
- **Disabled**: On home page or first visit
- **Keyboard**: Tab + Enter

### 📍 Breadcrumb Navigation
- **Location**: Below top nav
- **Shows**: Current path (e.g., Home / Control Tower)
- **Hidden**: On home page and mobile
- **Clickable**: Links to parent pages

---

## How to Use

### Go to Home
```
Any page → Click 🏠 → Home
```

### Go Back
```
/workflows → Click ← → Previous page
```

### Jump to Parent
```
Home / Workflows → Click "Home" → Home page
```

---

## Visual Layout

```
┌─ Top Nav (Fixed) ─────────────────────┐
│ ← 🏠  Page Title                      │
├───────────────────────────────────────┤
│ Breadcrumb: Home / Feature / Detail   │ (hidden on mobile)
├───────────────────────────────────────┤
│                                        │
│ Page Content                           │
│                                        │
└────────────────────────────────────────┘
```

---

## Features

✅ **Always Visible**: Home button on every page
✅ **Smart Back Button**: Knows when to disable
✅ **Clear Path**: Breadcrumbs show where you are
✅ **Mobile Friendly**: Optimized for all screen sizes
✅ **Dark Mode**: Full support
✅ **Accessible**: Keyboard & screen reader support

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Tab | Focus next button |
| Shift+Tab | Focus previous button |
| Enter/Space | Activate button |
| Alt+Left | Browser back (native) |

---

## Mobile Experience

- **Small Screens**: Breadcrumb hidden (buttons only)
- **Large Screens**: Full breadcrumb visible
- **Touch**: All buttons easy to tap

---

## Common Tasks

### Task: Go Home Quickly
→ Click home button 🏠

### Task: Undo Navigation
→ Click back button ← (or browser back)

### Task: Jump to Home via Breadcrumb
→ Click "Home" in breadcrumb (e.g., Home / Workflows)

### Task: See Current Location
→ Look at breadcrumb or page title

---

## Troubleshooting

**Back button not working?**
- On home page: back button disabled (expected)
- First page: no history to go back to
- Solution: Use home button instead

**Breadcrumb not showing?**
- Mobile screen: breadcrumb hidden
- Home page: not needed
- Solution: Use top nav buttons

**Not seeing home button?**
- Should always be visible (🏠)
- Check if page loaded properly
- Try refreshing browser

---

## Technical Details

### Components
- `TopNavigation` - Fixed top bar with buttons
- `Breadcrumb` - Path display
- `NavigationContext` - State management

### Routes Supported
All routes automatically supported:
- / (Home)
- /dashboard (Control Tower)
- /departments (Organization)
- /workflows (Workflows)
- /hitl (HITL Console)
- /simulator (Event Simulator)
- /reports (Reports)
- /security (Security)
- /models (Model Catalog)
- /settings (Settings)
- /help (Help Center)
- /export (Data Export)
- /realtime-monitor (Real-Time Monitor)
- /ml-observatory (ML Observatory)
- /automation (Automation Engine)

### Adding New Routes

1. Add route to `Router.tsx`:
```typescript
{ path: '/new-feature', element: <NewPage /> }
```

2. Add label in `NavigationContext.tsx`:
```typescript
'/new-feature': { label: 'New Feature', icon: '⭐' }
```

Done! Navigation works automatically.

---

## Documentation

- `NAVIGATION_SYSTEM.md` - Complete documentation
- `NAVIGATION_IMPLEMENTATION_SUMMARY.md` - Implementation details
- `NAVIGATION_VISUAL_GUIDE.md` - Visual examples

---

## Files

### Created
- `src/context/NavigationContext.tsx`
- `src/shared/components/TopNavigation.tsx`
- `src/shared/components/Breadcrumb.tsx`

### Modified
- `src/app/App.tsx`
- `src/shared/components/index.ts`

---

## Status

✅ Build: Successful
✅ Tests: Passing
✅ Performance: Optimized
✅ Accessibility: WCAG AA
✅ Dark Mode: Full Support
✅ Mobile: Responsive

---

**Version**: 1.0
**Date**: November 23, 2025
**Build**: Vite 5.4.21
