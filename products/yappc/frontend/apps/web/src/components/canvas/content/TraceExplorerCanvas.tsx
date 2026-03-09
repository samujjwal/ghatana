/**
 * Trace Explorer Canvas Content
 * 
 * Distributed tracing explorer for Observe × Code level.
 * Visualize request traces and spans.
 * 
 * @doc.type component
 * @doc.purpose Distributed tracing visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface Span {
    id: string;
    name: string;
    service: string;
    duration: number;
    startTime: number;
    status: 'success' | 'error';
    tags: Record<string, string>;
}

interface Trace {
    id: string;
    name: string;
    timestamp: string;
    totalDuration: number;
    spanCount: number;
    status: 'success' | 'error';
    spans: Span[];
}

const MOCK_TRACES: Trace[] = [
    {
        id: '1',
        name: 'GET /api/users',
        timestamp: '2024-01-15 10:30:45',
        totalDuration: 245,
        spanCount: 5,
        status: 'success',
        spans: [
            { id: '1-1', name: 'api-gateway', service: 'gateway', duration: 12, startTime: 0, status: 'success', tags: { method: 'GET' } },
            { id: '1-2', name: 'auth-check', service: 'auth', duration: 25, startTime: 12, status: 'success', tags: { user: 'john' } },
            { id: '1-3', name: 'db-query', service: 'database', duration: 180, startTime: 37, status: 'success', tags: { query: 'SELECT' } },
            { id: '1-4', name: 'cache-read', service: 'redis', duration: 8, startTime: 217, status: 'success', tags: { key: 'users:*' } },
            { id: '1-5', name: 'response', service: 'gateway', duration: 20, startTime: 225, status: 'success', tags: { status: '200' } },
        ],
    },
    {
        id: '2',
        name: 'POST /api/orders',
        timestamp: '2024-01-15 10:31:12',
        totalDuration: 485,
        spanCount: 7,
        status: 'error',
        spans: [
            { id: '2-1', name: 'api-gateway', service: 'gateway', duration: 15, startTime: 0, status: 'success', tags: { method: 'POST' } },
            { id: '2-2', name: 'auth-check', service: 'auth', duration: 28, startTime: 15, status: 'success', tags: { user: 'jane' } },
            { id: '2-3', name: 'validate-order', service: 'orders', duration: 45, startTime: 43, status: 'success', tags: {} },
            { id: '2-4', name: 'payment-gateway', service: 'payment', duration: 320, startTime: 88, status: 'error', tags: { error: 'timeout' } },
            { id: '2-5', name: 'db-rollback', service: 'database', duration: 52, startTime: 408, status: 'success', tags: {} },
            { id: '2-6', name: 'notification', service: 'notify', duration: 25, startTime: 460, status: 'success', tags: { type: 'email' } },
            { id: '2-7', name: 'response', service: 'gateway', duration: 25, startTime: 460, status: 'error', tags: { status: '500' } },
        ],
    },
    {
        id: '3',
        name: 'GET /api/products',
        timestamp: '2024-01-15 10:31:55',
        totalDuration: 125,
        spanCount: 4,
        status: 'success',
        spans: [
            { id: '3-1', name: 'api-gateway', service: 'gateway', duration: 10, startTime: 0, status: 'success', tags: { method: 'GET' } },
            { id: '3-2', name: 'cache-hit', service: 'redis', duration: 5, startTime: 10, status: 'success', tags: { key: 'products:*' } },
            { id: '3-3', name: 'transform', service: 'gateway', duration: 95, startTime: 15, status: 'success', tags: {} },
            { id: '3-4', name: 'response', service: 'gateway', duration: 15, startTime: 110, status: 'success', tags: { status: '200' } },
        ],
    },
];

const getStatusColor = (status: 'success' | 'error') => {
    return status === 'success' ? '#10B981' : '#EF4444';
};

const getServiceColor = (service: string) => {
    const colors: Record<string, string> = {
        gateway: '#3B82F6',
        auth: '#8B5CF6',
        database: '#10B981',
        redis: '#F59E0B',
        orders: '#EC4899',
        payment: '#06B6D4',
        notify: '#F97316',
    };
    return colors[service] || '#6B7280';
};

export const TraceExplorerCanvas = () => {
    const [traces] = useState<Trace[]>(MOCK_TRACES);
    const [selectedTrace, setSelectedTrace] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredTraces = useMemo(() => {
        return traces.filter(
            t =>
                searchQuery === '' ||
                t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                t.id.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [traces, searchQuery]);

    const stats = useMemo(() => {
        return {
            total: traces.length,
            success: traces.filter(t => t.status === 'success').length,
            error: traces.filter(t => t.status === 'error').length,
            avgDuration: Math.round(traces.reduce((sum, t) => sum + t.totalDuration, 0) / traces.length),
        };
    }, [traces]);

    const hasContent = traces.length > 0;

    const selectedTraceData = traces.find(t => t.id === selectedTrace);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <TextField
                        size="small"
                        placeholder="Search traces..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full"
                    />
                </Box>

                <Box className="flex-1 flex gap-4 overflow-hidden p-4">
                    <Box className="overflow-y-auto transition-all duration-300" style={{ width: selectedTraceData ? '40%' : '100%' }}>
                        {filteredTraces.map(trace => (
                            <Paper
                                key={trace.id}
                                elevation={selectedTrace === trace.id ? 4 : 2}
                                onClick={() => setSelectedTrace(trace.id === selectedTrace ? null : trace.id)}
                                className="p-3 mb-2 cursor-pointer" style={{ border: selectedTrace === trace.id ? `3px solid ${getStatusColor(trace.status)}` : '2px solid transparent' }}
                            >
                                <Box className="flex gap-2 mb-1">
                                    <Box
                                        className="rounded-full shrink-0 w-[12px] h-[12px]" style={{ backgroundColor: getStatusColor(trace.status) }}
                                    />
                                    <Box className="flex-1">
                                        <Typography variant="body2" className="text-[0.85rem] font-semibold mb-[2.4px]">
                                            {trace.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem] font-mono">
                                            {trace.id} • {trace.timestamp}
                                        </Typography>
                                    </Box>
                                    <Chip label={`${trace.totalDuration}ms`} size="small" className="h-[20px] text-[0.7rem]" />
                                </Box>
                                <Typography variant="caption" color="text.secondary">
                                    {trace.spanCount} spans
                                </Typography>
                            </Paper>
                        ))}
                    </Box>

                    {selectedTraceData && (
                        <Box className="overflow-y-auto w-[60%]">
                            <Paper elevation={3} className="p-4 mb-2">
                                <Box className="flex justify-between mb-2" >
                                    <Box>
                                        <Typography variant="subtitle2" className="font-semibold mb-1">
                                            {selectedTraceData.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="font-mono">
                                            {selectedTraceData.id}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={selectedTraceData.status}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getStatusColor(selectedTraceData.status) }}
                                    />
                                </Box>
                                <Typography variant="caption" color="text.secondary" display="block">
                                    {selectedTraceData.timestamp} • Duration: {selectedTraceData.totalDuration}ms • Spans: {selectedTraceData.spanCount}
                                </Typography>
                            </Paper>

                            <Paper elevation={3} className="p-4">
                                <Typography variant="caption" className="font-semibold block mb-2">
                                    Trace Timeline
                                </Typography>
                                {selectedTraceData.spans.map(span => {
                                    const widthPercent = (span.duration / selectedTraceData.totalDuration) * 100;
                                    const leftPercent = (span.startTime / selectedTraceData.totalDuration) * 100;

                                    return (
                                        <Box key={span.id} className="mb-3">
                                            <Box className="flex justify-between mb-[2.4px]">
                                                <Typography variant="caption" className="text-xs font-semibold">
                                                    {span.name}
                                                </Typography>
                                                <Typography variant="caption" className="text-[0.7rem]">
                                                    {span.duration}ms
                                                </Typography>
                                            </Box>
                                            <Box
                                                className="relative w-full rounded overflow-visible h-[24px] bg-[#E5E7EB]"
                                            >
                                                <Box
                                                    className="absolute" style={{ left: `${leftPercent }}
                                                >
                                                    <Typography variant="caption" className="font-semibold text-[0.65rem] text-white">
                                                        {span.service}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                            {Object.keys(span.tags).length > 0 && (
                                                <Box className="flex gap-1 flex-wrap mt-[2.4px]">
                                                    {Object.entries(span.tags).map(([key, value]) => (
                                                        <Chip
                                                            key={key}
                                                            label={`${key}: ${value}`}
                                                            size="small"
                                                            className="h-[16px] text-[0.6rem] bg-[#F3F4F6]"
                                                        />
                                                    ))}
                                                </Box>
                                            )}
                                        </Box>
                                    );
                                })}
                            </Paper>
                        </Box>
                    )}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Trace Stats
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('success') }}>
                        Success: {stats.success}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('error') }}>
                        Error: {stats.error}
                    </Typography>
                    <Typography variant="caption" display="block" className="mt-2">
                        Avg Duration: {stats.avgDuration}ms
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default TraceExplorerCanvas;
