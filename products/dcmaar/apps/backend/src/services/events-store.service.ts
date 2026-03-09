import { query } from '../db';
import { GuardianEvent } from '../types/guardian-events';

/**
 * Persist GuardianEvent envelopes into guardian_events table.
 *
 * This is a minimal event store for telemetry, alerts, and config changes.
 */
export async function storeGuardianEvents(events: GuardianEvent[]): Promise<void> {
    if (!events || events.length === 0) {
        return;
    }

    for (const event of events) {
        const occurredAt = new Date(event.occurred_at);
        const receivedAt = event.received_at ? new Date(event.received_at) : new Date();

        await query(
            `INSERT INTO guardian_events (
         event_id,
         kind,
         subtype,
         occurred_at,
         received_at,
         source_agent_type,
         source_agent_version,
         source_device_id,
         source_child_id,
         source_org_id,
         source_session_id,
         context,
         payload,
         ai,
         privacy,
         metadata
       ) VALUES (
         $1, $2, $3, $4, $5,
         $6, $7, $8, $9, $10,
         $11, $12, $13, $14, $15, $16
       )`,
            [
                event.event_id,
                event.kind,
                event.subtype,
                occurredAt,
                receivedAt,
                event.source.agent_type,
                event.source.agent_version,
                event.source.device_id || null,
                event.source.child_id || null,
                event.source.org_id || null,
                event.source.session_id || null,
                event.context || null,
                event.payload || null,
                event.ai || null,
                event.privacy || null,
                event.metadata || null,
            ]
        );
    }
}
