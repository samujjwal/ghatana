import { useState, useEffect, useRef } from "react";
import { Terminal, Play, Pause, Trash2, Download, Search, Wifi, WifiOff, RefreshCw } from 'lucide-react';
import { WebSocketClient, ConnectionStatus } from '@/lib/websocket';

/**
 * Real-time Logs Monitor
 *
 * Purpose:
 * Live streaming of application logs with WebSocket streaming and filtering.
 * Provides developers and operators with real-time visibility into system behavior.
 *
 * Features:
 * - Live log streaming via WebSocket
 * - Log level filtering (error, warn, info, debug)
 * - Service/source filtering
 * - Search functionality
 * - Auto-scroll toggle
 * - Export logs
 * - Clear logs
 * - Auto-reconnection with exponential backoff
 *
 * @doc.type component
 * @doc.purpose Real-time log streaming dashboard
 * @doc.layer product
 * @doc.pattern Page
 */
export function LogsMonitor() {
    const [logs, setLogs] = useState<LogEntry[]>([]);
    const [isStreaming, setIsStreaming] = useState(true);
    const [autoScroll, setAutoScroll] = useState(true);
    const [filterLevel, setFilterLevel] = useState<'all' | 'error' | 'warn' | 'info' | 'debug'>('all');
    const [filterSource, setFilterSource] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('disconnected');
    const wsClientRef = useRef<WebSocketClient | null>(null);
    const logsEndRef = useRef<HTMLDivElement>(null);

    // WebSocket connection for live log stream
    useEffect(() => {
        if (!isStreaming) {
            // Disconnect if streaming is off
            if (wsClientRef.current) {
                wsClientRef.current.disconnect();
                wsClientRef.current = null;
            }
            return;
        }

        // Create WebSocket client
        const wsClient = new WebSocketClient({
            url: 'http://localhost:3101',
            namespace: '/observe/logs',
        });

        wsClientRef.current = wsClient;

        // Subscribe to connection status changes
        const unsubscribeStatus = wsClient.onStatusChange((status) => {
            setConnectionStatus(status);
        });

        // Connect and subscribe to logs
        wsClient.connect()
            .then(() => {
                console.log('[Logs] Connected to WebSocket');
                
                // Subscribe to logs stream
                const filters: { level?: string; source?: string; search?: string } = {};
                if (filterLevel !== 'all') filters.level = filterLevel;
                if (filterSource !== 'all') filters.source = filterSource;
                if (searchQuery) filters.search = searchQuery;

                wsClient.emit('subscribe', {
                    tenantId: 'acme-payments-id',
                    filters,
                });

                // Listen for initial batch of logs
                wsClient.on('logs:batch', (data: { logs: LogEntry[] }) => {
                    console.log('[Logs] Received batch:', data.logs.length);
                    setLogs(data.logs);
                });

                // Listen for new logs
                wsClient.on('logs:new', (data: { log: LogEntry }) => {
                    setLogs(prev => [...prev, data.log].slice(-200)); // Keep last 200 logs
                });
            })
            .catch((error) => {
                console.error('[Logs] Connection failed:', error);
            });

        // Cleanup on unmount
        return () => {
            unsubscribeStatus();
            wsClient.disconnect();
        };
    }, [isStreaming, filterLevel, filterSource, searchQuery]);

    // Re-subscribe when filters change
    useEffect(() => {
        if (isStreaming && wsClientRef.current && connectionStatus === 'connected') {
            const filters: { level?: string; source?: string; search?: string } = {};
            if (filterLevel !== 'all') filters.level = filterLevel;
            if (filterSource !== 'all') filters.source = filterSource;
            if (searchQuery) filters.search = searchQuery;

            wsClientRef.current.emit('subscribe', {
                tenantId: 'acme-payments-id',
                filters,
            });
        }
    }, [filterLevel, filterSource, searchQuery, isStreaming, connectionStatus]);

    // Auto-scroll to bottom
    useEffect(() => {
        if (autoScroll && logsEndRef.current) {
            logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [logs, autoScroll]);

    const sources = ['all', ...Array.from(new Set(logs.map(l => l.source)))];

    const filteredLogs = logs
        .filter(log => filterLevel === 'all' || log.level === filterLevel)
        .filter(log => filterSource === 'all' || log.source === filterSource)
        .filter(log => 
            searchQuery === '' || 
            log.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
            log.source.toLowerCase().includes(searchQuery.toLowerCase())
        );

    const stats = {
        total: logs.length,
        errors: logs.filter(l => l.level === 'error').length,
        warnings: logs.filter(l => l.level === 'warn').length,
        info: logs.filter(l => l.level === 'info').length,
    };

    const handleClear = () => {
        setLogs([]);
    };

    const handleExport = () => {
        const content = filteredLogs
            .map(log => `[${log.timestamp.toISOString()}] [${log.level.toUpperCase()}] [${log.source}] ${log.message}`)
            .join('\n');
        
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `logs_${new Date().toISOString().split('T')[0]}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    const levelConfig = {
        error: { color: 'text-red-600 dark:text-red-400', bg: 'bg-red-50 dark:bg-red-900/20', badge: 'danger' as const },
        warn: { color: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-50 dark:bg-amber-900/20', badge: 'warning' as const },
        info: { color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50 dark:bg-blue-900/20', badge: 'neutral' as const },
        debug: { color: 'text-slate-600 dark:text-neutral-400', bg: 'bg-slate-50 dark:bg-slate-800', badge: 'neutral' as const },
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Live Logs</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1 flex items-center gap-2">
                        Real-time application log streaming
                        {connectionStatus === 'connected' && (
                            <span className="inline-flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                                <Wifi className="h-3 w-3" /> Connected
                            </span>
                        )}
                        {connectionStatus === 'connecting' && (
                            <span className="inline-flex items-center gap-1 text-xs text-blue-600 dark:text-blue-400">
                                <RefreshCw className="h-3 w-3 animate-spin" /> Connecting...
                            </span>
                        )}
                        {connectionStatus === 'reconnecting' && (
                            <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                <RefreshCw className="h-3 w-3 animate-spin" /> Reconnecting...
                            </span>
                        )}
                        {connectionStatus === 'disconnected' && (
                            <span className="inline-flex items-center gap-1 text-xs text-slate-500 dark:text-neutral-500">
                                <WifiOff className="h-3 w-3" /> Disconnected
                            </span>
                        )}
                        {connectionStatus === 'error' && (
                            <span className="inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
                                <WifiOff className="h-3 w-3" /> Connection error
                            </span>
                        )}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setAutoScroll(!autoScroll)}
                        className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            autoScroll
                                ? 'bg-blue-600 text-white hover:bg-blue-700'
                                : 'border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                        }`}
                    >
                        Auto-scroll
                    </button>
                    <button
                        onClick={handleExport}
                        disabled={filteredLogs.length === 0}
                        className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors disabled:opacity-50"
                    >
                        <Download className="h-4 w-4" />
                        Export
                    </button>
                    <button
                        onClick={handleClear}
                        className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        <Trash2 className="h-4 w-4" />
                        Clear
                    </button>
                    <button
                        onClick={() => setIsStreaming(!isStreaming)}
                        className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            isStreaming
                                ? 'bg-green-600 text-white hover:bg-green-700'
                                : 'bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-neutral-300 hover:bg-slate-300 dark:hover:bg-slate-600'
                        }`}
                    >
                        {isStreaming ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
                        {isStreaming ? 'Streaming' : 'Paused'}
                    </button>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Logs" value={stats.total} icon={<Terminal className="h-5 w-5" />} />
                <StatCard label="Errors" value={stats.errors} icon={<div className="h-5 w-5 rounded-full bg-red-500" />} />
                <StatCard label="Warnings" value={stats.warnings} icon={<div className="h-5 w-5 rounded-full bg-amber-500" />} />
                <StatCard label="Info" value={stats.info} icon={<div className="h-5 w-5 rounded-full bg-blue-500" />} />
            </div>

            {/* Filters and Search */}
            <div className="flex flex-col sm:flex-row gap-3">
                {/* Search */}
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Search logs..."
                        className="w-full pl-10 pr-4 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                    />
                </div>

                {/* Level Filter */}
                <select
                    value={filterLevel}
                    onChange={(e) => setFilterLevel(e.target.value as typeof filterLevel)}
                    className="px-4 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                >
                    <option value="all">All Levels</option>
                    <option value="error">Error</option>
                    <option value="warn">Warn</option>
                    <option value="info">Info</option>
                    <option value="debug">Debug</option>
                </select>

                {/* Source Filter */}
                <select
                    value={filterSource}
                    onChange={(e) => setFilterSource(e.target.value)}
                    className="px-4 py-2 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                >
                    {sources.map(source => (
                        <option key={source} value={source}>
                            {source === 'all' ? 'All Sources' : source}
                        </option>
                    ))}
                </select>
            </div>

            {/* Logs Display */}
            <div className="bg-slate-900 dark:bg-black rounded-lg border border-slate-700 overflow-hidden">
                <div className="h-[600px] overflow-y-auto p-4 font-mono text-sm">
                    {filteredLogs.length === 0 ? (
                        <div className="text-center py-12 text-slate-500">
                            No logs to display
                        </div>
                    ) : (
                        <div className="space-y-1">
                            {filteredLogs.map((log, index) => {
                                const config = levelConfig[log.level];
                                return (
                                    <div
                                        key={`${log.timestamp.getTime()}-${index}`}
                                        className={`${config.bg} px-3 py-1 rounded hover:bg-opacity-80 transition-colors`}
                                    >
                                        <span className="text-slate-500 dark:text-neutral-500">
                                            [{log.timestamp.toISOString()}]
                                        </span>
                                        {' '}
                                        <span className={`font-semibold ${config.color}`}>
                                            [{log.level.toUpperCase()}]
                                        </span>
                                        {' '}
                                        <span className="text-purple-600 dark:text-purple-400">
                                            [{log.source}]
                                        </span>
                                        {' '}
                                        <span className="text-slate-800 dark:text-neutral-200">
                                            {log.message}
                                        </span>
                                        {log.metadata && (
                                            <div className="ml-4 mt-1 text-xs text-slate-600 dark:text-neutral-400">
                                                {JSON.stringify(log.metadata)}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                            <div ref={logsEndRef} />
                        </div>
                    )}
                </div>
            </div>

            {/* Footer Info */}
            <div className="text-sm text-slate-500 dark:text-neutral-500 text-center">
                Showing {filteredLogs.length} of {logs.length} logs
                {searchQuery && ` (filtered by "${searchQuery}")`}
            </div>
        </div>
    );
}

// Types
interface LogEntry {
    timestamp: Date;
    level: 'error' | 'warn' | 'info' | 'debug';
    source: string;
    message: string;
    metadata?: Record<string, unknown>;
}

// Helper Components
function StatCard({ label, value, icon }: { label: string; value: number; icon: React.ReactNode }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{label}</div>
                </div>
                <div className="text-slate-400 dark:text-neutral-500">
                    {icon}
                </div>
            </div>
        </div>
    );
}


