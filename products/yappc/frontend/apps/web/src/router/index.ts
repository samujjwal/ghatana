/**
 * Router Module Exports
 *
 * @description Central export for routing configuration, paths, and hooks.
 */

// Router and Provider
export { router, AppRouter, default } from './routes';

// Route Paths
export { ROUTES, generateBreadcrumbs, matchRoute, extractRouteParams, getPhaseFromPath, buildUrl, parseQueryParams } from './paths';
export type { RouteKey, BreadcrumbItem } from './paths';

// Navigation Hooks
export {
  useProjectNavigation,
  useAppNavigation,
  useRouteState,
  useBreadcrumbs,
  useNavigationHistory,
  useUrlBuilder,
  usePhaseNavigation,
  useDeepLink,
} from './hooks';
