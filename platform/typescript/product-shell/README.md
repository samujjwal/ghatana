# @ghatana/product-shell

Shared product shell layout primitives for Ghatana products.

Provides a configurable, registry-driven shell that both AEP and Data Cloud (and any future product) can compose instead of each implementing their own sidebar, header, global search, notification center, and boundary surfaces.

## Motivation

AEP and Data Cloud both independently implement:

- Collapsible sidebar with capability-driven navigation
- Top header with search, notifications, and mode/role selector
- Global search overlay
- Notification center
- Active operations bar
- Unsupported/boundary surface wrapper
- Runtime truth banner

`@ghatana/product-shell` extracts these shared behaviors into a single, generic, configurable package. Each product registers its route capabilities and shell configuration; the shell handles layout, discoverability, and UX.

## Components

| Component | Description |
|---|---|
| `ProductShell` | Root layout wrapper — sidebar + header + content area |
| `PageHeader` | Shared page-level title, description, and action layout primitive |
| `CapabilitySidebar` | Registry-driven collapsible sidebar |
| `ProductHeader` | Top header with search, notifications, and role/mode selector |
| `RouteCapabilityNav` | Navigation links generated from route registry entries |
| `ProductViewModeSelector` | Mode/role disclosure selector dropdown |
| `GlobalSearch` | Global search overlay |
| `NotificationCenter` | Notification panel and trigger |
| `ActiveOperationsBar` | In-progress operations indicator bar |
| `UnsupportedSurfaceBoundary` | Boundary/preview surface wrapper |
| `RuntimeTruthBanner` | Runtime environment and configuration truth indicator |

## Usage

```tsx
import {
  ProductShell,
  type ProductShellConfig,
  type ProductRouteCapability,
  type ProductRouteEntitlement,
} from '@ghatana/product-shell';

const routes: ProductRouteCapability[] = [
  { path: '/', label: 'Home', group: 'Core', lifecycle: 'stable' },
  { path: '/pipelines', label: 'Pipelines', group: 'Core', lifecycle: 'stable' },
  { path: '/operations', label: 'Operations', group: 'Manage', lifecycle: 'stable', minimumRole: 'admin' },
];

const config: ProductShellConfig = {
  productName: 'Data Cloud',
  routes,
  currentRole: 'operator',
};

function App() {
  return (
    <ProductShell config={config}>
      <Outlet />
    </ProductShell>
  );
}
```

## Backend Entitlement Contract

Products may source shell disclosure state from a backend entitlement payload instead
of hardcoding route visibility. The canonical contract is `ProductRouteEntitlement`
and includes:

- `product`, `principalId`, `tenantId`, `role`, `persona`, `tier`, `correlationId`
- `routes` as `ProductRouteCapability[]`
- `actions` for allowed route or card actions
- `cards` for allowed dashboard/detail/sidebar content surfaces

```ts
const entitlement: ProductRouteEntitlement = {
  product: 'phr',
  principalId: 'principal-123',
  tenantId: 'tenant-nepal-01',
  role: 'patient',
  persona: 'patient',
  tier: 'core',
  correlationId: 'trace-abc',
  routes,
  actions: [
    { id: 'request-emergency-review', label: 'Request emergency review', routePath: '/emergency' },
  ],
  cards: [
    { id: 'care-plan', title: 'Care plan', routePath: '/dashboard', surface: 'dashboard' },
  ],
};
```

## Product-specific configuration

Both products supply a `ProductShellConfig` with:

- `productName` — display name in the sidebar header
- `routes` — array of `ProductRouteCapability` entries
- `currentRole` — the current user/shell role string
- `roleOrder` — mapping from role string to hierarchy number (for comparison)
- `roleLabels` — display labels for each role
- `roleSelectorTitle` / `roleSelectorLabel` — ARIA and heading labels
- `roleSelectorDisclosureNote` — explanatory note for mode selector
- `onRoleChange` — callback when the user selects a different role/mode
- `onSearch` — callback to open the global search overlay
- `logo` — product logo ReactNode

Products that still need router-specific back links or breadcrumb rendering
should keep those as thin wrappers and pass the rendered content through the
shared `PageHeader` component’s `eyebrow` slot rather than rebuilding the full
heading layout locally.

## Canonical package name

`@ghatana/product-shell` — see [LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md) for the authoritative registry.
