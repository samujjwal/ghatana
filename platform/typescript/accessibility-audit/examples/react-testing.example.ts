// Example: Component Accessibility Testing
// Copy this into your test files and adapt to your components

import { render, screen } from '@testing-library/react';
import { auditTarget } from '@ghatana/accessibility-audit';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

/**
 * QUICK START: Copy this test template for your components
 */

describe('MyComponent Accessibility', () => {
  // Test 1: Basic accessibility audit
  it('should pass accessibility audit', async () => {
    const { container } = render(<MyComponent />);
    
    // Run audit with WCAG AA standard
    const report = await auditTarget(container, 'AA');
    
    // Check score
    console.log(`Score: ${report.score.overall}/100 (${report.score.grade})`);
    expect(report.score.overall).toBeGreaterThanOrEqual(75);
  });

  // Test 2: No critical issues
  it('should have no critical accessibility issues', async () => {
    const { container } = render(<MyComponent />);
    const report = await auditTarget(container, 'AA');
    
    expect(report.summary.bySeverity.critical).toBe(0);
  });

  // Test 3: Check specific dimension
  it('should have good keyboard accessibility', async () => {
    const { container } = render(<MyComponent />);
    const report = await auditTarget(container, 'AA');
    
    const keyboardDim = report.score.dimensions.find(
      d => d.name === 'keyboardAccessibility'
    );
    
    expect(keyboardDim?.score).toBeGreaterThanOrEqual(80);
  });
});

/**
 * BATCH TESTING: Test multiple components
 */

async function testComponentLibrary() {
  const components = [
    { name: 'Button', Component: Button },
    { name: 'Form', Component: Form },
    { name: 'Modal', Component: Modal },
  ];

  const results = [];

  for (const { name, Component } of components) {
    const { container } = render(<Component />);
    const report = await auditTarget(container, 'AA');
    
    results.push({
      name,
      score: report.score.overall,
      passed: report.score.overall >= 75,
      critical: report.summary.bySeverity.critical,
    });
  }

  return results;
}

/**
 * PAGE TESTING: Test entire pages
 */

async function testPageAccessibility(html: string) {
  const report = await auditTarget(html, 'AA');
  
  return {
    score: report.score.overall,
    grade: report.score.grade,
    critical: report.summary.bySeverity.critical,
    findings: report.findings.length,
    recommendations: report.recommendations,
  };
}

/**
 * DETAILED ANALYSIS: Get specific information
 */

async function analyzeComponent(element: Element) {
  const report = await auditTarget(element, 'AA');
  
  console.log('ACCESSIBILITY REPORT');
  console.log('===================');
  console.log(`Overall Score: ${report.score.overall}/100`);
  console.log(`Grade: ${report.score.grade}`);
  console.log(`Critical Issues: ${report.summary.bySeverity.critical}`);
  console.log(`Serious Issues: ${report.summary.bySeverity.serious}`);
  console.log(`Total Findings: ${report.findings.length}`);
  
  console.log('\nDIMENSIONAL BREAKDOWN:');
  report.score.dimensions.forEach(dim => {
    console.log(`  ${dim.name}: ${dim.score}/100 (${dim.grade})`);
  });
  
  if (report.recommendations.immediate.length > 0) {
    console.log('\nIMMEDIATE FIXES NEEDED:');
    report.recommendations.immediate.forEach(r => {
      console.log(`  • ${r}`);
    });
  }
  
  return report;
}

/**
 * CI/CD HELPER: Check if component passes thresholds
 */

function assessComponentQuality(report: AccessibilityReport) {
  const thresholds = {
    score: 75,           // Overall score must be at least 75
    critical: 0,         // No critical issues allowed
    serious: 5,          // Max 5 serious issues
  };

  const issues = [];
  
  if (report.score.overall < thresholds.score) {
    issues.push(
      `Score ${report.score.overall} is below threshold ${thresholds.score}`
    );
  }
  
  if (report.summary.bySeverity.critical > thresholds.critical) {
    issues.push(
      `Found ${report.summary.bySeverity.critical} critical issues`
    );
  }
  
  if (report.summary.bySeverity.serious > thresholds.serious) {
    issues.push(
      `Found ${report.summary.bySeverity.serious} serious issues (max: ${thresholds.serious})`
    );
  }

  return {
    passed: issues.length === 0,
    issues,
    score: report.score.overall,
    grade: report.score.grade,
  };
}

/**
 * EXPORT REPORT: Generate report files
 */

async function generateReport(element: Element, filename: string) {
  const { auditTargetToFormat } = await import('@ghatana/accessibility-audit');
  
  // Generate JSON report
  const json = await auditTargetToFormat(element, 'json', 'AA');
  fs.writeFileSync(`${filename}.json`, json);
  
  // Generate HTML report
  const html = await auditTargetToFormat(element, 'html', 'AA');
  fs.writeFileSync(`${filename}.html`, html);
  
  // Generate SARIF for GitHub Code Scanning
  const sarif = await auditTargetToFormat(element, 'sarif', 'AA');
  fs.writeFileSync(`${filename}.sarif`, sarif);
  
  console.log(`✅ Reports generated: ${filename}.*`);
}

/**
 * USAGE EXAMPLES
 */

// Example 1: Simple component test
async function example1() {
  const { container } = render(<Button />);
  const report = await auditTarget(container, 'AA');
  console.log(`Button score: ${report.score.overall}/100`);
}

// Example 2: Test all components
async function example2() {
  const results = await testComponentLibrary();
  results.forEach(r => {
    console.log(`${r.name}: ${r.score}/100 - ${r.passed ? '✅' : '❌'}`);
  });
}

// Example 3: Detailed analysis
async function example3() {
  const form = document.querySelector('form');
  if (form) {
    await analyzeComponent(form);
  }
}

// Example 4: Quality assessment
async function example4() {
  const { container } = render(<MyComponent />);
  const report = await auditTarget(container, 'AA');
  const assessment = assessComponentQuality(report);
  
  if (assessment.passed) {
    console.log('✅ Component passes accessibility checks');
  } else {
    console.log('❌ Component has accessibility issues:');
    assessment.issues.forEach(i => console.log(`  - ${i}`));
  }
}

export {
  testComponentLibrary,
  testPageAccessibility,
  analyzeComponent,
  assessComponentQuality,
  generateReport,
};
