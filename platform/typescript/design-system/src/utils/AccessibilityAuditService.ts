// TODO: Update to use @ghatana/ui-integration AI services when available
// import type { CompletionOptions, IAIService } from '@ghatana/ui-integration';

export interface AccessibilityReport {
    score: number;
    issues: string[];
    suggestions: string[];
}

export interface CompletionOptions {
    model?: string;
    temperature?: number;
    maxTokens?: number;
}

export interface IAIService {
    complete(prompt: string, options?: CompletionOptions): Promise<{ content: string }>;
}

export class AccessibilityAuditService {
    private readonly ai: IAIService;

    constructor(ai: IAIService) {
        this.ai = ai;
    }

    async summarizeReport(report: AccessibilityReport, options?: CompletionOptions): Promise<string> {
        const prompt = `Summarize the following accessibility report in plain language for designers and developers.\n\nReport JSON:\n${JSON.stringify(report)}`;
        const response = await this.ai.complete(prompt, options);
        return response.content;
    }
}
