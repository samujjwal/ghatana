/**
 * Default sizes (width × height) for each canvas node type.
 * Override per-node via node.data.width / node.data.height if set.
 */
export const NODE_DEFAULT_SIZES: Record<string, { width: number; height: number }> = {
  frame:        { width: 400, height: 300 },
  'sticky-note': { width: 200, height: 200 },
  text:         { width: 300, height: 150 },
  task:         { width: 250, height: 70  },
  default:      { width: 150, height: 150 },
};

/** Resolve width/height for a given node type. */
function getNodeSize(type: string | undefined): { width: number; height: number } {
  return NODE_DEFAULT_SIZES[type ?? 'default'] ?? NODE_DEFAULT_SIZES.default;
}

/**
 * UnifiedCanvas Complete - Production-Ready Implementation
 *
 * Complete unified canvas with all features from Epics 1-10.
 * Decomposed into focused modules under _canvas/.
 *
 * Module structure:
 * - _canvas/types.ts — Shared types (DrawingTool, NodeContextMenuState)
 * - _canvas/DraggableBox.tsx — Draggable container component
 * - _canvas/useCanvasKeyboardShortcuts.ts — 30+ keyboard shortcuts
 * - _canvas/useCanvasDrawing.ts — Drawing state & pointer handlers
 * - _canvas/useCanvasExport.ts — Export/import handlers
 * - _canvas/useCanvasRoleInfo.tsx — Role/phase info derivation
 * - _canvas/CanvasNodeContextMenu.tsx — Node right-click menu
 * - _canvas/CanvasStatusBar.tsx — Bottom status bar
 * - _canvas/CanvasOutlinePanel.tsx — Outline & layers panel
 *
 * @doc.type component
 * @doc.purpose Complete canvas route (orchestrator)
 * @doc.layer routes
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { Box, Alert } from '@ghatana/ui';
import {
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  Background,
  MiniMap,
  type NodeChange,
  type EdgeChange,
  type Connection as RFConnection,
  Controls,
} from '@xyflow/react';
import { useAtom, useSetAtom } from 'jotai';
import {
  headerActionContextAtom,
  headerContextActionsAtom,
  headerCanvasModeAtom,
  headerShowCanvasModeAtom,
  headerOnCanvasModeChangeAtom,
  headerPhaseInfoAtom,
  headerRoleInfoAtom,
} from '../../../state/atoms/layoutAtom';
import '@xyflow/react/dist/style.css';
import { getPhaseTheme, type LifecyclePhase } from '../../../theme/phaseTheme';
import {
  Settings,
  Share2 as Share,
  Undo2 as Undo,
  Redo2 as Redo,
  ZoomIn,
  ZoomOut,
  Download as FileDownload,
} from 'lucide-react';

// Import custom node/edge types
import { nodeTypes } from '../../../components/canvas/nodeTypes';
import { DependencyEdge } from '../../../components/canvas/edges';

const edgeTypes = {
  dependency: DependencyEdge,
  flow: DependencyEdge,
};

type YAPPCCanvasNode = Parameters<UseUnifiedCanvasReturn['addNode']>[0];

// Canvas library imports
import {
  CanvasCommandProvider,
  useCanvasCommands,
  useCanvasTelemetry,
  CanvasChromeLayout,
  chromeCalmModeAtom,
  chromeLeftRailVisibleAtom,
  chromeMinimapVisibleAtom,
  chromeInspectorVisibleAtom,
  chromeZoomLevelAtom,
} from '@ghatana/canvas';

import { UnifiedToolbar } from '../../../components/canvas/unified/UnifiedToolbar';
import { UnifiedLeftRail } from '../../../components/canvas/unified/UnifiedLeftRail';
import { UnifiedRightPanel } from '../../../components/canvas/unified/UnifiedRightPanel';
import {
  type CanvasMode as NavCanvasMode,
} from '../../../components/navigation';

import { useCanvasMode } from '../../../hooks/useCanvasMode';
import type { CanvasMode } from '../../../types/canvasMode';
import { useWorkspaceContext } from '../../../hooks/useWorkspaceData';
import { useQuery } from '@tanstack/react-query';
import { CanvasErrorBoundary } from '../../../components/canvas/unified/CanvasErrorBoundary';
import { useUnifiedCanvas, type UseUnifiedCanvasReturn } from '../../../hooks/useUnifiedCanvas';

import {
  AIStatusBar,
  useAIStatusBar,
} from '../../../components/ai/AIStatusBar';
import {
  useLifecycleZones,
} from '../../../components/canvas/ZoomableLifecycleZones';
import {
  useInlineCodePanel,
} from '../../../components/canvas/InlineCodePanel';
import {
  useStudioMode,
} from '../../../components/studio/StudioLayout';
import {
  useKeyboardShortcuts,
} from '../../../components/keyboard/KeyboardShortcutsManager';
import { KeyboardShortcutLegend } from '../../../components/canvas/KeyboardShortcutLegend';

// Decomposed modules
import {
  DraggableBox,
  useCanvasKeyboardShortcuts,
  useCanvasDrawing,
  useCanvasExport,
  useCanvasRoleInfo,
  CanvasNodeContextMenu,
  CanvasStatusBar,
  CanvasOutlinePanel,
  type NodeContextMenuState,
} from './_canvas';
import type { AlignmentType, DistributionAxis } from '../../../lib/canvas/AlignmentEngine';

// ============================================================================
// Main Canvas Component
// ============================================================================

function UnifiedCanvasInner() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const reactFlowInstance = useReactFlow();
  const { currentMode, setMode } = useCanvasMode();

  // Canvas header configuration atoms
  const setHeaderActionContext = useSetAtom(headerActionContextAtom);
  const setHeaderContextActions = useSetAtom(headerContextActionsAtom);
  const setHeaderCanvasMode = useSetAtom(headerCanvasModeAtom);
  const setHeaderShowCanvasMode = useSetAtom(headerShowCanvasModeAtom);
  const setHeaderOnCanvasModeChange = useSetAtom(headerOnCanvasModeChangeAtom);
  const setHeaderPhaseInfo = useSetAtom(headerPhaseInfoAtom);
  const setHeaderRoleInfo = useSetAtom(headerRoleInfoAtom);

  // Workspace context
  const { currentWorkspace, ownedWorkspaces, ownedProjects, includedProjects } =
    useWorkspaceContext();

  // Fetch project data
  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) return null;
      return response.json();
    },
    enabled: !!projectId,
  });

  // Canvas mode mapping
  const mapToNavCanvasMode = (mode: CanvasMode): NavCanvasMode => {
    switch (mode) {
      case 'brainstorm':
      case 'diagram':
      case 'design':
        return 'design';
      case 'code':
        return 'code';
      case 'test':
      case 'observe':
        return 'architecture';
      case 'deploy':
        return 'deploy';
      default:
        return 'design';
    }
  };

  const handleNavCanvasModeChange = (navMode: NavCanvasMode) => {
    const modeMap: Record<NavCanvasMode, CanvasMode> = {
      design: 'design',
      architecture: 'diagram',
      code: 'code',
      deploy: 'deploy',
    };
    setMode(modeMap[navMode]);
  };

  // Feature hooks
  const { currentPhase } = useAIStatusBar();
  const lifecycleZones = useLifecycleZones(1200, 800, [
    'INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'BUILD', 'RUN', 'IMPROVE',
  ]);
  const { isVisible: codePanelVisible, handleToggle: toggleCodePanel } = useInlineCodePanel();
  const { isStudioMode, toggleStudioMode } = useStudioMode();
  const { isHelpOpen, closeHelp } = useKeyboardShortcuts();

  useCanvasTelemetry();
  useCanvasCommands();

  // =========================================================================
  // CANVAS STATE
  // =========================================================================

  const canvas = useUnifiedCanvas(projectId || '');

  // Sync zoom to chrome atom
  const [, setZoomLevel] = useAtom(chromeZoomLevelAtom);
  useEffect(() => {
    if (canvas.viewport?.zoom !== undefined) {
      setZoomLevel(canvas.viewport.zoom);
    }
  }, [canvas.viewport?.zoom, setZoomLevel]);

  // Expose ReactFlow instance globally
  useEffect(() => {
    (window as unknown).__reactFlowInstance = reactFlowInstance;
    return () => { delete (window as unknown).__reactFlowInstance; };
  }, [reactFlowInstance]);

  // Listen for YAPPC add-node events
  useEffect(() => {
    const handleAddNode = (event: CustomEvent<YAPPCCanvasNode>) => {
      canvas.addNode(event.detail);
    };
    window.addEventListener('yappc:add-node', handleAddNode);
    return () => window.removeEventListener('yappc:add-node', handleAddNode);
  }, [canvas]);

  const canvasRef = useRef<HTMLDivElement>(null);
  const drawingCanvasRef = useRef<HTMLCanvasElement>(null);

  // =========================================================================
  // UI STATE
  // =========================================================================

  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const [nodeContextMenu, setNodeContextMenu] = useState<NodeContextMenuState | null>(null);
  const [addMenuAnchor, setAddMenuAnchor] = useState<HTMLElement | null>(null);
  const [exportMenuAnchor, setExportMenuAnchor] = useState<HTMLElement | null>(null);
  const [alignMenuAnchor, setAlignMenuAnchor] = useState<HTMLElement | null>(null);
  const [layerMenuAnchor, setLayerMenuAnchor] = useState<HTMLElement | null>(null);
  const [propertiesPanelOpen, setPropertiesPanelOpen] = useState(false);
  const [copiedNodeIds, setCopiedNodeIds] = useState<string[]>([]);
  const [copiedNodes, setCopiedNodes] = useState<unknown[]>([]);
  const [toastOpen, setToastOpen] = useState(false);
  const [toastMessage, setToastMessage] = useState('');
  const [toastSeverity, setToastSeverity] = useState<'success' | 'info' | 'warning' | 'error'>('info');
  const [shortcutLegendOpen, setShortcutLegendOpen] = useState(false);

  const showToast = useCallback(
    (message: string, severity: 'success' | 'info' | 'warning' | 'error' = 'info') => {
      setToastMessage(message);
      setToastSeverity(severity);
      setToastOpen(true);
    },
    []
  );

  // =========================================================================
  // CHROME STATE (Calm UI)
  // =========================================================================

  const [calmMode, setCalmMode] = useAtom(chromeCalmModeAtom);
  const [leftRailVisible, setLeftRailVisible] = useAtom(chromeLeftRailVisibleAtom);
  const [,] = useAtom(chromeInspectorVisibleAtom);
  const [minimapVisible, setMinimapVisible] = useAtom(chromeMinimapVisibleAtom);
  const leftRailAutoOpenedRef = useRef(false);
  const [showShortcutHint, setShowShortcutHint] = useState(true);
  const [initialized, setInitialized] = useState(false);
  const shouldDefaultToCalmMode = true;

  // =========================================================================
  // DERIVED STATE
  // =========================================================================

  const selectedNode = useMemo(() => {
    if (canvas.selectedNodeIds.length === 1) {
      return canvas.nodes.find((n) => n.id === canvas.selectedNodeIds[0]);
    }
    return null;
  }, [canvas.selectedNodeIds, canvas.nodes]);

  const hasSelection = canvas.selectedNodeIds.length > 0;
  const hasMultipleSelection = canvas.selectedNodeIds.length > 1;

  // =========================================================================
  // LIFECYCLE
  // =========================================================================

  useEffect(() => {
    if (!initialized) {
      setInitialized(true);
      if (shouldDefaultToCalmMode) {
        setCalmMode(true);
        setMinimapVisible(false);
        setLeftRailVisible(false);
      }
    }
  }, [initialized, setCalmMode, setMinimapVisible, setLeftRailVisible, shouldDefaultToCalmMode]);

  useEffect(() => {
    if (!calmMode || !showShortcutHint) return;
    const timer = window.setTimeout(() => setShowShortcutHint(false), 6000);
    return () => window.clearTimeout(timer);
  }, [calmMode, showShortcutHint]);

  useEffect(() => {
    if (canvas.nodes.length > 0 && showShortcutHint) {
      setShowShortcutHint(false);
    }
  }, [canvas.nodes.length, showShortcutHint]);

  useEffect(() => {
    if (calmMode && !leftRailVisible && !leftRailAutoOpenedRef.current && canvas.nodes.length > 0) {
      setLeftRailVisible(true);
      leftRailAutoOpenedRef.current = true;
    }
  }, [calmMode, leftRailVisible, canvas.nodes.length, setLeftRailVisible]);

  // =========================================================================
  // NODE CREATION
  // =========================================================================

  const addNodeAtPosition = useCallback(
    (type: string, position: { x: number; y: number }) => {
      const nodeDefaults: Record<string, unknown> = {
        'sticky-note': { type: 'sticky-note', data: { text: 'New note', color: '#fef3c7' } },
        text: { type: 'text', data: { text: 'Enter text here' } },
        frame: { type: 'frame', data: { title: 'New Frame', width: 400, height: 300 } },
        task: { type: 'component', data: { label: 'New Task', status: 'todo' } },
        image: { type: 'image', data: { url: '', alt: 'Image' } },
        rectangle: { type: 'rectangle', data: { shape: 'rectangle', color: '#e3f2fd', label: 'Rectangle' } },
        circle: { type: 'circle', data: { shape: 'circle', color: '#fff3e0', label: 'Circle' } },
        diamond: { type: 'diamond', data: { shape: 'diamond', color: '#f3e5f5', label: 'Diamond' } },
        code: { type: 'code', data: { label: 'Code Block', code: '// code here', language: 'typescript' } },
      };

      const nodeData = nodeDefaults[type] || { type: 'component', data: { label: 'New Node' } };
      canvas.addNode({ ...nodeData, position, id: `node-${Date.now()}` });
      setContextMenu(null);
    },
    [canvas]
  );

  // =========================================================================
  // DRAWING (extracted hook)
  // =========================================================================

  const drawing = useCanvasDrawing({ canvas, canvasRef, drawingCanvasRef });

  // =========================================================================
  // EXPORT (extracted hook)
  // =========================================================================

  const { handleExportJSON, handleExportSVG, handleExportPNG, handleImportJSON } =
    useCanvasExport({ canvas, projectId, canvasRef, setExportMenuAnchor: () => setExportMenuAnchor(null) });

  // =========================================================================
  // KEYBOARD SHORTCUTS (extracted hook)
  // =========================================================================

  useCanvasKeyboardShortcuts({
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
    setDrawingTool: drawing.setDrawingTool,
    setContextMenu: () => setContextMenu(null),
    setNodeContextMenu: () => setNodeContextMenu(null),
    setAddMenuAnchor: () => setAddMenuAnchor(null),
    setExportMenuAnchor: () => setExportMenuAnchor(null),
    setAlignMenuAnchor: () => setAlignMenuAnchor(null),
    setLayerMenuAnchor: () => setLayerMenuAnchor(null),
    setShortcutLegendOpen,
    showToast,
    addNodeAtPosition,
  });

  // =========================================================================
  // ROLE INFO (extracted hook)
  // =========================================================================

  const { roleInfo, phaseInfo, roleMap } = useCanvasRoleInfo(currentMode, project);

  // =========================================================================
  // REACTFLOW CONVERSION
  // =========================================================================

  const reactFlowNodes = useMemo(() => {
    return canvas.nodes.map((node) => ({
      id: node.id,
      type: node.type,
      position: node.position,
      data: {
        ...node.data,
        onLabelChange: (label: string) => {
          canvas.updateNode(node.id, { data: { ...node.data, label } });
        },
        onTextChange: (text: string) => {
          canvas.updateNode(node.id, { data: { ...node.data, text } });
        },
        onStatusChange: (completed: boolean) => {
          canvas.updateNode(node.id, { data: { ...node.data, completed } });
        },
      },
      selected: canvas.selectedNodeIds.includes(node.id),
      draggable: true,
      deletable: true,
      style: getNodeSize(node.type),
    }));
  }, [canvas.nodes, canvas.selectedNodeIds, canvas]);

  const reactFlowEdges = useMemo(
    () => canvas.connections.map((conn) => ({ id: conn.id, source: conn.source, target: conn.target, animated: conn.animated })),
    [canvas.connections]
  );

  // =========================================================================
  // REACTFLOW HANDLERS
  // =========================================================================

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      changes.forEach((change) => {
        if (change.type === 'position' && 'position' in change && change.position) {
          canvas.updateNode(change.id, { position: change.position });
        } else if (change.type === 'select' && 'selected' in change) {
          if (change.selected) {
            canvas.selectNodes([...canvas.selectedNodeIds, change.id]);
          } else {
            canvas.selectNodes(canvas.selectedNodeIds.filter((id) => id !== change.id));
          }
        } else if (change.type === 'remove') {
          canvas.removeNode(change.id);
        }
      });
    },
    [canvas]
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      changes.forEach((change) => {
        if (change.type === 'remove') canvas.removeConnection(change.id);
      });
    },
    [canvas]
  );

  const onConnect = useCallback(
    (connection: RFConnection) => {
      if (connection.source && connection.target) {
        canvas.createConnection(connection.source, connection.target);
      }
    },
    [canvas]
  );

  // =========================================================================
  // EVENT HANDLERS
  // =========================================================================

  const handleCanvasRightClick = useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    const bounds = canvasRef.current?.getBoundingClientRect();
    if (bounds) {
      setNodeContextMenu(null);
      setContextMenu({ x: event.clientX - bounds.left, y: event.clientY - bounds.top });
    }
  }, []);

  const handleNodeRightClick = useCallback(
    (event: React.MouseEvent, nodeId: string) => {
      event.preventDefault();
      event.stopPropagation();
      const bounds = canvasRef.current?.getBoundingClientRect();
      if (bounds) {
        setContextMenu(null);
        setNodeContextMenu({ x: event.clientX - bounds.left, y: event.clientY - bounds.top, nodeId });
        if (!canvas.selectedNodeIds.includes(nodeId)) canvas.selectNodes([nodeId]);
      }
    },
    [canvas]
  );

  const handleCanvasClick = useCallback(
    (event: React.MouseEvent) => {
      setContextMenu(null);
      setNodeContextMenu(null);
      setShowShortcutHint(false);

      const creationTools = ['rectangle', 'ellipse', 'diamond', 'text', 'sticky', 'frame', 'image', 'code', 'circle'];
      if (creationTools.includes(canvas.activeTool)) {
        const position = reactFlowInstance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
        let finalType = canvas.activeTool;
        if (finalType === 'sticky') finalType = 'sticky-note';
        if (finalType === 'ellipse') finalType = 'circle';
        addNodeAtPosition(finalType, position);
      }
    },
    [canvas.activeTool, reactFlowInstance, addNodeAtPosition, canvas]
  );

  const handleNodeClick = useCallback((_event: React.MouseEvent, _node: unknown) => {
    setShowShortcutHint(false);
  }, []);

  const handleNodeContextMenu = useCallback((event: React.MouseEvent, node: unknown) => {
    event.preventDefault();
    event.stopPropagation();
    setNodeContextMenu({ x: event.clientX, y: event.clientY, nodeId: node.id });
  }, []);

  const handleAlign = useCallback(
    (alignment: AlignmentType) => {
      if (canvas.selectedNodeIds.length >= 2) canvas.alignNodes(alignment);
      setAlignMenuAnchor(null);
    },
    [canvas]
  );

  const handleDistribute = useCallback(
    (axis: DistributionAxis) => {
      if (canvas.selectedNodeIds.length >= 3) canvas.distributeNodes(axis);
      setAlignMenuAnchor(null);
    },
    [canvas]
  );

  // =========================================================================
  // PANELS
  // =========================================================================

  const leftRailContent = (
    <UnifiedLeftRail
      context={{ mode: currentMode as unknown, role: roleInfo?.label, phase: phaseInfo?.label }}
      nodes={canvas.nodes}
      selectedNodeIds={canvas.selectedNodeIds}
      onInsertNode={(nodeData, position) => {
        let pos = position;
        if (!pos) {
          const center = reactFlowInstance.getViewport();
          pos = { x: -center.x / center.zoom + 100, y: -center.y / center.zoom + 100 };
        }
        addNodeAtPosition(nodeData.type || 'rectangle', pos);
      }}
      onSelectNode={(nodeId) => canvas.selectNodes([nodeId])}
      onUpdateNode={(nodeId, updates) => canvas.updateNodeData(nodeId, updates)}
      onDeleteNode={(nodeId) => canvas.deleteNode(nodeId)}
      onToggleVisibility={(nodeId) => {
        const node = canvas.nodes.find((n) => n.id === nodeId);
        if (node) canvas.updateNodeData(nodeId, { hidden: !node.data.hidden });
      }}
      onToggleLock={(nodeId) => {
        const node = canvas.nodes.find((n) => n.id === nodeId);
        if (node) canvas.updateNodeData(nodeId, { locked: !node.data.locked });
      }}
    />
  );

  const inspectorPanel = (
    <UnifiedRightPanel
      selectedNodeIds={canvas.selectedNodeIds}
      nodes={canvas.nodes}
      onUpdateNode={(id, data) => canvas.updateNodeData(id, data)}
    />
  );

  const outlinePanel = (
    <CanvasOutlinePanel
      nodes={canvas.nodes}
      selectedNodeIds={canvas.selectedNodeIds}
      selectNodes={canvas.selectNodes}
      addNodeAtPosition={addNodeAtPosition}
      getViewport={() => reactFlowInstance.getViewport()}
    />
  );

  // =========================================================================
  // CONTEXT ACTIONS & HEADER SYNC
  // =========================================================================

  const contextActions = useMemo(
    () => [
      { id: 'undo', label: 'Undo', icon: Undo, onClick: () => canvas.undo(), disabled: !canvas.canUndo, tooltip: 'Undo', shortcut: '⌘Z' },
      { id: 'redo', label: 'Redo', icon: Redo, onClick: () => canvas.redo(), disabled: !canvas.canRedo, tooltip: 'Redo', shortcut: '⌘⇧Z' },
      { id: 'share', label: 'Share', icon: Share, onClick: () => console.log('Share project'), tooltip: 'Share with others' },
      { id: 'zoom-in', label: 'Zoom In', icon: ZoomIn, onClick: () => reactFlowInstance.zoomIn(), tooltip: 'Zoom in', shortcut: '⌘+' },
      { id: 'zoom-out', label: 'Zoom Out', icon: ZoomOut, onClick: () => reactFlowInstance.zoomOut(), tooltip: 'Zoom out', shortcut: '⌘-' },
      { id: 'settings', label: 'Settings', icon: Settings, onClick: () => navigate(`/app/p/${projectId}/settings`), tooltip: 'Project settings' },
      { id: 'export', label: 'Export', icon: FileDownload, onClick: () => setExportMenuAnchor(document.getElementById('root')), tooltip: 'Export canvas', divider: true },
    ],
    [canvas, reactFlowInstance, projectId, navigate]
  );

  useEffect(() => {
    setHeaderActionContext('canvas');
    setHeaderContextActions(contextActions);
    setHeaderCanvasMode(mapToNavCanvasMode(currentMode));
    setHeaderShowCanvasMode(true);
    setHeaderOnCanvasModeChange(() => handleNavCanvasModeChange);

    if (roleInfo) {
      setHeaderRoleInfo({ role: currentMode, ...roleInfo });
    }

    if (project?.currentPhase) {
      setHeaderPhaseInfo({
        phase: project.currentPhase,
        label: project.currentPhase.charAt(0).toUpperCase() + project.currentPhase.slice(1),
        progress: project.phaseProgress || 0,
        status: 'active',
      });
    }

    return () => {
      setHeaderActionContext('project');
      setHeaderContextActions([]);
      setHeaderShowCanvasMode(false);
      setHeaderPhaseInfo(undefined);
      setHeaderRoleInfo(undefined);
    };
  }, [
    currentMode, contextActions, project, roleInfo,
    setHeaderActionContext, setHeaderContextActions, setHeaderCanvasMode,
    setHeaderShowCanvasMode, setHeaderOnCanvasModeChange, setHeaderPhaseInfo, setHeaderRoleInfo,
  ]);

  // Phase theme
  const phaseTheme = getPhaseTheme(currentPhase as LifecyclePhase);

  // =========================================================================
  // RENDER
  // =========================================================================

  return (
    <CanvasErrorBoundary>
      <Box
        className="w-full h-full flex flex-col" style={{ backgroundColor: 'phaseTheme.canvasBg', transition: 'background-color 0.5s ease-in-out' }} >
        <CanvasChromeLayout
          defaultCalmMode={shouldDefaultToCalmMode}
          leftRail={leftRailContent}
          outline={outlinePanel}
          inspector={inspectorPanel}
          contextBar={null}
          topBar={null}
          showTopBar={false}
        >
          <Box
            className="w-full h-full flex flex-col bg-transparent"
          >
            {/* Main Canvas Area */}
            <Box className="flex-1 relative">
              <ReactFlow
                ref={canvasRef}
                nodes={canvas.nodes}
                edges={canvas.connections}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={handleNodeClick}
                onNodeContextMenu={handleNodeContextMenu}
                onPaneClick={handleCanvasClick}
                onPaneContextMenu={handleCanvasRightClick}
                onPointerDown={drawing.handlePointerDown}
                onPointerMove={drawing.handlePointerMove}
                onPointerUp={drawing.handlePointerUp}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                fitView
                attributionPosition="bottom-left"
                panOnDrag={canvas.activeTool === 'pan'}
                selectionOnDrag={canvas.activeTool === 'select'}
                panOnScroll
                zoomOnScroll
              >
                <Background />
                <Box
                  className="transition-all duration-300"
                  style={{
                    opacity: calmMode ? 0 : 1,
                    transform: calmMode ? 'scale(0.98)' : 'scale(1)',
                    pointerEvents: calmMode ? 'none' : 'auto',
                    transformOrigin: 'bottom left',
                  }}
                >
                  <MiniMap />
                </Box>
                <Box
                  className="transition-all duration-300" style={{ opacity: calmMode ? 0 : 1, transform: calmMode ? 'scale(0.98)' : 'scale(1)', pointerEvents: calmMode ? 'none' : 'auto', transformOrigin: 'bottom left' }} >
                  <Controls />
                </Box>
              </ReactFlow>

              {/* Shortcut hint */}
              <Box
                className="absolute left-[16px] bottom-[16px] px-3 py-1.5 bg-white dark:bg-gray-900 border border-solid border-gray-200 dark:border-gray-700 rounded-md text-xs text-gray-500 dark:text-gray-400 shadow-sm transition-all duration-300" style={{ opacity: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 1 : 0, transform: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 'translateY(0)' : 'translateY(6px)', pointerEvents: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 'auto' : 'none' }}
              >
                Press <strong>?</strong> for shortcuts
              </Box>

              {/* Floating Toolbar */}
              {(() => {
                const shouldShowToolbar = !calmMode || hasSelection || (canvas.activeTool && canvas.activeTool !== 'select');
                return (
                  <Box
                    className="absolute bottom-[24px] w-full flex justify-center z-[1000] pointer-events-none transition-all duration-300" style={{ opacity: shouldShowToolbar ? 1 : 0, transform: shouldShowToolbar ? 'translateY(0)' : 'translateY(8px)' }}
                  >
                    <DraggableBox className="pointer-events-auto w-fit">
                      <UnifiedToolbar
                        variant="floating"
                        activeTool={canvas.activeTool || 'select'}
                        onToolChange={canvas.setActiveTool}
                        onUndo={canvas.undo}
                        onRedo={canvas.redo}
                        canUndo={canvas.canUndo}
                        canRedo={canvas.canRedo}
                        zoomLevel={canvas.viewport?.zoom || 1}
                        onZoomIn={() => reactFlowInstance.zoomIn()}
                        onZoomOut={() => reactFlowInstance.zoomOut()}
                        onResetZoom={() => reactFlowInstance.fitView()}
                        hasSelection={hasSelection}
                      />
                    </DraggableBox>
                  </Box>
                );
              })()}

              {/* Drawing Canvas Overlay */}
              <canvas
                ref={drawingCanvasRef}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  height: '100%',
                  pointerEvents: 'none',
                  zIndex: 1000,
                }}
              />
            </Box>

            {/* Status Bar */}
            <CanvasStatusBar
              calmMode={calmMode}
              activeTool={canvas.activeTool}
              drawingTool={drawing.drawingTool}
              drawingColor={drawing.drawingColor}
              hasSelection={hasSelection}
              selectedCount={canvas.selectedNodeIds.length}
              currentPhase={currentPhase}
              zoom={canvas.viewport.zoom}
            />
          </Box>

          {/* Node Context Menu */}
          <CanvasNodeContextMenu
            nodeContextMenu={nodeContextMenu}
            onClose={() => setNodeContextMenu(null)}
            canvas={canvas}
            currentMode={currentMode}
            codePanelVisible={codePanelVisible}
            toggleCodePanel={toggleCodePanel}
          />

          {/* Toast Notifications */}
          <Snackbar
            open={toastOpen}
            autoHideDuration={2000}
            onClose={() => setToastOpen(false)}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
          >
            <Alert severity={toastSeverity} className="w-full">
              {toastMessage}
            </Alert>
          </Snackbar>

          {/* Keyboard Shortcut Legend */}
          <KeyboardShortcutLegend
            open={shortcutLegendOpen}
            onClose={() => setShortcutLegendOpen(false)}
          />
        </CanvasChromeLayout>
      </Box>
    </CanvasErrorBoundary>
  );
}

// Wrapper component with ReactFlowProvider
function UnifiedCanvas() {
  return (
    <ReactFlowProvider>
      <UnifiedCanvasInner />
    </ReactFlowProvider>
  );
}

export default UnifiedCanvas;
