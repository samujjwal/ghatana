import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { MinimalThemeProvider } from '../providers/MinimalThemeProvider';
import { AnalyticsPage } from './AnalyticsPage';

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <MinimalThemeProvider storageKey="test-theme">
          {ui}
        </MinimalThemeProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

describe('AnalyticsPage deep-linking', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    global.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const href = typeof input === 'string' ? input : input.toString();
      const url = new URL(href, 'http://localhost');
      const { pathname, searchParams } = url;
      const method = (init?.method ?? 'GET').toUpperCase();

      // Auth endpoints used by useAuth hook
      if (pathname === '/api/v1/auth/me' && method === 'GET') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            user: {
              id: 'user-admin-001',
              email: 'admin@demo.tutorputor.com',
              role: 'admin',
              firstName: 'Sarah',
              lastName: 'Admin',
              fullName: 'Sarah Admin',
            },
            tenantId: 'tenant-test',
            accessToken: 'test-token',
          }),
        } as Response);
      }

      if (pathname === '/auth/logout' && method === 'POST') {
        return Promise.resolve({
          ok: true,
          status: 204,
          json: async () => ({}),
        } as Response);
      }

      // Analytics content metrics (used in this test)
      if (pathname === '/admin/api/v1/analytics/content' && method === 'GET') {
        const range = searchParams.get('range') ?? '30d';
        void range;
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            totalModules: 1,
            publishedModules: 1,
            totalLessons: 1,
            totalQuizzes: 0,
            modulesByCategory: [],
            topModules: [
              {
                id: 'mod-1',
                title: 'Algebra 101',
                completions: 10,
                rating: 4.7,
              },
            ],
            averageRating: 4.7,
          }),
        } as Response);
      }

      // Other analytics endpoints can return minimal defaults
      if (
        pathname === '/admin/api/v1/analytics/engagement' ||
        pathname === '/admin/api/v1/analytics/users' ||
        pathname === '/admin/api/v1/analytics/performance'
      ) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        } as Response);
      }

      // Default: 404-style stub so no real network is ever used
      return Promise.resolve({
        ok: false,
        status: 404,
        json: async () => ({}),
      } as Response);
    }) as any;
  });

  it('renders top modules table with CMS deep-link', async () => {
    renderWithProviders(<AnalyticsPage />);

    // Switch to Content tab (defaults to Engagement)
    const contentTab = await screen.findByRole('button', { name: /content/i });
    contentTab.click();

    await waitFor(() => {
      expect(screen.getByText('Top Performing Modules')).toBeInTheDocument();
    });

    const link = screen.getByRole('link', { name: /algebra 101/i });
    expect(link).toHaveAttribute('href', '/cms?moduleId=mod-1');
  });
});
