import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { Input } from './Input';

describe('Input', () => {
  it('renders with label', () => {
    render(<Input label="Email" />);
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('renders with placeholder', () => {
    render(<Input placeholder="Enter your email" />);
    expect(screen.getByPlaceholderText('Enter your email')).toBeInTheDocument();
  });

  it('handles value changes', () => {
    const handleChange = vi.fn();
    render(<Input onChange={handleChange} />);
    
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'test@example.com' } });
    
    expect(handleChange).toHaveBeenCalled();
  });

  it('displays helper text', () => {
    render(<Input helperText="Enter a valid email address" />);
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
  });

  it('displays error message', () => {
    render(<Input error="Email is required" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Email is required');
  });

  it('error message overrides helper text', () => {
    render(
      <Input 
        helperText="This is helper text"
        error="This is an error"
      />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('This is an error');
    expect(screen.queryByText('This is helper text')).not.toBeInTheDocument();
  });

  it('shows required indicator', () => {
    render(<Input label="Email" required />);
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  it('shows character counter', () => {
    render(<Input showCounter maxLength={50} defaultValue="Hello" />);
    expect(screen.getByText('5 / 50')).toBeInTheDocument();
  });

  it('updates character counter on input', () => {
    render(<Input showCounter maxLength={50} />);
    
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'Hello World' } });
    
    expect(screen.getByText('11 / 50')).toBeInTheDocument();
  });

  it('respects maxLength attribute', () => {
    render(<Input maxLength={10} />);
    const input = screen.getByRole('textbox') as HTMLInputElement;
    expect(input.maxLength).toBe(10);
  });

  it('renders with different sizes', () => {
    const { rerender } = render(<Input size="small" />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();

    rerender(<Input size="medium" />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();

    rerender(<Input size="large" />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('renders full width', () => {
    render(<Input fullWidth />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('supports different input types', () => {
    const { rerender } = render(<Input type="email" />);
    expect(screen.getByRole('textbox')).toHaveAttribute('type', 'email');

    rerender(<Input type="password" />);
    const passwordInput = document.querySelector('input[type="password"]');
    expect(passwordInput).toBeInTheDocument();

    rerender(<Input type="number" />);
    const numberInput = document.querySelector('input[type="number"]');
    expect(numberInput).toBeInTheDocument();
  });

  it('is disabled when disabled prop is true', () => {
    render(<Input disabled />);
    expect(screen.getByRole('textbox')).toBeDisabled();
  });

  it('has proper accessibility attributes', () => {
    render(<Input label="Email" helperText="Enter your email" />);
    const input = screen.getByRole('textbox');
    
    expect(input).toHaveAccessibleName('Email');
    expect(input).toHaveAccessibleDescription('Enter your email');
  });

  it('sets aria-invalid when error is present', () => {
    render(<Input error="Invalid email" />);
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('forwards ref correctly', () => {
    const ref = vi.fn();
    render(<Input ref={ref} />);
    expect(ref).toHaveBeenCalled();
  });

  it('renders with start icon', () => {
    render(<Input startIcon={<span data-testid="start-icon">@</span>} />);
    expect(screen.getByTestId('start-icon')).toBeInTheDocument();
  });

  it('renders with end icon', () => {
    render(<Input endIcon={<span data-testid="end-icon">✓</span>} />);
    expect(screen.getByTestId('end-icon')).toBeInTheDocument();
  });

  // Tests for clear button functionality
  it('renders clear button when clearable is true and input has value', () => {
    render(<Input clearable defaultValue="test" />);
    expect(screen.getByRole('button', { name: 'Clear input' })).toBeInTheDocument();
  });

  it('does not render clear button when clearable is true but input has no value', () => {
    render(<Input clearable />);
    expect(screen.queryByRole('button', { name: 'Clear input' })).not.toBeInTheDocument();
  });

  it('clears input value when clear button is clicked', () => {
    const handleChange = vi.fn();
    render(<Input clearable defaultValue="test" onChange={handleChange} />);
    
    fireEvent.click(screen.getByRole('button', { name: 'Clear input' }));
    
    expect(handleChange).toHaveBeenCalled();
    expect(screen.getByRole('textbox')).toHaveValue('');
  });

  it('calls onClear callback when clear button is clicked', () => {
    const handleClear = vi.fn();
    render(<Input clearable defaultValue="test" onClear={handleClear} />);
    
    fireEvent.click(screen.getByRole('button', { name: 'Clear input' }));
    
    expect(handleClear).toHaveBeenCalled();
  });

  // Tests for password visibility toggle
  it('renders password visibility toggle when showPasswordToggle is true and type is password', () => {
    render(<Input type="password" showPasswordToggle />);
    expect(screen.getByRole('button', { name: 'Show password' })).toBeInTheDocument();
  });

  it('does not render password visibility toggle when type is not password', () => {
    render(<Input type="text" showPasswordToggle />);
    expect(screen.queryByRole('button', { name: 'Show password' })).not.toBeInTheDocument();
  });

  it('toggles password visibility when toggle button is clicked', () => {
    render(<Input type="password" showPasswordToggle />);
    
    const passwordInput = screen.getByRole('textbox') as HTMLInputElement;
    expect(passwordInput.type).toBe('password');
    
    fireEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(passwordInput.type).toBe('text');
    
    fireEvent.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(passwordInput.type).toBe('password');
  });

  // Tests for loading state
  it('renders loading spinner when loading is true', () => {
    render(<Input loading />);
    // Look for SVG path that's part of the loading spinner
    expect(document.querySelector('svg path[d="M12 2a10 10 0 0 1 10 10"]')).toBeInTheDocument();
  });

  it('disables input when loading is true', () => {
    render(<Input loading />);
    expect(screen.getByRole('textbox')).toBeDisabled();
  });

  it('sets aria-busy when loading is true', () => {
    render(<Input loading />);
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-busy', 'true');
  });

  it('applies loading styles to input', () => {
    render(<Input loading />);
    const input = screen.getByRole('textbox');
    const computedStyle = window.getComputedStyle(input);
    expect(input).toHaveStyle('cursor: wait');
  });
});
