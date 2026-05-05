#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const productsRoot = path.join(repoRoot, "products");
const violations = [];
const targetExtensions = new Set([".java", ".kt"]);

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry === "build" || entry === "dist" || entry === ".git") {
      continue;
    }

    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      walk(fullPath);
      continue;
    }

    if (!targetExtensions.has(path.extname(fullPath))) {
      continue;
    }

    const normalized = fullPath.replace(/\\/g, "/");
    if (!normalized.includes("/src/main/")) {
      continue;
    }

    const content = readFileSync(fullPath, "utf8");
    if (content.includes("InMemoryBoundaryPolicyStore")) {
      violations.push(path.relative(repoRoot, fullPath));
    }
  }
}

walk(productsRoot);

if (violations.length > 0) {
  console.error("❌ InMemoryBoundaryPolicyStore is limited to kernel tests/dev scaffolding and must not appear in product main sources:\n");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ No product main sources reference InMemoryBoundaryPolicyStore.");
