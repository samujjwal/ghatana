/**
 * Tests for PipelineDryRunDialog (F-014).
 * Verifies that the dialog renders the dry-run results, requires acknowledgement,
 * and only enables Publish after the operator has checked the acknowledgement box.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PipelineDryRunDialog } from '../PipelineDryRunDialog';
import * as pipelineApi from '@/api/pipeline.api';
import type { PipelineDryRunReport } from '@/api/pipeline.api';

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const PASSED_REPORT: PipelineDryRunReport = {
  pipelineId: 'pipe-1',
  tenantId: 'acme-corp',
  passed: true,
  agentSet: ['NlpAgent', 'PolicyAgent'],
  policySet: ['pii-enforcement:BLOCK', 'tenant-isolation:ENFORCED'],
  complianceBundle: { piiEnforcement: 'BLOCK', auditLogEnabled: true, killSwitchEnabled: true },
  validationErrors: [],
  warnings: ['Kill-switch is not enabled in staging'],
  acknowledgementRequired: true,
  timestamp: '2026-04-27T00:00:00Z',
};

const FAILED_REPORT: PipelineDryRunReport = {
  ...PASSED_REPORT,
  passed: false,
  validationErrors: ['Stage "ingest" has no agents', 'Cyclical dependency detected'],
};

describe('PipelineDryRunDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders dialog when open=true', () => {
    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
        }),
      ),
    );

    expect(screen.getByRole('dialog')).toBeDefined();
    expect(screen.getByText(/pre-flight dry-run/i)).toBeDefined();
    expect(screen.getByText(/My Pipeline/)).toBeDefined();
  });

  it('returns null when open=false', () => {
    const { container } = render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: false,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
        }),
      ),
    );

    expect(container.firstChild).toBeNull();
  });

  it('shows dry-run result when run button clicked and backend responds', async () => {
    vi.spyOn(pipelineApi, 'dryRunPipeline').mockResolvedValueOnce(PASSED_REPORT);

    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
        }),
      ),
    );

    fireEvent.click(screen.getByText(/run dry-run/i));

    await waitFor(() => {
      expect(screen.getByText(/pre-flight passed/i)).toBeDefined();
    });

    expect(screen.getByText(/NlpAgent/)).toBeDefined();
    expect(screen.getByText(/Kill-switch is not enabled in staging/)).toBeDefined();
  });

  it('shows validation errors when dry-run fails', async () => {
    vi.spyOn(pipelineApi, 'dryRunPipeline').mockResolvedValueOnce(FAILED_REPORT);

    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
        }),
      ),
    );

    fireEvent.click(screen.getByText(/run dry-run/i));

    await waitFor(() => {
      expect(screen.getByText(/pre-flight failed/i)).toBeDefined();
    });

    expect(screen.getByText(/Stage "ingest" has no agents/)).toBeDefined();
    expect(screen.getByText(/Cyclical dependency detected/)).toBeDefined();
  });

  it('Publish button disabled until acknowledgement is checked', async () => {
    vi.spyOn(pipelineApi, 'dryRunPipeline').mockResolvedValueOnce(PASSED_REPORT);
    const onPublish = vi.fn();

    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
          onPublish,
        }),
      ),
    );

    fireEvent.click(screen.getByText(/run dry-run/i));

    await waitFor(() => {
      expect(screen.getByText(/pre-flight passed/i)).toBeDefined();
    });

    const publishBtn = screen.getByRole('button', { name: /publish/i });
    expect(publishBtn).toHaveProperty('disabled', true);

    const checkbox = screen.getByRole('checkbox');
    fireEvent.click(checkbox);

    expect(publishBtn).toHaveProperty('disabled', false);

    fireEvent.click(publishBtn);
    expect(onPublish).toHaveBeenCalledWith('pipe-1');
  });

  it('shows error message when dry-run API call fails', async () => {
    vi.spyOn(pipelineApi, 'dryRunPipeline').mockRejectedValueOnce(new Error('Server unavailable'));

    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose: vi.fn(),
        }),
      ),
    );

    fireEvent.click(screen.getByText(/run dry-run/i));

    await waitFor(() => {
      expect(screen.getByText(/Dry-run failed/i)).toBeDefined();
    });

    expect(screen.getByText(/Server unavailable/)).toBeDefined();
  });

  it('calls onClose when Cancel button clicked', () => {
    const onClose = vi.fn();

    render(
      React.createElement(
        Wrapper,
        null,
        React.createElement(PipelineDryRunDialog, {
          open: true,
          pipelineId: 'pipe-1',
          pipelineName: 'My Pipeline',
          tenantId: 'acme-corp',
          onClose,
        }),
      ),
    );

    fireEvent.click(screen.getByText(/cancel/i));
    expect(onClose).toHaveBeenCalled();
  });
});

