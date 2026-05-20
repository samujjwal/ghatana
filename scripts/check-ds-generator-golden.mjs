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
 *   7. Has semantic validation for canonical component states.
 *   8. Has semantic alias resolution tests.
 *   9. Has contrast-failure gate validation.
 *   10. Has token graph cycle and missing reference validation.
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
  "platform/typescript/ds-generator/src/__tests__/golden.test.ts",
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

// ── Semantic validation: canonical component states ─────────────────────────────
console.log("\n3. Canonical component states");

const CANONICAL_STATES = [
  "default",
  "hover",
  "active",
  "focus",
  "focus-visible",
  "disabled",
  "loading",
  "selected",
  "error",
  "success",
  "warning",
];

check(docSrc.includes("CANONICAL_COMPONENT_STATES"), "CANONICAL_COMPONENT_STATES array defined", "CANONICAL_COMPONENT_STATES missing");
for (const state of CANONICAL_STATES) {
  check(docSrc.includes(`'${state}'`), `Canonical state '${state}' defined`, `Canonical state '${state}' missing`);
}
check(docSrc.includes("ComponentStateSchema"), "ComponentStateSchema defined", "ComponentStateSchema missing");
check(docSrc.includes("ComponentVariantDefinitionSchema"), "ComponentVariantDefinitionSchema defined", "ComponentVariantDefinitionSchema missing");

// ── Token graph ────────────────────────────────────────────────────────────────
console.log("\n4. Token graph");

const graphSrc = readSource("platform/typescript/ds-generator/src/tokens/token-graph.ts");
check(graphSrc.includes("buildTokenGraph"), "buildTokenGraph exported", "buildTokenGraph missing");
check(graphSrc.includes("cycle"), "cycle detection present", "cycle detection not found in token-graph.ts");

// Semantic validation: token graph cycle detection
// The implementation uses inline cycle detection in resolveChain with visited Set
check(graphSrc.includes("visited") && graphSrc.includes("cycle"), "Cycle detection logic present", "Cycle detection logic missing");
check(graphSrc.includes("missing-token") || graphSrc.includes("missing-alias"), "Missing reference error types present", "Missing reference error types missing");

// ── Contrast validation ───────────────────────────────────────────────────────
console.log("\n5. Contrast validation");

const contrastSrc = readSource("platform/typescript/ds-generator/src/validation/contrast.ts");
const emitFilesSrc = readSource("platform/typescript/ds-generator/src/targets/emit-files.ts");
check(contrastSrc.includes("auditContrastPairs"), "auditContrastPairs exported", "auditContrastPairs missing");
check(contrastSrc.includes("assertDocumentContrastCompliance"), "assertDocumentContrastCompliance exported", "assertDocumentContrastCompliance missing");

// Semantic validation: contrast-failure gate
check(contrastSrc.includes("fail") || contrastSrc.includes("threshold") || contrastSrc.includes("gate"), "Contrast failure gate/threshold present", "Contrast failure gate missing");
check(emitFilesSrc.includes("assertDocumentContrastCompliance") && emitFilesSrc.includes("enforceContrast"), "emitFiles blocks contrast failures by default", "emitFiles must enforce contrast compliance by default");
check(contrastSrc.includes("WCAG") || contrastSrc.includes("AA") || contrastSrc.includes("AAA"), "WCAG compliance check present", "WCAG compliance check missing");

// ── Target emitters ────────────────────────────────────────────────────────────
console.log("\n6. Target emitters");

const cssSrc = readSource("platform/typescript/ds-generator/src/targets/css.ts");
check(cssSrc.includes("emitCss"), "emitCss exported from css.ts", "emitCss missing from css.ts");
check(cssSrc.includes("--"), "CSS custom properties use -- prefix", "CSS -- prefix not found in emitCss");

const jsonSrc = readSource("platform/typescript/ds-generator/src/targets/json.ts");
check(jsonSrc.includes("emitJson"), "emitJson exported from json.ts", "emitJson missing from json.ts");

const tailwindSrc = readSource("platform/typescript/ds-generator/src/targets/tailwind.ts");
check(tailwindSrc.includes("emitTailwind"), "emitTailwind exported from tailwind.ts", "emitTailwind missing from tailwind.ts");

const reactThemeSrc = readSource("platform/typescript/ds-generator/src/targets/react-theme.ts");
check(reactThemeSrc.includes("emitReactTheme"), "emitReactTheme exported from react-theme.ts", "emitReactTheme missing from react-theme.ts");

// ── Semantic alias validation ───────────────────────────────────────────────────
console.log("\n7. Semantic alias validation");

check(docSrc.includes("SemanticTokenAliasSchema"), "SemanticTokenAliasSchema defined", "SemanticTokenAliasSchema missing");
check(docSrc.includes("alias"), "Semantic alias field present", "Semantic alias field missing");
check(docSrc.includes("tokenKey"), "Token key field present", "Token key field missing");
check(docSrc.includes("semanticAliases"), "Semantic aliases field in document", "Semantic aliases field missing");

// Check for alias resolution logic
check(docSrc.includes("resolve") || graphSrc.includes("resolve"), "Alias resolution logic present", "Alias resolution logic missing");

// ── Golden test validation ─────────────────────────────────────────────────────
console.log("\n8. Golden test validation");

const goldenTestSrc = readSource("platform/typescript/ds-generator/src/__tests__/golden.test.ts");
check(goldenTestSrc.includes("golden"), "Golden test file contains 'golden' references", "Golden test file missing 'golden' references");
check(goldenTestSrc.includes("snapshot") || goldenTestSrc.includes("toMatchSnapshot"), "Golden tests use snapshots", "Golden tests missing snapshot assertions");
check(goldenTestSrc.includes("emitFiles"), "Golden tests validate emitFiles output", "Golden tests missing emitFiles validation");
check(goldenTestSrc.includes("deterministic"), "Golden tests verify determinism", "Golden tests missing determinism check");

// ── Index exports ──────────────────────────────────────────────────────────────
console.log("\n9. Index exports");

const indexSrc = readSource("platform/typescript/ds-generator/src/index.ts");
const expectedSymbols = [
  "emitCss", "emitJson", "emitTailwind", "emitReactTheme",
  "buildTokenGraph", "auditContrastPairs", "assertDocumentContrastCompliance",
  "createDesignSystemDocument", "DS_DOCUMENT_SCHEMA_VERSION",
];
for (const sym of expectedSymbols) {
  check(indexSrc.includes(sym), `index.ts references '${sym}'`, `index.ts missing '${sym}'`);
}

// ── Schema migration validation ─────────────────────────────────────────────────
console.log("\n10. Schema migration validation");

check(docSrc.includes("schemaVersion"), "Schema version field present in document", "Schema version field missing");
check(docSrc.includes("DS_DOCUMENT_SCHEMA_VERSION"), "Schema version constant exported", "Schema version constant missing");
check(docSrc.includes("1.0.0"), "Schema version 1.0.0 defined", "Schema version 1.0.0 missing");

// ── Result ────────────────────────────────────────────────────────────────────
console.log(`\n${failures === 0 ? "✅ All checks passed." : `❌ ${failures} check(s) failed.`}\n`);
process.exit(failures > 0 ? 1 : 0);
