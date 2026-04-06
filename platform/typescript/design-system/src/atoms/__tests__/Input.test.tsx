/**
 * @file Input.test.tsx
 * Unit tests for the Input (TextField) atom component.
 *
 * Covers text input, placeholder, label, error states, helper text,
 * disabled state, onChange callback, and adornment slots.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
      render(<Input value="hello" onChange={() => { }} />);
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

    it('associates error, helper, and description text through aria-describedby', () => {
      render(
        <Input
          id="email"
          label="Email"
          error="Invalid email"
          helperText="Use a company address"
          description="Only administrators are notified"
        />
      );

      expect(screen.getByRole('textbox')).toHaveAttribute(
        'aria-describedby',
        'email-error email-helper email-description'
      );
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid email');
    });
  });

  // ── Validation and input attributes ──────────────────────────────────────

  describe('Validation and input attributes', () => {
    it('supports native required validation', () => {
      render(<Input label="Name" required />);
      const input = screen.getByRole('textbox');

      expect(input).toBeRequired();
      expect(input).toBeInvalid();
    });

    it('supports masked-input consumers by forwarding native mask-related attributes', () => {
      render(
        <Input
          label="PIN"
          InputProps={{
            inputProps: {
              inputMode: 'numeric',
              pattern: '[0-9]{4}',
              maxLength: 4,
            },
          }}
        />
      );

      const input = screen.getByRole('textbox');
      expect(input).toHaveAttribute('inputmode', 'numeric');
      expect(input).toHaveAttribute('pattern', '[0-9]{4}');
      expect(input).toHaveAttribute('maxlength', '4');
    });
  });

  // ── Disabled state ────────────────────────────────────────────────────────

  describe('Disabled state', () => {
    it('renders as disabled when disabled=true', () => {
      render(<Input disabled />);
      expect(screen.getByRole('textbox')).toBeDisabled();
    });

    it('does not fire onChange when disabled', () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<Input disabled onChange={onChange} />);
      return user.type(screen.getByRole('textbox'), 'x').then(() => {
        expect(onChange).not.toHaveBeenCalled();
      });
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

  // ── Rapid updates ─────────────────────────────────────────────────────────

  describe('Rapid updates', () => {
    it('surfaces each rapid change event without swallowing updates', () => {
      const onChange = vi.fn();
      render(<Input onChange={onChange} />);

      const input = screen.getByRole('textbox');
      fireEvent.change(input, { target: { value: '1' } });
      fireEvent.change(input, { target: { value: '12' } });
      fireEvent.change(input, { target: { value: '123' } });

      expect(onChange).toHaveBeenCalledTimes(3);
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
      render(<Input readOnly value="static" onChange={() => { }} />);
      expect(screen.getByRole('textbox')).toHaveAttribute('readonly');
    });
  });
});
