#!/usr/bin/env node

import { existsSync, readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

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

if (violations.length > 0) {
  console.error(`❌ Observability conformance check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ Observability conformance check passed.");
