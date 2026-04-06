/**
 * @file Checkbox.test.tsx
 * Unit tests for the Checkbox atom component.
 *
 * Covers checked/unchecked state, indeterminate, sizes, tones,
 * labeling, disabled behavior, and onChange callbacks.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Checkbox } from '../Checkbox';

describe('Checkbox', () => {
  // ── Basic rendering ──────────────────────────────────────────────────────

  describe('Basic rendering', () => {
    it('renders an accessible checkbox role', () => {
      render(<Checkbox />);
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });

    it('is unchecked by default', () => {
      render(<Checkbox />);
      expect(screen.getByRole('checkbox')).not.toBeChecked();
    });

    it('renders with a label', () => {
      render(<Checkbox label="Accept terms" />);
      expect(screen.getByLabelText('Accept terms')).toBeInTheDocument();
    });
  });

  // ── Controlled checked state ──────────────────────────────────────────────

  describe('Controlled checked state', () => {
    it('renders checked when checked=true', () => {
      render(<Checkbox checked onChange={() => { }} />);
      expect(screen.getByRole('checkbox')).toBeChecked();
    });

    it('renders unchecked when checked=false', () => {
      render(<Checkbox checked={false} onChange={() => { }} />);
      expect(screen.getByRole('checkbox')).not.toBeChecked();
    });
  });

  // ── Indeterminate ─────────────────────────────────────────────────────────

  describe('Indeterminate state', () => {
    it('sets indeterminate property when indeterminate=true', () => {
      render(<Checkbox indeterminate onChange={() => { }} />);
      const input = screen.getByRole('checkbox') as HTMLInputElement;
      expect(input.indeterminate).toBe(true);
    });

    it('does not set indeterminate when not provided', () => {
      render(<Checkbox onChange={() => { }} />);
      const input = screen.getByRole('checkbox') as HTMLInputElement;
      expect(input.indeterminate).toBe(false);
    });

    it('renders a custom indeterminate icon when provided', () => {
      render(
        <Checkbox
          indeterminate
          indeterminateIcon={<span data-testid="indeterminate-icon">-</span>}
          onChange={() => { }}
        />
      );

      expect(screen.getByTestId('indeterminate-icon')).toBeInTheDocument();
    });
  });

  // ── Disabled state ────────────────────────────────────────────────────────

  describe('Disabled state', () => {
    it('renders as disabled when disabled=true', () => {
      render(<Checkbox disabled />);
      expect(screen.getByRole('checkbox')).toBeDisabled();
    });

    it('does not call onChange when disabled and clicked', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<Checkbox disabled onChange={onChange} />);
      await user.click(screen.getByRole('checkbox'));
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  // ── onChange callback ─────────────────────────────────────────────────────

  describe('onChange callback', () => {
    it('fires onChange when clicked (uncontrolled)', () => {
      const onChange = vi.fn();
      render(<Checkbox onChange={onChange} />);
      fireEvent.click(screen.getByRole('checkbox'));
      expect(onChange).toHaveBeenCalledTimes(1);
    });

    it('passes the change event to onChange', () => {
      const onChange = vi.fn();
      render(<Checkbox onChange={onChange} />);
      fireEvent.click(screen.getByRole('checkbox'));
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'change' })
      );
    });
  });

  // ── Sizes ─────────────────────────────────────────────────────────────────

  describe('Sizes', () => {
    it('renders with size="sm"', () => {
      render(<Checkbox size="sm" />);
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });

    it('renders with size="md"', () => {
      render(<Checkbox size="md" />);
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });

    it('renders with size="lg"', () => {
      render(<Checkbox size="lg" />);
      expect(screen.getByRole('checkbox')).toBeInTheDocument();
    });
  });

  // ── Tones ─────────────────────────────────────────────────────────────────

  describe('Tones', () => {
    const tones = ['primary', 'secondary', 'success', 'warning', 'danger', 'neutral'] as const;

    tones.forEach((tone) => {
      it(`renders without error with tone="${tone}"`, () => {
        render(<Checkbox tone={tone} />);
        expect(screen.getByRole('checkbox')).toBeInTheDocument();
      });
    });
  });

  // ── Helper text ───────────────────────────────────────────────────────────

  describe('Helper text', () => {
    it('displays helperText below the checkbox', () => {
      render(<Checkbox label="Option" helperText="Required field" />);
      expect(screen.getByText('Required field')).toBeInTheDocument();
    });

    it('links helper text to the checkbox through aria-describedby', () => {
      render(<Checkbox id="terms" label="Terms" helperText="Read carefully" />);

      expect(screen.getByRole('checkbox')).toHaveAttribute('aria-describedby', 'terms-helper');
      expect(screen.getByText('Read carefully')).toHaveAttribute('id', 'terms-helper');
    });
  });

  // ── Accessibility ─────────────────────────────────────────────────────────

  describe('Accessibility', () => {
    it('is keyboard reachable through tab navigation', async () => {
      const user = userEvent.setup();
      render(
        <>
          <button type="button">Before</button>
          <Checkbox label="Keyboard checkbox" />
        </>
      );

      await user.tab();
      await user.tab();

      expect(screen.getByRole('checkbox')).toHaveFocus();
    });

    it('toggles when activated with the keyboard', async () => {
      const user = userEvent.setup();
      render(<Checkbox label="Keyboard toggle" />);

      const checkbox = screen.getByRole('checkbox');
      await user.tab();
      await user.keyboard(' ');

      expect(checkbox).toBeChecked();
    });
  });
});
