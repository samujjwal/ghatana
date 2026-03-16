/**
 * Children Routes Tests
 *
 * Validates child-related API endpoints, including request/decision flows:
 * - POST /api/children/:id/requests
 * - POST /api/children/:id/requests/:requestId/decision
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import { request } from '../helpers/request.helper';
import { createTestApp } from '../helpers/app.helper';
import { randomEmail, randomString } from '../setup';
import * as deviceService from '../../services/device.service';
import { query } from '../../db';
import { createTestUser } from '../fixtures/user.fixtures';

let app: FastifyInstance;

describe('Children Routes – Requests & Decisions', () => {
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
        const user = await createTestUser({ email: randomEmail() });
        testUserId = user.user.id;
        testAccessToken = user.accessToken;

        const childRows = await query(
            'INSERT INTO children (user_id, name, age) VALUES ($1, $2, $3) RETURNING *',
            [testUserId, 'Test Child', 12]
        );
        testChildId = childRows[0].id;

        const device = await deviceService.registerDevice(testUserId, {
            device_type: 'mobile',
            device_name: 'Child Phone',
            device_fingerprint: randomString(16),
            child_id: testChildId,
        });
        testDeviceId = device.id;
    });

    describe('POST /api/children/:id/requests', () => {
        it('should create an extend_session request for a child', async () => {
            const response = await request(app)
                .post(`/api/children/${testChildId}/requests`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'extend_session',
                    device_id: testDeviceId,
                    session_id: 'session-1',
                    minutes: 15,
                })
                .expect(201);

            expect(response.body.success).toBe(true);
            expect(response.body.request_id).toBeDefined();
        });

        it('should validate minutes for extend_session', async () => {
            const response = await request(app)
                .post(`/api/children/${testChildId}/requests`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'extend_session',
                    device_id: testDeviceId,
                    session_id: 'session-1',
                })
                .expect(400);

            expect(response.body.success).toBe(false);
            expect(response.body.error).toContain('minutes is required');
        });
    });

    describe('POST /api/children/:id/requests/:requestId/decision', () => {
        it('should approve extend_session and enqueue a command', async () => {
            const requestResponse = await request(app)
                .post(`/api/children/${testChildId}/requests`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'extend_session',
                    device_id: testDeviceId,
                    session_id: 'session-1',
                    minutes: 10,
                })
                .expect(201);

            const requestId = requestResponse.body.request_id as string;

            const decisionResponse = await request(app)
                .post(`/api/children/${testChildId}/requests/${requestId}/decision`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'extend_session',
                    decision: 'approved',
                    minutes_granted: 10,
                    device_id: testDeviceId,
                    session_id: 'session-1',
                })
                .expect(200);

            expect(decisionResponse.body.success).toBe(true);
            expect(decisionResponse.body.command_id).toBeDefined();
        });

        it('should allow denying a request without enqueuing a command', async () => {
            const requestResponse = await request(app)
                .post(`/api/children/${testChildId}/requests`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'unblock',
                    device_id: testDeviceId,
                    session_id: 'session-2',
                })
                .expect(201);

            const requestId = requestResponse.body.request_id as string;

            const decisionResponse = await request(app)
                .post(`/api/children/${testChildId}/requests/${requestId}/decision`)
                .set('Authorization', `Bearer ${testAccessToken}`)
                .send({
                    type: 'unblock',
                    decision: 'denied',
                    device_id: testDeviceId,
                    session_id: 'session-2',
                })
                .expect(200);

            expect(decisionResponse.body.success).toBe(true);
            expect(decisionResponse.body.command_id).toBeUndefined();
        });
    });
});
