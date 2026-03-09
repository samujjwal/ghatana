/**
 * Global Search Component
 * 
 * Command palette style global search for navigating and finding resources.
 * Supports keyboard shortcuts (Cmd/Ctrl + K) to open.
 * 
 * @doc.type component
 * @doc.purpose Global search and navigation
 * @doc.layer frontend
 * @doc.pattern Modal Component
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router';
import {
    Search,
    Database,
    Workflow,
    FileText,
    Settings,
    BarChart3,
    Shield,
    Bell,
    Command,
    ArrowRight,
} from 'lucide-react';
import { cn, textStyles, bgStyles, inputStyles } from '../../lib/theme';

/**
 * Search result item
 */
interface SearchResult {
    id: string;
    title: string;
    description?: string;
    type: 'collection' | 'workflow' | 'page' | 'action';
    icon: React.ReactNode;
    path?: string;
    action?: () => void;
}

/**
 * Quick navigation items
 */
const quickNavItems: SearchResult[] = [
    { id: 'nav-dashboard', title: 'Dashboard', type: 'page', icon: <BarChart3 className="h-4 w-4" />, path: '/dashboard' },
    { id: 'nav-collections', title: 'Collections', type: 'page', icon: <Database className="h-4 w-4" />, path: '/collections' },
    { id: 'nav-workflows', title: 'Workflows', type: 'page', icon: <Workflow className="h-4 w-4" />, path: '/workflows' },
    { id: 'nav-sql', title: 'SQL Workspace', type: 'page', icon: <FileText className="h-4 w-4" />, path: '/sql' },
    { id: 'nav-lineage', title: 'Lineage Explorer', type: 'page', icon: <Workflow className="h-4 w-4" />, path: '/lineage' },
    { id: 'nav-governance', title: 'Governance', type: 'page', icon: <Shield className="h-4 w-4" />, path: '/governance' },
    { id: 'nav-alerts', title: 'Alerts', type: 'page', icon: <Bell className="h-4 w-4" />, path: '/alerts' },
    { id: 'nav-settings', title: 'Settings', type: 'page', icon: <Settings className="h-4 w-4" />, path: '/settings' },
];

/**
 * Mock search results
 */
const mockCollections: SearchResult[] = [
    { id: 'col-1', title: 'user_events', description: 'User activity events', type: 'collection', icon: <Database className="h-4 w-4" />, path: '/collections/col-1' },
    { id: 'col-2', title: 'transactions', description: 'Payment transactions', type: 'collection', icon: <Database className="h-4 w-4" />, path: '/collections/col-2' },
    { id: 'col-3', title: 'analytics_data', description: 'Analytics aggregates', type: 'collection', icon: <Database className="h-4 w-4" />, path: '/collections/col-3' },
];

const mockWorkflows: SearchResult[] = [
    { id: 'wf-1', title: 'ETL Pipeline', description: 'Daily data transformation', type: 'workflow', icon: <Workflow className="h-4 w-4" />, path: '/workflows/wf-1' },
    { id: 'wf-2', title: 'Fraud Detection', description: 'Real-time fraud analysis', type: 'workflow', icon: <Workflow className="h-4 w-4" />, path: '/workflows/wf-2' },
];

interface GlobalSearchProps {
    isOpen: boolean;
    onClose: () => void;
}

/**
 * Global Search Component
 */
export function GlobalSearch({ isOpen, onClose }: GlobalSearchProps): React.ReactElement | null {
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);
    const navigate = useNavigate();

    // Filter results based on query
    const getResults = useCallback((): SearchResult[] => {
        if (!query.trim()) {
            return quickNavItems;
        }

        const lowerQuery = query.toLowerCase();
        const allItems = [...quickNavItems, ...mockCollections, ...mockWorkflows];

        return allItems.filter(
            (item) =>
                item.title.toLowerCase().includes(lowerQuery) ||
                item.description?.toLowerCase().includes(lowerQuery)
        );
    }, [query]);

    const results = getResults();

    // Focus input when opened
    useEffect(() => {
        if (isOpen) {
            inputRef.current?.focus();
            setQuery('');
            setSelectedIndex(0);
        }
    }, [isOpen]);

    // Handle keyboard navigation
    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            switch (e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    setSelectedIndex((i) => Math.min(i + 1, results.length - 1));
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    setSelectedIndex((i) => Math.max(i - 1, 0));
                    break;
                case 'Enter':
                    e.preventDefault();
                    const selected = results[selectedIndex];
                    if (selected) {
                        if (selected.path) {
                            navigate(selected.path);
                        } else if (selected.action) {
                            selected.action();
                        }
                        onClose();
                    }
                    break;
                case 'Escape':
                    onClose();
                    break;
            }
        },
        [results, selectedIndex, navigate, onClose]
    );

    // Handle result click
    const handleResultClick = (result: SearchResult) => {
        if (result.path) {
            navigate(result.path);
        } else if (result.action) {
            result.action();
        }
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 overflow-y-auto">
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/50 backdrop-blur-sm"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="relative min-h-screen flex items-start justify-center pt-[15vh] px-4">
                <div
                    className={cn(
                        'w-full max-w-xl rounded-xl shadow-2xl overflow-hidden',
                        bgStyles.surface
                    )}
                >
                    {/* Search Input */}
                    <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                        <Search className="h-5 w-5 text-gray-400" />
                        <input
                            ref={inputRef}
                            type="text"
                            value={query}
                            onChange={(e) => {
                                setQuery(e.target.value);
                                setSelectedIndex(0);
                            }}
                            onKeyDown={handleKeyDown}
                            placeholder="Search collections, workflows, pages..."
                            className={cn(
                                'flex-1 bg-transparent border-none outline-none',
                                'text-gray-900 dark:text-white placeholder-gray-400'
                            )}
                        />
                        <kbd className="hidden sm:inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-gray-400 bg-gray-100 dark:bg-gray-700 rounded">
                            ESC
                        </kbd>
                    </div>

                    {/* Results */}
                    <div className="max-h-[400px] overflow-y-auto py-2">
                        {results.length === 0 ? (
                            <div className="px-4 py-8 text-center">
                                <p className={textStyles.muted}>No results found for "{query}"</p>
                            </div>
                        ) : (
                            <>
                                {!query && (
                                    <p className={cn(textStyles.xs, 'px-4 py-2')}>Quick Navigation</p>
                                )}
                                {results.map((result, index) => (
                                    <button
                                        key={result.id}
                                        onClick={() => handleResultClick(result)}
                                        onMouseEnter={() => setSelectedIndex(index)}
                                        className={cn(
                                            'w-full flex items-center gap-3 px-4 py-2 text-left',
                                            index === selectedIndex
                                                ? 'bg-blue-50 dark:bg-blue-900/30'
                                                : 'hover:bg-gray-50 dark:hover:bg-gray-700'
                                        )}
                                    >
                                        <div className={cn(
                                            'p-2 rounded-lg',
                                            result.type === 'collection' && 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-400',
                                            result.type === 'workflow' && 'bg-purple-100 dark:bg-purple-900 text-purple-600 dark:text-purple-400',
                                            result.type === 'page' && 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400',
                                            result.type === 'action' && 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-400'
                                        )}>
                                            {result.icon}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className={textStyles.h4}>{result.title}</p>
                                            {result.description && (
                                                <p className={cn(textStyles.xs, 'truncate')}>{result.description}</p>
                                            )}
                                        </div>
                                        {index === selectedIndex && (
                                            <ArrowRight className="h-4 w-4 text-gray-400" />
                                        )}
                                    </button>
                                ))}
                            </>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between">
                        <div className="flex items-center gap-4 text-xs text-gray-400">
                            <span className="flex items-center gap-1">
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↑</kbd>
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↓</kbd>
                                to navigate
                            </span>
                            <span className="flex items-center gap-1">
                                <kbd className="px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 rounded">↵</kbd>
                                to select
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

/**
 * Hook to manage global search state
 */
export function useGlobalSearch() {
    const [isOpen, setIsOpen] = useState(false);

    const open = useCallback(() => setIsOpen(true), []);
    const close = useCallback(() => setIsOpen(false), []);
    const toggle = useCallback(() => setIsOpen((prev) => !prev), []);

    // Keyboard shortcut (Cmd/Ctrl + K)
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                toggle();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [toggle]);

    return { isOpen, open, close, toggle };
}

export default GlobalSearch;
