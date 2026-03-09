/**
 * useCanvasKeyboardShortcuts Hook
 *
 * Manages all 30+ keyboard shortcuts for the canvas including:
 * undo/redo, zoom, UI toggles, selection, clipboard, layer operations,
 * export, tool shortcuts, alignment, and help.
 *
 * @doc.type hook
 * @doc.purpose Canvas keyboard shortcut management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useEffect } from 'react';
import { useReactFlow } from '@xyflow/react';
import type { DrawingTool } from './types';

interface UseCanvasKeyboardShortcutsOptions {
  canvas: {
    nodes: unknown[];
    selectedNodeIds: string[];
    activeTool: string;
    canUndo: boolean;
    canRedo: boolean;
    undo: () => void;
    redo: () => void;
    resetZoom: () => void;
    zoomIn: () => void;
    zoomOut: () => void;
    selectNodes: (ids: string[]) => void;
    removeNode: (id: string) => void;
    duplicateNode: (id: string) => any;
    createGroup: (ids: string[], name: string) => void;
    ungroup: (id: string) => void;
    bringForward: (id: string) => void;
    sendBackward: (id: string) => void;
    bringToFront: (id: string) => void;
    sendToBack: (id: string) => void;
    downloadJSON: (filename: string) => void;
    downloadSVG: (filename: string) => void;
    setActiveTool: (tool: string) => void;
    addNode: (data: unknown) => any;
    alignNodes: (type: string) => void;
  };
  projectId: string | undefined;
  calmMode: boolean;
  setCalmMode: (v: boolean) => void;
  leftRailVisible: boolean;
  setLeftRailVisible: (v: boolean) => void;
  minimapVisible: boolean;
  setMinimapVisible: (v: boolean) => void;
  propertiesPanelOpen: boolean;
  setPropertiesPanelOpen: (v: boolean) => void;
  copiedNodes: unknown[];
  setCopiedNodeIds: (ids: string[]) => void;
  setCopiedNodes: (nodes: unknown[]) => void;
  hasMultipleSelection: boolean;
  setDrawingTool: (tool: DrawingTool) => void;
  setContextMenu: (v: null) => void;
  setNodeContextMenu: (v: null) => void;
  setAddMenuAnchor: (v: null) => void;
  setExportMenuAnchor: (v: null) => void;
  setAlignMenuAnchor: (v: null) => void;
  setLayerMenuAnchor: (v: null) => void;
  setShortcutLegendOpen: (v: boolean) => void;
  showToast: (message: string, severity?: 'success' | 'info' | 'warning' | 'error') => void;
  addNodeAtPosition: (type: string, position: { x: number; y: number }) => void;
}

export function useCanvasKeyboardShortcuts({
  canvas,
  projectId,
  calmMode,
  setCalmMode,
  leftRailVisible,
  setLeftRailVisible,
  minimapVisible,
  setMinimapVisible,
  propertiesPanelOpen,
  setPropertiesPanelOpen,
  copiedNodes,
  setCopiedNodeIds,
  setCopiedNodes,
  hasMultipleSelection,
  setDrawingTool,
  setContextMenu,
  setNodeContextMenu,
  setAddMenuAnchor,
  setExportMenuAnchor,
  setAlignMenuAnchor,
  setLayerMenuAnchor,
  setShortcutLegendOpen,
  showToast,
  addNodeAtPosition,
}: UseCanvasKeyboardShortcutsOptions): void {
  const reactFlowInstance = useReactFlow();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isMeta = e.metaKey || e.ctrlKey;
      const isShift = e.shiftKey;
      const target = e.target as HTMLElement;

      // Don't handle shortcuts when typing in inputs
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }

      // === UNDO/REDO ===
      if (isMeta && e.key === 'z' && !isShift) {
        e.preventDefault();
        canvas.undo();
        showToast('Undo', 'info');
        return;
      }
      if (isMeta && e.key === 'z' && isShift) {
        e.preventDefault();
        canvas.redo();
        showToast('Redo', 'info');
        return;
      }
      if (isMeta && e.key === 'y') {
        e.preventDefault();
        canvas.redo();
        showToast('Redo', 'info');
        return;
      }

      // === ZOOM CONTROLS ===
      if (isMeta && e.key === '0') {
        e.preventDefault();
        canvas.resetZoom();
        return;
      }
      if (isMeta && (e.key === '+' || e.key === '=')) {
        e.preventDefault();
        canvas.zoomIn();
        return;
      }
      if (isMeta && e.key === '-') {
        e.preventDefault();
        canvas.zoomOut();
        return;
      }

      // === UI TOGGLES ===
      if (isMeta && isShift && e.key === 'C') {
        e.preventDefault();
        setCalmMode(!calmMode);
        return;
      }
      if (isMeta && isShift && e.key === 'L') {
        e.preventDefault();
        setLeftRailVisible(!leftRailVisible);
        return;
      }
      if (isMeta && isShift && e.key === 'M') {
        e.preventDefault();
        setMinimapVisible(!minimapVisible);
        return;
      }
      if (isMeta && isShift && e.key === 'P') {
        e.preventDefault();
        setPropertiesPanelOpen(!propertiesPanelOpen);
        return;
      }

      // === SELECTION ===
      if (isMeta && e.key === 'a') {
        e.preventDefault();
        const allNodeIds = canvas.nodes.map((node) => node.id);
        canvas.selectNodes(allNodeIds);
        return;
      }
      if (isMeta && e.key === 'd' && !isShift) {
        e.preventDefault();
        canvas.selectNodes([]);
        return;
      }

      // === CLIPBOARD ===
      if (isMeta && e.key === 'c') {
        e.preventDefault();
        const nodesToCopy = canvas.nodes.filter((n) =>
          canvas.selectedNodeIds.includes(n.id)
        );
        setCopiedNodeIds([...canvas.selectedNodeIds]);
        setCopiedNodes(nodesToCopy.map((n) => ({ ...n })));
        return;
      }
      if (isMeta && e.key === 'v') {
        e.preventDefault();
        if (copiedNodes.length > 0) {
          const newNodeIds: string[] = [];
          copiedNodes.forEach((node) => {
            const newNode = canvas.addNode({
              ...node,
              id: `node-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              position: {
                x: node.position.x + 20,
                y: node.position.y + 20,
              },
            });
            newNodeIds.push(newNode.id);
          });
          canvas.selectNodes(newNodeIds);
        }
        return;
      }
      if (isMeta && e.key === 'x') {
        e.preventDefault();
        const nodesToCut = canvas.nodes.filter((n) =>
          canvas.selectedNodeIds.includes(n.id)
        );
        setCopiedNodeIds([...canvas.selectedNodeIds]);
        setCopiedNodes(nodesToCut.map((n) => ({ ...n })));
        canvas.selectedNodeIds.forEach((id) => canvas.removeNode(id));
        return;
      }

      // === DELETE ===
      if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        canvas.selectedNodeIds.forEach((nodeId) => canvas.removeNode(nodeId));
        return;
      }

      // === DUPLICATE ===
      if (isMeta && e.key === 'd' && isShift) {
        e.preventDefault();
        const newNodeIds: string[] = [];
        canvas.selectedNodeIds.forEach((nodeId) => {
          const newNode = canvas.duplicateNode(nodeId);
          newNodeIds.push(newNode.id);
        });
        canvas.selectNodes(newNodeIds);
        return;
      }

      // === GROUPING ===
      if (isMeta && e.key === 'g' && !isShift) {
        e.preventDefault();
        if (canvas.selectedNodeIds.length > 1) {
          canvas.createGroup(canvas.selectedNodeIds, 'Group');
        }
        return;
      }
      if (isMeta && e.key === 'g' && isShift) {
        e.preventDefault();
        const groupNodes = canvas.nodes.filter(
          (n) => canvas.selectedNodeIds.includes(n.id) && n.type === 'group'
        );
        groupNodes.forEach((g) => canvas.ungroup(g.id));
        return;
      }

      // === LAYER OPERATIONS ===
      if (e.key === ']' && !isMeta) {
        e.preventDefault();
        canvas.selectedNodeIds.forEach((id) => canvas.bringForward(id));
        return;
      }
      if (e.key === '[' && !isMeta) {
        e.preventDefault();
        canvas.selectedNodeIds.forEach((id) => canvas.sendBackward(id));
        return;
      }
      if (e.key === ']' && isMeta) {
        e.preventDefault();
        canvas.selectedNodeIds.forEach((id) => canvas.bringToFront(id));
        return;
      }
      if (e.key === '[' && isMeta) {
        e.preventDefault();
        canvas.selectedNodeIds.forEach((id) => canvas.sendToBack(id));
        return;
      }

      // === EXPORT ===
      if (isMeta && e.key === 'e') {
        e.preventDefault();
        canvas.downloadJSON(`canvas-${projectId || 'export'}.json`);
        return;
      }
      if (isMeta && isShift && e.key === 'E') {
        e.preventDefault();
        canvas.downloadSVG(`canvas-${projectId || 'export'}.svg`);
        return;
      }

      // === TOOL SHORTCUTS ===
      if (e.key === 'v' && !isMeta) {
        e.preventDefault();
        canvas.setActiveTool('select');
        return;
      }
      if (e.key === 'p' && !isMeta) {
        e.preventDefault();
        canvas.setActiveTool('draw');
        setDrawingTool('pen');
        return;
      }
      if (e.key === 'b' && !isMeta) {
        e.preventDefault();
        canvas.setActiveTool('draw');
        setDrawingTool('pencil');
        return;
      }
      if (e.key === 'f' && !isMeta) {
        e.preventDefault();
        const center = reactFlowInstance.getViewport();
        addNodeAtPosition('frame', {
          x: -center.x / center.zoom + 200,
          y: -center.y / center.zoom + 150,
        });
        return;
      }
      if (e.key === 'n' && !isMeta) {
        e.preventDefault();
        const center = reactFlowInstance.getViewport();
        addNodeAtPosition('sticky-note', {
          x: -center.x / center.zoom + 200,
          y: -center.y / center.zoom + 150,
        });
        return;
      }
      if (e.key === 't' && !isMeta) {
        e.preventDefault();
        const center = reactFlowInstance.getViewport();
        addNodeAtPosition('text', {
          x: -center.x / center.zoom + 200,
          y: -center.y / center.zoom + 150,
        });
        return;
      }

      // === ALIGNMENT SHORTCUTS ===
      if (isMeta && isShift && e.key === 'ArrowLeft') {
        e.preventDefault();
        if (hasMultipleSelection) canvas.alignNodes('left');
        return;
      }
      if (isMeta && isShift && e.key === 'ArrowRight') {
        e.preventDefault();
        if (hasMultipleSelection) canvas.alignNodes('right');
        return;
      }
      if (isMeta && isShift && e.key === 'ArrowUp') {
        e.preventDefault();
        if (hasMultipleSelection) canvas.alignNodes('top');
        return;
      }
      if (isMeta && isShift && e.key === 'ArrowDown') {
        e.preventDefault();
        if (hasMultipleSelection) canvas.alignNodes('bottom');
        return;
      }

      // === ESCAPE ===
      if (e.key === 'Escape') {
        e.preventDefault();
        canvas.selectNodes([]);
        setContextMenu(null);
        setNodeContextMenu(null);
        setAddMenuAnchor(null);
        setExportMenuAnchor(null);
        setAlignMenuAnchor(null);
        setLayerMenuAnchor(null);
        canvas.setActiveTool('select');
        return;
      }

      // === HELP ===
      if (e.key === '?' || (isShift && e.key === '/')) {
        e.preventDefault();
        setShortcutLegendOpen(true);
        return;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    canvas,
    calmMode,
    setCalmMode,
    leftRailVisible,
    setLeftRailVisible,
    minimapVisible,
    setMinimapVisible,
    propertiesPanelOpen,
    copiedNodes,
    hasMultipleSelection,
    projectId,
    reactFlowInstance,
    showToast,
    setCopiedNodeIds,
    setCopiedNodes,
    setDrawingTool,
    setContextMenu,
    setNodeContextMenu,
    setAddMenuAnchor,
    setExportMenuAnchor,
    setAlignMenuAnchor,
    setLayerMenuAnchor,
    setShortcutLegendOpen,
    addNodeAtPosition,
  ]);
}
