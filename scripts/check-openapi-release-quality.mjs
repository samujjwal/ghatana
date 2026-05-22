#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const specPath = path.join(repoRoot, 'products/data-cloud/contracts/openapi/data-cloud.yaml');
const waiverPath = path.join(repoRoot, 'config/openapi-generic-schema-waivers.json');

const violations = [];

if (!existsSync(specPath)) {
  console.error('OpenAPI release quality check failed: missing products/data-cloud/contracts/openapi/data-cloud.yaml');
  process.exit(1);
}
if (!existsSync(waiverPath)) {
  console.error('OpenAPI release quality check failed: missing config/openapi-generic-schema-waivers.json');
  process.exit(1);
}

const source = readFileSync(specPath, 'utf8');
const waiver = JSON.parse(readFileSync(waiverPath, 'utf8'));
const allowed = new Set((waiver.allowedGenericSchemas ?? []).map((entry) => String(entry)));

const schemasBlockStart = source.indexOf('\n  schemas:\n');
if (schemasBlockStart < 0) {
  violations.push('OpenAPI file is missing components.schemas block');
}

const genericSchemas = new Set();
if (schemasBlockStart >= 0) {
  const schemasText = source.slice(schemasBlockStart);
  const schemaPattern = /^ {4}([A-Z][A-Za-z0-9_]+):\n([\s\S]*?)(?=^ {4}[A-Z][A-Za-z0-9_]+:\n|^\S|\Z)/gm;
  for (const match of schemasText.matchAll(schemaPattern)) {
    const schemaName = match[1];
    const schemaBody = match[2];
    const isObject = /\n\s*type:\s*object\s*$/m.test(schemaBody);
    const hasAdditionalProperties = /\n\s*additionalProperties:\s*true\s*$/m.test(schemaBody);
    const hasNamedProperties = /\n\s*properties:\s*$/m.test(schemaBody);
    if (isObject && hasAdditionalProperties && !hasNamedProperties) {
      genericSchemas.add(schemaName);
    }
  }
}

for (const schemaName of genericSchemas) {
  if (!allowed.has(schemaName)) {
    violations.push(`Generic schema ${schemaName} must be waived in config/openapi-generic-schema-waivers.json`);
  }
}

for (const schemaName of allowed) {
  if (!genericSchemas.has(schemaName)) {
    violations.push(`Waiver entry ${schemaName} is stale: schema is not generic anymore`);
  }
}

if (!source.includes('example:') && !source.includes('examples:')) {
  violations.push('OpenAPI file must contain at least one example/examples block for customer-facing APIs');
}

if (violations.length > 0) {
  console.error('OpenAPI release quality check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('OpenAPI release quality check passed.');
