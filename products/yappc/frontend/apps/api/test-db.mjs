import { PrismaClient } from './src/generated/prisma/index.js';

const prisma = new PrismaClient();

async function testConnection() {
  try {
    const workspaces = await prisma.workspace.findMany();
    console.log('Workspaces found:', workspaces.length);
    if (workspaces.length > 0) {
      console.log('First workspace:', workspaces[0].name);
    }
  } catch (error) {
    console.error('Database connection error:', error);
  } finally {
    await prisma.$disconnect();
  }
}

testConnection();
