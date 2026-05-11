# Design System Usage

**Status:** Canonical
**Owner:** UX Team
**Last reviewed:** 2026-05-10
**Supersedes:** N/A
**Superseded by:** N/A

## Overview

The Data Cloud UI uses a custom design system built on top of TailwindCSS. This document provides guidelines for using the design system components and patterns.

## Components

### Button Components

Use the Button component from `@ghatana/design-system` for all button interactions.

```typescript
import { Button } from '@ghatana/design-system';

<Button variant="primary" onClick={handleClick}>
  Save
</Button>
```

### Form Components

Use the Input, Select, and other form components from the design system for all form interactions.

### Icon Components

Use Lucide React icons for all iconography.

```typescript
import { Shield, CheckCircle } from 'lucide-react';
```

## Styling Guidelines

### Color Palette

The design system uses a defined color palette for consistency. Use TailwindCSS color utilities from the approved palette.

### Spacing

Use the spacing scale defined in the design system. Avoid arbitrary pixel values.

### Typography

Use the typography scale defined in the design system. Use semantic HTML elements (`h1`, `h2`, `p`, etc.) and apply typography utilities.

## Patterns

### Loading States

Use the Loader2 component from lucide-react for loading indicators.

```typescript
import { Loader2 } from 'lucide-react';

<Loader2 className="h-4 w-4 animate-spin" />
```

### Empty States

Use consistent empty state patterns with icons and descriptive text.

### Error States

Use the canonical error envelope for all error states. Display errors using the ErrorAlert component.

## Accessibility

Follow WCAG 2.1 AA guidelines:
- All interactive elements must be keyboard accessible
- All images must have alt text
- Color contrast must meet minimum requirements
- Use semantic HTML elements

## Responsive Design

Use the design system's responsive breakpoints:
- Mobile: `sm`
- Tablet: `md`
- Desktop: `lg`
- Large Desktop: `xl`

Use responsive utilities: `md:`, `lg:`, etc.
