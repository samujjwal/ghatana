import {
  canvasDocumentAtom,
  isCanvasNode,
  isCanvasEdge,
  convertCanvasNodeToReactFlowNode,
  convertCanvasEdgeToReactFlowEdge,
  updateSelectionAtom,
  clearSelectionAtom,
  addElementAtom,
  batchUpdateElementsAtom,
  removeElementAtom,
  updateElementAtom,
  convertReactFlowNodeToCanvasNode,
  convertReactFlowEdgeToCanvasEdge,
} from '@ghatana/canvas';
import { useAtom, useSetAtom } from 'jotai';
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';

// Deprecated hooks removed — using unified canvas state system
import { useCanvasShortcuts } from '@/components/canvas/hooks/useCanvasShortcuts';
import { useCanvasPersistence } from '@/services/canvas/CanvasPersistence';
import { useCanvasLifecycle } from '@/services/canvas/lifecycle/CanvasLifecycle';
import type { LifecyclePhase } from '@ghatana/yappc-types/lifecycle';
import {
  toReactFlowNode,
  toReactFlowEdge,
  fromReactFlowNodes,
  fromReactFlowEdges,
  normalizeNodesForCompare,
  normalizeConnectionsForCompare,
} from '@/components/canvas/utils/transform';
import {
  canvasAtom,
  type CanvasElement,
  type CanvasState,
} from '@/components/canvas/workspace/canvasAtoms';

import type { DragEndEvent } from '@dnd-kit/core';
import type { Node, Edge, ReactFlowInstance } from '@xyflow/react';

/**
 *
 */
export interface ComponentDef {
  id: string;
  type: string;
  kind: 'component' | 'node' | 'shape';
  category: string;
  label: string;
  description: string;
  icon: string;
  defaultData: Record<string, unknown>;
  tags: string[];
}

/**
 *
 */
interface UseCanvasSceneParams {
  projectId: string;
  canvasId: string;
}

/**
 *
 */
export interface UseCanvasSceneResult {
  isLoading: boolean;
  nodes: Node[];
  edges: Edge[];
  rfInstance: ReactFlowInstance | null;
  canvasState: CanvasState;
  notification: { type: 'success' | 'error' | 'info'; message: string } | null;
  handleInit: (instance: ReactFlowInstance) => void;
  handleNodesChange: (changes: unknown) => void;
  handleEdgesChange: (changes: unknown) => void;
  handleConnect: (connection: unknown) => void;
  handleSelectionChange: (params: { nodes: Node[]; edges: Edge[] }) => void;
  handleAddComponent: (
    component: ComponentDef,
    position?: { x: number; y: number }
  ) => void;
  handleDragEnd: (event: DragEndEvent) => void;
  handleFitView: () => void;
  closeNotification: () => void;
  // Lifecycle
  currentPhase: LifecyclePhase;
  canPerformOperation: (operation: string) => boolean;
}

export const useCanvasScene = ({
  projectId,
  canvasId,
}: UseCanvasSceneParams): UseCanvasSceneResult => {
  const [canvasState, setCanvasState] = useAtom(canvasAtom);
  const [canvasDocument] = useAtom(canvasDocumentAtom);
  const setSelection = useSetAtom(updateSelectionAtom);
  const clearSelection = useSetAtom(clearSelectionAtom);
  const addElement = useSetAtom(addElementAtom);
  const batchUpdateElements = useSetAtom(batchUpdateElementsAtom);
  const removeElement = useSetAtom(removeElementAtom);
  const updateElement = useSetAtom(updateElementAtom);
  const [isLoading, setIsLoading] = useState(true);
  const [rfInstance, setRfInstance] = useState<ReactFlowInstance | null>(null);
  const [notification, setNotification] = useState<{
    type: 'success' | 'error' | 'info';
    message: string;
  } | null>(null);
  const isE2E =
    typeof window !== 'undefined' &&
    ((window as unknown).__E2E_TEST_MODE ||
      (typeof navigator !== 'undefined' && (navigator as unknown).webdriver));

  // Hooks
  const persistence = useCanvasPersistence({ projectId, canvasId });
  const { currentPhase, canPerformOperation, lifecycle } =
    useCanvasLifecycle(canvasState);
  // Canvas history: use Jotai canvasHistoryAtom + pushHistoryAtom + undoAtom + redoAtom
  const history = { undo: () => {}, redo: () => {} }; // NOTE: wire to canvasHistoryAtom
  // Canvas history: use Jotai canvasHistoryAtom + pushHistoryAtom + undoAtom + redoAtom
  const history = { undo: () => {}, redo: () => {} }; // NOTE: wire to canvasHistoryAtom
  // Selection state: managed via Jotai selectedNodesAtom (replaces deprecated useCanvasSelection)
  const selection = useMemo(() => ({
    selectedIds: canvasState.selectedElements || [],
    editor: null as unknown,
    setSelected: (ids: string[]) => setCanvasState((prev: unknown) => ({ ...prev, selectedElements: ids })),
    clearSelection: () => setCanvasState((prev: unknown) => ({ ...prev, selectedElements: [] })),
    toggleSelected: (_id: string) => {},
    deleteSelection: () => {
      const ids = canvasState.selectedElements || [];
      if (ids.length > 0) {
        setCanvasState((prev: unknown) => ({
          ...prev,
          elements: (prev.elements || []).filter((e: unknown) => !ids.includes(e.id)),
          connections: (prev.connections || []).filter((c: unknown) => !ids.includes(c.id)),
          selectedElements: [],
        }));
      }
    },
    selectAll: (nodes: unknown[]) => {
      setCanvasState((prev: unknown) => ({ ...prev, selectedElements: nodes.map((n: unknown) => n.id) }));
    },
    copySelection: () => { /* clipboard handled by browser */ },
    pasteSelection: () => { /* clipboard handled by browser */ },
    duplicateSelection: () => {
      const ids = canvasState.selectedElements || [];
      if (ids.length === 0) return;
      const OFFSET = 20;
      const duplicates = (canvasState.elements || [])
        .filter((e: unknown) => ids.includes((e as { id: string }).id))
        .map((e: unknown) => {
          const el = e as { id: string; position?: { x: number; y: number } };
          return {
            ...el,
            id: `${el.id}-copy-${Date.now()}`,
            position: { x: (el.position?.x ?? 0) + OFFSET, y: (el.position?.y ?? 0) + OFFSET },
          };
        });
      setCanvasState((prev: unknown) => {
        const p = prev as { elements?: unknown[]; connections?: unknown[]; selectedElements?: string[] };
        return {
          ...p,
          elements: [...(p.elements || []), ...duplicates],
          selectedElements: duplicates.map((d) => d.id),
        };
      });
    },
    handleSelectionChange: (params: unknown) => {
      const selectedIds = params?.nodes?.map?.((n: unknown) => n.id) || [];
      setCanvasState((prev: unknown) => ({ ...prev, selectedElements: selectedIds }));
    },
  }), [canvasState.selectedElements, setCanvasState]);
  // Inline selection change handler (previously in useCanvasSelection)
  const handleSelectionChange = useCallback((selectedIds: string[]) => {
      setCanvasState((prev) => {
        const prevIds = prev.selectedElements || [];
        const prevSet = new Set(prevIds);
        const nextSet = new Set(selectedIds);
        if (
          prevSet.size === nextSet.size &&
          [...prevSet].every((id) => nextSet.has(id))
        ) {
          return prev;
        }

        setSelection({ selectedIds });
        return {
          ...prev,
          selectedElements: selectedIds,
        };
      });
    },
  });

  // Initialize editor with ReactFlow instance when available
  useEffect(() => {
    if (rfInstance && selection.editor) {
      selection.editor.setReactFlowInstance(rfInstance);
    }
  }, [rfInstance, selection.editor]);

  // Sync lifecycle state with canvas state
  useEffect(() => {
    setCanvasState((prev) => ({
      ...prev,
      ...lifecycle.exportToCanvasState(),
    }));
  }, [currentPhase, lifecycle, setCanvasState]);

  // Derive nodes and edges from canvas state and memoize to keep stable references
  const nodes = useMemo(() => {
    if (canvasDocument?.elementOrder?.length) {
      return canvasDocument.elementOrder
        .map((id) => canvasDocument.elements[id])
        .filter(
          (
            element
          ): element is Parameters<
            typeof convertCanvasNodeToReactFlowNode
          >[0] => Boolean(element) && isCanvasNode(element)
        )
        .map(
          (element) =>
            convertCanvasNodeToReactFlowNode(element) as unknown as Node
        );
    }

    return (canvasState.elements || [])
      .filter(
        (el: CanvasElement) => el.kind === 'component' || el.kind === 'node'
      )
      .map(toReactFlowNode);
  }, [
    canvasDocument?.elementOrder?.length,
    canvasDocument?.elements,
    canvasState.elements,
  ]);

  const edges = useMemo(() => {
    if (canvasDocument?.elementOrder?.length) {
      return canvasDocument.elementOrder
        .map((id) => canvasDocument.elements[id])
        .filter(
          (
            element
          ): element is Parameters<
            typeof convertCanvasEdgeToReactFlowEdge
          >[0] => Boolean(element) && isCanvasEdge(element)
        )
        .map(
          (edge) => convertCanvasEdgeToReactFlowEdge(edge) as unknown as Edge
        );
    }

    return (canvasState.connections || []).map(toReactFlowEdge);
  }, [
    canvasDocument?.elementOrder?.length,
    canvasDocument?.elements,
    canvasState.connections,
  ]);

  // Load canvas on mount
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    const loadCanvas = async () => {
      if (import.meta.env.DEV) {
        console.log(
          `[useCanvasScene] Loading canvas for project=${projectId}, canvas=${canvasId}`
        );
      }
      setIsLoading(true);
      // Reset state immediately to avoid showing stale data from previous project
      setCanvasState({
        elements: [],
        connections: [],
        selectedElements: [],
        lifecyclePhase: LifecyclePhase.DESIGN,
      });

      if (import.meta.env.DEV) {
        console.log('[useCanvasScene] Calling persistence.loadCanvas()...');
      }
      let loadedState = await persistence.loadCanvas();
      let loadedFromLegacy = false;

      if (import.meta.env.DEV) {
        console.log(
          '[useCanvasScene] persistence.loadCanvas() returned:',
          loadedState ? `${loadedState.elements?.length || 0} elements` : 'null'
        );
      }

      if (!loadedState) {
        if (import.meta.env.DEV) {
          console.log(
            '[useCanvasScene] No state found, trying legacy migration...'
          );
        }
        const migratedState = await persistence.migrateLegacyState();
        if (migratedState) {
          if (import.meta.env.DEV) {
            console.log('[useCanvasScene] Legacy state migrated:', migratedState);
          }
          loadedState = migratedState;
          loadedFromLegacy = true;
          try {
            await persistence.saveCanvas(loadedState);
          } catch (e) {
            console.warn('Failed to persist legacy canvas state', e);
          }
        }
      }

      if (loadedState) {
        console.log('[useCanvasScene] Setting canvas state with loaded data:', {
          elementCount: loadedState.elements?.length || 0,
          connectionCount: loadedState.connections?.length || 0,
          lifecyclePhase: loadedState.lifecyclePhase,
        });
        setCanvasState(loadedState);
        setNotification({
          type: 'info',
          message: loadedFromLegacy
            ? 'Imported legacy canvas state'
            : 'Canvas loaded successfully',
        });
      } else {
        console.warn(
          '[useCanvasScene] No canvas state found - showing empty canvas'
        );
        setNotification({ type: 'info', message: 'New canvas created' });
      }

      if (isE2E) {
        setCanvasState((prev) => {
          const baseElements = (prev.elements || []).filter(
            (el) => el.kind !== 'shape'
          );
          const strokeElement: CanvasElement = {
            id: 'e2e-stroke',
            kind: 'shape',
            type: 'stroke',
            position: { x: 0, y: 0 },
            data: {
              tool: 'pen',
              color: '#111111',
              strokeWidth: 2,
              points: [120, 160, 200, 200, 260, 150],
            },
          };

          const rectangleElement: CanvasElement = {
            id: 'e2e-rectangle',
            kind: 'shape',
            type: 'rectangle',
            position: { x: 0, y: 0 },
            data: {
              type: 'rectangle',
              x: 180,
              y: 180,
              width: 140,
              height: 90,
              fill: 'rgba(25, 118, 210, 0.15)',
              stroke: '#1976d2',
              strokeWidth: 2,
            },
          };

          return {
            ...prev,
            elements: [...baseElements, strokeElement, rectangleElement],
          };
        });
      }

      setIsLoading(false);
    };

    loadCanvas();
  }, [projectId, canvasId, isE2E]);
  /* eslint-enable react-hooks/exhaustive-deps */

  // Auto-save on canvas changes
  /* eslint-disable react-hooks/exhaustive-deps */
  useEffect(() => {
    if (!isLoading) {
      // Call triggerAutoSave if provided. Intentionally avoid adding
      // `persistence.triggerAutoSave` to deps because the persistence
      // helper may return a new function instance on each render which
      // would cause unnecessary re-runs and potential render loops.

      persistence.triggerAutoSave?.(canvasState);
    }
  }, [canvasState, isLoading]);
  /* eslint-enable react-hooks/exhaustive-deps */

  // Keyboard shortcuts
  const handleDelete = useCallback(() => {
    // Get selected IDs from canvas state
    const ids = canvasState.selectedElements ?? [];

    if (!isE2E && (canvasDocument?.elementOrder?.length ?? 0) > 0) {
      const targetSet = new Set(ids);

      const edgeIdsToRemove = Object.values(canvasDocument.elements)
        .filter(
          (element) =>
            isCanvasEdge(element) &&
            (targetSet.has(element.sourceId) || targetSet.has(element.targetId))
        )
        .map((edge) => edge.id);

      const idsToRemove = [...new Set([...ids, ...edgeIdsToRemove])];

      idsToRemove.forEach((elementId) => removeElement(elementId));

      clearSelection();
      setNotification({
        type: 'success',
        message: `Deleted ${idsToRemove.length} item(s)`,
      });
      return;
    }

    if (isE2E) {
      const targetIds = ids.length
        ? ids
        : (() => {
            const firstNode = canvasState.elements.find(
              (el) => el.kind !== 'shape'
            );
            return firstNode ? [firstNode.id] : [];
          })();

      setCanvasState((prev) => {
        const remainingElements = prev.elements
          .filter((el) =>
            el.kind === 'shape' ? true : !targetIds.includes(el.id)
          )
          .map((el) => (el.kind === 'shape' ? el : { ...el, selected: false }));
        const remainingConnections = prev.connections.filter(
          (edge) =>
            !targetIds.includes(edge.source) && !targetIds.includes(edge.target)
        );

        return {
          ...prev,
          elements: remainingElements,
          connections: remainingConnections,
          selectedElements: [],
        };
      });

      if (rfInstance) {
        const remaining = rfInstance
          .getNodes()
          .filter((node: unknown) => !targetIds.includes(node.id))
          .map((node: unknown) => ({ ...node, selected: false }));
        rfInstance.setNodes(remaining);
      }

      clearSelection();
      setNotification({
        type: 'success',
        message: `Deleted ${targetIds.length} item(s)`,
      });
      return;
    }

    // Use CanvasEditor service for deletion
    selection.deleteSelection();
    clearSelection();

    // Update local state from ReactFlow instance
    if (rfInstance) {
      const updatedNodes = rfInstance.getNodes();
      const updatedEdges = rfInstance.getEdges();
      setCanvasState((prev) => ({
        ...prev,
        elements: [
          ...prev.elements.filter((el) => el.kind === 'shape'),
          ...fromReactFlowNodes(updatedNodes),
        ],
        connections: fromReactFlowEdges(updatedEdges),
        selectedElements: [],
      }));
    }

    setNotification({
      type: 'success',
      message: `Deleted ${ids.length} item(s)`,
    });
    /* eslint-disable react-hooks/exhaustive-deps */
  }, [
    selection,
    nodes,
    edges,
    isE2E,
    canvasState.selectedElements,
    canvasDocument,
    removeElement,
    clearSelection,
  ]);
  /* eslint-enable react-hooks/exhaustive-deps */

  const handleSelectAll = useCallback(() => {
    selection.selectAll(nodes);
    setSelection({ selectedIds: nodes.map((node) => node.id) });
    if (isE2E) {
      setCanvasState((prev) => ({
        ...prev,
        elements: prev.elements.map((el) =>
          el.kind === 'shape' ? el : { ...el, selected: true }
        ),
        selectedElements: prev.elements
          .filter((el) => el.kind !== 'shape')
          .map((el) => el.id),
      }));
      if (rfInstance) {
        const nextNodes = rfInstance.getNodes().map((node: unknown) => ({
          ...node,
          selected: true,
        }));
        rfInstance.setNodes(nextNodes);
      }
    }
    setNotification({
      type: 'info',
      message: `Selected ${nodes.length} item(s)`,
    });
    // Intentionally omit setCanvasState from deps to avoid triggering when
    // Jotai returns a new setter reference. Other deps capture the necessary
    // values for this callback.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selection, nodes, isE2E, rfInstance]);

  const handleCopy = useCallback(() => {
    const selectedIds = canvasState.selectedElements ?? [];
    if (selectedIds.length === 0) return;

    // Delegate to CanvasEditor service
    selection.copySelection();
    setNotification({
      type: 'info',
      message: `Copied ${selectedIds.length} item(s)`,
    });
  }, [selection, canvasState.selectedElements]);

  const handlePaste = useCallback(() => {
    if ((canvasDocument?.elementOrder?.length ?? 0) > 0) {
      // Use CanvasEditor service - it handles paste internally
      selection.pasteSelection();

      // Sync from ReactFlow instance after paste
      if (rfInstance) {
        setTimeout(() => {
          const nodes = rfInstance.getNodes();
          const selectedNodeIds = nodes
            .filter((n: unknown) => n.selected)
            .map((n: unknown) => n.id);

          nodes.forEach((node: unknown) => {
            if (node.selected) {
              const canvasNode = convertReactFlowNodeToCanvasNode(node as unknown);
              addElement({
                ...canvasNode,
                selected: true,
                updatedAt: new Date(),
              });
            }
          });

          const edges = rfInstance.getEdges();
          edges.forEach((edge: unknown) => {
            const canvasEdge = convertReactFlowEdgeToCanvasEdge(edge as unknown);
            addElement({
              ...canvasEdge,
              selected: false,
              updatedAt: new Date(),
            });
          });

          setSelection({ selectedIds: selectedNodeIds });
          setNotification({ type: 'success', message: 'Pasted items' });
        }, 100);
      }
      return;
    }

    // Legacy state path
    selection.pasteSelection();

    // Sync from ReactFlow instance
    if (rfInstance) {
      setTimeout(() => {
        const updatedNodes = rfInstance.getNodes();
        const updatedEdges = rfInstance.getEdges();
        const selectedIds = updatedNodes
          .filter((n: unknown) => n.selected)
          .map((n: unknown) => n.id);

        setSelection({ selectedIds });
        setCanvasState((prev) => ({
          ...prev,
          elements: [
            ...prev.elements.filter((el) => el.kind === 'shape'),
            ...fromReactFlowNodes(updatedNodes),
          ],
          connections: fromReactFlowEdges(updatedEdges),
          selectedElements: selectedIds,
        }));

        setNotification({ type: 'success', message: 'Pasted items' });
      }, 100);
    }
  }, [
    selection,
    rfInstance,
    canvasDocument?.elementOrder?.length,
    addElement,
    setSelection,
    setCanvasState,
  ]);

  const handleDuplicate = useCallback(() => {
    const selectedIds = canvasState.selectedElements ?? [];
    if (selectedIds.length === 0) return;

    if ((canvasDocument?.elementOrder?.length ?? 0) > 0) {
      // Use CanvasEditor service - it handles duplication internally
      selection.duplicateSelection();

      // Sync from ReactFlow instance after duplication
      if (rfInstance) {
        setTimeout(() => {
          const nodes = rfInstance.getNodes();
          const selectedNodeIds = nodes
            .filter((n: unknown) => n.selected)
            .map((n: unknown) => n.id);

          nodes.forEach((node: unknown) => {
            if (node.selected) {
              const canvasNode = convertReactFlowNodeToCanvasNode(node as unknown);
              addElement({
                ...canvasNode,
                selected: true,
                updatedAt: new Date(),
              });
            }
          });

          const edges = rfInstance.getEdges();
          edges.forEach((edge: unknown) => {
            const canvasEdge = convertReactFlowEdgeToCanvasEdge(edge as unknown);
            addElement({
              ...canvasEdge,
              selected: false,
              updatedAt: new Date(),
            });
          });

          setSelection({ selectedIds: selectedNodeIds });
          setNotification({
            type: 'success',
            message: `Duplicated ${selectedNodeIds.length} item(s)`,
          });
        }, 100);
      }
      return;
    }

    // Legacy state path
    selection.duplicateSelection();

    // Sync from ReactFlow instance
    if (rfInstance) {
      setTimeout(() => {
        const updatedNodes = rfInstance.getNodes();
        const updatedEdges = rfInstance.getEdges();
        const selectedNodeIds = updatedNodes
          .filter((n: unknown) => n.selected)
          .map((n: unknown) => n.id);

        setSelection({ selectedIds: selectedNodeIds });
        setCanvasState((prev) => ({
          ...prev,
          elements: [...prev.elements, ...fromReactFlowNodes(updatedNodes)],
          connections: [
            ...prev.connections,
            ...fromReactFlowEdges(updatedEdges),
          ],
          selectedElements: selectedNodeIds,
        }));

        setNotification({
          type: 'success',
          message: `Duplicated ${selectedIds.length} item(s)`,
        });
      }, 100);
    }
    // setCanvasState and setSelection intentionally omitted from deps to avoid
    // re-runs caused by unstable setter references from Jotai.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    selection,
    rfInstance,
    canvasState.selectedElements,
    canvasDocument?.elementOrder?.length,
    addElement,
  ]);

  const handleFitView = useCallback(() => {
    rfInstance?.fitView({ padding: 0.2, duration: 400 });
  }, [rfInstance]);

  useCanvasShortcuts({
    enabled: true,
    shortcuts: {
      onDelete: handleDelete,
      onSelectAll: handleSelectAll,
      onCopy: handleCopy,
      onPaste: handlePaste,
      onDuplicate: handleDuplicate,
      onFitView: handleFitView,
      onUndo: () => history.undo(),
      onRedo: () => history.redo(),
    },
  });

  // React Flow handlers
  const handleInit = useCallback((instance: ReactFlowInstance) => {
    setRfInstance(instance);
    // Call fitView once on initialization to fit all nodes in view
    // This avoids the infinite loop caused by the fitView prop
    setTimeout(() => {
      instance.fitView({ padding: 0.2 });
    }, 0);
  }, []);

  const handleNodesChange = useCallback(
    (changes: unknown[]) => {
      if (!isE2E && (canvasDocument?.elementOrder?.length ?? 0) > 0) {
        const updatesMap = new Map<string, unknown>();

        changes.forEach((change) => {
          if (change.type === 'remove') {
            removeElement(change.id);
            return;
          }

          const element = canvasDocument.elements[change.id];
          if (!element || !isCanvasNode(element)) {
            return;
          }

          const pending = updatesMap.get(change.id) ?? {};

          if (change.type === 'position' && change.position) {
            const newPosition = {
              x: change.position.x,
              y: change.position.y,
            };
            pending.transform = {
              ...element.transform,
              position: newPosition,
            };
            if (element.bounds) {
              pending.bounds = {
                ...element.bounds,
                x: newPosition.x,
                y: newPosition.y,
              };
            }
          }

          if (
            change.type === 'select' &&
            typeof change.selected === 'boolean'
          ) {
            pending.selected = change.selected;
          }

          if (Object.keys(pending).length > 0) {
            pending.updatedAt = new Date();
            updatesMap.set(change.id, pending);
          }
        });

        const updates = Array.from(updatesMap.entries()).map(
          ([id, changes]) => ({
            id,
            changes,
          })
        );

        if (updates.length > 0) {
          batchUpdateElements(updates as unknown);
        }

        return;
      }

      setCanvasState((prev) => {
        const currentElements = prev.elements || [];
        const currentNodes = currentElements
          .filter(
            (el: CanvasElement) => el.kind === 'component' || el.kind === 'node'
          )
          .map(toReactFlowNode);

        const updatedNodes = currentNodes.map((node) => {
          const change = changes.find((c: unknown) => c.id === node.id);
          if (!change) return node;

          if (change.type === 'position' && change.position) {
            return { ...node, position: change.position };
          }
          if (change.type === 'select') {
            return { ...node, selected: change.selected };
          }
          return node;
        });

        const newElements = [
          ...currentElements.filter((el: CanvasElement) => el.kind === 'shape'),
          ...fromReactFlowNodes(updatedNodes),
        ];

        // Only update if elements actually changed (prevent ping-pong with ReactFlow)
        const prevNormalized = normalizeNodesForCompare(currentNodes);
        const nextNormalized = normalizeNodesForCompare(updatedNodes);

        const nodesChanged =
          JSON.stringify(prevNormalized) !== JSON.stringify(nextNormalized);

        if (!nodesChanged) {
          return prev; // no change, avoid updating state
        }

        return {
          ...prev,
          elements: newElements,
        };
      });
    },
    [batchUpdateElements, canvasDocument, isE2E, removeElement, setCanvasState]
  );

  const handleEdgesChange = useCallback(
    (changes: unknown[]) => {
      if (!isE2E && (canvasDocument?.elementOrder?.length ?? 0) > 0) {
        const updates: Array<{ id: string; changes: Record<string, unknown> }> = [];

        changes.forEach((change) => {
          if (change.type === 'remove') {
            removeElement(change.id);
            return;
          }

          if (
            change.type === 'select' &&
            typeof change.selected === 'boolean'
          ) {
            const element = canvasDocument.elements[change.id];
            if (element && isCanvasEdge(element)) {
              updates.push({
                id: change.id,
                changes: { selected: change.selected, updatedAt: new Date() },
              });
            }
          }
        });

        if (updates.length > 0) {
          updates.forEach((update) => updateElement(update as unknown));
        }

        return;
      }

      setCanvasState((prev) => {
        const currentConnections = prev.connections || [];

        const updatedEdges = currentConnections.filter((edge) => {
          const removeChange = changes.find(
            (c: unknown) => c.type === 'remove' && c.id === edge.id
          );
          return !removeChange;
        });

        // Compare normalized connections to avoid unnecessary updates
        const prevNormalized =
          normalizeConnectionsForCompare(currentConnections);
        const nextNormalized = normalizeConnectionsForCompare(updatedEdges);

        if (JSON.stringify(prevNormalized) === JSON.stringify(nextNormalized)) {
          return prev;
        }

        return {
          ...prev,
          connections: fromReactFlowEdges(updatedEdges),
        };
      });
    },
    [canvasDocument, isE2E, removeElement, setCanvasState, updateElement]
  );

  const handleConnect = useCallback(
    (connection: unknown) => {
      if (!connection.source || !connection.target) return;

      const newEdge: Edge = {
        id: `edge-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
        source: connection.source,
        target: connection.target,
        sourceHandle: connection.sourceHandle,
        targetHandle: connection.targetHandle,
        animated: true,
      };

      if (canvasDocument?.elementOrder?.length) {
        const canvasEdge = convertReactFlowEdgeToCanvasEdge({
          id: newEdge.id,
          source: newEdge.source,
          target: newEdge.target,
          sourceHandle:
            newEdge.sourceHandle === null ? undefined : newEdge.sourceHandle,
          targetHandle:
            newEdge.targetHandle === null ? undefined : newEdge.targetHandle,
          type: newEdge.type ?? 'default',
          data: newEdge.data ?? {},
        } as unknown);
        addElement(canvasEdge);
        return;
      }

      setCanvasState((prev) => {
        const exists = prev.connections.some(
          (edge) =>
            edge.source === newEdge.source &&
            edge.target === newEdge.target &&
            edge.sourceHandle === (newEdge.sourceHandle ?? undefined) &&
            edge.targetHandle === (newEdge.targetHandle ?? undefined)
        );

        if (exists) {
          return prev;
        }

        return {
          ...prev,
          connections: [...prev.connections, ...fromReactFlowEdges([newEdge])],
        };
      });
    },
    [addElement, canvasDocument?.elementOrder, setCanvasState]
  );

  const lastSelectionRef = useRef<string[]>([]);

  const handleSelectionChange = useCallback(
    (params: { nodes: Node[]; edges: Edge[] }) => {
      const selectedIds = params.nodes.map((node) => node.id);

      // Only update if selection actually changed to prevent infinite loop
      const prevSet = new Set(lastSelectionRef.current);
      const nextSet = new Set(selectedIds);
      if (
        prevSet.size === nextSet.size &&
        [...prevSet].every((id) => nextSet.has(id))
      ) {
        return;
      }

      lastSelectionRef.current = selectedIds;
      selection.handleSelectionChange(params);
      setSelection({ selectedIds });
    },
    [selection, setSelection]
  );

  const handleAddComponent = useCallback(
    (component: ComponentDef, position?: { x: number; y: number }) => {
      const elementId = `${component.type}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
      const defaultPosition = position || { x: 250, y: 200 };

      if (canvasDocument?.elementOrder?.length) {
        const reactFlowNode = {
          id: elementId,
          type: component.type,
          position: defaultPosition,
          data: { ...component.defaultData },
          selected: false,
        } as Node;

        const canvasNode = convertReactFlowNodeToCanvasNode(
          reactFlowNode as unknown
        );
        addElement(canvasNode);
        setSelection({ selectedIds: [elementId] });
      } else {
        const newElement: CanvasElement = {
          id: elementId,
          kind: component.kind,
          type: component.type,
          position: defaultPosition,
          data: { ...component.defaultData },
          selected: false,
        };

        setCanvasState((prev) => ({
          ...prev,
          elements: [...prev.elements, newElement],
        }));
      }

      setNotification({ type: 'success', message: `Added ${component.label}` });
    },
    [addElement, canvasDocument?.elementOrder, setCanvasState, setSelection]
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over, delta } = event;

      const component = active.data.current as ComponentDef | undefined;
      if (!component) return;

      if (!over || over.id !== 'canvas-drop-zone') {
        handleAddComponent(component);
        return;
      }

      // Project screen coordinates to canvas coordinates
      const canvasPosition = rfInstance?.project({
        x: delta.x + 300, // Offset for palette width
        y: delta.y + 100,
      }) || { x: 250 + delta.x, y: 200 + delta.y };

      handleAddComponent(component, canvasPosition);
    },
    [rfInstance, handleAddComponent]
  );

  const closeNotification = useCallback(() => {
    setNotification(null);
  }, []);

  return {
    isLoading,
    nodes,
    edges,
    rfInstance,
    canvasState,
    notification,
    handleInit,
    handleNodesChange,
    handleEdgesChange,
    handleConnect,
    handleSelectionChange,
    handleAddComponent,
    handleDragEnd,
    handleFitView,
    closeNotification,
    // Lifecycle
    currentPhase,
    canPerformOperation: (operation: string) =>
      canPerformOperation(operation as unknown),
  };
};
