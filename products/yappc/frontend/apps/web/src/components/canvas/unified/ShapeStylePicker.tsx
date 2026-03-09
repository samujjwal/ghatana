/**
 * Shape Style Picker - Property panel for shape styling
 * 
 * Allows editing fill, stroke, opacity for selected shapes
 */

import React from 'react';
import {
  Box,
  Typography,
  Slider,
  Divider,
  Button,
} from '@ghatana/ui';

interface ShapeStylePickerProps {
    selectedNodeIds: string[];
    nodes: Array<{
        id: string;
        type: string;
        data: Record<string, unknown>;
    }>;
    onUpdateNode: (nodeId: string, updates: Record<string, unknown>) => void;
}

const PRESET_COLORS = [
    { name: 'Black', value: '#000000' },
    { name: 'Blue', value: '#1976d2' },
    { name: 'Green', value: '#4caf50' },
    { name: 'Red', value: '#f44336' },
    { name: 'Orange', value: '#ff9800' },
    { name: 'Purple', value: '#9c27b0' },
    { name: 'Pink', value: '#e91e63' },
    { name: 'Teal', value: '#009688' },
];

const FILL_PRESETS = [
    { name: 'None', value: 'transparent', opacity: 0 },
    { name: 'Light Blue', value: '#bbdefb', opacity: 1 },
    { name: 'Light Green', value: '#c8e6c9', opacity: 1 },
    { name: 'Light Red', value: '#ffcdd2', opacity: 1 },
    { name: 'Light Yellow', value: '#fff9c4', opacity: 1 },
    { name: 'Light Purple', value: '#e1bee7', opacity: 1 },
];

export function ShapeStylePicker({ selectedNodeIds, nodes, onUpdateNode }: ShapeStylePickerProps) {
    if (selectedNodeIds.length === 0) {
        return (
            <Box className="p-4 text-center text-gray-500 dark:text-gray-400">
                <Typography variant="body2">Select a shape to edit its style</Typography>
            </Box>
        );
    }

    const selectedNodes = nodes.filter(n => selectedNodeIds.includes(n.id));
    const shapeNodes = selectedNodes.filter(n => ['rectangle', 'ellipse', 'line', 'arrow'].includes(n.type));

    if (shapeNodes.length === 0) {
        return (
            <Box className="p-4 text-center text-gray-500 dark:text-gray-400">
                <Typography variant="body2">Select a shape to edit its style</Typography>
            </Box>
        );
    }

    // Get current values from first selected node
    const firstNode = shapeNodes[0];
    const currentFill = firstNode.data.fill || 'transparent';
    const currentFillOpacity = firstNode.data.fillOpacity !== undefined ? firstNode.data.fillOpacity : 0;
    const currentColor = firstNode.data.color || '#000000';
    const currentStrokeWidth = firstNode.data.strokeWidth || 2;

    const handleFillChange = (fill: string, opacity: number) => {
        shapeNodes.forEach(node => {
            onUpdateNode(node.id, {
                data: {
                    ...node.data,
                    fill,
                    fillOpacity: opacity
                }
            });
        });
    };

    const handleStrokeColorChange = (color: string) => {
        shapeNodes.forEach(node => {
            onUpdateNode(node.id, {
                data: {
                    ...node.data,
                    color
                }
            });
        });
    };

    const handleStrokeWidthChange = (width: number) => {
        shapeNodes.forEach(node => {
            onUpdateNode(node.id, {
                data: {
                    ...node.data,
                    strokeWidth: width
                }
            });
        });
    };

    return (
        <Box className="p-4">
            <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                Shape Style
            </Typography>

            <Divider className="my-3" />

            {/* Fill Color */}
            {!['line', 'arrow'].includes(firstNode.type) && (
                <>
                    <Typography variant="caption" color="text.secondary" className="mb-2 block">
                        Fill
                    </Typography>
                    <Box
                        className="grid gap-1 mb-4" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
                        {FILL_PRESETS.map(preset => (
                            <Box
                                key={preset.name}
                                onClick={() => handleFillChange(preset.value, preset.opacity)}
                                className="h-[36px] border-[2px] rounded-sm cursor-pointer flex items-center justify-center relative" style={{ backgroundColor: preset.value, opacity: preset.value === 'transparent' ? 1 : preset.opacity, borderColor: currentFill === preset.value ? '#2563eb' : '#e5e7eb' }}
                                title={preset.name}
                            >
                                {preset.value === 'transparent' && (
                                    <Typography variant="caption" className="text-[1.2rem]">∅</Typography>
                                )}
                            </Box>
                        ))}
                    </Box>

                    {/* Fill Opacity */}
                    {currentFill !== 'transparent' && (
                        <Box className="mb-4">
                            <Typography variant="caption" color="text.secondary" gutterBottom>
                                Fill Opacity: {Math.round((currentFillOpacity || 0) * 100)}%
                            </Typography>
                            <Slider
                                value={currentFillOpacity || 0}
                                onChange={(_, value) => handleFillChange(currentFill, value as number)}
                                min={0}
                                max={1}
                                step={0.1}
                                size="small"
                            />
                        </Box>
                    )}

                    <Divider className="my-3" />
                </>
            )}

            {/* Stroke Color */}
            <Typography variant="caption" color="text.secondary" className="mb-2 block">
                Stroke Color
            </Typography>
            <Box
                className="grid gap-1 mb-4" >
                {PRESET_COLORS.map(color => (
                    <Box
                        key={color.name}
                        onClick={() => handleStrokeColorChange(color.value)}
                        className="h-[36px] border-[2px] rounded-sm cursor-pointer hover:shadow-sm" style={{ backgroundColor: color.value, borderColor: currentColor === color.value ? 'white' : '#e5e7eb', boxShadow: currentColor === color.value ? '0 1px 3px rgba(0,0,0,0.12)' : 'none' }}
                        title={color.name}
                    />
                ))}
            </Box>

            <Divider className="my-3" />

            {/* Stroke Width */}
            <Box className="mb-4">
                <Typography variant="caption" color="text.secondary" gutterBottom>
                    Stroke Width: {currentStrokeWidth}px
                </Typography>
                <Slider
                    value={currentStrokeWidth}
                    onChange={(_, value) => handleStrokeWidthChange(value as number)}
                    min={1}
                    max={10}
                    step={1}
                    marks
                    size="small"
                />
            </Box>

            <Divider className="my-3" />

            {/* Quick Actions */}
            <Typography variant="caption" color="text.secondary" className="mb-2 block">
                Quick Actions
            </Typography>
            <Box className="flex flex-col gap-2">
                <Button
                    size="small"
                    variant="outlined"
                    onClick={() => {
                        shapeNodes.forEach(node => {
                            onUpdateNode(node.id, {
                                data: {
                                    ...node.data,
                                    fill: 'transparent',
                                    fillOpacity: 0,
                                    color: '#000000',
                                    strokeWidth: 2
                                }
                            });
                        });
                    }}
                >
                    Reset Style
                </Button>
            </Box>
        </Box>
    );
}
