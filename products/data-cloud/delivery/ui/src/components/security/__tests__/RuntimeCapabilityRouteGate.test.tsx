import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { CapabilityRegistrySnapshot } from '../../../api/capabilities.service';

const { mockUseCapabilityRegistry, mockUseCapabilityGate } = vi.hoisted(() => ({
  mockUseCapabilityRegistry: vi.fn<() => { data: CapabilityRegistrySnapshot | undefined; isLoading: boolean }>(),
  mockUseCapabilityGate: vi.fn<(capabilities: string[], mode?: 'active' | 'activeOrDegraded' | 'notUnavailable') => boolean>(),
}));

vi.mock('../../../api/capabilities.service', () => ({
  useCapabilityRegistry: mockUseCapabilityRegistry,
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
    mockUseCapabilityRegistry.mockReturnValue({ data: { capabilities: [], generatedAt: '2026-01-01', requestId: 'r', tenantId: 't' }, isLoading: false });
    mockUseCapabilityGate.mockReturnValue(true);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText('Fabric Page')).toBeInTheDocument();
  });

  it('renders fallback when capability is unavailable', () => {
    mockUseCapabilityRegistry.mockReturnValue({ data: { capabilities: [], generatedAt: '2026-01-01', requestId: 'r', tenantId: 't' }, isLoading: false });
    mockUseCapabilityGate.mockReturnValue(false);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']} fallback={<div>Not Found</div>}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText('Not Found')).toBeInTheDocument();
    expect(screen.queryByText('Fabric Page')).not.toBeInTheDocument();
  });

  it('keeps children visible while capabilities are loading', () => {
    mockUseCapabilityRegistry.mockReturnValue({ data: undefined, isLoading: true });
    mockUseCapabilityGate.mockReturnValue(false);

    render(
      <RuntimeCapabilityRouteGate aliases={['data-fabric']} fallback={<div>Not Found</div>}>
        <div>Fabric Page</div>
      </RuntimeCapabilityRouteGate>,
    );

    expect(screen.getByText('Fabric Page')).toBeInTheDocument();
    expect(screen.queryByText('Not Found')).not.toBeInTheDocument();
  });
});
