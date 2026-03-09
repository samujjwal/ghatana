/**
 * Events Store Service Tests
 *
 * Tests for GuardianEvent storage:
 * - Store events with required fields
 * - Store events with optional fields
 * - Handle empty event arrays
 * - Verify event retrieval
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { query } from '../../db';
import { randomEmail, randomString } from '../setup';
import * as authService from '../../services/auth.service';
import * as deviceService from '../../services/device.service';
import { storeGuardianEvents } from '../../services/events-store.service';
import { GuardianEvent } from '../../types/guardian-events';
import { v4 as uuidv4 } from 'uuid';

describe('EventsStoreService', () => {
    let testUserId: string;
    let testDeviceId: string;
    let testChildId: string;

    beforeAll(async () => {
        // Create test user
        const user = await authService.register({
            email: randomEmail(),
            password: 'TestPassword123!',
        });
        testUserId = user.user.id;

        // Create test child
        const childRows = await query(
            'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
            [testUserId, 'Test Child', 10]
        );
        testChildId = childRows[0].id;

        // Create test device
        const device = await deviceService.registerDevice(testUserId, {
            device_type: 'mobile',
            device_name: 'Test Device',
            device_fingerprint: randomString(16),
            child_id: testChildId,
        });
        testDeviceId = device.id;
    });

    beforeEach(async () => {
        // Clean up events before each test
        await query('DELETE FROM guardian_events WHERE source_device_id = $1', [testDeviceId]);
    });

    describe('storeGuardianEvents', () => {
        it('should store event with required fields', async () => {
            const eventId = uuidv4();
            const event: GuardianEvent = {
                schema_version: 1,
                event_id: eventId,
                kind: 'usage',
                subtype: 'session_started',
                occurred_at: new Date().toISOString(),
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.0.0',
                    device_id: testDeviceId,
                    child_id: testChildId,
                },
            };

            await storeGuardianEvents([event]);

            const rows = await query('SELECT * FROM guardian_events WHERE event_id = $1', [eventId]);
            expect(rows.length).toBe(1);
            expect(rows[0].kind).toBe('usage');
            expect(rows[0].subtype).toBe('session_started');
            expect(rows[0].source_agent_type).toBe('device_agent');
            expect(rows[0].source_device_id).toBe(testDeviceId);
            expect(rows[0].source_child_id).toBe(testChildId);
        });

        it('should store event with all optional fields', async () => {
            const eventId = uuidv4();
            const event: GuardianEvent = {
                schema_version: 1,
                event_id: eventId,
                kind: 'block',
                subtype: 'website_blocked',
                occurred_at: new Date().toISOString(),
                received_at: new Date().toISOString(),
                source: {
                    agent_type: 'browser_extension',
                    agent_version: '2.0.0',
                    device_id: testDeviceId,
                    child_id: testChildId,
                    session_id: 'session-123',
                },
                context: {
                    url: 'https://blocked.example.com',
                    category: 'social',
                },
                payload: {
                    policy_id: 'policy-123',
                    reason: 'category_blocked',
                },
                ai: {
                    risk_score: 0.8,
                    risk_bucket: 'high',
                    labels: ['social', 'distraction'],
                    model_version: 'v1.0',
                },
                privacy: {
                    pii_level: 'low',
                    contains_raw_content: false,
                    hashed_fields: ['url'],
                },
                metadata: {
                    custom_field: 'custom_value',
                },
            };

            await storeGuardianEvents([event]);

            const rows = await query('SELECT * FROM guardian_events WHERE event_id = $1', [eventId]);
            expect(rows.length).toBe(1);
            expect(rows[0].context.url).toBe('https://blocked.example.com');
            expect(rows[0].payload.policy_id).toBe('policy-123');
            expect(rows[0].ai.risk_score).toBe(0.8);
            expect(rows[0].privacy.pii_level).toBe('low');
            expect(rows[0].metadata.custom_field).toBe('custom_value');
        });

        it('should store multiple events', async () => {
            const events: GuardianEvent[] = [
                {
                    schema_version: 1,
                    event_id: uuidv4(),
                    kind: 'usage',
                    subtype: 'app_opened',
                    occurred_at: new Date().toISOString(),
                    source: {
                        agent_type: 'device_agent',
                        agent_version: '1.0.0',
                        device_id: testDeviceId,
                    },
                },
                {
                    schema_version: 1,
                    event_id: uuidv4(),
                    kind: 'usage',
                    subtype: 'app_closed',
                    occurred_at: new Date().toISOString(),
                    source: {
                        agent_type: 'device_agent',
                        agent_version: '1.0.0',
                        device_id: testDeviceId,
                    },
                },
            ];

            await storeGuardianEvents(events);

            const rows = await query(
                'SELECT * FROM guardian_events WHERE source_device_id = $1 ORDER BY occurred_at',
                [testDeviceId]
            );
            expect(rows.length).toBe(2);
            expect(rows[0].subtype).toBe('app_opened');
            expect(rows[1].subtype).toBe('app_closed');
        });

        it('should handle empty event array', async () => {
            await storeGuardianEvents([]);

            // Should not throw and no events should be stored
            const rows = await query('SELECT * FROM guardian_events WHERE source_device_id = $1', [
                testDeviceId,
            ]);
            expect(rows.length).toBe(0);
        });

        it('should store policy events', async () => {
            const eventId = uuidv4();
            const event: GuardianEvent = {
                schema_version: 1,
                event_id: eventId,
                kind: 'policy',
                subtype: 'device_lock_requested',
                occurred_at: new Date().toISOString(),
                source: {
                    agent_type: 'backend',
                    agent_version: '1.0.0',
                    device_id: testDeviceId,
                    child_id: testChildId,
                },
                context: {
                    action: 'lock_device',
                    device_id: testDeviceId,
                },
                payload: {
                    command_id: 'cmd-123',
                    params: { reason: 'bedtime' },
                },
            };

            await storeGuardianEvents([event]);

            const rows = await query('SELECT * FROM guardian_events WHERE event_id = $1', [eventId]);
            expect(rows.length).toBe(1);
            expect(rows[0].kind).toBe('policy');
            expect(rows[0].subtype).toBe('device_lock_requested');
            expect(rows[0].payload.command_id).toBe('cmd-123');
        });

        it('should store system events', async () => {
            const eventId = uuidv4();
            const event: GuardianEvent = {
                schema_version: 1,
                event_id: eventId,
                kind: 'system',
                subtype: 'command_acknowledged',
                occurred_at: new Date().toISOString(),
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.0.0',
                    device_id: testDeviceId,
                },
                context: {
                    command_id: 'cmd-123',
                    status: 'processed',
                },
                payload: {
                    device_id: testDeviceId,
                },
            };

            await storeGuardianEvents([event]);

            const rows = await query('SELECT * FROM guardian_events WHERE event_id = $1', [eventId]);
            expect(rows.length).toBe(1);
            expect(rows[0].kind).toBe('system');
            expect(rows[0].subtype).toBe('command_acknowledged');
        });
    });
});
