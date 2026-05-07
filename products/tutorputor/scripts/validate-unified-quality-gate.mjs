#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const workflowPath = path.resolve(
  process.cwd(),
  "products",
  "tutorputor",
  ".gitea",
  "workflows",
  "tutorputor-ci.yml",
);

const workflow = fs.existsSync(workflowPath) ? fs.readFileSync(workflowPath, "utf8") : "";

const requiredMarkers = [
  { area: "lint/typecheck", marker: "pnpm --filter @tutorputor/core type-check" },
  { area: "contract build", marker: "pnpm --filter @tutorputor/contracts build" },
  { area: "API contract drift", marker: "validate-api-contract-drift.mjs" },
  { area: "generated contract declarations", marker: "validate-generated-contracts.mjs" },
  { area: "unit tests", marker: "pnpm --filter @tutorputor/platform exec vitest run" },
  { area: "coverage", marker: "--coverage" },
  { area: "coverage threshold", marker: "Coverage $COVERAGE% is below threshold 75%" },
  { area: "integration tests", marker: "p0-4-e2e-worker-lifecycle.integration.test.ts" },
  { area: "accessibility", marker: "validate-accessibility-gates.mjs" },
  { area: "E2E critical journey", marker: "validate-critical-journey-coverage.mjs" },
  { area: "offline journey", marker: "validate-offline-sync-gates.mjs" },
  { area: "performance budget", marker: "performance-test-report" },
  { area: "bundle policy", marker: "validate-frontend-dependency-policy.mjs" },
  { area: "security scan", marker: "aquasecurity/trivy-action" },
  { area: "privacy flow", marker: "validate-privacy-product-flows.mjs" },
  { area: "consent enforcement", marker: "validate-consent-enforcement.mjs" },
  { area: "permission matrix", marker: "validate-permission-matrix.mjs" },
  { area: "type-safety ratchet", marker: "ANY_TYPE_THRESHOLD: \"0\"" },
  { area: "golden semantic tests", marker: "validate-golden-correctness-gates.mjs" },
  { area: "fresh report artifacts", marker: "validate-test-report-policy.mjs" },
  { area: "production placeholder scan", marker: "validate-production-placeholder-scan.mjs" },
  { area: "unified quality meta-gate", marker: "validate-unified-quality-gate.mjs" },
  { area: "strict artifact uploads", marker: "if-no-files-found: error" },
];

const errors = [];

for (const { area, marker } of requiredMarkers) {
  if (!workflow.includes(marker)) {
    errors.push(`Missing unified quality gate marker for ${area}: ${marker}`);
  }
}

const uploadArtifactCount = (workflow.match(/actions\/upload-artifact@v4/g) ?? []).length;
if (uploadArtifactCount < 7) {
  errors.push(`Expected at least 7 report/security artifact uploads, found ${uploadArtifactCount}.`);
}

if (errors.length > 0) {
  console.error("Unified TutorPutor quality gate validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Unified TutorPutor quality gate covers lint, typecheck, contracts, unit, integration, accessibility, E2E, performance, security, privacy, placeholders, and artifacts.");
