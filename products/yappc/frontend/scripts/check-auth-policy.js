import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const workspaceRoot = path.resolve(import.meta.dirname, '..');
const allowedDevAuthFiles = new Set([
  path.join('apps', 'api', 'src', 'index.ts'),
  path.join('apps', 'api', 'src', 'middleware', 'devAuth.ts'),
  path.join('apps', 'api', 'src', 'middleware', 'dev-auth-config.ts'),
]);
const allowedDemoLoginFiles = new Set([
  path.join('web', 'src', 'routes', 'login.tsx'),
  path.join('web', 'src', 'services', 'auth', 'AuthService.ts'),
]);
const sourceRoots = [
  path.join(workspaceRoot, 'apps'),
  path.join(workspaceRoot, 'web'),
  path.join(workspaceRoot, 'libs'),
  path.join(workspaceRoot, 'packages'),
  path.join(workspaceRoot, 'compat'),
];
const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs', '.json']);
const ignoredDirectories = new Set([
  'node_modules',
  'dist',
  'build',
  'coverage',
  '.turbo',
  '.pnpm',
]);
const findings = [];

async function walk(directoryPath) {
  const entries = await readdir(directoryPath, { withFileTypes: true });

  for (const entry of entries) {
    if (ignoredDirectories.has(entry.name)) {
      continue;
    }

    const fullPath = path.join(directoryPath, entry.name);
    if (entry.isDirectory()) {
      await walk(fullPath);
      continue;
    }

    if (!sourceExtensions.has(path.extname(entry.name))) {
      continue;
    }

    await inspectFile(fullPath);
  }
}

async function inspectFile(filePath) {
  const relativePath = path.relative(workspaceRoot, filePath);
  if (isTestFile(relativePath)) {
    return;
  }

  const source = await readFile(filePath, 'utf8');

  if (source.includes('VITE_MOCK_AUTH')) {
    findings.push(`${relativePath}: forbidden mock-auth env flag reference`);
  }

  if (source.includes('LEGACY_ENV_FLAGS.MOCK_AUTH')) {
    findings.push(`${relativePath}: forbidden legacy mock-auth feature flag reference`);
  }

  const importsDevAuth = /from\s+['"].*\/devAuth(?:\.ts)?['"]/.test(source)
    || /import\s+\{?\s*devAuthBypass\b/.test(source);
  if (importsDevAuth && !allowedDevAuthFiles.has(relativePath)) {
    findings.push(`${relativePath}: dev auth bypass import outside approved entrypoints`);
  }

  const referencesDemoLogin = source.includes('demoLogin(')
    || source.includes('Continue as Demo User')
    || source.includes('VITE_ENABLE_DEMO_LOGIN');
  if (referencesDemoLogin && !allowedDemoLoginFiles.has(relativePath) && !isTestFile(relativePath)) {
    findings.push(`${relativePath}: demo login reference outside approved auth files`);
  }
}

function isTestFile(relativePath) {
  return relativePath.includes(`${path.sep}__tests__${path.sep}`)
    || relativePath.endsWith('.test.ts')
    || relativePath.endsWith('.test.tsx')
    || relativePath.endsWith('.spec.ts')
    || relativePath.endsWith('.spec.tsx');
}

for (const sourceRoot of sourceRoots) {
  try {
    await walk(sourceRoot);
  } catch (error) {
    if (!(error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT')) {
      throw error;
    }
  }
}

if (findings.length > 0) {
  console.error('YAPPC frontend auth policy violations detected:');
  for (const finding of findings) {
    console.error(`- ${finding}`);
  }
  process.exitCode = 1;
} else {
  console.log('YAPPC frontend auth policy check passed.');
}