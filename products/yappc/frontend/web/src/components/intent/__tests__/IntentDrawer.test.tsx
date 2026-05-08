import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router';
import React from 'react';
import { IntentDrawer } from '../IntentDrawer';

// Mock child form components
vi.mock('../IdeaBriefForm', () => ({
  IdeaBriefForm: ({ onSubmit }: { onSubmit: (data: unknown) => Promise<void> }) => (
    <div data-testid="idea-brief-form">
      {React.createElement('button', { onClick: () => onSubmit({ title: 'Test Idea' }) }, 'Save Idea')}
    </div>
  ),
}));

vi.mock('../ResearchPackEditor', () => ({
  ResearchPackEditor: ({ onSubmit }: { onSubmit: (data: unknown) => Promise<void> }) => (
    <div data-testid="research-pack-editor">
      {React.createElement('button', { onClick: () => onSubmit({ findings: 'Test' }) }, 'Save Research')}
    </div>
  ),
}));

vi.mock('../ProblemStatementEditor', () => ({
  ProblemStatementEditor: ({ onSubmit }: { onSubmit: (data: unknown) => Promise<void> }) => (
    <div data-testid="problem-statement-editor">
      {React.createElement('button', { onClick: () => onSubmit({ problem: 'Test problem' }) }, 'Save Problem')}
    </div>
  ),
}));

const defaultProps = {
  onSave: vi.fn().mockResolvedValue({ projectId: 'proj-123' }),
  onAIAssist: vi.fn().mockResolvedValue(null),
};

const renderWithDrawer = (drawerParam: string | null, props = defaultProps) => {
  const path = drawerParam ? `/?drawer=${drawerParam}` : '/';
  return render(
    <MemoryRouter initialEntries={[path]}>
      <IntentDrawer {...props} />
    </MemoryRouter>
  );
};

describe('IntentDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when no drawer param', () => {
    const { container } = renderWithDrawer(null);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing for invalid drawer param', () => {
    const { container } = renderWithDrawer('invalid');
    expect(container.firstChild).toBeNull();
  });

  it('renders Idea Brief heading when drawer=idea', () => {
    renderWithDrawer('idea');
    expect(screen.getByRole('heading', { name: 'Idea Brief' })).toBeInTheDocument();
  });

  it('renders IdeaBriefForm when drawer=idea', () => {
    renderWithDrawer('idea');
    expect(screen.getByTestId('idea-brief-form')).toBeInTheDocument();
  });

  it('renders Research Pack heading when drawer=research', () => {
    renderWithDrawer('research');
    expect(screen.getByRole('heading', { name: 'Research Pack' })).toBeInTheDocument();
  });

  it('renders ResearchPackEditor when drawer=research', () => {
    renderWithDrawer('research');
    expect(screen.getByTestId('research-pack-editor')).toBeInTheDocument();
  });

  it('renders Problem Statement heading when drawer=problem', () => {
    renderWithDrawer('problem');
    expect(screen.getByRole('heading', { name: 'Problem Statement' })).toBeInTheDocument();
  });

  it('renders ProblemStatementEditor when drawer=problem', () => {
    renderWithDrawer('problem');
    expect(screen.getByTestId('problem-statement-editor')).toBeInTheDocument();
  });

  it('renders a dialog with aria-label', () => {
    renderWithDrawer('idea');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('renders the drawer title heading', () => {
    renderWithDrawer('idea');
    expect(screen.getByRole('heading', { name: 'Idea Brief' })).toBeInTheDocument();
  });

  it('renders close button with aria-label', () => {
    renderWithDrawer('idea');
    expect(screen.getByRole('button', { name: /Close drawer/i })).toBeInTheDocument();
  });

  it('renders navigation pills for all three drawer types', () => {
    renderWithDrawer('idea');
    // All 3 nav pills should be visible
    expect(screen.getAllByRole('button')).not.toHaveLength(0);
  });

  it('shows the idea drawer description', () => {
    renderWithDrawer('idea');
    expect(screen.getByText('Capture the initial idea with target users and value proposition')).toBeInTheDocument();
  });

  it('shows a backdrop overlay', () => {
    const { container } = renderWithDrawer('idea');
    const backdrop = container.querySelector('[aria-hidden="true"]');
    expect(backdrop).not.toBeNull();
  });

  it('calls onSave with correct kind when form is submitted (idea)', async () => {
    const onSave = vi.fn().mockResolvedValue({ projectId: 'p1' });
    renderWithDrawer('idea', { ...defaultProps, onSave });
    fireEvent.click(screen.getByText('Save Idea'));
    await vi.waitFor(() => {
      expect(onSave).toHaveBeenCalledWith(expect.anything(), { title: 'Test Idea' });
    }, { timeout: 2000 });
  });
});
