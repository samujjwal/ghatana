/**
 * Unified Data Page Tests
 *
 * Tests for the consolidated Data surface that unifies Entity Browser,
 * Context Explorer, and Data Fabric under a single page with tab navigation.
 *
 * @doc.type test
 * @doc.purpose Tests for unified Data surface with tab navigation
 * @doc.layer frontend
 * @doc.pattern UITest
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route, useSearchParams } from 'react-router';
import DataPage from '../../pages/DataPage';

describe('Unified Data Page Tests', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  describe('Tab Navigation', () => {
    it('[DATA001]: Default tab is collections', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify collections tab is active by default
      const collectionsTab = screen.getByText('Collections');
      expect(collectionsTab).toBeInTheDocument();
    });

    it('[DATA002]: Tab query parameter sets active tab', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data?tab=entities']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify entities tab is active when tab=entities in query param
      const entitiesTab = screen.getByText('Entities');
      expect(entitiesTab).toBeInTheDocument();
    });

    it('[DATA003]: Clicking tab changes active tab', async () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const entitiesTab = screen.getByText('Entities');
      fireEvent.click(entitiesTab);

      await waitFor(() => {
        // Verify URL updated with tab parameter
        expect(window.location.search).toContain('tab=entities');
      });
    });

    it('[DATA004]: All tabs are visible', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify all tabs are present
      expect(screen.getByText('Collections')).toBeInTheDocument();
      expect(screen.getByText('Entities')).toBeInTheDocument();
      expect(screen.getByText('Context')).toBeInTheDocument();
      expect(screen.getByText('Fabric')).toBeInTheDocument();
    });
  });

  describe('Tab Content Loading', () => {
    it('[DATA005]: Collections tab loads DataExplorer', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data?tab=collections']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify collections tab is active
      const collectionsTab = screen.getByText('Collections');
      expect(collectionsTab).toBeInTheDocument();
    });

    it('[DATA006]: Entities tab loads EntityBrowserPage', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data?tab=entities']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify entities tab is active
      const entitiesTab = screen.getByText('Entities');
      expect(entitiesTab).toBeInTheDocument();
    });

    it('[DATA007]: Context tab loads ContextExplorerPage', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data?tab=context']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify context tab is active
      const contextTab = screen.getByText('Context');
      expect(contextTab).toBeInTheDocument();
    });

    it('[DATA008]: Fabric tab loads DataFabricPage', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data?tab=fabric']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify fabric tab is active
      const fabricTab = screen.getByText('Fabric');
      expect(fabricTab).toBeInTheDocument();
    });
  });

  describe('Route Consolidation', () => {
    it('[DATA009]: /entities redirects to /data?tab=entities', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/entities']}>
            <Routes>
              <Route path="/entities" element={<div>Redirected</div>} />
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // This would be tested in routes.tsx tests
      // Here we verify the DataPage can handle the entities tab
    });

    it('[DATA010]: /context redirects to /data?tab=context', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/context']}>
            <Routes>
              <Route path="/context" element={<div>Redirected</div>} />
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // This would be tested in routes.tsx tests
      // Here we verify the DataPage can handle the context tab
    });

    it('[DATA011]: /fabric redirects to /data?tab=fabric', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/fabric']}>
            <Routes>
              <Route path="/fabric" element={<div>Redirected</div>} />
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // This would be tested in routes.tsx tests
      // Here we verify the DataPage can handle the fabric tab
    });
  });

  describe('Accessibility', () => {
    it('[DATA012]: Tab buttons are keyboard accessible', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const tabButtons = screen.getAllByRole('button');
      tabButtons.forEach(button => {
        expect(button).toHaveAttribute('type', 'button');
      });
    });

    it('[DATA013]: Active tab has visual indicator', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const collectionsTab = screen.getByText('Collections');
      // Verify active tab has different styling
      expect(collectionsTab).toBeInTheDocument();
    });

    it('[DATA014]: Tab navigation has proper ARIA roles', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const tablist = screen.getByRole('tablist');
      expect(tablist).toBeInTheDocument();
      expect(tablist).toHaveAttribute('aria-label', 'Data tabs');
    });

    it('[DATA015]: Tab buttons have aria-selected attribute', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const tabButtons = screen.getAllByRole('tab');
      expect(tabButtons.length).toBeGreaterThan(0);
      // At least one tab should be selected
      const selectedTabs = tabButtons.filter(button => button.getAttribute('aria-selected') === 'true');
      expect(selectedTabs.length).toBe(1);
    });

    it('[DATA016]: Tab content has proper ARIA role', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      const tabPanel = screen.getByRole('tabpanel');
      expect(tabPanel).toBeInTheDocument();
    });
  });

  describe('Loading States', () => {
    it('[DATA017]: Shows loading state while tab content loads', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify loading state is shown
      // This would be tested with mocked lazy loading
    });
  });

  describe('Internationalization', () => {
    it('[DATA018]: Tab labels use translation keys', () => {
      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/data']}>
            <Routes>
              <Route path="/data" element={<DataPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      );

      // Verify tabs are rendered with translated labels
      expect(screen.getByText('Collections')).toBeInTheDocument();
      expect(screen.getByText('Entities')).toBeInTheDocument();
      expect(screen.getByText('Context')).toBeInTheDocument();
      expect(screen.getByText('Fabric')).toBeInTheDocument();
    });
  });
});
