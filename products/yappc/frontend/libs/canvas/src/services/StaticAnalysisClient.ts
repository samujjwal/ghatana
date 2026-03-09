/**
 * @doc.type service
 * @doc.purpose Static analysis client for Journey 18.1 (Code Review Mode)
 * @doc.layer product
 * @doc.pattern Service Client
 */

/**
 * Code complexity metrics
 */
export interface ComplexityMetrics {
    /**
     * Cyclomatic complexity (number of decision points)
     */
    cyclomatic: number;

    /**
     * Cognitive complexity (how hard code is to understand)
     */
    cognitive: number;

    /**
     * Maintainability index (0-100, higher is better)
     */
    maintainabilityIndex: number;

    /**
     * Halstead metrics
     */
    halstead?: {
        vocabulary: number;
        difficulty: number;
        effort: number;
    };
}

/**
 * Code duplication detection
 */
export interface DuplicationResult {
    /**
     * Percentage of duplicated code
     */
    percentage: number;

    /**
     * Duplicated code blocks
     */
    blocks: {
        startLine: number;
        endLine: number;
        duplicateOf: {
            file: string;
            startLine: number;
            endLine: number;
        };
    }[];
}

/**
 * Security vulnerability
 */
export interface SecurityIssue {
    severity: 'critical' | 'high' | 'medium' | 'low';
    type: string;
    description: string;
    lineNumber: number;
    recommendation: string;
}

/**
 * Code smell detection
 */
export interface CodeSmell {
    type: 'long-method' | 'large-class' | 'long-parameter-list' | 'duplicate-code' | 'dead-code';
    severity: 'major' | 'minor';
    description: string;
    lineNumber: number;
    suggestion: string;
}

/**
 * Analysis result
 */
export interface AnalysisResult {
    complexity: ComplexityMetrics;
    duplication: DuplicationResult;
    security: SecurityIssue[];
    codeSmells: CodeSmell[];
    linesOfCode: {
        total: number;
        code: number;
        comment: number;
        blank: number;
    };
    dependencies: {
        external: string[];
        internal: string[];
        circular: boolean;
    };
}

/**
 * Analysis options
 */
export interface AnalysisOptions {
    /**
     * Include security analysis
     */
    includeSecurity?: boolean;

    /**
     * Include code smells
     */
    includeCodeSmells?: boolean;

    /**
     * Include duplication check
     */
    includeDuplication?: boolean;

    /**
     * Maximum complexity threshold
     */
    maxComplexity?: number;

    /**
     * Maximum duplication percentage
     */
    maxDuplication?: number;
}

/**
 * Static Analysis Client
 * 
 * Provides comprehensive static code analysis including complexity metrics,
 * duplication detection, security vulnerabilities, and code smells.
 */
export class StaticAnalysisClient {
    /**
     * Analyze source code
     */
    async analyze(
        code: string,
        language: string,
        options: AnalysisOptions = {}
    ): Promise<AnalysisResult> {
        const {
            includeSecurity = true,
            includeCodeSmells = true,
            includeDuplication = true,
            maxComplexity = 15,
            maxDuplication = 5,
        } = options;

        // Calculate complexity
        const complexity = this.calculateComplexity(code, language);

        // Calculate lines of code
        const linesOfCode = this.calculateLinesOfCode(code);

        // Detect duplication
        const duplication = includeDuplication
            ? this.detectDuplication(code)
            : { percentage: 0, blocks: [] };

        // Security analysis
        const security = includeSecurity
            ? this.detectSecurityIssues(code, language)
            : [];

        // Code smells
        const codeSmells = includeCodeSmells
            ? this.detectCodeSmells(code, complexity, linesOfCode)
            : [];

        // Dependencies
        const dependencies = this.analyzeDependencies(code, language);

        return {
            complexity,
            duplication,
            security,
            codeSmells,
            linesOfCode,
            dependencies,
        };
    }

    /**
     * Calculate cyclomatic complexity
     */
    private calculateComplexity(code: string, language: string): ComplexityMetrics {
        const lines = code.split('\n');

        // Count decision points
        const ifCount = (code.match(/\bif\b/g) || []).length;
        const forCount = (code.match(/\b(for|while|do)\b/g) || []).length;
        const caseCount = (code.match(/\bcase\b/g) || []).length;
        const catchCount = (code.match(/\bcatch\b/g) || []).length;
        const ternaryCount = (code.match(/\?/g) || []).length;
        const logicalOpCount = (code.match(/(\&\&|\|\|)/g) || []).length;

        // Cyclomatic complexity
        const cyclomatic = 1 + ifCount + forCount + caseCount + catchCount + ternaryCount + logicalOpCount;

        // Cognitive complexity (nested structures increase cost)
        let cognitive = 0;
        let nestingLevel = 0;

        lines.forEach(line => {
            const trimmed = line.trim();

            // Increase nesting
            if (trimmed.match(/\b(if|for|while|switch|catch)\b/)) {
                nestingLevel++;
                cognitive += nestingLevel;
            }

            // Decrease nesting
            if (trimmed.startsWith('}')) {
                nestingLevel = Math.max(0, nestingLevel - 1);
            }
        });

        // Maintainability index (Microsoft formula)
        const volume = lines.length * Math.log2(Math.max(cyclomatic, 1));
        const maintainabilityIndex = Math.max(
            0,
            Math.min(100, 171 - 5.2 * Math.log(volume) - 0.23 * cyclomatic)
        );

        return {
            cyclomatic,
            cognitive,
            maintainabilityIndex: Math.round(maintainabilityIndex),
        };
    }

    /**
     * Calculate lines of code
     */
    private calculateLinesOfCode(code: string): AnalysisResult['linesOfCode'] {
        const lines = code.split('\n');

        let commentLines = 0;
        let blankLines = 0;
        let inBlockComment = false;

        lines.forEach(line => {
            const trimmed = line.trim();

            if (trimmed.length === 0) {
                blankLines++;
                return;
            }

            // Block comments
            if (trimmed.startsWith('/*')) {
                inBlockComment = true;
            }
            if (inBlockComment) {
                commentLines++;
                if (trimmed.endsWith('*/')) {
                    inBlockComment = false;
                }
                return;
            }

            // Line comments
            if (trimmed.startsWith('//') || trimmed.startsWith('#')) {
                commentLines++;
                return;
            }
        });

        const codeLines = lines.length - commentLines - blankLines;

        return {
            total: lines.length,
            code: codeLines,
            comment: commentLines,
            blank: blankLines,
        };
    }

    /**
     * Detect code duplication
     */
    private detectDuplication(code: string): DuplicationResult {
        const lines = code.split('\n');
        const blocks: DuplicationResult['blocks'] = [];

        // Simple duplication detection (look for repeated 5+ line sequences)
        const minBlockSize = 5;
        const seenBlocks = new Map<string, { startLine: number; endLine: number }>();

        for (let i = 0; i < lines.length - minBlockSize; i++) {
            const block = lines.slice(i, i + minBlockSize).join('\n').trim();

            if (block.length < 20) continue; // Skip very short blocks

            const existing = seenBlocks.get(block);
            if (existing) {
                blocks.push({
                    startLine: i + 1,
                    endLine: i + minBlockSize,
                    duplicateOf: {
                        file: 'current',
                        startLine: existing.startLine,
                        endLine: existing.endLine,
                    },
                });
            } else {
                seenBlocks.set(block, { startLine: i + 1, endLine: i + minBlockSize });
            }
        }

        const duplicatedLines = blocks.length * minBlockSize;
        const percentage = lines.length > 0 ? (duplicatedLines / lines.length) * 100 : 0;

        return {
            percentage: Math.round(percentage * 10) / 10,
            blocks,
        };
    }

    /**
     * Detect security issues
     */
    private detectSecurityIssues(code: string, language: string): SecurityIssue[] {
        const issues: SecurityIssue[] = [];
        const lines = code.split('\n');

        lines.forEach((line, index) => {
            const trimmed = line.trim();

            // SQL injection
            if (trimmed.match(/query.*\+.*\$|exec.*\+.*\$/i)) {
                issues.push({
                    severity: 'high',
                    type: 'sql-injection',
                    description: 'Potential SQL injection vulnerability',
                    lineNumber: index + 1,
                    recommendation: 'Use parameterized queries or prepared statements',
                });
            }

            // XSS
            if (trimmed.match(/innerHTML|dangerouslySetInnerHTML/)) {
                issues.push({
                    severity: 'medium',
                    type: 'xss',
                    description: 'Potential XSS vulnerability',
                    lineNumber: index + 1,
                    recommendation: 'Sanitize user input before rendering',
                });
            }

            // Hardcoded credentials
            if (trimmed.match(/password\s*=\s*['"][^'"]+['"]/i)) {
                issues.push({
                    severity: 'critical',
                    type: 'hardcoded-credentials',
                    description: 'Hardcoded password detected',
                    lineNumber: index + 1,
                    recommendation: 'Use environment variables or secure vault',
                });
            }

            // eval usage
            if (trimmed.match(/\beval\s*\(/)) {
                issues.push({
                    severity: 'high',
                    type: 'code-injection',
                    description: 'Dangerous use of eval()',
                    lineNumber: index + 1,
                    recommendation: 'Avoid eval() and use safer alternatives',
                });
            }
        });

        return issues;
    }

    /**
     * Detect code smells
     */
    private detectCodeSmells(
        code: string,
        complexity: ComplexityMetrics,
        linesOfCode: AnalysisResult['linesOfCode']
    ): CodeSmell[] {
        const smells: CodeSmell[] = [];
        const lines = code.split('\n');

        // Long method
        if (linesOfCode.code > 50) {
            smells.push({
                type: 'long-method',
                severity: linesOfCode.code > 100 ? 'major' : 'minor',
                description: `Method is too long (${linesOfCode.code} lines)`,
                lineNumber: 1,
                suggestion: 'Extract smaller methods to improve readability',
            });
        }

        // High complexity
        if (complexity.cyclomatic > 15) {
            smells.push({
                type: 'long-method',
                severity: 'major',
                description: `High cyclomatic complexity (${complexity.cyclomatic})`,
                lineNumber: 1,
                suggestion: 'Simplify logic or extract methods',
            });
        }

        // Long parameter list
        const paramMatch = code.match(/function\s+\w+\s*\(([^)]+)\)/);
        if (paramMatch) {
            const paramCount = paramMatch[1].split(',').length;
            if (paramCount > 5) {
                smells.push({
                    type: 'long-parameter-list',
                    severity: paramCount > 7 ? 'major' : 'minor',
                    description: `Too many parameters (${paramCount})`,
                    lineNumber: 1,
                    suggestion: 'Use parameter object or builder pattern',
                });
            }
        }

        // Dead code (unused variables)
        lines.forEach((line, index) => {
            const varMatch = line.match(/(?:const|let|var)\s+(\w+)/);
            if (varMatch) {
                const varName = varMatch[1];
                const usageCount = (code.match(new RegExp(`\\b${varName}\\b`, 'g')) || []).length;

                if (usageCount === 1) {
                    smells.push({
                        type: 'dead-code',
                        severity: 'minor',
                        description: `Unused variable: ${varName}`,
                        lineNumber: index + 1,
                        suggestion: 'Remove unused variable',
                    });
                }
            }
        });

        return smells;
    }

    /**
     * Analyze dependencies
     */
    private analyzeDependencies(code: string, language: string): AnalysisResult['dependencies'] {
        const external: string[] = [];
        const internal: string[] = [];
        const importRegex = /import.*from\s+['"]([^'"]+)['"]/g;

        let match;
        while ((match = importRegex.exec(code)) !== null) {
            const importPath = match[1];

            if (importPath.startsWith('.')) {
                internal.push(importPath);
            } else {
                external.push(importPath);
            }
        }

        // Simple circular dependency check (would need full project context for real impl)
        const circular = false;

        return {
            external: Array.from(new Set(external)),
            internal: Array.from(new Set(internal)),
            circular,
        };
    }

    /**
     * Get complexity threshold recommendations
     */
    getComplexityRecommendations(complexity: number): string {
        if (complexity <= 5) return 'Low complexity - easy to maintain';
        if (complexity <= 10) return 'Moderate complexity - acceptable';
        if (complexity <= 15) return 'High complexity - consider refactoring';
        if (complexity <= 20) return 'Very high complexity - refactoring recommended';
        return 'Extremely high complexity - refactor urgently';
    }

    /**
     * Get maintainability grade
     */
    getMaintainabilityGrade(index: number): string {
        if (index >= 85) return 'A - Excellent';
        if (index >= 70) return 'B - Good';
        if (index >= 50) return 'C - Fair';
        if (index >= 25) return 'D - Poor';
        return 'F - Critical';
    }
}
