/**
 * Agent Sync Service Tests
 * 
 * Tests for the composable agent sync service that aggregates
 * policies and commands for device agents.
 */

import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { query } from '../../db';
import * as authService from '../../services/auth.service';
import * as deviceService from '../../services/device.service';
import { enqueueDeviceCommand } from '../../services/command-queue.service';
import {
    validateAction,
    getActionKind,
    getSupportedImmediateActions,
    getSupportedSessionActions,
    getAgentSyncPayload,
    isImmediateAction,
    isSessionAction,
    IMMEDIATE_ACTIONS,
    SESSION_ACTIONS,
    POLICY_ACTIONS,
} from '../../services/agent-sync.service';

const randomEmail = () => `test-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`;
const randomString = (len: number) => Math.random().toString(36).slice(2, 2 + len);

describe('AgentSyncService', () => {
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
            [testUserId, 'Sync Test Child', 10]
        );
        testChildId = childRows[0].id;

        // Create test device
        const device = await deviceService.registerDevice(testUserId, {
            device_type: 'mobile',
            device_name: 'Sync Test Device',
            device_fingerprint: randomString(16),
            child_id: testChildId,
        });
        testDeviceId = device.id;
    });

    beforeEach(async () => {
        // Clean up commands and policies before each test
        await query('DELETE FROM device_commands WHERE device_id = $1', [testDeviceId]);
        await query('DELETE FROM policies WHERE user_id = $1', [testUserId]);
    });

    describe('Action Registry', () => {
        describe('validateAction', () => {
            it('should validate supported immediate actions', () => {
                const result = validateAction('lock_device', {});
                expect(result.valid).toBe(true);
                expect(result.actionConfig).toBeDefined();
            });

            it('should validate supported session actions', () => {
                const result = validateAction('extend_session', { minutes_granted: 30 });
                expect(result.valid).toBe(true);
            });

            it('should reject unsupported actions', () => {
                const result = validateAction('invalid_action', {});
                expect(result.valid).toBe(false);
                expect(result.error).toContain('Unsupported action');
            });

            it('should reject missing required params', () => {
                const result = validateAction('extend_session', {});
                expect(result.valid).toBe(false);
                expect(result.error).toContain('Missing required params');
                expect(result.error).toContain('minutes_granted');
            });

            it('should accept optional params', () => {
                const result = validateAction('lock_device', { reason: 'bedtime', message: 'Time for bed!' });
                expect(result.valid).toBe(true);
            });
        });

        describe('getActionKind', () => {
            it('should return correct kind for immediate actions', () => {
                expect(getActionKind('lock_device')).toBe('immediate_action');
                expect(getActionKind('unlock_device')).toBe('immediate_action');
                expect(getActionKind('sound_alarm')).toBe('immediate_action');
            });

            it('should return correct kind for session actions', () => {
                expect(getActionKind('extend_session')).toBe('session_request');
                expect(getActionKind('temporary_unblock')).toBe('session_request');
            });

            it('should return correct kind for policy actions', () => {
                expect(getActionKind('sync_policies')).toBe('policy_update');
                expect(getActionKind('invalidate_cache')).toBe('policy_update');
            });

            it('should return default for unknown actions', () => {
                expect(getActionKind('unknown')).toBe('immediate_action');
            });
        });

        describe('isImmediateAction', () => {
            it('should return true for immediate actions', () => {
                expect(isImmediateAction('lock_device')).toBe(true);
                expect(isImmediateAction('unlock_device')).toBe(true);
                expect(isImmediateAction('sound_alarm')).toBe(true);
                expect(isImmediateAction('request_location')).toBe(true);
                expect(isImmediateAction('force_sync')).toBe(true);
            });

            it('should return false for non-immediate actions', () => {
                expect(isImmediateAction('extend_session')).toBe(false);
                expect(isImmediateAction('temporary_unblock')).toBe(false);
                expect(isImmediateAction('sync_policies')).toBe(false);
            });
        });

        describe('isSessionAction', () => {
            it('should return true for session actions', () => {
                expect(isSessionAction('extend_session')).toBe(true);
                expect(isSessionAction('temporary_unblock')).toBe(true);
            });

            it('should return false for non-session actions', () => {
                expect(isSessionAction('lock_device')).toBe(false);
                expect(isSessionAction('sync_policies')).toBe(false);
            });
        });

        describe('getSupportedImmediateActions', () => {
            it('should return all immediate action names', () => {
                const actions = getSupportedImmediateActions();
                expect(actions).toContain('lock_device');
                expect(actions).toContain('unlock_device');
                expect(actions).toContain('sound_alarm');
                expect(actions).toContain('request_location');
                expect(actions).toContain('force_sync');
                expect(actions.length).toBe(Object.keys(IMMEDIATE_ACTIONS).length);
            });
        });

        describe('getSupportedSessionActions', () => {
            it('should return all session action names', () => {
                const actions = getSupportedSessionActions();
                expect(actions).toContain('extend_session');
                expect(actions).toContain('temporary_unblock');
                expect(actions.length).toBe(Object.keys(SESSION_ACTIONS).length);
            });
        });
    });

    describe('getAgentSyncPayload', () => {
        it('should return null for non-existent device', async () => {
            const payload = await getAgentSyncPayload(testUserId, '00000000-0000-0000-0000-000000000000');
            expect(payload).toBeNull();
        });

        it('should return null for wrong user', async () => {
            // Create another user
            const otherUser = await authService.register({
                email: randomEmail(),
                password: 'TestPassword123!',
            });

            const payload = await getAgentSyncPayload(otherUser.user.id, testDeviceId);
            expect(payload).toBeNull();
        });

        it('should return valid sync payload structure', async () => {
            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            expect(payload).not.toBeNull();
            expect(payload!.schema_version).toBe(1);
            expect(payload!.device_id).toBe(testDeviceId);
            expect(payload!.child_id).toBe(testChildId);
            expect(payload!.synced_at).toBeDefined();
            expect(payload!.sync_version).toBeDefined();
            expect(payload!.policies).toBeDefined();
            expect(payload!.commands).toBeDefined();
            expect(payload!.next_sync_seconds).toBeGreaterThan(0);
        });

        it('should include policies in sync payload', async () => {
            // Create a policy
            await query(
                `INSERT INTO policies (user_id, child_id, name, policy_type, config, priority, enabled)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                [testUserId, testChildId, 'Test Policy', 'category', JSON.stringify({ blocked: ['social'] }), 50, true]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            expect(payload!.policies.count).toBeGreaterThan(0);
            expect(payload!.policies.items.length).toBeGreaterThan(0);
            expect(payload!.policies.items[0].name).toBe('Test Policy');
            expect(payload!.policies.items[0].scope).toBe('child');
        });

        it('should include pending commands in sync payload', async () => {
            // Enqueue a command
            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                childId: testChildId,
                kind: 'immediate_action',
                action: 'lock_device',
                params: { reason: 'test' },
                issuedByActorType: 'parent',
                issuedByUserId: testUserId,
            });

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            expect(payload!.commands.count).toBe(1);
            expect(payload!.commands.items.length).toBe(1);
            expect(payload!.commands.items[0].action).toBe('lock_device');
        });

        it('should recommend shorter sync interval when commands pending', async () => {
            // Without commands
            const payloadNoCommands = await getAgentSyncPayload(testUserId, testDeviceId);
            expect(payloadNoCommands!.next_sync_seconds).toBe(300); // 5 minutes

            // With commands
            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            const payloadWithCommands = await getAgentSyncPayload(testUserId, testDeviceId);
            expect(payloadWithCommands!.next_sync_seconds).toBe(30); // 30 seconds
        });

        it('should generate unique sync versions', async () => {
            const payload1 = await getAgentSyncPayload(testUserId, testDeviceId);

            // Add a command to change state
            await enqueueDeviceCommand({
                deviceId: testDeviceId,
                kind: 'immediate_action',
                action: 'lock_device',
                issuedByActorType: 'parent',
            });

            const payload2 = await getAgentSyncPayload(testUserId, testDeviceId);

            // Versions should differ due to command count change
            expect(payload1!.sync_version).not.toBe(payload2!.sync_version);
        });
    });

    describe('Domain Blocking Policy Sync', () => {
        it('should include website blocking policy with domains in sync payload', async () => {
            // Create a website blocking policy with specific domains
            const blockedDomains = ['facebook.com', 'tiktok.com', 'instagram.com'];
            await query(
                `INSERT INTO policies (user_id, child_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                [
                    testUserId,
                    testChildId,
                    'Social Media Block',
                    'website',
                    JSON.stringify({ domains: blockedDomains, action: 'block' }),
                    10,
                    true,
                ]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            expect(payload).not.toBeNull();
            expect(payload!.policies.count).toBeGreaterThan(0);

            const websitePolicy = payload!.policies.items.find(p => p.policy_type === 'website');
            expect(websitePolicy).toBeDefined();
            expect(websitePolicy!.name).toBe('Social Media Block');
            expect(websitePolicy!.config).toHaveProperty('domains');
            expect((websitePolicy!.config as { domains: string[] }).domains).toEqual(blockedDomains);
        });

        it('should include device-specific policies with correct scope', async () => {
            // Create device-specific policy
            await query(
                `INSERT INTO policies (user_id, child_id, device_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                [
                    testUserId,
                    testChildId,
                    testDeviceId,
                    'Device Specific Block',
                    'website',
                    JSON.stringify({ domains: ['gaming-site.com'], action: 'block' }),
                    100,
                    true,
                ]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            const devicePolicy = payload!.policies.items.find(p => p.name === 'Device Specific Block');
            expect(devicePolicy).toBeDefined();
            expect(devicePolicy!.scope).toBe('device');
        });

        it('should order policies by scope priority (device > child > global)', async () => {
            // Create policies at different scopes
            await query(
                `INSERT INTO policies (user_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6)`,
                [testUserId, 'Global Policy', 'website', JSON.stringify({ domains: ['global.com'] }), 1, true]
            );

            await query(
                `INSERT INTO policies (user_id, child_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                [testUserId, testChildId, 'Child Policy', 'website', JSON.stringify({ domains: ['child.com'] }), 1, true]
            );

            await query(
                `INSERT INTO policies (user_id, child_id, device_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
                [testUserId, testChildId, testDeviceId, 'Device Policy', 'website', JSON.stringify({ domains: ['device.com'] }), 1, true]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            // Device policies should come first
            const deviceIndex = payload!.policies.items.findIndex(p => p.name === 'Device Policy');
            const childIndex = payload!.policies.items.findIndex(p => p.name === 'Child Policy');
            const globalIndex = payload!.policies.items.findIndex(p => p.name === 'Global Policy');

            expect(deviceIndex).toBeLessThan(childIndex);
            expect(childIndex).toBeLessThan(globalIndex);
        });

        it('should not include disabled policies in sync payload', async () => {
            await query(
                `INSERT INTO policies (user_id, child_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                [testUserId, testChildId, 'Disabled Policy', 'website', JSON.stringify({ domains: ['disabled.com'] }), 10, false]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            const disabledPolicy = payload!.policies.items.find(p => p.name === 'Disabled Policy');
            expect(disabledPolicy).toBeUndefined();
        });

        it('should include category blocking policies', async () => {
            await query(
                `INSERT INTO policies (user_id, child_id, name, policy_type, config, priority, enabled)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)`,
                [
                    testUserId,
                    testChildId,
                    'Adult Content Block',
                    'category',
                    JSON.stringify({ categories: ['adult', 'gambling', 'violence'], action: 'block' }),
                    100,
                    true,
                ]
            );

            const payload = await getAgentSyncPayload(testUserId, testDeviceId);

            const categoryPolicy = payload!.policies.items.find(p => p.policy_type === 'category');
            expect(categoryPolicy).toBeDefined();
            expect(categoryPolicy!.name).toBe('Adult Content Block');
            expect((categoryPolicy!.config as { categories: string[] }).categories).toContain('adult');
        });
    });

    describe('Action Registry Constants', () => {
        it('should have all immediate actions defined', () => {
            expect(IMMEDIATE_ACTIONS.lock_device).toBeDefined();
            expect(IMMEDIATE_ACTIONS.unlock_device).toBeDefined();
            expect(IMMEDIATE_ACTIONS.sound_alarm).toBeDefined();
            expect(IMMEDIATE_ACTIONS.request_location).toBeDefined();
            expect(IMMEDIATE_ACTIONS.force_sync).toBeDefined();
        });

        it('should have all session actions defined', () => {
            expect(SESSION_ACTIONS.extend_session).toBeDefined();
            expect(SESSION_ACTIONS.temporary_unblock).toBeDefined();
        });

        it('should have all policy actions defined', () => {
            expect(POLICY_ACTIONS.sync_policies).toBeDefined();
            expect(POLICY_ACTIONS.invalidate_cache).toBeDefined();
        });

        it('should have descriptions for all actions', () => {
            Object.values(IMMEDIATE_ACTIONS).forEach(action => {
                expect(action.description).toBeDefined();
                expect(action.description.length).toBeGreaterThan(0);
            });

            Object.values(SESSION_ACTIONS).forEach(action => {
                expect(action.description).toBeDefined();
                expect(action.description.length).toBeGreaterThan(0);
            });

            Object.values(POLICY_ACTIONS).forEach(action => {
                expect(action.description).toBeDefined();
                expect(action.description.length).toBeGreaterThan(0);
            });
        });
    });
});
