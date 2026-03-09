import type { CompletionOptions, CompletionResponse, IAIService } from '@ghatana/yappc-ai';

export class DesignPromptService {
    private readonly ai: IAIService;

    constructor(ai: IAIService) {
        this.ai = ai;
    }

    async completePrompt(prompt: string, options?: CompletionOptions): Promise<CompletionResponse> {
        return this.ai.complete(prompt, options);
    }
}
