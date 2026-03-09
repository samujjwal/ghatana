import { PrismaClient } from './src/generated/prisma/client';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config();
if (!process.env.DATABASE_URL) {
  const rootEnvPath = path.resolve(__dirname, '../../../../../../.env');
  console.log(`Loading .env from ${rootEnvPath}`);
  dotenv.config({ path: rootEnvPath });
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const adapter = new PrismaPg(pool);
const prisma = new PrismaClient({ adapter });

async function main() {
  try {
    const count = await prisma.user.count();
    console.log(`User count: ${count}`);
    
    const insights = await prisma.aIInsight.count();
    console.log(`Insight count: ${insights}`);
  } catch (e) {
    console.error(e);
  } finally {
    await prisma.$disconnect();
  }
}

main();