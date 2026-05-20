#!/usr/bin/env node

/**
 * DC-P1-11: Stale AEP/Product Boundary Lint Script
 *
 * This script enforces product boundary language correctness across the Data Cloud codebase:
 * 1. Allows AEP (Agentic Event Processor) wording only in compatibility/deprecation sections
 * 2. Forbids customer-facing standalone AEP language (use "Action Plane" instead)
 * 3. Forbids tenant header/query as authoritative production tenant source
 *
 * Usage: node scripts/lint-stale-aep-boundary.mjs
 *
 * @doc.type script
 * @doc.purpose Enforce product boundary language and prevent stale AEP terminology
 * @doc.layer repo
 * @doc.pattern Lint
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Paths
const REPO_ROOT = path.resolve(__dirname, '..');
const PRODUCTS_DATA_CLOUD = path.join(REPO_ROOT, 'products/data-cloud');
const PLATFORM_CONTRACTS = path.join(REPO_ROOT, 'platform/contracts');

// File extensions to scan
const FILE_EXTENSIONS = [
  '.java',
  '.ts',
  '.tsx',
  '.yaml',
  '.yml',
  '.md',
  '.json'
];

// Excluded directories
const EXCLUDE_DIRS = [
  'node_modules',
  'build',
  'target',
  '.gradle',
  'dist',
  'out',
  '.git',
  '.idea',
  '.vscode',
  'coverage'
];

/**
 * Recursively walks a directory and returns all files with matching extensions
 */
function walkDirectory(dir, extensions, excludeDirs, results = []) {
  if (!fs.existsSync(dir)) {
    return results;
  }

  const entries = fs.readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    
    if (entry.isDirectory()) {
      if (excludeDirs.includes(entry.name)) {
        continue;
      }
      walkDirectory(fullPath, extensions, excludeDirs, results);
    } else if (entry.isFile()) {
      const ext = path.extname(entry.name);
      if (extensions.includes(ext)) {
        results.push(fullPath);
      }
    }
  }

  return results;
}

// DC-P1-11: Stale AEP patterns that are forbidden outside compatibility sections
const STALE_AEP_PATTERNS = [
  // Forbidden standalone AEP references (customer-facing)
  /\bAEP\b.*product/i,
  /\bAgentic Event Processor\b.*product/i,
  /\bAEP\b.*standalone/i,
  /\bAEP\b.*runtime.*service/i,
  
  // Forbidden: AEP as a standalone product name in customer-facing docs
  /the AEP product/i,
  /AEP platform/i,
  /AEP service/i,
  
  // Forbidden: Tenant header/query as authoritative source
  /tenant.*header.*authoritative/i,
  /tenant.*query.*authoritative/i,
  /X-Tenant-ID.*authoritative/i,
  /tenantId.*query.*authoritative/i,
  /tenantId.*header.*authoritative/i,
];

// DC-P1-11: Allowed AEP patterns (only in compatibility/deprecation sections)
const ALLOWED_AEP_CONTEXTS = [
  /compatibility.*AEP/i,
  /AEP.*compatibility/i,
  /deprecated.*AEP/i,
  /AEP.*deprecated/i,
  /legacy.*AEP/i,
  /AEP.*legacy/i,
  /migration.*AEP/i,
  /AEP.*migration/i,
];

// DC-P1-11: Correct terminology patterns (Action Plane)
const ACTION_PLANE_PATTERNS = [
  /Action Plane/i,
  /action-plane/i,
  /action plane/i,
];

// Lint results
const violations = [];
const warnings = [];

/**
 * Checks if a line is within a compatibility/deprecation section
 */
function isInCompatibilitySection(lines, lineIndex) {
  // Look back up to 10 lines for compatibility context markers
  const lookback = Math.min(10, lineIndex);
  for (let i = lineIndex - 1; i >= Math.max(0, lineIndex - lookback); i--) {
    const line = lines[i].toLowerCase();
    if (line.includes('compatibility') || 
        line.includes('deprecated') || 
        line.includes('legacy') ||
        line.includes('migration') ||
        line.includes('note:') ||
        line.includes('note:')) {
      return true;
    }
  }
  return false;
}

/**
 * Checks if a line contains stale AEP language
 */
function checkStaleAEPLanguage(line, lineIndex, lines, filePath) {
  for (const pattern of STALE_AEP_PATTERNS) {
    if (pattern.test(line)) {
      // Check if it's in an allowed context
      if (isInCompatibilitySection(lines, lineIndex)) {
        warnings.push({
          file: filePath,
          line: lineIndex + 1,
          message: 'AEP reference in compatibility section (allowed but consider migration)',
          content: line.trim()
        });
      } else {
        violations.push({
          file: filePath,
          line: lineIndex + 1,
          message: 'Standalone AEP reference found. Use "Action Plane" instead for customer-facing content.',
          content: line.trim(),
          suggestion: 'Replace AEP with "Action Plane" or move to compatibility/deprecation section'
        });
      }
    }
  }
}

/**
 * Checks if a line uses tenant header/query as authoritative
 */
function checkTenantAuthority(line, lineIndex, lines, filePath) {
  // Check for authoritative tenant header/query language
  if (/tenant.*header.*authoritative/i.test(line) ||
      /tenant.*query.*authoritative/i.test(line) ||
      /X-Tenant-ID.*authoritative/i.test(line)) {
    
    // Allow in compatibility/deprecation sections
    if (isInCompatibilitySection(lines, lineIndex)) {
      warnings.push({
        file: filePath,
        line: lineIndex + 1,
        message: 'Tenant authority reference in compatibility section',
        content: line.trim()
      });
    } else {
      violations.push({
        file: filePath,
        line: lineIndex + 1,
        message: 'Tenant header/query marked as authoritative. In production, tenant is derived from authenticated identity only.',
        content: line.trim(),
        suggestion: 'Mark as "compatibility hint only" or move to compatibility section'
      });
    }
  }
}

/**
 * Checks if a line should use Action Plane instead of AEP
 */
function checkActionPlaneTerminology(line, lineIndex, filePath) {
  // If AEP appears without Action Plane context, it might be stale
  if (/\bAEP\b/i.test(line) && !/Action Plane/i.test(line)) {
    // Check if it's in allowed context
    if (line.toLowerCase().includes('compatibility') ||
        line.toLowerCase().includes('deprecated') ||
        line.toLowerCase().includes('legacy') ||
        line.toLowerCase().includes('migration')) {
      // Allowed
    } else {
      warnings.push({
        file: filePath,
        line: lineIndex + 1,
        message: 'AEP reference found without Action Plane context. Consider using "Action Plane" for consistency.',
        content: line.trim()
      });
    }
  }
}

/**
 * Scans a single file for violations
 */
function scanFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    
    // Skip certain file types
    if (filePath.endsWith('.gradle') || 
        filePath.endsWith('.xml') ||
        filePath.includes('node_modules') ||
        filePath.includes('build') ||
        filePath.includes('target')) {
      return;
    }

    lines.forEach((line, lineIndex) => {
      checkStaleAEPLanguage(line, lineIndex, lines, filePath);
      checkTenantAuthority(line, lineIndex, lines, filePath);
      checkActionPlaneTerminology(line, lineIndex, filePath);
    });
  } catch (error) {
    // Skip files that can't be read (binary files, etc.)
  }
}

/**
 * Main execution
 */
function main() {
  console.log('DC-P1-11: Scanning for stale AEP/product boundary language violations...\n');
  
  // Scan products/data-cloud
  const dataCloudFiles = walkDirectory(PRODUCTS_DATA_CLOUD, FILE_EXTENSIONS, EXCLUDE_DIRS);
  dataCloudFiles.forEach(scanFile);
  
  // Scan platform/contracts if it exists
  let contractFiles = [];
  if (fs.existsSync(PLATFORM_CONTRACTS)) {
    contractFiles = walkDirectory(PLATFORM_CONTRACTS, FILE_EXTENSIONS, EXCLUDE_DIRS);
    contractFiles.forEach(scanFile);
  }
  
  // Report results
  const totalFiles = dataCloudFiles.length + contractFiles.length;
  console.log(`Scanned ${totalFiles} files\n`);
  
  if (warnings.length > 0) {
    console.log(`⚠️  Warnings (${warnings.length}):\n`);
    warnings.forEach(warning => {
      console.log(`  ${warning.file}:${warning.line}`);
      console.log(`    ${warning.message}`);
      console.log(`    Content: ${warning.content.substring(0, 100)}${warning.content.length > 100 ? '...' : ''}`);
      console.log('');
    });
  }
  
  if (violations.length > 0) {
    console.log(`❌ Violations (${violations.length}):\n`);
    violations.forEach(violation => {
      console.log(`  ${violation.file}:${violation.line}`);
      console.log(`    ${violation.message}`);
      console.log(`    Content: ${violation.content.substring(0, 100)}${violation.content.length > 100 ? '...' : ''}`);
      if (violation.suggestion) {
        console.log(`    Suggestion: ${violation.suggestion}`);
      }
      console.log('');
    });
    
    console.log(`\nDC-P1-11: Found ${violations.length} product boundary violations.`);
    console.log('Fix violations by:');
    console.log('  1. Replacing "AEP" with "Action Plane" for customer-facing content');
    console.log('  2. Moving AEP references to compatibility/deprecation sections');
    console.log('  3. Marking tenant header/query as "compatibility hint only"');
    
    process.exit(1);
  } else {
    console.log('✅ No product boundary violations found.');
    if (warnings.length > 0) {
      console.log(`⚠️  ${warnings.length} warnings found (non-blocking).`);
    }
    process.exit(0);
  }
}

main();
