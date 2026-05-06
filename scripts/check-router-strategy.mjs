#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const manifest = JSON.parse(readFileSync(path.join(repoRoot, 'config/product-shape.json'), 'utf8'));
const violations = [];

function getCanonicalWebPackage(config) {
  return (config.clientPackages ?? []).find((candidate) =>
    /\/(apps\/web|client\/web|ui)\/package\.json$/.test(candidate),
  );
}

function walk(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  if (!existsSync(absoluteDir)) {
    return [];
  }

  const files = [];
  for (const entry of readdirSync(absoluteDir)) {
    const absoluteEntry = path.join(absoluteDir, entry);
    const relativeEntry = path.relative(repoRoot, absoluteEntry);
    const stats = statSync(absoluteEntry);
    if (stats.isDirectory()) {
      if (entry === 'node_modules' || entry === 'dist') {
        continue;
      }
      files.push(...walk(relativeEntry));
      continue;
    }
    files.push(relativeEntry);
  }
  return files;
}

for (const [productName, config] of Object.entries(manifest.products)) {
  if (!config.ui) {
    continue;
  }

  const packageJsonPath = getCanonicalWebPackage(config);
  if (!packageJsonPath) {
    continue;
  }

  const pkg = JSON.parse(readFileSync(path.join(repoRoot, packageJsonPath), 'utf8'));
  const dependencies = pkg.dependencies ?? {};

  if (dependencies['react-router']) {
    violations.push(`${productName}: ${packageJsonPath} must not declare direct dependency react-router; use react-router-dom as the approved web router package`);
  }

  if (!dependencies['react-router-dom']) {
    violations.push(`${productName}: ${packageJsonPath} must declare react-router-dom as the approved web router package`);
  }

  const clientDir = path.dirname(packageJsonPath);
  const sourceFiles = walk(path.join(clientDir, 'src')).filter((file) => /\.(ts|tsx)$/.test(file) && !file.includes('__tests__'));

  for (const file of sourceFiles) {
    const content = readFileSync(path.join(repoRoot, file), 'utf8');
    if (/from ['"]react-router['"]/.test(content)) {
      violations.push(`${productName}: production source ${file} imports react-router directly; use react-router-dom for product UI code`);
    }
  }
}

if (violations.length > 0) {
  console.error('Router strategy check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Router strategy check passed.');
