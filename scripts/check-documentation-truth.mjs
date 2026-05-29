#!/usr/bin/env node

import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { runDocumentationSurfaceCheck } from './quality/check-documentation-surfaces.mjs';

export function runDocumentationTruthCheck() {
  return runDocumentationSurfaceCheck();
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = runDocumentationTruthCheck();

  if (result.passed) {
    console.log('Documentation truth checks passed.');
    process.exit(0);
  }

  console.error('Documentation truth checks failed:');
  for (const failure of result.failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}
