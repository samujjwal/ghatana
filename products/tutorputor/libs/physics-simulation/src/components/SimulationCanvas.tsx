import React, { useRef, useCallback } from "react";
import { Stage, Layer } from "react-konva";
import { useDrop } from "react-dnd";
import type { PhysicsEntity, EntityType } from "../types";
import { KonvaEntityRenderer } from "../rendering";
import type { EntityDropPayload } from "./EntityToolbox";

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
export const SimulationCanvas: React.FC<SimulationCanvasProps> = ({
  entities,
  selectedEntityId,
  isPreviewMode,
  onSelectEntity,
  onEntityMove,
  onEntityDrop,
  dndType = "PHYSICS_ENTITY",
  width,
  height,
  className = "",
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<any>(null);

  // Drop handler for new entities
  const [{ isOver }, drop] = useDrop<
    EntityDropPayload,
    unknown,
    { isOver: boolean }
  >(() => ({
    accept: dndType,
    drop: (item, monitor) => {
      const offset = monitor.getClientOffset();
      if (!offset || !containerRef.current) return;

      const rect = containerRef.current.getBoundingClientRect();
      const x = offset.x - rect.left;
      const y = offset.y - rect.top;

      onEntityDrop(item.entityType, x, y, item.color);
    },
    collect: (monitor) => ({
      isOver: monitor.isOver(),
    }),
  }));

  // Combine refs
  const setRefs = useCallback(
    (node: HTMLDivElement | null) => {
      containerRef.current = node;
      drop(node);
    },
    [drop],
  );

  // Get canvas dimensions
  const canvasWidth = width ?? containerRef.current?.clientWidth ?? 800;
  const canvasHeight = height ?? containerRef.current?.clientHeight ?? 600;

  return (
    <div
      ref={setRefs}
      role="application"
      aria-label="Physics Simulation Canvas"
      tabIndex={0}
      className={`
        relative flex-1 outline-none
        ${isOver && !isPreviewMode ? "bg-blue-50 dark:bg-blue-900/10" : "bg-gray-100 dark:bg-gray-900"}
        ${className}
      `}
    >
      {/* Preview Mode Indicator */}
      {isPreviewMode && (
        <div className="absolute top-4 left-4 z-10 px-4 py-2 bg-green-600 text-white rounded-lg shadow-lg flex items-center gap-2">
          <span className="text-xl">▶</span>
          <div>
            <div className="font-semibold">Preview Mode</div>
            <div className="text-xs opacity-90">
              Entities locked for viewing
            </div>
          </div>
        </div>
      )}

      {/* Empty State */}
      {entities.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="text-center">
            <p className="text-xl text-gray-400 dark:text-gray-500">
              Drag entities here to start building
            </p>
            <p className="text-sm text-gray-400 dark:text-gray-500 mt-2">
              Drop from the toolbox on the left
            </p>
          </div>
        </div>
      )}

      {/* Konva Canvas */}
      <Stage
        ref={stageRef}
        width={canvasWidth}
        height={canvasHeight}
        listening={!isPreviewMode}
      >
        <Layer>
          {entities.map((entity) => (
            <KonvaEntityRenderer
              key={entity.id}
              entity={entity}
              isSelected={!isPreviewMode && entity.id === selectedEntityId}
              isDraggable={!isPreviewMode}
              onSelect={isPreviewMode ? () => {} : onSelectEntity}
              onDragMove={isPreviewMode ? () => {} : onEntityMove}
            />
          ))}
        </Layer>
      </Stage>
    </div>
  );
};

SimulationCanvas.displayName = "SimulationCanvas";
