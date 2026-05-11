#!/usr/bin/env node

import { existsSync, readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const contracts = [
  {
    name: "DMOS operation context",
    file: "products/digital-marketing/dm-core-contracts/src/main/java/com/ghatana/digitalmarketing/contracts/DmOperationContext.java",
    required: [
      "tenantId",
      "principalId",
      "correlationId",
      "idempotencyKey",
      "toBridgeContext()",
      "toDataAccessMetadata(",
      "\"auditClassification\"",
      "\"dataOwnerScope\"",
    ],
  },
  {
    name: "PHR shared mutation metadata contract",
    file: "products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java",
    required: [
      "enrichMutationMetadata(",
      "\"tenantId\"",
      "\"principalId\"",
      "\"correlationId\"",
      "\"idempotencyKey\"",
      "\"auditClassification\"",
      "\"dataOwnerScope\"",
      "PhrTraceContext.metadata(",
    ],
  },
  {
    name: "Finance shared mutation metadata contract",
    file: "products/finance/src/main/java/com/ghatana/finance/kernel/service/FinanceServiceBase.java",
    required: [
      "enrichMutationMetadata(",
      "\"tenant_id\"",
      "\"principal_id\"",
      "\"correlation_id\"",
      "\"idempotency_key\"",
      "\"audit_classification\"",
      "\"data_owner_scope\"",
      "FinanceTraceContext.metadata(",
    ],
  },
  {
    name: "PHR appointment write flow",
    file: "products/phr/src/main/java/com/ghatana/phr/kernel/service/AppointmentService.java",
    required: [
      "DataWriteRequest",
      "PhrTraceContext.metadata(",
      "audit(\"APPOINTMENT_CREATE\"",
      "\"patientId\"",
      "\"providerId\"",
    ],
  },
  {
    name: "PHR patient record flow uses shared base writes",
    file: "products/phr/src/main/java/com/ghatana/phr/kernel/service/PatientRecordService.java",
    required: [
      "createRecord(",
      "updateRecord(",
    ],
  },
  {
    name: "Finance transaction mutation flow",
    file: "products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java",
    required: [
      "processedTransactions",
      "\"tenant_id\"",
      "FinanceTraceContext.metadata(",
      "TransactionRateLimitExceededException",
    ],
  },
  {
    name: "Finance trace context contract",
    file: "products/finance/src/main/java/com/ghatana/finance/service/FinanceTraceContext.java",
    required: [
      "\"correlation_id\"",
      "\"trace_operation\"",
    ],
  },
  {
    name: "Finance order management flow uses shared base writes",
    file: "products/finance/src/main/java/com/ghatana/finance/kernel/service/OrderManagementService.java",
    required: [
      "createRecord(",
      "updateRecord(",
    ],
  },
  {
    name: "FlashIt shared data-access context",
    file: "products/flashit/backend/gateway/src/lib/data-access-context.ts",
    required: [
      "FlashItDataAccessContext",
      "tenantId",
      "principalId",
      "correlationId",
      "idempotencyKey",
      "auditClassification",
      "dataOwnerScope",
      "requireIdempotencyKey",
      "FlashItDataAccessContextError",
      "x-tenant-id",
      "x-correlation-id",
      "x-idempotency-key",
      "FlashItTenantResolver",
      "resolvePersonalTenant",
    ],
  },
  {
    name: "FlashIt moment write route",
    file: "products/flashit/backend/gateway/src/routes/moments.ts",
    required: [
      "buildFlashItDataAccessContext",
      "getUserIdFromRequest",
      "sphereAccess.findFirst",
      "auditClassification: \"PERSONAL_MEMORY_WRITE\"",
      "auditClassification: \"PERSONAL_MEMORY_READ\"",
      "auditClassification: \"SEARCH_ACTIVITY_READ\"",
      "dataOwnerScope:",
      "requireIdempotencyKey: true",
      "dataAccessContext",
      "FlashItDataAccessContextError",
      "Missing data access context",
      "prisma.auditEvent.create(",
      "onRequest: [app.authenticate]",
      "logger.logBusinessEvent(",
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
      `${contract.name}: missing required contract evidence ${missing.join(", ")} in ${contract.file}`,
    );
  }
}

if (violations.length > 0) {
  console.error(`❌ Data-access contract check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ Data-access contract check passed.");
