/**
 * EndToEndOnboarding tests (F-Y033)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import React from 'react';
import { MemoryRouter } from 'react-router';

import { EndToEndOnboarding } from '../EndToEndOnboarding';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <MemoryRouter>{children}</MemoryRouter>;
}

describe('EndToEndOnboarding (F-Y033)', () => {
  it('renders the first onboarding step', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    expect(screen.getByRole('heading', { name: /Welcome to YAPPC/i })).toBeInTheDocument();
    expect(screen.getByText(/Step 1 of 7/i)).toBeInTheDocument();
  });

  it('renders all 7 journey steps in the step list', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    expect(screen.getByRole('button', { name: /Create Account/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Create Workspace/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Create Project/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Define Intent/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Guided Suggestions/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Review & Approve/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Deploy Preview/i })).toBeInTheDocument();
  });

  it('advances to next step on Next click', () => {
    render(<Wrapper><EndToEndOnboarding /></Wrapper>);
    const nextBtn = screen.getByRole('button', { name: /next/i });
    fireEvent.click(nextBtn);
    expect(screen.getByText(/Step 2 of 7/i)).toBeInTheDocument();
  });

  it('accepts an optional className', () => {
    const { container } = render(<Wrapper><EndToEndOnboarding className="test-class" /></Wrapper>);
    expect(container.firstChild).toHaveClass('test-class');
  });
});
