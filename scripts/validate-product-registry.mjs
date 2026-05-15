#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function issue(productId, field, message, remediation) {
  return { productId, field, message, remediation };
}

function pathExists(root, relativePath) {
  return existsSync(path.join(root, relativePath));
}

function readYamlFile(root, relativePath) {
  return YAML.parse(readFileSync(path.join(root, relativePath), 'utf8'));
}

function gradleModuleToRelativePath(modulePath) {
  return modulePath.replace(/^:/, '').replaceAll(':', '/');
}

function hasGradleBuildFile(root, modulePath) {
  const relativeDirectory = gradleModuleToRelativePath(modulePath);
  return (
    existsSync(path.join(root, relativeDirectory, 'build.gradle.kts')) ||
    existsSync(path.join(root, relativeDirectory, 'build.gradle'))
  );
}

function globToRegExp(pattern) {
  const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped.replace(/\*/g, '[^/]+')}$`);
}

function listPackageJsonDirectories(root, current = root, results = []) {
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist' || entry.name === 'build') {
      continue;
    }

    const absolutePath = path.join(current, entry.name);
    if (entry.isDirectory()) {
      listPackageJsonDirectories(root, absolutePath, results);
      continue;
    }

    if (entry.isFile() && entry.name === 'package.json') {
      results.push(path.relative(root, absolutePath).replaceAll('\\', '/').replace(/\/package\.json$/, ''));
    }
  }

  return results;
}

function hasWorkspacePackageMatch(root, packagePattern, cachedPackageDirectories) {
  if (!packagePattern.includes('*')) {
    return existsSync(path.join(root, packagePattern, 'package.json')) || existsSync(path.join(root, packagePattern));
  }

  const matcher = globToRegExp(packagePattern);
  return cachedPackageDirectories.some((packageDirectory) => matcher.test(packageDirectory));
}

export function validateProductRegistryDocument(registry, options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const yamlReader = options.yamlReader ?? ((relativePath) => readYamlFile(root, relativePath));
  const exists = options.pathExists ?? ((relativePath) => pathExists(root, relativePath));
  const runArtifactCheck = options.runArtifactCheck ?? (() => execFileSync(process.execPath, [path.join(root, 'scripts/generate-product-registry-artifacts.mjs'), '--check'], { cwd: root, encoding: 'utf8' }));
  const packageDirectories = options.packageDirectories ?? listPackageJsonDirectories(root);
  const issues = [];

  const entries = Object.entries(registry.registry ?? {});
  for (const [productId, product] of entries) {
    if (typeof product.id !== 'string' || product.id !== productId) {
      issues.push(issue(productId, 'id', 'registry key and product.id must match', `Set '${productId}.id' to '${productId}'.`));
    }

    if (!product.metadata || typeof product.metadata.owner !== 'string' || product.metadata.owner.trim().length === 0) {
      issues.push(issue(productId, 'metadata.owner', 'owner is required', 'Set metadata.owner for the product.'));
    }
    if (!product.metadata || typeof product.metadata.status !== 'string' || product.metadata.status.trim().length === 0) {
      issues.push(issue(productId, 'metadata.status', 'status is required', 'Set metadata.status for the product.'));
    }

    for (const modulePath of product.gradleModules ?? []) {
      if (!hasGradleBuildFile(root, modulePath)) {
        issues.push(issue(productId, 'gradleModules', `module '${modulePath}' does not resolve to a Gradle build file`, `Create the module directory for '${modulePath}' or remove it from the registry.`));
      }
    }

    for (const packagePattern of product.pnpmPackages ?? []) {
      if (!hasWorkspacePackageMatch(root, packagePattern, packageDirectories)) {
        issues.push(issue(productId, 'pnpmPackages', `package pattern '${packagePattern}' does not resolve to any workspace package`, `Create a package.json under '${packagePattern}' or remove the stale package pattern from the registry.`));
      }
    }

    const lifecycleEnabled = product.lifecycle?.enabled === true;
    const lifecycleStatus = product.lifecycleStatus;

    if (lifecycleEnabled && lifecycleStatus !== 'enabled') {
      issues.push(issue(productId, 'lifecycle', 'lifecycle.enabled=true requires lifecycleStatus=enabled', 'Set lifecycleStatus to enabled or disable lifecycle.enabled.'));
    }

    if (lifecycleStatus === 'enabled') {
      if (typeof product.lifecycleConfigPath !== 'string' || product.lifecycleConfigPath.trim().length === 0) {
        issues.push(issue(productId, 'lifecycleConfigPath', 'enabled lifecycle requires lifecycleConfigPath', 'Set lifecycleConfigPath for enabled lifecycle products.'));
      } else if (!exists(product.lifecycleConfigPath)) {
        issues.push(issue(productId, 'lifecycleConfigPath', `lifecycle config '${product.lifecycleConfigPath}' does not exist`, `Create '${product.lifecycleConfigPath}' or disable lifecycle execution.`));
      }
    }

    if (product.kind === 'platform-provider') {
      const requiredGates = product.lifecycleReadiness?.requiredGates ?? [];
      if (!Array.isArray(requiredGates) || requiredGates.length === 0) {
        issues.push(issue(productId, 'lifecycleReadiness.requiredGates', 'platform-provider products require readiness gates', 'Declare lifecycleReadiness.requiredGates for platform-provider products.'));
      }
    }

    if ((productId === 'data-cloud' || productId === 'yappc') && (lifecycleEnabled || lifecycleStatus === 'enabled')) {
      issues.push(issue(productId, 'lifecycle', 'Data Cloud and YAPPC must not be treated as ordinary lifecycle-enabled products', 'Keep these products fail-closed until platform-provider bootstrap/provider rules are implemented.'));
    }

    if (product.conformance?.bridge === true) {
      const bridgeAdapters = product.conformance.bridgeAdapters ?? [];
      if (!Array.isArray(bridgeAdapters) || bridgeAdapters.length === 0) {
        issues.push(issue(productId, 'conformance.bridgeAdapters', 'bridge-enabled products must declare bridge adapter contracts', 'Add bridgeAdapters entries for each bridge implementation.'));
      }
      for (const adapter of bridgeAdapters) {
        if (!exists(adapter.file)) {
          issues.push(issue(productId, 'conformance.bridgeAdapters.file', `bridge adapter file '${adapter.file}' does not exist`, `Create '${adapter.file}' or remove the bridge adapter entry.`));
        }
        for (const testFile of adapter.tests ?? []) {
          if (!exists(testFile.file)) {
            issues.push(issue(productId, 'conformance.bridgeAdapters.tests', `bridge adapter test file '${testFile.file}' does not exist`, `Create '${testFile.file}' or remove the missing bridge test reference.`));
          }
        }
      }
    }

    if (typeof product.lifecycleConfigPath === 'string' && product.lifecycleConfigPath.trim().length > 0 && exists(product.lifecycleConfigPath)) {
      const manifest = yamlReader(product.lifecycleConfigPath);
      if (!lifecycleEnabled && lifecycleStatus !== 'enabled') {
        if (manifest.executionEnabled !== false) {
          issues.push(issue(productId, 'kernel-product.yaml', 'disabled or planned lifecycle products must set executionEnabled: false', `Set executionEnabled: false in ${product.lifecycleConfigPath}.`));
        }
      }
      const requiredGates = product.lifecycleReadiness?.requiredGates ?? [];
      if (requiredGates.length > 0) {
        const manifestRequiredGates = new Set(manifest.readiness?.requiredGates ?? []);
        for (const gate of requiredGates) {
          if (!manifestRequiredGates.has(gate)) {
            issues.push(issue(productId, 'kernel-product.yaml', `missing readiness gate '${gate}' in ${product.lifecycleConfigPath}`, `Mirror registry lifecycleReadiness.requiredGates in ${product.lifecycleConfigPath}.`));
          }
        }
      }
    }
  }

  try {
    runArtifactCheck();
  } catch (error) {
    const details = error instanceof Error ? error.message : String(error);
    issues.push(issue('<artifacts>', 'generated-artifacts', 'generated product registry artifacts are stale or non-deterministic', `Run 'node scripts/generate-product-registry-artifacts.mjs' and commit the resulting generated files. Details: ${details}`));
  }

  return issues;
}

export function validateProductRegistryFiles(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const registry = options.registry ?? readJson(path.join(root, 'config/canonical-product-registry.json'));
  return validateProductRegistryDocument(registry, { ...options, repoRoot: root });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const issues = validateProductRegistryFiles();

  if (issues.length === 0) {
    console.log('OK: product registry validation passed.');
    process.exit(0);
  }

  console.error('FAIL: product registry validation found issues:');
  for (const problem of issues) {
    console.error(` - [${problem.productId}] ${problem.field}: ${problem.message}. Remediation: ${problem.remediation}`);
  }
  process.exit(1);
}