import React, {
  useState,
  useCallback,
  useRef,
  useMemo,
  useEffect,
} from 'react';

import { CanvasData } from '../schemas/canvas-schemas';

// Command palette types
/**
 *
 */
export interface Command {
  id: string;
  title: string;
  description?: string;
  category: string;
  keywords: string[];
  shortcut?: string;
  icon?: string;
  action: () => void | Promise<void>;
  enabled?: boolean;
  visible?: boolean;
}

/**
 *
 */
export interface CommandCategory {
  id: string;
  label: string;
  icon?: string;
  priority: number;
}

// Accessibility types
/**
 *
 */
export interface AccessibilityConfig {
  enableScreenReader: boolean;
  enableKeyboardNavigation: boolean;
  enableHighContrast: boolean;
  enableReducedMotion: boolean;
  fontSize: 'small' | 'medium' | 'large' | 'extra-large';
  colorScheme: 'light' | 'dark' | 'auto';
  announceChanges: boolean;
  focusManagement: boolean;
}

/**
 *
 */
export interface AccessibilityAnnouncement {
  id: string;
  message: string;
  priority: 'polite' | 'assertive';
  timestamp: string;
}

// Keyboard shortcut types
/**
 *
 */
export interface KeyboardShortcut {
  id: string;
  keys: string[];
  description: string;
  category: string;
  action: (event: KeyboardEvent) => void;
  preventDefault?: boolean;
  enabled?: boolean;
  context?: 'global' | 'canvas' | 'dialog';
}

// Advanced UX hooks
/**
 *
 */
export interface UseCommandPaletteConfig {
  commands?: Command[];
  categories?: CommandCategory[];
  maxResults?: number;
  searchThreshold?: number;
  enableCategories?: boolean;
}

/**
 *
 */
export interface UseCommandPaletteReturn {
  // State
  isOpen: boolean;
  searchQuery: string;
  selectedIndex: number;
  filteredCommands: Command[];

  // Actions
  open: () => void;
  close: () => void;
  toggle: () => void;
  setSearchQuery: (query: string) => void;
  selectNext: () => void;
  selectPrevious: () => void;
  executeSelected: () => void;
  executeCommand: (commandId: string) => void;

  // Registration
  registerCommand: (command: Command) => void;
  unregisterCommand: (commandId: string) => void;
  registerCategory: (category: CommandCategory) => void;
}

export const useCommandPalette = ({
  commands: initialCommands = [],
  categories: initialCategories = [],
  maxResults = 50,
  searchThreshold = 0.3,
  enableCategories = true,
}: UseCommandPaletteConfig = {}): UseCommandPaletteReturn => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [commands, setCommands] = useState<Command[]>(initialCommands);
  const [categories, setCategories] =
    useState<CommandCategory[]>(initialCategories);

  // Filter and search commands
  const filteredCommands = useMemo(() => {
    let filtered = commands.filter(
      (cmd) => cmd.enabled !== false && cmd.visible !== false
    );

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter((cmd) => {
        const searchText = [
          cmd.title,
          cmd.description || '',
          cmd.category,
          ...cmd.keywords,
        ]
          .join(' ')
          .toLowerCase();

        return searchText.includes(query);
      });

      // Sort by relevance
      filtered.sort((a, b) => {
        const aScore = calculateRelevanceScore(a, query);
        const bScore = calculateRelevanceScore(b, query);
        return bScore - aScore;
      });
    }

    // Group by category if enabled
    if (enableCategories) {
      filtered.sort((a, b) => {
        const aCat = categories.find((cat) => cat.id === a.category);
        const bCat = categories.find((cat) => cat.id === b.category);
        const aPriority = aCat?.priority || 999;
        const bPriority = bCat?.priority || 999;

        if (aPriority !== bPriority) {
          return aPriority - bPriority;
        }

        return a.title.localeCompare(b.title);
      });
    }

    return filtered.slice(0, maxResults);
  }, [commands, searchQuery, categories, enableCategories, maxResults]);

  const calculateRelevanceScore = (command: Command, query: string): number => {
    let score = 0;

    // Title exact match
    if (command.title.toLowerCase().includes(query)) {
      score += 10;
    }

    // Title starts with query
    if (command.title.toLowerCase().startsWith(query)) {
      score += 5;
    }

    // Description match
    if (command.description?.toLowerCase().includes(query)) {
      score += 3;
    }

    // Keyword match
    command.keywords.forEach((keyword) => {
      if (keyword.toLowerCase().includes(query)) {
        score += 2;
      }
    });

    return score;
  };

  const open = useCallback(() => {
    setIsOpen(true);
    setSearchQuery('');
    setSelectedIndex(0);
  }, []);

  const close = useCallback(() => {
    setIsOpen(false);
    setSearchQuery('');
    setSelectedIndex(0);
  }, []);

  const toggle = useCallback(() => {
    if (isOpen) {
      close();
    } else {
      open();
    }
  }, [isOpen, open, close]);

  const selectNext = useCallback(() => {
    setSelectedIndex((prev) =>
      prev < filteredCommands.length - 1 ? prev + 1 : 0
    );
  }, [filteredCommands.length]);

  const selectPrevious = useCallback(() => {
    setSelectedIndex((prev) =>
      prev > 0 ? prev - 1 : filteredCommands.length - 1
    );
  }, [filteredCommands.length]);

  const executeSelected = useCallback(async () => {
    const selectedCommand = filteredCommands[selectedIndex];
    if (selectedCommand) {
      try {
        await selectedCommand.action();
        close();
      } catch (error) {
        console.error('Command execution failed:', error);
      }
    }
  }, [filteredCommands, selectedIndex, close]);

  const executeCommand = useCallback(
    async (commandId: string) => {
      const command = commands.find((cmd) => cmd.id === commandId);
      if (command) {
        try {
          await command.action();
          close();
        } catch (error) {
          console.error('Command execution failed:', error);
        }
      }
    },
    [commands, close]
  );

  const registerCommand = useCallback((command: Command) => {
    setCommands((prev) => {
      const existing = prev.findIndex((cmd) => cmd.id === command.id);
      if (existing >= 0) {
        const updated = [...prev];
        updated[existing] = command;
        return updated;
      }
      return [...prev, command];
    });
  }, []);

  const unregisterCommand = useCallback((commandId: string) => {
    setCommands((prev) => prev.filter((cmd) => cmd.id !== commandId));
  }, []);

  const registerCategory = useCallback((category: CommandCategory) => {
    setCategories((prev) => {
      const existing = prev.findIndex((cat) => cat.id === category.id);
      if (existing >= 0) {
        const updated = [...prev];
        updated[existing] = category;
        return updated;
      }
      return [...prev, category];
    });
  }, []);

  // Reset selected index when filtered commands change
  useEffect(() => {
    setSelectedIndex(0);
    return undefined;
  }, [filteredCommands]);

  return {
    isOpen,
    searchQuery,
    selectedIndex,
    filteredCommands,
    open,
    close,
    toggle,
    setSearchQuery,
    selectNext,
    selectPrevious,
    executeSelected,
    executeCommand,
    registerCommand,
    unregisterCommand,
    registerCategory,
  };
};

// Accessibility hook
/**
 *
 */
export interface UseAccessibilityConfig {
  initialConfig?: Partial<AccessibilityConfig>;
  announceNavigation?: boolean;
  manageFocus?: boolean;
  enableAriaLive?: boolean;
}

/**
 *
 */
export interface UseAccessibilityReturn {
  // Configuration
  config: AccessibilityConfig;
  updateConfig: (updates: Partial<AccessibilityConfig>) => void;
  resetConfig: () => void;

  // Announcements
  announce: (message: string, priority?: 'polite' | 'assertive') => void;
  announcements: AccessibilityAnnouncement[];
  clearAnnouncements: () => void;

  // Focus management
  focusElement: (selector: string | HTMLElement) => void;
  focusNext: () => void;
  focusPrevious: () => void;
  createFocusTrap: (container: HTMLElement) => () => void;

  // ARIA utilities
  setAriaLabel: (element: HTMLElement, label: string) => void;
  setAriaDescribedBy: (element: HTMLElement, id: string) => void;
  setAriaExpanded: (element: HTMLElement, expanded: boolean) => void;
  setAriaSelected: (element: HTMLElement, selected: boolean) => void;

  // Keyboard navigation
  handleKeyboardNavigation: (
    event: KeyboardEvent,
    options?: {
      onEnter?: () => void;
      onEscape?: () => void;
      onArrowUp?: () => void;
      onArrowDown?: () => void;
      onArrowLeft?: () => void;
      onArrowRight?: () => void;
    }
  ) => void;
}

const defaultAccessibilityConfig: AccessibilityConfig = {
  enableScreenReader: true,
  enableKeyboardNavigation: true,
  enableHighContrast: false,
  enableReducedMotion: false,
  fontSize: 'medium',
  colorScheme: 'auto',
  announceChanges: true,
  focusManagement: true,
};

export const useAccessibility = ({
  initialConfig = {},
  announceNavigation = true,
  manageFocus = true,
  enableAriaLive = true,
}: UseAccessibilityConfig = {}): UseAccessibilityReturn => {
  const [config, setConfig] = useState<AccessibilityConfig>({
    ...defaultAccessibilityConfig,
    ...initialConfig,
  });
  const [announcements, setAnnouncements] = useState<
    AccessibilityAnnouncement[]
  >([]);

  const ariaLiveRef = useRef<HTMLElement | null>(null);

  // Create ARIA live region
  useEffect(() => {
    if (enableAriaLive && typeof document !== 'undefined') {
      const liveRegion = document.createElement('div');
      liveRegion.setAttribute('aria-live', 'polite');
      liveRegion.setAttribute('aria-atomic', 'true');
      liveRegion.style.position = 'absolute';
      liveRegion.style.left = '-10000px';
      liveRegion.style.width = '1px';
      liveRegion.style.height = '1px';
      liveRegion.style.overflow = 'hidden';

      document.body.appendChild(liveRegion);
      ariaLiveRef.current = liveRegion;

      return () => {
        if (liveRegion.parentNode) {
          liveRegion.parentNode.removeChild(liveRegion);
        }
      };
    }
    return undefined;
  }, [enableAriaLive]);

  const updateConfig = useCallback((updates: Partial<AccessibilityConfig>) => {
    setConfig((prev) => ({ ...prev, ...updates }));
  }, []);

  const resetConfig = useCallback(() => {
    setConfig({ ...defaultAccessibilityConfig, ...initialConfig });
  }, [initialConfig]);

  const announce = useCallback(
    (message: string, priority: 'polite' | 'assertive' = 'polite') => {
      if (!config.announceChanges) return;

      const announcement: AccessibilityAnnouncement = {
        id: `announcement-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        message,
        priority,
        timestamp: new Date().toISOString(),
      };

      setAnnouncements((prev) => [...prev, announcement]);

      // Announce via ARIA live region
      if (ariaLiveRef.current) {
        ariaLiveRef.current.setAttribute('aria-live', priority);
        ariaLiveRef.current.textContent = message;

        // Clear after announcement
        setTimeout(() => {
          if (ariaLiveRef.current) {
            ariaLiveRef.current.textContent = '';
          }
        }, 1000);
      }

      // Remove old announcements
      setTimeout(() => {
        setAnnouncements((prev) =>
          prev.filter((a) => a.id !== announcement.id)
        );
      }, 5000);
    },
    [config.announceChanges]
  );

  const clearAnnouncements = useCallback(() => {
    setAnnouncements([]);
  }, []);

  const focusElement = useCallback(
    (selector: string | HTMLElement) => {
      if (!config.focusManagement) return;

      let element: HTMLElement | null = null;

      if (typeof selector === 'string') {
        element = document.querySelector(selector);
      } else {
        element = selector;
      }

      if (element && typeof element.focus === 'function') {
        element.focus();

        if (announceNavigation) {
          const label =
            element.getAttribute('aria-label') ||
            element.getAttribute('title') ||
            element.textContent ||
            'Element focused';
          announce(`Focused on ${label}`);
        }
      }
    },
    [config.focusManagement, announceNavigation, announce]
  );

  const focusNext = useCallback(() => {
    const focusableElements = document.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    const currentIndex = Array.from(focusableElements).indexOf(
      document.activeElement as HTMLElement
    );
    const nextIndex = (currentIndex + 1) % focusableElements.length;

    focusElement(focusableElements[nextIndex] as HTMLElement);
  }, [focusElement]);

  const focusPrevious = useCallback(() => {
    const focusableElements = document.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    const currentIndex = Array.from(focusableElements).indexOf(
      document.activeElement as HTMLElement
    );
    const prevIndex =
      currentIndex <= 0 ? focusableElements.length - 1 : currentIndex - 1;

    focusElement(focusableElements[prevIndex] as HTMLElement);
  }, [focusElement]);

  const createFocusTrap = useCallback((container: HTMLElement) => {
    const focusableElements = container.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );

    if (focusableElements.length === 0) return () => {};

    const firstElement = focusableElements[0] as HTMLElement;
    const lastElement = focusableElements[
      focusableElements.length - 1
    ] as HTMLElement;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Tab') {
        if (event.shiftKey) {
          if (document.activeElement === firstElement) {
            event.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            event.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    container.addEventListener('keydown', handleKeyDown);
    firstElement.focus();

    return () => {
      container.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const setAriaLabel = useCallback((element: HTMLElement, label: string) => {
    element.setAttribute('aria-label', label);
  }, []);

  const setAriaDescribedBy = useCallback((element: HTMLElement, id: string) => {
    element.setAttribute('aria-describedby', id);
  }, []);

  const setAriaExpanded = useCallback(
    (element: HTMLElement, expanded: boolean) => {
      element.setAttribute('aria-expanded', expanded.toString());
    },
    []
  );

  const setAriaSelected = useCallback(
    (element: HTMLElement, selected: boolean) => {
      element.setAttribute('aria-selected', selected.toString());
    },
    []
  );

  const handleKeyboardNavigation = useCallback(
    (
      event: KeyboardEvent,
      options: {
        onEnter?: () => void;
        onEscape?: () => void;
        onArrowUp?: () => void;
        onArrowDown?: () => void;
        onArrowLeft?: () => void;
        onArrowRight?: () => void;
      } = {}
    ) => {
      if (!config.enableKeyboardNavigation) return;

      switch (event.key) {
        case 'Enter':
          options.onEnter?.();
          break;
        case 'Escape':
          options.onEscape?.();
          break;
        case 'ArrowUp':
          event.preventDefault();
          options.onArrowUp?.();
          break;
        case 'ArrowDown':
          event.preventDefault();
          options.onArrowDown?.();
          break;
        case 'ArrowLeft':
          event.preventDefault();
          options.onArrowLeft?.();
          break;
        case 'ArrowRight':
          event.preventDefault();
          options.onArrowRight?.();
          break;
      }
    },
    [config.enableKeyboardNavigation]
  );

  return {
    config,
    updateConfig,
    resetConfig,
    announce,
    announcements,
    clearAnnouncements,
    focusElement,
    focusNext,
    focusPrevious,
    createFocusTrap,
    setAriaLabel,
    setAriaDescribedBy,
    setAriaExpanded,
    setAriaSelected,
    handleKeyboardNavigation,
  };
};

// Keyboard shortcuts hook
/**
 *
 */
export interface UseKeyboardShortcutsConfig {
  shortcuts?: KeyboardShortcut[];
  enabled?: boolean;
  context?: 'global' | 'canvas' | 'dialog';
}

/**
 *
 */
export interface UseKeyboardShortcutsReturn {
  // Registration
  registerShortcut: (shortcut: KeyboardShortcut) => void;
  unregisterShortcut: (shortcutId: string) => void;
  updateShortcut: (
    shortcutId: string,
    updates: Partial<KeyboardShortcut>
  ) => void;

  // State
  shortcuts: KeyboardShortcut[];
  activeContext: string;

  // Control
  setContext: (context: 'global' | 'canvas' | 'dialog') => void;
  enable: () => void;
  disable: () => void;
  isEnabled: boolean;

  // Utilities
  getShortcutsByCategory: (category: string) => KeyboardShortcut[];
  getShortcutString: (keys: string[]) => string;
}

export const useKeyboardShortcuts = ({
  shortcuts: initialShortcuts = [],
  enabled = true,
  context = 'global',
}: UseKeyboardShortcutsConfig = {}): UseKeyboardShortcutsReturn => {
  const [shortcuts, setShortcuts] =
    useState<KeyboardShortcut[]>(initialShortcuts);
  const [isEnabled, setIsEnabled] = useState(enabled);
  const [activeContext, setActiveContext] = useState(context);

  const pressedKeys = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (!isEnabled) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      pressedKeys.current.add(event.key.toLowerCase());

      // Check for matching shortcuts
      const matchingShortcuts = shortcuts.filter((shortcut) => {
        if (shortcut.enabled === false) return false;
        if (shortcut.context && shortcut.context !== activeContext)
          return false;

        return shortcut.keys.every((key) =>
          pressedKeys.current.has(key.toLowerCase())
        );
      });

      // Execute the first matching shortcut
      if (matchingShortcuts.length > 0) {
        const shortcut = matchingShortcuts[0];

        if (shortcut.preventDefault !== false) {
          event.preventDefault();
        }

        try {
          shortcut.action(event);
        } catch (error) {
          console.error('Shortcut execution failed:', error);
        }
      }
    };

    const handleKeyUp = (event: KeyboardEvent) => {
      pressedKeys.current.delete(event.key.toLowerCase());
    };

    const handleBlur = () => {
      pressedKeys.current.clear();
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('keyup', handleKeyUp);
    window.addEventListener('blur', handleBlur);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('keyup', handleKeyUp);
      window.removeEventListener('blur', handleBlur);
    };
  }, [shortcuts, isEnabled, activeContext]);

  const registerShortcut = useCallback((shortcut: KeyboardShortcut) => {
    setShortcuts((prev) => {
      const existing = prev.findIndex((s) => s.id === shortcut.id);
      if (existing >= 0) {
        const updated = [...prev];
        updated[existing] = shortcut;
        return updated;
      }
      return [...prev, shortcut];
    });
  }, []);

  const unregisterShortcut = useCallback((shortcutId: string) => {
    setShortcuts((prev) => prev.filter((s) => s.id !== shortcutId));
  }, []);

  const updateShortcut = useCallback(
    (shortcutId: string, updates: Partial<KeyboardShortcut>) => {
      setShortcuts((prev) =>
        prev.map((shortcut) =>
          shortcut.id === shortcutId ? { ...shortcut, ...updates } : shortcut
        )
      );
    },
    []
  );

  const setContext = useCallback(
    (newContext: 'global' | 'canvas' | 'dialog') => {
      setActiveContext(newContext);
    },
    []
  );

  const enable = useCallback(() => {
    setIsEnabled(true);
  }, []);

  const disable = useCallback(() => {
    setIsEnabled(false);
  }, []);

  const getShortcutsByCategory = useCallback(
    (category: string) => {
      return shortcuts.filter((shortcut) => shortcut.category === category);
    },
    [shortcuts]
  );

  const getShortcutString = useCallback((keys: string[]) => {
    return keys
      .map((key) => {
        switch (key.toLowerCase()) {
          case 'meta':
            return '⌘';
          case 'ctrl':
            return 'Ctrl';
          case 'alt':
            return 'Alt';
          case 'shift':
            return 'Shift';
          case ' ':
            return 'Space';
          case 'arrowup':
            return '↑';
          case 'arrowdown':
            return '↓';
          case 'arrowleft':
            return '←';
          case 'arrowright':
            return '→';
          default:
            return key.toUpperCase();
        }
      })
      .join(' + ');
  }, []);

  return {
    registerShortcut,
    unregisterShortcut,
    updateShortcut,
    shortcuts,
    activeContext,
    setContext,
    enable,
    disable,
    isEnabled,
    getShortcutsByCategory,
    getShortcutString,
  };
};
