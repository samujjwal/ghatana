/**
 * Canvas outline minimap projection helpers.
 *
 * @doc.type utility
 * @doc.purpose Deterministic canvas outline minimap marker projection
 * @doc.layer components
 */

export interface CanvasOutlinePoint {
  readonly x: number;
  readonly y: number;
}

export interface CanvasOutlineSize {
  readonly width: number;
  readonly height: number;
}

export interface CanvasOutlineNodeInput {
  readonly id: string;
  readonly type: string;
  readonly position?: CanvasOutlinePoint;
  readonly size?: CanvasOutlineSize;
  readonly width?: number;
  readonly height?: number;
  readonly hidden?: boolean;
  readonly data?: {
    readonly label?: unknown;
    readonly title?: unknown;
    readonly text?: unknown;
    readonly hidden?: unknown;
    readonly locked?: unknown;
    readonly width?: unknown;
    readonly height?: unknown;
  };
}

export interface CanvasOutlineMinimapMarker {
  readonly id: string;
  readonly label: string;
  readonly type: string;
  readonly leftPercent: number;
  readonly topPercent: number;
  readonly widthPercent: number;
  readonly heightPercent: number;
  readonly selected: boolean;
  readonly hovered: boolean;
  readonly hidden: boolean;
  readonly locked: boolean;
}

export interface CanvasOutlineMinimapSummary {
  readonly markers: readonly CanvasOutlineMinimapMarker[];
  readonly nodeCount: number;
  readonly selectedCount: number;
  readonly hiddenCount: number;
  readonly lockedCount: number;
}

const DEFAULT_NODE_SIZE: CanvasOutlineSize = {
  width: 160,
  height: 96,
};

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function toPositiveNumber(value: unknown, fallback: number): number {
  return isFiniteNumber(value) && value > 0 ? value : fallback;
}

function toLabel(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : null;
}

function resolveNodeSize(node: CanvasOutlineNodeInput): CanvasOutlineSize {
  return {
    width: toPositiveNumber(node.size?.width ?? node.width ?? node.data?.width, DEFAULT_NODE_SIZE.width),
    height: toPositiveNumber(node.size?.height ?? node.height ?? node.data?.height, DEFAULT_NODE_SIZE.height),
  };
}

function resolveNodeLabel(node: CanvasOutlineNodeInput): string {
  return (
    toLabel(node.data?.label) ??
    toLabel(node.data?.title) ??
    toLabel(node.data?.text)?.slice(0, 40) ??
    `${node.type} ${node.id.slice(0, 8)}`
  );
}

function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, value));
}

export function createCanvasOutlineMinimapSummary(
  nodes: readonly CanvasOutlineNodeInput[],
  selectedNodeIds: readonly string[],
  hoveredNodeId: string | null = null,
): CanvasOutlineMinimapSummary {
  const selectedIds = new Set(selectedNodeIds);
  const positionedNodes = nodes.filter((node) => isFiniteNumber(node.position?.x) && isFiniteNumber(node.position?.y));

  if (positionedNodes.length === 0) {
    return {
      markers: [],
      nodeCount: nodes.length,
      selectedCount: selectedIds.size,
      hiddenCount: nodes.filter((node) => node.hidden === true || node.data?.hidden === true).length,
      lockedCount: nodes.filter((node) => node.data?.locked === true).length,
    };
  }

  const bounds = positionedNodes.reduce(
    (acc, node) => {
      const size = resolveNodeSize(node);
      const x = node.position?.x ?? 0;
      const y = node.position?.y ?? 0;

      return {
        minX: Math.min(acc.minX, x),
        minY: Math.min(acc.minY, y),
        maxX: Math.max(acc.maxX, x + size.width),
        maxY: Math.max(acc.maxY, y + size.height),
      };
    },
    {
      minX: Number.POSITIVE_INFINITY,
      minY: Number.POSITIVE_INFINITY,
      maxX: Number.NEGATIVE_INFINITY,
      maxY: Number.NEGATIVE_INFINITY,
    },
  );

  const canvasWidth = Math.max(bounds.maxX - bounds.minX, DEFAULT_NODE_SIZE.width);
  const canvasHeight = Math.max(bounds.maxY - bounds.minY, DEFAULT_NODE_SIZE.height);

  const markers = positionedNodes.map((node) => {
    const size = resolveNodeSize(node);
    const x = node.position?.x ?? bounds.minX;
    const y = node.position?.y ?? bounds.minY;

    return {
      id: node.id,
      label: resolveNodeLabel(node),
      type: node.type,
      leftPercent: clampPercent(((x - bounds.minX) / canvasWidth) * 100),
      topPercent: clampPercent(((y - bounds.minY) / canvasHeight) * 100),
      widthPercent: clampPercent(Math.max(3, (size.width / canvasWidth) * 100)),
      heightPercent: clampPercent(Math.max(3, (size.height / canvasHeight) * 100)),
      selected: selectedIds.has(node.id),
      hovered: hoveredNodeId === node.id,
      hidden: node.hidden === true || node.data?.hidden === true,
      locked: node.data?.locked === true,
    } satisfies CanvasOutlineMinimapMarker;
  });

  return {
    markers,
    nodeCount: nodes.length,
    selectedCount: selectedIds.size,
    hiddenCount: nodes.filter((node) => node.hidden === true || node.data?.hidden === true).length,
    lockedCount: nodes.filter((node) => node.data?.locked === true).length,
  };
}
