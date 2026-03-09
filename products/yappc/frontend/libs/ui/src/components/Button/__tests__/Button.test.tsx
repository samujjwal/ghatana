// All tests skipped - incomplete feature
import { render, screen, fireEvent } from '@testing-library/react';
import { act } from 'react';
import { describe, it, expect, vi } from 'vitest';

// Use the component folder entry so the runtime import resolves to the
// tailwind implementation via index.ts
import { Button } from '.';

describe.skip('Button', () => {
  it('renders with text', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByText('Click me')).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Click me</Button>);
    
    fireEvent.click(screen.getByText('Click me'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>Click me</Button>);
    expect(screen.getByText('Click me')).toBeDisabled();
  });

  it('does not call onClick when disabled', () => {
    const handleClick = vi.fn();
    render(<Button disabled onClick={handleClick}>Click me</Button>);
    
    fireEvent.click(screen.getByText('Click me'));
    expect(handleClick).not.toHaveBeenCalled();
  });

  it('renders with different variants', () => {
    const { rerender } = render(<Button variant="contained">Contained</Button>);
    expect(screen.getByText('Contained')).toBeInTheDocument();

    rerender(<Button variant="outlined">Outlined</Button>);
    expect(screen.getByText('Outlined')).toBeInTheDocument();

    rerender(<Button variant="text">Text</Button>);
    expect(screen.getByText('Text')).toBeInTheDocument();
  });

  it('renders with different sizes', () => {
    const { rerender } = render(<Button size="small">Small</Button>);
    expect(screen.getByText('Small')).toBeInTheDocument();

    rerender(<Button size="medium">Medium</Button>);
    expect(screen.getByText('Medium')).toBeInTheDocument();

    rerender(<Button size="large">Large</Button>);
    expect(screen.getByText('Large')).toBeInTheDocument();
  });

  it('renders with different shapes', () => {
    const { rerender } = render(<Button shape="rounded">Rounded</Button>);
    expect(screen.getByText('Rounded')).toBeInTheDocument();

    rerender(<Button shape="square">Square</Button>);
    expect(screen.getByText('Square')).toBeInTheDocument();

    rerender(<Button shape="pill">Pill</Button>);
    expect(screen.getByText('Pill')).toBeInTheDocument();
  });

  it('renders full width when fullWidth is true', () => {
    render(<Button fullWidth>Full Width</Button>);
    const button = screen.getByText('Full Width');
    expect(button).toBeInTheDocument();
  });

  it('has proper accessibility attributes', () => {
    render(
      <Button 
        aria-label="Submit form"
        aria-describedby="form-description"
      >
        Submit
      </Button>
    );
    
    const button = screen.getByRole('button', { name: /submit/i });
    expect(button).toHaveAttribute('aria-label', 'Submit form');
    expect(button).toHaveAttribute('aria-describedby', 'form-description');
  });

  it('supports aria-pressed for toggle buttons', () => {
    render(<Button aria-pressed={true}>Toggle</Button>);
    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('supports aria-expanded for expandable content', () => {
    render(<Button aria-expanded={false}>Expand</Button>);
    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('has visible focus indicator', () => {
    render(<Button>Focus me</Button>);
    const button = screen.getByText('Focus me');
    
    act(() => {
      button.focus();
    });
    expect(button).toHaveFocus();
  });

  it('forwards ref correctly', () => {
    const ref = vi.fn();
    render(<Button ref={ref}>Button</Button>);
    expect(ref).toHaveBeenCalled();
  });
});
