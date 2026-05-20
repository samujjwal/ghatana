/**
 * @fileoverview Tests for BuilderPage.
 *
 * Verifies that:
 * - The page mounts the BuilderStudio section without crashing
 * - The page renders the expected builder UI landmarks
 * - Component palette is present and accessible
 *
 * BuilderStudio itself has its own section-level tests; here we verify
 * the route wrapper integrates correctly and the section is mounted.
 *
 * @doc.type test
 * @doc.purpose Builder route page tests
 * @doc.layer studio
 */

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

// ============================================================================
// Mocks
// ============================================================================

// BuilderStudio is complex — mock it at the section boundary so this test
// remains a pure route-level integration test, not a full section test.
vi.mock('../../sections/BuilderStudio', () => ({
  default: () => (
    <main data-testid="builder-studio" aria-label="Builder Studio">
      <nav aria-label="Component Palette" />
      <section aria-label="Component Tree" />
      <section aria-label="Property Inspector" />
    </main>
  ),
}));

import BuilderPage from '../BuilderPage';

// ============================================================================
// Tests
// ============================================================================

describe('BuilderPage', () => {
  it('renders without crashing', () => {
    render(<BuilderPage />);
    expect(screen.getByTestId('builder-studio')).toBeInTheDocument();
  });

  it('mounts the BuilderStudio section with its accessibility landmarks', () => {
    render(<BuilderPage />);
    expect(screen.getByRole('main', { name: /Builder Studio/i })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /Component Palette/i })).toBeInTheDocument();
  });
});
