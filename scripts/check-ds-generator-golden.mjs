#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify DS generator golden output stability.
 *
 * Checks that ds-generator:
 *   1. Has all required source files.
 *   2. Has the design-system-document model.
 *   3. Has all target emitters (css, json, tailwind, react-theme).
 *   4. Has a token-graph module with cycle detection.
 *   5. Has a contrast audit module.
 *   6. Exports all required symbols from index.ts.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for DS generator golden test stability
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

console.log("\n=== check:ds-generator-golden ===\n");

// ── Source files ──────────────────────────────────────────────────────────────
console.log("1. Required source files");

const requiredFiles = [
  "platform/typescript/ds-generator/src/model/design-system-document.ts",
  "platform/typescript/ds-generator/src/tokens/token-graph.ts",
  "platform/typescript/ds-generator/src/tokens/contrast.ts",
  "platform/typescript/ds-generator/src/validation/contrast.ts",
  "platform/typescript/ds-generator/src/targets/css.ts",
  "platform/typescript/ds-generator/src/targets/json.ts",
  "platform/typescript/ds-generator/src/targets/tailwind.ts",
  "platform/typescript/ds-generator/src/targets/react-theme.ts",
  "platform/typescript/ds-generator/src/brand/index.ts",
  "platform/typescript/ds-generator/src/index.ts",
];
for (const rel of requiredFiles) {
  check(existsSync(resolve(root, rel)), `exists: ${rel}`, `Missing: ${rel}`);
}

// ── Design system document model ──────────────────────────────────────────────
console.log("\n2. Design system document model");

const docSrc = readSource("platform/typescript/ds-generator/src/model/design-system-document.ts");
check(docSrc.includes("DS_DOCUMENT_SCHEMA_VERSION"), "DS_DOCUMENT_SCHEMA_VERSION declared", "DS_DOCUMENT_SCHEMA_VERSION missing");
check(docSrc.includes("createDesignSystemDocument"), "createDesignSystemDocument factory present", "createDesignSystemDocument factory missing");
check(docSrc.includes("DesignSystemDocumentSchema"), "DesignSystemDocumentSchema defined", "DesignSystemDocumentSchema missing");

// ── Token graph ────────────────────────────────────────────────────────────────
console.log("\n3. Token graph");

const graphSrc = readSource("platform/typescript/ds-generator/src/tokens/token-graph.ts");
check(graphSrc.includes("buildTokenGraph"), "buildTokenGraph exported", "buildTokenGraph missing");
check(graphSrc.includes("cycle"), "cycle detection present", "cycle detection not found in token-graph.ts");

// ── Contrast validation ───────────────────────────────────────────────────────
console.log("\n4. Contrast validation");

const contrastSrc = readSource("platform/typescript/ds-generator/src/validation/contrast.ts");
check(contrastSrc.includes("auditContrastPairs"), "auditContrastPairs exported", "auditContrastPairs missing");

// ── Target emitters ────────────────────────────────────────────────────────────
console.log("\n5. Target emitters");

const cssSrc = readSource("platform/typescript/ds-generator/src/targets/css.ts");
check(cssSrc.includes("emitCss"), "emitCss exported from css.ts", "emitCss missing from css.ts");
check(cssSrc.includes("--"), "CSS custom properties use -- prefix", "CSS -- prefix not found in emitCss");

const jsonSrc = readSource("platform/typescript/ds-generator/src/targets/json.ts");
check(jsonSrc.includes("emitJson"), "emitJson exported from json.ts", "emitJson missing from json.ts");

const tailwindSrc = readSource("platform/typescript/ds-generator/src/targets/tailwind.ts");
check(tailwindSrc.includes("emitTailwind"), "emitTailwind exported from tailwind.ts", "emitTailwind missing from tailwind.ts");

const reactThemeSrc = readSource("platform/typescript/ds-generator/src/targets/react-theme.ts");
check(reactThemeSrc.includes("emitReactTheme"), "emitReactTheme exported from react-theme.ts", "emitReactTheme missing from react-theme.ts");

// ── Index exports ──────────────────────────────────────────────────────────────
console.log("\n6. Index exports");

const indexSrc = readSource("platform/typescript/ds-generator/src/index.ts");
const expectedSymbols = [
  "emitCss", "emitJson", "emitTailwind", "emitReactTheme",
  "buildTokenGraph", "auditContrastPairs",
  "createDesignSystemDocument", "DS_DOCUMENT_SCHEMA_VERSION",
];
for (const sym of expectedSymbols) {
  check(indexSrc.includes(sym), `index.ts references '${sym}'`, `index.ts missing '${sym}'`);
}

// ── Result ────────────────────────────────────────────────────────────────────
console.log(`\n${failures === 0 ? "✅ All checks passed." : `❌ ${failures} check(s) failed.`}\n`);
process.exit(failures > 0 ? 1 : 0);
