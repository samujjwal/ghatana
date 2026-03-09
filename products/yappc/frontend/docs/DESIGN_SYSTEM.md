# YAPPC Design System Documentation

> **Last Updated:** 2025-01-XX | **Version:** 1.0.0

This document provides comprehensive documentation for the YAPPC design system, including design tokens, component library, and usage guidelines.

---

## Table of Contents

1. [Overview](#overview)
2. [Design Tokens](#design-tokens)
3. [Component Library](#component-library)
4. [Accessibility Standards](#accessibility-standards)
5. [Usage Guidelines](#usage-guidelines)
6. [Best Practices](#best-practices)

---

## Overview

The YAPPC design system provides a consistent, accessible, and maintainable UI framework for building application interfaces. It consists of:

- **Design Tokens**: Centralized values for spacing, colors, typography, and layout
- **@yappc/ui Library**: Reusable React components built on top of the tokens
- **Accessibility Utilities**: Helpers for WCAG compliance
- **Tailwind Integration**: CSS utility classes aligned with tokens

### Architecture

```
libs/ui/                    # @yappc/ui component library
├── src/
│   ├── components/         # React components (atoms, molecules, organisms)
│   ├── tokens/             # Design tokens (colors, spacing, typography)
│   ├── theme/              # Theme configuration
│   └── hooks/              # Shared hooks

apps/web/src/
├── styles/
│   └── design-tokens.ts    # App-specific layout tokens
├── utils/
│   └── accessibility.ts    # Accessibility utilities
└── components/             # App components using the design system
```

---

## Design Tokens

### Spacing (`libs/ui/src/tokens/spacing.ts`)

Standard spacing scale using 4px base unit:

| Token | Value | Tailwind Class |
|-------|-------|----------------|
| `xs` | 4px | `gap-1`, `p-1`, `m-1` |
| `sm` | 8px | `gap-2`, `p-2`, `m-2` |
| `md` | 16px | `gap-4`, `p-4`, `m-4` |
| `lg` | 24px | `gap-6`, `p-6`, `m-6` |
| `xl` | 32px | `gap-8`, `p-8`, `m-8` |

### Colors (`libs/ui/src/tokens/colors.ts`)

Semantic color palette supporting light/dark themes:

| Category | Token | Light Mode | Dark Mode | Usage |
|----------|-------|------------|-----------|-------|
| **Primary** | `primary-500` | `#2563eb` | `#3b82f6` | Primary actions, links |
| **Primary** | `primary-600` | `#1d4ed8` | `#2563eb` | Hover states |
| **Text** | `text-primary` | `#1f2937` | `#f9fafb` | Body text |
| **Text** | `text-secondary` | `#6b7280` | `#9ca3af` | Secondary text |
| **Background** | `bg-default` | `#ffffff` | `#111827` | Page background |
| **Background** | `bg-paper` | `#f9fafb` | `#1f2937` | Card/panel background |
| **Border** | `divider` | `#e5e7eb` | `#374151` | Borders, dividers |
| **Success** | `success-color` | `#16a34a` | `#22c55e` | Success states |
| **Warning** | `warning-color` | `#d97706` | `#f59e0b` | Warning states |
| **Error** | `error-color` | `#dc2626` | `#ef4444` | Error states |

### Typography (`libs/ui/src/tokens/typography.ts`)

| Token | Size | Line Height | Weight | Usage |
|-------|------|-------------|--------|-------|
| `h1` | 2rem | 1.25 | 700 | Page titles |
| `h2` | 1.5rem | 1.33 | 600 | Section headers |
| `h3` | 1.25rem | 1.4 | 600 | Subsection headers |
| `body` | 1rem | 1.5 | 400 | Body text |
| `small` | 0.875rem | 1.43 | 400 | Captions, labels |
| `xs` | 0.75rem | 1.33 | 400 | Badges, hints |

### Layout Tokens (`apps/web/src/styles/design-tokens.ts`)

#### Sidebar

| Token | Value | Description |
|-------|-------|-------------|
| `SIDEBAR.expandedWidth` | `w-56` (224px) | Sidebar when expanded |
| `SIDEBAR.collapsedWidth` | `w-[60px]` (60px) | Sidebar when collapsed |

#### Toolbar

| Token | Value | Description |
|-------|-------|-------------|
| `TOOLBAR.height` | `h-12` (48px) | Standard toolbar height |
| `TOOLBAR.compactHeight` | `h-10` (40px) | Compact toolbar height |

#### Panels

| Token | Value | Description |
|-------|-------|-------------|
| `PANELS.width` | `w-80` (320px) | Standard panel width |
| `PANELS.wideWidth` | `w-[400px]` (400px) | Wide panel width |
| `PANELS.taskExpandedWidth` | `w-72` (280px) | Task panel expanded |

#### Z-Index Layers

| Token | Value | Usage |
|-------|-------|-------|
| `Z_INDEX.canvas` | 0 | Canvas base layer |
| `Z_INDEX.controls` | 20 | Floating controls |
| `Z_INDEX.panel` | 40 | Side panels |
| `Z_INDEX.commandPalette` | 60 | Command palette |
| `Z_INDEX.toast` | 70 | Toast notifications |
| `Z_INDEX.toolbar` | 100 | Fixed toolbars |
| `Z_INDEX.dropdown` | 1000 | Dropdown menus |
| `Z_INDEX.modal` | 1300 | Modal dialogs |

---

## Component Library

### Core Components (`@yappc/ui`)

#### Buttons

```tsx
import { Button } from '@yappc/ui';

// Variants
<Button variant="primary">Primary</Button>
<Button variant="secondary">Secondary</Button>
<Button variant="ghost">Ghost</Button>
<Button variant="danger">Danger</Button>

// Sizes
<Button size="sm">Small</Button>
<Button size="md">Medium</Button>
<Button size="lg">Large</Button>
```

#### Form Components

```tsx
import { Input, Select, Checkbox, Switch, Textarea } from '@yappc/ui';

<Input 
  label="Email" 
  placeholder="Enter email" 
  error="Invalid email"
/>

<Select
  label="Category"
  options={[
    { value: 'a', label: 'Option A' },
    { value: 'b', label: 'Option B' },
  ]}
/>

<Checkbox label="Accept terms" checked={accepted} onChange={setAccepted} />

<Switch label="Enable notifications" />
```

#### Feedback Components

```tsx
import { Alert, Toast, Progress, Spinner, Skeleton } from '@yappc/ui';

<Alert variant="success" title="Success!" message="Operation completed." />
<Alert variant="warning" title="Warning" message="Please review." />
<Alert variant="error" title="Error" message="Something went wrong." />

<Spinner size="md" />
<Progress value={75} max={100} />
<Skeleton width="100%" height="20px" />
```

#### Navigation Components

```tsx
import { Breadcrumb, Tabs, Menu } from '@yappc/ui';

<Breadcrumb
  items={[
    { label: 'Home', href: '/' },
    { label: 'Projects', href: '/projects' },
    { label: 'Canvas' },
  ]}
/>

<Tabs
  tabs={[
    { key: 'overview', label: 'Overview' },
    { key: 'settings', label: 'Settings' },
  ]}
  activeTab="overview"
  onChange={setActiveTab}
/>
```

#### Layout Components

```tsx
import { Card, Box, Stack, Grid, Divider } from '@yappc/ui';

<Card>
  <Card.Header title="Card Title" />
  <Card.Body>Content here</Card.Body>
  <Card.Footer>
    <Button>Action</Button>
  </Card.Footer>
</Card>

<Stack direction="column" gap="md">
  <Box>Item 1</Box>
  <Box>Item 2</Box>
</Stack>
```

#### Overlay Components

```tsx
import { Modal, Dialog, Drawer, Tooltip, Popover } from '@yappc/ui';

<Modal open={isOpen} onClose={handleClose} title="Confirm">
  <p>Are you sure?</p>
  <Modal.Actions>
    <Button onClick={handleClose}>Cancel</Button>
    <Button variant="primary" onClick={handleConfirm}>Confirm</Button>
  </Modal.Actions>
</Modal>

<Tooltip content="Helpful tip">
  <span>Hover me</span>
</Tooltip>
```

---

## Accessibility Standards

### Focus Indicators

All interactive elements MUST have visible focus indicators:

```tsx
import { focusRingClass, focusVisibleClass } from '@/utils/accessibility';

// Use focus ring classes
<button className={`px-4 py-2 ${focusRingClass}`}>
  Focused Button
</button>

// Classes available:
// focusRingClass = "focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
// focusVisibleClass = "focus-visible:outline-none focus-visible:ring-2 ..."
```

### ARIA Labels

All interactive elements MUST have accessible labels:

```tsx
// Buttons with icons only
<button aria-label="Delete item">
  <TrashIcon aria-hidden="true" />
</button>

// Decorative icons
<span aria-hidden="true">🎨</span>

// Form fields
<input 
  id="email" 
  aria-label="Email address" 
  aria-describedby="email-hint"
/>
<span id="email-hint">We'll never share your email.</span>
```

### Skip Links

The app shell includes skip links for keyboard navigation:

```tsx
import { SkipLink } from '@/components/accessibility';

// In layout
<SkipLink targetId="main-content" />
<nav>...</nav>
<main id="main-content" tabIndex={-1}>
  ...
</main>
```

### Live Regions

Dynamic content updates MUST announce to screen readers:

```tsx
import { liveRegionProps, assertiveRegionProps } from '@/utils/accessibility';

// Polite announcements (status updates)
<div {...liveRegionProps}>
  {status}
</div>

// Assertive announcements (errors)
<div role="alert" aria-live="assertive">
  {error}
</div>
```

### Landmarks

Use proper landmark roles:

```tsx
<header role="banner">...</header>
<nav role="navigation" aria-label="Main navigation">...</nav>
<main role="main" aria-label="Main content">...</main>
<aside role="complementary" aria-label="Sidebar">...</aside>
<footer role="contentinfo">...</footer>
```

---

## Usage Guidelines

### Importing Design Tokens

```tsx
// Layout tokens from app
import { SIDEBAR, TOOLBAR, PANELS, Z_INDEX, TRANSITIONS } from '@/styles/design-tokens';

// Use in components
<aside 
  className={`${SIDEBAR.expandedWidth} ${TRANSITIONS.default}`}
  style={{ zIndex: Z_INDEX.panel }}
>
```

### Using UI Components

```tsx
// Import from @yappc/ui
import { Button, Card, Input } from '@yappc/ui';

// Or from specific paths for tree-shaking
import { Button } from '@yappc/ui/components/Button';
```

### Theme Integration

Components automatically adapt to light/dark theme via CSS variables:

```css
/* Theme variables are set on :root and .dark */
:root {
  --color-primary-500: #2563eb;
  --color-bg-default: #ffffff;
}

.dark {
  --color-primary-500: #3b82f6;
  --color-bg-default: #111827;
}
```

### Responsive Design

Use Tailwind breakpoints consistently:

| Breakpoint | Min Width | Usage |
|------------|-----------|-------|
| `sm` | 640px | Mobile landscape |
| `md` | 768px | Tablets |
| `lg` | 1024px | Small laptops |
| `xl` | 1280px | Desktops |
| `2xl` | 1536px | Large screens |

```tsx
<div className="p-4 md:p-6 lg:p-8">
  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    ...
  </div>
</div>
```

---

## Best Practices

### 1. Use Semantic HTML

```tsx
// ✅ Good
<nav aria-label="Main">
  <ul>
    <li><a href="/home">Home</a></li>
  </ul>
</nav>

// ❌ Bad
<div className="nav">
  <div className="nav-item" onClick={...}>Home</div>
</div>
```

### 2. Leverage Design Tokens

```tsx
// ✅ Good - Uses design tokens
import { SIDEBAR, TRANSITIONS } from '@/styles/design-tokens';
<aside className={`${SIDEBAR.expandedWidth} ${TRANSITIONS.default}`}>

// ❌ Bad - Hardcoded values
<aside className="w-[224px] transition-all duration-200">
```

### 3. Maintain Consistent Spacing

```tsx
// ✅ Good - Uses spacing scale
<div className="p-4 gap-2">

// ❌ Bad - Arbitrary values
<div className="p-[13px] gap-[7px]">
```

### 4. Handle Loading States

```tsx
// ✅ Good - Shows loading feedback
{isLoading ? (
  <Skeleton variant="text" width="100%" count={3} />
) : (
  <DataList items={items} />
)}
```

### 5. Provide Error States

```tsx
// ✅ Good - Shows error with recovery option
{error ? (
  <Alert 
    variant="error" 
    title="Failed to load"
    action={<Button onClick={retry}>Retry</Button>}
  />
) : (
  <Content />
)}
```

### 6. Test Keyboard Navigation

All interactive elements must be:
- Reachable via Tab key
- Operable via Enter/Space
- Dismissible via Escape (modals, dropdowns)
- Have visible focus indicators

---

## Component Inventory

### Available in `@yappc/ui`

| Category | Components |
|----------|------------|
| **Actions** | Button, IconButton, ButtonGroup |
| **Form** | Input, Select, Checkbox, Radio, Switch, Textarea, DatePicker, TimePicker |
| **Feedback** | Alert, Toast, Progress, Spinner, Skeleton |
| **Data Display** | Badge, Chip, Avatar, Table, DataTable, TreeView |
| **Navigation** | Breadcrumb, Tabs, Menu, Stepper, Pagination |
| **Layout** | Box, Card, Stack, Grid, Container, Divider, Paper |
| **Overlay** | Modal, Dialog, Drawer, Tooltip, Popover |
| **Typography** | Typography, Text, Heading |

### App-Specific Components

| Category | Components |
|----------|------------|
| **Navigation** | BreadcrumbBar, UnifiedPhaseRail |
| **Accessibility** | SkipLink |
| **Workspace** | WorkspaceSelector, HeaderWithBreadcrumb |
| **Canvas** | CanvasScene, CanvasToolbar, CanvasStatusBar |

---

## Related Documentation

- [Tailwind CSS Configuration](../tailwind.config.js)
- [Accessibility Utilities](../src/utils/accessibility.ts)
- [Component Stories](../libs/ui/src/stories/)
- [UI/UX Enhancement Plan](./COMPREHENSIVE_UI_UX_IMPLEMENTATION_PLAN.md)
