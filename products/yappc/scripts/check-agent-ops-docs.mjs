#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, '..');
const docPath = path.join(productRoot, 'docs', 'AGENT_OPERATIONS.md');

const requiredSections = [
  '## Runtime Model',
  '## Execution State',
  '## Lifecycle Flow',
  '## Failure Handling',
  '## Governance Visibility',
  '## Retention',
  '## Operator Checks',
  '## Change Rules',
];

const requiredMarkers = [
  'AgentExecutorOperator',
  'AgentStateRepository',
  'agent-executions',
  'AgentRunViewer',
  'AgentMonitor',
  'AgentGovernanceHealthPanel',
  'PENDING',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'deleteOlderThan',
  'TenantContext',
  'AgentExecutorOperatorGovernedRuntimeTest',
  'YappcDataCloudRepositoryTenantEnforcementTest',
  'AgentRunViewer.test.tsx',
  'AgentMonitor.test.tsx',
];

function fail(message) {
  console.error(`[agent-ops-docs] ${message}`);
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

const stateRows = markdown
  .split(/\r?\n/)
  .filter((line) => line.startsWith('| `') && /\|.*\|.*\|.*\|/.test(line));
for (const state of ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED']) {
  if (!stateRows.some((line) => line.includes(`\`${state}\``))) {
    fail(`Missing execution-state row for ${state}`);
  }
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log('[agent-ops-docs] agent state, lifecycle, failure, governance, and retention guidance is documented.');
