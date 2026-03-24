import { render, screen } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';
import { describe, it, expect, vi } from 'vitest';

import { Input } from './Input';

// Add jest-axe matchers
expect.extend(toHaveNoViolations);

describe('Input Accessibility', () => {
  it('should have no accessibility violations for basic input', async () => {
    const { container } = render(
      <Input label="Name" placeholder="Enter your name" />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with error state', async () => {
    const { container } = render(
      <Input 
        label="Email" 
        placeholder="Enter your email" 
        error="Invalid email format" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with helper text', async () => {
    const { container } = render(
      <Input 
        label="Password" 
        type="password" 
        placeholder="Enter your password" 
        helperText="Password must be at least 8 characters" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with password toggle', async () => {
    const { container } = render(
      <Input 
        label="Password" 
        type="password" 
        placeholder="Enter your password" 
        showPasswordToggle 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with clear button', async () => {
    const { container } = render(
      <Input 
        label="Search" 
        placeholder="Search..." 
        clearable 
        defaultValue="test" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with loading state', async () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Checking availability..." 
        loading 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with autocomplete', async () => {
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
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with input masking', async () => {
    const { container } = render(
      <Input 
        label="Phone" 
        placeholder="(123) 456-7890" 
        mask="phone" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with formatting', async () => {
    const { container } = render(
      <Input 
        label="Username" 
        placeholder="Enter username" 
        format="uppercase" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with keyboard navigation', async () => {
    const { container } = render(
      <Input 
        label="Interactive Input" 
        placeholder="Press keys to navigate" 
        keyboardNavigation 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations in dark theme', async () => {
    const { container } = render(
      <Input 
        label="Dark Theme Input" 
        placeholder="Enter text..." 
        theme="dark" 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have no accessibility violations with mobile optimization', async () => {
    const { container } = render(
      <Input 
        label="Mobile Input" 
        placeholder="Enter text..." 
        mobileOptimized 
      />
    );
    
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('should have proper ARIA attributes', () => {
    render(
      <Input 
        label="Email" 
        placeholder="Enter email" 
        error="Invalid email" 
        ariaLabel="Email input field" 
        ariaDescription="Please enter a valid email address" 
      />
    );
    
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-label', 'Email input field');
    
    // Check that aria-describedby points to the error message
    const describedById = input.getAttribute('aria-describedby');
    expect(describedById).toBeTruthy();
    expect(screen.getByText('Invalid email').id).toBe(describedById);
  });
  
  it('should announce changes to screen readers when enabled', () => {
    render(
      <Input 
        label="Announcer" 
        placeholder="Type something" 
        announceChanges 
        defaultValue="Initial value" 
      />
    );
    
    // Check for the presence of the live region
    const liveRegion = screen.getByRole('status');
    expect(liveRegion).toBeInTheDocument();
    expect(liveRegion).toHaveAttribute('aria-live', 'polite');
  });
  
  it('should have proper focus management', () => {
    render(
      <Input 
        label="Focus Test" 
        placeholder="Click to focus" 
      />
    );
    
    const input = screen.getByLabelText('Focus Test');
    input.focus();
    expect(document.activeElement).toBe(input);
  });
});
