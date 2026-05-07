#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

const requiredChecks = [
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/integration/lti/evidence-grade.ts",
    patterns: [
      "calculateEvidenceBackedLtiGrade",
      "assessmentAttempt",
      "scorePercent",
      "learnerMastery",
      "masteryProbability",
      "LTI_EVIDENCE_NOT_READY",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/integration/lti/routes.ts",
    patterns: [
      "assessmentAttemptId",
      "moduleId",
      "calculateEvidenceBackedLtiGrade",
      "Provide score/maxScore for legacy passback or assessmentAttemptId/moduleId",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/integration/lti/routes.test.ts",
    patterns: [
      "calculates LTI grade passback from assessment evidence",
      "calculates LTI grade passback from module claim mastery evidence",
      "LTI_EVIDENCE_NOT_READY",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/lti/lti-full-service.ts",
    patterns: [
      "calculateEvidenceBackedLtiGrade",
      "Enrollments identify launched learners; scores are calculated only from evidence.",
    ],
  },
  {
    file: "products/tutorputor/scripts/verify-lti-grade-passback.sh",
    patterns: [
      "TUTORPUTOR_TEST_ASSESSMENT_ATTEMPT_ID",
      "TUTORPUTOR_TEST_MODULE_ID",
      "evidence-backed grade passback verification",
    ],
  },
  {
    file: "products/tutorputor/.gitea/workflows/tutorputor-ci.yml",
    patterns: ["Validate LTI evidence-backed grade passback"],
  },
];

const failures = [];

for (const check of requiredChecks) {
  const text = readFileSync(join(root, check.file), "utf8");
  for (const pattern of check.patterns) {
    if (!text.includes(pattern)) {
      failures.push(`${check.file} is missing required marker: ${pattern}`);
    }
  }
}

const helper = readFileSync(
  join(
    root,
    "products/tutorputor/services/tutorputor-platform/src/modules/integration/lti/evidence-grade.ts",
  ),
  "utf8",
);

if (helper.includes("progressPercent")) {
  failures.push(
    "evidence-grade.ts must not use enrollment progressPercent as an LTI grade source",
  );
}

const fullLtiService = readFileSync(
  join(
    root,
    "products/tutorputor/services/tutorputor-platform/src/modules/lti/lti-full-service.ts",
  ),
  "utf8",
);

if (fullLtiService.includes("scoreGiven: enrollment.progressPercent")) {
  failures.push(
    "lti-full-service.ts must not pass enrollment progressPercent as an LMS grade",
  );
}

if (failures.length > 0) {
  console.error("LTI evidence-backed passback validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("LTI evidence-backed passback validation passed.");
