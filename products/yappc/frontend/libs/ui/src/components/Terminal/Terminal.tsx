/**
 * Terminal Component
 * 
 * Log streaming terminal with ANSI color support.
 * 
 * @module ui/components
 */

import React, { useRef, useEffect, useState } from 'react';

export interface TerminalLine {
  id: string;
  content: string;
  timestamp?: Date;
  type?: 'info' | 'error' | 'warning' | 'success' | 'debug';
}

export interface TerminalProps {
  /** Lines to display */
  lines: TerminalLine[];
  /** Title */
  title?: string;
  /** Auto-scroll to bottom */
  autoScroll?: boolean;
  /** Show timestamps */
  showTimestamps?: boolean;
  /** Maximum lines to keep */
  maxLines?: number;
  /** Height */
  height?: number | string;
  /** Additional CSS classes */
  className?: string;
  /** On clear callback */
  onClear?: () => void;
}

/**
 * Terminal Component
 * 
 * @example
 * ```tsx
 * <Terminal
 *   lines={logs}
 *   title="Build Output"
 *   autoScroll={true}
 *   showTimestamps={true}
 * />
 * ```
 */
export const Terminal: React.FC<TerminalProps> = ({
  lines,
  title = 'Terminal',
  autoScroll = true,
  showTimestamps = false,
  maxLines = 1000,
  height = 400,
  className = '',
  onClear,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [isAtBottom, setIsAtBottom] = useState(true);

  // Auto-scroll to bottom
  useEffect(() => {
    if (autoScroll && isAtBottom && containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [lines, autoScroll, isAtBottom]);

  // Track scroll position
  const handleScroll = () => {
    if (!containerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
    setIsAtBottom(scrollTop + clientHeight >= scrollHeight - 10);
  };

  const getLineColor = (type?: TerminalLine['type']) => {
    switch (type) {
      case 'error':
        return 'text-red-400';
      case 'warning':
        return 'text-yellow-400';
      case 'success':
        return 'text-green-400';
      case 'debug':
        return 'text-zinc-500';
      default:
        return 'text-zinc-300';
    }
  };

  const formatTimestamp = (date?: Date) => {
    if (!date) return '';
    return date.toLocaleTimeString('en-US', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  // Limit lines
  const displayLines = lines.slice(-maxLines);

  return (
    <div className={`rounded-lg border border-zinc-700 overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-zinc-800 border-b border-zinc-700">
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            <div className="w-3 h-3 rounded-full bg-red-500" />
            <div className="w-3 h-3 rounded-full bg-yellow-500" />
            <div className="w-3 h-3 rounded-full bg-green-500" />
          </div>
          <span className="text-sm text-zinc-400 ml-2">{title}</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-zinc-500">{displayLines.length} lines</span>
          {onClear && (
            <button
              onClick={onClear}
              className="text-xs text-zinc-500 hover:text-white transition-colors"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Terminal Content */}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="bg-zinc-950 overflow-auto font-mono text-sm"
        style={{ height }}
      >
        <div className="p-4">
          {displayLines.length === 0 ? (
            <div className="text-zinc-600 italic">No output</div>
          ) : (
            displayLines.map((line) => (
              <div key={line.id} className={`${getLineColor(line.type)} whitespace-pre-wrap`}>
                {showTimestamps && line.timestamp && (
                  <span className="text-zinc-600 mr-2">
                    [{formatTimestamp(line.timestamp)}]
                  </span>
                )}
                {line.content}
              </div>
            ))
          )}
        </div>
      </div>

      {/* Scroll indicator */}
      {!isAtBottom && (
        <button
          onClick={() => {
            if (containerRef.current) {
              containerRef.current.scrollTop = containerRef.current.scrollHeight;
            }
          }}
          className="absolute bottom-4 right-4 px-3 py-1 bg-violet-600 text-white text-xs rounded-full shadow-lg hover:bg-violet-700 transition-colors"
        >
          ↓ Scroll to bottom
        </button>
      )}
    </div>
  );
};

export default Terminal;
