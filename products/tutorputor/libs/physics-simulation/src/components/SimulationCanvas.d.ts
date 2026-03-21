import React from "react";
import type { PhysicsEntity, EntityType } from "../types";
/**
 * @doc.type interface
 * @doc.purpose Props for simulation canvas
 * @doc.layer core
 * @doc.pattern Component
 */
export interface SimulationCanvasProps {
    /** All entities to render */
    entities: PhysicsEntity[];
    /** Currently selected entity ID */
    selectedEntityId: string | null;
    /** Whether in preview mode (entities not draggable) */
    isPreviewMode: boolean;
    /** Callback when entity is selected */
    onSelectEntity: (id: string) => void;
    /** Callback when entity is moved */
    onEntityMove: (id: string, x: number, y: number) => void;
    /** Callback when new entity is dropped */
    onEntityDrop: (type: EntityType, x: number, y: number, color: string) => void;
    /** DnD item type identifier */
    dndType?: string;
    /** Canvas width (default: container width) */
    width?: number;
    /** Canvas height (default: container height) */
    height?: number;
    /** Optional additional className */
    className?: string;
}
/**
 * Main simulation canvas with Konva rendering
 * @doc.type component
 * @doc.purpose Physics simulation canvas with drag-and-drop
 * @doc.layer core
 * @doc.pattern Component
 */
export declare const SimulationCanvas: React.FC<SimulationCanvasProps>;
//# sourceMappingURL=SimulationCanvas.d.ts.map