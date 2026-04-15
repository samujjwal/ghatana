import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  EXECUTION_MONITOR_BOUNDARY_NOTE,
  ExecutionMonitor,
} from '../ExecutionMonitor';

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('ExecutionMonitor', () => {
  it('renders an explicit launcher boundary instead of calling unsupported execution detail APIs', () => {
    render(<ExecutionMonitor executionId="exec-123" />, { wrapper: Wrapper });

    expect(screen.getByText(/Execution Monitoring Unavailable/i)).toBeInTheDocument();
    expect(screen.getByText(EXECUTION_MONITOR_BOUNDARY_NOTE)).toBeInTheDocument();
    expect(
      screen.getByText(/Use pipeline execution summaries and launcher-supported workflow pages instead of per-execution live monitoring/i),
    ).toBeInTheDocument();
  });
});