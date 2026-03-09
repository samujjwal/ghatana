/**
 * @ghatana/yappc-ide - Advanced Code Completion System
 * 
 * Intelligent code completion with AI-powered suggestions,
 * context-aware recommendations, and multi-language support.
 * 
 * @doc.type component
 * @doc.purpose Advanced code completion for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';

/**
 * Code completion item types
 */
export type CompletionItemType = 'function' | 'variable' | 'class' | 'method' | 'property' | 'keyword' | 'snippet' | 'ai-suggestion';

/**
 * Code completion item interface
 */
export interface CompletionItem {
  id: string;
  label: string;
  type: CompletionItemType;
  description?: string;
  documentation?: string;
  insertText: string;
  detail?: string;
  sortText?: string;
  filterText?: string;
  priority: number;
  source: 'builtin' | 'workspace' | 'ai' | 'snippet';
  language?: string;
}

/**
 * Code completion props
 */
export interface CodeCompletionProps {
  isVisible: boolean;
  position: { x: number; y: number };
  items: CompletionItem[];
  selectedIndex: number;
  onSelect: (item: CompletionItem) => void;
  onClose: () => void;
  onFilterChange: (filter: string) => void;
  className?: string;
}

/**
 * Code completion context
 */
export interface CompletionContext {
  language: string;
  prefix: string;
  line: number;
  column: number;
  file: string;
  imports: string[];
  variables: string[];
  functions: string[];
}

/**
 * Code completion provider class
 */
export class CodeCompletionProvider {
  private builtinItems: CompletionItem[] = [];
  private workspaceItems: CompletionItem[] = [];
  private snippetItems: CompletionItem[] = [];

  constructor() {
    this.initializeBuiltinItems();
    this.initializeSnippets();
  }

  /**
   * Initialize built-in items
   */
  private initializeBuiltinItems() {
    this.builtinItems = [
      // JavaScript/TypeScript keywords
      {
        id: 'function',
        label: 'function',
        type: 'keyword',
        insertText: 'function',
        documentation: 'Function declaration keyword',
        source: 'builtin',
        language: 'javascript',
        priority: 9,
      },
      {
        id: 'const',
        label: 'const',
        type: 'keyword',
        insertText: 'const',
        documentation: 'Constant declaration keyword',
        source: 'builtin',
        language: 'javascript',
        priority: 9,
      },
      {
        id: 'let',
        label: 'let',
        type: 'keyword',
        insertText: 'let',
        documentation: 'Variable declaration keyword',
        source: 'builtin',
        language: 'javascript',
        priority: 9,
      },
      {
        id: 'import',
        label: 'import',
        type: 'keyword',
        insertText: 'import ',
        documentation: 'Import statement',
        source: 'builtin',
        language: 'javascript',
        priority: 9,
      },
      {
        id: 'export',
        label: 'export',
        type: 'keyword',
        insertText: 'export ',
        documentation: 'Export statement',
        source: 'builtin',
        language: 'javascript',
        priority: 9,
      },
      // React specific
      {
        id: 'useState',
        label: 'useState',
        type: 'function',
        insertText: 'useState',
        documentation: 'React useState hook',
        source: 'builtin',
        language: 'typescript',
        priority: 8,
      },
      {
        id: 'useEffect',
        label: 'useEffect',
        type: 'function',
        insertText: 'useEffect',
        documentation: 'React useEffect hook',
        source: 'builtin',
        language: 'typescript',
        priority: 8,
      },
      {
        id: 'useCallback',
        label: 'useCallback',
        type: 'function',
        insertText: 'useCallback',
        documentation: 'React useCallback hook',
        source: 'builtin',
        language: 'typescript',
        priority: 8,
      },
      {
        id: 'useMemo',
        label: 'useMemo',
        type: 'function',
        insertText: 'useMemo',
        documentation: 'React useMemo hook',
        source: 'builtin',
        language: 'typescript',
        priority: 8,
      },
    ];
  }

  /**
   * Initialize snippets
   */
  private initializeSnippets() {
    this.snippetItems = [
      {
        id: 'react-component',
        label: 'React Component',
        type: 'snippet',
        insertText: 'const ${1:ComponentName} = () => {\n  return (\n    <div>\n      <h2>${1:ComponentName}</h2>\n      ${2:// Component content}\n    </div>\n  );\n};\n\nexport default ${1:ComponentName};',
        documentation: 'React functional component',
        source: 'snippet',
        language: 'typescript',
        priority: 7,
      },
      {
        id: 'api-endpoint',
        label: 'API Endpoint',
        type: 'snippet',
        insertText: 'app.${1:get}(\'${2:/endpoint}\', async (req: unknown, res: unknown) => {\n  try {\n    ${3:// Implementation}\n    res.json({ success: true });\n  } catch (error: unknown) {\n    res.status(500).json({ error: error instanceof Error ? error.message : \'Unknown error\' });\n  }\n});',
        documentation: 'Express.js API endpoint',
        source: 'snippet',
        language: 'typescript',
        priority: 7,
      },
    ];
  }

  /**
   * Get completions for current context
   */
  async getCompletions(context: CompletionContext): Promise<CompletionItem[]> {
    const allItems = [...this.builtinItems, ...this.workspaceItems, ...this.snippetItems];
    
    // Filter by language
    const languageFiltered = allItems.filter(item => 
      !item.language || item.language === context.language
    );

    // Filter by prefix
    const prefixFiltered = languageFiltered.filter(item =>
      item.label.toLowerCase().startsWith(context.prefix.toLowerCase()) ||
      item.filterText?.toLowerCase().startsWith(context.prefix.toLowerCase())
    );

    // Sort by priority and relevance
    const sorted = prefixFiltered.sort((a, b) => {
      // Exact matches first
      const aExact = a.label.toLowerCase() === context.prefix.toLowerCase();
      const bExact = b.label.toLowerCase() === context.prefix.toLowerCase();
      if (aExact && !bExact) return -1;
      if (!aExact && bExact) return 1;
      
      // Then by priority
      if (a.priority !== b.priority) {
        return b.priority - a.priority;
      }
      
      // Then alphabetically
      return a.label.localeCompare(b.label);
    });

    // Add AI suggestions if prefix is long enough
    if (context.prefix.length > 2) {
      const aiSuggestions = await this.getAISuggestions(context);
      sorted.push(...aiSuggestions);
    }

    return sorted.slice(0, 20); // Limit to 20 items
  }

  /**
   * Get AI-powered suggestions
   */
  private async getAISuggestions(context: CompletionContext): Promise<CompletionItem[]> {
    // Simulate AI processing delay
    await new Promise(resolve => setTimeout(resolve, 100));
    
    const suggestions: CompletionItem[] = [];
    
    if (context.prefix.length > 2) {
      suggestions.push({
        id: 'ai-suggestion-1',
        label: `${context.prefix}Handler`,
        type: 'ai-suggestion',
        insertText: `const ${context.prefix}Handler = async (\${1:params}) => {\n  \${2:// AI-generated implementation}\n};`,
        documentation: 'AI-suggested function based on context',
        source: 'ai',
        priority: 10,
      });
    }

    return suggestions;
  }

  /**
   * Update workspace items
   */
  updateWorkspaceItems(items: CompletionItem[]) {
    this.workspaceItems = items;
  }
}

/**
 * Code completion hook
 */
export const useCodeCompletion = (provider: CodeCompletionProvider) => {
  const [isVisible, setIsVisible] = useState(false);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [items, setItems] = useState<CompletionItem[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [context, setContext] = useState<CompletionContext | null>(null);

  const showCompletion = useCallback(async (ctx: CompletionContext, editorPosition: { x: number; y: number }) => {
    setContext(ctx);
    setPosition(editorPosition);
    setIsVisible(true);

    try {
      const completions = await provider.getCompletions(ctx);
      setItems(completions);
      setSelectedIndex(0);
    } catch (error) {
      console.error('Failed to get completions:', error);
      setItems([]);
    }
  }, [provider]);

  const hideCompletion = useCallback(() => {
    setIsVisible(false);
    setItems([]);
    setSelectedIndex(0);
  }, []);

  const selectItem = useCallback((item: CompletionItem) => {
    hideCompletion();
    return item;
  }, [hideCompletion]);

  const selectNext = useCallback(() => {
    setSelectedIndex(prev => (prev + 1) % items.length);
  }, [items.length]);

  const selectPrevious = useCallback(() => {
    setSelectedIndex(prev => (prev - 1 + items.length) % items.length);
  }, [items.length]);

  return {
    isVisible,
    position,
    items,
    selectedIndex,
    context,
    showCompletion,
    hideCompletion,
    selectItem,
    selectNext,
    selectPrevious,
  };
};

/**
 * Code Completion Component
 */
export const CodeCompletion: React.FC<CodeCompletionProps> = ({
  isVisible,
  position,
  items,
  selectedIndex,
  onSelect,
  onClose,
  onFilterChange,
  className = '',
}) => {
  const [filter, setFilter] = useState('');
  const listRef = useRef<HTMLDivElement>(null);

  /**
   * Filter items based on filter text
   */
  const filteredItems = items.filter(item =>
    item.label.toLowerCase().includes(filter.toLowerCase()) ||
    item.description?.toLowerCase().includes(filter.toLowerCase())
  );

  /**
   * Handle keyboard navigation
   */
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        e.stopPropagation();
        break;
      case 'ArrowUp':
        e.preventDefault();
        e.stopPropagation();
        break;
      case 'Enter':
        e.preventDefault();
        e.stopPropagation();
        if (filteredItems[selectedIndex]) {
          onSelect(filteredItems[selectedIndex]);
        }
        break;
      case 'Escape':
        e.preventDefault();
        e.stopPropagation();
        onClose();
        break;
    }
  }, [filteredItems, selectedIndex, onSelect, onClose]);

  /**
   * Handle item click
   */
  const handleItemClick = useCallback((item: CompletionItem) => {
    onSelect(item);
  }, [onSelect]);

  /**
   * Get item type icon
   */
  const getItemIcon = (type: CompletionItemType): string => {
    const icons = {
      function: 'ƒ',
      variable: 'x',
      class: 'C',
      method: 'm',
      property: 'p',
      keyword: 'k',
      snippet: '📝',
      'ai-suggestion': '✨',
    };
    return icons[type] || '•';
  };

  /**
   * Get item type color
   */
  const getItemColor = (type: CompletionItemType): string => {
    const colors = {
      function: 'text-blue-600 dark:text-blue-400',
      variable: 'text-green-600 dark:text-green-400',
      class: 'text-purple-600 dark:text-purple-400',
      method: 'text-blue-600 dark:text-blue-400',
      property: 'text-yellow-600 dark:text-yellow-400',
      keyword: 'text-red-600 dark:text-red-400',
      snippet: 'text-orange-600 dark:text-orange-400',
      'ai-suggestion': 'text-pink-600 dark:text-pink-400',
    };
    return colors[type] || 'text-gray-600 dark:text-gray-400';
  };

  /**
   * Scroll selected item into view
   */
  useEffect(() => {
    if (listRef.current && filteredItems[selectedIndex]) {
      const selectedElement = listRef.current.children[selectedIndex] as HTMLElement;
      if (selectedElement) {
        selectedElement.scrollIntoView({ block: 'nearest' });
      }
    }
  }, [selectedIndex, filteredItems]);

  if (!isVisible) return null;

  return (
    <div
      className={`fixed bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-50 ${className}`}
      style={{
        left: position.x,
        top: position.y,
        minWidth: '300px',
        maxWidth: '500px',
      }}
      onKeyDown={handleKeyDown}
    >
      {/* Filter input */}
      <div className="p-2 border-b border-gray-200 dark:border-gray-700">
        <input
          type="text"
          value={filter}
          onChange={(e) => {
            setFilter(e.target.value);
            onFilterChange(e.target.value);
          }}
          placeholder="Filter completions..."
          className="w-full px-2 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
          autoFocus
        />
      </div>

      {/* Items list */}
      <div
        ref={listRef}
        className="max-h-64 overflow-y-auto"
      >
        {filteredItems.length === 0 ? (
          <div className="p-4 text-center text-gray-500 dark:text-gray-400">
            No completions found
          </div>
        ) : (
          filteredItems.map((item, index) => (
            <div
              key={item.id}
              className={`px-3 py-2 cursor-pointer border-b border-gray-100 dark:border-gray-800 last:border-b-0 ${
                index === selectedIndex
                  ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-900 dark:text-blue-100'
                  : 'hover:bg-gray-50 dark:hover:bg-gray-800 text-gray-900 dark:text-gray-100'
              }`}
              onClick={() => handleItemClick(item)}
            >
              <div className="flex items-center space-x-2">
                <span className={`text-lg ${getItemColor(item.type)}`}>
                  {getItemIcon(item.type)}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-sm truncate">
                      {item.label}
                    </span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">
                      {item.source}
                    </span>
                  </div>
                  {item.description && (
                    <div className="text-xs text-gray-600 dark:text-gray-400 truncate">
                      {item.description}
                    </div>
                  )}
                  {item.documentation && (
                    <div className="text-xs text-gray-500 dark:text-gray-500 mt-1">
                      {item.documentation}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Footer */}
      <div className="px-3 py-1 text-xs text-gray-500 dark:text-gray-400 border-t border-gray-200 dark:border-gray-700">
        {filteredItems.length} item{filteredItems.length !== 1 ? 's' : ''}
        {selectedIndex < filteredItems.length && (
          <span className="ml-2">
            {selectedIndex + 1} of {filteredItems.length}
          </span>
        )}
      </div>
    </div>
  );
};

export default {
  CodeCompletion,
  CodeCompletionProvider,
  useCodeCompletion,
};
