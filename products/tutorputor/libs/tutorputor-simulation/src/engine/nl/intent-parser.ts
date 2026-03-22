/**
 * NL Intent Parser - Parses natural language commands for simulation refinement.
 *
 * @doc.type class
 * @doc.purpose Parse and classify user intents for simulation modification
 * @doc.layer product
 * @doc.pattern Parser
 */

import { z } from 'zod';

export type IntentType =
  | 'add_entity'
  | 'remove_entity'
  | 'modify_entity'
  | 'add_step'
  | 'remove_step'
  | 'modify_step'
  | 'change_speed'
  | 'change_visual'
  | 'add_annotation'
  | 'change_domain_config'
  | 'explain'
  | 'clarify'
  | 'undo'
  | 'redo'
  | 'unknown';

export interface IntentParams {
  targetEntity?: string;
  targetStep?: string | number;
  newValue?: unknown;
  property?: string;
  entityType?: string;
  position?: { x: number; y: number; z?: number };
  visual?: { color?: string; size?: number; opacity?: number; shape?: string };
  duration?: number;
  text?: string;
  color?: string;
}

export interface ParsedIntent {
  type: IntentType;
  confidence: number;
  params: IntentParams;
  originalInput: string;
  normalizedInput: string;
}

/**
 * Keyword patterns for intent classification.
 */
const INTENT_PATTERNS: Array<{ pattern: RegExp; intent: IntentType; extractor?: (match: RegExpExecArray) => Partial<IntentParams> }> = [
  // Entity operations
  {
    pattern: /add\s+(a\s+)?(?:new\s+)?(\w+)(?:\s+(?:at|to)\s+(?:position\s+)?(\d+),?\s*(\d+))?/i,
    intent: 'add_entity',
    extractor: (match) => ({
      entityType: match[2],
      position: match[3] ? { x: parseInt(match[3]), y: parseInt(match[4] ?? '0') } : undefined,
    }),
  },
  {
    pattern: /(?:remove|delete)\s+(?:the\s+)?(\w+)/i,
    intent: 'remove_entity',
    extractor: (match) => ({ targetEntity: match[1] }),
  },
  {
    pattern: /(?:change|modify|update|set)\s+(?:the\s+)?(\w+)(?:'s)?\s+(\w+)\s+to\s+(.+)/i,
    intent: 'modify_entity',
    extractor: (match) => ({
      targetEntity: match[1],
      property: match[2],
      newValue: match[3].trim(),
    }),
  },

  // Step operations
  {
    pattern: /add\s+(?:a\s+)?(?:new\s+)?step\s+(?:for\s+)?(.+)/i,
    intent: 'add_step',
    extractor: (match) => ({ text: match[1] }),
  },
  {
    pattern: /(?:remove|delete)\s+step\s+(\d+)/i,
    intent: 'remove_step',
    extractor: (match) => ({ targetStep: parseInt(match[1]) }),
  },
  {
    pattern: /(?:change|modify)\s+step\s+(\d+)\s+(.+)/i,
    intent: 'modify_step',
    extractor: (match) => ({ targetStep: parseInt(match[1]), text: match[2] }),
  },

  // Speed control
  {
    pattern: /(?:make\s+it\s+)?(?:go\s+)?(?:run\s+)?(\d+(?:\.\d+)?x?)\s*(?:faster|slower|speed)/i,
    intent: 'change_speed',
    extractor: (match) => ({
      duration: parseFloat(match[1].replace('x', '')),
    }),
  },
  {
    pattern: /(?:slow\s*down|speed\s*up)/i,
    intent: 'change_speed',
    extractor: (match) => ({
      duration: match[0].toLowerCase().includes('slow') ? 2 : 0.5,
    }),
  },

  // Visual changes
  {
    pattern: /(?:make|change|set)\s+(?:the\s+)?(\w+)\s+(?:to\s+)?(?:be\s+)?(?:color\s+)?(red|blue|green|yellow|orange|purple|pink|black|white|#[0-9a-fA-F]{6})/i,
    intent: 'change_visual',
    extractor: (match) => ({
      targetEntity: match[1],
      visual: { color: match[2] },
    }),
  },
  {
    pattern: /(?:make|change)\s+(?:the\s+)?(\w+)\s+(bigger|smaller|larger|tiny|huge)/i,
    intent: 'change_visual',
    extractor: (match) => {
      const sizeMap: Record<string, number> = {
        tiny: 0.25,
        smaller: 0.5,
        bigger: 1.5,
        larger: 2,
        huge: 3,
      };
      return {
        targetEntity: match[1],
        visual: { size: sizeMap[match[2].toLowerCase()] ?? 1 },
      };
    },
  },

  // Annotations
  {
    pattern: /(?:add|show)\s+(?:a\s+)?(?:text\s+)?annotation\s+(?:saying\s+)?["']?(.+?)["']?$/i,
    intent: 'add_annotation',
    extractor: (match) => ({ text: match[1] }),
  },
  {
    pattern: /(?:label|annotate)\s+(?:the\s+)?(\w+)\s+(?:as|with)\s+["']?(.+?)["']?$/i,
    intent: 'add_annotation',
    extractor: (match) => ({
      targetEntity: match[1],
      text: match[2],
    }),
  },

  // Explanation requests
  {
    pattern: /(?:explain|what\s+is|how\s+does|why\s+does|tell\s+me\s+about)/i,
    intent: 'explain',
    extractor: (match) => ({ text: match[0] }),
  },

  // Clarification
  {
    pattern: /(?:what\s+do\s+you\s+mean|can\s+you\s+clarify|i\s+don'?t\s+understand)/i,
    intent: 'clarify',
    extractor: () => ({}),
  },

  // Undo/Redo
  {
    pattern: /\bundo\b/i,
    intent: 'undo',
    extractor: () => ({}),
  },
  {
    pattern: /\bredo\b/i,
    intent: 'redo',
    extractor: () => ({}),
  },
];

/**
 * Color name to hex mapping.
 */
const COLOR_MAP: Record<string, string> = {
  red: '#FF0000',
  blue: '#0000FF',
  green: '#00FF00',
  yellow: '#FFFF00',
  orange: '#FFA500',
  purple: '#800080',
  pink: '#FFC0CB',
  black: '#000000',
  white: '#FFFFFF',
};

/**
 * NL Intent Parser class.
 */
export class NLIntentParser {
  /**
   * Parse a natural language input into a structured intent.
   *
   * @param input - The natural language input
   * @returns Parsed intent
   */
  parse(input: string): ParsedIntent {
    const normalizedInput = this.normalize(input);

    // Try each pattern
    for (const { pattern, intent, extractor } of INTENT_PATTERNS) {
      const match = pattern.exec(normalizedInput);
      if (match) {
        const params = extractor ? extractor(match) : {};

        // Normalize color if present
        if (params.visual?.color && !params.visual.color.startsWith('#')) {
          params.visual.color = COLOR_MAP[params.visual.color.toLowerCase()] ?? params.visual.color;
        }

        return {
          type: intent,
          confidence: this.calculateConfidence(match, normalizedInput),
          params,
          originalInput: input,
          normalizedInput,
        };
      }
    }

    // No match - return unknown intent
    return {
      type: 'unknown',
      confidence: 0,
      params: { text: normalizedInput },
      originalInput: input,
      normalizedInput,
    };
  }

  /**
   * Parse multiple intents from a compound sentence.
   *
   * @param input - The natural language input
   * @returns Array of parsed intents
   */
  parseMultiple(input: string): ParsedIntent[] {
    // Split on conjunctions
    const clauses = input.split(/\s+(?:and|then|also|,\s*then)\s+/i);
    return clauses.map((clause) => this.parse(clause.trim()));
  }

  /**
   * Normalize input text.
   */
  private normalize(input: string): string {
    return input
      .trim()
      .replace(/\s+/g, ' ')
      .replace(/[.,!?]+$/g, '');
  }

  /**
   * Calculate confidence score.
   */
  private calculateConfidence(match: RegExpExecArray, input: string): number {
    // Base confidence on match coverage
    const matchLength = match[0].length;
    const inputLength = input.length;
    const coverage = matchLength / inputLength;

    // Higher coverage = higher confidence
    return Math.min(0.95, coverage * 0.8 + 0.2);
  }
}

/**
 * Zod schema for validating parsed intents.
 */
export const ParsedIntentSchema = z.object({
  type: z.enum([
    'add_entity',
    'remove_entity',
    'modify_entity',
    'add_step',
    'remove_step',
    'modify_step',
    'change_speed',
    'change_visual',
    'add_annotation',
    'change_domain_config',
    'explain',
    'clarify',
    'undo',
    'redo',
    'unknown',
  ]),
  confidence: z.number().min(0).max(1),
  params: z.object({
    targetEntity: z.string().optional(),
    targetStep: z.union([z.string(), z.number()]).optional(),
    newValue: z.unknown().optional(),
    property: z.string().optional(),
    entityType: z.string().optional(),
    position: z.object({
      x: z.number(),
      y: z.number(),
      z: z.number().optional(),
    }).optional(),
    visual: z.object({
      color: z.string().optional(),
      size: z.number().optional(),
      opacity: z.number().optional(),
      shape: z.string().optional(),
    }).optional(),
    duration: z.number().optional(),
    text: z.string().optional(),
    color: z.string().optional(),
  }),
  originalInput: z.string(),
  normalizedInput: z.string(),
});

export type ValidatedIntent = z.infer<typeof ParsedIntentSchema>;
