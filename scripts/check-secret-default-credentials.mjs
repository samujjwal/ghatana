#!/usr/bin/env node

import { execFileSync } from "child_process";
import { readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const INCLUDE_ROOTS = [
  "products/phr",
  "products/finance",
  "products/digital-marketing",
  "products/flashit",
];
const FILE_ALLOWLIST = new Set([
  "docs/SECRETS_CLASSIFICATION.md",
  "docs/kernel/KERNEL_PURITY_RULES.md",
  "docs/process/PRODUCT_TRUTHFULNESS_POLICY.md",
]);
const EXCLUDE_FRAGMENTS = [
  "/node_modules/",
  "/dist/",
  "/build/",
  "/.git/",
  "/docs/archive/",
  "/.env.development",
  "/.env.local",
  "/.env.production",
  "/pnpm-lock.yaml",
  "/package-lock.json",
  "/yarn.lock",
];
const SCANNABLE_NAMES = new Set(["Dockerfile", "package.json", "README.md", "REVIEW_REPORT.md"]);
const SCANNABLE_EXTENSIONS = new Set([".yml", ".yaml", ".env", ".example", ".sh"]);
const PATTERNS = [
  /\b(sk-placeholder|changeme|password123|your_super_secret_[a-z0-9_]*|your_[a-z0-9_]+(?:_id|_key|_secret|_dsn|_email)?|postgres:password|admin\/admin|minioadmin123|guest\/guest)\b/i,
  /\b(ghatana123|redis123|flashit_dev_password|flashit_test_password)\b/,
  /\$\{(?:[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|KEY|DSN|DATABASE_URL|REDIS_URL)[A-Z0-9_]*):-[^}]+\}/,
];

const violations = [];

function shouldScan(relPath) {
  const normalized = relPath.replace(/\\/g, "/");
  if (FILE_ALLOWLIST.has(normalized)) return false;
  if (EXCLUDE_FRAGMENTS.some((fragment) => normalized.includes(fragment))) return false;
  const base = path.basename(normalized);
  const extension = path.extname(base);
  const isProductDoc = base === "README.md" || base === "REVIEW_REPORT.md" || normalized.includes("/docs/");
  const isProductInfra =
    base === "package.json" ||
    base === "Dockerfile" ||
    normalized.includes("docker-compose") ||
    base.endsWith(".env.example") ||
    extension === ".env" ||
    extension === ".example" ||
    extension === ".yml" ||
    extension === ".yaml" ||
    extension === ".sh";
  return isProductDoc || isProductInfra || SCANNABLE_NAMES.has(base) || SCANNABLE_EXTENSIONS.has(extension);
}

const rgArgs = [
  "--files",
  ...INCLUDE_ROOTS,
  "-g", "*.yml",
  "-g", "*.yaml",
  "-g", "*.env",
  "-g", "*.example",
  "-g", "*.md",
  "-g", "*.sh",
  "-g", "package.json",
  "-g", "Dockerfile",
];

const files = execFileSync("rg", rgArgs, { cwd: repoRoot, encoding: "utf8" })
  .split(/\r?\n/)
  .filter(Boolean)
  .filter(shouldScan)
  .map((relPath) => ({
    relPath,
    fullPath: path.join(repoRoot, relPath),
  }));

for (const { fullPath, relPath } of files) {
  const lines = readFileSync(fullPath, "utf8").split(/\r?\n/);
  lines.forEach((line, index) => {
    if (/^\s*(#|\/\/)/.test(line)) {
      return;
    }
    PATTERNS.forEach((pattern) => {
      const match = line.match(pattern);
      if (match) {
        violations.push(`${relPath}:${index + 1} ${match[0]}`);
      }
    });
  });
}

if (violations.length > 0) {
  console.error(`❌ Secret/default credential scan failed with ${violations.length} violation(s):\n`);
  for (const violation of violations.slice(0, 50)) {
    console.error(`- ${violation}`);
  }
  if (violations.length > 50) {
    console.error(`... and ${violations.length - 50} more`);
  }
  process.exit(1);
}

console.log("✅ Secret/default credential scan passed.");
