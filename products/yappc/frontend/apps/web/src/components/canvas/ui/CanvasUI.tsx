/**
 * Canvas UI Panels Module
 * 
 * Extracts UI panel components from CanvasScene
 * 
 * @doc.type component
 * @doc.purpose Canvas UI panel components
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Stack,
  Typography,
  Chip,
} from '@ghatana/ui';
import { Sparkles as AutoAwesome, FileText as Description, Pencil as Edit, Lightbulb } from 'lucide-react';
import { Panel } from '@xyflow/react';
import { HistoryToolbar } from '../HistoryToolbar';
import type { AutoSaveStatus } from '../versioning/AutoSaveIndicator';

// ============================================================================
// Canvas Stats Panel
// ============================================================================

export interface CanvasStatsPanelProps {
    nodeCount: number;
    edgeCount: number;
}

export const CanvasStatsPanel: React.FC<CanvasStatsPanelProps> = ({
    nodeCount,
    edgeCount,
}) => {
    return (
        <Panel position="top-right" style={{ zIndex: 20 }}>
            <Box className="rounded px-3 py-1 text-sm" style={{ backgroundColor: 'rgba(255, 255, 255, 0.9)' }}>
                Nodes: {nodeCount} | Edges: {edgeCount}
            </Box>
        </Panel>
    );
};

// ============================================================================
// Canvas History Panel
// ============================================================================

export interface CanvasHistoryPanelProps {
    projectId: string;
    canvasId: string;
}

export const CanvasHistoryPanel: React.FC<CanvasHistoryPanelProps> = ({
    projectId,
    canvasId,
}) => {
    return (
        <Panel position="top-left" style={{ zIndex: 20 }}>
            <Box>
                <HistoryToolbar
                    projectId={projectId}
                    canvasId={canvasId}
                    size="small"
                />
            </Box>
        </Panel>
    );
};

// ============================================================================
// Canvas Info Panel (Collapsible)
// ============================================================================

export interface CanvasInfoPanelProps {
    title: string;
    children: React.ReactNode;
    open?: boolean;
    onToggle?: () => void;
}

export const CanvasInfoPanel: React.FC<CanvasInfoPanelProps> = ({
    title,
    children,
    open = true,
    onToggle,
}) => {
    return (
        <Box
            className="absolute rounded bottom-[16px] right-[16px] w-[300px] shadow z-10" >
            <Box
                onClick={onToggle}
                className="p-2 border-gray-200 dark:border-gray-700 flex justify-between items-center" style={{ borderBottom: open ? 1 : 0, cursor: onToggle ? 'pointer' : 'default' }}
            >
                <Typography variant="subtitle2" fontWeight="bold">
                    {title}
                </Typography>
                {onToggle && (
                    <Typography variant="caption">
                        {open ? '▼' : '▶'}
                    </Typography>
                )}
            </Box>
            {open && (
                <Box className="p-4">
                    {children}
                </Box>
            )}
        </Box>
    );
};

// ============================================================================
// Performance Info Panel
// ============================================================================

export interface PerformanceMetrics {
    fps: number;
    elements: number;
    frameTime: number;
    renderTime: number;
}

export interface PerformanceInfoPanelProps {
    metrics: PerformanceMetrics;
    enabled: boolean;
    onToggle: () => void;
}

export const PerformanceInfoPanel: React.FC<PerformanceInfoPanelProps> = ({
    metrics,
    enabled,
    onToggle,
}) => {
    return (
        <Box
            className="absolute bottom-[16px] left-[16px] w-[250px] rounded shadow z-10" style={{ backgroundColor: enabled ? 'rgba(255,255,255,0.95)' : 'rgba(200,200,200,0.8)' }}
        >
            <Box
                onClick={onToggle}
                className="p-2 cursor-pointer flex justify-between items-center border-gray-200 dark:border-gray-700 border-b" >
                <Typography variant="subtitle2" fontWeight="bold">
                    Performance
                </Typography>
                <Chip
                    label={enabled ? 'ON' : 'OFF'}
                    size="small"
                    color={enabled ? 'success' : 'default'}
                />
            </Box>
            {enabled && (
                <Box className="p-4">
                    <Stack spacing={0.5}>
                        <Box display="flex" justifyContent="space-between">
                            <Typography variant="caption">FPS:</Typography>
                            <Typography variant="caption" fontWeight="bold">
                                {metrics.fps}
                            </Typography>
                        </Box>
                        <Box display="flex" justifyContent="space-between">
                            <Typography variant="caption">Elements:</Typography>
                            <Typography variant="caption" fontWeight="bold">
                                {metrics.elements}
                            </Typography>
                        </Box>
                        <Box display="flex" justifyContent="space-between">
                            <Typography variant="caption">Frame Time:</Typography>
                            <Typography variant="caption" fontWeight="bold">
                                {metrics.frameTime}ms
                            </Typography>
                        </Box>
                        <Box display="flex" justifyContent="space-between">
                            <Typography variant="caption">Render Time:</Typography>
                            <Typography variant="caption" fontWeight="bold">
                                {metrics.renderTime}ms
                            </Typography>
                        </Box>
                    </Stack>
                </Box>
            )}
        </Box>
    );
};

// ============================================================================
// Loading Overlay
// ============================================================================

export interface LoadingOverlayProps {
    message?: string;
}

export const LoadingOverlay: React.FC<LoadingOverlayProps> = ({
    message = 'Loading canvas...',
}) => {
    return (
        <Box
            className="flex items-center justify-center h-screen flex-col gap-4"
        >
            <Box
                className="w-[48px] h-[48px] border border-solid border-[rgba(0,0,0,0.1)] rounded-full"
            />
            <Typography variant="body2">{message}</Typography>
        </Box>
    );
};

// ============================================================================
// Empty Canvas State (Enhanced)
// ============================================================================

export interface EmptyCanvasStateProps {
    onUseTemplate?: () => void;
    onStartBlank?: () => void;
    onAskAI?: () => void;
    onGetStarted?: () => void; // Deprecated, kept for compatibility
}

export const EmptyCanvasState: React.FC<EmptyCanvasStateProps> = ({
    onUseTemplate,
    onStartBlank,
    onAskAI,
    onGetStarted,
}) => {
    return (
        <Box
            className="absolute text-center top-[50%] left-[50%] w-[600px] max-w-[90%] z-[5] pointer-events-auto" style={{ transform: 'translate(-50%' }} >
            <Stack spacing={3}>
                {/* Header */}
                <Box>
                    <Typography variant="h4" gutterBottom className="font-semibold">
                        Welcome to Your Canvas
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Choose how you'd like to start building your application
                    </Typography>
                </Box>

                {/* Action Cards */}
                <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={2}
                    className="mt-6"
                >
                    {/* Use Template Card */}
                    <Box
                        onClick={onUseTemplate}
                        className="flex-1 p-6 border-[2px_solid] border-blue-600 rounded-lg cursor-pointer transition-all duration-200 bg-white dark:bg-gray-900 hover:shadow-md"
                    >
                        <Box
                            className="rounded-full flex items-center justify-center mb-4 w-[48px] h-[48px] bg-blue-100 mx-auto"
                        >
                            <Description size={32} />
                        </Box>
                        <Typography variant="h6" gutterBottom>
                            Use Template
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            Start with pre-built architectures for common patterns
                        </Typography>
                    </Box>

                    {/* Start from Blank Card */}
                    <Box
                        onClick={onStartBlank}
                        className="flex-1 p-6 border-[2px_solid] border-gray-200 dark:border-gray-700 rounded-lg cursor-pointer transition-all duration-200 bg-white dark:bg-gray-900 hover:shadow-md"
                    >
                        <Box
                            className="rounded-full flex items-center justify-center mb-4 w-[48px] h-[48px] mx-auto bg-gray-200" >
                            <Edit size={32} />
                        </Box>
                        <Typography variant="h6" gutterBottom>
                            Blank Canvas
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            Build your architecture from scratch
                        </Typography>
                    </Box>

                    {/* AI Assistant Card */}
                    <Box
                        onClick={onAskAI}
                        className="flex-1 p-6 border-[2px_solid] border-gray-200 dark:border-gray-700 rounded-lg cursor-pointer transition-all duration-200 bg-white dark:bg-gray-900 hover:shadow-md"
                    >
                        <Box
                            className="rounded-full flex items-center justify-center mb-4 w-[48px] h-[48px] mx-auto bg-purple-400" >
                            <AutoAwesome size={32} />
                        </Box>
                        <Typography variant="h6" gutterBottom>
                            AI Assistant
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            Describe your app and let AI design it
                        </Typography>
                    </Box>
                </Stack>

                {/* Quick Tips */}
                <Box
                    className="mt-8 p-4 rounded border border-solid bg-sky-50" style={{ borderColor: 'info.200' }} >
                    <Typography variant="body2" className="font-medium mb-2 flex items-center gap-1">
                        <Lightbulb size={16} />
                        Quick Tips:
                    </Typography>
                    <Stack spacing={0.5} alignItems="flex-start">
                        <Typography variant="caption" color="text.secondary">
                            • Drag components from the left palette to add them to your canvas
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            • Use the toolbar at the top to switch between design phases
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            • Press 'C' to open the command palette for quick actions
                        </Typography>
                    </Stack>
                </Box>
            </Stack>
        </Box>
    );
};
