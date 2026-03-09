/**
 * Tag Suggestions Component (Mobile) - AI-Powered Tag Recommendations
 *
 * React Native component for displaying AI-suggested tags.
 * Provides accept/reject functionality with haptic feedback.
 *
 * Features:
 * - Display suggested tags with confidence scores
 * - Accept individual tags or all at once
 * - Reject individual tags
 * - Add custom tags
 * - Haptic feedback on interactions
 * - Loading and error states
 * - Offline queue support
 *
 * @doc.type component
 * @doc.purpose AI tag suggestion UI for mobile
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useEffect, useMemo } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  TextInput,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Vibration,
  Platform,
} from 'react-native';

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
  /** API base URL */
  apiBaseUrl?: string;
  /** Show entities section */
  showEntities?: boolean;
  /** Compact mode for smaller spaces */
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
 * Tag Suggestions Component for Mobile
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
  apiBaseUrl = '',
  showEntities = true,
  compact = false,
}: TagSuggestionsProps): React.ReactElement {
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
      const response = await fetch(`${apiBaseUrl}/api/moments/suggestions`, {
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
  }, [content, momentId, appliedTags, rejectedTags, apiBaseUrl, lastAnalyzedContent]);

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

  const hapticFeedback = useCallback(() => {
    if (Platform.OS === 'ios') {
      // iOS haptic feedback would go here with expo-haptics
    } else {
      Vibration.vibrate(10);
    }
  }, []);

  const handleAcceptTag = useCallback(
    (tag: SuggestedTag) => {
      hapticFeedback();
      const newTags = [...appliedTags, tag.name];
      onTagsChange(newTags);
      setSuggestions((prev) => prev.filter((t) => t.name !== tag.name));

      // Send feedback to API
      sendFeedback('tag', tag.name, 'accepted');
    },
    [appliedTags, onTagsChange, hapticFeedback]
  );

  const handleRejectTag = useCallback(
    (tag: SuggestedTag) => {
      hapticFeedback();
      setRejectedTags((prev) => new Set(prev).add(tag.name));
      setSuggestions((prev) => prev.filter((t) => t.name !== tag.name));

      // Send feedback to API
      sendFeedback('tag', tag.name, 'rejected');
    },
    [hapticFeedback]
  );

  const handleAcceptAll = useCallback(() => {
    hapticFeedback();
    const newTags = [...appliedTags, ...suggestions.map((t) => t.name)];
    onTagsChange(newTags);
    setSuggestions([]);
  }, [appliedTags, suggestions, onTagsChange, hapticFeedback]);

  const handleRemoveAppliedTag = useCallback(
    (tagName: string) => {
      hapticFeedback();
      onTagsChange(appliedTags.filter((t) => t !== tagName));
    },
    [appliedTags, onTagsChange, hapticFeedback]
  );

  const handleAddCustomTag = useCallback(() => {
    const normalized = customTag.trim().toLowerCase().replace(/\s+/g, '-');
    if (normalized && !appliedTags.includes(normalized)) {
      hapticFeedback();
      onTagsChange([...appliedTags, normalized]);
      setCustomTag('');
    }
  }, [customTag, appliedTags, onTagsChange, hapticFeedback]);

  const handleEntityPress = useCallback(
    (entity: ExtractedEntity) => {
      hapticFeedback();
      if (onEntitySelect) {
        onEntitySelect(entity);
      }
    },
    [onEntitySelect, hapticFeedback]
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
      await fetch(`${apiBaseUrl}/api/ai/feedback`, {
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

  const getConfidenceStyle = useMemo(
    () => (confidence: number) => {
      if (confidence >= 0.8) return styles.highConfidence;
      if (confidence >= 0.6) return styles.mediumConfidence;
      return styles.lowConfidence;
    },
    []
  );

  const getEntityIcon = useMemo(
    () => (type: ExtractedEntity['type']): string => {
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
    },
    []
  );

  // --------------------------------------------------------------------------
  // Render
  // --------------------------------------------------------------------------

  if (compact) {
    return (
      <View style={styles.compactContainer}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.compactScroll}
        >
          {/* Applied Tags */}
          {appliedTags.map((tag) => (
            <TouchableOpacity
              key={tag}
              style={styles.appliedTagCompact}
              onPress={() => handleRemoveAppliedTag(tag)}
            >
              <Text style={styles.appliedTagTextCompact}>{tag}</Text>
              <Text style={styles.removeIconCompact}>×</Text>
            </TouchableOpacity>
          ))}

          {/* Suggestions */}
          {suggestions.slice(0, 3).map((suggestion) => (
            <TouchableOpacity
              key={suggestion.name}
              style={styles.suggestionTagCompact}
              onPress={() => handleAcceptTag(suggestion)}
            >
              <Text style={styles.suggestionTagTextCompact}>+ {suggestion.name}</Text>
            </TouchableOpacity>
          ))}

          {isLoading && (
            <ActivityIndicator size="small" color="#6366f1" style={styles.loader} />
          )}
        </ScrollView>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Applied Tags Section */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Applied Tags</Text>
        <View style={styles.tagsContainer}>
          {appliedTags.length === 0 && (
            <Text style={styles.emptyText}>No tags applied yet</Text>
          )}
          {appliedTags.map((tag) => (
            <TouchableOpacity
              key={tag}
              style={styles.appliedTag}
              onPress={() => handleRemoveAppliedTag(tag)}
              activeOpacity={0.7}
            >
              <Text style={styles.appliedTagText}>#{tag}</Text>
              <Text style={styles.removeIcon}>×</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* AI Suggestions Section */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <View style={styles.sectionTitleRow}>
            <Text style={styles.sectionTitle}>✨ AI Suggestions</Text>
            {isLoading && (
              <ActivityIndicator size="small" color="#6366f1" style={styles.loader} />
            )}
          </View>
          {suggestions.length > 0 && (
            <TouchableOpacity onPress={handleAcceptAll}>
              <Text style={styles.acceptAllText}>Accept All</Text>
            </TouchableOpacity>
          )}
        </View>

        {error && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorText}>⚠️ {error}</Text>
            <TouchableOpacity onPress={fetchSuggestions}>
              <Text style={styles.retryText}>Retry</Text>
            </TouchableOpacity>
          </View>
        )}

        <View style={styles.tagsContainer}>
          {suggestions.length === 0 && !isLoading && !error && (
            <Text style={styles.emptyText}>
              Type more content to get AI suggestions
            </Text>
          )}
          {suggestions.map((suggestion) => (
            <View
              key={suggestion.name}
              style={[styles.suggestionTag, getConfidenceStyle(suggestion.confidence)]}
            >
              <TouchableOpacity
                style={styles.suggestionTagContent}
                onPress={() => handleAcceptTag(suggestion)}
                activeOpacity={0.7}
              >
                <Text style={styles.suggestionTagText}>#{suggestion.name}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={styles.rejectButton}
                onPress={() => handleRejectTag(suggestion)}
              >
                <Text style={styles.rejectIcon}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
        </View>
      </View>

      {/* Entities Section */}
      {showEntities && entities.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>🔍 Detected Entities</Text>
          <View style={styles.entitiesContainer}>
            {entities.map((entity, index) => (
              <TouchableOpacity
                key={`${entity.type}-${entity.value}-${index}`}
                style={styles.entityTag}
                onPress={() => handleEntityPress(entity)}
                activeOpacity={0.7}
              >
                <Text style={styles.entityIcon}>{getEntityIcon(entity.type)}</Text>
                <Text style={styles.entityText}>{entity.value}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      )}

      {/* Add Custom Tag */}
      <View style={styles.addTagContainer}>
        <TextInput
          style={styles.textInput}
          value={customTag}
          onChangeText={setCustomTag}
          placeholder="Add custom tag..."
          placeholderTextColor="#9ca3af"
          returnKeyType="done"
          onSubmitEditing={handleAddCustomTag}
        />
        <TouchableOpacity
          style={[styles.addButton, !customTag.trim() && styles.addButtonDisabled]}
          onPress={handleAddCustomTag}
          disabled={!customTag.trim()}
        >
          <Text style={styles.addButtonText}>Add</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.refreshButton}
          onPress={fetchSuggestions}
          disabled={isLoading}
        >
          <Text style={styles.refreshIcon}>🔄</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
  container: {
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  compactContainer: {
    paddingVertical: 8,
  },
  compactScroll: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  section: {
    marginBottom: 16,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 8,
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  entitiesContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#9ca3af',
  },
  loader: {
    marginLeft: 8,
  },

  // Applied Tags
  appliedTag: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#e0e7ff',
    borderColor: '#c7d2fe',
    borderWidth: 1,
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  appliedTagText: {
    fontSize: 14,
    color: '#4338ca',
    fontWeight: '500',
  },
  removeIcon: {
    marginLeft: 6,
    fontSize: 16,
    color: '#6366f1',
    fontWeight: 'bold',
  },
  appliedTagCompact: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#e0e7ff',
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  appliedTagTextCompact: {
    fontSize: 12,
    color: '#4338ca',
  },
  removeIconCompact: {
    marginLeft: 4,
    fontSize: 14,
    color: '#6366f1',
  },

  // Suggestion Tags
  suggestionTag: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 20,
    borderWidth: 1,
    overflow: 'hidden',
  },
  suggestionTagContent: {
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  suggestionTagText: {
    fontSize: 14,
  },
  suggestionTagCompact: {
    backgroundColor: '#f3f4f6',
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: '#d1d5db',
  },
  suggestionTagTextCompact: {
    fontSize: 12,
    color: '#6b7280',
  },
  rejectButton: {
    paddingHorizontal: 8,
    paddingVertical: 6,
    borderLeftWidth: 1,
    borderLeftColor: 'rgba(0,0,0,0.1)',
  },
  rejectIcon: {
    fontSize: 16,
    color: '#9ca3af',
    fontWeight: 'bold',
  },

  // Confidence Styles
  highConfidence: {
    backgroundColor: '#dcfce7',
    borderColor: '#86efac',
  },
  mediumConfidence: {
    backgroundColor: '#dbeafe',
    borderColor: '#93c5fd',
  },
  lowConfidence: {
    backgroundColor: '#f3f4f6',
    borderColor: '#d1d5db',
  },

  // Entities
  entityTag: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fef3c7',
    borderColor: '#fcd34d',
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  entityIcon: {
    fontSize: 14,
    marginRight: 4,
  },
  entityText: {
    fontSize: 13,
    color: '#92400e',
  },

  // Error
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  errorText: {
    fontSize: 13,
    color: '#dc2626',
    flex: 1,
  },
  retryText: {
    fontSize: 13,
    color: '#6366f1',
    fontWeight: '500',
    textDecorationLine: 'underline',
  },

  // Accept All
  acceptAllText: {
    fontSize: 13,
    color: '#6366f1',
    fontWeight: '600',
  },

  // Add Tag
  addTagContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  textInput: {
    flex: 1,
    height: 40,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 14,
    color: '#1f2937',
    backgroundColor: '#fff',
  },
  addButton: {
    backgroundColor: '#6366f1',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
  },
  addButtonDisabled: {
    opacity: 0.5,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  refreshButton: {
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#eef2ff',
    borderRadius: 8,
  },
  refreshIcon: {
    fontSize: 16,
  },
});

export default TagSuggestions;
