/**
 * SQL Workspace Page
 *
 * Full-featured SQL workspace for authoring, running, and managing queries.
 * See spec: docs/web-page-specs/14_sql_workspace_page.md
 *
 * Features:
 * - Schema-aware SQL editor
 * - AI-powered query assistance
 * - Natural language to SQL conversion
 * - Query optimization suggestions
 *
 * @doc.type page
 * @doc.purpose SQL query editor with AI-powered assistance
 * @doc.layer frontend
 */

import React, { useState, useCallback } from 'react';
import {
  Play,
  Save,
  Download,
  FileText,
  Clock,
  Database,
  ChevronRight,
  Bookmark,
  Sparkles,
  MessageSquare,
  Lightbulb,
  Zap,
  Send,
  Wand2,
} from 'lucide-react';
import {
  cn,
  cardStyles,
  textStyles,
  bgStyles,
  buttonStyles,
  inputStyles,
    tableStyles,
} from '../lib/theme';
import { SavedQueries, type SavedQuery } from '../components/sql/SavedQueries';/**
 * Mock schema data
 */
const mockSchemas = [
  {
    name: 'user_events',
    tables: ['events', 'sessions', 'page_views'],
  },
  {
    name: 'transactions',
    tables: ['orders', 'payments', 'refunds'],
  },
  {
    name: 'analytics',
    tables: ['metrics', 'aggregates', 'reports'],
  },
];

/**
 * Mock query history
 */
const mockQueryHistory = [
  { id: 1, query: 'SELECT * FROM events LIMIT 100', timestamp: '2 min ago', duration: '0.23s' },
  { id: 2, query: 'SELECT COUNT(*) FROM sessions WHERE date > ...', timestamp: '15 min ago', duration: '1.45s' },
  { id: 3, query: 'SELECT user_id, SUM(amount) FROM orders ...', timestamp: '1 hour ago', duration: '3.21s' },
];

/**
 * AI Query suggestions
 */
const AI_SUGGESTIONS = [
  {
    id: '1',
    type: 'optimization' as const,
    message: 'Add an index on timestamp column for faster queries',
    action: 'CREATE INDEX idx_timestamp ON events(timestamp);',
  },
  {
    id: '2',
    type: 'suggestion' as const,
    message: 'Consider using BETWEEN for date ranges',
    action: "WHERE timestamp BETWEEN '2024-01-01' AND '2024-01-31'",
  },
];

/**
 * AI Query Assist Panel
 */
function AIQueryAssist({
  onApply,
  onClose,
}: {
  onApply: (sql: string) => void;
  onClose: () => void;
}) {
  const [naturalQuery, setNaturalQuery] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedSql, setGeneratedSql] = useState('');

  const handleGenerate = useCallback(async () => {
    if (!naturalQuery.trim()) return;
    setIsGenerating(true);
    // Simulate AI generation
    await new Promise((resolve) => setTimeout(resolve, 1000));
    setGeneratedSql(`-- Generated from: "${naturalQuery}"
SELECT 
  user_id,
  event_type,
  COUNT(*) as event_count,
  MAX(timestamp) as last_event
FROM events
WHERE timestamp > NOW() - INTERVAL 7 DAY
GROUP BY user_id, event_type
ORDER BY event_count DESC
LIMIT 100;`);
    setIsGenerating(false);
  }, [naturalQuery]);

  return (
    <div className={cn(
      'bg-gradient-to-br from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20',
      'border border-purple-200 dark:border-purple-800',
      'rounded-xl p-4 mb-4'
    )}>
      <div className="flex items-center gap-2 mb-3">
        <Sparkles className="h-5 w-5 text-purple-500" />
        <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
          AI Query Assist
        </span>
      </div>

      {/* Natural language input */}
      <div className="flex gap-2 mb-3">
        <input
          type="text"
          value={naturalQuery}
          onChange={(e) => setNaturalQuery(e.target.value)}
          placeholder="Describe what you want to query... e.g., 'Show me top users by event count this week'"
          className={cn(
            'flex-1 px-3 py-2 rounded-lg text-sm',
            'bg-white dark:bg-gray-800',
            'border border-purple-200 dark:border-purple-700',
            'text-gray-900 dark:text-gray-100',
            'placeholder-gray-400',
            'focus:ring-2 focus:ring-purple-500 focus:border-transparent',
            'outline-none'
          )}
          onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
        />
        <button
          onClick={handleGenerate}
          disabled={!naturalQuery.trim() || isGenerating}
          className={cn(
            'px-4 py-2 rounded-lg',
            'bg-purple-600 hover:bg-purple-700',
            'text-white text-sm font-medium',
            'disabled:opacity-50 disabled:cursor-not-allowed',
            'transition-colors',
            'flex items-center gap-2'
          )}
        >
          {isGenerating ? (
            <>
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Generating...
            </>
          ) : (
            <>
              <Wand2 className="h-4 w-4" />
              Generate
            </>
          )}
        </button>
      </div>

      {/* Generated SQL */}
      {generatedSql && (
        <div className="mt-3">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-gray-500">Generated SQL:</span>
            <button
              onClick={() => onApply(generatedSql)}
              className={cn(
                'text-xs px-2 py-1 rounded',
                'bg-purple-600 hover:bg-purple-700',
                'text-white',
                'transition-colors'
              )}
            >
              Apply to Editor
            </button>
          </div>
          <pre className={cn(
            'text-xs font-mono p-3 rounded-lg overflow-x-auto',
            'bg-gray-900 text-gray-100'
          )}>
            {generatedSql}
          </pre>
        </div>
      )}

      {/* Quick suggestions */}
      <div className="mt-3 flex flex-wrap gap-2">
        {['Show top 10 users', 'Count events by type', 'Recent orders today'].map((suggestion) => (
          <button
            key={suggestion}
            onClick={() => setNaturalQuery(suggestion)}
            className={cn(
              'text-xs px-2 py-1 rounded-full',
              'bg-white dark:bg-gray-800',
              'border border-gray-200 dark:border-gray-700',
              'text-gray-600 dark:text-gray-400',
              'hover:border-purple-300 dark:hover:border-purple-700',
              'transition-colors'
            )}
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
}

/**
 * AI Suggestions Sidebar
 */
function AISuggestionsSidebar({ suggestions }: { suggestions: typeof AI_SUGGESTIONS }) {
  return (
    <div className={cn(cardStyles.base, 'overflow-hidden')}>
      <div className={cn(cardStyles.header, 'flex items-center gap-2')}>
        <Lightbulb className="h-4 w-4 text-amber-500" />
        <span className={textStyles.h4}>AI Suggestions</span>
      </div>
      <div className="p-2 space-y-2">
        {suggestions.map((suggestion) => (
          <div
            key={suggestion.id}
            className={cn(
              'p-3 rounded-lg',
              'bg-gray-50 dark:bg-gray-800',
              'border border-gray-100 dark:border-gray-700'
            )}
          >
            <div className="flex items-start gap-2">
              {suggestion.type === 'optimization' ? (
                <Zap className="h-4 w-4 text-amber-500 mt-0.5" />
              ) : (
                <MessageSquare className="h-4 w-4 text-blue-500 mt-0.5" />
              )}
              <div className="flex-1">
                <p className="text-sm text-gray-700 dark:text-gray-300">{suggestion.message}</p>
                <code className="text-xs text-gray-500 dark:text-gray-400 mt-1 block truncate">
                  {suggestion.action}
                </code>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Mock result data
 */
const mockResults = {
  columns: ['id', 'user_id', 'event_type', 'timestamp', 'properties'],
  rows: [
    ['evt-001', 'usr-123', 'page_view', '2024-01-12 10:30:00', '{"page": "/home"}'],
    ['evt-002', 'usr-456', 'click', '2024-01-12 10:31:15', '{"button": "signup"}'],
    ['evt-003', 'usr-123', 'form_submit', '2024-01-12 10:32:00', '{"form": "contact"}'],
    ['evt-004', 'usr-789', 'page_view', '2024-01-12 10:33:45', '{"page": "/pricing"}'],
    ['evt-005', 'usr-456', 'purchase', '2024-01-12 10:35:00', '{"amount": 99.99}'],
  ],
};

/**
 * SQL Workspace Page Component
 *
 * @returns JSX element
 */
export function SqlWorkspacePage(): React.ReactElement {
  const [query, setQuery] = useState('SELECT * FROM events\nWHERE timestamp > NOW() - INTERVAL 1 DAY\nLIMIT 100;');
  const [isRunning, setIsRunning] = useState(false);
  const [hasResults, setHasResults] = useState(false);
  const [expandedSchema, setExpandedSchema] = useState<string | null>('user_events');
  const [sidebarTab, setSidebarTab] = useState<'schema' | 'saved' | 'history'>('schema');
  const [showAIAssist, setShowAIAssist] = useState(false);

  const handleSelectSavedQuery = (savedQuery: SavedQuery) => {
    setQuery(savedQuery.sql);
  };

  const handleRunQuery = () => {
    setIsRunning(true);
    // Simulate query execution
    setTimeout(() => {
      setIsRunning(false);
      setHasResults(true);
    }, 1000);
  };

  const handleApplyAISql = useCallback((sql: string) => {
    setQuery(sql);
    setShowAIAssist(false);
  }, []);

  return (
    <div className={cn('min-h-screen', bgStyles.page)}>
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
        {/* Header */}
        <div className={cn(cardStyles.base, cardStyles.padded, 'mb-6')}>
          <div className="flex items-center justify-between">
            <div>
              <h1 className={textStyles.h1}>SQL Workspace</h1>
              <p className={textStyles.muted}>
                Run ad-hoc queries against your datasets with schema-aware assistance
              </p>
            </div>
            <div className="flex gap-2">
              <button
                onClick={() => setShowAIAssist(!showAIAssist)}
                className={cn(
                  buttonStyles.secondary,
                  'flex items-center gap-2',
                  showAIAssist && 'bg-purple-100 dark:bg-purple-900/30 border-purple-300 dark:border-purple-700'
                )}
              >
                <Sparkles className="h-4 w-4" />
                AI Assist
              </button>
              <button className={cn(buttonStyles.secondary, 'flex items-center gap-2')}>
                <Save className="h-4 w-4" />
                Save Query
              </button>
              <button
                onClick={handleRunQuery}
                disabled={isRunning}
                className={cn(buttonStyles.primary, 'flex items-center gap-2')}
              >
                <Play className="h-4 w-4" />
                {isRunning ? 'Running...' : 'Run Query'}
              </button>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Schema Browser Sidebar */}
          <div className="lg:col-span-1 space-y-4">
            {/* Sidebar Tabs */}
            <div className={cn(cardStyles.base, 'overflow-hidden')}>
              <div className="flex border-b border-gray-200 dark:border-gray-700">
                <button
                  onClick={() => setSidebarTab('schema')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'schema'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Database className="h-4 w-4" />
                  Schema
                </button>
                <button
                  onClick={() => setSidebarTab('saved')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'saved'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Bookmark className="h-4 w-4" />
                  Saved
                </button>
                <button
                  onClick={() => setSidebarTab('history')}
                  className={cn(
                    'flex-1 flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium',
                    sidebarTab === 'history'
                      ? 'bg-white dark:bg-gray-800 border-b-2 border-blue-500 text-blue-600'
                      : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
                  )}
                >
                  <Clock className="h-4 w-4" />
                  History
                </button>
              </div>

              {/* Schema Tab */}
              {sidebarTab === 'schema' && (
                <div className="p-2">
                  {mockSchemas.map((schema) => (
                    <div key={schema.name} className="mb-1">
                      <button
                        onClick={() => setExpandedSchema(expandedSchema === schema.name ? null : schema.name)}
                        className={cn(
                          'w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-sm',
                          'hover:bg-gray-100 dark:hover:bg-gray-700'
                        )}
                      >
                        <ChevronRight
                          className={cn(
                            'h-4 w-4 transition-transform',
                            expandedSchema === schema.name && 'rotate-90'
                          )}
                        />
                        <span className={textStyles.h4}>{schema.name}</span>
                      </button>
                      {expandedSchema === schema.name && (
                        <div className="ml-6 mt-1 space-y-1">
                          {schema.tables.map((table) => (
                            <button
                              key={table}
                              onClick={() => setQuery((q) => q + `\n-- ${schema.name}.${table}`)}
                              className={cn(
                                'w-full text-left px-2 py-1 text-sm rounded',
                                'hover:bg-blue-50 dark:hover:bg-blue-900/30',
                                textStyles.small
                              )}
                            >
                              📋 {table}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* Saved Queries Tab */}
              {sidebarTab === 'saved' && (
                <SavedQueries
                  onSelect={handleSelectSavedQuery}
                  currentSql={query}
                  className="h-[400px]"
                />
              )}

              {/* History Tab */}
              {sidebarTab === 'history' && (
                <div className="p-2 space-y-1">
                  {mockQueryHistory.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => setQuery(item.query)}
                      className={cn(
                        'w-full text-left p-2 rounded text-sm',
                        'hover:bg-gray-100 dark:hover:bg-gray-700'
                      )}
                    >
                      <p className={cn(textStyles.mono, 'truncate text-xs')}>{item.query}</p>
                      <p className={cn(textStyles.xs, 'mt-1')}>
                        {item.timestamp} • {item.duration}
                      </p>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Editor and Results */}
          <div className="lg:col-span-3 space-y-6">
            {/* AI Assist Panel */}
            {showAIAssist && (
              <AIQueryAssist
                onApply={handleApplyAISql}
                onClose={() => setShowAIAssist(false)}
              />
            )}

            {/* SQL Editor */}
            <div className={cn(cardStyles.base)}>
              <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                <h3 className={textStyles.h4}>Query Editor</h3>
                <div className="flex gap-2">
                  <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Format</button>
                  <button className={cn(buttonStyles.ghost, buttonStyles.sm)}>Clear</button>
                </div>
              </div>
              <div className="p-4">
                <textarea
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="-- Write your SQL query here
SELECT * FROM your_table
LIMIT 100;"
                  className={cn(
                    'w-full h-48 font-mono text-sm p-4 rounded-lg resize-y',
                    'bg-gray-50 dark:bg-gray-900',
                    'border border-gray-200 dark:border-gray-700',
                    'text-gray-900 dark:text-white',
                    'focus:ring-2 focus:ring-blue-500 focus:border-transparent'
                  )}
                />
              </div>
            </div>

            {/* Results */}
            <div className={cn(cardStyles.base)}>
              <div className={cn(cardStyles.header, 'flex items-center justify-between')}>
                <div className="flex items-center gap-4">
                  <h3 className={textStyles.h4}>Results</h3>
                  {hasResults && (
                    <span className={textStyles.xs}>
                      {mockResults.rows.length} rows • 0.23s
                    </span>
                  )}
                </div>
                {hasResults && (
                  <button className={cn(buttonStyles.ghost, buttonStyles.sm, 'flex items-center gap-1')}>
                    <Download className="h-3 w-3" />
                    Export CSV
                  </button>
                )}
              </div>

              {hasResults ? (
                <div className={tableStyles.container}>
                  <table className={tableStyles.table}>
                    <thead className={tableStyles.thead}>
                      <tr>
                        {mockResults.columns.map((col) => (
                          <th key={col} className={tableStyles.th}>{col}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className={tableStyles.tbody}>
                      {mockResults.rows.map((row, i) => (
                        <tr key={i} className={tableStyles.tr}>
                          {row.map((cell, j) => (
                            <td key={j} className={cn(tableStyles.td, 'font-mono text-xs')}>
                              {cell}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className={cn(textStyles.muted, 'text-center py-12')}>
                  {isRunning ? (
                    <div className="flex items-center justify-center gap-2">
                      <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                      <span>Executing query...</span>
                    </div>
                  ) : (
                    'Run a query to see results'
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

