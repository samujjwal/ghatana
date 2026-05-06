#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    name: 'DMOS boundary workflow coverage test',
    file: 'products/digital-marketing/dm-domain-packs/src/test/java/com/ghatana/digitalmarketing/pack/DigitalMarketingBoundaryWorkflowCoverageTest.java',
    required: [
      'DefaultBoundaryPolicyResolver',
      'DigitalMarketingBoundaryPolicyStore',
      'DM-BP-001',
      'DM-BP-002',
      'DM-BP-003',
      'DM-BP-004',
      'DM-BP-005',
      'DM-BP-006',
      'DM-BP-007',
      'DM-BP-008',
      'DM-BP-999',
      'workspace dashboard read',
      'contact profile read',
      'contact export mutation',
      'audience sync operation',
      'campaign launch',
      'budget increase',
      'content publish',
      'connector execute',
      'every DM-BP workflow has a blocked direct variant',
    ],
  },
  {
    name: 'DMOS domain-pack validation registration',
    file: 'products/digital-marketing/dm-domain-packs/build.gradle.kts',
    required: ['DigitalMarketingBoundaryWorkflowCoverageTest'],
  },
];

const violations = [];

for (const check of checks) {
  const filePath = path.join(repoRoot, check.file);
  if (!existsSync(filePath)) {
    violations.push(`${check.name}: missing ${check.file}`);
    continue;
  }
  const source = readFileSync(filePath, 'utf8');
  const missing = check.required.filter((token) => !source.includes(token));
  if (missing.length > 0) {
    violations.push(`${check.name}: missing coverage token(s) ${missing.join(', ')} in ${check.file}`);
  }
}

if (violations.length > 0) {
  console.error('DMOS boundary workflow coverage check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('DMOS boundary workflow coverage check passed.');
