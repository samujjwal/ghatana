/**
 * Persona preference management service for Software-Org application.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for managing user persona preferences per workspace.
 * Handles CRUD operations, validation, and persistence of persona configurations
 * including active roles, dashboard layouts, plugins, and feature preferences.
 *
 * <p><b>Service Functions</b><br>
 * - getPersonaPreference: Retrieve user's persona config for specific workspace
 * - upsertPersonaPreference: Create or update persona preferences
 * - deletePersonaPreference: Remove persona configuration
 * - listWorkspacePreferences: Get all preferences for a workspace (admin)
 *
 * <p><b>Data Model</b><br>
 * PersonaPreference stores:
 * - activeRoles: Array of role IDs (["admin", "tech-lead"])
 * - preferences: JSON object with dashboardLayout, plugins, metrics, features
 *
 * <p><b>Boundary Compliance</b><br>
 * This service handles USER PREFERENCES only. Does NOT handle:
 * - Role definitions (that's Java domain logic via PersonaRoleDomainClient)
 * - Permission enforcement (that's Java)
 * - Team/project workflows (that's Java)
 * - Event processing (that's Java)
 *
 * This is rapid-iteration, user-facing preference storage with real-time sync.
 *
 * <p><b>Java Integration</b><br>
 * This service calls Java domain service for:
 * - Role validation: validateRoleActivation() before saving preferences
 * - Role definitions: getRoleDefinition() to enrich UI responses
 * - Permission resolution: resolvePermissions() for authorization checks
 *
 * @doc.type service
 * @doc.purpose Persona preference CRUD and validation
 * @doc.layer product
 * @doc.pattern Service
 */
import { prisma } from '../db/client.js';
import { getPersonaRoleDomainClient, ValidationError } from './persona-role-domain.client.js';

// Singleton instance of Java domain client
const roleClient = getPersonaRoleDomainClient();

/**
 * Persona preference with JSON fields typed
 */
export interface PersonaPreferenceData {
    id: string;
    userId: string;
    workspaceId: string;
    activeRoles: string[];
    preferences: {
        dashboardLayout?: any;
        plugins?: any[];
        metrics?: any[];
        features?: Record<string, any>;
        [key: string]: any;
    };
    createdAt: Date;
    updatedAt: Date;
}

/**
 * Input for creating/updating persona preferences
 */
export interface PersonaPreferenceInput {
    activeRoles: string[];
    preferences: {
        dashboardLayout?: any;
        plugins?: any[];
        metrics?: any[];
        features?: Record<string, any>;
        [key: string]: any;
    };
}

/**
 * Get user's persona preference for a specific workspace
 * 
 * @param userId - User identifier
 * @param workspaceId - Workspace identifier
 * @returns Persona preference or null if not found
 */
export async function getPersonaPreference(
    userId: string,
    workspaceId: string
): Promise<PersonaPreferenceData | null> {
    const preference = await prisma.personaPreference.findUnique({
        where: {
            userId_workspaceId: {
                userId,
                workspaceId
            }
        }
    });

    if (!preference) {
        return null;
    }

    return {
        ...preference,
        activeRoles: preference.activeRoles as string[],
        preferences: preference.preferences as PersonaPreferenceData['preferences']
    };
}

/**
 * Create or update persona preference
 * 
 * Validates role combination with Java domain service before saving.
 * 
 * @param userId - User identifier
 * @param workspaceId - Workspace identifier
 * @param data - Persona preference data
 * @returns Created or updated preference
 * @throws ValidationError if role combination is invalid
 */
export async function upsertPersonaPreference(
    userId: string,
    workspaceId: string,
    data: PersonaPreferenceInput
): Promise<PersonaPreferenceData> {
    // Validate role combination with Java domain service
    const validationResult = await roleClient.validateRoleActivation(data.activeRoles);
    if (!validationResult.isValid) {
        throw new ValidationError(validationResult.errorMessage || 'Invalid role combination');
    }

    const preference = await prisma.personaPreference.upsert({
        where: {
            userId_workspaceId: {
                userId,
                workspaceId
            }
        },
        update: {
            activeRoles: data.activeRoles,
            preferences: data.preferences,
            updatedAt: new Date()
        },
        create: {
            userId,
            workspaceId,
            activeRoles: data.activeRoles,
            preferences: data.preferences
        }
    });

    return {
        ...preference,
        activeRoles: preference.activeRoles as string[],
        preferences: preference.preferences as PersonaPreferenceData['preferences']
    };
}

/**
 * Delete persona preference
 * 
 * @param userId - User identifier
 * @param workspaceId - Workspace identifier
 * @returns True if deleted, false if not found
 */
export async function deletePersonaPreference(
    userId: string,
    workspaceId: string
): Promise<boolean> {
    try {
        await prisma.personaPreference.delete({
            where: {
                userId_workspaceId: {
                    userId,
                    workspaceId
                }
            }
        });
        return true;
    } catch (error) {
        // Record not found
        return false;
    }
}

/**
 * List all persona preferences for a workspace (admin only)
 * 
 * @param workspaceId - Workspace identifier
 * @returns Array of persona preferences
 */
export async function listWorkspacePreferences(
    workspaceId: string
): Promise<PersonaPreferenceData[]> {
    const preferences = await prisma.personaPreference.findMany({
        where: { workspaceId },
        orderBy: { updatedAt: 'desc' }
    });

    return preferences.map((pref: any) => ({
        ...pref,
        activeRoles: pref.activeRoles as string[],
        preferences: pref.preferences as PersonaPreferenceData['preferences']
    }));
}

/**
 * Verify user has access to workspace
 * 
 * @param userId - User identifier
 * @param workspaceId - Workspace identifier
 * @returns True if user owns or has access to workspace
 */
export async function verifyWorkspaceAccess(
    userId: string,
    workspaceId: string
): Promise<boolean> {
    const workspace = await prisma.workspace.findFirst({
        where: {
            id: workspaceId,
            ownerId: userId // For now, only owner can access
            // TODO: Add workspace members table for shared workspaces
        }
    });

    return workspace !== null;
}
