import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

export const repoRoot = path.resolve(new URL('../..', import.meta.url).pathname);

const STAGES = new Set(['dev', 'validate', 'test', 'build', 'package', 'release']);
const SURFACE_SCRIPT_ALIASES = new Map([
  ['backend-api', 'gateway'],
  ['worker', 'worker'],
  ['web', 'web'],
  ['mobile', 'mobile'],
  ['mobile-ios', 'mobile'],
  ['mobile-android', 'mobile'],
  ['portal', 'portal'],
  ['operator', 'operator'],
  ['sdk', 'sdk'],
]);

const PRODUCT_PROVIDER_CHECKS = {
  'data-cloud': {
    validate: ['check:data-cloud-platform-providers'],
    test: ['check:data-cloud-platform-providers'],
    build: ['check:data-cloud-platform-providers'],
    release: ['check:data-cloud-platform-provider-readiness'],
  },
  yappc: {
    validate: ['check:yappc-product-unit-intent-handoff'],
    test: ['check:yappc-product-unit-intent-handoff'],
    build: ['check:yappc-artifact-intelligence-boundary'],
    release: ['check:yappc-platform-provider-readiness'],
  },
};

export function splitCsv(value) {
  return String(value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

export function parseStage(value) {
  const stage = value || 'validate';
  if (!STAGES.has(stage)) {
    throw new Error(`Unknown check stage "${stage}". Expected one of: ${[...STAGES].join(', ')}`);
  }
  return stage;
}

export function readChangedFiles({ argv = process.argv.slice(2), cwd = repoRoot } = {}) {
  const argValue = (name) => {
    const index = argv.indexOf(name);
    return index >= 0 ? argv[index + 1] : undefined;
  };

  const pathsArg = argValue('--paths');
  if (pathsArg) {
    return splitCsv(pathsArg);
  }

  if (argv.includes('--stdin')) {
    return readFileSync(0, 'utf8')
      .split(/\r?\n/)
      .map((entry) => entry.trim())
      .filter(Boolean);
  }

  const base = argValue('--base') || process.env.GITHUB_BASE_SHA || process.env.GITHUB_EVENT_BEFORE || 'origin/main';
  const head = argValue('--head') || process.env.GITHUB_SHA || 'HEAD';
  const result = spawnSync('git', ['diff', '--name-only', `${base}...${head}`], {
    cwd,
    encoding: 'utf8',
  });

  if (result.status !== 0) {
    const detail = result.stderr ? `\n${result.stderr.trim()}` : '';
    throw new Error(`Could not resolve diff ${base}...${head}. Refusing to run an unsafe empty scoped check.${detail}`);
  }

  return result.stdout.split(/\r?\n/).filter(Boolean);
}

export function commandKey(command) {
  return [command.cmd, ...command.args].join('\0');
}

export function dedupeCommands(commands) {
  const seen = new Set();
  return commands.filter((command) => {
    const key = commandKey(command);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

export function classifyFiles(files) {
  const normalized = files.map((file) => file.replace(/\\/g, '/')).filter(Boolean);
  const nonDocs = normalized.filter((file) => !isDocsPath(file));

  return {
    files: normalized,
    docsOnly: normalized.length > 0 && nonDocs.length === 0,
    hasCode: nonDocs.some((file) => /\.(?:cjs|cts|java|js|jsx|json|kts|mjs|mts|proto|sql|ts|tsx|yaml|yml)$/i.test(file)),
    hasTests: nonDocs.some((file) => /(?:^|\/|\.)(?:__tests__|test|tests|spec|e2e|playwright)(?:\/|\.|-)/i.test(file)),
    hasContracts: nonDocs.some((file) => /openapi|contract|proto|schema|manifest|route|policy/i.test(file)),
    hasRelease: nonDocs.some((file) => /release|deploy|rollback|evidence|kernel-product|lifecycle|promotion/i.test(file)),
    hasSecurity: nonDocs.some((file) => /auth|policy|permission|secret|token|jwt|rbac|abac|pii|privacy|consent/i.test(file)),
    hasExpensive: nonDocs.some((file) => /e2e|playwright|performance|load|durable|testcontainers|integration-tests/i.test(file)),
    hasBuildImpact: nonDocs.some((file) =>
      /\.(?:cjs|cts|java|js|jsx|json|kts|mjs|mts|proto|sql|ts|tsx|yaml|yml)$/i.test(file)
      || /package\.json|pnpm-lock\.yaml|vite|webpack|rollup|gradle|Dockerfile|container|compose/i.test(file)
    ),
  };
}

export function productFiles(files, productId, product = {}) {
  const roots = [
    `products/${productId}/`,
    product.manifestPath,
    product.buildFile,
    ...(product.pnpmPackages ?? []),
    ...(product.surfaces ?? []).flatMap((surface) => [surface.path, surface.packagePath].filter(Boolean)),
  ]
    .filter((entry) => typeof entry === 'string')
    .map((entry) => entry.replace(/\\/g, '/').replace(/\/package\.json$/, '/'));

  return files.filter((file) => {
    const normalized = file.replace(/\\/g, '/');
    return roots.some((root) => normalized === root || normalized.startsWith(root.endsWith('/') ? root : `${root}/`));
  });
}

export function dependenciesForProduct(productId, product, { stage = 'validate', includeDependencies = false } = {}) {
  if (!includeDependencies || stage === 'dev') {
    return [];
  }

  const dependencies = new Set();
  const profile = readFoundationUsageProfile(product);

  if (foundationBlockIsRequired(profile, 'dataCloud')) {
    dependencies.add('data-cloud');
  }

  if (foundationBlockIsRequired(profile, 'aep') || foundationBlockIsRequired(profile, 'agentCore')) {
    dependencies.add('yappc');
  }

  dependencies.delete(productId);
  return [...dependencies].sort();
}

export function buildStagePlan({
  stage,
  files,
  products,
  registry,
  packageJson,
  includeDependencies = false,
  releaseRisk = false,
  expensive = false,
  full = false,
} = {}) {
  const selectedStage = parseStage(stage);
  const packageScripts = packageJson?.scripts ?? {};
  const normalizedFiles = files.map((file) => file.replace(/\\/g, '/')).filter(Boolean);
  const scope = classifyFiles(normalizedFiles);
  const directProducts = products.filter((productId) => registry[productId]);
  const commands = [];
  const productScopes = [];

  if (scope.docsOnly) {
    const docs = docsCheckFiles(normalizedFiles);
    if (docs.length > 0 && selectedStage === 'dev') {
      commands.push({
        label: 'docs format check',
        cmd: 'pnpm',
        args: ['exec', 'prettier', '--check', ...docs],
        stage: selectedStage,
        reason: 'docs-only',
      });
    }
    return { stage: selectedStage, files: normalizedFiles, directProducts, productScopes, commands };
  }

  if (selectedStage === 'dev') {
    if (scope.hasCode) {
      commands.push({
        label: 'affected TypeScript workspace typecheck',
        cmd: 'node',
        args: ['./scripts/run-typescript-workspace-typecheck.js', '--affected', '--paths', normalizedFiles.join(',')],
        stage: selectedStage,
        reason: 'changed-code',
      });
    }
    return { stage: selectedStage, files: normalizedFiles, directProducts, productScopes, commands: dedupeCommands(commands) };
  }

  if (selectedStage === 'validate') {
    commands.push({
      label: 'production readiness changed scan',
      cmd: 'node',
      args: ['./scripts/check-production-readiness.mjs', '--changed-only', '--paths', normalizedFiles.join(',')],
      stage: selectedStage,
      reason: 'changed-production-files',
    });
    commands.push({
      label: 'test authenticity changed scan',
      cmd: 'node',
      args: ['./scripts/check-test-authenticity.mjs', '--changed-only', '--paths', normalizedFiles.join(',')],
      stage: selectedStage,
      reason: 'changed-test-or-source-files',
    });
  }

  for (const productId of directProducts) {
    const product = registry[productId];
    const filesForProduct = productFiles(normalizedFiles, productId, product);
    const productScope = classifyFiles(filesForProduct);
    const dependencyProducts = dependenciesForProduct(productId, product, {
      stage: selectedStage,
      includeDependencies,
    });

    productScopes.push({
      productId,
      files: filesForProduct,
      dependencies: dependencyProducts,
      classification: productScope,
    });

    const productCommand = commandForProductStage({
      stage: selectedStage,
      productId,
      product,
      productFiles: filesForProduct,
      packageScripts,
      releaseRisk,
      expensive,
      full,
    });

    if (productCommand) {
      commands.push(productCommand);
    }

    for (const dependencyId of dependencyProducts) {
      const dependencyCommand = commandForDependency({
        stage: selectedStage,
        productId,
        dependencyId,
      });
      if (dependencyCommand) {
        commands.push(dependencyCommand);
      }
    }
  }

  return {
    stage: selectedStage,
    files: normalizedFiles,
    directProducts,
    productScopes,
    commands: dedupeCommands(commands),
  };
}

function commandForProductStage({
  stage,
  productId,
  product,
  productFiles: changedFiles,
  packageScripts,
  releaseRisk,
  expensive,
  full,
}) {
  const productScope = classifyFiles(changedFiles);

  if (stage === 'validate') {
    if (!full && !releaseRisk && !productScope.hasContracts && !productScope.hasSecurity && !productScope.hasRelease) {
      return undefined;
    }
    return productTaskCommand({ productId, task: 'validate', product, changedFiles, packageScripts });
  }

  if (stage === 'test') {
    if (!full && !expensive && productScope.hasExpensive) {
      return {
        label: `product ${productId} expensive tests requested by changed paths`,
        cmd: 'pnpm',
        args: ['check:release:product', '--', '--products', productId, '--release-risk', '--paths', changedFiles.join(',')],
        stage,
        productId,
        reason: 'expensive-test-path',
      };
    }
    return productTaskCommand({ productId, task: 'test', product, changedFiles, packageScripts });
  }

  if (stage === 'build') {
    if (!full && !productScope.hasBuildImpact) {
      return undefined;
    }
    return productTaskCommand({ productId, task: 'build', product, changedFiles, packageScripts });
  }

  if (stage === 'package') {
    return productTaskCommand({ productId, task: 'package', product, changedFiles, packageScripts });
  }

  if (stage === 'release') {
    if (!releaseRisk && !full && !productScope.hasRelease && !productScope.hasSecurity && !productScope.hasContracts) {
      return undefined;
    }
    return {
      label: `product ${productId} release readiness`,
      cmd: 'pnpm',
      args: ['check:release:product', '--', '--products', productId, '--paths', changedFiles.join(',')],
      stage,
      productId,
      reason: releaseRisk ? 'release-risk' : 'release-relevant-files',
    };
  }

  return undefined;
}

function productTaskCommand({ productId, task, product, changedFiles, packageScripts }) {
  const surfaceScripts = surfaceTaskScripts({ productId, task, product, changedFiles, packageScripts });
  if (surfaceScripts.length > 0) {
    return {
      label: `product ${productId} ${task} (${surfaceScripts.map((script) => script.surface).join(', ')})`,
      cmd: process.platform === 'win32' ? 'cmd' : 'sh',
      args: process.platform === 'win32'
        ? ['/c', surfaceScripts.map((script) => `pnpm ${script.script}`).join(' && ')]
        : ['-c', surfaceScripts.map((script) => `pnpm ${script.script}`).join(' && ')],
      stage: task,
      productId,
      reason: 'affected-surfaces',
    };
  }

  const productScript = `${task}:${productId}`;
  if (packageScripts[productScript]) {
    return {
      label: `product ${productId} ${task}`,
      cmd: 'pnpm',
      args: [productScript],
      stage: task,
      productId,
      reason: 'product-script',
    };
  }

  if (task === 'validate') {
    return undefined;
  }

  return {
    label: `product ${productId} ${task}`,
    cmd: 'pnpm',
    args: ['product', productId, task],
    stage: task,
    productId,
    reason: 'product-task-fallback',
  };
}

function surfaceTaskScripts({ productId, task, product, changedFiles, packageScripts }) {
  const surfaces = product.surfaces ?? [];
  const changedSurfaces = surfaces
    .filter((surface) => {
      const surfaceRoots = [surface.path, surface.packagePath]
        .filter(Boolean)
        .map((entry) => entry.replace(/\\/g, '/').replace(/\/package\.json$/, ''));
      return surfaceRoots.some((root) =>
        changedFiles.some((file) => file === root || file.startsWith(`${root}/`))
      );
    })
    .map((surface) => ({
      surface: SURFACE_SCRIPT_ALIASES.get(surface.type) ?? surface.type,
      script: `${task}:${productId}-${SURFACE_SCRIPT_ALIASES.get(surface.type) ?? surface.type}`,
    }))
    .filter((entry, index, entries) => entries.findIndex((candidate) => candidate.script === entry.script) === index)
    .filter((entry) => packageScripts[entry.script]);

  if (changedSurfaces.length === 0 || changedSurfaces.length === surfaces.length) {
    return [];
  }

  return changedSurfaces;
}

function commandForDependency({ stage, productId, dependencyId }) {
  const scripts = PRODUCT_PROVIDER_CHECKS[dependencyId]?.[stage] ?? PRODUCT_PROVIDER_CHECKS[dependencyId]?.validate;
  if (!scripts?.length) {
    return undefined;
  }

  return {
    label: `dependency ${dependencyId} contract for ${productId}`,
    cmd: 'pnpm',
    args: scripts,
    stage,
    productId: dependencyId,
    dependencyOf: productId,
    reason: 'declared-foundation-dependency',
  };
}

function docsCheckFiles(files) {
  return files.filter((file) => /\.(?:md|mdx|adoc|txt|json|ya?ml)$/i.test(file));
}

function isDocsPath(file) {
  return /^docs\//.test(file)
    || /^products\/[^/]+\/(?:docs\/|README\.md$|CHANGELOG\.md$)/.test(file)
    || /(?:^|\/)(?:README|CHANGELOG|LICENSE)(?:\.[a-z]+)?$/i.test(file)
    || /\.(?:md|mdx|adoc|txt)$/i.test(file);
}

function readFoundationUsageProfile(product) {
  if (!product?.foundationUsageProfile) {
    return '';
  }

  const profilePath = path.join(repoRoot, product.foundationUsageProfile);
  if (!existsSync(profilePath)) {
    return '';
  }

  return readFileSync(profilePath, 'utf8');
}

function foundationBlockIsRequired(profile, key) {
  const match = profile.match(new RegExp(`^\\s{2}${key}:\\s*\\n([\\s\\S]*?)(?=^\\s{2}\\w|^\\S|\\s*$)`, 'm'));
  return Boolean(match?.[1] && /^\s{4}usage:\s*["']?required["']?/m.test(match[1]));
}
