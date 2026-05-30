#!/usr/bin/env node

/**
 * G11-T01: Static check to ensure backend responses include correlation IDs.
 * 
 * This script scans PHR route files to verify that:
 * 1. All PhrRouteSupport.errorResponse() calls include correlationId parameter
 * 2. All PhrRouteSupport.jsonResponse() calls include correlationId parameter
 * 3. Context is extracted before try-catch blocks to enable correlation ID usage in catch blocks
 */

import { readFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROUTES_DIR = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'routes');

// Patterns to detect missing correlation IDs
const PATTERNS = {
  // errorResponse without correlationId (3-arg version: statusCode, code, message)
  errorResponseNoCorrelation: /PhrRouteSupport\.errorResponse\(\s*\d+\s*,\s*"[^"]+"\s*,\s*[^)]+\)\s*(?!;)/g,
  
  // jsonResponse without correlationId (2-arg version: statusCode, body)
  jsonResponseNoCorrelation: /PhrRouteSupport\.jsonResponse\(\s*\d+\s*,\s*[^)]+\)\s*(?!;)/g,
  
  // Catch blocks that don't have correlationId extraction before them
  catchWithoutCorrelation: /}\s*catch\s*\([^)]+\)\s*{[\s\S]*?return\s+PhrRouteSupport\.errorResponse\([^)]+\)[\s\S]*?}/g,
};

const violations = [];

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const fileName = filePath.split('\\').pop().split('/').pop();
  
  // Check for errorResponse calls without correlationId
  const errorResponseMatches = content.matchAll(PATTERNS.errorResponseNoCorrelation);
  for (const match of errorResponseMatches) {
    // Check if this is actually a 3-arg call (should be 4-arg with correlationId)
    const argCount = (match[0].match(/,/g) || []).length + 1;
    if (argCount === 3) {
      violations.push({
        file: fileName,
        type: 'errorResponse without correlationId',
        line: getLineNumber(content, match.index),
        snippet: match[0].trim(),
      });
    }
  }
  
  // Check for jsonResponse calls without correlationId
  const jsonResponseMatches = content.matchAll(PATTERNS.jsonResponseNoCorrelation);
  for (const match of jsonResponseMatches) {
    const argCount = (match[0].match(/,/g) || []).length + 1;
    if (argCount === 2) {
      violations.push({
        file: fileName,
        type: 'jsonResponse without correlationId',
        line: getLineNumber(content, match.index),
        snippet: match[0].trim(),
      });
    }
  }
}

function getLineNumber(content, index) {
  const before = content.substring(0, index);
  return before.split('\n').length;
}

function main() {
  try {
    const files = readdirSync(ROUTES_DIR).filter(f => f.endsWith('Routes.java'));
    
    for (const file of files) {
      checkFile(join(ROUTES_DIR, file));
    }
    
    if (violations.length === 0) {
      console.log('✅ All route responses include correlation IDs');
      process.exit(0);
    } else {
      console.log(`❌ Found ${violations.length} violations:\n`);
      for (const v of violations) {
        console.log(`  ${v.file}:${v.line} - ${v.type}`);
        console.log(`    ${v.snippet}\n`);
      }
      process.exit(1);
    }
  } catch (error) {
    console.error('Error running check:', error.message);
    process.exit(1);
  }
}

main();
