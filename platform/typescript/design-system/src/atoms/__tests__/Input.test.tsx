/**
 * @file Input.test.tsx
 * Unit tests for the Input (TextField) atom component.
 *
 * Covers text input, placeholder, label, error states, helper text,
 * disabled state, onChange callback, and adornment slots.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Input } from '../Input';

describe('Input (TextField)', () => {
  // ── Basic rendering ──────────────────────────────────────────────────────

  describe('Basic rendering', () => {
    it('renders a text input by default', () => {
      render(<Input />);
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    it('renders with a label', () => {
      render(<Input label="Email address" />);
      expect(screen.getByLabelText('Email address')).toBeInTheDocument();
    });

    it('renders placeholder text', () => {
      render(<Input placeholder="Enter email" />);
      expect(screen.getByPlaceholderText('Enter email')).toBeInTheDocument();
    });

    it('renders helper text', () => {
      render(<Input helperText="We will never share your email." />);
      expect(screen.getByText('We will never share your email.')).toBeInTheDocument();
    });
  });

  // ── Value / onChange ──────────────────────────────────────────────────────

  describe('Value and onChange', () => {
    it('reflects a controlled value', () => {
      render(<Input value="hello" onChange={() => {}} />);
      const input = screen.getByRole('textbox') as HTMLInputElement;
      expect(input.value).toBe('hello');
    });

    it('calls onChange when user types', () => {
      const onChange = vi.fn();
      render(<Input onChange={onChange} />);
      fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test' } });
      expect(onChange).toHaveBeenCalledTimes(1);
    });

    it('passes the change event to onChange handler', () => {
      const onChange = vi.fn();
      render(<Input onChange={onChange} />);
      fireEvent.change(screen.getByRole('textbox'), { target: { value: 'x' } });
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'change' })
      );
    });
  });

  // ── Error state ────────────────────────────────────────────────────────────

  describe('Error state', () => {
    it('marks input as invalid when error=true', () => {
      render(<Input error label="Field" />);
      expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true');
    });

    it('renders error string as helper text', () => {
      render(<Input error="This field is required" />);
      expect(screen.getByText('This field is required')).toBeInTheDocument();
    });
  });

  // ── Disabled state ────────────────────────────────────────────────────────

  describe('Disabled state', () => {
    it('renders as disabled when disabled=true', () => {
      render(<Input disabled />);
      expect(screen.getByRole('textbox')).toBeDisabled();
    });

    it('does not fire onChange when disabled', () => {
      const onChange = vi.fn();
      render(<Input disabled onChange={onChange} />);
      fireEvent.change(screen.getByRole('textbox'), { target: { value: 'x' } });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  // ── Adornments ────────────────────────────────────────────────────────────

  describe('Adornments', () => {
    it('renders startAdornment inside InputProps', () => {
      render(
        <Input
          InputProps={{ startAdornment: <span data-testid="start-adornment">$</span> }}
        />
      );
      expect(screen.getByTestId('start-adornment')).toBeInTheDocument();
    });

    it('renders endAdornment inside InputProps', () => {
      render(
        <Input
          InputProps={{ endAdornment: <span data-testid="end-adornment">kg</span> }}
        />
      );
      expect(screen.getByTestId('end-adornment')).toBeInTheDocument();
    });
  });

  // ── Input type ────────────────────────────────────────────────────────────

  describe('Input type', () => {
    it('renders type="password" correctly', () => {
      render(<Input type="password" />);
      const input = screen.getByDisplayValue('') as HTMLInputElement;
      // password inputs don't have role="textbox" — check by type
      expect(document.querySelector('input[type="password"]')).toBeInTheDocument();
    });

    it('renders type="number" correctly', () => {
      render(<Input type="number" />);
      expect(document.querySelector('input[type="number"]')).toBeInTheDocument();
    });
  });

  // ── Read-only ─────────────────────────────────────────────────────────────

  describe('Read-only', () => {
    it('renders as readOnly when readOnly=true', () => {
      render(<Input readOnly value="static" onChange={() => {}} />);
      expect(screen.getByRole('textbox')).toHaveAttribute('readonly');
    });
  });
});
