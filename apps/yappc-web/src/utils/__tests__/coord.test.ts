import { describe, test, expect } from 'vitest';

import { screenToCanvas, canvasToScreen, testCoordinateAccuracy } from './coord';

describe('coordinate transformations', () => {
  test('screenToCanvas conversion', () => {
    const viewport = { x: 100, y: 50, zoom: 2 };
    const screenPoint = { x: 200, y: 150 };
    
    const canvasPoint = screenToCanvas(screenPoint, viewport);
    
    expect(canvasPoint.x).toBe(50); // (200 - 100) / 2
    expect(canvasPoint.y).toBe(50); // (150 - 50) / 2
  });

  test('canvasToScreen conversion', () => {
    const viewport = { x: 100, y: 50, zoom: 2 };
    const canvasPoint = { x: 50, y: 50 };
    
    const screenPoint = canvasToScreen(canvasPoint, viewport);
    
    expect(screenPoint.x).toBe(200); // 50 * 2 + 100
    expect(screenPoint.y).toBe(150); // 50 * 2 + 50
  });

  test('round-trip coordinate accuracy', () => {
    const viewport = { x: 123.5, y: 67.3, zoom: 1.5 };
    const originalPoint = { x: 200, y: 300 };
    
    const error = testCoordinateAccuracy(originalPoint, viewport);
    
    // Error should be less than 1px as per Phase 0 DoD requirement
    expect(error).toBeLessThan(1);
  });

  test('round-trip accuracy with various zoom levels', () => {
    const testPoints = [
      { x: 0, y: 0 },
      { x: 100, y: 200 },
      { x: -50, y: 150 },
      { x: 1000, y: -500 },
    ];
    
    const zoomLevels = [0.1, 0.5, 1, 2, 5, 10];
    const viewportOffsets = [
      { x: 0, y: 0 },
      { x: 100, y: -50 },
      { x: -200, y: 300 },
    ];

    for (const point of testPoints) {
      for (const zoom of zoomLevels) {
        for (const offset of viewportOffsets) {
          const viewport = { ...offset, zoom };
          const error = testCoordinateAccuracy(point, viewport);
          
          expect(error).toBeLessThan(1);
        }
      }
    }
  });
});