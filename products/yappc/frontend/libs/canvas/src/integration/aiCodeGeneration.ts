/**
 * AI-Powered Code Generation Service
 *
 * Integrates canvas nodes with AI service for intelligent code generation.
 * Uses LLMs to generate production-ready code from high-level specs.
 *
 * @doc.type class
 * @doc.purpose AI-powered code generation from canvas nodes
 * @doc.layer product
 * @doc.pattern Service
 */

import type { IAIService, CompletionOptions } from '@ghatana/yappc-ai/core';
import {
    buildServicePrompt,
    buildAPIRoutePrompt,
    buildPrismaSchemaPrompt,
    buildArchitecturePrompt,
    buildRequirementExtractionPrompt,
    buildTestGenerationPrompt,
    PERSONA_SYSTEM_PROMPTS,
    type Persona,
} from '@ghatana/yappc-ai/core';
import type {
    ServiceNodeData,
    APIEndpointNodeData,
    DatabaseNodeData,
} from './types';
import type { GeneratedFile, CodeGenerationResult } from './types';

type RequirementNodeData = {
    label: string;
    type: 'requirement';
    persona: 'pm' | 'architect' | 'developer' | 'qa' | 'ux';
    description?: string;
    priority: string;
    userStory?: string;
    acceptanceCriteria?: string[];
    storyPoints?: number;
    status?: 'draft' | 'ready' | 'in-progress' | 'completed' | 'error';
    metadata?: Record<string, unknown>;
};

type ArchitectureNodeData = {
    label: string;
    type: 'architecture';
    persona: 'pm' | 'architect' | 'developer' | 'qa' | 'ux';
    description?: string;
    components?: string[];
    technologies?: string[];
    constraints?: string[];
    decisions?: string[];
    status?: 'draft' | 'ready' | 'in-progress' | 'completed' | 'error';
    metadata?: Record<string, unknown>;
};

// ============================================================================
// SERVICE INTERFACE
// ============================================================================

export interface AICodeGenerationOptions {
    /** AI model to use (e.g., 'gpt-4', 'claude-3-opus') */
    model?: string;
    /** Temperature for generation (0-1, higher = more creative) */
    temperature?: number;
    /** Persona context for generation */
    persona?: Persona;
    /** Include tests in generation */
    includeTests?: boolean;
    /** Include documentation */
    includeDocumentation?: boolean;
}

// ============================================================================
// AI CODE GENERATION SERVICE
// ============================================================================

/**
 * AI-powered code generation service
 *
 * Generates production-ready code from canvas nodes using LLMs.
 * Supports TypeScript services, API routes, database schemas, and architecture docs.
 */
export class AICodeGenerationService {
    private aiService: IAIService;

    constructor(aiService: IAIService) {
        this.aiService = aiService;
    }

    /**
     * Generate code from a service or requirement node
     */
    async generateService(
        data: ServiceNodeData | RequirementNodeData,
        options: AICodeGenerationOptions = {}
    ): Promise<CodeGenerationResult> {
        try {
            const promptData =
                'type' in data && data.type === 'service'
                    ? {
                        ...data,
                        persona: (data as unknown as { persona?: string }).persona ?? 'developer',
                        status: (data as unknown as { status?: string }).status ?? 'draft',
                    }
                    : data;

            const prompt = buildServicePrompt(
                promptData as unknown as Parameters<typeof buildServicePrompt>[0]
            );
            const systemPrompt = options.persona ? PERSONA_SYSTEM_PROMPTS[options.persona] : PERSONA_SYSTEM_PROMPTS.developer;

            const response = await this.aiService.complete(prompt, {
                model: options.model || 'gpt-4',
                temperature: options.temperature ?? 0.3,
                systemPrompt,
                maxTokens: 2000,
            });

            const files: GeneratedFile[] = [
                {
                    path: `src/services/${this.toFileName(data.label)}.service.ts`,
                    content: this.extractCode(response.content),
                    language: 'typescript',
                    type: 'source',
                },
            ];

            // Generate tests if requested
            if (options.includeTests) {
                const testCode = await this.generateTests(response.content, 'unit');
                files.push({
                    path: `src/services/__tests__/${this.toFileName(data.label)}.service.test.ts`,
                    content: testCode,
                    language: 'typescript',
                    type: 'test',
                });
            }

            return {
                success: true,
                files,
                summary: `Generated service for ${data.label} using AI (${response.usage.totalTokens} tokens)`,
            };
        } catch (error) {
            return {
                success: false,
                files: [],
                summary: 'AI code generation failed',
                errors: [error instanceof Error ? error.message : 'Unknown error'],
            };
        }
    }

    /**
     * Generate API route from endpoint spec
     */
    async generateAPIRoute(
        data: APIEndpointNodeData,
        options: AICodeGenerationOptions = {}
    ): Promise<CodeGenerationResult> {
        try {
            const prompt = buildAPIRoutePrompt(data);
            const systemPrompt = PERSONA_SYSTEM_PROMPTS.developer;

            const response = await this.aiService.complete(prompt, {
                model: options.model || 'gpt-4',
                temperature: options.temperature ?? 0.2,
                systemPrompt,
                maxTokens: 1500,
            });

            const routeName = this.extractRouteName(data.path);
            const files: GeneratedFile[] = [
                {
                    path: `src/routes/${routeName}.ts`,
                    content: this.extractCode(response.content),
                    language: 'typescript',
                    type: 'source',
                },
            ];

            return {
                success: true,
                files,
                summary: `Generated API route for ${data.path} using AI (${response.usage.totalTokens} tokens)`,
            };
        } catch (error) {
            return {
                success: false,
                files: [],
                summary: 'AI API route generation failed',
                errors: [error instanceof Error ? error.message : 'Unknown error'],
            };
        }
    }

    /**
     * Generate Prisma schema from database design
     */
    async generateDatabaseSchema(
        data: DatabaseNodeData,
        options: AICodeGenerationOptions = {}
    ): Promise<CodeGenerationResult> {
        try {
            const prompt = buildPrismaSchemaPrompt(data);
            const systemPrompt = PERSONA_SYSTEM_PROMPTS.architect;

            const response = await this.aiService.complete(prompt, {
                model: options.model || 'gpt-4',
                temperature: options.temperature ?? 0.1,
                systemPrompt,
                maxTokens: 1500,
            });

            const files: GeneratedFile[] = [
                {
                    path: 'prisma/schema.prisma',
                    content: this.extractCode(response.content),
                    language: 'prisma',
                    type: 'config',
                },
            ];

            return {
                success: true,
                files,
                summary: `Generated Prisma schema for ${data.label} using AI (${response.usage.totalTokens} tokens)`,
            };
        } catch (error) {
            return {
                success: false,
                files: [],
                summary: 'AI schema generation failed',
                errors: [error instanceof Error ? error.message : 'Unknown error'],
            };
        }
    }

    /**
     * Generate architecture documentation
     */
    async generateArchitecture(
        data: ArchitectureNodeData | RequirementNodeData,
        options: AICodeGenerationOptions = {}
    ): Promise<CodeGenerationResult> {
        try {
            const prompt = buildArchitecturePrompt(data);
            const systemPrompt = PERSONA_SYSTEM_PROMPTS.architect;

            const response = await this.aiService.complete(prompt, {
                model: options.model || 'gpt-4',
                temperature: options.temperature ?? 0.4,
                systemPrompt,
                maxTokens: 3000,
            });

            const files: GeneratedFile[] = [
                {
                    path: `docs/architecture/${this.toFileName(data.label)}.md`,
                    content: response.content,
                    language: 'markdown',
                    type: 'documentation',
                },
            ];

            return {
                success: true,
                files,
                summary: `Generated architecture doc for ${data.label} using AI (${response.usage.totalTokens} tokens)`,
            };
        } catch (error) {
            return {
                success: false,
                files: [],
                summary: 'AI architecture generation failed',
                errors: [error instanceof Error ? error.message : 'Unknown error'],
            };
        }
    }

    /**
     * Extract structured requirements from natural language
     */
    async extractRequirements(userInput: string, options: AICodeGenerationOptions = {}): Promise<RequirementNodeData | null> {
        try {
            const prompt = buildRequirementExtractionPrompt(userInput);
            const systemPrompt = PERSONA_SYSTEM_PROMPTS.pm;

            const response = await this.aiService.complete(prompt, {
                model: options.model || 'gpt-4',
                temperature: options.temperature ?? 0.2,
                systemPrompt,
                maxTokens: 1000,
            });

            // Parse JSON response
            const jsonMatch = response.content.match(/\{[\s\S]*\}/);
            if (!jsonMatch) {
                throw new Error('Failed to extract JSON from AI response');
            }

            const extracted = JSON.parse(jsonMatch[0]);

            return {
                label: extracted.userStory.split(',')[0].replace('As a ', '').trim(),
                type: 'requirement',
                persona: 'pm',
                userStory: extracted.userStory,
                acceptanceCriteria: extracted.acceptanceCriteria,
                priority: extracted.priority,
                storyPoints: extracted.storyPoints,
                status: 'draft',
            };
        } catch (error) {
            console.error('Failed to extract requirements:', error);
            return null;
        }
    }

    /**
     * Generate tests for existing code
     */
    async generateTests(code: string, testType: 'unit' | 'integration' | 'e2e'): Promise<string> {
        const prompt = buildTestGenerationPrompt(code, testType);
        const systemPrompt = PERSONA_SYSTEM_PROMPTS.qa;

        const response = await this.aiService.complete(prompt, {
            model: 'gpt-4',
            temperature: 0.2,
            systemPrompt,
            maxTokens: 2000,
        });

        return this.extractCode(response.content);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Extract code from markdown code blocks
     */
    private extractCode(content: string): string {
        // Try to extract from markdown code blocks
        const codeBlockMatch = content.match(/```(?:typescript|ts|javascript|js|prisma)?\n([\s\S]*?)```/);
        if (codeBlockMatch) {
            return codeBlockMatch[1].trim();
        }

        // If no code block, return as-is
        return content.trim();
    }

    /**
     * Convert label to filename
     */
    private toFileName(label: string): string {
        return label
            .toLowerCase()
            .replace(/\s+/g, '-')
            .replace(/[^a-z0-9-]/g, '');
    }

    /**
     * Extract route name from path
     */
    private extractRouteName(path: string): string {
        const parts = path.split('/').filter(Boolean);
        return parts[parts.length - 1] || 'index';
    }
}

/**
 * Create AI code generation service instance
 */
export function createAICodeGenerationService(aiService: IAIService): AICodeGenerationService {
    return new AICodeGenerationService(aiService);
}
