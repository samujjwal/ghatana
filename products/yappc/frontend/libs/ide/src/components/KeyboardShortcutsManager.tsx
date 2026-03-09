/**
 * @ghatana/yappc-ide - Keyboard Shortcuts Manager Component
 * 
 * Comprehensive keyboard shortcuts management with customizable
 * bindings, conflict detection, and learning features.
 * 
 * @doc.type component
 * @doc.purpose Keyboard shortcuts management for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useKeyboardShortcuts, type KeyboardShortcut } from '../hooks/useKeyboardShortcuts';

// Local custom shortcuts state is maintained here


import { InteractiveButton } from './MicroInteractions';

/**
 * Shortcut category configuration
 */
export interface ShortcutCategory {
  id: string;
  name: string;
  description: string;
  icon: string;
  color: string;
}

/**
 * Custom shortcut definition
 */
export interface CustomShortcut {
  id: string;
  name: string;
  description: string;
  category: 'file' | 'edit' | 'view' | 'navigation' | 'tools';
  key: string;
  modifiers: ('shift' | 'meta' | 'ctrl' | 'alt')[];
  action: () => void;
  enabled: boolean;
  isCustom: boolean;
  conflicts?: string[];
}

/**
 * Keyboard shortcuts manager props
 */
export interface KeyboardShortcutsManagerProps {
  isOpen: boolean;
  onClose: () => void;
  onShortcutUpdate?: (shortcutId: string, shortcut: KeyboardShortcut) => void;
  onShortcutCreate?: (shortcut: CustomShortcut) => void;
  enableCustomShortcuts?: boolean;
  enableConflictDetection?: boolean;
  showUsageStats?: boolean;
  className?: string;
}

/**
 * Default shortcut categories
 */
const DEFAULT_CATEGORIES: ShortcutCategory[] = [
  {
    id: 'file',
    name: 'File Operations',
    description: 'File and folder operations',
    icon: '📁',
    color: 'blue',
  },
  {
    id: 'edit',
    name: 'Editing',
    description: 'Text editing and manipulation',
    icon: '✏️',
    color: 'green',
  },
  {
    id: 'view',
    name: 'View',
    description: 'Interface and layout controls',
    icon: '👁️',
    color: 'purple',
  },
  {
    id: 'navigation',
    name: 'Navigation',
    description: 'Cursor and file navigation',
    icon: '🧭',
    color: 'orange',
  },
  {
    id: 'search',
    name: 'Search',
    description: 'Search and replace operations',
    icon: '🔍',
    color: 'red',
  },
  {
    id: 'debug',
    name: 'Debug',
    description: 'Debugging and testing',
    icon: '🐛',
    color: 'yellow',
  },
];

/**
 * Shortcut editor component
 */
interface ShortcutEditorProps {
  shortcut: CustomShortcut;
  onSave: (shortcut: CustomShortcut) => void;
  onCancel: () => void;
  conflicts?: string[];
}

const ShortcutEditor: React.FC<ShortcutEditorProps> = ({
  shortcut,
  onSave,
  onCancel,
  conflicts = [],
}) => {
  const [editingShortcut, setEditingShortcut] = useState(shortcut);
  const [isRecording, setIsRecording] = useState(false);
  const [recordedKeys, setRecordedKeys] = useState<string[]>([]);

  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    if (!isRecording) return;

    e.preventDefault();
    e.stopPropagation();

    const keys: string[] = [];

    if (e.ctrlKey || e.metaKey) keys.push('Ctrl');
    if (e.altKey) keys.push('Alt');
    if (e.shiftKey) keys.push('Shift');

    if (e.key && !['Control', 'Alt', 'Shift', 'Meta'].includes(e.key)) {
      keys.push(e.key.toUpperCase());
    }

    if (keys.length > 0) {
      setRecordedKeys(keys);
      // Normalize modifiers to expected union type
      const mappedModifiers = keys.slice(0, -1).map(k => {
        const lk = k.toLowerCase();
        if (lk === 'ctrl') return 'ctrl';
        if (lk === 'alt') return 'alt';
        if (lk === 'shift') return 'shift';
        if (lk === 'meta') return 'meta';
        return 'ctrl';
      }) as ('shift' | 'meta' | 'ctrl' | 'alt')[];

      setEditingShortcut({
        ...editingShortcut,
        modifiers: mappedModifiers,
        key: keys[keys.length - 1],
      });
      setIsRecording(false);
    }
  }, [isRecording, editingShortcut]);

  useEffect(() => {
    if (isRecording) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isRecording, handleKeyDown]);

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl w-full max-w-md p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          Edit Shortcut
        </h3>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Name
            </label>
            <input
              type="text"
              value={editingShortcut.name}
              onChange={(e) => setEditingShortcut({ ...editingShortcut, name: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Description
            </label>
            <textarea
              value={editingShortcut.description}
              onChange={(e) => setEditingShortcut({ ...editingShortcut, description: e.target.value })}
              rows={2}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Keyboard Shortcut
            </label>
            <div className="flex gap-2">
              <button
                onClick={() => setIsRecording(!isRecording)}
                className={`
                  flex-1 px-3 py-2 border rounded-md font-mono text-sm
                  ${isRecording
                    ? 'bg-red-50 border-red-300 text-red-700 dark:bg-red-900/20 dark:border-red-700 dark:text-red-400'
                    : 'bg-gray-50 border-gray-300 text-gray-700 dark:bg-gray-800 dark:border-gray-600 dark:text-gray-300'
                  }
                `}
              >
                {isRecording ? 'Press keys...' : (
                  [...editingShortcut.modifiers, editingShortcut.key].join(' + ') || 'Click to record'
                )}
              </button>
              {recordedKeys.length > 0 && (
                <button
                  onClick={() => {
                    setRecordedKeys([]);
                    setEditingShortcut({ ...editingShortcut, modifiers: [], key: '' });
                  }}
                  className="px-3 py-2 text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {conflicts.length > 0 && (
            <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md">
              <div className="text-sm text-yellow-800 dark:text-yellow-200">
                ⚠️ Conflicts with: {conflicts.join(', ')}
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <InteractiveButton
            variant="ghost"
            onClick={onCancel}
          >
            Cancel
          </InteractiveButton>
          <InteractiveButton
            variant="primary"
            onClick={() => onSave(editingShortcut)}
            disabled={!editingShortcut.name || !editingShortcut.key}
          >
            Save
          </InteractiveButton>
        </div>
      </div>
    </div>
  );
};

/**
 * Keyboard Shortcuts Manager Component
 */
export const KeyboardShortcutsManager: React.FC<KeyboardShortcutsManagerProps> = ({
  isOpen,
  onClose,
  onShortcutUpdate,
  onShortcutCreate,
  enableCustomShortcuts = true,
  showUsageStats = true,
  className = '',
}) => {
  // Hook for managing global shortcuts (we keep a local shortcuts list for display)
  const { unregisterShortcut } = useKeyboardShortcuts();

  const [customShortcuts, setCustomShortcuts] = useState<CustomShortcut[]>([]);
  // TODO: wire real shortcuts into the hook; for now use customShortcuts as the source
  const shortcuts: KeyboardShortcut[] = customShortcuts.map(c => ({
    id: c.id,
    key: c.key.toLowerCase(),
    modifiers: c.modifiers as ('ctrl' | 'alt' | 'shift' | 'meta')[],
    action: c.action,
    description: c.description,
    category: (c.category as unknown) || 'tools',
  }));

  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [editingShortcut, setEditingShortcut] = useState<CustomShortcut | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);

  const filteredShortcuts = React.useMemo(() => {
    let filtered = shortcuts;

    if (selectedCategory !== 'all') {
      filtered = filtered.filter(s => s.category === selectedCategory);
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(s =>
        s.description.toLowerCase().includes(query) ||
        s.category.toLowerCase().includes(query)
      );
    }

    return filtered;
  }, [shortcuts, selectedCategory, searchQuery]);

  const handleCreateShortcut = useCallback(() => {
    const newShortcut: CustomShortcut = {
      id: `custom-${Date.now()}`,
      name: '',
      description: '',
      category: 'tools',
      key: '',
      modifiers: [],
      action: () => { },
      enabled: true,
      isCustom: true,
    };

    setEditingShortcut(newShortcut);
    setShowCreateDialog(true);
  }, []);

  const handleSaveShortcut = useCallback((shortcut: CustomShortcut) => {
    if (shortcut.isCustom) {
      if (showCreateDialog) {
        setCustomShortcuts(prev => [...prev, shortcut]);
        onShortcutCreate?.(shortcut);
      } else {
        setCustomShortcuts(prev =>
          prev.map(s => s.id === shortcut.id ? shortcut : s)
        );
        onShortcutUpdate?.(shortcut.id, shortcut);
      }
    } else {
      onShortcutUpdate?.(shortcut.id, shortcut);
    }

    setEditingShortcut(null);
    setShowCreateDialog(false);
  }, [showCreateDialog, onShortcutCreate, onShortcutUpdate]);

  const handleDeleteShortcut = useCallback((shortcutId: string) => {
    setCustomShortcuts(prev => prev.filter(s => s.id !== shortcutId));
    unregisterShortcut(shortcutId);
  }, [unregisterShortcut]);

  const usageStats = React.useMemo(() => ({} as Record<string, { count: number; lastUsed?: number }>), []);

  if (!isOpen) return null;

  return (
    <>
      <div className={`fixed inset-0 bg-black/50 flex items-center justify-center z-50 ${className}`}>
        <div className="bg-white dark:bg-gray-900 rounded-lg shadow-2xl w-full max-w-4xl max-h-[80vh] overflow-hidden flex flex-col">
          {/* Header */}
          <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
              ⌨️ Keyboard Shortcuts
            </h2>
            <div className="flex items-center gap-2">
              {enableCustomShortcuts && (
                <InteractiveButton
                  variant="secondary"
                  size="sm"
                  onClick={handleCreateShortcut}
                >
                  + New Shortcut
                </InteractiveButton>
              )}
              <InteractiveButton
                variant="ghost"
                size="sm"
                onClick={onClose}
              >
                ✕
              </InteractiveButton>
            </div>
          </div>

          {/* Search and filters */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <div className="flex gap-4">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search shortcuts..."
                className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              />
              <select
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value)}
                className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
              >
                <option value="all">All Categories</option>
                {DEFAULT_CATEGORIES.map(category => (
                  <option key={category.id} value={category.id}>
                    {category.icon} {category.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Shortcuts list */}
          <div className="flex-1 overflow-y-auto">
            <div className="divide-y divide-gray-200 dark:divide-gray-700">
              {filteredShortcuts.map(shortcut => {
                const category = DEFAULT_CATEGORIES.find(c => c.id === shortcut.category);
                const usage = usageStats[shortcut.id];

                return (
                  <div
                    key={shortcut.id}
                    className="p-4 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          {category && <span>{category.icon}</span>}
                          <h3 className="font-medium text-gray-900 dark:text-gray-100">
                            {shortcut.description}
                          </h3>
                          {shortcut.isCustom && (
                            <span className="px-2 py-1 text-xs bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400 rounded">
                              Custom
                            </span>
                          )}
                        </div>
                        <div className="text-sm text-gray-500 dark:text-gray-400">
                          {category?.name}
                        </div>
                        {showUsageStats && usage && (
                          <div className="text-xs text-gray-400 dark:text-gray-500 mt-1">
                            Used {usage.count} times • Last used {usage.lastUsed ? new Date(usage.lastUsed).toLocaleDateString() : 'never'}
                          </div>
                        )}
                      </div>

                      <div className="flex items-center gap-3">
                        <div className="px-3 py-1 bg-gray-100 dark:bg-gray-800 rounded text-sm font-mono text-gray-700 dark:text-gray-300">
                          {[...shortcut.modifiers, shortcut.key.toUpperCase()].join(' + ')}
                        </div>

                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => setEditingShortcut(shortcut as CustomShortcut)}
                            className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                            title="Edit"
                          >
                            ✏️
                          </button>
                          {shortcut.isCustom && (
                            <button
                              onClick={() => handleDeleteShortcut(shortcut.id)}
                              className="p-1 text-gray-400 hover:text-red-600 dark:hover:text-red-400"
                              title="Delete"
                            >
                              🗑️
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Footer */}
          <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
            <div className="flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
              <div>
                {filteredShortcuts.length} shortcuts
                {selectedCategory !== 'all' && ` in ${DEFAULT_CATEGORIES.find(c => c.id === selectedCategory)?.name}`}
              </div>
              <div>
                Press any shortcut to test • Click edit to modify
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Edit/Create Dialog */}
      {editingShortcut && (
        <ShortcutEditor
          shortcut={editingShortcut}
          onSave={handleSaveShortcut}
          onCancel={() => {
            setEditingShortcut(null);
            setShowCreateDialog(false);
          }}
        />
      )}
    </>
  );
};

export default KeyboardShortcutsManager;
