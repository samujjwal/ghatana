#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const defaultRepoRoot = path.resolve(__dirname, '..');
const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete', 'options', 'head']);
const REQUIRED_LEGACY_FIELDS = [
  'path',
  'canonical',
  'methods',
  'deprecated_since',
  'retirement_target',
  'feature_flag',
  'migration_notes',
];

const defaultFiles = {
  actionPlane: ['products', 'data-cloud', 'contracts', 'openapi', 'action-plane.yaml'],
  aep: ['products', 'data-cloud', 'contracts', 'openapi', 'aep.yaml'],
  routeRegistry: ['products', 'data-cloud', 'contracts', 'openapi', 'route-compatibility-registry.yaml'],
  routeSecurityRegistry: [
    'products',
    'data-cloud',
    'delivery',
    'launcher',
    'src',
    'main',
    'java',
    'com',
    'ghatana',
    'datacloud',
    'launcher',
    'http',
    'RouteSecurityRegistry.java',
  ],
};

function sleepMs(durationMs) {
  const signal = new Int32Array(new SharedArrayBuffer(4));
  Atomics.wait(signal, 0, 0, durationMs);
}

function writeJsonWithRetry(targetPath, payload, maxAttempts = 8) {
  mkdirSync(path.dirname(targetPath), { recursive: true });
  const tempPath = `${targetPath}.tmp`;
  let lastError = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      writeFileSync(tempPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
      renameSync(tempPath, targetPath);
      return;
    } catch (error) {
      lastError = error;
      try {
        if (existsSync(tempPath)) {
          unlinkSync(tempPath);
        }
      } catch {
        // Best-effort cleanup only.
      }

      const code = String(error?.code ?? '');
      const retriable = code === 'UNKNOWN' || code === 'EACCES' || code === 'EBUSY' || code === 'EPERM';
      if (!retriable || attempt === maxAttempts) {
        throw error;
      }
      sleepMs(50 * attempt);
    }
  }

  throw lastError;
}

function resolveRepoRootFromArgs(argv = process.argv.slice(2)) {
  const rootIndex = argv.indexOf('--root');
  if (rootIndex >= 0 && argv[rootIndex + 1]) {
    return path.resolve(argv[rootIndex + 1]);
  }

  return defaultRepoRoot;
}

function relative(repoRoot, absolutePath) {
  return path.relative(repoRoot, absolutePath).replace(/\\/g, '/');
}

function loadYamlFile(repoRoot, fileParts, violations) {
  const absolutePath = path.join(repoRoot, ...fileParts);
  if (!existsSync(absolutePath)) {
    violations.push(`Missing required route lifecycle file: ${relative(repoRoot, absolutePath)}`);
    return { absolutePath, content: '', document: null };
  }

  const content = readFileSync(absolutePath, 'utf8');
  try {
    return { absolutePath, content, document: YAML.parse(content) };
  } catch (error) {
    violations.push(`Invalid YAML in ${relative(repoRoot, absolutePath)}: ${error.message}`);
    return { absolutePath, content, document: null };
  }
}

function loadTextFile(repoRoot, fileParts, violations) {
  const absolutePath = path.join(repoRoot, ...fileParts);
  if (!existsSync(absolutePath)) {
    violations.push(`Missing required route lifecycle file: ${relative(repoRoot, absolutePath)}`);
    return { absolutePath, content: '' };
  }

  return {
    absolutePath,
    content: readFileSync(absolutePath, 'utf8'),
  };
}

function collectOperations(spec) {
  const operations = [];
  for (const [apiPath, pathItem] of Object.entries(spec?.paths ?? {})) {
    if (!pathItem || typeof pathItem !== 'object') {
      continue;
    }

    for (const [method, operation] of Object.entries(pathItem)) {
      const normalizedMethod = method.toLowerCase();
      if (!HTTP_METHODS.has(normalizedMethod) || !operation || typeof operation !== 'object') {
        continue;
      }

      operations.push({ path: apiPath, method: normalizedMethod, operation });
    }
  }

  return operations;
}

function normalizeRoutePath(apiPath) {
  return String(apiPath).replace(/\{([^}]+)\}/g, ':$1');
}

function routeKey(method, apiPath) {
  return `${String(method).toUpperCase()} ${normalizeRoutePath(apiPath)}`;
}

function collectRuntimeCompatibilityRoutes(source, actionPlanePathSet) {
  const routes = new Map();
  const routePattern = /route\(map,\s*"([^"]+)",\s*"([^"]+)".*?"data_cloud",\s*"compatibility-only"/;

  for (const line of source.split(/\r?\n/)) {
    const match = line.match(routePattern);
    if (!match) {
      continue;
    }

    const method = match[1].toUpperCase();
    const legacyPath = normalizeRoutePath(match[2]);
    if (!legacyPath.startsWith('/api/v1/')) {
      continue;
    }

    const canonicalPath = `/api/v1/action${legacyPath.slice('/api/v1'.length)}`;
    if (!actionPlanePathSet.has(canonicalPath)) {
      continue;
    }

    routes.set(routeKey(method, legacyPath), {
      method,
      legacyPath,
      canonicalPath,
    });
  }

  return routes;
}

function validateCanonicalActionPlane({ actionPlane, aep, violations }) {
  const actionPaths = Object.keys(actionPlane.document?.paths ?? {});
  const actionOperations = collectOperations(actionPlane.document);
  const infoText = `${actionPlane.document?.info?.title ?? ''}\n${actionPlane.document?.info?.description ?? ''}`;

  if (actionPaths.length === 0) {
    violations.push('Action Plane canonical contract must define at least one path.');
  }

  for (const apiPath of actionPaths) {
    if (!apiPath.startsWith('/api/v1/action/')) {
      violations.push(`Canonical Action Plane contract contains non-action path: ${apiPath}`);
    }
  }

  if (/\bAEP\b/i.test(String(actionPlane.document?.info?.title ?? ''))) {
    violations.push('Canonical Action Plane title must not expose AEP as product language.');
  }

  if (!/canonical action plane routes live under \/api\/v1\/action\/\*/i.test(infoText)) {
    violations.push('Canonical Action Plane contract must document /api/v1/action/* as the canonical namespace.');
  }

  if (!/legacy aep\/root paths are compatibility-only/i.test(infoText)) {
    violations.push('Canonical Action Plane contract must state that legacy AEP/root paths are compatibility-only.');
  }

  for (const { path: apiPath, method, operation } of actionOperations) {
    if (operation['x-ghatana-legacy-status'] !== 'active') {
      violations.push(`${method.toUpperCase()} ${apiPath} must include x-ghatana-legacy-status: active.`);
    }
    if (!operation['x-ghatana-sensitivity']) {
      violations.push(`${method.toUpperCase()} ${apiPath} must include x-ghatana-sensitivity.`);
    }
    if (!operation['x-ghatana-required-access']) {
      violations.push(`${method.toUpperCase()} ${apiPath} must include x-ghatana-required-access.`);
    }
  }

  const aepPaths = new Set(Object.keys(aep.document?.paths ?? {}));
  for (const apiPath of actionPaths) {
    if (aepPaths.has(apiPath)) {
      violations.push(`AEP compatibility contract must not duplicate canonical Action Plane route: ${apiPath}`);
    }
  }

  return { actionPaths, actionOperations };
}

function validateAepCompatibilityContract({ aep, violations }) {
  const aepOperations = collectOperations(aep.document);
  const title = String(aep.document?.info?.title ?? '');
  const description = String(aep.document?.info?.description ?? '');
  const contractText = `${aep.content}\n${title}\n${description}`;

  if (!/deprecated/i.test(title) && !/^#\s*deprecated:/im.test(aep.content)) {
    violations.push('AEP compatibility contract must be explicitly marked deprecated.');
  }

  if (!/AEP Compatibility Note/i.test(description)) {
    violations.push('AEP compatibility contract must include an AEP Compatibility Note.');
  }

  if (!/not a standalone product/i.test(description)) {
    violations.push('AEP compatibility contract must define AEP as non-standalone compatibility/runtime naming.');
  }

  if (!/Data Cloud Action Plane/i.test(contractText)) {
    violations.push('AEP compatibility contract must point customers to Data Cloud Action Plane language.');
  }

  const aepCompatibilityOnlyPaths = Object.keys(aep.document?.paths ?? {})
    .filter((apiPath) => apiPath.startsWith('/api/v1/') && !apiPath.startsWith('/api/v1/action/'))
    .sort();
  const aepDeprecatedOperations = aepOperations
    .filter(({ operation }) => operation.deprecated === true)
    .map(({ path: apiPath, method }) => `${method.toUpperCase()} ${apiPath}`)
    .sort();
  const aepCanonicalLeakPaths = Object.keys(aep.document?.paths ?? {})
    .filter((apiPath) => apiPath.startsWith('/api/v1/action/'))
    .sort();

  if (aepCompatibilityOnlyPaths.length === 0) {
    violations.push('AEP compatibility contract must expose compatibility-only /api/v1/* routes for legacy clients.');
  }

  for (const apiPath of aepCanonicalLeakPaths) {
    violations.push(`AEP compatibility contract must not expose canonical Action Plane route: ${apiPath}`);
  }

  return {
    aepOperations,
    aepCompatibilityOnlyPaths,
    aepDeprecatedOperations,
  };
}

function validateRouteCompatibilityRegistry({ routeRegistry, violations }) {
  const legacyRoutes = Array.isArray(routeRegistry.document?.legacy_routes)
    ? routeRegistry.document.legacy_routes
    : [];
  const canonicalActionRoutes = Array.isArray(routeRegistry.document?.canonical_action_routes)
    ? routeRegistry.document.canonical_action_routes
    : [];
  const duplicateKeys = new Set();
  const seenKeys = new Set();

  if (legacyRoutes.length === 0) {
    violations.push('Route compatibility registry must include legacy_routes entries.');
  }

  legacyRoutes.forEach((entry, index) => {
    const label = `legacy_routes[${index}]`;
    for (const field of REQUIRED_LEGACY_FIELDS) {
      if (entry?.[field] == null || entry?.[field] === '') {
        violations.push(`${label} is missing required lifecycle field: ${field}`);
      }
    }

    const legacyPath = String(entry?.path ?? '');
    const canonicalPath = String(entry?.canonical ?? '');
    if (!legacyPath.startsWith('/api/v1/')) {
      violations.push(`${label} path must be a legacy /api/v1/* route.`);
    }
    if (legacyPath.startsWith('/api/v1/action/')) {
      violations.push(`${label} path must not already be canonical: ${legacyPath}`);
    }
    if (!canonicalPath.startsWith('/api/v1/action/')) {
      violations.push(`${label} canonical route must point to /api/v1/action/*: ${canonicalPath}`);
    }

    if (!Array.isArray(entry?.methods) || entry.methods.length === 0) {
      violations.push(`${label} methods must be a non-empty array.`);
    }

    const migrationNotes = String(entry?.migration_notes ?? '');
    if (canonicalPath && !migrationNotes.includes(canonicalPath.replace(/:([A-Za-z0-9_]+)/g, '{$1}')) && !migrationNotes.includes(canonicalPath)) {
      violations.push(`${label} migration_notes must reference the canonical route.`);
    }

    for (const method of entry?.methods ?? []) {
      const key = `${String(method).toUpperCase()} ${legacyPath}`;
      if (seenKeys.has(key)) {
        duplicateKeys.add(key);
      }
      seenKeys.add(key);
    }
  });

  return {
    legacyRoutes,
    canonicalActionRoutes,
    duplicateRegistryEntryCount: duplicateKeys.size,
  };
}

function validateRuntimeRegistryAlignment({ actionPlane, routeRegistry, routeSecurityRegistry, violations }) {
  const actionPlanePathSet = new Set(Object.keys(actionPlane.document?.paths ?? {}).map(normalizeRoutePath));
  const runtimeRoutes = collectRuntimeCompatibilityRoutes(routeSecurityRegistry.content, actionPlanePathSet);
  const registeredRoutes = new Map();

  for (const entry of routeRegistry.document?.legacy_routes ?? []) {
    for (const method of entry?.methods ?? []) {
      registeredRoutes.set(routeKey(method, entry.path), {
        method: String(method).toUpperCase(),
        legacyPath: normalizeRoutePath(entry.path),
        canonicalPath: normalizeRoutePath(entry.canonical),
      });
    }
  }

  for (const [key, route] of runtimeRoutes) {
    const registered = registeredRoutes.get(key);
    if (!registered) {
      violations.push(`Runtime compatibility route is missing lifecycle registry metadata: ${key}`);
      continue;
    }
    if (registered.canonicalPath !== route.canonicalPath) {
      violations.push(`Runtime compatibility route ${key} maps to ${registered.canonicalPath}, expected ${route.canonicalPath}`);
    }
  }

  for (const [key, registered] of registeredRoutes) {
    if (!runtimeRoutes.has(key)) {
      violations.push(`Lifecycle registry contains stale compatibility route not marked compatibility-only at runtime: ${key}`);
    }
    if (!actionPlanePathSet.has(registered.canonicalPath)) {
      violations.push(`Lifecycle registry canonical route is absent from action-plane.yaml: ${registered.canonicalPath}`);
    }
  }

  return {
    runtimeCompatibilityRouteCount: runtimeRoutes.size,
    registeredRuntimeCompatibilityRouteCount: registeredRoutes.size,
  };
}

export function validateActionPlaneRouteLifecycle(options = {}) {
  const repoRoot = path.resolve(options.repoRoot ?? defaultRepoRoot);
  const evidencePath = options.evidencePath
    ? path.resolve(repoRoot, options.evidencePath)
    : path.join(repoRoot, '.kernel', 'evidence', 'action-plane-route-lifecycle.json');
  const violations = [];

  const actionPlane = loadYamlFile(repoRoot, defaultFiles.actionPlane, violations);
  const aep = loadYamlFile(repoRoot, defaultFiles.aep, violations);
  const routeRegistry = loadYamlFile(repoRoot, defaultFiles.routeRegistry, violations);
  const routeSecurityRegistry = loadTextFile(repoRoot, defaultFiles.routeSecurityRegistry, violations);

  const canonical = actionPlane.document && aep.document
    ? validateCanonicalActionPlane({ actionPlane, aep, violations })
    : { actionPaths: [], actionOperations: [] };
  const compatibility = aep.document
    ? validateAepCompatibilityContract({ aep, violations })
    : { aepOperations: [], aepCompatibilityOnlyPaths: [], aepDeprecatedOperations: [] };
  const registry = routeRegistry.document
    ? validateRouteCompatibilityRegistry({ routeRegistry, violations })
    : { legacyRoutes: [], canonicalActionRoutes: [], duplicateRegistryEntryCount: 0 };
  const runtimeAlignment = actionPlane.document && routeRegistry.document && routeSecurityRegistry.content
    ? validateRuntimeRegistryAlignment({ actionPlane, routeRegistry, routeSecurityRegistry, violations })
    : { runtimeCompatibilityRouteCount: 0, registeredRuntimeCompatibilityRouteCount: 0 };

  if (registry.duplicateRegistryEntryCount > 0) {
    violations.push(`Route compatibility registry contains ${registry.duplicateRegistryEntryCount} duplicate legacy method/path entries.`);
  }

  const report = {
    generatedAt: new Date().toISOString(),
    productId: 'data-cloud',
    area: 'action-plane-route-lifecycle',
    targetScore: 5,
    files: {
      actionPlane: relative(repoRoot, actionPlane.absolutePath),
      aep: relative(repoRoot, aep.absolutePath),
      routeRegistry: relative(repoRoot, routeRegistry.absolutePath),
      routeSecurityRegistry: relative(repoRoot, routeSecurityRegistry.absolutePath),
    },
    classification: {
      canonicalActionPathCount: canonical.actionPaths.length,
      canonicalActionOperationCount: canonical.actionOperations.length,
      aepCompatibilityOnlyPathCount: compatibility.aepCompatibilityOnlyPaths.length,
      aepDeprecatedOperationCount: compatibility.aepDeprecatedOperations.length,
      registeredLegacyRouteCount: registry.legacyRoutes.length,
      duplicateRegistryEntryCount: registry.duplicateRegistryEntryCount,
      runtimeCompatibilityRouteCount: runtimeAlignment.runtimeCompatibilityRouteCount,
      registeredRuntimeCompatibilityRouteCount: runtimeAlignment.registeredRuntimeCompatibilityRouteCount,
      canonicalActionRoutesReservedCount: registry.canonicalActionRoutes.length,
    },
    policy: {
      canonicalProductLanguage: 'Data Cloud Action Plane',
      internalCompatibilityName: 'AEP',
      canonicalRoutePrefix: '/api/v1/action/',
      compatibilityRouteStatus: 'compatibility-only',
      registryRequiredLifecycleFields: REQUIRED_LEGACY_FIELDS,
    },
    summary: {
      passed: violations.length === 0,
      violationCount: violations.length,
      proofAreaCount: 4,
    },
    violations,
  };

  if (options.writeEvidence !== false) {
    writeJsonWithRetry(evidencePath, report);
  }

  return report;
}

function main() {
  const repoRoot = resolveRepoRootFromArgs();
  const report = validateActionPlaneRouteLifecycle({ repoRoot });
  if (!report.summary.passed) {
    console.error('Action Plane route lifecycle checks failed:');
    for (const violation of report.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('Action Plane route lifecycle checks passed.');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
