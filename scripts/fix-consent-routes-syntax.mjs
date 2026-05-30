#!/usr/bin/env node

/**
 * Fix syntax errors in PhrConsentRoutes.java caused by automated fix script.
 * The script incorrectly inserted correlationId parameters inside function calls.
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const CONSENT_ROUTES_FILE = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java', 'com', 'ghatana', 'phr', 'api', 'routes', 'PhrConsentRoutes.java');

function fixFile(filePath) {
  let content = readFileSync(filePath, 'utf-8');
  let modified = false;

  // Fix pattern: ex.getMessage(, correlationId) -> ex.getMessage(), correlationId
  content = content.replace(/ex\.getMessage\(\s*,\s*correlationId\)/g, 'ex.getMessage(), correlationId');
  if (content !== readFileSync(filePath, 'utf-8')) {
    modified = true;
  }

  // Fix pattern: existing.get(, correlationId) -> existing.get(), correlationId
  content = content.replace(/existing\.get\(\s*,\s*correlationId\)/g, 'existing.get(), correlationId');
  if (content !== readFileSync(filePath, 'utf-8')) {
    modified = true;
  }

  if (modified) {
    writeFileSync(filePath, content, 'utf-8');
    console.log('Fixed syntax errors in PhrConsentRoutes.java');
    return true;
  }
  return false;
}

fixFile(CONSENT_ROUTES_FILE);
