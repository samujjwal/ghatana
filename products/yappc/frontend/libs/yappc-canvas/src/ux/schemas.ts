import { z } from 'zod';

// UX Validation Schemas - Phase 10: Advanced User Experience & Accessibility

// Command palette schemas
export const CommandSchema = z.object({
  id: z.string(),
  title: z.string(),
  description: z.string().optional(),
  category: z.string(),
  keywords: z.array(z.string()),
  shortcut: z.string().optional(),
  icon: z.string().optional(),
  enabled: z.boolean().default(true),
  visible: z.boolean().default(true),
  action: z.function().optional(),
});

export const CommandCategorySchema = z.object({
  id: z.string(),
  label: z.string(),
  icon: z.string().optional(),
  priority: z.number().default(100),
});

// Accessibility schemas
export const AccessibilityConfigSchema = z.object({
  enableScreenReader: z.boolean().default(true),
  enableKeyboardNavigation: z.boolean().default(true),
  enableHighContrast: z.boolean().default(false),
  enableReducedMotion: z.boolean().default(false),
  fontSize: z
    .enum(['small', 'medium', 'large', 'extra-large'])
    .default('medium'),
  colorScheme: z.enum(['light', 'dark', 'auto']).default('auto'),
  announceChanges: z.boolean().default(true),
  focusManagement: z.boolean().default(true),
});

export const AccessibilityAnnouncementSchema = z.object({
  id: z.string(),
  message: z.string(),
  priority: z.enum(['polite', 'assertive']).default('polite'),
  timestamp: z.string().datetime(),
});

// Keyboard shortcuts schemas
export const KeyboardShortcutSchema = z.object({
  id: z.string(),
  keys: z.array(z.string()),
  description: z.string(),
  category: z.string(),
  preventDefault: z.boolean().default(true),
  enabled: z.boolean().default(true),
  context: z.enum(['global', 'canvas', 'dialog']).default('global'),
  action: z.function().optional(),
});

// User preferences schemas
export const UserPreferencesSchema = z.object({
  accessibility: AccessibilityConfigSchema,
  shortcuts: z.array(KeyboardShortcutSchema),
  ui: z.object({
    theme: z.enum(['light', 'dark', 'auto']).default('auto'),
    density: z.enum(['compact', 'standard', 'comfortable']).default('standard'),
    animations: z.boolean().default(true),
    tooltips: z.boolean().default(true),
    soundEffects: z.boolean().default(false),
  }),
  canvas: z.object({
    snapToGrid: z.boolean().default(true),
    showGrid: z.boolean().default(true),
    autoSave: z.boolean().default(true),
    autoSaveInterval: z.number().min(10).max(300).default(30), // seconds
    maxUndoSteps: z.number().min(10).max(1000).default(50),
  }),
  collaboration: z.object({
    showCursors: z.boolean().default(true),
    showPresence: z.boolean().default(true),
    autoAcceptChanges: z.boolean().default(false),
    notifyOnChanges: z.boolean().default(true),
  }),
});

export const UXMetricsSchema = z.object({
  id: z.string(),
  userId: z.string().optional(),
  sessionId: z.string(),
  timestamp: z.string().datetime(),
  event: z.object({
    type: z.enum([
      'command_executed',
      'shortcut_used',
      'accessibility_feature_used',
      'ui_interaction',
      'error_encountered',
      'feature_discovered',
    ]),
    data: z.record(z.string(), z.any()),
    context: z.string().optional(),
    duration: z.number().optional(),
  }),
});

// Type exports
/**
 *
 */
export type Command = z.infer<typeof CommandSchema>;
/**
 *
 */
export type CommandCategory = z.infer<typeof CommandCategorySchema>;
/**
 *
 */
export type AccessibilityConfig = z.infer<typeof AccessibilityConfigSchema>;
/**
 *
 */
export type AccessibilityAnnouncement = z.infer<
  typeof AccessibilityAnnouncementSchema
>;
/**
 *
 */
export type KeyboardShortcut = z.infer<typeof KeyboardShortcutSchema>;
/**
 *
 */
export type UserPreferences = z.infer<typeof UserPreferencesSchema>;
/**
 *
 */
export type UXMetrics = z.infer<typeof UXMetricsSchema>;

// Default data creators
export const createDefaultUserPreferences = (): UserPreferences => ({
  accessibility: {
    enableScreenReader: true,
    enableKeyboardNavigation: true,
    enableHighContrast: false,
    enableReducedMotion: false,
    fontSize: 'medium',
    colorScheme: 'auto',
    announceChanges: true,
    focusManagement: true,
  },
  shortcuts: createDefaultShortcuts(),
  ui: {
    theme: 'auto',
    density: 'standard',
    animations: true,
    tooltips: true,
    soundEffects: false,
  },
  canvas: {
    snapToGrid: true,
    showGrid: true,
    autoSave: true,
    autoSaveInterval: 30,
    maxUndoSteps: 50,
  },
  collaboration: {
    showCursors: true,
    showPresence: true,
    autoAcceptChanges: false,
    notifyOnChanges: true,
  },
});

export const createDefaultShortcuts = (): KeyboardShortcut[] => [
  // Essential Canvas shortcuts
  {
    id: 'canvas-zoom-in',
    keys: ['ctrl', '+'],
    description: 'Zoom in on canvas',
    category: 'canvas',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'canvas-zoom-out',
    keys: ['ctrl', '-'],
    description: 'Zoom out of canvas',
    category: 'canvas',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'canvas-fit-screen',
    keys: ['ctrl', '0'],
    description: 'Fit canvas to screen',
    category: 'canvas',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  // File operations
  {
    id: 'file-new',
    keys: ['ctrl', 'n'],
    description: 'Create new canvas',
    category: 'file',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'file-open',
    keys: ['ctrl', 'o'],
    description: 'Open canvas',
    category: 'file',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'file-save',
    keys: ['ctrl', 's'],
    description: 'Save canvas',
    category: 'file',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  // Edit operations
  {
    id: 'edit-undo',
    keys: ['ctrl', 'z'],
    description: 'Undo last action',
    category: 'edit',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'edit-redo',
    keys: ['ctrl', 'shift', 'z'],
    description: 'Redo last undone action',
    category: 'edit',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'edit-copy',
    keys: ['ctrl', 'c'],
    description: 'Copy selection',
    category: 'edit',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'edit-paste',
    keys: ['ctrl', 'v'],
    description: 'Paste from clipboard',
    category: 'edit',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  // Tools
  {
    id: 'tool-select',
    keys: ['v'],
    description: 'Select tool',
    category: 'tools',
    enabled: true,
    preventDefault: true,
    context: 'canvas',
  },
  {
    id: 'tool-hand',
    keys: ['h'],
    description: 'Hand tool',
    category: 'tools',
    enabled: true,
    preventDefault: true,
    context: 'canvas',
  },
  // System
  {
    id: 'system-command-palette',
    keys: ['ctrl', 'shift', 'p'],
    description: 'Open command palette',
    category: 'system',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'system-preferences',
    keys: ['ctrl', ','],
    description: 'Open preferences',
    category: 'system',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
  {
    id: 'system-help',
    keys: ['f1'],
    description: 'Show help',
    category: 'help',
    enabled: true,
    preventDefault: true,
    context: 'global',
  },
];

export const createDefaultCommands = (): Command[] => [
  // File commands
  {
    id: 'create-new-canvas',
    title: 'Create New Canvas',
    description: 'Create a new blank canvas',
    category: 'file',
    keywords: ['new', 'create', 'canvas', 'blank'],
    icon: '📄',
    shortcut: 'Ctrl + N',
    enabled: true,
    visible: true,
  },
  {
    id: 'open-canvas',
    title: 'Open Canvas',
    description: 'Open an existing canvas',
    category: 'file',
    keywords: ['open', 'load', 'canvas'],
    icon: '📂',
    shortcut: 'Ctrl + O',
    enabled: true,
    visible: true,
  },
  {
    id: 'save-canvas',
    title: 'Save Canvas',
    description: 'Save the current canvas',
    category: 'file',
    keywords: ['save', 'canvas'],
    icon: '💾',
    shortcut: 'Ctrl + S',
    enabled: true,
    visible: true,
  },
  // Edit commands
  {
    id: 'undo',
    title: 'Undo',
    description: 'Undo the last action',
    category: 'edit',
    keywords: ['undo', 'revert'],
    icon: '↶',
    shortcut: 'Ctrl + Z',
    enabled: true,
    visible: true,
  },
  {
    id: 'redo',
    title: 'Redo',
    description: 'Redo the last undone action',
    category: 'edit',
    keywords: ['redo', 'restore'],
    icon: '↷',
    shortcut: 'Ctrl + Shift + Z',
    enabled: true,
    visible: true,
  },
  // View commands
  {
    id: 'zoom-in',
    title: 'Zoom In',
    description: 'Zoom in on the canvas',
    category: 'view',
    keywords: ['zoom', 'in', 'magnify'],
    icon: '🔍',
    shortcut: 'Ctrl + =',
    enabled: true,
    visible: true,
  },
  {
    id: 'zoom-out',
    title: 'Zoom Out',
    description: 'Zoom out of the canvas',
    category: 'view',
    keywords: ['zoom', 'out', 'shrink'],
    icon: '🔍',
    shortcut: 'Ctrl + -',
    enabled: true,
    visible: true,
  },
  {
    id: 'fit-to-screen',
    title: 'Fit to Screen',
    description: 'Fit the canvas to screen',
    category: 'view',
    keywords: ['fit', 'screen', 'zoom', 'all'],
    icon: '📺',
    shortcut: 'Ctrl + 0',
    enabled: true,
    visible: true,
  },
  // Tools
  {
    id: 'select-tool',
    title: 'Select Tool',
    description: 'Activate the selection tool',
    category: 'tools',
    keywords: ['select', 'cursor', 'pointer'],
    icon: '↖️',
    shortcut: 'V',
    enabled: true,
    visible: true,
  },
  {
    id: 'hand-tool',
    title: 'Hand Tool',
    description: 'Activate the hand panning tool',
    category: 'tools',
    keywords: ['hand', 'pan', 'move'],
    icon: '✋',
    shortcut: 'H',
    enabled: true,
    visible: true,
  },
  // Settings
  {
    id: 'preferences',
    title: 'Preferences',
    description: 'Open application preferences',
    category: 'settings',
    keywords: ['preferences', 'settings', 'options'],
    icon: '⚙️',
    shortcut: 'Ctrl + ,',
    enabled: true,
    visible: true,
  },
  {
    id: 'accessibility-settings',
    title: 'Accessibility Settings',
    description: 'Configure accessibility options',
    category: 'settings',
    keywords: ['accessibility', 'a11y', 'settings'],
    icon: '♿',
    enabled: true,
    visible: true,
  },
  // Help
  {
    id: 'keyboard-shortcuts',
    title: 'Keyboard Shortcuts',
    description: 'View keyboard shortcuts',
    category: 'help',
    keywords: ['shortcuts', 'keys', 'help'],
    icon: '⌨️',
    shortcut: 'F1',
    enabled: true,
    visible: true,
  },
  {
    id: 'about',
    title: 'About',
    description: 'About this application',
    category: 'help',
    keywords: ['about', 'version', 'info'],
    icon: 'ℹ️',
    enabled: true,
    visible: true,
  },
];

export const createDefaultCategories = (): CommandCategory[] => [
  {
    id: 'file',
    label: 'File',
    icon: '📁',
    priority: 10,
  },
  {
    id: 'edit',
    label: 'Edit',
    icon: '✏️',
    priority: 20,
  },
  {
    id: 'view',
    label: 'View',
    icon: '👁️',
    priority: 30,
  },
  {
    id: 'tools',
    label: 'Tools',
    icon: '🔧',
    priority: 40,
  },
  {
    id: 'canvas',
    label: 'Canvas',
    icon: '🎨',
    priority: 50,
  },
  {
    id: 'settings',
    label: 'Settings',
    icon: '⚙️',
    priority: 60,
  },
  {
    id: 'help',
    label: 'Help',
    icon: '❓',
    priority: 70,
  },
  {
    id: 'system',
    label: 'System',
    icon: '💻',
    priority: 80,
  },
];

// UX metrics utilities
export const createUXMetric = (
  userId: string | undefined,
  sessionId: string,
  eventType: UXMetrics['event']['type'],
  eventData: Record<string, unknown>,
  context?: string
): UXMetrics => ({
  id: `ux_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  userId,
  sessionId,
  timestamp: new Date().toISOString(),
  event: {
    type: eventType,
    data: eventData,
    context,
  },
});

// Validation utilities
export const validateAccessibilityConfig = (
  config: unknown
): AccessibilityConfig | null => {
  try {
    return AccessibilityConfigSchema.parse(config);
  } catch {
    return null;
  }
};

// Keyboard shortcuts utilities
export const formatShortcutKeys = (keys: string[]): string => {
  return keys
    .map((key) => {
      switch (key.toLowerCase()) {
        case 'ctrl':
        case 'cmd':
          return navigator.platform.indexOf('Mac') > -1 ? '⌘' : 'Ctrl';
        case 'shift':
          return '⇧';
        case 'alt':
          return navigator.platform.indexOf('Mac') > -1 ? '⌥' : 'Alt';
        case 'meta':
          return '⌘';
        default:
          return key.toUpperCase();
      }
    })
    .join(' + ');
};

export const isShortcutConflict = (
  shortcut1: KeyboardShortcut,
  shortcut2: KeyboardShortcut
): boolean => {
  if (shortcut1.context !== shortcut2.context) return false;
  if (shortcut1.keys.length !== shortcut2.keys.length) return false;

  const keys1 = shortcut1.keys.map((k) => k.toLowerCase()).sort();
  const keys2 = shortcut2.keys.map((k) => k.toLowerCase()).sort();

  return keys1.every((key, index) => key === keys2[index]);
};

export const findShortcutConflicts = (
  shortcuts: KeyboardShortcut[]
): KeyboardShortcut[][] => {
  const conflicts: KeyboardShortcut[][] = [];

  for (let i = 0; i < shortcuts.length; i++) {
    for (let j = i + 1; j < shortcuts.length; j++) {
      if (isShortcutConflict(shortcuts[i], shortcuts[j])) {
        conflicts.push([shortcuts[i], shortcuts[j]]);
      }
    }
  }

  return conflicts;
};

// Accessibility utilities
export const createAccessibilityAnnouncement = (
  message: string,
  priority: 'polite' | 'assertive' = 'polite'
): AccessibilityAnnouncement => ({
  id: `announcement_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  message,
  priority,
  timestamp: new Date().toISOString(),
});

// Focus management utilities
export const getFocusableElements = (container: HTMLElement): HTMLElement[] => {
  const focusableSelectors = [
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    'a[href]',
    '[tabindex]:not([tabindex="-1"])',
    '[contenteditable="true"]',
  ].join(', ');

  return Array.from(container.querySelectorAll(focusableSelectors));
};

export const getNextFocusableElement = (
  currentElement: HTMLElement,
  container: HTMLElement,
  direction: 'forward' | 'backward' = 'forward'
): HTMLElement | null => {
  const focusableElements = getFocusableElements(container);
  const currentIndex = focusableElements.indexOf(currentElement);

  if (currentIndex === -1) return focusableElements[0] || null;

  if (direction === 'forward') {
    return focusableElements[currentIndex + 1] || focusableElements[0] || null;
  } else {
    return (
      focusableElements[currentIndex - 1] ||
      focusableElements[focusableElements.length - 1] ||
      null
    );
  }
};

// Schema validation helpers
export const isValidCommand = (command: unknown): command is Command => {
  try {
    CommandSchema.parse(command);
    return true;
  } catch {
    return false;
  }
};

export const isValidKeyboardShortcut = (
  shortcut: unknown
): shortcut is KeyboardShortcut => {
  try {
    KeyboardShortcutSchema.parse(shortcut);
    return true;
  } catch {
    return false;
  }
};

export const isValidUserPreferences = (
  preferences: unknown
): preferences is UserPreferences => {
  try {
    UserPreferencesSchema.parse(preferences);
    return true;
  } catch {
    return false;
  }
};
