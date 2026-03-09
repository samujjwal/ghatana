#!/usr/bin/env node

/**
 * Security Scanning Script
 * 
 * This script performs security scanning for the YAPPC App Creator.
 * It checks for vulnerabilities in dependencies, code, and build output.
 */

const path = require('path');
const fs = require('fs-extra');
const chalk = require('chalk');
const { execSync } = require('child_process');

// Parse command line arguments
const argv = process.argv.slice(2);
const scanType = argv[0] || 'all';
const reportFormat = argv[1] || 'text';
const failOnIssues = argv.includes('--fail-on-issues');

// Configuration
const reportsDir = path.join(process.cwd(), 'security-reports');
fs.ensureDirSync(reportsDir);

// Timestamp for reports
const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

/**
 * Run command and return output
 */
function runCommand(command, options = {}) {
  try {
    return execSync(command, { encoding: 'utf8', ...options });
  } catch (error) {
    if (options.ignoreError) {
      return error.stdout;
    }
    throw error;
  }
}

/**
 * Save report to file
 */
function saveReport(name, content, format) {
  const reportPath = path.join(reportsDir, `${name}-${timestamp}.${format}`);
  fs.writeFileSync(reportPath, content);
  console.log(chalk.green(`✓ Report saved to ${reportPath}`));
  return reportPath;
}

/**
 * Scan dependencies for vulnerabilities
 */
async function scanDependencies() {
  console.log(chalk.bold('Scanning dependencies for vulnerabilities...'));

  try {
    // Run npm audit
    console.log('Running npm audit...');
    const npmAuditOutput = runCommand('npm audit --json', { ignoreError: true });
    const npmAuditReport = JSON.parse(npmAuditOutput);
    
    // Save npm audit report
    saveReport('npm-audit', npmAuditOutput, 'json');
    
    // Count vulnerabilities by severity
    const vulnerabilities = {
      critical: 0,
      high: 0,
      moderate: 0,
      low: 0,
      info: 0,
    };
    
    if (npmAuditReport.vulnerabilities) {
      Object.values(npmAuditReport.vulnerabilities).forEach((vuln) => {
        vulnerabilities[vuln.severity] = (vulnerabilities[vuln.severity] || 0) + 1;
      });
    }
    
    // Print summary
    console.log(chalk.bold('Dependency vulnerabilities:'));
    console.log(`  Critical: ${vulnerabilities.critical}`);
    console.log(`  High: ${vulnerabilities.high}`);
    console.log(`  Moderate: ${vulnerabilities.moderate}`);
    console.log(`  Low: ${vulnerabilities.low}`);
    console.log(`  Info: ${vulnerabilities.info}`);
    
    // Check for critical or high vulnerabilities
    const hasCriticalOrHigh = vulnerabilities.critical > 0 || vulnerabilities.high > 0;
    
    if (hasCriticalOrHigh) {
      console.warn(chalk.yellow('⚠ Critical or high severity vulnerabilities found.'));
      
      if (failOnIssues) {
        console.error(chalk.red('✗ Security scan failed due to critical or high severity vulnerabilities.'));
        process.exit(1);
      }
    } else {
      console.log(chalk.green('✓ No critical or high severity vulnerabilities found.'));
    }
    
    return {
      vulnerabilities,
      hasCriticalOrHigh,
    };
  } catch (error) {
    console.error(chalk.red('Error scanning dependencies:'), error.message);
    return {
      vulnerabilities: {},
      hasCriticalOrHigh: false,
      error: error.message,
    };
  }
}

/**
 * Scan code for security issues
 */
async function scanCode() {
  console.log(chalk.bold('\nScanning code for security issues...'));

  try {
    // Check if ESLint is installed with security plugin
    const hasEslintSecurity = fs.existsSync(path.join(process.cwd(), 'node_modules/eslint-plugin-security'));
    
    if (!hasEslintSecurity) {
      console.warn(chalk.yellow('⚠ eslint-plugin-security not found. Skipping code security scan.'));
      return {
        issues: [],
        hasIssues: false,
      };
    }
    
    // Run ESLint with security plugin
    console.log('Running ESLint with security plugin...');
    const eslintOutput = runCommand('npx eslint --ext .js,.jsx,.ts,.tsx --no-eslintrc --config .eslintrc.security.js src', { ignoreError: true });
    
    // Save ESLint report
    saveReport('eslint-security', eslintOutput, 'txt');
    
    // Count issues
    const issues = eslintOutput.split('\n').filter((line) => line.includes('error') || line.includes('warning'));
    const hasIssues = issues.length > 0;
    
    // Print summary
    console.log(chalk.bold('Code security issues:'));
    console.log(`  Total issues: ${issues.length}`);
    
    if (hasIssues) {
      console.warn(chalk.yellow('⚠ Security issues found in code.'));
      
      if (failOnIssues) {
        console.error(chalk.red('✗ Security scan failed due to code security issues.'));
        process.exit(1);
      }
    } else {
      console.log(chalk.green('✓ No security issues found in code.'));
    }
    
    return {
      issues,
      hasIssues,
    };
  } catch (error) {
    console.error(chalk.red('Error scanning code:'), error.message);
    return {
      issues: [],
      hasIssues: false,
      error: error.message,
    };
  }
}

/**
 * Scan build output for security issues
 */
async function scanBuild() {
  console.log(chalk.bold('\nScanning build output for security issues...'));

  const buildDir = path.join(process.cwd(), 'apps/web/dist');
  
  if (!fs.existsSync(buildDir)) {
    console.warn(chalk.yellow('⚠ Build directory not found. Run build script first.'));
    return {
      issues: [],
      hasIssues: false,
    };
  }
  
  try {
    // Check for sensitive information in build output
    console.log('Checking for sensitive information...');
    
    const sensitivePatterns = [
      'password',
      'secret',
      'api[_\\s-]?key',
      'token',
      'auth',
      'credential',
      'private[_\\s-]?key',
    ];
    
    const grepPattern = sensitivePatterns.join('|');
    const grepCommand = `grep -r -i -E '(${grepPattern})' --include="*.js" --include="*.css" --include="*.html" ${buildDir}`;
    
    const grepOutput = runCommand(grepCommand, { ignoreError: true });
    const sensitiveMatches = grepOutput.split('\n').filter(Boolean);
    
    // Save report
    saveReport('sensitive-info', grepOutput, 'txt');
    
    // Check for source maps in production build
    console.log('Checking for source maps...');
    const hasSourceMaps = fs.existsSync(path.join(buildDir, '*.js.map'));
    
    // Check for unminified JavaScript
    console.log('Checking for unminified JavaScript...');
    const jsFiles = fs.readdirSync(buildDir, { recursive: true })
      .filter(file => file.endsWith('.js') && !file.endsWith('.min.js'));
    
    const unminifiedJs = [];
    
    for (const file of jsFiles) {
      const filePath = path.join(buildDir, file);
      const content = fs.readFileSync(filePath, 'utf8');
      
      // Check if file is minified (heuristic: few newlines relative to file size)
      const newlineCount = (content.match(/\n/g) || []).length;
      const isMinified = newlineCount < content.length / 500;
      
      if (!isMinified) {
        unminifiedJs.push(file);
      }
    }
    
    // Check for insecure CSP in index.html
    console.log('Checking for insecure Content Security Policy...');
    const indexPath = path.join(buildDir, 'index.html');
    let hasInsecureCsp = false;
    
    if (fs.existsSync(indexPath)) {
      const indexContent = fs.readFileSync(indexPath, 'utf8');
      const hasCsp = indexContent.includes('Content-Security-Policy');
      
      if (!hasCsp) {
        hasInsecureCsp = true;
      } else {
        // Check for unsafe CSP directives
        const unsafeCspDirectives = [
          'unsafe-inline',
          'unsafe-eval',
          'data:',
          '*',
        ];
        
        hasInsecureCsp = unsafeCspDirectives.some(directive => indexContent.includes(directive));
      }
    }
    
    // Collect all issues
    const issues = [
      ...sensitiveMatches.map(match => `Sensitive information: ${match}`),
      ...(hasSourceMaps ? ['Source maps found in production build'] : []),
      ...unminifiedJs.map(file => `Unminified JavaScript: ${file}`),
      ...(hasInsecureCsp ? ['Insecure Content Security Policy'] : []),
    ];
    
    const hasIssues = issues.length > 0;
    
    // Print summary
    console.log(chalk.bold('Build security issues:'));
    console.log(`  Sensitive information: ${sensitiveMatches.length}`);
    console.log(`  Source maps: ${hasSourceMaps ? 'Yes' : 'No'}`);
    console.log(`  Unminified JavaScript: ${unminifiedJs.length}`);
    console.log(`  Insecure CSP: ${hasInsecureCsp ? 'Yes' : 'No'}`);
    
    if (hasIssues) {
      console.warn(chalk.yellow('⚠ Security issues found in build output.'));
      
      if (failOnIssues) {
        console.error(chalk.red('✗ Security scan failed due to build security issues.'));
        process.exit(1);
      }
    } else {
      console.log(chalk.green('✓ No security issues found in build output.'));
    }
    
    return {
      issues,
      hasIssues,
    };
  } catch (error) {
    console.error(chalk.red('Error scanning build:'), error.message);
    return {
      issues: [],
      hasIssues: false,
      error: error.message,
    };
  }
}

/**
 * Generate security report
 */
function generateReport(results) {
  console.log(chalk.bold('\nGenerating security report...'));
  
  const report = {
    timestamp: new Date().toISOString(),
    summary: {
      dependencies: {
        critical: results.dependencies.vulnerabilities.critical || 0,
        high: results.dependencies.vulnerabilities.high || 0,
        moderate: results.dependencies.vulnerabilities.moderate || 0,
        low: results.dependencies.vulnerabilities.low || 0,
        info: results.dependencies.vulnerabilities.info || 0,
        hasIssues: results.dependencies.hasCriticalOrHigh,
      },
      code: {
        issues: results.code.issues.length,
        hasIssues: results.code.hasIssues,
      },
      build: {
        issues: results.build.issues.length,
        hasIssues: results.build.hasIssues,
      },
    },
    details: {
      dependencies: results.dependencies,
      code: results.code,
      build: results.build,
    },
  };
  
  // Save report
  const reportPath = saveReport('security-report', JSON.stringify(report, null, 2), 'json');
  
  // Print summary
  console.log(chalk.bold('\nSecurity scan summary:'));
  console.log(`  Dependencies: ${report.summary.dependencies.hasIssues ? chalk.yellow('⚠ Issues found') : chalk.green('✓ No issues')}`);
  console.log(`  Code: ${report.summary.code.hasIssues ? chalk.yellow('⚠ Issues found') : chalk.green('✓ No issues')}`);
  console.log(`  Build: ${report.summary.build.hasIssues ? chalk.yellow('⚠ Issues found') : chalk.green('✓ No issues')}`);
  
  const hasIssues = report.summary.dependencies.hasIssues || report.summary.code.hasIssues || report.summary.build.hasIssues;
  
  if (hasIssues) {
    console.warn(chalk.yellow('\n⚠ Security issues found. See report for details.'));
    
    if (failOnIssues) {
      console.error(chalk.red('✗ Security scan failed due to security issues.'));
      process.exit(1);
    }
  } else {
    console.log(chalk.green('\n✓ No security issues found.'));
  }
  
  return report;
}

/**
 * Main function
 */
async function main() {
  console.log(chalk.bold('Starting security scan...'));
  console.log(`Scan type: ${scanType}`);
  console.log(`Report format: ${reportFormat}`);
  console.log(`Fail on issues: ${failOnIssues ? 'Yes' : 'No'}`);
  console.log();
  
  const results = {
    dependencies: { vulnerabilities: {}, hasCriticalOrHigh: false },
    code: { issues: [], hasIssues: false },
    build: { issues: [], hasIssues: false },
  };
  
  try {
    // Run scans based on type
    if (scanType === 'all' || scanType === 'dependencies') {
      results.dependencies = await scanDependencies();
    }
    
    if (scanType === 'all' || scanType === 'code') {
      results.code = await scanCode();
    }
    
    if (scanType === 'all' || scanType === 'build') {
      results.build = await scanBuild();
    }
    
    // Generate report
    generateReport(results);
  } catch (error) {
    console.error(chalk.red('Error during security scan:'), error);
    process.exit(1);
  }
}

main();
