/**
 * DevSecOps Canvas Integration Tests
 * 
 * Tests for DevSecOps runbook generation and execution from canvas nodes.
 * Verifies deployment strategies, infrastructure provisioning, and approval workflows.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
    DevSecOpsCanvasIntegration,
    createDevSecOpsCanvasIntegration,
    type DeploymentConfig,
} from '../devSecOpsIntegration';
import type { ServiceNodeData, DatabaseNodeData, TestSuiteNodeData } from '../../components/PersonaNodes';

describe('DevSecOpsCanvasIntegration', () => {
    let integration: DevSecOpsCanvasIntegration;

    beforeEach(() => {
        integration = createDevSecOpsCanvasIntegration();
    });

    describe('generateDeploymentRunbook', () => {
        it('should generate rolling deployment runbook', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'UserService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
                config: { port: 3000, replicas: 3, cpu: '500m', memory: '512Mi' },
            };

            const config: DeploymentConfig = {
                environment: 'production',
                strategy: 'rolling',
                autoRollback: true,
                requireApproval: true,
                approvers: ['tech-lead', 'devops-team'],
                requiredApprovals: 2,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            expect(runbook).toBeDefined();
            expect(runbook.name).toContain('UserService');
            expect(runbook.name).toContain('production');
            expect(runbook.type).toBe('script');
            expect(runbook.steps.length).toBeGreaterThan(0);

            // Verify key deployment steps
            const stepNames = runbook.steps.map(s => s.name);
            expect(stepNames).toContain('Pre-deployment Health Check');
            expect(stepNames).toContain('Build Application');
            expect(stepNames).toContain('Run Tests');
            expect(stepNames.some(name => name.includes('Deployment'))).toBe(true);
        });

        it('should generate blue-green deployment runbook', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'APIService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
                config: { port: 8080, replicas: 2, cpu: '250m', memory: '256Mi' },
            };

            const config: DeploymentConfig = {
                environment: 'staging',
                strategy: 'blue-green',
                autoRollback: true,
                requireApproval: false,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            expect(runbook.steps.some(s => s.name.includes('Green Environment'))).toBe(true);
            expect(runbook.steps.some(s => s.name.includes('Switch Traffic'))).toBe(true);
        });

        it('should generate canary deployment runbook', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'PaymentService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'production',
                strategy: 'canary',
                autoRollback: true,
                requireApproval: true,
                approvers: ['sre-team'],
                requiredApprovals: 1,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            expect(runbook.steps.some(s => s.name.includes('Canary'))).toBe(true);
            expect(runbook.steps.some(s => s.name.includes('Monitor'))).toBe(true);
        });

        it('should include approval gates for production', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'CriticalService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'production',
                strategy: 'rolling',
                autoRollback: true,
                requireApproval: true,
                approvers: ['cto', 'sre-lead'],
                requiredApprovals: 2,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            const approvalSteps = runbook.steps.filter(s => s.type === 'approval');
            expect(approvalSteps.length).toBeGreaterThan(0);
            expect(approvalSteps[0].name).toContain('Approval');
        });

        it('should skip approval for non-production environments', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'TestService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'dev',
                strategy: 'rolling',
                autoRollback: false,
                requireApproval: false,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            const approvalSteps = runbook.steps.filter(s => s.type === 'approval');
            expect(approvalSteps.length).toBe(0);
        });
    });

    describe('generateInfrastructureRunbook', () => {
        it('should generate database provisioning runbook', async () => {
            const dbNode: DatabaseNodeData = {
                label: 'ProductDB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        {
                            name: 'products',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'name', type: 'varchar', nullable: false },
                            ],
                        },
                    ],
                },
            };

            const runbook = await integration.generateInfrastructureRunbook(dbNode);

            expect(runbook.name).toContain('ProductDB');
            expect(runbook.type).toBe('terraform');
            expect(runbook.steps.length).toBeGreaterThan(0);

            const stepNames = runbook.steps.map(s => s.name);
            expect(stepNames.some(name => name.includes('VPC'))).toBe(true);
            expect(stepNames.some(name => name.includes('postgres'))).toBe(true);
            expect(stepNames.some(name => name.includes('Monitoring'))).toBe(true);
        });

        it('should support different database engines', async () => {
            const engines: Array<'postgres' | 'mysql' | 'mongodb' | 'redis' | 'dynamodb'> = [
                'postgres',
                'mysql',
                'mongodb',
            ];

            for (const engine of engines) {
                const dbNode: DatabaseNodeData = {
                    label: `${engine}DB`,
                    type: 'database',
                    persona: 'architect',
                    engine,
                    schema: { tables: [] },
                };

                const runbook = await integration.generateInfrastructureRunbook(dbNode);

                expect(runbook.name).toContain(engine);
                expect(runbook.steps.some(s => s.name.includes(engine))).toBe(true);
            }
        });

        it('should include backup configuration', async () => {
            const dbNode: DatabaseNodeData = {
                label: 'BackupDB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
            };

            const runbook = await integration.generateInfrastructureRunbook(dbNode);

            expect(runbook.steps.some(s => s.name.includes('Backup'))).toBe(true);
        });
    });

    describe('generateTestRunbook', () => {
        it('should generate test execution runbook', async () => {
            const testNode: TestSuiteNodeData = {
                label: 'API Integration Tests',
                type: 'testSuite',
                persona: 'qa',
                testType: 'integration',
                tests: [
                    { name: 'Test user creation', status: 'passing', description: '' },
                    { name: 'Test authentication', status: 'passing', description: '' },
                ],
                coverage: 85,
            };

            const runbook = await integration.generateTestRunbook(testNode);

            expect(runbook.name).toContain('API Integration Tests');
            expect(runbook.type).toBe('script');
            expect(runbook.steps.length).toBeGreaterThan(0);

            const stepNames = runbook.steps.map(s => s.name);
            expect(stepNames).toContain('Setup Test Environment');
            expect(stepNames.some(name => name.includes('Test'))).toBe(true);
            expect(stepNames).toContain('Cleanup Test Environment');
        });

        it('should handle different test types', async () => {
            const testTypes: Array<'unit' | 'integration' | 'e2e'> = [
                'unit',
                'integration',
                'e2e',
            ];

            for (const testType of testTypes) {
                const testNode: TestSuiteNodeData = {
                    label: `${testType} Tests`,
                    type: 'testSuite',
                    persona: 'qa',
                    testType,
                    tests: [],
                    coverage: 90,
                };

                const runbook = await integration.generateTestRunbook(testNode);

                expect(runbook.name).toContain(testType);
            }
        });
    });

    describe('runbook structure', () => {
        it('should have proper runbook metadata', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'TestService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'staging',
                strategy: 'rolling',
                autoRollback: false,
                requireApproval: false,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            expect(runbook.id).toBeDefined();
            expect(runbook.name).toBeDefined();
            expect(runbook.version).toBe('1.0.0');
            expect(runbook.steps).toBeDefined();
            expect(runbook.approvalGates).toBeDefined();
            expect(runbook.variables).toBeDefined();
            expect(runbook.metadata).toBeDefined();
            expect(runbook.metadata.status).toBe('pending');
        });

        it('should have valid step dependencies', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'DepService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'production',
                strategy: 'rolling',
                autoRollback: true,
                requireApproval: false,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            // Verify each step's dependencies reference valid step IDs
            const stepIds = new Set(runbook.steps.map(s => s.id));

            for (const step of runbook.steps) {
                for (const depId of step.dependsOn) {
                    expect(stepIds.has(depId)).toBe(true);
                }
            }
        });
    });

    describe('deployment variables', () => {
        it('should store deployment config in variables', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'VarService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const config: DeploymentConfig = {
                environment: 'staging',
                strategy: 'blue-green',
                autoRollback: true,
                requireApproval: false,
            };

            const runbook = await integration.generateDeploymentRunbook(serviceNode, config);

            expect(runbook.variables.environment).toBe('staging');
            expect(runbook.variables.strategy).toBe('blue-green');
            expect(runbook.variables.autoRollback).toBe(true);
        });
    });
});
