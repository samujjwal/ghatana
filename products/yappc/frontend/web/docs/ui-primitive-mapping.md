# YAPPC UI Primitive Mapping

## Purpose

This document maps raw HTML elements to design-system components for YAPPC. The goal is to ensure visual and behavioral consistency across the entire application by using `@ghatana/design-system` components instead of raw controls and ad-hoc Tailwind patterns.

## Mapping Table

| Raw Usage | Replace With | Notes |
|-----------|--------------|-------|
| `<button>` | `Button` | Use variants: primary, secondary, destructive, ghost, subtle |
| `<input type="text">` | `TextField` or `Input` | TextField for labeled inputs, Input for inline |
| `<input type="number">` | `TextField` with type="number" | Use TextField for consistency |
| `<input type="email">` | `TextField` with type="email" | Use TextField for consistency |
| `<input type="password">` | `TextField` with type="password" | Use TextField for consistency |
| `<textarea>` | `TextField` multiline | Use TextField with multiline prop |
| `<select>` | `Select` | Use Select for dropdowns |
| `<input type="checkbox">` | `Checkbox` | Use Checkbox component |
| `<input type="radio">` | `Radio` or `RadioGroup` | Use RadioGroup for grouped radios |
| Custom card `<div>` | `Card` | Use Card for consistent card styling |
| Status badge `<span>` | `Badge` or `Chip` | Badge for status, Chip for tags/filters |
| Modal/Dialog | `Dialog` | Use Dialog for modals and dialogs |
| `<table>` | `DataTable` | Use DataTable for data tables |
| Alert `<div>` | `Alert` | Use Alert for notifications and alerts |
| Skeleton `<div>` | `Skeleton` | Use Skeleton for loading states |
| Progress bar | `ProgressBar` | Use ProgressBar for progress indication |
| Tabs | `Tabs` | Use Tabs for tabbed interfaces |
| Accordion | `Accordion` | Use Accordion for collapsible sections |
| Tooltip | `Tooltip` | Use Tooltip for hover information |
| Dropdown menu | `Dropdown` or `Menu` | Use Dropdown for action menus |
| Breadcrumb | `Breadcrumb` | Use Breadcrumb for navigation |
| Pagination | `Pagination` | Use Pagination for paginated lists |
| Form | `Form` | Use Form component with validation |

## Component Variants

### Button Variants
- **primary**: Main call-to-action
- **secondary**: Secondary actions
- **destructive**: Destructive actions (delete, remove)
- **ghost**: Subtle actions with no background
- **subtle**: Even more subtle than ghost

### Badge/Chip Variants
- **status**: Status indicators (success, warning, error, info)
- **filter**: Filter tags
- **category**: Category labels

### Alert Variants
- **success**: Success messages
- **warning**: Warning messages
- **error**: Error messages
- **info**: Informational messages

## Acceptance Criteria

1. **No raw form controls in product routes**: All form controls should use design-system components unless inside a design-system component
2. **Standardized variants**: Common variants must use the standardized naming (primary, secondary, destructive, ghost, subtle)
3. **Shared states**: Loading, empty, error, and unavailable states must use shared components
4. **Visual consistency**: Dark mode must work without route-specific patches
5. **Accessibility**: All controls must have accessible labels
6. **Keyboard navigation**: Focus states must be consistent

## Migration Priority

### High Priority (Core User Flows)
1. Login page
2. Onboarding flow
3. Dashboard
4. Workspaces
5. Projects
6. Project overview
7. Preview

### Medium Priority (Secondary Flows)
8. Canvas side panels
9. Page builder inspector
10. Lifecycle/phase cockpits

### Low Priority (Admin/Settings)
11. Admin pages
12. Settings pages

## Files Requiring Migration

### Auth Routes
- `src/pages/auth/login/`
- `src/pages/auth/register/`

### Dashboard
- `src/components/dashboard/DashboardView.tsx`

### Workspaces
- `src/components/workspace/`
- `src/pages/dashboard/workspaces/`

### Projects
- `src/pages/dashboard/projects/`

### Canvas
- `src/components/canvas/`
- `src/routes/app/project/canvas/`

## Implementation Notes

1. **Check design-system availability**: Before replacing, verify the component exists in `@ghatana/design-system`
2. **Maintain functionality**: Ensure all existing functionality is preserved after migration
3. **Update tests**: Update test files to work with new components
4. **Storybook**: Add Storybook stories for new component usage if needed
5. **Accessibility**: Ensure all components have proper ARIA labels and keyboard navigation

## Enforcement

See `YAPPC-DS-003` for lint/check rules to enforce design-system usage.
