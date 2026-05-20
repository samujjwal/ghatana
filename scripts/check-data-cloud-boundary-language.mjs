#!/usr/bin/env node

/**
 * DC-P1-04: Lint script to detect stale AEP/product-boundary language in Data Cloud contracts.
 *
 * This script scans OpenAPI contracts and documentation files for deprecated language
 * that should not appear outside explicit compatibility/deprecation sections.
 *
 * <p>Forbidden patterns (outside compatibility sections):
 * <ul>
 *   <li>"AEP standalone" - AEP is now the runtime implementation within Data Cloud</li>
 *   <li>"AEP owns" - AEP does not own orchestration independently</li>
 *   <li>"AEP external orchestration" - All orchestration is now within Data Cloud</li>
 *   <li>"aep.ghatana.local" - Legacy external endpoint references</li>
 *   <li>"port 8090 AEP" - Legacy port references</li>
 *   <li>"tenantId as authoritative tenant source" - Tenant resolution is now via authenticated principal</li>
 * </ul>
 *
 * <p>Allowed sections (where these patterns may appear):
 * <ul>
 *   <li>Compatibility sections with explicit deprecation notices</li>
 *   <li>Migration guides</li>
 *   <li>Legacy route documentation in route-compatibility-registry.yaml</li>
 * </ul>
 *
 * Usage: node scripts/check-data-cloud-boundary-language.mjs
 *
 * @doc.type script
 * @doc.purpose Lint script to detect stale AEP/product-boundary language
 * @doc.layer repo
 * @doc.pattern Linter
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// DC-P1-04: Forbidden patterns that should not appear outside compatibility sections
const FORBIDDEN_PATTERNS = [
  {
    pattern: /AEP standalone/i,
    description: 'AEP standalone',
    replacement: 'Data Cloud Action Plane runtime implementation'
  },
  {
    pattern: /AEP owns/i,
    description: 'AEP owns',
    replacement: 'Data Cloud owns'
  },
  {
    pattern: /AEP external orchestration/i,
    description: 'AEP external orchestration',
    replacement: 'Data Cloud orchestration'
  },
  {
    pattern: /aep\.ghatana\.local/i,
    description: 'aep.ghatana.local',
    replacement: 'Data Cloud endpoint'
  },
  {
    pattern: /port 8090 AEP/i,
    description: 'port 8090 AEP',
    replacement: 'Data Cloud service port'
  },
  {
    pattern: /tenantId as authoritative tenant source/i,
    description: 'tenantId as authoritative tenant source',
    replacement: 'authenticated principal as tenant source'
  }
];

// DC-P1-04: File patterns to scan
const SCAN_PATTERNS = [
  'products/data-cloud/contracts/openapi/*.yaml',
  'products/data-cloud/contracts/openapi/*.yml',
  'products/data-cloud/**/*.md'
];

// DC-P1-04: Files/directories to exclude
const EXCLUDE_PATTERNS = [
  'node_modules',
  'build',
  'target',
  '.git',
  'route-compatibility-registry.yaml'  // Legacy routes are documented here
];

// DC-P1-04: Section markers that indicate compatibility/deprecation sections
const COMPATIBILITY_SECTION_MARKERS = [
  'compatibility',
  'deprecation',
  'migration',
  'legacy',
  'retirement'
];

/**
 * Checks if a line is within a compatibility section.
 */
function isInCompatibilitySection(lines, lineIndex) {
  // Look backwards for section markers
  for (let i = lineIndex; i >= Math.max(0, lineIndex - 50); i--) {
    const line = lines[i].toLowerCase();
    if (COMPATIBILITY_SECTION_MARKERS.some(marker => line.includes(marker))) {
      return true;
    }
    // If we hit a major section marker that's not compatibility, stop looking
    if (line.startsWith('#') && !COMPATIBILITY_SECTION_MARKERS.some(marker => line.includes(marker))) {
      return false;
    }
  }
  return false;
}

/**
 * Scans a file for forbidden patterns.
 */
function scanFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const lines = content.split('\n');
  const violations = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const lineNumber = i + 1;

    // Skip if in compatibility section
    if (isInCompatibilitySection(lines, i)) {
      continue;
    }

    for (const { pattern, description, replacement } of FORBIDDEN_PATTERNS) {
      if (pattern.test(line)) {
        violations.push({
          lineNumber,
          line: line.trim(),
          pattern: description,
          replacement,
          filePath
        });
      }
    }
  }

  return violations;
}

/**
 * Finds files matching the scan patterns.
 */
function findFiles() {
  const files = [];

  for (const pattern of SCAN_PATTERNS) {
    const dir = path.dirname(pattern);
    const basePattern = path.basename(pattern);
    
    if (!fs.existsSync(dir)) {
      continue;
    }

    const walk = (currentDir) => {
      const entries = fs.readdirSync(currentDir, { withFileTypes: true });
      
      for (const entry of entries) {
        const fullPath = path.join(currentDir, entry.name);
        
        // Skip excluded directories
        if (EXCLUDE_PATTERNS.some(exclude => fullPath.includes(exclude))) {
          continue;
        }
        
        if (entry.isDirectory()) {
          walk(fullPath);
        } else if (entry.isFile()) {
          const relativePath = fullPath.replace(process.cwd() + '/', '');
          if (relativePath.match(basePattern.replace('*', '.*'))) {
            files.push(fullPath);
          }
        }
      }
    };

    walk(path.resolve(process.cwd(), dir));
  }

  return files;
}

/**
 * Main lint function.
 */
function main() {
  console.log('DC-P1-04: Checking Data Cloud contracts for stale AEP/product-boundary language...');
  
  const files = findFiles();
  console.log(`Scanning ${files.length} files...`);
  
  let totalViolations = 0;
  const fileViolations = [];

  for (const file of files) {
    const violations = scanFile(file);
    if (violations.length > 0) {
      totalViolations += violations.length;
      fileViolations.push({ file, violations });
    }
  }

  if (totalViolations === 0) {
    console.log('✅ No stale AEP/product-boundary language found.');
    return;
  }

  console.error(`❌ Found ${totalViolations} violation(s) across ${fileViolations.length} file(s):\n`);

  for (const { file, violations } of fileViolations) {
    console.error(`File: ${file}`);
    for (const v of violations) {
      console.error(`  Line ${v.lineNumber}: ${v.line}`);
      console.error(`    Forbidden pattern: "${v.pattern}"`);
      console.error(`    Suggested replacement: "${v.replacement}"`);
      console.error('');
    }
  }

  console.error('\nDC-P1-04: Fix all violations before committing.');
  console.error('Allowed sections: compatibility, deprecation, migration, legacy, retirement documentation.');
  
  process.exit(1);
}

main();
