/**
 * Performance Metrics Panel
 *
 * Self-contained panel that reads performance metrics internally.
 * Extracted from CanvasWorkspace so that 60 fps metric updates don't
 * invalidate the parent component's render tree.
 *
 * @doc.type component
 * @doc.purpose Canvas performance metrics display panel
 * @doc.layer product
 * @doc.pattern Extracted Component
 */

import React from 'react';
import { Box, Typography } from '@ghatana/design-system';
import { usePerformanceMetrics } from '../hooks/usePerformanceMetrics';

export const PerformanceMetricsPanel: React.FC<{ nodeCount: number }> = ({ nodeCount }) => {
    const metrics = usePerformanceMetrics(nodeCount);
    return (
        <Box className="p-4 font-mono">
            <Typography as="span" className="text-xs text-fg-muted dark:text-fg-muted block">FPS: {metrics.fps}</Typography>
            <Typography as="span" className="text-xs text-fg-muted dark:text-fg-muted block">Nodes: {metrics.nodeCount}</Typography>
            <Typography as="span" className="text-xs text-fg-muted dark:text-fg-muted block">Render: {metrics.renderTimeMs}ms</Typography>
        </Box>
    );
};
