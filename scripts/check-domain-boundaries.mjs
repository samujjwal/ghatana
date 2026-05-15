#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const SOURCE_GLOBS = ['*.ts', '*.tsx', '*.js', '*.jsx', '*.mjs', '*.cjs'];
const IGNORED_SEGMENTS = ['/node_modules/', '/dist/', '/build/', '/coverage/', '/__tests__/', '.test.', '.spec.'];

function normalize(filePath) {
  return filePath.replace(/\\/g, '/');
}

function isIgnored(filePath) {
  const normalized = `/${normalize(filePath)}`;
  return IGNORED_SEGMENTS.some((segment) => normalized.includes(segment));
}

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), 'utf8'));
}

function listFiles(rootSegments, globPatterns) {
  try {
    const args = ['--files', ...rootSegments];
    for (const glob of globPatterns) {
      args.push('-g', glob);
    }
    return execFileSync('rg', args, { cwd: repoRoot, encoding: 'utf8' })
      .split(/\r?\n/)
      .filter(Boolean)
      .filter((file) => !isIgnored(file));
  } catch {
    const extensions = new Set(
      globPatterns
        .map((pattern) => pattern.replace('*', ''))
        .filter((pattern) => pattern.startsWith('.')),
    );
    const exactFileNames = new Set(
      globPatterns.filter((pattern) => !pattern.includes('*') && !pattern.startsWith('.')),
    );
    const files = [];
    for (const rootSegment of rootSegments) {
      const absoluteRoot = path.join(repoRoot, rootSegment);
      if (!existsSync(absoluteRoot)) {
        continue;
      }
      walkDirectory(absoluteRoot, extensions, exactFileNames, files);
    }
    return files;
  }
}

function walkDirectory(directory, extensions, exactFileNames, files) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const relativePath = normalize(path.relative(repoRoot, fullPath));
    if (isIgnored(relativePath)) {
      continue;
    }
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      walkDirectory(fullPath, extensions, exactFileNames, files);
      continue;
    }
    if (exactFileNames.has(entry) || extensions.has(path.extname(entry)) || (exactFileNames.size === 0 && extensions.size === 0)) {
      files.push(relativePath);
    }
  }
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

function relativeImportTarget(filePath, specifier) {
  if (!specifier.startsWith('.')) {
    return null;
  }
  return normalize(path.normalize(path.join(path.dirname(filePath), specifier)));
}

function packageNameFromSpecifier(specifier) {
  if (!specifier.startsWith('@')) {
    return specifier.split('/')[0];
  }
  const [scope, name] = specifier.split('/');
  return name ? `${scope}/${name}` : scope;
}

function productFromPath(filePath, productIds) {
  const normalized = normalize(filePath);
  for (const productId of productIds) {
    if (normalized.startsWith(`products/${productId}/`)) {
      return productId;
    }
  }
  return null;
}

export function analyzeBoundaryViolations(files, options) {
  const productIds = options.productIds;
  const productPackageOwners = options.productPackageOwners;
  const domainRegistry = options.domainRegistry || { domains: [] };
  const boundaryExceptions = options.boundaryExceptions || { exceptions: [] };
  const violations = [];

  // Build domain boundary policy lookup
  const domainBoundaryPolicies = new Map();
  for (const domain of domainRegistry.domains || []) {
    if (domain.boundaryPolicy) {
      domainBoundaryPolicies.set(domain.id, domain.boundaryPolicy);
    }
  }

  // Build exception lookup for allowlist support
  const exceptionLookup = new Map();
  for (const exception of boundaryExceptions.exceptions || []) {
    const key = `${exception.sourceFile}:${exception.targetImport || exception.ownershipViolation}`;
    exceptionLookup.set(key, exception);
  }

  for (const file of files) {
    const filePath = normalize(file.path);
    const source = file.source;
    const imports = extractImportSpecifiers(source);
    const fileProduct = productFromPath(filePath, productIds);
    const isPlatformFile = filePath.startsWith('platform/');
    const isSharedTsPackage = filePath.startsWith('platform/typescript/');
    const isKernelFile = filePath.startsWith('platform/typescript/kernel-') || filePath.startsWith('platform-kernel/');

    // Determine which domain this file belongs to (if any)
    let fileDomain = null;
    for (const domain of domainRegistry.domains || []) {
      const primaryLocations = domain.primaryLocations || [];
      const secondaryLocations = domain.secondaryLocations || [];
      const allLocations = [...primaryLocations, ...secondaryLocations];
      
      for (const location of allLocations) {
        if (filePath.startsWith(location.replace(/\/$/, '')) || location.includes(filePath)) {
          fileDomain = domain;
          break;
        }
      }
      if (fileDomain) break;
    }

    for (const specifier of imports) {
      const relativeTarget = relativeImportTarget(filePath, specifier);
      const packageName = packageNameFromSpecifier(specifier);
      const ownedProduct = productPackageOwners.get(packageName);

      // Use domain boundary policies if available
      if (fileDomain && fileDomain.boundaryPolicy) {
        const policy = fileDomain.boundaryPolicy;
        
        // Check mustNotImport patterns with allowlist support
        for (const forbiddenPattern of policy.mustNotImport || []) {
          const pattern = forbiddenPattern.replace(/\*\*/g, '**').replace(/\*/g, '*');
          if (relativeTarget?.match(pattern.replace(/\*/g, '[^/]*')) || specifier.match(pattern.replace(/\*/g, '[^/]*'))) {
            // Check if there's an exception for this import
            const exceptionKey = `${filePath}:${specifier}`;
            const exception = exceptionLookup.get(exceptionKey);
            
            if (!exception) {
              violations.push(`${filePath}: domain '${fileDomain.id}' must not import '${specifier}' (forbidden by boundary policy). Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
            }
          }
        }
      }

      if (isPlatformFile) {
        if (ownedProduct) {
          // Check for exception
          const exceptionKey = `${filePath}:${packageName}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: platform code imports product package '${packageName}' owned by ${ownedProduct}. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
        if (relativeTarget?.startsWith('products/')) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: platform code imports product implementation path '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
      }

      if (isKernelFile) {
        if (relativeTarget?.startsWith('products/yappc/')) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: kernel code imports YAPPC implementation internals via '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
        if (relativeTarget?.startsWith('products/data-cloud/planes/')) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: kernel code imports Data Cloud plane internals via '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
        if (/^products\/yappc\//.test(specifier)) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: kernel code imports YAPPC implementation internals via '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
        if (/^products\/data-cloud\/planes\//.test(specifier)) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: kernel code imports Data Cloud plane internals via '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
      }

      if (isSharedTsPackage) {
        if (ownedProduct) {
          const exceptionKey = `${filePath}:${packageName}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: shared TypeScript package imports product package '${packageName}' owned by ${ownedProduct}. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
        if (relativeTarget?.startsWith('products/')) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: shared TypeScript package imports product implementation path '${specifier}'. Remediation: Remove import or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
      }

      if (fileProduct) {
        if (relativeTarget?.startsWith('platform/') || /^@ghatana\/[^/]+\/src(?:\/|$)/.test(specifier)) {
          const exceptionKey = `${filePath}:${specifier}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: product code bypasses platform public exports via '${specifier}'. Remediation: Use public platform exports or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
      }
    }

    if (/^products\/[^/]+\/.*(?:kernel-product|kernel-lifecycle|run-product-task|lifecycle-runner)\.(?:[cm]?[jt]s|tsx?)$/.test(filePath)) {
      const exceptionKey = `${filePath}:lifecycle-runner`;
      const exception = exceptionLookup.get(exceptionKey);
      if (!exception) {
        violations.push(`${filePath}: product-local lifecycle runner detected; lifecycle execution must remain centralized in kernel tooling. Remediation: Remove lifecycle runner or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
      }
    }

    if (/^platform\/typescript\/[^/]*(digital-marketing|phr|finance|flashit|data-cloud|yappc)[^/]*\//.test(filePath)) {
      const exceptionKey = `${filePath}:product-named-code`;
      const exception = exceptionLookup.get(exceptionKey);
      if (!exception) {
        violations.push(`${filePath}: product-named code must not live under generic platform TypeScript modules. Remediation: Move code to product area or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
      }
    }

    // Validate file ownership against domain boundary policies
    if (fileDomain && fileDomain.boundaryPolicy) {
      const policy = fileDomain.boundaryPolicy;
      
      // Check mustNotOwn patterns
      for (const forbiddenPattern of policy.mustNotOwn || []) {
        if (filePath.match(forbiddenPattern.replace(/\*/g, '[^/]*'))) {
          const exceptionKey = `${filePath}:${forbiddenPattern}`;
          const exception = exceptionLookup.get(exceptionKey);
          if (!exception) {
            violations.push(`${filePath}: domain '${fileDomain.id}' must not own this file (forbidden by boundary policy). Remediation: Move file or add exception to config/domain-boundary-exceptions.json with expiresAt.`);
          }
        }
      }
    }
  }

  return violations;
}

function loadRepoFiles() {
  const files = listFiles(['platform', 'products', 'platform-kernel'], SOURCE_GLOBS);
  return files.map((file) => ({
    path: file,
    source: readFileSync(path.join(repoRoot, file), 'utf8'),
  }));
}

function loadProductIds() {
  return Object.keys(readJson('config/canonical-product-registry.json').registry ?? {});
}

function loadDomainRegistry() {
  try {
    return readJson('config/domain-registry.json');
  } catch (error) {
    console.warn('Warning: Failed to load domain registry:', error.message);
    return { domains: [] };
  }
}

function loadDomainBoundaryExceptions() {
  try {
    const exceptions = readJson('config/domain-boundary-exceptions.json');
    const now = new Date();
    
    // Filter out expired exceptions
    const activeExceptions = (exceptions.exceptions || []).filter((exception) => {
      if (!exception.expiresAt) {
        console.warn(`Warning: Boundary exception missing expiresAt: ${JSON.stringify(exception)}`);
        return false;
      }
      const expiryDate = new Date(exception.expiresAt);
      if (expiryDate < now) {
        console.warn(`Warning: Boundary exception expired on ${exception.expiresAt}: ${exception.id}`);
        return false;
      }
      return true;
    });
    
    return { exceptions: activeExceptions };
  } catch (error) {
    console.warn('Warning: Failed to load domain boundary exceptions:', error.message);
    return { exceptions: [] };
  }
}

function loadProductPackageOwners(productIds) {
  const packageOwners = new Map();
  const packageFiles = listFiles(['products'], ['package.json']);

  for (const packageFile of packageFiles) {
    const productId = productFromPath(packageFile, productIds);
    if (!productId) {
      continue;
    }
    const packageJson = JSON.parse(readFileSync(path.join(repoRoot, packageFile), 'utf8'));
    if (typeof packageJson.name === 'string' && packageJson.name.length > 0) {
      packageOwners.set(packageJson.name, productId);
    }
  }

  return packageOwners;
}

export function checkDomainBoundaries(options = {}) {
  const productIds = options.productIds ?? loadProductIds();
  const files = options.files ?? loadRepoFiles();
  const productPackageOwners = options.productPackageOwners ?? loadProductPackageOwners(productIds);
  const domainRegistry = options.domainRegistry ?? loadDomainRegistry();
  const boundaryExceptions = options.boundaryExceptions ?? loadDomainBoundaryExceptions();
  return analyzeBoundaryViolations(files, { productIds, productPackageOwners, domainRegistry, boundaryExceptions });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const violations = checkDomainBoundaries();

  if (violations.length === 0) {
    console.log('OK: domain boundary checks passed.');
    process.exit(0);
  }

  console.error('FAIL: domain boundary checks found violations:');
  for (const violation of violations) {
    console.error(` - ${violation}`);
  }
  process.exit(1);
}