#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify artifact fidelity infrastructure.
 *
 * Checks:
 *   1. @ghatana/artifact-contracts exports FidelityReport, LossPoint, ResidualIslandReport.
 *   2. Fidelity scorer in artifact-compiler-ts exports FIDELITY_THRESHOLDS and fidelityGate.
 *   3. Studio has a FidelityReportPage that uses FidelityReport from contracts.
 *   4. Fidelity tests exist in artifact-contracts and artifact-compiler-ts.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for artifact fidelity scoring and reporting infrastructure
 * @doc.layer platform
 */

import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, "..");

let failures = 0;

function fail(msg) { console.error(`  ✗ ${msg}`); failures++; }
function ok(msg) { console.log(`  ✓ ${msg}`); }
function check(condition, passMsg, failMsg) { condition ? ok(passMsg) : fail(failMsg); }

function readSource(rel) {
  try { return readFileSync(resolve(root, rel), "utf8"); } catch { return ""; }
}

console.log("\n=== check:artifact-fidelity ===\n");

// ── artifact-contracts fidelity types ────────────────────────────────────────
console.log("1. artifact-contracts fidelity types");

const fidelitySrc = readSource("platform/typescript/artifact-contracts/src/fidelity.ts");
check(
  fidelitySrc.includes("FidelityReport"),
  "fidelity.ts declares FidelityReport",
  "fidelity.ts must declare FidelityReport"
);
check(
  fidelitySrc.includes("LossPoint"),
  "fidelity.ts declares LossPoint",
  "fidelity.ts must declare LossPoint"
);
check(
  fidelitySrc.includes("ResidualIslandReport"),
  "fidelity.ts declares ResidualIslandReport",
  "fidelity.ts must declare ResidualIslandReport"
);
check(
  fidelitySrc.includes("score:") || fidelitySrc.includes("score "),
  "FidelityReport has a score field",
  "FidelityReport must have a score field (0-1)"
);
check(
  fidelitySrc.includes("lossPoints") || fidelitySrc.includes("loss_points"),
  "FidelityReport has a lossPoints field",
  "FidelityReport must have a lossPoints field"
);

// ── LossPoint severity levels ─────────────────────────────────────────────────
console.log("\n2. LossPoint severity levels");

check(
  fidelitySrc.includes('"critical"') || fidelitySrc.includes("'critical'") || fidelitySrc.includes("critical"),
  "LossPoint has critical severity level",
  "LossPoint must support critical severity"
);
check(
  fidelitySrc.includes('"warning"') || fidelitySrc.includes("'warning'") || fidelitySrc.includes("warning"),
  "LossPoint has warning severity level",
  "LossPoint must support warning severity"
);
check(
  fidelitySrc.includes('"info"') || fidelitySrc.includes("'info'") || fidelitySrc.includes("info"),
  "LossPoint has info severity level",
  "LossPoint must support info severity"
);

// ── computeFidelityReport ─────────────────────────────────────────────────────
console.log("\n3. computeFidelityReport primitive");

check(
  fidelitySrc.includes("computeFidelityReport"),
  "fidelity.ts exports computeFidelityReport",
  "fidelity.ts must export computeFidelityReport"
);
check(
  fidelitySrc.includes("createPerfectFidelityReport"),
  "fidelity.ts exports createPerfectFidelityReport",
  "fidelity.ts must export createPerfectFidelityReport"
);

// ── artifact-compiler-ts scorer ───────────────────────────────────────────────
console.log("\n4. artifact-compiler-ts fidelity scorer");

const scorerSrc = readSource("platform/typescript/artifact-compiler-ts/src/fidelity/scorer.ts");
check(
  scorerSrc.includes("FIDELITY_THRESHOLDS"),
  "scorer.ts exports FIDELITY_THRESHOLDS",
  "scorer.ts must export FIDELITY_THRESHOLDS"
);
check(
  scorerSrc.includes("fidelityGate"),
  "scorer.ts exports fidelityGate",
  "scorer.ts must export fidelityGate"
);
check(
  scorerSrc.includes("aggregateFidelityReports"),
  "scorer.ts exports aggregateFidelityReports",
  "scorer.ts must export aggregateFidelityReports"
);
check(
  scorerSrc.includes("CLEAN") && scorerSrc.includes("BLOCKED"),
  "FIDELITY_THRESHOLDS defines CLEAN and BLOCKED",
  "FIDELITY_THRESHOLDS must define CLEAN and BLOCKED levels"
);

// ── Studio FidelityReportPage ─────────────────────────────────────────────────
console.log("\n5. Studio FidelityReportPage");

const fidelityPagePath = "platform/typescript/ghatana-studio/src/routes/FidelityReportPage.tsx";
check(
  existsSync(resolve(root, fidelityPagePath)),
  `exists: ${fidelityPagePath}`,
  `Missing: ${fidelityPagePath}`
);

const fidelityPageSrc = readSource(fidelityPagePath);
check(
  fidelityPageSrc.includes("FidelityReport"),
  "FidelityReportPage imports FidelityReport from contracts",
  "FidelityReportPage must use FidelityReport type"
);
check(
  fidelityPageSrc.includes("LossPoint"),
  "FidelityReportPage renders LossPoint details",
  "FidelityReportPage must render individual LossPoint items"
);
check(
  fidelityPageSrc.includes("score"),
  "FidelityReportPage displays fidelity score",
  "FidelityReportPage must display the fidelity score"
);

// ── Fidelity tests ────────────────────────────────────────────────────────────
console.log("\n6. Fidelity test coverage");

const contractsFidelityTest = "platform/typescript/artifact-contracts/src/__tests__/fidelity.test.ts";
check(
  existsSync(resolve(root, contractsFidelityTest)),
  `exists: ${contractsFidelityTest}`,
  `Missing: ${contractsFidelityTest}`
);

const fidelityTestSrc = readSource(contractsFidelityTest);
check(
  fidelityTestSrc.includes("computeFidelityReport"),
  "fidelity test exercises computeFidelityReport",
  "fidelity test must call computeFidelityReport"
);

const scorerTestPath = "platform/typescript/artifact-compiler-ts/src/__tests__/scorer.test.ts";
const scorerTestAltPath = "platform/typescript/artifact-compiler-ts/src/__tests__/fidelity.test.ts";
const scorerTestExists =
  existsSync(resolve(root, scorerTestPath)) ||
  existsSync(resolve(root, scorerTestAltPath));
check(
  scorerTestExists,
  "fidelity scorer test exists",
  `Missing scorer test: ${scorerTestPath} or ${scorerTestAltPath}`
);

// ── Summary ───────────────────────────────────────────────────────────────────
console.log();
if (failures > 0) {
  console.error(`✗ ${failures} check(s) failed.`);
  process.exit(1);
} else {
  console.log("✅ All checks passed.");
  process.exit(0);
}
