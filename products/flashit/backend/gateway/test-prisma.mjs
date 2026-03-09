#!/usr/bin/env node

import { PrismaClient } from '@prisma/client';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';

const connectionString = process.env.DATABASE_URL || 'postgresql://ghatana:ghatana123@localhost:5433/flashit_dev';
console.log('Connection string:', connectionString.replace(/:[^:]+@/, ':***@'));

try {
  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  const prisma = new PrismaClient({ adapter });
  
  console.log('✅ Prisma client initialized successfully');
  prisma.$disconnect();
  process.exit(0);
} catch (err) {
  console.error('❌ Error initializing Prisma:', err instanceof Error ? err.message : err);
  process.exit(1);
}
