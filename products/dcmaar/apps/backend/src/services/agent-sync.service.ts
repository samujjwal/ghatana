/**
 * Agent Sync Service
 * 
 * Composable service that aggregates data from existing services
 * to provide a unified sync payload for device agents.
 * 
 * REUSES: policy.service, command-queue.service, device.service
 * NO DUPLICATION: All data fetching delegated to existing services
 */

import { getPoliciesForDevice } from './policy.service';
import { getPendingCommandsForDevice } from './command-queue.service';
import { getDeviceById } from './device.service';
import { GuardianCommand } from '../types/guardian-events';

/**
 * Supported immediate actions that can be triggered by parents.
 * Centralized registry prevents duplication across routes.
 */
export const IMMEDIATE_ACTIONS = {
    lock_device: {
        kind: 'immediate_action',
        description: 'Lock the device immediately',
        requiredParams: [] as string[],
        optionalParams: ['reason', 'message', 'duration_minutes'],
    },
    unlock_device: {
        kind: 'immediate_action',
        description: 'Unlock a previously locked device',
        requiredParams: [] as string[],
        optionalParams: ['reason'],
    },
    sound_alarm: {
        kind: 'immediate_action',
        description: 'Play a loud sound to locate the device',
        requiredParams: [] as string[],
        optionalParams: ['duration_seconds'],
    },
    request_location: {
        kind: 'immediate_action',
        description: 'Request current device location',
        requiredParams: [] as string[],
        optionalParams: [],
    },
    force_sync: {
        kind: 'system',
        description: 'Force agent to sync policies and settings',
        requiredParams: [] as string[],
        optionalParams: [],
    },
    request_data_sync: {
        kind: 'data_request',
        description: 'Request the agent to sync its local data to backend (for data recovery)',
        requiredParams: [] as string[],
        optionalParams: ['since_timestamp', 'data_types', 'reason'],
    },
    request_full_snapshot: {
        kind: 'data_request',
        description: 'Request complete analytics snapshot from the agent',
        requiredParams: [] as string[],
        optionalParams: ['include_history', 'reason'],
    },
} as const;

export type ImmediateActionType = keyof typeof IMMEDIATE_ACTIONS;

/**
 * Session request actions (child-initiated, parent-approved).
 */
export const SESSION_ACTIONS = {
    extend_session: {
        kind: 'session_request',
        description: 'Extend screen time by specified minutes',
        requiredParams: ['minutes_granted'],
        optionalParams: ['session_id', 'reason'],
    },
    temporary_unblock: {
        kind: 'session_request',
        description: 'Temporarily unblock a resource',
        requiredParams: ['resource'],
        optionalParams: ['duration_minutes', 'reason'],
    },
} as const;

export type SessionActionType = keyof typeof SESSION_ACTIONS;

/**
 * Policy sync actions (system-initiated).
 */
export const POLICY_ACTIONS = {
    sync_policies: {
        kind: 'policy_update',
        description: 'Sync all policies from backend',
        requiredParams: [] as string[],
        optionalParams: ['policy_version', 'force_refresh'],
    },
    invalidate_cache: {
        kind: 'policy_update',
        description: 'Invalidate local policy cache',
        requiredParams: [] as string[],
        optionalParams: [],
    },
} as const;

export type PolicyActionType = keyof typeof POLICY_ACTIONS;

/**
 * All supported actions combined.
 */
export const ALL_ACTIONS = {
    ...IMMEDIATE_ACTIONS,
    ...SESSION_ACTIONS,
    ...POLICY_ACTIONS,
} as const;

export type ActionType = keyof typeof ALL_ACTIONS;

/**
 * Validate if an action is supported and has required params.
 */
export function validateAction(
    action: string,
    params: Record<string, unknown> = {}
): { valid: boolean; error?: string; actionConfig?: typeof ALL_ACTIONS[ActionType] } {
    if (!(action in ALL_ACTIONS)) {
        return {
            valid: false,
            error: `Unsupported action: ${action}. Supported: ${Object.keys(ALL_ACTIONS).join(', ')}`,
        };
    }

    const actionConfig = ALL_ACTIONS[action as ActionType];
    const missingParams = actionConfig.requiredParams.filter(
        (p) => !(p in params) || params[p] === undefined || params[p] === null
    );

    if (missingParams.length > 0) {
        return {
            valid: false,
            error: `Missing required params for ${action}: ${missingParams.join(', ')}`,
        };
    }

    return { valid: true, actionConfig };
}

/**
 * Get action kind from action name.
 */
export function getActionKind(action: string): string {
    if (action in ALL_ACTIONS) {
        return ALL_ACTIONS[action as ActionType].kind;
    }
    return 'immediate_action'; // default
}

/**
 * Policy item in sync response (simplified for agent consumption).
 */
export interface SyncPolicy {
    id: string;
    name: string;
    policy_type: string;
    priority: number;
    enabled: boolean;
    config: Record<string, unknown>;
    scope: 'device' | 'child' | 'global';
}

/**
 * Unified sync payload for device agents.
 * Combines policies and pending commands in a single response.
 */
export interface AgentSyncPayload {
    /** Schema version for forward compatibility */
    schema_version: number;
    /** Device ID this sync is for */
    device_id: string;
    /** Child ID if device is assigned */
    child_id?: string;
    /** Timestamp of sync */
    synced_at: string;
    /** Version hash for change detection */
    sync_version: string;
    /** Policies to enforce */
    policies: {
        version: string;
        items: SyncPolicy[];
        count: number;
    };
    /** Pending commands to execute */
    commands: {
        items: GuardianCommand[];
        count: number;
    };
    /** Next recommended sync interval in seconds */
    next_sync_seconds: number;
}

/**
 * Fetch unified sync payload for a device.
 * Composes data from policy and command services.
 * 
 * @param userId - Owner user ID (for authorization)
 * @param deviceId - Device requesting sync
 * @returns Unified sync payload or null if device not found
 */
export async function getAgentSyncPayload(
    userId: string,
    deviceId: string
): Promise<AgentSyncPayload | null> {
    // Verify device ownership (reuses device.service)
    const device = await getDeviceById(userId, deviceId);
    if (!device) {
        return null;
    }

    // Fetch policies (reuses policy.service)
    // Note: getPoliciesForDevice requires device to have child_id
    // For devices without children, return empty policies
    let policies: Awaited<ReturnType<typeof getPoliciesForDevice>> = [];
    if (device.child_id) {
        try {
            policies = await getPoliciesForDevice(deviceId);
        } catch {
            // Device may not have child assigned yet
            policies = [];
        }
    }

    // Fetch pending commands (reuses command-queue.service)
    const commands = await getPendingCommandsForDevice(deviceId);

    // Generate version hash
    const policyVersion = policies.length > 0
        ? `p${Date.now()}-${policies.map(p => p.id.slice(0, 8)).join('')}`
        : 'p0';

    const commandVersion = commands.length > 0
        ? `c${commands.length}`
        : 'c0';

    const syncVersion = `${policyVersion}-${commandVersion}`;

    // Map policies to simplified format
    const syncPolicies: SyncPolicy[] = policies.map(p => ({
        id: p.id,
        name: p.name,
        policy_type: p.policy_type,
        priority: p.priority,
        enabled: p.enabled,
        config: p.config as Record<string, unknown>,
        scope: p.device_id ? 'device' : p.child_id ? 'child' : 'global',
    }));

    // Determine next sync interval based on pending commands
    // More frequent if commands pending, less frequent if idle
    const nextSyncSeconds = commands.length > 0 ? 30 : 300;

    return {
        schema_version: 1,
        device_id: deviceId,
        child_id: device.child_id || undefined,
        synced_at: new Date().toISOString(),
        sync_version: syncVersion,
        policies: {
            version: policyVersion,
            items: syncPolicies,
            count: syncPolicies.length,
        },
        commands: {
            items: commands,
            count: commands.length,
        },
        next_sync_seconds: nextSyncSeconds,
    };
}

/**
 * Check if immediate action is allowed.
 */
export function isImmediateAction(action: string): boolean {
    return action in IMMEDIATE_ACTIONS;
}

/**
 * Check if session action is allowed.
 */
export function isSessionAction(action: string): boolean {
    return action in SESSION_ACTIONS;
}

/**
 * Get list of all supported immediate actions.
 */
export function getSupportedImmediateActions(): string[] {
    return Object.keys(IMMEDIATE_ACTIONS);
}

/**
 * Get list of all supported session actions.
 */
export function getSupportedSessionActions(): string[] {
    return Object.keys(SESSION_ACTIONS);
}
