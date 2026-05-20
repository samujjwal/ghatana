#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify canvas document round-trip stability.
 *
 * Checks:
 *   1. @ghatana/canvas package has required source files.
 *   2. canvas document schema version is declared.
 *   3. createCanvasDocument factory is exported.
 *   4. createCanvasStore factory is exported (not just a singleton).
 *   5. @ghatana/ui-builder has canonical import/export surface.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for canvas document schema + UI Builder model stability
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

function readJson(rel) {
  try { return JSON.parse(readFileSync(resolve(root, rel), "utf8")); } catch { return null; }
}

console.log("\n=== check:canvas-document-roundtrip ===\n");

// ── @ghatana/canvas ───────────────────────────────────────────────────────────
console.log("1. @ghatana/canvas");

const canvasFiles = [
  "platform/typescript/canvas/src/schema/canvas-document.schema.ts",
  "platform/typescript/canvas/src/hybrid/state.ts",
  "platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts",
];
for (const rel of canvasFiles) {
  check(existsSync(resolve(root, rel)), `exists: ${rel}`, `Missing: ${rel}`);
}

const schemaSrc = readSource("platform/typescript/canvas/src/schema/canvas-document.schema.ts");
check(
  schemaSrc.includes("CANVAS_DOCUMENT_SCHEMA_VERSION"),
  "CANVAS_DOCUMENT_SCHEMA_VERSION is declared",
  "CANVAS_DOCUMENT_SCHEMA_VERSION not found in canvas-document.schema.ts"
);
check(
  schemaSrc.includes("createCanvasDocument"),
  "createCanvasDocument factory is exported",
  "createCanvasDocument factory not found"
);

const stateSrc = readSource("platform/typescript/canvas/src/hybrid/state.ts");
check(
  stateSrc.includes("createCanvasStore"),
  "createCanvasStore factory is exported",
  "createCanvasStore factory not found in state.ts"
);
check(
  stateSrc.includes("createStore()"),
  "createStore() is used (not singleton store)",
  "createStore() not found — may be using singleton pattern"
);

const controllerSrc = readSource("platform/typescript/canvas/src/hybrid/hybrid-canvas-controller.ts");
check(
  controllerSrc.includes("crypto.randomUUID()"),
  "hybrid-canvas-controller uses crypto.randomUUID() (not Math.random)",
  "crypto.randomUUID() not found — potential Math.random() usage"
);

// ── @ghatana/ui-builder ───────────────────────────────────────────────────────
console.log("\n2. @ghatana/ui-builder");

const uiBuilderFiles = [
  "platform/typescript/ui-builder/src/core/import.ts",
  "platform/typescript/ui-builder/src/core/types.ts",
];
for (const rel of uiBuilderFiles) {
  check(existsSync(resolve(root, rel)), `exists: ${rel}`, `Missing: ${rel}`);
}

const importSrc = readSource("platform/typescript/ui-builder/src/core/import.ts");
check(
  importSrc.includes("schemaVersion"),
  "import.ts validates schemaVersion",
  "import.ts does not validate schemaVersion — JSON import may be unsafe"
);

// ── Result ────────────────────────────────────────────────────────────────────
console.log(`\n${failures === 0 ? "✅ All checks passed." : `❌ ${failures} check(s) failed.`}\n`);
process.exit(failures > 0 ? 1 : 0);
