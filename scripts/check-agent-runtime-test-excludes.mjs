#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const DEFAULT_GRADLE_FILE =
  'products/data-cloud/planes/action/agent-runtime/build.gradle.kts';

const EXCLUDE_PATTERN = /exclude\("([^"]+)"\)/g;
const ISSUE_PATTERN = /\b(?:GH|AEP|DATA-CLOUD)-\d+\b/i;
const REMOVAL_DATE_PATTERN = /\b(?:remove by|target removal date)\s+20\d{2}-\d{2}-\d{2}\b/i;

export function checkAgentRuntimeTestExcludes(root = process.cwd(), gradleFile = DEFAULT_GRADLE_FILE) {
  const fullPath = path.join(root, gradleFile);
  if (!existsSync(fullPath)) {
    return [{ file: gradleFile, message: 'agent-runtime Gradle file is missing' }];
  }

  const source = readFileSync(fullPath, 'utf8');
  const lines = source.split(/\r?\n/);
  const violations = [];

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const matches = [...line.matchAll(EXCLUDE_PATTERN)];
    if (matches.length === 0) {
      continue;
    }

    const context = lines.slice(Math.max(0, index - 24), index + 1).join('\n');
    for (const match of matches) {
      const excludedPath = match[1];
      if (!ISSUE_PATTERN.test(context)) {
        violations.push({
          file: gradleFile,
          excludedPath,
          message: 'test exclusion is missing an issue reference',
        });
      }
      if (!REMOVAL_DATE_PATTERN.test(context)) {
        violations.push({
          file: gradleFile,
          excludedPath,
          message: 'test exclusion is missing a target removal date',
        });
      }
      if (/AgentOperatorFactoryCanonicalTypeTest/.test(excludedPath)) {
        violations.push({
          file: gradleFile,
          excludedPath,
          message: 'canonical capability factory test must not be excluded',
        });
      }
    }
  }

  return violations;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const violations = checkAgentRuntimeTestExcludes(root);
  if (violations.length === 0) {
    console.log('Agent runtime test exclusion check passed.');
    return;
  }

  console.error('Agent runtime test exclusion check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation.file} ${violation.excludedPath ?? ''}: ${violation.message}`.trim());
  }
  process.exit(1);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
