import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const packageRoot = path.resolve(__dirname, '..');
const sourcePackagePath = path.join(packageRoot, 'package.json');
const distDir = path.join(packageRoot, 'dist');
const distPackagePath = path.join(distDir, 'package.json');

if (!fs.existsSync(distDir)) {
  throw new Error(`Build output missing at ${distDir}. Run tsc before generating dist package.`);
}

const sourcePackage = JSON.parse(fs.readFileSync(sourcePackagePath, 'utf8'));

const distPackage = {
  name: sourcePackage.name,
  version: sourcePackage.version,
  description: sourcePackage.description,
  type: sourcePackage.type,
  main: sourcePackage.main,
  types: sourcePackage.types,
  exports: sourcePackage.exports,
  files: sourcePackage.files,
  dependencies: sourcePackage.dependencies,
  peerDependencies: sourcePackage.peerDependencies,
  publishConfig: sourcePackage.publishConfig,
  repository: sourcePackage.repository,
  keywords: sourcePackage.keywords,
  author: sourcePackage.author,
  license: sourcePackage.license,
};

fs.writeFileSync(distPackagePath, `${JSON.stringify(distPackage, null, 2)}\n`, 'utf8');
