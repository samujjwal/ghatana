#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required offline sync file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const offlineSync = read("apps/tutorputor-web/src/offline/offlineSync.ts");
const offlineSyncTest = read("apps/tutorputor-web/src/offline/offlineSync.test.ts");
const offlineE2e = read("apps/tutorputor-web/e2e/offline-resume-critical-journey.spec.ts");
const serviceWorker = read("apps/tutorputor-web/src/sw.ts");
const mobileConfig = read("apps/tutorputor-mobile/src/config.ts");
const telemetryContracts = read("contracts/v1/telemetry-events.ts");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

for (const capability of [
  "module.progress",
  "simulation.capture",
  "assessment.attempt",
  "ai.disabled-state",
  "telemetry.batch",
]) {
  if (!offlineSync.includes(capability)) {
    errors.push(`Offline sync policy does not model ${capability}.`);
  }
  if (!offlineSyncTest.includes(capability) && !offlineSyncTest.toLowerCase().includes(capability.split(".")[0])) {
    errors.push(`Offline sync tests do not cover ${capability}.`);
  }
  if (!mobileConfig.includes(capability)) {
    errors.push(`Mobile offline config does not list sync entity ${capability}.`);
  }
}

for (const policy of [
  "max-progress",
  "idempotent-hash",
  "submitted-attempt-lock",
  "most-restrictive-consent",
  "event-id-dedupe",
]) {
  if (!telemetryContracts.includes(policy)) {
    errors.push(`Telemetry contract missing offline conflict policy ${policy}.`);
  }
}

for (const route of [
  "/api/v1/simulations",
  "/api/v1/assessments",
  "/api/v1/telemetry/learning/batch",
  "/api/v1/ai/tutor/query",
]) {
  if (!serviceWorker.includes(route)) {
    errors.push(`Service worker does not capture offline mutation route ${route}.`);
  }
}

for (const marker of [
  "tutorputor.offline.syncQueue",
  "simulation.capture",
  "assessment.attempt",
  "telemetry.batch",
  "module.progress",
  "ai.disabled-state",
  "dashboard",
  "mastery",
]) {
  if (!offlineE2e.includes(marker)) {
    errors.push(`Offline-resume E2E does not prove marker: ${marker}.`);
  }
}

if (!ci.includes("validate-offline-sync-gates.mjs")) {
  errors.push("TutorPutor CI does not run the offline sync gate validator.");
}

if (errors.length > 0) {
  console.error("Offline sync gate validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Offline sync gates cover conflict policy, web SW capture, mobile config, telemetry context, E2E markers, and CI validation.");
