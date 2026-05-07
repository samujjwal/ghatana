#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const workflowPath = "products/tutorputor/.gitea/workflows/tutorputor-ci.yml";
const workflow = readFileSync(join(root, workflowPath), "utf8");

const requiredScripts = [
  {
    script: "run-critical-journey-e2e.ps1",
    parity: "run-critical-journey-e2e.sh",
    gate: "Validate critical learner journey coverage",
  },
  {
    script: "run-remediation-proof-suite.sh",
    gate: "Validate remediation proof suite coverage",
  },
  {
    script: "verify-content-routes.sh",
    gate: "Validate content route verification script",
  },
  {
    script: "verify-social-routes.sh",
    gate: "Validate social route verification coverage",
  },
  {
    script: "verify-lti-config.sh",
    gate: "Validate LTI route/config verification coverage",
  },
  {
    script: "verify-lti-phase2-routes.ps1",
    gate: "Validate LTI route/config verification coverage",
  },
  {
    script: "verify-lti-grade-passback.sh",
    gate: "Validate LTI evidence-backed grade passback",
  },
  {
    script: "verify-gdpr-delete-flow.sh",
    gate: "Validate privacy product flows",
  },
  {
    script: "verify-at-rest-encryption.ps1",
    gate: "Validate encryption verification coverage",
  },
];

const failures = [];

for (const requirement of requiredScripts) {
  const scriptPath = join(root, "products/tutorputor/scripts", requirement.script);
  if (!existsSync(scriptPath)) {
    failures.push(`Missing verification script: ${requirement.script}`);
    continue;
  }

  if (requirement.parity) {
    const parityPath = join(root, "products/tutorputor/scripts", requirement.parity);
    if (!existsSync(parityPath)) {
      failures.push(
        `${requirement.script} requires Linux/Windows parity script ${requirement.parity}`,
      );
    }
  }

  if (!workflow.includes(requirement.gate)) {
    failures.push(
      `${requirement.script} is not represented by mandatory CI gate "${requirement.gate}"`,
    );
  }
}

const contentRoutes = readFileSync(
  join(root, "products/tutorputor/scripts/verify-content-routes.sh"),
  "utf8",
);
if (!contentRoutes.includes("--filter @tutorputor/platform exec vitest run")) {
  failures.push("verify-content-routes.sh must invoke maintained platform route tests");
}

if (failures.length > 0) {
  console.error("CI verification gate validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("CI verification gate validation passed.");
