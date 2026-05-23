import React from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockUseCapabilityGate = vi.hoisted(() => vi.fn());

vi.mock('../../../hooks/useCapabilityGate', () => ({
  useCapabilityGate: mockUseCapabilityGate,
}));

vi.mock('../../../pages/product-family/ProductFamilyControlPlanePage', () => ({
  ProductFamilyControlPlanePage: () => <main data-testid="product-family-page" />,
}));

describe('Product family route gate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders unavailable state when backend-owned capability is denied', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: false, reason: 'backend-not-live' });

    const { Component } = await import('../product-family');
    render(<Component />);

    expect(screen.getByTestId('product-family-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('product-family-page')).not.toBeInTheDocument();
    expect(screen.getByText(/not yet available/i)).toBeInTheDocument();
  });

  it('renders product-family page when backend-owned capability is granted', async () => {
    mockUseCapabilityGate.mockReturnValue({ granted: true, reason: undefined });

    const { Component } = await import('../product-family');
    render(<Component />);

    expect(screen.getByTestId('product-family-page')).toBeInTheDocument();
    expect(screen.queryByTestId('product-family-unavailable')).not.toBeInTheDocument();
  });
});
