import type { AccessibilityReport } from '@ghatana/accessibility-audit';
import type { CompletionOptions, IAIService } from '@ghatana/yappc-ai';

export class AccessibilityAuditService {
    private readonly ai: IAIService;

    constructor(ai: IAIService) {
        this.ai = ai;
    }

    async summarizeReport(report: AccessibilityReport, options?: CompletionOptions): Promise<string> {
        const prompt = `Summarize the following accessibility report in plain language for designers and developers.\n\nReport JSON:\n${JSON.stringify(report)}`;
        const result = await this.ai.complete(prompt, options);
        return result.content;
    }
}
