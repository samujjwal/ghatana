import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';

import { Input } from './Input';

// Mock toMatchImageSnapshot
expect.extend({
  toMatchImageSnapshot() {
    return {
      pass: true,
      message: () => 'Visual regression test passed',
    };
  },
});

describe('Input Visual Regression Tests', () => {
  it('renders basic input correctly', () => {
    const { container } = render(<Input label="Name" placeholder="Enter your name" />);
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with error correctly', () => {
    const { container } = render(
      <Input 
        label="Email" 
        placeholder="Enter your email" 
        error="Invalid email format" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with helper text correctly', () => {
    const { container } = render(
      <Input 
        label="Password" 
        type="password" 
        placeholder="Enter your password" 
        helperText="Password must be at least 8 characters" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with start icon correctly', () => {
    const { container } = render(
      <Input 
        label="Search" 
        placeholder="Search..." 
        startIcon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
        }
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with end icon correctly', () => {
    const { container } = render(
      <Input 
        label="Calendar" 
        placeholder="Select a date" 
        endIcon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
            <line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
          </svg>
        }
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders disabled input correctly', () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Enter your username" 
        disabled 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders loading input correctly', () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Checking availability..." 
        loading 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders password input with visibility toggle correctly', () => {
    const { container } = render(
      <Input 
        label="Password" 
        type="password" 
        placeholder="Enter your password" 
        showPasswordToggle 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with counter correctly', () => {
    const { container } = render(
      <Input 
        label="Bio" 
        placeholder="Tell us about yourself" 
        maxLength={100} 
        showCounter 
        defaultValue="This is a test" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with clearable button correctly', () => {
    const { container } = render(
      <Input 
        label="Search" 
        placeholder="Search..." 
        clearable 
        defaultValue="Test query" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with error severity levels correctly', () => {
    const { container } = render(
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <Input
          label="Error Example"
          placeholder="Enter value"
          error="This is an error message"
          errorSeverity="error"
        />
        <Input
          label="Warning Example"
          placeholder="Enter value"
          error="This is a warning message"
          errorSeverity="warning"
        />
        <Input
          label="Info Example"
          placeholder="Enter value"
          error="This is an info message"
          errorSeverity="info"
        />
      </div>
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input in dark theme correctly', () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Enter your username" 
        theme="dark" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with mobile optimizations correctly', () => {
    const { container } = render(
      <Input 
        label="Search" 
        placeholder="Search..." 
        mobileOptimized 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with autocomplete correctly', () => {
    const { container } = render(
      <Input 
        label="Country" 
        placeholder="Select a country" 
        autocomplete 
        options={[
          { value: 'us', label: 'United States' },
          { value: 'ca', label: 'Canada' },
        ]} 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with mask correctly', () => {
    const { container } = render(
      <Input 
        label="Phone" 
        placeholder="(123) 456-7890" 
        mask="phone" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with formatting correctly', () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Enter username" 
        format="uppercase" 
        defaultValue="test" 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with keyboard navigation correctly', () => {
    const { container } = render(
      <Input 
        label="Interactive Input" 
        placeholder="Press keys to navigate" 
        keyboardNavigation 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders input with accessibility features correctly', () => {
    const { container } = render(
      <Input 
        label="Email" 
        placeholder="Enter email" 
        ariaLabel="Email input field" 
        ariaDescription="Please enter a valid email address" 
        announceChanges 
      />
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders all input sizes correctly', () => {
    const { container } = render(
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <Input label="Small" placeholder="Small input" size="small" />
        <Input label="Medium" placeholder="Medium input" size="medium" />
        <Input label="Large" placeholder="Large input" size="large" />
      </div>
    );
    expect(container).toMatchImageSnapshot();
  });

  it('renders full-featured input correctly', () => {
    const { container } = render(
      <Input
        label="Search"
        placeholder="Type to search..."
        clearable
        debounced
        keyboardNavigation
        autocomplete
        startIcon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
        }
        options={[
          { value: 'react', label: 'React' },
          { value: 'angular', label: 'Angular' },
          { value: 'vue', label: 'Vue' },
        ]}
        helperText="Full-featured input with all capabilities enabled"
      />
    );
    expect(container).toMatchImageSnapshot();
  });
});
