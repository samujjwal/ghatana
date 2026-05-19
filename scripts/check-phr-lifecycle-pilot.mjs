#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const PRODUCT_ID = 'phr';
const REQUIRED_HEALTHCARE_GATES = [
  'consent',
  'pii-classification',
  'audit-evidence',
  'fhir-contract-validation',
  'tenant-data-sovereignty',
];
const REQUIRED_MANIFEST_PHASES = ['build', 'package', 'deploy', 'verify'];
const REQUIRED_MANIFEST_SCHEMA_VERSIONS = [
  'lifecycle-result',
  'artifact-manifest',
  'lifecycle-health-snapshot',
  'deployment-manifest',
  'verify-health-report',
];
const KERNEL_ROOTS = [
  'platform-kernel',
  'platform/typescript/kernel-product-contracts',
  'platform/typescript/kernel-lifecycle',
  'platform/typescript/kernel-artifacts',
  'platform/typescript/kernel-deployment',
  'platform/typescript/kernel-release',
  'platform/typescript/kernel-providers',
  'platform/typescript/kernel-toolchains',
];

function readJson(root, relativePath) {
  return JSON.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function readYaml(root, relativePath) {
  return YAML.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function pathExists(root, relativePath) {
  return existsSync(path.join(root, relativePath));
}

function readText(root, relativePath) {
  return readFileSync(path.join(root, relativePath), 'utf8');
}

function listFiles(root, relativeDirectory) {
  const absoluteDirectory = path.join(root, relativeDirectory);
  if (!existsSync(absoluteDirectory)) {
    return [];
  }

  const files = [];
  const visit = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (['.git', 'node_modules', 'dist', 'build', '.gradle'].includes(entry.name)) {
        continue;
      }
      const absolutePath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        visit(absolutePath);
      } else if (entry.isFile()) {
        files.push(path.relative(root, absolutePath).replaceAll('\\', '/'));
      }
    }
  };
  visit(absoluteDirectory);
  return files;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function includesAll(label, actual, expected, errors) {
  const actualSet = new Set(asArray(actual));
  for (const item of expected) {
    if (!actualSet.has(item)) {
      errors.push(`${label} missing required entry: ${item}`);
    }
  }
}

function checkUnsafeSecretDefaults(envExampleContent) {
  const violations = [];
  for (const line of envExampleContent.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) {
      continue;
    }

    const [key, ...valueParts] = trimmed.split('=');
    const value = valueParts.join('=').trim();
    if (/SECRET|PASSWORD|TOKEN|API_KEY|KEY/i.test(key) && value && !value.startsWith('your-') && !value.startsWith('<') && !value.startsWith('CHANGE')) {
      violations.push(`${key} has a potentially unsafe default value: "${value}"`);
    }
  }
  return violations;
}

export function validatePhrLifecyclePilot(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const registry = options.registry ?? readJson(root, 'config/canonical-product-registry.json').registry;
  const exists = options.pathExists ?? ((relativePath) => pathExists(root, relativePath));
  const loadYaml = options.readYaml ?? ((relativePath) => readYaml(root, relativePath));
  const loadText = options.readText ?? ((relativePath) => readText(root, relativePath));
  const findFiles = options.listFiles ?? ((relativeDirectory) => listFiles(root, relativeDirectory));
  const product = registry[PRODUCT_ID];
  const errors = [];

  if (!product) {
    return ['PHR missing from canonical product registry'];
  }

  if (product.lifecycleStatus !== 'enabled') {
    errors.push(`PHR lifecycleStatus must be enabled, got ${product.lifecycleStatus}`);
  }
  if (product.lifecycleExecutionAllowed !== true) {
    errors.push('PHR lifecycleExecutionAllowed must be true');
  }
  if (product.lifecycle?.enabled !== true) {
    errors.push('PHR lifecycle.enabled must be true');
  }
  if (!exists(product.lifecycleConfigPath ?? '')) {
    errors.push(`PHR lifecycleConfigPath does not exist: ${product.lifecycleConfigPath}`);
    return errors;
  }

  let config;
  try {
    config = options.kernelProduct ?? loadYaml(product.lifecycleConfigPath);
  } catch (error) {
    errors.push(`PHR kernel-product.yaml failed to parse: ${error instanceof Error ? error.message : String(error)}`);
    return errors;
  }

  if (config.productId !== PRODUCT_ID) {
    errors.push('PHR kernel-product.yaml must declare productId: phr');
  }
  if (config.lifecycleProfile !== 'standard-web-api-product') {
    errors.push('PHR lifecycleProfile must be standard-web-api-product');
  }
  if (config.status !== 'enabled') {
    errors.push('PHR kernel-product.yaml status must be enabled');
  }
  if (config.executionEnabled !== true) {
    errors.push('PHR kernel-product.yaml executionEnabled must be true');
  }

  if (config.surfaces?.['backend-api']?.adapter !== 'gradle-java-service') {
    errors.push('PHR backend-api surface adapter must be gradle-java-service');
  }
  if (config.surfaces?.web?.adapter !== 'pnpm-vite-react') {
    errors.push('PHR web surface adapter must be pnpm-vite-react');
  }

  includesAll('PHR dev defaultSurfaces', config.phases?.dev?.defaultSurfaces, ['backend-api', 'web'], errors);
  includesAll('PHR build defaultSurfaces', config.phases?.build?.defaultSurfaces, ['backend-api', 'web'], errors);
  includesAll('PHR validate gates', config.gates?.validate, REQUIRED_HEALTHCARE_GATES, errors);
  includesAll('PHR build gates', config.gates?.build, REQUIRED_HEALTHCARE_GATES, errors);
  includesAll('PHR deploy gates', config.gates?.deploy, ['consent', 'tenant-data-sovereignty'], errors);

  for (const gateId of REQUIRED_HEALTHCARE_GATES) {
    const gatePath = `products/phr/lifecycle/gate-packs/${gateId}.yaml`;
    if (!exists(gatePath)) {
      errors.push(`PHR gate pack missing: ${gatePath}`);
      continue;
    }
    const gatePack = loadYaml(gatePath);
    if (gatePack.productId !== PRODUCT_ID || gatePack.gateId !== gateId) {
      errors.push(`${gatePath} must declare productId: phr and gateId: ${gateId}`);
    }
  }

  for (const evidencePath of [
    'products/phr/lifecycle/readiness-evidence.yaml',
    'products/phr/schema-packs/schema-registry.yaml',
    'products/phr/deploy/local.compose.yaml',
    'products/phr/deploy/local.env.example',
  ]) {
    if (!exists(evidencePath)) {
      errors.push(`PHR required lifecycle evidence file missing: ${evidencePath}`);
    }
  }

  for (const phase of REQUIRED_MANIFEST_PHASES) {
    if (!Array.isArray(config.requiredManifests?.[phase]) || config.requiredManifests[phase].length === 0) {
      errors.push(`PHR requiredManifests.${phase} must be declared`);
    }
  }
  for (const manifestName of REQUIRED_MANIFEST_SCHEMA_VERSIONS) {
    if (!config.manifestSchemaVersions?.[manifestName]) {
      errors.push(`PHR manifestSchemaVersions missing ${manifestName}`);
    }
  }

  for (const [surface, packageConfig] of Object.entries(config.package ?? {})) {
    if (packageConfig?.adapter !== 'docker-buildx') {
      errors.push(`PHR package.${surface}.adapter must be docker-buildx`);
    }
    for (const field of ['image', 'tag', 'dockerfile', 'context']) {
      if (!packageConfig?.[field]) {
        errors.push(`PHR package.${surface} missing ${field}`);
      }
    }
    if (packageConfig?.dockerfile && !exists(packageConfig.dockerfile)) {
      errors.push(`PHR package.${surface}.dockerfile does not exist: ${packageConfig.dockerfile}`);
    }
  }

  const deployLocal = config.deployment?.local;
  if (deployLocal?.adapter !== 'compose-local') {
    errors.push('PHR deployment.local.adapter must be compose-local');
  }
  includesAll('PHR deployment.local.expectedServices', deployLocal?.expectedServices, ['phr-api', 'phr-web'], errors);
  if (deployLocal?.requireEnvFile !== false) {
    errors.push('PHR deployment.local.requireEnvFile must be false for bootstrap local deploy');
  }
  if (!deployLocal?.healthChecks?.['backend-api'] || !deployLocal?.healthChecks?.web) {
    errors.push('PHR deployment.local health checks must include backend-api and web');
  }
  if (!config.verify?.local?.healthChecks?.['backend-api'] || !config.verify?.local?.healthChecks?.web) {
    errors.push('PHR verify.local health checks must include backend-api and web');
  }

  if (deployLocal?.envExampleFile && exists(deployLocal.envExampleFile)) {
    for (const violation of checkUnsafeSecretDefaults(loadText(deployLocal.envExampleFile))) {
      errors.push(`PHR env example unsafe default: ${violation}`);
    }
  }

  const kernelFiles = KERNEL_ROOTS.flatMap((directory) => findFiles(directory));
  for (const file of kernelFiles) {
    if (file.includes('/src/test/') || file.includes('/__tests__/')) {
      continue;
    }
    if (!/\.(java|ts|tsx|js|mjs)$/.test(file)) {
      continue;
    }
    const content = loadText(file);
    if (content.includes('products/phr') || content.includes('com.ghatana.phr')) {
      errors.push(`Kernel file imports or references PHR implementation code: ${file}`);
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const errors = validatePhrLifecyclePilot();
  if (errors.length === 0) {
    console.log('PHR lifecycle pilot check passed');
    process.exit(0);
  }

  console.error(`PHR lifecycle pilot check FAILED (${errors.length} error(s)):`);
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}
