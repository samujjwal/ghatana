import React from 'react';
import { createRoot } from 'react-dom/client';
import { flashItRouteContracts } from '@flashit/shared-contracts';
import { isRouteAllowedForRole } from './routeAccess';

const lifecycleVisibleRoutes = flashItRouteContracts.filter((route) =>
  route.discoverable !== false && isRouteAllowedForRole(route, 'member'),
);

function LifecycleReadinessApp(): React.ReactElement {
  return (
    <main aria-label="FlashIt lifecycle readiness">
      <h1>FlashIt Lifecycle Readiness</h1>
      <ul>
        {lifecycleVisibleRoutes.map((route) => (
          <li key={route.path}>{route.path}</li>
        ))}
      </ul>
    </main>
  );
}

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(<LifecycleReadinessApp />);
}
