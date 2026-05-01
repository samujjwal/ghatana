import React from 'react';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

type GateOverrides = {
  alertsEnabled?: boolean;
  fabricEnabled?: boolean;
  memoryEnabled?: boolean;
  entitiesEnabled?: boolean;
  contextEnabled?: boolean;
  agentsEnabled?: boolean;
  settingsEnabled?: boolean;
};

async function renderPathWithGates(path: string, gates: GateOverrides = {}): Promise<void> {
  const {
    alertsEnabled = true,
    fabricEnabled = true,
    memoryEnabled = true,
    entitiesEnabled = true,
    contextEnabled = true,
    agentsEnabled = true,
    settingsEnabled = true,
  } = gates;

  vi.resetModules();
  vi.doMock('../../layouts/DefaultLayout', async () => {
    const { Outlet } = await import('react-router');
    return {
      DefaultLayout: () => (
        <div data-testid="default-layout">
          <Outlet />
        </div>
      ),
    };
  });
  vi.doMock('../../components/common/RouteErrorBoundary', () => ({
    RouteErrorBoundary: () => <div data-testid="route-error-boundary">route-error</div>,
  }));
  vi.doMock('../../components/common/LoadingState', () => ({
    LoadingState: () => <div data-testid="loading-state">loading</div>,
  }));
  vi.doMock('../../lib/feature-gates', () => ({
    isAlertsSurfaceEnabled: () => alertsEnabled,
    isFabricSurfaceEnabled: () => fabricEnabled,
    isMemorySurfaceEnabled: () => memoryEnabled,
    isEntityBrowserSurfaceEnabled: () => entitiesEnabled,
    isContextSurfaceEnabled: () => contextEnabled,
    isAgentCatalogSurfaceEnabled: () => agentsEnabled,
    isSettingsSurfaceEnabled: () => settingsEnabled,
    isAiOperationsEnabled: () => true,
    isAiAlertGroupingFallbackEnabled: () => true,
  }));

  const [{ routes }, { createMemoryRouter, RouterProvider }] = await Promise.all([
    import('../../routes'),
    import('react-router'),
  ]);

  const router = createMemoryRouter(routes, {
    initialEntries: [path],
  });

  render(<RouterProvider router={router} />);
}

describe('preview route guards', () => {
  afterEach(() => {
    vi.doUnmock('../../lib/feature-gates');
    vi.doUnmock('../../layouts/DefaultLayout');
    vi.doUnmock('../../components/common/RouteErrorBoundary');
    vi.doUnmock('../../components/common/LoadingState');
    vi.resetModules();
  });

  it('fails closed to NotFound for /alerts when alerts gate is disabled', async () => {
    await renderPathWithGates('/alerts', { alertsEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /fabric when fabric gate is disabled', async () => {
    await renderPathWithGates('/fabric', { fabricEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /agents when agents gate is disabled', async () => {
    await renderPathWithGates('/agents', { agentsEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /memory when memory gate is disabled', async () => {
    await renderPathWithGates('/memory', { memoryEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /entities when entities gate is disabled', async () => {
    await renderPathWithGates('/entities', { entitiesEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /context when context gate is disabled', async () => {
    await renderPathWithGates('/context', { contextEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });

  it('fails closed to NotFound for /settings when settings gate is disabled', async () => {
    await renderPathWithGates('/settings', { settingsEnabled: false });

    expect(await screen.findByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
  });
});
