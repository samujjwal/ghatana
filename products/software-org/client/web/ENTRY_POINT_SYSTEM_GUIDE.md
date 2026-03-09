# Entry Point System - Implementation Guide

## Overview

The Entry Point System provides comprehensive role-based access control for the Software-Org application. It defines entry points for different personas at various organizational layers, with special support for a `root_user` with unrestricted access.

## Key Components

### 1. Types (`/src/types/entrypoints.ts`)

Defines all TypeScript types for the entry point system:

- `EntryPoint`: Core entry point definition
- `AccessRule`: Access control rules
- `EntryPointCategory`: Grouping of entry points
- `OrganizationLayer`: org, department, team, individual
- `AccessMode`: role-based, permission-based, layer-based, unrestricted
- `ExtendedPersonaType`: PersonaType | 'root_user'

### 2. Configuration (`/src/config/entrypoints.config.ts`)

Central registry of all entry points:

- `ENTRY_POINTS`: All entry point definitions
- `ENTRY_POINT_CATEGORIES`: Category groupings
- `getEntryPointRegistry()`: Registry singleton accessor

### 3. Hooks

#### `usePersona()` (`/src/hooks/usePersona.ts`)

- Access current user's persona
- Check permissions
- `loginAsRootUser()`: Switch to root user (dev only)
- `isRootUser`: Boolean flag

#### `useEntryPoints()` (`/src/hooks/useEntryPoints.ts`)

- `canAccess(entryPointId)`: Check access to specific entry point
- `canAccessRoute(route)`: Check access to route
- `getEntryPointsForCurrentUser()`: Get all accessible entry points
- `getEntryPointsForLayer(layer)`: Get entry points for org layer

### 4. Components

#### `EntryPointSelector` (`/src/components/navigation/EntryPointSelector.tsx`)

Header dropdown showing all entry points:

- Organized by category (Organization, Department, Team, Individual, Admin)
- Search/filter functionality
- Shows "ROOT" badge for root_user
- Collapsible sections
- Highlights current route

#### `PersonaGuard` (`/src/app/guards/PersonaGuard.tsx`)

Route protection with entry point integration:

```tsx
<PersonaGuard
  allowedPersonas={["admin", "owner"]}
  entryPointId="admin-dashboard"
  checkEntryPointAccess={true}
>
  <AdminDashboard />
</PersonaGuard>
```

#### `EntryPointGuard` (new in PersonaGuard.tsx)

Simplified entry point-only guard:

```tsx
<EntryPointGuard entryPointId="org-security">
  <SecurityPage />
</EntryPointGuard>
```

### 5. Persona System Updates

#### New `root_user` Persona Type

- Highest authority level (5)
- Wildcard permissions (`*`)
- Bypasses all access checks
- Available in `MOCK_PERSONAS.root_user`

#### Hierarchy Levels

```typescript
{
  root: 5,          // root_user
  organization: 4,  // owner
  executive: 3,     // executive
  management: 2,    // manager
  operations: 2,    // admin
  contributor: 1,   // ic
}
```

## Entry Points Defined

### Organization Level (Owner/Root)

- `org-dashboard`: Organization Dashboard
- `org-overview`: Organization Overview
- `org-kpis`: Organization KPIs
- `org-security`: Security & Compliance
- `org-reports`: Executive Reports

### Department Level (Executive)

- `dept-dashboard`: Department Dashboard
- `dept-stages`: DevSecOps Stages
- `dept-workflows`: Workflows
- `dept-agents`: AI Agents
- `dept-ml`: ML Observatory

### Team Level (Manager)

- `team-dashboard`: Team Dashboard
- `team-queue`: Work Queue
- `team-incidents`: Incidents
- `team-simulator`: Simulator

### Individual Level (IC)

- `ic-dashboard`: My Dashboard
- `ic-queue`: My Queue
- `ic-workflows`: Workflows

### Admin Level (Admin)

- `admin-dashboard`: Admin Dashboard
- `admin-organization`: Organization Management
- `admin-security`: Security Settings
- `admin-settings`: System Settings

## Usage Examples

### 1. Login as Root User (Development)

**Option A: Via UI**

1. Click `⋯` menu in header
2. Click "🔑 Login as Root User"
3. See "ROOT USER" badge

**Option B: Programmatically**

```tsx
import { usePersona } from "@/hooks/usePersona";

function MyComponent() {
  const { loginAsRootUser } = usePersona();

  return <button onClick={loginAsRootUser}>Login as Root</button>;
}
```

**Option C: Set Directly**

```tsx
import { MOCK_PERSONAS } from "@/hooks/usePersona";
import { useAtom } from "jotai";
import { currentPersonaAtom } from "@/state/atoms/persona.atoms";

const [, setPersona] = useAtom(currentPersonaAtom);
setPersona(MOCK_PERSONAS.root_user);
```

### 2. Check Entry Point Access

```tsx
import { useEntryPoints } from "@/hooks/useEntryPoints";

function NavigationItem({ entryPointId, label }) {
  const { canAccess } = useEntryPoints();

  if (!canAccess(entryPointId)) {
    return null;
  }

  return <Link to="...">{label}</Link>;
}
```

### 3. Get User's Entry Points

```tsx
import { useEntryPoints } from "@/hooks/useEntryPoints";

function UserNavigation() {
  const { getEntryPointsForCurrentUser } = useEntryPoints();
  const entryPoints = getEntryPointsForCurrentUser();

  return (
    <nav>
      {entryPoints.map((ep) => (
        <NavLink key={ep.id} to={ep.route}>
          {ep.icon} {ep.name}
        </NavLink>
      ))}
    </nav>
  );
}
```

### 4. Protect Routes

**Option A: PersonaGuard with Entry Point**

```tsx
<PersonaGuard
  allowedPersonas={["owner", "admin"]}
  entryPointId="admin-security"
>
  <SecurityPage />
</PersonaGuard>
```

**Option B: EntryPointGuard Only**

```tsx
<EntryPointGuard entryPointId="org-overview">
  <OrganizationPage />
</EntryPointGuard>
```

**Option C: Check Route Access**

```tsx
<PersonaGuard allowedPersonas={["owner"]} checkEntryPointAccess={true}>
  <ProtectedPage />
</PersonaGuard>
```

### 5. Custom Access Rules

```tsx
// In entrypoints.config.ts
{
  id: 'custom-entry',
  name: 'Custom Entry Point',
  accessRules: {
    mode: 'permission-based',
    requiredPermissions: ['custom:view', 'custom:edit'],
    permissionMode: 'any', // require ANY permission (not all)
  },
  // ... other fields
}
```

### 6. Layer-Based Access

```tsx
// In entrypoints.config.ts
{
  id: 'leadership-only',
  name: 'Leadership Dashboard',
  accessRules: {
    mode: 'layer-based',
    allowedLayers: ['root', 'organization', 'executive'],
  },
  // ... other fields
}
```

## Root User Features

### What Root User Can Do

✅ Access all entry points regardless of rules  
✅ Bypass all permission checks  
✅ See all entry points in EntryPointSelector  
✅ Navigate to any route  
✅ Manage all resources

### Root User Permissions

```typescript
[
  "entrypoint:root:access",
  "entrypoint:bypass:all",
  "org:restructure",
  "department:restructure",
  "team:restructure",
  "users:manage",
  "permissions:manage",
  "audit:view",
  "system:manage",
  "*", // Wildcard - grants all permissions
];
```

### Root User Detection

```tsx
const { isRootUser } = usePersona();

if (isRootUser) {
  // Show admin features
}

// Or via context
const { accessContext, isRootUser } = useEntryPoints();
```

## Adding New Entry Points

### Step 1: Define Entry Point

Edit `/src/config/entrypoints.config.ts`:

```typescript
export const ENTRY_POINTS: Record<string, EntryPoint> = {
  // ... existing entry points

  "my-new-feature": {
    id: "my-new-feature",
    name: "My New Feature",
    description: "Description of the feature",
    route: "/my/new/feature",
    icon: "🎯",
    organizationLayer: "team",
    targetPersonas: ["manager", "ic"],
    accessRules: {
      mode: "role-based",
      allowedPersonas: ["manager", "ic", "owner"],
    },
    scope: "team",
    order: 10,
    isPrimary: false,
  },
};
```

### Step 2: Add to Category

```typescript
export const ENTRY_POINT_CATEGORIES: Record<string, EntryPointCategory> = {
  team: {
    // ... existing config
    entryPointIds: [
      "team-dashboard",
      "team-queue",
      "my-new-feature", // Add here
    ],
  },
};
```

### Step 3: Protect Route

```tsx
// In your route component
<EntryPointGuard entryPointId="my-new-feature">
  <MyNewFeature />
</EntryPointGuard>
```

### Step 4: Test Access

```tsx
const { canAccess } = useEntryPoints();
console.log("Can access:", canAccess("my-new-feature"));
```

## Testing

### Test Different Personas

```tsx
import { MOCK_PERSONAS } from "@/hooks/usePersona";
import { currentPersonaAtom } from "@/state/atoms/persona.atoms";
import { useSetAtom } from "jotai";

function PersonaSwitcher() {
  const setPersona = useSetAtom(currentPersonaAtom);

  return (
    <div>
      <button onClick={() => setPersona(MOCK_PERSONAS.owner)}>Owner</button>
      <button onClick={() => setPersona(MOCK_PERSONAS.executive)}>
        Executive
      </button>
      <button onClick={() => setPersona(MOCK_PERSONAS.manager)}>Manager</button>
      <button onClick={() => setPersona(MOCK_PERSONAS.ic)}>IC</button>
      <button onClick={() => setPersona(MOCK_PERSONAS.admin)}>Admin</button>
      <button onClick={() => setPersona(MOCK_PERSONAS.root_user)}>
        Root User
      </button>
    </div>
  );
}
```

### Verify Entry Points

```tsx
import { getEntryPointRegistry } from "@/config/entrypoints.config";

const registry = getEntryPointRegistry();

// Get all entry points
const all = registry.getAllEntryPoints();
console.log("Total entry points:", all.length);

// Get by persona
const ownerEps = registry.getEntryPointsForPersona("owner");
console.log("Owner entry points:", ownerEps);

// Get by layer
const orgEps = registry.getEntryPointsForLayer("organization");
console.log("Org-level entry points:", orgEps);
```

## Best Practices

1. **Always use entry point IDs for critical routes**

   ```tsx
   <EntryPointGuard entryPointId="admin-security">
   ```

2. **Root user for development only**
   - Never deploy root user credentials to production
   - Use environment checks: `import.meta.env.DEV`

3. **Consistent naming**
   - Pattern: `{layer}-{feature}` (e.g., `team-dashboard`, `org-security`)

4. **Group related entry points**
   - Use categories for organization
   - Keep order consistent within categories

5. **Test all personas**
   - Verify access for each persona type
   - Ensure root_user always has access
   - Check that restricted users are blocked

6. **Document custom access rules**
   - Comment complex permission logic
   - Explain layer-based restrictions

## Troubleshooting

### Entry Point Not Showing

1. Check `targetPersonas` includes current persona
2. Verify `accessRules` allow current persona
3. Check if entry point is in a category
4. Ensure route exists in routes.ts

### Access Denied Error

1. Check persona type matches `allowedPersonas`
2. Verify required permissions are granted
3. Try with root_user to confirm entry point works
4. Check console for detailed access check results

### Root User Not Working

1. Verify persona type is 'root_user'
2. Check permissions include '\*' or 'entrypoint:root:access'
3. Ensure `isRootUser` flag is true
4. Check `ROOT_USER_PERSONA` is properly defined

## Migration from Old System

### Before (Direct Route Protection)

```tsx
<ProtectedRoute isAuthenticated={isOwner}>
  <OwnerPage />
</ProtectedRoute>
```

### After (Entry Point System)

```tsx
<EntryPointGuard entryPointId="org-dashboard">
  <OwnerPage />
</EntryPointGuard>
```

## Files Modified

- ✅ `/src/types/entrypoints.ts` - Type definitions
- ✅ `/src/config/entrypoints.config.ts` - Entry point registry
- ✅ `/src/state/atoms/persona.atoms.ts` - Added root_user support
- ✅ `/src/hooks/usePersona.ts` - Added root user functions
- ✅ `/src/hooks/useEntryPoints.ts` - Entry point access hook
- ✅ `/src/components/navigation/EntryPointSelector.tsx` - UI component
- ✅ `/src/app/Layout.tsx` - Integrated EntryPointSelector
- ✅ `/src/app/guards/PersonaGuard.tsx` - Added entry point checks

## Summary

The Entry Point System provides:

- ✅ Centralized access control
- ✅ Persona-based navigation
- ✅ Root user with unrestricted access
- ✅ Layer-based organization
- ✅ Flexible access rules (role, permission, layer, custom)
- ✅ Easy to extend and maintain
- ✅ Type-safe implementation
- ✅ UI components included
