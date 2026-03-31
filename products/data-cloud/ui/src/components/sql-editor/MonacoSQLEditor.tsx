/**
 * @fileoverview Monaco SQL Editor Component
 * SQL editor with syntax highlighting, autocomplete, and query execution
 * 
 * @doc.type component
 * @doc.purpose Interactive SQL editor for data exploration
 * @doc.layer presentation
 * @doc.pattern Controlled Component
 */

import { useRef, useEffect } from 'react';
import Editor, { OnMount } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';

// ============================================================================
// Types
// ============================================================================

export interface MonacoSQLEditorProps {
  /** SQL query value */
  value: string;
  /** Callback when value changes */
  onChange: (value: string) => void;
  /** Callback when user executes query (Ctrl+Enter) */
  onExecute?: () => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Editor height */
  height?: string;
  /** Theme (light or dark) */
  theme?: 'light' | 'dark';
  /** Show line numbers */
  lineNumbers?: boolean;
  /** Enable minimap */
  minimap?: boolean;
  /** Font size */
  fontSize?: number;
  /** Available table names for autocomplete */
  tables?: string[];
  /** Available column names for autocomplete */
  columns?: Record<string, string[]>;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Monaco SQL Editor
 * @doc.purpose Provide rich SQL editing experience with IntelliSense
 * 
 * @example
 * ```tsx
 * <MonacoSQLEditor
 *   value={query}
 *   onChange={setQuery}
 *   onExecute={handleExecute}
 *   tables={['users', 'orders', 'products']}
 *   columns={{
 *     users: ['id', 'name', 'email'],
 *     orders: ['id', 'user_id', 'total'],
 *   }}
 * />
 * ```
 */
export function MonacoSQLEditor({
  value,
  onChange,
  onExecute,
  readOnly = false,
  height = '400px',
  theme = 'light',
  lineNumbers = true,
  minimap = false,
  fontSize = 14,
  tables = [],
  columns = {},
}: MonacoSQLEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  /**
   * Handle editor mount
   * @doc.purpose Configure editor and register custom commands
   */
  const handleEditorDidMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;

    // Register execute query command (Ctrl+Enter)
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      if (onExecute) {
        onExecute();
      }
    });

    // Configure SQL language features
    monaco.languages.registerCompletionItemProvider('sql', {
      provideCompletionItems: (model, position) => {
        const word = model.getWordUntilPosition(position);
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn,
        };

        const suggestions: any[] = [];

        // SQL Keywords
        const keywords = [
          'SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER',
          'ON', 'GROUP', 'BY', 'HAVING', 'ORDER', 'LIMIT', 'OFFSET',
          'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER',
          'TABLE', 'INDEX', 'VIEW', 'AS', 'AND', 'OR', 'NOT', 'IN', 'LIKE',
          'BETWEEN', 'IS', 'NULL', 'DISTINCT', 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX',
        ];

        keywords.forEach((keyword) => {
          suggestions.push({
            label: keyword,
            kind: monaco.languages.CompletionItemKind.Keyword,
            insertText: keyword,
            range,
          });
        });

        // Table names
        tables.forEach((table) => {
          suggestions.push({
            label: table,
            kind: monaco.languages.CompletionItemKind.Class,
            insertText: table,
            range,
            detail: 'Table',
          });
        });

        // Column names (if we can determine current table context)
        Object.entries(columns).forEach(([table, cols]) => {
          cols.forEach((column) => {
            suggestions.push({
              label: `${table}.${column}`,
              kind: monaco.languages.CompletionItemKind.Field,
              insertText: `${table}.${column}`,
              range,
              detail: `Column in ${table}`,
            });
          });
        });

        return { suggestions };
      },
    });

    // Focus editor
    editor.focus();
  };

  /**
   * Handle value change
   * @doc.purpose Propagate changes to parent component
   */
  const handleChange = (newValue: string | undefined) => {
    onChange(newValue || '');
  };

  /**
   * Format SQL query
   * @doc.purpose Auto-format SQL for readability
   */
  const formatQuery = () => {
    if (editorRef.current) {
      editorRef.current.getAction('editor.action.formatDocument')?.run();
    }
  };

  // Expose format method via ref if needed
  useEffect(() => {
    // Could expose methods via imperative handle if needed
  }, []);

  return (
    <div className="monaco-sql-editor-container" style={{ height }}>
      <Editor
        height={height}
        language="sql"
        value={value}
        onChange={handleChange}
        onMount={handleEditorDidMount}
        theme={theme === 'dark' ? 'vs-dark' : 'vs-light'}
        options={{
          readOnly,
          minimap: { enabled: minimap },
          fontSize,
          lineNumbers: lineNumbers ? 'on' : 'off',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          wordWrap: 'on',
          wrappingIndent: 'indent',
          formatOnPaste: true,
          formatOnType: true,
          suggestOnTriggerCharacters: true,
          quickSuggestions: {
            other: true,
            comments: false,
            strings: false,
          },
          parameterHints: {
            enabled: true,
          },
          folding: true,
          foldingStrategy: 'indentation',
          showFoldingControls: 'always',
          matchBrackets: 'always',
          autoClosingBrackets: 'always',
          autoClosingQuotes: 'always',
          renderLineHighlight: 'all',
          selectOnLineNumbers: true,
          roundedSelection: false,
          cursorStyle: 'line',
          cursorBlinking: 'smooth',
          scrollbar: {
            vertical: 'auto',
            horizontal: 'auto',
            useShadows: false,
            verticalScrollbarSize: 10,
            horizontalScrollbarSize: 10,
          },
        }}
      />
    </div>
  );
}

/**
 * SQL Editor Toolbar Component
 * @doc.purpose Provide quick actions for SQL editor
 */
export interface SQLEditorToolbarProps {
  onExecute?: () => void;
  onFormat?: () => void;
  onClear?: () => void;
  onSave?: () => void;
  executing?: boolean;
  disabled?: boolean;
}

export function SQLEditorToolbar({
  onExecute,
  onFormat,
  onClear,
  onSave,
  executing = false,
  disabled = false,
}: SQLEditorToolbarProps) {
  return (
    <div className="flex items-center gap-2 p-2 border-b border-gray-200 dark:border-gray-700">
      <button
        onClick={onExecute}
        disabled={disabled || executing}
        className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        title="Execute Query (Ctrl+Enter)"
      >
        {executing ? 'Executing...' : 'Run Query'}
      </button>
      
      {onFormat && (
        <button
          onClick={onFormat}
          disabled={disabled}
          className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-50"
          title="Format SQL"
        >
          Format
        </button>
      )}
      
      {onClear && (
        <button
          onClick={onClear}
          disabled={disabled}
          className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-50"
          title="Clear Query"
        >
          Clear
        </button>
      )}
      
      {onSave && (
        <button
          onClick={onSave}
          disabled={disabled}
          className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-50"
          title="Save Query"
        >
          Save
        </button>
      )}
      
      <div className="ml-auto text-sm text-gray-500 dark:text-gray-400">
        Press <kbd className="px-1 py-0.5 bg-gray-100 dark:bg-gray-800 rounded">Ctrl+Enter</kbd> to execute
      </div>
    </div>
  );
}
