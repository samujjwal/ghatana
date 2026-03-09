import { describe, expect, it } from 'vitest';

import {
  computeDistributionTargets,
  snapPositionToGrid,
  shouldShiftViewportOrigin,
  computeOriginShiftDelta,
  type DistributionItem,
} from '../canvas-demo-helpers';

describe('snapPositionToGrid', () => {
  it('snaps positions when within tolerance', () => {
    const result = snapPositionToGrid(
      { x: 102, y: 98 },
      { enabled: true, spacing: 50, tolerance: 12 },
    );
    expect(result).toEqual({ x: 100, y: 100 });
  });

  it('preserves original values when outside tolerance', () => {
    const result = snapPositionToGrid(
      { x: 134, y: 166 },
      { enabled: true, spacing: 50, tolerance: 8 },
    );
    expect(result).toEqual({ x: 134, y: 166 });
  });

  it('returns original position when snapping disabled', () => {
    const initial = { x: 72, y: 84 };
    const result = snapPositionToGrid(initial, { enabled: false, spacing: 40, tolerance: 6 });
    expect(result).toEqual(initial);
  });
});

describe('computeDistributionTargets', () => {
  const sampleItems: DistributionItem[] = [
    { id: 'a', position: { x: 0, y: 0 }, size: { width: 40, height: 40 } },
    { id: 'b', position: { x: 120, y: 120 }, size: { width: 40, height: 40 } },
    { id: 'c', position: { x: 360, y: 300 }, size: { width: 40, height: 40 } },
  ];

  it('distributes items evenly on the horizontal axis', () => {
    const targets = computeDistributionTargets(sampleItems, 'horizontal');
    expect(targets.size).toBe(3);
    expect(targets.get('a')).toEqual({ x: 0, y: 0 });
    expect(targets.get('c')).toEqual({ x: 360, y: 300 });
    expect(targets.get('b')).toEqual({ x: 180, y: 120 });
  });

  it('distributes items evenly on the vertical axis', () => {
    const targets = computeDistributionTargets(sampleItems, 'vertical');
    expect(targets.size).toBe(3);
    expect(targets.get('a')).toEqual({ x: 0, y: 0 });
    expect(targets.get('c')).toEqual({ x: 360, y: 300 });
    const middle = targets.get('b');
    expect(middle).toEqual({ x: 120, y: 150 });
  });

  it('returns an empty map when there are fewer than three items', () => {
    const targets = computeDistributionTargets(sampleItems.slice(0, 2), 'horizontal');
    expect(targets.size).toBe(0);
  });
});

describe('shouldShiftViewportOrigin', () => {
  it('detects when translation exceeds threshold', () => {
    expect(shouldShiftViewportOrigin({ x: 1800, y: 0 }, 1000)).toBe(true);
    expect(shouldShiftViewportOrigin({ x: 0, y: -1200 }, 1000)).toBe(true);
  });

  it('returns false when below threshold or threshold invalid', () => {
    expect(shouldShiftViewportOrigin({ x: 500, y: 400 }, 1200)).toBe(false);
    expect(shouldShiftViewportOrigin({ x: 200, y: 200 }, 0)).toBe(false);
  });
});

describe('computeOriginShiftDelta', () => {
  it('computes world delta from translation and scale', () => {
    expect(computeOriginShiftDelta({ x: 800, y: -400 }, 2)).toEqual({ x: 400, y: -200 });
  });

  it('guards against non-finite scale', () => {
    expect(computeOriginShiftDelta({ x: 500, y: 500 }, 0)).toEqual({ x: 0, y: 0 });
  });
});
