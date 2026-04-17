/**
 * Basic Test Seed Script for YAPPC
 *
 * Tests basic Prisma client functionality with minimal data.
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
  console.log('🌱 Starting basic test seed...');

  try {
    // Test basic connection
    await prisma.$connect();
    console.log('✅ Database connection successful');

    // Test if we can query the database
    const userCount = await prisma.user.count();
    console.log(`📊 Current user count: ${userCount}`);

    // Create a test user if none exist
    if (userCount === 0) {
      console.log('👤 Creating test user...');
      const testUser = await prisma.user.create({
        data: {
          email: 'test@yappc.com',
          name: 'Test User',
          role: 'ADMIN',
        },
      });
      console.log(`✅ Created test user: ${testUser.name} (${testUser.email})`);
    }

    console.log('✅ Basic test seed completed successfully!');
  } catch (error) {
    console.error('❌ Error during seeding:', error);
    throw error;
  }
}

async function runSeed(): Promise<void> {
  try {
    await main();
  } catch (error) {
    console.error('❌ Seeding failed:', error);
    process.exitCode = 1;
  } finally {
    await prisma.$disconnect();
  }
}

void runSeed();
