/**
 * K-005: Kernel fix-forward route lifecycle mode.
 * Products reject retired and removed route states unless explicitly configured by migration tooling.
 */

import { z } from "zod";

export const RouteLifecycleModeSchema = z.literal('fix-forward');

export const RetiredRouteStateSchema = z
  .object({
    path: z.string().trim().min(1).startsWith('/'),
    retiredAt: z.string().trim().min(1).optional(),
    removedAt: z.string().trim().min(1).optional(),
    migrationNotes: z.string().trim().min(1).optional(),
    redirectTo: z.string().trim().min(1).startsWith('/').optional(),
  })
  .strict();

export const RouteLifecycleConfigSchema = z
  .object({
    mode: RouteLifecycleModeSchema,
    allowRetired: z.literal(false),
    allowRemoved: z.literal(false),
    retiredRoutes: z.array(RetiredRouteStateSchema).optional(),
    migrationStrategy: z.enum(['redirect', 'error']).optional(),
  })
  .strict();

export type RouteLifecycleMode = 'fix-forward';

export type RetiredRouteState = z.infer<typeof RetiredRouteStateSchema>;

export type RouteLifecycleConfig = z.infer<typeof RouteLifecycleConfigSchema>;

export function validateRouteLifecycleMode(value: unknown): value is RouteLifecycleMode {
  return RouteLifecycleModeSchema.safeParse(value).success;
}

export function validateRetiredRouteState(value: unknown): value is RetiredRouteState {
  return RetiredRouteStateSchema.safeParse(value).success;
}

export function createFixForwardLifecycleConfig(): RouteLifecycleConfig {
  return {
    mode: 'fix-forward',
    allowRetired: false,
    allowRemoved: false,
    migrationStrategy: 'error',
  };
}

export function validateRouteLifecycleConfig(config: RouteLifecycleConfig): boolean {
  if (config.allowRetired) return false;
  if (config.allowRemoved) return false;
  return config.mode === 'fix-forward';
}

export function isRouteAllowedInLifecycle(
  path: string,
  config: RouteLifecycleConfig
): { allowed: boolean; reason?: string; redirectTo?: string } {
  const retiredRoute = config.retiredRoutes?.find(route => route.path === path);

  if (retiredRoute?.removedAt && !config.allowRemoved) {
    return {
      allowed: false,
      reason: `Route removed at ${retiredRoute.removedAt}`,
    };
  }

  if (retiredRoute?.retiredAt && !config.allowRetired) {
    if (config.migrationStrategy === 'redirect' && retiredRoute.redirectTo) {
      return {
        allowed: false,
        reason: `Route retired at ${retiredRoute.retiredAt}`,
        redirectTo: retiredRoute.redirectTo,
      };
    }
    return {
      allowed: false,
      reason: `Route retired at ${retiredRoute.retiredAt}`,
    };
  }

  return { allowed: true };
}
