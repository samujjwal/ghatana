/**
 * @ghatana/ghatana-studio navigation test suite
 * Tests for Ghatana Studio navigation and routing
 */

import { describe, it, expect } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import App from '../App';
import {
  resolveStudioNavItems,
  resolveStudioRouteCapabilityState,
  STUDIO_NAV_ITEMS,
  type StudioNavItem,
} from '../navigation/studioNavigation';
import { STUDIO_TRANSLATIONS } from '../i18n/studioTranslations';

const UNCONFIGURED_NAV_ITEMS = resolveStudioNavItems(
  resolveStudioRouteCapabilityState({
    runtimeConfigured: false,
    lifecycleStatus: 'unconfigured',
  }),
);

function renderApp(initialEntry: string = '/'): void {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <App />
    </MemoryRouter>,
  );
}

describe('@ghatana/ghatana-studio - Navigation', () => {
  describe('Route Navigation', () => {
    it('should render App component', () => {
      renderApp();
      expect(screen.getByText('Ghatana Studio')).toBeTruthy();
    });

    it('should display all customer-visible navigation links in sidebar', () => {
      renderApp();

      for (const item of UNCONFIGURED_NAV_ITEMS.filter((candidate) => candidate.exposure !== 'hidden')) {
        expect(screen.getByRole('link', { name: new RegExp(item.label, 'i') })).toBeInTheDocument();
      }
    });

    it('should have translations for every canonical navigation label key', () => {
      for (const item of STUDIO_NAV_ITEMS) {
        expect(STUDIO_TRANSLATIONS[item.labelKey]).toBe(item.label);
      }
    });

    it('should display header with version', () => {
      renderApp();
      expect(screen.getByText('dev')).toBeTruthy();
    });

    it('should render a route-aware page title', () => {
      renderApp('/lifecycle');
      expect(screen.getAllByRole('heading', { name: /lifecycle/i }).length).toBeGreaterThan(0);
      expect(screen.getByText('kernel owned')).toBeInTheDocument();
    });

    it('should mark the active route with aria-current', () => {
      renderApp('/health');
      expect(screen.getByRole('link', { name: /health/i })).toHaveAttribute('aria-current', 'page');
    });

    it('should expose visible focus styling for keyboard navigation links', () => {
      renderApp('/health');
      expect(screen.getByRole('link', { name: /health/i })).toHaveClass('focus-visible:outline');
    });

    it('should render not found for unknown routes', () => {
      renderApp('/missing-route');
      expect(screen.getAllByText('Page not found').length).toBeGreaterThan(0);
      expect(screen.getByText(/not registered in the canonical navigation/i)).toBeInTheDocument();
    });

    it('should render production Studio route surfaces for lifecycle work', () => {
      renderApp('/develop');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();
    });

    it('should render lifecycle truth panels without raw execution controls', () => {
      renderApp('/lifecycle');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /run command/i })).not.toBeInTheDocument();
    });

    it('should render agent, artifact, deployment, and health route content', () => {
      renderApp('/agents');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();

      cleanup();
      renderApp('/artifacts');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();

      cleanup();
      renderApp('/deployments');
      expect(screen.getAllByText(/Page not found/i).length).toBeGreaterThan(0);

      cleanup();
      renderApp('/health');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();
    });

    it('should render YAPPC workflow routes with intent and artifact intelligence evidence', () => {
      renderApp('/ideas');
      expect(screen.getByText('Route access is disabled in this runtime mode.')).toBeInTheDocument();

      cleanup();
      renderApp('/blueprints');
      expect(screen.getAllByRole('heading', { name: 'Blueprints' }).length).toBeGreaterThan(0);
      expect(screen.getByText('web-app')).toBeInTheDocument();
      expect(screen.getByText(/generated changes/i)).toBeInTheDocument();

      cleanup();
      renderApp('/canvas');
      expect(screen.getAllByRole('heading', { name: 'Canvas' }).length).toBeGreaterThan(0);
      expect(screen.getByText(/Residual islands/i)).toBeInTheDocument();
      expect(screen.getAllByText(/legacy-promo-widget/i).length).toBeGreaterThan(0);

      cleanup();
      renderApp('/learn');
      expect(screen.getAllByRole('heading', { name: 'Learn' }).length).toBeGreaterThan(0);
      expect(screen.getByText(/Promote legacy-promo-widget/i)).toBeInTheDocument();
    });
  });

  describe('Sidebar Navigation', () => {
    it('should render sidebar with all sections', () => {
      renderApp();
      expect(screen.getByRole('navigation', { name: /studio navigation/i })).toBeInTheDocument();
      expect(screen.getByRole('main')).toBeInTheDocument();
    });

    it('should not include duplicate navigation ids or paths', () => {
      const ids = STUDIO_NAV_ITEMS.map((item: StudioNavItem) => item.id);
      const paths = STUDIO_NAV_ITEMS.map((item: StudioNavItem) => item.path);

      expect(new Set(ids).size).toBe(ids.length);
      expect(new Set(paths).size).toBe(paths.length);
    });

    it('should preserve landmarks and active route state for every customer route', () => {
      for (const item of UNCONFIGURED_NAV_ITEMS.filter((candidate) => candidate.exposure !== 'hidden')) {
        cleanup();
        renderApp(item.path);

        expect(screen.getByRole('navigation', { name: /studio navigation/i })).toBeInTheDocument();
        expect(screen.getByRole('main')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: new RegExp(item.label, 'i') })).toHaveAttribute(
          'aria-current',
          'page',
        );
      }
    });

    it('should hide routes with exposure: hidden from navigation', () => {
      renderApp();
      const hiddenRoutes = UNCONFIGURED_NAV_ITEMS.filter((item) => item.exposure === 'hidden');
      for (const route of hiddenRoutes) {
        expect(screen.queryByRole('link', { name: new RegExp(route.label, 'i') })).not.toBeInTheDocument();
      }
    });

    it('should render but disable routes with exposure: disabled', () => {
      renderApp();
      const disabledRoutes = UNCONFIGURED_NAV_ITEMS.filter((item) => item.exposure === 'disabled');
      for (const route of disabledRoutes) {
        const link = screen.getByRole('link', { name: new RegExp(route.label, 'i') });
        expect(link).toBeInTheDocument();
        expect(link).toHaveClass('cursor-not-allowed');
        expect(link).toHaveAttribute('aria-disabled', 'true');
      }
    });

    it('should render and enable routes with exposure: visible', () => {
      renderApp();
      const visibleRoutes = UNCONFIGURED_NAV_ITEMS.filter((item) => item.exposure === 'visible' || item.exposure === 'preview');
      for (const route of visibleRoutes) {
        const link = screen.getByRole('link', { name: new RegExp(route.label, 'i') });
        expect(link).toBeInTheDocument();
        expect(link).not.toHaveClass('cursor-not-allowed');
        expect(link).not.toHaveAttribute('aria-disabled');
      }
    });
  });
});
