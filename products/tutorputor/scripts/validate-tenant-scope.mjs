#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const productRoot = path.resolve("products/tutorputor");
const schemaPath = path.join(productRoot, "libs/tutorputor-core/prisma/schema.prisma");
const schema = fs.readFileSync(schemaPath, "utf8");

const requiredModels = {
  User: {
    fields: ["tenantId"],
    indexes: ["@@unique([tenantId, email])"],
  },
  Module: {
    fields: ["tenantId"],
    indexes: ["@@unique([tenantId, slug])"],
  },
  Enrollment: {
    fields: ["tenantId", "userId"],
    indexes: ["@@unique([tenantId, userId, moduleId])", "@@index([tenantId, userId])"],
  },
  Classroom: {
    fields: ["tenantId"],
    indexes: ["@@index([tenantId, teacherId])"],
  },
  Assessment: {
    fields: ["tenantId"],
    indexes: ["@@index([tenantId, moduleId])"],
  },
  AssessmentAttempt: {
    fields: ["tenantId", "userId"],
    indexes: ["@@index([tenantId, userId])", "@@index([tenantId, assessmentId])"],
  },
  LearningEvent: {
    fields: ["tenantId", "userId"],
    indexes: ["@@index([tenantId, userId])", "@@index([tenantId, eventType])"],
  },
  AIAuditLog: {
    fields: ["tenantId", "userId"],
    indexes: ["@@index([tenantId, userId, createdAt])"],
  },
  UserConsent: {
    fields: ["tenantId", "userId"],
    indexes: ["@@unique([tenantId, userId, category])", "@@index([tenantId, userId])"],
  },
  Badge: {
    fields: ["tenantId"],
    indexes: ["@@index([tenantId])"],
  },
  BadgeEarned: {
    fields: ["tenantId", "userId"],
    indexes: ["@@unique([tenantId, userId, badgeId])", "@@index([tenantId, userId])"],
  },
  LTIPlatform: {
    fields: ["tenantId"],
    indexes: ["@@unique([tenantId, issuer])"],
  },
  MarketplaceListing: {
    fields: ["tenantId", "creatorId"],
    indexes: ["@@index([tenantId, creatorId])", "@@index([tenantId, moduleId])"],
  },
  AuditLog: {
    fields: ["tenantId", "actorId"],
    indexes: ["@@index([tenantId, actorId])"],
  },
};

function getModelBlock(modelName) {
  const match = schema.match(new RegExp(`model\\s+${modelName}\\s+{([\\s\\S]*?)\\n}`));
  return match?.[1] ?? null;
}

const failures = [];

for (const [modelName, requirements] of Object.entries(requiredModels)) {
  const block = getModelBlock(modelName);
  if (!block) {
    failures.push(`Required scoped model is missing: ${modelName}`);
    continue;
  }

  for (const field of requirements.fields) {
    if (!new RegExp(`^\\s*${field}\\s+String\\b`, "m").test(block)) {
      failures.push(`${modelName} must include String field ${field}`);
    }
  }

  for (const index of requirements.indexes) {
    if (!block.includes(index)) {
      failures.push(`${modelName} must include scope index ${index}`);
    }
  }
}

const teacherAnalyticsPath = path.join(
  productRoot,
  "services/tutorputor-platform/src/modules/analytics/TeacherAnalyticsService.ts",
);
const teacherAnalytics = fs.readFileSync(teacherAnalyticsPath, "utf8");
if (!teacherAnalytics.includes("where: { id: classroomId, tenantId }")) {
  failures.push(
    "TeacherAnalyticsService must verify classroom ownership with id + tenantId before loading members",
  );
}

if (failures.length > 0) {
  console.error("Tenant scope validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log(
  `Tenant scope validation passed (${Object.keys(requiredModels).length} regulated models).`,
);
