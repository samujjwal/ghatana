#!/usr/bin/env node

/**
 * Kernel/product dependency boundary validator.
 *
 * Enforces:
 * - platform/** code must not import products/** code or product-scoped packages.
 * - products/** code must not import other products directly.
 * - products/** code must consume platform TypeScript through package exports, not source paths.
 * - Gradle platform modules must not depend on products modules.
 *
 * @doc.type tooling
 * @doc.purpose Enforce Kernel/product dependency direction across TypeScript and Gradle code
 * @doc.layer infrastructure
 */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');

const sourceGlobs = ['*.ts', '*.tsx', '*.js', '*.jsx', '*.mjs', '*.cjs'];
const ignoredPathFragments = [
  '/node_modules/',
  '/dist/',
  '/build/',
  '/coverage/',
  '/generated/',
  '/.turbo/',
  '/.gradle/',
  '/src/test/',
  '/__tests__/',
  '.test.',
  '.spec.',
];

// Contract packages that are safe to import from anywhere
const contractPackages = [
  '@ghatana/kernel-product-contracts',
  '@ghatana/kernel-lifecycle',
  '@ghatana/kernel-artifacts',
  '@ghatana/kernel-deployment',
  '@ghatana/kernel-toolchains',
  '@ghatana/kernel-release',
];

// Contract directories that are safe to import from
const contractDirectories = [
  'platform/typescript/kernel-product-contracts',
  'platform/typescript/kernel-lifecycle',
  'platform/typescript/kernel-artifacts',
  'platform/typescript/kernel-deployment',
  'platform/typescript/kernel-toolchains',
  'platform/typescript/kernel-release',
];

const violations = [];

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function addViolation(file, message) {
  violations.push(`${file}: ${message}`);
}

function normalizePath(filePath) {
  return filePath.replace(/\\/g, '/');
}

function loadRegistryProducts() {
  return Object.keys(readJson('config/canonical-product-registry.json').registry).sort((a, b) => b.length - a.length);
}

function loadStrictProductSet() {
  const registry = readJson('config/canonical-product-registry.json').registry;
  return new Set(
    Object.entries(registry)
      .filter(([, product]) => product.kind === 'business-product')
      .filter(([, product]) => product.metadata?.status === 'active')
      .filter(([, product]) => product.conformance?.manifest === true)
      .map(([productId]) => productId),
  );
}

function productFromPath(relativePath, productIds) {
  const normalized = normalizePath(relativePath);
  for (const productId of productIds) {
    if (normalized.startsWith(`products/${productId}/`)) {
      return productId;
    }
  }
  return null;
}

function isIgnored(relativePath) {
  const normalized = `/${normalizePath(relativePath)}`;
  return ignoredPathFragments.some((fragment) => normalized.includes(fragment));
}

function listSourceFiles() {
  const args = ['--files', 'platform', 'products'];
  for (const glob of sourceGlobs) {
    args.push('-g', glob);
  }
  return execFileSync('rg', args, { cwd: repoRoot, encoding: 'utf8' })
    .split(/\r?\n/)
    .filter(Boolean)
    .filter((file) => !isIgnored(file));
}

function listGradleFiles() {
  return execFileSync('rg', ['--files', 'platform', 'products', '-g', '*.gradle.kts', '-g', 'build.gradle'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
    .split(/\r?\n/)
    .filter(Boolean)
    .filter((file) => !isIgnored(file));
}

function buildProductPackageMap(productIds) {
  const packageMap = new Map();
  const packageFiles = execFileSync('rg', ['--files', 'products', '-g', 'package.json'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
    .split(/\r?\n/)
    .filter(Boolean)
    .filter((file) => !isIgnored(file));

  for (const packageFile of packageFiles) {
    const product = productFromPath(packageFile, productIds);
    if (!product) {
      continue;
    }
    try {
      const packageJson = JSON.parse(readFileSync(path.join(repoRoot, packageFile), 'utf8'));
      if (typeof packageJson.name === 'string' && packageJson.name.length > 0) {
        packageMap.set(packageJson.name, product);
      }
    } catch {
      addViolation(packageFile, 'package.json is not valid JSON');
    }
  }
  return packageMap;
}

function extractImportSpecifiers(source) {
  const specifiers = [];
  const patterns = [
    /\bimport\s+(?:type\s+)?(?:[^'"()]+?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bexport\s+(?:type\s+)?(?:[^'"]+?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ];

  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      specifiers.push(match[1]);
    }
  }

  return specifiers;
}

function packageNameFromSpecifier(specifier) {
  if (!specifier.startsWith('@')) {
    return specifier.split('/')[0];
  }
  const [scope, name] = specifier.split('/');
  return name ? `${scope}/${name}` : scope;
}

function relativeImportTarget(file, specifier) {
  if (!specifier.startsWith('.')) {
    return null;
  }
  return normalizePath(path.normalize(path.join(path.dirname(file), specifier)));
}

function validateSourceImports(productIds, strictProducts, packageMap) {
  for (const file of listSourceFiles()) {
    const source = readFileSync(path.join(repoRoot, file), 'utf8');
    const imports = extractImportSpecifiers(source);
    const fileProduct = productFromPath(file, productIds);
    const isPlatformFile = file.startsWith('platform/');

    for (const specifier of imports) {
      const packageName = packageNameFromSpecifier(specifier);
      const importedProduct = packageMap.get(packageName);
      const relativeTarget = relativeImportTarget(file, specifier);

      // Check if the import is from a contract package
      const isContractPackage = contractPackages.some((contract) => specifier.startsWith(contract));
      const isContractDirectory = contractDirectories.some((dir) => relativeTarget?.startsWith(dir));

      if (isPlatformFile) {
        // Platform code can import contract packages from other platform modules
        if (importedProduct && !isContractPackage) {
          addViolation(file, `platform code imports product package '${packageName}' owned by ${importedProduct}. Remediation: Remove import or use contract package instead.`);
        }
        if (relativeTarget?.startsWith('products/')) {
          addViolation(file, `platform code imports product source path '${specifier}'. Remediation: Remove import or use contract package instead.`);
        }
      }

      if (!fileProduct || !strictProducts.has(fileProduct)) {
        continue;
      }

      if (importedProduct && importedProduct !== fileProduct && !isContractPackage) {
        addViolation(file, `product ${fileProduct} imports product package '${packageName}' owned by ${importedProduct}. Remediation: Remove import or use contract package instead.`);
      }

      const targetProduct = relativeTarget ? productFromPath(relativeTarget, productIds) : null;
      if (targetProduct && targetProduct !== fileProduct && !isContractDirectory) {
        addViolation(file, `product ${fileProduct} imports source from product ${targetProduct} via '${specifier}'. Remediation: Remove import or use contract package instead.`);
      }

      // Allow imports from contract directories even if they're in platform
      const isBypassingPlatform = 
        relativeTarget?.startsWith('platform/') ||
        specifier.startsWith('platform/') ||
        /^@ghatana\/[^/]+\/src(?:\/|$)/.test(specifier);

      if (isBypassingPlatform && !isContractDirectory && !isContractPackage) {
        addViolation(file, `product ${fileProduct} bypasses platform public package exports via '${specifier}'. Remediation: Use public platform exports or contract packages instead.`);
      }
    }
  }
}

function gradleModuleFromPath(file) {
  const directory = path.dirname(file);
  return `:${directory.replace(/\//g, ':')}`;
}

function validateGradleBoundaries(productIds, strictProducts) {
  const productModules = new Map();
  const registry = JSON.parse(readFileSync(registryPath, 'utf8')).registry;
  for (const [productId, product] of Object.entries(registry)) {
    for (const modulePath of product.gradleModules ?? []) {
      productModules.set(modulePath, productId);
    }
  }

  const dependencyPattern = /\b(?:api|implementation|compileOnly|runtimeOnly|testImplementation)\s*\(\s*project\("([^"]+)"\)\s*\)/g;

  for (const file of listGradleFiles()) {
    const source = readFileSync(path.join(repoRoot, file), 'utf8');
    const modulePath = gradleModuleFromPath(file);
    const fileProduct = productFromPath(file, productIds);
    const isPlatformFile = file.startsWith('platform/');

    for (const match of source.matchAll(dependencyPattern)) {
      const dependency = match[1];
      const dependencyProduct = productModules.get(dependency);

      if (isPlatformFile && dependencyProduct) {
        addViolation(file, `platform Gradle module ${modulePath} depends on product module ${dependency}`);
      }

      if (
        fileProduct &&
        strictProducts.has(fileProduct) &&
        dependencyProduct &&
        dependencyProduct !== fileProduct
      ) {
        addViolation(file, `product ${fileProduct} Gradle module depends on product ${dependencyProduct} module ${dependency}`);
      }
    }
  }
}

function runFixtureAssertions(productIds, packageMap) {
  const fixtureMap = new Map(packageMap);
  fixtureMap.set('@flashit/shared', 'flashit');
  fixtureMap.set('@yappc/core', 'yappc');

  const fixtureSource = `
    import { shared } from '@flashit/shared';
    import { direct } from '../../../../platform/typescript/product-shell/src/types';
    import { ok } from '@ghatana/product-shell';
  `;
  const imports = extractImportSpecifiers(fixtureSource);
  if (!imports.includes('@flashit/shared') || !imports.includes('@ghatana/product-shell')) {
    addViolation('scripts/check-kernel-boundaries.mjs', 'fixture failed: import parser missed package specifiers');
  }
  if (packageNameFromSpecifier('@ghatana/product-shell/src/types') !== '@ghatana/product-shell') {
    addViolation('scripts/check-kernel-boundaries.mjs', 'fixture failed: package name parser missed scoped package');
  }
  if (productFromPath('products/digital-marketing/ui/src/main.ts', productIds) !== 'digital-marketing') {
    addViolation('scripts/check-kernel-boundaries.mjs', 'fixture failed: product path parser missed hyphenated product');
  }
}

function main() {
  const productIds = loadRegistryProducts();
  const strictProducts = loadStrictProductSet();
  const packageMap = buildProductPackageMap(productIds);

  runFixtureAssertions(productIds, packageMap);
  validateSourceImports(productIds, strictProducts, packageMap);
  validateGradleBoundaries(productIds, strictProducts);

  if (violations.length > 0) {
    console.error(`Kernel boundary check failed with ${violations.length} violation(s):\n`);
    for (const violation of violations.slice(0, 80)) {
      console.error(`- ${violation}`);
    }
    if (violations.length > 80) {
      console.error(`... and ${violations.length - 80} more`);
    }
    process.exit(1);
  }

  console.log(
    `Kernel boundary check passed (${strictProducts.size} strict business products, ${productIds.length} registry entries, ${packageMap.size} product packages).`,
  );
}

main();
