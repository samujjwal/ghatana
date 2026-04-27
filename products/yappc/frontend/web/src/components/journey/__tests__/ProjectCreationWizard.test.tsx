import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ProjectCreationWizard } from '../ProjectCreationWizard';

describe('ProjectCreationWizard', () => {
  const onComplete = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the wizard on step 0 - Project Basics', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    expect(screen.getByText('Project Basics')).toBeInTheDocument();
  });

  it('renders all step labels in the stepper', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    expect(screen.getByText('Project Basics')).toBeInTheDocument();
    expect(screen.getByText('Choose Type')).toBeInTheDocument();
    expect(screen.getByText('Select Template')).toBeInTheDocument();
    expect(screen.getByText('Features')).toBeInTheDocument();
    expect(screen.getByText('Create')).toBeInTheDocument();
  });

  it('renders project name text field on step 0', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    expect(screen.getByPlaceholderText(/My Awesome Project/i)).toBeInTheDocument();
  });

  it('allows entering a project name', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    const nameInput = screen.getByPlaceholderText(/My Awesome Project/i);
    fireEvent.change(nameInput, { target: { value: 'Test Project' } });
    expect(nameInput).toHaveValue('Test Project');
  });

  it('allows entering a project description', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    const descInput = screen.getByPlaceholderText(/What are you building/i);
    fireEvent.change(descInput, { target: { value: 'A great project' } });
    expect(descInput).toHaveValue('A great project');
  });

  it('Next button is disabled on step 0 when no project name', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    // Button renders but is disabled because projectData.name is empty
    const nextBtn = screen.getByRole('button', { name: /Next/i });
    expect(nextBtn).toBeDisabled();
  });

  it('Back button is present but disabled on step 0', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    const backBtn = screen.getByRole('button', { name: /Back/i });
    expect(backBtn).toBeDisabled();
  });

  it('advances to step 1 after entering project name and clicking Next', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    // Must enter name first because Next is disabled without it
    fireEvent.change(screen.getByPlaceholderText(/My Awesome Project/i), {
      target: { value: 'My Project' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    expect(screen.getByText('What type of project?')).toBeInTheDocument();
  });

  const advanceToStep = (step: number) => {
    // Enter project name so Next is enabled on step 0
    fireEvent.change(screen.getByPlaceholderText(/My Awesome Project/i), {
      target: { value: 'My Project' },
    });
    for (let i = 0; i < step; i++) {
      fireEvent.click(screen.getByRole('button', { name: /Next/i }));
    }
  };

  it('shows project type options on step 1', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(1);
    expect(screen.getByText('Web Application')).toBeInTheDocument();
    expect(screen.getByText('API Service')).toBeInTheDocument();
    expect(screen.getByText('Mobile App')).toBeInTheDocument();
    expect(screen.getByText('Component Library')).toBeInTheDocument();
  });

  it('goes back from step 1 to step 0 on Back click', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(1);
    fireEvent.click(screen.getByRole('button', { name: /Back/i }));
    expect(screen.getByPlaceholderText(/My Awesome Project/i)).toBeInTheDocument();
  });

  it('selects a project type and advances to templates on step 2', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(1);
    fireEvent.click(screen.getByText('Web Application')); // select type
    fireEvent.click(screen.getByRole('button', { name: /Next/i })); // to step 2
    expect(screen.getByText('Minimal')).toBeInTheDocument();
    expect(screen.getByText('Full Stack')).toBeInTheDocument();
    expect(screen.getByText('Enterprise')).toBeInTheDocument();
    expect(screen.getByText('Custom')).toBeInTheDocument();
  });

  it('advances to features step 3', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(3);
    expect(screen.getByText('Authentication')).toBeInTheDocument();
    expect(screen.getByText('Database')).toBeInTheDocument();
    expect(screen.getByText('Testing Suite')).toBeInTheDocument();
  });

  it('toggles feature selection on step 3', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(3);
    // Click authentication feature card to toggle it
    const authText = screen.getByText('Authentication');
    fireEvent.click(authText);
    // Feature should still be in DOM after toggle
    expect(screen.getByText('Authentication')).toBeInTheDocument();
  });

  it('advances to review step 4 — shows Ready to Create heading', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(4);
    expect(screen.getByText('Ready to Create!')).toBeInTheDocument();
  });

  it('shows Create Project button on final step', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(4);
    expect(screen.getByRole('button', { name: /Create Project/i })).toBeInTheDocument();
  });

  it('calls onComplete when Create Project clicked on final step', () => {
    render(<ProjectCreationWizard onComplete={onComplete} />);
    advanceToStep(4);
    fireEvent.click(screen.getByRole('button', { name: /Create Project/i }));
    expect(onComplete).toHaveBeenCalledTimes(1);
    expect(onComplete).toHaveBeenCalledWith(expect.stringContaining('project-'));
  });
});
