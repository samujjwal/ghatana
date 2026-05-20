#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify artifact round-trip pipeline integrity.
 *
 * Checks:
 *   1. @ghatana/artifact-compiler-ts has the round-trip test file.
 *   2. decompileTsx + compileReact form a two-way pipeline.
 *   3. fidelityGate and FIDELITY_THRESHOLDS are exported.
 *   4. ResidualIslandReport is exported from artifact-contracts.
 *   5. Typed extracted-structure contracts are exported.
 *   6. Import preservation, semantic diff, and repository scan tests exist.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for artifact source → model → source round-trip stability
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

console.log("\n=== check:artifact-roundtrip ===\n");

// ── Round-trip test files ─────────────────────────────────────────────────────
console.log("1. Round-trip test files");

const roundtripTest = "platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts";
check(
  existsSync(resolve(root, roundtripTest)),
  `exists: ${roundtripTest}`,
  `Missing round-trip test: ${roundtripTest}`
);

const roundtripSrc = readSource(roundtripTest);
check(
  roundtripSrc.includes("decompileTsx") && roundtripSrc.includes("compileReact"),
  "roundtrip.test.ts exercises both decompileTsx and compileReact",
  "roundtrip.test.ts must import and call both decompileTsx and compileReact"
);
check(
  roundtripSrc.includes("two-pass") || roundtripSrc.includes("model → source → model") || roundtripSrc.includes("second pass"),
  "roundtrip.test.ts covers a two-pass (source → model → source → model) scenario",
  "roundtrip.test.ts must include a two-pass round-trip scenario"
);
check(
  roundtripSrc.includes("fidelityGate") || roundtripSrc.includes("fidelity"),
  "roundtrip.test.ts validates fidelity",
  "roundtrip.test.ts must validate fidelity after round-trip"
);

// ── Decompiler API ────────────────────────────────────────────────────────────
console.log("\n2. Decompiler API (artifact-compiler-ts)");

const decompileSrc = readSource("platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts");
check(decompileSrc.includes("export"), "decompile/tsx.ts has exports", "decompile/tsx.ts has no exports");
check(
  decompileSrc.includes("decompileTsx"),
  "decompile/tsx.ts exports decompileTsx",
  "decompile/tsx.ts must export decompileTsx"
);

// ── Compiler API ──────────────────────────────────────────────────────────────
console.log("\n3. Compiler API (artifact-compiler-ts)");

const compileSrc = readSource("platform/typescript/artifact-compiler-ts/src/compile/react.ts");
check(compileSrc.includes("export"), "compile/react.ts has exports", "compile/react.ts has no exports");
check(
  compileSrc.includes("compileReact"),
  "compile/react.ts exports compileReact",
  "compile/react.ts must export compileReact"
);

// ── Fidelity scoring ──────────────────────────────────────────────────────────
console.log("\n4. Fidelity scoring");

const fidelitySrc = readSource("platform/typescript/artifact-compiler-ts/src/fidelity/scorer.ts");
check(
  fidelitySrc.includes("fidelityGate"),
  "fidelity/scorer.ts exports fidelityGate",
  "fidelity/scorer.ts must export fidelityGate"
);
check(
  fidelitySrc.includes("FIDELITY_THRESHOLDS"),
  "fidelity/scorer.ts exports FIDELITY_THRESHOLDS",
  "fidelity/scorer.ts must export FIDELITY_THRESHOLDS"
);
check(
  fidelitySrc.includes("CLEAN") && fidelitySrc.includes("BLOCKED"),
  "FIDELITY_THRESHOLDS declares CLEAN and BLOCKED levels",
  "FIDELITY_THRESHOLDS must declare at least CLEAN and BLOCKED levels"
);

// ── Residual islands ─────────────────────────────────────────────────────────
console.log("\n5. Residual island detection");

const residualSrc = readSource("platform/typescript/artifact-compiler-ts/src/residual/residual-islands.ts");
check(
  residualSrc.includes("detectResidualIslands"),
  "residual-islands.ts exports detectResidualIslands",
  "residual-islands.ts must export detectResidualIslands"
);

// ── Shared contracts ──────────────────────────────────────────────────────────
console.log("\n6. Shared contracts (artifact-contracts)");

const contractsIndex = readSource("platform/typescript/artifact-contracts/src/index.ts");
check(
  contractsIndex.includes("ResidualIslandReport"),
  "artifact-contracts exports ResidualIslandReport",
  "artifact-contracts index.ts must export ResidualIslandReport"
);
check(
  contractsIndex.includes("FidelityReport"),
  "artifact-contracts exports FidelityReport",
  "artifact-contracts index.ts must export FidelityReport"
);
check(
  contractsIndex.includes("EvidencePack"),
  "artifact-contracts exports EvidencePack",
  "artifact-contracts index.ts must export EvidencePack"
);
check(
  contractsIndex.includes("CompileResult") && contractsIndex.includes("DecompileResult"),
  "artifact-contracts exports CompileResult and DecompileResult",
  "artifact-contracts index.ts must export CompileResult and DecompileResult"
);

const structureSrc = readSource("platform/typescript/artifact-contracts/src/structure.ts");
check(
  structureSrc.includes("JsxTreeNodeSchema") &&
    structureSrc.includes("DetectedRouteSchema") &&
    structureSrc.includes("ComponentUsageRecordSchema") &&
    (
      structureSrc.includes("ProtectedRegionSchema") ||
      structureSrc.includes("ExtractedProtectedRegionSchema")
    ),
  "artifact-contracts declares typed JSX, route, component usage, and protected-region schemas",
  "artifact-contracts must declare typed schemas for extracted JSX/routes/usages/protected regions"
);
check(
  structureSrc.includes("SourceImportRecordSchema") &&
    readSource("platform/typescript/artifact-contracts/src/scan.ts").includes("RoundTripDiffReportSchema"),
  "artifact-contracts declares source import and round-trip diff schemas",
  "artifact-contracts must declare source import and round-trip diff schemas"
);
check(
  contractsIndex.includes("JsxTreeNode") &&
    contractsIndex.includes("DetectedRoute") &&
    contractsIndex.includes("ComponentUsageRecord") &&
    contractsIndex.includes("ProtectedRegion") &&
    contractsIndex.includes("RoundTripDiffReport"),
  "artifact-contracts exports typed extracted-structure and diff contracts",
  "artifact-contracts index.ts must export typed extracted-structure and diff contracts"
);

// ── Semantic round-trip and import preservation ──────────────────────────────
console.log("\n7. Semantic round-trip and import preservation");

const protectedRegionTest = readSource("platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts");
check(
  protectedRegionTest.includes("preserves original static import") ||
    protectedRegionTest.includes("preserves static imports") ||
    protectedRegionTest.includes("import preservation"),
  "react-protected-regions.test.ts covers import preservation",
  "react-protected-regions.test.ts must assert import preservation"
);

const roundtripDiffTest = "platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip-diff.test.ts";
check(
  existsSync(resolve(root, roundtripDiffTest)),
  `exists: ${roundtripDiffTest}`,
  `Missing semantic round-trip diff test: ${roundtripDiffTest}`
);

const roundtripDiffSrc = readSource("platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts");
check(
  roundtripDiffSrc.includes("buildRoundTripDiffReport") &&
    roundtripDiffSrc.includes("semanticallyEquivalent"),
  "roundtrip-diff.ts builds semantic diff reports",
  "roundtrip-diff.ts must expose buildRoundTripDiffReport with semantic equivalence"
);

// ── Repository-scale scan facade ────────────────────────────────────────────
console.log("\n8. Repository-scale scan facade");

const repositoryScanFile = "platform/typescript/artifact-compiler-ts/src/scan/repository-scan.ts";
check(
  existsSync(resolve(root, repositoryScanFile)),
  `exists: ${repositoryScanFile}`,
  `Missing repository scan facade: ${repositoryScanFile}`
);

const repositoryScanSrc = readSource(repositoryScanFile);
check(
  repositoryScanSrc.includes("scanRepositorySources") &&
    repositoryScanSrc.includes("ScanResult") &&
    repositoryScanSrc.includes("FileScanResult"),
  "repository-scan.ts builds contract-level scan results",
  "repository-scan.ts must build ScanResult/FileScanResult contracts"
);
check(
  repositoryScanSrc.includes("detectResidualIslands") &&
    repositoryScanSrc.includes("decompileTsx"),
  "repository-scan.ts connects decompile and residual detection",
  "repository-scan.ts must connect decompileTsx and residual detection"
);

const repositoryScanTest = "platform/typescript/artifact-compiler-ts/src/__tests__/repository-scan.test.ts";
check(
  existsSync(resolve(root, repositoryScanTest)),
  `exists: ${repositoryScanTest}`,
  `Missing repository scan test: ${repositoryScanTest}`
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
