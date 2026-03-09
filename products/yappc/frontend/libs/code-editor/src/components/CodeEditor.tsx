/**
 * CodeEditor Component
 * 
 * Monaco-based code editor with syntax highlighting, autocomplete,
 * and customizable themes.
 * 
 * @doc.type component
 * @doc.purpose Code editing with Monaco
 * @doc.layer shared
 * @doc.pattern Controlled Component
 */

import React, { useRef, useEffect, useCallback } from 'react';
import Editor, { OnMount, OnChange, Monaco } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';
import type { CodeEditorProps, CodeEditorConfig } from '../types';

/**
 * Default editor options
 */
const defaultOptions: editor.IStandaloneEditorConstructionOptions = {
    minimap: { enabled: false },
    fontSize: 14,
    lineNumbers: 'on',
    roundedSelection: true,
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 2,
    wordWrap: 'on',
    folding: true,
    lineDecorationsWidth: 10,
    lineNumbersMinChars: 3,
    renderLineHighlight: 'line',
    scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
        verticalScrollbarSize: 10,
        horizontalScrollbarSize: 10,
    },
    padding: { top: 10, bottom: 10 },
};

/**
 * SQL-specific keywords for autocomplete
 */
const sqlKeywords = [
    'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN',
    'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'FULL', 'CROSS', 'ON',
    'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT', 'OFFSET', 'DISTINCT',
    'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE', 'CREATE',
    'TABLE', 'INDEX', 'VIEW', 'DROP', 'ALTER', 'ADD', 'COLUMN',
    'PRIMARY KEY', 'FOREIGN KEY', 'REFERENCES', 'CONSTRAINT',
    'NULL', 'NOT NULL', 'DEFAULT', 'UNIQUE', 'CHECK',
    'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
    'AS', 'ASC', 'DESC', 'UNION', 'ALL', 'EXISTS', 'ANY', 'SOME',
    'WITH', 'RECURSIVE', 'OVER', 'PARTITION BY', 'ROW_NUMBER', 'RANK',
];

/**
 * Register SQL autocomplete provider
 */
function registerSqlAutocomplete(monaco: Monaco, schema?: Record<string, string[]>) {
    monaco.languages.registerCompletionItemProvider('sql', {
        provideCompletionItems: (model: editor.ITextModel, position: { lineNumber: number; column: number }) => {
            const word = model.getWordUntilPosition(position);
            const range = {
                startLineNumber: position.lineNumber,
                endLineNumber: position.lineNumber,
                startColumn: word.startColumn,
                endColumn: word.endColumn,
            };

            const suggestions: unknown[] = []; // use any to avoid monaco types dependency here

            // Add SQL keywords
            sqlKeywords.forEach((keyword) => {
                suggestions.push({
                    label: keyword,
                    kind: monaco.languages.CompletionItemKind.Keyword,
                    insertText: keyword,
                    range,
                });
            });

            // Add schema tables and columns if provided
            if (schema) {
                Object.entries(schema).forEach(([table, columns]) => {
                    // Add table
                    suggestions.push({
                        label: table,
                        kind: monaco.languages.CompletionItemKind.Class,
                        insertText: table,
                        detail: 'Table',
                        range,
                    });

                    // Add columns
                    columns.forEach((column) => {
                        suggestions.push({
                            label: `${table}.${column}`,
                            kind: monaco.languages.CompletionItemKind.Field,
                            insertText: `${table}.${column}`,
                            detail: `Column in ${table}`,
                            range,
                        });
                        suggestions.push({
                            label: column,
                            kind: monaco.languages.CompletionItemKind.Field,
                            insertText: column,
                            detail: `Column`,
                            range,
                        });
                    });
                });
            }

            return { suggestions };
        },
    });
}

/**
 * CodeEditor Component
 */
export function CodeEditor({
    value,
    onChange,
    config,
    onExecute,
    onFormat,
    schema,
    readOnly = false,
    className,
    height = '400px',
}: CodeEditorProps): React.ReactElement {
    const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
    const monacoRef = useRef<Monaco | null>(null);

    const language = config?.language ?? 'sql';
    const theme = config?.theme ?? 'vs-dark';

    /**
     * Handle editor mount
     */
    const handleEditorMount: OnMount = useCallback((editor, monaco) => {
        editorRef.current = editor;
        monacoRef.current = monaco;

        // Register SQL autocomplete if language is SQL
        if (language === 'sql') {
            registerSqlAutocomplete(monaco, schema);
        }

        // Add keyboard shortcuts
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
            onExecute?.();
        });

        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KeyF, () => {
            onFormat?.();
        });

        // Focus editor
        editor.focus();
    }, [language, schema, onExecute, onFormat]);

    /**
     * Handle value change
     */
    const handleChange: OnChange = useCallback((value) => {
        onChange?.(value ?? '');
    }, [onChange]);

    /**
     * Update schema when it changes
     */
    useEffect(() => {
        if (monacoRef.current && language === 'sql' && schema) {
            registerSqlAutocomplete(monacoRef.current, schema);
        }
    }, [schema, language]);

    return (
        <div className={className} style={{ height }}>
            <Editor
                height="100%"
                language={language}
                theme={theme}
                value={value}
                onChange={handleChange}
                onMount={handleEditorMount}
                options={{
                    ...defaultOptions,
                    readOnly,
                    ...config?.options,
                }}
                loading={
                    <div className="flex items-center justify-center h-full bg-gray-900">
                        <div className="text-gray-400">Loading editor...</div>
                    </div>
                }
            />
        </div>
    );
}

export default CodeEditor;
