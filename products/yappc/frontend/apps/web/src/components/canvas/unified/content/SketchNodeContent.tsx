/**
 * Sketch Node Content
 * 
 * Renders sketch/drawing content within a canvas node.
 * Provides inline drawing capabilities without mode switching.
 * 
 * @doc.type component
 * @doc.purpose Inline sketch content for canvas nodes
 * @doc.layer product
 * @doc.pattern Content Renderer
 */

import React, { useRef, useEffect, useState, useCallback } from 'react';
import { Box, IconButton, Tooltip } from '@ghatana/ui';
import { Pencil as Edit, XCircle as Clear, Palette } from 'lucide-react';
import { Stage, Layer, Line, Rect, Ellipse } from 'react-konva';
import type { SketchTool } from '@ghatana/yappc-canvas/sketch';

interface SketchNodeContentProps {
    data?: {
        strokes: unknown[];
        canvas?: HTMLCanvasElement;
    };
    onChange?: (newData: unknown) => void;
    readonly?: boolean;
}

const DEFAULT_STROKES = [];

export const SketchNodeContent: React.FC<SketchNodeContentProps> = ({
    data,
    onChange,
    readonly = false
}) => {
    const stageRef = useRef<unknown>(null);
    const [isDrawing, setIsDrawing] = useState(false);
    const [currentTool, setCurrentTool] = useState<SketchTool>('pen');
    const [strokes, setStrokes] = useState(data?.strokes || DEFAULT_STROKES);
    const [currentStroke, setCurrentStroke] = useState<unknown>(null);
    const [color, setColor] = useState('#000000');
    const [strokeWidth, setStrokeWidth] = useState(2);

    // Sync with parent data
    useEffect(() => {
        if (data?.strokes && data.strokes !== strokes) {
            setStrokes(data.strokes);
        }
    }, [data?.strokes, strokes]);

    const notifyChange = useCallback((newStrokes: unknown[]) => {
        const newData = {
            strokes: newStrokes,
        };
        setStrokes(newStrokes);
        onChange?.(newData);
    }, [onChange]);

    const handleMouseDown = useCallback((e: unknown) => {
        if (readonly) return;

        const stage = stageRef.current;
        const point = stage.getPointerPosition();

        setIsDrawing(true);

        if (currentTool === 'pen') {
            const newStroke = {
                tool: currentTool,
                points: [point.x, point.y],
                color,
                strokeWidth,
            };
            setCurrentStroke(newStroke);
        }
    }, [currentTool, color, strokeWidth, readonly]);

    const handleMouseMove = useCallback((e: unknown) => {
        if (!isDrawing || readonly) return;

        const stage = stageRef.current;
        const point = stage.getPointerPosition();

        if (currentTool === 'pen' && currentStroke) {
            const updatedStroke = {
                ...currentStroke,
                points: [...currentStroke.points, point.x, point.y],
            };
            setCurrentStroke(updatedStroke);
        }
    }, [isDrawing, currentTool, currentStroke, readonly]);

    const handleMouseUp = useCallback(() => {
        if (!isDrawing || readonly) return;

        if (currentStroke) {
            const newStrokes = [...strokes, currentStroke];
            notifyChange(newStrokes);
            setCurrentStroke(null);
        }

        setIsDrawing(false);
    }, [isDrawing, currentStroke, strokes, notifyChange, readonly]);

    const handleClear = useCallback(() => {
        if (readonly) return;
        notifyChange([]);
    }, [notifyChange, readonly]);

    const renderStrokes = () => {
        return [...strokes, ...(currentStroke ? [currentStroke] : [])].map((stroke, index) => {
            if (stroke.tool === 'pen') {
                return (
                    <Line
                        key={index}
                        points={stroke.points}
                        stroke={stroke.color}
                        strokeWidth={stroke.strokeWidth}
                        lineCap="round"
                        lineJoin="round"
                        tension={0.5}
                    />
                );
            }
            return null;
        });
    };

    return (
        <Box className="relative w-full h-[200px]">
            {/* Drawing Canvas */}
            <Stage
                ref={stageRef}
                width={280}
                height={200}
                onMouseDown={handleMouseDown}
                onMouseMove={handleMouseMove}
                onMouseUp={handleMouseUp}
                onMouseLeave={handleMouseUp}
                style={{
                    border: readonly ? 'none' : '1px solid #ccc',
                    borderRadius: 4,
                    cursor: readonly ? 'default' : 'crosshair',
                }}
            >
                <Layer>
                    {renderStrokes()}
                </Layer>
            </Stage>

            {/* Toolbar */}
            {!readonly && (
                <Box className="absolute flex gap-1 rounded p-1 top-[8px] right-[8px] bg-white dark:bg-gray-900 shadow-sm">
                    <Tooltip title="Clear">
                        <IconButton size="sm" onClick={handleClear}>
                            <Clear size={16} />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Color">
                        <IconButton
                            size="sm"
                            onClick={() => {
                                // Simple color toggle between black and red
                                setColor(prev => prev === '#000000' ? '#FF0000' : '#000000');
                            }}
                            className="hover:opacity-[0.8]" style={{ backgroundColor: color }}
                        >
                            <Palette size={16} style={{ color: color === '#000000' ? 'white' : 'white' }} />
                        </IconButton>
                    </Tooltip>
                </Box>
            )}

            {/* Read-only indicator */}
            {readonly && (
                <Box className="absolute rounded p-1 top-[8px] right-[8px] bg-white dark:bg-gray-900 shadow-sm opacity-[0.7]">
                    <Edit size={16} color="action" />
                </Box>
            )}
        </Box>
    );
};
