#!/usr/bin/env node
/**
 * @fileoverview Test Coverage Tracker
 * Monitors coverage gaps and generates improvement plans
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const COVERAGE_TARGETS = {
  lines: 70,
  functions: 70,
  branches: 60,
  statements: 70
};

const PRODUCT_PRIORITIES = {
  'flashit': 'critical',
  'yappc': 'high',
  'tutorputor': 'medium',
  'data-cloud': 'medium',
  'dcmaar': 'medium',
  'aep': 'medium'
};

function findCoverageReports() {
  const reports = [];
  const workspaceRoot = process.cwd();
  
  const products = [
    'products/flashit',
    'products/yappc',
    'products/tutorputor',
    'products/data-cloud',
    'products/dcmaar',
    'products/aep'
  ];
  
  for (const product of products) {
    const coveragePath = path.join(workspaceRoot, product, 'coverage/coverage-summary.json');
    if (fs.existsSync(coveragePath)) {
      try {
        const summary = JSON.parse(fs.readFileSync(coveragePath, 'utf-8'));
        reports.push({
          product: path.basename(product),
          path: coveragePath,
          summary: summary.total || summary
        });
      } catch (error) {
        console.error(`Error reading ${coveragePath}:`, error.message);
      }
    }
  }
  
  return reports;
}

function calculateGap(current, target) {
  return Math.max(0, target - current);
}

function generateImprovementPlan(reports) {
  const plan = [];
  
  for (const report of reports) {
    const priority = PRODUCT_PRIORITIES[report.product] || 'low';
    const total = report.summary;
    
    if (!total) continue;
    
    const gaps = {
      lines: calculateGap(total.lines?.pct || 0, COVERAGE_TARGETS.lines),
      functions: calculateGap(total.functions?.pct || 0, COVERAGE_TARGETS.functions),
      branches: calculateGap(total.branches?.pct || 0, COVERAGE_TARGETS.branches),
      statements: calculateGap(total.statements?.pct || 0, COVERAGE_TARGETS.statements)
    };
    
    const maxGap = Math.max(...Object.values(gaps));
    
    if (maxGap > 0) {
      plan.push({
        product: report.product,
        priority,
        current: {
          lines: total.lines?.pct || 0,
          functions: total.functions?.pct || 0,
          branches: total.branches?.pct || 0,
          statements: total.statements?.pct || 0
        },
        gaps,
        effort: Math.ceil(maxGap * 2) // Rough estimate: 2 hours per %
      });
    }
  }
  
  return plan.sort((a, b) => {
    const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
    return priorityOrder[a.priority] - priorityOrder[b.priority];
  });
}

function printReport(reports, plan) {
  console.log('\n📊 TEST COVERAGE REPORT\n');
  console.log('=' .repeat(70));
  
  console.log('\n🎯 TARGETS:');
  for (const [metric, target] of Object.entries(CVERAGE_TARGETS)) {
    console.log(`  ${metric}: ${target}%`);
  }
  
  console.log('\n📈 CURRENT STATUS:');
  for (const report of reports) {
    const total = report.summary;
    if (!total) continue;
    
    const lines = total.lines?.pct || 0;
    const status = lines >= COVERAGE_TARGETS.lines ? '✅' : '❌';
    
    console.log(`\n  ${status} ${report.product.toUpperCase()}`);
    console.log(`     Lines: ${lines.toFixed(1)}% | Functions: ${(total.functions?.pct || 0).toFixed(1)}%`);
    console.log(`     Branches: ${(total.branches?.pct || 0).toFixed(1)}% | Statements: ${(total.statements?.pct || 0).toFixed(1)}%`);
  }
  
  console.log('\n🔧 IMPROVEMENT PLAN:');
  let totalEffort = 0;
  
  for (const item of plan) {
    const icon = item.priority === 'critical' ? '🔴' : item.priority === 'high' ? '🟡' : '🟢';
    console.log(`\n  ${icon} ${item.product.toUpperCase()} (${item.priority})`);
    console.log(`     Current: ${item.current.lines.toFixed(1)}% lines`);
    console.log(`     Gap: +${item.gaps.lines.toFixed(1)}% needed`);
    console.log(`     Estimated effort: ${item.effort} hours`);
    totalEffort += item.effort;
  }
  
  console.log(`\n  Total effort to reach targets: ~${totalEffort} hours`);
  console.log('=' .repeat(70));
}

function main() {
  console.log('🔍 Analyzing test coverage...\n');
  
  const reports = findCoverageReports();
  
  if (reports.length === 0) {
    console.log('No coverage reports found. Run tests with coverage first:');
    console.log('  pnpm test:coverage');
    return;
  }
  
  const plan = generateImprovementPlan(reports);
  printReport(reports, plan);
  
  // Save plan
  const planPath = path.join(process.cwd(), 'coverage-improvement-plan.json');
  fs.writeFileSync(planPath, JSON.stringify({
    generated: new Date().toISOString(),
    targets: COVERAGE_TARGETS,
    reports,
    plan
  }, null, 2));
  
  console.log(`\n📄 Plan saved to: ${planPath}\n`);
}

if (require.main === module) {
  main();
}

module.exports = { findCoverageReports, generateImprovementPlan };
