/**
 * K-005: Kernel no-legacy/no-deprecation route mode.
 * Product can opt into fix-forward route model that rejects deprecated/removed states.
 */

export type RouteLifecycleMode = 'fix-forward' | 'legacy-compatible';

export type DeprecatedRouteState = {
  path: string;
  deprecatedAt?: string;
  removedAt?: string;
  migrationNotes?: string;
  redirectTo?: string;
};

export type RouteLifecycleConfig = {
  mode: RouteLifecycleMode;
  allowDeprecated: boolean;
  allowRemoved: boolean;
  deprecatedRoutes?: DeprecatedRouteState[];
  migrationStrategy?: 'redirect' | 'error' | 'silent';
};

export function createFixForwardLifecycleConfig(): RouteLifecycleConfig {
  return {
    mode: 'fix-forward',
    allowDeprecated: false,
    allowRemoved: false,
    migrationStrategy: 'error',
  };
}

export function createLegacyCompatibleLifecycleConfig(): RouteLifecycleConfig {
  return {
    mode: 'legacy-compatible',
    allowDeprecated: true,
    allowRemoved: false,
    migrationStrategy: 'redirect',
  };
}

export function validateRouteLifecycleConfig(config: RouteLifecycleConfig): boolean {
  if (config.mode === 'fix-forward') {
    if (config.allowDeprecated) return false;
    if (config.allowRemoved) return false;
  }
  return true;
}

export function isRouteAllowedInLifecycle(
  path: string,
  config: RouteLifecycleConfig
): { allowed: boolean; reason?: string; redirectTo?: string } {
  const deprecatedRoute = config.deprecatedRoutes?.find(r => r.path === path);
  
  if (deprecatedRoute) {
    if (deprecatedRoute.removedAt && !config.allowRemoved) {
      return {
        allowed: false,
        reason: `Route removed at ${deprecatedRoute.removedAt}`,
      };
    }
    
    if (deprecatedRoute.deprecatedAt && !config.allowDeprecated) {
      if (config.migrationStrategy === 'redirect' && deprecatedRoute.redirectTo) {
        return {
          allowed: false,
          reason: `Route deprecated at ${deprecatedRoute.deprecatedAt}`,
          redirectTo: deprecatedRoute.redirectTo,
        };
      }
      return {
        allowed: false,
        reason: `Route deprecated at ${deprecatedRoute.deprecatedAt}`,
      };
    }
  }
  
  return { allowed: true };
}
