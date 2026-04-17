import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AnimationExportService,
  ExportEncodingUnavailableError,
} from '../export-service';
import type { AnimationRenderer, AnimationSpec } from '../service';

class TestRenderer implements AnimationRenderer {
  setProperty(_property: string, _value: unknown): void {}

  getProperty(_property: string): unknown {
    return undefined;
  }

  applyTransform(_transform: Record<string, unknown>): void {}

  render(): void {}
}

const animation: AnimationSpec = {
  animationId: 'animation-1',
  title: 'Export Test',
  description: 'Test animation',
  type: '2d',
  durationSeconds: 1,
  keyframes: [
    {
      timeMs: 0,
      description: 'Start',
      properties: { x: 0 },
    },
  ],
  config: {},
};

describe('AnimationExportService', () => {
  const service = new AnimationExportService();
  const renderer = new TestRenderer();

  beforeEach(() => {
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      value: {
        createElement: (tagName: string) => {
          if (tagName === 'canvas') {
            return {
              width: 0,
              height: 0,
              getContext: () => ({
                fillStyle: '#000000',
                fillRect: vi.fn(),
                getImageData: () => ({
                  width: 10,
                  height: 10,
                  data: new Uint8ClampedArray(400),
                }),
              }),
            };
          }

          return {
            href: '',
            download: '',
            click: vi.fn(),
          };
        },
        body: {
          appendChild: vi.fn(),
          removeChild: vi.fn(),
        },
      },
    });
  });

  it('exports JSON without using a placeholder encoder', async () => {
    const blob = await service.exportToJSON(animation);

    expect(blob.type).toBe('application/json');
    await expect(blob.text()).resolves.toContain('Export Test');
  });

  it('fails video export explicitly when no encoder is configured', async () => {
    await expect(service.exportToVideo(animation, renderer)).rejects.toBeInstanceOf(
      ExportEncodingUnavailableError,
    );
  });

  it('fails GIF export explicitly when no encoder is configured', async () => {
    await expect(service.exportToGIF(animation, renderer)).rejects.toBeInstanceOf(
      ExportEncodingUnavailableError,
    );
  });
});