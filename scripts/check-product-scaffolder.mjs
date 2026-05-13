#!/usr/bin/env node

import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync, cpSync, existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const repoRoot = process.cwd();
const tempRoot = mkdtempSync(path.join(os.tmpdir(), 'ghatana-scaffold-check-'));

function ensureDir(relativePath) {
  mkdirSync(path.join(tempRoot, relativePath), { recursive: true });
}

function write(relativePath, content) {
  writeFileSync(path.join(tempRoot, relativePath), content, 'utf8');
}

try {
  ensureDir('scripts');
  ensureDir('config');
  ensureDir('.github/workflows');
  ensureDir('products');

  cpSync(path.join(repoRoot, 'scripts', 'scaffold-product.mjs'), path.join(tempRoot, 'scripts', 'scaffold-product.mjs'));
  cpSync(path.join(repoRoot, 'config', 'canonical-product-registry.json'), path.join(tempRoot, 'config', 'canonical-product-registry.json'));
  cpSync(path.join(repoRoot, 'config', 'product-shape.json'), path.join(tempRoot, 'config', 'product-shape.json'));
  cpSync(path.join(repoRoot, 'pnpm-workspace.yaml'), path.join(tempRoot, 'pnpm-workspace.yaml'));
  cpSync(path.join(repoRoot, 'settings.gradle.kts'), path.join(tempRoot, 'settings.gradle.kts'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'product-coverage-gates.yml'), path.join(tempRoot, '.github', 'workflows', 'product-coverage-gates.yml'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'api-contract-conformance.yml'), path.join(tempRoot, '.github', 'workflows', 'api-contract-conformance.yml'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'visual-regression.yml'), path.join(tempRoot, '.github', 'workflows', 'visual-regression.yml'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'accessibility.yml'), path.join(tempRoot, '.github', 'workflows', 'accessibility.yml'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'e2e-tests.yml'), path.join(tempRoot, '.github', 'workflows', 'e2e-tests.yml'));
  cpSync(path.join(repoRoot, '.github', 'workflows', 'performance-budgets.yml'), path.join(tempRoot, '.github', 'workflows', 'performance-budgets.yml'));

  const result = spawnSync(
    process.execPath,
    [
      path.join(tempRoot, 'scripts', 'scaffold-product.mjs'),
      '--id', 'sample-kernel-product',
      '--name', 'Sample Kernel Product',
      '--product-code', 'SKP',
      '--domain', 'sample-domain',
      '--ui', 'web',
      '--register-canonical-registry',
      '--register-product-shape',
      '--register-workspace',
      '--register-gradle-settings',
      '--register-ci-matrices',
    ],
    {
      cwd: tempRoot,
      encoding: 'utf8',
    },
  );

  if (result.status !== 0) {
    throw new Error(`Scaffolder execution failed:\n${result.stderr || result.stdout}`);
  }

  const generatedProductRoot = path.join(tempRoot, 'products', 'sample-kernel-product');
  const canonicalRegistry = JSON.parse(readFileSync(path.join(tempRoot, 'config', 'canonical-product-registry.json'), 'utf8'));
  const productShape = JSON.parse(readFileSync(path.join(tempRoot, 'config', 'product-shape.json'), 'utf8'));
  const workspaceSource = readFileSync(path.join(tempRoot, 'pnpm-workspace.yaml'), 'utf8');
  const settingsSource = readFileSync(path.join(tempRoot, 'settings.gradle.kts'), 'utf8');
  const coverageWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'product-coverage-gates.yml'), 'utf8');
  const contractWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'api-contract-conformance.yml'), 'utf8');
  const visualWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'visual-regression.yml'), 'utf8');
  const accessibilityWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'accessibility.yml'), 'utf8');
  const e2eWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'e2e-tests.yml'), 'utf8');
  const performanceWorkflow = readFileSync(path.join(tempRoot, '.github', 'workflows', 'performance-budgets.yml'), 'utf8');
  const webPackage = JSON.parse(readFileSync(path.join(generatedProductRoot, 'client', 'web', 'package.json'), 'utf8'));
  const webTsconfig = readFileSync(path.join(generatedProductRoot, 'client', 'web', 'tsconfig.json'), 'utf8');
  const webViteConfig = readFileSync(path.join(generatedProductRoot, 'client', 'web', 'vite.config.ts'), 'utf8');
  const webVitestConfig = readFileSync(path.join(generatedProductRoot, 'client', 'web', 'vitest.config.ts'), 'utf8');
  const conformanceScript = readFileSync(
    path.join(generatedProductRoot, 'scripts', 'check-sample-kernel-product-conformance.mjs'),
    'utf8',
  );
  const dataAccessFixture = JSON.parse(
    readFileSync(path.join(generatedProductRoot, 'conformance', 'data-access-context.json'), 'utf8'),
  );
  const routeEntitlementFixture = JSON.parse(
    readFileSync(path.join(generatedProductRoot, 'conformance', 'route-entitlements.json'), 'utf8'),
  );
  const idempotencyFixture = JSON.parse(
    readFileSync(path.join(generatedProductRoot, 'conformance', 'idempotency-observations.json'), 'utf8'),
  );
  const observabilityFixture = JSON.parse(
    readFileSync(path.join(generatedProductRoot, 'conformance', 'observability-flow.json'), 'utf8'),
  );
  const generatedConformanceResult = spawnSync(
    process.execPath,
    [path.join(generatedProductRoot, 'scripts', 'check-sample-kernel-product-conformance.mjs')],
    {
      cwd: generatedProductRoot,
      encoding: 'utf8',
    },
  );

  const requiredFiles = [
    'build.gradle.kts',
    'conformance/data-access-context.json',
    'conformance/route-entitlements.json',
    'conformance/idempotency-observations.json',
    'conformance/observability-flow.json',
    'domain-pack-manifest.yaml',
    'docker-compose.local.yml',
    'README.md',
    'docs/00-VISION.md',
    'docs/06-IMPLEMENTATION_PLAN.md',
    'client/web/package.json',
    'client/web/src/App.tsx',
    'client/web/src/main.tsx',
    'client/web/src/App.test.tsx',
    'client/web/src/routeManifest.tsx',
    'client/web/tsconfig.json',
    'client/web/vite.config.ts',
    'client/web/vitest.config.ts',
    'client/web/vitest.setup.ts',
    'client/web/playwright.config.ts',
    'client/web/e2e/visual-regression.spec.ts',
    'client/web/e2e/a11y.spec.ts',
    'client/web/index.html',
    'src/main/java/com/ghatana/samplekernelproduct/kernel/policy/SampleKernelProductBoundaryPolicyStore.java',
    'src/main/java/com/ghatana/samplekernelproduct/kernel/policy/SampleKernelProductComplianceRulePack.java',
    'src/main/java/com/ghatana/samplekernelproduct/kernel/policy/SampleKernelProductPluginBindings.java',
    'src/test/java/com/ghatana/samplekernelproduct/kernel/SampleKernelProductPackContractTest.java',
  ];

  const errors = [];

  for (const file of requiredFiles) {
    if (!existsSync(path.join(generatedProductRoot, file))) {
      errors.push(`missing generated file ${file}`);
    }
  }

  const shapeEntry = productShape.products['sample-kernel-product'];
  const registryEntry = canonicalRegistry.registry['sample-kernel-product'];
  if (!registryEntry) {
    errors.push('config/canonical-product-registry.json missing sample-kernel-product entry');
  } else {
    if (registryEntry.kind !== 'business-product') {
      errors.push(`canonical registry kind expected "business-product" but found ${JSON.stringify(registryEntry.kind)}`);
    }
    if (registryEntry.manifestPath !== 'products/sample-kernel-product/domain-pack-manifest.yaml') {
      errors.push('canonical registry missing scaffolded manifestPath');
    }
    if (registryEntry.buildFile !== 'products/sample-kernel-product/build.gradle.kts') {
      errors.push('canonical registry missing scaffolded buildFile');
    }
    if (!registryEntry.pnpmPackages?.includes('products/sample-kernel-product/client/*')) {
      errors.push('canonical registry missing scaffolded pnpm package registration');
    }
    if (!registryEntry.surfaces?.some((surface) => surface.type === 'web' && surface.packagePath === 'products/sample-kernel-product/client/web/package.json')) {
      errors.push('canonical registry missing scaffolded web surface package path');
    }
    if (registryEntry.conformance?.manifest !== true || registryEntry.conformance?.observability !== true) {
      errors.push('canonical registry missing scaffolded conformance obligations');
    }
  }

  if (!shapeEntry) {
    errors.push('config/product-shape.json missing sample-kernel-product entry');
  } else {
    if (shapeEntry.ui !== true) {
      errors.push('config/product-shape.json ui flag was not registered as true');
    }
    if (shapeEntry.uiMode !== 'web') {
      errors.push(`config/product-shape.json uiMode expected "web" but found ${JSON.stringify(shapeEntry.uiMode)}`);
    }
    const packagePath = 'products/sample-kernel-product/client/web/package.json';
    if (!Array.isArray(shapeEntry.clientPackages) || !shapeEntry.clientPackages.includes(packagePath)) {
      errors.push(`config/product-shape.json clientPackages missing ${packagePath}`);
    }
  }

  if (!workspaceSource.includes('- "products/sample-kernel-product/client/*"')) {
    errors.push('pnpm-workspace.yaml missing scaffolded product workspace registration');
  }

  if (!settingsSource.includes('include(":products:sample-kernel-product")')) {
    errors.push('settings.gradle.kts missing scaffolded product Gradle registration');
  }

  if (!coverageWorkflow.includes("      - 'products/sample-kernel-product/**'")) {
    errors.push('product-coverage-gates.yml missing scaffolded product path trigger');
  }
  if (!coverageWorkflow.includes("taskPrefix: ':products:sample-kernel-product'")) {
    errors.push('product-coverage-gates.yml missing scaffolded product matrix entry');
  }

  if (!contractWorkflow.includes("      - 'products/sample-kernel-product/**'")) {
    errors.push('api-contract-conformance.yml missing scaffolded product path trigger');
  }
  if (!contractWorkflow.includes('./gradlew :products:sample-kernel-product:checkApiContractConformance --no-daemon --stacktrace')) {
    errors.push('api-contract-conformance.yml missing scaffolded product contract command');
  }

  if (!visualWorkflow.includes('product: sample-kernel-product-web')) {
    errors.push('visual-regression.yml missing scaffolded product visual regression matrix entry');
  }
  if (!visualWorkflow.includes('path: products/sample-kernel-product/client/web')) {
    errors.push('visual-regression.yml missing scaffolded product visual regression path');
  }

  if (!accessibilityWorkflow.includes("      - 'products/sample-kernel-product/client/web/**'")) {
    errors.push('accessibility.yml missing scaffolded product path trigger');
  }
  if (!accessibilityWorkflow.includes("package: '@ghatana/sample-kernel-product-web'")) {
    errors.push('accessibility.yml missing scaffolded product accessibility package entry');
  }

  if (!e2eWorkflow.includes('product: sample-kernel-product-web')) {
    errors.push('e2e-tests.yml missing scaffolded product E2E matrix entry');
  }
  if (!e2eWorkflow.includes('path: products/sample-kernel-product/client/web')) {
    errors.push('e2e-tests.yml missing scaffolded product E2E path');
  }

  if (!performanceWorkflow.includes("      - 'products/sample-kernel-product/client/**'")) {
    errors.push('performance-budgets.yml missing scaffolded product path trigger');
  }
  if (!performanceWorkflow.includes('name: sample-kernel-product-web')) {
    errors.push('performance-budgets.yml missing scaffolded product performance matrix entry');
  }
  if (!performanceWorkflow.includes('path: products/sample-kernel-product/client/web')) {
    errors.push('performance-budgets.yml missing scaffolded product performance path');
  }

  const requiredScripts = {
    dev: 'vite',
    lint: 'pnpm exec eslint src --ext .ts,.tsx',
    'type-check': 'tsc --noEmit',
    test: 'vitest run',
    'test:coverage': 'vitest run --coverage',
    'test:e2e': 'playwright test --list',
    'test:e2e:a11y': 'playwright test --grep @a11y --list',
    build: 'tsc --noEmit && vite build',
  };

  for (const [scriptName, scriptValue] of Object.entries(requiredScripts)) {
    if (webPackage.scripts?.[scriptName] !== scriptValue) {
      errors.push(`client/web/package.json missing scaffolded script ${scriptName}=${JSON.stringify(scriptValue)}`);
    }
  }

  const requiredDevDependencies = [
    '@playwright/test',
    '@types/node',
    '@vitejs/plugin-react',
    'typescript',
    'vite',
    'vitest',
  ];
  const requiredDependencies = [
    '@ghatana/product-shell',
    'react',
    'react-dom',
    'react-router-dom',
    'scheduler',
  ];

  for (const dependency of requiredDevDependencies) {
    if (!webPackage.devDependencies?.[dependency]) {
      errors.push(`client/web/package.json missing scaffolded devDependency ${dependency}`);
    }
  }

  for (const dependency of requiredDependencies) {
    if (!webPackage.dependencies?.[dependency]) {
      errors.push(`client/web/package.json missing scaffolded dependency ${dependency}`);
    }
  }

  if (!webTsconfig.includes('"extends": "../../../../tsconfig.base.json"')) {
    errors.push('client/web/tsconfig.json missing shared tsconfig.base.json inheritance');
  }
  if (!webTsconfig.includes('"@ghatana/product-shell": ["../../../../platform/typescript/product-shell/src/index.ts"]')) {
    errors.push('client/web/tsconfig.json missing product-shell workspace path alias');
  }
  if (!webTsconfig.includes('"types": ["vite/client", "node", "react", "react-dom", "vitest/globals"]')) {
    errors.push('client/web/tsconfig.json missing workspace-safe runtime/test types contract');
  }

  if (!webViteConfig.includes('preserveSymlinks: true')) {
    errors.push('client/web/vite.config.ts missing preserveSymlinks resolution');
  }
  if (!webViteConfig.includes('"@ghatana/product-shell"')) {
    errors.push('client/web/vite.config.ts missing product-shell alias');
  }
  if (!webViteConfig.includes('react-router-dom')) {
    errors.push('client/web/vite.config.ts missing router resolution alias');
  }

  if (!webVitestConfig.includes('preserveSymlinks: true')) {
    errors.push('client/web/vitest.config.ts missing preserveSymlinks resolution');
  }
  if (!webVitestConfig.includes('include: ["src/**/*.test.{ts,tsx}"]')) {
    errors.push('client/web/vitest.config.ts missing scoped test include pattern');
  }
  if (!webVitestConfig.includes('"@ghatana/product-shell"')) {
    errors.push('client/web/vitest.config.ts missing product-shell alias');
  }

  if (!conformanceScript.includes('checkDataAccessFixtures()')) {
    errors.push('product conformance script missing data-access fixture validation');
  }
  if (!conformanceScript.includes('checkRouteEntitlementFixtures()')) {
    errors.push('product conformance script missing route entitlement fixture validation');
  }
  if (!conformanceScript.includes('checkIdempotencyFixtures()')) {
    errors.push('product conformance script missing idempotency fixture validation');
  }
  if (!conformanceScript.includes('checkObservabilityFlowFixture()')) {
    errors.push('product conformance script missing observability flow fixture validation');
  }

  if (dataAccessFixture?.[0]?.tenantId !== 'sample-kernel-product-tenant') {
    errors.push('data-access conformance fixture missing scaffolded tenant');
  }
  if (routeEntitlementFixture?.[0]?.product !== 'sample-kernel-product') {
    errors.push('route entitlement conformance fixture missing scaffolded product id');
  }
  if (idempotencyFixture?.[1]?.status !== 'completed' || idempotencyFixture?.[1]?.replayed !== true) {
    errors.push('idempotency conformance fixture missing replay observation');
  }
  if (observabilityFixture?.flows?.[0]?.product !== 'sample-kernel-product') {
    errors.push('observability conformance fixture missing scaffolded product flow');
  }
  if (generatedConformanceResult.status !== 0) {
    errors.push(
      `generated product conformance script failed:\n${generatedConformanceResult.stderr || generatedConformanceResult.stdout}`,
    );
  }

  if (errors.length > 0) {
    throw new Error(`Scaffolder contract check failed:\n- ${errors.join('\n- ')}`);
  }

  console.log('Product scaffolder check passed.');
} finally {
  rmSync(tempRoot, { recursive: true, force: true });
}
