export type StudioRouteId =
  | 'home'
  | 'ideas'
  | 'blueprints'
  | 'canvas'
  | 'develop'
  | 'lifecycle'
  | 'agents'
  | 'artifacts'
  | 'deployments'
  | 'health'
  | 'learn'
  | 'settings';

export type StudioRouteOwnership =
  | 'studio'
  | 'yappc'
  | 'kernel'
  | 'data-cloud'
  | 'shared';

export type StudioRouteStatus = 'ready' | 'empty' | 'degraded' | 'blocked';

export type RouteExposurePolicy = 'visible' | 'disabled' | 'hidden' | 'preview';

export interface StudioNavItem {
  readonly id: StudioRouteId;
  readonly path: string;
  readonly label: string;
  readonly labelKey: `studio.navigation.${StudioRouteId}`;
  readonly ownership: StudioRouteOwnership;
  readonly requiredCapability: string;
  readonly status: StudioRouteStatus;
  readonly isCustomerVisible: boolean;
  readonly exposure: RouteExposurePolicy;
}

export const STUDIO_NAV_ITEMS = [
  {
    id: 'home',
    path: '/',
    label: 'Home',
    labelKey: 'studio.navigation.home',
    ownership: 'studio',
    requiredCapability: 'studio.shell.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
  {
    id: 'ideas',
    path: '/ideas',
    label: 'Ideas',
    labelKey: 'studio.navigation.ideas',
    ownership: 'yappc',
    requiredCapability: 'yappc.ideas.view',
    status: 'degraded',
    isCustomerVisible: true,
    exposure: 'disabled',
  },
  {
    id: 'blueprints',
    path: '/blueprints',
    label: 'Blueprints',
    labelKey: 'studio.navigation.blueprints',
    ownership: 'yappc',
    requiredCapability: 'yappc.blueprints.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
  {
    id: 'canvas',
    path: '/canvas',
    label: 'Canvas',
    labelKey: 'studio.navigation.canvas',
    ownership: 'yappc',
    requiredCapability: 'yappc.canvas.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
  {
    id: 'develop',
    path: '/develop',
    label: 'Develop',
    labelKey: 'studio.navigation.develop',
    ownership: 'kernel',
    requiredCapability: 'kernel.development.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
  {
    id: 'lifecycle',
    path: '/lifecycle',
    label: 'Lifecycle',
    labelKey: 'studio.navigation.lifecycle',
    ownership: 'kernel',
    requiredCapability: 'kernel.lifecycle.view',
    status: 'degraded',
    isCustomerVisible: true,
    exposure: 'disabled',
  },
  {
    id: 'agents',
    path: '/agents',
    label: 'Agents',
    labelKey: 'studio.navigation.agents',
    ownership: 'data-cloud',
    requiredCapability: 'data-cloud.agent-evidence.view',
    status: 'degraded',
    isCustomerVisible: true,
    exposure: 'disabled',
  },
  {
    id: 'artifacts',
    path: '/artifacts',
    label: 'Artifacts',
    labelKey: 'studio.navigation.artifacts',
    ownership: 'kernel',
    requiredCapability: 'kernel.artifacts.view',
    status: 'empty',
    isCustomerVisible: true,
    exposure: 'disabled',
  },
  {
    id: 'deployments',
    path: '/deployments',
    label: 'Deployments',
    labelKey: 'studio.navigation.deployments',
    ownership: 'kernel',
    requiredCapability: 'kernel.deployments.view',
    status: 'blocked',
    isCustomerVisible: true,
    exposure: 'hidden',
  },
  {
    id: 'health',
    path: '/health',
    label: 'Health',
    labelKey: 'studio.navigation.health',
    ownership: 'data-cloud',
    requiredCapability: 'data-cloud.runtime-truth.view',
    status: 'degraded',
    isCustomerVisible: true,
    exposure: 'disabled',
  },
  {
    id: 'learn',
    path: '/learn',
    label: 'Learn',
    labelKey: 'studio.navigation.learn',
    ownership: 'yappc',
    requiredCapability: 'yappc.learning.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
  {
    id: 'settings',
    path: '/settings',
    label: 'Settings',
    labelKey: 'studio.navigation.settings',
    ownership: 'shared',
    requiredCapability: 'studio.settings.view',
    status: 'ready',
    isCustomerVisible: true,
    exposure: 'visible',
  },
] as const satisfies readonly StudioNavItem[];

export function findStudioNavItem(pathname: string): StudioNavItem | undefined {
  return STUDIO_NAV_ITEMS.find((item: StudioNavItem) => item.path === pathname);
}
