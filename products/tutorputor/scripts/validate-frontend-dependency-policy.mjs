#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, "..");

const appManifests = [
  "apps/tutorputor-web/package.json",
  "apps/tutorputor-admin/package.json",
];

const requiredSharedPackages = [
  "@ghatana/design-system",
  "@ghatana/theme",
  "@tutorputor/ui",
];

const approvedDirectAppOverlap = new Set([
  "@tanstack/query-core",
  "@tanstack/react-query",
  "clsx",
  "cookie",
  "jotai",
  "lucide-react",
  "react",
  "react-dom",
  "react-router",
  "react-router-dom",
  "recharts",
  "set-cookie-parser",
  "tailwind-merge",
]);

function readJson(relativePath) {
  return JSON.parse(fs.readFileSync(path.join(productRoot, relativePath), "utf8"));
}

const manifests = appManifests.map((relativePath) => ({
  relativePath,
  json: readJson(relativePath),
}));

const failures = [];

for (const { relativePath, json } of manifests) {
  const dependencies = json.dependencies ?? {};
  for (const packageName of requiredSharedPackages) {
    if (!dependencies[packageName]) {
      failures.push(`${relativePath} must consume shared package ${packageName}`);
    }
  }
}

const dependencyOwners = new Map();
for (const { relativePath, json } of manifests) {
  for (const packageName of Object.keys(json.dependencies ?? {})) {
    if (packageName.startsWith("@ghatana/") || packageName.startsWith("@tutorputor/")) {
      continue;
    }
    const owners = dependencyOwners.get(packageName) ?? [];
    owners.push(relativePath);
    dependencyOwners.set(packageName, owners);
  }
}

for (const [packageName, owners] of dependencyOwners.entries()) {
  if (owners.length < 2) continue;
  if (!approvedDirectAppOverlap.has(packageName)) {
    failures.push(
      `${packageName} is duplicated across ${owners.join(", ")}; move shared usage behind @tutorputor/ui or approve it in validate-frontend-dependency-policy.mjs`,
    );
  }
}

for (const packageName of approvedDirectAppOverlap) {
  if (!dependencyOwners.has(packageName)) {
    failures.push(
      `${packageName} is listed as approved overlap but is no longer used by TutorPutor apps; remove it from the policy.`,
    );
  }
}

const bundleScript = fs.readFileSync(
  path.join(productRoot, "scripts/analyze-bundle.sh"),
  "utf8",
);
for (const requiredFragment of [
  "MAX_BUNDLE_SIZE=500",
  'PRODUCT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"',
  'cd "$PRODUCT_ROOT/$APP_PATH"',
]) {
  if (!bundleScript.includes(requiredFragment)) {
    failures.push(`scripts/analyze-bundle.sh must include ${requiredFragment}`);
  }
}

if (failures.length > 0) {
  console.error("Frontend dependency policy validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend dependency policy validation passed.");
