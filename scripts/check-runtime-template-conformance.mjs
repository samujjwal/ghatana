#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    file: 'products/phr/launcher/Dockerfile',
    required: [
      'Shared template source: config/docker/templates/product-launcher.Dockerfile.template',
      ':products:phr:productConformanceCheck',
      ':products:phr:checkApiContractConformance',
      ':products:phr:launcher:check',
      ':products:phr:launcher:installDist',
    ],
    forbidden: ['-x test'],
  },
  {
    file: 'products/finance/launcher/Dockerfile',
    required: [
      'Shared template source: config/docker/templates/product-launcher.Dockerfile.template',
      ':products:finance:productConformanceCheck',
      ':products:finance:checkApiContractConformance',
      ':products:finance:launcher:check',
      ':products:finance:launcher:installDist',
    ],
    forbidden: ['-x test'],
  },
  {
    file: 'products/phr/launcher/build.gradle.kts',
    required: [
      'tasks.named("check")',
      ':products:phr:productConformanceCheck',
      ':products:phr:checkApiContractConformance',
    ],
    forbidden: [],
  },
  {
    file: 'products/finance/launcher/build.gradle.kts',
    required: [
      'tasks.named("check")',
      ':products:finance:productConformanceCheck',
      ':products:finance:checkApiContractConformance',
    ],
    forbidden: [],
  },
  {
    file: 'products/flashit/docker-compose.local.yml',
    required: [
      'Shared template source: config/docker/templates/product-runtime.compose.yaml',
      '${FLASHIT_POSTGRES_PORT:-5432}',
      '${FLASHIT_GRAFANA_PORT:-3001}',
      '${PRODUCT_OBSERVABILITY_ROOT:-./monitoring}/prometheus.yml',
      '${PRODUCT_OBSERVABILITY_ROOT:-./monitoring}/grafana/dashboards',
    ],
    forbidden: [
      'GF_SECURITY_ADMIN_PASSWORD=admin',
      'OPENAI_API_KEY=${OPENAI_API_KEY:-sk-placeholder}',
      './monitoring/prometheus.yml',
      './monitoring/grafana/dashboards',
    ],
  },
  {
    file: 'products/flashit/backend/gateway/Dockerfile',
    required: ['pnpm@10.33.0'],
    forbidden: ['pnpm@8'],
  },
  {
    file: 'products/flashit/client/web/Dockerfile',
    required: ['pnpm@10.33.0'],
    forbidden: ['pnpm@8'],
  },
];

const violations = [];

for (const check of checks) {
  const absolutePath = path.join(repoRoot, check.file);
  const source = readFileSync(absolutePath, 'utf8');

  for (const token of check.required) {
    if (!source.includes(token)) {
      violations.push(`${check.file} is missing required shared-runtime token "${token}"`);
    }
  }

  for (const token of check.forbidden) {
    if (source.includes(token)) {
      violations.push(`${check.file} still contains forbidden token "${token}"`);
    }
  }
}

if (violations.length > 0) {
  console.error('Runtime template conformance check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Runtime template conformance check passed.');
