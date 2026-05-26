#!/usr/bin/env node

/**
 * SEC-P1-007: Tenant-safe logging checks
 * 
 * Ensures logs do not contain PII, media payloads, extracted OCR text, raw tokens,
 * or other sensitive data that could leak tenant information.
 * 
 * @doc.type script
 * @doc.purpose Detect unsafe logging patterns that could leak sensitive data
 * @doc.layer scripts
 */

import { readFileSync } from 'fs';
import { readdirSync, statSync } from 'fs';
import { join, relative } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..');

// Patterns that should NOT appear in log statements
const UNSAFE_PATTERNS = [
  // Direct logging of sensitive fields
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*password/i, name: 'password in log' },
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*token/i, name: 'token in log' },
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*secret/i, name: 'secret in log' },
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*apiKey/i, name: 'apiKey in log' },
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*credential/i, name: 'credential in log' },
  
  // Logging full objects that may contain sensitive data
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*\{[^}]*\}[^)]*\)/, name: 'object literal in log' },
  
  // String concatenation with potentially sensitive variables
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*\+[^)]*\)/, name: 'string concatenation in log' },
  
  // Template literals with potentially sensitive variables
  { pattern: /log\.(info|debug|warn|error|trace)\([^)]*`[^`]*\$\{[^}]*\}[^`]*`\)/, name: 'template literal in log' },
  
  // Console logging (should use proper logger)
  { pattern: /console\.(log|info|debug|warn|error)\(/, name: 'console logging' },
];

// Safe patterns that are allowed
const SAFE_PATTERNS = [
  /log\.(info|debug|warn|error|trace)\([^)]*"[^"]*"\)/, // String literals only
  /log\.(info|debug|warn|error|trace)\([^)]*'[^']*'\)/, // String literals only
  /log\.(info|debug|warn|error|trace)\([^)]*redacted/i, // Explicitly redacted
  /log\.(info|debug|warn|error|trace)\([^)]*\*\*\*\*/i, // Masked values
  /log\.(info|debug|warn|error|trace)\([^)]*XXX/i, // Masked values
];

const VIOLATIONS = [];

function scanFile(filePath) {
  try {
    const content = readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    
    lines.forEach((line, index) => {
      // Skip comments
      if (line.trim().startsWith('//') || line.trim().startsWith('*')) {
        return;
      }
      
      // Check for unsafe patterns
      for (const { pattern, name } of UNSAFE_PATTERNS) {
        if (pattern.test(line)) {
          // Check if it's actually safe (e.g., string literal only)
          let isSafe = false;
          for (const safePattern of SAFE_PATTERNS) {
            if (safePattern.test(line)) {
              isSafe = true;
              break;
            }
          }
          
          if (!isSafe) {
            VIOLATIONS.push({
              file: relative(process.cwd(), filePath),
              line: index + 1,
              pattern: name,
              content: line.trim()
            });
          }
        }
      }
    });
  } catch (error) {
    // Skip files that can't be read
  }
}

function scanDirectory(dir, extensions = ['.java', '.ts', '.tsx', '.js', '.mjs']) {
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    
    if (entry.isDirectory()) {
      // Skip node_modules, build, and test directories
      if (
        entry.name === 'node_modules' ||
        entry.name === 'build' ||
        entry.name === 'dist' ||
        entry.name === 'target' ||
        entry.name === '.git' ||
        entry.name === '__tests__' ||
        entry.name === 'test' ||
        entry.name === 'tests'
      ) {
        continue;
      }
      scanDirectory(fullPath, extensions);
    } else if (entry.isFile()) {
      const ext = entry.name.substring(entry.name.lastIndexOf('.'));
      if (extensions.includes(ext)) {
        scanFile(fullPath);
      }
    }
  }
}

function main() {
  const args = process.argv.slice(2);
  const targetDir = args[0] || process.cwd();
  
  console.log(`Scanning ${targetDir} for unsafe logging patterns...`);
  
  scanDirectory(targetDir);
  
  if (VIOLATIONS.length === 0) {
    console.log('✓ No unsafe logging patterns found');
    process.exit(0);
  } else {
    console.error(`\n✗ Found ${VIOLATIONS.length} unsafe logging pattern(s):\n`);
    
    VIOLATIONS.forEach(({ file, line, pattern, content }) => {
      console.error(`  ${file}:${line} - ${pattern}`);
      console.error(`    ${content}\n`);
    });
    
    console.error('SEC-P1-007: Logs must not contain PII, media payloads, OCR text, or raw tokens.');
    console.error('Use structured logging with redaction or explicit masking.');
    
    process.exit(1);
  }
}

main();
