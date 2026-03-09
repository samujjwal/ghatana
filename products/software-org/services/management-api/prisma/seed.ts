/**
 * Prisma Database Seeder
 *
 * @doc.type module
 * @doc.purpose Seeds development database with demo data matching MSW mocks
 * @doc.layer product
 * @doc.pattern Seeder
 *
 * Data Created:
 * - Demo user (admin@example.com)
 * - Demo workspace ("Demo Workspace")
 * - Sample persona preferences (admin + tech-lead)
 * - Organization with departments
 * - KPIs with time-series data and narratives
 * - ML models with versions and metrics
 * - Reports with schedules
 * - Workflows with executions
 * - Tenants with environments, alerts, anomalies, A/B tests
 * - Audit events
 *
 * Usage:
 *   pnpm exec tsx prisma/seed.ts
 */

import bcrypt from 'bcryptjs';
import { prisma } from '../src/db/client';
import { getConfigLoader } from '../src/services/config-loader.service';

/** Generate date N days ago */
function daysAgo(days: number): Date {
    return new Date(Date.now() - days * 24 * 60 * 60 * 1000);
}

/** Generate date N hours ago */
function hoursAgo(hours: number): Date {
    return new Date(Date.now() - hours * 60 * 60 * 1000);
}

async function main(): Promise<void> {
    console.log('🌱 Seeding database...');

    // =========================================================================
    // 1. User & Workspace
    // =========================================================================
    const passwordHash = await bcrypt.hash('demo123', 10);
    const user = await prisma.user.upsert({
        where: { email: 'admin@example.com' },
        update: {},
        create: {
            email: 'admin@example.com',
            name: 'Demo Admin',
            passwordHash,
        },
    });
    console.log('✅ Created user:', user.email);

    const workspace = await prisma.workspace.upsert({
        where: { slug: 'demo-workspace' },
        update: {},
        create: {
            name: 'Demo Workspace',
            slug: 'demo-workspace',
            ownerId: user.id,
        },
    });
    console.log('✅ Created workspace:', workspace.name);

    await prisma.personaPreference.upsert({
        where: { userId_workspaceId: { userId: user.id, workspaceId: workspace.id } },
        update: {},
        create: {
            userId: user.id,
            workspaceId: workspace.id,
            activeRoles: ['admin', 'tech-lead'],
            preferences: {
                dashboardLayout: 'grid',
                plugins: ['workflows', 'reporting', 'security'],
                metrics: ['team-velocity', 'sprint-burndown', 'code-coverage'],
                features: { darkMode: true, notifications: true },
            },
        },
    });
    console.log('✅ Created persona preference');

    // =========================================================================
    // 2. Organization & Departments
    // =========================================================================
    const org = await prisma.organization.upsert({
        where: { namespace: 'software-org' },
        update: {},
        create: {
            name: 'Software Organization',
            namespace: 'software-org',
            displayName: 'Software Org',
            description: 'Virtual software organization for demo',
            structure: { type: 'hierarchical', maxDepth: 4 },
            settings: { defaultTimezone: 'UTC', events: {}, hitl: {}, ai: {} },
        },
    });
    console.log('✅ Created organization:', org.name);

    const engDept = await prisma.department.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Engineering' } },
        update: {},
        create: {
            organizationId: org.id,
            name: 'Engineering',
            type: 'ENGINEERING',
            description: 'Software development',
            status: 'ACTIVE',
        },
    });

    const opsDept = await prisma.department.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Operations' } },
        update: {},
        create: {
            organizationId: org.id,
            name: 'Operations',
            type: 'DEVOPS',
            description: 'Infrastructure and operations',
            status: 'ACTIVE',
        },
    });

    const sreDept = await prisma.department.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'SRE' } },
        update: {},
        create: {
            organizationId: org.id,
            name: 'SRE',
            type: 'ENGINEERING',
            description: 'Site Reliability Engineering',
            status: 'ACTIVE',
        },
    });

    const fraudDept = await prisma.department.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Fraud & Risk' } },
        update: {},
        create: {
            organizationId: org.id,
            name: 'Fraud & Risk',
            type: 'ENGINEERING',
            description: 'Fraud detection and risk management',
            status: 'ACTIVE',
        },
    });
    console.log('✅ Created departments: Engineering, Operations, SRE, Fraud & Risk');

    // =========================================================================
    // 2.3 Agents - AI Agents in the Organization
    // =========================================================================
    const buildAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Build Agent' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: engDept.id,
            name: 'Build Agent',
            role: 'automation',
            status: 'ONLINE',
            capabilities: ['compile', 'package', 'artifact-upload', 'cache-management', 'parallel-builds'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
                personality: { temperature: 0.3, creativity: 0.2, assertiveness: 0.8 },
                systemPrompt: 'You are a build automation agent. Your role is to compile code, manage dependencies, and produce build artifacts efficiently.',
            },
        },
    });

    const securityAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Security Scanner' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: engDept.id,
            name: 'Security Scanner',
            role: 'security',
            status: 'ONLINE',
            capabilities: ['vulnerability-scan', 'sast', 'dast', 'dependency-audit', 'compliance-check'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 8192 },
                personality: { temperature: 0.1, creativity: 0.1, assertiveness: 0.9 },
                systemPrompt: 'You are a security agent. Your role is to identify vulnerabilities, assess risks, and ensure compliance with security policies.',
            },
        },
    });

    const deployAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Deploy Agent' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: opsDept.id,
            name: 'Deploy Agent',
            role: 'deployment',
            status: 'ONLINE',
            capabilities: ['deploy', 'rollback', 'health-check', 'traffic-shift', 'canary-release'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
                personality: { temperature: 0.2, creativity: 0.3, assertiveness: 0.7 },
                systemPrompt: 'You are a deployment agent. Your role is to safely deploy applications, monitor health, and perform rollbacks when necessary.',
            },
        },
    });

    const triageAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Triage Agent' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: sreDept.id,
            name: 'Triage Agent',
            role: 'triage',
            status: 'ONLINE',
            capabilities: ['incident-analysis', 'context-gathering', 'severity-assessment', 'runbook-lookup', 'escalation'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 8192 },
                personality: { temperature: 0.4, creativity: 0.5, assertiveness: 0.6 },
                systemPrompt: 'You are an incident triage agent. Your role is to quickly analyze incidents, gather relevant context, and provide actionable recommendations.',
            },
        },
    });

    const monitoringAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Monitoring Agent' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: sreDept.id,
            name: 'Monitoring Agent',
            role: 'monitoring',
            status: 'ONLINE',
            capabilities: ['metrics-collection', 'anomaly-detection', 'alerting', 'dashboard-generation', 'trend-analysis'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
                personality: { temperature: 0.3, creativity: 0.4, assertiveness: 0.7 },
                systemPrompt: 'You are a monitoring agent. Your role is to collect metrics, detect anomalies, and alert teams about potential issues before they become critical.',
            },
        },
    });

    const testAgent = await prisma.agent.upsert({
        where: { organizationId_name: { organizationId: org.id, name: 'Test Automation Agent' } },
        update: {},
        create: {
            organizationId: org.id,
            departmentId: engDept.id,
            name: 'Test Automation Agent',
            role: 'testing',
            status: 'BUSY',
            capabilities: ['test-generation', 'test-execution', 'coverage-analysis', 'regression-testing', 'performance-testing'],
            configuration: {
                model: { id: 'gpt-4-turbo', provider: 'OpenAI', maxTokens: 4096 },
                personality: { temperature: 0.4, creativity: 0.6, assertiveness: 0.5 },
                systemPrompt: 'You are a test automation agent. Your role is to generate comprehensive test cases, execute tests, and ensure code quality through thorough testing.',
            },
        },
    });
    console.log('✅ Created agents: Build, Security, Deploy, Triage, Monitoring, Test Automation');

    // =========================================================================
    // 2.5 Admin: Teams, Services, Roles, Personas, Policies, Settings
    // (Supporting Admin journeys as per SOFTWARE_ORG_SEEDED_DATASET.md)
    // =========================================================================

    // Create primary tenant for Admin data (acme-payments)
    const acmeTenant = await prisma.tenant.upsert({
        where: { key: 'acme-payments' },
        update: {},
        create: {
            key: 'acme-payments',
            name: 'Acme Payments',
            displayName: 'Acme Payments Inc.',
            description: 'Primary tenant for happy-path journeys',
            status: 'active',
            plan: 'enterprise',
        },
    });

    // Create secondary tenant for edge cases (beta-retail)
    const betaTenant = await prisma.tenant.upsert({
        where: { key: 'beta-retail' },
        update: {},
        create: {
            key: 'beta-retail',
            name: 'Beta Retail',
            displayName: 'Beta Retail Corp',
            description: 'Edge-case tenant with partial configuration',
            status: 'active',
            plan: 'professional',
        },
    });
    console.log('✅ Created tenants: acme-payments, beta-retail');

    // Teams (per department)
    const platformCoreTeam = await prisma.team.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'platform-core' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            departmentId: engDept.id,
            name: 'Platform Core',
            slug: 'platform-core',
            description: 'Core platform engineering team',
            status: 'ACTIVE',
        },
    });

    const sreCoreTeam = await prisma.team.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'sre-core' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            departmentId: sreDept.id,
            name: 'SRE Core',
            slug: 'sre-core',
            description: 'Site reliability engineering team',
            status: 'ACTIVE',
        },
    });

    const paymentsApiTeam = await prisma.team.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'payments-api-team' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            departmentId: engDept.id,
            name: 'Payments API Team',
            slug: 'payments-api-team',
            description: 'Team responsible for payments API',
            status: 'ACTIVE',
        },
    });

    const fraudTeam = await prisma.team.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'fraud-detection-team' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            departmentId: fraudDept.id,
            name: 'Fraud Detection Team',
            slug: 'fraud-detection-team',
            description: 'Team for fraud detection and prevention',
            status: 'ACTIVE',
        },
    });
    console.log('✅ Created teams: platform-core, sre-core, payments-api-team, fraud-detection-team');

    // Services
    const paymentsApiService = await prisma.service.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'payments-api' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Payments API',
            slug: 'payments-api',
            description: 'Core payments processing API',
            tier: 'tier-1',
            status: 'healthy',
            teamId: paymentsApiTeam.id,
        },
    });

    const checkoutWebService = await prisma.service.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'checkout-web' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Checkout Web',
            slug: 'checkout-web',
            description: 'Web checkout application',
            tier: 'tier-2',
            status: 'healthy',
            teamId: platformCoreTeam.id,
        },
    });

    const fraudDetectorService = await prisma.service.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'fraud-detector' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Fraud Detector',
            slug: 'fraud-detector',
            description: 'ML-powered fraud detection service',
            tier: 'tier-1',
            status: 'healthy',
            teamId: fraudTeam.id,
        },
    });

    const mlOrchestratorService = await prisma.service.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'ml-orchestrator' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'ML Orchestrator',
            slug: 'ml-orchestrator',
            description: 'Machine learning model orchestration service',
            tier: 'tier-2',
            status: 'healthy',
            teamId: fraudTeam.id,
        },
    });

    // Orphaned service for beta-retail (edge case)
    await prisma.service.upsert({
        where: { tenantId_slug: { tenantId: betaTenant.id, slug: 'orphaned-service' } },
        update: {},
        create: {
            tenantId: betaTenant.id,
            name: 'Orphaned Service',
            slug: 'orphaned-service',
            description: 'Service with no owner for testing negative journeys',
            tier: 'tier-3',
            status: 'unknown',
            teamId: null,
        },
    });
    console.log('✅ Created services: payments-api, checkout-web, fraud-detector, ml-orchestrator, orphaned-service');

    // =========================================================================
    // 2.6 Budgets, Reviews, Growth Plans
    // =========================================================================

    // Budgets
    await prisma.budget.upsert({
        where: {
            departmentId_year_quarter: {
                departmentId: engDept.id,
                year: 2025,
                quarter: 'Q3'
            }
        },
        update: {},
        create: {
            departmentId: engDept.id,
            year: 2025,
            quarter: 'Q3',
            allocated: 1500000,
            spent: 850000,
            forecasted: 1450000,
            categories: {
                headcount: 1000000,
                tools: 300000,
                training: 100000,
                travel: 100000
            },
            status: 'approved',
        }
    });

    await prisma.budget.upsert({
        where: {
            departmentId_year_quarter: {
                departmentId: opsDept.id,
                year: 2025,
                quarter: 'Q3'
            }
        },
        update: {},
        create: {
            departmentId: opsDept.id,
            year: 2025,
            quarter: 'Q3',
            allocated: 800000,
            spent: 400000,
            forecasted: 750000,
            categories: {
                headcount: 500000,
                infrastructure: 250000,
                tools: 50000
            },
            status: 'approved',
        }
    });
    console.log('✅ Created budgets for Engineering and Operations');

    // Performance Reviews
    await prisma.performanceReview.create({
        data: {
            employeeId: user.id,
            reviewerId: user.id, // Self review for demo
            period: 'Q2-2025',
            status: 'completed',
            ratings: {
                overall: 4.5,
                technical: 5,
                communication: 4,
                leadership: 4.5
            },
            goals: {
                achieved: ['Completed migration project', 'Mentored 2 junior engineers'],
                inProgress: []
            },
            strengths: ['System Design', 'Mentoring'],
            improvements: ['Public Speaking'],
            feedback: 'Excellent performance this quarter.',
            submittedAt: new Date(),
            completedAt: new Date(),
        }
    });

    // Create a mock employee for review
    const mockEmployee = await prisma.user.upsert({
        where: { email: 'employee@example.com' },
        update: {},
        create: {
            email: 'employee@example.com',
            name: 'John Doe',
            passwordHash,
        },
    });

    await prisma.performanceReview.create({
        data: {
            employeeId: mockEmployee.id,
            reviewerId: user.id,
            period: 'Q3-2025',
            status: 'in-progress',
            ratings: {
                technical: 3.5,
                communication: 4.0
            },
            goals: {},
            strengths: [],
            improvements: [],
        }
    });
    console.log('✅ Created performance reviews');

    // Growth Plans
    await prisma.growthPlan.create({
        data: {
            userId: user.id,
            title: 'Senior Staff Engineer Path',
            description: 'Roadmap to Senior Staff Engineer role',
            period: '2025',
            status: 'active',
            progress: 65,
            goals: [
                { id: 'g1', title: 'Lead major architecture refactor', status: 'in-progress', progress: 70 },
                { id: 'g2', title: 'Mentor 2 senior engineers', status: 'completed', progress: 100 },
                { id: 'g3', title: 'Publish 3 technical blog posts', status: 'pending', progress: 0 }
            ],
            skills: ['System Architecture', 'Technical Strategy', 'Organization Design'],
            resources: ['Staff Engineer Book', 'Leadership Training'],
        }
    });
    console.log('✅ Created growth plans');

    // RBAC Roles (global system roles + tenant-scoped roles)
    // For global roles (tenantId = null), use findFirst + create pattern since upsert doesn't work well with nullable compound keys
    let adminRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'org-admin' } });
    if (!adminRole) {
        adminRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Organization Admin',
                slug: 'org-admin',
                description: 'Full administrative access',
                permissions: ['admin:*', 'tenant:*', 'org:*', 'security:*', 'settings:*'],
                scopes: ['global', 'tenant'],
                isSystem: true,
            },
        });
    }

    let securityAdminRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'security-admin' } });
    if (!securityAdminRole) {
        securityAdminRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Security Admin',
                slug: 'security-admin',
                description: 'Security and policy management',
                permissions: ['security:*', 'policy:*', 'audit:read'],
                scopes: ['global', 'tenant'],
                isSystem: true,
            },
        });
    }

    let operateOncallRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'operate-oncall' } });
    if (!operateOncallRole) {
        operateOncallRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Operate On-Call',
                slug: 'operate-oncall',
                description: 'On-call incident response',
                permissions: ['incident:*', 'queue:*', 'workflow:execute', 'observe:read'],
                scopes: ['tenant'],
                isSystem: true,
            },
        });
    }

    let buildAuthorRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'build-author' } });
    if (!buildAuthorRole) {
        buildAuthorRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Build Author',
                slug: 'build-author',
                description: 'Workflow and automation authoring',
                permissions: ['workflow:*', 'agent:*', 'simulator:*'],
                scopes: ['tenant'],
                isSystem: true,
            },
        });
    }

    let observeViewerRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'observe-viewer' } });
    if (!observeViewerRole) {
        observeViewerRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Observe Viewer',
                slug: 'observe-viewer',
                description: 'Read-only observability access',
                permissions: ['observe:read', 'report:read', 'metric:read'],
                scopes: ['tenant'],
                isSystem: false,
            },
        });
    }

    let readOnlyRole = await prisma.role.findFirst({ where: { tenantId: null, slug: 'read-only' } });
    if (!readOnlyRole) {
        readOnlyRole = await prisma.role.create({
            data: {
                tenantId: null,
                name: 'Read Only',
                slug: 'read-only',
                description: 'Read-only access across all modules',
                permissions: ['*:read'],
                scopes: ['tenant'],
                isSystem: false,
            },
        });
    }
    console.log('✅ Created RBAC roles: org-admin, security-admin, operate-oncall, build-author, observe-viewer, read-only');

    // Personas (store capabilities/responsibilities in metadata JSON field)
    const sreOncallPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'sre-oncall' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'SRE On-Call',
            slug: 'sre-oncall',
            description: 'On-call SRE persona for incident response',
            type: 'human',
            primaryTeamId: sreCoreTeam.id,
            metadata: {
                capabilities: ['incident-response', 'monitoring', 'alerting'],
                responsibilities: ['P1 incident triage', 'Escalation', 'Post-mortems'],
            },
        },
    });

    const platformEngineerPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'platform-engineer' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Platform Engineer',
            slug: 'platform-engineer',
            description: 'Platform engineering persona',
            type: 'human',
            primaryTeamId: platformCoreTeam.id,
            metadata: {
                capabilities: ['infrastructure', 'automation', 'tooling'],
                responsibilities: ['Platform development', 'CI/CD', 'Developer experience'],
            },
        },
    });

    const engManagerPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'eng-manager' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Engineering Manager',
            slug: 'eng-manager',
            description: 'Engineering management persona',
            type: 'human',
            primaryTeamId: paymentsApiTeam.id,
            metadata: {
                capabilities: ['team-management', 'planning', 'reporting'],
                responsibilities: ['Team leadership', 'Resource allocation', 'Cross-team coordination'],
            },
        },
    });

    const mlEngineerPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'ml-engineer' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'ML Engineer',
            slug: 'ml-engineer',
            description: 'Machine learning engineering persona',
            type: 'human',
            primaryTeamId: fraudTeam.id,
            metadata: {
                capabilities: ['ml-development', 'model-training', 'model-monitoring'],
                responsibilities: ['Model development', 'Model deployment', 'Performance tuning'],
            },
        },
    });

    const aiIncidentAgentPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'ai-incident-agent' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'AI Incident Agent',
            slug: 'ai-incident-agent',
            description: 'AI-powered incident triage agent',
            type: 'ai',
            primaryTeamId: null,
            metadata: {
                capabilities: ['incident-triage', 'root-cause-analysis', 'remediation-suggestions'],
                responsibilities: ['Automated triage', 'Context gathering', 'Recommendation generation'],
                toolAccess: ['metrics', 'incidents', 'ticketing'],
            },
        },
    });

    const deploymentAdvisorPersona = await prisma.persona.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'deployment-advisor-agent' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Deployment Advisor Agent',
            slug: 'deployment-advisor-agent',
            description: 'AI agent that advises on deployments',
            type: 'ai',
            primaryTeamId: null,
            metadata: {
                capabilities: ['deployment-analysis', 'risk-assessment', 'rollback-recommendations'],
                responsibilities: ['Deployment suggestions', 'CI analysis', 'Git diff analysis'],
                toolAccess: ['ci', 'git', 'metrics'],
            },
        },
    });
    console.log('✅ Created personas: sre-oncall, platform-engineer, eng-manager, ml-engineer, ai-incident-agent, deployment-advisor-agent');

    // Load agents from YAML configs using ConfigLoaderService
    console.log('📦 Loading agents from YAML configs...');
    try {
        const yamlAgents = await configLoader.loadAllAgents();
        let agentCount = 0;

        for (const agent of yamlAgents) {
            // Map agent department to existing departments
            let agentDept = engDept; // Default to Engineering
            const agentDeptName = agent.department?.toLowerCase() || '';
            if (agentDeptName.includes('ops') || agentDeptName.includes('operations')) {
                agentDept = opsDept;
            } else if (agentDeptName.includes('sre') || agentDeptName.includes('devops')) {
                agentDept = sreDept;
            } else if (agentDeptName.includes('fraud') || agentDeptName.includes('risk')) {
                agentDept = fraudDept;
            }

            // Create persona from agent YAML config
            await prisma.persona.upsert({
                where: { tenantId_slug: { tenantId: acmeTenant.id, slug: agent.id } },
                update: {},
                create: {
                    tenantId: acmeTenant.id,
                    name: agent.name || agent.id,
                    slug: agent.id,
                    description: agent.role ? `${agent.role} agent from YAML config` : 'Agent from YAML config',
                    type: 'ai',
                    primaryTeamId: agentDept?.id || null,
                    metadata: {
                        ...agent,
                        source: 'yaml-config',
                        yamlLocation: `config/agents/${agent.id}.yaml`,
                    } as any,
                },
            });
            agentCount++;
        }
        console.log(`✅ Loaded ${agentCount} agents from YAML configs as personas`);
    } catch (error) {
        console.warn('⚠️ Could not load agents from YAML configs:', error instanceof Error ? error.message : 'Unknown error');
        console.log('   Continuing with seed...');
    }

    // Role Assignments - map personas to roles
    await prisma.roleAssignment.createMany({
        data: [
            { roleId: operateOncallRole.id, personaId: sreOncallPersona.id, scope: acmeTenant.id },
            { roleId: observeViewerRole.id, personaId: sreOncallPersona.id, scope: acmeTenant.id },
            { roleId: buildAuthorRole.id, personaId: platformEngineerPersona.id, scope: acmeTenant.id },
            { roleId: operateOncallRole.id, personaId: platformEngineerPersona.id, scope: acmeTenant.id },
            { roleId: observeViewerRole.id, personaId: platformEngineerPersona.id, scope: acmeTenant.id },
            { roleId: observeViewerRole.id, personaId: engManagerPersona.id, scope: acmeTenant.id },
            { roleId: observeViewerRole.id, personaId: mlEngineerPersona.id, scope: acmeTenant.id },
            { roleId: buildAuthorRole.id, personaId: mlEngineerPersona.id, scope: acmeTenant.id },
        ],
        skipDuplicates: true,
    });
    console.log('✅ Created role assignments for personas');

    // Policies (as PatternSpecification references)
    const polApprovalDeploy = await prisma.policy.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'require-approval-prod-deploy' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Require Approval for Prod Deploy',
            slug: 'require-approval-prod-deploy',
            description: 'Requires manual approval for production deployments',
            type: 'deployment',
            scope: { environments: ['prod'] },
            triggers: ['deployment.requested'],
            actions: ['require-approval', 'notify-oncall'],
            status: 'active',
            priority: 100,
        },
    });

    const polAutoMitigate = await prisma.policy.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'auto-mitigate-low-risk' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Auto-Mitigate Low Risk Incidents',
            slug: 'auto-mitigate-low-risk',
            description: 'Automatically apply remediation for low-risk incidents',
            type: 'incident',
            scope: { severity: ['P3', 'P4'] },
            triggers: ['incident.created'],
            actions: ['auto-remediate', 'create-ticket'],
            status: 'active',
            priority: 50,
        },
    });

    await prisma.policy.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'guardrail-high-risk-ai' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'AI High Risk Action Guardrail',
            slug: 'guardrail-high-risk-ai',
            description: 'Prevents AI agents from taking high-risk actions without approval',
            type: 'guardrail',
            scope: { riskLevel: ['high', 'critical'] },
            triggers: ['ai.action.proposed'],
            actions: ['block', 'require-human-approval', 'notify-admin'],
            status: 'active',
            priority: 200,
        },
    });
    console.log('✅ Created policies: require-approval-prod-deploy, auto-mitigate-low-risk, guardrail-high-risk-ai');

    // Platform Settings
    await prisma.platformSettings.upsert({
        where: { tenantId_category: { tenantId: acmeTenant.id, category: 'general' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            category: 'general',
            settings: {
                displayName: 'Acme Payments',
                defaultTimezone: 'UTC',
                defaultLocale: 'en-US',
                features: { operate: true, observe: true, build: true },
                appearance: { defaultTheme: 'dark', brandColorToken: 'brand.primary' },
            },
        },
    });

    await prisma.platformSettings.upsert({
        where: { tenantId_category: { tenantId: acmeTenant.id, category: 'ai-agents' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            category: 'ai-agents',
            settings: {
                enabled: true,
                allowedTools: ['metrics', 'incidents', 'git', 'ticketing'],
                guardrails: {
                    maxRiskLevel: 'medium',
                    requireApprovalAbove: 'high',
                    disallowedActions: ['direct_prod_deploy', 'delete_data'],
                },
                auditLevel: 'all-decisions',
            },
        },
    });
    console.log('✅ Created platform settings for acme-payments');

    // Integrations
    await prisma.integration.upsert({
        where: { tenantId_type_provider: { tenantId: acmeTenant.id, type: 'observability', provider: 'datadog' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            type: 'observability',
            provider: 'datadog',
            name: 'Datadog Integration',
            status: 'connected',
            configuration: { apiKey: '***', site: 'datadoghq.com' },
            healthDetails: { status: 'healthy', lastPing: new Date().toISOString() },
        },
    });

    await prisma.integration.upsert({
        where: { tenantId_type_provider: { tenantId: acmeTenant.id, type: 'git', provider: 'github' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            type: 'git',
            provider: 'github',
            name: 'GitHub Integration',
            status: 'connected',
            configuration: { org: 'acme-payments', installationId: 12345 },
            healthDetails: { status: 'healthy', lastPing: new Date().toISOString() },
        },
    });

    await prisma.integration.upsert({
        where: { tenantId_type_provider: { tenantId: acmeTenant.id, type: 'ci', provider: 'github-actions' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            type: 'ci',
            provider: 'github-actions',
            name: 'GitHub Actions CI',
            status: 'connected',
            configuration: { workflows: ['build', 'test', 'deploy'] },
            healthDetails: { status: 'healthy', lastPing: new Date().toISOString() },
        },
    });

    await prisma.integration.upsert({
        where: { tenantId_type_provider: { tenantId: acmeTenant.id, type: 'ticketing', provider: 'jira' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            type: 'ticketing',
            provider: 'jira',
            name: 'Jira Integration',
            status: 'connected',
            configuration: { baseUrl: 'https://acme.atlassian.net', project: 'PAY' },
            healthDetails: { status: 'healthy', lastPing: new Date().toISOString() },
        },
    });
    console.log('✅ Created integrations: datadog, github, github-actions, jira');

    // =========================================================================
    // Interactions: Load from YAML configs using ConfigLoaderService
    // =========================================================================
    console.log('📦 Loading interactions from YAML configs...');
    const configLoader = getConfigLoader();

    try {
        const yamlInteractions = await configLoader.loadInteractions();
        let interactionCount = 0;

        for (const interaction of yamlInteractions) {
            // Map interaction source/target departments to existing departments
            let sourceDept: typeof engDept | null = null;
            let targetDept: typeof engDept | null = null;

            if (interaction.sourceDepartment) {
                const srcName = interaction.sourceDepartment.toLowerCase();
                if (srcName.includes('eng')) sourceDept = engDept;
                else if (srcName.includes('ops')) sourceDept = opsDept;
                else if (srcName.includes('sre')) sourceDept = sreDept;
                else if (srcName.includes('fraud')) sourceDept = fraudDept;
            }

            if (interaction.targetDepartment) {
                const tgtName = interaction.targetDepartment.toLowerCase();
                if (tgtName.includes('eng')) targetDept = engDept;
                else if (tgtName.includes('ops')) targetDept = opsDept;
                else if (tgtName.includes('sre')) targetDept = sreDept;
                else if (tgtName.includes('fraud')) targetDept = fraudDept;
            }

            await prisma.interaction.upsert({
                where: { tenantId_slug: { tenantId: acmeTenant.id, slug: interaction.id } },
                update: {},
                create: {
                    tenantId: acmeTenant.id,
                    name: interaction.id,
                    slug: interaction.id,
                    displayName: interaction.displayName,
                    description: interaction.description,
                    type: interaction.type,
                    status: interaction.status,
                    sourceDepartmentId: sourceDept?.id || null,
                    targetDepartmentId: targetDept?.id || null,
                    // Store full config as metadata for reference
                    metadata: { ...interaction, source: 'yaml-config' } as any,
                },
            });
            interactionCount++;
        }
        console.log(`✅ Loaded ${interactionCount} interactions from YAML configs`);
    } catch (error) {
        console.warn('⚠️ Could not load interactions from YAML configs:', error instanceof Error ? error.message : 'Unknown error');
        console.log('   Continuing with seed...');
    }

    // =========================================================================
    // Build: Workflows & Agents (from SOFTWARE_ORG_SEEDED_DATASET.md)
    // =========================================================================
    const wfStandardDeploy = await prisma.buildWorkflow.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'wf-standard-prod-deploy' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Standard Production Deploy',
            slug: 'wf-standard-prod-deploy',
            description: 'Standard deployment workflow for production',
            status: 'active',
            ownerTeamId: paymentsApiTeam.id,
            trigger: { eventType: 'deployment.requested', environment: ['staging', 'prod'] },
            steps: [
                { action: 'validate_changes', config: { requireApproval: true } },
                { action: 'run_tests', config: { testSuite: 'integration' } },
                { action: 'deploy', config: { strategy: 'blue-green' } },
                { action: 'verify', config: { healthChecks: true, rollbackOnFailure: true } },
            ],
            activatedAt: new Date(),
        },
    });

    const wfP1Mitigation = await prisma.buildWorkflow.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'wf-p1-incident-mitigation' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'P1 Incident Mitigation',
            slug: 'wf-p1-incident-mitigation',
            description: 'Automated mitigation for P1 incidents',
            status: 'active',
            ownerTeamId: sreCoreTeam.id,
            trigger: { eventType: 'incident.created', severity: 'P1' },
            steps: [
                { action: 'notify_oncall', config: { channels: ['pagerduty', 'slack'] } },
                { action: 'gather_diagnostics', config: { logs: true, metrics: true } },
                { action: 'attempt_auto_remediation', config: { requireApproval: false } },
                { action: 'escalate_if_unresolved', config: { timeout: 300 } },
            ],
            activatedAt: new Date(),
        },
    });

    const wfCanaryRollback = await prisma.buildWorkflow.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'wf-canary-rollback' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Canary Rollback',
            slug: 'wf-canary-rollback',
            description: 'Rollback workflow for canary deployments',
            status: 'active',
            ownerTeamId: platformCoreTeam.id,
            trigger: { eventType: 'deployment.canary_failed' },
            steps: [
                { action: 'stop_canary', config: {} },
                { action: 'rollback_to_stable', config: { preserveLogs: true } },
                { action: 'notify_team', config: { channels: ['slack'] } },
            ],
            activatedAt: new Date(),
        },
    });

    // Link workflows to services
    await prisma.buildWorkflowService.createMany({
        data: [
            { workflowId: wfStandardDeploy.id, serviceId: paymentsApiService.id },
            { workflowId: wfStandardDeploy.id, serviceId: checkoutWebService.id },
            { workflowId: wfP1Mitigation.id, serviceId: paymentsApiService.id },
            { workflowId: wfP1Mitigation.id, serviceId: checkoutWebService.id },
            { workflowId: wfCanaryRollback.id, serviceId: paymentsApiService.id },
        ],
        skipDuplicates: true,
    });

    // Load workflows from YAML configs using ConfigLoaderService
    console.log('📦 Loading workflows from YAML configs...');
    try {
        const yamlWorkflows = await configLoader.loadAllWorkflows();
        let workflowCount = 0;

        for (const workflow of yamlWorkflows) {
            // Create BuildWorkflow from YAML config
            const createdWorkflow = await prisma.buildWorkflow.upsert({
                where: { tenantId_slug: { tenantId: acmeTenant.id, slug: workflow.id } },
                update: {},
                create: {
                    tenantId: acmeTenant.id,
                    name: workflow.name,
                    slug: workflow.id,
                    description: `Workflow from YAML config: ${workflow.id}`,
                    status: 'active',
                    ownerTeamId: paymentsApiTeam.id, // Default to payments API team
                    trigger: workflow.trigger ? { eventType: workflow.trigger.event || 'workflow.triggered' } : { eventType: 'workflow.triggered' },
                    steps: JSON.parse(JSON.stringify(workflow.steps || [])), // Ensure proper serialization
                    metadata: {
                        ...workflow,
                        source: 'yaml-config',
                        yamlLocation: `config/workflows/${workflow.id}.yaml`,
                    } as any,
                    activatedAt: new Date(),
                },
            });

            // Link workflow to default service if available
            if (paymentsApiService) {
                await prisma.buildWorkflowService.upsert({
                    where: { workflowId_serviceId: { workflowId: createdWorkflow.id, serviceId: paymentsApiService.id } },
                    update: {},
                    create: {
                        workflowId: createdWorkflow.id,
                        serviceId: paymentsApiService.id,
                    },
                });
            }
            workflowCount++;
        }
        console.log(`✅ Loaded ${workflowCount} workflows from YAML configs`);
    } catch (error) {
        console.warn('⚠️ Could not load workflows from YAML configs:', error instanceof Error ? error.message : 'Unknown error');
        console.log('   Continuing with seed...');
    }

    // Link workflows to policies
    await prisma.buildWorkflowPolicy.createMany({
        data: [
            { workflowId: wfStandardDeploy.id, policyId: polApprovalDeploy.id },
            { workflowId: wfP1Mitigation.id, policyId: polAutoMitigate.id },
        ],
        skipDuplicates: true,
    });

    const agentIncidentTriage = await prisma.buildAgent.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'agent-incident-triage-bot' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Incident Triage Bot',
            slug: 'agent-incident-triage-bot',
            description: 'AI-powered incident triage and analysis',
            type: 'incident-triage',
            status: 'active',
            personaId: aiIncidentAgentPersona.id,
            tools: ['metrics', 'incidents', 'logs', 'ticketing'],
            guardrails: {
                maxActionsPerHour: 10,
                requireApprovalFor: ['production_changes', 'data_modifications'],
                allowedEnvironments: ['dev', 'staging', 'prod'],
            },
            activatedAt: new Date(),
        },
    });

    const agentDeploymentAdvisor = await prisma.buildAgent.upsert({
        where: { tenantId_slug: { tenantId: acmeTenant.id, slug: 'agent-deployment-advisor' } },
        update: {},
        create: {
            tenantId: acmeTenant.id,
            name: 'Deployment Advisor',
            slug: 'agent-deployment-advisor',
            description: 'AI agent that advises on deployment strategies',
            type: 'deployment-advisor',
            status: 'active',
            personaId: deploymentAdvisorPersona.id,
            tools: ['ci', 'git', 'metrics', 'deployment_history'],
            guardrails: {
                suggestionsOnly: true,
                requireApprovalFor: ['all_actions'],
                allowedEnvironments: ['dev', 'staging'],
            },
            activatedAt: new Date(),
        },
    });

    // Link agents to services
    await prisma.buildAgentService.createMany({
        data: [
            { agentId: agentIncidentTriage.id, serviceId: paymentsApiService.id },
            { agentId: agentIncidentTriage.id, serviceId: checkoutWebService.id },
            { agentId: agentIncidentTriage.id, serviceId: fraudDetectorService.id },
            { agentId: agentDeploymentAdvisor.id, serviceId: paymentsApiService.id },
            { agentId: agentDeploymentAdvisor.id, serviceId: checkoutWebService.id },
        ],
        skipDuplicates: true,
    });

    console.log('✅ Created Build workflows and agents: wf-standard-prod-deploy, wf-p1-incident-mitigation, wf-canary-rollback, agent-incident-triage-bot, agent-deployment-advisor');

    // Environments for acme-payments
    await prisma.environment.createMany({
        data: [
            { tenantId: acmeTenant.id, key: 'dev', name: 'Development', region: 'us-west-2', healthy: true },
            { tenantId: acmeTenant.id, key: 'staging', name: 'Staging', region: 'us-west-2', healthy: true },
            { tenantId: acmeTenant.id, key: 'prod', name: 'Production', region: 'us-east-1', healthy: true },
        ],
        skipDuplicates: true,
    });

    // Environments for beta-retail (partial)
    await prisma.environment.createMany({
        data: [
            { tenantId: betaTenant.id, key: 'dev', name: 'Development', region: 'us-west-2', healthy: true },
            { tenantId: betaTenant.id, key: 'prod', name: 'Production', region: 'us-east-1', healthy: false },
        ],
        skipDuplicates: true,
    });
    console.log('✅ Created environments for acme-payments and beta-retail');

    // =========================================================================
    // 3. KPIs (matching MSW mockKpis)
    // =========================================================================
    const kpiData = [
        { key: 'deployments', name: 'Deployments', value: 156, unit: '/week', trend: 23, target: 150, direction: 'higher_is_better', category: 'delivery' },
        { key: 'change-failure-rate', name: 'Change Failure Rate', value: 3.2, unit: '%', trend: -12, target: 5, direction: 'lower_is_better', category: 'quality' },
        { key: 'lead-time', name: 'Lead Time', value: 3.2, unit: 'h', trend: -45, target: 4, direction: 'lower_is_better', category: 'delivery' },
        { key: 'mttr', name: 'MTTR', value: 12, unit: 'm', trend: -67, target: 15, direction: 'lower_is_better', category: 'reliability' },
        { key: 'security-issues', name: 'Security Issues', value: 0, unit: 'critical', trend: 0, target: 0, direction: 'lower_is_better', category: 'security' },
        { key: 'cost-savings', name: 'Cost Savings', value: 2400, unit: 'USD/mo', trend: 30, target: 2000, direction: 'higher_is_better', category: 'efficiency' },
    ];

    for (const kd of kpiData) {
        const kpi = await prisma.kpi.upsert({
            where: { key: kd.key },
            update: { value: kd.value, trend: kd.trend },
            create: {
                key: kd.key,
                name: kd.name,
                value: kd.value,
                unit: kd.unit,
                trend: kd.trend,
                target: kd.target,
                direction: kd.direction,
                category: kd.category,
                departmentId: engDept.id,
            },
        });

        // Create time-series data points (10 points over last 10 hours)
        for (let i = 0; i < 10; i++) {
            await prisma.kpiDataPoint.create({
                data: {
                    kpiId: kpi.id,
                    timestamp: hoursAgo(9 - i),
                    value: kd.value + Math.sin(i / 2) * 10,
                },
            });
        }
    }
    console.log('✅ Created KPIs with time-series data');

    // KPI Narratives (matching MSW)
    await prisma.kpiNarrative.createMany({
        data: [
            {
                insight: 'Deployment frequency is trending upward with low change failure rate, indicating healthy delivery pipelines.',
                confidence: 0.92,
                timeRange: '7d',
            },
            {
                insight: 'MTTR remains below target, suggesting effective incident response and remediation practices.',
                confidence: 0.88,
                timeRange: '7d',
            },
        ],
        skipDuplicates: true,
    });
    console.log('✅ Created KPI narratives');

    // =========================================================================
    // 4. ML Models (matching MSW mockModels)
    // =========================================================================
    const churnModel = await prisma.mlModel.upsert({
        where: { key: 'churn-predictor' },
        update: {},
        create: {
            key: 'churn-predictor',
            name: 'Churn Predictor',
            description: 'Predicts customer churn probability',
            type: 'classification',
            status: 'deployed',
            ownerUserId: user.id,
            team: 'Data Science',
            useCase: 'Customer retention',
        },
    });

    const churnVersion = await prisma.mlModelVersion.upsert({
        where: { modelId_version: { modelId: churnModel.id, version: '2.3.1' } },
        update: {},
        create: {
            modelId: churnModel.id,
            version: '2.3.1',
            status: 'current',
            accuracy: 0.92,
            precision: 0.9,
            recall: 0.88,
            f1Score: 0.89,
            latency: 120,
            throughput: 125,
            deployedEnvironment: 'production',
            deployedAt: daysAgo(7),
        },
    });

    // Previous version
    await prisma.mlModelVersion.upsert({
        where: { modelId_version: { modelId: churnModel.id, version: '2.3.0' } },
        update: {},
        create: {
            modelId: churnModel.id,
            version: '2.3.0',
            status: 'previous',
            accuracy: 0.951,
            precision: 0.94,
            recall: 0.93,
            f1Score: 0.935,
            latency: 125,
            throughput: 90,
            deployedEnvironment: 'production',
            deployedAt: daysAgo(30),
        },
    });

    // Feature importances
    await prisma.modelFeatureImportance.createMany({
        data: [
            { modelVersionId: churnVersion.id, featureName: 'feature_a', importance: 0.4, direction: 'positive' },
            { modelVersionId: churnVersion.id, featureName: 'feature_b', importance: 0.3, direction: 'positive' },
            { modelVersionId: churnVersion.id, featureName: 'feature_c', importance: 0.15, direction: 'negative' },
        ],
        skipDuplicates: true,
    });

    // Metrics over time
    for (let i = 0; i < 5; i++) {
        await prisma.mlModelMetric.create({
            data: { modelVersionId: churnVersion.id, name: 'accuracy', value: 0.92 + Math.random() * 0.02, timestamp: hoursAgo(i * 2) },
        });
        await prisma.mlModelMetric.create({
            data: { modelVersionId: churnVersion.id, name: 'latency', value: 110 + Math.random() * 20, timestamp: hoursAgo(i * 2) },
        });
    }

    // Second model (training)
    const fraudModel = await prisma.mlModel.upsert({
        where: { key: 'fraud-detector' },
        update: {},
        create: {
            key: 'fraud-detector',
            name: 'Fraud Detector',
            description: 'Detects fraudulent transactions',
            type: 'classification',
            status: 'training',
            ownerUserId: user.id,
            team: 'Data Science',
            useCase: 'Fraud prevention',
        },
    });

    await prisma.mlModelVersion.upsert({
        where: { modelId_version: { modelId: fraudModel.id, version: '1.0.0' } },
        update: {},
        create: {
            modelId: fraudModel.id,
            version: '1.0.0',
            status: 'current',
            accuracy: 0.78,
            precision: 0.81,
            recall: 0.75,
            f1Score: 0.78,
            latency: 220,
            throughput: 40,
        },
    });
    console.log('✅ Created ML models with versions and metrics');

    // =========================================================================
    // 5. Reports & Schedules
    // =========================================================================
    const weeklyReport = await prisma.reportDefinition.upsert({
        where: { key: 'weekly-kpis' },
        update: {},
        create: {
            key: 'weekly-kpis',
            name: 'Weekly KPI Report',
            description: 'Weekly summary of key performance indicators',
            kpiKeys: ['deployments', 'change-failure-rate', 'lead-time', 'mttr'],
            audience: 'engineering',
        },
    });

    await prisma.reportSchedule.create({
        data: {
            reportId: weeklyReport.id,
            frequency: 'weekly',
            dayOfWeek: 'Monday',
            time: '09:00',
            recipients: ['admin@example.com'],
            formats: ['pdf', 'csv'],
            enabled: true,
            nextRun: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        },
    });
    console.log('✅ Created reports and schedules');

    // =========================================================================
    // 6. Workflows & Executions
    // =========================================================================
    const dailyWorkflow = await prisma.workflow.upsert({
        where: { departmentId_name: { departmentId: engDept.id, name: 'Daily Automation' } },
        update: {},
        create: {
            departmentId: engDept.id,
            name: 'Daily Automation',
            type: 'AUTOMATION',
            status: 'ACTIVE',
            configuration: { schedule: '0 2 * * *' },
            metadata: { description: 'Runs daily data-sync and validations' },
        },
    });

    // Create a trigger
    await prisma.workflowTrigger.create({
        data: {
            workflowId: dailyWorkflow.id,
            type: 'schedule',
            config: { cron: '0 2 * * *' },
            enabled: true,
        },
    });

    // Create a past execution
    const execution = await prisma.workflowExecution.create({
        data: {
            workflowId: dailyWorkflow.id,
            workflowKey: dailyWorkflow.name,
            status: 'completed',
            startedAt: daysAgo(1),
            finishedAt: daysAgo(1),
            logs: ['Execution started', 'Processing items...', 'Completed successfully'],
        },
    });

    await prisma.workflowExecutionStep.createMany({
        data: [
            { executionId: execution.id, stepKey: 'init', name: 'Initialize', status: 'completed', startedAt: daysAgo(1), finishedAt: daysAgo(1) },
            { executionId: execution.id, stepKey: 'sync', name: 'Data Sync', status: 'completed', startedAt: daysAgo(1), finishedAt: daysAgo(1) },
            { executionId: execution.id, stepKey: 'validate', name: 'Validate', status: 'completed', startedAt: daysAgo(1), finishedAt: daysAgo(1) },
        ],
    });
    console.log('✅ Created workflows with executions');

    // =========================================================================
    // 7. Tenants, Environments, Alerts, Anomalies, A/B Tests
    // =========================================================================
    const tenant = await prisma.tenant.upsert({
        where: { key: 'acme-corp' },
        update: {},
        create: {
            key: 'acme-corp',
            name: 'Acme Corporation',
            status: 'active',
            plan: 'enterprise',
        },
    });

    await prisma.environment.createMany({
        data: [
            { tenantId: tenant.id, key: 'dev', name: 'Development', region: 'us-west-2', healthy: true },
            { tenantId: tenant.id, key: 'staging', name: 'Staging', region: 'us-west-2', healthy: true },
            { tenantId: tenant.id, key: 'production', name: 'Production', region: 'us-east-1', healthy: true },
        ],
        skipDuplicates: true,
    });

    await prisma.alert.create({
        data: {
            tenantId: tenant.id,
            severity: 'warning',
            message: 'High latency detected',
            source: 'model-monitor',
            resolved: false,
        },
    });

    await prisma.anomaly.create({
        data: {
            tenantId: tenant.id,
            metric: 'cpu',
            value: 95,
            baselineValue: 45,
            severity: 'high',
            resolved: false,
        },
    });

    await prisma.abTest.create({
        data: {
            tenantId: tenant.id,
            name: 'A/B - models',
            description: 'Compare model versions',
            status: 'running',
            startedAt: daysAgo(3),
            configJson: { variants: ['A', 'B'], trafficSplit: [50, 50] },
        },
    });

    await prisma.trainingJob.create({
        data: {
            tenantId: tenant.id,
            modelId: fraudModel.id,
            status: 'running',
            progress: 42,
            startedAt: hoursAgo(2),
        },
    });
    console.log('✅ Created tenant with environments, alerts, anomalies, A/B tests');

    // =========================================================================
    // 8. Audit Events
    // =========================================================================
    await prisma.auditEvent.createMany({
        data: [
            {
                tenantId: tenant.id,
                actorUserId: user.id,
                entityType: 'workflow',
                entityId: dailyWorkflow.id,
                action: 'execute',
                decision: 'approve',
                reason: 'Approved by reviewer',
                timestamp: daysAgo(1),
            },
            {
                tenantId: tenant.id,
                actorUserId: user.id,
                entityType: 'model',
                entityId: churnModel.id,
                action: 'deploy',
                decision: 'approve',
                reason: 'Model passed validation',
                timestamp: daysAgo(7),
            },
        ],
    });
    console.log('✅ Created audit events');

    // =========================================================================
    // 9. User Journey Data - Approvals, Reviews, Time-off, Budgets, etc.
    // =========================================================================

    // Create additional users for different personas
    const ownerUser = await prisma.user.upsert({
        where: { email: 'owner@example.com' },
        update: {},
        create: {
            email: 'owner@example.com',
            name: 'Sarah Chen (Owner)',
            passwordHash: await bcrypt.hash('demo123', 10),
        },
    });

    const executiveUser = await prisma.user.upsert({
        where: { email: 'executive@example.com' },
        update: {},
        create: {
            email: 'executive@example.com',
            name: 'Michael Rodriguez (CTO)',
            passwordHash: await bcrypt.hash('demo123', 10),
        },
    });

    const managerUser = await prisma.user.upsert({
        where: { email: 'manager@example.com' },
        update: {},
        create: {
            email: 'manager@example.com',
            name: 'Emily Park (Eng Manager)',
            passwordHash: await bcrypt.hash('demo123', 10),
        },
    });

    const icUser1 = await prisma.user.upsert({
        where: { email: 'ic1@example.com' },
        update: {},
        create: {
            email: 'ic1@example.com',
            name: 'Alex Kumar (Senior Engineer)',
            passwordHash: await bcrypt.hash('demo123', 10),
        },
    });

    const icUser2 = await prisma.user.upsert({
        where: { email: 'ic2@example.com' },
        update: {},
        create: {
            email: 'ic2@example.com',
            name: 'Jordan Smith (Engineer)',
            passwordHash: await bcrypt.hash('demo123', 10),
        },
    });

    console.log('✅ Created journey users: owner, executive, manager, ic1, ic2');

    // 9.1 Budgets - Department budgets with different states
    const engBudget = await prisma.budget.upsert({
        where: {
            departmentId_year_quarter: {
                departmentId: engDept.id,
                year: 2025,
                quarter: 'Q1',
            },
        },
        update: {},
        create: {
            departmentId: engDept.id,
            year: 2025,
            quarter: 'Q1',
            allocated: 500000,
            spent: 235000,
            forecasted: 485000,
            categories: {
                salaries: 350000,
                contractors: 80000,
                infrastructure: 50000,
                tools: 20000,
            },
            status: 'ACTIVE',
            notes: 'Q1 engineering budget',
        },
    });

    const opsBudget = await prisma.budget.upsert({
        where: {
            departmentId_year_quarter: {
                departmentId: opsDept.id,
                year: 2025,
                quarter: 'Q1',
            },
        },
        update: {},
        create: {
            departmentId: opsDept.id,
            year: 2025,
            quarter: 'Q1',
            allocated: 300000,
            spent: 145000,
            forecasted: 295000,
            categories: {
                infrastructure: 200000,
                tools: 50000,
                training: 30000,
                other: 20000,
            },
            status: 'ACTIVE',
            notes: 'Q1 operations budget',
        },
    });

    console.log('✅ Created budgets for Engineering and Operations');

    // 9.2 Approvals - Various types in different states
    const restructureApproval = await prisma.approval.create({
        data: {
            type: 'RESTRUCTURE',
            requesterId: ownerUser.id,
            status: 'PENDING',
            data: {
                title: 'Create New Data Science Department',
                description: 'Establish a dedicated data science team',
                changeType: 'department_creation',
                impactedEmployees: 15,
                approvalChain: [
                    { level: 1, role: 'executive', required: true },
                    { level: 2, role: 'owner', required: true },
                ],
            },
            currentStepIndex: 0,
            metadata: { priority: 'high', estimatedCost: 150000 },
        },
    });

    await prisma.approvalStep.create({
        data: {
            approvalId: restructureApproval.id,
            level: 1,
            approverId: executiveUser.id,
            role: 'executive',
            status: 'PENDING',
            comment: null,
        },
    });

    const budgetApproval = await prisma.approval.create({
        data: {
            type: 'BUDGET',
            requesterId: managerUser.id,
            status: 'APPROVED',
            data: {
                title: 'Additional Q1 Budget for Cloud Infrastructure',
                amount: 50000,
                justification: 'Increased demand requires scaling',
                budgetId: engBudget.id,
                approvalChain: [
                    { level: 1, role: 'manager', required: true },
                    { level: 2, role: 'executive', required: true },
                ],
            },
            currentStepIndex: 2,
            completedAt: daysAgo(2),
            metadata: { department: 'Engineering' },
        },
    });

    await prisma.approvalStep.createMany({
        data: [
            {
                approvalId: budgetApproval.id,
                level: 1,
                approverId: managerUser.id,
                role: 'manager',
                status: 'APPROVED',
                decision: 'APPROVED',
                comment: 'Valid request, critical infrastructure needs',
                decidedAt: daysAgo(4),
            },
            {
                approvalId: budgetApproval.id,
                level: 2,
                approverId: executiveUser.id,
                role: 'executive',
                status: 'APPROVED',
                decision: 'APPROVED',
                comment: 'Approved. Monitor usage closely.',
                decidedAt: daysAgo(2),
            },
        ],
    });

    const hireApproval = await prisma.approval.create({
        data: {
            type: 'HIRE',
            requesterId: managerUser.id,
            status: 'REJECTED',
            data: {
                title: 'Hire Senior DevOps Engineer',
                position: 'Senior DevOps Engineer',
                level: 'Senior',
                estimatedSalary: 180000,
                justification: 'Team capacity constraints',
                approvalChain: [
                    { level: 1, role: 'executive', required: true },
                    { level: 2, role: 'owner', required: true },
                ],
            },
            currentStepIndex: 1,
            metadata: { department: 'Operations', headcount: 1 },
        },
    });

    await prisma.approvalStep.create({
        data: {
            approvalId: hireApproval.id,
            level: 1,
            approverId: executiveUser.id,
            role: 'executive',
            status: 'REJECTED',
            decision: 'REJECTED',
            comment: 'Hiring freeze in effect until Q2',
            decidedAt: daysAgo(8),
        },
    });

    console.log('✅ Created approvals: restructure (pending), budget (approved), hire (rejected)');

    // 9.3 Time-off Requests with conflicts
    const timeOff1 = await prisma.timeOffRequest.create({
        data: {
            userId: icUser1.id,
            type: 'VACATION',
            startDate: new Date('2025-02-10'),
            endDate: new Date('2025-02-14'),
            days: 5,
            reason: 'Family vacation',
            status: 'APPROVED',
            conflicts: [],
            createdAt: daysAgo(15),
            updatedAt: daysAgo(12),
        },
    });

    const timeOff2 = await prisma.timeOffRequest.create({
        data: {
            userId: icUser2.id,
            type: 'VACATION',
            startDate: new Date('2025-02-12'),
            endDate: new Date('2025-02-16'),
            days: 5,
            reason: 'Personal travel',
            status: 'PENDING',
            conflicts: [
                {
                    userId: icUser1.id,
                    userName: 'Alex Kumar',
                    dates: ['2025-02-12', '2025-02-13', '2025-02-14'],
                    type: 'VACATION',
                },
            ],
            createdAt: daysAgo(3),
        },
    });

    const timeOff3 = await prisma.timeOffRequest.create({
        data: {
            userId: icUser1.id,
            type: 'SICK',
            startDate: new Date('2025-01-15'),
            endDate: new Date('2025-01-16'),
            days: 2,
            status: 'APPROVED',
            conflicts: [],
            createdAt: daysAgo(25),
        },
    });

    console.log('✅ Created time-off requests: 2 approved, 1 pending with conflict');

    // 9.4 Performance Reviews with AI insights
    const perfReview1 = await prisma.performanceReview.create({
        data: {
            employeeId: icUser1.id,
            reviewerId: managerUser.id,
            period: '2024-Q4',
            status: 'COMPLETED',
            ratings: {
                technical: 4.5,
                collaboration: 4.0,
                delivery: 4.5,
                leadership: 3.5,
                overall: 4.1,
            },
            goals: [
                {
                    title: 'Lead authentication service redesign',
                    status: 'completed',
                    impact: 'high',
                },
                {
                    title: 'Mentor junior engineers',
                    status: 'in_progress',
                    impact: 'medium',
                },
                {
                    title: 'Improve test coverage to 80%',
                    status: 'completed',
                    impact: 'high',
                },
            ],
            feedback: 'Excellent technical execution and growing leadership. Continue mentoring work.',
            aiInsights: {
                strengths: [
                    'Exceptional technical problem solving',
                    'High quality code and documentation',
                    'Proactive in identifying issues',
                ],
                improvements: [
                    'Could increase cross-team collaboration',
                    'Opportunities for more public speaking/sharing',
                ],
                recommendations: [
                    'Consider tech lead role in next quarter',
                    'Lead architecture review sessions',
                ],
                sentiment: 'very_positive',
                keyThemes: ['technical_excellence', 'reliability', 'growth'],
            },
            completedAt: daysAgo(7),
        },
    });

    const perfReview2 = await prisma.performanceReview.create({
        data: {
            employeeId: icUser2.id,
            reviewerId: managerUser.id,
            period: '2024-Q4',
            status: 'IN_PROGRESS',
            ratings: {
                technical: 3.5,
                collaboration: 4.0,
                delivery: 3.5,
                overall: 3.7,
            },
            goals: [
                {
                    title: 'Complete microservices training',
                    status: 'completed',
                    impact: 'medium',
                },
                {
                    title: 'Ship user dashboard feature',
                    status: 'completed',
                    impact: 'high',
                },
            ],
            aiInsights: {
                strengths: ['Strong team player', 'Good communication', 'Steady delivery'],
                improvements: ['Deepen technical skills', 'Take on more complex tasks'],
                recommendations: ['Pair with senior engineers on architecture', 'Lead smaller features'],
                sentiment: 'positive',
                keyThemes: ['collaboration', 'reliability', 'growth_potential'],
            },
        },
    });

    console.log('✅ Created performance reviews: 1 completed, 1 in-progress (with AI insights)');

    // 9.5 Notifications for all personas
    await prisma.notification.createMany({
        data: [
            {
                userId: ownerUser.id,
                type: 'APPROVAL_REQUEST',
                title: 'Restructure Approval Pending',
                message: 'Department creation requires your approval',
                link: `/approvals/${restructureApproval.id}`,
                priority: 'HIGH',
                read: false,
                metadata: { approvalType: 'RESTRUCTURE' },
            },
            {
                userId: executiveUser.id,
                type: 'APPROVAL_REQUEST',
                title: 'New Restructure Proposal',
                message: 'Sarah Chen proposed creating a Data Science department',
                link: `/approvals/${restructureApproval.id}`,
                priority: 'HIGH',
                read: false,
                metadata: { approvalType: 'RESTRUCTURE' },
            },
            {
                userId: managerUser.id,
                type: 'TIME_OFF_REQUEST',
                title: 'Time-off Request from Jordan Smith',
                message: 'Vacation request Feb 12-16 (conflicts with Alex Kumar)',
                link: `/time-off/${timeOff2.id}`,
                priority: 'MEDIUM',
                read: false,
                metadata: { hasConflict: true },
            },
            {
                userId: icUser1.id,
                type: 'REVIEW_COMPLETED',
                title: 'Your Q4 Performance Review is Ready',
                message: 'Emily Park completed your performance review',
                link: `/reviews/${perfReview1.id}`,
                priority: 'MEDIUM',
                read: false,
            },
            {
                userId: icUser2.id,
                type: 'TIME_OFF_CONFLICT',
                title: 'Time-off Conflict Detected',
                message: 'Your request conflicts with Alex Kumar (Feb 12-14)',
                link: `/time-off/${timeOff2.id}`,
                priority: 'LOW',
                read: true,
                readAt: hoursAgo(2),
            },
        ],
    });

    console.log('✅ Created notifications for all personas');

    // 9.6 Comments on approvals and reviews
    await prisma.comment.createMany({
        data: [
            {
                entityType: 'approval',
                entityId: restructureApproval.id,
                authorId: executiveUser.id,
                content: 'What is the expected headcount and budget impact?',
                mentions: [ownerUser.id],
                metadata: { thread: 'questions' },
            },
            {
                entityType: 'approval',
                entityId: restructureApproval.id,
                authorId: ownerUser.id,
                content: '@michael We expect 10-15 people, estimated $150K quarterly budget.',
                mentions: [executiveUser.id],
                metadata: { thread: 'questions' },
            },
            {
                entityType: 'review',
                entityId: perfReview1.id,
                authorId: icUser1.id,
                content: 'Thank you for the feedback! Excited about the tech lead opportunity.',
                mentions: [managerUser.id],
            },
            {
                entityType: 'approval',
                entityId: budgetApproval.id,
                authorId: managerUser.id,
                content: 'Usage tracking dashboard set up, will monitor weekly.',
                mentions: [executiveUser.id],
                metadata: { followUp: true },
            },
        ],
    });

    console.log('✅ Created comments on approvals and reviews');

    // 9.7 Restructure proposals
    const restructure1 = await prisma.restructure.create({
        data: {
            proposerId: ownerUser.id,
            title: 'Create Data Science Department',
            description: 'Establish dedicated data science function',
            status: 'PENDING_APPROVAL',
            type: 'DEPARTMENT_CREATION',
            changeData: {
                departmentName: 'Data Science',
                departmentType: 'ENGINEERING',
                initialHeadcount: 10,
                reportingTo: 'CTO',
                transferredEmployees: [],
                newPositions: [
                    { title: 'VP of Data Science', level: 'executive', count: 1 },
                    { title: 'Senior Data Scientist', level: 'senior', count: 4 },
                    { title: 'Data Scientist', level: 'mid', count: 5 },
                ],
            },
            impactAnalysis: {
                employeesImpacted: 15,
                budgetImpact: 150000,
                timelineWeeks: 8,
                risks: ['Recruiting timeline', 'Budget allocation'],
                benefits: ['Better ML capabilities', 'Faster experimentation', 'Dedicated ownership'],
                dependencies: ['Budget approval', 'Office space allocation'],
                effectiveDate: '2025-03-01',
            },
            approvalId: restructureApproval.id,
            createdAt: daysAgo(2),
        },
    });

    const restructure2 = await prisma.restructure.create({
        data: {
            proposerId: executiveUser.id,
            title: 'Merge Platform and SRE Teams',
            description: 'Consolidate infrastructure teams for better efficiency',
            status: 'DRAFT',
            type: 'TEAM_MERGER',
            changeData: {
                sourceDepartments: ['SRE', 'Operations'],
                targetDepartment: 'Platform Engineering',
                affectedTeams: ['platform-core', 'sre-core'],
                newTeamStructure: {
                    name: 'Unified Platform Team',
                    leads: 2,
                    engineers: 18,
                },
            },
            impactAnalysis: {
                employeesImpacted: 20,
                budgetImpact: -25000,
                timelineWeeks: 12,
                risks: ['Cultural integration', 'Tooling consolidation'],
                benefits: ['Reduced overhead', 'Better collaboration', 'Unified tooling'],
                dependencies: ['Leadership alignment', 'Team surveys'],
                confidential: true,
            },
        },
    });

    console.log('✅ Created restructure proposals: 1 pending approval, 1 draft');

    // 9.8 Growth Plans for ICs
    const growthPlan1 = await prisma.growthPlan.create({
        data: {
            userId: icUser1.id,
            title: 'Path to Staff Engineer',
            period: '2025-Q1-Q2',
            status: 'ACTIVE',
            goals: [
                {
                    title: 'Lead cross-team technical initiative',
                    description: 'Drive API standardization across 3 teams',
                    targetDate: '2025-06-30',
                    status: 'in_progress',
                    progress: 30,
                },
                {
                    title: 'Mentorship program',
                    description: 'Mentor 2 junior engineers',
                    targetDate: '2025-06-30',
                    status: 'in_progress',
                    progress: 50,
                },
                {
                    title: 'Architecture review board participation',
                    description: 'Regular participant in architecture reviews',
                    targetDate: '2025-04-30',
                    status: 'completed',
                    progress: 100,
                },
            ],
            skills: [
                { name: 'System Design', current: 4, target: 5, category: 'technical' },
                { name: 'Technical Leadership', current: 3, target: 4, category: 'leadership' },
                { name: 'Architecture Patterns', current: 4, target: 5, category: 'technical' },
                { name: 'Mentoring', current: 3, target: 4, category: 'leadership' },
            ],
            resources: [
                { type: 'course', title: 'Advanced System Design', url: 'https://course.example.com' },
                { type: 'book', title: 'Staff Engineer Leadership Guide' },
                { type: 'conference', title: 'QCon SF 2025', approved: true },
            ],
            progress: 45,
        },
    });

    const growthPlan2 = await prisma.growthPlan.create({
        data: {
            userId: icUser2.id,
            title: 'Senior Engineer Development Plan',
            period: '2025-H1',
            status: 'ACTIVE',
            goals: [
                {
                    title: 'Lead feature development end-to-end',
                    description: 'Own user analytics dashboard from design to deployment',
                    targetDate: '2025-05-31',
                    status: 'in_progress',
                    progress: 20,
                },
                {
                    title: 'Improve system design skills',
                    description: 'Complete system design course and apply to real projects',
                    targetDate: '2025-04-30',
                    status: 'in_progress',
                    progress: 40,
                },
            ],
            skills: [
                { name: 'System Design', current: 2, target: 4, category: 'technical' },
                { name: 'Backend Development', current: 3, target: 4, category: 'technical' },
                { name: 'Code Review', current: 3, target: 4, category: 'technical' },
            ],
            resources: [
                { type: 'course', title: 'System Design Fundamentals', url: 'https://course.example.com' },
                { type: 'mentorship', title: 'Pairing with Alex Kumar' },
            ],
            progress: 30,
        },
    });

    console.log('✅ Created growth plans for ICs: 2 active development plans');

    // 9.9 Incidents for tracking and escalation
    const incident1 = await prisma.incident.create({
        data: {
            title: 'Production API Latency Spike',
            description: 'Payments API p95 latency increased from 200ms to 2000ms',
            severity: 'HIGH',
            status: 'INVESTIGATING',
            reporterId: icUser1.id,
            assigneeId: icUser2.id,
            timeline: [
                {
                    timestamp: hoursAgo(3).toISOString(),
                    event: 'Incident detected by monitoring',
                    actor: 'system',
                },
                {
                    timestamp: hoursAgo(3).toISOString(),
                    event: 'On-call engineer paged',
                    actor: 'system',
                },
                {
                    timestamp: hoursAgo(2.5).toISOString(),
                    event: 'Investigation started',
                    actor: icUser1.id,
                    notes: 'Checking database connection pool',
                },
                {
                    timestamp: hoursAgo(2).toISOString(),
                    event: 'Root cause identified - DB connection leak',
                    actor: icUser1.id,
                },
            ],
            metadata: {
                alertId: 'alert-12345',
                runbookUrl: 'https://runbook.example.com/api-latency',
                slackChannel: '#incidents',
                impactedServices: ['payments-api', 'checkout-web'],
            },
        },
    });

    const incident2 = await prisma.incident.create({
        data: {
            title: 'Authentication Service Outage',
            description: 'Complete auth service outage affecting all customers',
            severity: 'CRITICAL',
            status: 'RESOLVED',
            reporterId: icUser2.id,
            assigneeId: icUser1.id,
            timeline: [
                {
                    timestamp: daysAgo(5).toISOString(),
                    event: 'Service stopped responding',
                    actor: 'system',
                },
                {
                    timestamp: daysAgo(5).toISOString(),
                    event: 'Incident declared',
                    actor: icUser2.id,
                },
                {
                    timestamp: daysAgo(4.9).toISOString(),
                    event: 'Root cause: cert expiration',
                    actor: icUser1.id,
                },
                {
                    timestamp: daysAgo(4.8).toISOString(),
                    event: 'Certificate renewed and deployed',
                    actor: icUser1.id,
                },
                {
                    timestamp: daysAgo(4.7).toISOString(),
                    event: 'Service restored',
                    actor: 'system',
                },
            ],
            rootCause: 'SSL certificate expired - monitoring gap',
            remediation: 'Renewed certificate, added cert expiration monitoring',
            resolvedAt: daysAgo(4.7),
            metadata: {
                postmortemUrl: 'https://postmortem.example.com/auth-outage-2025-01',
                customersImpacted: 15000,
                downtimeMinutes: 180,
                impactedServices: ['auth-service', 'all-dependent-services'],
            },
        },
    });

    const incident3 = await prisma.incident.create({
        data: {
            title: 'Fraud Detection Model Degradation',
            description: 'Fraud detection accuracy dropped from 98% to 85%',
            severity: 'MEDIUM',
            status: 'MONITORING',
            reporterId: managerUser.id,
            assigneeId: icUser1.id,
            timeline: [
                {
                    timestamp: daysAgo(2).toISOString(),
                    event: 'Model performance degradation detected',
                    actor: 'system',
                },
                {
                    timestamp: daysAgo(1.5).toISOString(),
                    event: 'Analysis: data drift in input features',
                    actor: icUser1.id,
                },
                {
                    timestamp: daysAgo(1).toISOString(),
                    event: 'Mitigation: rolled back to previous model version',
                    actor: icUser1.id,
                },
            ],
            rootCause: 'Data distribution shift in transaction patterns',
            remediation: 'Rollback to v2.1, retraining v2.3 with new data',
            metadata: {
                modelVersion: 'v2.2',
                falsePositiveRate: 0.15,
                retrainingETA: '2025-01-20',
                impactedServices: ['fraud-detector'],
            },
        },
    });

    console.log('✅ Created incidents: 1 investigating, 1 resolved, 1 monitoring');

    // =========================================================================
    // Done
    // =========================================================================
    console.log('');
    console.log('🎉 Seed complete!');
    console.log('');
    console.log('📧 Test credentials:');
    console.log('   Owner: owner@example.com / demo123');
    console.log('   Executive: executive@example.com / demo123');
    console.log('   Manager: manager@example.com / demo123');
    console.log('   IC (Senior): ic1@example.com / demo123');
    console.log('   IC: ic2@example.com / demo123');
    console.log('   Admin: admin@example.com / demo123');
    console.log('');
    console.log('📧 Test credentials:');
    console.log('   Email: admin@example.com');
    console.log('   Password: demo123');
    console.log('');
}

main()
    .catch((e) => {
        console.error('❌ Seed failed:', e);
        process.exit(1);
    })
    .finally(async () => {
        await prisma.$disconnect();
    });
