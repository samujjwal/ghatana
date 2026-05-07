import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';

import { WorkflowDesigner } from '../pages/WorkflowDesigner';

vi.mock('../features/workflow/components/WorkflowCanvas', () => ({
  WorkflowCanvas: () => <div data-testid="workflow-canvas">Workflow canvas</div>,
}));

describe('WorkflowDesigner', () => {
  it('renders the workflow designer shell and guidance copy', () => {
    render(
      <MemoryRouter>
        <WorkflowDesigner />
      </MemoryRouter>,
    );

    expect(screen.getByText('Advanced Pipeline Editor')).toBeInTheDocument();
    expect(
      screen.getByText(/Use this canvas when the flow itself needs structural changes/i),
    ).toBeInTheDocument();
  });

  it('renders back navigation and workflow canvas', () => {
    render(
      <MemoryRouter>
        <WorkflowDesigner />
      </MemoryRouter>,
    );

    const backLink = screen.getByRole('link', { name: /Back to pipelines/i });
    expect(backLink).toHaveAttribute('href', '/pipelines');
    expect(screen.getByTestId('workflow-canvas')).toBeInTheDocument();
  });
});
