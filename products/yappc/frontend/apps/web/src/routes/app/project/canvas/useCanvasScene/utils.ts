/**
 * Local minimal types to avoid importing project type aliases from the monorepo.
 */
type MinimalCanvasElement = unknown;
/**
 *
 */
type MinimalCanvasConnection = unknown;
/**
 *
 */
type MinimalCanvasState = unknown;

/**
 *
 */
export async function loadCanvasFromPersistence(persistence: unknown): Promise<{ loadedState?: MinimalCanvasState; loadedFromLegacy: boolean }> {
  let loadedState = await persistence.loadCanvas();
  let loadedFromLegacy = false;

  if (!loadedState && typeof window !== 'undefined') {
    const legacyRaw = window.localStorage.getItem('canvas-state');
    if (legacyRaw) {
      try {
        const legacy = JSON.parse(legacyRaw);

        const legacyElements: MinimalCanvasElement[] = Array.isArray(legacy.elements)
          ? (legacy.elements as unknown[]).map((element: unknown, index: number) => ({
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
          ? (legacy.sketches as unknown[]).map((shape: unknown, index: number) => ({
              id: shape.id ?? `legacy-shape-${index}`,
              kind: 'shape',
              type: shape.type ?? 'stroke',
              position: shape.position ?? { x: 0, y: 0 },
              data: shape.data ?? shape,
              style: shape.style,
            }))
          : [];

        const legacyConnections: MinimalCanvasConnection[] = Array.isArray(legacy.connections)
          ? (legacy.connections as unknown[])
              .filter((connection: unknown) => connection?.source && connection?.target)
              .map((connection: unknown, index: number) => ({
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
