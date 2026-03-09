/**
 * Konva Rendering Adapter
 *
 * Implements the rendering adapter interface for Konva-based canvas rendering.
 * Optimized for physics simulations and interactive graphics.
 *
 * @doc.type class
 * @doc.purpose Konva rendering adapter implementation
 * @doc.layer core
 * @doc.pattern Adapter
 */

import Konva from 'konva';

import type { PhysicsEntity } from '../types';
import { EntityType } from '../types';
import {
    KONVA_CAPABILITIES,
    type IRenderingAdapter,
    type RenderingBackend,
    type RenderingCapabilities,
    registerRenderingAdapter,
} from './renderingAdapter';

/**
 * Konva rendering adapter for physics entities
 */
export class KonvaRenderingAdapter implements IRenderingAdapter<PhysicsEntity> {
    readonly backend: RenderingBackend = 'konva';
    readonly capabilities: RenderingCapabilities = KONVA_CAPABILITIES;

    private stage: Konva.Stage | null = null;
    private layer: Konva.Layer | null = null;
    private elements: Map<string, Konva.Shape | Konva.Group> = new Map();
    private selectedId: string | null = null;
    private onSelect?: (id: string) => void;
    private onMove?: (id: string, x: number, y: number) => void;

    constructor(options?: { onSelect?: (id: string) => void; onMove?: (id: string, x: number, y: number) => void }) {
        this.onSelect = options?.onSelect;
        this.onMove = options?.onMove;
    }

    async initialize(container: HTMLElement): Promise<void> {
        this.stage = new Konva.Stage({
            container: container as HTMLDivElement,
            width: container.clientWidth,
            height: container.clientHeight,
        });

        this.layer = new Konva.Layer();
        this.stage.add(this.layer);

        // Handle resize
        const resizeObserver = new ResizeObserver(() => {
            if (this.stage) {
                this.stage.width(container.clientWidth);
                this.stage.height(container.clientHeight);
            }
        });
        resizeObserver.observe(container);
    }

    destroy(): void {
        this.stage?.destroy();
        this.stage = null;
        this.layer = null;
        this.elements.clear();
    }

    render(entities: PhysicsEntity[]): void {
        if (!this.layer) return;

        // Track which entities still exist
        const existingIds = new Set(entities.map((e) => e.id));

        // Remove entities that no longer exist
        for (const [id, shape] of this.elements) {
            if (!existingIds.has(id)) {
                shape.destroy();
                this.elements.delete(id);
            }
        }

        // Add or update entities
        for (const entity of entities) {
            let shape = this.elements.get(entity.id);

            if (!shape) {
                const newShape = this.createShape(entity);
                if (newShape) {
                    this.layer.add(newShape);
                    this.elements.set(entity.id, newShape);
                }
            } else {
                this.updateShape(shape, entity);
            }
        }

        this.layer.batchDraw();
    }

    private createShape(entity: PhysicsEntity): Konva.Shape | Konva.Group | null {
        const isSelected = entity.id === this.selectedId;

        const commonConfig = {
            x: entity.x,
            y: entity.y,
            draggable: true,
            stroke: isSelected ? '#3b82f6' : undefined,
            strokeWidth: isSelected ? 3 : 0,
            shadowBlur: isSelected ? 10 : 0,
            shadowColor: isSelected ? '#3b82f6' : undefined,
        };

        let shape: Konva.Shape | Konva.Group | null = null;

        switch (entity.type) {
            case EntityType.BALL:
            case EntityType.WHEEL:
                shape = new Konva.Circle({
                    ...commonConfig,
                    radius: entity.radius ?? 30,
                    fill: entity.appearance.color,
                });
                break;

            case EntityType.BOX:
            case EntityType.PLATFORM:
            case EntityType.WALL:
                shape = new Konva.Rect({
                    ...commonConfig,
                    width: entity.width ?? 60,
                    height: entity.height ?? 60,
                    fill: entity.appearance.color,
                    rotation: entity.rotation ?? 0,
                });
                break;

            case EntityType.RAMP: {
                const width = entity.width ?? 100;
                const height = entity.height ?? 50;
                shape = new Konva.Line({
                    ...commonConfig,
                    points: [0, height, width, 0, width, height],
                    closed: true,
                    fill: entity.appearance.color,
                });
                break;
            }

            case EntityType.SPRING: {
                const springWidth = entity.width ?? 60;
                const coils = 8;
                const coilWidth = springWidth / coils;
                const points: number[] = [];
                for (let i = 0; i <= coils; i++) {
                    points.push(i * coilWidth, i % 2 === 0 ? 0 : 20);
                }
                shape = new Konva.Line({
                    ...commonConfig,
                    points,
                    stroke: entity.appearance.color,
                    strokeWidth: 3,
                    lineCap: 'round',
                    lineJoin: 'round',
                });
                break;
            }

            default:
                shape = new Konva.Rect({
                    ...commonConfig,
                    width: entity.width ?? 60,
                    height: entity.height ?? 60,
                    fill: entity.appearance.color,
                });
        }

        if (shape) {
            shape.setAttr('entityId', entity.id);

            // Event handlers
            shape.on('click tap', () => {
                this.onSelect?.(entity.id);
            });

            shape.on('dragend', () => {
                this.onMove?.(entity.id, shape!.x(), shape!.y());
            });
        }

        return shape;
    }

    private updateShape(shape: Konva.Shape | Konva.Group, entity: PhysicsEntity): void {
        const isSelected = entity.id === this.selectedId;

        shape.setAttrs({
            x: entity.x,
            y: entity.y,
            stroke: isSelected ? '#3b82f6' : undefined,
            strokeWidth: isSelected ? 3 : 0,
            shadowBlur: isSelected ? 10 : 0,
            shadowColor: isSelected ? '#3b82f6' : undefined,
        });

        // Update type-specific properties
        if (shape instanceof Konva.Circle) {
            shape.radius(entity.radius ?? 30);
            shape.fill(entity.appearance.color);
        } else if (shape instanceof Konva.Rect) {
            shape.width(entity.width ?? 60);
            shape.height(entity.height ?? 60);
            shape.fill(entity.appearance.color);
            shape.rotation(entity.rotation ?? 0);
        }
    }

    getState(): PhysicsEntity[] {
        // Convert shapes back to entities - for now return empty
        // Real implementation would track entities
        return [];
    }

    selectElement(id: string): void {
        this.selectedId = id;

        // Update visual selection
        for (const [entityId, shape] of this.elements) {
            const isSelected = entityId === id;
            shape.setAttrs({
                stroke: isSelected ? '#3b82f6' : undefined,
                strokeWidth: isSelected ? 3 : 0,
                shadowBlur: isSelected ? 10 : 0,
                shadowColor: isSelected ? '#3b82f6' : undefined,
            });
        }

        this.layer?.batchDraw();
    }

    clearSelection(): void {
        this.selectedId = null;

        for (const shape of this.elements.values()) {
            shape.setAttrs({
                stroke: undefined,
                strokeWidth: 0,
                shadowBlur: 0,
                shadowColor: undefined,
            });
        }

        this.layer?.batchDraw();
    }

    serialize(): Record<string, unknown> {
        return {
            backend: this.backend,
            selectedId: this.selectedId,
        };
    }

    deserialize(data: Record<string, unknown>): void {
        if (typeof data.selectedId === 'string') {
            this.selectedId = data.selectedId;
        }
    }
}

// Register the Konva adapter
registerRenderingAdapter('konva', (options) => new KonvaRenderingAdapter(options));

export { KonvaRenderingAdapter as default };
