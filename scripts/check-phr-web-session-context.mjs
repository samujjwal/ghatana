#!/usr/bin/env node
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(__dirname, '..');
const WEB_SRC = resolve(REPO_ROOT, 'products/phr/apps/web/src');
const WEB_SESSION_CONTEXT_SCAN_DIRS = ['pages', 'components', 'layout'];

function walkTsx(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory() && entry !== 'node_modules' && entry !== '__tests__') {
      results.push(...walkTsx(full));
    } else if (stat.isFile() && (entry.endsWith('.ts') || entry.endsWith('.tsx'))) {
      results.push(full);
    }
  }
  return results;
}

function lineNumber(content, index) {
  return content.slice(0, index).split('\n').length;
}

export function findPartialWebSessionContextViolations(files = loadWebSourceFiles()) {
  const violations = [];
  const partialSessionHeaderPattern = /(tenantId|principalId|role):\s*session\.(tenantId|principalId|role)/g;
  const partialAccessContextPattern = /\{\s*tenantId\s*,\s*principalId\s*,\s*role\s*\}/g;

  for (const [file, content] of files.entries()) {
    for (const match of content.matchAll(partialSessionHeaderPattern)) {
      const lineStart = content.lastIndexOf('\n', match.index ?? 0) + 1;
      const lineEnd = content.indexOf('\n', match.index ?? 0);
      const line = content.slice(lineStart, lineEnd < 0 ? undefined : lineEnd);
      if (line.includes('logInfo(') || line.includes('logWarn(') || line.includes('logError(')) {
        continue;
      }
      violations.push({
        file,
        line: lineNumber(content, match.index ?? 0),
        message: 'PHR web API calls must pass toSessionContext(session) so persona, tier, facility, and correlation context are preserved.',
      });
    }

    for (const match of content.matchAll(partialAccessContextPattern)) {
      const lineStart = content.lastIndexOf('\n', match.index ?? 0) + 1;
      const lineEnd = content.indexOf('\n', match.index ?? 0);
      const line = content.slice(lineStart, lineEnd < 0 ? undefined : lineEnd);
      if (!line.includes('=>') && !line.includes('useMemo') && !line.includes('const apiContext')) {
        continue;
      }
      violations.push({
        file,
        line: lineNumber(content, match.index ?? 0),
        message: 'PHR web API calls that use access context must pass usePhrRequestContext() so persona, tier, facility, and correlation context are preserved.',
      });
    }
  }

  return violations;
}

function loadWebSourceFiles() {
  const files = new Map();
  for (const dir of WEB_SESSION_CONTEXT_SCAN_DIRS) {
    const fullDir = resolve(WEB_SRC, dir);
    for (const file of walkTsx(fullDir)) {
      files.set(relative(REPO_ROOT, file).replaceAll('\\', '/'), readFileSync(file, 'utf8'));
    }
  }
  return files;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const violations = findPartialWebSessionContextViolations();
  if (violations.length > 0) {
    console.error('[phr-web-session-context] FAIL: partial session header objects found:');
    for (const violation of violations) {
      console.error(`  - ${violation.file}:${violation.line} ${violation.message}`);
    }
    process.exit(1);
  }
  console.log('[phr-web-session-context] PASS: PHR web API calls use full session context.');
}
