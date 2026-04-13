import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "prisma/config";

const currentFilePath = fileURLToPath(import.meta.url);
const currentDirPath = path.dirname(currentFilePath);

const databaseUrl =
  process.env.TUTORPUTOR_DATABASE_URL ??
  process.env.DATABASE_URL ??
  "postgresql://localhost:5432/tutorputor";

export default defineConfig({
  schema: path.resolve(currentDirPath, "prisma", "schema.prisma"),
  datasource: {
    provider: "postgresql",
    url: databaseUrl,
  },
  migrations: {
    seed: "tsx src/seed.ts",
  },
});
