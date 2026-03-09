/**
 * MomentsPage Component Tests
 * 
 * Tests the moments listing page with filtering, search, pagination.
 * Covers infinite scroll, sphere filtering, and accessibility.
 * 
 * @doc.type test
 * @doc.purpose Test moments page component
 * @doc.layer product
 * @doc.pattern ComponentTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import MomentsPage from '../MomentsPage';
import { AuthProvider } from '../../contexts/AuthContext';

vi.mock('../../hooks/useMoments', () => ({
  useMoments: vi.fn(),
}));

vi.mock('../../hooks/useSpheres', () => ({
  useSpheres: vi.fn(),
}));

import { useMoments } from '../../hooks/useMoments';
import { useSpheres } from '../../hooks/useSpheres';

const mockSpheres = [
  { id: 'sphere-1', name: 'Personal', type: 'PERSONAL', visibility: 'PRIVATE' },
  { id: 'sphere-2', name: 'Work', type: 'WORK', visibility: 'PRIVATE' },
  { id: 'sphere-3', name: 'Health', type: 'HEALTH', visibility: 'PRIVATE' },
];

const mockMomentsPage1 = [
  {
    id: 'moment-1',
    content: 'Productive morning coding session',
    capturedAt: new Date('2024-01-09T09:00:00Z'),
    emotions: ['focused', 'energized'],
    tags: ['work', 'coding'],
    sphere: mockSpheres[1],
    mediaReferences: [],
  },
  {
    id: 'moment-2',
    content: 'Meditated for 20 minutes',
    capturedAt: new Date('2024-01-09T07:00:00Z'),
    emotions: ['calm', 'peaceful'],
    tags: ['meditation', 'mindfulness'],
    sphere: mockSpheres[2],
    mediaReferences: [],
  },
];

const mockMomentsPage2 = [
  {
    id: 'moment-3',
    content: 'Family dinner was lovely',
    capturedAt: new Date('2024-01-08T19:00:00Z'),
    emotions: ['happy', 'grateful'],
    tags: ['family', 'social'],
    sphere: mockSpheres[0],
    mediaReferences: [],
  },
];

const renderMomentsPage = () => {
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
          <MomentsPage />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

describe('MomentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useSpheres).mockReturnValue({
      data: mockSpheres,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as any);
  });

  describe('rendering', () => {
    it('should display page title', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      expect(screen.getByRole('heading', { name: /my moments/i, level: 1 })).toBeInTheDocument();
    });

    it('should display moments list', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByText(/Productive morning coding session/i)).toBeInTheDocument();
        expect(screen.getByText(/Meditated for 20 minutes/i)).toBeInTheDocument();
      });
    });

    it('should display loading skeleton', () => {
      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      expect(screen.getByRole('status', { name: /loading moments/i })).toBeInTheDocument();
    });
  });

  describe('filtering', () => {
    it('should display sphere filter dropdown', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByRole('combobox', { name: /filter by sphere/i })).toBeInTheDocument();
      });
    });

    it('should filter moments by sphere', async () => {
      const user = userEvent.setup();
      const refetch = vi.fn();

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch,
      } as any);

      renderMomentsPage();

      const sphereFilter = await screen.findByRole('combobox', { name: /filter by sphere/i });
      await user.selectOptions(sphereFilter, 'sphere-2'); // Work sphere

      await waitFor(() => {
        expect(refetch).toHaveBeenCalled();
      });
    });

    it('should show all spheres in filter dropdown', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        const sphereFilter = screen.getByRole('combobox', { name: /filter by sphere/i });
        const options = within(sphereFilter).getAllByRole('option');
        
        // All spheres + "All" option
        expect(options).toHaveLength(4);
        expect(options[0]).toHaveTextContent(/all spheres/i);
        expect(options[1]).toHaveTextContent('Personal');
        expect(options[2]).toHaveTextContent('Work');
        expect(options[3]).toHaveTextContent('Health');
      });
    });
  });

  describe('search', () => {
    it('should display search input', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      expect(screen.getByRole('searchbox', { name: /search moments/i })).toBeInTheDocument();
    });

    it('should search moments on input', async () => {
      const user = userEvent.setup();
      const refetch = vi.fn();

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch,
      } as any);

      renderMomentsPage();

      const searchInput = screen.getByRole('searchbox', { name: /search moments/i });
      await user.type(searchInput, 'meditation');

      // Should debounce search
      await waitFor(() => {
        expect(refetch).toHaveBeenCalled();
      }, { timeout: 1500 });
    });

    it('should clear search on button click', async () => {
      const user = userEvent.setup();
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      const searchInput = screen.getByRole('searchbox', { name: /search moments/i }) as HTMLInputElement;
      await user.type(searchInput, 'test query');

      expect(searchInput.value).toBe('test query');

      const clearButton = screen.getByRole('button', { name: /clear search/i });
      await user.click(clearButton);

      expect(searchInput.value).toBe('');
    });
  });

  describe('infinite scroll', () => {
    it('should display load more button when hasNextPage is true', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: true }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: true,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /load more/i })).toBeInTheDocument();
      });
    });

    it('should fetch next page on load more click', async () => {
      const user = userEvent.setup();
      const fetchNextPage = vi.fn();

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: true }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage,
        hasNextPage: true,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      const loadMoreButton = await screen.findByRole('button', { name: /load more/i });
      await user.click(loadMoreButton);

      expect(fetchNextPage).toHaveBeenCalled();
    });

    it('should show loading spinner when fetching next page', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: true }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: true,
        isFetchingNextPage: true,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByRole('status', { name: /loading more moments/i })).toBeInTheDocument();
      });
    });

    it('should append new moments to existing list', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: {
          pages: [
            { moments: mockMomentsPage1, hasMore: true },
            { moments: mockMomentsPage2, hasMore: false },
          ],
          pageParams: [],
        },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        // Page 1 moments
        expect(screen.getByText(/Productive morning coding session/i)).toBeInTheDocument();
        expect(screen.getByText(/Meditated for 20 minutes/i)).toBeInTheDocument();
        
        // Page 2 moments
        expect(screen.getByText(/Family dinner was lovely/i)).toBeInTheDocument();
      });
    });
  });

  describe('empty state', () => {
    it('should show empty state when no moments exist', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByText(/no moments yet/i)).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /create your first moment/i })).toBeInTheDocument();
      });
    });

    it('should show no results message when search/filter returns empty', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      // Type in search to indicate active filter
      const searchInput = screen.getByRole('searchbox', { name: /search moments/i });
      await userEvent.type(searchInput, 'nonexistent');

      await waitFor(() => {
        expect(screen.getByText(/no moments found/i)).toBeInTheDocument();
      });
    });
  });

  describe('error handling', () => {
    it('should display error message on fetch failure', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to fetch moments'),
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByText(/failed to load moments/i)).toBeInTheDocument();
      });
    });

    it('should provide retry button on error', async () => {
      const refetch = vi.fn();
      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Network error'),
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch,
      } as any);

      renderMomentsPage();

      const retryButton = await screen.findByRole('button', { name: /try again/i });
      await userEvent.click(retryButton);

      expect(refetch).toHaveBeenCalled();
    });
  });

  describe('accessibility', () => {
    beforeEach(() => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);
    });

    it('should have main landmark', async () => {
      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByRole('main')).toBeInTheDocument();
      });
    });

    it('should have proper ARIA labels on controls', async () => {
      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByRole('searchbox')).toHaveAccessibleName();
        expect(screen.getByRole('combobox')).toHaveAccessibleName();
      });
    });

    it('should announce page changes to screen readers', async () => {
      renderMomentsPage();

      await waitFor(() => {
        const momentsList = screen.getByRole('list', { name: /moments list/i });
        expect(momentsList).toBeInTheDocument();
        expect(momentsList).toHaveAttribute('aria-live', 'polite');
      });
    });

    it('should support keyboard navigation', async () => {
      const user = userEvent.setup();
      renderMomentsPage();

      await waitFor(() => {
        expect(screen.getByText(/Productive morning coding session/i)).toBeInTheDocument();
      });

      // Tab through controls
      await user.tab();
      expect(screen.getByRole('searchbox')).toHaveFocus();

      await user.tab();
      expect(screen.getByRole('combobox')).toHaveFocus();
    });
  });
});
