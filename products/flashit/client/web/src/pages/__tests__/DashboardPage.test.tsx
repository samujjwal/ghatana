/**
 * DashboardPage Component Tests
 * 
 * Tests the main dashboard page rendering, data fetching, and user interactions.
 * Covers stats display, recent moments, loading states, and accessibility.
 * 
 * @doc.type test
 * @doc.purpose Test dashboard page component
 * @doc.layer product
 * @doc.pattern ComponentTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import DashboardPage from '../DashboardPage';
import { AuthProvider } from '../../contexts/AuthContext';

// Mock API hooks
vi.mock('../../hooks/useApi', () => ({
  useApi: () => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  }),
}));

vi.mock('../../hooks/useMoments', () => ({
  useMoments: vi.fn(),
}));

vi.mock('../../hooks/useStats', () => ({
  useStats: vi.fn(),
}));

import { useMoments } from '../../hooks/useMoments';
import { useStats } from '../../hooks/useStats';

const mockMoments = [
  {
    id: 'moment-1',
    content: 'Had a great morning walk. Feeling energized!',
    capturedAt: new Date('2024-01-09T08:00:00Z'),
    emotions: ['happy', 'energized'],
    tags: ['health', 'morning'],
    sphere: { id: 'sphere-1', name: 'Personal', type: 'PERSONAL' },
    mediaReferences: [],
  },
  {
    id: 'moment-2',
    content: 'Completed the quarterly report. Proud of the team.',
    capturedAt: new Date('2024-01-08T16:00:00Z'),
    emotions: ['proud', 'accomplished'],
    tags: ['work', 'achievement'],
    sphere: { id: 'sphere-2', name: 'Work', type: 'WORK' },
    mediaReferences: [{ id: 'media-1', fileName: 'report.pdf', mimeType: 'application/pdf' }],
  },
  {
    id: 'moment-3',
    content: 'Morning meditation session was peaceful.',
    capturedAt: new Date('2024-01-07T07:00:00Z'),
    emotions: ['calm', 'peaceful'],
    tags: ['meditation', 'mindfulness'],
    sphere: { id: 'sphere-1', name: 'Personal', type: 'PERSONAL' },
    mediaReferences: [],
  },
];

const mockStats = {
  totalMoments: 127,
  momentsThisWeek: 18,
  activeSpheres: 5,
  topEmotions: [
    { emotion: 'happy', count: 45 },
    { emotion: 'calm', count: 32 },
    { emotion: 'energized', count: 28 },
  ],
  weeklyActivity: [
    { date: '2024-01-03', count: 3 },
    { date: '2024-01-04', count: 5 },
    { date: '2024-01-05', count: 2 },
    { date: '2024-01-06', count: 4 },
    { date: '2024-01-07', count: 1 },
    { date: '2024-01-08', count: 2 },
    { date: '2024-01-09', count: 1 },
  ],
};

const renderDashboard = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('loading state', () => {
    it('should display skeleton loaders while fetching data', () => {
      vi.mocked(useStats).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      // Check for skeleton loaders
      expect(screen.getByRole('status', { name: /loading dashboard stats/i })).toBeInTheDocument();
      expect(screen.getByRole('status', { name: /loading moments/i })).toBeInTheDocument();

      // Verify aria-busy for accessibility
      const skeletons = screen.getAllByRole('status');
      skeletons.forEach((skeleton) => {
        expect(skeleton).toHaveAttribute('aria-busy', 'true');
      });
    });
  });

  describe('stats display', () => {
    beforeEach(() => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);
    });

    it('should display user statistics correctly', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText('127')).toBeInTheDocument(); // Total moments
        expect(screen.getByText('18')).toBeInTheDocument(); // This week
        expect(screen.getByText('5')).toBeInTheDocument(); // Active spheres
      });
    });

    it('should display top emotions with counts', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/happy/i)).toBeInTheDocument();
        expect(screen.getByText('45')).toBeInTheDocument();
        expect(screen.getByText(/calm/i)).toBeInTheDocument();
        expect(screen.getByText('32')).toBeInTheDocument();
      });
    });

    it('should have proper ARIA labels on stat cards', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        const statsRegion = screen.getByLabelText(/dashboard statistics/i);
        expect(statsRegion).toBeInTheDocument();

        const statCards = within(statsRegion).getAllByRole('article');
        expect(statCards).toHaveLength(3);
      });
    });
  });

  describe('recent moments', () => {
    beforeEach(() => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);
    });

    it('should display recent moments list', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/Had a great morning walk/i)).toBeInTheDocument();
        expect(screen.getByText(/Completed the quarterly report/i)).toBeInTheDocument();
        expect(screen.getByText(/Morning meditation session/i)).toBeInTheDocument();
      });
    });

    it('should display moment emotions and tags', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        // Check emotions
        expect(screen.getByText(/happy/i)).toBeInTheDocument();
        expect(screen.getByText(/energized/i)).toBeInTheDocument();

        // Check tags
        expect(screen.getByText(/health/i)).toBeInTheDocument();
        expect(screen.getByText(/morning/i)).toBeInTheDocument();
      });
    });

    it('should display sphere information for each moment', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getAllByText(/Personal/i)).toHaveLength(2);
        expect(screen.getByText(/Work/i)).toBeInTheDocument();
      });
    });

    it('should show media attachment indicators', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        // Second moment has attachment
        const momentWithMedia = screen.getByText(/Completed the quarterly report/i).closest('article');
        expect(momentWithMedia).toBeInTheDocument();
        const attachmentIcon = within(momentWithMedia!).getByLabelText(/has attachment/i);
        expect(attachmentIcon).toBeInTheDocument();
      });
    });

    it('should display formatted timestamps', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        // Should show relative times like "2 days ago"
        expect(screen.getByText(/\d+ (day|hour|minute)s? ago/i)).toBeInTheDocument();
      });
    });
  });

  describe('empty states', () => {
    it('should show welcome message when no moments exist', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: { ...mockStats, totalMoments: 0 },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/welcome to flashit/i)).toBeInTheDocument();
        expect(screen.getByText(/start capturing your first moment/i)).toBeInTheDocument();
      });
    });

    it('should provide capture button in empty state', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: { ...mockStats, totalMoments: 0 },
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        const captureButton = screen.getByRole('link', { name: /capture moment/i });
        expect(captureButton).toBeInTheDocument();
        expect(captureButton).toHaveAttribute('href', '/capture');
      });
    });
  });

  describe('error handling', () => {
    it('should display error message when stats fail to load', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to fetch stats'),
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/failed to load statistics/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
      });
    });

    it('should display error message when moments fail to load', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to fetch moments'),
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/failed to load moments/i)).toBeInTheDocument();
      });
    });

    it('should allow retry on error', async () => {
      const refetch = vi.fn();
      vi.mocked(useStats).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Network error'),
        refetch,
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      const retryButton = await screen.findByRole('button', { name: /try again/i });
      await userEvent.click(retryButton);

      expect(refetch).toHaveBeenCalled();
    });
  });

  describe('accessibility', () => {
    beforeEach(() => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);
    });

    it('should have main landmark', async () => {
      renderDashboard();

      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });
    });

    it('should have proper heading hierarchy', async () => {
      renderDashboard();

      await waitFor(() => {
        const h1 = screen.getByRole('heading', { level: 1 });
        expect(h1).toHaveTextContent(/dashboard/i);

        const h2Headings = screen.getAllByRole('heading', { level: 2 });
        expect(h2Headings.length).toBeGreaterThan(0);
      });
    });

    it('should have labeled regions', async () => {
      renderDashboard();

      await waitFor(() => {
        expect(screen.getByLabelText(/dashboard statistics/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/recent moments/i)).toBeInTheDocument();
      });
    });

    it('should support keyboard navigation', async () => {
      const user = userEvent.setup();
      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/Had a great morning walk/i)).toBeInTheDocument();
      });

      // Tab through interactive elements
      await user.tab();
      expect(document.activeElement?.tagName).toBe('A'); // Should focus first link
    });
  });
});
