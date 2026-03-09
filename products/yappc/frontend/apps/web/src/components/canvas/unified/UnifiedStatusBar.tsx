/**
 * UnifiedStatusBar - Bottom Status Bar
 */

import React from 'react';
import {
  Box,
  Typography,
  Chip,
  IconButton,
  Tooltip,
} from '@ghatana/ui';
import { Gauge as SpeedIcon, Users as Group, ListOrdered as ListAlt, Hammer as Build, CheckCircle, AlertCircle as ErrorOutline, CircleDot as RadioButtonChecked } from 'lucide-react';

interface UnifiedStatusBarProps {
    phaseProgress: string;
    techStack: string[];
    collaboratorCount: number;
    saveStatus: 'saved' | 'saving' | 'unsaved';
    zoomLevel: number;
    onTogglePerformanceMetrics?: () => void;
    showPerformanceMetrics?: boolean;
}

export function UnifiedStatusBar({
    phaseProgress,
    techStack,
    collaboratorCount,
    saveStatus,
    zoomLevel,
    onTogglePerformanceMetrics,
    showPerformanceMetrics = false
}: UnifiedStatusBarProps) {

    const saveStatusIcon = {
        saved: <CheckCircle size={16} />,
        saving: <RadioButtonChecked size={16} />,
        unsaved: <ErrorOutline size={16} />
    }[saveStatus];

    const saveStatusColor = {
        saved: 'success.main',
        saving: 'warning.main',
        unsaved: 'error.main'
    }[saveStatus];

    return (
        <Box
            className="flex items-center px-4 gap-6 h-[32px] border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 border-t" >
            {/* Phase Progress */}
            <Typography variant="caption" className="flex items-center gap-1">
                <ListAlt size={16} />
                {phaseProgress}
            </Typography>

            <Box className="w-[1px] h-[16px] bg-gray-200" />

            {/* Tech Stack */}
            <Typography variant="caption" className="flex items-center gap-1">
                <Build size={16} />
                {techStack.join(', ')}
            </Typography>

            <Box className="w-[1px] h-[16px] bg-gray-200" />

            {/* Collaborators */}
            <Typography variant="caption" className="flex items-center gap-1">
                <Group size={16} />
                {collaboratorCount} collaborators
            </Typography>

            {/* Spacer */}
            <Box className="flex-1" />

            {/* Performance Metrics Toggle */}
            {onTogglePerformanceMetrics && (
                <>
                    <Tooltip title="Performance Metrics">
                        <IconButton
                            size="small"
                            onClick={onTogglePerformanceMetrics}
                            style={{ color: showPerformanceMetrics ? 'primary.main' : 'text.secondary', color: 'saveStatusColor' }}
                        >
                            <SpeedIcon size={16} />
                        </IconButton>
                    </Tooltip>
                    <Box clasx: bgcolor: 'divider' */ />
                </>
            )}

            {/* Save Status */}
            <Typography
                variant="caption"
                className="flex items-center gap-1" >
                {saveStatusIcon}
                {saveStatus === 'saved' ? 'Saved' : saveStatus === 'saving' ? 'Saving...' : 'Unsaved'}
            </Typography>

            <Box className="w-[1px] h-[16px] bg-gray-200" />

            {/* Zoom Level */}
            <Typography variant="caption">
                {zoomLevel}%
            </Typography>
        </Box>
    );
}
