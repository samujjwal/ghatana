#!/usr/bin/env node
/**
 * Capability Schema Drift Check
 *
 * Verifies that the TypeScript `AepCapabilities` interface in
 * `products/data-cloud/planes/action/ui/src/hooks/useCapabilities.ts` stays aligned with the
 * canonical JSON schema at
 * `platform/contracts/capability-schema/aep-capabilities.v1.json`.
 *
 * Any field present in the schema but absent from the TypeScript interface
 * (or vice versa) is reported as a violation.  CI fails on any violation.
 *
 * Usage: node scripts/check-capability-schema-drift.mjs
 * Exit:  0 = aligned, 1 = drift detected
 *
 * @doc.type   tooling
 * @doc.purpose Prevent capability schema / TypeScript interface drift
 * @doc.layer  infrastructure
 */

import { readFileSync, existsSync } from 'fs';
import { resolve, join } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------
const SCHEMA_PATH = join(REPO_ROOT, 'platform', 'contracts', 'capability-schema', 'aep-capabilities.v1.json');
const TS_HOOK_PATH = join(REPO_ROOT, 'products', 'aep', 'ui', 'src', 'hooks', 'useCapabilities.ts');

// ---------------------------------------------------------------------------
// Load canonical schema fields
// ---------------------------------------------------------------------------
if (!existsSync(SCHEMA_PATH)) {
  console.error(`❌  Schema not found: ${SCHEMA_PATH}`);
  process.exit(1);
}

/** @type {{ properties: Record<string, unknown>; required: string[] }} */
const schema = JSON.parse(readFileSync(SCHEMA_PATH, 'utf8'));
const schemaFields = new Set(Object.keys(schema.properties ?? {}));
const schemaRequired = new Set(schema.required ?? []);

// ---------------------------------------------------------------------------
// Parse TypeScript interface fields
// Looks for the `export interface AepCapabilities { ... }` block and extracts
// `fieldName: boolean;` lines.  This is a structural text-parse — it is
// intentionally simple and covers only the expected interface shape.
// ---------------------------------------------------------------------------
if (!existsSync(TS_HOOK_PATH)) {
  console.error(`❌  TypeScript hook not found: ${TS_HOOK_PATH}`);
  process.exit(1);
}

const tsSource = readFileSync(TS_HOOK_PATH, 'utf8');

// Extract the AepCapabilities interface block
const interfaceMatch = /export\s+interface\s+AepCapabilities\s*\{([^}]+)\}/s.exec(tsSource);
if (!interfaceMatch) {
  console.error('❌  Could not locate `export interface AepCapabilities { ... }` in useCapabilities.ts');
  process.exit(1);
}

const interfaceBody = interfaceMatch[1];
const tsFields = new Set(
  [...interfaceBody.matchAll(/^\s{2}(\w+)\s*:/gm)].map((m) => m[1]),
);

// ---------------------------------------------------------------------------
// Compare
// ---------------------------------------------------------------------------
const inSchemaNotTs = [...schemaFields].filter((f) => !tsFields.has(f));
const inTsNotSchema = [...tsFields].filter((f) => !schemaFields.has(f));

const missingDefault = [...schemaFields].filter((f) => {
  // Check that DEFAULT_CAPABILITIES in the TS file declares this field
  return !tsSource.includes(`${f}:`);
});

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------
let exitCode = 0;

if (inSchemaNotTs.length > 0) {
  console.error('\n❌  Fields in canonical schema but missing from AepCapabilities interface:');
  for (const f of inSchemaNotTs) {
    console.error(`   • ${f}`);
  }
  exitCode = 1;
}

if (inTsNotSchema.length > 0) {
  console.error('\n❌  Fields in AepCapabilities interface but missing from canonical schema:');
  for (const f of inTsNotSchema) {
    console.error(`   • ${f}  ← add to platform/contracts/capability-schema/aep-capabilities.v1.json`);
  }
  exitCode = 1;
}

if (missingDefault.length > 0) {
  console.warn('\n⚠   Fields in schema but not found in DEFAULT_CAPABILITIES — verify they have a default:');
  for (const f of missingDefault) {
    console.warn(`   • ${f}`);
  }
  // Warn only, do not fail — default presence check is heuristic
}

if (exitCode === 0) {
  console.log(`✅  Capability schema aligned — ${schemaFields.size} field(s) match between schema and TypeScript interface.`);
}

process.exit(exitCode);
