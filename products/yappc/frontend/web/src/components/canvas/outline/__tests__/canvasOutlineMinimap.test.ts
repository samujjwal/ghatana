import { describe, expect, it } from 'vitest';

import { createCanvasOutlineMinimapSummary } from '../canvasOutlineMinimap';

describe('createCanvasOutlineMinimapSummary', () => {
  it('projects positioned canvas nodes into bounded outline markers', () => {
    const summary = createCanvasOutlineMinimapSummary(
      [
        {
          id: 'frame-1',
          type: 'frame',
          position: { x: -100, y: 50 },
          size: { width: 400, height: 240 },
          data: { title: 'Hero frame' },
        },
        {
          id: 'cta-1',
          type: 'button',
          position: { x: 200, y: 170 },
          size: { width: 120, height: 44 },
          data: { label: 'Start now', locked: true },
        },
        {
          id: 'hidden-note',
          type: 'sticky',
          position: { x: 0, y: 320 },
          size: { width: 140, height: 100 },
          data: { text: 'Hidden note', hidden: true },
        },
      ],
      ['cta-1'],
      'hidden-note',
    );

    expect(summary).toMatchObject({
      nodeCount: 3,
      selectedCount: 1,
      hiddenCount: 1,
      lockedCount: 1,
    });
    expect(summary.markers).toHaveLength(3);
    expect(summary.markers.find((marker) => marker.id === 'cta-1')).toMatchObject({
      label: 'Start now',
      selected: true,
      locked: true,
    });
    expect(summary.markers.find((marker) => marker.id === 'hidden-note')).toMatchObject({
      hovered: true,
      hidden: true,
    });
  });

  it('returns an empty marker list when nodes have no canvas position', () => {
    const summary = createCanvasOutlineMinimapSummary(
      [{ id: 'node-1', type: 'text', data: { label: 'Draft' } }],
      [],
    );

    expect(summary.markers).toEqual([]);
    expect(summary.nodeCount).toBe(1);
  });
});
