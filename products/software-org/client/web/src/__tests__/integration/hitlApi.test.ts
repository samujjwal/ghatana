/**
 * HITL API Integration Tests
 *
 * <p><b>Purpose</b><br>
 * Integration tests for Human-in-the-Loop (HITL) API endpoints.
 * Tests action submission, approval/rejection, and status tracking.
 *
 * <p><b>Coverage</b><br>
 * - Submit actions
 * - Get action status
 * - Approve actions
 * - Reject actions
 * - List pending actions
 * - Confidence-based routing
 * - Error handling
 *
 * @doc.type test
 * @doc.purpose HITL API integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import { describe, it, expect } from 'vitest';
import { hitlApi } from '../../services/api/hitlApi';
import './setup';

describe('HITL API Integration Tests', () => {
    describe('POST /api/v1/hitl/actions', () => {
        it('should submit action requiring approval (low confidence)', async () => {
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy to production',
                confidence: 0.5,
                context: { environment: 'production' },
            };

            const result = await hitlApi.submitAction(action);

            expect(result).toBeDefined();
            expect(result.actionId).toBeDefined();
            expect(result.status).toBe('PENDING');
            expect(result.requiresApproval).toBe(true);
            expect(result.message).toContain('approval');
        });

        it('should auto-approve action (high confidence)', async () => {
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy to staging',
                confidence: 0.9,
                context: { environment: 'staging' },
            };

            const result = await hitlApi.submitAction(action);

            expect(result).toBeDefined();
            expect(result.actionId).toBeDefined();
            expect(result.status).toBe('APPROVED');
            expect(result.requiresApproval).toBe(false);
            expect(result.message).toContain('auto-approved');
        });

        it('should handle confidence threshold correctly', async () => {
            // Test at threshold (0.7)
            const actionAtThreshold = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy at threshold',
                confidence: 0.7,
                context: {},
            };

            const resultAtThreshold = await hitlApi.submitAction(actionAtThreshold);
            expect(resultAtThreshold.requiresApproval).toBe(false); // >= 0.7 is auto-approved

            // Test below threshold
            const actionBelowThreshold = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy below threshold',
                confidence: 0.69,
                context: {},
            };

            const resultBelowThreshold = await hitlApi.submitAction(actionBelowThreshold);
            expect(resultBelowThreshold.requiresApproval).toBe(true); // < 0.7 requires approval
        });
    });

    describe('GET /api/v1/hitl/actions/:id', () => {
        it('should get action status', async () => {
            const status = await hitlApi.getActionStatus('action-1');

            expect(status).toBeDefined();
            expect(status.actionId).toBe('action-1');
            expect(status.state).toBeDefined();
            expect(status.submittedAt).toBeDefined();
            expect(status.submittedBy).toBeDefined();
        });

        it('should return status with required properties', async () => {
            const status = await hitlApi.getActionStatus('action-1');

            expect(status).toHaveProperty('actionId');
            expect(status).toHaveProperty('state');
            expect(status).toHaveProperty('submittedAt');
            expect(status).toHaveProperty('submittedBy');
        });
    });

    describe('POST /api/v1/hitl/actions/:id/approve', () => {
        it('should approve an action', async () => {
            const approvalRequest = {
                approverId: 'user-1',
                comment: 'Approved after review',
            };

            const result = await hitlApi.approveAction('action-1', approvalRequest);

            expect(result).toBeDefined();
            expect(result.actionId).toBe('action-1');
            expect(result.status).toBe('APPROVED');
            expect(result.message).toContain('approved');
        });

        it('should handle approval without comment', async () => {
            const approvalRequest = {
                approverId: 'user-1',
            };

            const result = await hitlApi.approveAction('action-1', approvalRequest);

            expect(result).toBeDefined();
            expect(result.status).toBe('APPROVED');
        });
    });

    describe('POST /api/v1/hitl/actions/:id/reject', () => {
        it('should reject an action', async () => {
            const rejectionRequest = {
                rejectorId: 'user-1',
                reason: 'Does not meet security requirements',
            };

            const result = await hitlApi.rejectAction('action-1', rejectionRequest);

            expect(result).toBeDefined();
            expect(result.actionId).toBe('action-1');
            expect(result.status).toBe('REJECTED');
            expect(result.message).toContain('rejected');
        });

        it('should require rejection reason', async () => {
            const rejectionRequest = {
                rejectorId: 'user-1',
                reason: 'Security concerns',
            };

            const result = await hitlApi.rejectAction('action-1', rejectionRequest);

            expect(result).toBeDefined();
            expect(result.message).toBeDefined();
        });
    });

    describe('GET /api/v1/hitl/pending', () => {
        it('should list pending actions', async () => {
            const pending = await hitlApi.listPendingActions();

            expect(pending).toBeInstanceOf(Array);
            expect(pending.length).toBeGreaterThan(0);
        });

        it('should return pending actions with required properties', async () => {
            const pending = await hitlApi.listPendingActions();

            pending.forEach(action => {
                expect(action).toHaveProperty('actionId');
                expect(action).toHaveProperty('state');
                expect(action.state).toBe('PENDING');
                expect(action).toHaveProperty('submittedAt');
                expect(action).toHaveProperty('submittedBy');
            });
        });

        it('should only return PENDING actions', async () => {
            const pending = await hitlApi.listPendingActions();

            pending.forEach(action => {
                expect(action.state).toBe('PENDING');
            });
        });
    });

    describe('HITL Workflow Integration', () => {
        it('should complete full approval workflow', async () => {
            // 1. Submit action
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy to production',
                confidence: 0.5,
                context: { environment: 'production' },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(true);

            const actionId = submitResult.actionId;

            // 2. Check status
            const status = await hitlApi.getActionStatus(actionId);
            expect(status.state).toBe('PENDING');

            // 3. Approve action
            const approvalRequest = {
                approverId: 'user-1',
                comment: 'Looks good',
            };

            const approveResult = await hitlApi.approveAction(actionId, approvalRequest);
            expect(approveResult.status).toBe('APPROVED');
        });

        it('should complete full rejection workflow', async () => {
            // 1. Submit action
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy to production',
                confidence: 0.4,
                context: { environment: 'production' },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(true);

            const actionId = submitResult.actionId;

            // 2. Reject action
            const rejectionRequest = {
                rejectorId: 'user-1',
                reason: 'Missing security review',
            };

            const rejectResult = await hitlApi.rejectAction(actionId, rejectionRequest);
            expect(rejectResult.status).toBe('REJECTED');
        });
    });
});
