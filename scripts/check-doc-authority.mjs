#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

export function validateDocAuthority(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const mapPath = path.join(root, 'docs/architecture/DOMAIN_WORKSTREAM_MAP.md');
  const registryPath = path.join(root, 'config/domain-registry.json');
  const issues = [];

  if (!existsSync(mapPath)) {
    issues.push('docs/architecture/DOMAIN_WORKSTREAM_MAP.md: missing authoritative domain map. Remediation: create the file and mark it as authoritative human-readable domain map.');
  }

  if (!existsSync(registryPath)) {
    issues.push('config/domain-registry.json: missing machine-readable domain registry. Remediation: create the registry before claiming authority coverage.');
  }

  if (existsSync(mapPath)) {
    const text = readFileSync(mapPath, 'utf8');
    if (!/Authoritative human-readable domain map/i.test(text)) {
      issues.push('docs/architecture/DOMAIN_WORKSTREAM_MAP.md: missing authoritative-map declaration. Remediation: state that the document is the authoritative human-readable domain map.');
    }
    if (!/Target commit baseline:\s*`?3d11768b045870c73b7f1ad7761a7b916203f768`?/i.test(text)) {
      issues.push('docs/architecture/DOMAIN_WORKSTREAM_MAP.md: missing target baseline commit. Remediation: record the execution-file baseline commit in the document header.');
    }
  }

  return issues;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const issues = validateDocAuthority();

  if (issues.length === 0) {
    console.log('OK: documentation authority checks passed.');
    process.exit(0);
  }

  console.error('FAIL: documentation authority checks found issues:');
  for (const issue of issues) {
    console.error(` - ${issue}`);
  }
  process.exit(1);
}