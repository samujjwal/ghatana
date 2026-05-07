#!/usr/bin/env node
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

const checks = [
  {
    file: "products/tutorputor/ci/deploy/helm/tutorputor/Chart.yaml",
    patterns: ["apiVersion:", "name:", "version:"],
  },
  {
    file: "products/tutorputor/ci/deploy/helm/tutorputor/values-production.yaml",
    patterns: [
      "replicaCount: 5",
      "autoscaling:",
      "minReplicas: 3",
      "maxReplicas:",
      "resources:",
      "tls:",
    ],
  },
  {
    file: "products/tutorputor/ci/deploy/helm/tutorputor/templates/deployment.yaml",
    patterns: [
      "securityContext:",
      "resources:",
      "livenessProbe:",
      "readinessProbe:",
      "path: /health",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/Dockerfile",
    patterns: [
      "FROM node:22-bookworm-slim AS runtime",
      "USER tutorputor",
      "HEALTHCHECK",
      "NODE_ENV=production",
      "@tutorputor/platform",
      "@tutorputor/core",
    ],
  },
  {
    file: "products/tutorputor/scripts/deploy-production.sh",
    patterns: [
      "PROJECT_ROOT=\"$(cd \"$SCRIPT_DIR/..\" && pwd)\"",
      "helm template",
      "helm upgrade --install",
      "--atomic",
      "--wait",
      "rollback_deployment",
      "dry_run_deployment",
      "run_smoke_tests",
    ],
  },
  {
    file: "products/tutorputor/scripts/verify-backups.sh",
    patterns: [
      "STRICT_BACKUP_MIN_COUNT",
      "pg_restore --list",
      "exit 1",
    ],
  },
  {
    file: "products/tutorputor/scripts/restore-database.sh",
    patterns: [
      "DRY_RUN",
      "pg_restore --list",
      "PROJECT_ROOT",
      "prisma:migrate:deploy",
    ],
  },
  {
    file: "products/tutorputor/.gitea/workflows/tutorputor-ci.yml",
    patterns: [
      "Validate production readiness gates",
      "validate-production-readiness.mjs",
    ],
  },
];

const failures = [];

for (const check of checks) {
  const path = join(root, check.file);
  if (!existsSync(path)) {
    failures.push(`Missing file: ${check.file}`);
    continue;
  }

  const text = readFileSync(path, "utf8");
  for (const pattern of check.patterns) {
    if (!text.includes(pattern)) {
      failures.push(`${check.file} is missing required marker: ${pattern}`);
    }
  }
}

const dockerfile = readFileSync(
  join(root, "products/tutorputor/services/tutorputor-platform/Dockerfile"),
  "utf8",
);
if (dockerfile.includes("@ghatana/tutorputor-platform") || dockerfile.includes("tutorputor-db")) {
  failures.push("Dockerfile still references obsolete package names or missing tutorputor-db path");
}

const restore = readFileSync(
  join(root, "products/tutorputor/scripts/restore-database.sh"),
  "utf8",
);
if (restore.includes("/Users/samujjwal")) {
  failures.push("restore-database.sh must not contain a developer-local absolute path");
}

if (failures.length > 0) {
  console.error("Production readiness validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Production readiness validation passed.");
