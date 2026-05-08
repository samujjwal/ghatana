/**
 * SmartSuggestions Component
 *
 * Context-aware suggestion component that displays multiple AI-powered suggestions
 * with keyboard navigation and click-to-insert functionality.
 *
 * Features:
 * - Multiple suggestion types (completion, edit, explain, improve)
 * - Keyboard navigation (arrow keys, Enter to select)
 * - Click to insert
 * - Loading states
 * - Error handling
 * - Customizable suggestion rendering
 * - Progressive disclosure (P3-2): Show only highest-priority suggestion initially
 *
 * @example
 * ```tsx
 * <SmartSuggestions
 *   aiService={provider}
 *   context="User is writing code"
 *   onSelect={(suggestion) => insertText(suggestion.text)}
 *   suggestionTypes={['completion', 'improve']}
 *   progressiveDisclosure={true}
 * />
 * ```
 */

import {
  Sparkles as AutoAwesomeIcon,
  RefreshCw as RefreshIcon,
  X as CloseIcon,
  ChevronDown as ExpandMoreIcon,
  ChevronUp as ExpandLessIcon,
} from 'lucide-react';
import React, { useState, useEffect, useRef, useMemo } from 'react';

import {
  Box,
  Typography,
  Spinner as CircularProgress,
  Surface as Paper,
  IconButton,
  Tooltip,
} from '@ghatana/design-system';

// fetchAllSuggestions moved into useSuggestions
import type {
  Suggestion,
  SuggestionType,
  SmartSuggestionsProps,
} from './SmartSuggestions/types';
import SuggestionList from './SuggestionList';
import { useSuggestions } from './useSuggestions';

export type { SmartSuggestionsProps } from './SmartSuggestions/types';

/**
 * Hook: keyboard navigation for suggestions
 */
function useKeyboardNavigation(
  suggestions: Suggestion[],
  onSelect: (s: Suggestion) => void,
  onDismiss: (() => void) | undefined,
  selectedIndexRef: React.MutableRefObject<number>,
  setSelectedIndex: (n: number) => void
) {
  useEffect(() => {
    if (!suggestions || suggestions.length === 0) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      switch (event.key) {
        case 'ArrowDown': {
          event.preventDefault();
          const next =
            ((selectedIndexRef.current ?? 0) + 1) % suggestions.length;
          selectedIndexRef.current = next;
          setSelectedIndex(next);
          break;
        }
        case 'ArrowUp': {
          event.preventDefault();
          const next =
            ((selectedIndexRef.current ?? 0) - 1 + suggestions.length) %
            suggestions.length;
          selectedIndexRef.current = next;
          setSelectedIndex(next);
          break;
        }
        case 'Enter': {
          event.preventDefault();
          const current = selectedIndexRef.current ?? 0;
          if (current >= 0 && current < suggestions.length) {
            const item = suggestions.find((_, i) => i === current);
            if (item) onSelect(item);
          }
          break;
        }
        case 'Escape':
          event.preventDefault();
          onDismiss?.();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [suggestions, onSelect, onDismiss, selectedIndexRef, setSelectedIndex]);
}

// types are imported from ./SmartSuggestions/types

// icons/labels/prompts moved to utils; we still render MUI icons inline for visual fidelity

const Header: React.FC<{
  isLoading: boolean;
  count: number;
  onRefresh: () => void;
  onDismiss?: () => void;
}> = ({ isLoading, count, onRefresh, onDismiss }) => (
  <Box className="p-4 flex items-center justify-between border-gray-200 dark:border-gray-700 border-b">
    <Typography as="h6" className="flex items-center gap-2">
      <AutoAwesomeIcon />
      Smart Suggestions
    </Typography>
    <Box>
      <Tooltip title="Refresh suggestions">
        <span>
          <IconButton
            aria-label="Refresh suggestions"
            size="sm"
            onClick={onRefresh}
            disabled={isLoading || count === 0}
          >
            <RefreshIcon />
          </IconButton>
        </span>
      </Tooltip>
      {onDismiss && (
        <Tooltip title="Dismiss (Esc)">
          <span>
            <IconButton
              aria-label="Dismiss suggestions"
              size="sm"
              onClick={onDismiss}
            >
              <CloseIcon />
            </IconButton>
          </span>
        </Tooltip>
      )}
    </Box>
  </Box>
);

const Footer: React.FC = () => (
  <Box className="p-3 border-gray-200 dark:border-gray-700 bg-gray-100 dark:bg-gray-800 border-t">
    <Typography
      as="span"
      className="text-xs text-gray-500"
      color="text.secondary"
    >
      Use ↑↓ to navigate, Enter to select, Esc to dismiss
    </Typography>
  </Box>
);

export const SmartSuggestions: React.FC<SmartSuggestionsProps> = ({
  aiService,
  context,
  selection = '',
  onSelect,
  onDismiss,
  suggestionTypes,
  maxSuggestionsPerType = 3,
  minConfidence = 0.5,
  completionOptions,
  showConfidence = true,
  autoGenerate = true,
  className,
  progressiveDisclosure = false,
  disclosureMode = 'single',
}) => {
  // Stabilize prop defaults that are arrays/objects so they don't create
  // new references on every render. This prevents useCallback/useEffect
  // from retriggering repeatedly in tests.
  const stableSuggestionTypes = useMemo<SuggestionType[]>(
    () => suggestionTypes ?? ['completion', 'improve'],
    [
      // stringify is cheap here and keeps reactivity sensible
      JSON.stringify(suggestionTypes),
    ]
  );

  const stableCompletionOptions = useMemo(
    () => completionOptions ?? {},
    [JSON.stringify(completionOptions)]
  );

  // stableClassName intentionally omitted because className is only forwarded into the Paper
  // component directly below — keep this comment for future extension.
  const [selectedIndex, setSelectedIndex] = useState(0);
  const selectedIndexRef = useRef<number>(0);

  const { suggestions, isLoading, error, refresh } = useSuggestions({
    aiService: aiService,
    types: stableSuggestionTypes,
    context,
    selection,
    maxPerType: maxSuggestionsPerType,
    minConfidence,
    completionOptions: stableCompletionOptions,
    autoGenerate,
  });

  const generateSuggestions = refresh;

  // local copy used for safe indexed access in handlers
  // no-op placeholder removed; keep suggestions accessed via local copies when needed

  // Helper: safely read groupedSuggestions for a type
  // removed unused helper to satisfy lint

  // mountedRef is provided by useMountedRef

  // P3-2: Progressive disclosure state
  const [showAll, setShowAll] = useState(progressiveDisclosure ? disclosureMode === 'all' : true);

  // P3-2: Calculate priority for suggestions (impact + confidence - effort heuristic)
  const prioritizedSuggestions = useMemo(() => {
    return suggestions.map(s => ({
      ...s,
      priority: s.priority ?? calculatePriority(s)
    })).sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));
  }, [suggestions]);

  // P3-2: Filter suggestions based on disclosure mode
  const displayedSuggestions = useMemo(() => {
    if (!progressiveDisclosure || showAll) {
      return prioritizedSuggestions;
    }
    return prioritizedSuggestions.slice(0, 1);
  }, [progressiveDisclosure, showAll, prioritizedSuggestions]);

  // P3-2: Priority calculation heuristic
  function calculatePriority(suggestion: Suggestion): number {
    let priority = 0;
    
    // Higher confidence = higher priority
    if (suggestion.confidence) {
      priority += suggestion.confidence * 50;
    }
    
    // Certain types get priority boost
    if (suggestion.type === 'completion') priority += 20;
    if (suggestion.type === 'improve') priority += 15;
    
    // Length-based heuristic (shorter suggestions often more actionable)
    if (suggestion.text.length < 50) priority += 10;
    else if (suggestion.text.length < 100) priority += 5;
    
    return Math.min(100, Math.max(0, priority));
  }

  // extract keyboard handling into hook to reduce component size
  useKeyboardNavigation(
    displayedSuggestions,
    onSelect,
    onDismiss,
    selectedIndexRef,
    setSelectedIndex
  );

  const handleSuggestionClick = (suggestion: Suggestion, index: number) => {
    setSelectedIndex(index);
    selectedIndexRef.current = index;
    onSelect(suggestion);
  };

  const handleRefresh = () => {
    generateSuggestions();
  };

  // Header extracted earlier but rendering inline below; removed unused Header symbol

  // grouping moved into SuggestionList; keep SmartSuggestions focused on state/handlers

  return (
    <Paper
      elevation={8}
      className={`w-[400px] max-h-[600px] overflow-auto ${className || ''}`}
    >
      <Header
        isLoading={isLoading}
        count={suggestions.length}
        onRefresh={handleRefresh}
        onDismiss={onDismiss}
      />

      {isLoading && (
        <Box className="p-8 flex justify-center items-center">
          <CircularProgress />
        </Box>
      )}

      {error && (
        <Box className="p-4">
          <Typography color="danger" as="p" className="text-sm">
            {error}
          </Typography>
          <Box className="mt-2">
            <Tooltip title="Refresh suggestions">
              <span>
                <IconButton
                  aria-label="Refresh suggestions"
                  size="sm"
                  onClick={handleRefresh}
                  disabled={isLoading || suggestions.length === 0}
                >
                  <RefreshIcon />
                </IconButton>
              </span>
            </Tooltip>
            {onDismiss && (
              <Tooltip title="Dismiss (Esc)">
                <span>
                  <IconButton
                    aria-label="Dismiss suggestions"
                    size="sm"
                    onClick={onDismiss}
                  >
                    <CloseIcon />
                  </IconButton>
                </span>
              </Tooltip>
            )}
          </Box>
        </Box>
      )}

      {!isLoading && !error && suggestions.length === 0 && (
        <Box className="p-4">
          <Typography>No suggestions available</Typography>
        </Box>
      )}

      {suggestions.length > 0 && (
        <>
          <SuggestionList
            suggestions={displayedSuggestions}
            suggestionTypes={stableSuggestionTypes}
            selectedIndex={selectedIndex}
            onClick={handleSuggestionClick}
            showConfidence={showConfidence}
          />
          
          {/* P3-2: Show more/less button for progressive disclosure */}
          {progressiveDisclosure && suggestions.length > 1 && (
            <Box className="p-3 border-gray-200 dark:border-gray-700 border-t">
              <button
                onClick={() => setShowAll(!showAll)}
                className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 transition-colors"
              >
                {showAll ? (
                  <>
                    <ExpandLessIcon size={16} />
                    Show less
                  </>
                ) : (
                  <>
                    <ExpandMoreIcon size={16} />
                    Show {suggestions.length - 1} more suggestions
                  </>
                )}
              </button>
            </Box>
          )}
        </>
      )}

      <Footer />
    </Paper>
  );
};
