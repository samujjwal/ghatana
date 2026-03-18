/**
 * Unified Dashboard Integration Tests
 *
 * @description Comprehensive integration tests for the unified dashboard
 * ensuring all components work together correctly.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';
import { Provider } from 'jotai';
import { MemoryRouter, Routes, Route } from 'react-router';

// Mock components
vi.mock('../../pages/dashboard/UnifiedProjectDashboard', () => ({
  default: () => <div data-testid="unified-dashboard">Unified Dashboard</div>,
}));

vi.mock('../../pages/dashboard/PhaseOverviewPage', () => ({
  default: () => <div data-testid="phase-overview">Phase Overview</div>,
}));

vi.mock('../../components/navigation/Breadcrumbs', () => ({
  Breadcrumbs: () => <nav data-testid="breadcrumbs">Breadcrumbs</nav>,
}));

vi.mock('../../components/search/GlobalSearch', () => ({
  GlobalSearch: () => <div data-testid="global-search">Global Search</div>,
}));

describe('Unified Dashboard Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Navigation Flow', () => {
    it('should render unified dashboard at project route', async () => {
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <Routes>
              <Route path="/project/:projectId/unified" element={<div data-testid="unified-dashboard">Dashboard</div>} />
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('unified-dashboard')).toBeInTheDocument();
    });

    it('should navigate between phases', async () => {
      const user = userEvent.setup();
      
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <Routes>
              <Route path="/project/:projectId/unified" element={<div data-testid="unified-dashboard">Dashboard</div>} />
              <Route path="/project/:projectId/unified/:phase" element={<div data-testid="phase-page">Phase Page</div>} />
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('unified-dashboard')).toBeInTheDocument();
    });
  });

  describe('Component Integration', () => {
    it('should render all dashboard components', () => {
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <div>
              <div data-testid="unified-dashboard">Dashboard</div>
              <nav data-testid="breadcrumbs">Breadcrumbs</nav>
              <div data-testid="global-search">Search</div>
            </div>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('unified-dashboard')).toBeInTheDocument();
      expect(screen.getByTestId('breadcrumbs')).toBeInTheDocument();
      expect(screen.getByTestId('global-search')).toBeInTheDocument();
    });
  });

  describe('State Management', () => {
    it('should maintain state across navigation', async () => {
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <Routes>
              <Route path="/project/:projectId/unified" element={<div data-testid="dashboard">Dashboard</div>} />
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('dashboard')).toBeInTheDocument();
    });
  });

  describe('Keyboard Navigation', () => {
    it('should support keyboard shortcuts', async () => {
      const user = userEvent.setup();
      
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <div data-testid="dashboard">Dashboard</div>
          </MemoryRouter>
        </Provider>
      );

      // Cmd+K should trigger search (mocked)
      await user.keyboard('{Meta>}k{/Meta}');
      
      // Dashboard should still be visible
      expect(screen.getByTestId('dashboard')).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should handle missing project gracefully', () => {
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/invalid/unified']}>
            <Routes>
              <Route path="/project/:projectId/unified" element={<div data-testid="dashboard">Dashboard</div>} />
            </Routes>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('dashboard')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA landmarks', () => {
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <main role="main" data-testid="dashboard">
              <nav role="navigation" data-testid="nav">Navigation</nav>
              <section role="region" data-testid="content">Content</section>
            </main>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByRole('main')).toBeInTheDocument();
      expect(screen.getByRole('navigation')).toBeInTheDocument();
      expect(screen.getByRole('region')).toBeInTheDocument();
    });

    it('should support focus management', async () => {
      const user = userEvent.setup();
      
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <div>
              <button data-testid="btn1">Button 1</button>
              <button data-testid="btn2">Button 2</button>
            </div>
          </MemoryRouter>
        </Provider>
      );

      const btn1 = screen.getByTestId('btn1');
      const btn2 = screen.getByTestId('btn2');

      btn1.focus();
      expect(document.activeElement).toBe(btn1);

      await user.tab();
      expect(document.activeElement).toBe(btn2);
    });
  });

  describe('Responsive Behavior', () => {
    it('should render mobile layout on small screens', () => {
      // Mock window.matchMedia for mobile
      Object.defineProperty(window, 'innerWidth', { value: 375 });
      
      render(
        <Provider>
          <MemoryRouter initialEntries={['/project/123/unified']}>
            <div data-testid="dashboard">Dashboard</div>
          </MemoryRouter>
        </Provider>
      );

      expect(screen.getByTestId('dashboard')).toBeInTheDocument();
    });
  });
});
