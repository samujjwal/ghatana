/**
 * Simple Seed Script for YAPPC
 *
 * Populates the database with basic demo data for testing.
 * Only uses models that actually exist in the current schema.
 */

import { PrismaClient } from '@prisma/client';

// Import enums directly from Prisma client
import {
  Role,
  ProjectType,
  ProjectStatus,
  ItemType,
  ItemPriority,
  ItemStatus,
} from '@prisma/client';

// Extract enums
const Role_Value = Role;
const ProjectType_Value = ProjectType;
const ProjectStatus = Prisma.ProjectStatus;
const WorkflowType = Prisma.WorkflowType;
const WorkflowStatus = Prisma.WorkflowStatus;
const WorkflowStep = Prisma.WorkflowStep;
const AIMode = Prisma.AIMode;
const AuditAction = Prisma.AuditAction;
const PhaseStatus = Prisma.PhaseStatus;
const MilestoneStatus = Prisma.MilestoneStatus;
const ItemType = Prisma.ItemType;
const ItemPriority = Prisma.ItemPriority;
const ItemStatus = Prisma.ItemStatus;
const ArtifactType = Prisma.ArtifactType;
const IntegrationProvider = Prisma.IntegrationProvider;
const KPICategory = Prisma.KPICategory;
const TrendDirection = Prisma.TrendDirection;
const PredictionTarget = Prisma.PredictionTarget;
const PredictionType = Prisma.PredictionType;
const AnomalyType = Prisma.AnomalyType;
const AnomalySeverity = Prisma.AnomalySeverity;
const SessionStatus = Prisma.SessionStatus;
const ExecutionStatus = Prisma.ExecutionStatus;
const DependencyType = Prisma.DependencyType;

import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Load environment variables
dotenv.config();

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  throw new Error('DATABASE_URL is not defined');
}

console.log(
  `🔗 Connecting to database: ${connectionString.replace(/:[^:@]+@/, ':****@')}`
);

const prisma = new PrismaClient({
  datasources: {
    db: {
      url: connectionString,
    },
  },
});

async function main() {
  console.log('🌱 Starting database seed...');

  // ============================================================================
  // 1. CLEANUP - Remove existing data in reverse order of dependencies
  // ============================================================================
  console.log('🧹 Cleaning up existing data...');

  // Clean up in order of dependencies
  await prisma.itemComment.deleteMany();
  await prisma.itemDependency.deleteMany();
  await prisma.itemIntegration.deleteMany();
  await prisma.artifact.deleteMany();
  await prisma.itemTag.deleteMany();
  await prisma.itemOwner.deleteMany();
  await prisma.item.deleteMany();

  await prisma.phaseKPI.deleteMany();
  await prisma.milestone.deleteMany();
  await prisma.phase.deleteMany();

  await prisma.canvasDocument.deleteMany();
  await prisma.page.deleteMany();

  await prisma.workspaceProject.deleteMany();
  await prisma.project.deleteMany();

  await prisma.workspaceMember.deleteMany();
  await prisma.workspace.deleteMany();
  await prisma.userAIPreferences.deleteMany();
  await prisma.user.deleteMany();

  await prisma.workflowAudit.deleteMany();
  await prisma.workflowContributor.deleteMany();
  await prisma.workflow.deleteMany();
  await prisma.workflowTemplate.deleteMany();

  await prisma.aIInsight.deleteMany();
  await prisma.anomalyAlert.deleteMany();
  await prisma.prediction.deleteMany();
  await prisma.phasePrediction.deleteMany();
  await prisma.itemEmbedding.deleteMany();
  await prisma.copilotSession.deleteMany();
  await prisma.userAIPreferences.deleteMany();

  // ============================================================================
  // 2. USERS - Create users with different roles
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
          { userId: viewerUser.id, role: Role.VIEWER },
        ],
      },
    },
  });

  // ============================================================================
  // 4. PROJECTS - Create projects
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
      lifecyclePhase: 'RUN',
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
        ],
        edges: [
          { source: '1', target: '2', label: 'auth' },
          { source: '1', target: '3', label: 'products' },
          { source: '2', target: '4', label: 'persist' },
          { source: '3', target: '4', label: 'persist' },
        ],
        viewport: { zoom: 1, x: 0, y: 0 },
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
        ],
      }),
    },
  });

  console.log('✅ Database seeding completed successfully!');
}

main()
  .catch((e) => {
    console.error('❌ Seeding failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
