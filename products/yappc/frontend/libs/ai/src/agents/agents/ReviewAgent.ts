/**
 * Review Agent
 *
 * AI agent specialized in code review and quality analysis.
 * Provides automated feedback on:
 * - Code quality and maintainability
 * - Best practices compliance
 * - Security vulnerabilities
 * - Performance issues
 * - Testing coverage
 */

import { BaseAgent } from '../base/Agent';

import type { AgentConfig, TaskResult } from '../types';
import type { IAIService } from '../../core/index.js';

/**
 * Code review input
 */
export interface CodeReviewInput {
  /** Source code to review */
  code: string;
  /** Programming language */
  language: string;
  /** File path (for context) */
  filePath?: string;
  /** Git diff (for change review) */
  diff?: string;
  /** Project context */
  context?: {
    framework?: string;
    testingFramework?: string;
    lintRules?: Record<string, unknown>;
    projectType?: 'library' | 'application' | 'component';
  };
}

/**
 * Code issue severity
 */
export type CodeIssueSeverity = 'critical' | 'major' | 'minor' | 'suggestion';

/**
 * Code issue category
 */
export type CodeIssueCategory =
  | 'security'
  | 'performance'
  | 'maintainability'
  | 'reliability'
  | 'testing'
  | 'documentation'
  | 'style'
  | 'complexity';

/**
 * Code issue found during review
 */
export interface CodeIssue {
  id: string;
  severity: CodeIssueSeverity;
  category: CodeIssueCategory;
  message: string;
  location?: {
    line: number;
    column?: number;
    endLine?: number;
  };
  suggestion?: string;
  codeSnippet?: string;
  fixExample?: string;
  references?: string[];
}

/**
 * Code review metrics
 */
export interface CodeMetrics {
  linesOfCode: number;
  cyclomaticComplexity: number;
  maintainabilityIndex: number; // 0-100
  testCoverage?: number; // 0-100
  duplicateCodePercentage?: number;
}

/**
 * Code review result
 */
export interface CodeReviewOutput {
  issues: CodeIssue[];
  score: number; // 0-100
  metrics: CodeMetrics;
  summary: string;
  strengths: string[];
  improvements: string[];
  securityScore: number; // 0-100
  maintainabilityScore: number; // 0-100
  performanceScore: number; // 0-100
}

/**
 * Review Agent implementation
 */
export class ReviewAgent extends BaseAgent<CodeReviewInput, CodeReviewOutput> {
  /**
   *
   */
  constructor(config: Omit<AgentConfig, 'capabilities'>) {
    super({
      ...config,
      capabilities: ['code-review', 'security', 'performance', 'testing'],
    });
  }

  /**
   * Execute code review task
   */
  protected async executeTask(
    input: CodeReviewInput
  ): Promise<TaskResult<CodeReviewOutput>> {
    const issues: CodeIssue[] = [];
    const strengths: string[] = [];

    // Calculate metrics
    const metrics = this.calculateMetrics(input.code);

    // Run all checks
    const securityIssues = await this.checkSecurity(input);
    const performanceIssues = await this.checkPerformance(input);
    const maintainabilityIssues = await this.checkMaintainability(input);
    const testingIssues = await this.checkTesting(input);
    const documentationIssues = await this.checkDocumentation(input);
    const complexityIssues = await this.checkComplexity(input, metrics);

    issues.push(
      ...securityIssues,
      ...performanceIssues,
      ...maintainabilityIssues,
      ...testingIssues,
      ...documentationIssues,
      ...complexityIssues
    );

    // Calculate scores
    const securityScore = this.calculateSecurityScore(securityIssues);
    const performanceScore = this.calculatePerformanceScore(performanceIssues);
    const maintainabilityScore = this.calculateMaintainabilityScore(
      metrics,
      maintainabilityIssues
    );
    const overallScore = this.calculateOverallScore(issues, metrics);

    // Identify strengths
    if (securityScore >= 90) {
      strengths.push('No significant security vulnerabilities');
    }
    if (metrics.cyclomaticComplexity < 10) {
      strengths.push('Low code complexity');
    }
    if (maintainabilityScore >= 80) {
      strengths.push('Good code maintainability');
    }

    // Generate summary using AI if available
    const summary = this._aiService
      ? await this.generateAISummary(input, issues, metrics)
      : this.generateBasicSummary(issues, metrics);

    // Generate improvements
    const improvements = this.generateImprovements(issues);

    const output: CodeReviewOutput = {
      issues,
      score: overallScore,
      metrics,
      summary,
      strengths,
      improvements,
      securityScore,
      maintainabilityScore,
      performanceScore,
    };

    return {
      success: true,
      output,
      confidence: this.calculateConfidence(issues, metrics),
      suggestions: improvements.slice(0, 3),
      warnings: issues
        .filter((i) => i.severity === 'critical' || i.severity === 'major')
        .map((i) => i.message),
    };
  }

  /**
   * Check for security vulnerabilities
   */
  private async checkSecurity(input: CodeReviewInput): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];
    const { code } = input;

    // Check for eval usage
    if (code.includes('eval(')) {
      issues.push({
        id: 'security-eval',
        severity: 'critical',
        category: 'security',
        message: 'Use of eval() detected - major security risk',
        suggestion:
          'Avoid eval(). Use safer alternatives like JSON.parse() or Function constructor',
        references: [
          'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/eval#never_use_eval!',
        ],
      });
    }

    // Check for SQL injection vulnerabilities
    if (code.match(/query.*\+.*['"`]/)) {
      issues.push({
        id: 'security-sql-injection',
        severity: 'critical',
        category: 'security',
        message: 'Potential SQL injection vulnerability',
        suggestion: 'Use parameterized queries or prepared statements',
        references: ['https://owasp.org/www-community/attacks/SQL_Injection'],
      });
    }

    // Check for hardcoded credentials
    const credentialPatterns = [
      /password\s*=\s*['"`][^'"`]+['"`]/i,
      /api[_-]?key\s*=\s*['"`][^'"`]+['"`]/i,
      /secret\s*=\s*['"`][^'"`]+['"`]/i,
      /token\s*=\s*['"`][^'"`]+['"`]/i,
    ];

    credentialPatterns.forEach((pattern, index) => {
      if (pattern.test(code)) {
        issues.push({
          id: `security-hardcoded-creds-${index}`,
          severity: 'critical',
          category: 'security',
          message: 'Hardcoded credentials detected',
          suggestion: 'Use environment variables or secure credential storage',
          references: [
            'https://owasp.org/www-community/vulnerabilities/Use_of_hard-coded_password',
          ],
        });
      }
    });

    // Check for innerHTML usage
    if (code.includes('innerHTML')) {
      issues.push({
        id: 'security-inner-html',
        severity: 'major',
        category: 'security',
        message: 'innerHTML usage detected - potential XSS vulnerability',
        suggestion: 'Use textContent or DOMPurify to sanitize HTML',
        references: ['https://owasp.org/www-community/attacks/xss/'],
      });
    }

    // Check for insecure random
    if (
      code.includes('Math.random()') &&
      code.match(/(crypto|security|token|password)/i)
    ) {
      issues.push({
        id: 'security-insecure-random',
        severity: 'major',
        category: 'security',
        message: 'Math.random() is not cryptographically secure',
        suggestion: 'Use crypto.getRandomValues() or crypto.randomUUID()',
      });
    }

    return issues;
  }

  /**
   * Check for performance issues
   */
  private async checkPerformance(input: CodeReviewInput): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];
    const { code, language } = input;

    // Check for synchronous blocking operations
    if (language === 'javascript' || language === 'typescript') {
      if (code.match(/fs\.(readFileSync|writeFileSync)/)) {
        issues.push({
          id: 'perf-sync-fs',
          severity: 'major',
          category: 'performance',
          message: 'Synchronous file system operations block the event loop',
          suggestion:
            'Use async alternatives: fs.promises or fs.readFile with callbacks',
        });
      }

      // Check for missing memoization in React
      if (code.includes('React') || code.includes('useState')) {
        const hasExpensiveCalculation = code.match(/\.map\(.*\.filter\(/);
        const hasMemo =
          code.includes('useMemo') || code.includes('useCallback');

        if (hasExpensiveCalculation && !hasMemo) {
          issues.push({
            id: 'perf-react-memo',
            severity: 'minor',
            category: 'performance',
            message: 'Complex calculations without memoization',
            suggestion: 'Consider using useMemo for expensive calculations',
          });
        }
      }

      // Check for inefficient loops
      const nestedLoops = (code.match(/for\s*\(/g) || []).length;
      if (nestedLoops >= 3) {
        issues.push({
          id: 'perf-nested-loops',
          severity: 'minor',
          category: 'performance',
          message: 'Deeply nested loops detected - O(n³) or worse',
          suggestion:
            'Consider optimizing algorithm or using more efficient data structures',
        });
      }
    }

    return issues;
  }

  /**
   * Check code maintainability
   */
  private async checkMaintainability(
    input: CodeReviewInput
  ): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];
    const { code } = input;

    // Check function length
    const functions = code.match(/function\s+\w+\s*\([^)]*\)\s*\{/g) || [];
    functions.forEach((func, index) => {
      const startIndex = code.indexOf(func);
      const endIndex = this.findClosingBrace(code, startIndex);
      const functionCode = code.substring(startIndex, endIndex);
      const lineCount = (functionCode.match(/\n/g) || []).length;

      if (lineCount > 50) {
        issues.push({
          id: `maintainability-long-function-${index}`,
          severity: 'minor',
          category: 'maintainability',
          message: `Function is too long (${lineCount} lines)`,
          suggestion:
            'Break down into smaller, focused functions (max 20-30 lines)',
        });
      }
    });

    // Check for magic numbers
    const magicNumbers = code.match(/\d{3,}/g) || [];
    if (magicNumbers.length > 5) {
      issues.push({
        id: 'maintainability-magic-numbers',
        severity: 'minor',
        category: 'maintainability',
        message: 'Multiple magic numbers found',
        suggestion: 'Extract magic numbers to named constants',
      });
    }

    // Check for duplicate code (simple heuristic)
    const lines = code.split('\n').filter((line) => line.trim().length > 10);
    const uniqueLines = new Set(lines);
    const duplicatePercentage =
      ((lines.length - uniqueLines.size) / lines.length) * 100;

    if (duplicatePercentage > 20) {
      issues.push({
        id: 'maintainability-duplicate-code',
        severity: 'minor',
        category: 'maintainability',
        message: `High code duplication detected (~${duplicatePercentage.toFixed(0)}%)`,
        suggestion: 'Extract common code into reusable functions or modules',
      });
    }

    return issues;
  }

  /**
   * Check testing practices
   */
  private async checkTesting(input: CodeReviewInput): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];
    const { code, filePath } = input;

    // Skip test files themselves
    if (filePath?.includes('.test.') || filePath?.includes('.spec.')) {
      return issues;
    }

    // Check if there's a corresponding test file mentioned
    const hasTestFile =
      code.includes('test(') ||
      code.includes('describe(') ||
      code.includes('it(');

    if (!hasTestFile) {
      issues.push({
        id: 'testing-missing-tests',
        severity: 'minor',
        category: 'testing',
        message: 'No test file found or mentioned',
        suggestion: 'Add unit tests to ensure code reliability',
      });
    }

    // Check for error handling
    const hasTryCatch = code.includes('try') && code.includes('catch');
    const hasErrorHandling =
      hasTryCatch || code.includes('.catch(') || code.includes('onerror');

    if (!hasErrorHandling && code.includes('async')) {
      issues.push({
        id: 'testing-no-error-handling',
        severity: 'major',
        category: 'reliability',
        message: 'Async code without error handling',
        suggestion: 'Add try-catch blocks or .catch() handlers',
      });
    }

    return issues;
  }

  /**
   * Check documentation
   */
  private async checkDocumentation(
    input: CodeReviewInput
  ): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];
    const { code } = input;

    // Check for JSDoc/TSDoc comments on exported functions
    const exportedFunctions =
      code.match(/export\s+(async\s+)?function\s+\w+/g) || [];
    const jsdocComments = code.match(/\/\*\*[\s\S]*?\*\//g) || [];

    if (exportedFunctions.length > jsdocComments.length) {
      issues.push({
        id: 'documentation-missing-jsdoc',
        severity: 'minor',
        category: 'documentation',
        message: 'Missing JSDoc comments on exported functions',
        suggestion: 'Add JSDoc comments to document public APIs',
      });
    }

    // Check for README or inline explanations for complex code
    const complexPatterns = [
      /for\s*\([^)]+\)\s*for\s*\(/, // nested loops
      /switch\s*\([^)]+\)\s*\{[\s\S]{100,}\}/, // long switch
      /\?\s*[^:]+\s*:\s*[^;]+\s*\?\s*/, // nested ternary
    ];

    complexPatterns.forEach((pattern, index) => {
      if (pattern.test(code)) {
        const hasComment =
          code.substring(0, code.search(pattern)).lastIndexOf('//') >
          code.substring(0, code.search(pattern)).lastIndexOf('\n');

        if (!hasComment) {
          issues.push({
            id: `documentation-complex-code-${index}`,
            severity: 'minor',
            category: 'documentation',
            message: 'Complex code without explanation',
            suggestion: 'Add comments to explain the logic',
          });
        }
      }
    });

    return issues;
  }

  /**
   * Check code complexity
   */
  private async checkComplexity(
    input: CodeReviewInput,
    metrics: CodeMetrics
  ): Promise<CodeIssue[]> {
    const issues: CodeIssue[] = [];

    if (metrics.cyclomaticComplexity > 15) {
      issues.push({
        id: 'complexity-high',
        severity: 'major',
        category: 'complexity',
        message: `High cyclomatic complexity (${metrics.cyclomaticComplexity})`,
        suggestion: 'Refactor to reduce complexity (target: < 10)',
      });
    }

    if (metrics.maintainabilityIndex < 40) {
      issues.push({
        id: 'complexity-low-maintainability',
        severity: 'major',
        category: 'maintainability',
        message: `Low maintainability index (${metrics.maintainabilityIndex}/100)`,
        suggestion: 'Simplify code structure and reduce complexity',
      });
    }

    return issues;
  }

  /**
   * Calculate code metrics
   */
  private calculateMetrics(code: string): CodeMetrics {
    const lines = code.split('\n');
    const linesOfCode = lines.filter((line) => {
      const trimmed = line.trim();
      return (
        trimmed.length > 0 &&
        !trimmed.startsWith('//') &&
        !trimmed.startsWith('/*')
      );
    }).length;

    // Calculate cyclomatic complexity (simplified)
    const decisionPoints = [
      /if\s*\(/g,
      /else\s+if\s*\(/g,
      /for\s*\(/g,
      /while\s*\(/g,
      /case\s+/g,
      /catch\s*\(/g,
      /&&/g,
      /\|\|/g,
      /\?/g,
    ];

    let cyclomaticComplexity = 1; // Base complexity
    decisionPoints.forEach((pattern) => {
      const matches = code.match(pattern);
      if (matches) {
        cyclomaticComplexity += matches.length;
      }
    });

    // Calculate maintainability index (simplified Microsoft formula)
    const volume = linesOfCode * Math.log2(Math.max(1, cyclomaticComplexity));
    const maintainabilityIndex = Math.max(
      0,
      Math.min(
        100,
        171 -
        5.2 * Math.log(volume) -
        0.23 * cyclomaticComplexity -
        16.2 * Math.log(linesOfCode)
      )
    );

    return {
      linesOfCode,
      cyclomaticComplexity,
      maintainabilityIndex: Math.round(maintainabilityIndex),
    };
  }

  /**
   * Calculate security score
   */
  private calculateSecurityScore(issues: CodeIssue[]): number {
    const securityIssues = issues.filter((i) => i.category === 'security');
    const criticalCount = securityIssues.filter(
      (i) => i.severity === 'critical'
    ).length;
    const majorCount = securityIssues.filter(
      (i) => i.severity === 'major'
    ).length;

    const deduction = criticalCount * 30 + majorCount * 15;
    return Math.max(0, 100 - deduction);
  }

  /**
   * Calculate performance score
   */
  private calculatePerformanceScore(issues: CodeIssue[]): number {
    const perfIssues = issues.filter((i) => i.category === 'performance');
    const deduction = perfIssues.length * 10;
    return Math.max(0, 100 - deduction);
  }

  /**
   * Calculate maintainability score
   */
  private calculateMaintainabilityScore(
    metrics: CodeMetrics,
    issues: CodeIssue[]
  ): number {
    const maintIssues = issues.filter((i) => i.category === 'maintainability');
    const deduction = maintIssues.length * 8;
    const metricsScore = metrics.maintainabilityIndex;

    return Math.max(0, Math.min(100, (metricsScore + (100 - deduction)) / 2));
  }

  /**
   * Calculate overall code score
   */
  private calculateOverallScore(
    issues: CodeIssue[],
    metrics: CodeMetrics
  ): number {
    const criticalCount = issues.filter(
      (i) => i.severity === 'critical'
    ).length;
    const majorCount = issues.filter((i) => i.severity === 'major').length;
    const minorCount = issues.filter((i) => i.severity === 'minor').length;

    const issueScore =
      100 - criticalCount * 20 - majorCount * 10 - minorCount * 5;
    const metricsScore = metrics.maintainabilityIndex;

    return Math.max(0, Math.round((issueScore + metricsScore) / 2));
  }

  /**
   * Calculate confidence in review
   */
  private calculateConfidence(
    issues: CodeIssue[],
    metrics: CodeMetrics
  ): number {
    // Higher confidence with concrete issues and good metrics
    const concreteIssues = issues.filter(
      (i) => i.category === 'security' || i.severity === 'critical'
    ).length;
    const metricsReliability = metrics.linesOfCode > 10 ? 0.3 : 0.1;

    return Math.min(1, 0.5 + concreteIssues * 0.05 + metricsReliability);
  }

  /**
   * Generate AI-powered summary
   */
  private async generateAISummary(
    input: CodeReviewInput,
    issues: CodeIssue[],
    metrics: CodeMetrics
  ): Promise<string> {
    if (!this._aiService) {
      return this.generateBasicSummary(issues, metrics);
    }

    const prompt = `Review this ${input.language} code and provide a concise summary:

Code metrics:
- Lines of code: ${metrics.linesOfCode}
- Cyclomatic complexity: ${metrics.cyclomaticComplexity}
- Maintainability index: ${metrics.maintainabilityIndex}/100

Issues found: ${issues.length}
- Critical: ${issues.filter((i) => i.severity === 'critical').length}
- Major: ${issues.filter((i) => i.severity === 'major').length}
- Minor: ${issues.filter((i) => i.severity === 'minor').length}

Provide a 2-3 sentence summary focusing on the most important findings.`;

    try {
      const response = await this._aiService.complete({
        messages: [{ role: 'user', content: prompt }],
        maxTokens: 150,
      });

      return response.content;
    } catch (error) {
      return this.generateBasicSummary(issues, metrics);
    }
  }

  /**
   * Generate basic summary without AI
   */
  private generateBasicSummary(
    issues: CodeIssue[],
    metrics: CodeMetrics
  ): string {
    const criticalCount = issues.filter(
      (i) => i.severity === 'critical'
    ).length;
    const majorCount = issues.filter((i) => i.severity === 'major').length;

    let summary = `Code review completed. Found ${issues.length} issue${issues.length !== 1 ? 's' : ''}`;

    if (criticalCount > 0) {
      summary += ` including ${criticalCount} critical`;
    }
    if (majorCount > 0) {
      summary += ` and ${majorCount} major`;
    }

    summary += `. Maintainability index: ${metrics.maintainabilityIndex}/100. `;
    summary += `Cyclomatic complexity: ${metrics.cyclomaticComplexity}.`;

    return summary;
  }

  /**
   * Generate improvement recommendations
   */
  private generateImprovements(issues: CodeIssue[]): string[] {
    return issues
      .filter((i) => i.severity === 'critical' || i.severity === 'major')
      .sort((a, b) => {
        const severityOrder = {
          critical: 0,
          major: 1,
          minor: 2,
          suggestion: 3,
        };
        return severityOrder[a.severity] - severityOrder[b.severity];
      })
      .slice(0, 5)
      .map((i) => i.suggestion || i.message)
      .filter(Boolean);
  }

  /**
   * Find closing brace for a function
   */
  private findClosingBrace(code: string, startIndex: number): number {
    let braceCount = 0;
    let inString = false;
    let stringChar = '';

    for (let i = startIndex; i < code.length; i++) {
      const char = code[i];
      const prevChar = code[i - 1];

      // Handle strings
      if ((char === '"' || char === "'" || char === '`') && prevChar !== '\\') {
        if (!inString) {
          inString = true;
          stringChar = char;
        } else if (char === stringChar) {
          inString = false;
        }
      }

      if (!inString) {
        if (char === '{') braceCount++;
        if (char === '}') {
          braceCount--;
          if (braceCount === 0) return i;
        }
      }
    }

    return code.length;
  }
}
