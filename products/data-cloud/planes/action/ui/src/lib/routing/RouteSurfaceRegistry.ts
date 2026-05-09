export {
  RouteLifecycleSchema,
  type RouteLifecycle,
  RouteCapabilitySchema as RouteSurfaceSchema,
  type RouteCapability as RouteSurface,
  RouteCapabilityRegistrySchema as RouteSurfaceRegistrySchema,
  type RouteCapabilityRegistry as RouteSurfaceRegistry,
  aepRouteRegistry as aepRouteSurfaceRegistry,
  getDiscoverableRoutes as getDiscoverableRouteSurfaces,
  getRouteByPath as getRouteSurfaceByPath,
  getActiveRoutes as getActiveRouteSurfaces,
  getRoutesByLifecycle as getRouteSurfacesByLifecycle,
  canAccessRoute,
} from './RouteCapabilityRegistry';
