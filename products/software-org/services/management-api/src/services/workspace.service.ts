/**
 * Workspace override management service for Software-Org application.
 *
 * <p><b>Purpose</b><br>
 * Provides business logic for managing admin-defined workspace-level persona
 * configuration overrides. Workspace admins can set default configurations,
 * disable features, or enforce specific UI settings across the workspace.
 *
 * <p><b>Service Functions</b><br>
 * - getWorkspaceOverride: Retrieve workspace-level overrides
 * - upsertWorkspaceOverride: Create or update overrides
 * - deleteWorkspaceOverride: Remove workspace overrides
 *
 * <p><b>Data Model</b><br>
 * WorkspaceOverride stores:
 * - overrides: JSON object with forced settings, disabled features, defaults
 *
 * <p><b>Override Priority</b><br>
 * Configuration resolution order (lowest to highest):
 * 1. System defaults (in frontend persona schemas)
 * 2. Workspace overrides (this service)
 * 3. User preferences (persona.service.ts)
 *
 * <p><b>Boundary Compliance</b><br>
 * This service handles WORKSPACE UI OVERRIDES only. Does NOT handle:
 * - Workspace creation/management (that's Java if complex)
 * - Team membership (that's Java domain logic)
 * - Access control policies (that's Java)
 * - Billing/subscription (that's Java)
 *
 * This is rapid-iteration, admin-facing UI configuration.
 *
 * @doc.type service
 * @doc.purpose Workspace-level persona override management
 * @doc.layer product
 * @doc.pattern Service
 */
import { prisma } from '../db/client.js';

/**
 * Workspace override with JSON fields typed
 */
export interface WorkspaceOverrideData {
    id: string;
    workspaceId: string;
    overrides: {
        disabledPlugins?: string[];
        forcedLayout?: any;
        defaultRoles?: string[];
        featureFlags?: Record<string, boolean>;
        [key: string]: any;
    };
    createdAt: Date;
    updatedAt: Date;
}

/**
 * Input for creating/updating workspace overrides
 */
export interface WorkspaceOverrideInput {
    overrides: {
        disabledPlugins?: string[];
        forcedLayout?: any;
        defaultRoles?: string[];
        featureFlags?: Record<string, boolean>;
        [key: string]: any;
    };
}

/**
 * Get workspace-level persona overrides
 * 
 * @param workspaceId - Workspace identifier
 * @returns Workspace override or null if not configured
 */
export async function getWorkspaceOverride(
    workspaceId: string
): Promise<WorkspaceOverrideData | null> {
    const override = await prisma.workspaceOverride.findUnique({
        where: { workspaceId }
    });

    if (!override) {
        return null;
    }

    return {
        ...override,
        overrides: override.overrides as WorkspaceOverrideData['overrides']
    };
}

/**
 * Create or update workspace override
 * 
 * @param workspaceId - Workspace identifier
 * @param data - Override configuration
 * @returns Created or updated override
 */
export async function upsertWorkspaceOverride(
    workspaceId: string,
    data: WorkspaceOverrideInput
): Promise<WorkspaceOverrideData> {
    const override = await prisma.workspaceOverride.upsert({
        where: { workspaceId },
        update: {
            overrides: data.overrides,
            updatedAt: new Date()
        },
        create: {
            workspaceId,
            overrides: data.overrides
        }
    });

    return {
        ...override,
        overrides: override.overrides as WorkspaceOverrideData['overrides']
    };
}

/**
 * Delete workspace override
 * 
 * @param workspaceId - Workspace identifier
 * @returns True if deleted, false if not found
 */
export async function deleteWorkspaceOverride(
    workspaceId: string
): Promise<boolean> {
    try {
        await prisma.workspaceOverride.delete({
            where: { workspaceId }
        });
        return true;
    } catch (error) {
        // Record not found
        return false;
    }
}

/**
 * Verify user is workspace admin
 * 
 * @param userId - User identifier
 * @param workspaceId - Workspace identifier
 * @returns True if user is workspace owner/admin
 */
export async function verifyWorkspaceAdmin(
    userId: string,
    workspaceId: string
): Promise<boolean> {
    const workspace = await prisma.workspace.findFirst({
        where: {
            id: workspaceId,
            ownerId: userId
        }
    });

    return workspace !== null;
}
