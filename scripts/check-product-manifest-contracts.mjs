#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  loadProductManifest,
  parseRegistry,
  validatePolicyResourcesVocabulary,
  validatePolicyVocabulary,
  validateProductIdentityAlignment,
  validateProductManifestShape,
  validateRegistryReferences,
  validateSurfaceAlignment,
} from '../platform/typescript/product-manifest-contracts/index.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const productShape = JSON.parse(
  readFileSync(path.join(repoRoot, 'config/product-shape.json'), 'utf8'),
);
const capabilityRegistry = parseRegistry(
  path.join(repoRoot, 'config/kernel-product-capability-registry.json'),
);

// Load manifests from canonical product registry instead of hardcoded array
function loadManifestsFromRegistry() {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    console.warn('Canonical product registry not found, using fallback');
    return [];
  }
  
  const registry = JSON.parse(readFileSync(registryPath, 'utf8'));
  const manifests = [];
  
  for (const [productId, product] of Object.entries(registry.registry)) {
    // Only include products that have manifest paths
    if (product.manifestPath && product.manifestFormat) {
      const entry = {
        product: productId,
        file: product.manifestPath,
        format: product.manifestFormat,
        buildFile: product.buildFile || null,
      };
      
      // If no explicit build file, try to infer from first gradle module
      if (!entry.buildFile && product.gradleModules && product.gradleModules.length > 0) {
        const firstModule = product.gradleModules[0];
        entry.buildFile = firstModule.replace(':', '').replace(/:/g, '/') + '/build.gradle.kts';
      }
      
      manifests.push(entry);
    }
  }
  
  return manifests;
}

const MANIFESTS = loadManifestsFromRegistry();

const violations = [];

function addViolation(file, message) {
  violations.push({ file, message });
}

function normalizeManifest(entry) {
  const absolutePath = path.join(repoRoot, entry.file);
  if (!existsSync(absolutePath)) {
    addViolation(entry.file, 'manifest file is missing');
    return null;
  }

  try {
    return loadProductManifest(absolutePath, entry.format);
  } catch (error) {
    addViolation(entry.file, `manifest parsing failed: ${error.message}`);
    return null;
  }
}

function validateManifest(entry, manifest) {
  const parsed = validateProductManifestShape(manifest);
  if (!parsed.success) {
    for (const issue of parsed.error.issues) {
      addViolation(entry.file, `schema violation at ${issue.path.join('.') || '<root>'}: ${issue.message}`);
    }
    return;
  }

  const shape = productShape.products[entry.product];
  for (const violation of validateSurfaceAlignment({
    product: entry.product,
    manifest,
    shape,
  })) {
    addViolation(entry.file, violation);
  }

  for (const violation of validateProductIdentityAlignment({
    product: entry.product,
    manifest,
  })) {
    addViolation(entry.file, violation);
  }

  validateClientPackageFiles(entry, shape);
  validatePluginOwnership(entry, manifest);

  for (const violation of validateRegistryReferences({
    product: entry.product,
    manifest,
    registry: capabilityRegistry,
  })) {
    addViolation(entry.file, violation);
  }

  for (const violation of validatePolicyVocabulary({
    manifest,
    registry: capabilityRegistry,
  })) {
    addViolation(entry.file, violation);
  }

  for (const violation of validatePolicyResourcesVocabulary({
    manifest,
    registry: capabilityRegistry,
  })) {
    addViolation(entry.file, violation);
  }
}

function validateClientPackageFiles(entry, shape) {
  if (!shape || !Array.isArray(shape.clientPackages)) {
    return;
  }

  if (shape.ui) {
    if (shape.clientPackages.length === 0) {
      addViolation(entry.file, 'UI-enabled product must declare at least one client package');
    }

    for (const packageJson of shape.clientPackages) {
      if (!existsSync(path.join(repoRoot, packageJson))) {
        addViolation(entry.file, `declared UI package does not exist: ${packageJson}`);
      }
    }
    return;
  }

  if (shape.clientPackages.length > 0) {
    addViolation(entry.file, 'backend-only product must not declare client packages in product-shape.json');
  }

  const declaration = shape.backendOnlyDeclaration;
  if (!declaration?.file || !declaration?.mustContain) {
    addViolation(entry.file, 'backend-only product must declare backendOnlyDeclaration in product-shape.json');
    return;
  }

  const declarationPath = path.join(repoRoot, declaration.file);
  if (!existsSync(declarationPath)) {
    addViolation(entry.file, `backendOnlyDeclaration file does not exist: ${declaration.file}`);
    return;
  }

  const declarationText = readFileSync(declarationPath, 'utf8').toLowerCase();
  if (!declarationText.includes(String(declaration.mustContain).toLowerCase())) {
    addViolation(declaration.file, `backend-only declaration must contain '${declaration.mustContain}'`);
  }
}

function validatePluginOwnership(entry, manifest) {
  const buildPath = path.join(repoRoot, entry.buildFile);
  if (!existsSync(buildPath)) {
    addViolation(entry.file, `declared build file does not exist: ${entry.buildFile}`);
    return;
  }

  const buildText = readFileSync(buildPath, 'utf8');
  const pluginDependencyPattern =
    /^\s*(?:api|implementation)\(project\(":platform-plugins:(plugin-[^"]+)"\)\)/gm;

  const declaredPlugins = new Set((manifest.pluginsConsumed ?? []).map(String));
  const buildPlugins = new Set();

  for (const match of buildText.matchAll(pluginDependencyPattern)) {
    buildPlugins.add(match[1]);
  }

  for (const plugin of buildPlugins) {
    if (!declaredPlugins.has(plugin)) {
      addViolation(
        entry.buildFile,
        `build declares platform plugin '${plugin}' but manifest pluginsConsumed does not`,
      );
    }
  }

  for (const plugin of declaredPlugins) {
    if (!buildPlugins.has(plugin)) {
      addViolation(
        entry.buildFile,
        `manifest declares platform plugin '${plugin}' but build does not depend on it`,
      );
    }
  }

  // Load product-specific validation rules from canonical registry
  validateProductSpecificRules(entry, manifest, buildText);
}

function validateProductSpecificRules(entry, manifest, buildText) {
  const registryPath = path.join(repoRoot, 'config/canonical-product-registry.json');
  if (!existsSync(registryPath)) {
    return;
  }
  
  const registry = JSON.parse(readFileSync(registryPath, 'utf8'));
  const productConfig = registry.registry[entry.product];
  
  if (!productConfig || !productConfig.validationRules) {
    return;
  }
  
  // Execute product-specific validation rules declaratively
  for (const rule of productConfig.validationRules) {
    if (rule.type === 'domain-dependency-scope') {
      validateDomainDependencyScopeRule(entry, buildText, rule);
    }
  }
}

function validateDomainDependencyScopeRule(entry, buildText, rule) {
  const domainPattern = rule.domainPattern || `:(products:${entry.product}:domains:[^"]+)`;
  const compileScopedPattern = new RegExp(`^\\s*(?:api|implementation)\\(project\\("${domainPattern}"\\)\\)`, 'gm');
  const runtimeScopedPattern = new RegExp(`^\\s*runtimeOnly\\(project\\("${domainPattern}"\\)\\)`, 'gm');

  const compileScopedDomains = [...buildText.matchAll(compileScopedPattern)].map((match) => match[1]);
  const runtimeScopedDomains = [...buildText.matchAll(runtimeScopedPattern)].map((match) => match[1]);

  if (rule.requireRuntimeOnly && compileScopedDomains.length > 0) {
    addViolation(
      entry.buildFile,
      `${entry.product} root must not compile-link domain modules; found compile-scoped dependencies: ${compileScopedDomains.join(', ')}`,
    );
  }

  if (rule.requireRuntimeOnly && runtimeScopedDomains.length === 0) {
    addViolation(
      entry.buildFile,
      `${entry.product} root must compose domain modules via runtimeOnly dependencies at the composition boundary`,
    );
  }
}

function runNegativeFixtureAssertions() {
  const webShape = {
    ui: true,
    uiMode: 'web',
    clientPackages: ['products/example/apps/web/package.json'],
  };
  const mismatch = validateSurfaceAlignment({
    product: 'example',
    manifest: { uiSurfaces: ['web', 'mobile'] },
    shape: webShape,
  });
  if (mismatch.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: uiMode "web" plus uiSurfaces ["web","mobile"] must fail',
    );
  }

  const invalidNestedCapability = validateProductManifestShape({
    id: 'fixture',
    version: '1.0.0',
    kernelCapabilitiesConsumed: ['audit-trail'],
    policyActions: ['read'],
    policyResources: ['records'],
    pluginsConsumed: ['plugin-audit-trail'],
    bridgesConsumed: [],
    domainPacksProvided: ['phr-healthcare-boundary-policy'],
    uiSurfaces: ['web'],
    runtimeServices: ['api'],
    dataSensitivity: 'regulated-health',
    capabilities: [{ id: 'fixture.capability', name: 'Missing metadata' }],
  });
  if (invalidNestedCapability.success) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: missing nested capability type/description must fail',
    );
  }

  const badPolicy = validatePolicyVocabulary({
    manifest: { policyActions: ['settle'] },
    registry: capabilityRegistry,
  });
  if (badPolicy.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: unprefixed product-specific policy action must fail',
    );
  }

  const mismatchedId = validateProductIdentityAlignment({
    product: 'flashit',
    manifest: { id: 'flashit-domain-pack', product: 'flashit' },
  });
  if (mismatchedId.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: manifest id must match product registry key',
    );
  }

  const mismatchedProduct = validateProductIdentityAlignment({
    product: 'flashit',
    manifest: { id: 'flashit', product: 'other-product' },
  });
  if (mismatchedProduct.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: manifest product must match product registry key',
    );
  }

  const missingId = validateProductIdentityAlignment({
    product: 'flashit',
    manifest: { product: 'flashit' },
  });
  if (missingId.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: manifest must declare an id field',
    );
  }

  const badResource = validatePolicyResourcesVocabulary({
    manifest: { policyResources: ['unprefixed-resource'] },
    registry: capabilityRegistry,
  });
  if (badResource.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: unprefixed product-specific policy resource must fail',
    );
  }

  const invalidNamespaceResource = validatePolicyResourcesVocabulary({
    manifest: { policyResources: ['invalid:resource'] },
    registry: capabilityRegistry,
  });
  if (invalidNamespaceResource.length === 0) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      'negative fixture failed: policy resource with invalid namespace must fail',
    );
  }

  // Scaffolder golden test: validate that a scaffolded manifest would pass schema validation
  const scaffoldedManifest = {
    id: 'test-scaffold',
    version: '0.1.0',
    product: 'test-scaffold',
    domain: 'test-domain',
    rulePrefix: 'TEST-BP-',
    kernelCapabilitiesConsumed: ['boundary-policy-evaluation', 'audit-trail', 'tenant-context'],
    policyActions: ['read', 'write', 'delete'],
    policyResources: ['test-scaffold:core'],
    pluginsConsumed: ['plugin-audit-trail', 'plugin-compliance'],
    bridgesConsumed: [],
    domainPacksProvided: ['test-scaffold-boundary-policy', 'test-scaffold-compliance-rule-pack'],
    uiSurfaces: ['web'],
    runtimeServices: ['launcher'],
    dataSensitivity: 'LOW',
  };
  const scaffoldedValidation = validateProductManifestShape(scaffoldedManifest);
  if (!scaffoldedValidation.success) {
    addViolation(
      'scripts/check-product-manifest-contracts.mjs',
      `scaffolder golden test failed: generated manifest must pass schema validation - ${scaffoldedValidation.error.issues.map(i => i.message).join(', ')}`,
    );
  }
}

for (const entry of MANIFESTS) {
  const manifest = normalizeManifest(entry);
  if (!manifest) {
    continue;
  }
  validateManifest(entry, manifest);
}

runNegativeFixtureAssertions();

if (violations.length > 0) {
  console.error(`Product manifest contract check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation.file}: ${violation.message}`);
  }
  process.exit(1);
}

console.log('Product manifest contract check passed (schema + registry + surface alignment).');
