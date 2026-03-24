/**
 * Editor Panel - IDE to Canvas Bridge
 * 
 * Maps IDE EditorPanel to CanvasEditorPanel
 * 
 * @deprecated Use CanvasEditorPanel from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect } from 'react';

export interface EditorPanelProps {
  /** Content to display in editor */
  children?: React.ReactNode;
  /** Initial content value */
  value?: string;
  /** Content change handler */
  onChange?: (value: string) => void;
  /** Editor language mode */
  language?: string;
  /** Read-only mode */
  readOnly?: boolean;
  /** Additional CSS classes */
  className?: string;
  /** Editor theme */
  theme?: 'light' | 'dark';
}

/**
 * EditorPanel - Bridge to Canvas Editor System
 * 
 * Maps IDE EditorPanel to Canvas code editor components.
 * During migration, this wraps the canvas editor surface.
 */
export const EditorPanel: React.FC<EditorPanelProps> = ({
  children,
  value,
  onChange,
  language = 'typescript',
  readOnly = false,
  className,
  theme = 'light',
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] EditorPanel from @ghatana/yappc-ide is deprecated. ' +
      'Use CanvasEditorPanel or CodeEditor from @ghatana/yappc-canvas. ' +
      'See LIBRARY_CONSOLIDATION_PLAN.md'
    );
  }, []);

  return (
    <div 
      className={`editor-panel-bridge ${className || ''}`}
      data-theme={theme}
      data-language={language}
      data-readonly={readOnly}
    >
      {children || value || 'Editor Content'}
    </div>
  );
};

/**
 * CodeEditor - Bridge Component
 * 
 * Maps IDE CodeEditor to Canvas code editor.
 */
export interface CodeEditorProps extends EditorPanelProps {
  /** Enable line numbers */
  showLineNumbers?: boolean;
  /** Enable minimap */
  showMinimap?: boolean;
  /** Enable autocomplete */
  enableAutocomplete?: boolean;
}

export const CodeEditor: React.FC<CodeEditorProps> = (props) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] CodeEditor from @ghatana/yappc-ide is deprecated. ' +
      'Use CodeEditor from @ghatana/yappc-canvas. ' +
      'See LIBRARY_CONSOLIDATION_PLAN.md'
    );
  }, []);

  return <EditorPanel {...props} />;
};

// Re-export types for backward compatibility
export type { EditorPanelProps as CanvasEditorPanelProps };
export { EditorPanel as CanvasEditorPanel };
