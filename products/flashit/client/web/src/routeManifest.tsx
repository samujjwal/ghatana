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

export type FlashItRole = 'guest' | 'member' | 'premium' | 'admin';

export interface FlashItRouteManifestEntry extends ProductRouteCapability {
  readonly element: React.ReactElement;
  readonly requiresAuthentication: boolean;
}

export const FLASHIT_ROLE_ORDER: Readonly<Record<FlashItRole, number>> = {
  guest: 0,
  member: 1,
  premium: 2,
  admin: 3,
};

export function isRouteAllowedForRole(
  route: Pick<FlashItRouteManifestEntry, 'minimumRole'>,
  role: FlashItRole,
): boolean {
  if (!route.minimumRole) {
    return true;
  }

  return FLASHIT_ROLE_ORDER[role] >= FLASHIT_ROLE_ORDER[route.minimumRole as FlashItRole];
}

export const flashitRouteManifest: readonly FlashItRouteManifestEntry[] = [
  {
    path: '/',
    label: 'Dashboard',
    description: 'Overview of memories, activity, and recommended next steps.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['view-dashboard'],
    cards: ['weekly-activity', 'recent-moments', 'capture-shortcuts'],
    iconName: 'home',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <DashboardPage />,
  },
  {
    path: '/capture',
    label: 'Capture',
    description: 'Capture a new moment with media and metadata.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator'],
    tiers: ['core'],
    actions: ['capture-moment'],
    cards: ['capture-prompt', 'media-uploader'],
    iconName: 'plus-circle',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <CapturePage />,
  },
  {
    path: '/moments',
    label: 'Moments',
    description: 'Browse captured memories and reflections.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['view-moments'],
    cards: ['moment-list', 'moment-filters'],
    iconName: 'file-text',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <MomentsPage />,
  },
  {
    path: '/spheres',
    label: 'Spheres',
    description: 'Organize memory collections by sphere and context.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['manage-spheres'],
    cards: ['sphere-overview', 'sphere-health'],
    iconName: 'layers',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <SpheresPage />,
  },
  {
    path: '/search',
    label: 'Search',
    description: 'Search across memories, tags, and summaries.',
    group: 'Discover',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['search-memories'],
    cards: ['saved-searches', 'search-results'],
    iconName: 'search',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <SearchPage />,
  },
  {
    path: '/analytics',
    label: 'Analytics',
    description: 'Track trends, activity, and memory patterns.',
    group: 'Discover',
    minimumRole: 'premium',
    personas: ['reflector', 'caregiver'],
    tiers: ['premium'],
    actions: ['view-analytics'],
    cards: ['meaning-metrics', 'trend-breakdown'],
    iconName: 'bar-chart-3',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <AnalyticsPage />,
  },
  {
    path: '/reflection',
    label: 'Reflection',
    description: 'Review summaries and guided reflection insights.',
    group: 'Discover',
    minimumRole: 'member',
    personas: ['reflector'],
    tiers: ['core'],
    actions: ['review-reflection'],
    cards: ['reflection-prompts', 'summary-panel'],
    iconName: 'brain',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <ReflectionPage />,
  },
  {
    path: '/collaboration',
    label: 'Collaboration',
    description: 'Shared review and collaboration workflows.',
    group: 'Governance',
    minimumRole: 'premium',
    personas: ['caregiver', 'partner'],
    tiers: ['premium'],
    actions: ['share-memory'],
    cards: ['shared-reviews', 'collaborator-activity'],
    iconName: 'users',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <CollaborationPage />,
  },
  {
    path: '/memory-expansion',
    label: 'Memory Expansion',
    description: 'Enrich moments with additional context and prompts.',
    group: 'Governance',
    minimumRole: 'premium',
    personas: ['reflector', 'creator'],
    tiers: ['premium'],
    actions: ['expand-memory'],
    cards: ['expansion-suggestions'],
    iconName: 'sparkles',
    lifecycle: 'stable',
    discoverable: false,
    requiresAuthentication: true,
    element: <MemoryExpansionPage />,
  },
  {
    path: '/language-insights',
    label: 'Language Insights',
    description: 'Analyze language evolution and communication trends.',
    group: 'Discover',
    minimumRole: 'premium',
    personas: ['reflector'],
    tiers: ['premium'],
    actions: ['view-language-insights'],
    cards: ['language-trends', 'return-to-meaning-rate'],
    iconName: 'languages',
    lifecycle: 'stable',
    discoverable: false,
    requiresAuthentication: true,
    element: <LanguageInsightsPage />,
  },
  {
    path: '/settings',
    label: 'Settings',
    description: 'Profile, preferences, privacy, and account controls.',
    group: 'Governance',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['manage-settings'],
    cards: ['privacy-controls', 'account-preferences'],
    iconName: 'settings',
    lifecycle: 'stable',
    requiresAuthentication: true,
    element: <SettingsPage />,
  },
];

export function getFlashitNavigationRoutes(
  role: FlashItRole = 'member',
): readonly FlashItRouteManifestEntry[] {
  return flashitRouteManifest.filter(
    (route) => route.discoverable !== false && isRouteAllowedForRole(route, role),
  );
}
