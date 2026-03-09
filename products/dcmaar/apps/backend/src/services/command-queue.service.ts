import { query } from '../db';
import { GuardianCommand } from '../types/guardian-events';

export type CommandStatus = 'pending' | 'processed' | 'failed' | 'expired';

interface DeviceCommandRow {
    id: string;
    device_id: string;
    child_id: string | null;
    org_id: string | null;
    kind: string;
    action: string;
    params: any | null;
    status: CommandStatus;
    issued_by_actor_type: string;
    issued_by_user_id: string | null;
    created_at: Date;
    expires_at: Date | null;
}

interface EnqueueCommandInput {
    deviceId: string;
    childId?: string;
    orgId?: string;
    kind: string;
    action: string;
    params?: any;
    issuedByActorType: 'parent' | 'child' | 'system';
    issuedByUserId?: string;
    expiresAt?: Date;
}

/**
 * Enqueue a new command for a device.
 */
export async function enqueueDeviceCommand(input: EnqueueCommandInput): Promise<string> {
    const rows = await query<{ id: string }>(
        `INSERT INTO device_commands (
       device_id,
       child_id,
       org_id,
       kind,
       action,
       params,
       status,
       issued_by_actor_type,
       issued_by_user_id,
       expires_at
     ) VALUES (
       $1, $2, $3, $4, $5,
       $6, 'pending', $7, $8, $9
     )
     RETURNING id`,
        [
            input.deviceId,
            input.childId || null,
            input.orgId || null,
            input.kind,
            input.action,
            input.params || null,
            input.issuedByActorType,
            input.issuedByUserId || null,
            input.expiresAt || null,
        ]
    );

    return rows[0]?.id;
}

/**
 * Fetch pending commands for a device and map them to GuardianCommand envelopes.
 */
export async function getPendingCommandsForDevice(
    deviceId: string,
    limit: number = 50
): Promise<GuardianCommand[]> {
    const rows = await query<DeviceCommandRow>(
        `SELECT
       id,
       device_id,
       child_id,
       org_id,
       kind,
       action,
       params,
       status,
       issued_by_actor_type,
       issued_by_user_id,
       created_at,
       expires_at
     FROM device_commands
     WHERE device_id = $1
       AND status = 'pending'
       AND (expires_at IS NULL OR expires_at > NOW())
     ORDER BY created_at ASC
     LIMIT $2`,
        [deviceId, limit]
    );

    return rows.map((row) =>
        rowToGuardianCommand(row)
    );
}

/**
 * Mark a command as processed or failed for a device.
 */
export async function acknowledgeCommand(
    deviceId: string,
    commandId: string,
    status: Exclude<CommandStatus, 'pending'>
): Promise<boolean> {
    const result = await query<{ id: string }>(
        `UPDATE device_commands
     SET status = $1,
         processed_at = NOW()
     WHERE id = $2
       AND device_id = $3
       AND status = 'pending'
     RETURNING id`,
        [status, commandId, deviceId]
    );

    return result.length > 0;
}

function rowToGuardianCommand(row: DeviceCommandRow): GuardianCommand {
    return {
        schema_version: 1,
        command_id: row.id,
        kind: row.kind,
        action: row.action,
        target: {
            device_id: row.device_id,
            child_id: row.child_id || undefined,
            org_id: row.org_id || undefined,
        },
        params: row.params || undefined,
        issued_by: {
            actor_type: (row.issued_by_actor_type as any) || 'system',
            user_id: row.issued_by_user_id || undefined,
        },
        created_at: row.created_at.toISOString(),
        expires_at: row.expires_at ? row.expires_at.toISOString() : undefined,
        metadata: undefined,
    };
}
