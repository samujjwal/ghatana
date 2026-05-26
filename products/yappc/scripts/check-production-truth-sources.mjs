#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = fileURLToPath(new URL('.', import.meta.url));
const yappcRoot = join(scriptDir, '..');

const productionFiles = [
  'Dockerfile',
  'deployment/helm/values.yaml',
  'deployment/kubernetes/base/configmap.yaml',
  'deployment/kubernetes/base/deployment.yaml',
  'deployment/kubernetes/base/yappc-lifecycle-blue-green.yaml',
  'deployment/kustomize/base/platform-resources.yaml',
];

const inlineSourcePattern = /YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE(?:\s*[:=]\s*)["']?([^"'\s]+)["']?/gi;
const yamlEnvSourcePattern = /name:\s*YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE[\s\S]*?value:\s*["']?([^"'\s]+)["']?/gi;
const forbiddenSourcePattern = /^(local|local-filesystem|filesystem|mock|fake|demo)$/i;

const errors = [];

for (const file of productionFiles) {
  const absolutePath = join(yappcRoot, file);
  if (!existsSync(absolutePath)) {
    errors.push(`${file}: expected production configuration file is missing`);
    continue;
  }

  const content = readFileSync(absolutePath, 'utf8');
  const matches = [
    ...[...content.matchAll(inlineSourcePattern)].map(match => match[1].trim()),
    ...[...content.matchAll(yamlEnvSourcePattern)].map(match => match[1].trim()),
  ];
  if (matches.length === 0) {
    errors.push(`${file}: missing YAPPC_KERNEL_LIFECYCLE_TRUTH_SOURCE=data-cloud or event-cloud`);
    continue;
  }

  for (const value of matches) {
    if (forbiddenSourcePattern.test(value)) {
      errors.push(`${file}: production Kernel lifecycle truth source cannot be ${value}`);
    }
    if (!/^(data-cloud|event-cloud)$/i.test(value)) {
      errors.push(`${file}: production Kernel lifecycle truth source must be data-cloud or event-cloud, found ${value}`);
    }
  }
}

if (errors.length > 0) {
  console.error('YAPPC production truth-source check failed:');
  for (const error of errors) {
    console.error(`  - ${relative(process.cwd(), join(yappcRoot, error.split(':')[0]))}: ${error.split(':').slice(1).join(':').trim()}`);
  }
  process.exit(1);
}

console.log('YAPPC production truth-source check passed.');
