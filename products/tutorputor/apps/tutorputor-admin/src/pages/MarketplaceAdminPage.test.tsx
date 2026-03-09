import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { MinimalThemeProvider } from '../providers/MinimalThemeProvider';
import { MarketplaceAdminPage } from './MarketplaceAdminPage';

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

describe('MarketplaceAdminPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    global.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const href = typeof input === 'string' ? input : input.toString();
      const url = new URL(href, 'http://localhost');
      const { pathname, search } = url;
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

      // Admin marketplace stats
      if (pathname === '/admin/api/v1/marketplace/stats' && method === 'GET') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            totalListings: 5,
            activeListings: 3,
            draftListings: 1,
            archivedListings: 1,
            totalRevenueCents: 12345,
            topListings: [],
          }),
        } as Response);
      }

      // Admin marketplace listings (query string ignored for now)
      if (pathname === '/admin/api/v1/marketplace/listings' && method === 'GET') {
        void search; // keep lints happy if enabled
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            listings: [
              {
                id: 'listing-1',
                moduleId: 'mod-1',
                title: 'Demo Listing',
                status: 'DRAFT',
                visibility: 'PRIVATE',
                priceCents: 1000,
                createdAt: new Date().toISOString(),
                module: {
                  id: 'mod-1',
                  title: 'Algebra 101',
                  slug: 'algebra-101',
                  domain: 'MATH',
                },
              },
            ],
          }),
        } as Response);
      }

      // Admin marketplace listing update
      if (pathname.startsWith('/admin/api/v1/marketplace/listings/') && method === 'PATCH') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            listing: {
              id: pathname.split('/').pop() ?? 'listing-1',
              status: 'ACTIVE',
              visibility: 'PUBLIC',
            },
          }),
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

  it('renders stats and listings', async () => {
    renderWithProviders(<MarketplaceAdminPage />);

    await waitFor(() => {
      expect(
        screen.getByText('Marketplace Administration')
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Total Listings')).toBeInTheDocument();
      expect(screen.getByText('Active Listings')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Algebra 101')).toBeInTheDocument();
    });
  });

  it('sends approve request when Approve is clicked', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch');

    renderWithProviders(<MarketplaceAdminPage />);

    await waitFor(() => {
      expect(screen.getByText('Algebra 101')).toBeInTheDocument();
    });

    const approveButton = screen.getByRole('button', { name: /approve/i });
    fireEvent.click(approveButton);

    await waitFor(() => {
      const patchCall = fetchSpy.mock.calls.find(([url, init]) => {
        return (
          typeof url === 'string' &&
          url.startsWith('/admin/api/v1/marketplace/listings/') &&
          (init as RequestInit | undefined)?.method === 'PATCH'
        );
      });
      expect(patchCall).toBeTruthy();
    });
  });
});
