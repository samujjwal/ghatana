/** Allowed suggestion kinds the AI can produce */
export type SuggestionType = 'completion' | 'edit' | 'explain' | 'improve';

/** A parsed suggestion item */
export interface Suggestion {
  /** Unique identifier */
  id: string;
  /** Type of suggestion */
  type: SuggestionType;
  /** Main suggestion text */
  text: string;
  /** Optional short description */
  description?: string;
  /** Optional confidence score (0-1) */
  confidence?: number;
  /** Any extra metadata from the AI */
  metadata?: Record<string, unknown>;
}

/** Props for the SmartSuggestions component */
/** Props for the SmartSuggestions component
 *
 * Note: aiService is typed as unknown here to avoid cross-package type imports
 * which can introduce circular references in the monorepo's TypeScript
 * project references setup. The concrete `IAIService` type is imported in
 * the implementation file where needed.
 */
export interface SmartSuggestionsProps {
  /** AI service instance (opaque) */
  aiService: unknown;
  context: string;
  selection?: string;
  onSelect: (suggestion: Suggestion) => void;
  onDismiss?: () => void;
  suggestionTypes?: SuggestionType[];
  maxSuggestionsPerType?: number;
  minConfidence?: number;
  completionOptions?: Record<string, unknown>;
  showConfidence?: boolean;
  autoGenerate?: boolean;
  className?: string;
  position?: 'above' | 'below' | 'left' | 'right';
}
