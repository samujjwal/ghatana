import React from 'react';
import { type PhysicsEntity } from '../types';
/**
 * @doc.type interface
 * @doc.purpose Props for Konva entity renderer
 * @doc.layer core
 * @doc.pattern Component
 */
export interface KonvaEntityRendererProps {
    /** The entity to render */
    entity: PhysicsEntity;
    /** Whether this entity is selected */
    isSelected: boolean;
    /** Whether entities are draggable (false in preview mode) */
    isDraggable: boolean;
    /** Callback when entity is selected */
    onSelect: (id: string) => void;
    /** Callback when entity is dragged */
    onDragMove: (id: string, x: number, y: number) => void;
    /** Selection highlight color */
    selectionColor?: string;
}
/**
 * Renders a physics entity using Konva shapes
 * @doc.type component
 * @doc.purpose Konva shape rendering for physics entities
 * @doc.layer core
 * @doc.pattern Renderer
 */
export declare const KonvaEntityRenderer: React.FC<KonvaEntityRendererProps>;
//# sourceMappingURL=KonvaEntityRenderer.d.ts.map