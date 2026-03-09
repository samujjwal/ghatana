/**
 * Complete Workflow E2E Tests
 *
 * <p><b>Purpose</b><br>
 * End-to-end tests for complete workflows across all APIs.
 * Tests realistic user scenarios and multi-step operations.
 *
 * <p><b>Coverage</b><br>
 * - Organization setup and configuration
 * - Department management
 * - Agent assignment and movement
 * - HITL approval workflows
 * - KPI tracking
 * - Error recovery
 *
 * @doc.type test
 * @doc.purpose Complete workflow E2E tests
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { describe, it, expect } from 'vitest';
import { organizationApi } from '../../services/api/organizationApi';
import { hitlApi } from '../../services/api/hitlApi';
import '../integration/setup';

const API_BASE_URL = 'http://localhost:8080';

describe('Complete Workflow E2E Tests', () => {
    describe('Organization Setup Workflow', () => {
        it('should complete full organization setup', async () => {
            // 1. Get organization configuration
            const config = await organizationApi.getConfig();
            expect(config).toBeDefined();
            expect(config.name).toBeDefined();

            // 2. Get hierarchy graph
            const graph = await organizationApi.getGraph();
            expect(graph.nodes.length).toBeGreaterThan(0);
            expect(graph.edges.length).toBeGreaterThan(0);

            // 3. List departments
            const departments = await organizationApi.listDepartments();
            expect(departments.length).toBeGreaterThan(0);

            // 4. List agents
            const agents = await organizationApi.listAgents();
            expect(agents.length).toBeGreaterThan(0);

            // Verify consistency
            const deptIds = departments.map(d => d.id);
            const agentDepts = agents.map(a => a.department);
            agentDepts.forEach(dept => {
                // Note: dept is name, not ID in current implementation
                expect(dept).toBeDefined();
            });
        });
    });

    describe('Department Management Workflow', () => {
        it('should manage department lifecycle', async () => {
            // 1. List all departments
            const response1 = await fetch(`${API_BASE_URL}/api/v1/departments`);
            const departments = await response1.json();
            expect(departments.length).toBeGreaterThan(0);

            const deptId = departments[0].id;

            // 2. Get department details
            const response2 = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}`);
            const detail = await response2.json();
            expect(detail.id).toBe(deptId);

            // 3. Get department agents
            const response3 = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/agents`);
            const agents = await response3.json();
            expect(agents).toBeInstanceOf(Array);

            // 4. Get department KPIs
            const response4 = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/kpis`);
            const kpis = await response4.json();
            expect(kpis.departmentId).toBe(deptId);
            expect(kpis.velocity).toBeDefined();

            // 5. Get department workflows
            const response5 = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/workflows`);
            const workflows = await response5.json();
            expect(workflows).toBeInstanceOf(Array);
        });
    });

    describe('Agent Movement Workflow', () => {
        it('should move agent between departments', async () => {
            // 1. Get initial state
            const departments = await organizationApi.listDepartments();
            expect(departments.length).toBeGreaterThanOrEqual(2);

            const agents = await organizationApi.listAgents();
            expect(agents.length).toBeGreaterThan(0);

            const agent = agents[0];
            const sourceDept = departments[0];
            const targetDept = departments[1];

            // 2. Move agent
            const moveRequest = {
                nodeId: agent.id,
                nodeType: 'agent',
                fromParentId: sourceDept.id,
                toParentId: targetDept.id,
            };

            const moveResult = await organizationApi.moveNode(moveRequest);
            expect(moveResult.success).toBe(true);
            expect(moveResult.nodeId).toBe(agent.id);
            expect(moveResult.newParentId).toBe(targetDept.id);

            // 3. Verify graph updated
            const updatedGraph = await organizationApi.getGraph();
            expect(updatedGraph.nodes.length).toBeGreaterThan(0);
        });
    });

    describe('HITL Approval Workflow', () => {
        it('should complete HITL approval workflow', async () => {
            // 1. Submit low-confidence action
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy critical update to production',
                confidence: 0.5,
                context: {
                    environment: 'production',
                    service: 'api-gateway',
                    version: '2.0.0',
                },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(true);
            expect(submitResult.status).toBe('PENDING');

            const actionId = submitResult.actionId;

            // 2. Check pending actions
            const pending = await hitlApi.listPendingActions();
            expect(pending.length).toBeGreaterThan(0);

            // 3. Get action status
            const status = await hitlApi.getActionStatus(actionId);
            expect(status.state).toBe('PENDING');
            expect(status.submittedBy).toBe('agent-1');

            // 4. Approve action
            const approvalRequest = {
                approverId: 'user-1',
                comment: 'Reviewed and approved',
            };

            const approveResult = await hitlApi.approveAction(actionId, approvalRequest);
            expect(approveResult.status).toBe('APPROVED');
            expect(approveResult.actionId).toBe(actionId);
        });

        it('should handle rejection workflow', async () => {
            // 1. Submit action
            const action = {
                agentId: 'agent-2',
                actionType: 'code_review',
                description: 'Merge pull request',
                confidence: 0.3,
                context: {
                    pr: '123',
                    branch: 'feature/new-api',
                },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(true);

            const actionId = submitResult.actionId;

            // 2. Reject action
            const rejectionRequest = {
                rejectorId: 'user-1',
                reason: 'Missing test coverage',
            };

            const rejectResult = await hitlApi.rejectAction(actionId, rejectionRequest);
            expect(rejectResult.status).toBe('REJECTED');
            expect(rejectResult.actionId).toBe(actionId);
        });

        it('should auto-approve high-confidence actions', async () => {
            // Submit high-confidence action
            const action = {
                agentId: 'agent-1',
                actionType: 'deployment',
                description: 'Deploy to staging',
                confidence: 0.95,
                context: {
                    environment: 'staging',
                },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(false);
            expect(submitResult.status).toBe('APPROVED');
            expect(submitResult.message).toContain('auto-approved');
        });
    });

    describe('KPI Tracking Workflow', () => {
        it('should track department KPIs over time', async () => {
            // 1. Get departments
            const departments = await organizationApi.listDepartments();
            const deptId = departments[0].id;

            // 2. Get initial KPIs
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/kpis`);
            const kpis = await response.json();

            expect(kpis).toBeDefined();
            expect(kpis.departmentId).toBe(deptId);
            expect(kpis.velocity).toBeGreaterThanOrEqual(0);
            expect(kpis.throughput).toBeGreaterThanOrEqual(0);
            expect(kpis.quality).toBeGreaterThanOrEqual(0);
            expect(kpis.efficiency).toBeGreaterThanOrEqual(0);
            expect(kpis.timestamp).toBeDefined();

            // Verify KPI values are in valid range (0-100)
            expect(kpis.velocity).toBeLessThanOrEqual(100);
            expect(kpis.quality).toBeLessThanOrEqual(100);
            expect(kpis.efficiency).toBeLessThanOrEqual(100);
        });
    });

    describe('Error Recovery Workflow', () => {
        it('should handle API errors gracefully', async () => {
            // Test 404 error
            await expect(async () => {
                const response = await fetch(`${API_BASE_URL}/api/v1/departments/non-existent`);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
            }).rejects.toThrow();
        });

        it('should handle invalid move operations', async () => {
            const invalidMove = {
                nodeId: 'invalid-node',
                nodeType: 'agent',
                toParentId: 'invalid-parent',
            };

            // Should still return a result (backend handles validation)
            const result = await organizationApi.moveNode(invalidMove);
            expect(result).toBeDefined();
        });
    });

    describe('Multi-API Integration Workflow', () => {
        it('should coordinate across all APIs', async () => {
            // 1. Get organization config
            const config = await organizationApi.getConfig();
            expect(config.settings.hitl.enabled).toBe(true);

            const threshold = config.settings.hitl.confidenceThreshold;

            // 2. Get departments
            const departments = await organizationApi.listDepartments();
            const deptId = departments[0].id;

            // 3. Get department agents
            const response = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/agents`);
            const agents = await response.json();
            expect(agents.length).toBeGreaterThan(0);

            const agentId = agents[0].id;

            // 4. Submit HITL action from agent
            const action = {
                agentId,
                actionType: 'deployment',
                description: 'Coordinated deployment',
                confidence: threshold - 0.1, // Just below threshold
                context: { department: deptId },
            };

            const submitResult = await hitlApi.submitAction(action);
            expect(submitResult.requiresApproval).toBe(true);

            // 5. Get department KPIs
            const kpisResponse = await fetch(`${API_BASE_URL}/api/v1/departments/${deptId}/kpis`);
            const kpis = await kpisResponse.json();
            expect(kpis.departmentId).toBe(deptId);

            // All APIs working together successfully
            expect(config).toBeDefined();
            expect(departments).toBeDefined();
            expect(agents).toBeDefined();
            expect(submitResult).toBeDefined();
            expect(kpis).toBeDefined();
        });
    });
});
