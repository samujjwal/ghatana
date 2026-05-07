/**
 * Command Bar Store
 *
 * Jotai-based state management for the Command Bar.
 * Handles command history, favorites, and AI intent parsing state.
 *
 * @doc.type store
 * @doc.purpose State management for Command Bar
 * @doc.layer frontend
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

/**
 * Command intent types for AI classification
 */
export type CommandIntentType = 'QUERY' | 'CREATE' | 'ANALYZE' | 'CONFIGURE' | 'NAVIGATE' | 'UNKNOWN';

/**
 * Command intent parsed by AI
 */
export interface CommandIntent {
  type: CommandIntentType;
  target?: string;
  action?: string;
  parameters?: Record<string, unknown>;
  confidence: number;
  rawQuery: string;
}

/**
 * AI suggestion for command bar
 */
export interface CommandSuggestion {
  id: string;
  text: string;
  description?: string;
  icon?: string;
  category: string;
  action: () => void;
}

/**
 * Command history entry
 */
export interface CommandHistoryEntry {
  id: string;
  query: string;
  intent: CommandIntent;
  timestamp: string;
  success: boolean;
}

/**
 * Command bar state
 */
export interface CommandBarState {
  isOpen: boolean;
  query: string;
  isProcessing: boolean;
  currentIntent: CommandIntent | null;
  suggestions: CommandSuggestion[];
  error: string | null;
}

/**
 * Initial state
 */
const initialState: CommandBarState = {
  isOpen: false,
  query: '',
  isProcessing: false,
  currentIntent: null,
  suggestions: [],
  error: null,
};

/**
 * Main command bar state atom
 */
export const commandBarStateAtom = atom<CommandBarState>(initialState);

/**
 * Persistent history (stored in localStorage)
 */
export const commandHistoryAtom = atomWithStorage<CommandHistoryEntry[]>(
  'data-cloud-command-history',
  []
);

/**
 * Persistent favorites (stored in localStorage)
 */
export const commandFavoritesAtom = atomWithStorage<string[]>(
  'data-cloud-command-favorites',
  []
);

/**
 * Derived atom: is command bar open
 */
export const isCommandBarOpenAtom = atom(
  (get) => get(commandBarStateAtom).isOpen
);

/**
 * Atom to toggle command bar
 */
export const toggleCommandBarAtom = atom(
  null,
  (get, set) => {
    const current = get(commandBarStateAtom);
    set(commandBarStateAtom, {
      ...current,
      isOpen: !current.isOpen,
      query: '',
      error: null,
    });
  }
);

/**
 * Atom to set query
 */
export const setCommandQueryAtom = atom(
  null,
  (get, set, query: string) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      query,
      error: null,
    });
  }
);

/**
 * Atom to set processing state
 */
export const setCommandProcessingAtom = atom(
  null,
  (get, set, isProcessing: boolean) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      isProcessing,
    });
  }
);

/**
 * Atom to set current intent
 */
export const setCommandIntentAtom = atom(
  null,
  (get, set, intent: CommandIntent | null) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      currentIntent: intent,
      isProcessing: false,
    });
  }
);

/**
 * Atom to set suggestions
 */
export const setCommandSuggestionsAtom = atom(
  null,
  (get, set, suggestions: CommandSuggestion[]) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      suggestions,
    });
  }
);

/**
 * Atom to add to history
 */
export const addToCommandHistoryAtom = atom(
  null,
  (get, set, entry: CommandHistoryEntry) => {
    const history = get(commandHistoryAtom);
    // Keep only last 50 entries
    const updatedHistory = [entry, ...history].slice(0, 50);
    set(commandHistoryAtom, updatedHistory);
  }
);

/**
 * Atom to toggle favorite
 */
export const toggleCommandFavoriteAtom = atom(
  null,
  (get, set, query: string) => {
    const favorites = get(commandFavoritesAtom);
    if (favorites.includes(query)) {
      set(commandFavoritesAtom, favorites.filter((f) => f !== query));
    } else {
      set(commandFavoritesAtom, [...favorites, query]);
    }
  }
);

/**
 * Atom to close command bar
 */
export const closeCommandBarAtom = atom(
  null,
  (get, set) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      isOpen: false,
      query: '',
      currentIntent: null,
      suggestions: [],
      error: null,
    });
  }
);

/**
 * Atom to set error
 */
export const setCommandErrorAtom = atom(
  null,
  (get, set, error: string | null) => {
    set(commandBarStateAtom, {
      ...get(commandBarStateAtom),
      error,
      isProcessing: false,
    });
  }
);

