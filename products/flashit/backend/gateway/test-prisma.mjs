#!/usr/bin/env node

import { PrismaClient } from '@prisma/client';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';

const connectionString = process.env.DATABASE_URL || 'postgresql://ghatana:ghatana123@localhost:5433/flashit_dev';
process.stdout.write(`Connection string: ${connectionString.replace(/:[^:]+@/, ':***@')}\n`);

try {
  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  const prisma = new PrismaClient({ adapter });  process.stdout.write('Prisma client initialized successfully\n');
  prisma.$disconnect();
  process.exit(0);
} catch (err) {  process.stderr.write(`Error initializing Prisma: ${err instanceof Error ? err.message : String(err)}\n`);
  process.exit(1);
}
