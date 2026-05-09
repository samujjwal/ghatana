import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { SurfaceRegistrySnapshot } from '../../../api/surfaces.service';

const { mockUseSurfaceRegistry, mockUseCapabilityGate } = vi.hoisted(() => ({
  mockUseSurfaceRegistry: vi.fn<() => { data: SurfaceRegistrySnapshot | undefined; isLoading: boolean }>(),
  mockUseCapabilityGate: vi.fn<(capabilities: string[], mode?: 'active' | 'activeOrDegraded' | 'notUnavailable') => boolean>(),
}));

vi.mock('../../../api/surfaces.service', () => ({
  useSurfaceRegistry: mockUseSurfaceRegistry,
  getSurfaceSignal: vi.fn(),
  isSurfaceAvailable: mockUseCapabilityGate,
}));

vi.mock('../../../hooks/useCapabilityGate', () => ({
  useCapabilityGate: mockUseCapabilityGate,
}));

import React from 'react';
import { RuntimeCapabilityRouteGate } from '../RuntimeCapabilityRouteGate';

describe('RuntimeCapabilityRouteGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders children when capability is allowed', () => {
    mockUseSurfaceRegistry.mockReturnValue({ data: { surfaces: [], generatedAt: '2026-01-01', requestId: 'r', tenantId: 't' }, isLoading: false });
    mockUseCapabilityGate.mockReturnValue(true);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText('Fabric Page')).toBeInTheDocument();
  });

  it('renders fallback when capability is unavailable', () => {
    mockUseSurfaceRegistry.mockReturnValue({ data: { surfaces: [], generatedAt: '2026-01-01', requestId: 'r', tenantId: 't' }, isLoading: false });
    mockUseCapabilityGate.mockReturnValue(false);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']} fallback={<div>Not Found</div>}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText('Not Found')).toBeInTheDocument();
    expect(screen.queryByText('Fabric Page')).not.toBeInTheDocument();
  });

  it('shows loading state while surfaces are loading', () => {
    mockUseSurfaceRegistry.mockReturnValue({ data: undefined, isLoading: true });
    mockUseCapabilityGate.mockReturnValue(false);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']} fallback={<div>Not Found</div>}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText(/Checking surface availability/i)).toBeInTheDocument();
    expect(screen.queryByText('Not Found')).not.toBeInTheDocument();
  });
});
