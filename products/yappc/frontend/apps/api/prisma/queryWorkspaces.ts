import { PrismaClient } from '../src/generated/prisma/client';
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { Pool } from 'pg';
import { PrismaPg } from '@prisma/adapter-pg';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

dotenv.config({ path: path.resolve(__dirname, '../.env.local') });

const connectionString = process.env.DATABASE_URL;
if (!connectionString) throw new Error('DATABASE_URL is not defined');

const pool = new Pool({ connectionString, max: 1, idleTimeoutMillis: 1000, connectionTimeoutMillis: 5000 });
const adapter = new PrismaPg(pool);
const prisma = new PrismaClient({ adapter });

async function main() {
    const list = await prisma.workspace.findMany({ select: { id: true, name: true } });
    console.log('WORKSPACES:', list);

    const admin = await prisma.user.findUnique({ where: { email: 'admin@yappc.com' }, select: { id: true, email: true } });
    console.log('ADMIN USER:', admin);
    await prisma.$disconnect();
}

main().catch(e => { console.error(e); process.exit(1); });
