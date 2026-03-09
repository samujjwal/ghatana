/**
 * Basic usage examples for accessibility-audit library
 * Run with: tsx examples/basic-usage.ts
 */

import { AccessibilityAuditor, runQuickAudit, runAuditWithFormat } from '../src/index';

// Example 1: Quick Audit
async function quickAuditExample() {
  console.log('\n=== Example 1: Quick Audit ===\n');
  
  try {
    const report = await runQuickAudit();
    console.log(`Overall Score: ${report.score.overall}/100 (${report.score.grade})`);
    console.log(`Total Issues: ${report.summary.total}`);
    console.log(`Critical: ${report.summary.bySeverity.critical}`);
    console.log(`Serious: ${report.summary.bySeverity.serious}`);
  } catch (error) {
    console.error('Audit failed:', error);
  }
}

// Example 2: Audit with Custom Configuration
async function customConfigExample() {
  console.log('\n=== Example 2: Custom Configuration ===\n');
  
  const auditor = new AccessibilityAuditor({
    mode: 'comprehensive',
    wcagLevel: 'AAA',
    outputFormats: ['json', 'html'],
    thresholds: {
      overall: 90,
      critical: 0,
      serious: 3,
    },
  });

  const report = await auditor.audit();
  console.log(`WCAG Level: ${report.metadata.wcagLevel}`);
  console.log(`Mode: ${report.metadata.mode}`);
  console.log(`Score: ${report.score.overall}/100`);
  
  // Check if thresholds are met
  const meetsThreshold = report.score.overall >= 90;
  console.log(`Meets threshold: ${meetsThreshold ? '✓' : '✗'}`);
}

// Example 3: Export to Different Formats
async function exportExample() {
  console.log('\n=== Example 3: Export to Different Formats ===\n');
  
  const auditor = new AccessibilityAuditor();
  const report = await auditor.audit();

  // Export as JSON
  const json = auditor.exportReport(report, 'json');
  console.log(`JSON export size: ${json.length} bytes`);

  // Export as HTML
  const html = auditor.exportReport(report, 'html');
  console.log(`HTML export size: ${html.length} bytes`);

  // Export as SARIF (for CI/CD)
  const sarif = auditor.exportReport(report, 'sarif');
  console.log(`SARIF export size: ${sarif.length} bytes`);

  // Export as Markdown
  const markdown = auditor.exportReport(report, 'markdown');
  console.log(`Markdown export size: ${markdown.length} bytes`);
}

// Example 4: Trend Analysis
async function trendAnalysisExample() {
  console.log('\n=== Example 4: Trend Analysis ===\n');
  
  const auditor = new AccessibilityAuditor();

  // First audit (baseline)
  const report1 = await auditor.audit();
  console.log(`Initial score: ${report1.score.overall}/100`);

  // Simulate some time passing and improvements made
  // In real scenario, you would audit again after changes
  const report2 = await auditor.audit();

  // Compare reports
  const comparison = auditor.compareReports(report2, report1);
  console.log(`\nTrend: ${comparison.trend.direction}`);
  console.log(`Change: ${comparison.trend.change > 0 ? '+' : ''}${comparison.trend.change.toFixed(1)} points`);
  console.log(`Previous: ${comparison.previous.score.overall}/100`);
  console.log(`Current: ${comparison.current.score.overall}/100`);
}

// Example 5: Detailed Score Breakdown
async function scoreBreakdownExample() {
  console.log('\n=== Example 5: Score Breakdown ===\n');
  
  const report = await runQuickAudit();
  
  console.log('Dimension Scores:');
  console.log(`  WCAG Compliance: ${report.score.dimensions.wcagCompliance}/100`);
  console.log(`  Keyboard Accessibility: ${report.score.dimensions.keyboardAccessibility}/100`);
  console.log(`  Semantic Structure: ${report.score.dimensions.semanticStructure}/100`);
  console.log(`  Visual Accessibility: ${report.score.dimensions.visualAccessibility}/100`);
  console.log(`  Form Accessibility: ${report.score.dimensions.formAccessibility}/100`);
  console.log(`  Media Accessibility: ${report.score.dimensions.mediaAccessibility}/100`);
  console.log(`  ARIA Implementation: ${report.score.dimensions.ariaImplementation}/100`);
  console.log(`  Focus Management: ${report.score.dimensions.focusManagement}/100`);
}

// Example 6: Recommendations
async function recommendationsExample() {
  console.log('\n=== Example 6: Recommendations ===\n');
  
  const report = await runQuickAudit();
  
  console.log('Immediate Actions:');
  report.recommendations.immediate.forEach((rec, i) => {
    console.log(`  ${i + 1}. ${rec}`);
  });

  console.log('\nShort-term Goals:');
  report.recommendations.shortTerm.forEach((rec, i) => {
    console.log(`  ${i + 1}. ${rec}`);
  });

  console.log('\nLong-term Strategy:');
  report.recommendations.longTerm.forEach((rec, i) => {
    console.log(`  ${i + 1}. ${rec}`);
  });
}

// Example 7: CI/CD Integration
async function cicdExample() {
  console.log('\n=== Example 7: CI/CD Integration ===\n');
  
  const auditor = new AccessibilityAuditor({
    mode: 'comprehensive',
    wcagLevel: 'AA',
    thresholds: {
      overall: 80,
      critical: 0,
      serious: 5,
    },
  });

  const report = await auditor.audit();
  
  // Export SARIF for GitHub Code Scanning
  const sarif = auditor.exportReport(report, 'sarif');
  console.log('SARIF report generated for CI/CD');
  
  // Check if build should fail
  const criticalViolations = report.summary.bySeverity.critical;
  const seriousViolations = report.summary.bySeverity.serious;
  
  if (criticalViolations > 0 || seriousViolations > 5 || report.score.overall < 80) {
    console.error('❌ Build failed: Accessibility thresholds not met');
    console.error(`  Critical: ${criticalViolations} (threshold: 0)`);
    console.error(`  Serious: ${seriousViolations} (threshold: 5)`);
    console.error(`  Score: ${report.score.overall}/100 (threshold: 80)`);
    process.exit(1);
  } else {
    console.log('✓ Build passed: All accessibility thresholds met');
  }
}

// Example 8: Console Report
async function consoleReportExample() {
  console.log('\n=== Example 8: Console Report ===\n');
  
  const auditor = new AccessibilityAuditor();
  const report = await auditor.audit();
  
  // Generate and display formatted console report
  const consoleReport = auditor.generateReport(report);
  console.log(consoleReport);
}

// Run all examples
async function runAllExamples() {
  console.log('╔═══════════════════════════════════════════════════════════╗');
  console.log('║   Accessibility Audit Library - Usage Examples           ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');

  await quickAuditExample();
  await customConfigExample();
  await exportExample();
  await trendAnalysisExample();
  await scoreBreakdownExample();
  await recommendationsExample();
  await cicdExample();
  await consoleReportExample();

  console.log('\n✓ All examples completed!\n');
}

// Run if executed directly
if (import.meta.url === `file://${process.argv[1]}`) {
  runAllExamples().catch(console.error);
}

export {
  quickAuditExample,
  customConfigExample,
  exportExample,
  trendAnalysisExample,
  scoreBreakdownExample,
  recommendationsExample,
  cicdExample,
  consoleReportExample,
};