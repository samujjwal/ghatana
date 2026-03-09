/**
 * Canvas Toolbar Module
 * 
 * Extracts toolbar controls and actions from CanvasScene
 * 
 * @doc.type component
 * @doc.purpose Canvas toolbar and action buttons
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Button,
  IconButton,
  Stack,
  Tooltip,
  Menu,
  Divider,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { Save as SaveIcon, Download as ExportIcon, Upload as ImportIcon, LayoutGrid as TemplateIcon, Grid3x3 as LayoutIcon, BarChart3 as PerformanceIcon, History as VersionHistoryIcon, MessageSquare as CommentIcon, PlusCircle as AddIcon, Play as RunIcon, FlaskConical as TestIcon } from 'lucide-react';

// ============================================================================
// Canvas Action Toolbar
// ============================================================================

export interface CanvasActionToolbarProps {
    onSave?: () => void;
    onExport?: () => void;
    onImport?: () => void;
    onTemplate?: () => void;
    onLayout?: () => void;
    onPerformance?: () => void;
    onVersionHistory?: () => void;
    onComments?: () => void;
    disabled?: boolean;
}

export const CanvasActionToolbar: React.FC<CanvasActionToolbarProps> = ({
    onSave,
    onExport,
    onImport,
    onTemplate,
    onLayout,
    onPerformance,
    onVersionHistory,
    onComments,
    disabled = false,
}) => {
    return (
        <Box
            className="absolute rounded-lg p-1 top-[16px] left-[50%] z-30 shadow" style={{ transform: 'translateX(-50%)', backgroundColor: 'rgba(255' }} >
            <Stack direction="row" spacing={0.5}>
                {onSave && (
                    <Tooltip title="Save Canvas (Cmd+S)">
                        <IconButton
                            size="small"
                            onClick={onSave}
                            disabled={disabled}
                            color="primary"
                        >
                            <SaveIcon />
                        </IconButton>
                    </Tooltip>
                )}

                {onVersionHistory && (
                    <Tooltip title="Version History">
                        <IconButton
                            size="small"
                            onClick={onVersionHistory}
                            disabled={disabled}
                        >
                            <VersionHistoryIcon />
                        </IconButton>
                    </Tooltip>
                )}

                <Divider orientation="vertical" flexItem className="mx-1" />

                {onExport && (
                    <Tooltip title="Export Canvas">
                        <IconButton
                            size="small"
                            onClick={onExport}
                            disabled={disabled}
                        >
                            <ExportIcon />
                        </IconButton>
                    </Tooltip>
                )}

                {onImport && (
                    <Tooltip title="Import Canvas">
                        <IconButton
                            size="small"
                            onClick={onImport}
                            disabled={disabled}
                        >
                            <ImportIcon />
                        </IconButton>
                    </Tooltip>
                )}

                {onTemplate && (
                    <Tooltip title="Templates">
                        <IconButton
                            size="small"
                            onClick={onTemplate}
                            disabled={disabled}
                        >
                            <TemplateIcon />
                        </IconButton>
                    </Tooltip>
                )}

                <Divider orientation="vertical" flexItem className="mx-1" />

                {onLayout && (
                    <Tooltip title="Auto Layout">
                        <IconButton
                            size="small"
                            onClick={onLayout}
                            disabled={disabled}
                        >
                            <LayoutIcon />
                        </IconButton>
                    </Tooltip>
                )}

                {onPerformance && (
                    <Tooltip title="Performance Monitor">
                        <IconButton
                            size="small"
                            onClick={onPerformance}
                            disabled={disabled}
                        >
                            <PerformanceIcon />
                        </IconButton>
                    </Tooltip>
                )}

                {onComments && (
                    <Tooltip title="Comments">
                        <IconButton
                            size="small"
                            onClick={onComments}
                            disabled={disabled}
                        >
                            <CommentIcon />
                        </IconButton>
                    </Tooltip>
                )}
            </Stack>
        </Box>
    );
};

// ============================================================================
// Quick Actions Toolbar (Bottom)
// ============================================================================

export interface QuickActionsToolbarProps {
    onAddNode?: () => void;
    onRun?: () => void;
    onTest?: () => void;
    disabled?: boolean;
}

export const QuickActionsToolbar: React.FC<QuickActionsToolbarProps> = ({
    onAddNode,
    onRun,
    onTest,
    disabled = false,
}) => {
    return (
        <Box
            className="absolute bottom-[16px] left-[50%] z-30" style={{ transform: 'translateX(-50%)' }} >
            <Stack direction="row" spacing={1}>
                {onAddNode && (
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={onAddNode}
                        disabled={disabled}
                    >
                        Add Node
                    </Button>
                )}

                {onRun && (
                    <Button
                        variant="outlined"
                        startIcon={<RunIcon />}
                        onClick={onRun}
                        disabled={disabled}
                        color="success"
                    >
                        Run
                    </Button>
                )}

                {onTest && (
                    <Button
                        variant="outlined"
                        startIcon={<TestIcon />}
                        onClick={onTest}
                        disabled={disabled}
                    >
                        Test
                    </Button>
                )}
            </Stack>
        </Box>
    );
};

// ============================================================================
// Export Menu
// ============================================================================

export interface ExportMenuProps {
    anchorEl: HTMLElement | null;
    open: boolean;
    onClose: () => void;
    onExportJSON: () => void;
    onExportPNG: () => void;
    onExportSVG: () => void;
}

export const ExportMenu: React.FC<ExportMenuProps> = ({
    anchorEl,
    open,
    onClose,
    onExportJSON,
    onExportPNG,
    onExportSVG,
}) => {
    const handleExport = (callback: () => void) => {
        callback();
        onClose();
    };

    return (
        <Menu
            anchorEl={anchorEl}
            open={open}
            onClose={onClose}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'center',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'center',
            }}
        >
            <MenuItem onClick={() => handleExport(onExportJSON)}>
                Export as JSON
            </MenuItem>
            <MenuItem onClick={() => handleExport(onExportPNG)}>
                Export as PNG Image
            </MenuItem>
            <MenuItem onClick={() => handleExport(onExportSVG)}>
                Export as SVG Vector
            </MenuItem>
        </Menu>
    );
};

// ============================================================================
// Template Menu
// ============================================================================

export interface Template {
    id: string;
    name: string;
    description?: string;
    nodeCount: number;
}

export interface TemplateMenuProps {
    anchorEl: HTMLElement | null;
    open: boolean;
    onClose: () => void;
    templates: Template[];
    onSelectTemplate: (templateId: string) => void;
    onSaveAsTemplate: () => void;
}

export const TemplateMenu: React.FC<TemplateMenuProps> = ({
    anchorEl,
    open,
    onClose,
    templates,
    onSelectTemplate,
    onSaveAsTemplate,
}) => {
    const handleSelectTemplate = (templateId: string) => {
        onSelectTemplate(templateId);
        onClose();
    };

    return (
        <Menu
            anchorEl={anchorEl}
            open={open}
            onClose={onClose}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'center',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'center',
            }}
        >
            <MenuItem onClick={onSaveAsTemplate}>
                <strong>Save as Template...</strong>
            </MenuItem>
            <Divider />
            {templates.length === 0 ? (
                <MenuItem disabled>No templates saved</MenuItem>
            ) : (
                templates.map((template) => (
                    <MenuItem
                        key={template.id}
                        onClick={() => handleSelectTemplate(template.id)}
                    >
                        <Stack>
                            <Box>{template.name}</Box>
                            <Box className="text-gray-500 dark:text-gray-400 text-[0.75em]">
                                {template.nodeCount} nodes
                                {template.description && ` • ${template.description}`}
                            </Box>
                        </Stack>
                    </MenuItem>
                ))
            )}
        </Menu>
    );
};

// ============================================================================
// Seed/Sample Data Menu
// ============================================================================

export interface SeedMenuProps {
    anchorEl: HTMLElement | null;
    open: boolean;
    onClose: () => void;
    onSeedSmall: () => void;
    onSeedMedium: () => void;
    onSeedLarge: () => void;
    onSeedCustom?: () => void;
}

export const SeedMenu: React.FC<SeedMenuProps> = ({
    anchorEl,
    open,
    onClose,
    onSeedSmall,
    onSeedMedium,
    onSeedLarge,
    onSeedCustom,
}) => {
    const handleSeed = (callback: () => void) => {
        callback();
        onClose();
    };

    return (
        <Menu
            anchorEl={anchorEl}
            open={open}
            onClose={onClose}
        >
            <MenuItem onClick={() => handleSeed(onSeedSmall)}>
                Small (10 nodes)
            </MenuItem>
            <MenuItem onClick={() => handleSeed(onSeedMedium)}>
                Medium (50 nodes)
            </MenuItem>
            <MenuItem onClick={() => handleSeed(onSeedLarge)}>
                Large (200 nodes)
            </MenuItem>
            {onSeedCustom && (
                <>
                    <Divider />
                    <MenuItem onClick={() => handleSeed(onSeedCustom)}>
                        Custom...
                    </MenuItem>
                </>
            )}
        </Menu>
    );
};
