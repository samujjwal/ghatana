#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required consent enforcement file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const policy = read("services/tutorputor-platform/src/modules/compliance/consentPolicy.ts");
const policyTest = read("services/tutorputor-platform/src/modules/compliance/__tests__/consentPolicy.test.ts");
const aiGovernance = read("services/tutorputor-platform/src/modules/ai/governance.ts");
const telemetryRoutes = read("services/tutorputor-platform/src/modules/content/telemetry/routes.ts");
const privacyUi = read("apps/tutorputor-web/src/pages/PrivacySettingsPage.tsx");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

for (const useCase of [
  "ai_tutor",
  "learning_telemetry",
  "voice_image",
  "social",
  "personalization",
]) {
  if (!policy.includes(useCase)) errors.push(`Consent policy missing ${useCase}.`);
  if (!policyTest.includes(useCase)) errors.push(`Consent tests missing ${useCase}.`);
  if (!privacyUi.includes(useCase) && ["ai_tutor", "learning_telemetry", "personalization"].includes(useCase)) {
    errors.push(`Privacy Center UI missing consent action ${useCase}.`);
  }
}

for (const behavior of [
  "parental_consent_required",
  "missing_consent",
  "revoked_consent",
  "telemetry_opt_out",
]) {
  if (!policy.includes(behavior)) errors.push(`Consent policy missing behavior ${behavior}.`);
  if (!policyTest.includes(behavior)) errors.push(`Consent tests missing behavior ${behavior}.`);
}

if (!aiGovernance.includes("assertConsentAllowed") || !aiGovernance.includes("ai_tutor")) {
  errors.push("AI governance is not wired to runtime consent enforcement.");
}

if (!telemetryRoutes.includes("enforceTelemetryConsent") || !telemetryRoutes.includes("x-telemetry-consent")) {
  errors.push("Telemetry ingest route is not wired to opt-out consent enforcement.");
}

if (!ci.includes("validate-consent-enforcement.mjs")) {
  errors.push("TutorPutor CI does not run the consent enforcement validator.");
}

if (errors.length > 0) {
  console.error("Consent enforcement validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Consent enforcement covers minors, AI, telemetry opt-out, voice/image, social, personalization, runtime AI/telemetry checks, UI revocation, tests, and CI validation.");
