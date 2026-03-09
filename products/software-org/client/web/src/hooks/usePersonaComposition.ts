/**
 * Persona Composition Hook
 *
 * <p><b>Purpose</b><br>
 * React hook for accessing merged multi-role persona configurations.
 * Automatically composes configurations based on user's active roles from API.
 *
 * <p><b>Data Flow</b><br>
 * 1. Fetch role definitions from API (useRoleDefinitions)
 * 2. Fetch user's persona preference from API (usePersonaPreference)
 * 3. Compose role configs + user preferences + workspace overrides
 * 4. Apply permission filtering and priority-based merging
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { usePersonaComposition } from '@/hooks/usePersonaComposition';
 *
 * function Dashboard() {
 *   const { merged, isLoading, roles } = usePersonaComposition('workspace-123');
 *   return <div>{merged?.quickActions.map(action => ...)}</div>;
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose React hook for multi-role persona composition with API integration
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useMemo } from 'react';
import { PersonaCompositionEngine } from '@/lib/persona/PersonaCompositionEngine';
import { adaptPersonaConfigs } from '@/lib/persona/personaConfigAdapter';
import { PERSONA_CONFIGS } from '@/config/personaConfig';
import {
    useRoleDefinitions,
    usePersonaPreference,
} from '@/lib/hooks/usePersonaQueries';
import type { MergedPersonaConfigV2, UserRole } from '@/schemas/persona.schema';

/**
 * Hook for accessing merged persona configuration with API integration
 *
 * Features:
 * - Multi-role composition with priority-based merging
 * - API-backed role definitions and user preferences
 * - Automatic recomputation when API data changes
 * - Backward compatibility with existing PersonaConfig
 * - Optimistic updates via React Query cache
 *
 * @param workspaceId Workspace ID for fetching preferences (default: 'default')
 * @returns Merged persona configuration and metadata
 */
export function usePersonaComposition(workspaceId: string = 'default') {
    // Fetch role definitions from API (cached for 1 hour)
    const { data: roles, isLoading: rolesLoading } = useRoleDefinitions();

    // Fetch user's persona preference from API (cached for 5 minutes)
    const { data: preference, isLoading: preferenceLoading } = usePersonaPreference(workspaceId);

    // Extract active roles from preference (fallback to empty array)
    const activeRoles = useMemo<UserRole[]>(() => {
        if (!preference?.activeRoles) return [];

        // Map role IDs to UserRole type (filter out unknown roles)
        return preference.activeRoles
            .filter((roleId): roleId is UserRole => {
                return ['admin', 'lead', 'engineer', 'viewer'].includes(roleId);
            });
    }, [preference?.activeRoles]);

    // Adapt legacy configs to v2 schema
    // TODO: Replace with API-based role definitions once adapter supports RoleDefinition type
    const v2Configs = useMemo(() => {
        return adaptPersonaConfigs(PERSONA_CONFIGS);
    }, []);

    // Compose configurations
    const merged = useMemo<MergedPersonaConfigV2 | null>(() => {
        if (activeRoles.length === 0) return null;

        const engine = new PersonaCompositionEngine();
        try {
            return engine.compose(activeRoles, v2Configs);
        } catch (error) {
            console.error('Failed to compose persona configurations:', error);
            return null;
        }
    }, [activeRoles, v2Configs]);

    // Helper: Check if user has permission
    const hasPermission = useMemo(() => {
        return (permission: string): boolean => {
            if (!merged) return false;
            const engine = new PersonaCompositionEngine();
            return engine.hasPermission(merged, permission);
        };
    }, [merged]);

    // Helper: Filter items by permissions
    const filterByPermissions = useMemo(() => {
        return <T extends { permissions?: string[] }>(items: T[]): T[] => {
            if (!merged) return [];
            const engine = new PersonaCompositionEngine();
            return engine.filterByPermissions(items, merged.permissions);
        };
    }, [merged]);

    const isLoading = rolesLoading || preferenceLoading;

    return {
        /** Merged persona configuration */
        merged,
        /** Active roles being composed */
        roles: activeRoles,
        /** Whether configuration is loading from API */
        isLoading,
        /** Primary role (highest priority) */
        primaryRole: activeRoles[0],
        /** Check if user has a specific permission */
        hasPermission,
        /** Filter items by user permissions */
        filterByPermissions,
        /** User's persona preference (raw API data) */
        preference,
        /** All available role definitions (raw API data) */
        roleDefinitions: roles,
    };
}

/**
 * Hook for accessing persona quick actions
 *
 * @param workspaceId Workspace ID for fetching preferences
 * @returns Quick actions with permission filtering
 */
export function usePersonaQuickActions(workspaceId: string = 'default') {
    const { merged, filterByPermissions, isLoading } = usePersonaComposition(workspaceId);

    const quickActions = useMemo(() => {
        if (!merged) return [];
        return filterByPermissions(merged.quickActions);
    }, [merged, filterByPermissions]);

    return {
        quickActions,
        isLoading,
    };
}

/**
 * Hook for accessing persona metrics
 *
 * Note: Metrics don't require permission filtering as they are informational
 *
 * @param workspaceId Workspace ID for fetching preferences
 * @returns Metrics from merged configuration
 */
export function usePersonaMetrics(workspaceId: string = 'default') {
    const { merged, isLoading } = usePersonaComposition(workspaceId);

    const metrics = useMemo(() => {
        if (!merged) return [];
        return merged.metrics;
    }, [merged]);

    return {
        metrics,
        isLoading,
    };
}

/**
 * Hook for accessing persona features
 *
 * @param workspaceId Workspace ID for fetching preferences
 * @returns Features with permission filtering
 */
export function usePersonaFeatures(workspaceId: string = 'default') {
    const { merged, filterByPermissions, isLoading } = usePersonaComposition(workspaceId);

    const features = useMemo(() => {
        if (!merged) return [];
        return filterByPermissions(merged.features);
    }, [merged, filterByPermissions]);

    return {
        features,
        isLoading,
    };
}
