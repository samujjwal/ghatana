/**
 * Keyboard Shortcut Reference System
 * Comprehensive keyboard shortcuts registry and help interface
 */

import clsx from 'clsx';
import React, { useState, useEffect, useMemo } from 'react';

/**
 *
 */
export interface KeybindingAction {
  id: string;
  name: string;
  description: string;
  category: 'navigation' | 'editing' | 'selection' | 'view' | 'file' | 'help' | 'canvas' | 'collaboration';
  keys: string[];
  context?: string;
  action: () => void;
  condition?: () => boolean;
  preventDefault?: boolean;
}

/**
 *
 */
export interface ShortcutCategory {
  id: string;
  name: string;
  description: string;
  icon: string;
  shortcuts: KeybindingAction[];
}

/**
 *
 */
export interface KeyboardShortcutProps {
  isOpen: boolean;
  onClose: () => void;
  className?: string;
}

/**
 *
 */
export class KeyboardShortcutRegistry {
  private shortcuts: Map<string, KeybindingAction> = new Map();
  private isEnabled: boolean = true;

  /**
   *
   */
  constructor() {
    this.registerDefaultShortcuts();
    this.initializeEventListeners();
  }

  /**
   * Register a keyboard shortcut
   */
  register(shortcut: KeybindingAction): void {
    this.shortcuts.set(shortcut.id, shortcut);
    this.updateKeyListener(shortcut);
  }

  /**
   * Unregister a keyboard shortcut
   */
  unregister(id: string): void {
    const shortcut = this.shortcuts.get(id);
    if (shortcut) {
      this.shortcuts.delete(id);
      this.removeKeyListener(shortcut);
    }
  }

  /**
   * Get all shortcuts grouped by category
   */
  getShortcutsByCategory(): ShortcutCategory[] {
    const categories = new Map<string, ShortcutCategory>();
    
    for (const shortcut of this.shortcuts.values()) {
      if (!categories.has(shortcut.category)) {
        categories.set(shortcut.category, {
          id: shortcut.category,
          name: this.getCategoryName(shortcut.category),
          description: this.getCategoryDescription(shortcut.category),
          icon: this.getCategoryIcon(shortcut.category),
          shortcuts: [],
        });
      }
      categories.get(shortcut.category)!.shortcuts.push(shortcut);
    }

    // Sort shortcuts within each category
    for (const category of categories.values()) {
      category.shortcuts.sort((a, b) => a.name.localeCompare(b.name));
    }

    return Array.from(categories.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  /**
   * Find shortcuts by search term
   */
  searchShortcuts(query: string): KeybindingAction[] {
    const queryLower = query.toLowerCase();
    return Array.from(this.shortcuts.values()).filter(shortcut =>
      shortcut.name.toLowerCase().includes(queryLower) ||
      shortcut.description.toLowerCase().includes(queryLower) ||
      shortcut.keys.some(key => key.toLowerCase().includes(queryLower))
    );
  }

  /**
   * Enable/disable keyboard shortcuts
   */
  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
  }

  /**
   * Format key combination for display
   */
  formatKeys(keys: string[]): string {
    return keys
      .map(key => {
        const formatted = key
          .replace('Mod', navigator.platform.includes('Mac') ? '⌘' : 'Ctrl')
          .replace('Alt', navigator.platform.includes('Mac') ? '⌥' : 'Alt')
          .replace('Shift', '⇧')
          .replace('Enter', '⏎')
          .replace('Escape', 'Esc')
          .replace('Backspace', '⌫')
          .replace('Delete', '⌦')
          .replace('Tab', '⇥')
          .replace('Space', '␣');
        
        return formatted.charAt(0).toUpperCase() + formatted.slice(1);
      })
      .join(' + ');
  }

  /**
   *
   */
  private registerDefaultShortcuts(): void {
    const shortcuts: KeybindingAction[] = [
      // Canvas Operations
      {
        id: 'canvas.selectAll',
        name: 'Select All',
        description: 'Select all elements on the canvas',
        category: 'selection',
        keys: ['Mod', 'a'],
        action: () => this.executeCanvasAction('selectAll'),
      },
      {
        id: 'canvas.copy',
        name: 'Copy',
        description: 'Copy selected elements',
        category: 'editing',
        keys: ['Mod', 'c'],
        action: () => this.executeCanvasAction('copy'),
      },
      {
        id: 'canvas.paste',
        name: 'Paste',
        description: 'Paste copied elements',
        category: 'editing',
        keys: ['Mod', 'v'],
        action: () => this.executeCanvasAction('paste'),
      },
      {
        id: 'canvas.cut',
        name: 'Cut',
        description: 'Cut selected elements',
        category: 'editing',
        keys: ['Mod', 'x'],
        action: () => this.executeCanvasAction('cut'),
      },
      {
        id: 'canvas.delete',
        name: 'Delete',
        description: 'Delete selected elements',
        category: 'editing',
        keys: ['Delete'],
        action: () => this.executeCanvasAction('delete'),
      },
      {
        id: 'canvas.undo',
        name: 'Undo',
        description: 'Undo last action',
        category: 'editing',
        keys: ['Mod', 'z'],
        action: () => this.executeCanvasAction('undo'),
      },
      {
        id: 'canvas.redo',
        name: 'Redo',
        description: 'Redo last undone action',
        category: 'editing',
        keys: ['Mod', 'Shift', 'z'],
        action: () => this.executeCanvasAction('redo'),
      },

      // View Operations
      {
        id: 'view.zoomIn',
        name: 'Zoom In',
        description: 'Zoom into the canvas',
        category: 'view',
        keys: ['Mod', '+'],
        action: () => this.executeViewAction('zoomIn'),
      },
      {
        id: 'view.zoomOut',
        name: 'Zoom Out',
        description: 'Zoom out of the canvas',
        category: 'view',
        keys: ['Mod', '-'],
        action: () => this.executeViewAction('zoomOut'),
      },
      {
        id: 'view.fitToScreen',
        name: 'Fit to Screen',
        description: 'Fit all elements to screen',
        category: 'view',
        keys: ['Mod', '0'],
        action: () => this.executeViewAction('fitView'),
      },
      {
        id: 'view.actualSize',
        name: 'Actual Size',
        description: 'Reset zoom to 100%',
        category: 'view',
        keys: ['Mod', '1'],
        action: () => this.executeViewAction('resetZoom'),
      },

      // Navigation
      {
        id: 'nav.commandPalette',
        name: 'Command Palette',
        description: 'Open command palette',
        category: 'navigation',
        keys: ['Mod', 'k'],
        action: () => this.executeNavigationAction('openCommandPalette'),
      },
      {
        id: 'nav.search',
        name: 'Search',
        description: 'Search elements and canvases',
        category: 'navigation',
        keys: ['Mod', 'f'],
        action: () => this.executeNavigationAction('search'),
      },
      {
        id: 'nav.goBack',
        name: 'Go Back',
        description: 'Navigate to previous canvas',
        category: 'navigation',
        keys: ['Alt', 'ArrowLeft'],
        action: () => this.executeNavigationAction('goBack'),
      },
      {
        id: 'nav.goForward',
        name: 'Go Forward',
        description: 'Navigate to next canvas',
        category: 'navigation',
        keys: ['Alt', 'ArrowRight'],
        action: () => this.executeNavigationAction('goForward'),
      },

      // File Operations
      {
        id: 'file.save',
        name: 'Save',
        description: 'Save current canvas',
        category: 'file',
        keys: ['Mod', 's'],
        action: () => this.executeFileAction('save'),
      },
      {
        id: 'file.export',
        name: 'Export',
        description: 'Export canvas',
        category: 'file',
        keys: ['Mod', 'e'],
        action: () => this.executeFileAction('export'),
      },
      {
        id: 'file.new',
        name: 'New Canvas',
        description: 'Create new canvas',
        category: 'file',
        keys: ['Mod', 'n'],
        action: () => this.executeFileAction('new'),
      },

      // Canvas-specific
      {
        id: 'canvas.addNode',
        name: 'Add Node',
        description: 'Add a new node',
        category: 'canvas',
        keys: ['n'],
        context: 'canvas',
        action: () => this.executeCanvasAction('addNode'),
      },
      {
        id: 'canvas.addConnection',
        name: 'Add Connection',
        description: 'Start connecting nodes',
        category: 'canvas',
        keys: ['c'],
        context: 'canvas',
        action: () => this.executeCanvasAction('startConnection'),
      },
      {
        id: 'canvas.toggleGrid',
        name: 'Toggle Grid',
        description: 'Show/hide canvas grid',
        category: 'view',
        keys: ['g'],
        context: 'canvas',
        action: () => this.executeViewAction('toggleGrid'),
      },

      // Help
      {
        id: 'help.shortcuts',
        name: 'Keyboard Shortcuts',
        description: 'Show this help dialog',
        category: 'help',
        keys: ['?'],
        action: () => this.executeHelpAction('showShortcuts'),
      },
      {
        id: 'help.escape',
        name: 'Cancel/Escape',
        description: 'Cancel current action or close dialogs',
        category: 'help',
        keys: ['Escape'],
        action: () => this.executeHelpAction('escape'),
      },
    ];

    shortcuts.forEach(shortcut => this.register(shortcut));
  }

  /**
   *
   */
  private initializeEventListeners(): void {
    document.addEventListener('keydown', this.handleKeyDown.bind(this));
  }

  /**
   *
   */
  private handleKeyDown(event: KeyboardEvent): void {
    if (!this.isEnabled) return;

    // Skip if user is typing in an input field
    if (this.isInputFocused(event.target as Element)) return;

    const keyCombo = this.getKeyCombo(event);
    
    for (const shortcut of this.shortcuts.values()) {
      if (this.matchesShortcut(keyCombo, shortcut)) {
        // Check condition if provided
        if (shortcut.condition && !shortcut.condition()) continue;

        if (shortcut.preventDefault !== false) {
          event.preventDefault();
        }
        
        try {
          shortcut.action();
        } catch (error) {
          console.error('Shortcut execution error:', error);
        }
        break;
      }
    }
  }

  /**
   *
   */
  private getKeyCombo(event: KeyboardEvent): string[] {
    const keys: string[] = [];
    
    if (event.ctrlKey || event.metaKey) keys.push('Mod');
    if (event.altKey) keys.push('Alt');
    if (event.shiftKey) keys.push('Shift');
    
    // Add the main key
    const key = event.key;
    if (key !== 'Control' && key !== 'Meta' && key !== 'Alt' && key !== 'Shift') {
      keys.push(key);
    }
    
    return keys;
  }

  /**
   *
   */
  private matchesShortcut(keyCombo: string[], shortcut: KeybindingAction): boolean {
    if (keyCombo.length !== shortcut.keys.length) return false;
    
    return shortcut.keys.every(key => keyCombo.includes(key));
  }

  /**
   *
   */
  private isInputFocused(target: Element): boolean {
    if (!target) return false;
    
    const tagName = target.tagName.toLowerCase();
    const inputTags = ['input', 'textarea', 'select'];
    
    return inputTags.includes(tagName) || 
           target.getAttribute('contenteditable') === 'true' ||
           target.closest('[contenteditable="true"]') !== null;
  }

  /**
   *
   */
  private updateKeyListener(_shortcut: KeybindingAction): void {
    // Implementation would update internal key mapping
  }

  /**
   *
   */
  private removeKeyListener(_shortcut: KeybindingAction): void {
    // Implementation would remove key mapping
  }

  /**
   *
   */
  private getCategoryName(category: string): string {
    const names: Record<string, string> = {
      navigation: 'Navigation',
      editing: 'Editing',
      selection: 'Selection',
      view: 'View',
      file: 'File',
      help: 'Help',
      canvas: 'Canvas',
      collaboration: 'Collaboration',
    };
    return names[category] || category;
  }

  /**
   *
   */
  private getCategoryDescription(category: string): string {
    const descriptions: Record<string, string> = {
      navigation: 'Navigate between canvases and UI elements',
      editing: 'Edit and manipulate canvas elements',
      selection: 'Select and manage element selection',
      view: 'Control canvas view and zoom',
      file: 'File operations and data management',
      help: 'Help and assistance shortcuts',
      canvas: 'Canvas-specific operations',
      collaboration: 'Collaboration and sharing features',
    };
    return descriptions[category] || '';
  }

  /**
   *
   */
  private getCategoryIcon(category: string): string {
    const icons: Record<string, string> = {
      navigation: '🧭',
      editing: '✏️',
      selection: '🎯',
      view: '👁️',
      file: '📁',
      help: '❓',
      canvas: '🎨',
      collaboration: '👥',
    };
    return icons[category] || '⚙️';
  }

  // Mock action executors - in real app these would call actual services
  /**
   *
   */
  private executeCanvasAction(action: string): void {
    console.log(`Canvas action: ${action}`);
    // Dispatch to canvas service
  }

  /**
   *
   */
  private executeViewAction(action: string): void {
    console.log(`View action: ${action}`);
    // Dispatch to view service
  }

  /**
   *
   */
  private executeNavigationAction(action: string): void {
    console.log(`Navigation action: ${action}`);
    // Dispatch to navigation service
  }

  /**
   *
   */
  private executeFileAction(action: string): void {
    console.log(`File action: ${action}`);
    // Dispatch to file service
  }

  /**
   *
   */
  private executeHelpAction(action: string): void {
    console.log(`Help action: ${action}`);
    // Dispatch to help service
  }
}

export const KeyboardShortcutHelp: React.FC<KeyboardShortcutProps> = ({
  isOpen,
  onClose,
  className,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const registry = useMemo(() => new KeyboardShortcutRegistry(), []);

  const categories = registry.getShortcutsByCategory();
  const filteredShortcuts = useMemo(() => {
    if (searchQuery) {
      return registry.searchShortcuts(searchQuery);
    }
    
    if (selectedCategory === 'all') {
      return categories.flatMap(cat => cat.shortcuts);
    }
    
    return categories.find(cat => cat.id === selectedCategory)?.shortcuts || [];
  }, [categories, selectedCategory, searchQuery, registry]);

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className={clsx(
        'fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50',
        className
      )}
      onClick={(e) => e.target === e.currentTarget && onClose()}
      data-testid="keyboard-shortcuts-dialog"
    >
      <div className="w-full max-w-4xl h-4/5 bg-white rounded-lg shadow-2xl flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex justify-between items-center p-6 border-b border-gray-200">
          <div>
            <h2 className="text-2xl font-semibold text-gray-900">Keyboard Shortcuts</h2>
            <p className="text-sm text-gray-600 mt-1">Master the canvas with these shortcuts</p>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            data-testid="close-shortcuts-dialog"
          >
            ✕
          </button>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* Categories Sidebar */}
          <div className="w-64 bg-gray-50 border-r border-gray-200 p-4 overflow-y-auto">
            <div className="mb-4">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search shortcuts..."
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="space-y-1">
              <button
                onClick={() => setSelectedCategory('all')}
                className={clsx(
                  'w-full text-left px-3 py-2 rounded-md text-sm transition-colors',
                  selectedCategory === 'all'
                    ? 'bg-blue-100 text-blue-900'
                    : 'hover:bg-gray-100'
                )}
              >
                📋 All Shortcuts ({categories.reduce((sum, cat) => sum + cat.shortcuts.length, 0)})
              </button>
              
              {categories.map((category) => (
                <button
                  key={category.id}
                  onClick={() => setSelectedCategory(category.id)}
                  className={clsx(
                    'w-full text-left px-3 py-2 rounded-md text-sm transition-colors',
                    selectedCategory === category.id
                      ? 'bg-blue-100 text-blue-900'
                      : 'hover:bg-gray-100'
                  )}
                >
                  {category.icon} {category.name} ({category.shortcuts.length})
                </button>
              ))}
            </div>
          </div>

          {/* Shortcuts Content */}
          <div className="flex-1 p-6 overflow-y-auto">
            {searchQuery && (
              <div className="mb-4">
                <h3 className="text-lg font-medium text-gray-900">
                  Search Results ({filteredShortcuts.length})
                </h3>
                <p className="text-sm text-gray-600">Showing shortcuts matching "{searchQuery}"</p>
              </div>
            )}

            <div className="space-y-6">
              {searchQuery ? (
                <ShortcutsList shortcuts={filteredShortcuts} registry={registry} />
              ) : selectedCategory === 'all' ? (
                categories.map((category) => (
                  <div key={category.id}>
                    <div className="flex items-center mb-3">
                      <span className="text-2xl mr-2">{category.icon}</span>
                      <div>
                        <h3 className="text-lg font-medium text-gray-900">{category.name}</h3>
                        <p className="text-sm text-gray-600">{category.description}</p>
                      </div>
                    </div>
                    <ShortcutsList shortcuts={category.shortcuts} registry={registry} />
                  </div>
                ))
              ) : (
                <ShortcutsList shortcuts={filteredShortcuts} registry={registry} />
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200">
          <div className="flex justify-between items-center text-sm text-gray-600">
            <span>Press <kbd className="px-2 py-1 bg-gray-200 rounded">Esc</kbd> to close</span>
            <span>{filteredShortcuts.length} shortcuts shown</span>
          </div>
        </div>
      </div>
    </div>
  );
};

/**
 *
 */
interface ShortcutsListProps {
  shortcuts: KeybindingAction[];
  registry: KeyboardShortcutRegistry;
}

const ShortcutsList: React.FC<ShortcutsListProps> = ({ shortcuts, registry }) => (
  <div className="space-y-2">
    {shortcuts.map((shortcut) => (
      <div
        key={shortcut.id}
        className="flex justify-between items-center p-3 bg-white rounded-lg border border-gray-200"
      >
        <div className="flex-1">
          <h4 className="font-medium text-gray-900">{shortcut.name}</h4>
          <p className="text-sm text-gray-600">{shortcut.description}</p>
          {shortcut.context && (
            <span className="inline-block px-2 py-1 mt-1 text-xs bg-gray-100 text-gray-600 rounded">
              {shortcut.context}
            </span>
          )}
        </div>
        <div className="ml-4">
          <kbd className="px-3 py-1 bg-gray-100 text-gray-800 rounded-md font-mono text-sm">
            {registry.formatKeys(shortcut.keys)}
          </kbd>
        </div>
      </div>
    ))}
  </div>
);

export const keyboardShortcutRegistry = new KeyboardShortcutRegistry();
export default KeyboardShortcutHelp;