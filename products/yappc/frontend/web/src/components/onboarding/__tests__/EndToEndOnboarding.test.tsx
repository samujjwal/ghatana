/**
 * EndToEndOnboarding tests (F-Y033)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';

import { EndToEndOnboarding } from '../EndToEndOnboarding';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <MemoryRouter>{children}</MemoryRouter>;
}

describe('EndToEndOnboarding (F-Y033)', () => {
  it('renders the first onboarding step', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    expect(screen.getByText(/Create Account/i)).toBeInTheDocument();
  });

  it('renders all 7 journey steps in the step list', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    expect(screen.getByText(/Create Account/i)).toBeInTheDocument();
    expect(screen.getByText(/Workspace/i)).toBeInTheDocument();
    expect(screen.getByText(/Project/i)).toBeInTheDocument();
    expect(screen.getByText(/Intent/i)).toBeInTheDocument();
    expect(screen.getByText(/AI Assist/i)).toBeInTheDocument();
    expect(screen.getByText(/Approval/i)).toBeInTheDocument();
    expect(screen.getByText(/Deploy/i)).toBeInTheDocument();
  });

  it('advances to next step on Next click', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    const nextBtn = screen.getByRole('button', { name: /next/i });
    fireEvent.click(nextBtn);
    // Second step (workspace) should now be highlighted
    expect(screen.getByText(/Set up your first workspace/i)).toBeInTheDocument();
  });

  it('accepts an optional className', () => {
    const { container } = render(<Wrapper><EndToEndOnboarding className="test-class" /></Wrapper>);
    expect(container.firstChild).toHaveClass('test-class');
  });
});
