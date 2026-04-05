// @ts-nocheck
/**
 * Local minimal types to avoid importing project type aliases from the monorepo.
 */
type MinimalCanvasElement = Record<string, unknown>;
/**
 *
 */
type MinimalCanvasConnection = Record<string, unknown>;
/**
 *
 */
type MinimalCanvasState = Record<string, unknown>;

interface CanvasPersistenceLike {
  loadCanvas: () => Promise<MinimalCanvasState | null | undefined>;
  saveCanvas: (state: MinimalCanvasState) => Promise<unknown>;
}

type LegacyRecord = Record<string, any>;

/**
 *
 */
export async function loadCanvasFromPersistence(
  persistence: CanvasPersistenceLike
): Promise<{ loadedState?: MinimalCanvasState; loadedFromLegacy: boolean }> {
  let loadedState = await persistence.loadCanvas();
  let loadedFromLegacy = false;

  if (!loadedState && typeof window !== 'undefined') {
    const legacyRaw = window.localStorage.getItem('canvas-state');
    if (legacyRaw) {
      try {
        const legacy = JSON.parse(legacyRaw) as LegacyRecord;

        const legacyElements: MinimalCanvasElement[] = Array.isArray(legacy.elements)
          ? (legacy.elements as LegacyRecord[]).map((element: LegacyRecord, index: number) => ({
              id: element.id ?? `legacy-element-${index}`,
              kind: element.kind ?? 'node',
              type: element.type ?? 'component',
              position: element.position ?? { x: 0, y: 0 },
              size: element.size,
              data: element.data ?? { label: element.id ?? `Element ${index + 1}` },
              style: element.style,
              selected: element.selected ?? false,
            }))
          : [];

        const legacyShapes: MinimalCanvasElement[] = Array.isArray(legacy.sketches)
          ? (legacy.sketches as LegacyRecord[]).map((shape: LegacyRecord, index: number) => ({
              id: shape.id ?? `legacy-shape-${index}`,
              kind: 'shape',
              type: shape.type ?? 'stroke',
              position: shape.position ?? { x: 0, y: 0 },
              data: shape.data ?? shape,
              style: shape.style,
            }))
          : [];

        const legacyConnections: MinimalCanvasConnection[] = Array.isArray(legacy.connections)
          ? (legacy.connections as LegacyRecord[])
              .filter((connection: LegacyRecord) => connection?.source && connection?.target)
              .map((connection: LegacyRecord, index: number) => ({
                id: connection.id ?? `legacy-connection-${index}`,
                source: connection.source,
                target: connection.target,
                sourceHandle: connection.sourceHandle,
                targetHandle: connection.targetHandle,
                type: connection.type ?? 'default',
                animated: connection.animated ?? false,
                data: connection.data,
                style: connection.style,
              }))
          : [];

        loadedState = {
          elements: [...legacyElements, ...legacyShapes],
          connections: legacyConnections,
          selectedElements: Array.isArray(legacy.selectedElements) ? legacy.selectedElements : [],
          viewportPosition: legacy.viewportPosition ?? legacy.viewport ?? { x: 0, y: 0 },
          viewport: legacy.viewport ?? { x: legacy.viewportPosition?.x ?? 0, y: legacy.viewportPosition?.y ?? 0, zoom: legacy.zoomLevel ?? 1 },
          zoomLevel: legacy.zoomLevel ?? legacy.viewport?.zoom ?? 1,
          metadata: legacy.metadata ?? {},
          draggedElement: legacy.draggedElement,
          isReadOnly: legacy.isReadOnly ?? false,
          layers: legacy.layers ?? [],
          history: legacy.history,
        } as MinimalCanvasState;

        try {
          await persistence.saveCanvas(loadedState);
        } catch (err) {
          // best-effort persist, swallow errors
           
          console.warn('Failed to persist legacy canvas state', err);
        }

        try {
          window.localStorage.removeItem('canvas-state');
        } catch {
          // ignore
        }

        loadedFromLegacy = true;
      } catch (err) {
        // ignore parse errors
         
        console.warn('Failed to parse legacy canvas-state', err);
      }
    }
  }

  return { loadedState, loadedFromLegacy };
}
