/**
 * MomentsScreen Component Tests (React Native)
 * 
 * Tests the mobile moments listing screen with filtering, search, and infinite scroll.
 * Covers accessibility, navigation, and error handling.
 * 
 * @doc.type test
 * @doc.purpose Test mobile moments screen
 * @doc.layer product
 * @doc.pattern ComponentTest
 */

import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MomentsScreen from '../MomentsScreen';
import { AuthContext } from '../../contexts/AuthContext';

vi.mock('../../hooks/useMoments', () => ({
  useMoments: vi.fn(),
}));

vi.mock('../../hooks/useSpheres', () => ({
  useSpheres: vi.fn(),
}));

vi.mock('@react-navigation/native', async () => {
  const actual = await vi.importActual('@react-navigation/native');
  return {
    ...actual,
    useNavigation: () => ({
      navigate: vi.fn(),
      goBack: vi.fn(),
      setOptions: vi.fn(),
    }),
    useFocusEffect: vi.fn((cb) => cb()),
  };
});

import { useMoments } from '../../hooks/useMoments';
import { useSpheres } from '../../hooks/useSpheres';

const mockUser = {
  id: 'user-123',
  email: 'test@example.com',
  displayName: 'Test User',
};

const mockAuthContext = {
  user: mockUser,
  isAuthenticated: true,
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  loading: false,
  token: 'mock-token',
};

const mockSpheres = [
  { id: 'sphere-1', name: 'Personal', type: 'PERSONAL', visibility: 'PRIVATE' },
  { id: 'sphere-2', name: 'Work', type: 'WORK', visibility: 'PRIVATE' },
  { id: 'sphere-3', name: 'Health', type: 'HEALTH', visibility: 'PRIVATE' },
];

const mockMomentsPage1 = [
  {
    id: 'moment-1',
    content: 'Great team meeting today',
    capturedAt: new Date('2024-01-09T10:00:00Z'),
    emotions: ['productive', 'energized'],
    tags: ['work', 'meeting'],
    sphere: mockSpheres[1],
    mediaReferences: [],
  },
  {
    id: 'moment-2',
    content: 'Morning yoga session',
    capturedAt: new Date('2024-01-09T07:00:00Z'),
    emotions: ['calm', 'centered'],
    tags: ['health', 'yoga'],
    sphere: mockSpheres[2],
    mediaReferences: [],
  },
];

const mockMomentsPage2 = [
  {
    id: 'moment-3',
    content: 'Dinner with family',
    capturedAt: new Date('2024-01-08T19:00:00Z'),
    emotions: ['happy', 'grateful'],
    tags: ['family', 'social'],
    sphere: mockSpheres[0],
    mediaReferences: [{ id: 'media-1', fileName: 'family.jpg', mimeType: 'image/jpeg' }],
  },
];

const renderMomentsScreen = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <NavigationContainer>
        <AuthContext.Provider value={mockAuthContext}>
          <MomentsScreen />
        </AuthContext.Provider>
      </NavigationContainer>
    </QueryClientProvider>
  );
};

describe('MomentsScreen', () => {
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
    it('should display screen title', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      expect(screen.getByText(/my moments/i)).toBeTruthy();
    });

    it('should display loading indicator while fetching', () => {
      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      expect(screen.getByAccessibilityLabel(/loading moments/i)).toBeTruthy();
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

      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/Great team meeting today/i)).toBeTruthy();
        expect(screen.getByText(/Morning yoga session/i)).toBeTruthy();
      });
    });
  });

  describe('moment cards', () => {
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

    it('should have accessible moment cards', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        const momentCard = screen.getByAccessibilityLabel(/moment in work sphere/i);
        expect(momentCard).toBeTruthy();
        expect(momentCard.props.accessible).toBe(true);
        expect(momentCard.props.accessibilityRole).toBe('button');
      });
    });

    it('should display sphere information', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/work/i)).toBeTruthy();
        expect(screen.getByText(/health/i)).toBeTruthy();
      });
    });

    it('should display emotions', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/productive/i)).toBeTruthy();
        expect(screen.getByText(/calm/i)).toBeTruthy();
      });
    });

    it('should display tags', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/meeting/i)).toBeTruthy();
        expect(screen.getByText(/yoga/i)).toBeTruthy();
      });
    });

    it('should display relative timestamps', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        // Should show relative times
        const timestamps = screen.getAllByText(/\d+ (hour|minute|day)s? ago/i);
        expect(timestamps.length).toBeGreaterThan(0);
      });
    });
  });

  describe('filtering', () => {
    it('should display sphere filter picker', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByAccessibilityLabel(/filter by sphere/i)).toBeTruthy();
      });
    });

    it('should filter moments by sphere', async () => {
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

      renderMomentsScreen();

      const spherePicker = await screen.findByAccessibilityLabel(/filter by sphere/i);
      fireEvent(spherePicker, 'onValueChange', 'sphere-2');

      await waitFor(() => {
        expect(refetch).toHaveBeenCalled();
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

      renderMomentsScreen();

      expect(screen.getByAccessibilityLabel(/search moments/i)).toBeTruthy();
      expect(screen.getByAccessibilityHint(/enter search query/i)).toBeTruthy();
    });

    it('should update search query on input', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      const searchInput = screen.getByAccessibilityLabel(/search moments/i);
      fireEvent.changeText(searchInput, 'meeting');

      expect(searchInput.props.value).toBe('meeting');
    });
  });

  describe('infinite scroll', () => {
    it('should display load more indicator when hasNextPage', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: true }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: true,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByAccessibilityLabel(/loading more moments/i)).toBeTruthy();
      });
    });

    it('should fetch next page on scroll', async () => {
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

      renderMomentsScreen();

      const flatList = await screen.findByTestId('moments-list');
      fireEvent(flatList, 'onEndReached');

      expect(fetchNextPage).toHaveBeenCalled();
    });

    it('should append new moments to list', async () => {
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

      renderMomentsScreen();

      await waitFor(() => {
        // Page 1 moments
        expect(screen.getByText(/Great team meeting today/i)).toBeTruthy();
        
        // Page 2 moments
        expect(screen.getByText(/Dinner with family/i)).toBeTruthy();
      });
    });
  });

  describe('empty state', () => {
    it('should display empty state when no moments', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/no moments yet/i)).toBeTruthy();
      });
    });

    it('should provide capture button in empty state', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: [], hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        const captureButton = screen.getByAccessibilityLabel(/create your first moment/i);
        expect(captureButton).toBeTruthy();
        expect(captureButton.props.accessibilityRole).toBe('button');
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

      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByText(/failed to load moments/i)).toBeTruthy();
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

      renderMomentsScreen();

      await waitFor(() => {
        const retryButton = screen.getByAccessibilityLabel(/retry loading/i);
        expect(retryButton).toBeTruthy();
        expect(retryButton.props.accessibilityRole).toBe('button');
      });
    });
  });

  describe('navigation', () => {
    it('should navigate to moment details on card press', async () => {
      const navigation = require('@react-navigation/native').useNavigation();
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        const momentCard = screen.getByAccessibilityLabel(/moment in work sphere/i);
        fireEvent.press(momentCard);
      });
    });

    it('should have floating action button for quick capture', async () => {
      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMomentsPage1, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      await waitFor(() => {
        const fab = screen.getByAccessibilityLabel(/capture new moment/i);
        expect(fab).toBeTruthy();
        expect(fab.props.accessibilityRole).toBe('button');
        expect(fab.props.accessibilityHint).toContain('open capture screen');
      });
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

    it('should have accessible labels on all controls', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        expect(screen.getByAccessibilityLabel(/search moments/i)).toBeTruthy();
        expect(screen.getByAccessibilityLabel(/filter by sphere/i)).toBeTruthy();
      });
    });

    it('should provide accessibility hints for complex actions', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        const searchInput = screen.getByAccessibilityLabel(/search moments/i);
        expect(searchInput.props.accessibilityHint).toBeTruthy();
      });
    });

    it('should use accessibilityLiveRegion for dynamic content', async () => {
      renderMomentsScreen();

      await waitFor(() => {
        const momentsList = screen.getByTestId('moments-list');
        expect(momentsList.props.accessibilityLiveRegion).toBe('polite');
      });
    });

    it('should announce loading states', () => {
      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderMomentsScreen();

      const loadingIndicator = screen.getByAccessibilityLabel(/loading moments/i);
      expect(loadingIndicator.props.accessibilityLiveRegion).toBe('polite');
    });
  });

  describe('pull-to-refresh', () => {
    it('should support pull to refresh', async () => {
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

      renderMomentsScreen();

      await waitFor(() => {
        const flatList = screen.getByTestId('moments-list');
        fireEvent(flatList, 'onRefresh');

        expect(refetch).toHaveBeenCalled();
      });
    });
  });
});
