/**
 * Feature 2.30: Shortcut Palette
 * Command palette for power users with searchable commands and shortcuts
 */

/**
 * Keyboard modifier keys
 */
export enum ModifierKey {
  CTRL = 'Ctrl',
  ALT = 'Alt',
  SHIFT = 'Shift',
  META = 'Meta', // Cmd on Mac, Win on Windows
}

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  key: string; // Main key (e.g., 'z', 'F1')
  modifiers?: ModifierKey[]; // Optional modifiers
}

/**
 * Command category for organization
 */
export enum CommandCategory {
  EDIT = 'Edit',
  VIEW = 'View',
  SELECTION = 'Selection',
  NAVIGATION = 'Navigation',
  LAYOUT = 'Layout',
  FORMAT = 'Format',
  TOOLS = 'Tools',
  HELP = 'Help',
}

/**
 * Command definition in the palette
 */
export interface Command {
  id: string;
  label: string; // Display name
  description?: string; // Optional description
  category: CommandCategory;
  shortcut?: KeyboardShortcut; // Keyboard shortcut
  execute: () => void | Promise<void>; // Action to perform
  enabled?: boolean; // Whether command is currently available
  keywords?: string[]; // Additional search keywords
}

/**
 * Shortcut conflict between commands
 */
export interface ShortcutConflict {
  shortcut: KeyboardShortcut;
  commands: Command[]; // Commands sharing the same shortcut
  severity: 'warning' | 'error'; // Severity level
}

/**
 * Search result with relevance score
 */
export interface SearchResult {
  command: Command;
  score: number; // Relevance score (0-100)
  matchedFields: string[]; // Fields that matched (label, description, keywords)
}

/**
 * Command palette state
 */
export interface CommandPaletteState {
  commands: Map<string, Command>; // All registered commands
  shortcuts: Map<string, string[]>; // Shortcut string -> command IDs
  conflicts: ShortcutConflict[]; // Detected conflicts
  searchQuery: string; // Current search query
  results: SearchResult[]; // Current search results
  selectedIndex: number; // Selected result index
}

/**
 * Options for creating command palette
 */
export interface CommandPaletteOptions {
  maxResults?: number; // Max search results (default: 20)
  fuzzySearch?: boolean; // Enable fuzzy search (default: true)
  caseSensitive?: boolean; // Case-sensitive search (default: false)
  detectConflicts?: boolean; // Detect shortcut conflicts (default: true)
}

const DEFAULT_OPTIONS: Required<CommandPaletteOptions> = {
  maxResults: 20,
  fuzzySearch: true,
  caseSensitive: false,
  detectConflicts: true,
};

/**
 * Create a new command palette
 */
export function createCommandPalette(
  options: CommandPaletteOptions = {}
): CommandPaletteState {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  return {
    commands: new Map(),
    shortcuts: new Map(),
    conflicts: [],
    searchQuery: '',
    results: [],
    selectedIndex: -1,
  };
}

/**
 * Convert keyboard shortcut to string representation
 */
export function shortcutToString(shortcut: KeyboardShortcut): string {
  const parts: string[] = [];

  if (shortcut.modifiers) {
    // Sort modifiers for consistency
    const sortedModifiers = [...shortcut.modifiers].sort();
    parts.push(...sortedModifiers);
  }

  parts.push(shortcut.key);

  return parts.join('+');
}

/**
 * Parse shortcut string to KeyboardShortcut
 */
export function parseShortcut(shortcutString: string): KeyboardShortcut | null {
  const parts = shortcutString.split('+').map(p => p.trim());
  if (parts.length === 0 || !shortcutString.trim()) return null;

  const key = parts[parts.length - 1];
  if (!key) return null; // Empty key

  const modifiers: ModifierKey[] = [];

  for (let i = 0; i < parts.length - 1; i++) {
    const mod = parts[i] as ModifierKey;
    if (Object.values(ModifierKey).includes(mod)) {
      modifiers.push(mod);
    }
  }

  return { key, modifiers: modifiers.length > 0 ? modifiers : undefined };
}

/**
 * Check if two shortcuts are equal
 */
export function shortcutsEqual(a: KeyboardShortcut, b: KeyboardShortcut): boolean {
  return shortcutToString(a) === shortcutToString(b);
}

/**
 * Register a command in the palette
 */
export function registerCommand(
  state: CommandPaletteState,
  command: Command,
  options: CommandPaletteOptions = {}
): void {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  // Register command
  state.commands.set(command.id, command);

  // Register shortcut mapping
  if (command.shortcut) {
    const shortcutKey = shortcutToString(command.shortcut);
    const existing = state.shortcuts.get(shortcutKey) || [];
    existing.push(command.id);
    state.shortcuts.set(shortcutKey, existing);

    // Detect conflicts
    if (opts.detectConflicts && existing.length > 1) {
      const conflictingCommands = existing
        .map(id => state.commands.get(id))
        .filter((cmd): cmd is Command => cmd !== undefined);

      // Check if conflict already exists
      const existingConflict = state.conflicts.find(c =>
        shortcutsEqual(c.shortcut, command.shortcut!)
      );

      if (existingConflict) {
        existingConflict.commands = conflictingCommands;
        existingConflict.severity = conflictingCommands.length >= 3 ? 'error' : 'warning';
      } else {
        state.conflicts.push({
          shortcut: command.shortcut,
          commands: conflictingCommands,
          severity: conflictingCommands.length >= 3 ? 'error' : 'warning',
        });
      }
    }
  }
}

/**
 * Unregister a command from the palette
 */
export function unregisterCommand(
  state: CommandPaletteState,
  commandId: string
): void {
  const command = state.commands.get(commandId);
  if (!command) return;

  // Remove from commands map
  state.commands.delete(commandId);

  // Remove from shortcuts map
  if (command.shortcut) {
    const shortcutKey = shortcutToString(command.shortcut);
    const existing = state.shortcuts.get(shortcutKey) || [];
    const filtered = existing.filter(id => id !== commandId);

    if (filtered.length === 0) {
      state.shortcuts.delete(shortcutKey);
    } else {
      state.shortcuts.set(shortcutKey, filtered);
    }

    // Update conflicts
    state.conflicts = state.conflicts
      .map(conflict => {
        if (shortcutsEqual(conflict.shortcut, command.shortcut!)) {
          const updatedCommands = conflict.commands.filter(cmd => cmd.id !== commandId);
          if (updatedCommands.length > 1) {
            return {
              ...conflict,
              commands: updatedCommands,
              severity: updatedCommands.length >= 3 ? 'error' : 'warning',
            } as ShortcutConflict;
          }
          return null; // Remove conflict if only one command left
        }
        return conflict;
      })
      .filter((c): c is ShortcutConflict => c !== null);
  }
}

/**
 * Get all commands by category
 */
export function getCommandsByCategory(
  state: CommandPaletteState,
  category: CommandCategory
): Command[] {
  return Array.from(state.commands.values()).filter(cmd => cmd.category === category);
}

/**
 * Get command by keyboard shortcut
 */
export function getCommandByShortcut(
  state: CommandPaletteState,
  shortcut: KeyboardShortcut
): Command[] {
  const shortcutKey = shortcutToString(shortcut);
  const commandIds = state.shortcuts.get(shortcutKey) || [];
  return commandIds
    .map(id => state.commands.get(id))
    .filter((cmd): cmd is Command => cmd !== undefined);
}

/**
 * Calculate relevance score for search match
 */
function calculateScore(
  command: Command,
  query: string,
  caseSensitive: boolean,
  fuzzySearch: boolean
): { score: number; matchedFields: string[] } {
  if (!query) return { score: 0, matchedFields: [] };

  const normalizedQuery = caseSensitive ? query : query.toLowerCase();
  const matchedFields: string[] = [];
  let score = 0;

  // Helper to normalize text
  const normalize = (text: string) => (caseSensitive ? text : text.toLowerCase());

  // Check label (highest priority)
  const label = normalize(command.label);
  if (label === normalizedQuery) {
    score += 100; // Exact match
    matchedFields.push('label');
  } else if (label.startsWith(normalizedQuery)) {
    score += 80; // Prefix match
    matchedFields.push('label');
  } else if (label.includes(normalizedQuery)) {
    score += 50; // Contains match
    matchedFields.push('label');
  } else if (fuzzySearch && fuzzyMatch(label, normalizedQuery)) {
    score += 30; // Fuzzy match
    matchedFields.push('label');
  }

  // Check description (medium priority)
  if (command.description) {
    const description = normalize(command.description);
    if (description.includes(normalizedQuery)) {
      score += 40;
      matchedFields.push('description');
    } else if (fuzzySearch && fuzzyMatch(description, normalizedQuery)) {
      score += 20;
      matchedFields.push('description');
    }
  }

  // Check keywords (lower priority)
  if (command.keywords) {
    for (const keyword of command.keywords) {
      const normalizedKeyword = normalize(keyword);
      if (normalizedKeyword === normalizedQuery) {
        score += 35;
        matchedFields.push('keywords');
        break;
      } else if (normalizedKeyword.includes(normalizedQuery)) {
        score += 25;
        matchedFields.push('keywords');
        break;
      }
    }
  }

  // Check shortcut (bonus points)
  if (command.shortcut) {
    const shortcutStr = normalize(shortcutToString(command.shortcut));
    if (shortcutStr.includes(normalizedQuery)) {
      score += 15;
      matchedFields.push('shortcut');
    }
  }

  // Boost enabled commands
  if (command.enabled !== false) {
    score += 5;
  }

  return { score, matchedFields };
}

/**
 * Simple fuzzy matching algorithm
 */
function fuzzyMatch(text: string, pattern: string): boolean {
  let patternIdx = 0;
  let textIdx = 0;

  while (patternIdx < pattern.length && textIdx < text.length) {
    if (pattern[patternIdx] === text[textIdx]) {
      patternIdx++;
    }
    textIdx++;
  }

  return patternIdx === pattern.length;
}

/**
 * Search commands with query
 */
export function searchCommands(
  state: CommandPaletteState,
  query: string,
  options: CommandPaletteOptions = {}
): SearchResult[] {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  state.searchQuery = query;

  // If query is empty, return all commands
  if (!query.trim()) {
    const allCommands = Array.from(state.commands.values())
      .filter(cmd => cmd.enabled !== false)
      .slice(0, opts.maxResults)
      .map(command => ({ command, score: 0, matchedFields: [] }));

    state.results = allCommands;
    state.selectedIndex = allCommands.length > 0 ? 0 : -1;
    return allCommands;
  }

  // Calculate scores for all commands
  const results: SearchResult[] = [];

  for (const command of state.commands.values()) {
    // Skip disabled commands
    if (command.enabled === false) continue;

    const { score, matchedFields } = calculateScore(
      command,
      query,
      opts.caseSensitive,
      opts.fuzzySearch
    );

    if (score > 0) {
      results.push({ command, score, matchedFields });
    }
  }

  // Sort by score (descending) and limit results
  results.sort((a, b) => b.score - a.score);
  const limitedResults = results.slice(0, opts.maxResults);

  state.results = limitedResults;
  state.selectedIndex = limitedResults.length > 0 ? 0 : -1;

  return limitedResults;
}

/**
 * Clear search and reset state
 */
export function clearSearch(state: CommandPaletteState): void {
  state.searchQuery = '';
  state.results = [];
  state.selectedIndex = -1;
}

/**
 * Select next result
 */
export function selectNext(state: CommandPaletteState): void {
  if (state.results.length === 0) return;

  state.selectedIndex = (state.selectedIndex + 1) % state.results.length;
}

/**
 * Select previous result
 */
export function selectPrevious(state: CommandPaletteState): void {
  if (state.results.length === 0) return;

  state.selectedIndex =
    state.selectedIndex <= 0 ? state.results.length - 1 : state.selectedIndex - 1;
}

/**
 * Get currently selected command
 */
export function getSelectedCommand(state: CommandPaletteState): Command | null {
  if (state.selectedIndex < 0 || state.selectedIndex >= state.results.length) {
    return null;
  }

  return state.results[state.selectedIndex].command;
}

/**
 * Execute a command
 */
export async function executeCommand(command: Command): Promise<void> {
  if (command.enabled === false) {
    throw new Error(`Command "${command.id}" is disabled`);
  }

  await command.execute();
}

/**
 * Execute selected command
 */
export async function executeSelected(state: CommandPaletteState): Promise<void> {
  const selected = getSelectedCommand(state);
  if (!selected) {
    throw new Error('No command selected');
  }

  await executeCommand(selected);
}

/**
 * Get all shortcut conflicts
 */
export function getConflicts(state: CommandPaletteState): ShortcutConflict[] {
  return state.conflicts;
}

/**
 * Get conflicts for specific command
 */
export function getConflictsForCommand(
  state: CommandPaletteState,
  commandId: string
): ShortcutConflict | null {
  const command = state.commands.get(commandId);
  if (!command || !command.shortcut) return null;

  return (
    state.conflicts.find(conflict => shortcutsEqual(conflict.shortcut, command.shortcut!)) ||
    null
  );
}

/**
 * Check if command has shortcut conflict
 */
export function hasConflict(state: CommandPaletteState, commandId: string): boolean {
  return getConflictsForCommand(state, commandId) !== null;
}

/**
 * Get all enabled commands
 */
export function getEnabledCommands(state: CommandPaletteState): Command[] {
  return Array.from(state.commands.values()).filter(cmd => cmd.enabled !== false);
}

/**
 * Get all disabled commands
 */
export function getDisabledCommands(state: CommandPaletteState): Command[] {
  return Array.from(state.commands.values()).filter(cmd => cmd.enabled === false);
}

/**
 * Update command enabled state
 */
export function setCommandEnabled(
  state: CommandPaletteState,
  commandId: string,
  enabled: boolean
): void {
  const command = state.commands.get(commandId);
  if (command) {
    command.enabled = enabled;
  }
}

/**
 * Get command statistics
 */
export function getStatistics(state: CommandPaletteState): {
  totalCommands: number;
  enabledCommands: number;
  disabledCommands: number;
  commandsByCategory: Record<CommandCategory, number>;
  totalShortcuts: number;
  totalConflicts: number;
  conflictsBySeverity: { warning: number; error: number };
} {
  const commandsByCategory: Record<CommandCategory, number> = {
    [CommandCategory.EDIT]: 0,
    [CommandCategory.VIEW]: 0,
    [CommandCategory.SELECTION]: 0,
    [CommandCategory.NAVIGATION]: 0,
    [CommandCategory.LAYOUT]: 0,
    [CommandCategory.FORMAT]: 0,
    [CommandCategory.TOOLS]: 0,
    [CommandCategory.HELP]: 0,
  };

  let enabledCount = 0;
  let disabledCount = 0;

  for (const command of state.commands.values()) {
    commandsByCategory[command.category]++;
    if (command.enabled !== false) {
      enabledCount++;
    } else {
      disabledCount++;
    }
  }

  const conflictsBySeverity = state.conflicts.reduce(
    (acc, conflict) => {
      acc[conflict.severity]++;
      return acc;
    },
    { warning: 0, error: 0 }
  );

  return {
    totalCommands: state.commands.size,
    enabledCommands: enabledCount,
    disabledCommands: disabledCount,
    commandsByCategory,
    totalShortcuts: state.shortcuts.size,
    totalConflicts: state.conflicts.length,
    conflictsBySeverity,
  };
}
