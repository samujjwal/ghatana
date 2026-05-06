import type { ProductRouteCapability } from '@ghatana/product-shell';

export type FlashItMobileScreen =
  | 'Dashboard'
  | 'Moments'
  | 'Capture'
  | 'Spheres'
  | 'Settings'
  | 'LanguageInsights'
  | 'NotificationSettings'
  | 'Search'
  | 'Analytics'
  | 'Billing'
  | 'Collaboration'
  | 'Reflection'
  | 'MemoryExpansion';

export interface FlashItMobileRouteManifestEntry extends ProductRouteCapability {
  readonly screen: FlashItMobileScreen;
  readonly requiresAuthentication: boolean;
  readonly showInTabBar?: boolean;
  readonly showInSettings?: boolean;
}

export type FlashItMobileRole = 'member' | 'premium' | 'admin';

export const FLASHIT_MOBILE_ROLE_ORDER: Readonly<Record<FlashItMobileRole, number>> = {
  member: 0,
  premium: 1,
  admin: 2,
};

function readStringField(value: unknown, key: string): string | null {
  if (!value || typeof value !== 'object' || !(key in value)) {
    return null;
  }

  const fieldValue = (value as Record<string, unknown>)[key];
  return typeof fieldValue === 'string' ? fieldValue : null;
}

export function resolveFlashitMobileRole(currentUser: unknown): FlashItMobileRole {
  const rawRole = readStringField(currentUser, 'role')?.trim().toLowerCase();
  if (rawRole === 'admin') {
    return 'admin';
  }
  if (rawRole === 'premium') {
    return 'premium';
  }

  const rawTier = readStringField(currentUser, 'tier')?.trim().toLowerCase();
  if (rawTier === 'premium' || rawTier === 'pro' || rawTier === 'plus') {
    return 'premium';
  }

  return 'member';
}

export const flashitMobileRouteManifest: readonly FlashItMobileRouteManifestEntry[] = [
  {
    screen: 'Dashboard',
    path: '/mobile/dashboard',
    label: 'Home',
    description: 'Overview of memories, capture prompts, and recent activity.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['view-dashboard'],
    iconName: 'home-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInTabBar: true,
  },
  {
    screen: 'Moments',
    path: '/mobile/moments',
    label: 'Moments',
    description: 'Browse and filter captured moments.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['view-moments'],
    iconName: 'time-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInTabBar: true,
  },
  {
    screen: 'Capture',
    path: '/mobile/capture',
    label: 'Capture',
    description: 'Create a new moment from text, audio, image, or video.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator'],
    tiers: ['core'],
    actions: ['capture-moment'],
    iconName: 'add-circle-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInTabBar: true,
  },
  {
    screen: 'Spheres',
    path: '/mobile/spheres',
    label: 'Spheres',
    description: 'Manage memory collections and sphere organization.',
    group: 'Capture',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['manage-spheres'],
    iconName: 'grid-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInTabBar: true,
  },
  {
    screen: 'Settings',
    path: '/mobile/settings',
    label: 'Settings',
    description: 'Profile, sync, privacy, and runtime preferences.',
    group: 'Governance',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['manage-settings'],
    iconName: 'settings-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInTabBar: true,
  },
  {
    screen: 'Search',
    path: '/mobile/search',
    label: 'Search Moments',
    description: 'Search across memories and associated metadata.',
    group: 'Discover',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['search-memories'],
    iconName: 'search-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'Analytics',
    path: '/mobile/analytics',
    label: 'Analytics',
    description: 'Track usage and memory trends.',
    group: 'Discover',
    minimumRole: 'premium',
    personas: ['reflector', 'caregiver'],
    tiers: ['premium'],
    actions: ['view-analytics'],
    iconName: 'bar-chart-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'Billing',
    path: '/mobile/billing',
    label: 'Billing & Subscription',
    description: 'Manage subscription and billing preferences.',
    group: 'Governance',
    minimumRole: 'premium',
    personas: ['reflector', 'creator'],
    tiers: ['premium'],
    actions: ['manage-billing'],
    iconName: 'card-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'Collaboration',
    path: '/mobile/collaboration',
    label: 'Collaboration',
    description: 'Shared memories and collaboration workflows.',
    group: 'Governance',
    minimumRole: 'premium',
    personas: ['caregiver', 'partner'],
    tiers: ['premium'],
    actions: ['share-memory'],
    iconName: 'people-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'Reflection',
    path: '/mobile/reflection',
    label: 'Reflection & Insights',
    description: 'Guided reflection and insight review.',
    group: 'Discover',
    minimumRole: 'member',
    personas: ['reflector'],
    tiers: ['core'],
    actions: ['review-reflection'],
    iconName: 'bulb-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'MemoryExpansion',
    path: '/mobile/memory-expansion',
    label: 'Memory Expansion',
    description: 'Expand memory context and generated summaries.',
    group: 'Discover',
    minimumRole: 'premium',
    personas: ['reflector', 'creator'],
    tiers: ['premium'],
    actions: ['expand-memory'],
    iconName: 'extension-puzzle-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'NotificationSettings',
    path: '/mobile/notification-settings',
    label: 'Notification Settings',
    description: 'Control notification preferences and delivery.',
    group: 'Governance',
    minimumRole: 'member',
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core'],
    actions: ['manage-notifications'],
    iconName: 'notifications-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
    showInSettings: true,
  },
  {
    screen: 'LanguageInsights',
    path: '/mobile/language-insights',
    label: 'Language Insights',
    description: 'Review language evolution and vocabulary trends.',
    group: 'Discover',
    minimumRole: 'premium',
    personas: ['reflector'],
    tiers: ['premium'],
    actions: ['view-language-insights'],
    iconName: 'analytics-outline',
    lifecycle: 'stable',
    requiresAuthentication: true,
  },
];

export function isFlashitMobileRouteAllowed(
  route: FlashItMobileRouteManifestEntry,
  currentUser: unknown,
): boolean {
  const currentRole = resolveFlashitMobileRole(currentUser);
  const minimumRole = route.minimumRole;

  if (!minimumRole) {
    return true;
  }

  const requiredLevel = FLASHIT_MOBILE_ROLE_ORDER[minimumRole as FlashItMobileRole];
  if (typeof requiredLevel !== 'number') {
    return true;
  }

  return FLASHIT_MOBILE_ROLE_ORDER[currentRole] >= requiredLevel;
}

export function getFlashitMobileAccessibleRoutes(
  currentUser: unknown,
): readonly FlashItMobileRouteManifestEntry[] {
  return flashitMobileRouteManifest.filter((route) => isFlashitMobileRouteAllowed(route, currentUser));
}

export function getFlashitMobileTabRoutes(
  currentUser: unknown,
): readonly FlashItMobileRouteManifestEntry[] {
  return getFlashitMobileAccessibleRoutes(currentUser).filter((route) => route.showInTabBar);
}

export function getFlashitMobileSettingsRoutes(
  currentUser: unknown,
): readonly FlashItMobileRouteManifestEntry[] {
  return getFlashitMobileAccessibleRoutes(currentUser).filter((route) => route.showInSettings);
}
