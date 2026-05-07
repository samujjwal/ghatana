#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, "..");

const forbiddenScripts = [
  "scripts/resolve-todos.js",
  "scripts/fix-error-handling.js",
  "services/tutorputor-content-generation/src/fix_getters.sh",
];

const scanRoots = [
  ".gitea",
  "scripts",
  "services/tutorputor-content-generation",
  "services/tutorputor-platform",
  "apps",
  "libs",
  "contracts",
].map((entry) => path.join(productRoot, entry));

const ignoredNames = new Set([
  "node_modules",
  "dist",
  "build",
  "coverage",
  ".gradle",
  ".git",
]);

const scannableExtensions = new Set([
  ".js",
  ".mjs",
  ".ts",
  ".tsx",
  ".json",
  ".yml",
  ".yaml",
  ".sh",
  ".ps1",
  ".gradle",
  ".kts",
]);

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    if (ignoredNames.has(entry.name) || entry.name.startsWith(".ignored_")) {
      return [];
    }

    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return walk(fullPath);
    }

    return entry.isFile() ? [fullPath] : [];
  });
}

const failures = [];

for (const relativePath of forbiddenScripts) {
  if (fs.existsSync(path.join(productRoot, relativePath))) {
    failures.push(`Forbidden repair script still exists: ${relativePath}`);
  }
}

const forbiddenInvocations = forbiddenScripts.map((script) => ({
  script,
  pattern: new RegExp(script.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
}));

for (const file of scanRoots.flatMap(walk)) {
  const ext = path.extname(file);
  if (!scannableExtensions.has(ext)) continue;

  const relativePath = path.relative(productRoot, file).replaceAll(path.sep, "/");
  if (
    [
      "scripts/validate-repair-scripts.mjs",
      "scripts/validate-error-envelope.mjs",
      "scripts/validate-content-generation-source.mjs",
    ].includes(relativePath)
  ) {
    continue;
  }

  const content = fs.readFileSync(file, "utf8");
  for (const { script, pattern } of forbiddenInvocations) {
    if (pattern.test(content)) {
      failures.push(`${relativePath} references removed repair script ${script}`);
    }
  }
}

if (failures.length > 0) {
  console.error("Repair-script validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Repair-script validation passed.");
