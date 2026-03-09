/// <reference types="jest" />
import '@testing-library/jest-dom';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PoliciesPage from './PoliciesPage';
import { describe, it, expect, beforeEach, afterEach, jest } from '@jest/globals';

describe('PoliciesPage', () => {
  const originalFetch = global.fetch;
  let fetchMock: jest.Mock;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    fetchMock = jest
      .fn()
      // /whoami
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ subject: 'smoke-user', roles: ['admin'] }), { status: 200 }),
      )
      // /policy?
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ version: '1.0', data: 'e30=', schemaVersion: 1 }), { status: 200 }),
      )
      // default fallback for any subsequent revalidation requests
      .mockResolvedValue(
        new Response(JSON.stringify({ version: '1.0', data: 'e30=', schemaVersion: 1 }), { status: 200 }),
      );

    Object.defineProperty(global, 'fetch', {
      value: fetchMock as unknown as typeof fetch,
      writable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(global, 'fetch', {
      value: originalFetch,
      writable: true,
    });
    queryClient.clear();
    jest.resetAllMocks();
  });

  it('renders identity and fetched policy', async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PoliciesPage />
      </QueryClientProvider>,
    );
    expect(await screen.findByText('Policies')).toBeInTheDocument();
    expect(await screen.findByText(/smoke-user/)).toBeInTheDocument();
    expect(await screen.findByText(/Policy Fetch/)).toBeInTheDocument();
  });
});

