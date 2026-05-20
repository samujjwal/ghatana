#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify BuilderCanvasProjectionAdapter is type-safe.
 *
 * Checks:
 *   1. BuilderCanvasProjectionAdapter.ts exists in ghatana-studio adapters.
 *   2. It exports builderToCanvas, canvasToBuilder, BuilderToCanvasResult.
 *   3. No unsafe `as NodeId[]` casts exist at canvas adapter boundaries.
 *   4. BuilderNodeData and BuilderEdgeData are explicitly typed.
 *   5. Tests exist for the bidirectional projection.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for type-safe BuilderDocument ↔ canvas node/edge projection
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

console.log("\n=== check:builder-canvas-adapter ===\n");

// ── Adapter file existence ────────────────────────────────────────────────────
console.log("1. BuilderCanvasProjectionAdapter.ts");

const adapterPath = "platform/typescript/ghatana-studio/src/adapters/BuilderCanvasProjectionAdapter.ts";
check(
  existsSync(resolve(root, adapterPath)),
  `exists: ${adapterPath}`,
  `Missing adapter: ${adapterPath}`
);

const adapterSrc = readSource(adapterPath);

// ── Required exports ──────────────────────────────────────────────────────────
console.log("\n2. Required exports");

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
  adapterSrc.includes("BuilderToCanvasResult"),
  "exports BuilderToCanvasResult",
  "Missing export: BuilderToCanvasResult"
);

// ── Type safety ───────────────────────────────────────────────────────────────
console.log("\n3. Type safety");

// Ensure no unsafe NodeId[] boundary casts (the anti-pattern from the plan)
const unsafeNodeIdCast = /as\s+NodeId\[\]/g;
const unsafeMatches = [...(adapterSrc.match(unsafeNodeIdCast) ?? [])];
check(
  unsafeMatches.length === 0,
  "No unsafe `as NodeId[]` casts found",
  `Found ${unsafeMatches.length} unsafe \`as NodeId[]\` cast(s) — use typed extraction instead`
);

check(
  adapterSrc.includes("BuilderNodeData") && adapterSrc.includes("BuilderEdgeData"),
  "BuilderNodeData and BuilderEdgeData are explicitly typed",
  "Missing explicit BuilderNodeData/BuilderEdgeData type definitions"
);

// ── Tests ─────────────────────────────────────────────────────────────────────
console.log("\n4. Adapter tests");

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
console.log("\n5. No legacy pattern imports in studio routes");

const routeFiles = [
  "platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx",
  "platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx",
];

for (const routeFile of routeFiles) {
  const src = readSource(routeFile);
  const hasUnsafeCast = /as\s+NodeId\[\]/.test(src);
  check(
    !hasUnsafeCast,
    `No unsafe NodeId[] cast in ${routeFile.split("/").at(-1)}`,
    `Unsafe \`as NodeId[]\` cast found in ${routeFile}`
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
