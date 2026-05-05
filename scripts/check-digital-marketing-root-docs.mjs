#!/usr/bin/env node

import { readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const files = [
  "digital-marketing-product-architecture.md",
  "digital-marketing-product-architecture-canonical.md",
  "digital-marketing-product-architecture-v2.md",
];

const requiredLinks = [
  "./products/digital-marketing/docs/01-ARCHITECTURE.md",
  "./products/digital-marketing/docs/02-API_CONTRACTS.md",
  "./products/digital-marketing/docs/06-IMPLEMENTATION_PLAN.md",
];

const violations = [];

for (const file of files) {
  const content = readFileSync(path.join(repoRoot, file), "utf8");
  const lineCount = content.trim().split(/\r?\n/).length;
  for (const link of requiredLinks) {
    if (!content.includes(link)) {
      violations.push(`${file}: missing canonical link ${link}`);
    }
  }
  if (lineCount > 12) {
    violations.push(`${file}: root redirect doc must stay a short stub (found ${lineCount} lines)`);
  }
}

if (violations.length > 0) {
  console.error(`❌ Digital Marketing root-doc ownership check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ Digital Marketing root-doc ownership check passed.");
