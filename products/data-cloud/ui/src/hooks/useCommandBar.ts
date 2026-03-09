/**
 * useCommandBar Hook
 *
 * Custom hook for Command Bar functionality including intent parsing,
 * command execution, and history management.
 *
 * @doc.type hook
 * @doc.purpose Command Bar logic and state management
 * @doc.layer frontend
 */

import { useCallback, useMemo } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useNavigate } from 'react-router';
import {
  commandBarStateAtom,
  commandHistoryAtom,
  commandFavoritesAtom,
  toggleCommandBarAtom,
  setCommandQueryAtom,
  setCommandProcessingAtom,
  setCommandIntentAtom,
  addToCommandHistoryAtom,
  toggleCommandFavoriteAtom,
  closeCommandBarAtom,
  setCommandSuggestionsAtom,
  CommandIntent,
  CommandIntentType,
  CommandSuggestion,
  CommandHistoryEntry,
} from '../stores/commandBar.store';
import { brainService } from '../api/brain.service';

/**
 * Navigation commands for quick access
 */
const NAVIGATION_COMMANDS: CommandSuggestion[] = [
  {
    id: 'nav-hub',
    text: 'Go to Hub',
    description: 'Open the Intelligent Hub',
    category: 'Navigation',
    icon: 'home',
    action: () => {},
  },
  {
    id: 'nav-data',
    text: 'Go to Data Explorer',
    description: 'Explore collections and datasets',
    category: 'Navigation',
    icon: 'database',
    action: () => {},
  },
  {
    id: 'nav-workflows',
    text: 'Go to Workflows',
    description: 'Manage data pipelines',
    category: 'Navigation',
    icon: 'workflow',
    action: () => {},
  },
  {
    id: 'nav-trust',
    text: 'Go to Trust Center',
    description: 'Governance and compliance',
    category: 'Navigation',
    icon: 'shield',
    action: () => {},
  },
];

/**
 * Action commands for quick operations
 */
const ACTION_COMMANDS: CommandSuggestion[] = [
  {
    id: 'create-collection',
    text: 'Create new collection',
    description: 'AI will help infer schema',
    category: 'Actions',
    icon: 'plus',
    action: () => {},
  },
  {
    id: 'create-workflow',
    text: 'Create new workflow',
    description: 'Describe your pipeline in natural language',
    category: 'Actions',
    icon: 'plus',
    action: () => {},
  },
  {
    id: 'run-query',
    text: 'Run SQL query',
    description: 'Open SQL workspace',
    category: 'Actions',
    icon: 'play',
    action: () => {},
  },
];

/**
 * Intent patterns for classification
 */
const INTENT_PATTERNS: Array<{ pattern: RegExp; type: CommandIntentType; action?: string }> = [
  // Navigation patterns
  { pattern: /^(go to|show|open|navigate to)\s+(hub|home|dashboard)/i, type: 'NAVIGATE', action: 'hub' },
  { pattern: /^(go to|show|open|navigate to)\s+(data|collections?|datasets?)/i, type: 'NAVIGATE', action: 'data' },
  { pattern: /^(go to|show|open|navigate to)\s+(workflows?|pipelines?)/i, type: 'NAVIGATE', action: 'workflows' },
  { pattern: /^(go to|show|open|navigate to)\s+(governance|trust|compliance)/i, type: 'NAVIGATE', action: 'trust' },
  { pattern: /^(go to|show|open|navigate to)\s+(quality)/i, type: 'NAVIGATE', action: 'quality' },
  { pattern: /^(go to|show|open|navigate to)\s+(cost|optimization)/i, type: 'NAVIGATE', action: 'cost' },
  { pattern: /^(go to|show|open|navigate to)\s+(lineage)/i, type: 'NAVIGATE', action: 'lineage' },

  // Create patterns
  { pattern: /^(create|new|add)\s+(collection|dataset)/i, type: 'CREATE', action: 'collection' },
  { pattern: /^(create|new|add|build)\s+(workflow|pipeline)/i, type: 'CREATE', action: 'workflow' },
  { pattern: /^(create|new|add)\s+(alert|rule)/i, type: 'CREATE', action: 'alert' },

  // Query patterns
  { pattern: /^(show|find|search|list)\s+(.+)\s+in\s+(.+)/i, type: 'QUERY' },
  { pattern: /^(show|find|search|list)\s+(my|all)?\s*(collections?|datasets?|workflows?)/i, type: 'QUERY' },
  { pattern: /^(what|how|why|when)/i, type: 'QUERY' },

  // Analyze patterns
  { pattern: /^(analyze|check|audit)\s+(quality|data quality)/i, type: 'ANALYZE', action: 'quality' },
  { pattern: /^(analyze|check|audit)\s+(cost|spending|usage)/i, type: 'ANALYZE', action: 'cost' },
  { pattern: /^(analyze|check|audit)\s+(lineage|impact)/i, type: 'ANALYZE', action: 'lineage' },

  // Configure patterns
  { pattern: /^(set|configure|update|change)\s+(.+)/i, type: 'CONFIGURE' },
  { pattern: /^(apply|enable|disable)\s+(policy|rule)/i, type: 'CONFIGURE' },
];

/**
 * Parse intent from natural language query
 */
function parseIntent(query: string): CommandIntent {
  const trimmedQuery = query.trim();

  for (const { pattern, type, action } of INTENT_PATTERNS) {
    const match = trimmedQuery.match(pattern);
    if (match) {
      return {
        type,
        action,
        target: match[2] || match[3],
        parameters: {},
        confidence: 0.85,
        rawQuery: trimmedQuery,
      };
    }
  }

  // Default to QUERY type for unrecognized patterns
  return {
    type: 'UNKNOWN',
    rawQuery: trimmedQuery,
    confidence: 0.3,
  };
}

/**
 * Route mappings for navigation intents
 */
const ROUTE_MAP: Record<string, string> = {
  hub: '/',
  home: '/',
  dashboard: '/',
  data: '/data',
  collections: '/data',
  datasets: '/data',
  workflows: '/pipelines',
  pipelines: '/pipelines',
  trust: '/trust',
  governance: '/trust',
  quality: '/data?view=quality',
  cost: '/data?view=cost',
  lineage: '/data?view=lineage',
};

export interface UseCommandBarReturn {
  isOpen: boolean;
  query: string;
  isProcessing: boolean;
  currentIntent: CommandIntent | null;
  suggestions: CommandSuggestion[];
  history: CommandHistoryEntry[];
  favorites: string[];
  error: string | null;
  toggle: () => void;
  close: () => void;
  setQuery: (query: string) => void;
  execute: (query?: string) => Promise<void>;
  toggleFavorite: (query: string) => void;
  isFavorite: (query: string) => boolean;
}

/**
 * Custom hook for Command Bar functionality
 */
export function useCommandBar(): UseCommandBarReturn {
  const navigate = useNavigate();
  const [state] = useAtom(commandBarStateAtom);
  const history = useAtomValue(commandHistoryAtom);
  const favorites = useAtomValue(commandFavoritesAtom);

  const toggle = useSetAtom(toggleCommandBarAtom);
  const close = useSetAtom(closeCommandBarAtom);
  const setQuery = useSetAtom(setCommandQueryAtom);
  const setProcessing = useSetAtom(setCommandProcessingAtom);
  const setIntent = useSetAtom(setCommandIntentAtom);
  const setSuggestions = useSetAtom(setCommandSuggestionsAtom);
  const addToHistory = useSetAtom(addToCommandHistoryAtom);
  const toggleFav = useSetAtom(toggleCommandFavoriteAtom);

  // Filter suggestions based on query
  const filteredSuggestions = useMemo(() => {
    const q = state.query.toLowerCase();
    if (!q) return [...NAVIGATION_COMMANDS, ...ACTION_COMMANDS];

    return [...NAVIGATION_COMMANDS, ...ACTION_COMMANDS].filter(
      (cmd) =>
        cmd.text.toLowerCase().includes(q) ||
        cmd.description?.toLowerCase().includes(q)
    );
  }, [state.query]);

  // Update suggestions when query changes
  const handleSetQuery = useCallback(
    (query: string) => {
      setQuery(query);
      // Parse intent as user types
      if (query.length > 2) {
        const intent = parseIntent(query);
        setIntent(intent);
      } else {
        setIntent(null);
      }
    },
    [setQuery, setIntent]
  );

  // Execute command
  const execute = useCallback(
    async (queryOverride?: string) => {
      const queryToExecute = queryOverride || state.query;
      if (!queryToExecute.trim()) return;

      setProcessing(true);

      try {
        const intent = parseIntent(queryToExecute);
        setIntent(intent);

        // Handle navigation intents
        if (intent.type === 'NAVIGATE' && intent.action) {
          const route = ROUTE_MAP[intent.action];
          if (route) {
            navigate(route);
            close();
          }
        }
        // Handle create intents
        else if (intent.type === 'CREATE') {
          if (intent.action === 'collection') {
            navigate('/collections/new');
          } else if (intent.action === 'workflow') {
            navigate('/pipelines/new');
          }
          close();
        }
        // Handle query/analyze intents - try Brain API
        else if (intent.type === 'QUERY' || intent.type === 'ANALYZE') {
          try {
            const memories = await brainService.recallMemory(queryToExecute);
            // If we got results, show them (in future, display in workspace)
            if (memories.length > 0) {
              console.log('Brain recall results:', memories);
            }
          } catch {
            // Brain API not available, continue with basic handling
          }
        }

        // Add to history
        addToHistory({
          id: Date.now().toString(),
          query: queryToExecute,
          intent,
          timestamp: new Date().toISOString(),
          success: true,
        });

        setProcessing(false);
      } catch (error) {
        setProcessing(false);
        addToHistory({
          id: Date.now().toString(),
          query: queryToExecute,
          intent: parseIntent(queryToExecute),
          timestamp: new Date().toISOString(),
          success: false,
        });
      }
    },
    [state.query, setProcessing, setIntent, navigate, close, addToHistory]
  );

  const isFavorite = useCallback(
    (query: string) => favorites.includes(query),
    [favorites]
  );

  return {
    isOpen: state.isOpen,
    query: state.query,
    isProcessing: state.isProcessing,
    currentIntent: state.currentIntent,
    suggestions: filteredSuggestions,
    history,
    favorites,
    error: state.error,
    toggle,
    close,
    setQuery: handleSetQuery,
    execute,
    toggleFavorite: toggleFav,
    isFavorite,
  };
}

export default useCommandBar;

