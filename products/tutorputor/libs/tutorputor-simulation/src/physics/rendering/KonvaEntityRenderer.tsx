import React, { useCallback, Fragment } from 'react';
import { Rect, Circle, Line } from 'react-konva';
import type { KonvaEventObject } from 'konva/lib/Node';
import { EntityType, type PhysicsEntity } from '../types';

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
export const KonvaEntityRenderer: React.FC<KonvaEntityRendererProps> = ({
    entity,
    isSelected,
    isDraggable,
    onSelect,
    onDragMove,
    selectionColor = '#3b82f6',
}) => {
    const handleDragEnd = useCallback(
        (e: KonvaEventObject<DragEvent>) => {
            onDragMove(entity.id, e.target.x(), e.target.y());
        },
        [entity.id, onDragMove]
    );

    const handleClick = useCallback(() => {
        onSelect(entity.id);
    }, [entity.id, onSelect]);

    // Common props for all shapes
    const commonProps = {
        x: entity.x,
        y: entity.y,
        draggable: isDraggable,
        onClick: handleClick,
        onTap: handleClick,
        onDragEnd: handleDragEnd,
        stroke: isSelected ? selectionColor : undefined,
        strokeWidth: isSelected ? 3 : 0,
        shadowBlur: isSelected ? 10 : (entity.appearance.shadowBlur ?? 0),
        shadowColor: isSelected ? selectionColor : entity.appearance.shadowColor,
        opacity: entity.appearance.opacity ?? 1,
    };

    switch (entity.type) {
        case EntityType.BALL:
            return (
                <Circle
                    {...commonProps}
                    radius={entity.radius ?? 30}
                    fill={entity.appearance.color}
                />
            );

        case EntityType.BOX:
        case EntityType.PLATFORM:
        case EntityType.WALL:
            return (
                <Rect
                    {...commonProps}
                    width={entity.width ?? 60}
                    height={entity.height ?? 60}
                    fill={entity.appearance.color}
                    rotation={entity.rotation ?? 0}
                    cornerRadius={entity.type === EntityType.WALL ? 0 : 2}
                />
            );

        case EntityType.RAMP: {
            const width = entity.width ?? 100;
            const height = entity.height ?? 50;
            return (
                <Line
                    {...commonProps}
                    points={[0, height, width, 0, width, height]}
                    closed
                    fill={entity.appearance.color}
                />
            );
        }

        case EntityType.SPRING: {
            const springWidth = entity.width ?? 60;
            const coils = 8;
            const coilWidth = springWidth / coils;
            const springPoints: number[] = [];
            for (let i = 0; i <= coils; i++) {
                springPoints.push(i * coilWidth, i % 2 === 0 ? 0 : 20);
            }
            return (
                <Line
                    {...commonProps}
                    points={springPoints}
                    stroke={entity.appearance.color}
                    strokeWidth={3}
                    lineCap="round"
                    lineJoin="round"
                    fill={undefined}
                />
            );
        }

        case EntityType.PENDULUM: {
            const ropeLength = entity.width ?? 100;
            const bobRadius = entity.radius ?? 20;
            return (
                <Fragment>
                    <Line
                        x={entity.x}
                        y={entity.y}
                        points={[0, 0, 0, ropeLength]}
                        stroke="#888888"
                        strokeWidth={2}
                    />
                    <Circle
                        {...commonProps}
                        y={entity.y + ropeLength}
                        radius={bobRadius}
                        fill={entity.appearance.color}
                    />
                </Fragment>
            );
        }

        case EntityType.PULLEY: {
            const radius = entity.radius ?? 25;
            return (
                <Fragment>
                    <Circle
                        {...commonProps}
                        radius={radius}
                        fill={entity.appearance.color}
                    />
                    <Circle
                        x={entity.x}
                        y={entity.y}
                        radius={radius * 0.3}
                        fill="#ffffff"
                        stroke="#888888"
                        strokeWidth={1}
                    />
                </Fragment>
            );
        }

        case EntityType.LEVER: {
            const leverWidth = entity.width ?? 120;
            const leverHeight = entity.height ?? 10;
            return (
                <Fragment>
                    <Rect
                        {...commonProps}
                        width={leverWidth}
                        height={leverHeight}
                        fill={entity.appearance.color}
                        offsetX={leverWidth / 2}
                        offsetY={leverHeight / 2}
                        rotation={entity.rotation ?? 0}
                    />
                    <Circle
                        x={entity.x}
                        y={entity.y + leverHeight / 2 + 5}
                        radius={8}
                        fill="#888888"
                    />
                </Fragment>
            );
        }

        case EntityType.WHEEL: {
            const wheelRadius = entity.radius ?? 30;
            return (
                <Fragment>
                    <Circle
                        {...commonProps}
                        radius={wheelRadius}
                        fill={entity.appearance.color}
                    />
                    <Line
                        x={entity.x}
                        y={entity.y}
                        points={[0, -wheelRadius, 0, wheelRadius]}
                        stroke="#ffffff"
                        strokeWidth={2}
                    />
                    <Line
                        x={entity.x}
                        y={entity.y}
                        points={[-wheelRadius, 0, wheelRadius, 0]}
                        stroke="#ffffff"
                        strokeWidth={2}
                    />
                </Fragment>
            );
        }

        default:
            return (
                <Rect
                    {...commonProps}
                    width={entity.width ?? 60}
                    height={entity.height ?? 60}
                    fill={entity.appearance.color}
                />
            );
    }
};

/**
 * @doc.type constant
 * @doc.purpose Display name for debugging
 * @doc.layer core
 * @doc.pattern Component
 */
KonvaEntityRenderer.displayName = 'KonvaEntityRenderer';
