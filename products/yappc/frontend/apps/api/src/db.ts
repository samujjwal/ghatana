import 'dotenv/config';
import { getPrismaClient, Prisma, PrismaClient } from './database/client';

const prisma = new Proxy({} as PrismaClient, {
	get(_target, property, receiver) {
		return Reflect.get(getPrismaClient() as unknown as object, property, receiver);
	},
}) as PrismaClient;

export default prisma;
export { Prisma, PrismaClient };
