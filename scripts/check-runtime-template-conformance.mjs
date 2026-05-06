#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
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
      '${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/prometheus/prometheus.yml',
      '${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/prometheus/rules',
      '${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/grafana/provisioning',
      '${PRODUCT_OBSERVABILITY_ROOT:-../../monitoring}/grafana/dashboards',
      '${FLASHIT_OBSERVABILITY_OVERLAY_ROOT:-./monitoring}/alerts/flashit-rules.yml',
      '${FLASHIT_OBSERVABILITY_OVERLAY_ROOT:-./monitoring}/grafana/dashboards',
    ],
    forbidden: [
      'GF_SECURITY_ADMIN_PASSWORD=admin',
      'OPENAI_API_KEY=${OPENAI_API_KEY:-sk-placeholder}',
      '${PRODUCT_OBSERVABILITY_ROOT:-./monitoring}',
    ],
  },
  {
    file: 'products/flashit/backend/gateway/Dockerfile',
    required: [
      'Shared template source: config/docker/templates/product-node-api.Dockerfile.template',
      'node:18-alpine AS deps',
      'node:18-alpine AS builder',
      'node:18-alpine AS runner',
      'pnpm@10.33.0',
      'USER flashit',
      'HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3',
      'http://localhost:3001/health',
    ],
    forbidden: ['pnpm@8', 'FROM node:20', 'USER root'],
  },
  {
    file: 'products/flashit/client/web/Dockerfile',
    required: [
      'Shared template source: config/docker/templates/product-node-web.Dockerfile.template',
      'node:18-alpine AS builder',
      'nginx:alpine AS runner',
      'pnpm@10.33.0',
      'COPY nginx.conf /etc/nginx/conf.d/default.conf',
      'HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3',
      'http://localhost/health.html',
    ],
    forbidden: ['pnpm@8', 'FROM node:20'],
  },
  {
    file: 'products/flashit/backend/agent/Dockerfile',
    required: [
      'Shared template source: config/docker/templates/product-java-service.Dockerfile.template',
      'eclipse-temurin:21-jdk-alpine AS builder',
      'eclipse-temurin:21-jre-alpine',
      './gradlew clean build --no-daemon',
      'USER appuser',
      'HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=3',
      'http://localhost:8090/health',
      'FLASHIT_AI_DISABLED',
      'OPENAI_API_KEY must be set for FlashIt AI mode',
    ],
    forbidden: ['-x test'],
  },
  {
    file: 'config/docker/templates/product-node-api.Dockerfile.template',
    required: [
      'Kernel-owned Node API runtime template',
      'node:18-alpine AS deps',
      'node:18-alpine AS builder',
      'node:18-alpine AS runner',
      'pnpm@10.33.0',
      'USER product',
      'HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3',
    ],
    forbidden: ['pnpm@8', 'FROM node:20'],
  },
  {
    file: 'config/docker/templates/product-node-web.Dockerfile.template',
    required: [
      'Kernel-owned Node/static web runtime template',
      'node:18-alpine AS builder',
      'nginx:alpine AS runner',
      'pnpm@10.33.0',
      'HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3',
    ],
    forbidden: ['pnpm@8', 'FROM node:20'],
  },
  {
    file: 'config/docker/templates/product-java-service.Dockerfile.template',
    required: [
      'Kernel-owned Java service runtime template',
      'eclipse-temurin:21-jdk-alpine AS builder',
      'eclipse-temurin:21-jre-alpine',
      './gradlew clean build --no-daemon',
      'USER appuser',
      'HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=3',
    ],
    forbidden: ['-x test'],
  },
];

const violations = [];

const forbiddenFiles = [
  'products/flashit/monitoring/prometheus.yml',
  'products/flashit/monitoring/grafana/provisioning/datasources/prometheus.yml',
  'products/flashit/monitoring/grafana/provisioning/dashboards/dashboards.yml',
];

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

for (const file of forbiddenFiles) {
  if (existsSync(path.join(repoRoot, file))) {
    violations.push(`${file} must not exist; FlashIt may only provide product dashboard/alert overlays and must use Kernel-owned observability stack config`);
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
