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
    file: "products/finance/src/main/java/com/ghatana/finance/service/FinanceTraceContext.java",
    required: [
      "\"correlation_id\"",
      "\"trace_operation\"",
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
