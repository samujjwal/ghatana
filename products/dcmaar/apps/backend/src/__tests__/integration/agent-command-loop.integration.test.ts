/**
 * Agent Command Loop Integration Tests
 *
 * Validates the end-to-end flow for device agents using the connector-based
 * sync and command endpoints:
 * - Parent enqueues a command via /api/devices/:id/actions
 * - Agent syncs via /api/devices/:id/sync and sees pending commands
 * - Agent acknowledges command via /api/devices/:id/commands/ack
 * - Backend records a GuardianEvent telemetry row for the acknowledgement
 */

import { describe, it, expect, beforeEach, beforeAll, afterAll } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import { query } from '../../db';

let app: FastifyInstance;

describe('Agent Command Loop Integration', () => {
    let testUserId: string;
    let testAccessToken: string;
    let testChildId: string;
    let testDeviceId: string;

    beforeAll(async () => {
        app = await createTestApp();
    });

    afterAll(async () => {
        await app.close();
    });

    beforeEach(async () => {
        // Register parent
        const authResponse = await request(app)
            .post('/api/auth/register')
            .send({
                email: randomEmail(),
                password: 'TestPassword123!',
                display_name: 'Agent Loop Parent',
            })
            .expect(201);

        testUserId = authResponse.body.user.id;
        testAccessToken = authResponse.body.accessToken;

        // Create child profile
        const childResponse = await request(app)
            .post('/api/children')
            .set('Authorization', `Bearer ${testAccessToken}`)
            .send({
                name: 'Agent Loop Child',
                birth_date: new Date(
                    Date.now() - 10 * 365 * 24 * 60 * 60 * 1000
                )
                    .toISOString()
                    .split('T')[0],
            })
            .expect(201);

        testChildId = childResponse.body.data.id;

        // Register device and pair with child
        const deviceResponse = await request(app)
            .post('/api/devices/register')
            .set('Authorization', `Bearer ${testAccessToken}`)
            .send({
                device_type: 'mobile',
                device_name: 'Agent Loop Device',
                device_fingerprint: randomString(16),
                child_id: testChildId,
            })
            .expect(201);

        expect(deviceResponse.body.success).toBe(true);
        testDeviceId = deviceResponse.body.data.id;
    });

    it('completes sync → ack → telemetry loop for lock_device commands', async () => {
        // 1. Parent enqueues a lock_device immediate action
        const actionResponse = await request(app)
            .post(`/api/devices/${testDeviceId}/actions`)
            .set('Authorization', `Bearer ${testAccessToken}`)
            .send({
                action: 'lock_device',
                params: { reason: 'integration-test' },
            })
            .expect(202);

        expect(actionResponse.body.success).toBe(true);
        const commandId = actionResponse.body.command_id as string;
        expect(commandId).toBeDefined();

        // 2. Agent syncs and sees the pending command in unified payload
        const syncResponse = await request(app)
            .get(`/api/devices/${testDeviceId}/sync`)
            .set('Authorization', `Bearer ${testAccessToken}`)
            .expect(200);

        expect(syncResponse.body.success).toBe(true);
        const payload = syncResponse.body.data as any;

        expect(payload.device_id).toBe(testDeviceId);
        expect(payload.commands).toBeDefined();
        expect(payload.commands.count).toBeGreaterThanOrEqual(1);

        const syncedCommands: any[] = payload.commands.items;
        const syncedCommand = syncedCommands.find((c) => c.command_id === commandId);

        expect(syncedCommand).toBeDefined();
        expect(syncedCommand.action).toBe('lock_device');
        expect(syncedCommand.target.device_id).toBe(testDeviceId);

        // 3. Agent executes locally (out of scope for backend) and acknowledges as processed
        const ackResponse = await request(app)
            .post(`/api/devices/${testDeviceId}/commands/ack`)
            .set('Authorization', `Bearer ${testAccessToken}`)
            .send({
                command_id: commandId,
                status: 'processed',
            })
            .expect(200);

        expect(ackResponse.body.success).toBe(true);
        expect(ackResponse.body.message).toContain('Command acknowledgement received');

        // 4. Command queue row should be updated to processed
        const commandRows = await query(
            'SELECT status FROM device_commands WHERE id = $1 AND device_id = $2',
            [commandId, testDeviceId]
        );

        expect(commandRows.length).toBe(1);
        expect(commandRows[0].status).toBe('processed');

        // 5. GuardianEvent telemetry for the acknowledgement should be recorded
        const eventRows = await query(
            `SELECT * FROM guardian_events
       WHERE kind = 'system'
         AND subtype = 'command_acknowledged'
         AND context->>'command_id' = $1
         AND payload->>'device_id' = $2`,
            [commandId, testDeviceId]
        );

        expect(eventRows.length).toBe(1);
        expect(eventRows[0].kind).toBe('system');
        expect(eventRows[0].subtype).toBe('command_acknowledged');
        expect(eventRows[0].payload.device_id).toBe(testDeviceId);
    });
});
