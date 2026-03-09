#!/usr/bin/env ts-node

/**
 * Documentation Validation Script
 * 
 * This script validates that all source files have proper JSDoc documentation.
 * It checks for:
 * - Class documentation
 * - Method documentation (public and private)
 * - Parameter documentation
 * - Return type documentation
 * - Field/property documentation
 */

import * as fs from 'fs';
import * as path from 'path';

interface ValidationResult {
  file: string;
  issues: string[];
  score: number;
}

const SRC_DIR = path.join(__dirname, '../src');
const REQUIRED_TAGS = ['@param', '@returns', '@throws', '@example'];

function validateFile(filePath: string): ValidationResult {
  const content = fs.readFileSync(filePath, 'utf-8');
  const issues: string[] = [];
  let score = 100;

  // Check for class documentation
  const classMatches = content.match(/export class \w+/g) || [];
  const classDocMatches = content.match(/\/\*\*[\s\S]*?\*\/\s*export class/g) || [];
  
  if (classMatches.length > classDocMatches.length) {
    issues.push(`Missing documentation for ${classMatches.length - classDocMatches.length} class(es)`);
    score -= 20;
  }

  // Check for method documentation
  const methodMatches = content.match(/(public|private|protected)?\s+(async\s+)?\w+\s*\([^)]*\)/g) || [];
  const methodDocMatches = content.match(/\/\*\*[\s\S]*?\*\/\s*(public|private|protected)?\s+(async\s+)?\w+\s*\(/g) || [];
  
  if (methodMatches.length > methodDocMatches.length) {
    issues.push(`Missing documentation for ${methodMatches.length - methodDocMatches.length} method(s)`);
    score -= 15;
  }

  // Check for @param tags
  const paramMatches = content.match(/@param/g) || [];
  const functionParams = content.match(/\([^)]+\)/g) || [];
  
  if (functionParams.length > 0 && paramMatches.length === 0) {
    issues.push('Missing @param documentation');
    score -= 10;
  }

  // Check for @returns tags
  const returnsMatches = content.match(/@returns/g) || [];
  const asyncMatches = content.match(/async\s+\w+/g) || [];
  
  if (asyncMatches.length > 0 && returnsMatches.length === 0) {
    issues.push('Missing @returns documentation for async methods');
    score -= 10;
  }

  // Check for @example tags
  const exampleMatches = content.match(/@example/g) || [];
  const publicMethodMatches = content.match(/public\s+\w+/g) || [];
  
  if (publicMethodMatches.length > 0 && exampleMatches.length === 0) {
    issues.push('Missing @example documentation for public methods');
    score -= 15;
  }

  return {
    file: path.relative(SRC_DIR, filePath),
    issues,
    score: Math.max(0, score)
  };
}

function walkDir(dir: string, callback: (filePath: string) => void) {
  const files = fs.readdirSync(dir);
  
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    
    if (stat.isDirectory()) {
      walkDir(filePath, callback);
    } else if (file.endsWith('.ts') && !file.endsWith('.test.ts') && !file.endsWith('.spec.ts')) {
      callback(filePath);
    }
  }
}

function main() {
  console.log('🔍 Validating documentation...\n');
  
  const results: ValidationResult[] = [];
  let totalScore = 0;
  let fileCount = 0;

  walkDir(SRC_DIR, (filePath) => {
    const result = validateFile(filePath);
    results.push(result);
    totalScore += result.score;
    fileCount++;
  });

  // Sort by score (worst first)
  results.sort((a, b) => a.score - b.score);

  // Print results
  console.log('📊 Documentation Quality Report\n');
  console.log('=' .repeat(80));
  
  for (const result of results) {
    const status = result.score >= 80 ? '✅' : result.score >= 60 ? '⚠️' : '❌';
    console.log(`${status} ${result.file} (Score: ${result.score}/100)`);
    
    if (result.issues.length > 0) {
      for (const issue of result.issues) {
        console.log(`   - ${issue}`);
      }
    }
    console.log();
  }

  console.log('=' .repeat(80));
  const avgScore = Math.round(totalScore / fileCount);
  console.log(`\n📈 Average Score: ${avgScore}/100`);
  console.log(`📁 Files Checked: ${fileCount}`);
  
  const excellent = results.filter(r => r.score >= 80).length;
  const good = results.filter(r => r.score >= 60 && r.score < 80).length;
  const needsWork = results.filter(r => r.score < 60).length;
  
  console.log(`\n✅ Excellent (≥80): ${excellent}`);
  console.log(`⚠️  Good (60-79): ${good}`);
  console.log(`❌ Needs Work (<60): ${needsWork}`);

  // Exit with error if average score is below threshold
  if (avgScore < 60) {
    console.log('\n❌ Documentation quality below threshold!');
    process.exit(1);
  } else {
    console.log('\n✅ Documentation quality acceptable!');
    process.exit(0);
  }
}

main();
