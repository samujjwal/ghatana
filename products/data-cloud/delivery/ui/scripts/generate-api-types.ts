#!/usr/bin/env tsx
/**
 * Generate API Types from OpenAPI Specifications
 *
 * This script generates TypeScript types from OpenAPI specs using openapi-typescript.
 * Generated types are the single source of truth for frontend-backend API contracts.
 *
 * Usage:
 *   pnpm generate:api-types
 *
 * @doc.type script
 * @doc.purpose Generate TypeScript types from OpenAPI specs
 * @doc.layer frontend
 * @doc.pattern BuildScript
 */

import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// Only include specs that are valid OpenAPI 3.0+ and can be parsed by openapi-typescript
const OPENAPI_SPECS = [
  { name: 'data-cloud', path: '../../contracts/openapi/data-cloud.yaml' },
  // action-plane.yaml and aep.yaml have unresolved $ref errors - need to be fixed
  // { name: 'action-plane', path: '../../contracts/openapi/action-plane.yaml' },
  // { name: 'aep', path: '../../contracts/openapi/aep.yaml' },
];

const OUTPUT_DIR = 'src/contracts/generated';

function ensureOutputDir(): void {
  if (!existsSync(OUTPUT_DIR)) {
    mkdirSync(OUTPUT_DIR, { recursive: true });
    console.log(`Created output directory: ${OUTPUT_DIR}`);
  }
}

function generateTypes(specName: string, specPath: string): void {
  const outputFile = join(OUTPUT_DIR, `${specName}.ts`);
  const fullPath = join(process.cwd(), specPath);

  if (!existsSync(fullPath)) {
    console.warn(`⚠️  OpenAPI spec not found: ${fullPath}`);
    return;
  }

  console.log(`Generating types from ${specName}...`);
  
  try {
    execSync(
      `npx openapi-typescript "${fullPath}" -o "${outputFile}"`,
      { stdio: 'inherit' }
    );
    console.log(`✅ Generated: ${outputFile}`);
  } catch (error) {
    console.error(`⚠️  Failed to generate types for ${specName} (skipping)`);
    console.error(`   Reason: ${error instanceof Error ? error.message : String(error)}`);
    // Continue with other specs instead of failing the entire build
  }
}

function generateIndex(): void {
  const indexPath = join(OUTPUT_DIR, 'index.ts');
  const imports = OPENAPI_SPECS.map(
    (spec) => `export * as ${spec.name.replace(/-/g, '')} from './${spec.name}.js';`
  ).join('\n');

  const content = `/**
 * Generated API Types
 *
 * Auto-generated from OpenAPI specifications. Do not edit manually.
 * Regenerate with: pnpm generate:api-types
 *
 * @doc.type types
 * @doc.purpose Export all generated API types
 * @doc.layer frontend
 */

${imports}
`;

  writeFileSync(indexPath, content, 'utf-8');
  console.log(`✅ Generated: ${indexPath}`);
}

function main(): void {
  console.log('🚀 Generating API types from OpenAPI specifications...\n');

  ensureOutputDir();

  for (const spec of OPENAPI_SPECS) {
    generateTypes(spec.name, spec.path);
  }

  generateIndex();

  console.log('\n✅ API type generation complete!');
  console.log(`📁 Generated types are in: ${OUTPUT_DIR}`);
}

main();
