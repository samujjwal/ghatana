/**
 * Tests for ui/ primitive components:
 * Card, Input, Select, Textarea, ProgressBar
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Card } from '../Card';
import { Input } from '../Input';
import { Select } from '../Select';
import { Textarea } from '../Textarea';
import { ProgressBar } from '../ProgressBar';

// ─── Card ─────────────────────────────────────────────────────────────────────

describe('Card', () => {
  it('renders children', () => {
    render(<Card>Card Content</Card>);
    expect(screen.getByText('Card Content')).toBeTruthy();
  });

  it('renders header when provided', () => {
    render(<Card header={<span>Header</span>}>Body</Card>);
    expect(screen.getByText('Header')).toBeTruthy();
  });

  it('renders footer when provided', () => {
    render(<Card footer={<span>Footer</span>}>Body</Card>);
    expect(screen.getByText('Footer')).toBeTruthy();
  });

  it('applies custom className', () => {
    const { container } = render(
      <Card className="my-card">Content</Card>
    );
    const div = container.querySelector('.my-card');
    expect(div).toBeTruthy();
  });

  it('renders elevated variant', () => {
    render(<Card variant="elevated">Elevated</Card>);
    expect(screen.getByText('Elevated')).toBeTruthy();
  });

  it('renders outlined variant', () => {
    render(<Card variant="outlined">Outlined</Card>);
    expect(screen.getByText('Outlined')).toBeTruthy();
  });

  it('renders filled variant', () => {
    render(<Card variant="filled">Filled</Card>);
    expect(screen.getByText('Filled')).toBeTruthy();
  });

  it('renders sm size', () => {
    render(<Card size="sm">Small</Card>);
    expect(screen.getByText('Small')).toBeTruthy();
  });

  it('renders lg size', () => {
    render(<Card size="lg">Large</Card>);
    expect(screen.getByText('Large')).toBeTruthy();
  });

  it('is clickable when clickable=true', () => {
    const onClick = vi.fn();
    render(<Card clickable onClick={onClick}>Click me</Card>);
    fireEvent.click(screen.getByText('Click me'));
    expect(onClick).toHaveBeenCalledOnce();
  });
});

// ─── Input ────────────────────────────────────────────────────────────────────

describe('Input', () => {
  it('renders label', () => {
    render(<Input label="Username" />);
    expect(screen.getByText('Username')).toBeTruthy();
  });

  it('renders input element', () => {
    render(<Input label="Email" placeholder="Enter email" />);
    expect(screen.getByPlaceholderText('Enter email')).toBeTruthy();
  });

  it('renders helper text', () => {
    render(<Input label="Name" helperText="Enter your full name" />);
    expect(screen.getByText('Enter your full name')).toBeTruthy();
  });

  it('renders error message', () => {
    render(<Input label="Email" error="Invalid email" />);
    expect(screen.getByText('Invalid email')).toBeTruthy();
  });

  it('renders success message', () => {
    render(<Input label="Email" success="Looks good!" />);
    expect(screen.getByText('Looks good!')).toBeTruthy();
  });

  it('renders warning message', () => {
    render(<Input label="Name" warning="Name too short" />);
    expect(screen.getByText('Name too short')).toBeTruthy();
  });

  it('calls onChange when user types', () => {
    const onChange = vi.fn();
    render(<Input label="Text" onChange={onChange} />);
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'hello' } });
    expect(onChange).toHaveBeenCalledOnce();
  });

  it('is disabled when disabled=true', () => {
    render(<Input label="Disabled" disabled />);
    const input = screen.getByRole('textbox');
    expect(input).toBeDisabled();
  });

  it('renders required indicator', () => {
    render(<Input label="Required Field" required />);
    // * indicator appears
    expect(screen.getByText('*')).toBeTruthy();
  });

  it('renders sm size', () => {
    render(<Input label="Small" size="sm" />);
    expect(screen.getByRole('textbox')).toBeTruthy();
  });
});

// ─── Select ───────────────────────────────────────────────────────────────────

describe('Select', () => {
  const options = [
    { value: 'a', label: 'Option A' },
    { value: 'b', label: 'Option B' },
    { value: 'c', label: 'Option C', disabled: true },
  ];

  it('renders label', () => {
    render(<Select label="Category" options={options} />);
    expect(screen.getByText('Category')).toBeTruthy();
  });

  it('renders all options', () => {
    render(<Select label="Cat" options={options} />);
    expect(screen.getByText('Option A')).toBeTruthy();
    expect(screen.getByText('Option B')).toBeTruthy();
    expect(screen.getByText('Option C')).toBeTruthy();
  });

  it('renders placeholder option', () => {
    render(<Select label="Cat" options={options} placeholder="Select..." />);
    expect(screen.getByText('Select...')).toBeTruthy();
  });

  it('renders error message', () => {
    render(<Select label="Cat" options={options} error="Required" />);
    expect(screen.getByText('Required')).toBeTruthy();
  });

  it('renders helper text', () => {
    render(<Select label="Cat" options={options} helperText="Pick one" />);
    expect(screen.getByText('Pick one')).toBeTruthy();
  });

  it('calls onChange when selection changes', () => {
    const onChange = vi.fn();
    render(<Select label="Cat" options={options} onChange={onChange} />);
    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: 'b' } });
    expect(onChange).toHaveBeenCalledOnce();
  });

  it('is disabled when disabled=true', () => {
    render(<Select label="Cat" options={options} disabled />);
    const select = screen.getByRole('combobox');
    expect(select).toBeDisabled();
  });
});

// ─── Textarea ─────────────────────────────────────────────────────────────────

describe('Textarea', () => {
  it('renders label', () => {
    render(<Textarea label="Notes" />);
    expect(screen.getByText('Notes')).toBeTruthy();
  });

  it('renders textarea element', () => {
    render(<Textarea label="Bio" placeholder="Enter bio..." />);
    expect(screen.getByPlaceholderText('Enter bio...')).toBeTruthy();
  });

  it('renders error message', () => {
    render(<Textarea label="Notes" error="Too long" />);
    expect(screen.getByText('Too long')).toBeTruthy();
  });

  it('renders helper text', () => {
    render(<Textarea label="Notes" helperText="Max 200 chars" />);
    expect(screen.getByText('Max 200 chars')).toBeTruthy();
  });

  it('shows char count when showCharCount=true with maxLength', () => {
    render(<Textarea label="Notes" showCharCount maxLength={100} />);
    // Shows "0/100"
    expect(screen.getByText('0/100')).toBeTruthy();
  });

  it('calls onChange when text changes', () => {
    const onChange = vi.fn();
    render(<Textarea label="Notes" onChange={onChange} />);
    const textarea = screen.getByRole('textbox');
    fireEvent.change(textarea, { target: { value: 'hello' } });
    expect(onChange).toHaveBeenCalledOnce();
  });

  it('is disabled when disabled=true', () => {
    render(<Textarea label="Notes" disabled />);
    const textarea = screen.getByRole('textbox');
    expect(textarea).toBeDisabled();
  });
});

// ─── ProgressBar ──────────────────────────────────────────────────────────────

describe('ProgressBar', () => {
  it('renders without crashing', () => {
    const { container } = render(<ProgressBar percentage={50} />);
    expect(container.firstChild).toBeTruthy();
  });

  it('clamps percentage at 0', () => {
    const { container } = render(<ProgressBar percentage={-10} />);
    const bar = container.querySelector('[style]');
    expect(bar?.getAttribute('style')).toContain('0%');
  });

  it('clamps percentage at 100', () => {
    const { container } = render(<ProgressBar percentage={150} />);
    const bar = container.querySelector('[style]');
    expect(bar?.getAttribute('style')).toContain('100%');
  });

  it('applies correct percentage style', () => {
    const { container } = render(<ProgressBar percentage={75} />);
    const bar = container.querySelector('[style]');
    expect(bar?.getAttribute('style')).toContain('75%');
  });

  it('renders green at 90%+', () => {
    const { container } = render(<ProgressBar percentage={95} />);
    const bar = container.querySelector('.bg-green-600');
    expect(bar).toBeTruthy();
  });

  it('renders red below 70%', () => {
    const { container } = render(<ProgressBar percentage={50} />);
    const bar = container.querySelector('.bg-red-600');
    expect(bar).toBeTruthy();
  });

  it('renders yellow between 70-89%', () => {
    const { container } = render(<ProgressBar percentage={80} />);
    const bar = container.querySelector('.bg-yellow-500');
    expect(bar).toBeTruthy();
  });

  it('accepts custom height class', () => {
    const { container } = render(<ProgressBar percentage={50} height="h-4" />);
    const wrapper = container.querySelector('.h-4');
    expect(wrapper).toBeTruthy();
  });
});
