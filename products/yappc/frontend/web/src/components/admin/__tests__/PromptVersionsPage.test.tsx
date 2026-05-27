/**
 * PromptVersionsPage unit tests (F-Y022)
 *
 * Verify prompt version list, rollback dialog, weight configuration dialog,
 * loading state, error state, and empty state.
 *
 * @doc.type test
 * @doc.purpose Verify admin prompt version management UI lifecycle
 * @doc.layer product
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PromptVersionsPage } from '../PromptVersionsPage';
import type { PromptVersion } from '../../../services/admin/promptVersioningApi';

// ─────────────────────────────────────────────────────────────────────────────
// Mocks
// ─────────────────────────────────────────────────────────────────────────────

const mockListVersions = vi.hoisted(() => vi.fn<() => Promise<{ items: PromptVersion[]; total: number }>>());
const mockRollback = vi.hoisted(() => vi.fn<() => Promise<PromptVersion>>());
const mockUpdateWeights = vi.hoisted(() => vi.fn<() => Promise<void>>());

vi.mock('../../../services/admin/promptVersioningApi', () => ({
  listPromptVersions: mockListVersions,
  rollbackPromptVersion: mockRollback,
  updatePromptWeights: mockUpdateWeights,
}));

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function makeVersion(overrides: Partial<PromptVersion> = {}): PromptVersion {
  return {
    id: 'ver-aabbcc001122',
    promptName: 'code-review',
    content: 'You are a code review assistant.',
    contentHash: 'sha256deadbeef1234',
    description: 'Initial code review prompt',
    author: 'alice',
    active: true,
    weight: 1.0,
    createdAt: '2026-01-01T00:00:00.000Z',
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <PromptVersionsPage />
    </QueryClientProvider>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('PromptVersionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading spinner while fetching', async () => {
    mockListVersions.mockReturnValue(new Promise(() => undefined)); // never resolves
    renderPage();
    expect(await screen.findByTestId('loading-spinner')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Loading prompt versions...');
  });

  it('shows error message when fetch fails', async () => {
    mockListVersions.mockRejectedValue(new Error('network error'));
    renderPage();
    expect(await screen.findByTestId('error-message')).toBeInTheDocument();
    expect(screen.getByText('network error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try Again' })).toBeInTheDocument();
  });

  it('shows correlation id in the standardized error state', async () => {
    mockListVersions.mockRejectedValue(new Error('network error [Correlation ID: corr-prompts-1]'));
    renderPage();
    expect(await screen.findByTestId('error-message')).toHaveTextContent('Correlation ID: corr-prompts-1');
  });

  it('shows empty state when no versions exist', async () => {
    mockListVersions.mockResolvedValue({ items: [], total: 0 });
    renderPage();
    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByText('No prompt versions found')).toBeInTheDocument();
    expect(screen.getByText(/after the prompt registry publishes/i)).toBeInTheDocument();
  });

  it('renders version rows grouped by prompt name', async () => {
    const active = makeVersion({ active: true });
    const inactive = makeVersion({
      id: 'ver-inactive9999',
      active: false,
      weight: 0,
      description: 'Older version',
    });
    mockListVersions.mockResolvedValue({ items: [active, inactive], total: 2 });
    renderPage();
    expect(await screen.findByTestId('version-row-ver-aabbcc001122')).toBeInTheDocument();
    expect(screen.getByTestId('version-row-ver-inactive9999')).toBeInTheDocument();
    // group heading
    expect(screen.getByText('code-review')).toBeInTheDocument();
  });

  it('shows Active badge for active version', async () => {
    mockListVersions.mockResolvedValue({ items: [makeVersion({ active: true })], total: 1 });
    renderPage();
    await screen.findByTestId('version-row-ver-aabbcc001122');
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('opens rollback dialog and calls rollbackPromptVersion', async () => {
    const inactive = makeVersion({ id: 'ver-old', active: false });
    mockListVersions.mockResolvedValue({ items: [inactive], total: 1 });
    mockRollback.mockResolvedValue(makeVersion({ id: 'ver-old', active: true }));
    renderPage();
    await screen.findByTestId('version-row-ver-old');

    // click rollback action button
    fireEvent.click(screen.getByRole('button', { name: 'Rollback to this version' }));

    // dialog appears
    expect(await screen.findByTestId('rollback-dialog')).toBeInTheDocument();

    // fill reason and confirm
    fireEvent.change(screen.getByLabelText('Reason (required)'), {
      target: { value: 'Reverting bad prompt' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Rollback' }));

    await waitFor(() =>
      expect(mockRollback).toHaveBeenCalledWith('ver-old', 'Reverting bad prompt')
    );
  });

  it('keeps Confirm Rollback disabled while reason is empty', async () => {
    const inactive = makeVersion({ id: 'ver-old', active: false });
    mockListVersions.mockResolvedValue({ items: [inactive], total: 1 });
    renderPage();
    await screen.findByTestId('version-row-ver-old');

    fireEvent.click(screen.getByRole('button', { name: 'Rollback to this version' }));
    await screen.findByTestId('rollback-dialog');

    const confirmBtn = screen.getByRole('button', { name: 'Confirm Rollback' });
    expect(confirmBtn).toBeDisabled();
  });

  it('closes rollback dialog on Cancel', async () => {
    const inactive = makeVersion({ id: 'ver-old', active: false });
    mockListVersions.mockResolvedValue({ items: [inactive], total: 1 });
    renderPage();
    await screen.findByTestId('version-row-ver-old');

    fireEvent.click(screen.getByRole('button', { name: 'Rollback to this version' }));
    await screen.findByTestId('rollback-dialog');

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByTestId('rollback-dialog')).not.toBeInTheDocument();
  });

  it('opens weight dialog and calls updatePromptWeights', async () => {
    const version = makeVersion({ weight: 0.5 });
    mockListVersions.mockResolvedValue({ items: [version], total: 1 });
    mockUpdateWeights.mockResolvedValue(undefined);
    renderPage();
    await screen.findByTestId('version-row-ver-aabbcc001122');

    fireEvent.click(screen.getByRole('button', { name: 'Configure weight' }));
    expect(await screen.findByTestId('weight-dialog')).toBeInTheDocument();

    // update weight value
    fireEvent.change(screen.getByLabelText('Weight (0.0–1.0)'), {
      target: { value: '0.7' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save Weight' }));

    await waitFor(() =>
      expect(mockUpdateWeights).toHaveBeenCalledWith({ 'ver-aabbcc001122': 0.7 })
    );
  });

  it('does not show rollback button for active versions', async () => {
    mockListVersions.mockResolvedValue({ items: [makeVersion({ active: true })], total: 1 });
    renderPage();
    await screen.findByTestId('version-row-ver-aabbcc001122');
    expect(screen.queryByRole('button', { name: 'Rollback to this version' })).not.toBeInTheDocument();
  });
});
