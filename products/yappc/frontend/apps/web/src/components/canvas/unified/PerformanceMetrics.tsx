/**
 * PerformanceMetrics - Canvas Performance Display
 * 
 * Shows real-time performance metrics for viewport culling
 * 
 * @doc.type component
 * @doc.purpose Performance monitoring overlay
 * @doc.layer components
 * @doc.pattern Component
 */

import React from 'react';
import {
  Box,
  Stack,
  Typography,
  IconButton,
  Surface as Paper,
} from '@ghatana/ui';
import { X as CloseIcon, Gauge as SpeedIcon } from 'lucide-react';

interface PerformanceMetricsProps {
    totalNodes: number;
    visibleNodes: number;
    cullingRatio: number;
    fps?: number;
    renderTime?: number;
    onClose?: () => void;
}

export function PerformanceMetrics({
    totalNodes,
    visibleNodes,
    cullingRatio,
    fps = 60,
    renderTime = 0,
    onClose
}: PerformanceMetricsProps) {
    const culledNodes = totalNodes - visibleNodes;
    const cullingPercentage = Math.round(cullingRatio * 100);

    // Determine performance status
    const getPerformanceColor = () => {
        if (fps >= 55) return 'success.main';
        if (fps >= 45) return 'warning.main';
        return 'error.main';
    };

    return (
        <Paper
            elevation={3}
            className="absolute p-4 rounded-lg top-[16px] right-[16px] z-[1000] min-w-[280px] bg-white dark:bg-gray-900"
        >
            <Stack spacing={1.5}>
                {/* Header */}
                <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Box display="flex" alignItems="center" gap={1}>
                        <SpeedIcon size={16} />
                        <Typography variant="subtitle2" fontWeight="bold">
                            Performance Metrics
                        </Typography>
                    </Box>
                    {onClose && (
                        <IconButton size="small" onClick={onClose}>
                            <CloseIcon size={16} />
                        </IconButton>
                    )}
                </Box>

                {/* Metrics Grid */}
                <Stack spacing={1}>
                    {/* Nodes */}
                    <Box>
                        <Typography variant="caption" color="text.secondary">
                            Total Nodes
                        </Typography>
                        <Typography variant="h6" fontWeight="medium">
                            {totalNodes.toLocaleString()}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography variant="caption" color="text.secondary">
                            Visible Nodes
                        </Typography>
                        <Typography variant="h6" fontWeight="medium" color="primary.main">
                            {visibleNodes.toLocaleString()}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography variant="caption" color="text.secondary">
                            Culled Nodes
                        </Typography>
                        <Typography variant="h6" fontWeight="medium" color="text.disabled">
                            {culledNodes.toLocaleString()} ({cullingPercentage}%)
                        </Typography>
                    </Box>

                    {/* FPS */}
                    <Box>
                        <Typography variant="caption" color="text.secondary">
                            Frame Rate
                        </Typography>
                        <Typography
                            variant="h6"
                            fontWeight="medium"
                            color={getPerformanceColor()}
                        >
                            {fps.toFixed(1)} FPS
                        </Typography>
                    </Box>

                    {/* Render Time */}
                    {renderTime > 0 && (
                        <Box>
                            <Typography variant="caption" color="text.secondary">
                                Render Time
                            </Typography>
                            <Typography variant="h6" fontWeight="medium">
                                {renderTime.toFixed(2)}ms
                            </Typography>
                        </Box>
                    )}

                    {/* Performance Status */}
                    <Box
                        className={`mt-2 p-2 rounded ${fps >= 55 ? 'bg-green-100' : fps >= 45 ? 'bg-yellow-100' : 'bg-red-100'}`}
                    >
                        <Typography
                            variant="caption"
                            fontWeight="bold"
                            color={fps >= 55 ? 'success.dark' : fps >= 45 ? 'warning.dark' : 'error.dark'}
                        >
                            {fps >= 55 ? '✓ Excellent' : fps >= 45 ? '⚠ Moderate' : '✗ Poor'}
                        </Typography>
                    </Box>
                </Stack>

                {/* Culling Info */}
                {totalNodes >= 50 && (
                    <Box
                        className="mt-2 p-2 rounded bg-sky-100 dark:bg-sky-900/30 opacity-[0.1]"
                    >
                        <Typography variant="caption" color="info.dark">
                            ℹ Viewport culling active
                        </Typography>
                    </Box>
                )}
            </Stack>
        </Paper>
    );
}
