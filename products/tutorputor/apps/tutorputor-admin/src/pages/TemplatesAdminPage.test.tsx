import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { MinimalThemeProvider } from '../providers/MinimalThemeProvider';
import { TemplatesAdminPage } from './TemplatesAdminPage';

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

describe('TemplatesAdminPage', () => {
  const baseTemplate = {
    id: 'tmpl-1',
    title: 'Physics Lab',
    description: 'Intro physics simulation',
    domain: 'SCIENCE',
    difficulty: 'INTERMEDIATE',
    tags: ['physics'],
    thumbnailUrl: '',
    isVerified: false,
    isPremium: false,
    version: '1.0',
    publishedAt: new Date().toISOString(),
    status: 'DRAFT',
    stats: {
      views: 100,
      uses: 20,
      rating: 4.5,
      ratingCount: 5,
      favorites: 10,
      completionRate: 0.8,
      avgTimeMinutes: 12,
    },
  } as const;

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

      // Admin template stats
      if (pathname === '/admin/api/v1/templates/stats' && method === 'GET') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            totalTemplates: 10,
            verifiedTemplates: 4,
            premiumTemplates: 2,
            byDomain: { MATH: 5, SCIENCE: 5 },
          }),
        } as Response);
      }

      // Admin templates listing
      if (pathname === '/admin/api/v1/templates' && method === 'GET') {
        void search;
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            templates: [baseTemplate],
            total: 1,
            page: 1,
            pageSize: 50,
            hasMore: false,
          }),
        } as Response);
      }

      // Admin template creation
      if (pathname === '/admin/api/v1/templates' && method === 'POST') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ template: { ...baseTemplate, title: 'New Template', id: 'tmpl-2' } }),
        } as Response);
      }

      // Admin template update (flags)
      if (pathname.startsWith('/admin/api/v1/templates/') && method === 'PATCH') {
        if (pathname.endsWith('/status')) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({ template: { ...baseTemplate, status: JSON.parse(init?.body as string).status } }),
          } as Response);
        }
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ template: { ...baseTemplate, ...JSON.parse(init?.body as string) } }),
        } as Response);
      }

      if (pathname.startsWith('/admin/api/v1/templates/') && method === 'DELETE') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ warnings: [] }),
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

  it('renders stats and templates grid', async () => {
    renderWithProviders(<TemplatesAdminPage />);

    await waitFor(() => {
      expect(
        screen.getByText('Simulation Template Curation')
      ).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Total Templates')).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByText('Physics Lab')).toBeInTheDocument();
    });
  });

  it('sends verify request when Verify button is clicked', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch');

    renderWithProviders(<TemplatesAdminPage />);

    await waitFor(() => {
      expect(screen.getByText('Physics Lab')).toBeInTheDocument();
    });

    const verifyButton = screen.getByRole('button', { name: /verify/i });
    fireEvent.click(verifyButton);

    await waitFor(() => {
      const patchCall = fetchSpy.mock.calls.find(([url, init]) => {
        return (
          typeof url === 'string' &&
          url.startsWith('/admin/api/v1/templates/') &&
          (init as RequestInit | undefined)?.method === 'PATCH'
        );
      });
      expect(patchCall).toBeTruthy();
    });
  });

  it('opens create modal and submits draft', async () => {
    renderWithProviders(<TemplatesAdminPage />);

    await waitFor(() => expect(screen.getByText('Physics Lab')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: '+ New Template' }));

    const titleInput = screen.getByPlaceholderText('Title');
    fireEvent.change(titleInput, { target: { value: 'New Template' } });
    fireEvent.change(screen.getByPlaceholderText('Description'), { target: { value: 'desc' } });

    fireEvent.click(screen.getByRole('button', { name: 'Create Template' }));

    await waitFor(() => {
      expect(screen.queryByText('Create Template')).not.toBeInTheDocument();
    });
  });

  it('opens delete modal and calls delete endpoint', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch');
    renderWithProviders(<TemplatesAdminPage />);

    await waitFor(() => expect(screen.getByText('Physics Lab')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(screen.getByText('Delete Template')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Delete Template' }));

    await waitFor(() => {
      const deleteCall = fetchSpy.mock.calls.find(([url, init]) => {
        return (
          typeof url === 'string' &&
          url.startsWith('/admin/api/v1/templates/') &&
          (init as RequestInit | undefined)?.method === 'DELETE'
        );
      });
      expect(deleteCall).toBeTruthy();
    });
  });
});
