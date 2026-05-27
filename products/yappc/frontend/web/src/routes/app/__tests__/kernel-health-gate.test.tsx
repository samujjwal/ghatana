import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockUseCapabilityGate = vi.hoisted(() => vi.fn());

vi.mock('../../../hooks/useCapabilityGate', () => ({
  useCapabilityGate: mockUseCapabilityGate,
}));

vi.mock('../../../pages/kernel-health/KernelHealthDashboardPage', () => ({
  KernelHealthDashboardPage: () => <div data-testid="kernel-health-page">Kernel health page</div>,
}));

import KernelHealthRoute from '../kernel-health';
import KernelHealthProductRoute from '../kernel-health-product';

describe('Kernel health route capability gate', () => {
  beforeEach(() => {
    mockUseCapabilityGate.mockReset();
  });

  it('renders an unavailable state when Kernel health read capability is denied', () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'insufficient-role' });

    render(<KernelHealthRoute />);

    expect(mockUseCapabilityGate).toHaveBeenCalledWith('kernel-health:read');
    expect(screen.getByTestId('kernel-health-unavailable')).toHaveTextContent('owner or admin');
    expect(screen.queryByTestId('kernel-health-page')).not.toBeInTheDocument();
  });

  it('renders the list route when Kernel health read capability is granted', () => {
    mockUseCapabilityGate.mockReturnValue({ granted: true, reason: undefined });

    render(<KernelHealthRoute />);

    expect(screen.getByTestId('kernel-health-page')).toBeInTheDocument();
  });

  it('protects the product detail route with the same capability', () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'unauthenticated' });

    render(<KernelHealthProductRoute />);

    expect(mockUseCapabilityGate).toHaveBeenCalledWith('kernel-health:read');
    expect(screen.getByTestId('kernel-health-unavailable')).toHaveTextContent('Sign in');
  });
});
