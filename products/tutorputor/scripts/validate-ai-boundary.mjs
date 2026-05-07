#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const productRoot = path.resolve("products/tutorputor");
const allowedProviderRoots = [
  path.join(productRoot, "libs/tutorputor-ai/src"),
].map((entry) => path.normalize(entry));
const scannedRoots = [
  "apps",
  "services",
  "libs",
].map((entry) => path.join(productRoot, entry));
const ignoredSegments = new Set([
  ".git",
  ".ignored_admin",
  ".ignored_contracts",
  ".ignored_core",
  ".ignored_platform",
  ".ignored_simulation",
  "build",
  "coverage",
  "dist",
  "node_modules",
]);
const sourceExtensions = new Set([".ts", ".tsx", ".js", ".jsx", ".mjs"]);
const providerPatterns = [
  /\bfrom\s+["']openai["']/,
  /\bimport\s+OpenAI\s+from\s+["']openai["']/,
  /\bnew\s+OpenAI\s*\(/,
  /\bfrom\s+["']@anthropic-ai\//,
  /\bfrom\s+["']@google\/generative-ai["']/,
  /\bfrom\s+["']@langchain\//,
  /\bfrom\s+["']langchain\//,
  /\bnew\s+ChatOpenAI\s*\(/,
  /\bnew\s+ChatAnthropic\s*\(/,
];

function isAllowedProviderFile(filePath) {
  const normalized = path.normalize(filePath);
  return allowedProviderRoots.some((root) => normalized.startsWith(root));
}

function isTestOrFixture(filePath) {
  const normalized = filePath.replaceAll("\\", "/");
  return (
    normalized.includes("/__tests__/") ||
    normalized.includes("/fixtures/") ||
    normalized.endsWith(".test.ts") ||
    normalized.endsWith(".test.tsx") ||
    normalized.endsWith(".spec.ts") ||
    normalized.endsWith(".spec.tsx")
  );
}

function walk(dir, files = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (error) {
    if (error?.code === "ENOENT") {
      return files;
    }
    throw error;
  }

  for (const entry of entries) {
    if (ignoredSegments.has(entry.name)) {
      continue;
    }
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
      continue;
    }
    if (entry.isFile() && sourceExtensions.has(path.extname(entry.name))) {
      files.push(fullPath);
    }
  }
  return files;
}

const violations = [];

for (const root of scannedRoots) {
  for (const filePath of walk(root)) {
    if (isAllowedProviderFile(filePath) || isTestOrFixture(filePath)) {
      continue;
    }
    const content = fs.readFileSync(filePath, "utf8");
    const lines = content.split(/\r?\n/);
    lines.forEach((line, index) => {
      if (providerPatterns.some((pattern) => pattern.test(line))) {
        violations.push({
          filePath: path.relative(productRoot, filePath).replaceAll("\\", "/"),
          line: index + 1,
          text: line.trim(),
        });
      }
    });
  }
}

if (violations.length > 0) {
  console.error("Direct AI provider usage found outside the canonical AI boundary:");
  for (const violation of violations) {
    console.error(
      `- ${violation.filePath}:${violation.line} ${violation.text}`,
    );
  }
  console.error(
    "Use the CanonicalAIService contract or the tutorputor-ai provider boundary instead.",
  );
  process.exit(1);
}

console.log("AI boundary validation passed.");
