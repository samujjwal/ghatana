import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RunTable } from '@/components/monitoring/RunTable';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import type { PipelineRun } from '@/api/aep.api';
import type { AiSuggestion } from '@/components/monitoring/AiSuggestionsPanel';

const RUN: PipelineRun = {
  id: 'run-001',
  pipelineId: 'pipe-001',
  pipelineName: 'Fraud Detection',
  status: 'RUNNING',
  startedAt: new Date().toISOString(),
  eventsProcessed: 120,
  errorsCount: 2,
};

const SUGGESTION: AiSuggestion = {
  id: 'suggestion-001',
  runId: 'run-001',
  type: 'anomaly',
  severity: 'high',
  message: 'Error rate spike detected in the validation stage.',
  confidence: 0.61,
  rationale: 'Validation errors increased beyond the last healthy baseline during the most recent run window.',
  sources: [{ label: 'Validation anomaly sample', href: 'https://example.com/anomaly' }],
};

describe('RunTable', () => {
  it('shows an explainable assist narrative for run suggestions', async () => {
    const user = userEvent.setup();

    render(
      <RunTable runs={[RUN]} aiSuggestions={[SUGGESTION]} />,
      { wrapper: createAepTestWrapper() },
    );

    await user.click(screen.getByRole('button', { name: /anomaly suggestion/i }));
    await user.click(screen.getByRole('button', { name: /show reasoning/i }));

    expect(screen.getByText('Assist narrative')).toBeInTheDocument();
    expect(screen.getByText(/review before apply/i)).toBeInTheDocument();
    expect(screen.getByText(/validation errors increased/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /validation anomaly sample/i })).toBeInTheDocument();
  });

  it('exposes a false-positive feedback action for anomaly suggestions', async () => {
    const user = userEvent.setup();
    const onMarkFalsePositive = vi.fn();

    render(
      <RunTable
        runs={[RUN]}
        aiSuggestions={[{ ...SUGGESTION, anomalyId: 'anomaly-001' }]}
        onMarkFalsePositive={onMarkFalsePositive}
      />,
      { wrapper: createAepTestWrapper() },
    );

    await user.click(screen.getByRole('button', { name: /anomaly suggestion/i }));
    await user.click(screen.getByRole('button', { name: /show reasoning/i }));
    await user.click(screen.getByRole('button', { name: /mark as not an anomaly/i }));

    expect(onMarkFalsePositive).toHaveBeenCalledWith(
      expect.objectContaining({ anomalyId: 'anomaly-001' }),
    );
  });
});
