#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, '..');
const docPath = path.join(productRoot, 'docs', 'FEATURE_FLAGS_AND_ENTITLEMENTS.md');

const requiredFlags = [
  'phase.advance',
  'phase.governance.configure',
  'phase.report.export',
  'phase.generate.enabled',
  'phase.run.preview.enabled',
  'phase.observe.enabled',
  'phase.learn.patterns.enabled',
  'phase.evolve.enabled',
  'intent',
  'shape',
  'validate',
  'generate',
  'run',
  'observe',
  'learn',
  'evolve',
  'kernel visibility',
  'product family',
  'admin prompts',
  'admin experiments',
  'admin feature flags',
  'admin observability',
  'legacy route policy',
  'onboarding',
  'canvas-calm-mode',
  'command-palette',
  'ai-suggestions',
  'ai-canvas-assistant',
  'ai-code-review',
  'real-time-collaboration',
  'canvas-comments',
  'approval-workflows',
  'agent-orchestration',
  'canvas-versioning',
  'ops-alerts',
  'ops-incidents',
  'ops-runbooks',
  'ops-postmortems',
  'ops-oncall',
  'ops-warroom',
  'ops-service-map',
  'ops-logs',
  'ops-metrics',
  'ops-dashboards',
  'admin-billing',
  'phase-run',
  'canvas-3d-mode',
  'voice-commands',
  'VITE_ENABLE_DEV_PREVIEW_MODE',
  'GITHUB_CI_CD_ENABLED',
  'artifactCompiler.unsupportedParserDiagnostics.enabled',
];

function fail(message) {
  console.error(`[feature-flag-docs] ${message}`);
  process.exitCode = 1;
}

function rowForFlag(lines, flag) {
  const needle = `\`${flag}\``;
  return lines.find((line) => line.startsWith('|') && line.includes(needle));
}

if (!fs.existsSync(docPath)) {
  fail(`Missing ${path.relative(process.cwd(), docPath)}`);
  process.exit();
}

const markdown = fs.readFileSync(docPath, 'utf8');
const lines = markdown.split(/\r?\n/);

for (const flag of requiredFlags) {
  const row = rowForFlag(lines, flag);
  if (!row) {
    fail(`Missing documented flag row for ${flag}`);
    continue;
  }
  const cells = row.split('|').slice(1, -1).map((cell) => cell.trim());
  if (cells.length < 5) {
    fail(`Flag ${flag} row must include flag, owner, default, behavior, and validation cells`);
    continue;
  }
  const [name, owner, defaultValue, behavior, validation] = cells;
  for (const [label, value] of Object.entries({ name, owner, defaultValue, behavior, validation })) {
    if (!value || /^(-|n\/a|tbd)$/i.test(value)) {
      fail(`Flag ${flag} has an incomplete ${label} cell`);
    }
  }
}

const requiredSourceMarkers = [
  'AdminFeatureFlagController.FLAG_COLLECTION',
  'yappc_feature_flags',
  'yappc_feature_flag_audit',
  'PhaseActionAuthorizationService',
  'PhasePacketServiceImpl.determineEnabledFlags',
  'FeatureFlagProvider',
  'useCapabilityGate',
  'VITE_ENABLE_DEV_PREVIEW_MODE',
  'GITHUB_CI_CD_ENABLED',
  'artifactCompiler.unsupportedParserDiagnostics.enabled',
];

for (const marker of requiredSourceMarkers) {
  if (!markdown.includes(marker)) {
    fail(`Missing source marker ${marker}`);
  }
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log(`[feature-flag-docs] ${requiredFlags.length} flags and entitlements documented with owner/default/test evidence.`);
