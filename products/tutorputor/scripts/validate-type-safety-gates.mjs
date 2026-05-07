#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const files = {
  allowlist: "products/tutorputor/config/ts-nocheck-allowlist.txt",
  audit: "products/tutorputor/scripts/audit-any-types.ts",
  workflow: "products/tutorputor/.gitea/workflows/tutorputor-ci.yml",
};

const texts = Object.fromEntries(
  Object.entries(files).map(([key, file]) => [key, readFileSync(join(root, file), "utf8")]),
);

const failures = [];

for (const marker of [
  "ANY_AUDIT_ROOT",
  "ANY_AUDIT_INCLUDE_TESTS",
  "ANY_TYPE_THRESHOLD",
  "__tests__",
]) {
  if (!texts.audit.includes(marker)) {
    failures.push(`${files.audit} is missing audit marker ${marker}`);
  }
}

if (texts.allowlist.includes("plugin-policy.ts")) {
  failures.push("ts-nocheck allowlist still includes already-typed plugin-policy.ts");
}

for (const marker of [
  "Prevent new @ts-nocheck usage",
  "Audit production any usage",
  "node products/tutorputor/scripts/audit-any-types.ts",
  "ANY_TYPE_THRESHOLD: \"176\"",
  "Validate type-safety gates",
]) {
  if (!texts.workflow.includes(marker)) {
    failures.push(`${files.workflow} is missing CI marker ${marker}`);
  }
}

if (failures.length > 0) {
  console.error("Type-safety gate validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Type-safety gate validation passed.");
