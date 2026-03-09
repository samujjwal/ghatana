/**
 * Log Viewer Canvas Content
 * 
 * Log file viewer for Observe × File level.
 * Displays application logs with filtering and search capabilities.
 * 
 * @doc.type component
 * @doc.purpose Log file viewer with filtering and search
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo, useRef, useEffect } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  IconButton,
  Switch,
  FormControlLabel,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { Search, Download, RefreshCw as Refresh, Play as PlayArrow, Pause } from 'lucide-react';

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogEntry {
    timestamp: string;
    level: LogLevel;
    source: string;
    message: string;
    metadata?: Record<string, unknown>;
}

// Mock log data
const generateMockLogs = (): LogEntry[] => {
    const levels: LogLevel[] = ['debug', 'info', 'warn', 'error'];
    const sources = ['API', 'Database', 'Auth', 'Cache', 'Queue', 'Worker'];
    const messages = [
        'Request received',
        'Query executed successfully',
        'Connection pool exhausted',
        'Token validation failed',
        'Cache miss for key',
        'Job processing started',
        'Slow query detected',
        'Rate limit exceeded',
        'Health check passed',
        'Configuration loaded',
    ];

    return Array.from({ length: 100 }, (_, i) => {
        const level = levels[Math.floor(Math.random() * levels.length)];
        const source = sources[Math.floor(Math.random() * sources.length)];
        const message = messages[Math.floor(Math.random() * messages.length)];
        const timestamp = new Date(Date.now() - i * 5000).toISOString();

        return {
            timestamp,
            level,
            source,
            message,
            ...(Math.random() > 0.7 && {
                metadata: {
                    duration: Math.floor(Math.random() * 1000),
                    userId: `user-${Math.floor(Math.random() * 1000)}`,
                },
            }),
        };
    });
};

const getLevelColor = (level: LogLevel) => {
    switch (level) {
        case 'debug':
            return '#9E9E9E';
        case 'info':
            return '#2196F3';
        case 'warn':
            return '#FF9800';
        case 'error':
            return '#F44336';
    }
};

const getLevelBgColor = (level: LogLevel) => {
    switch (level) {
        case 'debug':
            return 'rgba(158, 158, 158, 0.1)';
        case 'info':
            return 'rgba(33, 150, 243, 0.1)';
        case 'warn':
            return 'rgba(255, 152, 0, 0.1)';
        case 'error':
            return 'rgba(244, 67, 54, 0.1)';
    }
};

const LogEntryItem = ({ log }: { log: LogEntry }) => {
    const [expanded, setExpanded] = useState(false);

    return (
        <Box
            onClick={() => log.metadata && setExpanded(!expanded)}
            className="p-[6px 12px] font-mono text-[0.8rem]" style={{ borderLeft: `3px solid ${getLevelColor(log.level), backgroundColor: 'rgba(0', backgroundColor: getLevelColor(log.level) }}
        >
            <Box className="flex items-center gap-4">
                <Typography
                    variant="caption"
                    className="text-gray-500 dark:text-gray-400 min-w-[180px] font-mono"
                >
                    {new Date(log.timestamp).toLocaleString()}
                </Typography>
                <Chip
                    label={log.level.toUpperCase()}
                    size="small"
                    className="font-semibold h-[20px] text-[0.7remgetLevelColor(log.level) */
                />
                <Chip
                    label={log.source}
                    size="small"
                    variant="outlined"
                    className="h-[20px] text-[0.7rem] min-w-[80px]"
                />
                <Typography
                    variant="body2"
                    className="flex-1 font-mono"
                >
                    {log.message}
                </Typography>
            </Box>

            {expanded && log.metadata && (
                <Box
                    className="rounded text-xs mt-2 p-2" >
                    <Typography variant="caption" className="font-semibold">
                        Metadata:
                    </Typography>
                    <pre style={{ margin: 0, marginTop: 4 }}>
                        {JSON.stringify(log.metadata, null, 2)}
                    </pre>
                </Box>
            )}
        </Box>
    );
};

export const LogViewerCanvas = () => {
    const [logs, setLogs] = useState<LogEntry[]>(generateMockLogs());
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedLevels, setSelectedLevels] = useState<Set<LogLevel>>(new Set(['info', 'warn', 'error']));
    const [autoScroll, setAutoScroll] = useState(true);
    const [isPaused, setIsPaused] = useState(false);
    const logContainerRef = useRef<HTMLDivElement>(null);

    // Auto-scroll to bottom when new logs arrive
    useEffect(() => {
        if (autoScroll && logContainerRef.current && !isPaused) {
            logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
        }
    }, [logs, autoScroll, isPaused]);

    // Simulate real-time log streaming
    useEffect(() => {
        if (isPaused) return;

        const interval = setInterval(() => {
            const newLog: LogEntry = {
                timestamp: new Date().toISOString(),
                level: (['debug', 'info', 'warn', 'error'] as LogLevel[])[Math.floor(Math.random() * 4)],
                source: (['API', 'Database', 'Auth', 'Cache'] as const)[Math.floor(Math.random() * 4)],
                message: 'New log entry generated',
            };
            setLogs(prev => [newLog, ...prev].slice(0, 200)); // Keep last 200 logs
        }, 3000);

        return () => clearInterval(interval);
    }, [isPaused]);

    const filteredLogs = useMemo(() => {
        return logs.filter(log => {
            const matchesSearch = searchQuery === '' ||
                log.message.toLowerCase().includes(searchQuery.toLowerCase()) ||
                log.source.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesLevel = selectedLevels.has(log.level);
            return matchesSearch && matchesLevel;
        });
    }, [logs, searchQuery, selectedLevels]);

    const toggleLevel = (level: LogLevel) => {
        setSelectedLevels(prev => {
            const newSet = new Set(prev);
            if (newSet.has(level)) {
                newSet.delete(level);
            } else {
                newSet.add(level);
            }
            return newSet;
        });
    };

    const hasContent = logs.length > 0;

    const handleRefresh = () => {
        setLogs(generateMockLogs());
    };

    const handleDownload = () => {
        const content = filteredLogs
            .map(log => `[${log.timestamp}] ${log.level.toUpperCase()} [${log.source}] ${log.message}`)
            .join('\n');
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `logs-${new Date().toISOString()}.txt`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Start Logging',
                    onClick: () => {
                        setLogs(generateMockLogs());
                    },
                },
            }}
        >
            <Box
                className="h-full w-full flex flex-col bg-[#1E1E1E] text-[#D4D4D4]"
            >
                {/* Toolbar */}
                <Box
                    className="flex items-center gap-4 p-3" style={{ borderBottom: '1px solid rgba(255, backgroundColor: 'rgba(255 }} >
                    <TextField
                        size="small"
                        placeholder="Search logs..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        InputProps={{
                            startAdornment: <Search className="mr-2 text-[rgba(255,_255,_255,_0.5)]" />,
                        }}
                        className="flex-1 text-white" style={{ backgroundColor: 'rgba(255' }} />

                    <Box className="flex gap-1">
                        {(['debug', 'info', 'warn', 'error'] as LogLevel[]).map((level) => (
                            <Chip
                                key={level}
                                label={level.toUpperCase()}
                                size="small"
                                onClick={() => toggleLevel(level)}
                                className="text-white font-semibold" style={{ backgroundColor: selectedLevels.has(level) ? getLevelColor(level) : 'rgba(255, 255, 255, 0.1)', backgroundColor: 'rgba(255, backgroundColor: 'rgba(255' }}
                            />
                        ))}
                    </Box>

                    <FormControlLabel
                        control={
                            <Switch
                                checked={autoScroll}
                                onChange={(e) => setAutoScroll(e.target.checked)}
                                size="small"
                            />
                        }
                        label={<Typography variant="caption">Auto-scroll</Typography>}
                        className="m-0 text-[rgba(255,_255,_255,_0.7)]"
                    />

                    <IconButton
                        size="small"
                        onClick={() => setIsPaused(!isPaused)}
                        style={{ color: isPaused ? '#FF9800' : 'rgba(255, 255, 255, 0.7)', borderTop: '1px solid rgba(255, backgroundColor: 'rgba(255 }}
                    >
                        {isPaused ? <PlayArrow /> : <Pause />}
                    </IconButton>

                    <IconButton
                        size="small"
                        onClick={handleRefresh}
                        className="text-[rgba(255,_255,_255,_0.7)]"
                    >
                        <Refresh />
                    </IconButton>

                    <IconButton
                        size="small"
                        onClick={handleDownload}
                        className="text-[rgba(255,_255,_255,_0.7)]"
                    >
                        <Download />
                    </IconButton>
                </Box>

                {/* Logs display */}
                <Box
                    ref={logContainerRef} x: backgroundColor: 'rgba(255, backgroundColor: 'rgba(255 */
                >
                    {filteredLogs.map((log, index) => (
                        <LogEntryItem key={`${log.timestamp}-${index}`} log={log} />
                    ))}
                    {filteredLogs.length === 0 && (
                        <Box className="text-center p-8">
                            <Typography color="rgba(255, 255, 255, 0.5)">
                                No logs match your filters
                            </Typography>
                        </Box>
                    )}
                </Box>

                {/* Status bar */}
                <Box
                    className="flex justify-between items-center p-[6px 12px]" >
                    <Typography variant="caption" className="font-mono text-[rgba(255,_255,_255,_0.7)]">
                        {filteredLogs.length} / {logs.length} logs
                    </Typography>
                    <Typography variant="caption" className="font-mono text-[rgba(255,_255,_255,_0.7)]">
                        {isPaused ? '⏸ PAUSED' : '▶ STREAMING'}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default LogViewerCanvas;
