// TODO: Update to use @ghatana/ui-integration AI services when available

export interface CompletionOptions {
    model?: string;
    temperature?: number;
    maxTokens?: number;
}

export interface CompletionResponse {
    content: string;
    usage?: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
}

export interface IAIService {
    complete(prompt: string, options?: CompletionOptions): Promise<CompletionResponse>;
}

export class DesignPromptService {
    private readonly ai: IAIService;

    constructor(ai: IAIService) {
        this.ai = ai;
    }

    async generateDesignPrompt(requirements: string): Promise<string> {
        const response = await this.ai.complete(
            `Generate UI design components for: ${requirements}\n\nProvide React component code with TypeScript types.`
        );
        return response.content;
    }
}
