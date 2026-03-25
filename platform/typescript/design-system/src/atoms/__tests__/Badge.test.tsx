/**
 * @file Badge.test.tsx
 * Unit tests for the Badge atom component.
 *
 * Covers variants, tones, icon slots, overlay-counter (badgeContent),
 * and accessibility characteristics.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from '../Badge';

describe('Badge', () => {
  // ── Basic rendering ──────────────────────────────────────────────────────

  describe('Basic rendering', () => {
    it('renders text content', () => {
      render(<Badge>Active</Badge>);
      expect(screen.getByText('Active')).toBeInTheDocument();
    });

    it('renders as a <span> element', () => {
      const { container } = render(<Badge>Test</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });
  });

  // ── Variants ─────────────────────────────────────────────────────────────

  describe('Variants', () => {
    it('renders with variant="solid"', () => {
      const { container } = render(<Badge variant="solid">Solid</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });

    it('renders with variant="soft"', () => {
      const { container } = render(<Badge variant="soft">Soft</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });

    it('renders with variant="outline"', () => {
      const { container } = render(<Badge variant="outline">Outline</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });

    // MUI-compatibility aliases
    it('renders with variant="default" (MUI alias)', () => {
      const { container } = render(<Badge variant="default">Default</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });

    it('renders with variant="destructive" (MUI alias)', () => {
      const { container } = render(<Badge variant="destructive">Error</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });

    it('renders with variant="success" (MUI alias)', () => {
      const { container } = render(<Badge variant="success">OK</Badge>);
      expect(container.querySelector('span')).toBeTruthy();
    });
  });

  // ── Tones ────────────────────────────────────────────────────────────────

  describe('Tones', () => {
    const tones = ['neutral', 'primary', 'secondary', 'success', 'info', 'warning', 'danger'] as const;

    tones.forEach((tone) => {
      it(`renders without error with tone="${tone}"`, () => {
        const { container } = render(<Badge tone={tone}>Label</Badge>);
        expect(container.querySelector('span')).toBeTruthy();
      });
    });
  });

  // ── Icon slots ────────────────────────────────────────────────────────────

  describe('Icon slots', () => {
    it('renders startIcon', () => {
      render(
        <Badge startIcon={<span data-testid="start" />}>Status</Badge>
      );
      expect(screen.getByTestId('start')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    });

    it('renders endIcon', () => {
      render(
        <Badge endIcon={<span data-testid="end" />}>Status</Badge>
      );
      expect(screen.getByTestId('end')).toBeInTheDocument();
    });
  });

  // ── Counter (badgeContent) ────────────────────────────────────────────────

  describe('Counter overlay (badgeContent)', () => {
    it('renders badgeContent as an overlay counter', () => {
      render(<Badge badgeContent={5}>Notifications</Badge>);
      expect(screen.getByText('5')).toBeInTheDocument();
      expect(screen.getByText('Notifications')).toBeInTheDocument();
    });

    it('renders string badgeContent', () => {
      render(<Badge badgeContent="NEW">Item</Badge>);
      expect(screen.getByText('NEW')).toBeInTheDocument();
    });
  });

  // ── className forwarding ──────────────────────────────────────────────────

  describe('className forwarding', () => {
    it('merges custom className', () => {
      const { container } = render(<Badge className="custom-badge">Test</Badge>);
      expect(container.querySelector('.custom-badge')).toBeTruthy();
    });
  });
});
