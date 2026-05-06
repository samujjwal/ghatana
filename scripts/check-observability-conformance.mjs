#!/usr/bin/env node

import { existsSync, readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const flowManifestFile = "config/observability/product-observability-flows.json";

const contracts = [
  {
    name: "FlashIt API observability bootstrap",
    file: "products/flashit/backend/gateway/src/server.ts",
    required: [
      "registerLoggerPlugin",
      "registerTracingMiddleware",
      "metricsPlugin",
      "monitoring",
      "tenant-isolation",
    ],
  },
  {
    name: "Kernel-owned FlashIt scrape config",
    file: "monitoring/prometheus/prometheus.yml",
    required: [
      "job_name: 'flashit-gateway'",
      "job_name: 'flashit-agent'",
      "job_name: 'flashit-redis'",
      "job_name: 'flashit-postgres'",
      "product: 'flashit'",
    ],
  },
  {
    name: "Kernel-owned product dashboard overlay provisioning",
    file: "monitoring/grafana/provisioning/dashboards-products.yaml",
    required: [
      "Product Observability Overlays",
      "/etc/grafana/dashboards/products",
      "foldersFromFilesStructure: true",
    ],
  },
  {
    name: "FlashIt observability overlay compose mounts",
    file: "products/flashit/docker-compose.local.yml",
    required: [
      "${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/prometheus/prometheus.yml",
      "${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/grafana/provisioning",
      "${FLASHIT_OBSERVABILITY_OVERLAY_ROOT:-./monitoring}/alerts/flashit-rules.yml",
      "${FLASHIT_OBSERVABILITY_OVERLAY_ROOT:-./monitoring}/grafana/dashboards",
    ],
  },
  {
    name: "DMOS API observability bootstrap",
    file: "products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java",
    required: [
      "wireObservability()",
      "DmosObservability",
      "Metrics",
      "TracingManager",
    ],
  },
  {
    name: "Finance trace metadata contract",
    file: "products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java",
    required: [
      "outcomeMetadata(",
      "\"tenant_id\"",
      "\"transaction_id\"",
      "\"idempotency_key\"",
      "\"audit_classification\"",
    ],
  },
  {
    name: "Finance transaction observability test evidence",
    file: "products/finance/src/test/java/com/ghatana/finance/service/TransactionServiceTest.java",
    required: [
      "testProcessTransaction_LowRisk_ShouldApprove",
      "get(\"tenant_id\")",
      "get(\"transaction_id\")",
      "get(\"idempotency_key\")",
      "get(\"audit_classification\")",
      "finance_transaction_manual_review",
    ],
  },
  {
    name: "PHR appointment audit trace metadata",
    file: "products/phr/src/main/java/com/ghatana/phr/kernel/service/AppointmentService.java",
    required: [
      "PhrTraceContext.metadata(",
      "correlationId",
      "audit(\"APPOINTMENT_CREATE\"",
    ],
  },
  {
    name: "PHR consent boundary access gate audit contract",
    file: "products/phr/src/main/java/com/ghatana/phr/kernel/policy/PhrConsentBoundaryAccessGate.java",
    required: [
      "auditRecorder.record(",
      "matched_rule",
      "emergencyOverride",
      "MISSING_REQUIRED_FEATURE",
      "BOUNDARY_DENY",
    ],
  },
  {
    name: "DMOS bridge audit and trace evidence",
    file: "products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java",
    required: [
      "shouldRecordAuditWithContextAttributes",
      "workspaceId",
      "correlationId",
      "tenantId",
      "shouldNotifyUser",
      "shouldDelegateFeatureFlagEvaluation",
    ],
  },
];

const violations = [];

const forbiddenProductStackFiles = [
  "products/flashit/monitoring/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/datasources/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/dashboards/dashboards.yml",
];

function validateFlowManifest() {
  const manifestPath = path.join(repoRoot, flowManifestFile);
  if (!existsSync(manifestPath)) {
    violations.push(`Missing observability flow manifest ${flowManifestFile}`);
    return;
  }

  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const requiredFacets = manifest.requiredFacets ?? [];
  const flows = manifest.flows ?? [];
  const requiredProducts = ["phr", "finance", "digital-marketing", "flashit"];
  const coveredProducts = new Set();
  let bridgeFlowCount = 0;

  if (!Array.isArray(requiredFacets) || requiredFacets.length === 0) {
    violations.push(`${flowManifestFile}: requiredFacets must list observability facets`);
  }

  if (!Array.isArray(flows) || flows.length === 0) {
    violations.push(`${flowManifestFile}: flows must be a non-empty array`);
    return;
  }

  for (const flow of flows) {
    const flowName = `${flow.product ?? "unknown"}:${flow.flow ?? "unknown"}`;
    if (!flow.product || !flow.flow || !flow.kind) {
      violations.push(`${flowManifestFile}: ${flowName} must declare product, flow, and kind`);
      continue;
    }

    coveredProducts.add(flow.product);
    if (flow.kind === "bridge") {
      bridgeFlowCount += 1;
    }

    const facets = new Set(flow.facets ?? []);
    const missingFacets = requiredFacets.filter((facet) => !facets.has(facet));
    if (missingFacets.length > 0) {
      violations.push(`${flowManifestFile}: ${flowName} is missing facets ${missingFacets.join(", ")}`);
    }

    if (!Array.isArray(flow.evidence) || flow.evidence.length === 0) {
      violations.push(`${flowManifestFile}: ${flowName} must declare evidence files`);
      continue;
    }

    for (const evidence of flow.evidence) {
      const evidenceFile = evidence.file;
      const evidencePath = path.join(repoRoot, evidenceFile ?? "");
      if (!evidenceFile || !existsSync(evidencePath)) {
        violations.push(`${flowManifestFile}: ${flowName} references missing evidence file ${evidenceFile}`);
        continue;
      }

      const content = readFileSync(evidencePath, "utf8");
      const missingTokens = (evidence.tokens ?? []).filter((token) => !content.includes(token));
      if (missingTokens.length > 0) {
        violations.push(
          `${flowManifestFile}: ${flowName} evidence ${evidenceFile} missing tokens ${missingTokens.join(", ")}`,
        );
      }
    }
  }

  for (const product of requiredProducts) {
    if (!coveredProducts.has(product)) {
      violations.push(`${flowManifestFile}: missing observability flow coverage for ${product}`);
    }
  }

  if (bridgeFlowCount === 0) {
    violations.push(`${flowManifestFile}: at least one bridge flow must be covered`);
  }
}

for (const contract of contracts) {
  const filePath = path.join(repoRoot, contract.file);
  if (!existsSync(filePath)) {
    violations.push(`${contract.name}: missing file ${contract.file}`);
    continue;
  }

  const content = readFileSync(filePath, "utf8");
  const missing = contract.required.filter((token) => !content.includes(token));
  if (missing.length > 0) {
    violations.push(
      `${contract.name}: missing observability evidence ${missing.join(", ")} in ${contract.file}`,
    );
  }
}

for (const file of forbiddenProductStackFiles) {
  if (existsSync(path.join(repoRoot, file))) {
    violations.push(
      `FlashIt observability must not own stack config file ${file}; keep only product dashboards and alert overlays under products/flashit/monitoring`,
    );
  }
}

validateFlowManifest();

if (violations.length > 0) {
  console.error(`❌ Observability conformance check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ Observability conformance check passed.");
