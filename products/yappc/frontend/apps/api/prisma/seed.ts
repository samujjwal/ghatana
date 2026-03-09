/**
 * Comprehensive Seed Script
 *
 * Populates the database with comprehensive demo data for end-to-end testing.
 * Covers ALL models: Users, Workspaces, Projects, Canvas Documents, Pages,
 * Workflows, Phases, Items, Milestones, AI Insights, Predictions, Anomalies,
 * Compliance, Activity Logs, Copilot Sessions, and more.
 *
 * Run with: ./node_modules/.bin/prisma db push && npx tsx prisma/seed.ts
 */

import { PrismaClient } from '@prisma/client';
import {
  Role,
  ItemType,
  ItemPriority,
  ItemStatus,
  InsightType,
  InsightSeverity,
  InsightStatus,
  ProjectType,
  ProjectStatus,
  WorkflowType,
  WorkflowStatus,
  WorkflowStep,
  AIMode,
  AuditAction,
  PhaseStatus,
  MilestoneStatus,
  ArtifactType,
  IntegrationProvider,
  KPICategory,
  TrendDirection,
  PredictionTarget,
  PredictionType,
  AnomalyType,
  AnomalySeverity,
  SessionStatus,
  ExecutionStatus,
  DependencyType,
} from '@prisma/client';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Try loading from local .env
dotenv.config();

// If not found, try loading from root .env
if (!process.env.DATABASE_URL) {
  const rootEnvPath = path.resolve(__dirname, '../../../../../../.env');
  console.log(`Loading .env from ${rootEnvPath}`);
  dotenv.config({ path: rootEnvPath });
}

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  throw new Error('DATABASE_URL is not defined');
}

console.log(
  `🔗 Connecting to database: ${connectionString.replace(/:[^:@]+@/, ':****@')}`
);

const pool = new Pool({
  connectionString,
  // Ensure fresh connections, no stale pool
  max: 1,
  idleTimeoutMillis: 1000,
  connectionTimeoutMillis: 5000,
});

const adapter = new PrismaPg(pool);
const prisma = new PrismaClient({ adapter });

async function main() {
  // Test connection and verify schema before proceeding
  try {
    const client = await pool.connect();
    const result = await client.query(
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
    );
    console.log(`✓ Connected to database with ${result.rows[0].count} tables`);

    // Verify critical tables exist
    const criticalTables = await client.query(`
      SELECT table_name FROM information_schema.tables 
      WHERE table_schema = 'public' 
      AND table_name IN ('User', 'Workspace', 'Project', 'AIMetric')
      ORDER BY table_name
    `);

    if (criticalTables.rows.length < 4) {
      throw new Error(
        `Missing critical tables! Found: ${criticalTables.rows.map((r) => r.table_name).join(', ')}`
      );
    }

    console.log(
      `✓ Verified critical tables: ${criticalTables.rows.map((r) => r.table_name).join(', ')}`
    );
    client.release();
  } catch (error) {
    console.error('❌ Failed to verify database schema:', error);
    throw error;
  }

  console.log('🌱 Starting comprehensive database seed...');
  console.log('📦 Seeding lifecycle-specific data for Lifecycle Hub...');

  // ============================================================================
  // 1. CLEANUP - Remove existing data in reverse order of dependencies
  // ============================================================================
  console.log('🧹 Cleaning up existing data...');

  // AI & Analytics
  await prisma.aIMetric.deleteMany();
  await prisma.agentExecution.deleteMany();
  await prisma.copilotSession.deleteMany();
  await prisma.aIInsight.deleteMany();
  await prisma.anomalyAlert.deleteMany();
  await prisma.prediction.deleteMany();
  await prisma.phasePrediction.deleteMany();
  await prisma.vectorEmbedding.deleteMany();
  await prisma.itemEmbedding.deleteMany();

  // Compliance
  await prisma.reportSchedule.deleteMany();
  await prisma.complianceReport.deleteMany();
  await prisma.remediationStep.deleteMany();
  await prisma.remediationPlan.deleteMany();
  await prisma.complianceAssessment.deleteMany();

  // Activity & Audit
  await prisma.activityLog.deleteMany();
  await prisma.auditLogEntry.deleteMany();
  await prisma.rateLimitConfig.deleteMany();
  await prisma.upgradeRequest.deleteMany();
  await prisma.flowState.deleteMany();

  // Workflows
  await prisma.aIGeneratedPlan.deleteMany();
  await prisma.workflowAudit.deleteMany();
  await prisma.workflowContributor.deleteMany();
  await prisma.workflow.deleteMany();
  await prisma.workflowTemplate.deleteMany();

  // Items & Related
  await prisma.itemComment.deleteMany();
  await prisma.itemDependency.deleteMany();
  await prisma.itemIntegration.deleteMany();
  await prisma.artifact.deleteMany();
  await prisma.itemTag.deleteMany();
  await prisma.itemOwner.deleteMany();
  await prisma.item.deleteMany();

  // Phases
  await prisma.phaseKPI.deleteMany();
  await prisma.milestone.deleteMany();
  await prisma.phase.deleteMany();

  // Documents & Pages
  await prisma.canvasDocument.deleteMany();
  await prisma.page.deleteMany();

  // Projects
  await prisma.workspaceProject.deleteMany();
  await prisma.project.deleteMany();

  // Workspaces & Users
  await prisma.workspaceMember.deleteMany();
  await prisma.workspace.deleteMany();
  await prisma.userAIPreferences.deleteMany();
  await prisma.user.deleteMany();

  // ============================================================================
  // 2. USERS - Create multiple users with different roles
  // ============================================================================
  console.log('👤 Creating users...');

  const adminUser = await prisma.user.create({
    data: {
      email: 'admin@yappc.com',
      name: 'Admin User',
      role: Role.ADMIN,
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin',
      aiPreferences: {
        create: {
          enableAISuggestions: true,
          enablePredictions: true,
          enableCopilot: true,
          preferredModel: 'gpt-4',
          temperature: 0.7,
          maxTokens: 4096,
          notificationLevel: 'all',
        },
      },
    },
  });

  const devUser = await prisma.user.create({
    data: {
      email: 'developer@yappc.com',
      name: 'Developer User',
      role: Role.EDITOR,
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=developer',
      aiPreferences: {
        create: {
          enableAISuggestions: true,
          enablePredictions: true,
          enableCopilot: true,
          preferredModel: 'gpt-4',
        },
      },
    },
  });

  const securityUser = await prisma.user.create({
    data: {
      email: 'security@yappc.com',
      name: 'Security Analyst',
      role: Role.EDITOR,
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=security',
      aiPreferences: {
        create: {
          enableAISuggestions: true,
          enablePredictions: true,
          enableCopilot: false,
          preferredModel: 'gpt-4',
        },
      },
    },
  });

  const viewerUser = await prisma.user.create({
    data: {
      email: 'viewer@yappc.com',
      name: 'Viewer User',
      role: Role.VIEWER,
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=viewer',
    },
  });

  // ============================================================================
  // 3. WORKSPACES - Create workspaces with members
  // ============================================================================
  console.log('🏢 Creating workspaces...');

  const mainWorkspace = await prisma.workspace.create({
    data: {
      name: 'Engineering Team',
      description: 'Main engineering workspace for all projects',
      ownerId: adminUser.id,
      isDefault: true,
      aiSummary:
        'Primary workspace containing e-commerce and internal tools projects',
      aiTags: ['engineering', 'production', 'main'],
      members: {
        create: [
          { userId: adminUser.id, role: Role.ADMIN },
          { userId: devUser.id, role: Role.EDITOR },
          { userId: securityUser.id, role: Role.EDITOR },
          { userId: viewerUser.id, role: Role.VIEWER },
        ],
      },
    },
  });

  const securityWorkspace = await prisma.workspace.create({
    data: {
      name: 'Security Operations',
      description: 'Security-focused workspace for audits and compliance',
      ownerId: securityUser.id,
      isDefault: false,
      aiTags: ['security', 'compliance', 'audit'],
      members: {
        create: [
          { userId: securityUser.id, role: Role.ADMIN },
          { userId: adminUser.id, role: Role.EDITOR },
        ],
      },
    },
  });

  // Third workspace for mobile projects
  const mobileWorkspace = await prisma.workspace.create({
    data: {
      name: 'Mobile Development',
      description: 'Workspace for mobile application projects',
      ownerId: devUser.id,
      isDefault: false,
      aiTags: ['mobile', 'ios', 'android', 'react-native'],
      members: {
        create: [
          { userId: devUser.id, role: Role.ADMIN },
          { userId: adminUser.id, role: Role.EDITOR },
          { userId: viewerUser.id, role: Role.VIEWER },
        ],
      },
    },
  });

  // Fourth workspace for internal tools
  const internalToolsWorkspace = await prisma.workspace.create({
    data: {
      name: 'Internal Tools',
      description: 'Workspace for internal dashboards and admin tools',
      ownerId: adminUser.id,
      isDefault: false,
      aiTags: ['internal', 'tools', 'admin', 'dashboard'],
      members: {
        create: [
          { userId: adminUser.id, role: Role.ADMIN },
          { userId: devUser.id, role: Role.EDITOR },
        ],
      },
    },
  });

  // ============================================================================
  // 4. PROJECTS - Create multiple projects with different types and statuses
  // ============================================================================
  console.log('🚀 Creating projects...');

  const ecommerceProject = await prisma.project.create({
    data: {
      name: 'E-Commerce Platform',
      description:
        'Next-generation e-commerce solution with AI recommendations',
      ownerWorkspaceId: mainWorkspace.id,
      createdById: adminUser.id,
      type: ProjectType.FULL_STACK,
      status: ProjectStatus.ACTIVE,
      lifecyclePhase: 'RUN', // Production deployment
      isDefault: true,
      aiSummary:
        'High-priority e-commerce platform with microservices architecture',
      aiNextActions: [
        'Complete authentication module',
        'Setup CI/CD pipeline',
        'Performance testing',
      ],
      aiHealthScore: 78,
    },
  });

  const mobileAppProject = await prisma.project.create({
    data: {
      name: 'Mobile Shopping App',
      description: 'iOS and Android mobile application for e-commerce',
      ownerWorkspaceId: mobileWorkspace.id,
      createdById: devUser.id,
      type: ProjectType.MOBILE,
      status: ProjectStatus.ACTIVE,
      lifecyclePhase: 'SHAPE', // Active development in canvas
      isDefault: true,
      aiSummary: 'Cross-platform mobile app using React Native',
      aiHealthScore: 85,
    },
  });

  const adminDashboardProject = await prisma.project.create({
    data: {
      name: 'Admin Dashboard',
      description: 'Internal admin dashboard for managing the platform',
      ownerWorkspaceId: internalToolsWorkspace.id,
      createdById: adminUser.id,
      type: ProjectType.UI,
      status: ProjectStatus.DRAFT,
      lifecyclePhase: 'VALIDATE', // In validation phase
      isDefault: true,
      aiSummary: 'React-based admin panel with real-time analytics',
      aiHealthScore: 65,
    },
  });

  const securityAuditProject = await prisma.project.create({
    data: {
      name: 'Security Audit Q4',
      description: 'Quarterly security audit and penetration testing',
      ownerWorkspaceId: securityWorkspace.id,
      createdById: securityUser.id,
      type: ProjectType.BACKEND,
      status: ProjectStatus.ACTIVE,
      lifecyclePhase: 'IMPROVE', // Continuous improvement
      isDefault: true,
      aiSummary: 'Comprehensive security assessment of all production systems',
      aiHealthScore: 92,
    },
  });

  // Include e-commerce project in security workspace (read-only)
  await prisma.workspaceProject.create({
    data: {
      workspaceId: securityWorkspace.id,
      projectId: ecommerceProject.id,
      addedById: securityUser.id,
      aiInclusionReason: 'Required for security audit review',
    },
  });

  // Include mobile app in main workspace for cross-team visibility
  await prisma.workspaceProject.create({
    data: {
      workspaceId: mainWorkspace.id,
      projectId: mobileAppProject.id,
      addedById: adminUser.id,
      aiInclusionReason: 'Related mobile companion app for e-commerce platform',
    },
  });

  // Include admin dashboard in main workspace
  await prisma.workspaceProject.create({
    data: {
      workspaceId: mainWorkspace.id,
      projectId: adminDashboardProject.id,
      addedById: adminUser.id,
      aiInclusionReason: 'Internal tooling for platform management',
    },
  });

  // ============================================================================
  // 5. CANVAS DOCUMENTS - Create design documents
  // ============================================================================
  console.log('🎨 Creating canvas documents...');

  await prisma.canvasDocument.create({
    data: {
      projectId: ecommerceProject.id,
      createdById: adminUser.id,
      name: 'System Architecture',
      description:
        'High-level architecture diagram for the e-commerce platform',
      content: JSON.stringify({
        nodes: [
          { id: '1', type: 'service', label: 'API Gateway', x: 100, y: 100 },
          { id: '2', type: 'service', label: 'Auth Service', x: 300, y: 100 },
          {
            id: '3',
            type: 'service',
            label: 'Product Service',
            x: 300,
            y: 250,
          },
          { id: '4', type: 'database', label: 'PostgreSQL', x: 500, y: 175 },
          { id: '5', type: 'cache', label: 'Redis Cache', x: 500, y: 50 },
        ],
        edges: [
          { source: '1', target: '2', label: 'auth' },
          { source: '1', target: '3', label: 'products' },
          { source: '2', target: '4', label: 'persist' },
          { source: '3', target: '4', label: 'persist' },
          { source: '2', target: '5', label: 'cache' },
        ],
        viewport: { zoom: 1, x: 0, y: 0 },
      }),
    },
  });

  await prisma.canvasDocument.create({
    data: {
      projectId: ecommerceProject.id,
      createdById: devUser.id,
      name: 'User Flow Diagram',
      description: 'User journey from landing page to checkout',
      content: JSON.stringify({
        nodes: [
          { id: '1', type: 'screen', label: 'Landing Page', x: 50, y: 100 },
          { id: '2', type: 'screen', label: 'Product List', x: 200, y: 100 },
          { id: '3', type: 'screen', label: 'Product Detail', x: 350, y: 100 },
          { id: '4', type: 'screen', label: 'Cart', x: 500, y: 100 },
          { id: '5', type: 'screen', label: 'Checkout', x: 650, y: 100 },
        ],
        edges: [
          { source: '1', target: '2' },
          { source: '2', target: '3' },
          { source: '3', target: '4' },
          { source: '4', target: '5' },
        ],
        viewport: { zoom: 0.8, x: 0, y: 0 },
      }),
    },
  });

  // ============================================================================
  // 6. PAGES - Create page builder pages
  // ============================================================================
  console.log('📄 Creating pages...');

  await prisma.page.create({
    data: {
      projectId: ecommerceProject.id,
      createdById: devUser.id,
      name: 'Home Page',
      path: '/',
      layout: 'flex',
      content: JSON.stringify({
        components: [
          { type: 'header', props: { title: 'Welcome to Our Store' } },
          { type: 'hero', props: { image: '/hero.jpg', cta: 'Shop Now' } },
          { type: 'productGrid', props: { columns: 4, limit: 8 } },
          { type: 'footer', props: {} },
        ],
      }),
    },
  });

  await prisma.page.create({
    data: {
      projectId: ecommerceProject.id,
      createdById: devUser.id,
      name: 'Product Detail',
      path: '/product/:id',
      layout: 'flex',
      content: JSON.stringify({
        components: [
          { type: 'breadcrumb', props: {} },
          { type: 'productDetail', props: {} },
          { type: 'relatedProducts', props: { limit: 4 } },
        ],
      }),
    },
  });

  // ============================================================================
  // 7. DEVSECOPS PHASES - Create lifecycle phases with KPIs
  // ============================================================================
  console.log('📅 Creating DevSecOps phases...');

  const planningPhase = await prisma.phase.create({
    data: {
      key: 'planning',
      title: 'Planning',
      description: 'Requirements gathering, design, and sprint planning',
      order: 0,
      color: '#3b82f6',
      icon: 'clipboard-list',
      status: PhaseStatus.COMPLETED,
      healthScore: 95,
      riskScore: 0.1,
      kpis: {
        create: [
          {
            name: 'Requirements Coverage',
            category: KPICategory.QUALITY,
            value: 98,
            target: 100,
            unit: '%',
          },
          {
            name: 'Story Points Planned',
            category: KPICategory.VELOCITY,
            value: 42,
            target: 40,
            unit: 'points',
          },
        ],
      },
    },
  });

  const devPhase = await prisma.phase.create({
    data: {
      key: 'development',
      title: 'Development',
      description: 'Coding, unit testing, and code review',
      order: 1,
      color: '#10b981',
      icon: 'code',
      status: PhaseStatus.ACTIVE,
      healthScore: 78,
      riskScore: 0.25,
      kpis: {
        create: [
          {
            name: 'Code Coverage',
            category: KPICategory.QUALITY,
            value: 82,
            target: 85,
            unit: '%',
            trendDirection: TrendDirection.UP,
            trendPercentage: 3.5,
          },
          {
            name: 'Sprint Velocity',
            category: KPICategory.VELOCITY,
            value: 38,
            target: 42,
            unit: 'points',
            trendDirection: TrendDirection.DOWN,
            trendPercentage: -5.2,
          },
          {
            name: 'PR Cycle Time',
            category: KPICategory.VELOCITY,
            value: 4.2,
            target: 2,
            unit: 'hours',
            warningThreshold: 4,
            criticalThreshold: 8,
          },
        ],
      },
    },
  });

  const securityPhase = await prisma.phase.create({
    data: {
      key: 'security',
      title: 'Security',
      description:
        'Security scanning, vulnerability assessment, and compliance',
      order: 2,
      color: '#8b5cf6',
      icon: 'shield-check',
      status: PhaseStatus.AT_RISK,
      healthScore: 65,
      riskScore: 0.45,
      kpis: {
        create: [
          {
            name: 'Critical Vulnerabilities',
            category: KPICategory.SECURITY,
            value: 3,
            target: 0,
            unit: 'count',
            criticalThreshold: 1,
          },
          {
            name: 'SAST Coverage',
            category: KPICategory.SECURITY,
            value: 78,
            target: 95,
            unit: '%',
            warningThreshold: 80,
          },
          {
            name: 'DAST Scan Status',
            category: KPICategory.SECURITY,
            value: 1,
            target: 1,
            unit: 'status',
          },
        ],
      },
    },
  });

  const opsPhase = await prisma.phase.create({
    data: {
      key: 'operations',
      title: 'Operations',
      description: 'Deployment, monitoring, and incident management',
      order: 3,
      color: '#f59e0b',
      icon: 'server',
      status: PhaseStatus.ACTIVE,
      healthScore: 88,
      riskScore: 0.15,
      kpis: {
        create: [
          {
            name: 'Deployment Frequency',
            category: KPICategory.OPERATIONS,
            value: 12,
            target: 10,
            unit: 'deploys/week',
            trendDirection: TrendDirection.UP,
          },
          {
            name: 'MTTR',
            category: KPICategory.OPERATIONS,
            value: 45,
            target: 30,
            unit: 'minutes',
            warningThreshold: 60,
          },
          {
            name: 'Uptime',
            category: KPICategory.OPERATIONS,
            value: 99.95,
            target: 99.9,
            unit: '%',
          },
        ],
      },
    },
  });

  // ============================================================================
  // 8. MILESTONES - Create milestones for phases
  // ============================================================================
  console.log('🎯 Creating milestones...');

  await prisma.milestone.create({
    data: {
      phaseId: planningPhase.id,
      title: 'Requirements Sign-off',
      description: 'All stakeholders approve final requirements',
      dueDate: new Date('2025-01-15'),
      status: MilestoneStatus.COMPLETED,
      progress: 100,
      ownerId: adminUser.id,
    },
  });

  await prisma.milestone.create({
    data: {
      phaseId: devPhase.id,
      title: 'API Gateway Complete',
      description: 'All API endpoints implemented and tested',
      dueDate: new Date('2025-02-01'),
      status: MilestoneStatus.IN_PROGRESS,
      progress: 75,
      ownerId: devUser.id,
      predictedCompletionDate: new Date('2025-02-05'),
      riskScore: 0.3,
    },
  });

  await prisma.milestone.create({
    data: {
      phaseId: securityPhase.id,
      title: 'Security Audit Complete',
      description: 'All critical and high vulnerabilities remediated',
      dueDate: new Date('2025-02-15'),
      status: MilestoneStatus.AT_RISK,
      progress: 40,
      ownerId: securityUser.id,
      predictedCompletionDate: new Date('2025-02-25'),
      riskScore: 0.65,
    },
  });

  await prisma.milestone.create({
    data: {
      phaseId: opsPhase.id,
      title: 'Production Launch',
      description: 'Full production deployment with monitoring',
      dueDate: new Date('2025-03-01'),
      status: MilestoneStatus.PENDING,
      progress: 0,
      ownerId: adminUser.id,
    },
  });

  // ============================================================================
  // 9. ITEMS - Create comprehensive work items across phases
  // ============================================================================
  console.log('📝 Creating items...');

  // Planning Items
  const architectureItem = await prisma.item.create({
    data: {
      title: 'Design System Architecture',
      description:
        'Define the core architectural components, data flow, and integration patterns.',
      type: ItemType.EPIC,
      priority: ItemPriority.HIGH,
      status: ItemStatus.COMPLETED,
      phaseId: planningPhase.id,
      progress: 100,
      estimatedHours: 40,
      actualHours: 38,
      completedAt: new Date('2025-01-10'),
      owners: { create: [{ userId: adminUser.id, role: 'LEAD' }] },
      tags: { create: [{ tag: 'architecture' }, { tag: 'planning' }] },
    },
  });

  await prisma.item.create({
    data: {
      title: 'Gather Product Requirements',
      description:
        'Interview stakeholders and document all functional and non-functional requirements.',
      type: ItemType.STORY,
      priority: ItemPriority.HIGH,
      status: ItemStatus.COMPLETED,
      phaseId: planningPhase.id,
      progress: 100,
      estimatedHours: 24,
      actualHours: 28,
      completedAt: new Date('2025-01-08'),
      parentId: architectureItem.id,
      owners: { create: [{ userId: adminUser.id }] },
      tags: { create: [{ tag: 'requirements' }] },
    },
  });

  // Development Items
  const authApiItem = await prisma.item.create({
    data: {
      title: 'Implement User Authentication API',
      description:
        'Create JWT-based authentication with OAuth2 support. Includes login, register, refresh token, and password reset endpoints.',
      type: ItemType.FEATURE,
      priority: ItemPriority.CRITICAL,
      status: ItemStatus.IN_PROGRESS,
      phaseId: devPhase.id,
      progress: 65,
      startDate: new Date('2025-01-12'),
      dueDate: new Date('2025-01-25'),
      estimatedHours: 32,
      actualHours: 24,
      aiPriorityScore: 0.95,
      riskScore: 0.2,
      predictedDueDate: new Date('2025-01-26'),
      owners: { create: [{ userId: devUser.id, role: 'OWNER' }] },
      tags: {
        create: [
          { tag: 'api' },
          { tag: 'authentication' },
          { tag: 'security' },
        ],
      },
      artifacts: {
        create: [
          {
            type: ArtifactType.DOCUMENT,
            title: 'API Design Doc',
            description: 'OpenAPI specification',
            url: '/docs/auth-api.yaml',
            createdById: devUser.id,
          },
          {
            type: ArtifactType.CODE,
            title: 'Auth Module',
            description: 'Source code link',
            url: 'https://github.com/repo/auth',
            createdById: devUser.id,
          },
        ],
      },
      integrations: {
        create: [
          {
            provider: IntegrationProvider.GITHUB,
            externalId: 'issue-123',
            externalUrl: 'https://github.com/org/repo/issues/123',
          },
        ],
      },
    },
  });

  const dashboardItem = await prisma.item.create({
    data: {
      title: 'Frontend Dashboard Layout',
      description:
        'Implement the main dashboard layout using React and TailwindCSS.',
      type: ItemType.TASK,
      priority: ItemPriority.MEDIUM,
      status: ItemStatus.NOT_STARTED,
      phaseId: devPhase.id,
      progress: 0,
      estimatedHours: 16,
      dueDate: new Date('2025-02-01'),
      owners: { create: [{ userId: devUser.id }] },
      tags: { create: [{ tag: 'frontend' }, { tag: 'ui' }] },
    },
  });

  const productApiItem = await prisma.item.create({
    data: {
      title: 'Product Catalog API',
      description: 'CRUD operations for products with search and filtering.',
      type: ItemType.FEATURE,
      priority: ItemPriority.HIGH,
      status: ItemStatus.IN_REVIEW,
      phaseId: devPhase.id,
      progress: 90,
      estimatedHours: 24,
      actualHours: 22,
      dueDate: new Date('2025-01-20'),
      owners: { create: [{ userId: devUser.id }] },
      tags: { create: [{ tag: 'api' }, { tag: 'products' }] },
    },
  });

  const bugItem = await prisma.item.create({
    data: {
      title: 'Fix Memory Leak in Session Handler',
      description:
        'Sessions are not being cleaned up properly, causing memory to grow over time.',
      type: ItemType.BUG,
      priority: ItemPriority.CRITICAL,
      status: ItemStatus.IN_PROGRESS,
      phaseId: devPhase.id,
      progress: 30,
      estimatedHours: 8,
      riskScore: 0.7,
      sentimentScore: -0.5,
      owners: { create: [{ userId: devUser.id }] },
      tags: {
        create: [{ tag: 'bug' }, { tag: 'performance' }, { tag: 'critical' }],
      },
      comments: {
        create: [
          {
            userId: devUser.id,
            content: 'Investigating heap dumps now',
            sentimentScore: -0.2,
            sentimentLabel: 'neutral',
          },
          {
            userId: adminUser.id,
            content: 'This is blocking production deployment',
            sentimentScore: -0.7,
            sentimentLabel: 'negative',
          },
        ],
      },
    },
  });

  // Security Items
  const sastItem = await prisma.item.create({
    data: {
      title: 'Run SAST Scan',
      description:
        'Execute Static Application Security Testing on the codebase using SonarQube.',
      type: ItemType.TASK,
      priority: ItemPriority.HIGH,
      status: ItemStatus.IN_PROGRESS,
      phaseId: securityPhase.id,
      progress: 60,
      estimatedHours: 8,
      owners: { create: [{ userId: securityUser.id }] },
      tags: { create: [{ tag: 'security' }, { tag: 'sast' }] },
      integrations: {
        create: [
          {
            provider: IntegrationProvider.SONARQUBE,
            externalId: 'project-ecom',
            externalUrl: 'https://sonar.example.com/dashboard?id=ecom',
          },
        ],
      },
    },
  });

  const vulnItem = await prisma.item.create({
    data: {
      title: 'Remediate SQL Injection Vulnerability',
      description:
        'Critical SQL injection found in user search endpoint. Must be fixed before launch.',
      type: ItemType.SECURITY_ISSUE,
      priority: ItemPriority.CRITICAL,
      status: ItemStatus.BLOCKED,
      phaseId: securityPhase.id,
      progress: 20,
      estimatedHours: 16,
      riskScore: 0.95,
      owners: { create: [{ userId: securityUser.id }, { userId: devUser.id }] },
      tags: {
        create: [
          { tag: 'security' },
          { tag: 'vulnerability' },
          { tag: 'critical' },
        ],
      },
    },
  });

  // Operations Items
  await prisma.item.create({
    data: {
      title: 'Setup CI/CD Pipeline',
      description:
        'Configure GitHub Actions for automated testing and deployment.',
      type: ItemType.TASK,
      priority: ItemPriority.HIGH,
      status: ItemStatus.COMPLETED,
      phaseId: opsPhase.id,
      progress: 100,
      estimatedHours: 16,
      actualHours: 14,
      completedAt: new Date('2025-01-14'),
      owners: { create: [{ userId: adminUser.id }] },
      tags: { create: [{ tag: 'devops' }, { tag: 'ci-cd' }] },
      integrations: {
        create: [
          {
            provider: IntegrationProvider.GITHUB,
            externalId: 'workflow-main',
            externalUrl: 'https://github.com/org/repo/actions',
          },
        ],
      },
    },
  });

  await prisma.item.create({
    data: {
      title: 'Configure Monitoring & Alerting',
      description:
        'Setup Prometheus, Grafana, and PagerDuty for production monitoring.',
      type: ItemType.TASK,
      priority: ItemPriority.MEDIUM,
      status: ItemStatus.NOT_STARTED,
      phaseId: opsPhase.id,
      progress: 0,
      estimatedHours: 24,
      dueDate: new Date('2025-02-20'),
      owners: { create: [{ userId: adminUser.id }] },
      tags: { create: [{ tag: 'monitoring' }, { tag: 'observability' }] },
    },
  });

  // Create dependencies
  await prisma.itemDependency.create({
    data: {
      dependentId: dashboardItem.id,
      blockingId: authApiItem.id,
      type: DependencyType.DEPENDS_ON,
    },
  });

  await prisma.itemDependency.create({
    data: {
      dependentId: vulnItem.id,
      blockingId: sastItem.id,
      type: DependencyType.DEPENDS_ON,
    },
  });

  // ============================================================================
  // 10. WORKFLOW TEMPLATES & WORKFLOWS
  // ============================================================================
  console.log('⚡ Creating workflows...');

  const bugFixTemplate = await prisma.workflowTemplate.create({
    data: {
      name: 'Bug Fix Workflow',
      description:
        'Standard process for handling production bugs with AI-assisted triage',
      workflowType: WorkflowType.BUG_FIX,
      isSystem: true,
      defaultIntent: JSON.stringify({
        goal: 'Fix bug with minimal impact',
        successCriteria: ['Bug reproduced', 'Fix verified', 'No regression'],
      }),
      requiredFields: JSON.stringify({
        severity: true,
        affectedUsers: true,
        reproduction: true,
      }),
      defaultRisks: JSON.stringify([
        'Regression',
        'Data loss',
        'Performance impact',
      ]),
    },
  });

  await prisma.workflowTemplate.create({
    data: {
      name: 'Feature Development',
      description:
        'End-to-end feature development lifecycle with security gates',
      workflowType: WorkflowType.FEATURE,
      isSystem: true,
      defaultIntent: JSON.stringify({
        goal: 'Deliver feature meeting requirements',
        successCriteria: [
          'Requirements met',
          'Tests passed',
          'Security approved',
        ],
      }),
    },
  });

  await prisma.workflowTemplate.create({
    data: {
      name: 'Security Update',
      description: 'Process for addressing security vulnerabilities',
      workflowType: WorkflowType.SECURITY_UPDATE,
      isSystem: true,
      defaultIntent: JSON.stringify({
        goal: 'Remediate security issue',
        successCriteria: ['Vulnerability patched', 'No new issues introduced'],
      }),
    },
  });

  await prisma.workflowTemplate.create({
    data: {
      name: 'Incident Response',
      description: 'Structured approach to handling production incidents',
      workflowType: WorkflowType.INCIDENT,
      isSystem: true,
    },
  });

  // Create active workflows
  const memoryLeakWorkflow = await prisma.workflow.create({
    data: {
      name: 'Fix Memory Leak Issue',
      description: 'Workflow to address session handler memory leak',
      workflowType: WorkflowType.BUG_FIX,
      currentStep: WorkflowStep.EXECUTE,
      status: WorkflowStatus.IN_PROGRESS,
      aiMode: AIMode.ASSIST,
      ownerId: devUser.id,
      ownerName: devUser.name,
      templateId: bugFixTemplate.id,
      steps: JSON.stringify({
        intent: {
          status: 'completed',
          data: { goal: 'Fix memory leak in session handler' },
        },
        context: {
          status: 'completed',
          data: { affectedService: 'auth-service', severity: 'critical' },
        },
        plan: {
          status: 'completed',
          data: { approach: 'Implement proper session cleanup' },
        },
        execute: {
          status: 'in_progress',
          data: { tasksCompleted: 2, totalTasks: 5 },
        },
      }),
      contributors: {
        create: [
          { userId: adminUser.id, userName: adminUser.name, role: 'REVIEWER' },
        ],
      },
      auditEntries: {
        create: [
          {
            action: AuditAction.CREATED,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_STARTED,
            step: WorkflowStep.INTENT,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_COMPLETED,
            step: WorkflowStep.INTENT,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_STARTED,
            step: WorkflowStep.CONTEXT,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_COMPLETED,
            step: WorkflowStep.CONTEXT,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_STARTED,
            step: WorkflowStep.PLAN,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.AI_SUGGESTION_ACCEPTED,
            step: WorkflowStep.PLAN,
            userId: devUser.id,
            userName: devUser.name,
            details: { suggestion: 'Use WeakMap for session storage' },
          },
          {
            action: AuditAction.STEP_COMPLETED,
            step: WorkflowStep.PLAN,
            userId: devUser.id,
            userName: devUser.name,
          },
          {
            action: AuditAction.STEP_STARTED,
            step: WorkflowStep.EXECUTE,
            userId: devUser.id,
            userName: devUser.name,
          },
        ],
      },
    },
  });

  // Create AI-generated plan for the workflow
  await prisma.aIGeneratedPlan.create({
    data: {
      workflowId: memoryLeakWorkflow.id,
      intent: 'Fix memory leak in session handler to prevent OOM errors',
      context: JSON.stringify({
        service: 'auth-service',
        component: 'SessionManager',
        severity: 'critical',
      }),
      tasks: JSON.stringify([
        {
          id: '1',
          title: 'Analyze heap dumps',
          status: 'completed',
          estimatedHours: 2,
        },
        {
          id: '2',
          title: 'Identify root cause',
          status: 'completed',
          estimatedHours: 4,
        },
        {
          id: '3',
          title: 'Implement fix',
          status: 'in_progress',
          estimatedHours: 8,
        },
        { id: '4', title: 'Write tests', status: 'pending', estimatedHours: 4 },
        {
          id: '5',
          title: 'Deploy to staging',
          status: 'pending',
          estimatedHours: 2,
        },
      ]),
      estimatedDuration: '20 hours',
      riskFactors: JSON.stringify([
        'Potential performance regression',
        'Breaking changes to session API',
      ]),
      dependencies: JSON.stringify([]),
      suggestedAssignments: JSON.stringify([
        {
          taskId: '3',
          odId: devUser.id,
          reason: 'Most familiar with codebase',
        },
      ]),
      confidence: 0.88,
      reasoning:
        'Based on heap analysis, the issue is in SessionManager not properly cleaning up expired sessions.',
      model: 'gpt-4',
      accepted: true,
      acceptedAt: new Date(),
    },
  });

  // ============================================================================
  // 11. AI INSIGHTS - Create various types of insights
  // ============================================================================
  console.log('🔮 Creating AI Insights...');

  await prisma.aIInsight.create({
    data: {
      type: InsightType.PREDICTION,
      title: 'Sprint Velocity Decline Detected',
      description:
        'Current sprint velocity is 15% below the 3-month average. This may impact the February milestone.',
      confidence: 0.87,
      severity: InsightSeverity.WARNING,
      status: InsightStatus.ACTIVE,
      agentName: 'velocity-tracker',
      modelVersion: '1.2.0',
      actionable: true,
      suggestedAction: JSON.stringify({
        label: 'Review Sprint Backlog',
        action: 'navigate',
        params: { route: '/devsecops/phase/development' },
      }),
      phaseId: devPhase.id,
    },
  });

  await prisma.aIInsight.create({
    data: {
      type: InsightType.ANOMALY,
      title: 'Unusual Security Scan Failures',
      description:
        'Security scan failure rate increased from 2% to 18% in the past 48 hours. New vulnerabilities may have been introduced.',
      confidence: 0.93,
      severity: InsightSeverity.CRITICAL,
      status: InsightStatus.ACTIVE,
      agentName: 'security-monitor',
      modelVersion: '2.1.0',
      actionable: true,
      suggestedAction: JSON.stringify({
        label: 'View Security Dashboard',
        action: 'navigate',
        params: { route: '/devsecops/phase/security' },
      }),
      phaseId: securityPhase.id,
    },
  });

  await prisma.aIInsight.create({
    data: {
      type: InsightType.RECOMMENDATION,
      title: 'Optimize API Performance',
      description:
        'Authentication API latency is higher than expected (avg 400ms). Consider adding Redis caching for session lookups.',
      confidence: 0.85,
      severity: InsightSeverity.MEDIUM,
      status: InsightStatus.ACTIVE,
      agentName: 'performance-analyzer',
      modelVersion: '1.5.0',
      itemId: authApiItem.id,
      actionable: true,
      suggestedAction: JSON.stringify({
        label: 'View Performance Metrics',
        action: 'navigate',
        params: { route: '/observability' },
      }),
    },
  });

  await prisma.aIInsight.create({
    data: {
      type: InsightType.RISK_ALERT,
      title: 'SQL Injection Vulnerability Blocks Launch',
      description:
        'The critical SQL injection vulnerability must be resolved before production deployment. Current ETA is behind schedule.',
      confidence: 0.98,
      severity: InsightSeverity.CRITICAL,
      status: InsightStatus.ACTIVE,
      agentName: 'risk-assessor',
      modelVersion: '1.0.0',
      itemId: vulnItem.id,
      actionable: true,
      suggestedAction: JSON.stringify({
        label: 'Prioritize Remediation',
        action: 'assign',
        params: { assignTo: securityUser.id },
      }),
    },
  });

  await prisma.aIInsight.create({
    data: {
      type: InsightType.TREND_ANALYSIS,
      title: 'Code Coverage Improving',
      description:
        'Code coverage has improved by 8% over the last 2 weeks. Continue this trend to meet the 85% target.',
      confidence: 0.92,
      severity: InsightSeverity.INFO,
      status: InsightStatus.ACKNOWLEDGED,
      agentName: 'quality-tracker',
      modelVersion: '1.3.0',
      acknowledged: true,
      acknowledgedBy: devUser.id,
      acknowledgedAt: new Date(),
      feedbackScore: 4,
      phaseId: devPhase.id,
    },
  });

  await prisma.aIInsight.create({
    data: {
      type: InsightType.SUGGESTION,
      title: 'Consider Breaking Down Large Epic',
      description:
        'The Authentication Epic has 15 subtasks. Consider splitting into smaller, more manageable epics for better tracking.',
      confidence: 0.75,
      severity: InsightSeverity.LOW,
      status: InsightStatus.DISMISSED,
      agentName: 'planning-assistant',
      modelVersion: '1.1.0',
      itemId: architectureItem.id,
      actionable: true,
    },
  });

  // ============================================================================
  // 12. PREDICTIONS & ANOMALIES
  // ============================================================================
  console.log('📊 Creating predictions and anomalies...');

  await prisma.prediction.create({
    data: {
      targetType: PredictionTarget.MILESTONE,
      targetId: 'security-audit-milestone',
      type: PredictionType.DEADLINE_RISK,
      probability: 0.72,
      timeline: '10 days late',
      affectedItems: JSON.stringify([vulnItem.id, sastItem.id]),
      suggestedMitigation: JSON.stringify([
        'Add additional security resources',
        'Defer non-critical items',
      ]),
      confidence: 0.85,
      modelName: 'deadline-predictor',
      modelVersion: '2.0.0',
    },
  });

  await prisma.prediction.create({
    data: {
      targetType: PredictionTarget.ITEM,
      targetId: bugItem.id,
      type: PredictionType.BLOCKER_LIKELIHOOD,
      probability: 0.65,
      affectedItems: JSON.stringify([dashboardItem.id]),
      suggestedMitigation: JSON.stringify([
        'Pair programming session',
        'Senior developer review',
      ]),
      confidence: 0.78,
      modelName: 'blocker-detector',
      modelVersion: '1.5.0',
    },
  });

  await prisma.phasePrediction.create({
    data: {
      phaseId: securityPhase.id,
      predictedEndDate: new Date('2025-02-25'),
      confidenceInterval: 0.82,
      riskFactors: JSON.stringify([
        '3 critical vulnerabilities pending',
        'Resource shortage',
      ]),
      recommendations: JSON.stringify([
        'Request additional security analyst',
        'Prioritize SQL injection fix',
      ]),
      modelVersion: '2.1.0',
    },
  });

  await prisma.anomalyAlert.create({
    data: {
      type: AnomalyType.VELOCITY,
      severity: AnomalySeverity.WARNING,
      title: 'Sprint Velocity Drop',
      description:
        'Sprint velocity dropped by 25% compared to the rolling 4-week average.',
      affectedItems: JSON.stringify([authApiItem.id, productApiItem.id]),
      baselineValue: 42,
      currentValue: 31,
      deviationPercent: -26.2,
      suggestedActions: JSON.stringify([
        'Review blocked items',
        'Check for scope creep',
        'Team capacity check',
      ]),
      confidence: 0.89,
      modelVersion: '1.2.0',
    },
  });

  await prisma.anomalyAlert.create({
    data: {
      type: AnomalyType.SECURITY,
      severity: AnomalySeverity.CRITICAL,
      title: 'Unusual Failed Login Attempts',
      description:
        'Failed login attempts increased 500% in the last hour. Possible brute force attack.',
      baselineValue: 10,
      currentValue: 60,
      deviationPercent: 500,
      suggestedActions: JSON.stringify([
        'Enable rate limiting',
        'Review IP addresses',
        'Enable CAPTCHA',
      ]),
      confidence: 0.95,
      modelVersion: '1.0.0',
      acknowledged: true,
      acknowledgedBy: securityUser.id,
      acknowledgedAt: new Date(),
    },
  });

  // ============================================================================
  // 13. COPILOT SESSIONS & AGENT EXECUTIONS
  // ============================================================================
  console.log('🤖 Creating AI sessions and executions...');

  await prisma.copilotSession.create({
    data: {
      userId: devUser.id,
      title: 'Help with authentication implementation',
      status: SessionStatus.COMPLETED,
      context: JSON.stringify({
        projectId: ecommerceProject.id,
        itemId: authApiItem.id,
      }),
      messages: JSON.stringify([
        { role: 'user', content: 'How should I implement JWT refresh tokens?' },
        {
          role: 'assistant',
          content: 'I recommend using a rotating refresh token pattern...',
        },
        { role: 'user', content: 'Can you show me an example?' },
        {
          role: 'assistant',
          content: 'Here is a code example using jsonwebtoken library...',
        },
      ]),
      actionsExecuted: JSON.stringify([
        { action: 'generateCode', success: true },
      ]),
      tokensUsed: 2450,
      modelUsed: 'gpt-4',
      costUSD: 0.12,
      satisfactionRating: 5,
      feedback: 'HELPFUL',
      endedAt: new Date(),
    },
  });

  await prisma.copilotSession.create({
    data: {
      userId: securityUser.id,
      title: 'Security vulnerability analysis',
      status: SessionStatus.ACTIVE,
      context: JSON.stringify({
        phase: 'security',
        focus: 'vulnerability-remediation',
      }),
      messages: JSON.stringify([
        {
          role: 'user',
          content: 'Analyze the SQL injection vulnerability in user search',
        },
        {
          role: 'assistant',
          content: 'I have identified the issue in the search query...',
        },
      ]),
      tokensUsed: 890,
      modelUsed: 'gpt-4',
    },
  });

  await prisma.agentExecution.create({
    data: {
      agentName: 'code-reviewer',
      requestId: `req-${Date.now()}-1`,
      userId: devUser.id,
      workspaceId: mainWorkspace.id,
      input: JSON.stringify({
        pullRequestId: 'PR-42',
        files: ['src/auth/session.ts'],
      }),
      output: JSON.stringify({ issues: 2, suggestions: 5, approved: false }),
      status: ExecutionStatus.SUCCESS,
      latencyMs: 3420,
      tokensUsed: 1850,
      modelVersion: 'gpt-4-turbo',
    },
  });

  await prisma.agentExecution.create({
    data: {
      agentName: 'security-scanner',
      requestId: `req-${Date.now()}-2`,
      userId: securityUser.id,
      workspaceId: securityWorkspace.id,
      input: JSON.stringify({ target: 'auth-service', scanType: 'SAST' }),
      output: JSON.stringify({ vulnerabilities: 3, critical: 1, high: 2 }),
      status: ExecutionStatus.SUCCESS,
      latencyMs: 45000,
      tokensUsed: 450,
      modelVersion: '1.0.0',
    },
  });

  // ============================================================================
  // 14. AI METRICS
  // ============================================================================
  console.log('📈 Creating AI metrics...');

  const aiMetricsData = [
    {
      agentName: 'code-reviewer',
      model: 'gpt-4',
      operation: 'completion',
      tokensUsed: 1850,
      latencyMs: 3420,
      costUSD: 0.09,
      success: true,
    },
    {
      agentName: 'velocity-tracker',
      model: 'gpt-4',
      operation: 'prediction',
      tokensUsed: 320,
      latencyMs: 890,
      costUSD: 0.02,
      success: true,
    },
    {
      agentName: 'security-scanner',
      model: 'claude-3',
      operation: 'analysis',
      tokensUsed: 2100,
      latencyMs: 5200,
      costUSD: 0.08,
      success: true,
    },
    {
      agentName: 'planning-assistant',
      model: 'gpt-4',
      operation: 'completion',
      tokensUsed: 1200,
      latencyMs: 2800,
      costUSD: 0.06,
      success: true,
    },
    {
      agentName: 'code-reviewer',
      model: 'gpt-4',
      operation: 'completion',
      tokensUsed: 980,
      latencyMs: 2100,
      costUSD: 0.05,
      success: false,
      errorMessage: 'Rate limit exceeded',
    },
  ];

  for (const metric of aiMetricsData) {
    await prisma.aIMetric.create({ data: { ...metric, userId: devUser.id } });
  }

  // ============================================================================
  // 15. ACTIVITY LOGS
  // ============================================================================
  console.log('📋 Creating activity logs...');

  const activities = [
    {
      userId: adminUser.id,
      action: 'project.created',
      description: 'Created E-Commerce Platform project',
    },
    {
      userId: devUser.id,
      itemId: authApiItem.id,
      action: 'item.started',
      description: 'Started working on User Authentication API',
    },
    {
      userId: devUser.id,
      itemId: productApiItem.id,
      action: 'item.submitted_review',
      description: 'Submitted Product Catalog API for review',
    },
    {
      userId: securityUser.id,
      phaseId: securityPhase.id,
      action: 'phase.risk_updated',
      description: 'Updated security phase risk score to 0.45',
    },
    {
      userId: adminUser.id,
      workflowId: memoryLeakWorkflow.id,
      action: 'workflow.plan_accepted',
      description: 'Accepted AI-generated plan for memory leak fix',
    },
    {
      userId: viewerUser.id,
      action: 'dashboard.viewed',
      description: 'Viewed project dashboard',
    },
  ];

  for (const activity of activities) {
    await prisma.activityLog.create({ data: activity });
  }

  // ============================================================================
  // 16. COMPLIANCE DATA
  // ============================================================================
  console.log('📜 Creating compliance data...');

  await prisma.complianceAssessment.create({
    data: {
      title: 'SOC 2 Type II Assessment',
      description: 'Annual SOC 2 compliance assessment for security controls',
      framework: 'SOC2',
      status: 'IN_PROGRESS',
      riskScore: 0.35,
      findings: JSON.stringify([
        {
          id: 'F-001',
          severity: 'high',
          control: 'CC6.1',
          description: 'Incomplete access logging',
        },
        {
          id: 'F-002',
          severity: 'medium',
          control: 'CC7.2',
          description: 'Incident response documentation outdated',
        },
      ]),
      controls: JSON.stringify([
        { id: 'CC6.1', name: 'Logical Access Controls', status: 'partial' },
        { id: 'CC7.2', name: 'System Monitoring', status: 'compliant' },
      ]),
      remediationPlans: {
        create: {
          totalEffort: 80,
          completionTarget: new Date('2025-03-15'),
          riskScore: 0.35,
          status: 'IN_PROGRESS',
          steps: {
            create: [
              {
                controlId: 'CC6.1',
                title: 'Implement complete access logging',
                priority: 'HIGH',
                estimatedEffort: 40,
                owner: adminUser.id,
                deadline: new Date('2025-02-28'),
                status: 'IN_PROGRESS',
              },
              {
                controlId: 'CC7.2',
                title: 'Update incident response documentation',
                priority: 'MEDIUM',
                estimatedEffort: 16,
                owner: securityUser.id,
                deadline: new Date('2025-02-15'),
                status: 'OPEN',
              },
            ],
          },
        },
      },
      reports: {
        create: {
          title: 'SOC 2 Interim Report',
          summary: 'Interim assessment findings and remediation progress',
          generatedBy: securityUser.id,
          format: 'PDF',
          framework: 'SOC2',
        },
      },
    },
  });

  // ============================================================================
  // 17. AUDIT LOG ENTRIES
  // ============================================================================
  console.log('🔍 Creating audit log entries...');

  const auditEntries = [
    {
      action: 'user.login',
      actor: adminUser.email,
      actorRole: 'ADMIN',
      severity: 'info',
      ipAddress: '192.168.1.100',
    },
    {
      action: 'project.created',
      actor: adminUser.email,
      actorRole: 'ADMIN',
      resource: ecommerceProject.id,
      severity: 'info',
    },
    {
      action: 'item.updated',
      actor: devUser.email,
      actorRole: 'EDITOR',
      resource: authApiItem.id,
      severity: 'info',
    },
    {
      action: 'security.vulnerability_found',
      actor: 'security-scanner',
      actorRole: 'SYSTEM',
      resource: vulnItem.id,
      severity: 'critical',
    },
    {
      action: 'workflow.ai_plan_generated',
      actor: 'planning-assistant',
      actorRole: 'SYSTEM',
      resource: memoryLeakWorkflow.id,
      severity: 'info',
    },
  ];

  for (const entry of auditEntries) {
    await prisma.auditLogEntry.create({ data: entry });
  }

  // ============================================================================
  // 18. RATE LIMIT CONFIGS
  // ============================================================================
  console.log('⚙️ Creating rate limit configs...');

  await prisma.rateLimitConfig.create({
    data: {
      userId: adminUser.id,
      tier: 'enterprise',
      userTiers: JSON.stringify({
        free: { requestsPerMinute: 10, tokensPerDay: 10000 },
        pro: { requestsPerMinute: 60, tokensPerDay: 100000 },
        enterprise: { requestsPerMinute: 300, tokensPerDay: 1000000 },
      }),
      enableAuditLog: true,
      maxAuditLogEntries: 50000,
    },
  });

  await prisma.rateLimitConfig.create({
    data: {
      userId: devUser.id,
      tier: 'pro',
      userTiers: JSON.stringify({
        free: { requestsPerMinute: 10, tokensPerDay: 10000 },
        pro: { requestsPerMinute: 60, tokensPerDay: 100000 },
      }),
      enableAuditLog: true,
    },
  });

  // ============================================================================
  // 19. FLOW STATES (Golden Flows)
  // ============================================================================
  console.log('🌊 Creating flow states...');

  await prisma.flowState.create({
    data: {
      flowId: 'bug-fix-flow-1',
      currentState: 'executing',
      context: JSON.stringify({ bugId: bugItem.id, severity: 'critical' }),
      history: JSON.stringify([
        {
          state: 'initiated',
          timestamp: new Date(Date.now() - 86400000).toISOString(),
        },
        {
          state: 'analyzing',
          timestamp: new Date(Date.now() - 43200000).toISOString(),
        },
        {
          state: 'planning',
          timestamp: new Date(Date.now() - 21600000).toISOString(),
        },
        { state: 'executing', timestamp: new Date().toISOString() },
      ]),
      artifacts: JSON.stringify([
        { type: 'heap-dump', path: '/artifacts/heap-001.hprof' },
      ]),
      activeTasks: JSON.stringify([
        { id: 'implement-fix', status: 'in_progress' },
      ]),
    },
  });

  // ============================================================================
  // SUMMARY
  // ============================================================================
  console.log('');
  console.log('✅ Comprehensive seeding completed successfully!');
  console.log('');
  console.log('📊 Summary of created data:');
  console.log('   👤 Users: 4 (admin, developer, security, viewer)');
  console.log('   🏢 Workspaces: 2 (Engineering Team, Security Operations)');
  console.log(
    '   🚀 Projects: 4 (E-Commerce, Mobile App, Admin Dashboard, Security Audit)'
  );
  console.log('   🎨 Canvas Documents: 2');
  console.log('   📄 Pages: 2');
  console.log('   📅 Phases: 4 (Planning, Development, Security, Operations)');
  console.log('   🎯 Milestones: 4');
  console.log('   📝 Items: 10+ (various types and statuses)');
  console.log('   ⚡ Workflow Templates: 4');
  console.log('   🔄 Active Workflows: 1');
  console.log('   🔮 AI Insights: 6');
  console.log('   📊 Predictions: 3');
  console.log('   ⚠️ Anomaly Alerts: 2');
  console.log('   🤖 Copilot Sessions: 2');
  console.log('   📈 AI Metrics: 5');
  console.log('   📜 Compliance Assessments: 1');
  console.log('   📋 Activity Logs: 6');
  console.log('   🔍 Audit Log Entries: 5');
  console.log('');
  console.log('🎉 Database is ready for comprehensive testing!');
}

main()
  .catch((e) => {
    console.error('❌ Error during seeding:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
