const fs = require('fs');
const path = require('path');

const repoRoot = path.resolve(__dirname, '..');
const packagesRoot = path.join(repoRoot, 'platform', 'typescript');

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

function main() {
  const violations = [];
  const packageJsonFiles = findPackageJsonFiles(packagesRoot);

  for (const filePath of packageJsonFiles) {
    const pkg = readJson(filePath);
    checkReadmeContract(filePath, pkg, violations);
    checkDependencyDirection(filePath, pkg, violations);
  }

  if (violations.length > 0) {
    console.error('Platform TypeScript package governance violations found:');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Validated ${packageJsonFiles.length} platform TypeScript packages: README contracts and dependency direction are consistent.`);
}

main();
