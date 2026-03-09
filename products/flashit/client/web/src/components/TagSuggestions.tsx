/**
 * Tag Suggestions Component - AI-Powered Tag Recommendations
 *
 * Displays AI-suggested tags with accept/reject functionality.
 * Integrates with the NLP service for intelligent tagging.
 *
 * Features:
 * - Display suggested tags with confidence scores
 * - Accept individual tags or all at once
 * - Reject individual tags with feedback
 * - Add custom tags
 * - Visual confidence indicators
 * - Loading and error states
 *
 * @doc.type component
 * @doc.purpose AI tag suggestion UI
 * @doc.layer product
 * @doc.pattern Component
 */

'use client';

import React, { useState, useCallback, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

interface SuggestedTag {
  /** Tag name */
  name: string;
  /** Confidence score (0-1) */
  confidence: number;
  /** Source of the suggestion */
  source: 'inferred' | 'suggested' | 'user';
}

interface ExtractedEntity {
  /** Entity type */
  type: 'person' | 'place' | 'organization' | 'topic' | 'event' | 'time' | 'concept';
  /** Entity value */
  value: string;
  /** Confidence score */
  confidence: number;
}

interface TagSuggestionsProps {
  /** Moment ID for tracking */
  momentId?: string;
  /** Current content to analyze */
  content: string;
  /** Already applied tags */
  appliedTags?: string[];
  /** Callback when tags are updated */
  onTagsChange: (tags: string[]) => void;
  /** Callback for entity selection */
  onEntitySelect?: (entity: ExtractedEntity) => void;
  /** Whether to auto-fetch suggestions */
  autoFetch?: boolean;
  /** API endpoint for suggestions */
  apiEndpoint?: string;
  /** Custom class name */
  className?: string;
  /** Show entities section */
  showEntities?: boolean;
  /** Compact mode */
  compact?: boolean;
}

interface AIAnalysisResult {
  tags: SuggestedTag[];
  entities: ExtractedEntity[];
  mood?: {
    sentiment: string;
    confidence: number;
  };
}

// ============================================================================
// Component
// ============================================================================

/**
 * Tag Suggestions Component
 *
 * GIVEN: Moment content for analysis
 * WHEN: Component renders or content changes
 * THEN: Displays AI-suggested tags with accept/reject controls
 *
 * @example
 * ```tsx
 * <TagSuggestions
 *   content="Had a meeting with Sarah about the product launch"
 *   appliedTags={['work']}
 *   onTagsChange={(tags) => setTags(tags)}
 * />
 * ```
 */
export function TagSuggestions({
  momentId,
  content,
  appliedTags = [],
  onTagsChange,
  onEntitySelect,
  autoFetch = true,
  apiEndpoint = '/api/moments/suggestions',
  className = '',
  showEntities = true,
  compact = false,
}: TagSuggestionsProps) {
  // State
  const [suggestions, setSuggestions] = useState<SuggestedTag[]>([]);
  const [entities, setEntities] = useState<ExtractedEntity[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejectedTags, setRejectedTags] = useState<Set<string>>(new Set());
  const [customTag, setCustomTag] = useState('');
  const [lastAnalyzedContent, setLastAnalyzedContent] = useState('');

  // --------------------------------------------------------------------------
  // API Calls
  // --------------------------------------------------------------------------

  const fetchSuggestions = useCallback(async () => {
    if (!content || content.trim().length < 10) {
      return;
    }

    // Don't re-analyze same content
    if (content === lastAnalyzedContent) {
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(apiEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          content,
          momentId,
          existingTags: appliedTags,
        }),
      });

      if (!response.ok) {
        throw new Error('Failed to fetch suggestions');
      }

      const result: AIAnalysisResult = await response.json();

      // Filter out already applied tags and rejected tags
      const filteredTags = result.tags.filter(
        (tag) => !appliedTags.includes(tag.name) && !rejectedTags.has(tag.name)
      );

      setSuggestions(filteredTags);
      setEntities(result.entities || []);
      setLastAnalyzedContent(content);
    } catch (err) {
      console.error('[TagSuggestions] Error fetching suggestions:', err);
      setError(err instanceof Error ? err.message : 'Failed to load suggestions');
    } finally {
      setIsLoading(false);
    }
  }, [content, momentId, appliedTags, rejectedTags, apiEndpoint, lastAnalyzedContent]);

  // Auto-fetch on content change
  useEffect(() => {
    if (autoFetch && content && content !== lastAnalyzedContent) {
      // Debounce
      const timer = setTimeout(fetchSuggestions, 500);
      return () => clearTimeout(timer);
    }
  }, [autoFetch, content, lastAnalyzedContent, fetchSuggestions]);

  // --------------------------------------------------------------------------
  // Event Handlers
  // --------------------------------------------------------------------------

  const handleAcceptTag = useCallback(
    (tag: SuggestedTag) => {
      const newTags = [...appliedTags, tag.name];
      onTagsChange(newTags);
      setSuggestions((prev) => prev.filter((t) => t.name !== tag.name));

      // Send feedback to API
      sendFeedback('tag', tag.name, 'accepted');
    },
    [appliedTags, onTagsChange]
  );

  const handleRejectTag = useCallback(
    (tag: SuggestedTag) => {
      setRejectedTags((prev) => new Set(prev).add(tag.name));
      setSuggestions((prev) => prev.filter((t) => t.name !== tag.name));

      // Send feedback to API
      sendFeedback('tag', tag.name, 'rejected');
    },
    []
  );

  const handleAcceptAll = useCallback(() => {
    const newTags = [...appliedTags, ...suggestions.map((t) => t.name)];
    onTagsChange(newTags);
    setSuggestions([]);
  }, [appliedTags, suggestions, onTagsChange]);

  const handleRemoveAppliedTag = useCallback(
    (tagName: string) => {
      onTagsChange(appliedTags.filter((t) => t !== tagName));
    },
    [appliedTags, onTagsChange]
  );

  const handleAddCustomTag = useCallback(() => {
    const normalized = customTag.trim().toLowerCase().replace(/\s+/g, '-');
    if (normalized && !appliedTags.includes(normalized)) {
      onTagsChange([...appliedTags, normalized]);
      setCustomTag('');
    }
  }, [customTag, appliedTags, onTagsChange]);

  const handleCustomTagKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleAddCustomTag();
      }
    },
    [handleAddCustomTag]
  );

  const handleEntityClick = useCallback(
    (entity: ExtractedEntity) => {
      if (onEntitySelect) {
        onEntitySelect(entity);
      }
    },
    [onEntitySelect]
  );

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  const sendFeedback = async (
    type: 'tag' | 'entity',
    suggestion: string,
    action: 'accepted' | 'rejected'
  ) => {
    try {
      await fetch('/api/ai/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type,
          suggestion,
          action,
          momentId,
        }),
      });
    } catch (err) {
      console.warn('[TagSuggestions] Failed to send feedback:', err);
    }
  };

  const getConfidenceColor = (confidence: number): string => {
    if (confidence >= 0.8) return 'bg-green-100 text-green-800 border-green-200';
    if (confidence >= 0.6) return 'bg-blue-100 text-blue-800 border-blue-200';
    return 'bg-gray-100 text-gray-700 border-gray-200';
  };

  const getEntityIcon = (type: ExtractedEntity['type']): string => {
    const icons: Record<ExtractedEntity['type'], string> = {
      person: '👤',
      place: '📍',
      organization: '🏢',
      topic: '💡',
      event: '📅',
      time: '⏰',
      concept: '🔮',
    };
    return icons[type] || '📎';
  };

  // --------------------------------------------------------------------------
  // Render
  // --------------------------------------------------------------------------

  if (compact) {
    return (
      <div className={`tag-suggestions-compact ${className}`}>
        {/* Compact Applied Tags */}
        <div className="flex flex-wrap gap-1 items-center">
          {appliedTags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center px-2 py-0.5 rounded-full text-xs
                         bg-indigo-100 text-indigo-800 border border-indigo-200"
            >
              {tag}
              <button
                onClick={() => handleRemoveAppliedTag(tag)}
                className="ml-1 text-indigo-500 hover:text-indigo-700"
                aria-label={`Remove ${tag}`}
              >
                ×
              </button>
            </span>
          ))}

          {/* Compact Suggestions */}
          {suggestions.slice(0, 3).map((suggestion) => (
            <button
              key={suggestion.name}
              onClick={() => handleAcceptTag(suggestion)}
              className="inline-flex items-center px-2 py-0.5 rounded-full text-xs
                         bg-gray-100 text-gray-600 border border-dashed border-gray-300
                         hover:bg-indigo-50 hover:text-indigo-700 hover:border-indigo-300
                         transition-colors"
            >
              + {suggestion.name}
            </button>
          ))}

          {isLoading && (
            <span className="text-xs text-gray-400 animate-pulse">Analyzing...</span>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className={`tag-suggestions ${className}`}>
      {/* Applied Tags Section */}
      <div className="mb-4">
        <h4 className="text-sm font-medium text-gray-700 mb-2">Applied Tags</h4>
        <div className="flex flex-wrap gap-2">
          {appliedTags.length === 0 && (
            <span className="text-sm text-gray-400">No tags applied yet</span>
          )}
          {appliedTags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center px-3 py-1 rounded-full text-sm
                         bg-indigo-100 text-indigo-800 border border-indigo-200"
            >
              #{tag}
              <button
                onClick={() => handleRemoveAppliedTag(tag)}
                className="ml-2 text-indigo-500 hover:text-indigo-700 font-bold"
                aria-label={`Remove ${tag}`}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      </div>

      {/* AI Suggestions Section */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2">
          <h4 className="text-sm font-medium text-gray-700 flex items-center gap-2">
            <span className="text-lg">✨</span>
            AI Suggestions
            {isLoading && (
              <span className="text-xs text-gray-400 animate-pulse ml-2">
                Analyzing...
              </span>
            )}
          </h4>
          {suggestions.length > 0 && (
            <button
              onClick={handleAcceptAll}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
            >
              Accept All
            </button>
          )}
        </div>

        {error && (
          <div className="text-sm text-red-500 mb-2 flex items-center gap-2">
            <span>⚠️</span>
            {error}
            <button
              onClick={fetchSuggestions}
              className="text-indigo-600 hover:text-indigo-800 underline"
            >
              Retry
            </button>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          {suggestions.length === 0 && !isLoading && !error && (
            <span className="text-sm text-gray-400">
              No suggestions available. Type more content to get AI suggestions.
            </span>
          )}
          {suggestions.map((suggestion) => (
            <div
              key={suggestion.name}
              className={`inline-flex items-center rounded-full border ${getConfidenceColor(
                suggestion.confidence
              )}`}
            >
              <button
                onClick={() => handleAcceptTag(suggestion)}
                className="px-3 py-1 text-sm hover:opacity-80 transition-opacity"
                title={`Confidence: ${Math.round(suggestion.confidence * 100)}%`}
              >
                #{suggestion.name}
              </button>
              <button
                onClick={() => handleRejectTag(suggestion)}
                className="px-2 py-1 border-l border-current/20 hover:bg-red-50 
                           hover:text-red-600 transition-colors rounded-r-full"
                aria-label={`Reject ${suggestion.name}`}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      </div>

      {/* Entities Section */}
      {showEntities && entities.length > 0 && (
        <div className="mb-4">
          <h4 className="text-sm font-medium text-gray-700 mb-2 flex items-center gap-2">
            <span className="text-lg">🔍</span>
            Detected Entities
          </h4>
          <div className="flex flex-wrap gap-2">
            {entities.map((entity, index) => (
              <button
                key={`${entity.type}-${entity.value}-${index}`}
                onClick={() => handleEntityClick(entity)}
                className="inline-flex items-center px-2 py-1 rounded-lg text-sm
                           bg-amber-50 text-amber-800 border border-amber-200
                           hover:bg-amber-100 transition-colors"
                title={`${entity.type}: ${entity.value} (${Math.round(entity.confidence * 100)}%)`}
              >
                <span className="mr-1">{getEntityIcon(entity.type)}</span>
                {entity.value}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Add Custom Tag */}
      <div className="flex gap-2">
        <input
          type="text"
          value={customTag}
          onChange={(e) => setCustomTag(e.target.value)}
          onKeyDown={handleCustomTagKeyDown}
          placeholder="Add custom tag..."
          className="flex-1 px-3 py-1.5 text-sm border border-gray-200 rounded-lg
                     focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
        />
        <button
          onClick={handleAddCustomTag}
          disabled={!customTag.trim()}
          className="px-4 py-1.5 text-sm font-medium text-white bg-indigo-600 rounded-lg
                     hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed
                     transition-colors"
        >
          Add
        </button>
        <button
          onClick={fetchSuggestions}
          disabled={isLoading}
          className="px-4 py-1.5 text-sm font-medium text-indigo-600 bg-indigo-50 rounded-lg
                     hover:bg-indigo-100 disabled:opacity-50 transition-colors"
          title="Refresh suggestions"
        >
          🔄
        </button>
      </div>
    </div>
  );
}

export default TagSuggestions;
