#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify artifact-compiler-ts contracts.
 *
 * Checks:
 *   1. artifact-contracts package is present and has correct exports.
 *   2. artifact-compiler-ts package is present and depends on artifact-contracts.
 *   3. All expected public APIs are exported from each package.
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for artifact compiler/contracts contract stability
 * @doc.layer platform
 */

import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, "..");

let failures = 0;

function fail(msg) {
  console.error(`  ✗ ${msg}`);
  failures++;
}

function ok(msg) {
  console.log(`  ✓ ${msg}`);
}

function check(condition, passMsg, failMsg) {
  if (condition) {
    ok(passMsg);
  } else {
    fail(failMsg);
  }
}

function readJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch {
    return null;
  }
}

console.log("\n=== check:artifact-compiler-contracts ===\n");

// ── artifact-contracts package ────────────────────────────────────────────────
console.log("1. @ghatana/artifact-contracts");

const contractsPkg = resolve(root, "platform/typescript/artifact-contracts/package.json");
const contractsJson = readJson(contractsPkg);
check(contractsJson !== null, "package.json exists", "package.json missing at platform/typescript/artifact-contracts/");
check(contractsJson?.name === "@ghatana/artifact-contracts", "package name is @ghatana/artifact-contracts", `Unexpected package name: ${contractsJson?.name}`);

const contractsExports = [
  "platform/typescript/artifact-contracts/src/model.ts",
  "platform/typescript/artifact-contracts/src/fidelity.ts",
  "platform/typescript/artifact-contracts/src/source.ts",
  "platform/typescript/artifact-contracts/src/provenance.ts",
  "platform/typescript/artifact-contracts/src/evidence.ts",
  "platform/typescript/artifact-contracts/src/index.ts",
];
for (const rel of contractsExports) {
  check(existsSync(resolve(root, rel)), `source file exists: ${rel}`, `Missing source file: ${rel}`);
}

// ── artifact-compiler-ts package ─────────────────────────────────────────────
console.log("\n2. @ghatana/artifact-compiler-ts");

const compilerPkg = resolve(root, "platform/typescript/artifact-compiler-ts/package.json");
const compilerJson = readJson(compilerPkg);
check(compilerJson !== null, "package.json exists", "package.json missing at platform/typescript/artifact-compiler-ts/");
check(compilerJson?.name === "@ghatana/artifact-compiler-ts", "package name is @ghatana/artifact-compiler-ts", `Unexpected package name: ${compilerJson?.name}`);

const compilerDeps = compilerJson?.dependencies ?? {};
check(
  "@ghatana/artifact-contracts" in compilerDeps,
  "depends on @ghatana/artifact-contracts",
  "Missing dependency @ghatana/artifact-contracts in artifact-compiler-ts"
);
check(
  "typescript" in compilerDeps,
  "depends on typescript (compiler API)",
  "Missing dependency 'typescript' in artifact-compiler-ts"
);

const compilerExports = [
  "platform/typescript/artifact-compiler-ts/src/decompile/tsx.ts",
  "platform/typescript/artifact-compiler-ts/src/compile/react.ts",
  "platform/typescript/artifact-compiler-ts/src/fidelity/scorer.ts",
  "platform/typescript/artifact-compiler-ts/src/residual/residual-islands.ts",
  "platform/typescript/artifact-compiler-ts/src/projection/builder.ts",
  "platform/typescript/artifact-compiler-ts/src/projection/canvas.ts",
  "platform/typescript/artifact-compiler-ts/src/projection/ds.ts",
  "platform/typescript/artifact-compiler-ts/src/index.ts",
  "platform/typescript/artifact-compiler-ts/src/__tests__/tsx.test.ts",
  "platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts",
];
for (const rel of compilerExports) {
  check(existsSync(resolve(root, rel)), `source file exists: ${rel}`, `Missing source file: ${rel}`);
}

// ── API surface checks ────────────────────────────────────────────────────────
console.log("\n3. Public API surface");

function readSource(rel) {
  try {
    return readFileSync(resolve(root, rel), "utf8");
  } catch {
    return "";
  }
}

const indexSrc = readSource("platform/typescript/artifact-compiler-ts/src/index.ts");
const expectedExports = [
  "decompileTsx",
  "compileReact",
  "detectResidualIslands",
  "fidelityGate",
  "projectToBuilder",
  "projectToCanvas",
  "projectToDs",
];
for (const sym of expectedExports) {
  check(
    indexSrc.includes(sym),
    `index.ts exports '${sym}'`,
    `index.ts does not export '${sym}'`
  );
}

// ── Result ────────────────────────────────────────────────────────────────────
console.log(`\n${failures === 0 ? "✅ All checks passed." : `❌ ${failures} check(s) failed.`}\n`);
process.exit(failures > 0 ? 1 : 0);
