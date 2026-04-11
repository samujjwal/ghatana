/**
 * Vitest setup file for canvas package.
 * Mocks the HTML5 Canvas 2D rendering context so jsdom can execute canvas tests.
 */

const mockContext: Partial<CanvasRenderingContext2D> = {
  clearRect: () => undefined,
  fillRect: () => undefined,
  strokeRect: () => undefined,
  beginPath: () => undefined,
  closePath: () => undefined,
  moveTo: () => undefined,
  lineTo: () => undefined,
  arc: () => undefined,
  fill: () => undefined,
  stroke: () => undefined,
  save: () => undefined,
  restore: () => undefined,
  translate: () => undefined,
  scale: () => undefined,
  rotate: () => undefined,
  fillText: () => undefined,
  strokeText: () => undefined,
  measureText: () => ({ width: 10 } as TextMetrics),
  drawImage: () => undefined,
  setLineDash: () => undefined,
  getLineDash: () => [],
  createLinearGradient: () => ({} as CanvasGradient),
  createRadialGradient: () => ({} as CanvasGradient),
  createPattern: () => null,
  setTransform: () => undefined,
  resetTransform: () => undefined,
  clip: () => undefined,
  isPointInPath: () => false,
  quadraticCurveTo: () => undefined,
  bezierCurveTo: () => undefined,
  rect: () => undefined,
  canvas: {} as HTMLCanvasElement,
  fillStyle: "#000000",
  strokeStyle: "#000000",
  lineWidth: 1,
  lineCap: "butt" as CanvasLineCap,
  lineJoin: "miter" as CanvasLineJoin,
  globalAlpha: 1,
  font: "10px sans-serif",
  textAlign: "left" as CanvasTextAlign,
  textBaseline: "alphabetic" as CanvasTextBaseline,
  shadowBlur: 0,
  shadowColor: "transparent",
  shadowOffsetX: 0,
  shadowOffsetY: 0,
};

HTMLCanvasElement.prototype.getContext = function (contextId: string): RenderingContext | null {
  if (contextId === "2d") {
    return mockContext as unknown as CanvasRenderingContext2D;
  }
  return null;
} as typeof HTMLCanvasElement.prototype.getContext;
