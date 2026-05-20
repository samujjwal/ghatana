/**
 * Tests for @ghatana/product-shell
 *
 * Covers:
 * - RouteCapabilityNav role filtering
 * - RouteCapabilityNav lifecycle filtering (boundary excluded)
 * - RouteCapabilityNav lifecycle filtering (deprecated excluded)
 * - RouteCapabilityNav discoverable filtering
 * - RouteCapabilityNav group rendering
 * - UnsupportedSurfaceBoundary lifecycle='boundary' renders notice
 * - UnsupportedSurfaceBoundary lifecycle='preview' renders banner + content
 * - ProductViewModeSelector renders current role label
 * - ActiveOperationsBar only renders when count > 0
 *
 * @doc.type test
 * @doc.purpose Verify product shell component behavior
 * @doc.layer platform
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, renderHook, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { RouteCapabilityNav } from '../components/RouteCapabilityNav';
import { UnsupportedSurfaceBoundary } from '../components/UnsupportedSurfaceBoundary';
import { ProductViewModeSelector } from '../components/ProductViewModeSelector';
import { ActiveOperationsBar } from '../components/ActiveOperationsBar';
import { ProductShell } from '../components/ProductShell';
import {
  ProductHeaderUserMenu,
  ProductShellFooter,
  createProductRoleSelectorConfig,
} from '../components/ProductShellChrome';
import {
  createRouteAccessEvaluator,
  filterDiscoverableRoutes,
  hasMinimumRole,
  hydrateRoutesFromEntitlement,
  isRouteAllowed,
  resolveHighestRole,
} from '../access';
import type { ProductRouteCapability, ProductShellConfig, UnsupportedSurfaceConfig } from '../types';
import { useProductEntitlements } from '../useProductEntitlements';

// ---------------------------------------------------------------------------
// RouteCapabilityNav
// ---------------------------------------------------------------------------

const ROLE_ORDER: Record<string, number> = {
  'primary-user': 0,
  operator: 1,
  admin: 2,
};

const routes: ProductRouteCapability[] = [
  { path: '/', label: 'Home', group: 'Core', lifecycle: 'stable', discoverable: true },
  { path: '/data', label: 'Data', group: 'Core', lifecycle: 'stable', discoverable: true },
  {
    path: '/operations',
    label: 'Operations',
    group: 'Manage',
    minimumRole: 'admin',
    lifecycle: 'stable',
    discoverable: true,
  },
  { path: '/reports', label: 'Reports', group: 'Core', lifecycle: 'boundary', discoverable: true },
  { path: '/legacy', label: 'Legacy', group: 'Core', lifecycle: 'deprecated', discoverable: true },
  { path: '/beta', label: 'Beta Feature', group: 'Core', lifecycle: 'stable', discoverable: false },
  {
    path: '/trust',
    label: 'Trust',
    group: 'Observability',
    minimumRole: 'operator',
    lifecycle: 'stable',
    discoverable: true,
  },
  {
    path: '/preview-thing',
    label: 'Preview Thing',
    group: 'Core',
    lifecycle: 'preview',
    discoverable: true,
  },
];

describe('RouteCapabilityNav', () => {
  it('renders routes accessible to the current role', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'operator', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.getByText('Data')).toBeInTheDocument();
    expect(screen.getByText('Trust')).toBeInTheDocument();
  });

  it('excludes routes below minimumRole', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'operator', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    // operator (1) < admin (2) — Operations should be hidden
    expect(screen.queryByText('Operations')).not.toBeInTheDocument();
  });

  it('shows admin routes when role is admin', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'admin', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Operations')).toBeInTheDocument();
  });

  it('excludes boundary routes', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'admin', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.queryByText('Reports')).not.toBeInTheDocument();
  });

  it('excludes deprecated routes', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'admin', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.queryByText('Legacy')).not.toBeInTheDocument();
  });

  it('excludes routes with discoverable=false', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'admin', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.queryByText('Beta Feature')).not.toBeInTheDocument();
  });

  it('renders preview badge for preview routes', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'operator', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Preview')).toBeInTheDocument();
  });

  it('renders group headings', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'operator', roleOrder: ROLE_ORDER }}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Core')).toBeInTheDocument();
    expect(screen.getByText('Observability')).toBeInTheDocument();
  });

  it('hides labels in collapsed mode', () => {
    render(
      <MemoryRouter>
        <RouteCapabilityNav
          routes={routes}
          config={{ currentRole: 'operator', roleOrder: ROLE_ORDER }}
          collapsed
        />
      </MemoryRouter>
    );

    // Labels are rendered but visually hidden; group headings are hidden
    expect(screen.queryByText('Core')).not.toBeInTheDocument();
    expect(screen.queryByText('Observability')).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// UnsupportedSurfaceBoundary
// ---------------------------------------------------------------------------

const surfaceConfig: UnsupportedSurfaceConfig = {
  title: 'Reports unavailable',
  reason: 'This surface is under construction.',
  guidance: 'Use the Data page instead.',
};

describe('UnsupportedSurfaceBoundary', () => {
  it('renders boundary notice and blocks content when lifecycle=boundary', () => {
    render(
      <UnsupportedSurfaceBoundary lifecycle="boundary" surface={surfaceConfig}>
        <span>should-not-render</span>
      </UnsupportedSurfaceBoundary>
    );

    expect(screen.getByText('Reports unavailable')).toBeInTheDocument();
    expect(screen.queryByText('should-not-render')).not.toBeInTheDocument();
  });

  it('renders preview banner and content when lifecycle=preview', () => {
    render(
      <UnsupportedSurfaceBoundary lifecycle="preview" surface={surfaceConfig}>
        <span>page-content</span>
      </UnsupportedSurfaceBoundary>
    );

    expect(screen.getByText('page-content')).toBeInTheDocument();
    expect(screen.getByText(/Reports unavailable/)).toBeInTheDocument();
  });

  it('dismisses the preview banner when X is clicked', () => {
    render(
      <UnsupportedSurfaceBoundary lifecycle="preview" surface={surfaceConfig}>
        <span>page-content</span>
      </UnsupportedSurfaceBoundary>
    );

    const dismissButton = screen.getByLabelText('Dismiss preview notice');
    fireEvent.click(dismissButton);

    expect(screen.queryByText(/Reports unavailable/)).not.toBeInTheDocument();
    // Content is still visible
    expect(screen.getByText('page-content')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// ProductViewModeSelector
// ---------------------------------------------------------------------------

const selectorConfig: Pick<
  ProductShellConfig,
  | 'currentRole'
  | 'availableRoles'
  | 'roleLabels'
  | 'roleDescriptions'
  | 'roleSelectorTitle'
  | 'roleSelectorLabel'
  | 'roleSelectorDisclosureNote'
  | 'onRoleChange'
> = {
  currentRole: 'operator',
  availableRoles: ['primary-user', 'operator', 'admin'],
  roleLabels: {
    'primary-user': 'Standard view',
    operator: 'Operator view',
    admin: 'Admin view',
  },
  roleDescriptions: {
    'primary-user': 'Show standard surfaces.',
    operator: 'Show operator surfaces.',
    admin: 'Show admin surfaces.',
  },
  roleSelectorTitle: 'View mode',
  roleSelectorLabel: 'View mode menu',
  roleSelectorDisclosureNote: 'This is a UI disclosure control only.',
  onRoleChange: vi.fn(),
};

describe('ProductViewModeSelector', () => {
  it('renders the current role label', () => {
    render(<ProductViewModeSelector config={selectorConfig} />);
    expect(screen.getByText('Operator view')).toBeInTheDocument();
  });

  it('opens dropdown on click', () => {
    render(<ProductViewModeSelector config={selectorConfig} />);
    const button = screen.getByLabelText('View mode menu');
    fireEvent.click(button);
    expect(screen.getByText('View mode')).toBeInTheDocument();
    expect(screen.getByText('Standard view')).toBeInTheDocument();
    expect(screen.getByText('Admin view')).toBeInTheDocument();
  });

  it('calls onRoleChange when a role is selected', () => {
    const onRoleChange = vi.fn();
    render(<ProductViewModeSelector config={{ ...selectorConfig, onRoleChange }} />);
    const button = screen.getByLabelText('View mode menu');
    fireEvent.click(button);
    fireEvent.click(screen.getByText('Admin view'));
    expect(onRoleChange).toHaveBeenCalledWith('admin');
  });

  it('renders disclosure note in the dropdown', () => {
    render(<ProductViewModeSelector config={selectorConfig} />);
    fireEvent.click(screen.getByLabelText('View mode menu'));
    expect(screen.getByText('This is a UI disclosure control only.')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Product shell chrome
// ---------------------------------------------------------------------------

describe('ProductShellChrome', () => {
  it('renders the shared shell footer with default eyebrow copy', () => {
    render(<ProductShellFooter description="Shared route disclosure." />);

    expect(screen.getByText('Kernel shell')).toBeInTheDocument();
    expect(screen.getByText('Shared route disclosure.')).toBeInTheDocument();
  });

  it('renders a user display label and logout action', () => {
    const onLogout = vi.fn();
    render(<ProductHeaderUserMenu displayName="Ada Lovelace" onLogout={onLogout} />);

    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));

    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('falls back to email and signed-in labels for user display', () => {
    const { rerender } = render(<ProductHeaderUserMenu email="user@example.com" />);

    expect(screen.getByText('user@example.com')).toBeInTheDocument();

    rerender(<ProductHeaderUserMenu />);
    expect(screen.getByText('Signed in')).toBeInTheDocument();
  });

  it('returns role selector config without forcing product wrappers to build it manually', () => {
    const config = createProductRoleSelectorConfig({
      roleOrder: ROLE_ORDER,
      roleLabels: selectorConfig.roleLabels,
      roleDescriptions: selectorConfig.roleDescriptions,
      availableRoles: selectorConfig.availableRoles,
      roleSelectorTitle: selectorConfig.roleSelectorTitle,
      roleSelectorLabel: selectorConfig.roleSelectorLabel,
      roleSelectorDisclosureNote: selectorConfig.roleSelectorDisclosureNote,
      onRoleChange: selectorConfig.onRoleChange,
    });

    expect(config.roleLabels?.admin).toBe('Admin view');
    expect(config.availableRoles).toEqual(['primary-user', 'operator', 'admin']);
  });
});

// ---------------------------------------------------------------------------
// ActiveOperationsBar
// ---------------------------------------------------------------------------

describe('ActiveOperationsBar', () => {
  it('does not render when count is 0', () => {
    const { container } = render(<ActiveOperationsBar count={0} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders when count > 0', () => {
    render(<ActiveOperationsBar count={3} />);
    expect(screen.getByText(/3 operations in progress/)).toBeInTheDocument();
  });

  it('renders singular text when count is 1', () => {
    render(<ActiveOperationsBar count={1} />);
    expect(screen.getByText(/1 operation in progress/)).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<ActiveOperationsBar count={2} onClick={onClick} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalled();
  });
});

describe('ProductShell', () => {
  it('renders without a router provider when children are supplied', () => {
    const config: ProductShellConfig = {
      productName: 'Test Product',
      currentRole: 'operator',
      roleOrder: ROLE_ORDER,
      routes: [],
    };

    render(
      <ProductShell config={config}>
        <span>router-free-content</span>
      </ProductShell>,
    );

    expect(screen.getByText('router-free-content')).toBeInTheDocument();
  });

  it('keeps product content stable when sidebar state changes', () => {
    const onRender = vi.fn();
    const child = <RenderProbe label="stable-content" onRender={onRender} />;
    const config = createShellPerformanceConfig();

    render(
      <MemoryRouter>
        <ProductShell config={config}>{child}</ProductShell>
      </MemoryRouter>,
    );

    expect(onRender).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Collapse sidebar' }));

    expect(screen.getByRole('button', { name: 'Expand sidebar' })).toBeInTheDocument();
    expect(onRender).toHaveBeenCalledTimes(1);
  });

  it('keeps product content stable while route, role, and notification shell inputs update', () => {
    const onRender = vi.fn();
    const child = <RenderProbe label="stable-content" onRender={onRender} />;
    const operatorConfig = createShellPerformanceConfig();

    const { rerender } = render(
      <MemoryRouter>
        <ProductShell config={operatorConfig}>{child}</ProductShell>
      </MemoryRouter>,
    );

    expect(onRender).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('Admin')).not.toBeInTheDocument();

    const adminConfig = createShellPerformanceConfig({ currentRole: 'admin' });
    rerender(
      <MemoryRouter>
        <ProductShell config={adminConfig}>{child}</ProductShell>
      </MemoryRouter>,
    );

    expect(screen.getByText('Admin')).toBeInTheDocument();
    expect(onRender).toHaveBeenCalledTimes(1);

    const notifiedConfig = createShellPerformanceConfig({
      currentRole: 'admin',
      notifications: [
        {
          id: 'job-complete',
          title: 'Import complete',
          level: 'success',
          timestamp: 'now',
          read: false,
        },
      ],
    });
    rerender(
      <MemoryRouter>
        <ProductShell config={notifiedConfig}>{child}</ProductShell>
      </MemoryRouter>,
    );

    expect(screen.getByLabelText('Notifications, 1 unread')).toBeInTheDocument();
    expect(onRender).toHaveBeenCalledTimes(1);

    const routeUpdatedConfig = createShellPerformanceConfig({
      currentRole: 'admin',
      routes: [
        ...shellPerformanceRoutes,
        { path: '/reports', label: 'Reports', lifecycle: 'stable', discoverable: true },
      ],
    });
    rerender(
      <MemoryRouter>
        <ProductShell config={routeUpdatedConfig}>{child}</ProductShell>
      </MemoryRouter>,
    );

    expect(screen.getByText('Reports')).toBeInTheDocument();
    expect(onRender).toHaveBeenCalledTimes(1);
  });
});

const shellPerformanceRoutes: ProductRouteCapability[] = [
  { path: '/', label: 'Home', lifecycle: 'stable', discoverable: true },
  { path: '/admin', label: 'Admin', minimumRole: 'admin', lifecycle: 'stable', discoverable: true },
];

function createShellPerformanceConfig(
  overrides: Partial<ProductShellConfig> = {},
): ProductShellConfig {
  return {
    productName: 'Performance Shell',
    currentRole: 'operator',
    roleOrder: ROLE_ORDER,
    routes: shellPerformanceRoutes,
    ...overrides,
  };
}

interface RenderProbeProps {
  readonly label: string;
  readonly onRender: () => void;
}

const RenderProbe = React.memo(function RenderProbe({
  label,
  onRender,
}: RenderProbeProps): React.ReactElement {
  onRender();
  return <span>{label}</span>;
});

describe('shared route access helpers', () => {
  it('resolves the highest known role', () => {
    expect(resolveHighestRole(['primary-user', 'admin', 'unknown'], ROLE_ORDER, 'primary-user')).toBe('admin');
  });

  it('creates a reusable route access evaluator', () => {
    const evaluator = createRouteAccessEvaluator(ROLE_ORDER);

    expect(evaluator.resolveHighestRole(['primary-user', 'operator'], 'primary-user')).toBe('operator');
    expect(evaluator.hasMinimumRole(['operator'], 'operator', 'primary-user')).toBe(true);
    expect(evaluator.isRouteAllowed({ minimumRole: 'admin' }, 'operator')).toBe(false);
    expect(evaluator.filterDiscoverableRoutes(routes, 'operator').map((route) => route.path)).toEqual([
      '/',
      '/data',
      '/trust',
      '/preview-thing',
    ]);
  });

  it('checks minimum role from a role list with a fallback', () => {
    expect(hasMinimumRole(['unknown'], 'operator', ROLE_ORDER, 'primary-user')).toBe(false);
    expect(hasMinimumRole(['admin'], 'operator', ROLE_ORDER, 'primary-user')).toBe(true);
  });

  it('denies unknown roles and unknown minimum roles', () => {
    expect(isRouteAllowed({ minimumRole: 'admin' }, 'unknown', ROLE_ORDER)).toBe(false);
    expect(isRouteAllowed({ minimumRole: 'owner' }, 'admin', ROLE_ORDER)).toBe(false);
  });

  it('filters hidden, boundary, and role-denied routes', () => {
    expect(filterDiscoverableRoutes(routes, 'operator', ROLE_ORDER).map((route) => route.path)).toEqual([
      '/',
      '/data',
      '/trust',
      '/preview-thing',
    ]);
  });

  it('hydrates route metadata from backend entitlements and hides missing routes', () => {
    const hydrated = hydrateRoutesFromEntitlement(routes, {
      product: 'test',
      principalId: 'principal',
      tenantId: 'tenant',
      role: 'operator',
      routes: [{ path: '/data', label: 'Server Data', actions: ['read'] }],
    });

    expect(hydrated.find((route) => route.path === '/data')?.label).toBe('Server Data');
    expect(hydrated.find((route) => route.path === '/')?.discoverable).toBe(false);
  });
});

describe('useProductEntitlements', () => {
  const fallbackRoutes: ProductRouteCapability[] = [
    { path: '/', label: 'Home', lifecycle: 'stable' },
    { path: '/admin', label: 'Admin', minimumRole: 'admin', lifecycle: 'stable' },
  ];

  it('validates backend entitlements before hydrating routes', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          product: 'test',
          principalId: 'principal-1',
          tenantId: 'tenant-1',
          role: 'admin',
          routes: [{ path: '/', label: 'Server Home', actions: ['read'] }],
          actions: [{ id: 'read', label: 'Read', routePath: '/' }],
          cards: [{ id: 'summary', title: 'Summary', routePath: '/', surface: 'dashboard' }],
        }),
        { status: 200 },
      ),
    );

    const { result } = renderHook(() =>
      useProductEntitlements({
        endpoint: '/route-entitlements?case=valid',
        fallbackRoutes,
        fetcher,
      }),
    );

    await waitFor(() => expect(result.current.status).toBe('ready'));

    expect(result.current.entitlement?.actions?.[0]?.id).toBe('read');
    expect(result.current.routes.find((route) => route.path === '/')?.label).toBe('Server Home');
    expect(result.current.routes.find((route) => route.path === '/admin')?.discoverable).toBe(false);
  });

  it('fails closed when entitlement payload shape is invalid', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          product: 'test',
          principalId: 'principal-1',
          tenantId: 'tenant-1',
          role: 'admin',
          routes: [{ path: '/', label: '' }],
        }),
        { status: 200 },
      ),
    );

    const { result } = renderHook(() =>
      useProductEntitlements({
        endpoint: '/route-entitlements?case=invalid',
        fallbackRoutes,
        fetcher,
      }),
    );

    await waitFor(() => expect(result.current.status).toBe('error'));

    expect(result.current.error?.message).toContain('must include a label');
    expect(result.current.routes.every((route) => route.discoverable === false)).toBe(true);
  });

  it('does not expose fallback routes when backend denies entitlements', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 403 }));

    const { result } = renderHook(() =>
      useProductEntitlements({
        endpoint: '/route-entitlements?case=denied',
        fallbackRoutes,
        fetcher,
      }),
    );

    await waitFor(() => expect(result.current.status).toBe('denied'));

    expect(result.current.routes.every((route) => route.discoverable === false)).toBe(true);
    expect(result.current.entitlement).toBeNull();
  });

  it('clears the cache when refreshed', async () => {
    const firstResponse = new Response(
      JSON.stringify({
        product: 'test',
        principalId: 'principal-1',
        tenantId: 'tenant-1',
        role: 'admin',
        routes: [{ path: '/', label: 'First' }],
      }),
      { status: 200 },
    );
    const secondResponse = new Response(
      JSON.stringify({
        product: 'test',
        principalId: 'principal-1',
        tenantId: 'tenant-1',
        role: 'admin',
        routes: [{ path: '/', label: 'Second' }],
      }),
      { status: 200 },
    );
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(firstResponse)
      .mockResolvedValueOnce(secondResponse);

    const { result } = renderHook(() =>
      useProductEntitlements({
        endpoint: '/route-entitlements?case=refresh',
        fallbackRoutes,
        fetcher,
      }),
    );

    await waitFor(() => expect(result.current.routes[0]?.label).toBe('First'));

    act(() => result.current.refresh());

    await waitFor(() => expect(result.current.routes[0]?.label).toBe('Second'));

    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
