/**
 * Spatial Index - Simple implementation for hit-testing and alignment guides
 * This is a simplified version that doesn't require rbush dependency
 */

/**
 *
 */
export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface IndexedItem {
  id: string;
  bounds: BoundingBox;
  data?: unknown;
}

/**
 *
 */
export interface AlignmentGuide {
  type: 'vertical' | 'horizontal';
  position: number;
  elementIds: string[];
}

/**
 *
 */
export class SpatialIndex {
  private items: Map<string, IndexedItem> = new Map();
  private gridSize = 50; // Grid cell size for spatial partitioning

  /**
   * Add or update an item in the index
   */
  insert(item: IndexedItem): void {
    this.items.set(item.id, item);
  }

  /**
   * Remove an item from the index
   */
  remove(id: string): boolean {
    return this.items.delete(id);
  }

  /**
   * Find items within a bounding box
   */
  search(bounds: BoundingBox): IndexedItem[] {
    const results: IndexedItem[] = [];

    for (const item of this.items.values()) {
      if (this.intersects(item.bounds, bounds)) {
        results.push(item);
      }
    }

    return results;
  }

  /**
   * Find items at a specific point
   */
  searchPoint(x: number, y: number): IndexedItem[] {
    return this.search({ x, y, width: 1, height: 1 });
  }

  /**
   * Find nearest items to a point
   */
  nearest(x: number, y: number, maxDistance: number = 50): IndexedItem[] {
    const results: { item: IndexedItem; distance: number }[] = [];

    for (const item of this.items.values()) {
      const distance = this.distanceToBox(x, y, item.bounds);
      if (distance <= maxDistance) {
        results.push({ item, distance });
      }
    }

    return results
      .sort((a, b) => a.distance - b.distance)
      .map((r) => r.item);
  }

  /**
   * Generate alignment guides for a set of elements
   */
  generateAlignmentGuides(
    targetBounds: BoundingBox,
    threshold: number = 5,
  ): AlignmentGuide[] {
    const guides: AlignmentGuide[] = [];
    const processed = new Set<string>();

    // Check for vertical alignment (left, center, right edges)
    const targetLeft = targetBounds.x;
    const targetCenter = targetBounds.x + targetBounds.width / 2;
    const targetRight = targetBounds.x + targetBounds.width;

    // Check for horizontal alignment (top, center, bottom edges)
    const targetTop = targetBounds.y;
    const targetMiddle = targetBounds.y + targetBounds.height / 2;
    const targetBottom = targetBounds.y + targetBounds.height;

    for (const item of this.items.values()) {
      const bounds = item.bounds;

      // Skip self
      if (this.boundsEqual(bounds, targetBounds)) {
        continue;
      }

      // Vertical guides
      const itemLeft = bounds.x;
      const itemCenter = bounds.x + bounds.width / 2;
      const itemRight = bounds.x + bounds.width;

      if (Math.abs(targetLeft - itemLeft) <= threshold) {
        this.addGuide(guides, processed, 'vertical', itemLeft, [item.id]);
      }
      if (Math.abs(targetCenter - itemCenter) <= threshold) {
        this.addGuide(guides, processed, 'vertical', itemCenter, [item.id]);
      }
      if (Math.abs(targetRight - itemRight) <= threshold) {
        this.addGuide(guides, processed, 'vertical', itemRight, [item.id]);
      }

      // Horizontal guides
      const itemTop = bounds.y;
      const itemMiddle = bounds.y + bounds.height / 2;
      const itemBottom = bounds.y + bounds.height;

      if (Math.abs(targetTop - itemTop) <= threshold) {
        this.addGuide(guides, processed, 'horizontal', itemTop, [item.id]);
      }
      if (Math.abs(targetMiddle - itemMiddle) <= threshold) {
        this.addGuide(guides, processed, 'horizontal', itemMiddle, [item.id]);
      }
      if (Math.abs(targetBottom - itemBottom) <= threshold) {
        this.addGuide(guides, processed, 'horizontal', itemBottom, [item.id]);
      }
    }

    return guides;
  }

  /**
   * Get all items
   */
  getAll(): IndexedItem[] {
    return Array.from(this.items.values());
  }

  /**
   * Clear all items
   */
  clear(): void {
    this.items.clear();
  }

  /**
   * Get statistics
   */
  stats(): { count: number; bounds?: BoundingBox } {
    const count = this.items.size;
    if (count === 0) {
      return { count };
    }

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const item of this.items.values()) {
      const { x, y, width, height } = item.bounds;
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + width);
      maxY = Math.max(maxY, y + height);
    }

    return {
      count,
      bounds: {
        x: minX,
        y: minY,
        width: maxX - minX,
        height: maxY - minY,
      },
    };
  }

  /**
   *
   */
  private intersects(a: BoundingBox, b: BoundingBox): boolean {
    return (
      a.x < b.x + b.width &&
      a.x + a.width > b.x &&
      a.y < b.y + b.height &&
      a.y + a.height > b.y
    );
  }

  /**
   *
   */
  private distanceToBox(x: number, y: number, bounds: BoundingBox): number {
    const dx = Math.max(0, Math.max(bounds.x - x, x - (bounds.x + bounds.width)));
    const dy = Math.max(0, Math.max(bounds.y - y, y - (bounds.y + bounds.height)));
    return Math.sqrt(dx * dx + dy * dy);
  }

  /**
   *
   */
  private boundsEqual(a: BoundingBox, b: BoundingBox): boolean {
    return a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height;
  }

  /**
   *
   */
  private addGuide(
    guides: AlignmentGuide[],
    processed: Set<string>,
    type: 'vertical' | 'horizontal',
    position: number,
    elementIds: string[],
  ): void {
    const key = `${type}-${position}`;
    if (processed.has(key)) {
      return;
    }

    processed.add(key);
    guides.push({ type, position, elementIds });
  }
}

// Global spatial index instance
export const spatialIndex = new SpatialIndex();
