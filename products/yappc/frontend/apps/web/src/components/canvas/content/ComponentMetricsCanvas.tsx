/**
 * Component Metrics Canvas Content
 * 
 * Component performance metrics for Observe × Component level.
 * Monitor individual component metrics.
 * 
 * @doc.type component
 * @doc.purpose Component-level performance monitoring
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

interface ComponentMetrics {
    id: string;
    name: string;
    path: string;
    renderTime: number;
    renderCount: number;
    errorCount: number;
    memoryUsage: number;
    lastRender: string;
    props: number;
    children: number;
}

const MOCK_METRICS: ComponentMetrics[] = [
    {
        id: '1',
        name: 'UserDashboard',
        path: 'src/components/UserDashboard.tsx',
        renderTime: 12.5,
        renderCount: 453,
        errorCount: 0,
        memoryUsage: 2.4,
        lastRender: '2s ago',
        props: 8,
        children: 5,
    },
    {
        id: '2',
        name: 'DataTable',
        path: 'src/components/DataTable.tsx',
        renderTime: 45.2,
        renderCount: 892,
        errorCount: 3,
        memoryUsage: 8.1,
        lastRender: '5s ago',
        props: 12,
        children: 150,
    },
    {
        id: '3',
        name: 'SearchBar',
        path: 'src/components/SearchBar.tsx',
        renderTime: 3.8,
        renderCount: 1250,
        errorCount: 0,
        memoryUsage: 0.8,
        lastRender: '1s ago',
        props: 5,
        children: 2,
    },
    {
        id: '4',
        name: 'Modal',
        path: 'src/components/Modal.tsx',
        renderTime: 18.3,
        renderCount: 120,
        errorCount: 1,
        memoryUsage: 3.2,
        lastRender: '15s ago',
        props: 6,
        children: 1,
    },
    {
        id: '5',
        name: 'Chart',
        path: 'src/components/Chart.tsx',
        renderTime: 85.6,
        renderCount: 245,
        errorCount: 8,
        memoryUsage: 15.4,
        lastRender: '3s ago',
        props: 15,
        children: 0,
    },
    {
        id: '6',
        name: 'Button',
        path: 'src/components/Button.tsx',
        renderTime: 1.2,
        renderCount: 3450,
        errorCount: 0,
        memoryUsage: 0.3,
        lastRender: '0s ago',
        props: 4,
        children: 1,
    },
];

const getRenderTimeColor = (time: number) => {
    if (time < 10) return '#10B981';
    if (time < 50) return '#F59E0B';
    return '#EF4444';
};

export const ComponentMetricsCanvas = () => {
    const [metrics] = useState<ComponentMetrics[]>(MOCK_METRICS);
    const [selectedMetric, setSelectedMetric] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredMetrics = useMemo(() => {
        return metrics.filter(
            m =>
                searchQuery === '' ||
                m.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                m.path.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [metrics, searchQuery]);

    const stats = useMemo(() => {
        return {
            total: metrics.length,
            avgRenderTime: Math.round((metrics.reduce((sum, m) => sum + m.renderTime, 0) / metrics.length) * 10) / 10,
            totalRenders: metrics.reduce((sum, m) => sum + m.renderCount, 0),
            totalErrors: metrics.reduce((sum, m) => sum + m.errorCount, 0),
            totalMemory: Math.round(metrics.reduce((sum, m) => sum + m.memoryUsage, 0) * 10) / 10,
        };
    }, [metrics]);

    const hasContent = metrics.length > 0;

    const selectedMetricData = metrics.find(m => m.id === selectedMetric);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <TextField
                        size="small"
                        placeholder="Search components..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full"
                    />
                </Box>

                <Box className="flex-1 flex gap-4 overflow-hidden p-4">
                    <Box className="overflow-y-auto transition-all duration-300" style={{ width: selectedMetricData ? '50%' : '100%' }}>
                        {filteredMetrics.map(metric => (
                            <Paper
                                key={metric.id}
                                elevation={selectedMetric === metric.id ? 4 : 2}
                                onClick={() => setSelectedMetric(metric.id === selectedMetric ? null : metric.id)}
                                className="p-3 mb-2 cursor-pointer transition-all duration-200 hover:shadow-lg" style={{ border: selectedMetric === metric.id ? '3px solid #3B82F6' : '2px solid transparent' }}
                            >
                                <Box className="flex gap-2 mb-1">
                                    <Box className="flex-1">
                                        <Typography variant="body2" className="text-[0.85rem] font-semibold mb-[2.4px]">
                                            {metric.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem] font-mono">
                                            {metric.path}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={`${metric.renderTime}ms`}
                                        size="small"
                                        className="h-[20px] text-[0.7rem] text-white" />
                                </Box>
                                <Box className="flex gap-3 mt-1">
                                    <Typography variant="caption" color="text.secondary">
                                        Renders: {metric.renderCount}
                                    </Typography>
                                    {metric.errorCount > 0 && (
                                        <Typography variant="caption" className="text-[#EF4444]">
                                            Errors: {metric.errorCount}
                                        </Typography>
                                    )}
                                    <Typography variant="caption" color="text.secondary">
                                        Memory: {metric.memoryUsage}MB
                                    </Typography>
                                </Box>
                            </Paper>
                        ))}
                    </Box>

                    {selectedMetricData && (
                        <Box className="overflow-y-auto w-[50%]">
                            <Paper elevation={3} className="p-4">
                                <Typography variant="subtitle2" className="font-semibold mb-1">
                                    {selectedMetricData.name}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" className="block font-mono mb-4">
                                    {selectedMetricData.path}
                                </Typography>

                                <Typography variant="caption" className="font-semibold block mb-1">
                                    Performance Metrics
                                </Typography>
                                <Box className="grid gap-2 mb-4" >
                                    <Paper className="p-2 bg-[#F8FAFC]">
                                        <Typography variant="caption" color="text.secondary" display="block">
                                            Render Time
                                        </Typography>
                                        <Typography variant="body2" className="font-semibold" style={{ color: getRenderTimeColor(selectedMetricData.renderTime), gridTemplateColumns: 'repeat(2 }}>
                                            {selectedMetricData.renderTime}ms
                                        </Typography>
                                    </Paper>
                                    <Paper className="p-2 bg-[#F8FAFC]">
                                        <Typography variant="caption" color="text.secondary" display="block">
                                            Render Count
                                        </Typography>
                                        <Typography variant="body2" className="font-semibold">
                                            {selectedMetricData.renderCount}
                                        </Typography>
                                    </Paper>
                                    <Paper className="p-2 bg-[#F8FAFC]">
                                        <Typography variant="caption" color="text.secondary" display="block">
                                            Memory Usage
                                        </Typography>
                                        <Typography variant="body2" className="font-semibold">
                                            {selectedMetricData.memoryUsage}MB
                                        </Typography>
                                    </Paper>
                                    <Paper className="p-2 bg-[#F8FAFC]">
                                        <Typography variant="caption" color="text.secondary" display="block">
                                            Error Count
                                        </Typography>
                                        <Typography variant="body2" className="font-semibold" style={{ color: selectedMetricData.errorCount > 0 ? '#EF4444' : '#10B981', transform: 'translate(-50%' }}>
                                            {selectedMetricData.errorCount}
                                        </Typography>
                                    </Paper>
                                </Box>

                                <Typography variant="caption" className="font-semibold block mb-1">
                                    Component Details
                                </Typography>
                                <Paper className="p-2 bg-[#F8FAFC] mb-4">
                                    <Typography variant="caption" display="block">
                                        Props: {selectedMetricData.props}
                                    </Typography>
                                    <Typography variant="caption" display="block">
                                        Children: {selectedMetricData.children}
                                    </Typography>
                                    <Typography variant="caption" display="block">
                                        Last Render: {selectedMetricData.lastRender}
                                    </Typography>
                                </Paper>

                                <Typography variant="caption" className="font-semibold block mb-1">
                                    Performance Score
                                </Typography>
                                <Box
                                    className="w-full rounded overflow-hidden relative h-[24px] bg-[#E5E7EB]"
                                >
                                    <Box
                                        style={{ width: `${Math.max(0 }}
                                    />
                                    <Typography
                                        variant="caption"
                                        className="absolute font-semibold top-[50%] left-[50%] text-[#1E293B]" >
                                        {Math.round(Math.max(0, 100 - selectedMetricData.renderTime))}
                                    </Typography>
                                </Box>
                            </Paper>
                        </Box>
                    )}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Overall Metrics
                    </Typography>
                    <Typography variant="caption" display="block">
                        Components: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block">
                        Avg Render: {stats.avgRenderTime}ms
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total Renders: {stats.totalRenders}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: stats.totalErrors > 0 ? '#EF4444' : '#10B981' }}>
                        Total Errors: {stats.totalErrors}
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total Memory: {stats.totalMemory}MB
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default ComponentMetricsCanvas;
