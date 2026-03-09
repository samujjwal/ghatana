/**
 * Code Diff Viewer Component
 * 
 * Side-by-side diff viewer with syntax highlighting.
 * Supports unified and split view modes.
 * 
 * @module ui/components
 */

import React, { useMemo } from 'react';

export interface DiffLine {
  type: 'added' | 'removed' | 'unchanged' | 'header';
  content: string;
  oldLineNumber?: number;
  newLineNumber?: number;
}

export interface CodeDiffViewerProps {
  /** Original code */
  oldCode: string;
  /** New code */
  newCode: string;
  /** File name */
  fileName?: string;
  /** Language for syntax highlighting */
  language?: string;
  /** View mode */
  viewMode?: 'unified' | 'split';
  /** Show line numbers */
  showLineNumbers?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Simple diff algorithm
 */
function computeDiff(oldLines: string[], newLines: string[]): DiffLine[] {
  const diff: DiffLine[] = [];
  let oldIndex = 0;
  let newIndex = 0;

  while (oldIndex < oldLines.length || newIndex < newLines.length) {
    if (oldIndex >= oldLines.length) {
      diff.push({
        type: 'added',
        content: newLines[newIndex],
        newLineNumber: newIndex + 1,
      });
      newIndex++;
    } else if (newIndex >= newLines.length) {
      diff.push({
        type: 'removed',
        content: oldLines[oldIndex],
        oldLineNumber: oldIndex + 1,
      });
      oldIndex++;
    } else if (oldLines[oldIndex] === newLines[newIndex]) {
      diff.push({
        type: 'unchanged',
        content: oldLines[oldIndex],
        oldLineNumber: oldIndex + 1,
        newLineNumber: newIndex + 1,
      });
      oldIndex++;
      newIndex++;
    } else {
      diff.push({
        type: 'removed',
        content: oldLines[oldIndex],
        oldLineNumber: oldIndex + 1,
      });
      diff.push({
        type: 'added',
        content: newLines[newIndex],
        newLineNumber: newIndex + 1,
      });
      oldIndex++;
      newIndex++;
    }
  }

  return diff;
}

/**
 * Code Diff Viewer Component
 * 
 * @example
 * ```tsx
 * <CodeDiffViewer
 *   oldCode="const x = 1;"
 *   newCode="const x = 2;"
 *   fileName="example.ts"
 *   language="typescript"
 *   viewMode="unified"
 * />
 * ```
 */
export const CodeDiffViewer: React.FC<CodeDiffViewerProps> = ({
  oldCode,
  newCode,
  fileName,
  language = 'text',
  viewMode = 'unified',
  showLineNumbers = true,
  className = '',
}) => {
  const diff = useMemo(() => {
    const oldLines = oldCode.split('\n');
    const newLines = newCode.split('\n');
    return computeDiff(oldLines, newLines);
  }, [oldCode, newCode]);

  const stats = useMemo(() => {
    const added = diff.filter(d => d.type === 'added').length;
    const removed = diff.filter(d => d.type === 'removed').length;
    return { added, removed };
  }, [diff]);

  const getLineClass = (type: DiffLine['type']) => {
    switch (type) {
      case 'added':
        return 'bg-green-900/30 text-green-300';
      case 'removed':
        return 'bg-red-900/30 text-red-300';
      case 'header':
        return 'bg-blue-900/30 text-blue-300';
      default:
        return 'text-zinc-300';
    }
  };

  const getGutterClass = (type: DiffLine['type']) => {
    switch (type) {
      case 'added':
        return 'bg-green-900/50 text-green-400';
      case 'removed':
        return 'bg-red-900/50 text-red-400';
      default:
        return 'bg-zinc-800 text-zinc-500';
    }
  };

  const getPrefix = (type: DiffLine['type']) => {
    switch (type) {
      case 'added':
        return '+';
      case 'removed':
        return '-';
      default:
        return ' ';
    }
  };

  return (
    <div className={`rounded-lg border border-zinc-700 overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-zinc-800 border-b border-zinc-700">
        <div className="flex items-center gap-2">
          {fileName && (
            <span className="text-sm font-mono text-zinc-300">{fileName}</span>
          )}
          {language && (
            <span className="text-xs px-2 py-0.5 bg-zinc-700 rounded text-zinc-400">
              {language}
            </span>
          )}
        </div>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-green-400">+{stats.added}</span>
          <span className="text-red-400">-{stats.removed}</span>
        </div>
      </div>

      {/* Diff Content */}
      <div className="overflow-x-auto">
        {viewMode === 'unified' ? (
          <table className="w-full text-sm font-mono">
            <tbody>
              {diff.map((line, index) => (
                <tr key={index} className={getLineClass(line.type)}>
                  {showLineNumbers && (
                    <>
                      <td className={`w-12 px-2 text-right select-none ${getGutterClass(line.type)}`}>
                        {line.oldLineNumber || ''}
                      </td>
                      <td className={`w-12 px-2 text-right select-none ${getGutterClass(line.type)}`}>
                        {line.newLineNumber || ''}
                      </td>
                    </>
                  )}
                  <td className={`w-6 px-1 text-center select-none ${getGutterClass(line.type)}`}>
                    {getPrefix(line.type)}
                  </td>
                  <td className="px-4 py-0.5 whitespace-pre">
                    {line.content || ' '}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="flex">
            {/* Old Code (Left) */}
            <div className="flex-1 border-r border-zinc-700">
              <table className="w-full text-sm font-mono">
                <tbody>
                  {diff.filter(d => d.type !== 'added').map((line, index) => (
                    <tr key={index} className={getLineClass(line.type)}>
                      {showLineNumbers && (
                        <td className={`w-12 px-2 text-right select-none ${getGutterClass(line.type)}`}>
                          {line.oldLineNumber || ''}
                        </td>
                      )}
                      <td className="px-4 py-0.5 whitespace-pre">
                        {line.content || ' '}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {/* New Code (Right) */}
            <div className="flex-1">
              <table className="w-full text-sm font-mono">
                <tbody>
                  {diff.filter(d => d.type !== 'removed').map((line, index) => (
                    <tr key={index} className={getLineClass(line.type)}>
                      {showLineNumbers && (
                        <td className={`w-12 px-2 text-right select-none ${getGutterClass(line.type)}`}>
                          {line.newLineNumber || ''}
                        </td>
                      )}
                      <td className="px-4 py-0.5 whitespace-pre">
                        {line.content || ' '}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CodeDiffViewer;
