/**
 * Basic Canvas Example
 * 
 * Demonstrates fundamental Canvas library usage including:
 * - Component setup and initialization
 * - Basic element management
 * - State management with Canvas atoms
 * - Theme integration
 * - Event handling
 * 
 * This example is intended for development and documentation purposes only.
 * Do not import in production builds.
 * 
 * @example
 * ```tsx
 * // Development-only usage
 * if (process.env.NODE_ENV === 'development') {
 *   const { BasicCanvasExample } = await import('@ghatana/yappc-canvas/examples/basic');
 *   <BasicCanvasExample onElementAdd={handleAdd} />
 * }
 * ```
 */

import { useAtom } from 'jotai';
import React, { useState } from 'react';

import {
    Canvas,
    canvasDocumentAtom,
    addElementAtom,
    updateElementAtom,
    type CanvasNode,
    type CanvasEdge,
    type CanvasTheme
} from '../../src';

/**
 *
 */
export interface BasicCanvasExampleProps {
    /** Called when an element is added */
    onElementAdd?: (element: CanvasNode | CanvasEdge) => void;

    /** Called when an element is updated */
    onElementUpdate?: (elementId: string, updates: unknown) => void;

    /** Optional theme override */
    theme?: Partial<CanvasTheme>;

    /** Width of the canvas */
    width?: number;

    /** Height of the canvas */
    height?: number;
}

/**
 * Basic Canvas Example Component
 * 
 * Provides a minimal working Canvas with example elements
 * and basic interaction capabilities.
 */
export const BasicCanvasExample: React.FC<BasicCanvasExampleProps> = ({
    onElementAdd,
    onElementUpdate,
    theme,
    width = 800,
    height = 600
}) => {
    const [document] = useAtom(canvasDocumentAtom);
    const [, addElement] = useAtom(addElementAtom);
    const [, updateElement] = useAtom(updateElementAtom);
    const [selectedTool, setSelectedTool] = useState<'select' | 'node' | 'edge'>('select');

    // Example: Add a sample node
    const handleAddSampleNode = () => {
        const position = { x: Math.random() * 400 + 200, y: Math.random() * 300 + 150 };
        const sampleNode: CanvasNode = {
            id: `node-${Date.now()}`,
            type: 'node',
            nodeType: 'process',
            data: {
                label: 'Sample Node',
                category: 'automated',
                color: '#3B82F6',
                tags: ['example'],
                description: 'This is a sample node for demonstration'
            },
            inputs: ['input'],
            outputs: ['output'],
            style: {},
            transform: {
                position,
                rotation: 0,
                scale: 1
            },
            bounds: {
                x: position.x,
                y: position.y,
                width: 120,
                height: 80
            },
            zIndex: 1,
            version: '1.0.0',
            metadata: {
                createdBy: 'example-system',
                example: true
            },
            visible: true,
            locked: false,
            selected: false,
            createdAt: new Date(),
            updatedAt: new Date()
        };

        addElement(sampleNode);
        onElementAdd?.(sampleNode);
    };

    // Example: Add a sample edge
    const handleAddSampleEdge = () => {
        // Only add edge if we have at least 2 nodes
        const nodes = Object.values(document.elements).filter(el => el.type === 'node');
        if (nodes.length >= 2) {
            const sampleEdge: CanvasEdge = {
                id: `edge-${Date.now()}`,
                type: 'edge',
                sourceId: nodes[0].id,
                targetId: nodes[1].id,
                sourceHandle: 'output',
                targetHandle: 'input',
                path: [],
                style: {},
                transform: {
                    position: { x: 0, y: 0 },
                    rotation: 0,
                    scale: 1
                },
                bounds: { x: 0, y: 0, width: 0, height: 0 },
                zIndex: 0,
                version: '1.0.0',
                metadata: { example: true },
                visible: true,
                locked: false,
                selected: false,
                createdAt: new Date(),
                updatedAt: new Date()
            };

            addElement(sampleEdge);
            onElementAdd?.(sampleEdge);
        }
    };

    return (
        <div style={{
            width,
            height,
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            overflow: 'hidden',
            background: '#ffffff'
        }}>
            {/* Example Toolbar */}
            <div style={{
                padding: '12px',
                borderBottom: '1px solid #e2e8f0',
                background: '#f8fafc',
                display: 'flex',
                gap: '8px',
                alignItems: 'center'
            }}>
                <button
                    onClick={() => setSelectedTool('select')}
                    style={{
                        padding: '6px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '4px',
                        background: selectedTool === 'select' ? '#3b82f6' : '#ffffff',
                        color: selectedTool === 'select' ? '#ffffff' : '#374151',
                        cursor: 'pointer'
                    }}
                >
                    Select
                </button>
                <button
                    onClick={handleAddSampleNode}
                    style={{
                        padding: '6px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '4px',
                        background: '#ffffff',
                        color: '#374151',
                        cursor: 'pointer'
                    }}
                >
                    Add Node
                </button>
                <button
                    onClick={handleAddSampleEdge}
                    style={{
                        padding: '6px 12px',
                        border: '1px solid #d1d5db',
                        borderRadius: '4px',
                        background: '#ffffff',
                        color: '#374151',
                        cursor: 'pointer'
                    }}
                >
                    Add Edge
                </button>
                <div style={{ marginLeft: 'auto', fontSize: '12px', color: '#6b7280' }}>
                    Elements: {document.elementOrder.length}
                </div>
            </div>

            {/* Canvas Component */}
            <div style={{ height: height - 60 }}>
                <Canvas
                    theme={theme}
                    width={width}
                    height={height - 60}
                    ariaLabel="Basic Canvas Example"
                    testId="basic-canvas-example"
                />
            </div>
        </div>
    );
};

export default BasicCanvasExample;