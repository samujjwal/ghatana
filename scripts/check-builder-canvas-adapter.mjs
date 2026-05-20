#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify canonical BuilderCanvas adapter and prevent duplicates.
 *
 * Checks:
 *   1. Only one canonical builder/canvas adapter exists (no duplicate ownership).
 *   2. BuilderCanvasProjectionAdapter.ts exists in ghatana-studio adapters.
 *   3. It exports builderToCanvas, canvasToBuilder, filterCanvasSelectionToNodeIds, reconcileCanvasGeometryDeltas.
 *   4. No unsafe `as NodeId[]` or `as unknown as` casts exist at canvas adapter boundaries.
 *   5. BuilderNodeData and BuilderEdgeData are explicitly typed.
 *   6. Tests exist for the bidirectional projection.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for canonical, type-safe BuilderDocument ↔ canvas node/edge projection
 * @doc.layer platform
 */

import { readFileSync, existsSync, readdirSync } from "node:fs";
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

console.log("\n=== check:builder-canvas-adapter ===\n");

// ── Root script target alignment ─────────────────────────────────────────────
console.log("0. Root script target alignment");

const rootPackage = JSON.parse(readSource("package.json") || "{}");
const checkScript = rootPackage?.scripts?.["check:builder-canvas-adapter"];
const expectedTestTarget = "src/adapters/__tests__/BuilderCanvasProjectionAdapter.test.ts";
const legacyTestTarget = "src/adapters/__tests__/BuilderCanvasAdapter.test.ts";

check(
  typeof checkScript === "string" && checkScript.includes(expectedTestTarget),
  `Root script references canonical adapter test target: ${expectedTestTarget}`,
  `Root script must reference canonical adapter test target: ${expectedTestTarget}`
);

check(
  typeof checkScript === "string" && !checkScript.includes(legacyTestTarget),
  "Root script does not reference legacy adapter test target",
  `Root script still references legacy adapter test target: ${legacyTestTarget}`
);

// ── Enforce single canonical adapter (no duplicate ownership) ─────────────────
console.log("1. Single canonical adapter enforcement");

const adaptersDir = "platform/typescript/ghatana-studio/src/adapters";
const adapterFiles = readdirSync(resolve(root, adaptersDir))
  .filter(f => f.endsWith('.ts') && f.toLowerCase().includes('canvas') && f.toLowerCase().includes('adapter'));

check(
  adapterFiles.length === 1,
  `Exactly one canvas adapter exists: ${adapterFiles.join(', ')}`,
  `Duplicate canvas adapters found: ${adapterFiles.join(', ')} — consolidate to prevent ownership conflicts`
);

check(
  adapterFiles.includes("BuilderCanvasProjectionAdapter.ts"),
  "Canonical adapter is BuilderCanvasProjectionAdapter.ts",
  "Canonical adapter must be BuilderCanvasProjectionAdapter.ts"
);

// ── Adapter file existence ────────────────────────────────────────────────────
console.log("\n2. BuilderCanvasProjectionAdapter.ts");

const adapterPath = "platform/typescript/ghatana-studio/src/adapters/BuilderCanvasProjectionAdapter.ts";
check(
  existsSync(resolve(root, adapterPath)),
  `exists: ${adapterPath}`,
  `Missing adapter: ${adapterPath}`
);

const adapterSrc = readSource(adapterPath);

// ── Required exports ──────────────────────────────────────────────────────────
console.log("\n3. Required exports");

check(
  adapterSrc.includes("builderToCanvas"),
  "exports builderToCanvas",
  "Missing export: builderToCanvas"
);
check(
  adapterSrc.includes("canvasToBuilder"),
  "exports canvasToBuilder",
  "Missing export: canvasToBuilder"
);
check(
  adapterSrc.includes("filterCanvasSelectionToNodeIds"),
  "exports filterCanvasSelectionToNodeIds",
  "Missing export: filterCanvasSelectionToNodeIds"
);
check(
  adapterSrc.includes("reconcileCanvasGeometryDeltas"),
  "exports reconcileCanvasGeometryDeltas",
  "Missing export: reconcileCanvasGeometryDeltas"
);

// ── Type safety ───────────────────────────────────────────────────────────────
console.log("\n4. Type safety");

// Ensure no unsafe NodeId[] boundary casts
// We allow `as NodeId[]` only after explicit validation against document knownIds
const unsafeNodeIdCastPattern = /as\s+NodeId\[\]/g;
const unsafeNodeIdMatches = [...(adapterSrc.match(unsafeNodeIdCastPattern) ?? [])];

// Check if the cast is in a context where it's validated (after knownIds check)
// Safe pattern: filterCanvasSelectionToNodeIds validates against knownIds before casting
// We exclude casts that appear in lines containing the validation pattern
const lines = adapterSrc.split('\n');
let unsafeCount = 0;
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  if (line.match(unsafeNodeIdCastPattern)) {
    // Check if this line or the previous 10 lines contain validation logic
    const contextLines = lines.slice(Math.max(0, i - 10), i + 1).join('\n');
    const isValidated = contextLines.includes('knownIds') && contextLines.includes('has');
    if (!isValidated) {
      unsafeCount++;
    }
  }
}

check(
  unsafeCount === 0,
  "No unsafe `as NodeId[]` casts found in adapter",
  `Found ${unsafeCount} unsafe \`as NodeId[]\` cast(s) — use typed extraction instead`
);

// Ensure no unsafe `as unknown as` casts (the anti-pattern from the audit)
const unsafeUnknownCast = /as\s+unknown\s+as/g;
const unsafeUnknownMatches = [...(adapterSrc.match(unsafeUnknownCast) ?? [])];
check(
  unsafeUnknownMatches.length === 0,
  "No unsafe `as unknown as` casts found in adapter",
  `Found ${unsafeUnknownMatches.length} unsafe \`as unknown as\` cast(s) — use type guards instead`
);

check(
  adapterSrc.includes("BuilderNodeData") && adapterSrc.includes("BuilderEdgeData"),
  "BuilderNodeData and BuilderEdgeData are explicitly typed",
  "Missing explicit BuilderNodeData/BuilderEdgeData type definitions"
);

// ── Tests ─────────────────────────────────────────────────────────────────────
console.log("\n5. Adapter tests");

const canvasPageTestPath = "platform/typescript/ghatana-studio/src/routes/__tests__/CanvasPage.test.tsx";
check(
  existsSync(resolve(root, canvasPageTestPath)),
  `CanvasPage tests exist: ${canvasPageTestPath}`,
  `Missing CanvasPage tests: ${canvasPageTestPath}`
);

const canvasPageTestSrc = readSource(canvasPageTestPath);
check(
  canvasPageTestSrc.includes("builderToCanvas"),
  "CanvasPage tests exercise builderToCanvas",
  "CanvasPage tests must verify builderToCanvas projection"
);
check(
  canvasPageTestSrc.includes("canvasToBuilder"),
  "CanvasPage tests exercise canvasToBuilder write-back",
  "CanvasPage tests must verify canvasToBuilder write-back"
);

// ── No legacy unsafe imports ──────────────────────────────────────────────────
console.log("\n6. No legacy pattern imports in studio routes");

const routeFiles = [
  "platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx",
  "platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx",
  "platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx",
];

for (const routeFile of routeFiles) {
  const src = readSource(routeFile);
  const hasUnsafeNodeIdCast = /as\s+NodeId\[\]/.test(src);
  const hasUnsafeUnknownCast = /as\s+unknown\s+as/.test(src);
  check(
    !hasUnsafeNodeIdCast && !hasUnsafeUnknownCast,
    `No unsafe casts in ${routeFile.split("/").at(-1)}`,
    `Unsafe cast found in ${routeFile} — use type guards instead`
  );
}

// ── No duplicate adapter imports ───────────────────────────────────────────────
console.log("\n7. No duplicate adapter imports");

const studioSourceFiles = [
  "platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx",
  "platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx",
  "platform/typescript/ghatana-studio/src/components/builder/VisualCanvas.tsx",
];

for (const file of studioSourceFiles) {
  const src = readSource(file);
  const hasBuilderCanvasAdapterImport = /from.*BuilderCanvasAdapter[^P]/.test(src);
  check(
    !hasBuilderCanvasAdapterImport,
    `No BuilderCanvasAdapter import in ${file.split("/").at(-1)}`,
    `Found BuilderCanvasAdapter import in ${file} — use BuilderCanvasProjectionAdapter instead`
  );
}

// ── Summary ───────────────────────────────────────────────────────────────────
console.log("");
if (failures > 0) {
  console.error(`check:builder-canvas-adapter failed with ${failures} violation(s).`);
  process.exit(1);
} else {
  console.log("check:builder-canvas-adapter passed.");
  process.exit(0);
}
