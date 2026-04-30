/**
 * Unit tests for FeatureDiscovery components.
 *
 * Tests cover: tooltip render/dismiss, provider state management,
 * localStorage persistence, and FeatureBadge new/seen states.
 */

import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  FeatureDiscoveryTooltip,
  FeatureDiscoveryProvider,
  useFeatureDiscovery,
  FeatureBadge,
} from '../FeatureDiscovery';

// ---------------------------------------------------------------------------
// Storage mock
// ---------------------------------------------------------------------------

const storageMock: Record<string, unknown> = {};

vi.mock('../../../services/storage', () => ({
  readStorage: <T,>(key: string): T | null => (storageMock[key] as T) ?? null,
  writeStorage: (key: string, value: unknown): void => {
    storageMock[key] = value;
  },
}));

// useFeatureFlag: return true so discovery is enabled in all tests
vi.mock('yappc-core/config/features/feature-flags', () => ({
  useFeatureFlag: () => true,
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Wrapper that provides FeatureDiscoveryContext. */
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <FeatureDiscoveryProvider>{children}</FeatureDiscoveryProvider>;
}

/** Consumer component to exercise the context hook. */
function ContextConsumer() {
  const { activeFeature, dismissedFeatures, showFeature, dismissFeature } = useFeatureDiscovery();
  return (
    <div>
      <span data-testid="active">{activeFeature ?? 'none'}</span>
      <span data-testid="dismissed">{dismissedFeatures.join(',')}</span>
      <button onClick={() => showFeature('command-palette')}>show</button>
      <button onClick={() => dismissFeature('command-palette')}>dismiss</button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tests: FeatureDiscoveryTooltip
// ---------------------------------------------------------------------------

describe('FeatureDiscoveryTooltip', () => {
  beforeEach(() => {
    // Reset storage mock
    Object.keys(storageMock).forEach((k) => { delete storageMock[k]; });

    // Mock getBoundingClientRect so useEffect position calculation works
    vi.spyOn(document, 'querySelector').mockReturnValue({
      getBoundingClientRect: () => ({ top: 100, bottom: 120, left: 50, width: 200, height: 20, right: 250 }),
    } as unknown as Element);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <FeatureDiscoveryTooltip featureId="command-palette" isOpen={false} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when featureId is unknown', () => {
    const { container } = render(
      <FeatureDiscoveryTooltip featureId="nonexistent" isOpen={true} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders tooltip title and description for a known feature when open', async () => {
    render(
      <FeatureDiscoveryTooltip featureId="command-palette" isOpen={true} />,
    );
    // Position is set asynchronously via useEffect
    // The component renders null until position is calculated; trigger layout
    await act(async () => {});
    expect(screen.getByRole('dialog', { name: /Feature: Command Palette/i })).toBeInTheDocument();
    expect(screen.getByText(/Press Cmd\+K/i)).toBeInTheDocument();
  });

  it('calls onDismiss when the dismiss button is clicked', async () => {
    const onDismiss = vi.fn();
    render(
      <FeatureDiscoveryTooltip featureId="command-palette" isOpen={true} onDismiss={onDismiss} />,
    );
    await act(async () => {});
    fireEvent.click(screen.getByRole('button', { name: /Dismiss/i }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  it('calls onDismiss when the "Got it" button is clicked', async () => {
    const onDismiss = vi.fn();
    render(
      <FeatureDiscoveryTooltip featureId="command-palette" isOpen={true} onDismiss={onDismiss} />,
    );
    await act(async () => {});
    fireEvent.click(screen.getByRole('button', { name: /Got it/i }));
    expect(onDismiss).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// Tests: FeatureDiscoveryProvider + context
// ---------------------------------------------------------------------------

describe('FeatureDiscoveryProvider', () => {
  beforeEach(() => {
    Object.keys(storageMock).forEach((k) => { delete storageMock[k]; });
  });

  it('starts with no active feature and empty dismissed list', () => {
    render(<TestWrapper><ContextConsumer /></TestWrapper>);
    expect(screen.getByTestId('active').textContent).toBe('none');
    expect(screen.getByTestId('dismissed').textContent).toBe('');
  });

  it('shows a feature that has not been dismissed', () => {
    render(<TestWrapper><ContextConsumer /></TestWrapper>);
    fireEvent.click(screen.getByRole('button', { name: 'show' }));
    expect(screen.getByTestId('active').textContent).toBe('command-palette');
  });

  it('does not show a feature that is already dismissed', () => {
    // Pre-populate storage with dismissed state
    storageMock['yappc:dismissed-features'] = ['command-palette'];
    render(<TestWrapper><ContextConsumer /></TestWrapper>);
    fireEvent.click(screen.getByRole('button', { name: 'show' }));
    expect(screen.getByTestId('active').textContent).toBe('none');
  });

  it('persists dismissed features to storage', () => {
    render(<TestWrapper><ContextConsumer /></TestWrapper>);
    fireEvent.click(screen.getByRole('button', { name: 'show' }));
    fireEvent.click(screen.getByRole('button', { name: 'dismiss' }));
    expect(screen.getByTestId('dismissed').textContent).toBe('command-palette');
    expect(storageMock['yappc:dismissed-features']).toEqual(['command-palette']);
  });

  it('clears active feature after dismissal', () => {
    render(<TestWrapper><ContextConsumer /></TestWrapper>);
    fireEvent.click(screen.getByRole('button', { name: 'show' }));
    fireEvent.click(screen.getByRole('button', { name: 'dismiss' }));
    expect(screen.getByTestId('active').textContent).toBe('none');
  });
});

// ---------------------------------------------------------------------------
// Tests: FeatureBadge
// ---------------------------------------------------------------------------

describe('FeatureBadge', () => {
  beforeEach(() => {
    Object.keys(storageMock).forEach((k) => { delete storageMock[k]; });
  });

  it('shows the animated badge for a feature that has not been dismissed', () => {
    render(
      <TestWrapper>
        <FeatureBadge featureId="command-palette">
          <button>Palette</button>
        </FeatureBadge>
      </TestWrapper>,
    );
    // The ping animation span is rendered when the feature is new
    // eslint-disable-next-line testing-library/no-container
    const { container } = render(
      <TestWrapper>
        <FeatureBadge featureId="command-palette">
          <button>Palette</button>
        </FeatureBadge>
      </TestWrapper>,
    );
    expect(container.querySelector('.animate-ping')).toBeInTheDocument();
  });

  it('hides the badge for a feature that has been dismissed', () => {
    storageMock['yappc:dismissed-features'] = ['command-palette'];
    const { container } = render(
      <TestWrapper>
        <FeatureBadge featureId="command-palette">
          <button>Palette</button>
        </FeatureBadge>
      </TestWrapper>,
    );
    expect(container.querySelector('.animate-ping')).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// Tests: useFeatureDiscovery hook guard
// ---------------------------------------------------------------------------

describe('useFeatureDiscovery', () => {
  it('throws when used outside FeatureDiscoveryProvider', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    function BrokenConsumer() {
      useFeatureDiscovery();
      return null;
    }
    expect(() => render(<BrokenConsumer />)).toThrow(
      'useFeatureDiscovery must be used within FeatureDiscoveryProvider',
    );
    consoleSpy.mockRestore();
  });
});
