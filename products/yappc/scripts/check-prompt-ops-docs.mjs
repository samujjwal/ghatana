#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, '..');
const docPath = path.join(productRoot, 'docs', 'PROMPT_OPERATIONS.md');

const requiredSections = [
  '## Runtime Model',
  '## Evaluation',
  '## Promotion',
  '## Rollback',
  '## Weight Rebalancing',
  '## Quarantine',
  '## Admin UI Contract',
  '## Change Rules',
];

const requiredMarkers = [
  'yappc_prompt_versions',
  'yappc_prompt_version_audit',
  'AdminPromptVersionController',
  'PromptLifecycleService',
  'PromptTemplateRegistry',
  'PromptVersionsPage',
  'recordScore',
  'promote',
  'rollback',
  'WEIGHTS_REBALANCED',
  'quarantine:',
  'AdminPromptVersionControllerTest',
  'PromptLifecycleServiceTest',
  'PromptVersionsPage.test.tsx',
];

function fail(message) {
  console.error(`[prompt-ops-docs] ${message}`);
  process.exitCode = 1;
}

if (!fs.existsSync(docPath)) {
  fail(`Missing ${path.relative(process.cwd(), docPath)}`);
  process.exit();
}

const markdown = fs.readFileSync(docPath, 'utf8');

for (const section of requiredSections) {
  if (!markdown.includes(section)) {
    fail(`Missing section ${section}`);
  }
}

for (const marker of requiredMarkers) {
  if (!markdown.includes(marker)) {
    fail(`Missing evidence marker ${marker}`);
  }
}

const quarantineSection = markdown.split('## Quarantine')[1]?.split('## Admin UI Contract')[0] ?? '';
if (!quarantineSection.includes('no separate prompt quarantine endpoint')) {
  fail('Quarantine section must state the current endpoint boundary explicitly');
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log('[prompt-ops-docs] prompt evaluation, promotion, rollback, rebalance, and quarantine guidance is documented.');
