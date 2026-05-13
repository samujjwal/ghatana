const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const packagesRoot = path.join(repoRoot, 'platform', 'typescript');
const workspacePolicyPath = path.join(repoRoot, 'config', 'workspace-dependency-policy.json');
const rootPackagePath = path.join(repoRoot, 'package.json');
const pnpmWorkspacePath = path.join(repoRoot, 'pnpm-workspace.yaml');

const packageLayers = {
  '@ghatana/platform-utils': 0,
  '@ghatana/api': 1,
  '@ghatana/realtime': 1,
  '@ghatana/sso-client': 1,
  '@ghatana/accessibility-audit': 1,
  '@ghatana/i18n': 1,
  '@ghatana/tokens': 1,
  '@ghatana/theme': 2,
  '@ghatana/design-system': 3,
  '@ghatana/charts': 4,
  '@ghatana/canvas': 4,
  '@ghatana/platform-shell': 4,
  '@ghatana/ui-integration': 5,
};

function findPackageJsonFiles(dirPath) {
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === 'build') {
      continue;
    }

    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      files.push(...findPackageJsonFiles(fullPath));
      continue;
    }

    if (entry.isFile() && entry.name === 'package.json') {
      files.push(fullPath);
    }
  }

  return files;
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function loadWorkspaceDependencyPolicy() {
  return readJson(workspacePolicyPath);
}

function collectInternalDeps(pkg) {
  return [
    ...Object.keys(pkg.dependencies || {}),
    ...Object.keys(pkg.peerDependencies || {}),
    ...Object.keys(pkg.devDependencies || {}),
  ].filter((name, index, values) => values.indexOf(name) === index && name.startsWith('@ghatana/'));
}

function checkReadmeContract(filePath, pkg, violations) {
  if (!Array.isArray(pkg.files) || !pkg.files.includes('README.md')) {
    return;
  }

  const readmePath = path.join(path.dirname(filePath), 'README.md');
  if (!fs.existsSync(readmePath)) {
    violations.push(`${path.relative(repoRoot, filePath)} advertises README.md in files but the package directory has no README.md`);
  }
}

function checkDependencyDirection(filePath, pkg, violations) {
  const sourceLayer = packageLayers[pkg.name];
  if (sourceLayer === undefined) {
    return;
  }

  for (const dependencyName of collectInternalDeps(pkg)) {
    const dependencyLayer = packageLayers[dependencyName];
    if (dependencyLayer === undefined) {
      continue;
    }

    if (dependencyLayer > sourceLayer) {
      violations.push(
        `${path.relative(repoRoot, filePath)} depends on ${dependencyName}, which is a higher-layer package (${dependencyLayer} > ${sourceLayer})`
      );
    }
  }
}

function checkRootDependencyPolicy(policy, violations) {
  const rootPackage = readJson(rootPackagePath);
  const expectedPackageManager = `${policy.packageManager.name}@${policy.packageManager.version}`;
  if (rootPackage.packageManager !== expectedPackageManager) {
    violations.push(`package.json packageManager must be ${expectedPackageManager}`);
  }

  const rootOverrides = rootPackage.pnpm?.overrides || {};
  for (const dependencyName of policy.rootOverrideDependencies || []) {
    const expectedVersion = policy.catalog?.[dependencyName];
    if (!expectedVersion) {
      violations.push(`config/workspace-dependency-policy.json rootOverrideDependencies includes ${dependencyName} without a catalog entry`);
      continue;
    }
    if (rootOverrides[dependencyName] !== expectedVersion) {
      violations.push(`package.json pnpm.overrides.${dependencyName} must match catalog version ${expectedVersion}`);
    }
  }
}

function checkPnpmCatalog(policy, violations) {
  const workspaceSource = fs.readFileSync(pnpmWorkspacePath, 'utf8');
  if (!workspaceSource.includes('\ncatalog:\n')) {
    violations.push('pnpm-workspace.yaml must declare the workspace dependency catalog');
    return;
  }

  for (const [dependencyName, expectedVersion] of Object.entries(policy.catalog || {})) {
    const quotedLine = `  "${dependencyName}": "${expectedVersion}"`;
    if (!workspaceSource.includes(quotedLine)) {
      violations.push(`pnpm-workspace.yaml catalog must include ${dependencyName}: ${expectedVersion}`);
    }
  }
}

function checkCatalogUsage(policy, violations) {
  const catalog = policy.catalog || {};
  const requiredPackages = policy.catalogRequiredPackages || [];
  for (const relativePackagePath of requiredPackages) {
    const absolutePackagePath = path.join(repoRoot, relativePackagePath);
    if (!fs.existsSync(absolutePackagePath)) {
      violations.push(`config/workspace-dependency-policy.json references missing package ${relativePackagePath}`);
      continue;
    }

    const pkg = readJson(absolutePackagePath);
    for (const dependencyField of ['dependencies', 'devDependencies']) {
      for (const [dependencyName, version] of Object.entries(pkg[dependencyField] || {})) {
        if (!Object.prototype.hasOwnProperty.call(catalog, dependencyName)) {
          continue;
        }
        if (version !== 'catalog:') {
          violations.push(`${relativePackagePath} ${dependencyField}.${dependencyName} must use catalog:`);
        }
      }
    }

    for (const [dependencyName, version] of Object.entries(pkg.peerDependencies || {})) {
      const expectedPeerVersion = policy.peerDependencyOverrides?.[dependencyName];
      if (expectedPeerVersion && version !== expectedPeerVersion) {
        violations.push(`${relativePackagePath} peerDependencies.${dependencyName} must use ${expectedPeerVersion}`);
      }
    }
  }
}

function main() {
  const violations = [];
  const workspaceDependencyPolicy = loadWorkspaceDependencyPolicy();
  const packageJsonFiles = findPackageJsonFiles(packagesRoot);

  for (const filePath of packageJsonFiles) {
    const pkg = readJson(filePath);
    checkReadmeContract(filePath, pkg, violations);
    checkDependencyDirection(filePath, pkg, violations);
  }

  checkRootDependencyPolicy(workspaceDependencyPolicy, violations);
  checkPnpmCatalog(workspaceDependencyPolicy, violations);
  checkCatalogUsage(workspaceDependencyPolicy, violations);

  if (violations.length > 0) {
    console.error('Platform TypeScript package governance violations found:');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Validated ${packageJsonFiles.length} platform TypeScript packages: README contracts, dependency direction, and workspace dependency policy are consistent.`);
}

main();
