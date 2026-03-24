/**
 * Query Parser Agent Implementation
 *
 * Parses natural language queries into structured intents and filters.
 * Handles search, filter, command, question, and navigation intents.
 *
 * @module ai/agents/QueryParserAgent
 * @doc.type class
 * @doc.purpose Natural language query parsing
 * @doc.layer product
 * @doc.pattern AIAgent
 */

import type { AIService } from '../core/AIService';
import { BaseAgent, type ProcessResult } from './BaseAgent';
import type {
    AgentContext,
    ExtractedEntity,
    ItemFilter,
    ParsedQueryAlternative,
    QueryIntent,
    QueryParserInput,
    QueryParserOutput,
} from './types';
import { QueryParserInputSchema, AgentError } from './types';

/**
 * System prompt for query parsing
 */
const QUERY_PARSER_PROMPT = `You are a query parser for a DevSecOps project management platform.
Parse the user's natural language query into a structured format.

Output a JSON object with:
{
  "intent": "search" | "filter" | "command" | "question" | "navigate",
  "entities": [
    {
      "type": "status" | "priority" | "phase" | "owner" | "tag" | "date" | "item_type" | "keyword",
      "value": "extracted value",
      "confidence": 0.0-1.0
    }
  ],
  "filters": {
    "status": ["in-progress", "blocked"],
    "priority": ["high", "critical"],
    "phaseIds": ["development", "security"],
    "tags": ["urgent"],
    "search": "keyword search"
  },
  "confidence": 0.0-1.0
}

Entity mappings:
- Status: not-started, in-progress, blocked, in-review, completed, archived
- Priority: low, medium, high, critical
- Phases: ideation, planning, development, security, testing, deployment, operations
- Item types: feature, story, task, bug, epic

Examples:
- "show me high priority bugs" → intent: search, filters: {priority: ["high"], status: [], search: "bugs"}
- "what's blocking the security phase?" → intent: question, filters: {phaseIds: ["security"], status: ["blocked"]}
- "assign task-123 to John" → intent: command
- "go to settings" → intent: navigate
`;

/**
 * QueryParserAgent for natural language understanding
 */
export class QueryParserAgent extends BaseAgent<
    QueryParserInput,
    QueryParserOutput
> {
    private aiService: AIService;

    constructor(aiService: AIService) {
        super({
            name: 'QueryParserAgent',
            version: '3.0.0',
            description: 'Natural language query parsing for DevSecOps platform',
            capabilities: [
                'intent-detection',
                'entity-extraction',
                'filter-generation',
                'multi-intent',
            ],
            supportedModels: ['gpt-3.5-turbo', 'gpt-4-turbo'],
            latencySLA: 300,
            defaultTimeout: 5000,
        });

        this.aiService = aiService;
    }

    /**
     * Validate input
     */
    protected validateInput(input: QueryParserInput): void {
        const result = QueryParserInputSchema.safeParse(input);
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
     * Process query parsing request
     */
    protected async processRequest(
        input: QueryParserInput,
        context: AgentContext
    ): Promise<ProcessResult<QueryParserOutput>> {
        // Try fast local parsing first
        const localResult = this.tryLocalParse(input.query);
        if (localResult && localResult.confidence > 0.9) {
            return {
                data: localResult,
                modelVersion: 'local-v1',
                confidence: localResult.confidence,
            };
        }

        // Fall back to AI parsing for complex queries
        const enrichedPrompt = this.buildParsePrompt(input);

        const response = await this.aiService.complete(enrichedPrompt, {
            model: 'gpt-3.5-turbo',
            temperature: 0.1,
            maxTokens: 500,
            systemPrompt: QUERY_PARSER_PROMPT,
        });

        const parsed = this.parseAIResponse(response.content);

        // Generate suggestions based on parsed result
        const suggestions = this.generateSuggestions(input.query, parsed);
        const alternatives = this.generateAlternatives(input.query, parsed);

        return {
            data: {
                ...parsed,
                suggestions,
                alternatives,
            },
            tokensUsed: response.usage.totalTokens,
            modelVersion: response.model,
            confidence: parsed.confidence,
        };
    }

    /**
     * Try to parse query locally without AI
     */
    private tryLocalParse(query: string): QueryParserOutput | null {
        const lowerQuery = query.toLowerCase().trim();

        // Simple navigation patterns
        const navPatterns: Record<string, QueryParserOutput> = {
            'go to settings': this.createNavResult('settings'),
            'open settings': this.createNavResult('settings'),
            'show dashboard': this.createNavResult('dashboard'),
            'go home': this.createNavResult('home'),
            'open phases': this.createNavResult('phases'),
            'show workflows': this.createNavResult('workflows'),
        };

        if (navPatterns[lowerQuery]) {
            return navPatterns[lowerQuery];
        }

        // Status filter patterns
        const statusMatch = lowerQuery.match(
            /show (?:me |all )?(blocked|completed|in progress|in-progress) (?:items|tasks)?/
        );
        if (statusMatch) {
            const status = statusMatch[1].replace(' ', '-');
            return {
                intent: 'filter',
                entities: [
                    { type: 'status', value: status, confidence: 0.95, startIndex: 0, endIndex: 0 },
                ],
                filters: { status: [status] },
                confidence: 0.95,
                suggestions: [],
                alternatives: [],
            };
        }

        // Priority patterns
        const priorityMatch = lowerQuery.match(
            /(?:show |find )?(high|critical|low|medium) priority (?:items|tasks|bugs)?/
        );
        if (priorityMatch) {
            const priority = priorityMatch[1];
            const type = lowerQuery.includes('bug') ? 'bugs' : undefined;
            return {
                intent: 'search',
                entities: [
                    { type: 'priority', value: priority, confidence: 0.95, startIndex: 0, endIndex: 0 },
                ],
                filters: {
                    priority: [priority],
                    search: type,
                },
                confidence: 0.95,
                suggestions: [],
                alternatives: [],
            };
        }

        return null;
    }

    /**
     * Create navigation result
     */
    private createNavResult(target: string): QueryParserOutput {
        return {
            intent: 'navigate',
            entities: [
                { type: 'keyword', value: target, confidence: 0.98, startIndex: 0, endIndex: 0 },
            ],
            filters: {},
            confidence: 0.98,
            suggestions: [],
            alternatives: [],
        };
    }

    /**
     * Build prompt for AI parsing
     */
    private buildParsePrompt(input: QueryParserInput): string {
        const parts: string[] = [`Query: "${input.query}"`];

        if (input.currentRoute) {
            parts.push(`Current page: ${input.currentRoute}`);
        }

        if (input.persona) {
            parts.push(`User persona: ${input.persona}`);
        }

        if (input.recentQueries?.length) {
            parts.push(`Recent queries: ${input.recentQueries.slice(0, 3).join(', ')}`);
        }

        return parts.join('\n');
    }

    /**
     * Parse AI response into structured output
     */
    private parseAIResponse(content: string): Omit<QueryParserOutput, 'suggestions' | 'alternatives'> {
        try {
            // Extract JSON from response
            const jsonMatch = content.match(/\{[\s\S]*\}/);
            if (!jsonMatch) {
                return this.defaultResponse();
            }

            const parsed = JSON.parse(jsonMatch[0]);

            return {
                intent: this.validateIntent(parsed.intent),
                entities: this.validateEntities(parsed.entities || []),
                filters: this.validateFilters(parsed.filters || {}),
                confidence: typeof parsed.confidence === 'number' ? parsed.confidence : 0.7,
            };
        } catch {
            return this.defaultResponse();
        }
    }

    /**
     * Validate intent
     */
    private validateIntent(intent: unknown): QueryIntent {
        const validIntents: QueryIntent[] = [
            'search',
            'filter',
            'command',
            'question',
            'navigate',
        ];
        if (typeof intent === 'string' && validIntents.includes(intent as QueryIntent)) {
            return intent as QueryIntent;
        }
        return 'search';
    }

    /**
     * Validate entities
     */
    private validateEntities(entities: unknown[]): ExtractedEntity[] {
        if (!Array.isArray(entities)) {
            return [];
        }

        return entities
            .filter((e): e is Record<string, unknown> => typeof e === 'object' && e !== null)
            .map((e) => ({
                type: String(e.type || 'keyword'),
                value: String(e.value || ''),
                confidence: typeof e.confidence === 'number' ? e.confidence : 0.5,
                startIndex: 0,
                endIndex: 0,
            }));
    }

    /**
     * Validate filters
     */
    private validateFilters(filters: unknown): ItemFilter {
        if (typeof filters !== 'object' || filters === null) {
            return {};
        }

        const f = filters as Record<string, unknown>;
        const result: ItemFilter = {};

        if (Array.isArray(f.status)) {
            result.status = f.status.filter((s): s is string => typeof s === 'string');
        }
        if (Array.isArray(f.priority)) {
            result.priority = f.priority.filter((p): p is string => typeof p === 'string');
        }
        if (Array.isArray(f.phaseIds)) {
            result.phaseIds = f.phaseIds.filter((p): p is string => typeof p === 'string');
        }
        if (Array.isArray(f.tags)) {
            result.tags = f.tags.filter((t): t is string => typeof t === 'string');
        }
        if (typeof f.search === 'string') {
            result.search = f.search;
        }

        return result;
    }

    /**
     * Default response for failed parsing
     */
    private defaultResponse(): Omit<QueryParserOutput, 'suggestions' | 'alternatives'> {
        return {
            intent: 'search',
            entities: [],
            filters: {},
            confidence: 0.5,
        };
    }

    /**
     * Generate query suggestions
     */
    private generateSuggestions(
        query: string,
        parsed: Omit<QueryParserOutput, 'suggestions' | 'alternatives'>
    ): string[] {
        const suggestions: string[] = [];

        if (parsed.intent === 'search' && !parsed.filters.status?.length) {
            suggestions.push(`${query} that are blocked`);
            suggestions.push(`${query} in progress`);
        }

        if (parsed.intent === 'filter') {
            suggestions.push('Sort by priority');
            suggestions.push('Group by phase');
        }

        return suggestions.slice(0, 3);
    }

    /**
     * Generate alternative interpretations
     */
    private generateAlternatives(
        _query: string,
        parsed: Omit<QueryParserOutput, 'suggestions' | 'alternatives'>
    ): ParsedQueryAlternative[] {
        const alternatives: ParsedQueryAlternative[] = [];

        // If parsed as search, could also be filter
        if (parsed.intent === 'search' && parsed.filters.search) {
            alternatives.push({
                intent: 'filter',
                entities: parsed.entities,
                confidence: parsed.confidence * 0.8,
            });
        }

        return alternatives;
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
                aiService: aiHealthy ? 'healthy' : 'degraded', // Can fall back to local
                localParser: 'healthy',
            };
        } catch {
            return {
                aiService: 'degraded',
                localParser: 'healthy',
            };
        }
    }
}
