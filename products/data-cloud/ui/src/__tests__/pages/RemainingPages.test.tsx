/**
 * Tests for remaining Data Cloud pages:
 * WorkflowsPage, WorkflowDesigner, WorkflowList, NotFound
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

vi.mock('../../features/workflow/components/WorkflowCanvas', () => ({
  WorkflowCanvas: () => <div data-testid="workflow-canvas">workflow-canvas</div>,
}));

import { WorkflowsPage } from '../../pages/WorkflowsPage';
import { WorkflowDesigner } from '../../pages/WorkflowDesigner/index';
import { WorkflowList } from '../../pages/WorkflowList/index';
import { NotFound } from '../../pages/NotFound/index';


// ── WorkflowsPage ─────────────────────────────────────────────────────────────

describe('WorkflowsPage', () => {
  it('renders the workflows shell with search and create controls', () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Workflows' })).toBeInTheDocument();
    expect(screen.getByText(/Monitor and manage your automated workflows/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search workflows/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /New Workflow/i })).toBeInTheDocument();
  });
});

// ── WorkflowDesigner ──────────────────────────────────────────────────────────

describe('WorkflowDesigner', () => {
  it('delegates rendering to the workflow canvas component', () => {
    render(<WorkflowDesigner />, { wrapper: TestWrapper });

    expect(screen.getByTestId('workflow-canvas')).toBeInTheDocument();
  });
});

// ── WorkflowList ──────────────────────────────────────────────────────────────

describe('WorkflowList', () => {
  it('renders the workflow list heading and placeholder copy', () => {
    render(<WorkflowList />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Workflows' })).toBeInTheDocument();
    expect(screen.getByText(/Workflow list will be displayed here/i)).toBeInTheDocument();
  });
});

// ── NotFound ──────────────────────────────────────────────────────────────────

describe('NotFound', () => {
  it('renders the 404 boundary shell with a recovery action', () => {
    render(<NotFound />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: '404' })).toBeInTheDocument();
    expect(screen.getByText('Page Not Found')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Go to Home/i })).toBeInTheDocument();
  });
});
