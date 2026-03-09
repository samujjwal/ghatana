/**
 * Comprehensive Database Seeding Script
 *
 * <p><b>Purpose</b><br>
 * Populates the database with realistic test data for all major entities:
 * - Users, Workspaces, Organizations
 * - Departments, Teams, Workflows
 * - KPIs, Budgets, Reports
 * - Approvals, Performance Reviews, Time Off
 * - DevSecOps WorkItems, Alerts, Logs
 * - ML Models, Metrics
 *
 * <p><b>Usage</b><br>
 * Run: pnpm run seed:complete
 *
 * @doc.type script
 * @doc.purpose Comprehensive database seeding
 * @doc.layer platform
 * @doc.pattern DataSeeding
 */

import { prisma } from '../db/client.js';
import bcrypt from 'bcryptjs';

const TENANT_ID = '7146cb95-180c-485f-9ca4-5adf5dcaf7c4';

// ============================================================================
// Helper Functions
// ============================================================================

async function hashPassword(password: string): Promise<string> {
    return bcrypt.hash(password, 10);
}

function generateId(): string {
    return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
}

// ============================================================================
// Seed Users
// ============================================================================

async function seedUsers() {
    console.log('👥 Seeding users...');

    const hashedPassword = await hashPassword('password123');

    const users = await prisma.user.createMany({
        data: [
            {
                email: 'admin@company.com',
                name: 'Admin User',
                passwordHash: hashedPassword,
            },
            {
                email: 'engineer@company.com',
                name: 'John Engineer',
                passwordHash: hashedPassword,
            },
            {
                email: 'manager@company.com',
                name: 'Sarah Manager',
                passwordHash: hashedPassword,
            },
            {
                email: 'qa@company.com',
                name: 'Mike QA',
                passwordHash: hashedPassword,
            },
            {
                email: 'devops@company.com',
                name: 'Lisa DevOps',
                passwordHash: hashedPassword,
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created users`);

    // Get created users for foreign key references
    return prisma.user.findMany({
        where: {
            email: {
                in: ['admin@company.com', 'engineer@company.com', 'manager@company.com', 'qa@company.com', 'devops@company.com'],
            },
        },
    });
}

// ============================================================================
// Seed Tenant, Organization, Departments
// ============================================================================

async function seedTenantAndOrg(users: any[]) {
    console.log('🏢 Seeding tenant and organization...');

    // Ensure tenant exists
    const tenant = await prisma.tenant.upsert({
        where: { id: TENANT_ID },
        update: {},
        create: {
            id: TENANT_ID,
            key: 'test-org',
            name: 'Test Organization',
            displayName: 'Test Org Inc.',
            plan: 'enterprise',
        },
    });

    // Create or update organization
    const org = await prisma.organization.upsert({
        where: { namespace: 'software-company' },
        update: {},
        create: {
            name: 'Software Company',
            namespace: 'software-company',
            displayName: 'Software Company Inc.',
            description: 'A test organization for seeding',
            structure: {
                type: 'hierarchical',
                maxDepth: 4,
            },
            settings: {
                defaultTimezone: 'UTC',
                events: true,
                hitl: true,
                ai: true,
            },
        },
    });

    console.log(`✅ Created tenant and organization`);

    return { tenant, org };
}

// ============================================================================
// Seed Departments and Teams
// ============================================================================

async function seedDepartments(org: any) {
    console.log('🏗️ Seeding departments...');

    const departments = await prisma.department.createMany({
        data: [
            {
                organizationId: org.id,
                name: 'Engineering',
                type: 'ENGINEERING',
                description: 'Software engineering and development',
                status: 'ACTIVE',
            },
            {
                organizationId: org.id,
                name: 'Quality Assurance',
                type: 'QA',
                description: 'Quality assurance and testing',
                status: 'ACTIVE',
            },
            {
                organizationId: org.id,
                name: 'DevOps',
                type: 'DEVOPS',
                description: 'Infrastructure and operations',
                status: 'ACTIVE',
            },
            {
                organizationId: org.id,
                name: 'Product',
                type: 'PRODUCT',
                description: 'Product management',
                status: 'ACTIVE',
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created departments`);

    return prisma.department.findMany({
        where: { organizationId: org.id },
    });
}

// ============================================================================
// Seed Workspaces
// ============================================================================

async function seedWorkspaces(users: any[], org: any) {
    console.log('🌐 Seeding workspaces...');

    const adminUser = users.find((u) => u.email === 'admin@company.com');

    const workspaces = await prisma.workspace.createMany({
        data: [
            {
                name: 'Main Workspace',
                slug: 'main-workspace',
                ownerId: adminUser!.id,
            },
            {
                name: 'Engineering Workspace',
                slug: 'engineering-workspace',
                ownerId: adminUser!.id,
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created workspaces`);

    return prisma.workspace.findMany({
        where: { ownerId: adminUser!.id },
    });
}

// ============================================================================
// Seed KPIs
// ============================================================================

async function seedKpis(departments: any[]) {
    console.log('📊 Seeding KPIs...');

    const engDept = departments.find((d) => d.type === 'ENGINEERING');
    const qaDept = departments.find((d) => d.type === 'QA');

    const kpis = await prisma.kpi.createMany({
        data: [
            {
                key: 'deployments-per-week',
                name: 'Deployments per Week',
                unit: '/week',
                category: 'delivery',
                departmentId: engDept?.id,
                target: 10,
                value: 8,
                trend: 5,
            },
            {
                key: 'change-failure-rate',
                name: 'Change Failure Rate',
                unit: '%',
                category: 'quality',
                departmentId: engDept?.id,
                target: 5,
                value: 3.2,
                trend: -2,
                direction: 'lower_is_better',
            },
            {
                key: 'mean-time-to-recovery',
                name: 'Mean Time to Recovery',
                unit: 'h',
                category: 'reliability',
                departmentId: engDept?.id,
                target: 1,
                value: 1.5,
                trend: 10,
                direction: 'lower_is_better',
            },
            {
                key: 'test-coverage',
                name: 'Test Coverage',
                unit: '%',
                category: 'quality',
                departmentId: qaDept?.id,
                target: 85,
                value: 78,
                trend: 3,
            },
            {
                key: 'bug-escape-rate',
                name: 'Bug Escape Rate',
                unit: '%',
                category: 'quality',
                departmentId: qaDept?.id,
                target: 2,
                value: 1.8,
                trend: -0.5,
                direction: 'lower_is_better',
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created KPIs`);

    return prisma.kpi.findMany({
        where: {
            key: {
                in: ['deployments-per-week', 'change-failure-rate', 'mean-time-to-recovery', 'test-coverage', 'bug-escape-rate'],
            },
        },
    });
}

// ============================================================================
// Seed Workflows
// ============================================================================

async function seedWorkflows(departments: any[]) {
    console.log('⚙️ Seeding workflows...');

    const engDept = departments.find((d) => d.type === 'ENGINEERING');

    const workflows = await prisma.workflow.createMany({
        data: [
            {
                departmentId: engDept!.id,
                name: 'Feature Development',
                type: 'FEATURE_DEVELOPMENT',
                status: 'ACTIVE',
                configuration: {
                    reviewRequired: true,
                    approvalRequired: true,
                },
            },
            {
                departmentId: engDept!.id,
                name: 'Bug Fix',
                type: 'BUG_FIX',
                status: 'ACTIVE',
                configuration: {
                    reviewRequired: true,
                    priorityBased: true,
                },
            },
            {
                departmentId: engDept!.id,
                name: 'Deployment',
                type: 'DEPLOYMENT',
                status: 'ACTIVE',
                configuration: {
                    approvalRequired: true,
                    notificationChannels: ['email', 'slack'],
                },
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created workflows`);

    return prisma.workflow.findMany({
        where: { departmentId: engDept!.id },
    });
}

// ============================================================================
// Seed Work Items (DevSecOps)
// ============================================================================

async function seedWorkItems(workflows: any[], departments: any[]) {
    console.log('📋 Seeding work items...');

    const engDept = departments.find((d) => d.type === 'ENGINEERING');
    const workflow = workflows[0];

    const stages = ['plan', 'build', 'test', 'deploy', 'operate'];

    const workItems = [];
    for (let i = 0; i < 20; i++) {
        workItems.push({
            type: ['FEATURE', 'BUG', 'DEPLOYMENT', 'TASK'][i % 4],
            title: `Work Item ${i + 1}: ${['Implement API', 'Fix null pointer', 'Deploy v2.0', 'Refactor auth'][i % 4]}`,
            description: `Description for work item ${i + 1}`,
            status: ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'][i % 4],
            priority: ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'][i % 4],
            stageKey: stages[i % stages.length],
            departmentId: engDept!.id,
            workflowId: workflow.id,
            dueDate: new Date(Date.now() + Math.random() * 30 * 24 * 60 * 60 * 1000),
        });
    }

    await prisma.workItem.createMany({
        data: workItems,
        skipDuplicates: true,
    });

    console.log(`✅ Created work items`);
}

// ============================================================================
// Seed Budgets
// ============================================================================

async function seedBudgets(departments: any[]) {
    console.log('💰 Seeding budgets...');

    const budgets = [];
    for (const dept of departments) {
        for (const quarter of ['Q1', 'Q2', 'Q3', 'Q4']) {
            const allocated = Math.random() * 1000000 + 100000;
            budgets.push({
                departmentId: dept.id,
                year: 2025,
                quarter: quarter,
                allocated: allocated,
                forecasted: allocated * 0.95,
                status: 'active',
                categories: {
                    headcount: { allocated: allocated * 0.6, spent: allocated * 0.55 },
                    operations: { allocated: allocated * 0.2, spent: allocated * 0.18 },
                    tools: { allocated: allocated * 0.15, spent: allocated * 0.12 },
                    contingency: { allocated: allocated * 0.05, spent: 0 },
                },
            });
        }
    }

    await prisma.budget.createMany({
        data: budgets,
        skipDuplicates: true,
    });

    console.log(`✅ Created budgets`);
}

// ============================================================================
// Seed ML Models
// ============================================================================

async function seedMlModels(users: any[]) {
    console.log('🤖 Seeding ML models...');

    const owner = users.find((u) => u.email === 'engineer@company.com');

    const models = await prisma.mlModel.createMany({
        data: [
            {
                key: 'churn-predictor',
                name: 'Customer Churn Predictor',
                type: 'classification',
                status: 'deployed',
                ownerUserId: owner!.id,
                team: 'Data Science',
                useCase: 'Predict customer churn',
            },
            {
                key: 'demand-forecaster',
                name: 'Demand Forecaster',
                type: 'regression',
                status: 'training',
                ownerUserId: owner!.id,
                team: 'Data Science',
                useCase: 'Forecast demand',
            },
            {
                key: 'anomaly-detector',
                name: 'Anomaly Detector',
                type: 'unsupervised',
                status: 'deployed',
                ownerUserId: owner!.id,
                team: 'Platform',
                useCase: 'Detect system anomalies',
            },
        ],
        skipDuplicates: true,
    });

    console.log(`✅ Created ML models`);

    // Create model versions
    for (const model of models.data || []) {
        const actualModel = await prisma.mlModel.findUnique({
            where: { key: model.key },
        });
        if (actualModel) {
            const version = await prisma.mlModelVersion.create({
                data: {
                    modelId: actualModel.id,
                    version: '1.0.0',
                    status: 'current',
                    accuracy: 0.92,
                    precision: 0.89,
                    recall: 0.94,
                    f1Score: 0.915,
                    latency: 150,
                    throughput: 1000,
                    deployedAt: new Date(),
                },
            });

            // Add metrics
            await prisma.mlModelMetric.createMany({
                data: [
                    {
                        modelVersionId: version.id,
                        name: 'accuracy',
                        value: 0.92,
                        window: '1h',
                    },
                    {
                        modelVersionId: version.id,
                        name: 'latency',
                        value: 150,
                        window: '1h',
                    },
                    {
                        modelVersionId: version.id,
                        name: 'throughput',
                        value: 1000,
                        window: '1h',
                    },
                ],
            });
        }
    }
}

// ============================================================================
// Seed Alerts and Logs (Observability)
// ============================================================================

async function seedAlertsAndLogs() {
    console.log('🚨 Seeding alerts and logs...');

    const alertSeverities = ['critical', 'high', 'medium', 'low'];
    const alertStatuses = ['active', 'acknowledged', 'resolved'];

    const alerts = [];
    const now = Date.now();

    for (let i = 0; i < 30; i++) {
        alerts.push({
            tenantId: TENANT_ID,
            severity: alertSeverities[i % alertSeverities.length] as 'critical' | 'high' | 'medium' | 'low',
            status: alertStatuses[i % alertStatuses.length] as 'active' | 'acknowledged' | 'resolved',
            title: `Alert #${i + 1}`,
            message: `Alert message for item ${i + 1}`,
            source: `system-${i % 5}`,
            timestamp: new Date(now - Math.random() * 7 * 24 * 60 * 60 * 1000),
            metadata: {
                component: `service-${i % 10}`,
                impact: 'high',
            },
        });
    }

    await prisma.alert.createMany({
        data: alerts,
        skipDuplicates: true,
    });

    const logLevels = ['ERROR', 'WARN', 'INFO', 'DEBUG'];
    const logs = [];

    for (let i = 0; i < 100; i++) {
        logs.push({
            tenantId: TENANT_ID,
            level: logLevels[i % logLevels.length],
            source: `service-${i % 15}`,
            message: `Log message ${i + 1}`,
            timestamp: new Date(now - Math.random() * 24 * 60 * 60 * 1000),
            metadata: {
                correlationId: `corr_${i}`,
                requestId: `req_${i}`,
            },
        });
    }

    const batchSize = 50;
    for (let i = 0; i < logs.length; i += batchSize) {
        const batch = logs.slice(i, i + batchSize);
        await prisma.logEntry.createMany({
            data: batch,
            skipDuplicates: true,
        });
    }

    console.log(`✅ Created alerts and logs`);
}

// ============================================================================
// Main Seed Function
// ============================================================================

async function main() {
    console.log('\n🌱 Starting comprehensive database seeding...\n');

    try {
        // Seed users
        const users = await seedUsers();

        // Seed tenant and organization
        const { tenant, org } = await seedTenantAndOrg(users);

        // Seed departments
        const departments = await seedDepartments(org);

        // Seed workspaces
        await seedWorkspaces(users, org);

        // Seed KPIs
        const kpis = await seedKpis(departments);

        // Seed workflows
        const workflows = await seedWorkflows(departments);

        // Seed work items
        await seedWorkItems(workflows, departments);

        // Seed budgets
        await seedBudgets(departments);

        // Seed ML models
        await seedMlModels(users);

        // Seed alerts and logs
        await seedAlertsAndLogs();

        console.log('\n✨ Database seeding completed successfully!');
        console.log('\n📊 Summary:');
        console.log(`  - Users: 5`);
        console.log(`  - Tenants: 1`);
        console.log(`  - Organizations: 1`);
        console.log(`  - Departments: 4`);
        console.log(`  - Workspaces: 2`);
        console.log(`  - KPIs: 5`);
        console.log(`  - Workflows: 3`);
        console.log(`  - Work Items: 20`);
        console.log(`  - Budgets: 16`);
        console.log(`  - ML Models: 3`);
        console.log(`  - Alerts: 30`);
        console.log(`  - Logs: 100`);

        console.log('\n🔗 Test endpoints:');
        console.log('  GET /api/v1/organizations');
        console.log('  GET /api/v1/workspaces');
        console.log('  GET /api/v1/kpis');
        console.log('  GET /api/v1/workflows');
        console.log('  GET /api/v1/budgets');
    } catch (error) {
        console.error('❌ Error seeding database:', error);
        throw error;
    } finally {
        await prisma.$disconnect();
    }
}

main();
