#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path, { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const defaultRepoRoot = resolve(__dirname, '..');
const registryPath = join(defaultRepoRoot, 'config/canonical-product-registry.json');

const productAgnosticDocs = [
  'docs/',
  'README.md',
  'CHANGELOG.md',
  'LICENSE',
  'LICENSE.md',
];

const globalImpactFiles = new Set([
  'package.json',
  'pnpm-lock.yaml',
  'pnpm-workspace.yaml',
  'settings.gradle.kts',
  'build.gradle.kts',
  'gradle.properties',
  'config/canonical-product-registry.json',
  'config/canonical-product-registry-schema.json',
  'config/product-shape.json',
  'config/kernel-product-capability-registry.json',
]);

const globalImpactPrefixes = [
  '.github/workflows/',
  'gradle/',
  'scripts/',
  'config/generated/',
  'platform-kernel/',
  'platform-plugins/',
  'shared-services/',
];

const platformJavaPrefixes = [
  'platform/java/',
  'platform/contracts/',
  'platform-kernel/',
];

const platformTypescriptPrefixes = [
  'platform/typescript/',
  'platform-kernel/',
  'platform-plugins/',
];

const lifecyclePackagePrefixes = [
  'platform/typescript/kernel-lifecycle/',
  'platform/typescript/kernel-toolchains/',
  'platform/typescript/kernel-artifacts/',
  'platform/typescript/kernel-release/',
];

const lifecycleConfigPrefixes = [
  'config/kernel-plugin-registry.json',
  'config/kernel-plugin-registry-schema.json',
  'config/lifecycle-profiles/',
  'config/deployment-targets/',
  'config/artifact-schemas/',
];

const uiPlatformPackagePrefixes = [
  'platform/typescript/design-system/',
  'platform/typescript/product-shell/',
  'platform/typescript/theme/',
  'platform/typescript/tokens/',
  'platform/typescript/i18n/',
];

function normalizePath(changedPath) {
  return changedPath
    .replace(/\\/g, '/')
    .replace(/^\.\//, '')
    .replace(/^\/+/, '')
    .trim();
}

function hasPrefix(changedPath, prefixes) {
  return prefixes.some((prefix) => changedPath === prefix.slice(0, -1) || changedPath.startsWith(prefix));
}

function isDocsOnlyPath(changedPath) {
  if (productAgnosticDocs.some((docPath) => changedPath === docPath || changedPath.startsWith(docPath))) {
    return true;
  }

  if (/^products\/[^/]+\/(?:docs\/|README\.md$|CHANGELOG\.md$)/.test(changedPath)) {
    return true;
  }

  return /\.(?:md|mdx|adoc|txt)$/.test(changedPath);
}

function globPatternToRegExp(pattern) {
  const normalized = normalizePath(pattern);
  let source = '^';

  for (let index = 0; index < normalized.length; index += 1) {
    const char = normalized[index];
    const nextChar = normalized[index + 1];

    if (char === '*' && nextChar === '*') {
      source += '.*';
      index += 1;
      continue;
    }

    if (char === '*') {
      source += '[^/]*';
      continue;
    }

    source += char.replace(/[|\\{}()[\]^$+?.]/g, '\\$&');
  }

  return new RegExp(`${source}(?:/.*)?$`);
}

function registryEntries(registry, options = {}) {
  const includeDemo = options.includeDemo === true;

  return Object.entries(registry)
    .filter(([, product]) => product.metadata?.status === 'active')
    .filter(([, product]) => product.ci?.enabled === true)
    .filter(([, product]) => includeDemo || product.kind !== 'demo/example')
    .filter(([, product]) => !options.businessProductsOnly || product.kind === 'business-product')
    .map(([productId, product]) => ({ productId, product }));
}

function productHasLifecycleEnabled(product) {
  return product.lifecycle?.enabled === true;
}

function productHasPnpmSurface(product) {
  return (product.pnpmPackages ?? []).length > 0
    || (product.surfaces ?? []).some((surface) => typeof surface.packagePath === 'string');
}

function productHasGradleSurface(product) {
  return (product.gradleModules ?? []).length > 0 || typeof product.buildFile === 'string';
}

function productOwnsPath(productId, product, changedPath) {
  const productRoot = `products/${productId}/`;
  const matchers = [
    productRoot,
    product.manifestPath,
    product.buildFile,
    ...(product.pnpmPackages ?? []),
    ...(product.surfaces ?? []).flatMap((surface) => [surface.path, surface.packagePath].filter(Boolean)),
  ]
    .filter((candidate) => typeof candidate === 'string')
    .map((candidate) => normalizePath(candidate));

  return matchers.some((matcher) => {
    if (matcher.includes('*')) {
      return globPatternToRegExp(matcher).test(changedPath);
    }
    return changedPath === matcher || changedPath.startsWith(`${matcher.replace(/\/$/, '')}/`);
  }) || changedPath.startsWith(productRoot);
}

function addReason(affected, productId, reason) {
  const current = affected.get(productId) ?? new Set();
  current.add(reason);
  affected.set(productId, current);
}

export function loadCanonicalRegistry(repoRoot = defaultRepoRoot) {
  const source = readFileSync(join(repoRoot, 'config/canonical-product-registry.json'), 'utf8');
  return JSON.parse(source).registry;
}

export function resolveAffectedProducts(changedFiles, registry, options = {}) {
  const entries = registryEntries(registry, options);
  const normalizedFiles = changedFiles.map(normalizePath).filter(Boolean);
  const nonDocsFiles = normalizedFiles.filter((changedPath) => !isDocsOnlyPath(changedPath));
  const affected = new Map();

  for (const changedPath of nonDocsFiles) {
    for (const { productId, product } of entries) {
      if (productOwnsPath(productId, product, changedPath)) {
        addReason(affected, productId, `owns:${changedPath}`);
      }
    }

    if (globalImpactFiles.has(changedPath) || hasPrefix(changedPath, globalImpactPrefixes)) {
      for (const { productId } of entries) {
        addReason(affected, productId, `global:${changedPath}`);
      }
      continue;
    }

    if (hasPrefix(changedPath, platformJavaPrefixes)) {
      for (const { productId, product } of entries) {
        if (productHasGradleSurface(product)) {
          addReason(affected, productId, `platform-java:${changedPath}`);
        }
      }
      continue;
    }

    if (hasPrefix(changedPath, uiPlatformPackagePrefixes)) {
      for (const { productId, product } of entries) {
        if (productHasPnpmSurface(product)) {
          addReason(affected, productId, `platform-ui:${changedPath}`);
        }
      }
      continue;
    }

    if (hasPrefix(changedPath, platformTypescriptPrefixes)) {
      for (const { productId, product } of entries) {
        if (productHasPnpmSurface(product)) {
          addReason(affected, productId, `platform-typescript:${changedPath}`);
        }
      }
      continue;
    }

    if (hasPrefix(changedPath, lifecyclePackagePrefixes)) {
      for (const { productId, product } of entries) {
        if (productHasLifecycleEnabled(product)) {
          addReason(affected, productId, `lifecycle-package:${changedPath}`);
        }
      }
      continue;
    }

    if (globalImpactFiles.has(changedPath) || hasPrefix(changedPath, lifecycleConfigPrefixes)) {
      for (const { productId, product } of entries) {
        if (productHasLifecycleEnabled(product)) {
          addReason(affected, productId, `lifecycle-config:${changedPath}`);
        }
      }
      continue;
    }

    if (changedPath.startsWith('config/observability/')) {
      for (const { productId, product } of entries) {
        if (product.conformance?.observability === true) {
          addReason(affected, productId, `observability:${changedPath}`);
        }
      }
    }
  }

  const products = [...affected.entries()]
    .map(([productId, reasons]) => ({ productId, reasons: [...reasons].sort() }))
    .sort((left, right) => left.productId.localeCompare(right.productId));

  return {
    changedFiles: normalizedFiles,
    affectedProducts: products.map((entry) => entry.productId),
    products,
    docsOnly: normalizedFiles.length > 0 && nonDocsFiles.length === 0,
  };
}

function readChangedFilesFromGit(repoRoot, baseRef, headRef) {
  const args = ['diff', '--name-only'];
  if (baseRef && headRef) {
    args.push(`${baseRef}...${headRef}`);
  } else if (baseRef) {
    args.push(baseRef);
  } else {
    args.push('HEAD');
  }

  return execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).split(/\r?\n/).filter(Boolean);
}

function readStdin() {
  return readFileSync(0, 'utf8').split(/\r?\n/).filter(Boolean);
}

function parseArgs(argv) {
  const options = {
    json: false,
    businessProductsOnly: false,
    includeDemo: false,
    stdin: false,
    baseRef: undefined,
    headRef: undefined,
    files: [],
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--json') {
      options.json = true;
      continue;
    }
    if (arg === '--business-products-only') {
      options.businessProductsOnly = true;
      continue;
    }
    if (arg === '--include-demo') {
      options.includeDemo = true;
      continue;
    }
    if (arg === '--stdin') {
      options.stdin = true;
      continue;
    }
    if (arg === '--base') {
      options.baseRef = argv[index + 1];
      index += 1;
      continue;
    }
    if (arg === '--head') {
      options.headRef = argv[index + 1];
      index += 1;
      continue;
    }
    if (arg === '--help') {
      options.help = true;
      continue;
    }
    options.files.push(arg);
  }

  return options;
}

function usage() {
  console.log([
    'Usage: node scripts/resolve-affected-products.mjs [options] [changed-file...]',
    '',
    'Options:',
    '  --json                    Emit structured JSON output',
    '  --business-products-only  Exclude platform providers and shared services',
    '  --include-demo            Include demo/example registry entries',
    '  --stdin                   Read changed files from stdin, one per line',
    '  --base <ref>              Git base ref for diff discovery',
    '  --head <ref>              Git head ref for diff discovery',
  ].join('\n'));
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    usage();
    return;
  }

  const registry = loadCanonicalRegistry(defaultRepoRoot);
  const changedFiles = options.stdin
    ? readStdin()
    : options.files.length > 0
      ? options.files
      : readChangedFilesFromGit(defaultRepoRoot, options.baseRef, options.headRef);
  const result = resolveAffectedProducts(changedFiles, registry, options);

  if (options.json) {
    console.log(JSON.stringify(result, null, 2));
    return;
  }

  if (result.docsOnly) {
    console.log('No affected products: docs-only change set');
    return;
  }

  if (result.affectedProducts.length === 0) {
    console.log('No affected products');
    return;
  }

  console.log(result.affectedProducts.join('\n'));
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  try {
    main();
  } catch (error) {
    console.error(`Affected product resolution failed: ${error.message}`);
    process.exit(1);
  }
}
