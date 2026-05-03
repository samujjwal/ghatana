/**
 * Tests for @ghatana/product-shell
 *
 * Covers:
 * - RouteCapabilityNav role filtering
 * - RouteCapabilityNav lifecycle filtering (boundary excluded)
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
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { RouteCapabilityNav } from '../components/RouteCapabilityNav';
import { UnsupportedSurfaceBoundary } from '../components/UnsupportedSurfaceBoundary';
import { ProductViewModeSelector } from '../components/ProductViewModeSelector';
import { ActiveOperationsBar } from '../components/ActiveOperationsBar';
import type { ProductRouteCapability, ProductShellConfig, UnsupportedSurfaceConfig } from '../types';

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
