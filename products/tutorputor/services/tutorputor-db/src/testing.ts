import { promises as fs } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { PrismaClient } from "../generated/prisma";

const packageRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  ".."
);
const DEFAULT_MIGRATIONS_DIR = path.join(packageRoot, "prisma", "migrations");

export interface ApplyMigrationsOptions {
  migrationsPath?: string;
}

export async function applyMigrationsForTests(
  prisma: PrismaClient,
  options: ApplyMigrationsOptions = {}
): Promise<void> {
  const migrationsDir = options.migrationsPath ?? DEFAULT_MIGRATIONS_DIR;
  const entries = await fs.readdir(migrationsDir, { withFileTypes: true });
  const directories = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();

  for (const directory of directories) {
    const sqlPath = path.join(migrationsDir, directory, "migration.sql");
    const sql = await fs.readFile(sqlPath, "utf8");
    await executeSqlStatements(prisma, sql);
  }
}

async function executeSqlStatements(prisma: PrismaClient, sql: string) {
  const sanitized = sql
    .split("\n")
    .map((line) => {
      const trimmed = line.trim();
      return trimmed.startsWith("--") ? "" : line;
    })
    .join("\n")
    .replace(/\r\n/g, "\n");

  const statements = sanitized
    .split(/;\s*(?:\n|$)/)
    .map((statement) => statement.trim())
    .filter((statement) => statement.length > 0);

  for (const statement of statements) {
    await prisma.$executeRawUnsafe(statement);
  }
}
