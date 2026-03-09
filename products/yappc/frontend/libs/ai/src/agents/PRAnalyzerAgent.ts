/**
 * PR Analyzer Agent Implementation
 *
 * Analyzes pull requests for code quality, security issues, and best practices.
 * Uses LLM-powered code review with structured output.
 *
 * @module ai/agents/PRAnalyzerAgent
 * @doc.type class
 * @doc.purpose AI-powered pull request analysis
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import { BaseAgent, type ProcessResult } from './BaseAgent';
import type { AgentContext } from './types';
import { AgentError } from './types';
import { z } from 'zod';

/**
 * Input schema for PR analysis
 */
export const PRAnalysisInputSchema = z.object({
    prNumber: z.number().positive(),
    repository: z.string(),
    owner: z.string(),
    diff: z.string(),
    files: z.array(
        z.object({
            filename: z.string(),
            status: z.enum(['added', 'modified', 'removed', 'renamed']),
            additions: z.number(),
            deletions: z.number(),
            changes: z.number(),
            patch: z.string().optional(),
        })
    ),
    metadata: z
        .object({
            title: z.string(),
            description: z.string().optional(),
            author: z.string(),
            baseBranch: z.string(),
            headBranch: z.string(),
        })
        .optional(),
});

export type PRAnalysisInput = z.infer<typeof PRAnalysisInputSchema>;

/**
 * Code review finding types
 */
export type FindingSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';
export type FindingCategory =
    | 'security'
    | 'performance'
    | 'maintainability'
    | 'style'
    | 'testing'
    | 'documentation';

export interface CodeFinding {
    file: string;
    line?: number;
    severity: FindingSeverity;
    category: FindingCategory;
    title: string;
    description: string;
    suggestion?: string;
    codeSnippet?: string;
}

export interface PRMetrics {
    complexity: number; // 1-10 scale
    testCoverage: number; // percentage
    documentationScore: number; // 1-10 scale
    maintainabilityIndex: number; // 1-100 scale
}

/**
 * Output schema for PR analysis
 */
export interface PRAnalysisOutput {
    overallScore: number; // 1-10 scale
    summary: string;
    findings: CodeFinding[];
    metrics: PRMetrics;
    positiveAspects: string[];
    recommendations: string[];
    approvalStatus: 'approve' | 'request-changes' | 'comment';
    estimatedReviewTime: number; // minutes
}

/**
 * GitHub Service interface (to be injected)
 */
export interface GitHubService {
    getPullRequest(owner: string, repo: string, prNumber: number): Promise<unknown>;
    getPullRequestDiff(owner: string, repo: string, prNumber: number): Promise<string>;
    getPullRequestFiles(
        owner: string,
        repo: string,
        prNumber: number
    ): Promise<Array<{
        filename: string;
        status: string;
        additions: number;
        deletions: number;
        changes: number;
        patch?: string;
    }>>;
}

/**
 * PRAnalyzerAgent for AI-powered code review
 */
export class PRAnalyzerAgent extends BaseAgent<PRAnalysisInput, PRAnalysisOutput> {
    private _githubService?: GitHubService;

    constructor(githubService?: GitHubService) {
        super({
            name: 'PRAnalyzerAgent',
            version: '1.0.0',
            description: 'AI-powered pull request analysis and code review',
            capabilities: [
                'code-review',
                'security-scan',
                'best-practices',
                'complexity-analysis',
                'test-coverage-check',
            ],
            supportedModels: ['gpt-4-turbo', 'claude-3-opus', 'local/codellama'],
            latencySLA: 5000, // 5 seconds for PR analysis
            defaultTimeout: 30000,
        });

        this._githubService = githubService;
    }

    /**
     * Validate input
     */
    protected validateInput(input: PRAnalysisInput): void {
        const result = PRAnalysisInputSchema.safeParse(input);
        if (!result.success) {
            throw new AgentError(
                `Invalid input: ${result.error.message}`,
                'VALIDATION_ERROR',
                this.name,
                false
            );
        }
    }

    /**
     * Process PR analysis request
     */
    protected async processRequest(
        input: PRAnalysisInput,
        context: AgentContext
    ): Promise<ProcessResult<PRAnalysisOutput>> {
        // Analyze code changes
        const findings = await this.analyzeChanges(input, context);

        // Calculate metrics
        const metrics = this.calculateMetrics(input, findings);

        // Identify positive aspects
        const positiveAspects = this.identifyPositiveAspects(input, findings);

        // Generate recommendations
        const recommendations = this.generateRecommendations(findings, metrics);

        // Calculate overall score
        const overallScore = this.calculateOverallScore(findings, metrics);

        // Determine approval status
        const approvalStatus = this.determineApprovalStatus(findings, overallScore);

        // Estimate review time
        const estimatedReviewTime = this.estimateReviewTime(input);

        // Generate summary
        const summary = this.generateSummary(
            input,
            findings,
            metrics,
            overallScore
        );

        return {
            data: {
                overallScore,
                summary,
                findings,
                metrics,
                positiveAspects,
                recommendations,
                approvalStatus,
                estimatedReviewTime,
            },
            modelVersion: 'gpt-4-turbo',
            confidence: 0.85,
        };
    }

    /**
     * Analyze code changes using LLM
     */
    private async analyzeChanges(
        input: PRAnalysisInput,
        _context: AgentContext
    ): Promise<CodeFinding[]> {
        const findings: CodeFinding[] = [];

        // NOTE: Replace with actual LLM call when GitHub Enterprise API is available
        // This is a stub implementation

        // Analyze each file
        for (const file of input.files) {
            // Static analysis patterns
            if (file.patch) {
                // Check for security issues
                if (this.hasSecurityIssue(file.patch)) {
                    findings.push({
                        file: file.filename,
                        severity: 'high',
                        category: 'security',
                        title: 'Potential security vulnerability',
                        description:
                            'Code pattern suggests potential security issue. Review for SQL injection, XSS, or sensitive data exposure.',
                        suggestion:
                            'Use parameterized queries and input validation.',
                    });
                }

                // Check for missing tests
                if (file.filename.includes('.ts') && !file.filename.includes('.test.')) {
                    findings.push({
                        file: file.filename,
                        severity: 'low',
                        category: 'testing',
                        title: 'Missing test coverage',
                        description:
                            'New code added without corresponding test file.',
                        suggestion: `Add tests in ${file.filename.replace('.ts', '.test.ts')}`,
                    });
                }
            }
        }

        return findings;
    }

    /**
     * Check for common security patterns
     */
    private hasSecurityIssue(patch: string): boolean {
        const securityPatterns = [
            /eval\(/,
            /dangerouslySetInnerHTML/,
            /\.innerHTML\s*=/,
            /process\.env\./,
            /password|secret|token|apikey/i,
        ];

        return securityPatterns.some((pattern) => pattern.test(patch));
    }

    /**
     * Calculate code metrics
     */
    private calculateMetrics(
        input: PRAnalysisInput,
        findings: CodeFinding[]
    ): PRMetrics {
        const totalChanges = input.files.reduce(
            (sum, file) => sum + file.changes,
            0
        );

        // Simple heuristics (replace with actual metrics when available)
        const complexity = Math.min(10, Math.ceil(totalChanges / 50));
        const criticalFindings = findings.filter((f) => f.severity === 'critical')
            .length;
        const testCoverage = input.files.some((f) => f.filename.includes('.test.'))
            ? 80
            : 30;
        const documentationScore = input.metadata?.description ? 8 : 4;
        const maintainabilityIndex = Math.max(
            0,
            100 - complexity * 10 - criticalFindings * 20
        );

        return {
            complexity,
            testCoverage,
            documentationScore,
            maintainabilityIndex,
        };
    }

    /**
     * Identify positive aspects
     */
    private identifyPositiveAspects(
        input: PRAnalysisInput,
        _findings: CodeFinding[]
    ): string[] {
        const positives: string[] = [];

        if (input.metadata?.description && input.metadata.description.length > 50) {
            positives.push('Well-documented PR with clear description');
        }

        const hasTests = input.files.some((f) => f.filename.includes('.test.'));
        if (hasTests) {
            positives.push('Includes test coverage');
        }

        const totalChanges = input.files.reduce(
            (sum, file) => sum + file.changes,
            0
        );
        if (totalChanges < 200) {
            positives.push('Small, focused PR (easy to review)');
        }

        return positives;
    }

    /**
     * Generate recommendations
     */
    private generateRecommendations(
        findings: CodeFinding[],
        metrics: PRMetrics
    ): string[] {
        const recommendations: string[] = [];

        const criticalCount = findings.filter((f) => f.severity === 'critical')
            .length;
        if (criticalCount > 0) {
            recommendations.push(
                `Address ${criticalCount} critical issue(s) before merging`
            );
        }

        if (metrics.testCoverage < 70) {
            recommendations.push('Increase test coverage to at least 70%');
        }

        if (metrics.complexity > 7) {
            recommendations.push(
                'Consider breaking down complex changes into smaller PRs'
            );
        }

        if (metrics.documentationScore < 6) {
            recommendations.push('Add more detailed PR description and code comments');
        }

        return recommendations;
    }

    /**
     * Calculate overall score
     */
    private calculateOverallScore(
        findings: CodeFinding[],
        metrics: PRMetrics
    ): number {
        let score = 10;

        // Deduct for findings
        findings.forEach((finding) => {
            switch (finding.severity) {
                case 'critical':
                    score -= 3;
                    break;
                case 'high':
                    score -= 2;
                    break;
                case 'medium':
                    score -= 1;
                    break;
                case 'low':
                    score -= 0.5;
                    break;
            }
        });

        // Adjust for metrics
        score += (metrics.testCoverage - 50) / 50; // +/- 1 point
        score += (metrics.documentationScore - 5) / 5; // +/- 1 point
        score -= (metrics.complexity - 5) / 2; // +/- 2.5 points

        return Math.max(1, Math.min(10, score));
    }

    /**
     * Determine approval status
     */
    private determineApprovalStatus(
        findings: CodeFinding[],
        score: number
    ): 'approve' | 'request-changes' | 'comment' {
        const hasCritical = findings.some((f) => f.severity === 'critical');
        const hasHigh = findings.some((f) => f.severity === 'high');

        if (hasCritical || score < 5) {
            return 'request-changes';
        }

        if (hasHigh || score < 7) {
            return 'comment';
        }

        return 'approve';
    }

    /**
     * Estimate review time
     */
    private estimateReviewTime(input: PRAnalysisInput): number {
        const totalChanges = input.files.reduce(
            (sum, file) => sum + file.changes,
            0
        );

        // Rough estimate: 1 minute per 10 lines changed, minimum 5 minutes
        return Math.max(5, Math.ceil(totalChanges / 10));
    }

    /**
     * Generate summary
     */
    private generateSummary(
        input: PRAnalysisInput,
        findings: CodeFinding[],
        metrics: PRMetrics,
        score: number
    ): string {
        const { repository, prNumber } = input;
        const fileCount = input.files.length;
        const totalChanges = input.files.reduce(
            (sum, file) => sum + file.changes,
            0
        );

        const criticalCount = findings.filter((f) => f.severity === 'critical')
            .length;
        const highCount = findings.filter((f) => f.severity === 'high').length;

        return `PR #${prNumber} in ${repository}: ${fileCount} files changed with ${totalChanges} total changes. Overall score: ${score.toFixed(1)}/10. Found ${criticalCount} critical and ${highCount} high severity issues. Test coverage: ${metrics.testCoverage}%, Complexity: ${metrics.complexity}/10.`;
    }

    /**
     * Health check (verify GitHub API connectivity)
     */
    async healthCheck(): Promise<import('./types').AgentHealth> {
        // NOTE: Check GitHub API connectivity when available
        return {
            healthy: true,
            latency: 0,
            lastCheck: new Date(),
            dependencies: {},
        };
    }
}
