/**
 * Code Diff Viewer
 *
 * Monaco diff-editor wrapper for reviewing generated vs edited code.
 *
 * @doc.type component
 * @doc.purpose Render side-by-side or inline code diffs
 * @doc.layer platform
 * @doc.pattern React Component
 */

import { DiffEditor, type Monaco } from '@monaco-editor/react';
import React, { useCallback } from 'react';

import type { CodeDiffViewerProps } from '../types';

const DEFAULT_HEIGHT = '360px';

export function CodeDiffViewer({
  original,
  modified,
  language = 'typescript',
  theme = 'vs-dark',
  height = DEFAULT_HEIGHT,
  renderSideBySide = true,
  enableSplitViewResizing = true,
  className,
}: CodeDiffViewerProps): React.ReactElement {
  const handleMount = useCallback((_: unknown, monaco: Monaco) => {
    // Keep the TS compiler diagnostics deterministic inside the diff editor.
    monaco.languages.typescript.typescriptDefaults.setDiagnosticsOptions({
      noSemanticValidation: false,
      noSyntaxValidation: false,
    });
  }, []);

  return (
    <div className={className} style={{ height }}>
      <DiffEditor
        height="100%"
        original={original}
        modified={modified}
        language={language}
        theme={theme}
        onMount={handleMount}
        options={{
          readOnly: true,
          renderSideBySide,
          enableSplitViewResizing,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: 'on',
        }}
      />
    </div>
  );
}

export default CodeDiffViewer;