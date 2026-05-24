const ts = require('typescript');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const repoRoot = path.resolve(__dirname, '..');
const dryRun = process.argv.includes('--dry-run');
const affectedOnly = process.argv.includes('--affected');
const jobs = Math.max(1, Number(argValue('--jobs') || Math.min(4, os.cpus().length)) || 1);
const skipDirs = new Set([
  '.git',
  '.gradle',
  '.idea',
  '.next',
  '.pnpm-store',
  '.turbo',
  '.vscode',
  'build',
  'coverage',
  'dist',
  'node_modules',
  'target',
]);
const skipConfigNames = new Set([
  'tsconfig.emits.json',
  'tsconfig.eslint.json',
  'tsconfig.paths.json',
  'tsconfig.refs.json',
]);

function argValue(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

function normalizePath(value) {
  return String(value).replace(/\\/g, '/').replace(/^\.\//, '');
}

function splitCsv(value) {
  return String(value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map(normalizePath);
}

function readChangedFiles() {
  const explicitPaths = splitCsv(argValue('--paths'));
  if (explicitPaths.length > 0) {
    return explicitPaths;
  }

  const base = argValue('--base') || process.env.GITHUB_BASE_SHA || 'origin/main';
  const head = argValue('--head') || process.env.GITHUB_SHA || 'HEAD';
  const result = spawnSync('git', ['diff', '--name-only', `${base}...${head}`], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: process.env,
  });

  if (result.status !== 0) {
    throw new Error(`Could not resolve affected TypeScript diff ${base}...${head}`);
  }

  return result.stdout.split(/\r?\n/).filter(Boolean).map(normalizePath);
}

function isUnder(candidate, root) {
  const normalizedRoot = root.replace(/\/$/, '');
  return candidate === normalizedRoot || candidate.startsWith(`${normalizedRoot}/`);
}

function productRoots() {
  const productIds = splitCsv(argValue('--products') || process.env.AFFECTED_PRODUCTS);
  if (productIds.length === 0) {
    return [];
  }

  const registry = JSON.parse(fs.readFileSync(path.join(repoRoot, 'config/canonical-product-registry.json'), 'utf8')).registry;
  const roots = [];
  for (const productId of productIds) {
    const product = registry[productId];
    if (!product) {
      throw new Error(`Unknown product id for TypeScript typecheck: ${productId}`);
    }
    roots.push(`products/${productId}`);
    for (const candidate of [
      product.manifestPath,
      product.buildFile,
      ...(product.pnpmPackages ?? []),
      ...(product.surfaces ?? []).flatMap((surface) => [surface.path, surface.packagePath].filter(Boolean)),
    ]) {
      if (typeof candidate === 'string') {
        roots.push(candidate.replace(/\/package\.json$/, ''));
      }
    }
  }
  return [...new Set(roots.map(normalizePath))];
}

function isGlobalTypeScriptImpact(changedFile) {
  return [
    'package.json',
    'pnpm-lock.yaml',
    'pnpm-workspace.yaml',
    'tsconfig.json',
    'tsconfig.base.json',
  ].includes(changedFile)
    || changedFile.startsWith('scripts/')
    || changedFile.startsWith('config/');
}

function listWorkspacePackageDirs() {
  const result = spawnSync('pnpm', ['list', '-r', '--depth', '-1', '--json'], {
    cwd: repoRoot,
    encoding: 'utf8',
    env: process.env,
  });

  if (result.status !== 0) {
    throw new Error(result.stderr || 'pnpm list failed');
  }

  const packages = JSON.parse(result.stdout);
  return packages
    .map((pkg) => pkg.path)
    .filter((packageDir) => typeof packageDir === 'string' && packageDir !== repoRoot)
    .sort();
}

function readJson(filePath) {
  const result = ts.readConfigFile(filePath, ts.sys.readFile);
  if (result.error) {
    throw new Error(ts.flattenDiagnosticMessageText(result.error.messageText, '\n'));
  }
  return result.config;
}

function listDirectTsconfigFiles(packageDir) {
  return fs.readdirSync(packageDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && /^tsconfig.*\.json$/u.test(entry.name) && !skipConfigNames.has(entry.name))
    .map((entry) => path.join(packageDir, entry.name))
    .sort();
}

function isSolutionStyleConfig(rawConfig) {
  const hasReferences = Array.isArray(rawConfig.references) && rawConfig.references.length > 0;
  const hasCompilerOptions = rawConfig.compilerOptions && Object.keys(rawConfig.compilerOptions).length > 0;
  const hasInclude = Array.isArray(rawConfig.include) && rawConfig.include.length > 0;
  const hasFiles = Array.isArray(rawConfig.files) && rawConfig.files.length > 0;

  return hasReferences && !hasCompilerOptions && !hasInclude && !hasFiles && !rawConfig.extends;
}

function collectTargetsFromConfig(configPath, seen) {
  const resolvedPath = path.resolve(configPath);
  if (seen.has(resolvedPath)) {
    return [];
  }
  seen.add(resolvedPath);

  const rawConfig = readJson(resolvedPath);
  if (!isSolutionStyleConfig(rawConfig)) {
    return [resolvedPath];
  }

  const configDir = path.dirname(resolvedPath);
  const targets = [];
  for (const reference of rawConfig.references || []) {
    if (!reference.path) {
      continue;
    }

    const candidatePath = path.resolve(configDir, reference.path);
    const referenceConfigPath = fs.existsSync(candidatePath) && fs.statSync(candidatePath).isFile()
      ? candidatePath
      : path.join(candidatePath, 'tsconfig.json');

    if (!fs.existsSync(referenceConfigPath)) {
      throw new Error(`Missing referenced tsconfig: ${path.relative(repoRoot, referenceConfigPath)}`);
    }

    targets.push(...collectTargetsFromConfig(referenceConfigPath, seen));
  }

  return targets;
}

function selectedPackageDirs(allPackageDirs) {
  const roots = productRoots();
  const changedFiles = affectedOnly || argValue('--paths') ? readChangedFiles() : [];

  if (roots.length === 0 && changedFiles.length === 0) {
    return allPackageDirs;
  }

  if (roots.length === 0 && changedFiles.some(isGlobalTypeScriptImpact)) {
    return allPackageDirs;
  }

  const selectedRoots = [...roots];
  const packageRoots = allPackageDirs.map((packageDir) => ({
    absolutePath: packageDir,
    relativePath: normalizePath(path.relative(repoRoot, packageDir)),
  }));

  for (const changedFile of changedFiles) {
    const owner = packageRoots
      .filter((entry) => isUnder(changedFile, entry.relativePath))
      .sort((left, right) => right.relativePath.length - left.relativePath.length)[0];
    if (owner) {
      selectedRoots.push(owner.relativePath);
    }
  }

  const uniqueRoots = [...new Set(selectedRoots)];
  return packageRoots
    .filter((entry) => uniqueRoots.some((root) => isUnder(entry.relativePath, root) || isUnder(root, entry.relativePath)))
    .map((entry) => entry.absolutePath);
}

function buildCommands(packageDirs) {
  const commands = [];
  const seenTargets = new Set();

  for (const packageDir of packageDirs) {
    const packageJsonPath = path.join(packageDir, 'package.json');
    if (!fs.existsSync(packageJsonPath)) {
      continue;
    }

    const packageJson = readJson(packageJsonPath);

    if (packageJson.scripts && typeof packageJson.scripts.typecheck === 'string') {
      commands.push({
        label: `${path.relative(repoRoot, packageDir) || '.'}:typecheck`,
        command: ['pnpm', '--dir', packageDir, 'run', 'typecheck'],
      });
      continue;
    }

    const tsconfigFiles = listDirectTsconfigFiles(packageDir);
    if (tsconfigFiles.length === 0) {
      continue;
    }

    const primaryConfig = tsconfigFiles.find((configPath) => path.basename(configPath) === 'tsconfig.json');
    const candidateConfigs = primaryConfig
      ? collectTargetsFromConfig(primaryConfig, new Set())
      : tsconfigFiles.filter((configPath) => {
          const name = path.basename(configPath);
          return name === 'tsconfig.app.json' || name === 'tsconfig.node.json' || name === 'tsconfig.test.json' || name === 'tsconfig.types.json';
        });

    for (const configPath of candidateConfigs) {
      const resolvedPath = path.resolve(configPath);
      if (seenTargets.has(resolvedPath)) {
        continue;
      }
      seenTargets.add(resolvedPath);
      commands.push({
        label: path.relative(repoRoot, resolvedPath),
        command: ['pnpm', 'exec', 'tsc', '--noEmit', '--pretty', 'false', '-p', resolvedPath],
      });
    }
  }

  return commands;
}

async function main() {
  let commands;
  try {
    const packageDirs = listWorkspacePackageDirs();
    commands = buildCommands(selectedPackageDirs(packageDirs));
  } catch (error) {
    console.error(`Failed to build the typecheck plan: ${error.message}`);
    process.exit(1);
  }

  if (commands.length === 0) {
    console.log('No TypeScript workspace targets found.');
    return;
  }

  console.log(`Discovered ${commands.length} TypeScript workspace targets.`);
  console.log(`Worker count: ${dryRun ? 0 : jobs}.`);

  if (dryRun) {
    for (const { label, command } of commands) {
      console.log(`\n==> ${label}`);
      console.log(command.join(' '));
    }
    return;
  }

  await runCommands(commands, jobs);
}

function runOne({ label, command }) {
  return new Promise((resolve) => {
    console.log(`\n==> ${label}`);
    console.log(command.join(' '));

    const child = spawn(command[0], command.slice(1), {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
      shell: process.platform === 'win32',
    });

    child.on('exit', (status) => resolve(status ?? 1));
    child.on('error', () => resolve(1));
  });
}

async function runCommands(commands, workerCount) {
  let nextIndex = 0;
  let failedStatus = 0;

  async function worker() {
    while (nextIndex < commands.length && failedStatus === 0) {
      const command = commands[nextIndex];
      nextIndex += 1;
      const status = await runOne(command);
      if (status !== 0) {
        failedStatus = status;
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(workerCount, commands.length) }, () => worker()));
  if (failedStatus !== 0) {
    process.exit(failedStatus);
  }
}

main();
