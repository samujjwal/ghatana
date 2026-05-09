import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { IntelligentOnboarding } from '../IntelligentOnboarding';

describe('IntelligentOnboarding', () => {
  const onComplete = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders Welcome step on load', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    expect(screen.getByText('Welcome to Yappc')).toBeInTheDocument();
    expect(screen.getByText(/guided recommendations/i)).toBeInTheDocument();
  });

  it('renders all step labels', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    expect(screen.getByText('Welcome')).toBeInTheDocument();
    expect(screen.getByText('Your Role')).toBeInTheDocument();
    expect(screen.getByText('Project Setup')).toBeInTheDocument();
    expect(screen.getByText('Ready to Go!')).toBeInTheDocument();
  });

  it('renders Your Name field on step 0', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    expect(screen.getByLabelText('Your Name')).toBeInTheDocument();
  });

  it('allows entering user name', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    const nameInput = screen.getByLabelText('Your Name');
    fireEvent.change(nameInput, { target: { value: 'Alice' } });
    expect(nameInput).toHaveValue('Alice');
  });

  it('Back button is disabled on step 0', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    expect(screen.getByRole('button', { name: /Back/i })).toBeDisabled();
  });

  it('advances to step 1 (role selection) on Next click', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    expect(screen.getByText("What's your primary role?")).toBeInTheDocument();
  });

  it('shows role options on step 1', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    expect(screen.getByText('Developer')).toBeInTheDocument();
    expect(screen.getByText('Designer')).toBeInTheDocument();
    expect(screen.getByText('Product Manager')).toBeInTheDocument();
    expect(screen.getByText('Full Stack')).toBeInTheDocument();
  });

  it('Next button is disabled on step 1 when no role selected', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 1
    expect(screen.getByRole('button', { name: /Next/i })).toBeDisabled();
  });

  it('enables Next on step 1 after selecting role', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 1
    fireEvent.click(screen.getByText('Developer')); // select role
    expect(screen.getByRole('button', { name: /Next/i })).not.toBeDisabled();
  });

  it('goes back from step 1 to step 0', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 1
    fireEvent.click(screen.getByRole('button', { name: /Back/i })); // back to step 0
    expect(screen.getByText('Welcome to Yappc')).toBeInTheDocument();
  });

  it('advances to step 2 (project setup) after selecting role', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 1
    fireEvent.click(screen.getByText('Developer'));
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 2
    expect(screen.getByText("What are you building?")).toBeInTheDocument();
  });

  it('shows project type options on step 2', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 1
    fireEvent.click(screen.getByText('Developer'));
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 2
    expect(screen.getByText('Web Application')).toBeInTheDocument();
    expect(screen.getByText('Mobile App')).toBeInTheDocument();
    expect(screen.getByText('API Service')).toBeInTheDocument();
  });

  it('advances to final step — shows Ready message', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 1
    fireEvent.click(screen.getByText('Developer'));
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 2
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 3
    expect(screen.getByText(/Your workspace is ready/i)).toBeInTheDocument();
  });

  it('shows Start Journey button on final step', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 1
    fireEvent.click(screen.getByText('Developer'));
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 2
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 3
    expect(screen.getByRole('button', { name: /Start Journey/i })).toBeInTheDocument();
  });

  it('calls onComplete when Start Journey clicked', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 1
    fireEvent.click(screen.getByText('Developer'));
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 2
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // step 3
    fireEvent.click(screen.getByRole('button', { name: /Start Journey/i }));
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it('shows experience level input on step 0', () => {
    render(<IntelligentOnboarding onComplete={onComplete} />);
    expect(screen.getByPlaceholderText(/e.g., Beginner/i)).toBeInTheDocument();
  });
});
