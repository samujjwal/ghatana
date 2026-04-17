import 'dotenv/config';
import { getPrismaClient, Prisma, PrismaClient } from './database/client';

const prisma = getPrismaClient();

export default prisma;
export { Prisma, PrismaClient };
