#!/usr/bin/env node

/**
 * PHR PHI-Safe Logging Verification Script
 * 
 * This script verifies that PHR backend code uses PHI-safe logging practices
 * by checking for direct logging calls that may contain PHI without redaction.
 * 
 * Usage: node scripts/check-phr-phi-logging-usage.mjs
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, extname } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const PHR_BACKEND_DIR = join(__dirname, '..', 'products', 'phr', 'src', 'main', 'java');

// Patterns that indicate direct logging without PHI redaction
const LOGGING_PATTERNS = [
  /\.info\(/,
  /\.debug\(/,
  /\.error\(/,
  /\.warn\(/,
  /\.trace\(/,
  /LOG\.info/,
  /LOG\.debug/,
  /LOG\.error/,
  /LOG\.warn/,
  /logger\.info/,
  /logger\.debug/,
  /logger\.error/,
  /logger\.warn/,
];

// Patterns that indicate PHI fields in log messages
const PHI_FIELD_PATTERNS = [
  /patientId/,
  /patient_id/,
  /principalId/,
  /principal_id/,
  /fullName/,
  /full_name/,
  /diagnosis/,
  /medication/,
  /condition/,
  /labResult/,
  /lab_result/,
  /nationalId/,
  /national_id/,
  /ssn/,
  /socialSecurity/,
  /email/,
  /phoneNumber/,
  /phone_number/,
  /address/,
];

// Files that are allowed to use direct logging (e.g., test files, infrastructure)
const ALLOWLIST = [
  'Test.java',
  'IT.java',
  'E2E.java',
  'Mock',
  'Stub',
];

let totalFiles = 0;
let filesWithIssues = 0;
let totalIssues = 0;

function scanDirectory(dir, results = []) {
  const files = readdirSync(dir);
  
  for (const file of files) {
    const filePath = join(dir, file);
    const stat = statSync(filePath);
    
    if (stat.isDirectory()) {
      scanDirectory(filePath, results);
    } else if (extname(file) === '.java') {
      results.push(filePath);
    }
  }
  
  return results;
}

function checkFile(filePath) {
  const content = readFileSync(filePath, 'utf-8');
  const fileName = filePath.split('\\').pop();
  
  // Skip allowlisted files
  if (ALLOWLIST.some(pattern => fileName.includes(pattern))) {
    return [];
  }
  
  const issues = [];
  const lines = content.split('\n');
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const lineNumber = i + 1;
    
    // Check if line contains logging call
    const hasLogging = LOGGING_PATTERNS.some(pattern => pattern.test(line));
    
    if (hasLogging) {
      // Check if line contains PHI field references
      const hasPhiField = PHI_FIELD_PATTERNS.some(pattern => pattern.test(line));
      
      if (hasPhiField) {
        issues.push({
          line: lineNumber,
          text: line.trim(),
          reason: 'Contains PHI field reference in logging call'
        });
      }
    }
  }
  
  return issues;
}

function main() {
  console.log('Scanning PHR backend Java files for PHI-safe logging compliance...\n');
  
  const javaFiles = scanDirectory(PHR_BACKEND_DIR);
  totalFiles = javaFiles.length;
  
  for (const file of javaFiles) {
    const issues = checkFile(file);
    
    if (issues.length > 0) {
      filesWithIssues++;
      totalIssues += issues.length;
      
      const relativePath = file.replace(join(__dirname, '..'), '');
      console.log(`\n${relativePath}:`);
      
      for (const issue of issues) {
        console.log(`  Line ${issue.line}: ${issue.reason}`);
        console.log(`    ${issue.text.substring(0, 100)}${issue.text.length > 100 ? '...' : ''}`);
      }
    }
  }
  
  console.log('\n=== Summary ===');
  console.log(`Total files scanned: ${totalFiles}`);
  console.log(`Files with issues: ${filesWithIssues}`);
  console.log(`Total issues found: ${totalIssues}`);
  
  if (totalIssues > 0) {
    console.log('\n⚠️  PHI logging issues detected. Review and update to use PhrSafeLogger.redactPhi() or PhrLogRedactor.');
    process.exit(1);
  } else {
    console.log('\n✅ No PHI logging issues detected.');
    process.exit(0);
  }
}

main();
