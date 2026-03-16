/**
 * Command Queue Service Tests
 *
 * Tests for device command queue lifecycle:
 * - Enqueue commands
 * - Fetch pending commands
 * - Acknowledge commands (processed/failed/expired)
 * - Command expiration
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { query } from '../../db';
import { randomEmail, randomString } from '../setup';
import * as deviceService from '../../services/device.service';
import { createTestUser } from '../fixtures/user.fixtures';
import {
    enqueueDeviceCommand,
    getPendingCommandsForDevice,
    acknowledgeCommand,
} from '../../services/command-queue.service';

describe('CommandQueueService', () => {
    let testUserId: string;
    let testDeviceId: string;
    let testChildId: string;

    beforeAll(async () => {
        // Create test user
        const user = await createTestUser({ email: randomEmail() });
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
        // Clean up commands before each test
        await query('DELETE FROM device_commands WHERE device_id = $1', [testDeviceId]);
    });

    describe('enqueueDeviceCommand', () => {
        it('should enqueue a command and return command ID', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                childId: testChildId,
                kind: 'immediate_action',
                action: 'lock_device',
                params: { reason: 'test' },
                issuedByActorType: 'parent',
                issuedByUserId: testUserId,
            });

            expect(commandId).toBeDefined();
            expect(typeof commandId).toBe('string');

            // Verify command exists in DB
            const rows = await query('SELECT * FROM device_commands WHERE id = $1', [commandId]);
            expect(rows.length).toBe(1);
            expect(rows[0].device_id).toBe(testDeviceId);
            expect(rows[0].kind).toBe('immediate_action');
            expect(rows[0].action).toBe('lock_device');
            expect(rows[0].status).toBe('pending');
        });

        it('should enqueue command with expiration', async () => {
            const expiresAt = new Date(Date.now() + 60 * 60 * 1000); // 1 hour from now

            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'session_request',
                action: 'extend_session',
                params: { minutes: 30 },
                issuedByActorType: 'parent',
                issuedByUserId: testUserId,
                expiresAt,
            });

            const rows = await query('SELECT * FROM device_commands WHERE id = $1', [commandId]);
            expect(rows[0].expires_at).toBeDefined();
        });

        it('should enqueue command without optional fields', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'system',
                action: 'sync_policies',
                issuedByActorType: 'system',
            });

            const rows = await query('SELECT * FROM device_commands WHERE id = $1', [commandId]);
            expect(rows[0].child_id).toBeNull();
            expect(rows[0].issued_by_user_id).toBeNull();
            expect(rows[0].expires_at).toBeNull();
        });
    });

    describe('getPendingCommandsForDevice', () => {
        it('should return pending commands as GuardianCommand envelopes', async () => {
            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                childId: testChildId,
                kind: 'immediate_action',
                action: 'lock_device',
                params: { reason: 'test' },
                issuedByActorType: 'parent',
                issuedByUserId: testUserId,
            });

            const commands = await getPendingCommandsForDevice(testDeviceId);

            expect(commands.length).toBe(1);
            expect(commands[0].schema_version).toBe(1);
            expect(commands[0].command_id).toBeDefined();
            expect(commands[0].kind).toBe('immediate_action');
            expect(commands[0].action).toBe('lock_device');
            expect(commands[0].target?.device_id).toBe(testDeviceId);
            expect(commands[0].target?.child_id).toBe(testChildId);
            expect(commands[0].issued_by.actor_type).toBe('parent');
            expect(commands[0].issued_by.user_id).toBe(testUserId);
            expect(commands[0].params?.reason).toBe('test');
        });

        it('should not return processed commands', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            // Mark as processed
            await acknowledgeCommand(testDeviceId, commandId, 'processed');

            const commands = await getPendingCommandsForDevice(testDeviceId);
            expect(commands.length).toBe(0);
        });

        it('should not return expired commands', async () => {
            const expiredAt = new Date(Date.now() - 1000); // 1 second ago

            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
                expiresAt: expiredAt,
            });

            const commands = await getPendingCommandsForDevice(testDeviceId);
            expect(commands.length).toBe(0);
        });

        it('should return commands ordered by created_at ASC', async () => {
            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'first_command',
                issuedByActorType: 'parent',
            });

            // Small delay to ensure different timestamps
            await new Promise((resolve) => setTimeout(resolve, 10));

            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'second_command',
                issuedByActorType: 'parent',
            });

            const commands = await getPendingCommandsForDevice(testDeviceId);
            expect(commands.length).toBe(2);
            expect(commands[0].action).toBe('first_command');
            expect(commands[1].action).toBe('second_command');
        });

        it('should respect limit parameter', async () => {
            for (let i = 0; i < 5; i++) {
                await enqueueDeviceCommand({
                    deviceId: testDeviceId,
                    kind: 'immediate_action',
                    action: `command_${i}`,
                    issuedByActorType: 'parent',
                });
            }

            const commands = await getPendingCommandsForDevice(testDeviceId, 3);
            expect(commands.length).toBe(3);
        });
    });

    describe('acknowledgeCommand', () => {
        it('should mark command as processed', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            const result = await acknowledgeCommand(testDeviceId, commandId, 'processed');
            expect(result).toBe(true);

            const rows = await query('SELECT * FROM device_commands WHERE id = $1', [commandId]);
            expect(rows[0].status).toBe('processed');
            expect(rows[0].processed_at).toBeDefined();
        });

        it('should mark command as failed', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            const result = await acknowledgeCommand(testDeviceId, commandId, 'failed');
            expect(result).toBe(true);

            const rows = await query('SELECT * FROM device_commands WHERE id = $1', [commandId]);
            expect(rows[0].status).toBe('failed');
        });

        it('should return false for non-existent command', async () => {
            const result = await acknowledgeCommand(
                testDeviceId,
                '00000000-0000-0000-0000-000000000000',
                'processed'
            );
            expect(result).toBe(false);
        });

        it('should return false for already acknowledged command', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            // First ack
            await acknowledgeCommand(testDeviceId, commandId, 'processed');

            // Second ack should fail
            const result = await acknowledgeCommand(testDeviceId, commandId, 'failed');
            expect(result).toBe(false);
        });

        it('should return false for wrong device ID', async () => {
            const commandId = await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            const result = await acknowledgeCommand(
                '00000000-0000-0000-0000-000000000000',
                commandId,
                'processed'
            );
            expect(result).toBe(false);
        });
    });
});
