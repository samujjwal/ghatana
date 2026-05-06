#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const productRoot = resolve(new URL('..', import.meta.url).pathname);
const composePath = resolve(productRoot, 'docker-compose.local.yml');
const composeText = readFileSync(composePath, 'utf8');

const aiDisabled = (process.env.FLASHIT_AI_DISABLED ?? 'false').trim().toLowerCase() === 'true';
const openAiKey = process.env.OPENAI_API_KEY?.trim();

if (!composeText.includes('FLASHIT_AI_DISABLED')) {
  console.error('FlashIt compose validation failed: docker-compose.local.yml must declare FLASHIT_AI_DISABLED.');
  process.exit(1);
}

if (!composeText.includes('OPENAI_API_KEY')) {
  console.error('FlashIt compose validation failed: docker-compose.local.yml must declare OPENAI_API_KEY.');
  process.exit(1);
}

if (composeText.includes('OPENAI_API_KEY: ${OPENAI_API_KEY:?')) {
  console.error(
    'FlashIt compose validation failed: OPENAI_API_KEY interpolation must not break FLASHIT_AI_DISABLED=true compose config.'
  );
  process.exit(1);
}

for (const requiredSecret of [
  'FLASHIT_POSTGRES_PASSWORD',
  'FLASHIT_POSTGRES_TEST_PASSWORD',
  'MINIO_ROOT_USER',
  'MINIO_ROOT_PASSWORD',
  'GF_SECURITY_ADMIN_PASSWORD',
]) {
  if (!composeText.includes(requiredSecret)) {
    console.error(`FlashIt compose validation failed: docker-compose.local.yml must declare ${requiredSecret}.`);
    process.exit(1);
  }
}

if (!aiDisabled && !openAiKey) {
  console.error(
    'FlashIt compose validation failed: OPENAI_API_KEY is required unless FLASHIT_AI_DISABLED=true.'
  );
  process.exit(1);
}

console.log(
  aiDisabled
    ? 'FlashIt compose validation passed in AI-disabled mode.'
    : 'FlashIt compose validation passed with OPENAI_API_KEY configured.'
);
