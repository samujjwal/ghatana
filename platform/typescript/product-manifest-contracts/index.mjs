import { readFileSync } from 'node:fs';
import yaml from 'js-yaml';
import { z } from 'zod';

const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const semverPattern = /^\d+\.\d+\.\d+(?:[-+].+)?$/;
const namespacedPolicyPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*:[a-z0-9]+(?:-[a-z0-9]+)*$/;

const stringArraySchema = z.array(z.string().min(1));
const looseRecordSchema = z.record(z.string(), z.unknown());

const capabilitySchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  type: z.string().min(1),
  description: z.string().min(1),
  metadata: looseRecordSchema.optional(),
});

const extensionSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  description: z.string().min(1),
  target_capability: z.string().min(1),
});

const moduleSchema = z.object({
  name: z.string().min(1),
  description: z.string().min(1),
});

export const productManifestSchema = z.object({
  id: z.string().regex(slugPattern),
  version: z.string().regex(semverPattern),
  kernelCapabilitiesConsumed: stringArraySchema,
  policyActions: stringArraySchema,
  policyResources: stringArraySchema,
  pluginsConsumed: stringArraySchema,
  bridgesConsumed: z.array(z.string()),
  domainPacksProvided: stringArraySchema,
  uiSurfaces: z.array(z.enum(['web', 'mobile'])),
  runtimeServices: stringArraySchema,
  dataSensitivity: z.enum([
    'HIGH',
    'MEDIUM',
    'LOW',
    'RESTRICTED',
    'PUBLIC',
    'regulated-health',
    'regulated-finance',
    'marketing-consent',
    'personal-journal',
  ]),
  domain: z.union([z.string().min(1), looseRecordSchema]).optional(),
  capabilities: z.array(capabilitySchema).optional(),
  extensions: z.array(extensionSchema).optional(),
  modules: z.array(moduleSchema).optional(),
  deployment: looseRecordSchema.optional(),
  metadata: looseRecordSchema.optional(),
}).passthrough();

export function loadProductManifest(filePath, format) {
  const content = readFileSync(filePath, 'utf8');
  if (format === 'json') {
    return JSON.parse(content);
  }

  const parsed = yaml.load(content);
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('manifest must parse to an object');
  }

  const pack = parsed.pack;
  if (!pack || typeof pack !== 'object') {
    throw new Error('YAML manifest must contain a top-level pack object');
  }

  return pack;
}

export function parseRegistry(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

export function validateProductManifestShape(manifest) {
  return productManifestSchema.safeParse(manifest);
}

export function expectedSurfacesForShape(shape) {
  if (shape.uiMode === 'backend-only' || shape.ui === false) {
    return [];
  }
  if (shape.uiMode === 'web') {
    return ['web'];
  }
  if (shape.uiMode === 'multi-surface') {
    return ['web', 'mobile'];
  }
  return [];
}

export function validateSurfaceAlignment({ product, manifest, shape }) {
  const violations = [];
  if (!shape) {
    return [`missing product shape declaration for '${product}'`];
  }

  if (!Array.isArray(shape.clientPackages)) {
    violations.push('product-shape.json must declare clientPackages[]');
    return violations;
  }

  const expectedSurfaces = expectedSurfacesForShape(shape);
  const manifestSurfaces = Array.isArray(manifest.uiSurfaces) ? [...manifest.uiSurfaces].sort() : [];
  const expectedSorted = [...expectedSurfaces].sort();

  if (JSON.stringify(manifestSurfaces) !== JSON.stringify(expectedSorted)) {
    violations.push(
      `product-shape.json uiMode "${shape.uiMode}" expects uiSurfaces [${expectedSorted.join(', ')}] but manifest declares [${manifestSurfaces.join(', ')}]`,
    );
  }

  const packageSurfaceNames = new Set(
    shape.clientPackages.map((packagePath) => {
      if (packagePath.includes('/mobile/')) return 'mobile';
      if (packagePath.includes('/web/') || packagePath.endsWith('/ui/package.json')) return 'web';
      return 'unknown';
    }),
  );

  for (const surface of expectedSurfaces) {
    if (!packageSurfaceNames.has(surface)) {
      violations.push(`uiMode "${shape.uiMode}" requires a declared ${surface} client package`);
    }
  }

  for (const packageSurface of packageSurfaceNames) {
    if (packageSurface !== 'unknown' && !expectedSurfaces.includes(packageSurface)) {
      violations.push(`clientPackages declares ${packageSurface} but uiMode "${shape.uiMode}" does not support it`);
    }
  }

  return violations;
}

export function validateRegistryReferences({ product, manifest, registry }) {
  const violations = [];

  for (const capability of manifest.kernelCapabilitiesConsumed ?? []) {
    if (!registry.kernelCapabilities?.[capability]) {
      violations.push(`kernelCapabilitiesConsumed entry '${capability}' is not in the canonical registry`);
    }
  }

  for (const plugin of manifest.pluginsConsumed ?? []) {
    if (!registry.plugins?.[plugin]) {
      violations.push(`pluginsConsumed entry '${plugin}' is not in the canonical registry`);
    }
  }

  for (const domainPack of manifest.domainPacksProvided ?? []) {
    const registered = registry.domainPacks?.[domainPack];
    if (!registered) {
      violations.push(`domainPacksProvided entry '${domainPack}' is not in the canonical registry`);
    } else if (registered.owner && registered.owner !== product) {
      violations.push(`domainPacksProvided entry '${domainPack}' is owned by '${registered.owner}', not '${product}'`);
    }
  }

  return violations;
}

export function validatePolicyVocabulary({ manifest, registry }) {
  const violations = [];
  const canonicalActions = new Set(registry.policyActions?.canonical ?? []);
  const actionNamespaces = new Set(registry.policyActions?.extensionNamespaces ?? []);

  for (const action of manifest.policyActions ?? []) {
    if (canonicalActions.has(action)) {
      continue;
    }
    const namespace = action.includes(':') ? action.split(':')[0] : undefined;
    if (!namespace || !actionNamespaces.has(namespace) || !namespacedPolicyPattern.test(action)) {
      violations.push(
        `policyActions entry '${action}' must be canonical or namespaced as '<product>:<verb>'`,
      );
    }
  }

  return violations;
}

export function validateProductIdentityAlignment({ product, manifest }) {
  const violations = [];

  const manifestId = manifest.id;
  const manifestProduct = manifest.product;

  if (!manifestId) {
    violations.push('manifest must declare an id field');
    return violations;
  }

  if (manifestProduct && manifestProduct !== product) {
    violations.push(
      `manifest declares product '${manifestProduct}' but product registry key is '${product}'; these must match for canonical identity`,
    );
  }

  if (manifestId !== product) {
    violations.push(
      `manifest id '${manifestId}' must match the canonical product registry key '${product}' for identity alignment`,
    );
  }

  return violations;
}

export function validatePolicyResourcesVocabulary({ manifest, registry }) {
  const violations = [];
  const canonicalResources = new Set(registry.policyResources?.canonical ?? []);
  const resourceNamespaces = new Set(registry.policyResources?.extensionNamespaces ?? []);

  for (const resource of manifest.policyResources ?? []) {
    if (canonicalResources.has(resource)) {
      continue;
    }
    const namespace = resource.includes(':') ? resource.split(':')[0] : undefined;
    if (!namespace || !resourceNamespaces.has(namespace) || !namespacedPolicyPattern.test(resource)) {
      violations.push(
        `policyResources entry '${resource}' must be canonical or namespaced as '<product>:<resource>'`,
      );
    }
  }

  return violations;
}
