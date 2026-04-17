import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export function readPrismaSchema(): string {
  const currentFilePath = fileURLToPath(import.meta.url);
  const currentDir = path.dirname(currentFilePath);

  const candidatePaths = [
    path.resolve(currentDir, "../../../../../../libs/tutorputor-core/prisma/schema.prisma"),
    path.resolve(process.cwd(), "libs/tutorputor-core/prisma/schema.prisma"),
    path.resolve(process.cwd(), "products/tutorputor/libs/tutorputor-core/prisma/schema.prisma"),
  ];

  const schemaPath = candidatePaths.find((candidate) => existsSync(candidate));
  if (!schemaPath) {
    throw new Error(
      `Unable to locate Prisma schema. Checked: ${candidatePaths.join(", ")}`,
    );
  }

  return readFileSync(schemaPath, "utf-8");
}

export function getModelBlock(schema: string, modelName: string): string {
  const modelStart = schema.indexOf(`model ${modelName} {`);
  if (modelStart < 0) {
    throw new Error(`Prisma model not found: ${modelName}`);
  }

  const rest = schema.slice(modelStart);
  const nextModelStart = rest.indexOf("\nmodel ", 1);

  if (nextModelStart < 0) {
    return rest;
  }

  return rest.slice(0, nextModelStart);
}
