#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const repoRoot = resolve(fileURLToPath(new URL('..', import.meta.url)));

const files = [
  'products/phr/schema-packs/contracts/consent-status-request.v1.json',
  'products/phr/schema-packs/contracts/consent-status-response.v1.json',
  'products/phr/schema-packs/contracts/notification-preference-request.v1.json',
  'products/phr/schema-packs/contracts/notification-preference-response.v1.json',
  'products/digital-marketing/dm-core-contracts/src/main/resources/contracts/lead-captured.v1.json',
  'products/digital-marketing/dm-core-contracts/src/main/resources/contracts/phr-consent-status-request.v1.json',
  'products/digital-marketing/dm-core-contracts/src/main/resources/contracts/phr-consent-status-response.v1.json',
  'products/digital-marketing/dm-core-contracts/src/main/resources/contracts/notification-preference-request.v1.json',
  'products/digital-marketing/dm-core-contracts/src/main/resources/contracts/notification-preference-response.v1.json',
];

files.forEach(file => {
  const absolutePath = resolve(repoRoot, file);
  const content = readFileSync(absolutePath, 'utf8');
  const hash = createHash('sha256').update(content).digest('hex');
  console.log(`${file}: ${hash}`);
});
