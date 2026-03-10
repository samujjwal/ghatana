import path from "node:path";
import { defineConfig } from "prisma/config";

const databaseUrl =
    process.env.TUTORPUTOR_DATABASE_URL ??
    process.env.DATABASE_URL ??
    `file:${path.resolve(__dirname, "prisma", "dev.db")}`;

export default defineConfig({
    schema: path.resolve(__dirname, "prisma", "schema.prisma"),
    datasources: {
        db: {
            kind: "sqlite",
            url: databaseUrl
        }
    },
    migrations: {
        seed: "tsx src/seed.ts"
    }
});
