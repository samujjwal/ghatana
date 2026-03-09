import { vi } from 'vitest';

// Minimal, resilient DnD mock used by tests. Keeps behaviour intentionally
// small so jsdom-based integration tests can simulate pointerdown -> pointerup.
// This implementation registers a module-global onDragEnd bridge so tests
// that import/mock modules in varying orders still receive an onDragEnd call.

/**
 *
 */
type OnDragEndShape = (event: unknown) => void;

// Module-global registry / bridge
const __test_dnd_bridge: {
  onDragEnd?: OnDragEndShape | null;
  setOnDragEnd?: (fn: OnDragEndShape | null) => void;
} =
  (globalThis as unknown).__TEST_DND_BRIDGE__ ||
  ((globalThis as unknown).__TEST_DND_BRIDGE__ = {});

/**
 *
 */
export function mockUseDraggableSimple() {
  (vi as unknown).mock('@dnd-kit/core', () => ({
    __esModule: true,
    DndContext: ({ children }: unknown) => children,
    useDraggable: (_opts: unknown) => ({
      attributes: {},
      listeners: {},
      setNodeRef: (_el: unknown) => {},
      transform: undefined,
      isDragging: false,
    }),
  }));
}

/**
 *
 */
export function mockUseDraggableWithPayload() {
  // Note: do NOT rely on a module-scoped activePayload when running under
  // the test runner. Capture the payload per-element inside the pointerdown
  // handler so the one-time pointerup callback closes over the correct
  // value and doesn't reference an undefined identifier.

  (vi as unknown).mock('@dnd-kit/core', () => {
    const React = require('react');

    /**
     *
     */
    function DndContext({ children, onDragEnd }: unknown) {
      // Bridge the onDragEnd handler to a module-global slot so pointerup
      // callbacks attached elsewhere can still trigger the handler.
      // Register synchronously so event handlers that run during the same
      // tick (pointerup attached during pointerdown) can find the handler
      // even before React effects are flushed in some runtimes.
      try {
        __test_dnd_bridge.setOnDragEnd = (fn: OnDragEndShape | null) => {
          __test_dnd_bridge.onDragEnd = fn ?? undefined;
        };
      } catch (_) {}

      if (typeof onDragEnd === 'function') {
        try {
          __test_dnd_bridge.onDragEnd = onDragEnd;
        } catch (_) {}
        try {
          (globalThis as unknown).__TEST_DND_ONDRAGEND__ = onDragEnd;
        } catch (_) {}
      }

      // Keep an effect to perform cleanup when the context unmounts.
      React.useEffect(() => {
        return () => {
          try {
            if (__test_dnd_bridge.onDragEnd === onDragEnd) {
              __test_dnd_bridge.onDragEnd = undefined;
            }
          } catch (_) {}
          try {
            if ((globalThis as unknown).__TEST_DND_ONDRAGEND__ === onDragEnd) {
              (globalThis as unknown).__TEST_DND_ONDRAGEND__ = undefined;
            }
          } catch (_) {}
        };
      }, [onDragEnd]);

      return React.createElement(
        'div',
        { 'data-testid': 'dnd-context' },
        children
      );
    }

    /**
     *
     */
    function useDraggable(opts: unknown) {
      const payload = opts?.data ?? null;

      /**
       *
       */
      function setNodeRef(el: HTMLElement | null) {
        if (!el) return;

        try {
          console.debug(
            '[useDraggableMock] setNodeRef called, payload=',
            payload
          );
        } catch (_) {}

        // Write payload attribute for tests that read it directly
        try {
          el.setAttribute('data-dndkit-payload', JSON.stringify(payload));
        } catch (_) {}

        // Attach pointerdown which will capture the payload in a closure and
        // attach a one-time pointerup (and fallback mouseup) to simulate a
        // drop. Capturing per-element avoids cross-module scoping issues.
        const onPointerDown = (_ev: PointerEvent) => {
          try {
            console.debug(
              '[useDraggableMock] pointerdown on',
              el,
              'payload=',
              payload
            );
          } catch (_) {}
          const activePayloadForThisPointer = payload;

          // Track last seen pointer coordinates for this drag in case the
          // final pointerup event does not include clientX/clientY (some
          // environments fire a synthetic up event without coords). We
          // register a pointermove listener that updates `lastCoords` and
          // remove it once pointerup fires.
          let lastCoords: { x: number; y: number } | null = null;
          const onPointerMove = (moveEv: PointerEvent) => {
            try {
              const mx = (moveEv as unknown).clientX;
              const my = (moveEv as unknown).clientY;
              if (typeof mx === 'number' && typeof my === 'number') {
                lastCoords = { x: mx, y: my };
              }
            } catch (_) {}
          };

          // Attach move listener to capture coordinates while dragging.
          try {
            document.addEventListener('pointermove', onPointerMove as unknown);
          } catch (_) {}

          const callDragEnd = (upEv: Event) => {
            // clean up move listener immediately
            try {
              document.removeEventListener('pointermove', onPointerMove as unknown);
            } catch (_) {}
            try {
              console.debug(
                '[useDraggableMock] document pointerup triggered, activePayload=',
                activePayloadForThisPointer
              );
            } catch (_) {}

            // Determine over target robustly: prefer event.target.closest, then
            // inspect composedPath, then fallback to elementFromPoint when
            // available. This covers jsdom vs. DOM event ordering differences in
            // tests where pointer events bubble to document.
            let over: Element | null = null;
            try {
              const tryEl = (el: unknown) => {
                if (!el || !(el instanceof HTMLElement)) return null;
                return el.closest(
                  '#canvas-drop-zone, [data-testid="canvas-drop-zone"]'
                );
              };

              // 1) Direct target
              const target = (upEv as unknown)?.target as HTMLElement | null;
              let elFound = tryEl(target);

              // 2) composedPath (if present)
              if (
                !elFound &&
                typeof (upEv as unknown)?.composedPath === 'function'
              ) {
                const path = (upEv as unknown).composedPath();
                for (const node of path) {
                  elFound = tryEl(node);
                  if (elFound) break;
                }
              }

              // 3) elementFromPoint fallback (uses pointer coordinates).
              // If the up event lacks clientX/clientY, fall back to the last
              // seen pointermove coordinates for this drag.
              const cxCandidate =
                typeof (upEv as unknown)?.clientX === 'number'
                  ? (upEv as unknown).clientX
                  : lastCoords?.x;
              const cyCandidate =
                typeof (upEv as unknown)?.clientY === 'number'
                  ? (upEv as unknown).clientY
                  : lastCoords?.y;

              if (
                !elFound &&
                typeof cxCandidate === 'number' &&
                typeof cyCandidate === 'number'
              ) {
                try {
                  const elAtPoint = document.elementFromPoint(
                    cxCandidate,
                    cyCandidate
                  ) as HTMLElement | null;
                  elFound = tryEl(elAtPoint);
                } catch (_) {
                  // elementFromPoint may throw in some jsdom environments; ignore
                }
              }

              // If we found an element via any of the strategies, use it.
              if (elFound) {
                over = {
                  id: elFound.id || elFound.getAttribute('data-testid'),
                };
              } else {
                // Last-resort: elementFromPoint might be unavailable or returned
                // null in some jsdom setups. In that case, detect the drop zone
                // by checking whether the pointer coords fall within the
                // bounding rect of an element with id/data-testid
                // 'canvas-drop-zone'. This is conservative and only classifies
                // the drop as inside the canvas if the coordinates actually
                // lie within the zone, avoiding earlier forced fallbacks that
                // broke outside-drop tests.
                try {
                  const cx = (upEv as unknown).clientX;
                  const cy = (upEv as unknown).clientY;
                  if (typeof cx === 'number' && typeof cy === 'number') {
                    const maybe =
                      document.getElementById('canvas-drop-zone') ||
                      document.querySelector(
                        '[data-testid="canvas-drop-zone"]'
                      );
                    if (maybe && maybe instanceof HTMLElement) {
                      const r = maybe.getBoundingClientRect();
                      // If bounding rect is empty, try inline styles as a
                      // fallback similar to simulateDrag.
                      const left = r.left || 0;
                      const top = r.top || 0;
                      let right = r.right || left + (r.width || 0);
                      let bottom = r.bottom || top + (r.height || 0);

                      if (right - left === 0 || bottom - top === 0) {
                        const s = maybe.style || ({} as CSSStyleDeclaration);
                        const parsePx = (v: string) => {
                          if (!v) return 0;
                          const m = v.match(/([0-9.]+)px$/);
                          return m ? parseFloat(m[1]) : parseFloat(v) || 0;
                        };
                        const w = parsePx(s.width || '') || 100;
                        const h = parsePx(s.height || '') || 100;
                        right = left + w;
                        bottom = top + h;
                      }

                      if (
                        cx >= left - 5 &&
                        cx <= right + 5 &&
                        cy >= top - 5 &&
                        cy <= bottom + 5
                      ) {
                        over = {
                          id:
                            (maybe.id && maybe.id) ||
                            maybe.getAttribute('data-testid'),
                        };
                      }
                    }
                  }
                } catch (_) {}
              }
            } catch (_) {}

            // If we couldn't locate a drop target leave `over` as null.
            // Tests should provide deterministic elementFromPoint behavior
            // (see simulateDrag) instead of relying on an automatic fallback
            // which can incorrectly classify drops outside the canvas.

            // Prefer the DndContext-provided onDragEnd if available, else use
            // the module-global bridge. Deduplicate handler invocations so
            // that if the same function is available in both slots we only
            // call it once. Also, if a handler is registered later during
            // the same tick (render/effect ordering), schedule a microtask
            // to invoke any newly-registered handlers that weren't present
            // synchronously.
            try {
              const fn = (__test_dnd_bridge && __test_dnd_bridge.onDragEnd) as
                | OnDragEndShape
                | undefined;
              const alternate = (globalThis as unknown).__TEST_DND_ONDRAGEND__ as
                | OnDragEndShape
                | undefined;
              try {
                console.debug(
                  '[useDraggableMock] invoking bridge onDragEnd with active=',
                  activePayloadForThisPointer,
                  'over=',
                  over
                );
              } catch (_) {}

              const payload = {
                active: {
                  id: activePayloadForThisPointer?.id,
                  data: { current: activePayloadForThisPointer },
                },
                over,
              };

              // Record last call for debugging/observability in tests
              try {
                (globalThis as unknown).__TEST_LAST_DND_CALLS__ =
                  (globalThis as unknown).__TEST_LAST_DND_CALLS__ || [];
                const entry: Record<string, unknown> = {
                  timestamp: Date.now(),
                  payload,
                  fnType: typeof fn,
                  alternateType: typeof alternate,
                };
                try {
                  entry.fnEqual = fn === alternate;
                  entry.fnName =
                    typeof fn === 'function' ? fn.name || null : null;
                  entry.alternateName =
                    typeof alternate === 'function'
                      ? alternate.name || null
                      : null;
                  entry.fnStringLen =
                    typeof fn === 'function' ? fn.toString().length : null;
                  entry.altStringLen =
                    typeof alternate === 'function'
                      ? alternate.toString().length
                      : null;
                } catch (_) {}
                (globalThis as unknown).__TEST_LAST_DND_CALLS__.push(entry);
              } catch (_) {}

              // Build a set of unique handlers (dedupe identical references)
              const calledFns = new Set<OnDragEndShape>();
              const syncFns: OnDragEndShape[] = [];
              if (typeof fn === 'function') syncFns.push(fn);
              if (typeof alternate === 'function') syncFns.push(alternate);

              for (const f of syncFns) {
                if (!calledFns.has(f)) {
                  calledFns.add(f);
                  try {
                    f(payload);
                  } catch (_) {}
                }
              }

              // Schedule microtask to invoke any handlers that were not yet
              // registered at the time of the pointerup (e.g., registered by
              // a React effect during the same tick). Avoid re-calling
              // functions already invoked above.
              try {
                Promise.resolve().then(() => {
                  try {
                    const laterFn = (__test_dnd_bridge &&
                      __test_dnd_bridge.onDragEnd) as
                      | OnDragEndShape
                      | undefined;
                    const laterAlt = (globalThis as unknown)
                      .__TEST_DND_ONDRAGEND__ as OnDragEndShape | undefined;

                    const laterFns: OnDragEndShape[] = [];
                    if (typeof laterFn === 'function') laterFns.push(laterFn);
                    if (typeof laterAlt === 'function') laterFns.push(laterAlt);

                    for (const f of laterFns) {
                      if (!calledFns.has(f)) {
                        calledFns.add(f);
                        try {
                          f(payload);
                        } catch (_) {}
                      }
                    }
                  } catch (_) {}
                });
              } catch (_) {}
            } catch (_) {}
          };

          document.addEventListener('pointerup', callDragEnd, { once: true });
          // fallback for environments that use mouse events
          document.addEventListener('mouseup', callDragEnd, { once: true });
        };

        el.addEventListener('pointerdown', onPointerDown as unknown);
      }

      return {
        attributes: {},
        listeners: {},
        setNodeRef,
        transform: undefined,
        isDragging: false,
      };
    }

    return { __esModule: true, DndContext, useDraggable } as unknown;
  });
}
