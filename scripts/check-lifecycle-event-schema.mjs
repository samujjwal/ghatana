#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateKernelLifecycleEvent } from '../platform/typescript/kernel-product-contracts/dist/index.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const REQUIRED_EVENT_TYPES = [
  'lifecycle.plan.created',
  'lifecycle.phase.started',
  'lifecycle.step.started',
  'lifecycle.step.completed',
  'lifecycle.gate.evaluated',
  'lifecycle.phase.completed',
];
const PRODUCT_REQUIRED_GATES = {
  'digital-marketing': [
    'bridge-compliance',
    'marketing-consent-boundary',
    'non-regulated-customer-data-minimization',
  ],
  phr: [
    'consent',
    'pii-classification',
    'audit-evidence',
    'fhir-contract-validation',
    'tenant-data-sovereignty',
  ],
};

function parseArgs(argv) {
  const productIndex = argv.indexOf('--product');
  if (productIndex === -1 || !argv[productIndex + 1]) {
    throw new Error('Usage: node scripts/check-lifecycle-event-schema.mjs --product <productId> [--runs-dir .kernel-runs]');
  }
  const runsIndex = argv.indexOf('--runs-dir');
  return {
    productId: argv[productIndex + 1],
    runsDir: runsIndex === -1 ? '.kernel-runs' : argv[runsIndex + 1],
  };
}

function listFiles(directory) {
  if (!existsSync(directory)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...listFiles(absolutePath));
    } else if (entry.isFile()) {
      files.push(absolutePath);
    }
  }
  return files;
}

function loadEventManifests(productRunsDir) {
  return listFiles(productRunsDir)
    .filter((filePath) => path.basename(filePath) === 'lifecycle-events.json')
    .map((filePath) => ({
      filePath,
      document: JSON.parse(readFileSync(filePath, 'utf8')),
    }))
    .filter(({ document }) => Array.isArray(document.events) && document.events.length > 0);
}

export function validateLifecycleEventSchema(options) {
  const productRunsDir = path.resolve(options.repoRoot ?? repoRoot, options.runsDir, options.productId);
  const manifests = loadEventManifests(productRunsDir);
  const errors = [];
  if (manifests.length === 0) {
    return [`No lifecycle-events.json manifests found for ${options.productId} under ${productRunsDir}`];
  }

  const allEvents = [];
  for (const { filePath, document } of manifests) {
    for (const event of document.events) {
      const validation = validateKernelLifecycleEvent(event);
      if (!validation.valid) {
        errors.push(`${path.relative(options.repoRoot ?? repoRoot, filePath)} has invalid event ${event?.metadata?.eventId ?? '<unknown>'}: ${validation.errors.join('; ')}`);
        continue;
      }
      if (event.metadata.productUnitId !== options.productId) {
        errors.push(`${path.relative(options.repoRoot ?? repoRoot, filePath)} event ${event.metadata.eventId} productUnitId must be ${options.productId}`);
      }
      if (event.metadata.correlationId.trim().length === 0) {
        errors.push(`${path.relative(options.repoRoot ?? repoRoot, filePath)} event ${event.metadata.eventId} missing correlationId`);
      }
      allEvents.push(event);
    }
  }

  const eventTypes = new Set(allEvents.map((event) => event.metadata.eventType));
  for (const eventType of REQUIRED_EVENT_TYPES) {
    if (!eventTypes.has(eventType)) {
      errors.push(`${options.productId} lifecycle events missing required event type ${eventType}`);
    }
  }

  const gateIds = new Set(
    allEvents
      .filter((event) => event.metadata.eventType === 'lifecycle.gate.evaluated')
      .map((event) => event.payload.gateId),
  );
  for (const gateId of PRODUCT_REQUIRED_GATES[options.productId] ?? []) {
    if (!gateIds.has(gateId)) {
      errors.push(`${options.productId} lifecycle events missing required gate event ${gateId}`);
    }
  }

  return errors;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const errors = validateLifecycleEventSchema(parseArgs(process.argv.slice(2)));
    if (errors.length === 0) {
      console.log('Lifecycle event schema check passed.');
      process.exit(0);
    }
    console.error(`Lifecycle event schema check FAILED (${errors.length} error(s)):`);
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
