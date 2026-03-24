/**
 * HelpPanel component - Full help center modal
 * @module components/ContextualHelp/HelpPanel
 */

import clsx from 'clsx';
import React, { useEffect, useState } from 'react';

import { helpContentManager } from './manager-singleton';

import type { HelpContent, HelpPanelProps, HelpCategory } from './types';

/**
 * PanelHeader - Help panel header with close button
 * @internal
 */
const PanelHeader: React.FC<{ onClose: () => void }> = ({ onClose }) => (
    <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
        <div className="flex items-center space-x-2">
            <span className="text-2xl">❓</span>
            <h2 className="text-xl font-semibold text-gray-900">Help Center</h2>
        </div>

        <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            data-testid="close-help-panel"
        >
            <svg
                className="w-6 h-6"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
            >
                <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                />
            </svg>
        </button>
    </div>
);

/**
 * SearchInput - Help panel search input
 * @internal
 */
const SearchInput: React.FC<{
    value: string;
    onChange: (value: string) => void;
}> = ({ value, onChange }) => (
    <div className="px-6 py-4 border-b border-gray-200">
        <div className="relative">
            <input
                type="text"
                placeholder="Search help topics..."
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                data-testid="help-search"
            />
            <svg
                className="absolute left-3 top-2.5 w-5 h-5 text-gray-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
            >
                <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                />
            </svg>
        </div>
    </div>
);

/**
 * Help categories for navigation
 *
 * @internal
 */
const HELP_CATEGORIES: HelpCategory[] = [
    { id: 'all', name: 'All Topics', icon: '📚' },
    { id: 'getting-started', name: 'Getting Started', icon: '🚀' },
    { id: 'canvas', name: 'Canvas', icon: '🎨' },
    { id: 'collaboration', name: 'Collaboration', icon: '👥' },
    { id: 'shortcuts', name: 'Shortcuts', icon: '⌨️' },
    { id: 'troubleshooting', name: 'Troubleshooting', icon: '🔧' },
];

/**
 * HelpPanel component provides a full help center interface
 *
 * Features:
 * - Search across all help topics
 * - Category filtering
 * - Detailed article view
 * - Related topics navigation
 * - Video tutorials (if configured)
 * - External links
 *
 * @param isOpen - Whether the help panel is displayed
 * @param onClose - Callback when panel should close
 * @param context - Optional context for pre-filtered help
 * @param searchQuery - Initial search query
 * @param className - Additional CSS classes
 *
 * @example
 * ```typescript
 * <HelpPanel
 *   isOpen={showHelp}
 *   onClose={() => setShowHelp(false)}
 *   context="canvas"
 * />
 * ```
 */
export const HelpPanel: React.FC<HelpPanelProps> = ({
    isOpen,
    onClose,
    context,
    searchQuery: initialSearchQuery = '',
    className,
}) => {
    const [searchQuery, setSearchQuery] = useState(initialSearchQuery);
    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [selectedContent, setSelectedContent] = useState<HelpContent | null>(null);
    const [searchResults, setSearchResults] = useState<HelpContent[]>([]);

    useEffect(() => {
        if (searchQuery) {
            setSearchResults(helpContentManager.search(searchQuery));
        } else if (context) {
            setSearchResults(helpContentManager.getContextualHelp(context));
        } else if (selectedCategory === 'all') {
            setSearchResults(helpContentManager.getAllContent());
        } else {
            setSearchResults(
                helpContentManager.getContentByCategory(selectedCategory as HelpContent['category'])
            );
        }
    }, [searchQuery, selectedCategory, context]);
    if (!isOpen) return null;
    const backDropClasses = clsx('fixed inset-0 z-50 flex', className);
    const panelClasses = clsx(
        'relative ml-auto w-full max-w-2xl',
        'bg-white shadow-xl flex flex-col h-full'
    );

    const handleCategoryClick = (categoryId: string) => {
        setSelectedCategory(categoryId);
        setSearchQuery('');
        setSelectedContent(null);
    };

    return (
        <div className={backDropClasses}>
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black bg-opacity-50"
                onClick={onClose}
                onKeyDown={(e) => {
                    if (e.key === 'Escape') onClose();
                }}
                role="button"
                tabIndex={0}
            />
            {/* Panel */}
            <div className={panelClasses}>
                <PanelHeader onClose={onClose} />
                <SearchInput value={searchQuery} onChange={setSearchQuery} />
                <div className="flex flex-1 overflow-hidden">
                    {/* Sidebar */}
                    <div className="w-48 bg-gray-50 border-r border-gray-200 p-4 overflow-y-auto">
                        <h3 className="text-sm font-medium text-gray-900 mb-3">Categories</h3>
                        <div className="space-y-1">
                            {HELP_CATEGORIES.map((category) => (
                                <button
                                    key={category.id}
                                    onClick={() => handleCategoryClick(category.id)}
                                    className={clsx(
                                        'w-full text-left px-3 py-2 rounded-md text-sm transition-colors flex items-center',
                                        selectedCategory === category.id
                                            ? 'bg-blue-100 text-blue-900'
                                            : 'text-gray-700 hover:bg-gray-100'
                                    )}
                                    data-testid={`help-category-${category.id}`}
                                >
                                    <span className="mr-2">{category.icon}</span>
                                    {category.name}
                                </button>
                            ))}
                        </div>
                    </div>
                    {/* Content */}
                    <div className="flex-1 overflow-y-auto">
                        {selectedContent ? (
                            <ArticleView
                                content={selectedContent}
                                onBack={() => setSelectedContent(null)}
                            />
                        ) : (
                            <ResultsView
                                results={searchResults}
                                searchQuery={searchQuery}
                                context={context}
                                selectedCategory={selectedCategory}
                                categories={HELP_CATEGORIES}
                                onSelectContent={setSelectedContent}
                            />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

/**
 * Article view - detailed help article display
 * @internal
 */
const ArticleView: React.FC<{
    content: HelpContent;
    onBack: () => void;
}> = ({ content, onBack }) => {
    const relatedContent = helpContentManager.getRelatedContent(content.id);

    return (
        <div className="p-6">
            <button
                onClick={onBack}
                className="flex items-center text-sm text-gray-600 hover:text-gray-900 mb-4"
            >
                <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M15 19l-7-7 7-7"
                    />
                </svg>
                Back to results
            </button>

            <ArticleContent content={content} />

            {/* Related topics */}
            {relatedContent.length > 0 && (
                <div className="border-t pt-6">
                    <h4 className="text-lg font-semibold mb-4">Related Topics</h4>
                    <div className="grid gap-3">
                        {relatedContent.map(related => (
                            <button
                                key={related.id}
                                onClick={() => window.scrollTo(0, 0)}
                                className="text-left p-3 border border-gray-200 rounded-lg hover:border-blue-300 transition-colors"
                            >
                                <h5 className="font-medium text-gray-900">{related.title}</h5>
                                <p className="text-sm text-gray-600 mt-1">
                                    {related.content.substring(0, 100)}...
                                </p>
                            </button>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
};

/**
 * ArticleContent - Renders article details
 * @internal
 */
const ArticleContent: React.FC<{ content: HelpContent }> = ({ content }) => {
    return (
        <div className="mb-6">
            <h3 className="text-2xl font-bold text-gray-900 mb-2">{content.title}</h3>

            <div className="flex items-center space-x-2 mb-4">
                <span className="px-2 py-1 text-xs font-medium text-blue-700 bg-blue-100 rounded-full">
                    {content.category.replace('-', ' ')}
                </span>
            </div>

            <div className="prose max-w-none">
                <p className="text-gray-700 leading-relaxed whitespace-pre-wrap">{content.content}</p>
            </div>

            {content.videoUrl && (
                <div className="mt-6">
                    <h4 className="text-lg font-semibold mb-2">Video Tutorial</h4>
                    <div className="aspect-video bg-gray-100 rounded-lg flex items-center justify-center">
                        <span className="text-gray-500">Video player would go here</span>
                    </div>
                </div>
            )}

            {content.externalLink && (
                <div className="mt-4">
                    <a
                        href={content.externalLink}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center text-blue-600 hover:text-blue-800"
                    >
                        Learn more
                        <svg
                            className="w-4 h-4 ml-1"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                        >
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                            />
                        </svg>
                    </a>
                </div>
            )}
        </div>
    );
};

/**
 * Results view - list of help topics
 * @internal
 */
const ResultsView: React.FC<{
    results: HelpContent[];
    searchQuery: string;
    context?: string;
    selectedCategory: string;
    categories: HelpCategory[];
    onSelectContent: (content: HelpContent) => void;
}> = ({ results, searchQuery, context, selectedCategory, categories, onSelectContent }) => {
    const resultTitle = searchQuery
        ? `Search results for "${searchQuery}"`
        : context
            ? `Help for ${context}`
            : selectedCategory === 'all'
                ? 'All Help Topics'
                : categories.find(c => c.id === selectedCategory)?.name;

    return (
        <div className="p-6">
            <div className="mb-4">
                <h3 className="text-lg font-semibold text-gray-900">{resultTitle}</h3>
                <p className="text-sm text-gray-600">
                    {results.length} {results.length === 1 ? 'result' : 'results'}
                </p>
            </div>

            <div className="space-y-4">
                {results.map(content => (
                    <button
                        key={content.id}
                        onClick={() => onSelectContent(content)}
                        className="w-full text-left p-4 border border-gray-200 rounded-lg hover:border-blue-300 transition-colors"
                        data-testid={`help-item-${content.id}`}
                    >
                        <h4 className="font-medium text-gray-900 mb-2">{content.title}</h4>
                        <p className="text-sm text-gray-600 mb-2">
                            {content.content.length > 150
                                ? `${content.content.substring(0, 150)}...`
                                : content.content}
                        </p>
                        <div className="flex items-center space-x-2">
                            <span className="px-2 py-1 text-xs font-medium text-gray-600 bg-gray-100 rounded-full">
                                {content.category.replace('-', ' ')}
                            </span>
                            {content.keywords.slice(0, 3).map(keyword => (
                                <span key={keyword} className="text-xs text-blue-600">
                                    #{keyword}
                                </span>
                            ))}
                        </div>
                    </button>
                ))}
            </div>

            {results.length === 0 && (
                <div className="text-center py-12">
                    <div className="text-4xl mb-4">🔍</div>
                    <h3 className="text-lg font-medium text-gray-900 mb-2">No results found</h3>
                    <p className="text-gray-600">Try adjusting your search terms or browse by category.</p>
                </div>
            )}
        </div>
    );
};

export default HelpPanel;
