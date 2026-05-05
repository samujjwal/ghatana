#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    file: 'products/flashit/docs/00-VISION.md',
    required: [
      '## Domain Boundary',
      '## Kernel Dependencies',
      '## Platform Capabilities Consumed',
      '## Product-Only Business Logic',
    ],
  },
  {
    file: 'products/flashit/docs/01-ARCHITECTURE.md',
    required: [
      '## Kernel Ownership Boundary',
      '## Product-Owned Services',
      '## UI Surfaces',
      '## Runtime And Observability',
    ],
  },
  {
    file: 'products/flashit/docs/06-IMPLEMENTATION_PLAN.md',
    required: [
      '## Kernel Onboarding',
      '## UI Conformance',
      '## Runtime Conformance',
      '## Remaining Delivery Work',
    ],
  },
];

const violations = [];

for (const check of checks) {
  const source = readFileSync(path.join(repoRoot, check.file), 'utf8');
  for (const token of check.required) {
    if (!source.includes(token)) {
      violations.push(`${check.file} is missing section "${token}"`);
    }
  }
}

if (violations.length > 0) {
  console.error('FlashIt documentation content check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('FlashIt documentation content check passed.');
