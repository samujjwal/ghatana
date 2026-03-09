/**
 * @ghatana/yappc-ide - Keyboard Navigation Hook
 * 
 * Comprehensive keyboard navigation system with shortcuts,
 * focus management, and accessibility features.
 * 
 * @doc.type hook
 * @doc.purpose Keyboard navigation for IDE accessibility
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect, useCallback, useRef, useState } from 'react';

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  altKey?: boolean;
  shiftKey?: boolean;
  metaKey?: boolean;
  description: string;
  action: () => void;
  category?: 'file' | 'edit' | 'view' | 'navigation' | 'collaboration' | 'help';
}

/**
 * Focus management options
 */
export interface FocusOptions {
  trapFocus?: boolean;
  restoreFocus?: boolean;
  initialFocus?: HTMLElement | string;
}

/**
 * Keyboard navigation state
 */
interface KeyboardNavigationState {
  isKeyboardUser: boolean;
  currentFocus: HTMLElement | null;
  shortcuts: Map<string, KeyboardShortcut>;
}

/**
 * Hook for keyboard navigation
 */
export const useKeyboardNavigation = () => {
  const [state, setState] = useState<KeyboardNavigationState>({
    isKeyboardUser: false,
    currentFocus: null,
    shortcuts: new Map(),
  });

  const previousFocusRef = useRef<HTMLElement | null>(null);
  const containerRef = useRef<HTMLElement | null>(null);

  /**
   * Detect if user is navigating with keyboard
   */
  const detectKeyboardUser = useCallback(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Tab') {
        setState(prev => ({ ...prev, isKeyboardUser: true }));
      }
    };

    const handleMouseDown = () => {
      setState(prev => ({ ...prev, isKeyboardUser: false }));
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handleMouseDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleMouseDown);
    };
  }, []);

  /**
   * Register keyboard shortcut
   */
  const registerShortcut = useCallback((shortcut: KeyboardShortcut) => {
    const key = [
      shortcut.ctrlKey && 'ctrl',
      shortcut.altKey && 'alt',
      shortcut.shiftKey && 'shift',
      shortcut.metaKey && 'meta',
      shortcut.key.toLowerCase(),
    ].filter(Boolean).join('+');

    setState(prev => ({
      ...prev,
      shortcuts: new Map(prev.shortcuts).set(key, shortcut),
    }));

    return () => {
      setState(prev => {
        const newShortcuts = new Map(prev.shortcuts);
        newShortcuts.delete(key);
        return { ...prev, shortcuts: newShortcuts };
      });
    };
  }, []);

  /**
   * Handle keyboard shortcuts
   */
  const handleShortcuts = useCallback((e: KeyboardEvent) => {
    const key = [
      e.ctrlKey && 'ctrl',
      e.altKey && 'alt',
      e.shiftKey && 'shift',
      e.metaKey && 'meta',
      e.key.toLowerCase(),
    ].filter(Boolean).join('+');

    const shortcut = state.shortcuts.get(key);
    if (shortcut) {
      e.preventDefault();
      e.stopPropagation();
      shortcut.action();
    }
  }, [state.shortcuts]);

  /**
   * Focus management
   */
  const focusManagement = {
    /**
     * Trap focus within a container
     */
    trapFocus: useCallback((container: HTMLElement) => {
      containerRef.current = container;
      
      const focusableElements = container.querySelectorAll(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      ) as NodeListOf<HTMLElement>;

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      const handleTabKey = (e: KeyboardEvent) => {
        if (e.key !== 'Tab') return;

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            lastElement.focus();
            e.preventDefault();
          }
        } else {
          if (document.activeElement === lastElement) {
            firstElement.focus();
            e.preventDefault();
          }
        }
      };

      container.addEventListener('keydown', handleTabKey);
      
      return () => {
        container.removeEventListener('keydown', handleTabKey);
      };
    }, []),

    /**
     * Save current focus for restoration
     */
    saveFocus: useCallback(() => {
      previousFocusRef.current = document.activeElement as HTMLElement;
    }, []),

    /**
     * Restore previously saved focus
     */
    restoreFocus: useCallback(() => {
      if (previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
        previousFocusRef.current.focus();
      }
    }, []),

    /**
     * Set focus to element
     */
    setFocus: useCallback((element: HTMLElement | string) => {
      const el = typeof element === 'string' 
        ? document.querySelector(element) as HTMLElement
        : element;
      
      if (el && typeof el.focus === 'function') {
        el.focus();
      }
    }, []),

    /**
     * Get next focusable element
     */
    getNextFocusable: useCallback((current: HTMLElement, direction: 'next' | 'previous' = 'next') => {
      const focusableElements = Array.from(
        document.querySelectorAll(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
      ) as HTMLElement[];

      const currentIndex = focusableElements.indexOf(current);
      
      if (currentIndex === -1) return null;

      if (direction === 'next') {
        return focusableElements[currentIndex + 1] || focusableElements[0];
      } else {
        return focusableElements[currentIndex - 1] || focusableElements[focusableElements.length - 1];
      }
    }, []),
  };

  /**
   * IDE-specific keyboard shortcuts
   */
  const ideShortcuts = {
    /**
     * File operations
     */
    file: {
      newFile: () => registerShortcut({
        key: 'n',
        ctrlKey: true,
        description: 'New File',
        action: () => console.log('New file'),
        category: 'file',
      }),
      
      openFile: () => registerShortcut({
        key: 'o',
        ctrlKey: true,
        description: 'Open File',
        action: () => console.log('Open file'),
        category: 'file',
      }),
      
      saveFile: () => registerShortcut({
        key: 's',
        ctrlKey: true,
        description: 'Save File',
        action: () => console.log('Save file'),
        category: 'file',
      }),
    },

    /**
     * Edit operations
     */
    edit: {
      undo: () => registerShortcut({
        key: 'z',
        ctrlKey: true,
        description: 'Undo',
        action: () => console.log('Undo'),
        category: 'edit',
      }),
      
      redo: () => registerShortcut({
        key: 'y',
        ctrlKey: true,
        description: 'Redo',
        action: () => console.log('Redo'),
        category: 'edit',
      }),
      
      find: () => registerShortcut({
        key: 'f',
        ctrlKey: true,
        description: 'Find',
        action: () => console.log('Find'),
        category: 'edit',
      }),
    },

    /**
     * Navigation
     */
    navigation: {
      goToLine: () => registerShortcut({
        key: 'g',
        ctrlKey: true,
        description: 'Go to Line',
        action: () => console.log('Go to line'),
        category: 'navigation',
      }),
      
      toggleSidebar: () => registerShortcut({
        key: 'b',
        ctrlKey: true,
        description: 'Toggle Sidebar',
        action: () => console.log('Toggle sidebar'),
        category: 'navigation',
      }),
    },

    /**
     * Collaboration
     */
    collaboration: {
      shareFile: () => registerShortcut({
        key: 's',
        ctrlKey: true,
        shiftKey: true,
        description: 'Share File',
        action: () => console.log('Share file'),
        category: 'collaboration',
      }),
      
      startCollaboration: () => registerShortcut({
        key: 'c',
        ctrlKey: true,
        shiftKey: true,
        description: 'Start Collaboration',
        action: () => console.log('Start collaboration'),
        category: 'collaboration',
      }),
    },
  };

  /**
   * Accessibility helpers
   */
  const accessibility = {
    /**
     * Announce to screen readers
     */
    announce: useCallback((message: string, priority: 'polite' | 'assertive' = 'polite') => {
      const announcement = document.createElement('div');
      announcement.setAttribute('aria-live', priority);
      announcement.setAttribute('aria-atomic', 'true');
      announcement.className = 'sr-only';
      announcement.textContent = message;

      document.body.appendChild(announcement);

      setTimeout(() => {
        document.body.removeChild(announcement);
      }, 1000);
    }, []),

    /**
     * Set ARIA attributes
     */
    setAria: useCallback((element: HTMLElement, attributes: Record<string, string>) => {
      Object.entries(attributes).forEach(([key, value]) => {
        element.setAttribute(key, value);
      });
    }, []),

    /**
     * Check if element is visible
     */
    isVisible: useCallback((element: HTMLElement): boolean => {
      return !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
    }, []),
  };

  /**
   * Focus trap hook for modals and dialogs
   */
  const useFocusTrap = (isOpen: boolean, options: FocusOptions = {}) => {
    const containerRef = useRef<HTMLElement>(null);

    useEffect(() => {
      if (!isOpen) return;

      const container = containerRef.current;
      if (!container) return;

      // Save previous focus
      if (options.restoreFocus) {
        focusManagement.saveFocus();
      }

      // Set initial focus
      if (options.initialFocus) {
        if (typeof options.initialFocus === 'string') {
          const element = container.querySelector(options.initialFocus) as HTMLElement;
          element?.focus();
        } else {
          options.initialFocus.focus();
        }
      } else {
        // Focus first focusable element
        const firstFocusable = container.querySelector(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        ) as HTMLElement;
        firstFocusable?.focus();
      }

      // Trap focus
      if (options.trapFocus) {
        return focusManagement.trapFocus(container);
      }
    }, [isOpen, options]);

    useEffect(() => {
      return () => {
        if (options.restoreFocus) {
          focusManagement.restoreFocus();
        }
      };
    }, [options.restoreFocus]);

    return containerRef;
  };

  /**
   * Keyboard navigation for lists
   */
  const useListNavigation = (
    items: unknown[],
    onSelect: (item: unknown, index: number) => void,
    options: {
      orientation?: 'vertical' | 'horizontal';
      loop?: boolean;
      disabled?: boolean;
    } = {}
  ) => {
    const [selectedIndex, setSelectedIndex] = useState(-1);
    const { orientation = 'vertical', loop = true, disabled = false } = options;

    const handleKeyDown = useCallback((e: KeyboardEvent) => {
      if (disabled) return;

      switch (e.key) {
        case 'ArrowUp':
        case 'ArrowLeft':
          e.preventDefault();
          setSelectedIndex(prev => {
            const newIndex = prev - 1;
            if (newIndex < 0) {
              return loop ? items.length - 1 : -1;
            }
            return newIndex;
          });
          break;

        case 'ArrowDown':
        case 'ArrowRight':
          e.preventDefault();
          setSelectedIndex(prev => {
            const newIndex = prev + 1;
            if (newIndex >= items.length) {
              return loop ? 0 : -1;
            }
            return newIndex;
          });
          break;

        case 'Enter':
        case ' ':
          e.preventDefault();
          if (selectedIndex >= 0 && selectedIndex < items.length) {
            onSelect(items[selectedIndex], selectedIndex);
          }
          break;

        case 'Escape':
          e.preventDefault();
          setSelectedIndex(-1);
          break;
      }
    }, [items, selectedIndex, onSelect, loop, disabled]);

    return {
      selectedIndex,
      setSelectedIndex,
      handleKeyDown,
      ariaProps: {
        role: 'listbox',
        'aria-orientation': orientation,
      },
    };
  };

  /**
   * Setup global keyboard listeners
   */
  useEffect(() => {
    const cleanup = detectKeyboardUser();
    document.addEventListener('keydown', handleShortcuts);
    
    return () => {
      cleanup();
      document.removeEventListener('keydown', handleShortcuts);
    };
  }, [detectKeyboardUser, handleShortcuts]);

  return {
    // State
    isKeyboardUser: state.isKeyboardUser,
    currentFocus: state.currentFocus,
    shortcuts: state.shortcuts,

    // Core functionality
    registerShortcut,
    focusManagement,
    accessibility,

    // IDE shortcuts
    ideShortcuts,

    // Hooks
    useFocusTrap,
    useListNavigation,
  };
};

/**
 * Hook for roving tabindex
 */
export const useRovingTabIndex = (items: HTMLElement[]) => {
  const [activeIndex, setActiveIndex] = useState(-1);

  const setTabIndex = useCallback((element: HTMLElement, index: number) => {
    element.tabIndex = index;
  }, []);

  useEffect(() => {
    items.forEach((item, index) => {
      setTabIndex(item, index === activeIndex ? 0 : -1);
    });
  }, [items, activeIndex, setTabIndex]);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setActiveIndex(prev => (prev + 1) % items.length);
        break;
      case 'ArrowUp':
        e.preventDefault();
        setActiveIndex(prev => (prev - 1 + items.length) % items.length);
        break;
      case 'Home':
        e.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        e.preventDefault();
        setActiveIndex(items.length - 1);
        break;
    }
  }, [items.length]);

  return {
    activeIndex,
    setActiveIndex,
    handleKeyDown,
  };
};

export default useKeyboardNavigation;
