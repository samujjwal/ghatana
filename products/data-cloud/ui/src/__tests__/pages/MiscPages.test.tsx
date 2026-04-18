/**
 * Tests for miscellaneous Data Cloud pages:
 * PluginDetailsPage, SmartWorkflowBuilder, IntelligentHub,
 * DataExplorer, CreateCollectionPage
 */
import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';
import { smartWorkflowGenerationBoundary } from '@/components/common/unsupportedSurfaceRegistry';
import { PluginDetailsPage } from '../../pages/PluginDetailsPage';
import { SmartWorkflowBuilder } from '../../pages/SmartWorkflowBuilder';
import { IntelligentHub } from '../../pages/IntelligentHub';
import { DataExplorer } from '../../pages/DataExplorer';
import { CreateCollectionPage } from '../../pages/CreateCollectionPage';

vi.mock('../../api/capabilities.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/capabilities.service')>();
  return {
    ...actual,
    useCapabilityRegistry: () => ({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-misc-pages',
        tenantId: TEST_TENANT_ID,
        capabilities: [
          {
            key: 'ai_assist',
            label: 'AI Assist',
            status: 'unavailable',
            summary: 'UNAVAILABLE',
            detail: smartWorkflowGenerationBoundary.details[1],
            rawValue: 'UNAVAILABLE',
          },
        ],
      },
    }),
  };
});


// ── PluginDetailsPage ─────────────────────────────────────────────────────────

describe('PluginDetailsPage', () => {
  it('shows the not-found boundary when no plugin id is available', () => {
    render(<PluginDetailsPage />, { wrapper: TestWrapper });

    expect(screen.getByText('Plugin Not Found')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Back to Plugins/i })).toBeInTheDocument();
  });
});

// ── SmartWorkflowBuilder ──────────────────────────────────────────────────────

describe('SmartWorkflowBuilder', () => {
  it('renders the smart workflow shell with natural-language input controls', () => {
    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Smart Workflow Builder' })).toBeInTheDocument();
    expect(screen.getAllByText(/Describe your pipeline/i).length).toBeGreaterThan(0);
    expect(screen.getByPlaceholderText(/Load data from S3/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Generate Draft/i })).toBeDisabled();
    expect(screen.getByText(/Try an example:/i)).toBeInTheDocument();
  });

  it('surfaces an explicit boundary instead of a fabricated generated workflow', async () => {
    const user = userEvent.setup();

    render(<SmartWorkflowBuilder />, { wrapper: TestWrapper });

    await user.type(
      screen.getByPlaceholderText(/Load data from S3/i),
      'Load data from S3, clean email addresses, save to PostgreSQL',
    );
    await user.click(screen.getByRole('button', { name: /Generate Draft/i }));

    expect(await screen.findByText(smartWorkflowGenerationBoundary.title)).toBeInTheDocument();
    expect(screen.getByText(smartWorkflowGenerationBoundary.summary)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Generated Pipeline/i })).not.toBeInTheDocument();
  });
});

// ── IntelligentHub ────────────────────────────────────────────────────────────

describe('IntelligentHub', () => {
  it('renders the unified workspace shell with ask-anything and quick actions', () => {
    render(<IntelligentHub />, { wrapper: TestWrapper });

    expect(screen.getByPlaceholderText(/What do you need to do/i)).toBeInTheDocument();
    expect(screen.getByText('Start with an outcome')).toBeInTheDocument();
    expect(screen.getByText('Insights')).toBeInTheDocument();
    expect(screen.getByText('Recommended Next Steps')).toBeInTheDocument();
  });
});

// ── DataExplorer ──────────────────────────────────────────────────────────────

describe('DataExplorer', () => {
  it('renders the data explorer shell', () => {
    render(<DataExplorer />, { wrapper: TestWrapper });

    expect(screen.getByText(/Data Explorer/i)).toBeInTheDocument();
  });

  it('normalizes unsupported view params back to a safe default', async () => {
    window.history.pushState({}, '', '/data?view=cost');

    render(<DataExplorer />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(window.location.search).toBe('?view=table');
    });
  });

  it('renders live lineage preview data when the lineage view is selected', async () => {
    window.history.pushState({}, '', '/data?view=lineage');

    render(<DataExplorer />, { wrapper: TestWrapper });

    const collection = await screen.findByText(/Products/i);
    fireEvent.click(collection);

    await waitFor(() => {
      expect(screen.getByText(/Live upstream and downstream lineage/i)).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getByText(/Affected datasets:/i)).toBeInTheDocument();
    });
  });
});

// ── CreateCollectionPage ──────────────────────────────────────────────────────

describe('CreateCollectionPage', () => {
  it('renders the create-collection shell and form container', () => {
    render(<CreateCollectionPage />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Create New Collection' })).toBeInTheDocument();
    expect(screen.getByText(/Define a new collection and its schema/i)).toBeInTheDocument();
  });

  it('has form submission elements', () => {
    render(<CreateCollectionPage />, { wrapper: TestWrapper });
    const interactive = document.querySelectorAll('button, input, textarea, select');
    expect(interactive.length).toBeGreaterThan(0);
  });
});
