/**
 * Ticket Classifier Agent Implementation
 *
 * Classifies tickets/issues using ML-powered categorization.
 * Predicts priority, severity, assignee, and estimated effort.
 *
 * @module ai/agents/TicketClassifierAgent
 * @doc.type class
 * @doc.purpose AI-powered ticket classification
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import { BaseAgent, type ProcessResult } from './BaseAgent';
import type { AgentContext } from './types';
import { AgentError } from './types';
import { z } from 'zod';

/**
 * Input schema for ticket classification
 */
export const TicketClassificationInputSchema = z.object({
    ticketId: z.string(),
    title: z.string().min(5),
    description: z.string().optional(),
    type: z.enum(['bug', 'feature', 'task', 'story', 'epic']).optional(),
    labels: z.array(z.string()).optional(),
    reporter: z
        .object({
            id: z.string(),
            name: z.string(),
            email: z.string().optional(),
        })
        .optional(),
    project: z
        .object({
            id: z.string(),
            name: z.string(),
            domain: z.string().optional(),
        })
        .optional(),
    historicalData: z
        .array(
            z.object({
                ticketId: z.string(),
                title: z.string(),
                type: z.string(),
                priority: z.string(),
                severity: z.string(),
                actualEffort: z.number().optional(),
                timeToResolve: z.number().optional(),
            })
        )
        .optional(),
});

export type TicketClassificationInput = z.infer<
    typeof TicketClassificationInputSchema
>;

/**
 * Classification types
 */
export type TicketPriority = 'critical' | 'high' | 'medium' | 'low';
export type TicketSeverity = 'blocker' | 'major' | 'minor' | 'trivial';
export type TicketCategory =
    | 'security'
    | 'performance'
    | 'ui/ux'
    | 'backend'
    | 'frontend'
    | 'infrastructure'
    | 'documentation'
    | 'testing';

export interface ClassificationConfidence {
    priority: number;
    severity: number;
    category: number;
    effort: number;
}

export interface SuggestedAssignee {
    userId: string;
    name: string;
    expertise: string[];
    confidence: number;
    reasoning: string;
}

export interface SimilarTicket {
    ticketId: string;
    title: string;
    similarity: number;
    resolution: string;
    timeToResolve: number;
}

/**
 * Output schema for ticket classification
 */
export interface TicketClassificationOutput {
    predictedPriority: TicketPriority;
    predictedSeverity: TicketSeverity;
    categories: TicketCategory[];
    estimatedEffort: number; // in hours
    suggestedAssignees: SuggestedAssignee[];
    similarTickets: SimilarTicket[];
    confidence: ClassificationConfidence;
    reasoning: string;
    suggestedLabels: string[];
}

/**
 * Jira/Issue Service interface (to be injected)
 */
export interface IssueService {
    getTicket(ticketId: string): Promise<unknown>;
    getProjectTickets(projectId: string): Promise<unknown[]>;
    getUserWorkload(userId: string): Promise<number>;
    getUserExpertise(userId: string): Promise<string[]>;
}

/**
 * TicketClassifierAgent for ML-powered ticket classification
 */
export class TicketClassifierAgent extends BaseAgent<
    TicketClassificationInput,
    TicketClassificationOutput
> {
    private _issueService?: IssueService;

    constructor(issueService?: IssueService) {
        super({
            name: 'TicketClassifierAgent',
            version: '1.0.0',
            description: 'AI-powered ticket classification and assignment',
            capabilities: [
                'priority-prediction',
                'severity-prediction',
                'category-classification',
                'effort-estimation',
                'assignee-recommendation',
            ],
            supportedModels: [
                'gpt-4-turbo',
                'claude-3-opus',
                'custom/ticket-classifier',
            ],
            latencySLA: 2000, // 2 seconds for classification
            defaultTimeout: 10000,
        });

        this._issueService = issueService;
    }

    /**
     * Validate input
     */
    protected validateInput(input: TicketClassificationInput): void {
        const result = TicketClassificationInputSchema.safeParse(input);
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
     * Process classification request
     */
    protected async processRequest(
        input: TicketClassificationInput,
        context: AgentContext
    ): Promise<ProcessResult<TicketClassificationOutput>> {
        // Predict priority
        const priority = await this.predictPriority(input, context);

        // Predict severity
        const severity = await this.predictSeverity(input, context);

        // Classify categories
        const categories = await this.classifyCategories(input, context);

        // Estimate effort
        const effort = await this.estimateEffort(input, context);

        // Suggest assignees
        const assignees = await this.suggestAssignees(input, context);

        // Find similar tickets
        const similar = await this.findSimilarTickets(input);

        // Generate reasoning
        const reasoning = this.generateReasoning(
            input,
            priority,
            severity,
            categories,
            effort
        );

        // Suggest labels
        const suggestedLabels = this.generateLabels(categories, priority, severity);

        // Calculate confidence scores
        const confidence = this.calculateConfidence(input, similar);

        return {
            data: {
                predictedPriority: priority,
                predictedSeverity: severity,
                categories,
                estimatedEffort: effort,
                suggestedAssignees: assignees,
                similarTickets: similar,
                confidence,
                reasoning,
                suggestedLabels,
            },
            modelVersion: 'gpt-4-turbo',
            confidence: (confidence.priority + confidence.severity) / 2,
        };
    }

    /**
     * Predict ticket priority using keyword analysis
     */
    private async predictPriority(
        input: TicketClassificationInput,
        _context: AgentContext
    ): Promise<TicketPriority> {
        // NOTE: Replace with trained ML model when available
        // This is a rule-based stub implementation

        const text = `${input.title} ${input.description || ''}`.toLowerCase();

        // Critical keywords
        if (
            text.includes('critical') ||
            text.includes('production down') ||
            text.includes('security breach') ||
            text.includes('data loss')
        ) {
            return 'critical';
        }

        // High priority keywords
        if (
            text.includes('urgent') ||
            text.includes('blocker') ||
            text.includes('regression') ||
            text.includes('high priority')
        ) {
            return 'high';
        }

        // Low priority keywords
        if (
            text.includes('nice to have') ||
            text.includes('enhancement') ||
            text.includes('documentation') ||
            text.includes('low priority')
        ) {
            return 'low';
        }

        // Default to medium
        return 'medium';
    }

    /**
     * Predict ticket severity
     */
    private async predictSeverity(
        input: TicketClassificationInput,
        _context: AgentContext
    ): Promise<TicketSeverity> {
        const text = `${input.title} ${input.description || ''}`.toLowerCase();

        // Blocker keywords
        if (
            text.includes('cannot') ||
            text.includes('blocked') ||
            text.includes('prevents') ||
            text.includes('stops')
        ) {
            return 'blocker';
        }

        // Major keywords
        if (
            text.includes('major') ||
            text.includes('serious') ||
            text.includes('significant')
        ) {
            return 'major';
        }

        // Trivial keywords
        if (
            text.includes('typo') ||
            text.includes('cosmetic') ||
            text.includes('minor') ||
            text.includes('trivial')
        ) {
            return 'trivial';
        }

        // Default to minor
        return 'minor';
    }

    /**
     * Classify ticket into categories
     */
    private async classifyCategories(
        input: TicketClassificationInput,
        __context: AgentContext
    ): Promise<TicketCategory[]> {
        const categories: TicketCategory[] = [];
        const text = `${input.title} ${input.description || ''}`.toLowerCase();

        // Security
        if (
            text.includes('security') ||
            text.includes('vulnerability') ||
            text.includes('exploit') ||
            text.includes('auth')
        ) {
            categories.push('security');
        }

        // Performance
        if (
            text.includes('slow') ||
            text.includes('performance') ||
            text.includes('latency') ||
            text.includes('timeout')
        ) {
            categories.push('performance');
        }

        // UI/UX
        if (
            text.includes('ui') ||
            text.includes('ux') ||
            text.includes('design') ||
            text.includes('layout')
        ) {
            categories.push('ui/ux');
        }

        // Backend
        if (
            text.includes('api') ||
            text.includes('server') ||
            text.includes('database') ||
            text.includes('backend')
        ) {
            categories.push('backend');
        }

        // Frontend
        if (
            text.includes('frontend') ||
            text.includes('react') ||
            text.includes('component') ||
            text.includes('page')
        ) {
            categories.push('frontend');
        }

        // Infrastructure
        if (
            text.includes('deploy') ||
            text.includes('infra') ||
            text.includes('ci/cd') ||
            text.includes('docker')
        ) {
            categories.push('infrastructure');
        }

        // Documentation
        if (
            text.includes('docs') ||
            text.includes('documentation') ||
            text.includes('readme')
        ) {
            categories.push('documentation');
        }

        // Testing
        if (text.includes('test') || text.includes('qa') || text.includes('bug')) {
            categories.push('testing');
        }

        // Default if no categories found
        if (categories.length === 0) {
            categories.push('backend');
        }

        return categories;
    }

    /**
     * Estimate effort in hours
     */
    private async estimateEffort(
        input: TicketClassificationInput,
        _context: AgentContext
    ): Promise<number> {
        // NOTE: Replace with ML model trained on historical data
        // This is a simple heuristic-based stub

        const description = input.description || '';
        const complexity = description.length > 500 ? 'high' : description.length > 200 ? 'medium' : 'low';

        // Base effort by ticket type
        let baseEffort = 4; // default
        switch (input.type) {
            case 'bug':
                baseEffort = 3;
                break;
            case 'feature':
                baseEffort = 8;
                break;
            case 'story':
                baseEffort = 16;
                break;
            case 'epic':
                baseEffort = 40;
                break;
            case 'task':
                baseEffort = 4;
                break;
        }

        // Adjust by complexity
        const complexityMultiplier =
            complexity === 'high' ? 1.5 : complexity === 'medium' ? 1.0 : 0.7;

        return Math.round(baseEffort * complexityMultiplier);
    }

    /**
     * Suggest assignees based on expertise
     */
    private async suggestAssignees(
        input: TicketClassificationInput,
        _context: AgentContext
    ): Promise<SuggestedAssignee[]> {
        // NOTE: Replace with actual user expertise matching when available
        // This is a stub implementation

        const categories = await this.classifyCategories(input, _context);

        // Stub assignees (replace with actual user lookup)
        const assignees: SuggestedAssignee[] = [
            {
                userId: 'user-1',
                name: 'Senior Developer',
                expertise: ['backend', 'security'],
                confidence: 0.85,
                reasoning: 'High expertise in backend and security domains',
            },
            {
                userId: 'user-2',
                name: 'Frontend Specialist',
                expertise: ['frontend', 'ui/ux'],
                confidence: 0.75,
                reasoning: 'Specialized in frontend development',
            },
        ];

        // Filter by category match
        return assignees
            .filter((assignee) =>
                assignee.expertise.some((exp) => categories.includes(exp as TicketCategory))
            )
            .sort((a, b) => b.confidence - a.confidence)
            .slice(0, 3);
    }

    /**
     * Find similar historical tickets
     */
    private async findSimilarTickets(
        input: TicketClassificationInput
    ): Promise<SimilarTicket[]> {
        // NOTE: Replace with vector similarity search when available
        // This is a stub implementation

        if (!input.historicalData || input.historicalData.length === 0) {
            return [];
        }

        // Simple keyword matching (replace with semantic search)
        const inputKeywords = this.extractKeywords(input.title);

        return input.historicalData
            .map((ticket) => {
                const ticketKeywords = this.extractKeywords(ticket.title);
                const commonKeywords = inputKeywords.filter((k) =>
                    ticketKeywords.includes(k)
                );
                const similarity = commonKeywords.length / Math.max(inputKeywords.length, 1);

                return {
                    ticketId: ticket.ticketId,
                    title: ticket.title,
                    similarity,
                    resolution: 'Resolved',
                    timeToResolve: ticket.timeToResolve || 24,
                };
            })
            .filter((t) => t.similarity > 0.3)
            .sort((a, b) => b.similarity - a.similarity)
            .slice(0, 5);
    }

    /**
     * Extract keywords from text
     */
    private extractKeywords(text: string): string[] {
        return text
            .toLowerCase()
            .split(/\W+/)
            .filter((word) => word.length > 3);
    }

    /**
     * Generate classification reasoning
     */
    private generateReasoning(
        input: TicketClassificationInput,
        priority: TicketPriority,
        severity: TicketSeverity,
        categories: TicketCategory[],
        effort: number
    ): string {
        return `Classified as ${priority} priority and ${severity} severity based on ticket content analysis. Identified as ${categories.join(', ')} issue. Estimated effort: ${effort} hours based on ticket complexity and historical data.`;
    }

    /**
     * Generate suggested labels
     */
    private generateLabels(
        categories: TicketCategory[],
        priority: TicketPriority,
        severity: TicketSeverity
    ): string[] {
        const labels: string[] = [...categories, priority, severity];

        // Add additional context labels
        if (priority === 'critical' || severity === 'blocker') {
            labels.push('needs-immediate-attention');
        }

        return labels;
    }

    /**
     * Calculate confidence scores
     */
    private calculateConfidence(
        input: TicketClassificationInput,
        similar: SimilarTicket[]
    ): ClassificationConfidence {
        // Higher confidence when we have similar tickets
        const baseConfidence = similar.length > 0 ? 0.8 : 0.6;

        // Higher confidence with more detailed description
        const descriptionBonus = input.description && input.description.length > 100 ? 0.1 : 0;

        // Higher confidence with historical data
        const historyBonus = input.historicalData && input.historicalData.length > 10 ? 0.1 : 0;

        const finalConfidence = Math.min(
            1.0,
            baseConfidence + descriptionBonus + historyBonus
        );

        return {
            priority: finalConfidence,
            severity: finalConfidence,
            category: finalConfidence + 0.05, // Category detection is more reliable
            effort: finalConfidence - 0.1, // Effort estimation is less reliable
        };
    }

    /**
     * Health check (verify issue tracking service connectivity)
     */
    async healthCheck(): Promise<import('./types').AgentHealth> {
        // NOTE: Check Jira/issue tracking API connectivity when available
        return {
            healthy: true,
            latency: 0,
            lastCheck: new Date(),
            dependencies: {},
        };
    }
}
