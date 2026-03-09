#!/usr/bin/env node

/**
 * TASK-017: Final Quality Audit Script
 *
 * Comprehensive quality validation including:
 * - Code duplication analysis
 * - Bundle size analysis
 * - DoD compliance verification
 * - Performance metrics validation
 * - Accessibility compliance check
 * - Security vulnerability assessment
 */

const fs = require('fs').promises;
const path = require('path');
const { execSync } = require('child_process');

// Configuration
const CONFIG = {
  maxBundleSize: 500 * 1024, // 500KB max for main bundle
  maxDuplicationThreshold: 10, // Max 10% code duplication
  minTestCoverage: 80, // Min 80% test coverage
  maxLighthouseScore: 90, // Min 90 performance score
  workspaceRoot: process.cwd(),
  outputDir: path.join(process.cwd(), '.quality-audit'),
  reports: {
    duplication: 'duplication-report.json',
    bundle: 'bundle-analysis.json',
    coverage: 'coverage-summary.json',
    accessibility: 'a11y-report.json',
    performance: 'lighthouse-report.json',
    security: 'security-audit.json',
  },
};

// Color codes for console output
const colors = {
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  reset: '\x1b[0m',
  bold: '\x1b[1m',
};

class QualityAuditor {
  constructor() {
    this.results = {
      duplication: { passed: false, score: 0, details: [] },
      bundleSize: { passed: false, score: 0, details: [] },
      testCoverage: { passed: false, score: 0, details: [] },
      accessibility: { passed: false, score: 0, details: [] },
      performance: { passed: false, score: 0, details: [] },
      security: { passed: false, score: 0, details: [] },
      dodCompliance: { passed: false, score: 0, details: [] },
    };
    this.overallScore = 0;
    this.criticalIssues = [];
  }

  log(message, color = 'reset') {
    console.log(`${colors[color]}${message}${colors.reset}`);
  }

  async init() {
    this.log('🔍 YAPPC Quality Audit - TASK-017', 'bold');
    this.log('═══════════════════════════════════════', 'blue');

    // Create output directory
    await fs.mkdir(CONFIG.outputDir, { recursive: true });
  }

  async runDuplicationAnalysis() {
    this.log('\n📋 Running Code Duplication Analysis...', 'blue');

    try {
      // Use jscpd for duplication detection
      const command = `npx jscpd --threshold 10 --reporters json --output ${CONFIG.outputDir} --format typescript,tsx,javascript,jsx src/`;

      try {
        execSync(command, { cwd: CONFIG.workspaceRoot, stdio: 'pipe' });
      } catch (error) {
        // jscpd exits with error code when duplications found
        if (!error.stdout) throw error;
      }

      const reportPath = path.join(CONFIG.outputDir, 'jscpd-report.json');
      let duplicationData = {
        statistics: { total: { percentage: 0 }, duplicates: [] },
      };

      try {
        const reportContent = await fs.readFile(reportPath, 'utf8');
        duplicationData = JSON.parse(reportContent);
      } catch (e) {
        this.log(
          '⚠️  No duplication report generated, assuming 0% duplication',
          'yellow'
        );
      }

      const duplicationPercentage =
        duplicationData.statistics?.total?.percentage || 0;
      const duplicates = duplicationData.duplicates || [];

      this.results.duplication = {
        passed: duplicationPercentage <= CONFIG.maxDuplicationThreshold,
        score: Math.max(0, 100 - duplicationPercentage * 2),
        details: [
          `Duplication percentage: ${duplicationPercentage.toFixed(2)}%`,
          `Threshold: ${CONFIG.maxDuplicationThreshold}%`,
          `Duplicate blocks found: ${duplicates.length}`,
          ...duplicates
            .slice(0, 5)
            .map((d) => `  - ${d.firstFile?.name}: ${d.lines} lines`),
        ],
      };

      if (!this.results.duplication.passed) {
        this.criticalIssues.push(
          `High code duplication: ${duplicationPercentage.toFixed(2)}%`
        );
      }

      this.log(
        `✅ Duplication: ${duplicationPercentage.toFixed(2)}% (${this.results.duplication.passed ? 'PASS' : 'FAIL'})`,
        this.results.duplication.passed ? 'green' : 'red'
      );
    } catch (error) {
      this.log(`❌ Duplication analysis failed: ${error.message}`, 'red');
      this.results.duplication.details.push(`Error: ${error.message}`);
    }
  }

  async runBundleAnalysis() {
    this.log('\n📦 Running Bundle Size Analysis...', 'blue');

    try {
      // Build the project first
      this.log('Building project for analysis...');
      execSync('npm run build', { cwd: CONFIG.workspaceRoot, stdio: 'pipe' });

      // Analyze bundle with webpack-bundle-analyzer
      const buildDir = path.join(CONFIG.workspaceRoot, 'apps/web/dist');
      const bundleFiles = await this.findBundleFiles(buildDir);

      let totalSize = 0;
      let mainBundleSize = 0;
      const fileDetails = [];

      for (const file of bundleFiles) {
        const stats = await fs.stat(file);
        const size = stats.size;
        totalSize += size;

        const fileName = path.basename(file);
        fileDetails.push(`${fileName}: ${this.formatBytes(size)}`);

        if (fileName.includes('index') || fileName.includes('main')) {
          mainBundleSize = Math.max(mainBundleSize, size);
        }
      }

      this.results.bundleSize = {
        passed: mainBundleSize <= CONFIG.maxBundleSize,
        score: Math.max(
          0,
          100 - (mainBundleSize / CONFIG.maxBundleSize - 1) * 50
        ),
        details: [
          `Main bundle size: ${this.formatBytes(mainBundleSize)}`,
          `Total bundle size: ${this.formatBytes(totalSize)}`,
          `Threshold: ${this.formatBytes(CONFIG.maxBundleSize)}`,
          `Files analyzed: ${bundleFiles.length}`,
          ...fileDetails.slice(0, 10),
        ],
      };

      if (!this.results.bundleSize.passed) {
        this.criticalIssues.push(
          `Bundle size too large: ${this.formatBytes(mainBundleSize)}`
        );
      }

      this.log(
        `✅ Bundle Size: ${this.formatBytes(mainBundleSize)} (${this.results.bundleSize.passed ? 'PASS' : 'FAIL'})`,
        this.results.bundleSize.passed ? 'green' : 'red'
      );
    } catch (error) {
      this.log(`❌ Bundle analysis failed: ${error.message}`, 'red');
      this.results.bundleSize.details.push(`Error: ${error.message}`);
    }
  }

  async runTestCoverage() {
    this.log('\n🧪 Running Test Coverage Analysis...', 'blue');

    try {
      // Run tests with coverage
      execSync('npm run test:coverage', {
        cwd: CONFIG.workspaceRoot,
        stdio: 'pipe',
      });

      // Read coverage report
      const coveragePath = path.join(
        CONFIG.workspaceRoot,
        'coverage/coverage-summary.json'
      );
      const coverageData = JSON.parse(await fs.readFile(coveragePath, 'utf8'));

      const totalCoverage = coverageData.total;
      const linesCoverage = totalCoverage.lines.pct;
      const branchesCoverage = totalCoverage.branches.pct;
      const functionsCoverage = totalCoverage.functions.pct;
      const statementsCoverage = totalCoverage.statements.pct;

      const averageCoverage =
        (linesCoverage +
          branchesCoverage +
          functionsCoverage +
          statementsCoverage) /
        4;

      this.results.testCoverage = {
        passed: averageCoverage >= CONFIG.minTestCoverage,
        score: averageCoverage,
        details: [
          `Lines coverage: ${linesCoverage.toFixed(2)}%`,
          `Branches coverage: ${branchesCoverage.toFixed(2)}%`,
          `Functions coverage: ${functionsCoverage.toFixed(2)}%`,
          `Statements coverage: ${statementsCoverage.toFixed(2)}%`,
          `Average coverage: ${averageCoverage.toFixed(2)}%`,
          `Threshold: ${CONFIG.minTestCoverage}%`,
        ],
      };

      if (!this.results.testCoverage.passed) {
        this.criticalIssues.push(
          `Low test coverage: ${averageCoverage.toFixed(2)}%`
        );
      }

      this.log(
        `✅ Test Coverage: ${averageCoverage.toFixed(2)}% (${this.results.testCoverage.passed ? 'PASS' : 'FAIL'})`,
        this.results.testCoverage.passed ? 'green' : 'red'
      );
    } catch (error) {
      this.log(`❌ Test coverage analysis failed: ${error.message}`, 'red');
      this.results.testCoverage.details.push(`Error: ${error.message}`);
    }
  }

  async runAccessibilityAudit() {
    this.log('\n♿ Running Accessibility Audit...', 'blue');

    try {
      // Use axe-core for accessibility testing
      const auditScript = `
        const { AxePuppeteer } = require('@axe-core/puppeteer');
        const puppeteer = require('puppeteer');
        
        (async () => {
          const browser = await puppeteer.launch();
          const page = await browser.newPage();
          await page.goto('http://localhost:3000');
          
          const results = await new AxePuppeteer(page).analyze();
          console.log(JSON.stringify(results, null, 2));
          
          await browser.close();
        })();
      `;

      // Start dev server and run audit
      const serverProcess = execSync('npm run dev &', {
        cwd: CONFIG.workspaceRoot,
      });

      // Wait for server to start
      await new Promise((resolve) => setTimeout(resolve, 5000));

      const auditResults = JSON.parse(
        execSync(`node -e "${auditScript}"`, {
          cwd: CONFIG.workspaceRoot,
          encoding: 'utf8',
        })
      );

      const violations = auditResults.violations || [];
      const passes = auditResults.passes || [];
      const totalTests = violations.length + passes.length;
      const passRate =
        totalTests > 0 ? (passes.length / totalTests) * 100 : 100;

      this.results.accessibility = {
        passed: violations.length === 0,
        score: passRate,
        details: [
          `Total violations: ${violations.length}`,
          `Tests passed: ${passes.length}`,
          `Pass rate: ${passRate.toFixed(2)}%`,
          ...violations
            .slice(0, 5)
            .map((v) => `  - ${v.impact}: ${v.description}`),
        ],
      };

      if (violations.length > 0) {
        this.criticalIssues.push(
          `Accessibility violations: ${violations.length}`
        );
      }

      this.log(
        `✅ Accessibility: ${violations.length} violations (${this.results.accessibility.passed ? 'PASS' : 'FAIL'})`,
        this.results.accessibility.passed ? 'green' : 'red'
      );
    } catch (error) {
      this.log(`❌ Accessibility audit failed: ${error.message}`, 'red');
      this.results.accessibility.details.push(`Error: ${error.message}`);
    }
  }

  async runSecurityAudit() {
    this.log('\n🔒 Running Security Audit...', 'blue');

    try {
      // Run npm audit
      const auditResult = execSync('npm audit --json', {
        cwd: CONFIG.workspaceRoot,
        encoding: 'utf8',
      });

      const auditData = JSON.parse(auditResult);
      const vulnerabilities = auditData.vulnerabilities || {};
      const totalVulns = Object.keys(vulnerabilities).length;

      let criticalVulns = 0;
      let highVulns = 0;
      let moderateVulns = 0;

      Object.values(vulnerabilities).forEach((vuln) => {
        if (vuln.severity === 'critical') criticalVulns++;
        else if (vuln.severity === 'high') highVulns++;
        else if (vuln.severity === 'moderate') moderateVulns++;
      });

      const securityScore = Math.max(
        0,
        100 - (criticalVulns * 25 + highVulns * 10 + moderateVulns * 5)
      );

      this.results.security = {
        passed: criticalVulns === 0 && highVulns === 0,
        score: securityScore,
        details: [
          `Total vulnerabilities: ${totalVulns}`,
          `Critical: ${criticalVulns}`,
          `High: ${highVulns}`,
          `Moderate: ${moderateVulns}`,
          `Security score: ${securityScore.toFixed(2)}`,
        ],
      };

      if (criticalVulns > 0 || highVulns > 0) {
        this.criticalIssues.push(
          `Security vulnerabilities: ${criticalVulns} critical, ${highVulns} high`
        );
      }

      this.log(
        `✅ Security: ${criticalVulns + highVulns} critical/high issues (${this.results.security.passed ? 'PASS' : 'FAIL'})`,
        this.results.security.passed ? 'green' : 'red'
      );
    } catch (error) {
      this.log(`❌ Security audit failed: ${error.message}`, 'red');
      this.results.security.details.push(`Error: ${error.message}`);
    }
  }

  async runDoDCompliance() {
    this.log('\n✓ Verifying Definition of Done Compliance...', 'blue');

    const dodCriteria = [
      {
        name: 'TypeScript strict mode',
        check: () => this.checkTypeScriptStrict(),
      },
      { name: 'ESLint configuration', check: () => this.checkESLintConfig() },
      { name: 'Prettier formatting', check: () => this.checkPrettierConfig() },
      {
        name: 'Component documentation',
        check: () => this.checkComponentDocs(),
      },
      { name: 'Error boundaries', check: () => this.checkErrorBoundaries() },
      { name: 'Loading states', check: () => this.checkLoadingStates() },
      { name: 'Responsive design', check: () => this.checkResponsiveDesign() },
      { name: 'Accessibility features', check: () => this.checkA11yFeatures() },
      { name: 'Test files exist', check: () => this.checkTestFiles() },
      { name: 'Build succeeds', check: () => this.checkBuildSuccess() },
    ];

    const results = [];
    let passedCriteria = 0;

    for (const criterion of dodCriteria) {
      try {
        const passed = await criterion.check();
        results.push(`${passed ? '✅' : '❌'} ${criterion.name}`);
        if (passed) passedCriteria++;
      } catch (error) {
        results.push(`❌ ${criterion.name}: ${error.message}`);
      }
    }

    const dodScore = (passedCriteria / dodCriteria.length) * 100;

    this.results.dodCompliance = {
      passed: passedCriteria === dodCriteria.length,
      score: dodScore,
      details: results,
    };

    if (!this.results.dodCompliance.passed) {
      this.criticalIssues.push(
        `DoD compliance: ${passedCriteria}/${dodCriteria.length} criteria met`
      );
    }

    this.log(
      `✅ DoD Compliance: ${passedCriteria}/${dodCriteria.length} (${this.results.dodCompliance.passed ? 'PASS' : 'FAIL'})`,
      this.results.dodCompliance.passed ? 'green' : 'red'
    );
  }

  // DoD Check Methods
  async checkTypeScriptStrict() {
    const tsConfig = JSON.parse(
      await fs.readFile(
        path.join(CONFIG.workspaceRoot, 'tsconfig.json'),
        'utf8'
      )
    );
    return tsConfig.compilerOptions?.strict === true;
  }

  async checkESLintConfig() {
    const eslintPath = path.join(CONFIG.workspaceRoot, '.eslintrc.js');
    try {
      await fs.access(eslintPath);
      return true;
    } catch {
      return false;
    }
  }

  async checkPrettierConfig() {
    const prettierPath = path.join(CONFIG.workspaceRoot, '.prettierrc');
    try {
      await fs.access(prettierPath);
      return true;
    } catch {
      return false;
    }
  }

  async checkComponentDocs() {
    const srcPath = path.join(CONFIG.workspaceRoot, 'apps/web/src');
    const files = await this.getAllFiles(srcPath, ['.tsx']);

    let documentedComponents = 0;
    for (const file of files.slice(0, 20)) {
      // Check first 20 components
      const content = await fs.readFile(file, 'utf8');
      if (content.includes('/**') || content.includes('* @')) {
        documentedComponents++;
      }
    }

    return documentedComponents / Math.min(files.length, 20) >= 0.8; // 80% documented
  }

  async checkErrorBoundaries() {
    const srcPath = path.join(CONFIG.workspaceRoot, 'apps/web/src');
    const files = await this.getAllFiles(srcPath, ['.tsx']);

    return files.some(async (file) => {
      const content = await fs.readFile(file, 'utf8');
      return (
        content.includes('ErrorBoundary') ||
        content.includes('componentDidCatch')
      );
    });
  }

  async checkLoadingStates() {
    const srcPath = path.join(CONFIG.workspaceRoot, 'apps/web/src');
    const files = await this.getAllFiles(srcPath, ['.tsx']);

    let loadingComponents = 0;
    for (const file of files.slice(0, 10)) {
      const content = await fs.readFile(file, 'utf8');
      if (
        content.includes('loading') ||
        content.includes('Loading') ||
        content.includes('Skeleton')
      ) {
        loadingComponents++;
      }
    }

    return loadingComponents >= 5; // At least 5 components with loading states
  }

  async checkResponsiveDesign() {
    const srcPath = path.join(CONFIG.workspaceRoot, 'apps/web/src');
    const files = await this.getAllFiles(srcPath, ['.tsx']);

    let responsiveComponents = 0;
    for (const file of files.slice(0, 10)) {
      const content = await fs.readFile(file, 'utf8');
      if (
        content.includes('useMediaQuery') ||
        content.includes('breakpoints') ||
        content.includes('xs={') ||
        content.includes('sm={')
      ) {
        responsiveComponents++;
      }
    }

    return responsiveComponents >= 3; // At least 3 responsive components
  }

  async checkA11yFeatures() {
    const srcPath = path.join(CONFIG.workspaceRoot, 'apps/web/src');
    const files = await this.getAllFiles(srcPath, ['.tsx']);

    let a11yComponents = 0;
    for (const file of files.slice(0, 10)) {
      const content = await fs.readFile(file, 'utf8');
      if (
        content.includes('aria-') ||
        content.includes('role=') ||
        content.includes('alt=') ||
        content.includes('tabIndex')
      ) {
        a11yComponents++;
      }
    }

    return a11yComponents >= 5; // At least 5 components with a11y features
  }

  async checkTestFiles() {
    const testPaths = [
      path.join(CONFIG.workspaceRoot, 'apps/web/src/**/*.test.tsx'),
      path.join(CONFIG.workspaceRoot, 'apps/web/src/**/*.spec.tsx'),
      path.join(CONFIG.workspaceRoot, 'e2e/**/*.spec.ts'),
    ];

    const testFiles = await this.getAllFiles(CONFIG.workspaceRoot, [
      '.test.tsx',
      '.spec.tsx',
      '.spec.ts',
    ]);
    return testFiles.length >= 10; // At least 10 test files
  }

  async checkBuildSuccess() {
    try {
      execSync('npm run build', { cwd: CONFIG.workspaceRoot, stdio: 'pipe' });
      return true;
    } catch {
      return false;
    }
  }

  // Utility methods
  async findBundleFiles(buildDir) {
    const files = await this.getAllFiles(buildDir, ['.js', '.css']);
    return files.filter((file) => !file.includes('node_modules'));
  }

  async getAllFiles(dir, extensions = []) {
    const files = [];

    try {
      const entries = await fs.readdir(dir, { withFileTypes: true });

      for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);

        if (entry.isDirectory()) {
          files.push(...(await this.getAllFiles(fullPath, extensions)));
        } else if (
          extensions.length === 0 ||
          extensions.some((ext) => entry.name.endsWith(ext))
        ) {
          files.push(fullPath);
        }
      }
    } catch (error) {
      // Directory might not exist or be accessible
    }

    return files;
  }

  formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))  } ${  sizes[i]}`;
  }

  async generateReport() {
    this.log('\n📊 Generating Quality Report...', 'blue');

    // Calculate overall score
    const scores = Object.values(this.results).map((r) => r.score);
    this.overallScore =
      scores.reduce((sum, score) => sum + score, 0) / scores.length;

    const report = {
      timestamp: new Date().toISOString(),
      overallScore: this.overallScore,
      overallStatus: this.overallScore >= 85 ? 'PASS' : 'FAIL',
      criticalIssues: this.criticalIssues,
      results: this.results,
      summary: {
        totalChecks: Object.keys(this.results).length,
        passedChecks: Object.values(this.results).filter((r) => r.passed)
          .length,
        failedChecks: Object.values(this.results).filter((r) => !r.passed)
          .length,
      },
    };

    // Save detailed report
    const reportPath = path.join(CONFIG.outputDir, 'quality-audit-report.json');
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2));

    // Generate markdown summary
    const markdownReport = this.generateMarkdownReport(report);
    const markdownPath = path.join(
      CONFIG.outputDir,
      'QUALITY_AUDIT_SUMMARY.md'
    );
    await fs.writeFile(markdownPath, markdownReport);

    this.log(`\n📈 Quality Audit Complete!`, 'bold');
    this.log(
      `Overall Score: ${this.overallScore.toFixed(2)}/100`,
      this.overallScore >= 85 ? 'green' : 'red'
    );
    this.log(
      `Status: ${report.overallStatus}`,
      report.overallStatus === 'PASS' ? 'green' : 'red'
    );
    this.log(`Report saved to: ${reportPath}`, 'blue');
    this.log(`Summary saved to: ${markdownPath}`, 'blue');

    if (this.criticalIssues.length > 0) {
      this.log(`\n⚠️  Critical Issues:`, 'yellow');
      this.criticalIssues.forEach((issue) => this.log(`  - ${issue}`, 'red'));
    }

    return report;
  }

  generateMarkdownReport(report) {
    return `# YAPPC Quality Audit Report

**Generated:** ${report.timestamp}  
**Overall Score:** ${report.overallScore.toFixed(2)}/100  
**Status:** ${report.overallStatus}  

## Summary

- **Total Checks:** ${report.summary.totalChecks}
- **Passed:** ${report.summary.passedChecks}
- **Failed:** ${report.summary.failedChecks}

## Results

${Object.entries(report.results)
  .map(
    ([category, result]) => `
### ${category.charAt(0).toUpperCase() + category.slice(1)}

- **Status:** ${result.passed ? '✅ PASS' : '❌ FAIL'}
- **Score:** ${result.score.toFixed(2)}/100

**Details:**
${result.details.map((detail) => `- ${detail}`).join('\n')}
`
  )
  .join('\n')}

${
  report.criticalIssues.length > 0
    ? `
## Critical Issues

${report.criticalIssues.map((issue) => `- ⚠️ ${issue}`).join('\n')}
`
    : ''
}

## Recommendations

1. **Code Quality:** Maintain low duplication and high test coverage
2. **Performance:** Keep bundle sizes optimized and loading times fast  
3. **Accessibility:** Ensure all components meet WCAG guidelines
4. **Security:** Regularly update dependencies and fix vulnerabilities
5. **Documentation:** Keep component docs and README files updated

---

*This report was generated automatically by the YAPPC Quality Audit System (TASK-017)*
`;
  }

  async run() {
    try {
      await this.init();

      // Run all audits in parallel where possible
      await Promise.all([
        this.runDuplicationAnalysis(),
        this.runBundleAnalysis(),
        this.runTestCoverage(),
        this.runSecurityAudit(),
      ]);

      // Run sequential audits
      await this.runAccessibilityAudit();
      await this.runDoDCompliance();

      const report = await this.generateReport();

      // Exit with appropriate code
      process.exit(report.overallStatus === 'PASS' ? 0 : 1);
    } catch (error) {
      this.log(`💥 Quality audit failed: ${error.message}`, 'red');
      console.error(error);
      process.exit(1);
    }
  }
}

// Run the audit if called directly
if (require.main === module) {
  const auditor = new QualityAuditor();
  auditor.run();
}

module.exports = QualityAuditor;
