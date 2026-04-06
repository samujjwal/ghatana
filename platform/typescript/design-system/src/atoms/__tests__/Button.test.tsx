/**
 * @file Button.test.tsx
 * Unit tests for the Button atom component.
 *
 * Covers variants, sizes, tones, loading state, disabled state, icon slots,
 * and accessibility characteristics.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

    it('renders without visible children when an aria-label provides the accessible name', () => {
      render(<Button aria-label="Icon only button" />);
      expect(screen.getByRole('button', { name: 'Icon only button' })).toBeInTheDocument();
    });

    it('renders safely when optional icon and class props are nullish', () => {
      render(
        <Button
          className={undefined}
          startIcon={undefined}
          endIcon={null}
        >
          Safe render
        </Button>
      );

      expect(screen.getByRole('button', { name: 'Safe render' })).toBeInTheDocument();
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

    it('falls back gracefully when an unsupported runtime variant is provided', () => {
      render(<Button variant={'unsupported' as never}>Fallback</Button>);
      expect(screen.getByRole('button', { name: 'Fallback' })).toBeInTheDocument();
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

    it('falls back gracefully when an unsupported runtime tone is provided', () => {
      render(<Button tone={'unsupported' as never}>Fallback tone</Button>);
      expect(screen.getByRole('button', { name: 'Fallback tone' })).toBeInTheDocument();
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

    it('fires onClick for each double click interaction', async () => {
      const user = userEvent.setup();
      const onClick = vi.fn();
      render(<Button onClick={onClick}>Double click</Button>);

      await user.dblClick(screen.getByRole('button'));

      expect(onClick).toHaveBeenCalledTimes(2);
    });

    it('does not treat right click as a primary click interaction', () => {
      const onClick = vi.fn();
      render(<Button onClick={onClick}>Right click</Button>);

      fireEvent.contextMenu(screen.getByRole('button'));

      expect(onClick).not.toHaveBeenCalled();
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

  // ── Accessibility ─────────────────────────────────────────────────────────

  describe('Accessibility', () => {
    it('participates in keyboard tab navigation', async () => {
      const user = userEvent.setup();
      render(
        <>
          <button type="button">Before</button>
          <Button>Focusable</Button>
        </>
      );

      await user.tab();
      await user.tab();

      expect(screen.getByRole('button', { name: 'Focusable' })).toHaveFocus();
    });

    it('exposes aria-busy while loading', () => {
      render(<Button loading>Loading</Button>);
      expect(screen.getByRole('button', { name: 'Loading' })).toHaveAttribute('aria-busy', 'true');
    });

    it('marks disabled links with aria-disabled and removes navigation target', () => {
      render(
        <Button href="/next" disabled>
          Disabled link
        </Button>
      );

      const anchor = screen.getByText('Disabled link').closest('a');

      expect(anchor).toHaveAttribute('aria-disabled', 'true');
      expect(anchor).not.toHaveAttribute('href');
      expect(anchor).toHaveAttribute('tabindex', '-1');
    });
  });
});
