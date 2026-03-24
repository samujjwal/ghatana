/**
 * Copilot Agent Implementation
 *
 * Handles conversational AI interactions via command palette.
 * Supports multi-turn conversations with context awareness.
 *
 * @module ai/agents/CopilotAgent
 * @doc.type class
 * @doc.purpose Conversational AI agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import type { AIService } from '../core/AIService';
import { BaseAgent, type ProcessResult } from './BaseAgent';
import type {
    AgentContext,
    CopilotAction,
    CopilotInput,
    CopilotOutput,
    ConversationMessage,
} from './types';
import { CopilotInputSchema, AgentError } from './types';

/**
 * System prompt for the Copilot agent
 */
const COPILOT_SYSTEM_PROMPT = `You are an intelligent DevSecOps assistant integrated into a project management platform.
Your role is to help users navigate, manage, and optimize their software development lifecycle.

You can:
1. Answer questions about project status, metrics, and insights
2. Execute actions like creating items, updating status, or reassigning tasks
3. Navigate users to relevant pages or views
4. Analyze data and provide recommendations
5. Generate reports and summaries

When suggesting actions, always structure them as JSON with:
- type: 'navigate' | 'filter' | 'create' | 'update' | 'delete' | 'execute' | 'query'
- target: the entity or page to act on
- parameters: action-specific parameters
- impact: 'low' | 'medium' | 'high'
- requiresConfirmation: boolean (true for destructive actions)

Be concise, helpful, and always explain your reasoning.`;

/**
 * CopilotAgent for conversational AI
 */
export class CopilotAgent extends BaseAgent<CopilotInput, CopilotOutput> {
    private aiService: AIService;

    constructor(aiService: AIService) {
        super({
            name: 'CopilotAgent',
            version: '2.0.0',
            description: 'Conversational AI assistant for DevSecOps platform',
            capabilities: [
                'conversation',
                'action-execution',
                'context-awareness',
                'multi-turn',
                'streaming',
            ],
            supportedModels: ['gpt-4-turbo', 'gpt-4', 'claude-3-opus', 'claude-3-sonnet'],
            latencySLA: 2000,
            defaultTimeout: 30000,
        });

        this.aiService = aiService;
    }

    /**
     * Validate copilot input
     */
    protected validateInput(input: CopilotInput): void {
        const result = CopilotInputSchema.safeParse(input);
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
     * Process copilot request
     */
    protected async processRequest(
        input: CopilotInput,
        context: AgentContext
    ): Promise<ProcessResult<CopilotOutput>> {
        // Build enriched context
        const enrichedPrompt = this.buildEnrichedPrompt(input, context);

        // Get AI response
        const response = await this.aiService.complete(enrichedPrompt, {
            model: context.preferences?.preferredModel || 'gpt-4-turbo',
            temperature: context.preferences?.temperature || 0.7,
            maxTokens: context.preferences?.maxTokens || 2048,
            systemPrompt: COPILOT_SYSTEM_PROMPT,
        });

        // Parse action from response
        const action = this.parseAction(response.content);

        // Generate follow-up suggestions
        const suggestions = await this.generateFollowUps(input, action);

        return {
            data: {
                response: response.content,
                action,
                suggestions,
            },
            tokensUsed: response.usage.totalTokens,
            modelVersion: response.model,
            confidence: action?.confidence || 0.9,
        };
    }

    /**
     * Build enriched prompt with context
     */
    private buildEnrichedPrompt(
        input: CopilotInput,
        context: AgentContext
    ): string {
        const contextParts: string[] = [];

        // Add conversation history
        if (input.conversationHistory?.length) {
            contextParts.push('Previous conversation:');
            input.conversationHistory.forEach((msg) => {
                contextParts.push(`${msg.role.toUpperCase()}: ${msg.content}`);
            });
            contextParts.push('---');
        }

        // Add current context
        if (input.currentView) {
            contextParts.push(`Current view: ${input.currentView}`);
        }

        if (input.selectedItems?.length) {
            contextParts.push(`Selected items: ${input.selectedItems.join(', ')}`);
        }

        if (input.recentActions?.length) {
            contextParts.push(
                `Recent actions: ${input.recentActions.slice(0, 5).join(', ')}`
            );
        }

        // Add user context
        contextParts.push(`User: ${context.userId}`);
        contextParts.push(`Workspace: ${context.workspaceId}`);

        // Combine with user query
        return `${contextParts.join('\n')}\n\nUser query: ${input.query}`;
    }

    /**
     * Parse action from AI response
     */
    private parseAction(content: string): CopilotAction | undefined {
        // Look for JSON action block in response
        const jsonMatch = content.match(/```json\s*([\s\S]*?)\s*```/);
        if (!jsonMatch) {
            // Try to find inline JSON
            const inlineMatch = content.match(/\{[\s\S]*?"type"[\s\S]*?\}/);
            if (!inlineMatch) {
                return undefined;
            }
            try {
                return this.validateAction(JSON.parse(inlineMatch[0]));
            } catch {
                return undefined;
            }
        }

        try {
            return this.validateAction(JSON.parse(jsonMatch[1]));
        } catch {
            return undefined;
        }
    }

    /**
     * Validate parsed action
     */
    private validateAction(action: unknown): CopilotAction | undefined {
        if (!action || typeof action !== 'object') {
            return undefined;
        }

        const a = action as Record<string, unknown>;
        const validTypes = [
            'navigate',
            'filter',
            'create',
            'update',
            'delete',
            'execute',
            'query',
        ];

        if (!validTypes.includes(a.type as string)) {
            return undefined;
        }

        return {
            type: a.type as CopilotAction['type'],
            target: String(a.target || ''),
            parameters: (a.parameters as Record<string, unknown>) || {},
            impact: (['low', 'medium', 'high'].includes(a.impact as string)
                ? a.impact
                : 'low') as CopilotAction['impact'],
            confidence: typeof a.confidence === 'number' ? a.confidence : 0.8,
            requiresConfirmation:
                a.requiresConfirmation === true || a.impact === 'high',
        };
    }

    /**
     * Generate follow-up suggestions
     */
    private async generateFollowUps(
        input: CopilotInput,
        action?: CopilotAction
    ): Promise<string[]> {
        const suggestions: string[] = [];

        // Context-based suggestions
        if (action?.type === 'navigate') {
            suggestions.push('Show me more details');
            suggestions.push('What else is on this page?');
        } else if (action?.type === 'update' || action?.type === 'create') {
            suggestions.push('What are the next steps?');
            suggestions.push('Who should be notified?');
        } else if (action?.type === 'query') {
            suggestions.push('Can you elaborate?');
            suggestions.push('Show me related items');
        }

        // Generic suggestions
        if (input.selectedItems?.length) {
            suggestions.push('Compare these items');
            suggestions.push('Show common dependencies');
        }

        return suggestions.slice(0, 4);
    }

    /**
     * Check dependencies
     */
    protected async checkDependencies(): Promise<
        Record<string, 'healthy' | 'degraded' | 'unhealthy'>
    > {
        try {
            const aiHealthy = await this.aiService.healthCheck();
            return {
                aiService: aiHealthy ? 'healthy' : 'unhealthy',
            };
        } catch {
            return {
                aiService: 'unhealthy',
            };
        }
    }

    /**
     * Execute a copilot action (for action execution flow)
     */
    async executeAction(
        action: CopilotAction,
        context: AgentContext
    ): Promise<{ success: boolean; result?: unknown; error?: string }> {
        // This would integrate with the actual action execution system
        // For now, return success with the action details
        return {
            success: true,
            result: {
                actionType: action.type,
                target: action.target,
                parameters: action.parameters,
                executedAt: new Date().toISOString(),
                executedBy: context.userId,
            },
        };
    }
}
