const fs = require('fs');
const path = require('path');
const ts = require('typescript');

const repoRoot = path.resolve(__dirname, '..');
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

function walkForPackageJson(dirPath, results) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (skipDirs.has(entry.name)) {
        continue;
      }
      walkForPackageJson(path.join(dirPath, entry.name), results);
      continue;
    }

    if (entry.isFile() && entry.name === 'package.json') {
      results.push(path.join(dirPath, entry.name));
    }
  }
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
    .filter((entry) => entry.isFile() && /^tsconfig.*\.json$/u.test(entry.name))
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

function getParsedConfig(configPath) {
  const parsed = ts.getParsedCommandLineOfConfigFile(
    configPath,
    {},
    {
      ...ts.sys,
      onUnRecoverableConfigFileDiagnostic: (diagnostic) => {
        throw new Error(ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
      },
    }
  );

  if (!parsed) {
    throw new Error('TypeScript could not parse the configuration.');
  }

  return parsed;
}

function main() {
  const packageJsonFiles = [];
  const violations = [];

  walkForPackageJson(repoRoot, packageJsonFiles);

  for (const packageJsonPath of packageJsonFiles) {
    const packageDir = path.dirname(packageJsonPath);
    const tsconfigFiles = listDirectTsconfigFiles(packageDir);
    if (tsconfigFiles.length === 0) {
      continue;
    }

    for (const configPath of tsconfigFiles) {
      const configName = path.basename(configPath);
      if (configName === 'tsconfig.emits.json' || configName === 'tsconfig.refs.json') {
        continue;
      }

      let rawConfig;
      try {
        rawConfig = readJson(configPath);
      } catch (error) {
        violations.push(`${path.relative(repoRoot, configPath)} could not be parsed as JSON: ${error.message}`);
        continue;
      }

      if (isSolutionStyleConfig(rawConfig)) {
        continue;
      }

      let parsed;
      try {
        parsed = getParsedConfig(configPath);
      } catch (error) {
        violations.push(`${path.relative(repoRoot, configPath)} could not be resolved by TypeScript: ${error.message}`);
        continue;
      }

      if (parsed.options.strict !== true) {
        violations.push(`${path.relative(repoRoot, configPath)} resolves compilerOptions.strict to ${String(parsed.options.strict)} instead of true`);
      }
    }
  }

  if (violations.length > 0) {
    console.error('TypeScript strict-mode governance violations found:');
    for (const violation of violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log('All first-party TypeScript configs resolve compilerOptions.strict to true.');
}

main();