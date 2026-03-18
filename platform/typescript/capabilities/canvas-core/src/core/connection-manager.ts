/**
 * Connection Manager - Edge/Connector Management System
 * 
 * @doc.type class
 * @doc.purpose Manages connections between elements with automatic path routing
 * @doc.layer core
 * @doc.pattern Manager
 * 
 * Provides AFFiNE-style connector management including:
 * - Connection point calculation
 * - Automatic path routing (straight, orthogonal, curved)
 * - Connector source/target binding
 * - Connection validation
 */

import { CanvasElement } from "../elements/base.js";
import { ConnectorElement, type ConnectorProps } from "../elements/connector.js";
import { elementRegistry } from "./element-registry.js";
import type { Point } from "../types/index.js";
import { nanoid } from "nanoid";

/**
 * Connection point on an element
 */
export interface ConnectionPoint {
    /** Point coordinates */
    position: Point;
    /** Connection direction (for orthogonal routing) */
    direction: 'top' | 'right' | 'bottom' | 'left' | 'center';
    /** Element this point belongs to */
    elementId: string;
}

/**
 * Connection definition
 */
export interface Connection {
    /** Connected element ID, or null for free point */
    id?: string;
    /** Explicit position (used when id is null) */
    position?: Point;
}

/**
 * Connector mode (matches AFFiNE's ConnectorMode)
 */
export enum ConnectorMode {
    Straight = 0,
    Orthogonal = 1,
    Curve = 2,
}

/**
 * Connection endpoint locations
 */
export const ConnectionLocations = {
    TopLeft: { x: 0, y: 0 },
    Top: { x: 0.5, y: 0 },
    TopRight: { x: 1, y: 0 },
    Right: { x: 1, y: 0.5 },
    BottomRight: { x: 1, y: 1 },
    Bottom: { x: 0.5, y: 1 },
    BottomLeft: { x: 0, y: 1 },
    Left: { x: 0, y: 0.5 },
    Center: { x: 0.5, y: 0.5 },
} as const;

/**
 * Connection location type
 */
export type ConnectionLocation = keyof typeof ConnectionLocations;

/**
 * Get connection point locations for different shapes
 */
export function getConnectionLocationsForShape(shapeType: string): ConnectionLocation[] {
    switch (shapeType) {
        case 'triangle':
            return ['Top', 'BottomLeft', 'BottomRight', 'Center'];
        case 'diamond':
            return ['Top', 'Right', 'Bottom', 'Left', 'Center'];
        default:
            // Rectangle, ellipse, circle - all 8 edge points
            return ['TopLeft', 'Top', 'TopRight', 'Right', 'BottomRight', 'Bottom', 'BottomLeft', 'Left'];
    }
}

/**
 * Connection Manager
 * 
 * Manages all connections in the canvas, including:
 * - Creating and deleting connectors
 * - Updating connector paths when elements move
 * - Finding connection points on elements
 * - Path routing algorithms
 */
export class ConnectionManager {
    /** Map of element ID to connected connector IDs */
    private elementConnectors = new Map<string, Set<string>>();

    /** Map of connector ID to connector element */
    private connectors = new Map<string, ConnectorElement>();

    /** Callback to add element to canvas */
    private addElementCallback?: (element: CanvasElement) => void;

    /** Callback to remove element from canvas */
    private removeElementCallback?: (element: CanvasElement) => void;

    /** Callback to get element by ID */
    private getElementCallback?: (id: string) => CanvasElement | null;

    /**
     * Set callbacks for canvas integration
     */
    setCallbacks(callbacks: {
        addElement?: (element: CanvasElement) => void;
        removeElement?: (element: CanvasElement) => void;
        getElement?: (id: string) => CanvasElement | null;
    }): void {
        this.addElementCallback = callbacks.addElement;
        this.removeElementCallback = callbacks.removeElement;
        this.getElementCallback = callbacks.getElement;
    }

    /**
     * Create a new connector between two elements or points
     */
    createConnector(options: {
        source: Connection;
        target: Connection;
        mode?: ConnectorMode;
        strokeColor?: string;
        strokeWidth?: number;
        arrowStyle?: 'none' | 'arrow' | 'diamond';
    }): ConnectorElement | null {
        const { source, target, mode = ConnectorMode.Straight, strokeColor = '#1e40af', strokeWidth = 2, arrowStyle = 'arrow' } = options;

        // Resolve source position
        const sourcePoint = this.resolveConnectionPoint(source);
        const targetPoint = this.resolveConnectionPoint(target);

        if (!sourcePoint || !targetPoint) {
            console.warn('[ConnectionManager] Cannot resolve connection points');
            return null;
        }

        // Calculate bounds from points
        const minX = Math.min(sourcePoint.x, targetPoint.x);
        const minY = Math.min(sourcePoint.y, targetPoint.y);
        const maxX = Math.max(sourcePoint.x, targetPoint.x);
        const maxY = Math.max(sourcePoint.y, targetPoint.y);

        const connectorType = mode === ConnectorMode.Straight ? 'straight'
            : mode === ConnectorMode.Orthogonal ? 'orthogonal'
                : 'curved';

        const props: ConnectorProps = {
            id: nanoid(),
            xywh: JSON.stringify([minX, minY, maxX - minX, maxY - minY]),
            index: Date.now().toString(),
            startElementId: source.id,
            endElementId: target.id,
            startPoint: sourcePoint,
            endPoint: targetPoint,
            strokeColor,
            strokeWidth,
            connectorType,
            arrowStyle,
        };

        const connector = new ConnectorElement(props);
        this.connectors.set(connector.id, connector);

        // Track element connections
        if (source.id) {
            this.addConnectorToElement(source.id, connector.id);
        }
        if (target.id) {
            this.addConnectorToElement(target.id, connector.id);
        }

        // Add to canvas
        this.addElementCallback?.(connector);

        return connector;
    }

    /**
     * Delete a connector
     */
    deleteConnector(connectorId: string): void {
        const connector = this.connectors.get(connectorId);
        if (!connector) return;

        // Remove from element tracking
        if (connector.startElementId) {
            this.removeConnectorFromElement(connector.startElementId, connectorId);
        }
        if (connector.endElementId) {
            this.removeConnectorFromElement(connector.endElementId, connectorId);
        }

        // Remove from canvas
        this.removeElementCallback?.(connector);
        this.connectors.delete(connectorId);
    }

    /**
     * Get all connectors for an element
     */
    getConnectors(elementId: string): ConnectorElement[] {
        const connectorIds = this.elementConnectors.get(elementId);
        if (!connectorIds) return [];

        return Array.from(connectorIds)
            .map(id => this.connectors.get(id))
            .filter((c): c is ConnectorElement => c !== undefined);
    }

    /**
     * Update connector when source/target element moves
     */
    updateConnectorsForElement(elementId: string): void {
        const connectors = this.getConnectors(elementId);

        for (const connector of connectors) {
            this.recalculateConnectorPath(connector);
        }
    }

    /**
     * Recalculate connector path
     */
    private recalculateConnectorPath(connector: ConnectorElement): void {
        // Resolve updated positions
        const sourcePoint = connector.startElementId
            ? this.getConnectionPointOnElement(connector.startElementId, connector.endPoint)
            : connector.startPoint;

        const targetPoint = connector.endElementId
            ? this.getConnectionPointOnElement(connector.endElementId, connector.startPoint)
            : connector.endPoint;

        if (sourcePoint && targetPoint) {
            connector.startPoint = sourcePoint;
            connector.endPoint = targetPoint;

            // Update bounds
            const minX = Math.min(sourcePoint.x, targetPoint.x);
            const minY = Math.min(sourcePoint.y, targetPoint.y);
            const maxX = Math.max(sourcePoint.x, targetPoint.x);
            const maxY = Math.max(sourcePoint.y, targetPoint.y);
            connector.xywh = JSON.stringify([minX, minY, maxX - minX, maxY - minY]);
        }
    }

    /**
     * Resolve connection point from Connection definition
     */
    private resolveConnectionPoint(connection: Connection): Point | null {
        if (connection.position) {
            return connection.position;
        }

        if (connection.id) {
            // Get element center as default connection point
            const element = this.getElementCallback?.(connection.id);
            if (element) {
                const bounds = element.getBounds();
                return {
                    x: bounds.x + bounds.w / 2,
                    y: bounds.y + bounds.h / 2,
                };
            }
        }

        return null;
    }

    /**
     * Get nearest connection point on an element to a target point
     */
    getConnectionPointOnElement(elementId: string, targetPoint: Point): Point | null {
        const element = this.getElementCallback?.(elementId);
        if (!element) return null;

        const bounds = element.getBounds();
        const behavior = elementRegistry.getBehavior(element.type);

        // Get connection points based on behavior
        const points = this.getElementConnectionPoints(element, behavior.connectionPoints);

        // Find nearest point
        let nearestPoint = points[0];
        let nearestDistance = Infinity;

        for (const point of points) {
            const distance = Math.sqrt(
                Math.pow(point.x - targetPoint.x, 2) + Math.pow(point.y - targetPoint.y, 2)
            );
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPoint = point;
            }
        }

        return nearestPoint;
    }

    /**
     * Get all connection points for an element
     */
    private getElementConnectionPoints(
        element: CanvasElement,
        connectionType: 'corners' | 'edges' | 'center' | 'custom' | undefined
    ): Point[] {
        const bounds = element.getBounds();
        const points: Point[] = [];

        switch (connectionType) {
            case 'corners':
                points.push(
                    { x: bounds.x, y: bounds.y },
                    { x: bounds.x + bounds.w, y: bounds.y },
                    { x: bounds.x, y: bounds.y + bounds.h },
                    { x: bounds.x + bounds.w, y: bounds.y + bounds.h }
                );
                break;

            case 'center':
                points.push({
                    x: bounds.x + bounds.w / 2,
                    y: bounds.y + bounds.h / 2,
                });
                break;

            case 'edges':
            default:
                // 8 points: corners + edge midpoints
                points.push(
                    // Corners
                    { x: bounds.x, y: bounds.y },
                    { x: bounds.x + bounds.w, y: bounds.y },
                    { x: bounds.x, y: bounds.y + bounds.h },
                    { x: bounds.x + bounds.w, y: bounds.y + bounds.h },
                    // Edge midpoints
                    { x: bounds.x + bounds.w / 2, y: bounds.y },
                    { x: bounds.x + bounds.w / 2, y: bounds.y + bounds.h },
                    { x: bounds.x, y: bounds.y + bounds.h / 2 },
                    { x: bounds.x + bounds.w, y: bounds.y + bounds.h / 2 }
                );
                break;
        }

        return points;
    }

    /**
     * Add connector to element tracking
     */
    private addConnectorToElement(elementId: string, connectorId: string): void {
        if (!this.elementConnectors.has(elementId)) {
            this.elementConnectors.set(elementId, new Set());
        }
        this.elementConnectors.get(elementId)!.add(connectorId);
    }

    /**
     * Remove connector from element tracking
     */
    private removeConnectorFromElement(elementId: string, connectorId: string): void {
        this.elementConnectors.get(elementId)?.delete(connectorId);
    }

    /**
     * Register an existing connector (e.g., loaded from storage)
     */
    registerConnector(connector: ConnectorElement): void {
        this.connectors.set(connector.id, connector);

        if (connector.startElementId) {
            this.addConnectorToElement(connector.startElementId, connector.id);
        }
        if (connector.endElementId) {
            this.addConnectorToElement(connector.endElementId, connector.id);
        }
    }

    /**
     * Clear all connectors
     */
    clear(): void {
        this.connectors.clear();
        this.elementConnectors.clear();
    }

    /**
     * Get all connectors
     */
    getAllConnectors(): ConnectorElement[] {
        return Array.from(this.connectors.values());
    }

    /**
     * Find potential connection target at a point
     */
    findConnectionTarget(
        point: Point,
        excludeIds: string[] = []
    ): { elementId: string; connectionPoint: Point } | null {
        // This would need access to all elements - implemented via callback
        // For now, return null - the canvas renderer will handle this
        return null;
    }
}

// Export singleton instance
export const connectionManager = new ConnectionManager();
