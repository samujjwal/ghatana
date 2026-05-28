/**
 * Backward-compatibility layer for legacy route capability imports.
 *
 * Route surface names are canonical. New imports should use RouteSurfaceRegistry.
 */

export {
  RouteLifecycleSchema,
  type RouteLifecycle,
  RouteCapabilitySchema,
  type RouteCapability,
  RouteCapabilityRegistrySchema,
  type RouteCapabilityRegistry,
  canonicalRouteRegistry,
  getDiscoverableRoutes,
  getRouteByPath,
  getActiveRoutes,
  getRoutesByLifecycle,
} from './RouteSurfaceRegistry';
