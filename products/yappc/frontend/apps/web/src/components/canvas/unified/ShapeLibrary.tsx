/**
 * Shape Library Panel - Quick shape insertion with drag-to-insert
 * 
 * Provides a library of pre-configured shapes that can be:
 * 1. DRAGGED onto the canvas (AFFiNE-style) - primary interaction
 * 2. CLICKED to set active tool (toolbar sync)
 * 3. DOUBLE-CLICKED to insert at viewport center (quick insert)
 */

import React, { useState, useCallback, useRef } from 'react';
import {
  Box,
  Typography,
  IconButton,
  Divider,
  Tooltip,
} from '@ghatana/ui';
import type { Tool } from '../../../state/atoms/unifiedCanvasAtom';

export interface ShapeTemplate {
    id: string;
    name: string;
    icon: string;
    type: 'rectangle' | 'ellipse' | 'line' | 'arrow' | 'text' | 'code' | 'sticky' | 'connector' | 'frame' | 'mindmap' | 'embed' | 'image';
    defaultSize: { width: number; height: number };
    defaultData: Record<string, unknown>;
    category: 'basic' | 'flowchart' | 'uml' | 'annotation' | 'advanced';
}

export type ShapeLibraryAction =
    | { type: 'insert'; template: ShapeTemplate }        // Insert at center (double-click)
    | { type: 'setTool'; tool: Tool; template: ShapeTemplate }  // Set active tool (single-click)
    | { type: 'drag'; template: ShapeTemplate; startPosition: { x: number; y: number } };  // Drag start

const SHAPE_TEMPLATES: ShapeTemplate[] = [
    // Basic Shapes
    {
        id: 'rect-default',
        name: 'Rectangle',
        icon: '▭',
        type: 'rectangle',
        defaultSize: { width: 150, height: 100 },
        defaultData: { fill: 'transparent', fillOpacity: 0, color: '#000000', strokeWidth: 2 },
        category: 'basic'
    },
    {
        id: 'rect-filled',
        name: 'Filled Rectangle',
        icon: '◼️',
        type: 'rectangle',
        defaultSize: { width: 150, height: 100 },
        defaultData: { fill: '#1976d2', fillOpacity: 0.3, color: '#1976d2', strokeWidth: 2 },
        category: 'basic'
    },
    {
        id: 'circle-default',
        name: 'Circle',
        icon: '⭕',
        type: 'ellipse',
        defaultSize: { width: 120, height: 120 },
        defaultData: { fill: 'transparent', fillOpacity: 0, color: '#000000', strokeWidth: 2 },
        category: 'basic'
    },
    {
        id: 'circle-filled',
        name: 'Filled Circle',
        icon: '🔵',
        type: 'ellipse',
        defaultSize: { width: 120, height: 120 },
        defaultData: { fill: '#4caf50', fillOpacity: 0.3, color: '#4caf50', strokeWidth: 2 },
        category: 'basic'
    },

    // Flowchart Shapes
    {
        id: 'process',
        name: 'Process',
        icon: '▭',
        type: 'rectangle',
        defaultSize: { width: 180, height: 80 },
        defaultData: { fill: '#bbdefb', fillOpacity: 1, color: '#1976d2', strokeWidth: 2 },
        category: 'flowchart'
    },
    {
        id: 'decision',
        name: 'Decision',
        icon: '◇',
        type: 'rectangle',
        defaultSize: { width: 140, height: 140 },
        defaultData: { fill: '#fff9c4', fillOpacity: 1, color: '#f57f17', strokeWidth: 2 },
        category: 'flowchart'
    },
    {
        id: 'start-end',
        name: 'Start/End',
        icon: '⬭',
        type: 'ellipse',
        defaultSize: { width: 160, height: 80 },
        defaultData: { fill: '#c8e6c9', fillOpacity: 1, color: '#388e3c', strokeWidth: 2 },
        category: 'flowchart'
    },

    // Arrows & Connectors
    {
        id: 'arrow-default',
        name: 'Arrow',
        icon: '➝',
        type: 'arrow',
        defaultSize: { width: 150, height: 2 },
        defaultData: { color: '#000000', strokeWidth: 2 },
        category: 'basic'
    },
    {
        id: 'line-default',
        name: 'Line',
        icon: '─',
        type: 'line',
        defaultSize: { width: 150, height: 2 },
        defaultData: { color: '#000000', strokeWidth: 2 },
        category: 'basic'
    },

    // Annotations
    {
        id: 'sticky-yellow',
        name: 'Sticky Note',
        icon: '📌',
        type: 'sticky',
        defaultSize: { width: 200, height: 200 },
        defaultData: { color: 'yellow', text: '' },
        category: 'annotation'
    },
    {
        id: 'text-default',
        name: 'Text',
        icon: 'Aa',
        type: 'text',
        defaultSize: { width: 300, height: 100 },
        defaultData: { text: 'Text', fontSize: 16 },
        category: 'annotation'
    },
    {
        id: 'code-default',
        name: 'Code Block',
        icon: '</>',
        type: 'code',
        defaultSize: { width: 400, height: 300 },
        defaultData: { code: '', language: 'javascript' },
        category: 'annotation'
    },

    // Advanced - Connectors
    {
        id: 'connector-straight',
        name: 'Straight Connector',
        icon: '─→',
        type: 'connector',
        defaultSize: { width: 150, height: 50 },
        defaultData: { mode: 'straight', color: '#64748b', strokeWidth: 2, arrowEnd: true },
        category: 'advanced'
    },
    {
        id: 'connector-elbow',
        name: 'Elbow Connector',
        icon: '└→',
        type: 'connector',
        defaultSize: { width: 150, height: 50 },
        defaultData: { mode: 'elbow', color: '#64748b', strokeWidth: 2, arrowEnd: true },
        category: 'advanced'
    },
    {
        id: 'connector-curve',
        name: 'Curve Connector',
        icon: '⌒→',
        type: 'connector',
        defaultSize: { width: 150, height: 50 },
        defaultData: { mode: 'curve', color: '#64748b', strokeWidth: 2, arrowEnd: true },
        category: 'advanced'
    },

    // Advanced - Frames
    {
        id: 'frame-default',
        name: 'Frame',
        icon: '🖼️',
        type: 'frame',
        defaultSize: { width: 400, height: 300 },
        defaultData: { title: 'Frame', color: '#6366f1', showTitle: true },
        category: 'advanced'
    },
    {
        id: 'frame-slide',
        name: 'Slide Frame',
        icon: '📽️',
        type: 'frame',
        defaultSize: { width: 960, height: 540 },
        defaultData: { title: 'Slide 1', color: '#0891b2', showTitle: true, presentationIndex: 1 },
        category: 'advanced'
    },

    // Advanced - Mind Map
    {
        id: 'mindmap-root',
        name: 'Mind Map',
        icon: '🧠',
        type: 'mindmap',
        defaultSize: { width: 150, height: 60 },
        defaultData: { text: 'Central Idea', style: 'default', level: 0, isRoot: true },
        category: 'advanced'
    },
    {
        id: 'mindmap-bubble',
        name: 'Bubble Mind Map',
        icon: '💭',
        type: 'mindmap',
        defaultSize: { width: 120, height: 50 },
        defaultData: { text: 'Idea', style: 'bubble', level: 0, isRoot: true },
        category: 'advanced'
    },

    // Advanced - Embed & Media
    {
        id: 'embed-web',
        name: 'Web Embed',
        icon: '🌐',
        type: 'embed',
        defaultSize: { width: 560, height: 315 },
        defaultData: { embedType: 'url', showControls: true, aspectRatio: '16:9' },
        category: 'advanced'
    },
    {
        id: 'embed-youtube',
        name: 'YouTube Video',
        icon: '▶️',
        type: 'embed',
        defaultSize: { width: 560, height: 315 },
        defaultData: { embedType: 'youtube', showControls: true, aspectRatio: '16:9' },
        category: 'advanced'
    },
    {
        id: 'embed-figma',
        name: 'Figma Embed',
        icon: '🎨',
        type: 'embed',
        defaultSize: { width: 800, height: 450 },
        defaultData: { embedType: 'figma', showControls: true, aspectRatio: '16:9' },
        category: 'advanced'
    },
    {
        id: 'image-upload',
        name: 'Image',
        icon: '🖼️',
        type: 'image',
        defaultSize: { width: 300, height: 200 },
        defaultData: { objectFit: 'contain', borderRadius: 8 },
        category: 'advanced'
    },
];

// Export templates for use in unified canvas
export { SHAPE_TEMPLATES };

interface ShapeLibraryProps {
    /** Called when user wants to insert shape immediately (double-click or drag-drop) */
    onShapeInsert?: (template: ShapeTemplate, position?: { x: number; y: number }) => void;
    /** Called when user wants to set active tool (single-click) */
    onToolSelect?: (tool: Tool, template: ShapeTemplate) => void;
    /** Currently active tool (for highlighting) */
    activeTool?: Tool;
    /** Template currently selected for drawing */
    activeTemplate?: ShapeTemplate | null;
    /** Legacy support: if provided, acts as combined handler */
    onShapeSelect?: (template: ShapeTemplate) => void;
}

export function ShapeLibrary({
    onShapeInsert,
    onToolSelect,
    activeTool,
    activeTemplate,
    onShapeSelect // Legacy support
}: ShapeLibraryProps) {
    const [selectedCategory, setSelectedCategory] = useState<'basic' | 'flowchart' | 'uml' | 'annotation' | 'advanced'>('basic');
    const [draggedShape, setDraggedShape] = useState<ShapeTemplate | null>(null);
    const dragTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    const categories = [
        { id: 'basic' as const, name: 'Basic', icon: '▭' },
        { id: 'flowchart' as const, name: 'Flowchart', icon: '◇' },
        { id: 'annotation' as const, name: 'Annotation', icon: '📝' },
        { id: 'advanced' as const, name: 'Advanced', icon: '🚀' },
    ];

    const filteredShapes = SHAPE_TEMPLATES.filter(s => s.category === selectedCategory);

    // Handle single click - set active tool (new behavior)
    const handleClick = useCallback((shape: ShapeTemplate) => {
        if (onToolSelect) {
            // New API: set the tool so user can draw
            onToolSelect(shape.type as Tool, shape);
        } else if (onShapeSelect) {
            // Legacy API: immediate insert - we keep this for backward compat
            // but recommend using onToolSelect for single-click
            onShapeSelect(shape);
        }
    }, [onToolSelect, onShapeSelect]);

    // Handle double click - immediate insert at viewport center
    const handleDoubleClick = useCallback((shape: ShapeTemplate) => {
        if (onShapeInsert) {
            onShapeInsert(shape); // Position will be calculated in parent
        } else if (onShapeSelect) {
            // Legacy: use the old handler
            onShapeSelect(shape);
        }
    }, [onShapeInsert, onShapeSelect]);

    // Handle drag start (for AFFiNE-style drag-to-insert)
    const handleDragStart = useCallback((e: React.DragEvent, shape: ShapeTemplate) => {
        setDraggedShape(shape);
        // Set transfer data for drag-drop
        e.dataTransfer.setData('application/ghatana-shape', JSON.stringify(shape));
        e.dataTransfer.effectAllowed = 'copy';

        // Create a drag preview
        const dragPreview = document.createElement('div');
        dragPreview.innerHTML = shape.icon;
        dragPreview.style.cssText = `
            font-size: 32px;
            padding: 8px;
            background: white;
            border: 2px solid #1976d2;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        `;
        document.body.appendChild(dragPreview);
        e.dataTransfer.setDragImage(dragPreview, 24, 24);

        // Clean up preview element after a short delay
        setTimeout(() => {
            document.body.removeChild(dragPreview);
        }, 0);
    }, []);

    const handleDragEnd = useCallback(() => {
        setDraggedShape(null);
    }, []);

    // Check if a shape is currently selected (for highlighting)
    const isShapeSelected = useCallback((shape: ShapeTemplate) => {
        if (activeTemplate?.id === shape.id) return true;
        if (activeTool === shape.type) return true;
        return false;
    }, [activeTemplate, activeTool]);

    return (
        <Box
            className="flex flex-col h-full overflow-hidden"
        >

            {/* Category Tabs */}
            <Box
                className="flex gap-1 p-2 border-gray-200 dark:border-gray-700 border-b" >
                {categories.map(cat => (
                    <Box
                        key={cat.id}
                        onClick={() => setSelectedCategory(cat.id)}
                        className={`flex-1 px-2 py-1 text-center cursor-pointer rounded-sm text-xs ${selectedCategory === cat.id ? 'bg-blue-600 text-white' : 'bg-transparent text-gray-900 dark:text-white'}`} style={{ fontWeight: selectedCategory === cat.id ? 600 : 400 }}
                    >
                        {cat.icon} {cat.name}
                    </Box>
                ))}
            </Box>

            {/* Shape Grid */}
            <Box
                className="flex-1 p-2 overflow-auto grid gap-2" >
                {filteredShapes.map(shape => {
                    const isSelected = isShapeSelected(shape);
                    const isDragging = draggedShape?.id === shape.id;

                    return (
                        <Tooltip
                            key={shape.id}
                            title={
                                <Box className="text-center">
                                    <Typography variant="caption" fontWeight={600}>{shape.name}</Typography>
                                    <Typography variant="caption" display="block" className="opacity-[0.8] text-[0.65rem]">
                                        Click to draw • Double-click to insert • Drag to place
                                    </Typography>
                                </Box>
                            }
                        >
                            <Box
                                draggable
                                onDragStart={(e) => handleDragStart(e, shape)}
                                onDragEnd={handleDragEnd}
                                onClick={() => handleClick(shape)}
                                onDoubleClick={() => handleDoubleClick(shape)}
                                className={`p-4 border-[2px] rounded flex flex-col items-center gap-1 cursor-grab transition-all duration-150 active:cursor-grabbing ${isSelected ? 'border-blue-600 bg-blue-100' : 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-950'}`} style={{ opacity: isDragging ? 0.5 : 1 }}
                            >
                                <Box className="text-[2rem]" style={{ filter: isSelected ? 'drop-shadow(0 0 2px rgba(25, 118, 210, 0.5))' : 'none' }}>
                                    {shape.icon}
                                </Box>
                                <Typography
                                    variant="caption"
                                    align="center"
                                    className="text-[0.65rem]" style={{ fontWeight: isSelected ? 600 : 400, color: isSelected ? '#1e40af' : '#6b7280' }}
                                >
                                    {shape.name}
                                </Typography>
                            </Box>
                        </Tooltip>
                    );
                })}
            </Box>

            {/* Usage hint at bottom */}
            <Box className="p-3 border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 border-t" >
                <Typography variant="caption" color="text.secondary" className="block text-center text-[0.7rem]">
                    💡 <strong>Click</strong> to draw mode • <strong>Double-click</strong> to insert • <strong>Drag</strong> to place
                </Typography>
            </Box>
        </Box>
    );
}
