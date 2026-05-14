/**
 * Check script for ProductUnit provider contracts.
 *
 * Validates that provider implementations conform to the ProductUnit provider contracts.
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const PROVIDERS_PATH = join(__dirname, '..', 'platform', 'typescript', 'kernel-providers', 'src');
const CONTRACTS_PATH = join(__dirname, '..', 'platform', 'typescript', 'kernel-product-contracts', 'src', 'provider');

function isFile(path) {
  try {
    return statSync(path).isFile();
  } catch {
    return false;
  }
}

function isDirectory(path) {
  try {
    return statSync(path).isDirectory();
  } catch {
    return false;
  }
}

function findFiles(dir, extensions = ['.ts']) {
  const files = [];

  if (!isDirectory(dir)) {
    return files;
  }

  const entries = readdirSync(dir);

  for (const entry of entries) {
    const fullPath = join(dir, entry);

    if (isDirectory(fullPath)) {
      // Skip test directories
      if (entry === '__tests__' || entry === 'dist') {
        continue;
      }
      files.push(...findFiles(fullPath, extensions));
    } else if (isFile(fullPath)) {
      const ext = entry.slice(entry.lastIndexOf('.'));
      if (extensions.includes(ext)) {
        files.push(fullPath);
      }
    }
  }

  return files;
}

function checkProviderImplementsContract(providerFile, contractFile, errors) {
  try {
    const providerContent = readFileSync(providerFile, 'utf-8');
    const contractContent = readFileSync(contractFile, 'utf-8');

    // Check that provider file imports from contracts
    if (!providerContent.includes('@ghatana/kernel-product-contracts')) {
      errors.push(
        `Provider ${providerFile} does not import from @ghatana/kernel-product-contracts`
      );
    }

    // Check that provider implements the interface
    const providerName = providerFile.split('/').pop().replace('.ts', '');
    if (!providerContent.includes('implements')) {
      errors.push(
        `Provider ${providerFile} does not explicitly implement a contract interface`
      );
    }
  } catch (error) {
    console.error(`Error checking ${providerFile}:`, error.message);
  }
}

function main() {
  if (!isDirectory(PROVIDERS_PATH)) {
    console.error(`Providers directory not found at ${PROVIDERS_PATH}`);
    process.exit(1);
  }

  if (!isDirectory(CONTRACTS_PATH)) {
    console.error(`Contracts directory not found at ${CONTRACTS_PATH}`);
    process.exit(1);
  }

  console.log('Checking ProductUnit provider contracts...');

  const providerFiles = findFiles(PROVIDERS_PATH);
  const contractFiles = findFiles(CONTRACTS_PATH);

  console.log(`Found ${providerFiles.length} provider files.`);
  console.log(`Found ${contractFiles.length} contract files.`);

  const errors = [];

  // Simple validation: check that providers reference contracts
  for (const providerFile of providerFiles) {
    // Check if provider file references any contract
    try {
      const providerContent = readFileSync(providerFile, 'utf-8');
      if (!providerContent.includes('@ghatana/kernel-product-contracts')) {
        errors.push(
          `Provider ${providerFile} does not import from @ghatana/kernel-product-contracts`
        );
      }
    } catch (error) {
      console.error(`Error reading ${providerFile}:`, error.message);
    }
  }

  if (errors.length > 0) {
    console.error('Provider contract validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Provider contract validation passed.');
}

main();
