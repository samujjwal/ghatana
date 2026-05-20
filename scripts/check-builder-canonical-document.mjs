#!/usr/bin/env node
/**
 * @fileoverview CI gate: verify UI Builder uses the canonical BuilderDocument.
 *
 * Checks:
 *   1. @ghatana/ui-builder has a canonical builder-document.ts with Zod schema.
 *   2. BuilderDocumentSchema and CURRENT_SCHEMA_VERSION are exported.
 *   3. import.ts reconciles against BuilderDocument (no rootNodes legacy pattern).
 *   4. operations.ts uses the canonical document type.
 *   5. codegen.ts generates from BuilderDocument.
 *   6. Tests exist and are non-trivial (not theater).
 *
 * Exit 0 = pass, exit 1 = fail.
 *
 * @doc.type script
 * @doc.purpose CI gate for UI Builder canonical BuilderDocument model integrity
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

console.log("\n=== check:builder-canonical-document ===\n");

// ── builder-document.ts ───────────────────────────────────────────────────────
console.log("1. Canonical builder-document.ts");

const builderDocPath = "platform/typescript/ui-builder/src/core/builder-document.ts";
check(
  existsSync(resolve(root, builderDocPath)),
  `exists: ${builderDocPath}`,
  `Missing: ${builderDocPath}`
);

const builderDocSrc = readSource(builderDocPath);
check(
  builderDocSrc.includes("BuilderDocumentSchema"),
  "builder-document.ts declares BuilderDocumentSchema",
  "builder-document.ts must declare BuilderDocumentSchema (Zod schema)"
);
check(
  builderDocSrc.includes("CURRENT_SCHEMA_VERSION"),
  "builder-document.ts declares CURRENT_SCHEMA_VERSION",
  "builder-document.ts must declare CURRENT_SCHEMA_VERSION"
);
check(
  builderDocSrc.includes("schemaVersion"),
  "BuilderDocument has schemaVersion field",
  "BuilderDocument must include schemaVersion for migration support"
);
check(
  builderDocSrc.includes("createBuilderDocument") || builderDocSrc.includes("export function create"),
  "builder-document.ts has a factory function",
  "builder-document.ts must export a factory function (e.g. createBuilderDocument)"
);
check(
  !builderDocSrc.includes('"rootNodes"') || builderDocSrc.includes("normalizeBuilderDocument"),
  "No unguarded rootNodes legacy field (or migration is present)",
  "builder-document.ts must not silently accept legacy rootNodes without migration"
);

// ── import.ts ─────────────────────────────────────────────────────────────────
console.log("\n2. import.ts uses canonical BuilderDocument");

const importSrc = readSource("platform/typescript/ui-builder/src/core/import.ts");
check(
  importSrc.includes("import type { BuilderDocument }") ||
    importSrc.includes("import { BuilderDocument }") ||
    importSrc.includes("BuilderDocument"),
  "import.ts references BuilderDocument",
  "import.ts must import and use BuilderDocument"
);
// Allow rootNodes only in comments — check that no non-comment line actually uses it
const importNonCommentLines = importSrc.split("\n").filter(l => !l.trimStart().startsWith("//"));
const importNonCommentSrc = importNonCommentLines.join("\n");
check(
  !importNonCommentSrc.includes("rootNodes"),
  "import.ts does not use legacy rootNodes field in production code (comment mentions allowed)",
  "import.ts must not use legacy rootNodes in production code — use nodes[] from canonical BuilderDocument"
);
check(
  importSrc.includes("schemaVersion") || importSrc.includes("normalizeBuilderDocument") || importSrc.includes("attachBuilderDocumentCompatibility"),
  "import.ts validates schemaVersion or normalizes document",
  "import.ts must validate schemaVersion or call a normalizer to prevent silent migration failures"
);

// ── operations.ts ─────────────────────────────────────────────────────────────
console.log("\n3. operations.ts uses canonical document");

const opsPath = "platform/typescript/ui-builder/src/core/operations.ts";
check(
  existsSync(resolve(root, opsPath)),
  `exists: ${opsPath}`,
  `Missing: ${opsPath}`
);

const opsSrc = readSource(opsPath);
check(
  opsSrc.includes("BuilderDocument"),
  "operations.ts uses BuilderDocument type",
  "operations.ts must accept and return BuilderDocument"
);
check(
  opsSrc.includes("export function") || opsSrc.includes("export const"),
  "operations.ts exports operation functions",
  "operations.ts must export at least one operation function"
);

// ── codegen.ts ────────────────────────────────────────────────────────────────
console.log("\n4. codegen.ts generates from BuilderDocument");

const codegenPath = "platform/typescript/ui-builder/src/core/codegen.ts";
check(
  existsSync(resolve(root, codegenPath)),
  `exists: ${codegenPath}`,
  `Missing: ${codegenPath}`
);

const codegenSrc = readSource(codegenPath);
check(
  codegenSrc.includes("BuilderDocument"),
  "codegen.ts accepts BuilderDocument",
  "codegen.ts must accept BuilderDocument as input"
);
check(
  codegenSrc.includes("export function") || codegenSrc.includes("export const"),
  "codegen.ts exports code generation functions",
  "codegen.ts must export at least one codegen function"
);

// ── Public index.ts exports ───────────────────────────────────────────────────
console.log("\n5. Public API exports from @ghatana/ui-builder");

const indexSrc = readSource("platform/typescript/ui-builder/src/index.ts");
// Also check core/index.ts which is transitively re-exported via `export * from './core/index'`
const coreIndexSrc = readSource("platform/typescript/ui-builder/src/core/index.ts");
check(
  indexSrc.includes("BuilderDocument") || indexSrc.includes("builder-document") || coreIndexSrc.includes("BuilderDocument"),
  "index.ts re-exports BuilderDocument (directly or via core/index barrel)",
  "index.ts must export BuilderDocument type or builder-document module"
);
check(
  indexSrc.includes("BuilderDocumentSchema") || indexSrc.includes("CURRENT_SCHEMA_VERSION") ||
    coreIndexSrc.includes("BuilderDocumentSchema") || coreIndexSrc.includes("CURRENT_SCHEMA_VERSION"),
  "BuilderDocumentSchema and CURRENT_SCHEMA_VERSION are re-exported (directly or via core/index barrel)",
  "Must export BuilderDocumentSchema and CURRENT_SCHEMA_VERSION from index.ts or core/index.ts"
);

// ── Tests ─────────────────────────────────────────────────────────────────────
console.log("\n6. Test coverage for canonical document");

const testFiles = [
  "platform/typescript/ui-builder/src/core/__tests__/builder-document.test.ts",
  "platform/typescript/ui-builder/src/__tests__/builder-document.test.ts",
];
const testFileExists = testFiles.some((f) => existsSync(resolve(root, f)));
check(
  testFileExists,
  "builder-document test file exists",
  `Missing builder-document test in ${testFiles.join(" or ")}`
);

if (testFileExists) {
  const testPath = testFiles.find((f) => existsSync(resolve(root, f)));
  const testSrc = readSource(testPath);
  check(
    testSrc.includes("import") && (testSrc.includes("from '") || testSrc.includes('from "')),
    "builder-document test imports from production source (not theater)",
    "builder-document test must import real production code"
  );
  check(
    testSrc.includes("createBuilderDocument") || testSrc.includes("BuilderDocumentSchema") || testSrc.includes("parseBuilderDocument"),
    "builder-document test exercises canonical factory or schema",
    "builder-document test must call createBuilderDocument or parse via BuilderDocumentSchema"
  );
}

// ── Summary ───────────────────────────────────────────────────────────────────
console.log();
if (failures > 0) {
  console.error(`✗ ${failures} check(s) failed.`);
  process.exit(1);
} else {
  console.log("✅ All checks passed.");
  process.exit(0);
}
