/**
 * Canvas demo helper utilities used by the canvas demo route and its tests.
 *
 * Re-exports origin-shift helpers from the canvas-legacy viewport module and
 * provides grid-snapping and distribution utilities specific to the demo surface.
 */

export {
  shouldShiftOrigin as shouldShiftViewportOrigin,
  computeOriginShiftDelta,
} from '../lib/canvas-legacy/viewport/infinite-canvas';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface DistributionItem {
  id: string;
  position: { x: number; y: number };
  size: { width: number; height: number };
}

interface GridSnapConfig {
  enabled: boolean;
  spacing: number;
  tolerance: number;
}

// ---------------------------------------------------------------------------
// Grid snapping
// ---------------------------------------------------------------------------

/**
 * Snap a position to the nearest grid intersection if it is within tolerance.
 */
export function snapPositionToGrid(
  position: { x: number; y: number },
  config: GridSnapConfig,
): { x: number; y: number } {
  if (!config.enabled) {
    return position;
  }

  const { spacing, tolerance } = config;

  const snapX = Math.round(position.x / spacing) * spacing;
  const snapY = Math.round(position.y / spacing) * spacing;

  return {
    x: Math.abs(position.x - snapX) <= tolerance ? snapX : position.x,
    y: Math.abs(position.y - snapY) <= tolerance ? snapY : position.y,
  };
}

// ---------------------------------------------------------------------------
// Distribution
// ---------------------------------------------------------------------------

/**
 * Compute target positions for evenly distributing items along an axis.
 *
 * Returns an empty map when fewer than 3 items are supplied (distribution is
 * only meaningful with at least 3 items — the two extremes are anchored and
 * the middle items are redistributed).
 *
 * @param items - Items to distribute
 * @param axis  - `'horizontal'` distributes along X; `'vertical'` along Y
 * @returns A map from item id to its new `{ x, y }` position
 */
export function computeDistributionTargets(
  items: DistributionItem[],
  axis: 'horizontal' | 'vertical',
): Map<string, { x: number; y: number }> {
  const result = new Map<string, { x: number; y: number }>();

  if (items.length < 3) {
    return result;
  }

  if (axis === 'horizontal') {
    const sorted = [...items].sort((a, b) => a.position.x - b.position.x);
    const minX = sorted[0]!.position.x;
    const maxX = sorted[sorted.length - 1]!.position.x;
    const gap = (maxX - minX) / (sorted.length - 1);

    sorted.forEach((item, i) => {
      result.set(item.id, {
        x: minX + i * gap,
        y: item.position.y,
      });
    });
  } else {
    const sorted = [...items].sort((a, b) => a.position.y - b.position.y);
    const minY = sorted[0]!.position.y;
    const maxY = sorted[sorted.length - 1]!.position.y;
    const gap = (maxY - minY) / (sorted.length - 1);

    sorted.forEach((item, i) => {
      result.set(item.id, {
        x: item.position.x,
        y: minY + i * gap,
      });
    });
  }

  return result;
}
