/**
 * @ghatana/yappc-ide - Keyboard Navigation Component
 * 
 * Advanced keyboard navigation system with shortcuts, focus management,
 * and screen reader support for full accessibility.
 * 
 * @doc.type component
 * @doc.purpose Enhanced keyboard navigation for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { InteractiveButton } from './MicroInteractions';

/**
 * Keyboard navigation item
 */
export interface NavigationItem {
  id: string;
  label: string;
  description?: string;
  element?: HTMLElement;
  selector?: string;
  shortcut?: string;
  category: string;
  enabled: boolean;
  visible: boolean;
  focusable: boolean;
}

/**
 * Focus trap configuration
 */
export interface FocusTrapConfig {
  enabled: boolean;
  restoreFocus: boolean;
  escapeKey: boolean;
  initialFocus?: string;
}

/**
 * Keyboard navigation props
 */
export interface KeyboardNavigationProps {
  className?: string;
  enableFocusManagement?: boolean;
  enableShortcuts?: boolean;
  enableScreenReader?: boolean;
  enableFocusTrap?: boolean;
  focusTrap?: FocusTrapConfig;
  onNavigationChange?: (item: NavigationItem) => void;
  onShortcutTriggered?: (shortcut: string, item: NavigationItem) => void;
}

/**
 * Keyboard navigation manager class
 */
class KeyboardNavigationManager {
  private navigationItems: Map<string, NavigationItem> = new Map();
  private currentIndex = 0;
  private focusHistory: HTMLElement[] = [];
  private shortcuts: Map<string, string[]> = new Map();
  private observers: MutationObserver[] = [];

  registerItem(item: NavigationItem): void {
    this.navigationItems.set(item.id, item);

    // Register shortcut if provided
    if (item.shortcut) {
      const keys = this.parseShortcut(item.shortcut);
      this.shortcuts.set(item.id, keys);
    }
  }

  unregisterItem(itemId: string): void {
    this.navigationItems.delete(itemId);
    this.shortcuts.delete(itemId);
  }

  updateItem(itemId: string, updates: Partial<NavigationItem>): void {
    const item = this.navigationItems.get(itemId);
    if (item) {
      this.navigationItems.set(itemId, { ...item, ...updates });
    }
  }

  getVisibleItems(): NavigationItem[] {
    return Array.from(this.navigationItems.values())
      .filter(item => item.visible && item.enabled && item.focusable);
  }

  navigateNext(): NavigationItem | null {
    const visibleItems = this.getVisibleItems();
    if (visibleItems.length === 0) return null;

    this.currentIndex = (this.currentIndex + 1) % visibleItems.length;
    return visibleItems[this.currentIndex];
  }

  navigatePrevious(): NavigationItem | null {
    const visibleItems = this.getVisibleItems();
    if (visibleItems.length === 0) return null;

    this.currentIndex = this.currentIndex === 0 ? visibleItems.length - 1 : this.currentIndex - 1;
    return visibleItems[this.currentIndex];
  }

  navigateToItem(itemId: string): NavigationItem | null {
    const item = this.navigationItems.get(itemId);
    if (!item || !item.visible || !item.enabled) return null;

    const visibleItems = this.getVisibleItems();
    this.currentIndex = visibleItems.findIndex(i => i.id === itemId);
    return item;
  }

  focusItem(item: NavigationItem): void {
    if (item.element) {
      this.saveFocusHistory();
      item.element.focus();
    } else if (item.selector) {
      const element = document.querySelector(item.selector) as HTMLElement;
      if (element) {
        this.saveFocusHistory();
        element.focus();
      }
    }
  }

  private saveFocusHistory(): void {
    const activeElement = document.activeElement as HTMLElement;
    if (activeElement && activeElement !== document.body) {
      this.focusHistory.push(activeElement);
      // Keep only last 10 elements
      if (this.focusHistory.length > 10) {
        this.focusHistory.shift();
      }
    }
  }

  restoreFocus(): void {
    if (this.focusHistory.length > 0) {
      const previousElement = this.focusHistory.pop();
      if (previousElement) {
        previousElement.focus();
      }
    }
  }

  parseShortcut(shortcut: string): string[] {
    return shortcut.toLowerCase().split('+').map(key => key.trim());
  }

  getItemsByCategory(category: string): NavigationItem[] {
    return Array.from(this.navigationItems.values())
      .filter(item => item.category === category);
  }

  startAutoDiscovery(): void {
    // Automatically discover focusable elements
    const observer = new MutationObserver(() => {
      this.discoverElements();
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['tabindex', 'disabled', 'aria-hidden'],
    });

    this.observers.push(observer);
    this.discoverElements();
  }

  stopAutoDiscovery(): void {
    this.observers.forEach(observer => observer.disconnect());
    this.observers = [];
  }

  private discoverElements(): void {
    const focusableSelectors = [
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      'a[href]',
      '[tabindex]:not([tabindex="-1"])',
      '[contenteditable="true"]',
    ];

    const elements = document.querySelectorAll(focusableSelectors.join(', '));

    elements.forEach((element, index) => {
      const htmlElement = element as HTMLElement;
      const id = `auto-discovered-${index}`;

      if (!this.navigationItems.has(id)) {
        const label = this.getElementLabel(htmlElement);
        const category = this.getElementCategory(htmlElement);

        this.registerItem({
          id,
          label,
          category,
          element: htmlElement,
          enabled: !(htmlElement as HTMLInputElement).disabled,
          visible: htmlElement.offsetParent !== null,
          focusable: true,
        });
      }
    });
  }

  private getElementLabel(element: HTMLElement): string {
    // Try various methods to get element label
    if (element.getAttribute('aria-label')) {
      return element.getAttribute('aria-label')!;
    }

    if (element.getAttribute('aria-labelledby')) {
      const labelId = element.getAttribute('aria-labelledby')!;
      const labelElement = document.getElementById(labelId);
      if (labelElement) {
        return labelElement.textContent || '';
      }
    }

    if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
      const placeholder = element.getAttribute('placeholder');
      if (placeholder) return placeholder;
    }

    if (element.tagName === 'BUTTON') {
      return element.textContent || 'Button';
    }

    if (element.tagName === 'A') {
      return element.textContent || 'Link';
    }

    return element.tagName.toLowerCase();
  }

  private getElementCategory(element: HTMLElement): string {
    const tagName = element.tagName.toLowerCase();

    if (['button', 'input[type="button"]', 'input[type="submit"]'].includes(tagName)) {
      return 'actions';
    }

    if (['input', 'select', 'textarea'].includes(tagName)) {
      return 'forms';
    }

    if (tagName === 'a') {
      return 'navigation';
    }

    if (element.hasAttribute('role')) {
      return element.getAttribute('role')!;
    }

    return 'interactive';
  }
}

/**
 * Keyboard Navigation Component
 */
export const KeyboardNavigation: React.FC<KeyboardNavigationProps> = ({
  className = '',
  enableFocusManagement = true,
  enableShortcuts = true,
  enableScreenReader = true,
  onNavigationChange,
  onShortcutTriggered,
}) => {
  const [navigationManager] = useState(() => new KeyboardNavigationManager());
  const [currentItem, setCurrentItem] = useState<NavigationItem | null>(null);
  const [showHelp, setShowHelp] = useState(false);
  const [pressedKeys, setPressedKeys] = useState<Set<string>>(new Set());

  const containerRef = useRef<HTMLDivElement>(null);
  const helpTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Callback functions
  const handleNavigationChange = useCallback((item: NavigationItem) => {
    setCurrentItem(item);
    onNavigationChange?.(item);
  }, [onNavigationChange]);

  const handleShortcutTriggered = useCallback((shortcut: string, item: NavigationItem) => {
    onShortcutTriggered?.(shortcut, item);
  }, [onShortcutTriggered]);

  // Register keyboard shortcuts
  useEffect(() => {
    if (!enableShortcuts) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const key = e.key.toLowerCase();
      const keys = new Set<string>();

      if (e.ctrlKey || e.metaKey) keys.add('ctrl');
      if (e.altKey) keys.add('alt');
      if (e.shiftKey) keys.add('shift');
      keys.add(key);

      setPressedKeys(keys);

      // Check for shortcuts
      const items = navigationManager.getVisibleItems();
      for (const item of items) {
        if (item.shortcut) {
          const shortcutKeys = navigationManager.parseShortcut(item.shortcut);
          if (ArraysMatch(Array.from(keys), shortcutKeys)) {
            e.preventDefault();
            onShortcutTriggered?.(item.shortcut, item);
            navigationManager.focusItem(item);
            break;
          }
        }
      }

      // Navigation shortcuts
      if (enableFocusManagement) {
        switch (e.key) {
          case 'Tab':
            if (e.shiftKey) {
              e.preventDefault();
              const prevItem = navigationManager.navigatePrevious();
              if (prevItem) {
                navigationManager.focusItem(prevItem);
                setCurrentItem(prevItem);
                onNavigationChange?.(prevItem);
              }
            } else {
              e.preventDefault();
              const nextItem = navigationManager.navigateNext();
              if (nextItem) {
                navigationManager.focusItem(nextItem);
                setCurrentItem(nextItem);
                onNavigationChange?.(nextItem);
              }
            }
            break;

          case '?':
            if (e.ctrlKey || e.metaKey) {
              e.preventDefault();
              setShowHelp(!showHelp);
            }
            break;
        }
      }
    };

    const handleKeyUp = () => {
      setPressedKeys(new Set());
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('keyup', handleKeyUp);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('keyup', handleKeyUp);
    };
  }, [enableShortcuts, enableFocusManagement, navigationManager, showHelp, handleNavigationChange, handleShortcutTriggered, onNavigationChange, onShortcutTriggered]);

  // Auto-discover navigation items
  useEffect(() => {
    navigationManager.startAutoDiscovery();

    return () => {
      navigationManager.stopAutoDiscovery();
    };
  }, [navigationManager]);

  // Announce navigation changes to screen readers
  useEffect(() => {
    if (!enableScreenReader || !currentItem) return;

    const announcement = `Navigated to ${currentItem.label}`;
    const announcer = document.getElementById('keyboard-navigation-announcer');
    if (announcer) {
      announcer.textContent = announcement;
    }
  }, [enableScreenReader, currentItem]);

  // Hide help after 5 seconds of inactivity
  useEffect(() => {
    if (showHelp) {
      if (helpTimeoutRef.current) {
        clearTimeout(helpTimeoutRef.current);
      }

      helpTimeoutRef.current = setTimeout(() => {
        setShowHelp(false);
      }, 5000);
    }

    return () => {
      if (helpTimeoutRef.current) {
        clearTimeout(helpTimeoutRef.current);
      }
    };
  }, [showHelp]);

  // Get shortcuts by category
  const shortcutsByCategory = React.useMemo(() => {
    const items = navigationManager.getVisibleItems().filter(item => item.shortcut);
    const categories: Record<string, NavigationItem[]> = {};

    items.forEach(item => {
      if (!categories[item.category]) {
        categories[item.category] = [];
      }
      categories[item.category].push(item);
    });

    return categories;
  }, [navigationManager]);

  return (
    <>
      {/* Screen reader announcer */}
      {enableScreenReader && (
        <div
          id="keyboard-navigation-announcer"
          className="sr-only"
          role="status"
          aria-live="polite"
          aria-atomic="true"
        />
      )}

      {/* Keyboard navigation help overlay */}
      {showHelp && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl w-full max-w-2xl max-h-[80vh] overflow-hidden">
            <div className="p-4 border-b border-gray-200 dark:border-gray-700">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  ⌨️ Keyboard Shortcuts
                </h2>
                <InteractiveButton
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowHelp(false)}
                >
                  ✕
                </InteractiveButton>
              </div>
            </div>

            <div className="p-4 max-h-96 overflow-y-auto">
              <div className="space-y-4">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
                    Navigation
                  </h3>
                  <div className="space-y-1 text-sm">
                    <div className="flex justify-between">
                      <span>Move to next item</span>
                      <kbd className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">Tab</kbd>
                    </div>
                    <div className="flex justify-between">
                      <span>Move to previous item</span>
                      <kbd className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">Shift + Tab</kbd>
                    </div>
                    <div className="flex justify-between">
                      <span>Show keyboard help</span>
                      <kbd className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">Ctrl + ?</kbd>
                    </div>
                  </div>
                </div>

                {Object.entries(shortcutsByCategory).map(([category, items]) => (
                  <div key={category}>
                    <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2 capitalize">
                      {category}
                    </h3>
                    <div className="space-y-1 text-sm">
                      {items.map(item => (
                        <div key={item.id} className="flex justify-between">
                          <span>{item.label}</span>
                          <kbd className="px-2 py-1 bg-gray-100 dark:bg-gray-800 rounded text-xs">
                            {item.shortcut}
                          </kbd>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
              <div className="text-xs text-gray-600 dark:text-gray-400 text-center">
                Press any key or click outside to close
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Current item indicator */}
      {currentItem && (
        <div className="fixed bottom-4 left-4 bg-blue-600 text-white px-3 py-2 rounded-lg shadow-lg z-40">
          <div className="text-sm font-medium">{currentItem.label}</div>
          {currentItem.shortcut && (
            <div className="text-xs opacity-90">{currentItem.shortcut}</div>
          )}
        </div>
      )}

      {/* Pressed keys indicator */}
      {pressedKeys.size > 0 && (
        <div className="fixed top-4 right-4 bg-gray-900 text-white px-3 py-2 rounded-lg shadow-lg z-40">
          <div className="text-sm font-mono">
            {Array.from(pressedKeys).join(' + ')}
          </div>
        </div>
      )}

      {/* Main content with focus trap */}
      <div ref={containerRef} className={className}>
        {/* Component content goes here */}
      </div>
    </>
  );
};

// Utility function to compare arrays
function ArraysMatch(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((val, index) => val === b[index]);
}

export default KeyboardNavigation;
