/**
 * Sketch Toolbar
 * 
 * Bottom toolbar that appears when sketch mode is active.
 * Provides drawing tools, color picker, and stroke width control.
 * 
 * Keyboard Shortcuts:
 * - P: Pen tool
 * - R: Rectangle tool  
 * - E: Ellipse tool
 * - D: Eraser tool
 * - [/]: Decrease/increase stroke width
 * - Escape: Exit sketch mode
 * 
 * @doc.type component
 * @doc.purpose Drawing tool controls for sketch mode
 * @doc.layer product
 * @doc.pattern Contextual Toolbar
 */

import React, { useEffect } from 'react';
import { useAtom } from 'jotai';
import { Surface as Paper, ToggleButtonGroup, ToggleButton, Slider, Box, Tooltip, IconButton, Popper as Popover } from '@ghatana/ui';
import { Pencil as PenIcon, Crop as RectIcon, Circle as CircleIcon, Trash2 as EraserIcon, Palette as ColorIcon } from 'lucide-react';
import {
    sketchToolAtom,
    sketchColorAtom,
    sketchStrokeWidthAtom,
    canvasInteractionModeAtom,
} from '../workspace/canvasAtoms';

export interface SketchToolbarProps {
    className?: string;
}

/**
 * SketchToolbar - Contextual toolbar for sketch mode
 */
export const SketchToolbar: React.FC<SketchToolbarProps> = ({ className = '' }) => {
    const [tool, setTool] = useAtom(sketchToolAtom);
    const [color, setColor] = useAtom(sketchColorAtom);
    const [strokeWidth, setStrokeWidth] = useAtom(sketchStrokeWidthAtom);
    const [interactionMode, setInteractionMode] = useAtom(canvasInteractionModeAtom);
    const [colorPickerAnchor, setColorPickerAnchor] = React.useState<HTMLElement | null>(null);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Only handle shortcuts in sketch mode
            if (interactionMode !== 'sketch') return;

            // Don't trigger if typing in input
            if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
                return;
            }

            switch (e.key.toLowerCase()) {
                case 'p':
                    setTool('pen');
                    e.preventDefault();
                    break;
                case 'r':
                    setTool('rect');
                    e.preventDefault();
                    break;
                case 'e':
                    setTool('ellipse');
                    e.preventDefault();
                    break;
                case 'd':
                    setTool('eraser');
                    e.preventDefault();
                    break;
                case '[':
                    setStrokeWidth(Math.max(1, strokeWidth - 1));
                    e.preventDefault();
                    break;
                case ']':
                    setStrokeWidth(Math.min(20, strokeWidth + 1));
                    e.preventDefault();
                    break;
                case 'escape':
                    setInteractionMode('navigate');
                    e.preventDefault();
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [interactionMode, strokeWidth, setTool, setColor, setStrokeWidth, setInteractionMode]);

    const handleToolChange = (_event: React.MouseEvent<HTMLElement>, newTool: typeof tool | null) => {
        if (newTool !== null) {
            setTool(newTool);
        }
    };

    const handleOpenColorPicker = (event: React.MouseEvent<HTMLElement>) => {
        setColorPickerAnchor(event.currentTarget);
    };

    const handleCloseColorPicker = () => {
        setColorPickerAnchor(null);
    };

    const commonColors = [
        '#000000', // Black
        '#FF0000', // Red
        '#00FF00', // Green
        '#0000FF', // Blue
        '#FFFF00', // Yellow
        '#FF00FF', // Magenta
        '#00FFFF', // Cyan
        '#FFA500', // Orange
        '#800080', // Purple
        '#FFC0CB', // Pink
    ];

    return (
        <Paper
            className={className}
            
            elevation={8}
            data-testid="sketch-toolbar"
        >
            {/* Drawing Tools */}
            <ToggleButtonGroup
                value={tool}
                exclusive
                onChange={handleToolChange}
                aria-label="sketch tool"
                size="sm"
            >
                <ToggleButton value="pen" aria-label="pen tool">
                    <Tooltip title="Pen (Draw freehand)">
                        <PenIcon />
                    </Tooltip>
                </ToggleButton>
                <ToggleButton value="rect" aria-label="rectangle tool">
                    <Tooltip title="Rectangle">
                        <RectIcon />
                    </Tooltip>
                </ToggleButton>
                <ToggleButton value="ellipse" aria-label="ellipse tool">
                    <Tooltip title="Circle/Ellipse">
                        <CircleIcon />
                    </Tooltip>
                </ToggleButton>
                <ToggleButton value="eraser" aria-label="eraser tool">
                    <Tooltip title="Eraser">
                        <EraserIcon />
                    </Tooltip>
                </ToggleButton>
            </ToggleButtonGroup>

            {/* Color Picker */}
            <Box className="flex items-center gap-2">
                <Tooltip title="Choose color">
                    <IconButton
                        onClick={handleOpenColorPicker}
                        size="sm"
                        className="w-[40px] h-[40px] border-[2px] border-solid border-gray-200 dark:border-gray-700 hover:opacity-[0.8]"
                        style={{ backgroundColor: color }}
                        aria-label="color picker"
                    >
                        <ColorIcon style={{ color: getContrastColor(color) }} />
                    </IconButton>
                </Tooltip>

                <Popover
                    open={Boolean(colorPickerAnchor)}
                    anchorEl={colorPickerAnchor}
                    onClose={handleCloseColorPicker}
                    anchorOrigin={{
                        vertical: 'top',
                        horizontal: 'center',
                    }}
                    transformOrigin={{
                        vertical: 'bottom',
                        horizontal: 'center',
                    }}
                >
                    <Box className="grid grid-cols-5 gap-1 p-2">
                        {commonColors.map((presetColor) => (
                            <IconButton
                                key={presetColor}
                                onClick={() => {
                                    setColor(presetColor);
                                    handleCloseColorPicker();
                                }}
                                className="w-[32px] h-[32px] hover:opacity-[0.8]" style={{ backgroundColor: presetColor, border: color === presetColor ? '3px solid' : '1px solid', borderColor: color === presetColor ? 'primary.main' : 'divider' }}
                                aria-label={`color ${presetColor}`}
                            />
                        ))}
                    </Box>
                </Popover>
            </Box>

            {/* Stroke Width Slider */}
            <Box className="flex items-center gap-2 min-w-[150px]">
                <Box
                    className="rounded w-[24px]"
                    style={{ height: strokeWidth }}
                />
                <Slider
                    value={strokeWidth}
                    onChange={(_event, value) => setStrokeWidth(value as number)}
                    min={1}
                    max={20}
                    step={1}
                    valueLabelDisplay="auto"
                    aria-label="stroke width"
                    className="w-[120px]"
                />
            </Box>
        </Paper>
    );
};

/**
 * Helper to get contrasting text color for a background color
 */
function getContrastColor(hexColor: string): string {
    // Convert hex to RGB
    const r = parseInt(hexColor.slice(1, 3), 16);
    const g = parseInt(hexColor.slice(3, 5), 16);
    const b = parseInt(hexColor.slice(5, 7), 16);

    // Calculate luminance
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

    return luminance > 0.5 ? '#000000' : '#FFFFFF';
}
