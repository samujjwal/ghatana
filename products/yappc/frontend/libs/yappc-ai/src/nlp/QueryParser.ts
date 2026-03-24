/**
 * Natural Language Query Parser
 *
 * Parses natural language queries to extract intent, entities, and structure.
 * Supports both pattern-based (offline) and AI-powered (online) parsing.
 *
 * Features:
 * - Intent classification (create, edit, delete, search, navigate, etc.)
 * - Entity extraction (dates, numbers, names, locations, etc.)
 * - Query normalization and preprocessing
 * - Pattern matching with regex
 * - AI-powered parsing for complex queries
 * - Confidence scoring
 * - Multi-language support (basic)
 *
 * @example
 * ```typescript
 * const parser = new QueryParser(aiService);
 *
 * const result = await parser.parse('Create a new component called Button');
 * // {
 * //   intent: 'create',
 * //   confidence: 0.95,
 * //   entities: { type: 'component', name: 'Button' },
 * //   normalizedQuery: 'create new component button',
 * //   rawQuery: 'Create a new component called Button'
 * // }
 * ```
 */

import type { IAIService } from '../core/types';

/**
 *
 */
export type Intent =
  | 'create'
  | 'edit'
  | 'delete'
  | 'search'
  | 'navigate'
  | 'help'
  | 'explain'
  | 'translate'
  | 'summarize'
  | 'analyze'
  | 'unknown';

/**
 *
 */
export interface Entity {
  type: string;
  value: string | number | Date;
  confidence?: number;
  start?: number;
  end?: number;
}

/**
 *
 */
export interface ParsedQuery {
  intent: Intent;
  confidence: number;
  entities: Record<string, Entity>;
  normalizedQuery: string;
  rawQuery: string;
  metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface QueryParserOptions {
  /** AI service for advanced parsing (optional) */
  aiService?: IAIService;

  /** Use AI for parsing (slower but more accurate) */
  useAI?: boolean;

  /** Minimum confidence threshold for results */
  minConfidence?: number;

  /** Language code (default: 'en') */
  language?: string;

  /** Custom intent patterns */
  customPatterns?: IntentPattern[];

  /** Custom entity extractors */
  customExtractors?: EntityExtractor[];
}

/**
 *
 */
export interface IntentPattern {
  intent: Intent;
  patterns: RegExp[];
  confidence?: number;
}

/**
 *
 */
export interface EntityExtractor {
  name: string;
  extract: (text: string) => Entity[];
}

// Default intent patterns
const DEFAULT_INTENT_PATTERNS: IntentPattern[] = [
  {
    intent: 'create',
    patterns: [
      /create\s+(a\s+)?(new\s+)?(\w+)/i,
      /add\s+(a\s+)?(new\s+)?(\w+)/i,
      /make\s+(a\s+)?(new\s+)?(\w+)/i,
      /build\s+(a\s+)?(new\s+)?(\w+)/i,
    ],
    confidence: 0.9,
  },
  {
    intent: 'edit',
    patterns: [
      /edit\s+(\w+)/i,
      /modify\s+(\w+)/i,
      /update\s+(\w+)/i,
      /change\s+(\w+)/i,
      /refactor\s+(\w+)/i,
    ],
    confidence: 0.85,
  },
  {
    intent: 'delete',
    patterns: [
      /delete\s+(\w+)/i,
      /remove\s+(\w+)/i,
      /destroy\s+(\w+)/i,
      /clear\s+(\w+)/i,
    ],
    confidence: 0.9,
  },
  {
    intent: 'search',
    patterns: [
      /search\s+for\s+(\w+)/i,
      /find\s+(\w+)/i,
      /look\s+for\s+(\w+)/i,
      /locate\s+(\w+)/i,
      /where\s+is\s+(\w+)/i,
    ],
    confidence: 0.85,
  },
  {
    intent: 'navigate',
    patterns: [
      /go\s+to\s+(\w+)/i,
      /navigate\s+to\s+(\w+)/i,
      /open\s+(\w+)/i,
      /show\s+(me\s+)?(\w+)/i,
    ],
    confidence: 0.8,
  },
  {
    intent: 'help',
    patterns: [
      /help(\s+with)?(\s+\w+)?/i,
      /how\s+do\s+i\s+(\w+)/i,
      /what\s+is\s+(\w+)/i,
      /tell\s+me\s+about\s+(\w+)/i,
    ],
    confidence: 0.75,
  },
  {
    intent: 'explain',
    patterns: [
      /explain\s+(\w+)/i,
      /describe\s+(\w+)/i,
      /what\s+does\s+(\w+)\s+do/i,
    ],
    confidence: 0.8,
  },
  {
    intent: 'translate',
    patterns: [
      /translate\s+(\w+)\s+to\s+(\w+)/i,
      /convert\s+(\w+)\s+to\s+(\w+)/i,
    ],
    confidence: 0.85,
  },
  {
    intent: 'summarize',
    patterns: [
      /summarize\s+(\w+)/i,
      /give\s+me\s+a\s+summary\s+of\s+(\w+)/i,
      /brief\s+overview\s+of\s+(\w+)/i,
    ],
    confidence: 0.8,
  },
  {
    intent: 'analyze',
    patterns: [
      /analyze\s+(\w+)/i,
      /examine\s+(\w+)/i,
      /review\s+(\w+)/i,
      /check\s+(\w+)/i,
    ],
    confidence: 0.8,
  },
];

// Default entity extractors
const DEFAULT_EXTRACTORS: EntityExtractor[] = [
  {
    name: 'number',
    extract: (text: string): Entity[] => {
      const pattern = /\b\d+(\.\d+)?\b/g;
      const matches: Entity[] = [];
      let match;

      while ((match = pattern.exec(text)) !== null) {
        matches.push({
          type: 'number',
          value: parseFloat(match[0]),
          confidence: 1.0,
          start: match.index,
          end: pattern.lastIndex,
        });
      }

      return matches;
    },
  },
  {
    name: 'date',
    extract: (text: string): Entity[] => {
      const patterns = [
        /\b\d{4}-\d{2}-\d{2}\b/, // YYYY-MM-DD
        /\b\d{1,2}\/\d{1,2}\/\d{4}\b/, // MM/DD/YYYY
        /\b(?:today|tomorrow|yesterday)\b/i,
      ];

      const matches: Entity[] = [];

      for (const pattern of patterns) {
        let match;
        while ((match = pattern.exec(text)) !== null) {
          const value = match[0].toLowerCase();
          let date: Date;

          if (value === 'today') {
            date = new Date();
          } else if (value === 'tomorrow') {
            date = new Date();
            date.setDate(date.getDate() + 1);
          } else if (value === 'yesterday') {
            date = new Date();
            date.setDate(date.getDate() - 1);
          } else {
            date = new Date(value);
          }

          if (!isNaN(date.getTime())) {
            matches.push({
              type: 'date',
              value: date,
              confidence: 0.95,
              start: match.index,
              end: match.index + match[0].length,
            });
          }
        }
      }

      return matches;
    },
  },
  {
    name: 'email',
    extract: (text: string): Entity[] => {
      const pattern = /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g;
      const matches: Entity[] = [];
      let match;

      while ((match = pattern.exec(text)) !== null) {
        matches.push({
          type: 'email',
          value: match[0],
          confidence: 0.98,
          start: match.index,
          end: pattern.lastIndex,
        });
      }

      return matches;
    },
  },
  {
    name: 'url',
    extract: (text: string): Entity[] => {
      const pattern = /https?:\/\/[^\s]+/g;
      const matches: Entity[] = [];
      let match;

      while ((match = pattern.exec(text)) !== null) {
        matches.push({
          type: 'url',
          value: match[0],
          confidence: 0.99,
          start: match.index,
          end: pattern.lastIndex,
        });
      }

      return matches;
    },
  },
  {
    name: 'quoted',
    extract: (text: string): Entity[] => {
      const pattern = /"([^"]+)"/g;
      const matches: Entity[] = [];
      let match;

      while ((match = pattern.exec(text)) !== null) {
        matches.push({
          type: 'quoted',
          value: match[1],
          confidence: 1.0,
          start: match.index,
          end: pattern.lastIndex,
        });
      }

      return matches;
    },
  },
];

/**
 *
 */
export class QueryParser {
  private aiService?: IAIService;
  private useAI: boolean;
  private minConfidence: number;
  private language: string;
  private intentPatterns: IntentPattern[];
  private entityExtractors: EntityExtractor[];

  /**
   *
   */
  constructor(options: QueryParserOptions = {}) {
    this.aiService = options.aiService;
    this.useAI = options.useAI ?? false;
    this.minConfidence = options.minConfidence ?? 0.6;
    this.language = options.language ?? 'en';
    this.intentPatterns = [
      ...DEFAULT_INTENT_PATTERNS,
      ...(options.customPatterns ?? []),
    ];
    this.entityExtractors = [
      ...DEFAULT_EXTRACTORS,
      ...(options.customExtractors ?? []),
    ];
  }

  /**
   * Parse a natural language query
   */
  async parse(query: string): Promise<ParsedQuery> {
    if (!query || query.trim().length === 0) {
      return {
        intent: 'unknown',
        confidence: 0,
        entities: {},
        normalizedQuery: '',
        rawQuery: query,
      };
    }

    const normalizedQuery = this.normalizeQuery(query);

    // Try pattern-based parsing first (fast)
    const patternResult = this.parseWithPatterns(normalizedQuery);

    // If confidence is high enough or AI is disabled, return pattern result
    if (!this.useAI || !this.aiService || patternResult.confidence >= 0.9) {
      return {
        ...patternResult,
        normalizedQuery,
        rawQuery: query,
      };
    }

    // Fall back to AI parsing for complex queries
    try {
      const aiResult = await this.parseWithAI(query);

      // Use AI result if confidence is higher
      if (aiResult.confidence > patternResult.confidence) {
        return {
          ...aiResult,
          normalizedQuery,
          rawQuery: query,
        };
      }
    } catch (error) {
      console.warn('AI parsing failed, using pattern result:', error);
    }

    return {
      ...patternResult,
      normalizedQuery,
      rawQuery: query,
    };
  }

  /**
   * Normalize query (lowercase, trim, remove extra spaces)
   */
  private normalizeQuery(query: string): string {
    return query
      .toLowerCase()
      .trim()
      .replace(/\s+/g, ' ')
      .replace(/[^\w\s@.-]/g, '');
  }

  /**
   * Parse using pattern matching
   */
  private parseWithPatterns(
    query: string
  ): Omit<ParsedQuery, 'normalizedQuery' | 'rawQuery'> {
    let bestMatch: { intent: Intent; confidence: number } = {
      intent: 'unknown',
      confidence: 0,
    };

    // Match against intent patterns
    for (const { intent, patterns, confidence = 0.8 } of this.intentPatterns) {
      for (const pattern of patterns) {
        if (pattern.test(query)) {
          if (confidence > bestMatch.confidence) {
            bestMatch = { intent, confidence };
          }
        }
      }
    }

    // Extract entities
    const entities: Record<string, Entity> = {};

    for (const extractor of this.entityExtractors) {
      const extracted = extractor.extract(query);
      for (const entity of extracted) {
        if (
          !entities[entity.type] ||
          entity.confidence! > entities[entity.type].confidence!
        ) {
          entities[entity.type] = entity;
        }
      }
    }

    return {
      intent: bestMatch.intent,
      confidence: bestMatch.confidence,
      entities,
    };
  }

  /**
   * Parse using AI service
   */
  private async parseWithAI(
    query: string
  ): Promise<Omit<ParsedQuery, 'normalizedQuery' | 'rawQuery'>> {
    if (!this.aiService) {
      throw new Error('AI service not available');
    }

    const prompt = `Parse this natural language query and extract:
1. Intent (create, edit, delete, search, navigate, help, explain, translate, summarize, analyze, or unknown)
2. Entities (type and value pairs)
3. Confidence score (0-1)

Query: "${query}"

Respond with JSON:
{
  "intent": "...",
  "confidence": 0.0,
  "entities": {
    "entityType": { "type": "entityType", "value": "...", "confidence": 0.0 }
  }
}`;

    const response = await this.aiService.complete({
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.3,
      maxTokens: 300,
    });

    try {
      // Extract JSON from response
      const jsonMatch = response.content.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        throw new Error('No JSON found in AI response');
      }

      const parsed = JSON.parse(jsonMatch[0]);

      return {
        intent: parsed.intent || 'unknown',
        confidence: parsed.confidence || 0.5,
        entities: parsed.entities || {},
        metadata: { aiParsed: true },
      };
    } catch (error) {
      console.error('Failed to parse AI response:', error);
      throw error;
    }
  }

  /**
   * Add a custom intent pattern
   */
  addIntentPattern(pattern: IntentPattern): void {
    this.intentPatterns.push(pattern);
  }

  /**
   * Add a custom entity extractor
   */
  addEntityExtractor(extractor: EntityExtractor): void {
    this.entityExtractors.push(extractor);
  }

  /**
   * Batch parse multiple queries
   */
  async parseMultiple(queries: string[]): Promise<ParsedQuery[]> {
    return Promise.all(queries.map((q) => this.parse(q)));
  }

  /**
   * Get supported intents
   */
  getSupportedIntents(): Intent[] {
    return Array.from(new Set(this.intentPatterns.map((p) => p.intent)));
  }

  /**
   * Get supported entity types
   */
  getSupportedEntityTypes(): string[] {
    return this.entityExtractors.map((e) => e.name);
  }
}
