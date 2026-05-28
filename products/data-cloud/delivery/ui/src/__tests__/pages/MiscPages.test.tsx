/**
 * Tests for miscellaneous Data Cloud pages:
 * PluginDetailsPage, SmartWorkflowBuilder, IntelligentHub,
 * DataExplorer, CreateCollectionPage
 */
import { beforeEach, describe, it, expect, vi } from 'vitest';
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

const { getQualityAdvisoriesMock } = vi.hoisted(() => ({
  getQualityAdvisoriesMock: vi.fn(),
}));

vi.mock('../../api/ai-operations.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/ai-operations.service')>();
  return {
    ...actual,
    aiOperationsService: {
      ...actual.aiOperationsService,
      getQualityAdvisories: getQualityAdvisoriesMock,
    },
  };
});

vi.mock('../../api/surfaces.service', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/surfaces.service')>();
  return {
    ...actual,
    useSurfaceRegistry: () => ({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-misc-pages',
        tenantId: TEST_TENANT_ID,
        surfaces: [
          {
            key: 'ai_assist',
            label: 'AI Assist',
            status: 'UNAVAILABLE',
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

    expect(screen.getByRole('heading', { name: 'Workflow Builder' })).toBeInTheDocument();
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
    expect(screen.getByText('Next action')).toBeInTheDocument();
    expect(screen.getByText('Ask a question')).toBeInTheDocument();
    expect(screen.getByText('Build an automated flow')).toBeInTheDocument();
    expect(screen.getByText('Platform snapshot')).toBeInTheDocument();
  });
});

// ── DataExplorer ──────────────────────────────────────────────────────────────

describe('DataExplorer', () => {
  beforeEach(() => {
    getQualityAdvisoriesMock.mockReset();
    getQualityAdvisoriesMock.mockRejectedValue({ status: 404 });
  });

  it('uses backend quality advisory content when advisory payload is available', async () => {
    getQualityAdvisoriesMock.mockResolvedValueOnce({
      collectionId: 'products',
      tenantId: TEST_TENANT_ID,
      overallScore: 0.72,
      scoreBand: 'medium',
      advisories: [
        {
          id: 'adv-1',
          type: 'completeness',
          title: 'Missing mandatory values',
          description: 'Critical completeness gaps detected.',
          affectedCount: 23,
          confidence: 0.91,
          suggestedAction: 'Fill required fields for primary key attributes before publishing.',
        },
      ],
      generatedAt: '2026-05-28T10:00:00Z',
      modelVersion: 'quality-v2',
    });

    render(<DataExplorer />, { wrapper: TestWrapper });

    expect(
      await screen.findByText('Fill required fields for primary key attributes before publishing.')
    ).toBeInTheDocument();
    expect(getQualityAdvisoriesMock).toHaveBeenCalled();
  });

  it('shows backend advisory unavailable message instead of deriving client-side heuristic content', async () => {
    render(<DataExplorer />, { wrapper: TestWrapper });

    expect(
      await screen.findByText(
        'No backend quality advisory is currently available for this collection. Retry after the quality advisory pipeline finishes.'
      )
    ).toBeInTheDocument();
  });

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

  it('supports keyboard activation for always-visible row actions', async () => {
    const user = userEvent.setup();

    render(<DataExplorer />, { wrapper: TestWrapper });

    const editButton = await screen.findByRole('button', { name: /Edit Products/i });
    editButton.focus();
    await user.keyboard('{Enter}');

    await waitFor(() => {
      expect(window.location.pathname).toBe('/data/col-001/edit');
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
