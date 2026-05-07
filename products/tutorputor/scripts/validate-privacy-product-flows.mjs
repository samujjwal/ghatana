#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required privacy product file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const routes = read("services/tutorputor-platform/src/modules/compliance/routes.ts");
const service = read("services/tutorputor-platform/src/modules/compliance/service.ts");
const types = read("services/tutorputor-platform/src/modules/compliance/types.ts");
const ui = read("apps/tutorputor-web/src/pages/PrivacySettingsPage.tsx");
const tests = read("services/tutorputor-platform/src/modules/compliance/__tests__/routes.test.ts");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

for (const endpoint of [
  "/privacy-center",
  "/export",
  "/export/:requestId",
  "/export/:requestId/download",
  "/deletion/request",
  "/deletion/:requestId",
  "/consent/revoke",
  "/telemetry/delete",
  "/deletion/process-now",
]) {
  if (!routes.includes(endpoint)) {
    errors.push(`Compliance routes missing product endpoint ${endpoint}.`);
  }
}

for (const method of [
  "getPrivacyDataAccessSummary",
  "requestUserExport",
  "downloadExport",
  "requestUserDeletion",
  "cancelDeletionRequest",
  "revokeConsent",
  "deleteTelemetryForUser",
  "processDeletionNow",
]) {
  if (!service.includes(method)) {
    errors.push(`Compliance service missing product method ${method}.`);
  }
  if (!tests.includes(method)) {
    errors.push(`Compliance route tests missing coverage for ${method}.`);
  }
}

for (const type of [
  "PrivacyDataAccessSummary",
  "ConsentRevocationResult",
  "PrivacyAuditEvidence",
]) {
  if (!types.includes(type)) {
    errors.push(`Compliance types missing ${type}.`);
  }
}

for (const uiAction of [
  "Export My Data",
  "Request Deletion",
  "Delete Telemetry",
  "/api/v1/compliance/privacy-center",
  "/api/v1/compliance/export",
  "/api/v1/compliance/deletion/request",
  "/api/v1/compliance/consent/revoke",
  "/api/v1/compliance/telemetry/delete",
]) {
  if (!ui.includes(uiAction)) {
    errors.push(`Privacy Center UI missing action or API ${uiAction}.`);
  }
}

for (const script of [
  "verify-gdpr-delete-flow.sh",
  "verify-gdpr-delete-cascade.sql",
  "collect-gdpr-deletion-evidence.sh",
]) {
  if (!fs.existsSync(path.join(root, "scripts", script))) {
    errors.push(`GDPR evidence script missing: ${script}.`);
  }
}

if (!ci.includes("validate-privacy-product-flows.mjs")) {
  errors.push("TutorPutor CI does not run the privacy product flow validator.");
}

if (errors.length > 0) {
  console.error("Privacy product flow validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Privacy product flows cover export, deletion, consent revocation, telemetry deletion, audit evidence, UI actions, script parity, and CI validation.");
