/**
 * SprintPlanningAidPanel tests (AI-Y10)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { SprintPlanningAidPanel } from '../SprintPlanningAidPanel';
import type { SprintPlanningAidData } from '../SprintPlanningAidPanel';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(data),
  } as Response);
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

const sampleData: SprintPlanningAidData = {
  sprintId: 'sp-1',
  estimatedVelocity: 42,
  capacityPoints: 40,
  overloadRisk: 'high',
  risks: [
    { id: 'r1', description: 'Story SP-44 has no acceptance criteria', severity: 'high' },
    { id: 'r2', description: '3 stories blocked by external dependency', severity: 'medium' },
  ],
  modelSource: 'model',
  confidence: 0.78,
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('SprintPlanningAidPanel (AI-Y10)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="proj-1" sprintId="sp-1" />
      </Wrapper>
    );

    expect(screen.getByTestId('sprint-aid-loading')).toBeInTheDocument();
  });

  it('renders velocity summary and risk items', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="proj-1" sprintId="sp-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('sprint-aid-panel')).toBeInTheDocument();
    expect(screen.getByTestId('sprint-velocity-summary')).toBeInTheDocument();
    expect(screen.getByText(/Estimated velocity: 42 pts/)).toBeInTheDocument();
    expect(screen.getByTestId('sprint-risk-r1')).toBeInTheDocument();
    expect(screen.getByTestId('sprint-risk-r2')).toBeInTheDocument();
  });

  it('shows "no risks" message when risks array is empty', async () => {
    mockFetch.mockReturnValue(jsonOk({ ...sampleData, risks: [] }));

    render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="proj-1" sprintId="sp-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('sprint-no-risks')).toBeInTheDocument();
  });

  it('shows AI assist label with confidence', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="proj-1" sprintId="sp-1" />
      </Wrapper>
    );

    await screen.findByTestId('sprint-aid-panel');
    expect(screen.getByTestId('ai-assist-label')).toBeInTheDocument();
    expect(screen.getByTestId('ai-assist-label')).toHaveAttribute('data-source', 'model');
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="proj-1" sprintId="sp-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('sprint-aid-error')).toBeInTheDocument();
  });

  it('renders nothing when projectId or sprintId is empty', () => {
    const { container } = render(
      <Wrapper>
        <SprintPlanningAidPanel projectId="" sprintId="" />
      </Wrapper>
    );
    expect(container.firstChild).toBeNull();
    expect(mockFetch).not.toHaveBeenCalled();
  });
});
