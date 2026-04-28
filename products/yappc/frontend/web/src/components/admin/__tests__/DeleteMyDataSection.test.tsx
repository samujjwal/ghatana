/**
 * @doc.type test
 * @doc.purpose Unit tests for DeleteMyDataSection (C-Y15 / F-Y058)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import DeleteMyDataSection from '../DeleteMyDataSection';

// ── Test helpers ───────────────────────────────────────────────────────────────

function makeQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { mutations: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }): React.JSX.Element {
  return (
    <QueryClientProvider client={makeQueryClient()}>{children}</QueryClientProvider>
  );
}

function mockFetch(status: number, body: unknown, headers: Record<string, string> = {}): void {
  const responseHeaders = new Headers({ 'Content-Type': 'application/json', ...headers });
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValueOnce({
      ok: status >= 200 && status < 300,
      status,
      headers: responseHeaders,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('DeleteMyDataSection', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the initial state with a request button', () => {
    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    expect(screen.getByText('Delete my data')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /request data deletion/i })).toBeInTheDocument();
  });

  it('shows confirmation prompt when request button is clicked', async () => {
    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    await userEvent.click(screen.getByRole('button', { name: /request data deletion/i }));
    expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /yes, delete my data/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
  });

  it('returns to idle when cancel is clicked', async () => {
    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    await userEvent.click(screen.getByRole('button', { name: /request data deletion/i }));
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(screen.getByRole('button', { name: /request data deletion/i })).toBeInTheDocument();
  });

  it('shows success state on 202 response', async () => {
    mockFetch(202, { statusUrl: '/api/users/me/data/status/abc', estimatedDuration: 'PT30S' });

    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    await userEvent.click(screen.getByRole('button', { name: /request data deletion/i }));
    await userEvent.click(screen.getByRole('button', { name: /yes, delete my data/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/deletion request submitted/i),
    );
    expect(screen.getByRole('link', { name: /check deletion status/i })).toBeInTheDocument();
  });

  it('shows error state on non-202 response', async () => {
    mockFetch(500, { title: 'Internal Server Error' });

    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    await userEvent.click(screen.getByRole('button', { name: /request data deletion/i }));
    await userEvent.click(screen.getByRole('button', { name: /yes, delete my data/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/request failed/i),
    );
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('resets to idle when try again is clicked after error', async () => {
    mockFetch(500, {});

    render(<DeleteMyDataSection />, { wrapper: Wrapper });
    await userEvent.click(screen.getByRole('button', { name: /request data deletion/i }));
    await userEvent.click(screen.getByRole('button', { name: /yes, delete my data/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(screen.getByRole('button', { name: /request data deletion/i })).toBeInTheDocument();
  });
});
