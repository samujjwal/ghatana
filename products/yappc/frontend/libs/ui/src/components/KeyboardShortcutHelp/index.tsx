/**
 * KeyboardShortcutHelp Component - Refactored with modularized internals
 *
 * Provides a comprehensive keyboard shortcuts reference dialog for users
 * to discover and understand available keyboard shortcuts organized by category.
 *
 * @module KeyboardShortcutHelp
 */

import clsx from 'clsx';
import React, { useState, useEffect, useMemo, useCallback } from 'react';

import {
    keyboardShortcutRegistry,
    type KeybindingAction,
    type KeyboardShortcutProps,
} from './internals';

/**
 * Internal component for rendering a list of shortcuts
 *
 * @internal
 */
const ShortcutsList: React.FC<{
    shortcuts: KeybindingAction[];
    registry: typeof keyboardShortcutRegistry;
}> = ({ shortcuts, registry }) => (
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

/**
 * Content rendering component for the shortcuts dialog
 *
 * @internal
 */
const ShortcutsContent: React.FC<{
    searchQuery: string;
    selectedCategory: string;
    categories: ReturnType<typeof keyboardShortcutRegistry.getShortcutsByCategory>;
    filteredShortcuts: KeybindingAction[];
    registry: typeof keyboardShortcutRegistry;
}> = ({ searchQuery, selectedCategory, categories, filteredShortcuts, registry }) => (
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
);

/**
 * Sidebar component for category selection
 *
 * @internal
 */
const CategoriesSidebar: React.FC<{
    categories: ReturnType<typeof keyboardShortcutRegistry.getShortcutsByCategory>;
    selectedCategory: string;
    searchQuery: string;
    onCategoryChange: (category: string) => void;
    onSearchChange: (query: string) => void;
}> = ({ categories, selectedCategory, searchQuery, onCategoryChange, onSearchChange }) => (
    <div className="w-64 bg-gray-50 border-r border-gray-200 p-4 overflow-y-auto">
        <div className="mb-4">
            <input
                type="text"
                value={searchQuery}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder="Search shortcuts..."
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
        </div>

        <div className="space-y-1">
            <button
                onClick={() => onCategoryChange('all')}
                className={clsx(
                    'w-full text-left px-3 py-2 rounded-md text-sm transition-colors',
                    selectedCategory === 'all' ? 'bg-blue-100 text-blue-900' : 'hover:bg-gray-100'
                )}
            >
                📋 All Shortcuts ({categories.reduce((sum, cat) => sum + cat.shortcuts.length, 0)})
            </button>

            {categories.map((category) => (
                <button
                    key={category.id}
                    onClick={() => onCategoryChange(category.id)}
                    className={clsx(
                        'w-full text-left px-3 py-2 rounded-md text-sm transition-colors',
                        selectedCategory === category.id ? 'bg-blue-100 text-blue-900' : 'hover:bg-gray-100'
                    )}
                >
                    {category.icon} {category.name} ({category.shortcuts.length})
                </button>
            ))}
        </div>
    </div>
);

/**
 * KeyboardShortcutHelp Component
 *
 * Displays a modal dialog with all available keyboard shortcuts organized by category.
 * Features include:
 * - Category-based organization
 * - Search functionality
 * - Keyboard navigation (Escape to close)
 * - Responsive layout
 *
 * @example
 * ```tsx
 * function MyApp() {
 *   const [showShortcuts, setShowShortcuts] = useState(false);
 *
 *   return (
 *     <>
 *       <button onClick={() => setShowShortcuts(true)}>Show Shortcuts</button>
 *       <KeyboardShortcutHelp
 *         isOpen={showShortcuts}
 *         onClose={() => setShowShortcuts(false)}
 *       />
 *     </>
 *   );
 * }
 * ```
 */
export const KeyboardShortcutHelp: React.FC<KeyboardShortcutProps> = ({
    isOpen,
    onClose,
    className,
}) => {
    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const registry = useMemo(() => keyboardShortcutRegistry, []);

    const categories = registry.getShortcutsByCategory();
    const filteredShortcuts = useMemo(() => {
        if (searchQuery) {
            return registry.searchShortcuts(searchQuery);
        }

        if (selectedCategory === 'all') {
            return categories.flatMap((cat) => cat.shortcuts);
        }

        return categories.find((cat) => cat.id === selectedCategory)?.shortcuts || [];
    }, [categories, selectedCategory, searchQuery, registry]);

    const handleBackdropClick = useCallback(
        (e: React.MouseEvent<HTMLDivElement>) => {
            if (e.target === e.currentTarget) {
                onClose();
            }
        },
        [onClose]
    );

    // Separate handler for document keyboard events (different event type)
    const handleDocumentKeyDown = useCallback((e: KeyboardEvent) => {
        if (e.key === 'Escape') {
            onClose();
        }
    }, [onClose]);

    useEffect(() => {
        if (!isOpen) return;

        document.addEventListener('keydown', handleDocumentKeyDown);
        return () => document.removeEventListener('keydown', handleDocumentKeyDown);
    }, [isOpen, handleDocumentKeyDown]);

    if (!isOpen) return null;

    return (
        <div
            role="presentation"
            className={clsx(
                'fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50',
                className
            )}
            onClick={handleBackdropClick}
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
                    <CategoriesSidebar
                        categories={categories}
                        selectedCategory={selectedCategory}
                        searchQuery={searchQuery}
                        onCategoryChange={setSelectedCategory}
                        onSearchChange={setSearchQuery}
                    />

                    <ShortcutsContent
                        searchQuery={searchQuery}
                        selectedCategory={selectedCategory}
                        categories={categories}
                        filteredShortcuts={filteredShortcuts}
                        registry={registry}
                    />
                </div>

                {/* Footer */}
                <div className="px-6 py-4 bg-gray-50 border-t border-gray-200">
                    <div className="flex justify-between items-center text-sm text-gray-600">
                        <span>
                            Press <kbd className="px-2 py-1 bg-gray-200 rounded">Esc</kbd> to close
                        </span>
                        <span>{filteredShortcuts.length} shortcuts shown</span>
                    </div>
                </div>
            </div>
        </div>
    );
};

KeyboardShortcutHelp.displayName = 'KeyboardShortcutHelp';

export type {
    KeybindingAction,
    KeyboardShortcutProps,
    KeyboardShortcutRegistry,
    ShortcutCategory,
    ShortcutCategoryType,
} from './internals';

export default KeyboardShortcutHelp;
