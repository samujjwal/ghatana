/**
 * @file Spinner.test.tsx
 * Unit tests for the Spinner atom component.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Spinner } from '../Spinner';

describe('Spinner', () => {
  it('renders into the DOM', () => {
    const { container } = render(<Spinner />);
    expect(container.firstChild).toBeTruthy();
  });

  it('has role="status" for accessibility', () => {
    render(<Spinner />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('includes a screen-reader accessible label', () => {
    render(<Spinner />);
    // The spinner should have either aria-label or an sr-only text
    const status = screen.getByRole('status');
    const ariaLabel = status.getAttribute('aria-label');
    const srText = status.textContent;
    expect(ariaLabel || srText).toBeTruthy();
  });

  it('accepts size prop without error', () => {
    (['sm', 'md', 'lg'] as const).forEach((size) => {
      const { container } = render(<Spinner size={size} />);
      expect(container.firstChild).toBeTruthy();
    });
  });

  it('accepts className forwarding', () => {
    const { container } = render(<Spinner className="custom-spinner" />);
    expect(container.querySelector('.custom-spinner')).toBeTruthy();
  });
});
