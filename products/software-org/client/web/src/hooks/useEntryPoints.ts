/**
 * useEntryPoints Hook
 *
 * React hook for accessing entry points and checking access permissions.
 *
 * <p><b>Purpose</b><br>
 * Provides easy access to entry point configuration and access checks
 * from React components.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useEntryPoints } from '@/hooks/useEntryPoints';
 *
 * function MyComponent() {
 *   const { canAccess, getEntryPointsForCurrentUser } = useEntryPoints();
 *   
 *   if (!canAccess('admin-dashboard')) {
 *     return <AccessDenied />;
 *   }
 *   
 *   const myEntryPoints = getEntryPointsForCurrentUser();
 *   // render entry points...
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Entry point access hook
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useMemo, useCallback } from 'react';
import { useLocation } from 'react-router';
import { usePersona } from '@/hooks/usePersona';
import {
  getEntryPointRegistry,
  checkEntryPointAccess,
  isRootUser as checkIsRootUser,
  ENTRY_POINTS,
  ENTRY_POINT_CATEGORIES,
} from '@/config/entrypoints.config';
import type {
  EntryPoint,
  EntryPointCategory,
  EntryPointAccessContext,
  AccessCheckResult,
  OrganizationLayer,
} from '@/types/entrypoints';

// =============================================================================
// Types
// =============================================================================

export interface UseEntryPointsReturn {
  /** Check if current user can access an entry point by ID */
  canAccess: (entryPointId: string) => boolean;

  /** Check if current user can access an entry point by route */
  canAccessRoute: (route: string) => boolean;

  /** Get access check result with detailed information */
  checkAccess: (entryPointId: string) => AccessCheckResult;

  /** Get all entry points accessible to current user */
  getEntryPointsForCurrentUser: () => EntryPoint[];

  /** Get entry points for a specific organization layer */
  getEntryPointsForLayer: (layer: OrganizationLayer) => EntryPoint[];

  /** Get primary entry point for current user */
  getPrimaryEntryPoint: () => EntryPoint | undefined;

  /** Get entry point by ID */
  getEntryPoint: (id: string) => EntryPoint | undefined;

  /** Get entry point for current route */
  getCurrentEntryPoint: () => EntryPoint | undefined;

  /** Get all categories */
  getCategories: () => EntryPointCategory[];

  /** Access context for current user */
  accessContext: EntryPointAccessContext;

  /** Whether current user is a root user */
  isRootUser: boolean;

  /** All entry points (for root user) */
  allEntryPoints: EntryPoint[];
}

// =============================================================================
// Hook Implementation
// =============================================================================

/**
 * useEntryPoints Hook
 *
 * Provides entry point access checking and retrieval based on current user's
 * persona and permissions.
 */
export function useEntryPoints(): UseEntryPointsReturn {
  const location = useLocation();
  const { persona, isRootUser: personaIsRootUser } = usePersona();

  // Create access context from current persona
  const accessContext: EntryPointAccessContext = useMemo(() => ({
    persona: persona ? {
      id: persona.id,
      type: persona.type,
      layer: persona.layer || 'contributor',
      permissions: persona.permissions,
      departmentId: persona.departmentId,
      teamId: persona.teamId,
    } : null,
    isRootUser: personaIsRootUser,
  }), [persona, personaIsRootUser]);

  // Check if current context is root user
  const isRootUser = useMemo(() => {
    return checkIsRootUser(accessContext);
  }, [accessContext]);

  // Get registry
  const registry = useMemo(() => getEntryPointRegistry(), []);

  // Get all entry points
  const allEntryPoints = useMemo(() => {
    return registry.getAllEntryPoints();
  }, [registry]);

  /**
   * Check if user can access an entry point by ID
   */
  const canAccess = useCallback((entryPointId: string): boolean => {
    return registry.canAccess(entryPointId, accessContext);
  }, [registry, accessContext]);

  /**
   * Check if user can access an entry point by route
   */
  const canAccessRoute = useCallback((route: string): boolean => {
    const entryPoint = registry.getEntryPointByRoute(route);
    if (!entryPoint) {
      // If no entry point defined for route, allow access (fallback behavior)
      return true;
    }
    return checkEntryPointAccess(entryPoint, accessContext).allowed;
  }, [registry, accessContext]);

  /**
   * Get detailed access check result
   */
  const checkAccess = useCallback((entryPointId: string): AccessCheckResult => {
    const entryPoint = ENTRY_POINTS[entryPointId];
    if (!entryPoint) {
      return {
        allowed: false,
        reason: 'Entry point not found',
      };
    }
    return checkEntryPointAccess(entryPoint, accessContext);
  }, [accessContext]);

  /**
   * Get all entry points for current user
   */
  const getEntryPointsForCurrentUser = useCallback((): EntryPoint[] => {
    if (isRootUser) {
      return allEntryPoints;
    }

    if (!persona) {
      return [];
    }

    return registry.getEntryPointsForPersona(persona.type, persona.layer, accessContext);
  }, [isRootUser, persona, registry, accessContext, allEntryPoints]);

  /**
   * Get entry points for a specific organization layer
   */
  const getEntryPointsForLayer = useCallback((layer: OrganizationLayer): EntryPoint[] => {
    return registry.getEntryPointsForLayer(layer, accessContext);
  }, [registry, accessContext]);

  /**
   * Get primary entry point for current user
   */
  const getPrimaryEntryPoint = useCallback((): EntryPoint | undefined => {
    const entryPoints = getEntryPointsForCurrentUser();
    return entryPoints.find(ep => ep.isPrimary);
  }, [getEntryPointsForCurrentUser]);

  /**
   * Get entry point by ID
   */
  const getEntryPoint = useCallback((id: string): EntryPoint | undefined => {
    return ENTRY_POINTS[id];
  }, []);

  /**
   * Get entry point for current route
   */
  const getCurrentEntryPoint = useCallback((): EntryPoint | undefined => {
    return registry.getEntryPointByRoute(location.pathname);
  }, [registry, location.pathname]);

  /**
   * Get all categories
   */
  const getCategories = useCallback((): EntryPointCategory[] => {
    return Object.values(ENTRY_POINT_CATEGORIES).sort((a, b) => a.order - b.order);
  }, []);

  return {
    canAccess,
    canAccessRoute,
    checkAccess,
    getEntryPointsForCurrentUser,
    getEntryPointsForLayer,
    getPrimaryEntryPoint,
    getEntryPoint,
    getCurrentEntryPoint,
    getCategories,
    accessContext,
    isRootUser,
    allEntryPoints,
  };
}

export default useEntryPoints;
