/**
 * Plugin Logs Viewer Component
 *
 * Advanced log viewer for plugin execution logs with:
 * - Real-time log streaming
 * - Multi-level filtering (ERROR, WARN, INFO, DEBUG)
 * - Full-text search
 * - Timestamp filtering
 * - Export functionality
 * - Auto-scroll with pause
 *
 * @doc.type component
 * @doc.purpose Real-time plugin log monitoring
 * @doc.layer frontend
 */

import React, { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  FileText,
  Search,
  Filter,
  Download,
  Pause,
  Play,
  Trash2,
  AlertCircle,
  AlertTriangle,
  Info,
  Bug,
  CheckCircle,
} from 'lucide-react';
import { cn, cardStyles, textStyles, buttonStyles, inputStyles } from '../../lib/theme';

export type LogLevel = 'ERROR' | 'WARN' | 'INFO' | 'DEBUG' | 'TRACE';

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  context?: Record<string, unknown>;
  source?: string;
  stackTrace?: string;
}

export interface PluginLogsViewerProps {
  /** Plugin ID */
  pluginId: string;
  /** Maximum number of logs to display */
  maxLogs?: number;
  /** Enable real-time streaming */
  streaming?: boolean;
  /** Refresh interval in milliseconds */
  refreshInterval?: number;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Plugin Logs Viewer Component
 */
export function PluginLogsViewer({
  pluginId,
  maxLogs = 1000,
  streaming = true,
  refreshInterval = 2000,
  className,
}: PluginLogsViewerProps): React.ReactElement {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedLevels, setSelectedLevels] = useState<Set<LogLevel>>(
    new Set(['ERROR', 'WARN', 'INFO', 'DEBUG'])
  );
  const [isPaused, setIsPaused] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const logsEndRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Fetch logs
  const { data: newLogs } = useQuery({
    queryKey: ['plugins', pluginId, 'logs', selectedLevels],
    queryFn: async (): Promise<LogEntry[]> => {
      // Mock data - in production, this would fetch from API
      await new Promise((resolve) => setTimeout(resolve, 200));

      // Generate mock logs
      const logLevels: LogLevel[] = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];
      const messages = [
        'Connection established to data source',
        'Processing batch of 1000 records',
        'Data validation completed successfully',
        'Transformed 850 records',
        'Rate limit threshold reached, throttling requests',
        'Retrying failed operation (attempt 2/3)',
        'Cache miss for key: data_snapshot_xyz',
        'Memory usage: 142MB / 256MB',
        'Configuration updated: sync_interval=30s',
        'Error parsing JSON response from API',
        'Warning: High memory usage detected',
        'Plugin health check passed',
      ];

      const numLogs = Math.floor(Math.random() * 3) + 1; // 1-3 new logs
      return Array.from({ length: numLogs }, () => {
        const level = logLevels[Math.floor(Math.random() * logLevels.length)];
        return {
          timestamp: new Date().toISOString(),
          level,
          message: messages[Math.floor(Math.random() * messages.length)],
          context: {
            pluginId,
            threadId: `thread-${Math.floor(Math.random() * 10)}`,
            requestId: `req-${Math.random().toString(36).slice(2, 11)}`,
          },
          source: `Plugin:${pluginId}`,
        };
      });
    },
    refetchInterval: streaming && !isPaused ? refreshInterval : false,
  });

  // Append new logs
  useEffect(() => {
    if (newLogs && newLogs.length > 0 && !isPaused) {
      setLogs((prev) => {
        const updated = [...prev, ...newLogs];
        return updated.slice(-maxLogs); // Keep only last maxLogs entries
      });
    }
  }, [newLogs, isPaused, maxLogs]);

  // Auto-scroll to bottom
  useEffect(() => {
    if (autoScroll && logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  // Handle manual scroll to detect if user scrolled up
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const isAtBottom =
        Math.abs(container.scrollHeight - container.scrollTop - container.clientHeight) < 10;
      setAutoScroll(isAtBottom);
    };

    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, []);

  // Toggle log level filter
  const toggleLevel = (level: LogLevel) => {
    setSelectedLevels((prev) => {
      const next = new Set(prev);
      if (next.has(level)) {
        next.delete(level);
      } else {
        next.add(level);
      }
      return next;
    });
  };

  // Export logs
  const exportLogs = () => {
    const filteredLogs = getFilteredLogs();
    const logText = filteredLogs
      .map((log) => `[${log.timestamp}] [${log.level}] ${log.message}`)
      .join('\n');
    
    const blob = new Blob([logText], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `plugin-${pluginId}-logs-${new Date().toISOString()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Clear logs
  const clearLogs = () => {
    setLogs([]);
  };

  // Filter logs
  const getFilteredLogs = (): LogEntry[] => {
    return logs.filter((log) => {
      // Level filter
      if (!selectedLevels.has(log.level)) return false;

      // Search filter
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          log.message.toLowerCase().includes(query) ||
          log.level.toLowerCase().includes(query) ||
          log.source?.toLowerCase().includes(query)
        );
      }

      return true;
    });
  };

  const filteredLogs = getFilteredLogs();

  // Get icon and color for log level
  const getLevelConfig = (level: LogLevel) => {
    switch (level) {
      case 'ERROR':
        return { icon: AlertCircle, color: 'text-red-600', bg: 'bg-red-50 dark:bg-red-900/20' };
      case 'WARN':
        return { icon: AlertTriangle, color: 'text-yellow-600', bg: 'bg-yellow-50 dark:bg-yellow-900/20' };
      case 'INFO':
        return { icon: Info, color: 'text-blue-600', bg: 'bg-blue-50 dark:bg-blue-900/20' };
      case 'DEBUG':
        return { icon: Bug, color: 'text-gray-600', bg: 'bg-gray-50 dark:bg-gray-900/20' };
      case 'TRACE':
        return { icon: CheckCircle, color: 'text-gray-500', bg: 'bg-gray-50 dark:bg-gray-900/20' };
    }
  };

  return (
    <div className={cn('space-y-4', className)}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FileText className="h-5 w-5 text-blue-600" />
          <h3 className={textStyles.h4}>Plugin Logs</h3>
          <span className="text-xs text-gray-500">
            ({filteredLogs.length} / {logs.length})
          </span>
        </div>

        {/* Controls */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsPaused(!isPaused)}
            className={cn(
              buttonStyles.secondary,
              'px-3 py-1.5 text-sm',
              isPaused && 'bg-yellow-100 dark:bg-yellow-900/20'
            )}
            title={isPaused ? 'Resume streaming' : 'Pause streaming'}
          >
            {isPaused ? <Play className="h-4 w-4" /> : <Pause className="h-4 w-4" />}
          </button>
          <button
            onClick={exportLogs}
            className={cn(buttonStyles.secondary, 'px-3 py-1.5 text-sm')}
            title="Export logs"
          >
            <Download className="h-4 w-4" />
          </button>
          <button
            onClick={clearLogs}
            className={cn(buttonStyles.secondary, 'px-3 py-1.5 text-sm text-red-600')}
            title="Clear logs"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4 flex-wrap">
        {/* Search */}
        <div className="flex-1 min-w-[200px] relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search logs..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={cn(inputStyles.base, 'pl-10')}
          />
        </div>

        {/* Level Filters */}
        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-gray-400" />
          {(['ERROR', 'WARN', 'INFO', 'DEBUG'] as LogLevel[]).map((level) => {
            const config = getLevelConfig(level);
            const Icon = config.icon;
            return (
              <button
                key={level}
                onClick={() => toggleLevel(level)}
                className={cn(
                  'px-3 py-1 text-xs font-medium rounded transition-colors',
                  selectedLevels.has(level)
                    ? cn(config.bg, config.color)
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-500 opacity-50'
                )}
              >
                <Icon className="h-3 w-3 inline mr-1" />
                {level}
              </button>
            );
          })}
        </div>
      </div>

      {/* Logs Container */}
      <div
        ref={containerRef}
        className={cn(
          cardStyles.base,
          'h-[500px] overflow-y-auto font-mono text-xs'
        )}
      >
        {filteredLogs.length > 0 ? (
          <div className="space-y-1">
            {filteredLogs.map((log, idx) => {
              const config = getLevelConfig(log.level);
              const Icon = config.icon;
              return (
                <div
                  key={idx}
                  className={cn(
                    'flex items-start gap-2 p-2 rounded hover:bg-gray-50 dark:hover:bg-gray-800/50',
                    config.bg
                  )}
                >
                  <Icon className={cn('h-4 w-4 flex-shrink-0 mt-0.5', config.color)} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-gray-500">
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </span>
                      <span className={cn('font-semibold', config.color)}>{log.level}</span>
                      {log.source && (
                        <span className="text-gray-500 text-[10px]">{log.source}</span>
                      )}
                    </div>
                    <div className="text-gray-900 dark:text-gray-100 break-words">
                      {log.message}
                    </div>
                    {log.context && Object.keys(log.context).length > 0 && (
                      <details className="mt-1">
                        <summary className="text-gray-500 cursor-pointer text-[10px]">
                          Context
                        </summary>
                        <pre className="mt-1 text-[10px] text-gray-600 dark:text-gray-400 overflow-x-auto">
                          {JSON.stringify(log.context, null, 2)}
                        </pre>
                      </details>
                    )}
                  </div>
                </div>
              );
            })}
            <div ref={logsEndRef} />
          </div>
        ) : (
          <div className="h-full flex items-center justify-center text-gray-500">
            {logs.length === 0 ? (
              <div className="text-center">
                <FileText className="h-12 w-12 mx-auto mb-2 opacity-50" />
                <p>No logs available</p>
                <p className="text-xs mt-1">Logs will appear here when the plugin is active</p>
              </div>
            ) : (
              <div className="text-center">
                <Filter className="h-12 w-12 mx-auto mb-2 opacity-50" />
                <p>No logs match the current filters</p>
                <p className="text-xs mt-1">Try adjusting your search or filter criteria</p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Status Bar */}
      <div className="flex items-center justify-between text-xs text-gray-600 dark:text-gray-400">
        <div className="flex items-center gap-4">
          <span>
            {streaming && !isPaused ? (
              <>
                <span className="inline-block w-2 h-2 bg-green-500 rounded-full animate-pulse mr-1" />
                Live
              </>
            ) : (
              <>
                <span className="inline-block w-2 h-2 bg-gray-400 rounded-full mr-1" />
                Paused
              </>
            )}
          </span>
          {autoScroll && <span>Auto-scroll enabled</span>}
        </div>
        <span>Refresh interval: {refreshInterval / 1000}s</span>
      </div>
    </div>
  );
}
