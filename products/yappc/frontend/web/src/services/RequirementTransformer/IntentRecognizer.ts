/**
 * Intent Recognizer
 *
 * Recognizes user intent from natural language input.
 *
 * @packageDocumentation
 */

import type { IntentConfig } from '@yappc/config-schema';

/**
 * @doc.type service
 * @doc.purpose Recognize user intent from natural language
 * @doc.layer product
 * @doc.pattern Service
 */
export class IntentRecognizer {
  private readonly intentPatterns: Map<string, RegExp[]> = new Map([
    ['create-page', [/create\s+(?:a\s+)?(?:new\s+)?page/i, /build\s+(?:a\s+)?page/i, /design\s+(?:a\s+)?page/i]],
    ['create-form', [/create\s+(?:a\s+)?form/i, /build\s+(?:a\s+)?form/i, /add\s+(?:a\s+)?form/i]],
    ['create-dashboard', [/create\s+(?:a\s+)?dashboard/i, /build\s+(?:a\s+)?dashboard/i, /show\s+(?:a\s+)?dashboard/i]],
    ['create-table', [/create\s+(?:a\s+)?table/i, /display\s+(?:a\s+)?table/i, /list\s+(?:a\s+)?items/i]],
    ['add-component', [/add\s+(?:a\s+)?component/i, /include\s+(?:a\s+)?component/i, /insert\s+(?:a\s+)?component/i]],
    ['navigation', [/navigate\s+to/i, /go\s+to/i, /link\s+to/i, /open\s+page/i]],
    ['data-binding', [/bind\s+data/i, /connect\s+data/i, /display\s+data/i]],
    ['event-handling', [/handle\s+(?:an\s+)?event/i, /on\s+(?:click|submit|change)/i]],
    ['validation', [/validate\s+(?:input|form)/i, /check\s+(?:input|form)/i, /require\s+(?:input|field)/i]],
  ]);

  /**
   * Recognize the intent from natural language input.
   *
   * @param input - Natural language input
   * @returns Recognized intent with confidence
   */
  recognize(input: string): { intent: string; confidence: number; matchedPattern?: string } {
    const lowerInput = input.toLowerCase();

    for (const [intent, patterns] of this.intentPatterns.entries()) {
      for (const pattern of patterns) {
        const match = lowerInput.match(pattern);
        if (match) {
          const confidence = this.calculateConfidence(match[0], lowerInput);
          return {
            intent,
            confidence,
            matchedPattern: match[0],
          };
        }
      }
    }

    return {
      intent: 'unknown',
      confidence: 0,
    };
  }

  /**
   * Create an IntentConfig from natural language input.
   *
   * @param input - Natural language input
   * @returns IntentConfig
   */
  createIntentConfig(input: string): IntentConfig {
    const recognition = this.recognize(input);

    return {
      id: this.generateIntentId(),
      intent: input,
      description: `User intent: ${input}`,
      requirements: [],
      metadata: {
        recognizedIntent: recognition.intent,
        confidence: recognition.confidence,
        matchedPattern: recognition.matchedPattern,
      },
      aiGenerated: false,
      aiConfidence: recognition.confidence,
    };
  }

  /**
   * Calculate confidence score for a match.
   */
  private calculateConfidence(matched: string, fullInput: string): number {
    const ratio = matched.length / fullInput.length;
    const positionBonus = fullInput.indexOf(matched) < fullInput.length * 0.3 ? 0.2 : 0;
    return Math.min(0.95, ratio + positionBonus);
  }

  /**
   * Generate a unique intent ID.
   */
  private generateIntentId(): string {
    return `intent-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }
}
