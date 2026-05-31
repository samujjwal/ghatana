/**
 * Backward-compatibility layer for legacy route capability imports.
 *
 * Route surface names are canonical. New imports should use RouteSurfaceRegistry.
 */

export {
  RouteCapabilityRegistrySchema,
  RouteCapabilitySchema,
  RouteLifecycleSchema,
  canonicalRouteRegistry,
  getActiveRoutes,
  getDiscoverableRoutes,
  getRouteByPath,
  getRoutesByLifecycle,
  type RouteCapability,
  type RouteCapabilityRegistry,
  type RouteLifecycle,
} from "./RouteSurfaceRegistry";
