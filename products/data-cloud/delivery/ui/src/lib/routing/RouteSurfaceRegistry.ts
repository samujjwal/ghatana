export {
  RouteLifecycleSchema,
  type RouteLifecycle,
  RouteCapabilitySchema as RouteSurfaceSchema,
  type RouteCapability as RouteSurface,
  RouteCapabilityRegistrySchema as RouteSurfaceRegistrySchema,
  type RouteCapabilityRegistry as RouteSurfaceRegistry,
  canonicalRouteRegistry as canonicalRouteSurfaceRegistry,
  getDiscoverableRoutes as getDiscoverableRouteSurfaces,
  getRouteByPath as getRouteSurfaceByPath,
  getActiveRoutes as getActiveRouteSurfaces,
  getRoutesByLifecycle as getRouteSurfacesByLifecycle,
} from './RouteCapabilityRegistry';
