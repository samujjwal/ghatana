#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../../..");
const contractsRoot = path.join(repoRoot, "products/tutorputor/contracts");
const sourceRoot = path.join(contractsRoot, "v1");

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "dist") return [];
      return walk(fullPath);
    }
    return [fullPath];
  });
}

const manualDeclarations = walk(sourceRoot)
  .filter((file) => file.endsWith(".d.ts"))
  .map((file) => path.relative(repoRoot, file).replaceAll(path.sep, "/"))
  .sort();

if (manualDeclarations.length > 0) {
  console.error(
    "Manual source-adjacent contract declarations are not allowed. " +
      "Run the contracts TypeScript build and use products/tutorputor/contracts/dist instead.",
  );
  for (const file of manualDeclarations) {
    console.error(` - ${file}`);
  }
  process.exit(1);
}

const requiredGeneratedDeclarations = [
  "dist/index.d.ts",
  "dist/types.d.ts",
  "dist/services.d.ts",
  "dist/learning-unit.d.ts",
  "dist/telemetry-events.d.ts",
  "dist/content-studio.d.ts",
];

const missingGenerated = requiredGeneratedDeclarations.filter(
  (file) => !fs.existsSync(path.join(contractsRoot, file)),
);

if (missingGenerated.length > 0) {
  console.error(
    "Generated contract declarations are missing from dist. Run `pnpm --filter @tutorputor/contracts build`.",
  );
  for (const file of missingGenerated) {
    console.error(` - products/tutorputor/contracts/${file}`);
  }
  process.exit(1);
}

console.log("Generated contract declaration policy passed.");
