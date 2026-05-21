#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = path.join(repoRoot, 'config', 'canonical-product-registry.json');

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function isDirectory(filePath) {
  try {
    return statSync(filePath).isDirectory();
  } catch {
    return false;
  }
}

function isFile(filePath) {
  try {
    return statSync(filePath).isFile();
  } catch {
    return false;
  }
}

function collectSourceFiles(rootDir) {
  if (!isDirectory(rootDir)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(rootDir)) {
    if (['build', 'dist', 'node_modules', '.gradle', '.turbo'].includes(entry)) {
      continue;
    }
    const absolutePath = path.join(rootDir, entry);
    if (isDirectory(absolutePath)) {
      files.push(...collectSourceFiles(absolutePath));
    } else if (isFile(absolutePath) && /\.(java|kt|ts|tsx|js|jsx|mjs|cjs|gradle\.kts)$/.test(entry)) {
      files.push(absolutePath);
    }
  }
  return files;
}

function productJavaPackage(productId) {
  return `com.ghatana.${productId.replaceAll('-', '')}`;
}

export function checkCrossProductInteractionBoundaries(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const registryDocument = readJson(options.registryPath ?? registryPath);
  const registry = registryDocument.registry ?? registryDocument;
  const pairs = new Set();
  const errors = [];

  for (const [productId, product] of Object.entries(registry)) {
    const lifecycleConfigPath = product?.lifecycleConfigPath;
    if (typeof lifecycleConfigPath !== 'string' || !lifecycleConfigPath.endsWith('kernel-product.yaml')) {
      continue;
    }
    const absoluteConfigPath = path.join(root, lifecycleConfigPath);
    if (!existsSync(absoluteConfigPath)) {
      errors.push(`${productId} lifecycle config missing: ${lifecycleConfigPath}`);
      continue;
    }
    const config = YAML.parse(readFileSync(absoluteConfigPath, 'utf8'));
    for (const contract of asArray(config?.interactions?.consumes)) {
      if (typeof contract?.providerProductId === 'string') {
        pairs.add(`${productId}->${contract.providerProductId}`);
      }
    }
  }

  for (const pair of pairs) {
    const [consumerProductId, providerProductId] = pair.split('->');
    const consumerRoot = path.join(root, 'products', consumerProductId);
    const providerProjectNeedle = `project(":products:${providerProductId}`;
    const providerPathNeedles = [
      `products/${providerProductId}/`,
      productJavaPackage(providerProductId),
    ];
    for (const filePath of collectSourceFiles(consumerRoot)) {
      const relativePath = path.relative(root, filePath).replaceAll(path.sep, '/');
      if (relativePath.includes('/kernel-product.yaml')) {
        continue;
      }
      const source = readFileSync(filePath, 'utf8');
      if (source.includes(providerProjectNeedle)) {
        errors.push(`${relativePath} directly depends on ${providerProductId}; use ProductInteractionHandler/Broker contracts`);
      }
      for (const needle of providerPathNeedles) {
        if (source.includes(needle)) {
          errors.push(`${relativePath} directly references ${providerProductId} (${needle}); use Kernel interaction contracts`);
        }
      }
    }
  }

  return { errors, checkedPairs: [...pairs].sort() };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkCrossProductInteractionBoundaries();
  if (result.errors.length > 0) {
    console.error('Cross-product interaction boundary check failed:');
    for (const error of result.errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }
  console.log(`Cross-product interaction boundary check passed for ${result.checkedPairs.length} interaction pair(s).`);
}
