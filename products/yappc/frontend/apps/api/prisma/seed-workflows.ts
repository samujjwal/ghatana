/**
 * Prisma Seed Script - Workflow Data
 *
 * Seeds the database with sample workflows and templates
 *
 * @doc.type script
 * @doc.purpose Seed workflow data for development/testing
 * @doc.layer data
 * @doc.pattern Seed Script
 *
 * Run with: npx prisma db seed
 */

import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

// ============================================================================
// SAMPLE DATA
// ============================================================================

const workflowTemplates = [
    {
        name: 'Bug Fix Template',
        description: 'Standard template for bug fixes with root cause analysis',
        type: 'BUG_FIX',
        defaultAIMode: 'AI_ASSISTED',
        category: 'Engineering',
        steps: {
            intent: {
                data: {
                    workflowType: 'BUG_FIX',
                    successCriteria: ['Bug is reproducible', 'Fix verified in staging', 'No regression introduced'],
                },
            },
            context: {
                data: {
                    systems: ['affected-service'],
                    constraints: ['Minimize downtime', 'Backward compatible'],
                },
            },
            plan: {
                data: {
                    hasRollbackPlan: true,
                },
            },
        },
        isActive: true,
        version: 1,
        createdBy: 'system',
    },
    {
        name: 'Feature Development Template',
        description: 'Template for new feature implementation with full lifecycle',
        type: 'FEATURE',
        defaultAIMode: 'AI_ASSISTED',
        category: 'Engineering',
        steps: {
            intent: {
                data: {
                    workflowType: 'FEATURE',
                    successCriteria: [
                        'Feature meets acceptance criteria',
                        'Unit tests pass',
                        'Integration tests pass',
                        'Documentation updated',
                    ],
                },
            },
            context: {
                data: {
                    constraints: ['API backward compatible', 'Performance within SLA'],
                },
            },
        },
        isActive: true,
        version: 1,
        createdBy: 'system',
    },
    {
        name: 'Incident Response Template',
        description: 'Template for production incident response and resolution',
        type: 'INCIDENT',
        defaultAIMode: 'AI_AUTONOMOUS',
        category: 'Operations',
        steps: {
            intent: {
                data: {
                    workflowType: 'INCIDENT',
                    successCriteria: ['Service restored', 'Root cause identified', 'Post-mortem completed'],
                },
            },
            context: {
                data: {
                    constraints: ['Minimize MTTR', 'Keep stakeholders informed'],
                },
            },
        },
        isActive: true,
        version: 1,
        createdBy: 'system',
    },
    {
        name: 'Release Template',
        description: 'Standard release workflow with verification gates',
        type: 'RELEASE',
        defaultAIMode: 'AI_ASSISTED',
        category: 'Operations',
        steps: {
            intent: {
                data: {
                    workflowType: 'RELEASE',
                    successCriteria: [
                        'All tests pass',
                        'Change log updated',
                        'Stakeholders notified',
                        'Monitoring verified',
                    ],
                },
            },
        },
        isActive: true,
        version: 1,
        createdBy: 'system',
    },
    {
        name: 'Security Patch Template',
        description: 'Template for security vulnerability fixes',
        type: 'SECURITY',
        defaultAIMode: 'HUMAN_ONLY',
        category: 'Security',
        steps: {
            intent: {
                data: {
                    workflowType: 'SECURITY',
                    successCriteria: [
                        'Vulnerability patched',
                        'Security scan passes',
                        'No new vulnerabilities introduced',
                    ],
                },
            },
            context: {
                data: {
                    constraints: ['Confidential handling', 'Expedited review'],
                },
            },
        },
        isActive: true,
        version: 1,
        createdBy: 'system',
    },
];

const sampleWorkflows = [
    {
        title: 'Fix login timeout issue',
        type: 'BUG_FIX',
        status: 'ACTIVE',
        currentStep: 'EXECUTE',
        aiMode: 'AI_ASSISTED',
        priority: 'HIGH',
        steps: {
            intent: {
                status: 'COMPLETED',
                data: {
                    workflowType: 'BUG_FIX',
                    goalStatement: 'Fix the login timeout issue causing users to be logged out after 5 minutes',
                    successCriteria: [
                        'Session timeout increased to 30 minutes',
                        'Token refresh mechanism working',
                        'No regression in auth flow',
                    ],
                },
                aiConfidence: 0.92,
                completedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
            },
            context: {
                status: 'COMPLETED',
                data: {
                    systems: ['auth-service', 'api-gateway', 'user-session-store'],
                    constraints: ['Zero downtime deployment', 'Token backward compatibility'],
                    references: ['https://docs.example.com/auth-architecture'],
                },
                aiConfidence: 0.88,
                completedAt: new Date(Date.now() - 86400000).toISOString(),
            },
            plan: {
                status: 'COMPLETED',
                data: {
                    tasks: [
                        { id: 't1', title: 'Update session timeout config', status: 'DONE', assignee: 'alice' },
                        { id: 't2', title: 'Implement token refresh', status: 'DONE', assignee: 'bob' },
                        { id: 't3', title: 'Add monitoring alerts', status: 'IN_PROGRESS', assignee: 'carol' },
                    ],
                    riskAssessment: 'LOW',
                    hasRollbackPlan: true,
                    rollbackPlan: 'Revert config changes via feature flag',
                },
                aiConfidence: 0.85,
                completedAt: new Date(Date.now() - 43200000).toISOString(),
            },
            execute: {
                status: 'IN_PROGRESS',
                data: {
                    changes: [
                        {
                            id: 'c1',
                            description: 'Update session timeout to 30 minutes',
                            status: 'MERGED',
                            prUrl: 'https://github.com/example/pr/123',
                        },
                        {
                            id: 'c2',
                            description: 'Implement token refresh endpoint',
                            status: 'IN_REVIEW',
                            prUrl: 'https://github.com/example/pr/124',
                        },
                    ],
                    progress: 65,
                },
                startedAt: new Date(Date.now() - 21600000).toISOString(),
            },
            verify: { status: 'NOT_STARTED', data: {} },
            observe: { status: 'NOT_STARTED', data: {} },
            learn: { status: 'NOT_STARTED', data: {} },
            institutionalize: { status: 'NOT_STARTED', data: {} },
        },
        metrics: {
            startTime: new Date(Date.now() - 86400000 * 3).toISOString(),
            stepDurations: {
                INTENT: 3600000,
                CONTEXT: 7200000,
                PLAN: 10800000,
            },
        },
        createdBy: 'alice',
    },
    {
        title: 'Implement user dashboard v2',
        type: 'FEATURE',
        status: 'ACTIVE',
        currentStep: 'PLAN',
        aiMode: 'AI_ASSISTED',
        priority: 'MEDIUM',
        steps: {
            intent: {
                status: 'COMPLETED',
                data: {
                    workflowType: 'FEATURE',
                    goalStatement: 'Build a new user dashboard with real-time analytics and customizable widgets',
                    successCriteria: [
                        'Dashboard loads in under 2 seconds',
                        'Users can customize widget layout',
                        'Real-time data updates every 30 seconds',
                        'Mobile responsive design',
                    ],
                },
                aiConfidence: 0.95,
                completedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
            },
            context: {
                status: 'COMPLETED',
                data: {
                    systems: ['dashboard-service', 'analytics-api', 'widget-engine', 'websocket-server'],
                    constraints: ['Must work on IE11', 'Accessibility AA compliant', 'CDN-friendly assets'],
                    references: [
                        'https://figma.com/file/dashboard-v2-designs',
                        'https://docs.example.com/widget-api',
                    ],
                },
                aiConfidence: 0.91,
                completedAt: new Date(Date.now() - 86400000 * 3).toISOString(),
            },
            plan: {
                status: 'IN_PROGRESS',
                data: {
                    tasks: [
                        { id: 't1', title: 'Setup dashboard service scaffold', status: 'DONE', assignee: 'david' },
                        { id: 't2', title: 'Implement widget framework', status: 'IN_PROGRESS', assignee: 'eve' },
                        { id: 't3', title: 'Build analytics integration', status: 'TODO', assignee: 'frank' },
                        { id: 't4', title: 'Create widget library', status: 'TODO' },
                        { id: 't5', title: 'Implement drag-and-drop layout', status: 'TODO' },
                    ],
                    riskAssessment: 'MEDIUM',
                    hasRollbackPlan: true,
                    rollbackPlan: 'Feature flag to switch back to v1 dashboard',
                },
                startedAt: new Date(Date.now() - 86400000).toISOString(),
            },
            execute: { status: 'NOT_STARTED', data: {} },
            verify: { status: 'NOT_STARTED', data: {} },
            observe: { status: 'NOT_STARTED', data: {} },
            learn: { status: 'NOT_STARTED', data: {} },
            institutionalize: { status: 'NOT_STARTED', data: {} },
        },
        metrics: {
            startTime: new Date(Date.now() - 86400000 * 5).toISOString(),
            stepDurations: {
                INTENT: 7200000,
                CONTEXT: 14400000,
            },
        },
        createdBy: 'david',
    },
    {
        title: 'Production database outage - 2024-01-15',
        type: 'INCIDENT',
        status: 'COMPLETED',
        currentStep: 'INSTITUTIONALIZE',
        aiMode: 'AI_AUTONOMOUS',
        priority: 'CRITICAL',
        steps: {
            intent: {
                status: 'COMPLETED',
                data: {
                    workflowType: 'INCIDENT',
                    goalStatement: 'Restore database connectivity and prevent recurrence of connection pool exhaustion',
                    successCriteria: [
                        'Database connectivity restored',
                        'Connection pool properly configured',
                        'Monitoring alerts added',
                        'Post-mortem completed',
                    ],
                },
                aiConfidence: 0.98,
                completedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
            },
            context: {
                status: 'COMPLETED',
                data: {
                    systems: ['primary-db', 'replica-db', 'connection-pooler', 'all-backend-services'],
                    constraints: ['Immediate action required', 'Customer impact ongoing'],
                },
                aiConfidence: 0.96,
                completedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
            },
            plan: {
                status: 'COMPLETED',
                data: {
                    tasks: [
                        { id: 't1', title: 'Restart connection pooler', status: 'DONE', assignee: 'on-call' },
                        { id: 't2', title: 'Scale up DB replicas', status: 'DONE', assignee: 'on-call' },
                        { id: 't3', title: 'Investigate root cause', status: 'DONE', assignee: 'bob' },
                        { id: 't4', title: 'Implement fix', status: 'DONE', assignee: 'alice' },
                    ],
                    riskAssessment: 'CRITICAL',
                    hasRollbackPlan: true,
                },
                aiConfidence: 0.94,
                completedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
            },
            execute: {
                status: 'COMPLETED',
                data: {
                    changes: [
                        { id: 'c1', description: 'Increased connection pool size', status: 'MERGED' },
                        { id: 'c2', description: 'Added connection timeout', status: 'MERGED' },
                        { id: 'c3', description: 'Implemented circuit breaker', status: 'MERGED' },
                    ],
                    progress: 100,
                },
                completedAt: new Date(Date.now() - 86400000 * 9).toISOString(),
            },
            verify: {
                status: 'COMPLETED',
                data: {
                    acceptanceChecklist: [
                        { id: 'a1', description: 'Database connections stable', checked: true },
                        { id: 'a2', description: 'No timeout errors in logs', checked: true },
                        { id: 'a3', description: 'Load test passes', checked: true },
                    ],
                    evidence: ['Load test results showing 10k concurrent connections'],
                },
                completedAt: new Date(Date.now() - 86400000 * 8).toISOString(),
            },
            observe: {
                status: 'COMPLETED',
                data: {
                    observationWindow: 168,
                    metrics: {
                        before: { errorRate: 45, p99Latency: 5000, availability: 85 },
                        after: { errorRate: 0.1, p99Latency: 150, availability: 99.99 },
                    },
                    anomalies: [],
                },
                completedAt: new Date(Date.now() - 86400000 * 7).toISOString(),
            },
            learn: {
                status: 'COMPLETED',
                data: {
                    lessons: [
                        { id: 'l1', category: 'WHAT_DIDNT', description: 'Monitoring did not alert until users reported', actionable: true },
                        { id: 'l2', category: 'WHAT_WORKED', description: 'Runbook for DB issues was helpful', actionable: false },
                        { id: 'l3', category: 'IMPROVEMENT', description: 'Need connection pool monitoring', actionable: true },
                    ],
                    rootCauses: [
                        {
                            id: 'r1',
                            category: 'TECHNOLOGY',
                            description: 'Connection pool max size too low for peak load',
                            contributingFactors: ['Black Friday traffic spike', 'No auto-scaling for connections'],
                        },
                    ],
                },
                completedAt: new Date(Date.now() - 86400000 * 6).toISOString(),
            },
            institutionalize: {
                status: 'IN_PROGRESS',
                data: {
                    actions: [
                        {
                            id: 'ia1',
                            type: 'CHECKLIST',
                            title: 'Add connection pool check to deployment checklist',
                            owner: 'alice',
                            enforcementLevel: 75,
                            status: 'APPROVED',
                            approvers: ['bob', 'carol'],
                        },
                        {
                            id: 'ia2',
                            type: 'RUNBOOK',
                            title: 'Update DB incident runbook with connection pool section',
                            owner: 'bob',
                            enforcementLevel: 100,
                            status: 'PENDING',
                            approvers: ['alice'],
                        },
                    ],
                },
                startedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
            },
        },
        metrics: {
            startTime: new Date(Date.now() - 86400000 * 10).toISOString(),
            endTime: new Date(Date.now() - 86400000 * 5).toISOString(),
            totalDuration: 86400000 * 5,
            stepDurations: {
                INTENT: 1800000,
                CONTEXT: 900000,
                PLAN: 3600000,
                EXECUTE: 86400000,
                VERIFY: 43200000,
                OBSERVE: 604800000,
                LEARN: 86400000,
            },
        },
        createdBy: 'on-call',
    },
];

// ============================================================================
// SEED FUNCTION
// ============================================================================

async function main() {
    console.log('🌱 Starting workflow seed...');

    // Seed workflow templates
    console.log('📝 Seeding workflow templates...');
    for (const template of workflowTemplates) {
        await prisma.workflowTemplate.upsert({
            where: {
                name_version: {
                    name: template.name,
                    version: template.version,
                },
            },
            update: template,
            create: template,
        });
    }
    console.log(`   ✅ Created ${workflowTemplates.length} templates`);

    // Seed sample workflows
    console.log('📋 Seeding sample workflows...');
    for (const workflow of sampleWorkflows) {
        const created = await prisma.workflow.create({
            data: workflow,
        });

        // Add some audit entries for completed workflows
        if (workflow.status === 'COMPLETED' || workflow.currentStep !== 'INTENT') {
            await prisma.workflowAudit.createMany({
                data: [
                    {
                        workflowId: created.id,
                        step: 'INTENT',
                        action: 'STEP_COMPLETED',
                        actor: workflow.createdBy,
                        details: { aiConfidence: workflow.steps.intent.aiConfidence },
                    },
                    {
                        workflowId: created.id,
                        step: 'CONTEXT',
                        action: 'STEP_COMPLETED',
                        actor: workflow.createdBy,
                        details: { aiConfidence: workflow.steps.context.aiConfidence },
                    },
                ],
            });
        }

        // Add contributors
        await prisma.workflowContributor.create({
            data: {
                workflowId: created.id,
                userId: workflow.createdBy,
                role: 'OWNER',
            },
        });
    }
    console.log(`   ✅ Created ${sampleWorkflows.length} workflows`);

    console.log('');
    console.log('🎉 Workflow seed completed successfully!');
}

main()
    .catch((e) => {
        console.error('❌ Seed failed:', e);
        process.exit(1);
    })
    .finally(async () => {
        await prisma.$disconnect();
    });
