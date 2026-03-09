/**
 * Guardian Event/Command Contract Tests
 *
 * Tests to validate that GuardianEvent and GuardianCommand schemas
 * match the documented JSON contracts and prevent contract drift.
 */

import { describe, it, expect } from 'vitest';
import { guardianEventSchema, guardianCommandSchema } from '../../types/guardian-events';

describe('GuardianEvent Contract', () => {
    describe('Usage Event', () => {
        it('should validate app_session_started event', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440001',
                kind: 'usage',
                subtype: 'app_session_started',
                occurred_at: '2025-01-15T14:30:00.000Z',
                received_at: '2025-01-15T14:30:01.500Z',
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.2.0',
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                    session_id: 'sess-abc123',
                },
                context: {
                    app_id: 'com.example.game',
                    app_name: 'Example Game',
                    category: 'games',
                },
                payload: {
                    start_time: '2025-01-15T14:30:00.000Z',
                },
                privacy: {
                    pii_level: 'none',
                    contains_raw_content: false,
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(true);
        });
    });

    describe('Block Event', () => {
        it('should validate website_blocked event', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440002',
                kind: 'block',
                subtype: 'website_blocked',
                occurred_at: '2025-01-15T15:00:00.000Z',
                source: {
                    agent_type: 'browser_extension',
                    agent_version: '1.1.0',
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                },
                context: {
                    domain: 'blocked-site.example.com',
                    url: 'https://blocked-site.example.com/page',
                    category: 'social_media',
                    policy_id: 'p1234567-89ab-cdef-0123-456789abcdef',
                },
                payload: {
                    reason: 'category_blocked',
                    policy_name: 'Block Social Media',
                },
                ai: {
                    risk_score: 0.7,
                    risk_bucket: 'medium',
                    labels: ['social', 'distraction'],
                },
                privacy: {
                    pii_level: 'low',
                    contains_raw_content: false,
                    hashed_fields: ['url'],
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(true);
        });
    });

    describe('Policy Event', () => {
        it('should validate policy_created event', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440003',
                kind: 'policy',
                subtype: 'policy_created',
                occurred_at: '2025-01-15T10:00:00.000Z',
                source: {
                    agent_type: 'backend',
                    agent_version: '1.0.0',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                },
                context: {
                    policy_id: 'p1234567-89ab-cdef-0123-456789abcdef',
                    policy_type: 'category',
                    user_id: 'u1234567-89ab-cdef-0123-456789abcdef',
                },
                payload: {
                    name: 'Block Social Media',
                    enabled: true,
                    priority: 50,
                    config: {
                        blocked_categories: ['social_media', 'gaming'],
                    },
                },
                privacy: {
                    pii_level: 'none',
                    contains_raw_content: false,
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(true);
        });
    });

    describe('System Event', () => {
        it('should validate command_acknowledged event', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440004',
                kind: 'system',
                subtype: 'command_acknowledged',
                occurred_at: '2025-01-15T16:00:00.000Z',
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.2.0',
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                },
                context: {
                    command_id: 'cmd-123456',
                    status: 'processed',
                },
                payload: {
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    action: 'lock_device',
                    result: 'success',
                },
                privacy: {
                    pii_level: 'none',
                    contains_raw_content: false,
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(true);
        });
    });

    describe('Required Fields', () => {
        it('should reject event without event_id', () => {
            const event = {
                schema_version: 1,
                kind: 'usage',
                subtype: 'app_session_started',
                occurred_at: '2025-01-15T14:30:00.000Z',
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.0.0',
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(false);
        });

        it('should reject event without kind', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440001',
                subtype: 'app_session_started',
                occurred_at: '2025-01-15T14:30:00.000Z',
                source: {
                    agent_type: 'device_agent',
                    agent_version: '1.0.0',
                },
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(false);
        });

        it('should reject event without source', () => {
            const event = {
                schema_version: 1,
                event_id: '550e8400-e29b-41d4-a716-446655440001',
                kind: 'usage',
                subtype: 'app_session_started',
                occurred_at: '2025-01-15T14:30:00.000Z',
            };

            const result = guardianEventSchema.safeParse(event);
            expect(result.success).toBe(false);
        });
    });
});

describe('GuardianCommand Contract', () => {
    describe('Immediate Action', () => {
        it('should validate lock_device command', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-550e8400-e29b-41d4-a716-446655440010',
                kind: 'immediate_action',
                action: 'lock_device',
                target: {
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                },
                params: {
                    reason: 'bedtime',
                    message: 'Time for bed! Device locked by parent.',
                },
                issued_by: {
                    actor_type: 'parent',
                    user_id: 'u1234567-89ab-cdef-0123-456789abcdef',
                },
                created_at: '2025-01-15T21:00:00.000Z',
                expires_at: '2025-01-15T22:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(true);
        });
    });

    describe('Session Request', () => {
        it('should validate extend_session command', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-550e8400-e29b-41d4-a716-446655440011',
                kind: 'session_request',
                action: 'extend_session',
                target: {
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                },
                params: {
                    minutes_granted: 30,
                    session_id: 'sess-abc123',
                    reason: 'homework',
                },
                issued_by: {
                    actor_type: 'parent',
                    user_id: 'u1234567-89ab-cdef-0123-456789abcdef',
                },
                created_at: '2025-01-15T18:30:00.000Z',
                expires_at: '2025-01-15T19:30:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(true);
        });

        it('should validate temporary_unblock command', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-550e8400-e29b-41d4-a716-446655440012',
                kind: 'session_request',
                action: 'temporary_unblock',
                target: {
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                    child_id: 'c1234567-89ab-cdef-0123-456789abcdef',
                },
                params: {
                    resource: {
                        domain: 'educational-site.example.com',
                    },
                    duration_minutes: 60,
                    reason: 'school project',
                },
                issued_by: {
                    actor_type: 'parent',
                    user_id: 'u1234567-89ab-cdef-0123-456789abcdef',
                },
                created_at: '2025-01-15T14:00:00.000Z',
                expires_at: '2025-01-15T15:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(true);
        });
    });

    describe('Policy Update', () => {
        it('should validate sync_policies command', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-550e8400-e29b-41d4-a716-446655440013',
                kind: 'policy_update',
                action: 'sync_policies',
                target: {
                    device_id: 'd1234567-89ab-cdef-0123-456789abcdef',
                },
                params: {
                    policy_version: 'v2025-01-15-001',
                    force_refresh: true,
                },
                issued_by: {
                    actor_type: 'system',
                },
                created_at: '2025-01-15T10:05:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(true);
        });
    });

    describe('Required Fields', () => {
        it('should reject command without command_id', () => {
            const command = {
                schema_version: 1,
                kind: 'immediate_action',
                action: 'lock_device',
                issued_by: {
                    actor_type: 'parent',
                },
                created_at: '2025-01-15T21:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(false);
        });

        it('should reject command without action', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-123',
                kind: 'immediate_action',
                issued_by: {
                    actor_type: 'parent',
                },
                created_at: '2025-01-15T21:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(false);
        });

        it('should reject command without issued_by', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-123',
                kind: 'immediate_action',
                action: 'lock_device',
                created_at: '2025-01-15T21:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(false);
        });

        it('should reject invalid actor_type', () => {
            const command = {
                schema_version: 1,
                command_id: 'cmd-123',
                kind: 'immediate_action',
                action: 'lock_device',
                issued_by: {
                    actor_type: 'invalid_actor',
                },
                created_at: '2025-01-15T21:00:00.000Z',
            };

            const result = guardianCommandSchema.safeParse(command);
            expect(result.success).toBe(false);
        });
    });
});
