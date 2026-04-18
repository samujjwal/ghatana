/**
 * AnalyticsPage Tests
 *
 * Tests for the Analytics dashboard page.
 *
 * @doc.type test
 * @doc.purpose Test Analytics page functionality
 * @doc.layer product
 * @doc.pattern Component Test
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import '@testing-library/jest-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AnalyticsPage } from '../AnalyticsPage';
import { apiClient } from '../../api/tutorputorClient';

// Mock the API client
vi.mock('../../api/tutorputorClient', () => ({
  apiClient: {
    getAnalyticsSummary: vi.fn(),
    getUsageTrends: vi.fn(),
    getAtRiskStudents: vi.fn(),
  },
}));

describe('AnalyticsPage', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('should show loading state initially', () => {
    (apiClient.getAnalyticsSummary as any).mockImplementation(() => new Promise(() => {}));
    (apiClient.getUsageTrends as any).mockImplementation(() => new Promise(() => {}));
    (apiClient.getAtRiskStudents as any).mockImplementation(() => new Promise(() => {}));

    render(<AnalyticsPage />, { wrapper });

    expect(screen.getByRole('generic', { name: /loading/i })).toBeInTheDocument();
  });

  it('should display analytics data when loaded', async () => {
    (apiClient.getAnalyticsSummary as any).mockResolvedValue({
      totalEvents: 1000,
      activeLearners: 150,
      eventsByType: {
        module_completed: 500,
        assessment_completed: 300,
        simulation_started: 200,
      },
    });

    (apiClient.getUsageTrends as any).mockResolvedValue({
      periods: [
        { periodStart: '2024-01-01', eventCount: 100 },
        { periodStart: '2024-01-02', eventCount: 150 },
        { periodStart: '2024-01-03', eventCount: 200 },
      ],
    });

    (apiClient.getAtRiskStudents as any).mockResolvedValue([
      {
        userId: 'student-1',
        displayName: 'John Doe',
        riskLevel: 'high',
        riskFactors: ['low_engagement', 'missed_deadlines'],
      },
    ]);

    render(<AnalyticsPage />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('Learning Analytics')).toBeInTheDocument();
    });

    expect(screen.getByText('1,000')).toBeInTheDocument(); // Total Events
    expect(screen.getByText('150')).toBeInTheDocument(); // Active Learners
    expect(screen.getByText('500')).toBeInTheDocument(); // Completions
    expect(screen.getByText('1')).toBeInTheDocument(); // At-Risk Students
  });

  it('should display empty state when no data available', async () => {
    (apiClient.getAnalyticsSummary as any).mockResolvedValue({
      totalEvents: 0,
      activeLearners: 0,
      eventsByType: {},
    });

    (apiClient.getUsageTrends as any).mockResolvedValue({
      periods: [],
    });

    (apiClient.getAtRiskStudents as any).mockResolvedValue([]);

    render(<AnalyticsPage />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('No trend data available yet.')).toBeInTheDocument();
      expect(screen.getByText('No at-risk students detected.')).toBeInTheDocument();
    });
  });

  it('should handle period switching', async () => {
    (apiClient.getAnalyticsSummary as any).mockResolvedValue({
      totalEvents: 1000,
      activeLearners: 150,
      eventsByType: {},
    });

    (apiClient.getUsageTrends as any).mockResolvedValue({
      periods: [],
    });

    (apiClient.getAtRiskStudents as any).mockResolvedValue([]);

    render(<AnalyticsPage />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('Daily')).toBeInTheDocument();
      expect(screen.getByText('Weekly')).toBeInTheDocument();
      expect(screen.getByText('Monthly')).toBeInTheDocument();
    });
  });
});
