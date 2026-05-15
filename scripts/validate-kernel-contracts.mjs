#!/usr/bin/env node

/**
 * Kernel Contracts Validator
 *
 * Validates that every public contract in @ghatana/kernel-product-contracts has:
 * - TypeScript type definition
 * - Zod schema for validation
 * - Parse function for runtime validation
 *
 * @doc.type tooling
 * @doc.purpose Validate kernel contracts have complete type, schema, and parse function coverage
 * @doc.layer infrastructure
 */

import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const contractsPackagePath = path.join(repoRoot, 'platform/typescript/kernel-product-contracts/src');

function findTypeScriptFiles(dir, root) {
  const files = [];
  const entries = readdirSync(dir);
  
  for (const entry of entries) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    
    if (stat.isDirectory()) {
      if (!entry.startsWith('.') && entry !== 'node_modules' && entry !== '__tests__') {
        files.push(...findTypeScriptFiles(fullPath, root));
      }
    } else if (entry.endsWith('.ts') || entry.endsWith('.js')) {
      files.push(fullPath);
    }
  }
  
  return files;
}

function extractExports(fileContent) {
  const exports = {
    types: [],
    schemas: [],
    validators: [],
    parseFunctions: []
  };
  
  // Extract type exports
  const typeMatches = fileContent.matchAll(/export\s+(type\s+(\w+)|interface\s+(\w+))/g);
  for (const match of typeMatches) {
    const typeName = match[2] || match[3];
    if (typeName) exports.types.push(typeName);
  }
  
  // Extract Zod schema exports (typically end with 'Schema')
  const schemaMatches = fileContent.matchAll(/export\s+(const\s+(\w+Schema)|function\s+(\w+Schema))/g);
  for (const match of schemaMatches) {
    const schemaName = match[2] || match[3];
    if (schemaName) exports.schemas.push(schemaName);
  }
  
  // Extract validator functions (typically start with 'validate' or 'is')
  const validatorMatches = fileContent.matchAll(/export\s+(const\s+(validate\w+|is\w+)|function\s+(validate\w+|is\w+))/g);
  for (const match of validatorMatches) {
    const validatorName = match[2] || match[3];
    if (validatorName) exports.validators.push(validatorName);
  }
  
  // Extract parse functions (typically start with 'parse')
  const parseMatches = fileContent.matchAll(/export\s+(const\s+(parse\w+)|function\s+(parse\w+))/g);
  for (const match of parseMatches) {
    const parseName = match[2] || match[3];
    if (parseName) exports.parseFunctions.push(parseName);
  }
  
  return exports;
}

function validateContractCoverage() {
  const violations = [];
  const contractFiles = findTypeScriptFiles(contractsPackagePath, contractsPackagePath);
  
  for (const filePath of contractFiles) {
    const relativePath = path.relative(repoRoot, filePath);
    const content = readFileSync(filePath, 'utf8');
    const exports = extractExports(content);
    
    // Skip index.ts and test files
    if (relativePath.endsWith('index.ts') || relativePath.includes('__tests__')) {
      continue;
    }
    
    // Check for contracts that have types but missing schemas or parse functions
    for (const typeName of exports.types) {
      const expectedSchemaName = `${typeName}Schema`;
      const expectedValidatorName = `validate${typeName}`;
      const expectedParseName = `parse${typeName}`;
      
      const hasSchema = exports.schemas.some(s => s === expectedSchemaName || s.includes(typeName));
      const hasValidator = exports.validators.some(v => v === expectedValidatorName || v.includes(typeName));
      const hasParseFunction = exports.parseFunctions.some(p => p === expectedParseName || p.includes(typeName));
      
      // Only report violations for types that appear to be public contracts
      // (not internal types starting with underscore)
      if (!typeName.startsWith('_') && typeName[0] === typeName[0].toUpperCase()) {
        if (!hasSchema && !hasValidator && !hasParseFunction) {
          violations.push(`${relativePath}: type '${typeName}' has no schema or validation function. Add Zod schema '${expectedSchemaName}' and validator '${expectedValidatorName}'.`);
        }
      }
    }
  }
  
  return violations;
}

function main() {
  const violations = validateContractCoverage();
  
  if (violations.length > 0) {
    console.error('Kernel contracts validation failed:');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }
  
  console.log('OK: kernel contracts validation passed.');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}

export { validateContractCoverage };
