#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify Studio authoring workflow integration.
 *
 * Checks:
 *   1. Required route pages exist.
 *   2. Required adapter files exist.
 *   3. App.tsx registers all required routes.
 *   4. Studio nav includes required routes.
 *   5. Translation keys are present for nav items.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for Studio authoring workflow route/adapter integration
 * @doc.layer studio
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

const studioBase = "platform/typescript/ghatana-studio/src";

console.log("\n=== check:studio-authoring-workflows ===\n");

// ── Route pages ────────────────────────────────────────────────────────────────
console.log("1. Route pages");

const routeFiles = [
  `${studioBase}/routes/BuilderPage.tsx`,
  `${studioBase}/routes/DesignSystemPage.tsx`,
  `${studioBase}/routes/ImportDecompilePage.tsx`,
  `${studioBase}/routes/FidelityReportPage.tsx`,
  `${studioBase}/routes/PreviewPage.tsx`,
  `${studioBase}/routes/ArtifactsPage.tsx`,
];
for (const rel of routeFiles) {
  check(existsSync(resolve(root, rel)), `exists: ${rel}`, `Missing route: ${rel}`);
}

// ── Adapter files ──────────────────────────────────────────────────────────────
console.log("\n2. Adapter files");

const adapterFiles = [
  `${studioBase}/adapters/BuilderCanvasProjectionAdapter.ts`,
  `${studioBase}/adapters/ArtifactStudioWorkflowAdapter.ts`,
];
for (const rel of adapterFiles) {
  check(existsSync(resolve(root, rel)), `exists: ${rel}`, `Missing adapter: ${rel}`);
}

// ── App.tsx routes ─────────────────────────────────────────────────────────────
console.log("\n3. App.tsx routes");

const appSrc = readSource(`${studioBase}/App.tsx`);
const requiredRoutes = [
  "/builder",
  "/design-system",
  "/import",
  "/fidelity-report",
  "/preview",
];
for (const route of requiredRoutes) {
  check(
    appSrc.includes(`path="${route}"`),
    `App.tsx registers route '${route}'`,
    `App.tsx missing route '${route}'`
  );
}

// Route page imports
const requiredImports = [
  "ImportDecompilePage",
  "FidelityReportPage",
  "PreviewPage",
  "BuilderPage",
];
for (const imp of requiredImports) {
  check(
    appSrc.includes(imp),
    `App.tsx imports ${imp}`,
    `App.tsx missing import for ${imp}`
  );
}

// ── Navigation ─────────────────────────────────────────────────────────────────
console.log("\n4. Navigation");

const navSrc = readSource(`${studioBase}/navigation/studioNavigation.ts`);
check(navSrc.includes('"builder"'), 'studioNavigation includes "builder" route', '"builder" missing from studioNavigation');

// ── Translations ────────────────────────────────────────────────────────────────
console.log("\n5. Translations");

const translationSrc = readSource(`${studioBase}/i18n/studioTranslations.ts`);
check(
  translationSrc.includes("studio.navigation.builder"),
  'translations include "studio.navigation.builder"',
  '"studio.navigation.builder" translation missing'
);

// ── ArtifactsPage decompile workflow ──────────────────────────────────────────
console.log("\n6. ArtifactsPage decompile workflow");

const artifactsSrc = readSource(`${studioBase}/routes/ArtifactsPage.tsx`);
check(
  artifactsSrc.includes("DecompileJobState"),
  "ArtifactsPage uses DecompileJobState",
  "ArtifactsPage missing decompile job state"
);
check(
  artifactsSrc.includes("DecompileJobsPanel"),
  "ArtifactsPage renders DecompileJobsPanel",
  "ArtifactsPage missing DecompileJobsPanel"
);
check(
  artifactsSrc.includes("ResidualReviewQueue"),
  "ArtifactsPage renders ResidualReviewQueue",
  "ArtifactsPage missing ResidualReviewQueue"
);

// ── Preview runtime safety ───────────────────────────────────────────────────
console.log("\n7. Preview runtime safety");

const previewRuntimeSrc = readSource(`${studioBase}/preview/in-memory-preview-runtime.ts`);
check(
  !/\bFunction\s*\(/.test(previewRuntimeSrc) && !/\bnew\s+Function\b/.test(previewRuntimeSrc),
  "Preview runtime does not construct dynamic functions",
  "Preview runtime must not use Function/new Function for imported source"
);
check(
  previewRuntimeSrc.includes("parsePreviewSource") && previewRuntimeSrc.includes("renderStaticPreviewHtml"),
  "Preview runtime uses static TypeScript AST preview extraction",
  "Preview runtime must use static AST preview extraction"
);
check(
  previewRuntimeSrc.includes("module \"") && previewRuntimeSrc.includes("is not allowed"),
  "Preview runtime rejects disallowed module imports",
  "Preview runtime must reject disallowed module imports"
);

// ── Result ────────────────────────────────────────────────────────────────────
console.log(`\n${failures === 0 ? "✅ All checks passed." : `❌ ${failures} check(s) failed.`}\n`);
process.exit(failures > 0 ? 1 : 0);
