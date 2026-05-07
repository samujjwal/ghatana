import { describe, expect, it, vi } from 'vitest';

import { BidirectionalPreviewSync } from '../BidirectionalPreviewSync';

describe('BidirectionalPreviewSync', () => {
  it('maps preview hover ids to canvas hover ids when no explicit mapping is needed', () => {
    const sync = new BidirectionalPreviewSync();
    const telemetry = vi.fn();
    sync.onTelemetry(telemetry);

    sync.handlePreviewHover('node-preview-hover');

    expect(sync.getState()).toMatchObject({
      hoveredCanvasElementId: 'node-preview-hover',
      hoveredPreviewElementId: 'node-preview-hover',
    });
    expect(sync.getEventHistory()[0]).toMatchObject({
      type: 'hover',
      canvasElementId: 'node-preview-hover',
      previewElementId: 'node-preview-hover',
    });
    expect(telemetry).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'preview_hover',
        canvasElementId: 'node-preview-hover',
        previewElementId: 'node-preview-hover',
      }),
    );
  });

  it('preserves explicit preview-to-canvas id mappings when provided', () => {
    const sync = new BidirectionalPreviewSync();

    sync.handlePreviewClick('preview-generated-id', 'canvas-node-id');

    expect(sync.getState()).toMatchObject({
      selectedCanvasElementId: 'canvas-node-id',
      selectedPreviewElementId: 'preview-generated-id',
    });
  });
});
