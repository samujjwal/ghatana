# Navigation Implementation Guide

**For:** Developers implementing pages and navigation in the software-org web app  
**Last Updated:** November 22, 2025

---

## Quick Start: Navigation Structure

The app follows a **3-tier navigation model** based on the web page specs:

1. **Primary Routes** (8 items in sidebar)
   - Always visible, stable navigation
   - Main user workflows

2. **Secondary Routes** (7 items in "More" section)
   - Context-sensitive, collapsible menu
   - Accessible via header icons

3. **Detail Routes** (dynamic parameters)
   - Individual item views (e.g., `/departments/:id`)

---

## Primary Routes (Sidebar - Always Visible)

These routes are the main entry points and should always be visible in the sidebar:

```
📊 Dashboard              → /                   (Control Tower)
🏢 Departments            → /departments         (Teams Directory)
🔄 Workflows              → /workflows           (Workflow Explorer)
✋ HITL Console           → /hitl                (Human-In-The-Loop)
⚡ Event Simulator       → /simulator           (Test Event Creation)
📈 Reports               → /reports             (Analytics Dashboard)
🤖 AI Intelligence       → /ai                  (AI Insights)
🔒 Security              → /security            (Security & Compliance)
```

### How to Add a Primary Route

**Step 1:** Create the component

```bash
mkdir -p src/features/my-feature
touch src/features/my-feature/MyFeature.tsx
```

**Step 2:** Create component with proper header

```tsx
// src/features/my-feature/MyFeature.tsx

/**
 * MyFeature Page
 *
 * <p><b>Purpose</b><br>
 * Clear description of what this page does
 *
 * @doc.type component
 * @doc.purpose Page component
 * @doc.layer product
 * @doc.pattern Page
 */
export function MyFeature() {
    return (
        <div>
            <h1>My Feature</h1>
            <p>One-line description of what users can do here.</p>
            {/* Page content */}
        </div>
    );
}

export default MyFeature;
```

**Step 3:** Add to route config

Update `src/lib/routes.config.ts`:

```typescript
myFeature: {
    path: "/my-feature",
    label: "My Feature",
    icon: "🎯",
    category: "primary",
    description: "My Feature - Clear description",
    componentPath: "src/features/my-feature/MyFeature.tsx",
    specFile: "XX_my_feature.md",
},
```

**Step 4:** Add to App.tsx

```tsx
import MyFeature from "./features/my-feature/MyFeature.tsx";

<Route path="/my-feature" element={<MyFeature />} />
```

**Step 5:** The sidebar will auto-update!

Since `Layout.tsx` iterates over `getPrimaryRoutes()`, your new route automatically appears in the sidebar when it's marked as `category: "primary"`.

---

## Secondary Routes (Header Menu - "More" Button)

These routes are less frequently accessed and grouped under a "More" menu in the header:

```
⏱️ Real-Time Monitor      → /realtime-monitor
⚙️ Automation Engine      → /automation-engine
📦 Model Catalog          → /models
🔬 ML Observatory         → /ml-observatory
⚙️ Settings               → /settings
❓ Help Center            → /help
📤 Data Export            → /export
```

### How to Add a Secondary Route

**Steps 1-4 are identical to Primary Routes, except:**

**Step 3 (Modified):** Set `category: "secondary"`

```typescript
myFeature: {
    path: "/my-secondary-feature",
    label: "My Secondary Feature",
    icon: "🎯",
    category: "secondary",  // ← Change this
    description: "...",
    componentPath: "...",
    specFile: "...",
},
```

**Step 5:** Route automatically appears in the header "More" menu

Since `HeaderContent` checks `getSecondaryRoutes()`, your new secondary route automatically appears in the dropdown menu.

---

## Detail Routes (Dynamic Parameters)

For routes that reference individual items (e.g., viewing a specific department):

```
/departments/:id  →  Department detail page for department with ID
```

### How to Add a Detail Route

**Step 1:** Create the detail component

```bash
touch src/features/departments/DepartmentDetail.tsx
```

**Step 2:** Create component with ID handling

```tsx
// src/features/departments/DepartmentDetail.tsx
import { useParams } from "react-router-dom";

export function DepartmentDetail() {
    const { id } = useParams<{ id: string }>();

    return (
        <div>
            <h1>Department {id}</h1>
            {/* Load and display department data */}
        </div>
    );
}

export default DepartmentDetail;
```

**Step 3:** Add to route config as "detail" category

```typescript
departmentDetail: {
    path: "/departments/:id",
    label: "Department Detail",
    icon: "🏢",
    category: "detail",  // ← Important
    description: "...",
    componentPath: "...",
    specFile: "03_department_detail.md",
},
```

**Step 4:** Add to App.tsx

```tsx
<Route path="/departments/:id" element={<DepartmentDetail />} />
```

**Step 5:** Link from the list page

```tsx
// In DepartmentList.tsx
import { Link } from "react-router-dom";

<Link to={`/departments/${department.id}`}>
    {department.name}
</Link>
```

---

## Navigation Between Pages

### Linking to Routes

Use `<Link>` from `react-router-dom`:

```tsx
import { Link } from "react-router-dom";

<Link to="/">Dashboard</Link>
<Link to="/departments">All Departments</Link>
<Link to={`/departments/${id}`}>Department Detail</Link>
```

### Programmatic Navigation

Use `useNavigate` hook:

```tsx
import { useNavigate } from "react-router-dom";

function MyComponent() {
    const navigate = useNavigate();

    const handleClick = () => {
        navigate("/reports");
    };

    return <button onClick={handleClick}>Go to Reports</button>;
}
```

---

## Page Header Pattern (REQUIRED)

Every page must follow the same header pattern as defined in the specs:

```tsx
// ✅ CORRECT - Follows spec pattern
<div className="mb-8">
    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
        Page Title
    </h1>
    <p className="text-gray-600 dark:text-gray-400 mt-2">
        One-line, plain-language description of what this page does.
    </p>

    {/* Optional CTAs */}
    <div className="flex gap-3 mt-4">
        <button className="btn btn-primary">Primary Action</button>
        <button className="btn btn-secondary">Secondary Action</button>
    </div>
</div>

{/* Page content */}
```

### Page Header Examples

**Dashboard:**
```
Control Tower
Organization-wide metrics and AI insights.
```

**Departments:**
```
Departments Directory
Overview of all teams, their automation level, and agent activity.
```

**Reports:**
```
Reporting Dashboard
Detailed analytics, trends, and historical data export.
```

---

## Using the Routes Configuration

The `routes.config.ts` file provides helper functions:

```tsx
import {
    ROUTES,
    getPrimaryRoutes,
    getSecondaryRoutes,
    getDetailRoutes,
    getRouteByPath,
} from "@/lib/routes.config";

// Get all primary routes for sidebar
const primaryRoutes = getPrimaryRoutes();
primaryRoutes.forEach((route) => {
    console.log(`${route.icon} ${route.label} → ${route.path}`);
});

// Get specific route
const dashboardRoute = getRouteByPath("/");
console.log(dashboardRoute.description);

// Build breadcrumb
const route = getRouteByPath(location.pathname);
if (route) {
    console.log(`Current page: ${route.label}`);
}
```

---

## Sidebar Navigation Implementation

The sidebar in `Layout.tsx` automatically renders navigation based on routes:

```tsx
// Renders all primary routes
{getPrimaryRoutes().map((route) => (
    <NavLinkItem
        key={route.path}
        to={route.path}
        icon={route.icon}
        label={route.label}
        collapsed={sidebarCollapsed}
    />
))}

// Renders all secondary routes (in "More" menu)
{getSecondaryRoutes().map((route) => (
    <NavLinkItem
        key={route.path}
        to={route.path}
        icon={route.icon}
        label={route.label}
        collapsed={sidebarCollapsed}
    />
))}
```

**Benefits:**
- ✅ No need to manually update sidebar
- ✅ Add route to config → it appears automatically
- ✅ Single source of truth for all routes
- ✅ Easy to reorder or reorganize

---

## Contextual Navigation (CTAs Between Pages)

Connect related pages with links and buttons:

```tsx
// From AI Intelligence page
<Link to="/models" className="btn btn-secondary">
    View Model Catalog →
</Link>

<Link to="/ml-observatory" className="btn btn-secondary">
    ML Observatory →
</Link>

// From Departments page
<Link to={`/departments/${id}`} className="btn btn-primary">
    View Department
</Link>

// From Reports page
<Link to="/export" className="btn btn-secondary">
    Export Data
</Link>
```

---

## Current Implementation Status

### ✅ Implemented
- Dashboard (`/`)
- Global Layout with collapsible sidebar
- Theme, tenant, environment selectors
- Header with quick-access icons

### ❌ Not Yet Implemented (Use This Guide!)
- Departments (`/departments`)
- Departments Detail (`/departments/:id`)
- Workflows (`/workflows`)
- HITL Console (`/hitl`)
- Event Simulator (`/simulator`)
- Reports (`/reports`)
- Security (`/security`)
- AI Intelligence (`/ai`)
- Real-Time Monitor (`/realtime-monitor`)
- Automation Engine (`/automation-engine`)
- Model Catalog (`/models`)
- ML Observatory (`/ml-observatory`)
- Settings (`/settings`)
- Help Center (`/help`)
- Data Export (`/export`)

**Next Steps:** Pick a route, follow the guide above, and implement it!

---

## Mobile Navigation

On mobile, the sidebar collapses automatically. Secondary routes are always accessible via the "More" menu in the header.

```tsx
// Mobile-friendly navigation
<div className="relative">
    <button>⋯ More</button>
    {showMenu && (
        <dropdown>
            {getSecondaryRoutes().map((route) => (
                <Link key={route.path} to={route.path}>
                    {route.icon} {route.label}
                </Link>
            ))}
        </dropdown>
    )}
</div>
```

---

## Testing Navigation

When you add a new route, test:

1. **Link works:** Click the link, verify page loads
2. **Active state:** NavLink highlights when on that page
3. **Mobile:** Sidebar collapses, menu still works
4. **Parameters:** For detail routes, verify `:id` is correctly passed
5. **Header:** Tenant/environment/theme context persists across navigation

---

## Troubleshooting

### Route not appearing in sidebar?
- ✅ Check `category: "primary"` in `routes.config.ts`
- ✅ Check component imported in `App.tsx`
- ✅ Check `<Route>` added in `App.tsx`

### Secondary route not in "More" menu?
- ✅ Check `category: "secondary"` in `routes.config.ts`
- ✅ Verify `getSecondaryRoutes()` is called in header

### Link doesn't navigate?
- ✅ Check path matches exactly: `to="/path"` must match `path: "/path"`
- ✅ Use `<Link>` not `<a>` for client-side routing
- ✅ Use `useNavigate()` for programmatic navigation

### Detail route not rendering?
- ✅ Check `useParams()` hook imports `useParams` from `react-router-dom`
- ✅ Verify parameter name matches: `/:id` → `useParams<{ id: string }>()`

---

## Summary

| Task | Do This |
|------|---------|
| Add a primary route | Update `routes.config.ts` (category: "primary"), create component, add to `App.tsx` |
| Add a secondary route | Update `routes.config.ts` (category: "secondary"), create component, add to `App.tsx` |
| Add a detail route | Update `routes.config.ts` (category: "detail"), create component, add to `App.tsx`, add links from list page |
| Link to a page | Use `<Link to="/path">Label</Link>` |
| Navigate programmatically | Use `useNavigate()` hook |
| Get all routes | Import `getPrimaryRoutes()`, `getSecondaryRoutes()`, or use `ROUTES` object |

**Remember:** Follow the page spec for each page to ensure consistent UX!
