import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "prisma/config";

const currentFilePath = fileURLToPath(import.meta.url);
const currentDirPath = path.dirname(currentFilePath);

const databaseUrl =
  process.env.TUTORPUTOR_DATABASE_URL ??
  process.env.DATABASE_URL ??
  `file:${path.resolve(currentDirPath, "prisma", "dev.db")}`;

export default defineConfig({
  schema: path.resolve(currentDirPath, "prisma", "schema.prisma"),
  datasources: {
    db: {
      kind: "sqlite",
      url: databaseUrl,
    },
  },
  migrations: {
    seed: "tsx src/seed.ts",
  },
});
