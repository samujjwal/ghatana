/**
 * Integration Test Suite for End-to-End Flows
 * 
 * Comprehensive end-to-end tests covering critical user workflows.
 * These tests verify that all components work together correctly.
 * 
 * @doc.type test
 * @doc.purpose End-to-end integration testing
 * @doc.layer testing
 * @doc.pattern Integration Test
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { setupTestEnvironment, teardownTestEnvironment } from './testSetup';

// Test data
const TEST_USER = {
  email: 'test@example.com',
  name: 'Test User',
};

describe('End-to-End Flow Tests', () => {
  let env: any;

  beforeAll(async () => {
    env = await setupTestEnvironment();
  });

  afterAll(async () => {
    await teardownTestEnvironment(env);
  });

  describe('Flow 1: User Onboarding', () => {
    it('should create user and default workspace', async () => {
      const user = await env.api.users.create(TEST_USER);
      expect(user.id).toBeDefined();
      
      const workspaces = await env.api.workspaces.list(user.id);
      expect(workspaces).toHaveLength(1);
      expect(workspaces[0].isDefault).toBe(true);
    });

    it('should complete onboarding checklist', async () => {
      const user = await env.api.users.create({
        email: 'onboarding@test.com',
        name: 'Onboarding Test',
      });

      // Complete each onboarding step
      await env.api.onboarding.completeStep(user.id, 'profile');
      await env.api.onboarding.completeStep(user.id, 'workspace');
      await env.api.onboarding.completeStep(user.id, 'first-project');

      const status = await env.api.onboarding.getStatus(user.id);
      expect(status.completed).toBe(true);
      expect(status.steps).toHaveLength(3);
    });
  });

  describe('Flow 2: Project Creation', () => {
    it('should create project from template', async () => {
      const workspace = await env.api.workspaces.create({
        name: 'Test Workspace',
      });

      const project = await env.api.projects.create({
        workspaceId: workspace.id,
        name: 'React App',
        template: 'react-vite',
      });

      expect(project.id).toBeDefined();
      expect(project.workspaceId).toBe(workspace.id);
      expect(project.status).toBe('DRAFT');
    });

    it('should scaffold project files', async () => {
      const project = await env.api.projects.create({
        name: 'Node API',
        template: 'node-express',
      });

      const scaffoldResult = await env.api.scaffold.execute({
        projectId: project.id,
        steps: ['init', 'dependencies', 'structure'],
      });

      expect(scaffoldResult.completed).toBe(true);
      expect(scaffoldResult.files).toBeGreaterThan(0);
    });
  });

  describe('Flow 3: Canvas Design', () => {
    it('should create canvas with nodes', async () => {
      const project = await env.api.projects.create({
        name: 'Canvas Test Project',
      });

      const canvas = await env.api.canvas.create({
        projectId: project.id,
        name: 'Architecture Diagram',
      });

      // Add nodes
      const node1 = await env.api.canvas.addNode(canvas.id, {
        type: 'service',
        position: { x: 100, y: 100 },
        data: { name: 'API Gateway' },
      });

      const node2 = await env.api.canvas.addNode(canvas.id, {
        type: 'service',
        position: { x: 300, y: 100 },
        data: { name: 'Auth Service' },
      });

      // Add edge
      await env.api.canvas.addEdge(canvas.id, {
        source: node1.id,
        target: node2.id,
      });

      const canvasState = await env.api.canvas.getState(canvas.id);
      expect(canvasState.nodes).toHaveLength(2);
      expect(canvasState.edges).toHaveLength(1);
    });

    it('should generate code from canvas', async () => {
      const project = await env.api.projects.create({ name: 'Code Gen Test' });
      const canvas = await env.api.canvas.create({ projectId: project.id });

      // Add architecture nodes
      await env.api.canvas.addNode(canvas.id, {
        type: 'api-endpoint',
        data: { path: '/users', method: 'GET' },
      });

      // Generate code
      const generation = await env.api.ai.generateFromCanvas({
        canvasId: canvas.id,
        target: 'backend',
      });

      expect(generation.status).toBe('completed');
      expect(generation.files).toBeDefined();
      expect(generation.files.length).toBeGreaterThan(0);
    });
  });

  describe('Flow 4: Agent Execution', () => {
    it('should execute single agent', async () => {
      const result = await env.api.agents.execute({
        agentId: 'code-analyzer',
        input: {
          code: 'function hello() { return "world"; }',
          language: 'javascript',
        },
      });

      expect(result.status).toBe('success');
      expect(result.output).toBeDefined();
      expect(result.agentId).toBe('code-analyzer');
    });

    it('should execute agent workflow', async () => {
      const workflow = await env.api.agents.createWorkflow({
        name: 'Code Review Flow',
        steps: [
          { agent: 'code-analyzer', input: { type: 'analyze' } },
          { agent: 'security-analyzer', input: { type: 'security-check' } },
          { agent: 'test-generator', input: { type: 'generate-tests' } },
        ],
      });

      const execution = await env.api.agents.executeWorkflow(workflow.id, {
        code: 'sample code here',
      });

      expect(execution.status).toBe('completed');
      expect(execution.steps).toHaveLength(3);
      expect(execution.results).toBeDefined();
    });
  });

  describe('Flow 5: Compliance Assessment', () => {
    it('should run compliance audit', async () => {
      const project = await env.api.projects.create({
        name: 'Compliance Test',
        type: 'BACKEND',
      });

      const audit = await env.api.compliance.runAudit({
        projectId: project.id,
        frameworks: ['SOC2', 'GDPR'],
      });

      expect(audit.id).toBeDefined();
      expect(audit.status).toBe('running');

      // Poll for completion
      const result = await env.helpers.poll(
        () => env.api.compliance.getAuditStatus(audit.id),
        (status) => status === 'completed',
        { timeout: 60000 }
      );

      expect(result.findings).toBeDefined();
      expect(result.score).toBeGreaterThanOrEqual(0);
      expect(result.score).toBeLessThanOrEqual(100);
    });
  });

  describe('Flow 6: Multi-tenancy Isolation', () => {
    it('should isolate tenant data', async () => {
      const tenant1 = await env.api.tenants.create({ name: 'Tenant A' });
      const tenant2 = await env.api.tenants.create({ name: 'Tenant B' });

      // Create workspace in tenant 1
      const ws1 = await env.api.workspaces.create(
        { name: 'Tenant A Workspace' },
        { tenantId: tenant1.id }
      );

      // Try to access from tenant 2 (should fail)
      await expect(
        env.api.workspaces.get(ws1.id, { tenantId: tenant2.id })
      ).rejects.toThrow('Not Found');
    });

    it('should isolate tenant agents', async () => {
      const tenant1 = await env.api.tenants.create({ name: 'Tenant A' });
      const tenant2 = await env.api.tenants.create({ name: 'Tenant B' });

      // Execute agent in tenant 1 context
      const result1 = await env.api.agents.execute({
        agentId: 'context-gathering-agent',
        tenantId: tenant1.id,
      });

      // Result should only contain tenant 1 data
      expect(result1.tenantId).toBe(tenant1.id);
    });
  });

  describe('Flow 7: Real-time Collaboration', () => {
    it('should sync canvas changes across clients', async () => {
      const project = await env.api.projects.create({ name: 'Collab Test' });
      const canvas = await env.api.canvas.create({ projectId: project.id });

      // Connect two clients
      const client1 = await env.websocket.connect(canvas.id, 'user-1');
      const client2 = await env.websocket.connect(canvas.id, 'user-2');

      // Client 1 makes change
      await client1.send({
        type: 'add-node',
        data: { type: 'service', position: { x: 100, y: 100 } },
      });

      // Client 2 should receive the update
      const update = await client2.waitForMessage({ type: 'node-added' });
      expect(update.data.node).toBeDefined();

      // Cleanup
      await client1.disconnect();
      await client2.disconnect();
    });
  });

  describe('Flow 8: Error Handling & Recovery', () => {
    it('should handle agent failure gracefully', async () => {
      const result = await env.api.agents.execute({
        agentId: 'always-fails-agent', // Test agent that always fails
        input: {},
      });

      expect(result.status).toBe('failed');
      expect(result.error).toBeDefined();
      expect(result.error.code).toBeDefined();
      expect(result.requestId).toBeDefined();
    });

    it('should implement circuit breaker pattern', async () => {
      // Make multiple failing requests
      for (let i = 0; i < 5; i++) {
        try {
          await env.api.agents.execute({
            agentId: 'failing-agent',
          });
        } catch (e) {
          // Expected to fail
        }
      }

      // Circuit should be open now
      const result = await env.api.agents.execute({
        agentId: 'failing-agent',
      });

      expect(result.status).toBe('circuit-open');
    });
  });
});
