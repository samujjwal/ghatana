/**
 * @ghatana/yappc-ide - Editor Panel Component
 * 
 * Monaco editor panel with collaborative features.
 * 
 * @doc.type component
 * @doc.purpose Code editor panel for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useEffect, useRef, useCallback } from 'react';
import { useAtom } from 'jotai';
import CodeEditor from '../../../code-editor/src/components/CodeEditor';
import { ideActiveFileAtom, ideSettingsAtom } from '../state/atoms';
import { useIDEFileOperations } from '../hooks/useIDEFileOperations';
import type { CodeLanguage } from '../../../code-editor/src/types';

/**
 * Editor Panel Props
 */
export interface EditorPanelProps {
  className?: string;
  readOnly?: boolean;
  showMinimap?: boolean;
  showLineNumbers?: boolean;
  onContentChange?: (content: string) => void;
}

/**
 * Map IDE language to CodeEditor language
 */
function mapLanguage(ideLanguage: string): CodeLanguage {
  const languageMap: Record<string, CodeLanguage> = {
    typescript: 'typescript',
    javascript: 'javascript',
    python: 'python',
    java: 'java',
    go: 'go',
    rust: 'rust',
    html: 'html',
    css: 'css',
    json: 'json',
    markdown: 'markdown',
    sql: 'sql',
    yaml: 'yaml',
    xml: 'html',
  };

  return (languageMap[ideLanguage.toLowerCase()] as CodeLanguage) || 'typescript';
}

/**
 * Editor Panel Component
 * 
 * @doc.param props - Component props
 * @doc.returns Editor panel component
 */
export const EditorPanel: React.FC<EditorPanelProps> = ({
  className = '',
  readOnly = false,
  showMinimap = true,
  showLineNumbers = true,
  onContentChange,
}) => {
  const [activeFile] = useAtom(ideActiveFileAtom);
  const [settings] = useAtom(ideSettingsAtom);
  const { updateFileContent, saveFile } = useIDEFileOperations();
  const editorRef = useRef<unknown>(null);
  const contentRef = useRef<string>('');

  // Update content ref when active file changes
  useEffect(() => {
    if (activeFile) {
      contentRef.current = activeFile.content;
    }
  }, [activeFile?.id]);

  const handleContentChange = useCallback(
    (newContent: string) => {
      if (!activeFile || readOnly) return;

      contentRef.current = newContent;
      updateFileContent(activeFile.id, newContent);
      onContentChange?.(newContent);

      // Auto-save if enabled
      if (settings.autoSave === 'afterDelay') {
        const timeoutId = setTimeout(() => {
          if (contentRef.current === newContent) {
            saveFile(activeFile.id);
          }
        }, settings.autoSaveDelay);

        return () => clearTimeout(timeoutId);
      }
    },
    [activeFile, readOnly, updateFileContent, onContentChange, settings, saveFile]
  );

  const handleSave = useCallback(() => {
    if (activeFile && !readOnly) {
      saveFile(activeFile.id);
    }
  }, [activeFile, readOnly, saveFile]);

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Ctrl/Cmd + S to save
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSave();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleSave]);

  if (!activeFile) {
    return (
      <div className={`flex flex-col items-center justify-center h-full bg-white dark:bg-gray-900 ${className}`}>
        <div className="text-center">
          <div className="text-6xl mb-4">📝</div>
          <h3 className="text-lg font-medium text-gray-700 dark:text-gray-300 mb-2">
            No file selected
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Open a file from the explorer to start editing
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex flex-col h-full bg-white dark:bg-gray-900 ${className}`}>
      {/* Editor Header */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {activeFile.name}
          </span>
          {activeFile.isDirty && (
            <span className="text-xs text-orange-500">● Modified</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500 dark:text-gray-400">
            {activeFile.language}
          </span>
          {activeFile.isDirty && (
            <button
              className="px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
              onClick={handleSave}
            >
              Save
            </button>
          )}
        </div>
      </div>

      {/* Monaco Editor */}
      <div className="flex-1 overflow-hidden">
        <CodeEditor
          onMount={(editor) => { editorRef.current = editor; }}
          value={activeFile.content}
          onChange={handleContentChange}
          config={{
            language: mapLanguage(activeFile.language),
            readOnly,
            fontSize: settings.fontSize,
            tabSize: settings.tabSize,
            insertSpaces: settings.insertSpaces,
            minimap: showMinimap,
            lineNumbers: showLineNumbers ? 'on' : 'off',
            theme: settings.theme === 'dark' ? 'vs-dark' : 'vs',
            formatOnPaste: settings.formatOnPaste,
            formatOnType: true,
            wordWrap: 'on',
            bracketPairColorization: true,
            options: {
              automaticLayout: true,
              scrollBeyondLastLine: false,
              renderWhitespace: 'selection',
              guides: {
                bracketPairs: true,
                indentation: true,
              },
            },
          }}
        />
      </div>

      {/* Editor Footer */}
      <div className="flex items-center justify-between px-4 py-1 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800">
        <div className="flex items-center gap-4 text-xs text-gray-600 dark:text-gray-400">
          <span>Line 1, Col 1</span>
          <span>UTF-8</span>
          <span>{activeFile.language}</span>
        </div>
        <div className="flex items-center gap-4 text-xs text-gray-600 dark:text-gray-400">
          <span>{activeFile.content.split('\n').length} lines</span>
          <span>{activeFile.size} bytes</span>
        </div>
      </div>
    </div>
  );
};

export default EditorPanel;
