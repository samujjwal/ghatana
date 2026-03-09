/**
 * useSurfaceCanvas Hook
 * 
 * Manages the surface canvas state and bridges between the unified canvas
 * tool system and the surface layer's tool system.
 * 
 * @doc.type hook
 * @doc.purpose Surface canvas state management
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import type { CanvasElement, ShapeType } from '../components/canvas/unified/surface';
import type { ToolType as SurfaceToolType } from '../components/canvas/unified/tools';
import type { Tool } from '../state/atoms/unifiedCanvasAtom';

// ============================================================================
// Types
// ============================================================================

export interface SurfaceCanvasState {
    /** Elements on the surface layer */
    elements: CanvasElement[];
    /** IDs of selected surface elements */
    selectedIds: string[];
    /** The mapped surface tool type */
    surfaceTool: SurfaceToolType;
    /** Whether the surface layer should handle input */
    surfaceActive: boolean;
}

export interface UseSurfaceCanvasReturn extends SurfaceCanvasState {
    /** Add an element to the surface */
    addElement: (element: CanvasElement) => void;
    /** Update an element */
    updateElement: (id: string, updates: Partial<CanvasElement>) => void;
    /** Remove an element */
    removeElement: (id: string) => void;
    /** Clear all elements */
    clearElements: () => void;
    /** Set selected elements */
    setSelectedIds: (ids: string[]) => void;
    /** Handle elements change from surface */
    handleElementsChange: (elements: CanvasElement[]) => void;
    /** Handle selection change from surface */
    handleSelectionChange: (ids: string[]) => void;
    /** Check if a unified tool maps to a surface tool */
    isSurfaceTool: (tool: Tool) => boolean;
    /** Map unified tool to surface tool */
    mapToSurfaceTool: (tool: Tool) => SurfaceToolType;
}

// ============================================================================
// Tool Mapping
// ============================================================================

/**
 * Maps unified canvas tools to surface layer tools
 */
const TOOL_MAPPING: Record<Tool, SurfaceToolType | null> = {
    select: 'select',
    pan: 'pan',
    draw: 'brush',
    text: 'text',
    code: null, // Handled by ReactFlow
    sticky: null, // Handled by ReactFlow
    rectangle: 'shape',
    ellipse: 'shape',
    line: 'line',
    arrow: 'arrow',
    connector: 'connector',
    frame: 'frame',
    mindmap: null, // Handled by ReactFlow
    embed: null, // Handled by ReactFlow
    image: 'image'
};

/**
 * Tools that should activate the surface layer for input
 */
const SURFACE_INPUT_TOOLS: Set<Tool> = new Set([
    'draw',
    'rectangle',
    'ellipse',
    'line',
    'arrow'
]);

/**
 * Maps unified tools to shape types for the surface
 */
const SHAPE_TYPE_MAPPING: Partial<Record<Tool, ShapeType>> = {
    rectangle: 'rectangle',
    ellipse: 'ellipse'
};

// ============================================================================
// Hook
// ============================================================================

export function useSurfaceCanvas(activeTool: Tool): UseSurfaceCanvasReturn {
    const [elements, setElements] = useState<CanvasElement[]>([]);
    const [selectedIds, setSelectedIds] = useState<string[]>([]);

    // Reference for the current shape type being drawn
    const shapeTypeRef = useRef<ShapeType>('rectangle');

    // Update shape type when tool changes
    useEffect(() => {
        const shapeType = SHAPE_TYPE_MAPPING[activeTool];
        if (shapeType) {
            shapeTypeRef.current = shapeType;
        }
    }, [activeTool]);

    // Map the active tool to surface tool
    const surfaceTool = useMemo((): SurfaceToolType => {
        return TOOL_MAPPING[activeTool] ?? 'select';
    }, [activeTool]);

    // Determine if surface should handle input
    const surfaceActive = useMemo(() => {
        return SURFACE_INPUT_TOOLS.has(activeTool);
    }, [activeTool]);

    // Add element
    const addElement = useCallback((element: CanvasElement) => {
        setElements(prev => [...prev, element]);
    }, []);

    // Update element
    const updateElement = useCallback((id: string, updates: Partial<CanvasElement>) => {
        setElements(prev => prev.map(el =>
            el.id === id ? { ...el, ...updates } as CanvasElement : el
        ));
    }, []);

    // Remove element
    const removeElement = useCallback((id: string) => {
        setElements(prev => prev.filter(el => el.id !== id));
        setSelectedIds(prev => prev.filter(selId => selId !== id));
    }, []);

    // Clear all elements
    const clearElements = useCallback(() => {
        setElements([]);
        setSelectedIds([]);
    }, []);

    // Handle elements change from surface
    const handleElementsChange = useCallback((newElements: CanvasElement[]) => {
        setElements(newElements);
    }, []);

    // Handle selection change from surface
    const handleSelectionChange = useCallback((ids: string[]) => {
        setSelectedIds(ids);
    }, []);

    // Check if a tool uses the surface layer
    const isSurfaceTool = useCallback((tool: Tool): boolean => {
        return TOOL_MAPPING[tool] !== null;
    }, []);

    // Map unified tool to surface tool
    const mapToSurfaceTool = useCallback((tool: Tool): SurfaceToolType => {
        return TOOL_MAPPING[tool] ?? 'select';
    }, []);

    return {
        elements,
        selectedIds,
        surfaceTool,
        surfaceActive,
        addElement,
        updateElement,
        removeElement,
        clearElements,
        setSelectedIds,
        handleElementsChange,
        handleSelectionChange,
        isSurfaceTool,
        mapToSurfaceTool
    };
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Creates a unique element ID
 */
export function generateElementId(prefix = 'el'): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Checks if a tool should use the surface layer for rendering/input
 */
export function shouldUseSurface(tool: Tool): boolean {
    return SURFACE_INPUT_TOOLS.has(tool);
}

/**
 * Gets the shape type for a tool (if applicable)
 */
export function getShapeTypeForTool(tool: Tool): ShapeType | null {
    return SHAPE_TYPE_MAPPING[tool] ?? null;
}
