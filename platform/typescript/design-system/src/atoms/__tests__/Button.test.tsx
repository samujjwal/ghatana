/**
 * @file Button.test.tsx
 * Unit tests for the Button atom component.
 *
 * Covers variants, sizes, tones, loading state, disabled state, icon slots,
 * and accessibility characteristics.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Button } from '../Button';

describe('Button', () => {
  // ── Basic rendering ──────────────────────────────────────────────────────

  describe('Basic rendering', () => {
    it('renders children text', () => {
      render(<Button>Click me</Button>);
      expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
    });

    it('renders as a <button> element by default', () => {
      render(<Button>Label</Button>);
      expect(screen.getByRole('button')).toBeInstanceOf(HTMLButtonElement);
    });

    it('applies an accessible name via aria-label', () => {
      render(<Button aria-label="Submit form">GO</Button>);
      expect(screen.getByRole('button', { name: 'Submit form' })).toBeInTheDocument();
    });
  });

  // ── Variants ─────────────────────────────────────────────────────────────

  describe('Variants', () => {
    const variants = ['solid', 'outline', 'soft', 'ghost', 'link'] as const;

    variants.forEach((variant) => {
      it(`renders without error with variant="${variant}"`, () => {
        const { container } = render(<Button variant={variant}>Test</Button>);
        expect(container.querySelector('button')).toBeTruthy();
      });
    });
  });

  // ── Sizes ────────────────────────────────────────────────────────────────

  describe('Sizes', () => {
    const sizes = ['sm', 'md', 'lg'] as const;

    sizes.forEach((size) => {
      it(`renders without error with size="${size}"`, () => {
        const { container } = render(<Button size={size}>Test</Button>);
        expect(container.querySelector('button')).toBeTruthy();
      });
    });

    it('accepts MUI-style size aliases (small, medium, large)', () => {
      const { container: c1 } = render(<Button size="small">Small</Button>);
      const { container: c2 } = render(<Button size="medium">Medium</Button>);
      const { container: c3 } = render(<Button size="large">Large</Button>);
      expect(c1.querySelector('button')).toBeTruthy();
      expect(c2.querySelector('button')).toBeTruthy();
      expect(c3.querySelector('button')).toBeTruthy();
    });
  });

  // ── Tones ────────────────────────────────────────────────────────────────

  describe('Tones', () => {
    const tones = ['primary', 'secondary', 'success', 'warning', 'danger', 'info', 'neutral'] as const;

    tones.forEach((tone) => {
      it(`renders without error with tone="${tone}"`, () => {
        const { container } = render(<Button tone={tone}>Test</Button>);
        expect(container.querySelector('button')).toBeTruthy();
      });
    });
  });

  // ── Disabled state ───────────────────────────────────────────────────────

  describe('Disabled state', () => {
    it('has disabled attribute when disabled=true', () => {
      render(<Button disabled>Disabled</Button>);
      expect(screen.getByRole('button')).toBeDisabled();
    });

    it('does not fire onClick when disabled', () => {
      const onClick = vi.fn();
      render(<Button disabled onClick={onClick}>Disabled</Button>);
      fireEvent.click(screen.getByRole('button'));
      expect(onClick).not.toHaveBeenCalled();
    });
  });

  // ── Loading state ─────────────────────────────────────────────────────────

  describe('Loading state', () => {
    it('is disabled (effectively) while loading', () => {
      render(<Button loading>Loading</Button>);
      // Loading buttons are rendered disabled to prevent double-submits
      const btn = screen.getByRole('button');
      expect(btn).toBeDisabled();
    });

    it('does not fire onClick while loading', () => {
      const onClick = vi.fn();
      render(<Button loading onClick={onClick}>Loading</Button>);
      fireEvent.click(screen.getByRole('button'));
      expect(onClick).not.toHaveBeenCalled();
    });
  });

  // ── Click handling ────────────────────────────────────────────────────────

  describe('Click handling', () => {
    it('calls onClick when clicked', () => {
      const onClick = vi.fn();
      render(<Button onClick={onClick}>Click</Button>);
      fireEvent.click(screen.getByRole('button'));
      expect(onClick).toHaveBeenCalledTimes(1);
    });

    it('passes the click event to onClick handler', () => {
      const onClick = vi.fn();
      render(<Button onClick={onClick}>Click</Button>);
      fireEvent.click(screen.getByRole('button'));
      expect(onClick).toHaveBeenCalledWith(expect.objectContaining({ type: 'click' }));
    });
  });

  // ── Type attribute ────────────────────────────────────────────────────────

  describe('Type attribute', () => {
    it('defaults to type="button" to prevent accidental form submission', () => {
      render(<Button>Test</Button>);
      expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
    });

    it('accepts type="submit"', () => {
      render(<Button type="submit">Submit</Button>);
      expect(screen.getByRole('button')).toHaveAttribute('type', 'submit');
    });
  });

  // ── Icon slots ──────────────────────────────────────────────────────────

  describe('Icon slots', () => {
    it('renders startIcon alongside label', () => {
      render(
        <Button startIcon={<span data-testid="start-icon" />}>
          With start
        </Button>
      );
      expect(screen.getByTestId('start-icon')).toBeInTheDocument();
      expect(screen.getByRole('button')).toHaveTextContent('With start');
    });

    it('renders endIcon alongside label', () => {
      render(
        <Button endIcon={<span data-testid="end-icon" />}>
          With end
        </Button>
      );
      expect(screen.getByTestId('end-icon')).toBeInTheDocument();
    });
  });

  // ── className forwarding ──────────────────────────────────────────────────

  describe('className forwarding', () => {
    it('merges custom className with component styles', () => {
      const { container } = render(<Button className="custom-class">Test</Button>);
      expect(container.querySelector('button.custom-class')).toBeTruthy();
    });
  });
});
