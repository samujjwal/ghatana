/**
 * UnifiedTopBar - Top Navigation Bar
 */

import React, { useState } from 'react';
import {
  Box,
  Typography,
  Button,
  IconButton,
  Avatar,
  Menu,
  ListItemIcon,
  ListItemText,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { useAtomValue } from 'jotai';
import { currentPhaseAtom, currentPhaseProgressAtom } from '../../../state/atoms/unifiedCanvasAtom';
import { Download as DownloadIcon, Upload as UploadIcon, GitBranch as LayoutIcon, GitBranch as AccountTree, Lightbulb, CheckCircle, Sparkles as AutoAwesome, Hammer as Build, Play as PlayArrow, Eye as Visibility, RefreshCw as Refresh, Settings as SettingsIcon } from 'lucide-react';
import { getPhaseTheme, type LifecyclePhase } from '../../../theme/phaseTheme';

interface UnifiedTopBarProps {
    projectId?: string;
    onExportImport?: () => void;
    onMindMapLayout?: () => void;
    modeSelector?: React.ReactNode;
    quickActions?: React.ReactNode;
}

export function UnifiedTopBar({ projectId, onExportImport, onMindMapLayout, modeSelector, quickActions }: UnifiedTopBarProps) {
    const currentPhase = useAtomValue(currentPhaseAtom);
    const phaseProgress = useAtomValue(currentPhaseProgressAtom);
    const [fileMenuAnchor, setFileMenuAnchor] = useState<null | HTMLElement>(null);

    const handleFileMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
        setFileMenuAnchor(event.currentTarget);
    };

    const handleFileMenuClose = () => {
        setFileMenuAnchor(null);
    };

    const handleExportImport = () => {
        handleFileMenuClose();
        onExportImport?.();
    };

    const handleMindMapLayout = () => {
        handleFileMenuClose();
        onMindMapLayout?.();
    };

    // Get phase-specific theme
    const phaseTheme = getPhaseTheme(currentPhase as LifecyclePhase);

    const phaseIcons: Record<string, React.ReactNode> = {
        intent: <Lightbulb className="text-lg" />,
        shape: <AccountTree className="text-lg" />,
        validate: <CheckCircle className="text-lg" />,
        generate: <AutoAwesome className="text-lg" />,
        build: <Build className="text-lg" />,
        run: <PlayArrow className="text-lg" />,
        observe: <Visibility className="text-lg" />,
        improve: <Refresh className="text-lg" />
    };

    const phaseLabels: Record<string, string> = {
        intent: 'Intent',
        shape: 'Shape',
        validate: 'Validate',
        generate: 'Generate',
        build: 'Build',
        run: 'Run',
        observe: 'Observe',
        improve: 'Improve'
    };

    return (
        <Box
            className="flex items-center px-4 gap-4 h-[56px] border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 border-b" >
            {/* Logo */}
            <Typography variant="h6" className="font-bold text-blue-600">
                YAPPC
            </Typography>

            {/* Mode Selector */}
            {modeSelector && (
                <Box className="ml-4">
                    {modeSelector}
                </Box>
            )}

            {/* File Menu */}
            <Button
                size="small"
                onClick={handleFileMenuOpen}
                className="normal-case"
            >
                File
            </Button>
            <Menu
                anchorEl={fileMenuAnchor}
                open={Boolean(fileMenuAnchor)}
                onClose={handleFileMenuClose}
            >
                <MenuItem onClick={handleExportImport}>
                    <ListItemIcon>
                        <DownloadIcon size={16} />
                    </ListItemIcon>
                    <ListItemText>Export / Import</ListItemText>
                </MenuItem>
                <MenuItem onClick={handleMindMapLayout}>
                    <ListItemIcon>
                        <LayoutIcon size={16} />
                    </ListItemIcon>
                    <ListItemText>Mind Map Layout</ListItemText>
                </MenuItem>
            </Menu>

            {/* Project Name */}
            <Typography variant="body1" className="font-medium">
                Project {projectId || 'Untitled'}
            </Typography>

            {/* Phase Indicator */}
            <Box
                className="flex items-center gap-2 px-4 py-1 rounded-2xl border" >
                <Box className="flex items-center" style={{ color: 'phaseTheme.icon', backgroundColor: 'phaseTheme.accent', borderColor: 'phaseTheme.border', color: 'phaseTheme.text', transition: 'all 0.5s ease-in-out' }} >
                    {phaseIcons[currentPhase]}
                </Box>
                <Typography variant="body2" className="font-medium">
                    {phaseLabels[currentPhase]}
                </Typography>
                <Typography variant="body2" className="font-semibold">
                    {phaseProgress?.progress || 0}%
                </Typography>
            </Box>

            {/* Spacer */}
            <Box className="flex-1" />

            {/* Quick Actions */}
            {quickActions && (
                <Box className="mr-4 flex items-center">
                    {quickActions}
                </Box>
            )}

            {/* AI Button */}
            <Button
                variant="contained"
                size="small"
                className="min-w-[80px]"
            >
                AI ⌘K
            </Button>

            {/* User Avatar */}
            <Avatar className="w-[32px] h-[32px] bg-blue-600">
                U
            </Avatar>

            {/* Settings */}
            <IconButton size="small">
                <SettingsIcon size={16} />
            </IconButton>
        </Box>
    );
}
