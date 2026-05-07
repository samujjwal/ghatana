#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

const metricNames = [
  "tutorputor_ai_request_duration_seconds",
  "tutorputor_ai_token_cost_usd_total",
  "tutorputor_ai_failures_total",
  "tutorputor_simulation_runtime_errors_total",
  "tutorputor_telemetry_ingest_failures_total",
  "tutorputor_assessment_scoring_failures_total",
  "tutorputor_lti_passback_failures_total",
  "tutorputor_content_generation_validation_failures_total",
  "tutorputor_privacy_deletion_failures_total",
];

const alertNames = [
  "TutorPutorAILatencyHigh",
  "TutorPutorAICostSpike",
  "TutorPutorAIFailureRate",
  "TutorPutorSimulationRuntimeErrors",
  "TutorPutorTelemetryIngestFailures",
  "TutorPutorAssessmentScoringFailures",
  "TutorPutorLTIPassbackFailures",
  "TutorPutorContentGenerationValidationFailures",
  "TutorPutorPrivacyDeletionFailures",
];

const files = {
  metrics: "products/tutorputor/services/tutorputor-platform/src/core/observability/metrics.ts",
  metricsTest: "products/tutorputor/services/tutorputor-platform/src/core/observability/metrics.test.ts",
  alerts: "shared-services/infrastructure/monitoring/alerts/simulation-alert-rules.yml",
  workflow: "products/tutorputor/.gitea/workflows/tutorputor-ci.yml",
};

const texts = Object.fromEntries(
  Object.entries(files).map(([key, file]) => [key, readFileSync(join(root, file), "utf8")]),
);

const failures = [];

for (const metric of metricNames) {
  if (!texts.metrics.includes(metric)) {
    failures.push(`${files.metrics} is missing metric ${metric}`);
  }
  if (!texts.metricsTest.includes(metric)) {
    failures.push(`${files.metricsTest} is missing test assertion for ${metric}`);
  }
  if (!texts.alerts.includes(metric)) {
    failures.push(`${files.alerts} is missing alert expression for ${metric}`);
  }
}

for (const alert of alertNames) {
  if (!texts.alerts.includes(`alert: ${alert}`)) {
    failures.push(`${files.alerts} is missing alert ${alert}`);
  }
}

for (const marker of [
  "recordTutorPutorDomainMetric",
  "ai.request",
  "simulation.error",
  "telemetry.ingest.failure",
  "assessment.scoring.failure",
  "lti.passback.failure",
  "content_generation.validation.failure",
  "privacy.deletion.failure",
]) {
  if (!texts.metrics.includes(marker)) {
    failures.push(`${files.metrics} is missing recorder marker ${marker}`);
  }
}

if (!texts.workflow.includes("Validate TutorPutor observability gates")) {
  failures.push("CI workflow is missing TutorPutor observability gate validation");
}

if (failures.length > 0) {
  console.error("Observability gate validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("TutorPutor observability gate validation passed.");
