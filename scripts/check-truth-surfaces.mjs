import { readFileSync, statSync, readdirSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const targets = [
  'products/data-cloud/README.md',
  'products/data-cloud/ui/src/pages/SqlWorkspacePage.tsx',
  'products/data-cloud/ui/src/pages/SettingsPage.tsx',
  'products/data-cloud/ui/src/components/brain',
  'products/audio-video/modules/intelligence/ai-voice/README.md',
  'products/audio-video/modules/intelligence/ai-voice/INTEGRATION_GUIDE.md',
  'products/audio-video/modules/intelligence/ai-voice/DEPLOYMENT_GUIDE.md',
  'products/audio-video/modules/intelligence/ai-voice/apps/desktop/src/components/layout',
  'products/audio-video/modules/intelligence/ai-voice/apps/desktop/src/components/views',
  'products/aep/ui/src/pages/GovernancePage.tsx',
  'products/aep/ui/src/pages/RunDetailPage.tsx',
  'products/aep/server/src/main/resources/openapi.yaml'
];

const issues = [];

for (const relativeTarget of targets) {
  const absoluteTarget = path.join(repoRoot, relativeTarget);
  const stats = statSync(absoluteTarget);
  if (stats.isDirectory()) {
    walkDirectory(relativeTarget);
    continue;
  }
  inspectFile(relativeTarget);
}

if (issues.length > 0) {
  console.error('Truth-surface check failed:');
  for (const issue of issues) {
    console.error(`- ${issue}`);
  }
  process.exit(1);
}

console.log('Truth-surface check passed.');

function walkDirectory(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  for (const entry of readdirSync(absoluteDir, { withFileTypes: true })) {
    const childRelative = path.posix.join(relativeDir, entry.name);
    if (entry.isDirectory()) {
      walkDirectory(childRelative);
      continue;
    }
    if (entry.isFile()) {
      inspectFile(childRelative);
    }
  }
}

function inspectFile(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  const content = readFileSync(absolutePath, 'utf8');
  const lines = content.split(/\r?\n/);

  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (/coming soon/i.test(trimmed)) {
      issues.push(`${relativePath}:${index + 1} contains disallowed placeholder text: ${trimmed}`);
      return;
    }

    if (/production[- ]ready/i.test(trimmed) && !/not production[- ]ready|production ready:\s*no/i.test(trimmed)) {
      issues.push(`${relativePath}:${index + 1} contains unsupported readiness language: ${trimmed}`);
      return;
    }

    if (relativePath === 'products/data-cloud/README.md' && /\|\s*(Ready|Beta|Available)\s*\|/.test(trimmed)) {
      issues.push(`${relativePath}:${index + 1} uses legacy binary readiness labels instead of the evidence-linked matrix: ${trimmed}`);
    }
  });
}