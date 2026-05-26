/**
 * Tests for FeatureFlagPage — verifies coming-soon message and route attribute.
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FeatureFlagPage } from '../FeatureFlagPage';

vi.mock('@ghatana/design-system', () => ({
  Card: ({ children }: { children: React.ReactNode }) => React.createElement('div', null, children),
  CardHeader: ({ title, subheader }: { title: string; subheader: string }) =>
    React.createElement('div', null, React.createElement('h1', null, title), React.createElement('p', null, subheader)),
  CardContent: ({ children }: { children: React.ReactNode }) => React.createElement('div', null, children),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

describe('FeatureFlagPage', () => {
  it('renders the coming soon title key', () => {
    render(<FeatureFlagPage routePath="/voice/input" />);
    expect(screen.getAllByText('featureFlag.comingSoon').length).toBeGreaterThan(0);
  });

  it('renders the deferred subheader key', () => {
    render(<FeatureFlagPage routePath="/voice/input" />);
    expect(screen.getByText('featureFlag.deferred')).toBeTruthy();
  });

  it('exposes the route path in data attribute', () => {
    render(<FeatureFlagPage routePath="/claims" />);
    const el = screen.getByTestId('feature-flag-path');
    expect(el.getAttribute('data-route')).toBe('/claims');
  });

  it('works with different route paths', () => {
    const { rerender } = render(<FeatureFlagPage routePath="/imaging" />);
    expect(screen.getByTestId('feature-flag-path').getAttribute('data-route')).toBe('/imaging');
    rerender(<FeatureFlagPage routePath="/referrals" />);
    expect(screen.getByTestId('feature-flag-path').getAttribute('data-route')).toBe('/referrals');
  });
});
