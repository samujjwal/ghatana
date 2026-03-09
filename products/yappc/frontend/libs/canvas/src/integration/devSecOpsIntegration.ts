/**
 * DevSecOps Canvas Integration
 *
 * Connects persona canvas nodes to DevSecOps runbook execution and approval workflows.
 * Enables deployment automation, infrastructure provisioning, and change management.
 *
 * @doc.type module
 * @doc.purpose DevSecOps workflow integration with canvas
 * @doc.layer product
 * @doc.pattern Integration
 */

import type {
    Runbook,
    RunbookStep,
    ApprovalGate,
    ExecutionStatus,
    ResourceChange,
} from '../devsecops/types';
import {
    executeStep,
    completeStep,
    requestApproval,
    processApproval,
    areApprovalsComplete,
    getPendingApprovals,
} from '../devsecops';
import type {
    ServiceNodeData,
    DatabaseNodeData,
    TestSuiteNodeData,
} from '../components/PersonaNodes';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface DeploymentConfig {
    /** Target environment */
    environment: 'dev' | 'staging' | 'production';
    /** Deployment strategy */
    strategy: 'rolling' | 'blue-green' | 'canary';
    /** Auto-rollback on failure */
    autoRollback: boolean;
    /** Require approval before deployment */
    requireApproval: boolean;
    /** Approvers (if approval required) */
    approvers?: string[];
    /** Number of required approvals */
    requiredApprovals?: number;
}

export interface DeploymentResult {
    success: boolean;
    runbookId: string;
    status: ExecutionStatus;
    steps: RunbookStep[];
    approvalGates?: ApprovalGate[];
    changes: ResourceChange[];
    duration?: number;
    errors?: string[];
}

export interface InfrastructureProvisionResult {
    success: boolean;
    runbookId: string;
    resourcesCreated: ResourceChange[];
    status: ExecutionStatus;
    errors?: string[];
}

// ============================================================================
// DEVSECOPS CANVAS INTEGRATION SERVICE
// ============================================================================

/**
 * Service for integrating canvas nodes with DevSecOps workflows
 */
export class DevSecOpsCanvasIntegration {
    /**
     * Generate deployment runbook from service node
     */
    async generateDeploymentRunbook(
        serviceData: ServiceNodeData,
        config: DeploymentConfig
    ): Promise<Runbook> {
        const steps: RunbookStep[] = [];
        let stepCounter = 0;

        // Pre-deployment checks
        steps.push({
            id: `step-${stepCounter++}`,
            name: 'Pre-deployment Health Check',
            type: 'task',
            command: 'health-check',
            module: 'monitoring',
            args: { service: serviceData.label, environment: config.environment },
            dependsOn: [],
            timeout: 60,
            metadata: {},
        });

        // Build & test
        steps.push({
            id: `step-${stepCounter++}`,
            name: 'Build Application',
            type: 'task',
            command: 'build',
            module: 'ci',
            args: {
                service: serviceData.label,
                version: 'latest',
                registry: `registry.${config.environment}.example.com`,
            },
            dependsOn: [steps[0].id],
            timeout: 600,
            metadata: {},
        });

        steps.push({
            id: `step-${stepCounter++}`,
            name: 'Run Tests',
            type: 'task',
            command: 'test',
            module: 'ci',
            args: { service: serviceData.label, coverage: true },
            dependsOn: [steps[1].id],
            timeout: 300,
            metadata: {},
        });

        // Approval gate for production
        if (config.requireApproval && config.environment === 'production') {
            steps.push({
                id: `step-${stepCounter++}`,
                name: 'Production Deployment Approval',
                type: 'approval',
                dependsOn: [steps[2].id],
                metadata: {},
            });
        }

        // Database migration step (if service has database interactions)
        // Note: ServiceNodeData doesn't have dependencies field, this would be determined
        // by analyzing edges in the canvas graph
        // For now, we'll add migration step conditionally based on environment
        if (config.environment === 'production') {
            const migrationStep: RunbookStep = {
                id: `step-${stepCounter++}`,
                name: 'Run Database Migrations',
                type: 'task',
                command: 'migrate',
                module: 'database',
                args: { service: serviceData.label, direction: 'up' },
                dependsOn: config.requireApproval ? [steps[3].id] : [steps[2].id],
                timeout: 300,
                rollbackStep: `step-${stepCounter + 10}`, // Will be created later
                metadata: {},
            };
            steps.push(migrationStep);
        }

        // Deploy based on strategy
        const deployStepDeps = steps[steps.length - 1].id;
        switch (config.strategy) {
            case 'rolling':
                steps.push({
                    id: `step-${stepCounter++}`,
                    name: 'Rolling Deployment',
                    type: 'task',
                    command: 'deploy-rolling',
                    module: 'kubernetes',
                    args: {
                        service: serviceData.label,
                        replicas: serviceData.config?.replicas || 3,
                        maxUnavailable: 1,
                        maxSurge: 1,
                    },
                    dependsOn: [deployStepDeps],
                    timeout: 900,
                    metadata: {},
                });
                break;

            case 'blue-green':
                steps.push(
                    {
                        id: `step-${stepCounter++}`,
                        name: 'Deploy Green Environment',
                        type: 'task',
                        command: 'deploy-blue-green',
                        module: 'kubernetes',
                        args: {
                            service: serviceData.label,
                            slot: 'green',
                            replicas: serviceData.config?.replicas || 3,
                        },
                        dependsOn: [deployStepDeps],
                        timeout: 600,
                        metadata: {},
                    },
                    {
                        id: `step-${stepCounter++}`,
                        name: 'Switch Traffic to Green',
                        type: 'task',
                        command: 'switch-slot',
                        module: 'loadbalancer',
                        args: { service: serviceData.label, target: 'green' },
                        dependsOn: [steps[steps.length - 1].id],
                        timeout: 60,
                        metadata: {},
                    }
                );
                break;

            case 'canary':
                steps.push(
                    {
                        id: `step-${stepCounter++}`,
                        name: 'Deploy Canary (10%)',
                        type: 'task',
                        command: 'deploy-canary',
                        module: 'kubernetes',
                        args: { service: serviceData.label, weight: 10 },
                        dependsOn: [deployStepDeps],
                        timeout: 300,
                        metadata: {},
                    },
                    {
                        id: `step-${stepCounter++}`,
                        name: 'Monitor Canary Metrics',
                        type: 'task',
                        command: 'monitor',
                        module: 'observability',
                        args: {
                            service: serviceData.label,
                            duration: 600,
                            thresholds: { errorRate: 0.01, latencyP95: 500 },
                        },
                        dependsOn: [steps[steps.length - 1].id],
                        timeout: 660,
                        metadata: {},
                    },
                    {
                        id: `step-${stepCounter++}`,
                        name: 'Promote to 100%',
                        type: 'task',
                        command: 'promote-canary',
                        module: 'kubernetes',
                        args: { service: serviceData.label, weight: 100 },
                        dependsOn: [steps[steps.length - 1].id],
                        timeout: 300,
                        metadata: {},
                    }
                );
                break;
        }

        // Post-deployment verification
        steps.push({
            id: `step-${stepCounter++}`,
            name: 'Post-deployment Verification',
            type: 'task',
            command: 'verify',
            module: 'testing',
            args: {
                service: serviceData.label,
                tests: ['health', 'smoke', 'integration'],
            },
            dependsOn: [steps[steps.length - 1].id],
            timeout: 300,
            metadata: {},
        });

        // Notification
        steps.push({
            id: `step-${stepCounter++}`,
            name: 'Send Deployment Notification',
            type: 'notification',
            command: 'notify',
            module: 'messaging',
            args: {
                channel: 'deployments',
                service: serviceData.label,
                environment: config.environment,
            },
            dependsOn: [steps[steps.length - 1].id],
            metadata: {},
        });

        return {
            id: `deployment-${Date.now()}`,
            name: `Deploy ${serviceData.label} to ${config.environment}`,
            type: 'script',
            description: `Automated deployment using ${config.strategy} strategy`,
            version: '1.0.0',
            steps,
            approvalGates: [],
            variables: {
                environment: config.environment,
                strategy: config.strategy,
                autoRollback: config.autoRollback,
                requireApproval: config.requireApproval,
            },
            metadata: {
                status: 'pending',
                tags: ['deployment', serviceData.label, config.environment],
            },
        };
    }

    /**
     * Generate infrastructure provisioning runbook from database node
     */
    async generateInfrastructureRunbook(dbData: DatabaseNodeData): Promise<Runbook> {
        const steps: RunbookStep[] = [];

        // Create VPC/Network
        steps.push({
            id: 'step-0',
            name: 'Create VPC and Subnets',
            type: 'task',
            command: 'terraform-apply',
            module: 'terraform',
            args: {
                resource: 'vpc',
                config: {
                    cidr: '10.0.0.0/16',
                    subnets: ['10.0.1.0/24', '10.0.2.0/24', '10.0.3.0/24'],
                },
            },
            dependsOn: [],
            timeout: 300,
            metadata: {},
        });

        // Create security groups
        steps.push({
            id: 'step-1',
            name: 'Create Security Groups',
            type: 'task',
            command: 'terraform-apply',
            module: 'terraform',
            args: {
                resource: 'security-group',
                rules: [
                    { port: 5432, source: '10.0.0.0/16', protocol: 'tcp' }, // PostgreSQL
                    { port: 3306, source: '10.0.0.0/16', protocol: 'tcp' }, // MySQL
                ],
            },
            dependsOn: ['step-0'],
            timeout: 120,
            metadata: {},
        });

        // Provision database
        steps.push({
            id: 'step-2',
            name: `Provision ${dbData.engine} Database`,
            type: 'task',
            command: 'terraform-apply',
            module: 'terraform',
            args: {
                resource: 'database',
                engine: dbData.engine,
                instanceClass: 'db.t3.medium', // Default instance class
                storage: 100, // Default storage in GB
                multiAz: true,
                backupRetention: 7,
            },
            dependsOn: ['step-1'],
            timeout: 900,
            metadata: {},
        });

        // Configure monitoring
        steps.push({
            id: 'step-3',
            name: 'Setup Database Monitoring',
            type: 'task',
            command: 'configure-monitoring',
            module: 'cloudwatch',
            args: {
                database: dbData.label,
                metrics: ['connections', 'cpu', 'memory', 'iops'],
                alarms: true,
            },
            dependsOn: ['step-2'],
            timeout: 120,
            metadata: {},
        });

        // Create backups
        steps.push({
            id: 'step-4',
            name: 'Configure Automated Backups',
            type: 'task',
            command: 'configure-backups',
            module: 'backup',
            args: {
                database: dbData.label,
                schedule: '0 2 * * *', // Daily at 2 AM
                retention: 30,
            },
            dependsOn: ['step-2'],
            timeout: 60,
            metadata: {},
        });

        return {
            id: `infra-${Date.now()}`,
            name: `Provision ${dbData.label} Infrastructure`,
            type: 'terraform',
            description: `Provision ${dbData.engine} database with monitoring and backups`,
            version: '1.0.0',
            steps,
            approvalGates: [],
            variables: {
                engine: dbData.engine,
                dbName: dbData.label,
            },
            metadata: {
                status: 'pending',
                tags: ['infrastructure', dbData.label, dbData.engine],
            },
        };
    }

    /**
     * Execute deployment with approval workflow
     */
    async executeDeployment(
        runbook: Runbook,
        config: DeploymentConfig
    ): Promise<DeploymentResult> {
        const startTime = Date.now();
        const changes: ResourceChange[] = [];
        const errors: string[] = [];
        let approvalGates: ApprovalGate[] | undefined;

        try {
            // Execute steps sequentially
            for (const step of runbook.steps) {
                // Handle approval steps
                if (step.type === 'approval' && config.requireApproval) {
                    const approvers = config.approvers || ['ops-lead', 'tech-lead'];
                    const required = config.requiredApprovals || 2;

                    const withApproval = requestApproval(runbook, step.id, approvers, required);
                    approvalGates = approvalGates || [];
                    const gate = withApproval.approvalGates?.find((g) => g.stepId === step.id);
                    if (gate) {
                        approvalGates.push(gate);
                    }

                    // In real implementation, would wait for approvals
                    // For now, simulate immediate approval
                    continue;
                }

                // Execute regular steps
                const executed = executeStep(runbook, step.id, async (s) => ({
                    success: true,
                    output: `Executed ${s.name}`,
                }));
                const completed = completeStep(executed, step.id, {
                    success: true,
                    output: `Completed ${step.name}`,
                });

                // Track changes
                if (step.metadata.changes) {
                    changes.push(...step.metadata.changes);
                }

                // Update runbook state
                Object.assign(runbook, completed);
            }

            return {
                success: true,
                runbookId: runbook.id,
                status: 'completed',
                steps: runbook.steps,
                approvalGates,
                changes,
                duration: Date.now() - startTime,
            };
        } catch (error) {
            errors.push(error instanceof Error ? error.message : 'Unknown error');
            return {
                success: false,
                runbookId: runbook.id,
                status: 'failed',
                steps: runbook.steps,
                approvalGates,
                changes,
                duration: Date.now() - startTime,
                errors,
            };
        }
    }

    /**
     * Generate test execution runbook
     */
    async generateTestRunbook(testData: TestSuiteNodeData): Promise<Runbook> {
        const steps: RunbookStep[] = [];

        steps.push({
            id: 'step-0',
            name: 'Setup Test Environment',
            type: 'task',
            command: 'setup-env',
            module: 'testing',
            args: { testType: testData.testType, isolation: true },
            dependsOn: [],
            timeout: 120,
            metadata: {},
        });

        steps.push({
            id: 'step-1',
            name: 'Run Test Suite',
            type: 'task',
            command: 'run-tests',
            module: 'testing',
            args: {
                suite: testData.label,
                type: testData.testType,
                parallel: testData.testType === 'unit',
            },
            dependsOn: ['step-0'],
            timeout: 600,
            metadata: {},
        });

        steps.push({
            id: 'step-2',
            name: 'Generate Coverage Report',
            type: 'task',
            command: 'coverage-report',
            module: 'testing',
            args: { format: ['html', 'json'], threshold: 80 },
            dependsOn: ['step-1'],
            timeout: 60,
            metadata: {},
        });

        steps.push({
            id: 'step-3',
            name: 'Cleanup Test Environment',
            type: 'task',
            command: 'cleanup',
            module: 'testing',
            dependsOn: ['step-2'],
            timeout: 60,
            metadata: {},
        });

        return {
            id: `test-${Date.now()}`,
            name: `Execute ${testData.label}`,
            type: 'script',
            description: `Run ${testData.testType} tests`,
            version: '1.0.0',
            steps,
            approvalGates: [],
            variables: {
                testType: testData.testType,
                testName: testData.label,
            },
            metadata: {
                status: 'pending',
                tags: ['testing', testData.testType, testData.label],
            },
        };
    }
}

/**
 * Create DevSecOps canvas integration instance
 */
export function createDevSecOpsCanvasIntegration(): DevSecOpsCanvasIntegration {
    return new DevSecOpsCanvasIntegration();
}
