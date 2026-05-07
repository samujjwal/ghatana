#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");

const requiredFiles = [
  "apps/tutorputor-web/e2e/critical-learner-journey.spec.ts",
  "apps/tutorputor-web/e2e/offline-resume-critical-journey.spec.ts",
  "apps/tutorputor-web/src/pages/OnboardingPage.tsx",
  "apps/tutorputor-web/src/pages/DiagnosticPage.tsx",
  "apps/tutorputor-web/src/pages/CredentialsPage.tsx",
  "apps/tutorputor-web/src/pages/PrivacySettingsPage.tsx",
  "apps/tutorputor-web/src/sw.ts",
  "apps/tutorputor-admin/e2e/admin-core.spec.ts",
  "apps/tutorputor-mobile/e2e/login.yaml",
  "apps/tutorputor-mobile/e2e/dashboard.yaml",
  "apps/tutorputor-mobile/e2e/modules.yaml",
  "apps/tutorputor-mobile/e2e/ai-tutor.yaml",
  "apps/tutorputor-mobile/e2e/offline.yaml",
  "apps/tutorputor-mobile/e2e/navigation.yaml",
  "scripts/run-critical-journey-e2e.ps1",
  "scripts/run-critical-journey-e2e.sh",
];

const requiredStages = [
  "onboarding",
  "diagnostic",
  "pathway",
  "module",
  "simulation",
  "AI tutor",
  "assessment",
  "feedback",
  "remediation",
  "mastery",
  "credential",
  "privacy",
  "offline",
];

const requiredRunnerReferences = [
  "critical-learner-journey.spec.ts",
  "offline-resume-critical-journey.spec.ts",
  "admin-core.spec.ts",
  "login.yaml",
  "dashboard.yaml",
  "modules.yaml",
  "ai-tutor.yaml",
  "offline.yaml",
  "navigation.yaml",
];

const errors = [];

for (const file of requiredFiles) {
  if (!fs.existsSync(path.join(root, file))) {
    errors.push(`Missing required critical journey file: ${file}`);
  }
}

const readIfExists = (relativePath) => {
  const absolutePath = path.join(root, relativePath);
  return fs.existsSync(absolutePath) ? fs.readFileSync(absolutePath, "utf8") : "";
};

const webSpec = readIfExists("apps/tutorputor-web/e2e/critical-learner-journey.spec.ts");
const offlineSpec = readIfExists("apps/tutorputor-web/e2e/offline-resume-critical-journey.spec.ts");
const psRunner = readIfExists("scripts/run-critical-journey-e2e.ps1");
const shRunner = readIfExists("scripts/run-critical-journey-e2e.sh");

const combinedSpec = `${webSpec}\n${offlineSpec}`;
for (const stage of requiredStages) {
  if (!combinedSpec.toLowerCase().includes(stage.toLowerCase())) {
    errors.push(`Critical journey specs do not cover stage: ${stage}`);
  }
}

for (const runnerRef of requiredRunnerReferences) {
  if (!psRunner.includes(runnerRef)) {
    errors.push(`PowerShell critical journey runner does not reference ${runnerRef}`);
  }
  if (!shRunner.includes(runnerRef)) {
    errors.push(`Shell critical journey runner does not reference ${runnerRef}`);
  }
}

const routeMap = readIfExists("apps/tutorputor-web/src/router/routes.tsx");
for (const route of ["onboarding", "diagnostic", "credentials", "settings/privacy", "learn/:simulationId"]) {
  if (!routeMap.includes(route)) {
    errors.push(`Web route map does not expose critical journey route: ${route}`);
  }
}

if (errors.length > 0) {
  console.error("Critical journey coverage validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Critical learner journey coverage is mapped across web, admin, mobile, offline, and runners.");
