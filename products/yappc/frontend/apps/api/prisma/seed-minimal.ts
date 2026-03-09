/**
 * Minimal Seed Script for YAPPC
 *
 * Basic seed script that only uses core models with string literals for enums.
 */

import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import dotenv from 'dotenv';

// Load environment variables
dotenv.config();

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  throw new Error('DATABASE_URL is not defined');
}

console.log(
  `🔗 Connecting to database: ${connectionString.replace(/:[^:@]+@/, ':****@')}`
);

const adapter = new PrismaPg({
  connectionString: connectionString,
});

const prisma = new PrismaClient({
  adapter: adapter,
  log: ['query', 'info', 'warn', 'error'],
});

async function main() {
  console.log('🌱 Starting minimal database seed...');

  // ============================================================================
  // 1. CLEANUP - Remove existing data
  // ============================================================================
  console.log('🧹 Cleaning up existing data...');

  // Clean up in order of dependencies (only using basic models)
  await prisma.page.deleteMany();
  await prisma.canvasDocument.deleteMany();
  await prisma.project.deleteMany();
  await prisma.workspaceMember.deleteMany();
  await prisma.workspace.deleteMany();
  await prisma.user.deleteMany();

  // ============================================================================
  // 2. USERS - Create basic users
  // ============================================================================
  console.log('👤 Creating users...');

  const adminUser = await prisma.user.create({
    data: {
      email: 'admin@yappc.com',
      name: 'Admin User',
      role: 'ADMIN',
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin',
    },
  });

  const devUser = await prisma.user.create({
    data: {
      email: 'developer@yappc.com',
      name: 'Developer User',
      role: 'EDITOR',
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=developer',
    },
  });

  // ============================================================================
  // 3. WORKSPACES - Create basic workspace
  // ============================================================================
  console.log('🏢 Creating workspace...');

  const mainWorkspace = await prisma.workspace.create({
    data: {
      name: 'Engineering Team',
      description: 'Main engineering workspace for all projects',
      ownerId: adminUser.id,
      isDefault: true,
      aiSummary: 'Primary workspace for development projects',
      aiTags: ['engineering', 'development'],
    },
  });

  // Add workspace members
  await prisma.workspaceMember.createMany({
    data: [
      { userId: adminUser.id, workspaceId: mainWorkspace.id, role: 'ADMIN' },
      { userId: devUser.id, workspaceId: mainWorkspace.id, role: 'EDITOR' },
    ],
  });

  // ============================================================================
  // 4. PROJECTS - Create basic project
  // ============================================================================
  console.log('🚀 Creating project...');

  const ecommerceProject = await prisma.project.create({
    data: {
      name: 'E-Commerce Platform',
      description: 'Next-generation e-commerce solution',
      ownerWorkspaceId: mainWorkspace.id,
      createdById: adminUser.id,
      type: 'FULL_STACK',
      status: 'ACTIVE',
      lifecyclePhase: 'RUN',
      isDefault: true,
      aiSummary: 'E-commerce platform with modern architecture',
      aiNextActions: [
        'Setup authentication',
        'Create product catalog',
        'Implement checkout',
      ],
      aiHealthScore: 85,
    },
  });

  // ============================================================================
  // 5. CANVAS DOCUMENTS - Create basic canvas document
  // ============================================================================
  console.log('🎨 Creating canvas document...');

  await prisma.canvasDocument.create({
    data: {
      projectId: ecommerceProject.id,
      createdById: adminUser.id,
      name: 'System Architecture',
      description: 'High-level architecture diagram',
      content: JSON.stringify({
        nodes: [
          { id: '1', type: 'service', label: 'API Gateway', x: 100, y: 100 },
          { id: '2', type: 'service', label: 'Auth Service', x: 300, y: 100 },
          { id: '3', type: 'database', label: 'PostgreSQL', x: 500, y: 175 },
        ],
        edges: [
          { source: '1', target: '2', label: 'auth' },
          { source: '1', target: '3', label: 'data' },
        ],
        viewport: { zoom: 1, x: 0, y: 0 },
      }),
    },
  });

  // ============================================================================
  // 6. PAGES - Create basic page
  // ============================================================================
  console.log('📄 Creating page...');

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

  console.log('✅ Minimal database seeding completed successfully!');
  console.log(`📊 Created:
  - 2 Users
  - 1 Workspace
  - 1 Project
  - 1 Canvas Document
  - 1 Page`);
}

main()
  .catch((e) => {
    console.error('❌ Seeding failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
