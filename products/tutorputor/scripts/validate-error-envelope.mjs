#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const productRoot = path.resolve("products/tutorputor");
const failures = [];

const removedRepairScript = path.join(productRoot, "scripts/fix-error-handling.js");
if (fs.existsSync(removedRepairScript)) {
  failures.push("scripts/fix-error-handling.js must stay deleted; use the canonical error envelope instead.");
}

const requiredFiles = [
  "services/tutorputor-platform/src/core/http/error-envelope.ts",
  "services/tutorputor-platform/src/core/middleware/error-handler.ts",
  "services/tutorputor-platform/src/core/middleware/standard-error-response.ts",
  "services/tutorputor-platform/src/core/http/requestContext.ts",
  "api/tutorputor-api.openapi.yaml",
  "contracts/v1/openapi.ts",
];

for (const relativePath of requiredFiles) {
  const fullPath = path.join(productRoot, relativePath);
  if (!fs.existsSync(fullPath)) {
    failures.push(`Required error-envelope file is missing: ${relativePath}`);
  }
}

function read(relativePath) {
  return fs.readFileSync(path.join(productRoot, relativePath), "utf8");
}

if (!read("services/tutorputor-platform/src/core/http/error-envelope.ts").includes("CanonicalErrorEnvelope")) {
  failures.push("CanonicalErrorEnvelope type must be defined in core/http/error-envelope.ts");
}

for (const relativePath of [
  "services/tutorputor-platform/src/core/middleware/error-handler.ts",
  "services/tutorputor-platform/src/core/middleware/standard-error-response.ts",
  "services/tutorputor-platform/src/core/http/requestContext.ts",
]) {
  if (!read(relativePath).includes("createErrorEnvelope")) {
    failures.push(`${relativePath} must use createErrorEnvelope`);
  }
}

const openApi = read("api/tutorputor-api.openapi.yaml");
for (const requiredFragment of [
  "statusCode:",
  "timestamp:",
  "code:",
  "message:",
  "traceId:",
]) {
  if (!openApi.includes(requiredFragment)) {
    failures.push(`OpenAPI ErrorResponse must include ${requiredFragment}`);
  }
}

const openApiContracts = read("contracts/v1/openapi.ts");
for (const requiredFragment of [
  "export interface ErrorResponse",
  "statusCode: number",
  "timestamp: string",
  "traceId: string",
]) {
  if (!openApiContracts.includes(requiredFragment)) {
    failures.push(`contracts/v1/openapi.ts ErrorResponse missing ${requiredFragment}`);
  }
}

if (failures.length > 0) {
  console.error("Error envelope validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Canonical error envelope validation passed.");
