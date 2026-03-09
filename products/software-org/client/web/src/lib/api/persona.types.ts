/**
 * Type definitions for Persona API
 *
 * Matches backend types from apps/backend/src/services/persona.service.ts
 */

/**
 * Role definition from Java domain service
 */
export interface RoleDefinition {
    roleId: string;
    displayName: string;
    description: string;
    type: 'BASE' | 'SPECIALIZED' | 'CUSTOM';
    permissions: string[];
    capabilities: string[];
    parentRoles: string[];
}

/**
 * Persona preference data (user-specific)
 */
export interface PersonaPreference {
    id: string;
    userId: string;
    workspaceId: string;
    activeRoles: string[];
    preferences: PersonaPreferences;
    createdAt: string;
    updatedAt: string;
}

/**
 * Persona preferences JSON structure
 */
export interface PersonaPreferences {
    dashboardLayout?: DashboardLayout;
    plugins?: PluginConfig[];
    metrics?: MetricConfig[];
    features?: Record<string, unknown>;
    [key: string]: unknown;
}

export interface DashboardLayout {
    type: 'grid' | 'list' | 'kanban';
    columns?: number;
    widgets?: WidgetConfig[];
}

export interface WidgetConfig {
    id: string;
    type: string;
    position: { x: number; y: number };
    size: { w: number; h: number };
    config?: Record<string, unknown>;
}

export interface PluginConfig {
    id: string;
    enabled: boolean;
    config?: Record<string, unknown>;
}

export interface MetricConfig {
    id: string;
    visible: boolean;
    order?: number;
}

/**
 * Input for creating/updating persona preference
 */
export interface UpdatePersonaPreferenceInput {
    activeRoles: string[];
    preferences: PersonaPreferences;
}

/**
 * Effective permissions resolved from active roles
 */
export interface EffectivePermissions {
    permissions: Record<string, boolean>;
    capabilities: Record<string, boolean>;
}

/**
 * Validation result from role validation
 */
export interface ValidationResult {
    isValid: boolean;
    errorMessage?: string;
}

/**
 * Workspace access verification
 */
export interface WorkspaceAccessResult {
    hasAccess: boolean;
    role?: 'owner' | 'member' | 'viewer';
}
