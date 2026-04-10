# `@ghatana/platform-shell`

Ghatana platform chrome — tenant selector, navigation bar, notification center, product picker, and authentication bridge.

## Overview

Provides the shared shell components that wrap all Ghatana product UIs. Drop `PlatformShell` at the app root to get full platform navigation, cross-tenant switching, and notification handling.

## Usage

```tsx
import { PlatformShell } from '@ghatana/platform-shell';

function Root() {
  return (
    <PlatformShell tenantId={tenantId} productId="yappc">
      <App />
    </PlatformShell>
  );
}
```

## API

| Export | Description |
|--------|-------------|
| `PlatformShell` | Root shell wrapper with full platform navigation and auth bridge |
| `NavBar` | Top navigation bar with product and user controls |
| `TenantSelector` | Dropdown for switching the active tenant |
| `NotificationCenter` | Notification bell with real-time notification stream |
| `ProductPicker` | Cross-product launcher menu |
| `useAuth` | Hook for auth state (`isAuthenticated`, `user`, `login`, `logout`) |

## Installation

```jsonc
// package.json
"dependencies": {
  "@ghatana/platform-shell": "workspace:*"
}
```
