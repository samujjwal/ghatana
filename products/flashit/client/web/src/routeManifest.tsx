import React from 'react';
import type { ProductRouteCapability } from '@ghatana/product-shell';
import DashboardPage from './pages/DashboardPage';
import CapturePage from './pages/CapturePage';
import MomentsPage from './pages/MomentsPage';
import SpheresPage from './pages/SpheresPage';
import SearchPage from './pages/SearchPage';
import AnalyticsPage from './pages/AnalyticsPage';
import SettingsPage from './pages/SettingsPage';
import ReflectionPage from './pages/ReflectionPage';
import CollaborationPage from './pages/CollaborationPage';
import MemoryExpansionPage from './pages/MemoryExpansionPage';
import { LanguageInsightsPage } from './pages/LanguageInsightsPage';
import { isRouteAllowedForRole, type FlashItRole } from './routeAccess';
import { flashItRouteContracts } from '../../../backend/gateway/src/routes/route-manifest.contract';

export interface FlashItRouteManifestEntry extends ProductRouteCapability {
  readonly element: React.ReactElement;
  readonly requiresAuthentication: boolean;
}

const ROUTE_ELEMENTS: Readonly<Record<string, React.ReactElement>> = {
  '/': <DashboardPage />,
  '/capture': <CapturePage />,
  '/moments': <MomentsPage />,
  '/spheres': <SpheresPage />,
  '/search': <SearchPage />,
  '/analytics': <AnalyticsPage />,
  '/reflection': <ReflectionPage />,
  '/collaboration': <CollaborationPage />,
  '/memory-expansion': <MemoryExpansionPage />,
  '/language-insights': <LanguageInsightsPage />,
  '/settings': <SettingsPage />,
};

export const flashitRouteManifest: readonly FlashItRouteManifestEntry[] = flashItRouteContracts.map((route) => ({
  ...route,
  requiresAuthentication: true,
  element: ROUTE_ELEMENTS[route.path] ?? <DashboardPage />,
}));

export function getFlashitNavigationRoutes(
  role: FlashItRole = 'member',
): readonly FlashItRouteManifestEntry[] {
  return flashitRouteManifest.filter(
    (route) => route.discoverable !== false && isRouteAllowedForRole(route, role),
  );
}
