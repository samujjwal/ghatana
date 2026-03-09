/**
 * Prisma Client Singleton
 * Ensures only one instance of PrismaClient exists during development
 * to prevent connection pool exhaustion during hot-reloading.
 *
 * Prisma 7 configuration: Pass database URL via adapter in constructor
 */

// Import from locally generated Prisma client (NOT from @prisma/client package)
// This ensures we use the PostgreSQL schema we generated, not any shared/cached client
import { PrismaClient } from "../../generated/prisma/index.js";
import { Pool } from "pg";
import { PrismaPg } from "@prisma/adapter-pg";

const globalForPrisma = globalThis as unknown as {
  prisma: PrismaClient | undefined;
};

const createPrismaClient = () => {
  const connectionString = process.env.DATABASE_URL;
  
  if (!connectionString) {
    throw new Error(
      "DATABASE_URL environment variable is not set. " +
      "Please set DATABASE_URL=postgresql://user:password@host:port/database"
    );
  }

  console.log("🔗 Creating Prisma Client with connection string:", connectionString.replace(/:[^:]+@/, ':***@'));
  
  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);

  console.log("📦 Initializing PrismaClient with @prisma/adapter-pg...");

  try {
    const client = new PrismaClient({
      adapter,
      log:
        process.env.NODE_ENV === "development"
          ? ["query", "error", "warn"]
          : ["error"],
    });
    console.log("✅ PrismaClient initialized successfully with PostgreSQL adapter");
    return client;
  } catch (error) {
    console.error("❌ Failed to initialize PrismaClient:", error instanceof Error ? error.message : error);
    throw error;
  }
};

export const prisma =
  globalForPrisma.prisma ?? createPrismaClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.prisma = prisma;
}

/**
 * Gracefully disconnect Prisma on process termination
 */
export const disconnectPrisma = async () => {
  await prisma.$disconnect();
};

