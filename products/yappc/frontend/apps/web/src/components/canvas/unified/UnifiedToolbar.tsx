/**
 * UnifiedToolbar - Tool Selection Toolbar
 */

import React from 'react';
import {
  Box,
  Button,
  Divider,
  IconButton,
  Typography,
} from '@ghatana/ui';
import { ButtonGroup } from '@ghatana/ui';
import { GitBranch as AccountTree, MoveRight as ArrowRightAlt, ScanLine as CropFree, Square as CropSquare, Pencil as Edit, Image, Link, MousePointer as Mouse, Hand as PanTool, Globe as Public, Type as TextFields, Code, StickyNote as StickyNote2, Circle as CircleOutlined, Minus as HorizontalRule, Undo2 as Undo, Redo2 as Redo, ZoomIn, ZoomOut } from 'lucide-react';
import type { Tool } from '../../../state/atoms/unifiedCanvasAtom';

interface UnifiedToolbarProps {
    activeTool: Tool;
    onToolChange: (tool: Tool) => void;
    onUndo?: () => void;
    onRedo?: () => void;
    canUndo?: boolean;
    canRedo?: boolean;
    zoomLevel?: number;
    onZoomIn?: () => void;
    onZoomOut?: () => void;
    onResetZoom?: () => void;
    // Alignment operations
    onAlignLeft?: () => void;
    onAlignCenter?: () => void;
    onAlignRight?: () => void;
    onAlignTop?: () => void;
    onAlignMiddle?: () => void;
    onAlignBottom?: () => void;
    onDistributeHorizontal?: () => void;
    onDistributeVertical?: () => void;
    hasSelection?: boolean;
    variant?: 'fixed' | 'floating';
    className?: string;
}

export function UnifiedToolbar({
    activeTool,
    onToolChange,
    onUndo,
    onRedo,
    canUndo = false,
    canRedo = false,
    zoomLevel = 50,
    onZoomIn,
    onZoomOut,
    onResetZoom,
    onAlignLeft,
    onAlignCenter,
    onAlignRight,
    onAlignTop,
    onAlignMiddle,
    onAlignBottom,
    onDistributeHorizontal,
    onDistributeVertical,
    hasSelection = false,
    variant = 'fixed',
    className: propClassName
}: UnifiedToolbarProps) {

    const tools: Array<{ id: Tool; label: string; icon: React.ReactNode; shortcut: string; group: 'nav' | 'create' | 'shape' | 'advanced' }> = [
        // Navigation
        { id: 'select', label: 'Select', icon: <Mouse size={16} />, shortcut: 'V', group: 'nav' },
        { id: 'pan', label: 'Pan', icon: <PanTool size={16} />, shortcut: 'H', group: 'nav' },
        // Creation
        { id: 'draw', label: 'Draw', icon: <Edit size={16} />, shortcut: 'P', group: 'create' },
        { id: 'text', label: 'Text', icon: <TextFields size={16} />, shortcut: 'T', group: 'create' },
        { id: 'code', label: 'Code', icon: <Code size={16} />, shortcut: 'C', group: 'create' },
        { id: 'sticky', label: 'Sticky', icon: <StickyNote2 size={16} />, shortcut: 'N', group: 'create' },
        // Shapes
        { id: 'rectangle', label: 'Rectangle', icon: <CropSquare size={16} />, shortcut: 'R', group: 'shape' },
        { id: 'ellipse', label: 'Ellipse', icon: <CircleOutlined size={16} />, shortcut: 'O', group: 'shape' },
        { id: 'line', label: 'Line', icon: <HorizontalRule size={16} />, shortcut: 'L', group: 'shape' },
        { id: 'arrow', label: 'Arrow', icon: <ArrowRightAlt size={16} />, shortcut: 'A', group: 'shape' },
        // Advanced (AFFiNE features)
        { id: 'connector', label: 'Connector', icon: <Link size={16} />, shortcut: 'K', group: 'advanced' },
        { id: 'frame', label: 'Frame', icon: <CropFree size={16} />, shortcut: 'F', group: 'advanced' },
        { id: 'mindmap', label: 'Mind Map', icon: <AccountTree size={16} />, shortcut: 'M', group: 'advanced' },
        { id: 'embed', label: 'Embed', icon: <Public size={16} />, shortcut: 'E', group: 'advanced' },
        { id: 'image', label: 'Image', icon: <Image size={16} />, shortcut: 'I', group: 'advanced' },
    ];

    const navTools = tools.filter(t => t.group === 'nav');
    const createTools = tools.filter(t => t.group === 'create');
    const shapeTools = tools.filter(t => t.group === 'shape');
    const advancedTools = tools.filter(t => t.group === 'advanced');

    const isFloating = variant === 'floating';

    return (
        <Box
            className={`flex h-12 items-center gap-2 overflow-x-auto bg-white px-4 dark:bg-neutral-900 [&::-webkit-scrollbar]:h-1 ${
                isFloating
                    ? 'rounded-full border border-neutral-200 shadow-lg dark:border-neutral-700'
                    : 'w-full border-b border-neutral-200 dark:border-neutral-700'
            } ${propClassName ?? ''}`}
        >
            {/* Navigation Tools */}
            <ButtonGroup size="small" variant="outlined">
                {navTools.map(tool => (
                    <Button
                        key={tool.id}
                        onClick={() => onToolChange(tool.id)}
                        variant={activeTool === tool.id ? 'contained' : 'outlined'}
                        title={`${tool.label} (${tool.shortcut})`}
                    >
                        {tool.icon}
                    </Button>
                ))}
            </ButtonGroup>

            <Divider orientation="vertical" flexItem />

            {/* Creation Tools */}
            <ButtonGroup size="small" variant="outlined">
                {createTools.map(tool => (
                    <Button
                        key={tool.id}
                        onClick={() => onToolChange(tool.id)}
                        variant={activeTool === tool.id ? 'contained' : 'outlined'}
                        title={`${tool.label} (${tool.shortcut})`}
                    >
                        {tool.icon}
                    </Button>
                ))}
            </ButtonGroup>

            {/* Shape Tools */}
            <ButtonGroup size="small" variant="outlined">
                {shapeTools.map(tool => (
                    <Button
                        key={tool.id}
                        onClick={() => onToolChange(tool.id)}
                        variant={activeTool === tool.id ? 'contained' : 'outlined'}
                        title={`${tool.label} (${tool.shortcut})`}
                    >
                        {tool.icon}
                    </Button>
                ))}
            </ButtonGroup>

            {/* Advanced Tools (AFFiNE features) */}
            <ButtonGroup size="small" variant="outlined">
                {advancedTools.map(tool => (
                    <Button
                        key={tool.id}
                        onClick={() => onToolChange(tool.id)}
                        variant={activeTool === tool.id ? 'contained' : 'outlined'}
                        title={`${tool.label} (${tool.shortcut})`}
                    >
                        {tool.icon}
                    </Button>
                ))}
            </ButtonGroup>

            <Divider orientation="vertical" flexItem />

            {/* Alignment Tools (show when nodes selected) */}
            {hasSelection && (
                <>
                    <ButtonGroup size="small" variant="outlined">
                        <Button
                            onClick={onAlignLeft}
                            title="Align Left"
                            size="small"
                        >
                            ⫷
                        </Button>
                        <Button
                            onClick={onAlignCenter}
                            title="Align Center"
                            size="small"
                        >
                            ◫
                        </Button>
                        <Button
                            onClick={onAlignRight}
                            title="Align Right"
                            size="small"
                        >
                            ⫸
                        </Button>
                    </ButtonGroup>

                    <ButtonGroup size="small" variant="outlined">
                        <Button
                            onClick={onAlignTop}
                            title="Align Top"
                            size="small"
                        >
                            ⫶
                        </Button>
                        <Button
                            onClick={onAlignMiddle}
                            title="Align Middle"
                            size="small"
                        >
                            ⬒
                        </Button>
                        <Button
                            onClick={onAlignBottom}
                            title="Align Bottom"
                            size="small"
                        >
                            ⫯
                        </Button>
                    </ButtonGroup>

                    <ButtonGroup size="small" variant="outlined">
                        <Button
                            onClick={onDistributeHorizontal}
                            title="Distribute Horizontally"
                            size="small"
                        >
                            ⬌
                        </Button>
                        <Button
                            onClick={onDistributeVertical}
                            title="Distribute Vertically"
                            size="small"
                        >
                            ⬍
                        </Button>
                    </ButtonGroup>

                    <Divider orientation="vertical" flexItem />
                </>
            )}

            {/* History */}
            <ButtonGroup size="small" variant="outlined">
                <Button
                    onClick={onUndo}
                    disabled={!canUndo}
                    title="Undo (⌘Z)"
                >
                    <Undo size={16} />
                </Button>
                <Button
                    onClick={onRedo}
                    disabled={!canRedo}
                    title="Redo (⌘⇧Z)"
                >
                    <Redo size={16} />
                </Button>
            </ButtonGroup>

            <Divider orientation="vertical" flexItem />

            {/* Zoom Controls */}
            <Box className="flex items-center gap-2">
                <IconButton size="small" onClick={onZoomOut} title="Zoom Out (-)">
                    <ZoomOut size={16} />
                </IconButton>
                <Button
                    size="small"
                    onClick={onResetZoom}
                    className="min-w-[60px]"
                    title="Reset Zoom (⌘1)"
                >
                    {zoomLevel}%
                </Button>
                <IconButton size="small" onClick={onZoomIn} title="Zoom In (+)">
                    <ZoomIn size={16} />
                </IconButton>
            </Box>
        </Box>
    );
}
