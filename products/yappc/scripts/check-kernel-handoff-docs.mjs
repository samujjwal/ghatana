#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, '..');
const docPath = path.join(productRoot, 'docs', 'KERNEL_HANDOFF.md');

const requiredSections = [
  '## Boundary',
  '## Contract Values',
  '## CLI Flow',
  '## API Flow',
  '## Validation',
  '## Kernel Consumption',
  '## Operator Checks',
  '## Change Rules',
];

const requiredMarkers = [
  'ProductUnitIntentExporter',
  'ProductUnitIntentValidationService',
  'ProductUnitKernelContractRegistry',
  'KernelProductUnitHandoffService',
  'CreateCommand',
  '/api/v1/yappc/generate/product-unit-intent',
  'generateProductUnitIntent',
  'kernel-product-unit',
  'ghatana-file-registry',
  'standard-web-api-product',
  'CreateCommandKernelProductUnitTest',
  'CICommandKernelProductUnitTest',
  'ProductUnitIntentExporterTest',
  'ProductUnitIntentValidationServiceTest',
  'KernelProductUnitHandoffServiceTest',
  'GenerationApiControllerTest',
  'RouteManifestParityTest',
  'ProductUnitKernelContractRegistryTest',
  'DataCloudKernelLifecycleTruthSourceTest',
  'golden/product-unit-intent.standard.yaml',
];

function fail(message) {
  console.error(`[kernel-handoff-docs] ${message}`);
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

if (!markdown.includes('Invoke-RestMethod')) {
  fail('API flow must include a concrete PowerShell Invoke-RestMethod example');
}

if (!markdown.includes('--target kernel-product-unit')) {
  fail('CLI flow must include the kernel-product-unit target flag');
}

if (!markdown.includes('YAPPC does not mutate Kernel registries')) {
  fail('Kernel consumption section must state the ownership boundary explicitly');
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log('[kernel-handoff-docs] CLI and API Kernel ProductUnitIntent handoff guidance is documented.');
