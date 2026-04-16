const ts = require('typescript');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const repoRoot = path.resolve(__dirname, '..');
const dryRun = process.argv.includes('--dry-run');
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

function buildCommands() {
  const commands = [];
  const seenTargets = new Set();

  for (const packageDir of listWorkspacePackageDirs()) {
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

function main() {
  let commands;
  try {
    commands = buildCommands();
  } catch (error) {
    console.error(`Failed to build the typecheck plan: ${error.message}`);
    process.exit(1);
  }

  if (commands.length === 0) {
    console.log('No TypeScript workspace targets found.');
    return;
  }

  console.log(`Discovered ${commands.length} TypeScript workspace targets.`);

  for (const { label, command } of commands) {
    console.log(`\n==> ${label}`);
    console.log(command.join(' '));

    if (dryRun) {
      continue;
    }

    const result = spawnSync(command[0], command.slice(1), {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });

    if (result.status !== 0) {
      process.exit(result.status || 1);
    }
  }
}

main();