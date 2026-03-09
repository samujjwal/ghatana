# Navigation System Documentation

## Overview

The Software-Org application now includes a comprehensive navigation system that allows users to:
- **Go to Home** from any page (fixed home button in top navigation)
- **Go Back** to the previous page (back button in top navigation)
- **See Breadcrumbs** showing the current navigation path

## Components

### 1. TopNavigation Component
**Location**: `src/shared/components/TopNavigation.tsx`

Fixed navigation bar at the top of every page with:
- **Back Button** (←): Navigate to previous page (disabled on home/first page)
- **Home Button** (🏠): Always navigate to home page
- **Page Title**: Shows the current page name in the center

**Features**:
- Responsive design (buttons always visible)
- Dark mode support
- Accessibility-friendly (proper ARIA labels and keyboard support)
- Smart back button (disabled when no history)
- Fixed positioning with z-index management

**Props**:
```typescript
interface TopNavigationProps {
    className?: string;           // Additional CSS classes
    showBackButton?: boolean;      // Default: true
    showHomeButton?: boolean;      // Default: true
}
```

### 2. Breadcrumb Component
**Location**: `src/shared/components/Breadcrumb.tsx`

Displays the navigation path as clickable breadcrumbs (e.g., `Home / Control Tower`).

**Features**:
- Shows full navigation path
- Previous pages are clickable links
- Icons for visual distinction
- Hidden on mobile (for compact UI)
- Hidden on home page (no breadcrumb needed)
- Dark mode support

**Example Output**:
```
Home / Control Tower
Home / Organization
Home / Workflows / [Details]
```

### 3. NavigationContext
**Location**: `src/context/NavigationContext.tsx`

React Context providing navigation information to all components.

**Exports**:
- `NavigationProvider`: Wraps the entire app (in App.tsx)
- `useNavigation()`: Hook to access navigation context

**Usage**:
```typescript
import { useNavigation } from '@/context/NavigationContext';

export function MyComponent() {
    const { breadcrumbs, pageTitle, isHomePage, currentPath } = useNavigation();
    
    return (
        <div>
            <h1>{pageTitle}</h1>
            {breadcrumbs.map(crumb => (
                <span key={crumb.path}>{crumb.label}</span>
            ))}
        </div>
    );
}
```

## Route Configuration

The navigation system automatically maps routes to display names:

```typescript
const ROUTE_LABELS: Record<string, { label: string; icon?: string }> = {
    '/': { label: 'Home', icon: '🏠' },
    '/dashboard': { label: 'Control Tower', icon: '📊' },
    '/departments': { label: 'Organization', icon: '🏢' },
    '/workflows': { label: 'Workflows', icon: '🔄' },
    '/hitl': { label: 'HITL Console', icon: '✋' },
    '/simulator': { label: 'Event Simulator', icon: '⚡' },
    '/reports': { label: 'Reports', icon: '📈' },
    '/security': { label: 'Security', icon: '🔒' },
    '/models': { label: 'Model Catalog', icon: '🎓' },
    '/settings': { label: 'Settings', icon: '⚙️' },
    '/help': { label: 'Help Center', icon: '❓' },
    '/export': { label: 'Data Export', icon: '📥' },
    '/realtime-monitor': { label: 'Real-Time Monitor', icon: '⏱️' },
    '/ml-observatory': { label: 'ML Observatory', icon: '🧠' },
    '/automation': { label: 'Automation Engine', icon: '⚙️' },
};
```

## Architecture

### Component Hierarchy

```
App
├── ErrorBoundary
└── NavigationProvider
    ├── TopNavigation
    │   ├── BackButton
    │   ├── HomeButton
    │   └── PageTitle
    ├── Breadcrumb
    │   └── BreadcrumbItem[] (clickable links)
    └── main (pt-16)
        └── Outlet (page content)
```

### Data Flow

1. **Location Change**: User navigates to different route
2. **NavigationContext Updates**: Automatically re-computes breadcrumbs and page title
3. **Components Re-render**: TopNavigation and Breadcrumb display updated information
4. **User Sees**: Current page title and navigation path

### Styling

- **Fixed Top Navigation**: `z-50`, `h-16` (64px)
- **Main Content**: `pt-16` (padding-top to account for fixed nav)
- **Dark Mode**: All components fully styled for dark mode
- **Responsive**: Breadcrumbs hidden on small screens (< 640px)

## Usage Examples

### Navigate from Home to a Feature
1. User clicks on feature card on home page
2. Gets navigated to `/dashboard` (or other feature)
3. TopNavigation shows:
   - Back button (enabled)
   - Home button
   - "Control Tower" title
4. Breadcrumb shows:
   - Home (clickable) / Control Tower

### Go Back and Forward
1. User is on `/workflows`
2. Clicks back button → goes to previous page (e.g., `/`)
3. Browser back button (or back button widget) → goes to `/workflows`
4. Back button is disabled on first page (no history)

### Navigate to a New Feature
1. User is on `/dashboard`
2. Clicks home button → goes to `/`
3. Breadcrumb disappears (only shown on non-home pages)
4. Back button is disabled (on home page)

## Adding New Routes

To add navigation support for a new route:

1. **Add route to Router.tsx**:
```typescript
const NewPage = React.lazy(() => import('@/features/new/NewPage'));
const routes = [
    // ... other routes
    { path: '/new-feature', element: <NewPage />, errorElement: <RouteErrorElement /> },
];
```

2. **Add label to ROUTE_LABELS in NavigationContext.tsx**:
```typescript
const ROUTE_LABELS: Record<string, { label: string; icon?: string }> = {
    // ... other routes
    '/new-feature': { label: 'New Feature', icon: '⭐' },
};
```

3. **That's it!** The navigation system automatically handles breadcrumbs and page title

## Accessibility Features

- ✅ Semantic HTML (nav, main elements)
- ✅ ARIA labels on buttons
- ✅ Keyboard navigation support
- ✅ Proper contrast ratios
- ✅ Focus indicators for interactive elements
- ✅ Disabled state styling for back button

## Performance

- **Lazy Rendering**: Breadcrumb only renders when necessary (not on home page)
- **Memoization**: Context value is memoized to prevent unnecessary re-renders
- **Lightweight**: Navigation components add minimal overhead
- **No External Dependencies**: Uses only React Router built-in features

## Future Enhancements

- [ ] Nested route breadcrumbs (e.g., `/dashboard/workflows/123`)
- [ ] Customizable breadcrumb icons per route
- [ ] Breadcrumb click animations
- [ ] Navigation history sidebar
- [ ] Keyboard shortcuts (e.g., `Alt+Home`, `Alt+Left`)
- [ ] Mobile-optimized navigation drawer
- [ ] Breadcrumb search/autocomplete
- [ ] Recent pages menu in home button dropdown

## Testing

### Test Cases

1. **Navigation between pages**:
   - Navigate to `/dashboard` → breadcrumb shows
   - Back button is enabled
   - Page title is "Control Tower"

2. **Back button behavior**:
   - On first page → back button disabled
   - After navigation → back button enabled
   - Click back → returns to previous page

3. **Home button**:
   - Visible on all pages
   - Click → always navigates to `/`
   - Breadcrumb disappears on home page

4. **Dark mode**:
   - All components styled correctly
   - Sufficient contrast
   - Icons visible

### Quick Test Sequence

```
1. Open http://localhost:3000/
   - Top nav visible (🏠 | Home)
   - No breadcrumb
   - Back button disabled

2. Click on Control Tower card
   - Navigate to /dashboard
   - Breadcrumb: Home / Control Tower
   - Back button enabled
   - Page title: "Control Tower"

3. Click back button
   - Navigate to /
   - Breadcrumb disappears
   - Back button disabled

4. Click on Organization card
   - Navigate to /departments
   - Breadcrumb: Home / Organization

5. Click on Workflows link
   - Navigate to /workflows
   - Breadcrumb: Home / Workflows

6. Click home button
   - Navigate to /
   - Breadcrumb disappears
   - Back button disabled
```

## Troubleshooting

### Back button not working
- **Cause**: Browser history is empty or context not loaded
- **Fix**: Ensure NavigationProvider wraps entire app (it does in App.tsx)

### Breadcrumb not showing
- **Cause**: On home page (breadcrumb intentionally hidden) or mobile (< 640px)
- **Fix**: Navigate to non-home page, or test on desktop

### Page title not updating
- **Cause**: New route not added to ROUTE_LABELS
- **Fix**: Add route label to NavigationContext.tsx

### Navigation buttons unresponsive
- **Cause**: CSS issue or React Router not working
- **Fix**: Check browser console for errors, verify Router.tsx configuration

## Files Modified/Created

### Created
- `src/context/NavigationContext.tsx` - Navigation context and provider
- `src/shared/components/TopNavigation.tsx` - Top navigation bar
- `src/shared/components/Breadcrumb.tsx` - Breadcrumb component

### Modified
- `src/app/App.tsx` - Added NavigationProvider and navigation components
- `src/shared/components/index.ts` - Exported new navigation components

## Related Files

- `src/app/Router.tsx` - Route definitions
- `src/pages/HomePage.tsx` - Home page
- `src/config/personaConfig.ts` - Persona configuration

## Questions or Issues?

If you encounter any issues with the navigation system:
1. Check the browser console for errors
2. Verify all new routes are added to ROUTE_LABELS
3. Ensure NavigationProvider is wrapping the entire app
4. Check that component imports are correct
