import { PrismaClient } from './src/generated/prisma/index.js';

const prisma = new PrismaClient();

async function main() {
  // Create a user if it doesn't exist
  const user = await prisma.user.upsert({
    where: { email: 'admin@yappc.com' },
    update: {},
    create: {
      email: 'admin@yappc.com',
      name: 'Admin User',
      role: 'ADMIN',
    },
  });

  console.log('User created/exists:', user);

  // Create a workspace
  const workspace = await prisma.workspace.create({
    data: {
      name: 'Default Workspace',
      description: 'Your first workspace',
      ownerId: user.id,
      isDefault: true,
      members: {
        create: {
          userId: user.id,
          role: 'ADMIN',
        },
      },
    },
  });

  console.log('Workspace created:', workspace);
}

main()
  .catch(e => {
    console.error('Error:', e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
