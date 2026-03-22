/**
 * Validation Agent Implementation
 * 
 * Thin client wrapper for Java Canvas AI Backend validation service.
 * Delegates heavy validation logic to Java backend via gRPC.
 * 
 * @doc.type service
 * @doc.purpose Canvas validation agent (thin client for Java backend)
 * @doc.layer product
 * @doc.pattern Agent, Proxy
 * @architecture Hybrid Backend - Node.js thin client -> Java gRPC service
 */

import {
    ValidationAgentContract,
    AgentExecutionContext,
} from './AgentContract';
import { agentExecutor } from './AgentExecutor';
import { LifecyclePhase } from '../../../types/lifecycle';
import type { CanvasState, CanvasElement } from '../../../components/canvas/workspace/canvasAtoms';
import { getCanvasAIService, isCanvasAIAvailable } from '../api/CanvasAIService';

// ============================================================================
// Validation Types
// ============================================================================

export type ValidationSeverity = 'error' | 'warning' | 'info';

export interface ValidationIssue {
    id: string;
    phase: LifecyclePhase;
    severity: ValidationSeverity;
    category: string;
    title: string;
    description: string;
    elementIds: string[];
    suggestion?: string;
    autoFixable: boolean;
}

export interface ValidationReport {
    phase: LifecyclePhase;
    timestamp: number;
    valid: boolean;
    score: number; // 0-100
    issues: ValidationIssue[];
    summary: {
        errors: number;
        warnings: number;
        info: number;
    };
    gaps: string[];
    risks: RiskAssessment[];
}

export interface RiskAssessment {
    id: string;
    type: 'security' | 'performance' | 'maintainability' | 'scalability';
    severity: 'low' | 'medium' | 'high' | 'critical';
    title: string;
    description: string;
    impact: string;
    mitigation: string;
}

// ============================================================================
// Validation Agent
// ============================================================================

/**
 * Validation Agent
 * Thin client wrapper - delegates to Java backend for validation
 */
export class ValidationAgent {
    private useBackend: boolean = true;

    /**
     * Validate canvas design
     */
    async validate(context: AgentExecutionContext): Promise<ValidationReport> {
        // Check if Java backend is available
        const backendAvailable = await isCanvasAIAvailable();

        if (backendAvailable && this.useBackend) {
            return this.validateViaBackend(context);
        } else {
            console.warn('[ValidationAgent] Java backend unavailable, falling back to local validation');
            return this.validateLocally(context);
        }
    }

    /**
     * Validate via Java backend (preferred)
     */
    private async validateViaBackend(context: AgentExecutionContext): Promise<ValidationReport> {
        const service = await getCanvasAIService();

        try {
            const result = await service.validateCanvas({
                canvasState: context.canvasState,
                phase: context.lifecyclePhase,
                options: {
                    strictMode: true,
                    validateRisks: true,
                },
            });

            return result;
        } catch (error) {
            console.error('[ValidationAgent] Backend validation failed, falling back:', error);
            return this.validateLocally(context);
        }
    }

    /**
     * Validate locally (fallback for when backend is unavailable)
     */
    private async validateLocally(context: AgentExecutionContext): Promise<ValidationReport> {
        // Execute within contract
        const modifiedContract = {
            ...ValidationAgentContract,
            allowedPhases: Object.values(LifecyclePhase),
        };

        const result = await agentExecutor.executeAgent(
            modifiedContract,
            context,
            async (ctx) => {
                return this.performLocalValidation(ctx);
            }
        );

        if (!result.success) {
            throw new Error(`Validation agent failed: ${result.errors?.join(', ')}`);
        }

        return result.artifacts as ValidationReport;
    }

    /**
     * Perform local validation (original implementation)
     */
    private async performLocalValidation(
        context: AgentExecutionContext
    ): Promise<ValidationReport> {
        const { canvasState, lifecyclePhase } = context;
        const issues: ValidationIssue[] = [];
        const gaps: string[] = [];
        const risks: RiskAssessment[] = [];

        // Phase-specific validation
        switch (lifecyclePhase) {
            case LifecyclePhase.SHAPE:
                this.validateShapePhase(canvasState, issues, gaps, risks);
                break;
            case LifecyclePhase.LAYOUT:
                this.validateLayoutPhase(canvasState, issues, gaps, risks);
                break;
            case LifecyclePhase.COMPONENT:
                this.validateComponentPhase(canvasState, issues, gaps, risks);
                break;
            case LifecyclePhase.VALIDATE:
                this.validateValidatePhase(canvasState, issues, gaps, risks);
                break;
            case LifecyclePhase.DEPLOY:
                this.validateDeployPhase(canvasState, issues, gaps, risks);
                break;
        }

        // Calculate summary
        const summary = {
            errors: issues.filter(i => i.severity === 'error').length,
            warnings: issues.filter(i => i.severity === 'warning').length,
            info: issues.filter(i => i.severity === 'info').length,
        };

        // Calculate score (100 - penalties)
        let score = 100;
        score -= summary.errors * 10;
        score -= summary.warnings * 5;
        score -= summary.info * 2;
        score = Math.max(0, score);

        return {
            phase: lifecyclePhase,
            timestamp: Date.now(),
            valid: summary.errors === 0,
            score,
            issues,
            summary,
            gaps,
            risks,
        };
    }

    /**
     * Validate SHAPE phase
     * Focus: Architectural patterns, component types, basic structure
     */
    private validateShapePhase(
        canvasState: CanvasState,
        issues: ValidationIssue[],
        gaps: string[],
        risks: RiskAssessment[]
    ): void {
        const elements = canvasState.elements || [];
        const connections = canvasState.connections || [];

        // Must have at least one element
        if (elements.length === 0) {
            issues.push({
                id: `shape-empty-${Date.now()}`,
                phase: LifecyclePhase.SHAPE,
                severity: 'error',
                category: 'structure',
                title: 'Empty Canvas',
                description: 'Canvas must have at least one element',
                elementIds: [],
                suggestion: 'Add components to define your application architecture',
                autoFixable: false,
            });
            gaps.push('No components defined');
        }

        // Detect isolated nodes (no connections)
        const isolatedNodes = elements.filter((el: CanvasElement) => {
            return !connections.some((conn: unknown) =>
                conn.source === el.id || conn.target === el.id
            );
        });

        if (isolatedNodes.length > 0) {
            issues.push({
                id: `shape-isolated-${Date.now()}`,
                phase: LifecyclePhase.SHAPE,
                severity: 'warning',
                category: 'connectivity',
                title: 'Isolated Components',
                description: `${isolatedNodes.length} component(s) have no connections`,
                elementIds: isolatedNodes.map((el: CanvasElement) => el.id),
                suggestion: 'Connect components to show data flow and dependencies',
                autoFixable: false,
            });
        }

        // Check for API without data layer
        const hasApi = elements.some((el: CanvasElement) => el.type === 'api');
        const hasData = elements.some((el: CanvasElement) => el.type === 'data');

        if (hasApi && !hasData) {
            gaps.push('Missing data layer');
            issues.push({
                id: `shape-no-data-${Date.now()}`,
                phase: LifecyclePhase.SHAPE,
                severity: 'warning',
                category: 'architecture',
                title: 'Missing Data Layer',
                description: 'API exists without a data source',
                elementIds: elements.filter((el: CanvasElement) => el.type === 'api').map(el => el.id),
                suggestion: 'Add a database or data source to complete the architecture',
                autoFixable: false,
            });

            risks.push({
                id: `risk-no-data-${Date.now()}`,
                type: 'maintainability',
                severity: 'medium',
                title: 'Incomplete Architecture',
                description: 'API layer without data persistence',
                impact: 'Data will not be persisted, limiting application functionality',
                mitigation: 'Add a database component and connect it to the API',
            });
        }

        // Check for frontend without API
        const hasFrontend = elements.some((el: CanvasElement) => el.type === 'component');
        if (hasFrontend && !hasApi) {
            gaps.push('Missing API layer');
            issues.push({
                id: `shape-no-api-${Date.now()}`,
                phase: LifecyclePhase.SHAPE,
                severity: 'warning',
                category: 'architecture',
                title: 'Missing API Layer',
                description: 'Frontend components exist without an API',
                elementIds: elements.filter((el: CanvasElement) => el.type === 'component').map(el => el.id),
                suggestion: 'Add an API layer to handle data operations',
                autoFixable: false,
            });
        }
    }

    /**
     * Validate LAYOUT phase
     * Focus: Positioning, spacing, visual hierarchy
     */
    private validateLayoutPhase(
        canvasState: CanvasState,
        issues: ValidationIssue[],
        gaps: string[],
        risks: RiskAssessment[]
    ): void {
        const elements = canvasState.elements || [];

        // Check for overlapping elements
        for (let i = 0; i < elements.length; i++) {
            for (let j = i + 1; j < elements.length; j++) {
                const el1 = elements[i] as CanvasElement;
                const el2 = elements[j] as CanvasElement;

                if (this.elementsOverlap(el1, el2)) {
                    issues.push({
                        id: `layout-overlap-${Date.now()}-${i}-${j}`,
                        phase: LifecyclePhase.LAYOUT,
                        severity: 'warning',
                        category: 'positioning',
                        title: 'Overlapping Elements',
                        description: `Elements "${el1.data?.label || el1.id}" and "${el2.data?.label || el2.id}" overlap`,
                        elementIds: [el1.id, el2.id],
                        suggestion: 'Adjust positions to prevent visual overlap',
                        autoFixable: true,
                    });
                }
            }
        }

        // Check for elements outside typical bounds
        elements.forEach((el: CanvasElement) => {
            const pos = el.position || { x: 0, y: 0 };
            if (pos.x < 0 || pos.y < 0 || pos.x > 5000 || pos.y > 5000) {
                issues.push({
                    id: `layout-bounds-${Date.now()}-${el.id}`,
                    phase: LifecyclePhase.LAYOUT,
                    severity: 'info',
                    category: 'positioning',
                    title: 'Element Outside Typical Bounds',
                    description: `Element "${el.data?.label || el.id}" may be positioned too far`,
                    elementIds: [el.id],
                    suggestion: 'Consider repositioning element closer to main canvas area',
                    autoFixable: true,
                });
            }
        });
    }

    /**
     * Validate COMPONENT phase
     * Focus: Component configuration, properties, data completeness
     */
    private validateComponentPhase(
        canvasState: CanvasState,
        issues: ValidationIssue[],
        gaps: string[],
        risks: RiskAssessment[]
    ): void {
        const elements = canvasState.elements || [];

        // Check for components without labels
        const unlabeledElements = elements.filter((el: CanvasElement) =>
            !el.data?.label || el.data.label.trim() === ''
        );

        if (unlabeledElements.length > 0) {
            issues.push({
                id: `component-unlabeled-${Date.now()}`,
                phase: LifecyclePhase.COMPONENT,
                severity: 'warning',
                category: 'metadata',
                title: 'Components Without Labels',
                description: `${unlabeledElements.length} component(s) lack descriptive labels`,
                elementIds: unlabeledElements.map((el: CanvasElement) => el.id),
                suggestion: 'Add meaningful labels to improve code generation quality',
                autoFixable: false,
            });

            risks.push({
                id: `risk-unlabeled-${Date.now()}`,
                type: 'maintainability',
                severity: 'low',
                title: 'Missing Component Labels',
                description: 'Components without labels reduce code readability',
                impact: 'Generated code will have generic names, harder to maintain',
                mitigation: 'Add descriptive labels to all components',
            });
        }

        // Check for API components without endpoints
        const apiElements = elements.filter((el: CanvasElement) => el.type === 'api');
        apiElements.forEach((el: CanvasElement) => {
            if (!el.data?.endpoint) {
                issues.push({
                    id: `component-api-no-endpoint-${Date.now()}-${el.id}`,
                    phase: LifecyclePhase.COMPONENT,
                    severity: 'error',
                    category: 'configuration',
                    title: 'API Missing Endpoint',
                    description: `API "${el.data?.label || el.id}" has no endpoint defined`,
                    elementIds: [el.id],
                    suggestion: 'Define API endpoint path and method',
                    autoFixable: false,
                });
            }
        });

        // Check for data components without connection strings
        const dataElements = elements.filter((el: CanvasElement) => el.type === 'data');
        dataElements.forEach((el: CanvasElement) => {
            if (!el.data?.connectionString && !el.data?.database) {
                issues.push({
                    id: `component-data-no-config-${Date.now()}-${el.id}`,
                    phase: LifecyclePhase.COMPONENT,
                    severity: 'error',
                    category: 'configuration',
                    title: 'Database Missing Configuration',
                    description: `Database "${el.data?.label || el.id}" has no connection details`,
                    elementIds: [el.id],
                    suggestion: 'Configure database connection or type',
                    autoFixable: false,
                });
            }
        });
    }

    /**
     * Validate VALIDATE phase
     * Focus: Comprehensive validation, security, performance
     */
    private validateValidatePhase(
        canvasState: CanvasState,
        issues: ValidationIssue[],
        gaps: string[],
        risks: RiskAssessment[]
    ): void {
        const elements = canvasState.elements || [];
        const connections = canvasState.connections || [];

        // Re-run all previous phase validations
        this.validateShapePhase(canvasState, issues, gaps, risks);
        this.validateLayoutPhase(canvasState, issues, gaps, risks);
        this.validateComponentPhase(canvasState, issues, gaps, risks);

        // Additional comprehensive checks

        // Check for security risks
        const publicApis = elements.filter((el: CanvasElement) =>
            el.type === 'api' && el.data?.public === true
        );

        if (publicApis.length > 0) {
            risks.push({
                id: `risk-public-api-${Date.now()}`,
                type: 'security',
                severity: 'medium',
                title: 'Public API Endpoints',
                description: `${publicApis.length} API endpoint(s) exposed publicly`,
                impact: 'Public APIs may be vulnerable to unauthorized access',
                mitigation: 'Implement authentication and rate limiting',
            });
        }

        // Check for performance risks
        if (elements.length > 50) {
            risks.push({
                id: `risk-complexity-${Date.now()}`,
                type: 'performance',
                severity: 'medium',
                title: 'High Component Count',
                description: `Canvas has ${elements.length} components`,
                impact: 'Complex designs may impact rendering performance',
                mitigation: 'Consider breaking into multiple canvases or optimizing structure',
            });
        }

        // Check for circular dependencies
        const cycles = this.detectCycles(elements, connections);
        if (cycles.length > 0) {
            issues.push({
                id: `validate-cycles-${Date.now()}`,
                phase: LifecyclePhase.VALIDATE,
                severity: 'error',
                category: 'architecture',
                title: 'Circular Dependencies Detected',
                description: `Found ${cycles.length} circular dependency path(s)`,
                elementIds: cycles.flat(),
                suggestion: 'Break circular dependencies by introducing one-way data flow',
                autoFixable: false,
            });

            risks.push({
                id: `risk-cycles-${Date.now()}`,
                type: 'maintainability',
                severity: 'high',
                title: 'Circular Dependencies',
                description: 'Components form circular dependency chains',
                impact: 'Circular dependencies can cause initialization issues and hard-to-debug errors',
                mitigation: 'Refactor to use event-driven or one-way data flow patterns',
            });
        }
    }

    /**
     * Validate DEPLOY phase
     * Focus: Deployment readiness, configuration completeness
     */
    private validateDeployPhase(
        canvasState: CanvasState,
        issues: ValidationIssue[],
        gaps: string[],
        risks: RiskAssessment[]
    ): void {
        // Re-run all validations
        this.validateValidatePhase(canvasState, issues, gaps, risks);

        // Check deployment readiness
        const elements = canvasState.elements || [];

        // Must have no errors to deploy
        const hasErrors = issues.some(issue => issue.severity === 'error');
        if (hasErrors) {
            issues.push({
                id: `deploy-has-errors-${Date.now()}`,
                phase: LifecyclePhase.DEPLOY,
                severity: 'error',
                category: 'readiness',
                title: 'Cannot Deploy with Errors',
                description: 'Fix all errors before deploying',
                elementIds: [],
                suggestion: 'Resolve all error-level issues first',
                autoFixable: false,
            });
        }

        // Check for deployment configuration
        if (!canvasState.metadata?.deploymentConfig) {
            gaps.push('Missing deployment configuration');
            issues.push({
                id: `deploy-no-config-${Date.now()}`,
                phase: LifecyclePhase.DEPLOY,
                severity: 'error',
                category: 'configuration',
                title: 'Missing Deployment Configuration',
                description: 'No deployment settings configured',
                elementIds: [],
                suggestion: 'Configure deployment target and settings',
                autoFixable: false,
            });
        }
    }

    /**
     * Check if two elements overlap
     */
    private elementsOverlap(el1: CanvasElement, el2: CanvasElement): boolean {
        const pos1 = el1.position || { x: 0, y: 0 };
        const pos2 = el2.position || { x: 0, y: 0 };
        const size1 = el1.size || { width: 100, height: 60 };
        const size2 = el2.size || { width: 100, height: 60 };

        return !(
            pos1.x + size1.width < pos2.x ||
            pos2.x + size2.width < pos1.x ||
            pos1.y + size1.height < pos2.y ||
            pos2.y + size2.height < pos1.y
        );
    }

    /**
     * Detect circular dependencies using DFS
     */
    private detectCycles(
        elements: CanvasElement[],
        connections: unknown[]
    ): string[][] {
        const cycles: string[][] = [];
        const visited = new Set<string>();
        const recursionStack = new Set<string>();

        const dfs = (nodeId: string, path: string[]): void => {
            visited.add(nodeId);
            recursionStack.add(nodeId);
            path.push(nodeId);

            // Find outgoing connections
            const outgoing = connections.filter(conn => conn.source === nodeId);

            for (const conn of outgoing) {
                const targetId = conn.target;

                if (!visited.has(targetId)) {
                    dfs(targetId, [...path]);
                } else if (recursionStack.has(targetId)) {
                    // Cycle detected
                    const cycleStart = path.indexOf(targetId);
                    if (cycleStart !== -1) {
                        cycles.push(path.slice(cycleStart));
                    }
                }
            }

            recursionStack.delete(nodeId);
        };

        elements.forEach((el: CanvasElement) => {
            if (!visited.has(el.id)) {
                dfs(el.id, []);
            }
        });

        return cycles;
    }
}

// Export singleton instance
export const validationAgent = new ValidationAgent();
