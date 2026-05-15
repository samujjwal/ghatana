#!/usr/bin/env node
// Authoritative Source: docs/SECRETS_CLASSIFICATION.md

import { execFileSync } from "child_process";
import { readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const configPath = path.join(repoRoot, "config/security-secret-scan.json");

const violations = [];

function loadPolicy() {
  const policy = JSON.parse(readFileSync(configPath, "utf8"));
  const requiredArrays = [
    "includeRoots",
    "fileAllowlist",
    "excludeFragments",
    "scannableNames",
    "scannableExtensions",
    "patterns",
  ];

  for (const key of requiredArrays) {
    if (!Array.isArray(policy[key])) {
      throw new Error(`config/security-secret-scan.json must declare ${key}[]`);
    }
  }

  return {
    includeRoots: policy.includeRoots,
    fileAllowlist: new Set(policy.fileAllowlist),
    excludeFragments: policy.excludeFragments,
    scannableNames: new Set(policy.scannableNames),
    scannableExtensions: new Set(policy.scannableExtensions),
    patterns: policy.patterns.map((entry) => ({
      id: entry.id,
      regex: new RegExp(entry.pattern, entry.flags ?? ""),
    })),
  };
}

const policy = loadPolicy();

function shouldScan(relPath) {
  const normalized = relPath.replace(/\\/g, "/");
  if (policy.fileAllowlist.has(normalized)) return false;
  if (policy.excludeFragments.some((fragment) => normalized.includes(fragment))) return false;
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
  return isProductDoc || isProductInfra || policy.scannableNames.has(base) || policy.scannableExtensions.has(extension);
}

const rgArgs = [
  "--files",
  ...policy.includeRoots,
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
    policy.patterns.forEach((pattern) => {
      const match = line.match(pattern.regex);
      if (match) {
        violations.push(`${relPath}:${index + 1} ${pattern.id} ${match[0]}`);
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
