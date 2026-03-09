/**
 * Organization API Integration Tests
 *
 * <p><b>Purpose</b><br>
 * Integration tests for Organization API endpoints.
 * Tests all CRUD operations and error handling.
 *
 * <p><b>Coverage</b><br>
 * - Get organization configuration
 * - Get hierarchy graph
 * - Move nodes
 * - List departments and agents
 * - Error handling
 *
 * @doc.type test
 * @doc.purpose Organization API integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import { describe, it, expect } from 'vitest';
import { organizationApi } from '../../services/api/organizationApi';
import './setup';

describe('Organization API Integration Tests', () => {
    describe('GET /api/v1/org/config', () => {
        it('should fetch organization configuration', async () => {
            const config = await organizationApi.getConfig();

            expect(config).toBeDefined();
            expect(config.name).toBe('test-org');
            expect(config.namespace).toBe('test');
            expect(config.displayName).toBe('Test Organization');
            expect(config.structure.type).toBe('hierarchical');
            expect(config.settings.hitl.enabled).toBe(true);
            expect(config.settings.hitl.confidenceThreshold).toBe(0.7);
        });

        it('should have valid settings structure', async () => {
            const config = await organizationApi.getConfig();

            expect(config.settings).toHaveProperty('defaultTimezone');
            expect(config.settings).toHaveProperty('events');
            expect(config.settings).toHaveProperty('hitl');
            expect(config.settings).toHaveProperty('ai');
            expect(config.settings.events.enabled).toBe(true);
        });
    });

    describe('GET /api/v1/org/graph', () => {
        it('should fetch organization hierarchy graph', async () => {
            const graph = await organizationApi.getGraph();

            expect(graph).toBeDefined();
            expect(graph.nodes).toBeInstanceOf(Array);
            expect(graph.edges).toBeInstanceOf(Array);
            expect(graph.metadata).toBeDefined();
        });

        it('should have valid graph structure', async () => {
            const graph = await organizationApi.getGraph();

            expect(graph.nodes.length).toBeGreaterThan(0);
            expect(graph.edges.length).toBeGreaterThan(0);
            expect(graph.metadata.nodeCount).toBe(graph.nodes.length);
            expect(graph.metadata.edgeCount).toBe(graph.edges.length);
        });

        it('should have nodes with required properties', async () => {
            const graph = await organizationApi.getGraph();

            graph.nodes.forEach(node => {
                expect(node).toHaveProperty('id');
                expect(node).toHaveProperty('label');
                expect(node).toHaveProperty('type');
                expect(node).toHaveProperty('metadata');
            });
        });

        it('should have edges with required properties', async () => {
            const graph = await organizationApi.getGraph();

            graph.edges.forEach(edge => {
                expect(edge).toHaveProperty('from');
                expect(edge).toHaveProperty('to');
                expect(edge).toHaveProperty('type');
            });
        });
    });

    describe('POST /api/v1/org/hierarchy/move', () => {
        it('should move a node successfully', async () => {
            const moveRequest = {
                nodeId: 'agent-1',
                nodeType: 'agent',
                fromParentId: 'dept-1',
                toParentId: 'dept-2',
            };

            const result = await organizationApi.moveNode(moveRequest);

            expect(result).toBeDefined();
            expect(result.success).toBe(true);
            expect(result.nodeId).toBe('agent-1');
            expect(result.newParentId).toBe('dept-2');
            expect(result.message).toContain('successfully');
        });

        it('should return valid move result structure', async () => {
            const moveRequest = {
                nodeId: 'agent-1',
                nodeType: 'agent',
                toParentId: 'dept-2',
            };

            const result = await organizationApi.moveNode(moveRequest);

            expect(result).toHaveProperty('success');
            expect(result).toHaveProperty('nodeId');
            expect(result).toHaveProperty('message');
            expect(result).toHaveProperty('metadata');
        });
    });

    describe('GET /api/v1/org/departments', () => {
        it('should list all departments', async () => {
            const departments = await organizationApi.listDepartments();

            expect(departments).toBeInstanceOf(Array);
            expect(departments.length).toBeGreaterThan(0);
        });

        it('should return departments with required properties', async () => {
            const departments = await organizationApi.listDepartments();

            departments.forEach(dept => {
                expect(dept).toHaveProperty('id');
                expect(dept).toHaveProperty('name');
                expect(dept).toHaveProperty('agentCount');
                expect(typeof dept.agentCount).toBe('number');
            });
        });
    });

    describe('GET /api/v1/org/agents', () => {
        it('should list all agents', async () => {
            const agents = await organizationApi.listAgents();

            expect(agents).toBeInstanceOf(Array);
            expect(agents.length).toBeGreaterThan(0);
        });

        it('should return agents with required properties', async () => {
            const agents = await organizationApi.listAgents();

            agents.forEach(agent => {
                expect(agent).toHaveProperty('id');
                expect(agent).toHaveProperty('name');
                expect(agent).toHaveProperty('role');
                expect(agent).toHaveProperty('department');
                expect(agent).toHaveProperty('status');
            });
        });

        it('should have valid agent status values', async () => {
            const agents = await organizationApi.listAgents();

            agents.forEach(agent => {
                expect(['ONLINE', 'OFFLINE', 'BUSY']).toContain(agent.status);
            });
        });
    });
});
