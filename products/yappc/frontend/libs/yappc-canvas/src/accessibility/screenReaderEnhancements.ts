/**
 * Feature 2.31: Screen Reader Enhancements
 *
 * Advanced screen reader support with relationship announcements, live collaboration
 * updates, and keyboard shortcut help system. Extends the base ariaRoles functionality.
 *
 * @example
 * ```typescript
 * // Create announcer
 * const announcer = createScreenReaderEnhancements({
 *   enableRelationships: true,
 *   enableCollaboration: true,
 *   announceDelay: 300,
 * });
 *
 * // Announce node relationships
 * announcer.announceNodeRelationships('node-1', {
 *   incoming: ['node-a', 'node-b'],
 *   outgoing: ['node-x', 'node-y'],
 * });
 *
 * // Announce collaboration event
 * announcer.announceCollaborativeEdit({
 *   user: 'Alice',
 *   action: 'updated',
 *   elementId: 'node-1',
 *   elementType: 'node',
 * });
 *
 * // Provide keyboard help
 * const help = announcer.getKeyboardShortcutHelp('navigation');
 * ```
 */

/**
 * Configuration for screen reader enhancements
 */
export interface ScreenReaderConfig {
  /** Enable relationship announcements */
  readonly enableRelationships: boolean;
  /** Enable collaboration announcements */
  readonly enableCollaboration: boolean;
  /** Enable keyboard shortcut help */
  readonly enableShortcutHelp: boolean;
  /** Delay before announcing (ms) to debounce rapid changes */
  readonly announceDelay: number;
  /** Maximum announcement queue size */
  readonly maxQueueSize: number;
  /** Enable verbose descriptions */
  readonly verboseMode: boolean;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: ScreenReaderConfig = {
  enableRelationships: true,
  enableCollaboration: true,
  enableShortcutHelp: true,
  announceDelay: 300,
  maxQueueSize: 10,
  verboseMode: false,
};

/**
 * Politeness level for announcements
 */
export type PolitenessLevel = 'off' | 'polite' | 'assertive';

/**
 * Relationship information for a node
 */
export interface NodeRelationships {
  readonly incoming: readonly string[]; // IDs of nodes with edges pointing to this node
  readonly outgoing: readonly string[]; // IDs of nodes this node points to
  readonly bidirectional: readonly string[]; // IDs of nodes with edges in both directions
  readonly labels?: Record<string, string>; // Optional: node ID -> label mapping
}

/**
 * Collaborative edit event
 */
export interface CollaborativeEditEvent {
  readonly user: string;
  readonly action: 'created' | 'updated' | 'deleted' | 'moved' | 'selected';
  readonly elementId: string;
  readonly elementType: 'node' | 'edge' | 'group' | 'annotation';
  readonly elementLabel?: string;
  readonly timestamp: Date;
  readonly details?: Record<string, unknown>;
}

/**
 * Keyboard shortcut category
 */
export type ShortcutCategory =
  | 'navigation'
  | 'edit'
  | 'selection'
  | 'layout'
  | 'layers'
  | 'document'
  | 'accessibility';

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  readonly keys: string;
  readonly description: string;
  readonly category: ShortcutCategory;
  readonly enabled: boolean;
}

/**
 * Announcement queue item
 */
interface AnnouncementQueueItem {
  readonly message: string;
  readonly politeness: PolitenessLevel;
  readonly timestamp: number;
  readonly category: 'relationship' | 'collaboration' | 'shortcut' | 'general';
}

/**
 * Screen reader enhancement state
 */
export interface ScreenReaderEnhancementState {
  readonly config: ScreenReaderConfig;
  readonly announcementQueue: readonly AnnouncementQueueItem[];
  readonly lastAnnouncement: AnnouncementQueueItem | null;
  readonly shortcuts: readonly KeyboardShortcut[];
  readonly isEnabled: boolean;
  readonly liveRegionId: string;
}

/**
 * Create screen reader enhancement state
 */
export function createScreenReaderEnhancements(
  config: Partial<ScreenReaderConfig> = {}
): ScreenReaderEnhancementState {
  return {
    config: { ...DEFAULT_CONFIG, ...config },
    announcementQueue: [],
    lastAnnouncement: null,
    shortcuts: DEFAULT_SHORTCUTS,
    isEnabled: true,
    liveRegionId: `sr-live-${Date.now()}`,
  };
}

/**
 * Default keyboard shortcuts
 */
const DEFAULT_SHORTCUTS: readonly KeyboardShortcut[] = [
  // Navigation
  {
    keys: 'Tab',
    description: 'Move to next element',
    category: 'navigation',
    enabled: true,
  },
  {
    keys: 'Shift+Tab',
    description: 'Move to previous element',
    category: 'navigation',
    enabled: true,
  },
  {
    keys: 'Arrow Keys',
    description: 'Navigate between connected nodes',
    category: 'navigation',
    enabled: true,
  },
  {
    keys: 'Home',
    description: 'Go to first node',
    category: 'navigation',
    enabled: true,
  },
  {
    keys: 'End',
    description: 'Go to last node',
    category: 'navigation',
    enabled: true,
  },

  // Selection
  {
    keys: 'Space',
    description: 'Select/deselect focused element',
    category: 'selection',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+A',
    description: 'Select all elements',
    category: 'selection',
    enabled: true,
  },
  {
    keys: 'Escape',
    description: 'Clear selection',
    category: 'selection',
    enabled: true,
  },

  // Edit
  {
    keys: 'Enter',
    description: 'Edit selected element',
    category: 'edit',
    enabled: true,
  },
  {
    keys: 'Delete/Backspace',
    description: 'Delete selected elements',
    category: 'edit',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+C',
    description: 'Copy selected elements',
    category: 'edit',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+V',
    description: 'Paste elements',
    category: 'edit',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+Z',
    description: 'Undo last action',
    category: 'edit',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+Shift+Z',
    description: 'Redo action',
    category: 'edit',
    enabled: true,
  },

  // Layout
  {
    keys: 'Cmd/Ctrl+L',
    description: 'Apply auto-layout',
    category: 'layout',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+F',
    description: 'Fit canvas to view',
    category: 'layout',
    enabled: true,
  },
  {
    keys: '+/=',
    description: 'Zoom in',
    category: 'layout',
    enabled: true,
  },
  {
    keys: '-',
    description: 'Zoom out',
    category: 'layout',
    enabled: true,
  },

  // Layers
  {
    keys: 'Cmd/Ctrl+]',
    description: 'Bring forward',
    category: 'layers',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+[',
    description: 'Send backward',
    category: 'layers',
    enabled: true,
  },

  // Document
  {
    keys: 'Cmd/Ctrl+S',
    description: 'Save document',
    category: 'document',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+P',
    description: 'Print/export document',
    category: 'document',
    enabled: true,
  },

  // Accessibility
  {
    keys: 'Cmd/Ctrl+/',
    description: 'Show keyboard shortcuts',
    category: 'accessibility',
    enabled: true,
  },
  {
    keys: 'Cmd/Ctrl+Shift+R',
    description: 'Announce node relationships',
    category: 'accessibility',
    enabled: true,
  },
];

/**
 * Announce node relationships to screen reader
 */
export function announceNodeRelationships(
  state: ScreenReaderEnhancementState,
  nodeId: string,
  relationships: NodeRelationships,
  nodeLabel?: string
): ScreenReaderEnhancementState {
  if (!state.config.enableRelationships || !state.isEnabled) {
    return state;
  }

  const label = nodeLabel || nodeId;
  const parts: string[] = [];

  // Count relationships
  const incomingCount = relationships.incoming.length;
  const outgoingCount = relationships.outgoing.length;
  const bidirectionalCount = relationships.bidirectional.length;

  parts.push(`Node ${label} has ${incomingCount + outgoingCount + bidirectionalCount} relationships.`);

  // Incoming connections
  if (incomingCount > 0) {
    const incomingLabels = relationships.incoming
      .map((id) => relationships.labels?.[id] || id)
      .join(', ');
    parts.push(
      `${incomingCount} incoming ${incomingCount === 1 ? 'connection' : 'connections'} from: ${incomingLabels}.`
    );
  }

  // Outgoing connections
  if (outgoingCount > 0) {
    const outgoingLabels = relationships.outgoing
      .map((id) => relationships.labels?.[id] || id)
      .join(', ');
    parts.push(
      `${outgoingCount} outgoing ${outgoingCount === 1 ? 'connection' : 'connections'} to: ${outgoingLabels}.`
    );
  }

  // Bidirectional connections
  if (bidirectionalCount > 0) {
    const bidirectionalLabels = relationships.bidirectional
      .map((id) => relationships.labels?.[id] || id)
      .join(', ');
    parts.push(
      `${bidirectionalCount} bidirectional ${bidirectionalCount === 1 ? 'connection' : 'connections'} with: ${bidirectionalLabels}.`
    );
  }

  if (incomingCount === 0 && outgoingCount === 0 && bidirectionalCount === 0) {
    parts.push('This node has no connections.');
  }

  const message = parts.join(' ');

  return enqueueAnnouncement(state, {
    message,
    politeness: 'polite',
    timestamp: Date.now(),
    category: 'relationship',
  });
}

/**
 * Announce collaborative edit event
 */
export function announceCollaborativeEdit(
  state: ScreenReaderEnhancementState,
  event: CollaborativeEditEvent
): ScreenReaderEnhancementState {
  if (!state.config.enableCollaboration || !state.isEnabled) {
    return state;
  }

  const elementLabel = event.elementLabel || event.elementId;
  const article = ['a', 'e', 'i', 'o', 'u'].includes(event.action[0]) ? 'an' : 'a';

  let message = `${event.user} ${event.action} ${article} ${event.elementType}: ${elementLabel}`;

  // Add verbose details if enabled
  if (state.config.verboseMode && event.details) {
    const detailStr = Object.entries(event.details)
      .map(([key, value]) => `${key}: ${value}`)
      .join(', ');
    message += `. Details: ${detailStr}`;
  }

  return enqueueAnnouncement(state, {
    message,
    politeness: 'polite', // Collaborative edits are polite, not assertive
    timestamp: event.timestamp.getTime(),
    category: 'collaboration',
  });
}

/**
 * Get keyboard shortcut help for a category
 */
export function getKeyboardShortcutHelp(
  state: ScreenReaderEnhancementState,
  category?: ShortcutCategory
): readonly KeyboardShortcut[] {
  if (!state.config.enableShortcutHelp) {
    return [];
  }

  if (category) {
    return state.shortcuts.filter(
      (s) => s.category === category && s.enabled
    );
  }

  return state.shortcuts.filter((s) => s.enabled);
}

/**
 * Announce keyboard shortcuts for a category
 */
export function announceKeyboardShortcuts(
  state: ScreenReaderEnhancementState,
  category?: ShortcutCategory
): ScreenReaderEnhancementState {
  if (!state.config.enableShortcutHelp || !state.isEnabled) {
    return state;
  }

  const shortcuts = getKeyboardShortcutHelp(state, category);

  if (shortcuts.length === 0) {
    return enqueueAnnouncement(state, {
      message: 'No keyboard shortcuts available.',
      politeness: 'polite',
      timestamp: Date.now(),
      category: 'shortcut',
    });
  }

  const categoryName = category || 'all categories';
  const shortcutList = shortcuts
    .map((s) => `${s.keys}: ${s.description}`)
    .join('. ');

  const message = `Keyboard shortcuts for ${categoryName}: ${shortcutList}`;

  return enqueueAnnouncement(state, {
    message,
    politeness: 'polite',
    timestamp: Date.now(),
    category: 'shortcut',
  });
}

/**
 * Custom announcement
 */
export function announceCustom(
  state: ScreenReaderEnhancementState,
  message: string,
  politeness: PolitenessLevel = 'polite'
): ScreenReaderEnhancementState {
  if (!state.isEnabled) {
    return state;
  }

  return enqueueAnnouncement(state, {
    message,
    politeness,
    timestamp: Date.now(),
    category: 'general',
  });
}

/**
 * Enqueue announcement (internal)
 */
function enqueueAnnouncement(
  state: ScreenReaderEnhancementState,
  item: AnnouncementQueueItem
): ScreenReaderEnhancementState {
  // Check if similar announcement was made recently (within announceDelay)
  if (
    state.lastAnnouncement &&
    state.lastAnnouncement.message === item.message &&
    item.timestamp - state.lastAnnouncement.timestamp < state.config.announceDelay
  ) {
    return state; // Skip duplicate announcement
  }

  // Add to queue, respecting max size
  const newQueue = [...state.announcementQueue, item];
  if (newQueue.length > state.config.maxQueueSize) {
    newQueue.shift(); // Remove oldest
  }

  return {
    ...state,
    announcementQueue: newQueue,
    lastAnnouncement: item,
  };
}

/**
 * Get next announcement to speak
 */
export function getNextAnnouncement(
  state: ScreenReaderEnhancementState
): {
  state: ScreenReaderEnhancementState;
  announcement: AnnouncementQueueItem | null;
} {
  if (state.announcementQueue.length === 0) {
    return { state, announcement: null };
  }

  const [announcement, ...remainingQueue] = state.announcementQueue;

  return {
    state: {
      ...state,
      announcementQueue: remainingQueue,
    },
    announcement,
  };
}

/**
 * Clear announcement queue
 */
export function clearAnnouncementQueue(
  state: ScreenReaderEnhancementState
): ScreenReaderEnhancementState {
  return {
    ...state,
    announcementQueue: [],
  };
}

/**
 * Enable/disable screen reader enhancements
 */
export function setScreenReaderEnabled(
  state: ScreenReaderEnhancementState,
  enabled: boolean
): ScreenReaderEnhancementState {
  return {
    ...state,
    isEnabled: enabled,
  };
}

/**
 * Update configuration
 */
export function updateScreenReaderConfig(
  state: ScreenReaderEnhancementState,
  config: Partial<ScreenReaderConfig>
): ScreenReaderEnhancementState {
  return {
    ...state,
    config: { ...state.config, ...config },
  };
}

/**
 * Register custom keyboard shortcut
 */
export function registerKeyboardShortcut(
  state: ScreenReaderEnhancementState,
  shortcut: KeyboardShortcut
): ScreenReaderEnhancementState {
  // Check if shortcut already exists
  const existingIndex = state.shortcuts.findIndex(
    (s) => s.keys === shortcut.keys && s.category === shortcut.category
  );

  if (existingIndex >= 0) {
    // Update existing
    const newShortcuts = [...state.shortcuts];
    newShortcuts[existingIndex] = shortcut;
    return {
      ...state,
      shortcuts: newShortcuts,
    };
  }

  // Add new
  return {
    ...state,
    shortcuts: [...state.shortcuts, shortcut],
  };
}

/**
 * Unregister keyboard shortcut
 */
export function unregisterKeyboardShortcut(
  state: ScreenReaderEnhancementState,
  keys: string,
  category: ShortcutCategory
): ScreenReaderEnhancementState {
  return {
    ...state,
    shortcuts: state.shortcuts.filter(
      (s) => !(s.keys === keys && s.category === category)
    ),
  };
}

/**
 * Get announcement queue statistics
 */
export function getAnnouncementStatistics(state: ScreenReaderEnhancementState): {
  queueSize: number;
  categoryCounts: Record<string, number>;
  oldestAnnouncement: number | null;
  newestAnnouncement: number | null;
} {
  const categoryCounts: Record<string, number> = {};

  for (const item of state.announcementQueue) {
    categoryCounts[item.category] = (categoryCounts[item.category] || 0) + 1;
  }

  const timestamps = state.announcementQueue.map((item) => item.timestamp);

  return {
    queueSize: state.announcementQueue.length,
    categoryCounts,
    oldestAnnouncement: timestamps.length > 0 ? Math.min(...timestamps) : null,
    newestAnnouncement: timestamps.length > 0 ? Math.max(...timestamps) : null,
  };
}

/**
 * Generate relationship description text
 */
export function describeNodeRelationships(
  relationships: NodeRelationships,
  nodeLabel?: string,
  verbose = false
): string {
  const incomingCount = relationships.incoming.length;
  const outgoingCount = relationships.outgoing.length;
  const bidirectionalCount = relationships.bidirectional.length;
  const totalCount = incomingCount + outgoingCount + bidirectionalCount;

  if (totalCount === 0) {
    return verbose
      ? `${nodeLabel || 'This node'} is not connected to any other nodes.`
      : 'No connections';
  }

  const parts: string[] = [];

  if (incomingCount > 0) {
    parts.push(
      `${incomingCount} incoming ${incomingCount === 1 ? 'edge' : 'edges'}`
    );
  }

  if (outgoingCount > 0) {
    parts.push(
      `${outgoingCount} outgoing ${outgoingCount === 1 ? 'edge' : 'edges'}`
    );
  }

  if (bidirectionalCount > 0) {
    parts.push(
      `${bidirectionalCount} bidirectional ${bidirectionalCount === 1 ? 'edge' : 'edges'}`
    );
  }

  if (verbose) {
    return `${nodeLabel || 'This node'} has ${parts.join(', ')}.`;
  }

  return parts.join(', ');
}
