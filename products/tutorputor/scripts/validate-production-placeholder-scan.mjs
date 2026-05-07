#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const productionPath = /^products\/tutorputor\/(apps|contracts|libs|services)\//;
const ignoredPathParts = [
  "/__tests__/",
  "/e2e/",
  "/fixtures/",
  "/test/",
  "/tests/",
  ".test.",
  ".spec.",
  ".stories.",
  "/node_modules/",
  "/dist/",
  "/build/",
  "/coverage/",
];

const forbiddenPatterns = [
  { pattern: /TODO\s*:\s*production/i, label: "production TODO marker" },
  { pattern: /FIXME\s*:\s*production/i, label: "production FIXME marker" },
  { pattern: /placeholder_(secret|key|token)/i, label: "placeholder credential" },
  { pattern: /stripe_test_placeholder_secret/i, label: "test Stripe secret in production source" },
  { pattern: /mock implementation for production/i, label: "mock production implementation" },
  { pattern: /not implemented for production/i, label: "unimplemented production flow" },
];

const trackedFiles = execFileSync("git", ["ls-files", "products/tutorputor"], {
  cwd: repoRoot,
  encoding: "utf8",
})
  .split(/\r?\n/)
  .filter(Boolean)
  .map((file) => file.replace(/\\/g, "/"))
  .filter((file) => productionPath.test(file))
  .filter((file) => !ignoredPathParts.some((part) => file.includes(part)));

const errors = [];

for (const file of trackedFiles) {
  const absolutePath = path.join(repoRoot, file);
  if (!fs.existsSync(absolutePath)) continue;
  const source = fs.readFileSync(absolutePath, "utf8");
  for (const { pattern, label } of forbiddenPatterns) {
    if (pattern.test(source)) {
      errors.push(`${file}: contains ${label}`);
    }
  }
}

if (errors.length > 0) {
  console.error("Production placeholder scan failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Production placeholder scan passed for TutorPutor app, contract, lib, and service source.");
