#!/usr/bin/env node
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();

const checks = [
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/credentials/service.ts",
    patterns: [
      "evaluateEvidenceEligibility",
      "issueFromEvidence",
      "verifyCredential",
      "revokeCredential",
      "reissueCredential",
      "masteryProbability",
      "CREDENTIAL_EVIDENCE_INSUFFICIENT",
      "A micro-viva requirement is unresolved",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/credentials/routes.ts",
    patterns: [
      "/credentials/issue-from-evidence",
      "/credentials/:credentialId/verify",
      "/credentials/:credentialId/revoke",
      "/credentials/:credentialId/reissue",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/credentials/__tests__/service.test.ts",
    patterns: [
      "requires mastered claims and valid assessment evidence",
      "blocks eligibility when a viva requirement is unresolved",
      "issues credentials with mastery evidence provenance",
      "verifies, revokes, and reissues credential records",
    ],
  },
  {
    file: "products/tutorputor/services/tutorputor-platform/src/modules/credentials/__tests__/routes.test.ts",
    patterns: [
      "issues credentials from mastery evidence",
      "verifies credential URLs",
      "revokes credentials",
      "reissues credentials from existing evidence",
    ],
  },
  {
    file: "products/tutorputor/.gitea/workflows/tutorputor-ci.yml",
    patterns: ["Validate credential evidence issuance"],
  },
];

const failures = [];

for (const check of checks) {
  const text = readFileSync(join(root, check.file), "utf8");
  for (const pattern of check.patterns) {
    if (!text.includes(pattern)) {
      failures.push(`${check.file} is missing required marker: ${pattern}`);
    }
  }
}

const service = readFileSync(
  join(root, "products/tutorputor/services/tutorputor-platform/src/modules/credentials/service.ts"),
  "utf8",
);

const issueStart = service.indexOf("async issueFromEvidence");
const issueEnd = service.indexOf("async verifyCredential");
const issueFromEvidenceBody = service.slice(issueStart, issueEnd);

if (issueFromEvidenceBody.includes("progressPercent")) {
  failures.push(
    "Credential issueFromEvidence must not use module progressPercent as eligibility evidence",
  );
}

if (failures.length > 0) {
  console.error("Credential evidence validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Credential evidence issuance validation passed.");
