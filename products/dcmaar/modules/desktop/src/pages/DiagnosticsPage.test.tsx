/// <reference types="jest" />
import '@testing-library/jest-dom';
import { render, screen } from '@testing-library/react';
import DiagnosticsPage from './DiagnosticsPage';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const createTestClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const fakeMetrics = {
  'dcmaar_server_policy_denied_total': 1,
};

describe('DiagnosticsPage', () => {
  const originalFetch = global.fetch;
  let fetchMock: jest.Mock;

  beforeEach(() => {
    fetchMock = jest
      .fn()
      // /healthz
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 'ok', service: 'dcmaar-service' }), { status: 200 }),
      )
      // /whoami
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            subject: 'operator@example.com',
          }),
          { status: 200 },
        ),
      )
      // /metrics
      .mockResolvedValueOnce(new Response(JSON.stringify(fakeMetrics), { status: 200 }));
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
    jest.resetAllMocks();
  });

  it('renders health, whoami, and metrics', async () => {
    const client = createTestClient();
    render(
      <QueryClientProvider client={client}>
        <DiagnosticsPage />
      </QueryClientProvider>
    );
    expect(await screen.findByText('Diagnostics')).toBeInTheDocument();
    expect(await screen.findByText(/dcmaar-service/)).toBeInTheDocument();
    expect(await screen.findByText(/operator@example.com/)).toBeInTheDocument();
    expect(await screen.findByText(/dcmaar_server_policy_denied_total/)).toBeInTheDocument();
  });
});
