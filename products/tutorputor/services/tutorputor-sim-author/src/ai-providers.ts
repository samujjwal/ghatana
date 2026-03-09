/**
 * Multi-Provider AI Service Abstraction
 * 
 * @doc.type module
 * @doc.purpose Support multiple AI providers (OpenAI, Anthropic, Ollama, etc.)
 * @doc.layer product
 * @doc.pattern Provider
 */

export type AIProvider = 'openai' | 'anthropic' | 'ollama' | 'azure' | 'google' | 'cohere';

export interface AIModel {
    id: string;
    provider: AIProvider;
    name: string;
    maxTokens: number;
    costPer1kTokens: number;
    capabilities: {
        textGeneration: boolean;
        functionCalling: boolean;
        streaming: boolean;
        imageInput: boolean;
    };
}

export interface AIProviderConfig {
    provider: AIProvider;
    apiKey?: string;
    baseUrl?: string;
    model?: string;
    maxRetries?: number;
    timeout?: number;
    organizationId?: string;
    projectId?: string;
    region?: string;
    // Ollama specific
    host?: string;
    port?: number;
    // Azure specific
    deploymentId?: string;
    apiVersion?: string;
    // Google specific
    location?: string;
}

export interface AIRequest {
    prompt: string;
    systemPrompt?: string;
    temperature?: number;
    maxTokens?: number;
    stopSequences?: string[];
    functions?: Array<{
        name: string;
        description: string;
        parameters: Record<string, any>;
    }>;
    stream?: boolean;
}

export interface AIResponse {
    content: string;
    usage?: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
    finishReason?: 'stop' | 'length' | 'content_filter';
    model: string;
    provider: AIProvider;
}

/**
 * Base AI Provider interface
 */
export abstract class BaseAIProvider {
    protected config: AIProviderConfig;
    protected rateLimitState: {
        requests: number[];
        tokens: number[];
    } = { requests: [], tokens: [] };

    constructor(config: AIProviderConfig) {
        this.config = config;
    }

    abstract generate(request: AIRequest): Promise<AIResponse>;
    abstract generateStream(request: AIRequest): AsyncIterable<string>;
    abstract checkHealth(): Promise<boolean>;
    abstract getModels(): Promise<AIModel[]>;

    protected checkRateLimit(requestsPerMinute: number, tokensPerMinute: number): boolean {
        const now = Date.now();
        const oneMinuteAgo = now - 60000;

        // Clean old entries
        this.rateLimitState.requests = this.rateLimitState.requests.filter(t => t > oneMinuteAgo);
        this.rateLimitState.tokens = this.rateLimitState.tokens.filter(t => t > oneMinuteAgo);

        return (
            this.rateLimitState.requests.length < requestsPerMinute &&
            this.rateLimitState.tokens.length < tokensPerMinute
        );
    }

    protected recordRequest(tokens: number): void {
        const now = Date.now();
        this.rateLimitState.requests.push(now);
        for (let i = 0; i < tokens; i++) {
            this.rateLimitState.tokens.push(now);
        }
    }
}

/**
 * OpenAI Provider
 */
export class OpenAIProvider extends BaseAIProvider {
    private client: any; // OpenAI client

    constructor(config: AIProviderConfig) {
        super(config);
        // Initialize OpenAI client
        this.client = new (require('openai')).OpenAI({
            apiKey: config.apiKey,
            organization: config.organizationId,
            baseURL: config.baseUrl,
            timeout: config.timeout || 30000,
        });
    }

    async generate(request: AIRequest): Promise<AIResponse> {
        if (!this.checkRateLimit(60, 90000)) {
            throw new Error('Rate limit exceeded');
        }

        try {
            const completion = await this.client.chat.completions.create({
                model: this.config.model || 'gpt-4-turbo-preview',
                messages: [
                    ...(request.systemPrompt ? [{ role: 'system', content: request.systemPrompt }] : []),
                    { role: 'user', content: request.prompt }
                ],
                temperature: request.temperature || 0.7,
                max_tokens: request.maxTokens || 2000,
                stop: request.stopSequences,
                functions: request.functions,
                stream: false,
            });

            const response: AIResponse = {
                content: completion.choices[0]?.message?.content || '',
                usage: completion.usage ? {
                    promptTokens: completion.usage.prompt_tokens,
                    completionTokens: completion.usage.completion_tokens,
                    totalTokens: completion.usage.total_tokens,
                } : undefined,
                finishReason: completion.choices[0]?.finish_reason as any,
                model: completion.model,
                provider: 'openai'
            };

            this.recordRequest(response.usage?.totalTokens || 0);
            return response;
        } catch (error) {
            throw new Error(`OpenAI API error: ${error}`);
        }
    }

    async *generateStream(request: AIRequest): AsyncIterable<string> {
        if (!this.checkRateLimit(60, 90000)) {
            throw new Error('Rate limit exceeded');
        }

        const stream = await this.client.chat.completions.create({
            model: this.config.model || 'gpt-4-turbo-preview',
            messages: [
                ...(request.systemPrompt ? [{ role: 'system', content: request.systemPrompt }] : []),
                { role: 'user', content: request.prompt }
            ],
            temperature: request.temperature || 0.7,
            max_tokens: request.maxTokens || 2000,
            stream: true,
        });

        for await (const chunk of stream) {
            const content = chunk.choices[0]?.delta?.content;
            if (content) {
                yield content;
            }
        }
    }

    async checkHealth(): Promise<boolean> {
        try {
            await this.client.models.list();
            return true;
        } catch {
            return false;
        }
    }

    async getModels(): Promise<AIModel[]> {
        const models = await this.client.models.list();
        return models.data.map((model: any) => ({
            id: model.id,
            provider: 'openai',
            name: model.id,
            maxTokens: model.id.includes('gpt-4') ? 8192 : 4096,
            costPer1kTokens: model.id.includes('gpt-4') ? 0.03 : 0.002,
            capabilities: {
                textGeneration: true,
                functionCalling: model.id.includes('gpt-4'),
                streaming: true,
                imageInput: model.id.includes('vision'),
            }
        }));
    }
}

/**
 * Anthropic Provider
 */
export class AnthropicProvider extends BaseAIProvider {
    private client: any; // Anthropic client

    constructor(config: AIProviderConfig) {
        super(config);
        this.client = new (require('@anthropic-ai/sdk')).Anthropic({
            apiKey: config.apiKey,
            baseURL: config.baseUrl,
            timeout: config.timeout || 30000,
        });
    }

    async generate(request: AIRequest): Promise<AIResponse> {
        if (!this.checkRateLimit(50, 40000)) {
            throw new Error('Rate limit exceeded');
        }

        try {
            const message = await this.client.messages.create({
                model: this.config.model || 'claude-3-sonnet-20240229',
                max_tokens: request.maxTokens || 2000,
                temperature: request.temperature || 0.7,
                system: request.systemPrompt,
                messages: [{ role: 'user', content: request.prompt }],
                stop_sequences: request.stopSequences,
            });

            const response: AIResponse = {
                content: message.content[0]?.text || '',
                usage: message.usage ? {
                    promptTokens: message.usage.input_tokens,
                    completionTokens: message.usage.output_tokens,
                    totalTokens: message.usage.input_tokens + message.usage.output_tokens,
                } : undefined,
                finishReason: message.stop_reason as any,
                model: message.model,
                provider: 'anthropic'
            };

            this.recordRequest(response.usage?.totalTokens || 0);
            return response;
        } catch (error) {
            throw new Error(`Anthropic API error: ${error}`);
        }
    }

    async *generateStream(request: AIRequest): AsyncIterable<string> {
        if (!this.checkRateLimit(50, 40000)) {
            throw new Error('Rate limit exceeded');
        }

        const stream = await this.client.messages.create({
            model: this.config.model || 'claude-3-sonnet-20240229',
            max_tokens: request.maxTokens || 2000,
            temperature: request.temperature || 0.7,
            system: request.systemPrompt,
            messages: [{ role: 'user', content: request.prompt }],
            stream: true,
        });

        for await (const chunk of stream) {
            if (chunk.type === 'content_block_delta' && chunk.delta.text) {
                yield chunk.delta.text;
            }
        }
    }

    async checkHealth(): Promise<boolean> {
        try {
            await this.client.messages.create({
                model: 'claude-3-haiku-20240307',
                max_tokens: 10,
                messages: [{ role: 'user', content: 'Hi' }],
            });
            return true;
        } catch {
            return false;
        }
    }

    async getModels(): Promise<AIModel[]> {
        // Anthropic doesn't have a models endpoint, return known models
        return [
            {
                id: 'claude-3-opus-20240229',
                provider: 'anthropic',
                name: 'Claude 3 Opus',
                maxTokens: 4096,
                costPer1kTokens: 0.015,
                capabilities: {
                    textGeneration: true,
                    functionCalling: true,
                    streaming: true,
                    imageInput: true,
                }
            },
            {
                id: 'claude-3-sonnet-20240229',
                provider: 'anthropic',
                name: 'Claude 3 Sonnet',
                maxTokens: 4096,
                costPer1kTokens: 0.003,
                capabilities: {
                    textGeneration: true,
                    functionCalling: true,
                    streaming: true,
                    imageInput: true,
                }
            },
            {
                id: 'claude-3-haiku-20240307',
                provider: 'anthropic',
                name: 'Claude 3 Haiku',
                maxTokens: 4096,
                costPer1kTokens: 0.00025,
                capabilities: {
                    textGeneration: true,
                    functionCalling: false,
                    streaming: true,
                    imageInput: true,
                }
            }
        ];
    }
}

/**
 * Ollama Provider (Local Models)
 */
export class OllamaProvider extends BaseAIProvider {
    private baseUrl: string;

    constructor(config: AIProviderConfig) {
        super(config);
        this.baseUrl = `http://${config.host || 'localhost'}:${config.port || 11434}`;
    }

    async generate(request: AIRequest): Promise<AIResponse> {
        if (!this.checkRateLimit(100, 100000)) {
            throw new Error('Rate limit exceeded');
        }

        try {
            const response = await fetch(`${this.baseUrl}/api/generate`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    model: this.config.model || 'llama2',
                    prompt: request.prompt,
                    system: request.systemPrompt,
                    options: {
                        temperature: request.temperature || 0.7,
                        num_predict: request.maxTokens || 2000,
                        stop: request.stopSequences,
                    }
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();

            const aiResponse: AIResponse = {
                content: result.response,
                usage: {
                    promptTokens: result.prompt_eval_count || 0,
                    completionTokens: result.eval_count || 0,
                    totalTokens: (result.prompt_eval_count || 0) + (result.eval_count || 0),
                },
                finishReason: result.done ? 'stop' : 'length',
                model: result.model,
                provider: 'ollama'
            };

            this.recordRequest(aiResponse.usage?.totalTokens || 0);
            return aiResponse;
        } catch (error) {
            throw new Error(`Ollama API error: ${error}`);
        }
    }

    async *generateStream(request: AIRequest): AsyncIterable<string> {
        if (!this.checkRateLimit(100, 100000)) {
            throw new Error('Rate limit exceeded');
        }

        const response = await fetch(`${this.baseUrl}/api/generate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: this.config.model || 'llama2',
                prompt: request.prompt,
                system: request.systemPrompt,
                stream: true,
                options: {
                    temperature: request.temperature || 0.7,
                    num_predict: request.maxTokens || 2000,
                    stop: request.stopSequences,
                }
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body?.getReader();
        if (!reader) throw new Error('No response body');

        const decoder = new TextDecoder();
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value);
            const lines = chunk.split('\n').filter(line => line.trim());

            for (const line of lines) {
                try {
                    const data = JSON.parse(line);
                    if (data.response) {
                        yield data.response;
                    }
                    if (data.done) break;
                } catch {
                    // Ignore malformed JSON
                }
            }
        }
    }

    async checkHealth(): Promise<boolean> {
        try {
            const response = await fetch(`${this.baseUrl}/api/tags`);
            return response.ok;
        } catch {
            return false;
        }
    }

    async getModels(): Promise<AIModel[]> {
        try {
            const response = await fetch(`${this.baseUrl}/api/tags`);
            if (!response.ok) return [];

            const data = await response.json();
            return data.models.map((model: any) => ({
                id: model.name,
                provider: 'ollama',
                name: model.name,
                maxTokens: 4096, // Default for most local models
                costPer1kTokens: 0, // Free for local models
                capabilities: {
                    textGeneration: true,
                    functionCalling: false,
                    streaming: true,
                    imageInput: false,
                }
            }));
        } catch {
            return [];
        }
    }
}

/**
 * AI Provider Factory
 */
export class AIProviderFactory {
    private providers = new Map<AIProvider, typeof BaseAIProvider>();

    constructor() {
        this.providers.set('openai', OpenAIProvider);
        this.providers.set('anthropic', AnthropicProvider);
        this.providers.set('ollama', OllamaProvider);
    }

    createProvider(config: AIProviderConfig): BaseAIProvider {
        const ProviderClass = this.providers.get(config.provider);
        if (!ProviderClass) {
            throw new Error(`Unsupported AI provider: ${config.provider}`);
        }
        return new ProviderClass(config);
    }

    getSupportedProviders(): AIProvider[] {
        return Array.from(this.providers.keys());
    }
}

/**
 * Multi-Provider AI Service
 */
export class MultiProviderAIService {
    private providers: Map<string, BaseAIProvider> = new Map();
    private defaultProvider: string;
    private factory: AIProviderFactory;

    constructor(configs: Array<{ name: string; config: AIProviderConfig; isDefault?: boolean }>) {
        this.factory = new AIProviderFactory();

        for (const { name, config, isDefault } of configs) {
            try {
                const provider = this.factory.createProvider(config);
                this.providers.set(name, provider);
                if (isDefault || !this.defaultProvider) {
                    this.defaultProvider = name;
                }
            } catch (error) {
                console.warn(`Failed to initialize provider ${name}:`, error);
            }
        }

        if (!this.defaultProvider) {
            throw new Error('No valid AI providers configured');
        }
    }

    async generate(request: AIRequest, providerName?: string): Promise<AIResponse> {
        const provider = this.providers.get(providerName || this.defaultProvider);
        if (!provider) {
            throw new Error(`Provider not found: ${providerName || this.defaultProvider}`);
        }

        return provider.generate(request);
    }

    async *generateStream(request: AIRequest, providerName?: string): AsyncIterable<string> {
        const provider = this.providers.get(providerName || this.defaultProvider);
        if (!provider) {
            throw new Error(`Provider not found: ${providerName || this.defaultProvider}`);
        }

        yield* provider.generateStream(request);
    }

    async checkHealth(providerName?: string): Promise<boolean> {
        if (providerName) {
            const provider = this.providers.get(providerName);
            return provider ? provider.checkHealth() : false;
        }

        // Check all providers
        const results = await Promise.all(
            Array.from(this.providers.values()).map(p => p.checkHealth())
        );
        return results.some(r => r);
    }

    async getModels(providerName?: string): Promise<AIModel[]> {
        if (providerName) {
            const provider = this.providers.get(providerName);
            return provider ? provider.getModels() : [];
        }

        // Get models from all providers
        const allModels: AIModel[] = [];
        for (const provider of this.providers.values()) {
            try {
                const models = await provider.getModels();
                allModels.push(...models);
            } catch (error) {
                console.warn('Failed to get models from provider:', error);
            }
        }
        return allModels;
    }

    getAvailableProviders(): string[] {
        return Array.from(this.providers.keys());
    }

    getDefaultProvider(): string {
        return this.defaultProvider;
    }
}

/**
 * Create multi-provider AI service
 */
export function createMultiProviderAIService(
    configs: Array<{ name: string; config: AIProviderConfig; isDefault?: boolean }>
): MultiProviderAIService {
    return new MultiProviderAIService(configs);
}
