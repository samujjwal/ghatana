/**
 * SuggestionPanel Component
 *
 * Displays AI-generated lifecycle suggestions for a YAPPC project phase.
 * Fetches suggestions from the AISuggestionService via the AI API, then
 * allows the user to accept or dismiss each suggestion.
 *
 * @doc.type component
 * @doc.purpose AI suggestion display and interaction for YAPPC lifecycle phases
 * @doc.layer product
 * @doc.pattern Component
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Lightbulb,
  Check,
  X,
  AlertTriangle,
  Code2,
  TestTube2,
  ArrowRight,
  FileText,
} from 'lucide-react';
import React, { useCallback } from 'react';

import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
  IconButton,
  Tooltip,
  LinearProgress,
} from '@ghatana/design-system';

// ── Types ────────────────────────────────────────────────────────────────────

/** Backend suggestion type labels */
export type SuggestionType =
  | 'REQUIREMENT'
  | 'DESIGN'
  | 'TEST'
  | 'RISK'
  | 'ACTION';

/** A single AI suggestion returned by AISuggestionService */
export interface Suggestion {
  id: string;
  projectId: string;
  phase: string;
  type: SuggestionType;
  text: string;
  generatedAt: string;
}

/** Props for SuggestionPanel */
export interface SuggestionPanelProps {
  /** Project to generate suggestions for */
  projectId: string;
  /** Current lifecycle phase (e.g. "SHAPE", "VALIDATE") */
  phase: string;
  /** Called when the user clicks Accept on a suggestion */
  onAccept?: (suggestion: Suggestion) => void;
  /** Called when the user clicks Dismiss on a suggestion */
  onDismiss?: (suggestionId: string) => void;
  /** Additional context forwarded to the AI model */
  context?: Record<string, unknown>;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function getSuggestionIcon(type: SuggestionType): React.ReactNode {
  switch (type) {
    case 'REQUIREMENT':
      return <FileText size={16} />;
    case 'DESIGN':
      return <Code2 size={16} />;
    case 'TEST':
      return <TestTube2 size={16} />;
    case 'RISK':
      return <AlertTriangle size={16} />;
    case 'ACTION':
      return <ArrowRight size={16} />;
    default:
      return <Lightbulb size={16} />;
  }
}

function getSuggestionLabel(type: SuggestionType): string {
  switch (type) {
    case 'REQUIREMENT':
      return 'Requirement';
    case 'DESIGN':
      return 'Design';
    case 'TEST':
      return 'Test';
    case 'RISK':
      return 'Risk';
    case 'ACTION':
      return 'Action';
    default:
      return 'Suggestion';
  }
}

function getSuggestionColor(
  type: SuggestionType
): 'default' | 'warning' | 'error' | 'success' | 'info' {
  switch (type) {
    case 'RISK':
      return 'warning';
    case 'TEST':
      return 'success';
    case 'ACTION':
      return 'info';
    default:
      return 'default';
  }
}

// ── API ──────────────────────────────────────────────────────────────────────

async function fetchSuggestions(
  projectId: string,
  phase: string,
  context: Record<string, unknown>
): Promise<Suggestion[]> {
  const response = await fetch(
    `/api/v1/projects/${encodeURIComponent(projectId)}/suggestions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phase, context }),
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch suggestions: ${response.statusText}`);
  }

  return response.json() as Promise<Suggestion[]>;
}

// ── Sub-component ─────────────────────────────────────────────────────────────

interface SuggestionItemProps {
  suggestion: Suggestion;
  onAccept?: (suggestion: Suggestion) => void;
  onDismiss?: (suggestionId: string) => void;
}

const SuggestionItem: React.FC<SuggestionItemProps> = ({
  suggestion,
  onAccept,
  onDismiss,
}) => (
  <Box className="flex items-start gap-3 p-3 rounded-lg hover:bg-black/5 transition-colors">
    <Box className="mt-0.5 text-gray-500 shrink-0">
      {getSuggestionIcon(suggestion.type)}
    </Box>

    <Box className="flex-1 min-w-0">
      <Box className="flex items-center gap-2 mb-0.5">
        <Chip
          size="sm"
          label={getSuggestionLabel(suggestion.type)}
          color={getSuggestionColor(suggestion.type)}
          variant="outlined"
        />
      </Box>
      <Typography className="text-sm text-gray-800 leading-snug">
        {suggestion.text}
      </Typography>
    </Box>

    <Box className="flex items-center gap-1 shrink-0">
      {onAccept && (
        <Tooltip title="Accept suggestion">
          <IconButton
            size="sm"
            tone="primary"
            aria-label="Accept suggestion"
            onClick={() => onAccept(suggestion)}
          >
            <Check size={14} />
          </IconButton>
        </Tooltip>
      )}
      {onDismiss && (
        <Tooltip title="Dismiss">
          <IconButton
            size="sm"
            aria-label="Dismiss suggestion"
            onClick={() => onDismiss(suggestion.id)}
          >
            <X size={14} />
          </IconButton>
        </Tooltip>
      )}
    </Box>
  </Box>
);

// ── Main Component ─────────────────────────────────────────────────────────────

/**
 * SuggestionPanel displays AI-generated suggestions for a lifecycle phase.
 *
 * Suggestions are fetched server-side via TanStack Query and cached per
 * (projectId, phase) pair. Accepts an optional `context` map to pass
 * additional metadata to the underlying AI model.
 */
export const SuggestionPanel: React.FC<SuggestionPanelProps> = ({
  projectId,
  phase,
  onAccept,
  onDismiss,
  context = {},
}) => {
  const queryClient = useQueryClient();

  const {
    data: suggestions = [],
    isLoading,
    isError,
    error,
  } = useQuery<Suggestion[], Error>({
    queryKey: ['suggestions', projectId, phase],
    queryFn: () => fetchSuggestions(projectId, phase, context),
    staleTime: 60_000,
    retry: 1,
  });

  const dismissMutation = useMutation<void, Error, string>({
    mutationFn: async (suggestionId: string) => {
      await fetch(
        `/api/v1/projects/${encodeURIComponent(projectId)}/suggestions/${encodeURIComponent(suggestionId)}`,
        { method: 'DELETE' }
      );
    },
    onSuccess: (_data, suggestionId) => {
      queryClient.setQueryData<Suggestion[]>(
        ['suggestions', projectId, phase],
        (prev = []) => prev.filter((s) => s.id !== suggestionId)
      );
      onDismiss?.(suggestionId);
    },
  });

  const handleAccept = useCallback(
    (suggestion: Suggestion) => {
      onAccept?.(suggestion);
    },
    [onAccept]
  );

  const handleDismiss = useCallback(
    (suggestionId: string) => {
      dismissMutation.mutate(suggestionId);
    },
    [dismissMutation]
  );

  return (
    <Paper className="rounded-xl overflow-hidden">
      {/* Header */}
      <Box className="flex items-center gap-2 px-4 py-3 border-b border-gray-100">
        <Lightbulb size={18} className="text-amber-500" />
        <Typography className="font-medium text-sm text-gray-800">
          AI Suggestions
        </Typography>
        {suggestions.length > 0 && (
          <Chip
            size="sm"
            label={String(suggestions.length)}
            color="default"
            className="ml-auto"
          />
        )}
      </Box>

      {/* Loading */}
      {isLoading && (
        <Box className="px-4 py-2">
          <LinearProgress />
        </Box>
      )}

      {/* Error */}
      {isError && (
        <Box className="flex items-center gap-2 px-4 py-3 text-red-600">
          <AlertTriangle size={16} />
          <Typography className="text-sm">
            {error?.message ?? 'Failed to load suggestions.'}
          </Typography>
        </Box>
      )}

      {/* Empty state */}
      {!isLoading && !isError && suggestions.length === 0 && (
        <Box className="px-4 py-6 text-center text-gray-400">
          <Lightbulb size={32} className="mx-auto mb-2 opacity-40" />
          <Typography className="text-sm">
            No suggestions for this phase.
          </Typography>
        </Box>
      )}

      {/* Suggestion list */}
      {!isLoading && !isError && suggestions.length > 0 && (
        <Box className="divide-y divide-gray-50">
          {suggestions.map((suggestion) => (
            <SuggestionItem
              key={suggestion.id}
              suggestion={suggestion}
              onAccept={handleAccept}
              onDismiss={handleDismiss}
            />
          ))}
        </Box>
      )}
    </Paper>
  );
};
