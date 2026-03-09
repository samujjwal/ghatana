/**
 * DashboardScreen Component Tests (React Native)
 * 
 * Tests the mobile dashboard screen rendering, data fetching, and interactions.
 * Covers stats display, recent moments, navigation, and accessibility.
 * 
 * @doc.type test
 * @doc.purpose Test mobile dashboard screen
 * @doc.layer product
 * @doc.pattern ComponentTest
 */

import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DashboardScreen from '../DashboardScreen';
import { AuthContext } from '../../contexts/AuthContext';

// Mock hooks
vi.mock('../../hooks/useMoments', () => ({
  useMoments: vi.fn(),
}));

vi.mock('../../hooks/useStats', () => ({
  useStats: vi.fn(),
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
import { useStats } from '../../hooks/useStats';

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

const mockMoments = [
  {
    id: 'moment-1',
    content: 'Morning run was refreshing',
    capturedAt: new Date('2024-01-09T08:00:00Z'),
    emotions: ['energized', 'happy'],
    tags: ['exercise', 'morning'],
    sphere: { id: 'sphere-1', name: 'Personal', type: 'PERSONAL' },
    mediaReferences: [],
  },
  {
    id: 'moment-2',
    content: 'Finished the project presentation',
    capturedAt: new Date('2024-01-09T14:00:00Z'),
    emotions: ['accomplished', 'proud'],
    tags: ['work', 'achievement'],
    sphere: { id: 'sphere-2', name: 'Work', type: 'WORK' },
    mediaReferences: [{ id: 'media-1', fileName: 'presentation.pdf', mimeType: 'application/pdf' }],
  },
];

const mockStats = {
  totalMoments: 52,
  momentsThisWeek: 12,
  activeSpheres: 4,
  topEmotions: [
    { emotion: 'happy', count: 18 },
    { emotion: 'calm', count: 14 },
    { emotion: 'energized', count: 12 },
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
      <NavigationContainer>
        <AuthContext.Provider value={mockAuthContext}>
          <DashboardScreen />
        </AuthContext.Provider>
      </NavigationContainer>
    </QueryClientProvider>
  );
};

describe('DashboardScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('rendering', () => {
    it('should display welcome message with user name', async () => {
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

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/welcome back, test user/i)).toBeTruthy();
      });
    });

    it('should display loading indicator while fetching data', () => {
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

      expect(screen.getByAccessibilityLabel(/loading dashboard/i)).toBeTruthy();
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

    it('should display user statistics', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText('52')).toBeTruthy(); // Total moments
        expect(screen.getByText('12')).toBeTruthy(); // This week
        expect(screen.getByText('4')).toBeTruthy(); // Active spheres
      });
    });

    it('should have accessible stat cards', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByAccessibilityLabel(/total moments: 52/i)).toBeTruthy();
        expect(screen.getByAccessibilityLabel(/moments this week: 12/i)).toBeTruthy();
        expect(screen.getByAccessibilityLabel(/active spheres: 4/i)).toBeTruthy();
      });
    });

    it('should display top emotions', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/happy/i)).toBeTruthy();
        expect(screen.getByText('18')).toBeTruthy();
        expect(screen.getByText(/calm/i)).toBeTruthy();
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
        expect(screen.getByText(/Morning run was refreshing/i)).toBeTruthy();
        expect(screen.getByText(/Finished the project presentation/i)).toBeTruthy();
      });
    });

    it('should have accessible moment cards', async () => {
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
        const momentCard = screen.getByAccessibilityLabel(/moment in personal sphere/i);
        expect(momentCard).toBeTruthy();
        expect(momentCard.props.accessible).toBe(true);
      });
    });

    it('should display emotions and tags for moments', async () => {
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
        expect(screen.getByText(/energized/i)).toBeTruthy();
        expect(screen.getByText(/happy/i)).toBeTruthy();
        expect(screen.getByText(/exercise/i)).toBeTruthy();
      });
    });

    it('should show media attachment indicator', async () => {
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
        expect(screen.getByAccessibilityLabel(/has 1 attachment/i)).toBeTruthy();
      });
    });
  });

  describe('navigation', () => {
    it('should have capture button', async () => {
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

      renderDashboard();

      await waitFor(() => {
        const captureButton = screen.getByAccessibilityLabel(/capture new moment/i);
        expect(captureButton).toBeTruthy();
        expect(captureButton.props.accessibilityRole).toBe('button');
      });
    });

    it('should have view all moments button', async () => {
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

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/view all moments/i)).toBeTruthy();
      });
    });
  });

  describe('empty state', () => {
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
        expect(screen.getByText(/start capturing your moments/i)).toBeTruthy();
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
        const captureButton = screen.getByAccessibilityLabel(/capture your first moment/i);
        expect(captureButton).toBeTruthy();
      });
    });
  });

  describe('error handling', () => {
    it('should display error message on stats fetch failure', async () => {
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
        expect(screen.getByText(/failed to load statistics/i)).toBeTruthy();
      });
    });

    it('should display error message on moments fetch failure', async () => {
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch: vi.fn(),
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Network error'),
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch: vi.fn(),
      } as any);

      renderDashboard();

      await waitFor(() => {
        expect(screen.getByText(/failed to load moments/i)).toBeTruthy();
      });
    });

    it('should provide retry button on error', async () => {
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

      await waitFor(() => {
        const retryButton = screen.getByAccessibilityLabel(/retry loading/i);
        expect(retryButton).toBeTruthy();
      });
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

    it('should have accessible labels on all interactive elements', async () => {
      renderDashboard();

      await waitFor(() => {
        const captureButton = screen.getByAccessibilityLabel(/capture new moment/i);
        expect(captureButton.props.accessible).toBe(true);
        expect(captureButton.props.accessibilityRole).toBe('button');
      });
    });

    it('should use accessibilityHint for complex actions', async () => {
      renderDashboard();

      await waitFor(() => {
        const captureButton = screen.getByAccessibilityLabel(/capture new moment/i);
        expect(captureButton.props.accessibilityHint).toBeTruthy();
      });
    });

    it('should have proper accessibilityRole for lists', async () => {
      renderDashboard();

      await waitFor(() => {
        const momentsList = screen.getByAccessibilityLabel(/recent moments list/i);
        expect(momentsList.props.accessible).toBe(true);
      });
    });

    it('should announce loading states to screen readers', () => {
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

      const loadingIndicator = screen.getByAccessibilityLabel(/loading dashboard/i);
      expect(loadingIndicator.props.accessibilityLiveRegion).toBe('polite');
    });
  });

  describe('pull-to-refresh', () => {
    it('should support pull to refresh', async () => {
      const refetch = vi.fn();
      vi.mocked(useStats).mockReturnValue({
        data: mockStats,
        isLoading: false,
        error: null,
        refetch,
      } as any);

      vi.mocked(useMoments).mockReturnValue({
        data: { pages: [{ moments: mockMoments, hasMore: false }], pageParams: [] },
        isLoading: false,
        error: null,
        fetchNextPage: vi.fn(),
        hasNextPage: false,
        isFetchingNextPage: false,
        refetch,
      } as any);

      renderDashboard();

      await waitFor(() => {
        const scrollView = screen.getByTestId('dashboard-scroll-view');
        expect(scrollView.props.refreshControl).toBeTruthy();
      });
    });
  });
});
