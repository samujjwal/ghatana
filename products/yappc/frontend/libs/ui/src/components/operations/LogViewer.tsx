/**
 * LogViewer Component
 *
 * @description Real-time log viewer with search, filtering, live tail,
 * and syntax highlighting for the operations log explorer.
 *
 * @doc.type component
 * @doc.purpose Log display and analysis
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error' | 'fatal';

export interface LogEntry {
  id: string;
  timestamp: string;
  level: LogLevel;
  service: string;
  message: string;
  traceId?: string;
  spanId?: string;
  metadata?: Record<string, unknown>;
}

export interface LogFilter {
  levels: LogLevel[];
  services: string[];
  search: string;
  startTime?: string;
  endTime?: string;
}

export interface LogViewerProps {
  logs: LogEntry[];
  isLiveTail?: boolean;
  onToggleLiveTail?: (enabled: boolean) => void;
  onLoadMore?: () => void;
  onFilterChange?: (filter: LogFilter) => void;
  onLogClick?: (log: LogEntry) => void;
  availableServices?: string[];
  filter?: LogFilter;
  maxHeight?: string;
  showTimestamps?: boolean;
  showServices?: boolean;
  wrapLines?: boolean;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const getLevelConfig = (level: LogLevel) => {
  const configs: Record<LogLevel, { color: string; bg: string; label: string }> = {
    trace: { color: '#9CA3AF', bg: 'rgba(156, 163, 175, 0.1)', label: 'TRC' },
    debug: { color: '#6B7280', bg: 'rgba(107, 114, 128, 0.1)', label: 'DBG' },
    info: { color: '#3B82F6', bg: 'rgba(59, 130, 246, 0.1)', label: 'INF' },
    warn: { color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.1)', label: 'WRN' },
    error: { color: '#EF4444', bg: 'rgba(239, 68, 68, 0.1)', label: 'ERR' },
    fatal: { color: '#DC2626', bg: 'rgba(220, 38, 38, 0.2)', label: 'FTL' },
  };
  return configs[level];
};

const formatTimestamp = (timestamp: string): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3,
  });
};

const highlightText = (text: string, search: string): React.ReactNode => {
  if (!search.trim()) return text;

  try {
    const regex = new RegExp(`(${search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
    const parts = text.split(regex);

    return parts.map((part, i) =>
      regex.test(part) ? (
        <mark key={i} className="log-highlight">
          {part}
        </mark>
      ) : (
        part
      )
    );
  } catch {
    return text;
  }
};

// ============================================================================
// Log Line Sub-component
// ============================================================================

interface LogLineProps {
  log: LogEntry;
  search?: string;
  showTimestamp: boolean;
  showService: boolean;
  wrapLines: boolean;
  onClick?: () => void;
}

const LogLine: React.FC<LogLineProps> = ({
  log,
  search = '',
  showTimestamp,
  showService,
  wrapLines,
  onClick,
}) => {
  const levelConfig = getLevelConfig(log.level);
  const [expanded, setExpanded] = useState(false);

  const hasMetadata = log.metadata && Object.keys(log.metadata).length > 0;

  return (
    <div
      className={cn(
        'log-line',
        `log-line--${log.level}`,
        wrapLines && 'log-line--wrap',
        expanded && 'log-line--expanded'
      )}
      onClick={onClick}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && onClick) {
          e.preventDefault();
          onClick();
        }
      }}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {/* Main Row */}
      <div className="log-row">
        {showTimestamp && (
          <span className="log-timestamp">{formatTimestamp(log.timestamp)}</span>
        )}
        <span
          className="log-level"
          style={{ color: levelConfig.color, backgroundColor: levelConfig.bg }}
        >
          {levelConfig.label}
        </span>
        {showService && <span className="log-service">{log.service}</span>}
        <span className="log-message">{highlightText(log.message, search)}</span>
        {(log.traceId || hasMetadata) && (
          <button
            type="button"
            className="log-expand-btn"
            onClick={(e) => {
              e.stopPropagation();
              setExpanded(!expanded);
            }}
            aria-label={expanded ? 'Collapse details' : 'Expand details'}
          >
            {expanded ? '▼' : '▶'}
          </button>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="log-details">
          {log.traceId && (
            <div className="log-detail-row">
              <span className="detail-label">trace_id:</span>
              <span className="detail-value detail-value--mono">{log.traceId}</span>
            </div>
          )}
          {log.spanId && (
            <div className="log-detail-row">
              <span className="detail-label">span_id:</span>
              <span className="detail-value detail-value--mono">{log.spanId}</span>
            </div>
          )}
          {hasMetadata &&
            Object.entries(log.metadata!).map(([key, value]) => (
              <div key={key} className="log-detail-row">
                <span className="detail-label">{key}:</span>
                <span className="detail-value detail-value--mono">
                  {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                </span>
              </div>
            ))}
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const LogViewer: React.FC<LogViewerProps> = ({
  logs,
  isLiveTail = false,
  onToggleLiveTail,
  onLoadMore,
  onFilterChange,
  onLogClick,
  availableServices = [],
  filter,
  maxHeight = '600px',
  showTimestamps = true,
  showServices = true,
  wrapLines = false,
  className,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [localSearch, setLocalSearch] = useState(filter?.search || '');
  const [selectedLevels, setSelectedLevels] = useState<Set<LogLevel>>(
    new Set(filter?.levels || ['trace', 'debug', 'info', 'warn', 'error', 'fatal'])
  );

  // Auto-scroll to bottom when live tail is on
  useEffect(() => {
    if (isLiveTail && containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [logs, isLiveTail]);

  // Handle scroll for loading more
  const handleScroll = useCallback(() => {
    if (!containerRef.current || isLiveTail) return;

    const { scrollTop } = containerRef.current;
    if (scrollTop === 0 && onLoadMore) {
      onLoadMore();
    }
  }, [isLiveTail, onLoadMore]);

  // Filter logs
  const filteredLogs = useMemo(() => {
    return logs.filter((log) => {
      if (!selectedLevels.has(log.level)) return false;
      if (filter?.services?.length && !filter.services.includes(log.service)) return false;
      if (localSearch) {
        const searchLower = localSearch.toLowerCase();
        return (
          log.message.toLowerCase().includes(searchLower) ||
          log.service.toLowerCase().includes(searchLower) ||
          log.traceId?.toLowerCase().includes(searchLower)
        );
      }
      return true;
    });
  }, [logs, selectedLevels, filter?.services, localSearch]);

  // Level toggle
  const toggleLevel = (level: LogLevel) => {
    const newLevels = new Set(selectedLevels);
    if (newLevels.has(level)) {
      newLevels.delete(level);
    } else {
      newLevels.add(level);
    }
    setSelectedLevels(newLevels);
    onFilterChange?.({
      ...filter!,
      levels: Array.from(newLevels),
    });
  };

  // Search handler
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setLocalSearch(value);
    onFilterChange?.({
      ...filter!,
      search: value,
    });
  };

  return (
    <div className={cn('log-viewer', className)}>
      {/* Toolbar */}
      <div className="log-toolbar">
        {/* Search */}
        <div className="log-search">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="Search logs..."
            value={localSearch}
            onChange={handleSearchChange}
            className="search-input"
          />
          {localSearch && (
            <button
              type="button"
              className="search-clear"
              onClick={() => {
                setLocalSearch('');
                onFilterChange?.({ ...filter!, search: '' });
              }}
              aria-label="Clear search"
            >
              ✕
            </button>
          )}
        </div>

        {/* Level Filters */}
        <div className="log-level-filters">
          {(['trace', 'debug', 'info', 'warn', 'error', 'fatal'] as LogLevel[]).map((level) => {
            const config = getLevelConfig(level);
            const isActive = selectedLevels.has(level);
            return (
              <button
                key={level}
                type="button"
                className={cn('level-filter', isActive && 'level-filter--active')}
                style={{
                  color: isActive ? config.color : '#9CA3AF',
                  backgroundColor: isActive ? config.bg : 'transparent',
                }}
                onClick={() => toggleLevel(level)}
              >
                {config.label}
              </button>
            );
          })}
        </div>

        {/* Live Tail Toggle */}
        {onToggleLiveTail && (
          <button
            type="button"
            className={cn('live-tail-btn', isLiveTail && 'live-tail-btn--active')}
            onClick={() => onToggleLiveTail(!isLiveTail)}
          >
            <span className="live-indicator" />
            Live Tail
          </button>
        )}
      </div>

      {/* Log Container */}
      <div
        ref={containerRef}
        className="log-container"
        style={{ maxHeight }}
        onScroll={handleScroll}
      >
        {filteredLogs.length === 0 ? (
          <div className="log-empty">
            <span className="empty-icon">📋</span>
            <span className="empty-text">No logs match your filters</span>
          </div>
        ) : (
          filteredLogs.map((log) => (
            <LogLine
              key={log.id}
              log={log}
              search={localSearch}
              showTimestamp={showTimestamps}
              showService={showServices}
              wrapLines={wrapLines}
              onClick={onLogClick ? () => onLogClick(log) : undefined}
            />
          ))
        )}
      </div>

      {/* Footer */}
      <div className="log-footer">
        <span className="log-count">
          {filteredLogs.length.toLocaleString()} logs
          {filteredLogs.length !== logs.length && ` (${logs.length.toLocaleString()} total)`}
        </span>
      </div>

      <style>{`
        .log-viewer {
          display: flex;
          flex-direction: column;
          background: #0D1117;
          border-radius: 8px;
          overflow: hidden;
          font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', monospace;
        }

        .log-toolbar {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 0.75rem;
          background: #161B22;
          border-bottom: 1px solid #21262D;
        }

        .log-search {
          display: flex;
          align-items: center;
          flex: 1;
          max-width: 300px;
          background: #0D1117;
          border: 1px solid #30363D;
          border-radius: 6px;
          padding: 0 0.5rem;
        }

        .search-icon {
          font-size: 0.75rem;
          opacity: 0.5;
        }

        .search-input {
          flex: 1;
          background: transparent;
          border: none;
          outline: none;
          color: #C9D1D9;
          font-size: 0.8125rem;
          padding: 0.375rem 0.5rem;
        }

        .search-input::placeholder {
          color: #484F58;
        }

        .search-clear {
          background: transparent;
          border: none;
          color: #484F58;
          cursor: pointer;
          font-size: 0.75rem;
          padding: 0.25rem;
        }

        .search-clear:hover {
          color: #C9D1D9;
        }

        .log-level-filters {
          display: flex;
          gap: 0.25rem;
        }

        .level-filter {
          font-size: 0.6875rem;
          font-weight: 600;
          padding: 0.25rem 0.5rem;
          border: 1px solid #30363D;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .level-filter:hover {
          border-color: #484F58;
        }

        .live-tail-btn {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          font-size: 0.75rem;
          font-weight: 500;
          padding: 0.375rem 0.625rem;
          background: #21262D;
          border: 1px solid #30363D;
          border-radius: 6px;
          color: #8B949E;
          cursor: pointer;
          margin-left: auto;
        }

        .live-tail-btn:hover {
          color: #C9D1D9;
          border-color: #484F58;
        }

        .live-tail-btn--active {
          color: #3FB950;
          border-color: #238636;
          background: rgba(35, 134, 54, 0.1);
        }

        .live-indicator {
          width: 6px;
          height: 6px;
          background: currentColor;
          border-radius: 50%;
        }

        .live-tail-btn--active .live-indicator {
          animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.4; }
        }

        .log-container {
          flex: 1;
          overflow-y: auto;
          padding: 0.5rem 0;
        }

        .log-line {
          padding: 0.25rem 0.75rem;
          cursor: default;
          transition: background 0.1s ease;
        }

        .log-line:hover {
          background: #161B22;
        }

        .log-line--error,
        .log-line--fatal {
          background: rgba(248, 81, 73, 0.05);
        }

        .log-row {
          display: flex;
          align-items: flex-start;
          gap: 0.5rem;
          font-size: 0.8125rem;
          line-height: 1.5;
        }

        .log-line--wrap .log-message {
          white-space: pre-wrap;
          word-break: break-word;
        }

        .log-timestamp {
          color: #484F58;
          font-size: 0.75rem;
          flex-shrink: 0;
        }

        .log-level {
          font-size: 0.625rem;
          font-weight: 700;
          padding: 0.125rem 0.375rem;
          border-radius: 3px;
          flex-shrink: 0;
        }

        .log-service {
          color: #79C0FF;
          font-size: 0.75rem;
          flex-shrink: 0;
          max-width: 120px;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .log-message {
          color: #C9D1D9;
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .log-highlight {
          background: #9E6A03;
          color: #F0F6FC;
          border-radius: 2px;
          padding: 0 0.125rem;
        }

        .log-expand-btn {
          background: transparent;
          border: none;
          color: #484F58;
          cursor: pointer;
          font-size: 0.625rem;
          padding: 0.125rem 0.25rem;
          flex-shrink: 0;
        }

        .log-expand-btn:hover {
          color: #C9D1D9;
        }

        .log-details {
          margin-top: 0.375rem;
          padding: 0.5rem;
          background: #161B22;
          border-radius: 4px;
          border: 1px solid #21262D;
        }

        .log-detail-row {
          display: flex;
          gap: 0.5rem;
          font-size: 0.75rem;
          padding: 0.125rem 0;
        }

        .detail-label {
          color: #8B949E;
          flex-shrink: 0;
        }

        .detail-value {
          color: #C9D1D9;
          word-break: break-all;
        }

        .detail-value--mono {
          font-family: 'JetBrains Mono', monospace;
          color: #79C0FF;
        }

        .log-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 3rem;
          color: #484F58;
        }

        .empty-icon {
          font-size: 2rem;
          margin-bottom: 0.5rem;
        }

        .empty-text {
          font-size: 0.875rem;
        }

        .log-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 0.5rem 0.75rem;
          background: #161B22;
          border-top: 1px solid #21262D;
        }

        .log-count {
          font-size: 0.75rem;
          color: #8B949E;
        }
      `}</style>
    </div>
  );
};

LogViewer.displayName = 'LogViewer';

export default LogViewer;
