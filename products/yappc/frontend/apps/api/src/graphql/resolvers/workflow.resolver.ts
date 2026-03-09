/**
 * Workflow GraphQL Resolvers
 *
 * @doc.type module
 * @doc.purpose GraphQL resolvers for workflow operations
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';

const prisma: PrismaClient = new Proxy({} as PrismaClient, {
    get(_target, property) {
        return (getPrismaClient() as unknown)[property];
    },
});

// Helper to create default workflow steps
function createDefaultWorkflowSteps() {
    return {
        intent: {
            status: 'NOT_STARTED',
            data: { intentType: 'FEATURE', goal: '', successCriteria: [] },
            revisitCount: 0,
        },
        context: {
            status: 'NOT_STARTED',
            data: { systemsImpacted: [], constraints: [], references: [] },
            revisitCount: 0,
        },
        plan: {
            status: 'NOT_STARTED',
            data: {
                selectedPlan: [],
                alternatives: [],
                riskAssessment: { level: 'LOW', factors: [], mitigations: [], rollbackPlan: '' },
            },
            revisitCount: 0,
        },
        execute: {
            status: 'NOT_STARTED',
            data: { changes: [], executors: [], artifacts: [] },
            revisitCount: 0,
        },
        verify: {
            status: 'NOT_STARTED',
            data: { verificationStatus: 'PENDING', evidence: [], acceptanceChecklist: [] },
            revisitCount: 0,
        },
        observe: {
            status: 'NOT_STARTED',
            data: {
                metricsDelta: { before: {}, after: {}, percentChange: {} },
                anomalies: [],
                observationWindow: { startedAt: new Date().toISOString(), durationHours: 24, status: 'ACTIVE' },
            },
            revisitCount: 0,
        },
        learn: {
            status: 'NOT_STARTED',
            data: { lessons: [], rootCauses: [] },
            revisitCount: 0,
        },
        institutionalize: {
            status: 'NOT_STARTED',
            data: { institutionalActions: [], owners: [] },
            revisitCount: 0,
        },
    };
}

// Helper to create default metrics
function createDefaultMetrics() {
    return {
        stepDurations: {},
        revisitCount: 0,
        aiSuggestionsAccepted: 0,
        aiSuggestionsRejected: 0,
        blockedCount: 0,
    };
}

// Step order for validation
const STEP_ORDER = [
    'INTENT',
    'CONTEXT',
    'PLAN',
    'EXECUTE',
    'VERIFY',
    'OBSERVE',
    'LEARN',
    'INSTITUTIONALIZE',
];

export const workflowResolvers = {
    Query: {
        workflow: async (_: unknown, { id }: { id: string }) => {
            return prisma.workflow.findUnique({
                where: { id },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });
        },

        workflows: async (
            _: unknown,
            {
                filter,
                pagination,
            }: {
                filter?: { status?: string; workflowType?: string; ownerId?: string; projectId?: string };
                pagination?: { limit?: number; offset?: number };
            }
        ) => {
            const where: Record<string, unknown> = {};
            if (filter?.status) where.status = filter.status;
            if (filter?.workflowType) where.workflowType = filter.workflowType;
            if (filter?.ownerId) where.ownerId = filter.ownerId;
            if (filter?.projectId) where.projectId = filter.projectId;

            const limit = pagination?.limit ?? 20;
            const offset = pagination?.offset ?? 0;

            const [workflows, total] = await Promise.all([
                prisma.workflow.findMany({
                    where,
                    include: { contributors: true, template: true },
                    take: limit,
                    skip: offset,
                    orderBy: { updatedAt: 'desc' },
                }),
                prisma.workflow.count({ where }),
            ]);

            return {
                workflows,
                total,
                hasMore: offset + workflows.length < total,
            };
        },

        myWorkflows: async (
            _: unknown,
            args: {
                filter?: { status?: string; workflowType?: string };
                pagination?: { limit?: number; offset?: number };
            },
            context: { userId: string }
        ) => {
            const where: Record<string, unknown> = { ownerId: context.userId };
            if (args.filter?.status) where.status = args.filter.status;
            if (args.filter?.workflowType) where.workflowType = args.filter.workflowType;

            const limit = args.pagination?.limit ?? 20;
            const offset = args.pagination?.offset ?? 0;

            const [workflows, total] = await Promise.all([
                prisma.workflow.findMany({
                    where,
                    include: { contributors: true, template: true },
                    take: limit,
                    skip: offset,
                    orderBy: { updatedAt: 'desc' },
                }),
                prisma.workflow.count({ where }),
            ]);

            return {
                workflows,
                total,
                hasMore: offset + workflows.length < total,
            };
        },

        workflowTemplates: async (_: unknown, { workflowType }: { workflowType?: string }) => {
            const where: Record<string, unknown> = {};
            if (workflowType) where.workflowType = workflowType;

            return prisma.workflowTemplate.findMany({
                where,
                orderBy: [{ isSystem: 'desc' }, { name: 'asc' }],
            });
        },

        workflowTemplate: async (_: unknown, { id }: { id: string }) => {
            return prisma.workflowTemplate.findUnique({ where: { id } });
        },
    },

    Mutation: {
        createWorkflow: async (
            _: unknown,
            {
                input,
            }: {
                input: {
                    name: string;
                    description?: string;
                    workflowType: string;
                    templateId?: string;
                    projectId?: string;
                    aiMode?: string;
                };
            },
            context: { userId: string; userName: string }
        ) => {
            // Get template if provided
            let defaultSteps = createDefaultWorkflowSteps();
            if (input.templateId) {
                const template = await prisma.workflowTemplate.findUnique({
                    where: { id: input.templateId },
                });
                if (template?.defaultIntent) {
                    defaultSteps.intent.data = {
                        ...defaultSteps.intent.data,
                        ...(template.defaultIntent as Record<string, unknown>),
                    };
                }
            }

            const workflow = await prisma.workflow.create({
                data: {
                    name: input.name,
                    description: input.description,
                    workflowType: input.workflowType as never,
                    aiMode: (input.aiMode ?? 'ASSIST') as never,
                    ownerId: context.userId,
                    ownerName: context.userName,
                    projectId: input.projectId,
                    templateId: input.templateId,
                    steps: defaultSteps,
                    metrics: createDefaultMetrics(),
                    auditEntries: {
                        create: {
                            action: 'CREATED',
                            userId: context.userId,
                            userName: context.userName,
                        },
                    },
                },
                include: {
                    contributors: true,
                    auditEntries: true,
                    template: true,
                },
            });

            return workflow;
        },

        updateWorkflow: async (
            _: unknown,
            {
                id,
                input,
            }: {
                id: string;
                input: { name?: string; description?: string; aiMode?: string; status?: string };
            },
            context: { userId: string; userName: string }
        ) => {
            const updateData: Record<string, unknown> = {};
            if (input.name !== undefined) updateData.name = input.name;
            if (input.description !== undefined) updateData.description = input.description;
            if (input.aiMode !== undefined) updateData.aiMode = input.aiMode;
            if (input.status !== undefined) updateData.status = input.status;

            const workflow = await prisma.workflow.update({
                where: { id },
                data: {
                    ...updateData,
                    auditEntries: {
                        create: {
                            action: input.status ? 'STATUS_CHANGED' : 'DATA_UPDATED',
                            userId: context.userId,
                            userName: context.userName,
                            details: input,
                        },
                    },
                },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });

            return workflow;
        },

        updateStepData: async (
            _: unknown,
            {
                workflowId,
                input,
            }: {
                workflowId: string;
                input: { step: string; data: unknown };
            },
            context: { userId: string; userName: string }
        ) => {
            const workflow = await prisma.workflow.findUnique({ where: { id: workflowId } });
            if (!workflow) throw new Error('Workflow not found');

            const steps = workflow.steps as Record<string, unknown>;
            const stepKey = input.step.toLowerCase();
            const currentStepData = steps[stepKey] as Record<string, unknown>;

            // Update step data
            steps[stepKey] = {
                ...currentStepData,
                data: input.data,
                status: currentStepData.status === 'NOT_STARTED' ? 'IN_PROGRESS' : currentStepData.status,
                startedAt: currentStepData.startedAt ?? new Date().toISOString(),
            };

            const updated = await prisma.workflow.update({
                where: { id: workflowId },
                data: {
                    steps: steps as unknown,
                    status: workflow.status === 'DRAFT' ? 'IN_PROGRESS' : workflow.status,
                    auditEntries: {
                        create: {
                            action: 'DATA_UPDATED',
                            step: input.step as never,
                            userId: context.userId,
                            userName: context.userName,
                            newValue: input.data as never,
                        },
                    },
                },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });

            return updated;
        },

        advanceStep: async (
            _: unknown,
            {
                workflowId,
                input,
            }: {
                workflowId: string;
                input: { fromStep: string; toStep: string };
            },
            context: { userId: string; userName: string }
        ) => {
            const workflow = await prisma.workflow.findUnique({ where: { id: workflowId } });
            if (!workflow) throw new Error('Workflow not found');

            // Validate step transition
            const fromIndex = STEP_ORDER.indexOf(input.fromStep);
            const toIndex = STEP_ORDER.indexOf(input.toStep);

            if (toIndex !== fromIndex + 1) {
                throw new Error('Can only advance to the next step');
            }

            const steps = workflow.steps as Record<string, unknown>;
            const fromStepKey = input.fromStep.toLowerCase();
            const toStepKey = input.toStep.toLowerCase();

            // Mark from step as completed
            const fromStepData = steps[fromStepKey] as Record<string, unknown>;
            steps[fromStepKey] = {
                ...fromStepData,
                status: 'COMPLETED',
                completedAt: new Date().toISOString(),
            };

            // Mark to step as in progress
            const toStepData = steps[toStepKey] as Record<string, unknown>;
            steps[toStepKey] = {
                ...toStepData,
                status: 'IN_PROGRESS',
                startedAt: new Date().toISOString(),
            };

            // Check if this is the final step
            const isComplete = input.toStep === 'INSTITUTIONALIZE';

            const updated = await prisma.workflow.update({
                where: { id: workflowId },
                data: {
                    currentStep: input.toStep as never,
                    steps: steps as unknown,
                    status: isComplete ? 'COMPLETED' : 'IN_PROGRESS',
                    auditEntries: {
                        createMany: {
                            data: [
                                {
                                    action: 'STEP_COMPLETED',
                                    step: input.fromStep as never,
                                    userId: context.userId,
                                    userName: context.userName,
                                },
                                {
                                    action: 'STEP_STARTED',
                                    step: input.toStep as never,
                                    userId: context.userId,
                                    userName: context.userName,
                                },
                            ],
                        },
                    },
                },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });

            return updated;
        },

        revisitStep: async (
            _: unknown,
            { workflowId, step }: { workflowId: string; step: string },
            context: { userId: string; userName: string }
        ) => {
            const workflow = await prisma.workflow.findUnique({ where: { id: workflowId } });
            if (!workflow) throw new Error('Workflow not found');

            const steps = workflow.steps as Record<string, unknown>;
            const stepKey = step.toLowerCase();
            const stepData = steps[stepKey] as Record<string, unknown>;

            // Increment revisit count
            steps[stepKey] = {
                ...stepData,
                status: 'REVISITED',
                revisitCount: ((stepData.revisitCount as number) || 0) + 1,
            };

            // Update metrics
            const metrics = workflow.metrics as Record<string, unknown>;
            metrics.revisitCount = ((metrics.revisitCount as number) || 0) + 1;

            const updated = await prisma.workflow.update({
                where: { id: workflowId },
                data: {
                    currentStep: step as never,
                    steps: steps as unknown,
                    metrics: metrics as unknown,
                    auditEntries: {
                        create: {
                            action: 'STEP_REVISITED',
                            step: step as never,
                            userId: context.userId,
                            userName: context.userName,
                        },
                    },
                },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });

            return updated;
        },

        addContributor: async (
            _: unknown,
            {
                workflowId,
                input,
            }: {
                workflowId: string;
                input: { userId: string; userName: string; role?: string };
            }
        ) => {
            await prisma.workflowContributor.create({
                data: {
                    workflowId,
                    userId: input.userId,
                    userName: input.userName,
                    role: input.role ?? 'CONTRIBUTOR',
                },
            });

            return prisma.workflow.findUnique({
                where: { id: workflowId },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });
        },

        removeContributor: async (
            _: unknown,
            { workflowId, userId }: { workflowId: string; userId: string }
        ) => {
            await prisma.workflowContributor.deleteMany({
                where: { workflowId, userId },
            });

            return prisma.workflow.findUnique({
                where: { id: workflowId },
                include: {
                    contributors: true,
                    auditEntries: { orderBy: { timestamp: 'desc' } },
                    template: true,
                },
            });
        },

        deleteWorkflow: async (_: unknown, { id }: { id: string }) => {
            await prisma.workflow.delete({ where: { id } });
            return true;
        },

        createWorkflowTemplate: async (
            _: unknown,
            {
                name,
                description,
                workflowType,
                defaultIntent,
                requiredFields,
                defaultRisks,
                defaultMetrics,
            }: {
                name: string;
                description: string;
                workflowType: string;
                defaultIntent?: unknown;
                requiredFields?: unknown;
                defaultRisks?: unknown;
                defaultMetrics?: unknown;
            }
        ) => {
            return prisma.workflowTemplate.create({
                data: {
                    name,
                    description,
                    workflowType: workflowType as never,
                    defaultIntent: defaultIntent ?? {},
                    requiredFields: requiredFields ?? {},
                    defaultRisks: defaultRisks ?? [],
                    defaultMetrics: defaultMetrics ?? [],
                },
            });
        },
    },

    // Field resolvers for nested types
    Workflow: {
        auditEntries: (parent: { id: string }) => {
            return prisma.workflowAudit.findMany({
                where: { workflowId: parent.id },
                orderBy: { timestamp: 'desc' },
            });
        },
        contributors: (parent: { id: string }) => {
            return prisma.workflowContributor.findMany({
                where: { workflowId: parent.id },
            });
        },
    },
};

export default workflowResolvers;
