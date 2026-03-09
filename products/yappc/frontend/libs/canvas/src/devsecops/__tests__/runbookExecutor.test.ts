/**
 * Tests for Runbook Integration & Automation
 */

import { describe, it, expect } from 'vitest';

import {
  parseAnsiblePlaybook,
  parseTerraformPlan,
  runbookToCanvas,
  executeStep,
  completeStep,
  rollbackRunbook,
  requestApproval,
  processApproval,
  getExecutionHistory,
  analyzeRunbook,
  createRunbookConfig,
  getStepStyle,
  type Runbook,
  type RunbookStep,
  type ResourceChange,
} from '../index';

describe.skip('Runbook - Configuration', () => {
  it('should create default configuration', () => {
    const config = createRunbookConfig();

    expect(config.layout).toBe('sequential');
    expect(config.showTiming).toBe(true);
    expect(config.showApprovals).toBe(true);
    expect(config.enableRollback).toBe(true);
    expect(config.maxRetries).toBe(3);
  });

  it('should create configuration with overrides', () => {
    const config = createRunbookConfig({
      layout: 'dag',
      showTiming: false,
      maxRetries: 5,
    });

    expect(config.layout).toBe('dag');
    expect(config.showTiming).toBe(false);
    expect(config.maxRetries).toBe(5);
    expect(config.showApprovals).toBe(true); // Not overridden
  });
});

describe.skip('Runbook - Ansible Playbook Parsing', () => {
  it('should parse simple Ansible playbook', () => {
    const playbook = [
      {
        name: 'Deploy Web Server',
        hosts: 'webservers',
        tasks: [
          {
            name: 'Install nginx',
            apt: {
              name: 'nginx',
              state: 'present',
            },
          },
          {
            name: 'Start nginx',
            service: {
              name: 'nginx',
              state: 'started',
            },
          },
        ],
      },
    ];

    const runbook = parseAnsiblePlaybook(playbook);

    expect(runbook.type).toBe('ansible');
    expect(runbook.name).toBe('Deploy Web Server');
    
    // Should have: play-start, 2 tasks, play-end
    expect(runbook.steps).toHaveLength(4);
    
    const taskSteps = runbook.steps.filter(s => s.type === 'task');
    expect(taskSteps).toHaveLength(2);
    expect(taskSteps[0].name).toBe('Install nginx');
    expect(taskSteps[0].module).toBe('apt');
    expect(taskSteps[1].name).toBe('Start nginx');
    expect(taskSteps[1].module).toBe('service');
  });

  it('should handle multiple plays', () => {
    const playbook = [
      {
        name: 'Setup Database',
        hosts: 'database',
        tasks: [
          { name: 'Install PostgreSQL', apt: { name: 'postgresql' } },
        ],
      },
      {
        name: 'Setup Application',
        hosts: 'appservers',
        tasks: [
          { name: 'Deploy app', copy: { src: '/app', dest: '/opt/app' } },
        ],
      },
    ];

    const runbook = parseAnsiblePlaybook(playbook);

    // 2 plays × (start + tasks + end)
    expect(runbook.steps.length).toBeGreaterThan(4);
    
    const play1Start = runbook.steps.find(s => s.id === 'play-0-start');
    const play2Start = runbook.steps.find(s => s.id === 'play-1-start');
    
    expect(play1Start?.name).toContain('Setup Database');
    expect(play2Start?.name).toContain('Setup Application');
  });

  it('should parse task dependencies', () => {
    const playbook = [
      {
        name: 'Test Play',
        hosts: 'all',
        tasks: [
          { name: 'Task 1', command: 'echo hello' },
          { name: 'Task 2', command: 'echo world' },
        ],
      },
    ];

    const runbook = parseAnsiblePlaybook(playbook);

    const task1 = runbook.steps.find(s => s.name === 'Task 1');
    const task2 = runbook.steps.find(s => s.name === 'Task 2');

    expect(task1?.dependsOn).toContain('play-0-start');
    expect(task2?.dependsOn).toContain('step-0'); // Depends on Task 1
  });

  it('should handle conditional tasks', () => {
    const playbook = [
      {
        name: 'Conditional Deploy',
        hosts: 'all',
        tasks: [
          {
            name: 'Deploy only in production',
            command: 'deploy.sh',
            when: "environment == 'production'",
          },
        ],
      },
    ];

    const runbook = parseAnsiblePlaybook(playbook);

    const task = runbook.steps.find(s => s.type === 'task');
    expect(task?.condition).toBe("environment == 'production'");
  });
});

describe.skip('Runbook - Terraform Plan Parsing', () => {
  it('should parse simple Terraform plan', () => {
    const plan = {
      terraform_version: '1.5.0',
      resource_changes: [
        {
          address: 'aws_instance.web',
          type: 'aws_instance',
          name: 'web',
          change: {
            actions: ['create'],
            before: null,
            after: {
              instance_type: 't2.micro',
              ami: 'ami-12345',
            },
          },
        },
        {
          address: 'aws_security_group.web_sg',
          type: 'aws_security_group',
          name: 'web_sg',
          change: {
            actions: ['create'],
            before: null,
            after: {
              name: 'web-sg',
            },
          },
        },
      ],
    };

    const runbook = parseTerraformPlan(plan);

    expect(runbook.type).toBe('terraform');
    expect(runbook.version).toBe('1.5.0');
    expect(runbook.steps).toHaveLength(2);
    
    const createSteps = runbook.steps.filter(s => s.name.includes('create'));
    expect(createSteps).toHaveLength(2);
  });

  it('should detect high-risk resource changes', () => {
    const plan = {
      resource_changes: [
        {
          address: 'aws_db_instance.main',
          type: 'aws_db_instance',
          name: 'main',
          change: {
            actions: ['delete'],
            before: { engine: 'postgres' },
            after: null,
          },
        },
      ],
    };

    const runbook = parseTerraformPlan(plan);

    // Delete operation should create approval gate
    expect(runbook.approvalGates).toHaveLength(1);
    expect(runbook.approvalGates[0].metadata.riskLevel).toBe('high');
  });

  it('should create resource changes with risk levels', () => {
    const plan = {
      resource_changes: [
        {
          address: 'aws_s3_bucket.data',
          type: 'aws_s3_bucket',
          name: 'data',
          change: {
            actions: ['update'],
            before: { versioning: false },
            after: { versioning: true },
          },
        },
      ],
    };

    const runbook = parseTerraformPlan(plan);

    const step = runbook.steps[0];
    expect(step.metadata.changes).toHaveLength(1);
    expect(step.metadata.changes![0].action).toBe('update');
    expect(step.metadata.changes![0].risk).toBe('high'); // S3 bucket is high-risk
  });

  it('should skip no-op resources', () => {
    const plan = {
      resource_changes: [
        {
          address: 'aws_instance.existing',
          change: {
            actions: ['no-op'],
          },
        },
        {
          address: 'aws_instance.new',
          type: 'aws_instance',
          name: 'new',
          change: {
            actions: ['create'],
            after: {},
          },
        },
      ],
    };

    const runbook = parseTerraformPlan(plan);

    // Only the create action should generate a step
    expect(runbook.steps).toHaveLength(1);
    expect(runbook.steps[0].name).toContain('create');
  });
});

describe.skip('Runbook - Canvas Conversion', () => {
  it('should convert runbook to canvas document', () => {
    const runbook: Runbook = {
      id: 'test-runbook',
      name: 'Test Runbook',
      type: 'ansible',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'First Step',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
        {
          id: 'step-2',
          name: 'Second Step',
          type: 'task',
          dependsOn: ['step-1'],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const config = createRunbookConfig();
    const doc = runbookToCanvas(runbook, config);

    expect(doc.title).toBe('Runbook: Test Runbook');
    expect(Object.keys(doc.elements)).toHaveLength(3); // 2 nodes + 1 edge
    expect(doc.elements['step-1']).toBeDefined();
    expect(doc.elements['step-2']).toBeDefined();
  });

  it('should position steps sequentially', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 's1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
        {
          id: 's2',
          name: 'Step 2',
          type: 'task',
          dependsOn: ['s1'],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const config = createRunbookConfig({ layout: 'sequential' });
    const doc = runbookToCanvas(runbook, config);

    const s1 = doc.elements['s1'];
    const s2 = doc.elements['s2'];

    if (s1.type === 'node' && s2.type === 'node') {
      expect(s2.transform.position.y).toBeGreaterThan(s1.transform.position.y);
    }
  });

  it('should create edges for dependencies', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'a',
          name: 'A',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
        {
          id: 'b',
          name: 'B',
          type: 'task',
          dependsOn: ['a'],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const doc = runbookToCanvas(runbook, createRunbookConfig());

    const edge = doc.elements['edge-a-b'];
    expect(edge).toBeDefined();
    expect(edge.type).toBe('edge');
    
    if (edge.type === 'edge') {
      const canvasEdge = edge as import('../../types/canvas-document').CanvasEdge;
      expect(canvasEdge.sourceId).toBe('a');
      expect(canvasEdge.targetId).toBe('b');
    }
  });

  it('should include approval gate nodes', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [
        {
          id: 'critical-step',
          name: 'Delete Database',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [
        {
          id: 'approval-1',
          stepId: 'critical-step',
          approvers: ['admin'],
          requiredApprovals: 1,
          approvals: [],
          status: 'pending',
          createdAt: new Date(),
          metadata: {
            resourceChanges: [],
            impactAnalysis: 'High risk',
            riskLevel: 'high',
          },
        },
      ],
      variables: {},
      metadata: {},
    };

    const doc = runbookToCanvas(runbook, createRunbookConfig());

    const approvalNode = doc.elements['approval-approval-1'];
    expect(approvalNode).toBeDefined();
    expect(approvalNode.type).toBe('node');
  });

  it('should highlight failed steps with higher z-index', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'failed',
          name: 'Failed Step',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'failed' },
        },
        {
          id: 'success',
          name: 'Success Step',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'success' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const doc = runbookToCanvas(runbook, createRunbookConfig());

    const failedNode = doc.elements['failed'];
    const successNode = doc.elements['success'];

    expect(failedNode.zIndex).toBe(3); // Failed
    expect(successNode.zIndex).toBe(1); // Success
  });
});

describe.skip('Runbook - Step Execution', () => {
  it('should mark step as running when executed', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const mockExecutor = async () => ({ success: true });
    const updated = executeStep(runbook, 'step-1', mockExecutor);

    const step = updated.steps.find(s => s.id === 'step-1');
    expect(step?.metadata.status).toBe('running');
    expect(step?.metadata.startTime).toBeDefined();
  });

  it('should throw error if dependencies not met', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
        {
          id: 'step-2',
          name: 'Step 2',
          type: 'task',
          dependsOn: ['step-1'],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const mockExecutor = async () => ({ success: true });

    expect(() => {
      executeStep(runbook, 'step-2', mockExecutor);
    }).toThrow('Dependencies not met');
  });

  it('should complete step with success', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'running', startTime: new Date() },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const updated = completeStep(runbook, 'step-1', {
      success: true,
      output: 'Task completed successfully',
    });

    const step = updated.steps.find(s => s.id === 'step-1');
    expect(step?.metadata.status).toBe('success');
    expect(step?.metadata.endTime).toBeDefined();
    expect(step?.metadata.duration).toBeGreaterThanOrEqual(0);
    expect(step?.metadata.output).toBe('Task completed successfully');
  });

  it('should complete step with failure', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'running', startTime: new Date() },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const updated = completeStep(runbook, 'step-1', {
      success: false,
      error: 'Connection timeout',
    });

    const step = updated.steps.find(s => s.id === 'step-1');
    expect(step?.metadata.status).toBe('failed');
    expect(step?.metadata.error).toBe('Connection timeout');
  });

  it('should update runbook status when all steps complete', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'success' },
        },
        {
          id: 'step-2',
          name: 'Step 2',
          type: 'task',
          dependsOn: ['step-1'],
          metadata: { status: 'running', startTime: new Date() },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {
        completedSteps: 1,
        failedSteps: 0,
      },
    };

    const updated = completeStep(runbook, 'step-2', { success: true });

    expect(updated.metadata.status).toBe('completed');
    expect(updated.metadata.completedSteps).toBe(2);
  });
});

describe.skip('Runbook - Rollback', () => {
  it('should create rollback steps in reverse order', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Create Resource A',
          type: 'task',
          dependsOn: [],
          rollbackStep: 'delete-a',
          metadata: { status: 'success' },
        },
        {
          id: 'step-2',
          name: 'Create Resource B',
          type: 'task',
          dependsOn: ['step-1'],
          rollbackStep: 'delete-b',
          metadata: { status: 'success' },
        },
        {
          id: 'step-3',
          name: 'Failed Step',
          type: 'task',
          dependsOn: ['step-2'],
          metadata: { status: 'failed' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const rolledBack = rollbackRunbook(runbook);

    // Should have original 3 steps + 2 rollback steps (for step-2 and step-1)
    expect(rolledBack.steps.length).toBeGreaterThan(3);
    
    const rollbackSteps = rolledBack.steps.filter(s => s.id.startsWith('rollback-'));
    expect(rollbackSteps).toHaveLength(2);
    
    // Rollback should be in reverse order: step-2 before step-1
    expect(rollbackSteps[0].id).toBe('rollback-step-2');
    expect(rollbackSteps[1].id).toBe('rollback-step-1');
  });

  it('should only rollback completed steps', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'script',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          rollbackStep: 'undo-1',
          metadata: { status: 'success' },
        },
        {
          id: 'step-2',
          name: 'Step 2',
          type: 'task',
          dependsOn: ['step-1'],
          rollbackStep: 'undo-2',
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const rolledBack = rollbackRunbook(runbook);

    const rollbackSteps = rolledBack.steps.filter(s => s.id.startsWith('rollback-'));
    expect(rollbackSteps).toHaveLength(1); // Only step-1 was completed
  });
});

describe.skip('Runbook - Approval Gates', () => {
  it('should request approval for a step', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [
        {
          id: 'critical',
          name: 'Delete Production DB',
          type: 'task',
          dependsOn: [],
          metadata: { status: 'pending' },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const updated = requestApproval(runbook, 'critical', ['admin', 'dba'], 2);

    expect(updated.approvalGates).toHaveLength(1);
    expect(updated.approvalGates[0].approvers).toEqual(['admin', 'dba']);
    expect(updated.approvalGates[0].requiredApprovals).toBe(2);
    expect(updated.approvalGates[0].status).toBe('pending');
    expect(updated.metadata.status).toBe('paused');
  });

  it('should process approval and update status', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [],
      approvalGates: [
        {
          id: 'approval-1',
          stepId: 'step-1',
          approvers: ['admin'],
          requiredApprovals: 1,
          approvals: [],
          status: 'pending',
          createdAt: new Date(),
          metadata: {
            resourceChanges: [],
            impactAnalysis: 'Test',
            riskLevel: 'low',
          },
        },
      ],
      variables: {},
      metadata: { status: 'paused' },
    };

    const updated = processApproval(runbook, 'approval-1', 'admin', true, 'Approved');

    const gate = updated.approvalGates[0];
    expect(gate.approvals).toHaveLength(1);
    expect(gate.approvals[0].approved).toBe(true);
    expect(gate.approvals[0].approver).toBe('admin');
    expect(gate.status).toBe('approved');
    expect(updated.metadata.status).toBe('running');
  });

  it('should reject approval', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [],
      approvalGates: [
        {
          id: 'approval-1',
          stepId: 'step-1',
          approvers: ['admin'],
          requiredApprovals: 1,
          approvals: [],
          status: 'pending',
          createdAt: new Date(),
          metadata: {
            resourceChanges: [],
            impactAnalysis: 'Test',
            riskLevel: 'high',
          },
        },
      ],
      variables: {},
      metadata: { status: 'paused' },
    };

    const updated = processApproval(runbook, 'approval-1', 'admin', false, 'Too risky');

    const gate = updated.approvalGates[0];
    expect(gate.status).toBe('rejected');
    expect(updated.metadata.status).toBe('failed');
  });

  it('should require multiple approvals', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [],
      approvalGates: [
        {
          id: 'approval-1',
          stepId: 'step-1',
          approvers: ['admin1', 'admin2'],
          requiredApprovals: 2,
          approvals: [],
          status: 'pending',
          createdAt: new Date(),
          metadata: {
            resourceChanges: [],
            impactAnalysis: 'Test',
            riskLevel: 'critical',
          },
        },
      ],
      variables: {},
      metadata: { status: 'paused' },
    };

    // First approval
    let updated = processApproval(runbook, 'approval-1', 'admin1', true);
    expect(updated.approvalGates[0].status).toBe('pending'); // Still pending

    // Second approval
    updated = processApproval(updated, 'approval-1', 'admin2', true);
    expect(updated.approvalGates[0].status).toBe('approved'); // Now approved
  });
});

describe.skip('Runbook - Execution History', () => {
  it('should create execution history from runbook', () => {
    const runbook: Runbook = {
      id: 'test-runbook',
      name: 'Test Runbook',
      type: 'ansible',
      version: '1.0',
      steps: [
        {
          id: 'step-1',
          name: 'Step 1',
          type: 'task',
          dependsOn: [],
          metadata: {
            status: 'success',
            changes: [
              {
                resource: 'nginx',
                type: 'package',
                action: 'create',
                risk: 'low',
              },
            ],
          },
        },
      ],
      approvalGates: [],
      variables: { env: 'production' },
      metadata: {
        status: 'completed',
        executionId: 'exec-123',
        startTime: new Date('2024-01-01T10:00:00Z'),
        endTime: new Date('2024-01-01T10:05:00Z'),
        author: 'devops-team',
      },
    };

    const history = getExecutionHistory(runbook);

    expect(history.id).toBe('exec-123');
    expect(history.runbookId).toBe('test-runbook');
    expect(history.status).toBe('completed');
    expect(history.triggeredBy).toBe('devops-team');
    expect(history.changes).toHaveLength(1);
    expect(history.variables.env).toBe('production');
  });
});

describe.skip('Runbook - Analysis', () => {
  it('should analyze runbook complexity', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: Array.from({ length: 25 }, (_, i) => ({
        id: `step-${i}`,
        name: `Step ${i}`,
        type: 'task' as const,
        dependsOn: [],
        metadata: { status: 'pending' as const },
      })),
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const analysis = analyzeRunbook(runbook);

    expect(analysis.complexity).toBe('high'); // > 20 steps
    expect(analysis.estimatedDuration).toBeGreaterThan(10); // minutes
  });

  it('should detect high-risk operations', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: [
        {
          id: 'delete-db',
          name: 'Delete Database',
          type: 'task',
          dependsOn: [],
          metadata: {
            status: 'pending',
            changes: [
              {
                resource: 'aws_db_instance.main',
                type: 'aws_db_instance',
                action: 'delete',
                risk: 'high',
              },
            ],
          },
        },
      ],
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const analysis = analyzeRunbook(runbook);

    expect(analysis.risk).toBe('medium'); // Has delete operation
    expect(analysis.criticalSteps).toContain('delete-db');
    expect(analysis.recommendations).toContain(
      'Review all delete operations carefully before execution'
    );
  });

  it('should recommend approval gates for high-risk runbooks', () => {
    const runbook: Runbook = {
      id: 'test',
      name: 'Test',
      type: 'terraform',
      version: '1.0',
      steps: Array.from({ length: 5 }, (_, i) => ({
        id: `step-${i}`,
        name: `Delete Resource ${i}`,
        type: 'task' as const,
        dependsOn: [],
        metadata: {
          status: 'pending' as const,
          changes: [
            {
              resource: `resource-${i}`,
              type: 'aws_instance',
              action: 'delete' as const,
              risk: 'high' as const,
            },
          ],
        },
      })),
      approvalGates: [],
      variables: {},
      metadata: {},
    };

    const analysis = analyzeRunbook(runbook);

    expect(analysis.risk).toBe('high');
    const approvalRecommendation = analysis.recommendations.find(r =>
      r.includes('approval gates')
    );
    expect(approvalRecommendation).toBeDefined();
  });
});

describe.skip('Runbook - Step Styling', () => {
  it('should style running steps', () => {
    const step: RunbookStep = {
      id: 'step-1',
      name: 'Running Step',
      type: 'task',
      dependsOn: [],
      metadata: { status: 'running' },
    };

    const config = createRunbookConfig();
    const style = getStepStyle(step, config);

    expect(style.backgroundColor).toBe('#dbeafe');
    expect(style.borderColor).toBe('#3b82f6');
    expect(style.borderWidth).toBe(3);
  });

  it('should style successful steps', () => {
    const step: RunbookStep = {
      id: 'step-1',
      name: 'Success Step',
      type: 'task',
      dependsOn: [],
      metadata: { status: 'success' },
    };

    const style = getStepStyle(step, createRunbookConfig());

    expect(style.backgroundColor).toBe('#d1fae5');
    expect(style.borderColor).toBe('#10b981');
  });

  it('should style failed steps when highlighting enabled', () => {
    const step: RunbookStep = {
      id: 'step-1',
      name: 'Failed Step',
      type: 'task',
      dependsOn: [],
      metadata: { status: 'failed' },
    };

    const config = createRunbookConfig({ highlightFailures: true });
    const style = getStepStyle(step, config);

    expect(style.backgroundColor).toBe('#fee2e2');
    expect(style.borderColor).toBe('#ef4444');
    expect(style.borderWidth).toBe(3);
  });

  it('should style checkpoint steps', () => {
    const step: RunbookStep = {
      id: 'checkpoint',
      name: 'Checkpoint',
      type: 'checkpoint',
      dependsOn: [],
      metadata: { status: 'pending' },
    };

    const style = getStepStyle(step, createRunbookConfig());

    expect(style.borderColor).toBe('#8b5cf6');
    expect(style.backgroundColor).toBe('#f5f3ff');
  });

  it('should style approval steps', () => {
    const step: RunbookStep = {
      id: 'approval',
      name: 'Approval Gate',
      type: 'approval',
      dependsOn: [],
      metadata: { status: 'pending' },
    };

    const style = getStepStyle(step, createRunbookConfig());

    expect(style.borderColor).toBe('#f59e0b');
    expect(style.backgroundColor).toBe('#fef3c7');
  });
});
