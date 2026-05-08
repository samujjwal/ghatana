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
import { getNodeSize } from './_canvas/CanvasCollaborationBanner';
import { useParams, useNavigate } from 'react-router';
import { Alert, Box, Snackbar } from '@ghatana/design-system';
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Connection as RFConnection,
  type Edge,
  type EdgeChange,
  type EdgeTypes,
  type Node,
  type NodeChange,
  type NodeTypes,
} from '@xyflow/react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery } from '@tanstack/react-query';
import {
  Download as FileDownload,
  Redo2 as Redo,
  Settings,
  Share2 as Share,
  Undo2 as Undo,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';

import {
  CanvasChromeLayout,
  useCanvasCommands,
  useCanvasTelemetry,
  // Canonical chrome atoms — use these so CanvasChromeLayout stays in sync
  chromeCalmModeAtom,
  chromeInspectorVisibleAtom,
  chromeLeftRailVisibleAtom,
  chromeMinimapVisibleAtom,
  chromeZoomLevelAtom,
} from '@ghatana/canvas';
import { headerActionContextAtom, headerCanvasModeAtom, headerContextActionsAtom, headerOnCanvasModeChangeAtom, headerPhaseInfoAtom, headerRoleInfoAtom, headerShowCanvasModeAtom, type HeaderAction } from '../../../state/atoms/layoutAtom';
import '@xyflow/react/dist/style.css';
import { DependencyEdge } from '../../../components/canvas/edges';
import { KeyboardShortcutLegend } from '../../../components/canvas/KeyboardShortcutLegend';
import { useInlineCodePanel } from '../../../components/canvas/InlineCodePanel';
import { nodeTypes } from '../../../components/canvas/nodeTypes';
import { useLifecycleZones } from '../../../components/canvas/ZoomableLifecycleZones';
import { CanvasErrorBoundary } from '../../../components/canvas/unified/CanvasErrorBoundary';
import { UnifiedLeftRail } from '../../../components/canvas/unified/UnifiedLeftRail';
import { UnifiedRightPanel } from '../../../components/canvas/unified/UnifiedRightPanel';
import { UnifiedToolbar } from '../../../components/canvas/unified/UnifiedToolbar';
import { createPageArtifactDocument } from '../../../components/canvas/page/pageArtifactDocument';
import { type CanvasMode as NavCanvasMode } from '../../../components/navigation';
import { useKeyboardShortcuts } from '../../../components/keyboard/KeyboardShortcutsManager';
import { useStudioMode } from '../../../components/studio/StudioLayout';
import { useAIStatusBar } from '../../../components/ai/AIStatusBar';
import { deriveCanvasAccessPolicy, normalizeCanvasPolicyPhase } from '../../../components/canvas/canvasAccessPolicy';
import { useCanvasMode } from '../../../hooks/useCanvasMode';
import { useAuth } from '../../../hooks/useAuth';
import { useUnifiedCanvas } from '../../../hooks/useUnifiedCanvas';
import { useWorkspaceContext } from '../../../hooks/useWorkspaceData';
import { yappcApi } from '@/lib/api/client';
import type { AlignmentType, DistributionAxis } from '../../../lib/canvas/AlignmentEngine';
import { getPhaseTheme, type LifecyclePhase } from '../../../theme/phaseTheme';
import type { CanvasMode } from '../../../types/canvasMode';
import { LifecyclePhase as RailLifecyclePhase } from '../../../types/lifecycle';
import {
  CanvasCollaborationBanner,
  CanvasNodeContextMenu,
  CanvasOutlinePanel,
  CanvasStatusBar,
  type CanvasImportAuditEvent,
  type CanvasSyncStatus,
  DraggableBox,
  type NodeContextMenuState,
  useCanvasDrawing,
  useCanvasExport,
  useCanvasKeyboardShortcuts,
  useCanvasRoleInfo,
} from './_canvas';
import { LegacyRouteCompatibilityNotice } from './LegacyRouteCompatibilityNotice';

const edgeTypes = {
  dependency: DependencyEdge,
  flow: DependencyEdge,
} as unknown as EdgeTypes;

interface CanvasProjectData {
  currentPhase: string;
  phaseProgress?: number;
}

type KeyboardShortcutCanvas = Parameters<typeof useCanvasKeyboardShortcuts>[0]['canvas'];
type KeyboardShortcutNode = Parameters<typeof useCanvasKeyboardShortcuts>[0]['copiedNodes'][number];

function UnifiedCanvasInner() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const reactFlowInstance = useReactFlow();
  const { currentMode, setMode } = useCanvasMode();
  const { currentUser } = useAuth();

  // Canvas header configuration atoms
  const setHeaderActionContext = useSetAtom(headerActionContextAtom);
  const setHeaderContextActions = useSetAtom(headerContextActionsAtom);
  const setHeaderCanvasMode = useSetAtom(headerCanvasModeAtom);
  const setHeaderShowCanvasMode = useSetAtom(headerShowCanvasModeAtom);
  const setHeaderOnCanvasModeChange = useSetAtom(headerOnCanvasModeChangeAtom);
  const setHeaderPhaseInfo = useSetAtom(headerPhaseInfoAtom);
  const setHeaderRoleInfo = useSetAtom(headerRoleInfoAtom);

  // Workspace context
  const { currentWorkspaceId, ownedProjects, includedProjects } = useWorkspaceContext();

  // Fetch project data
  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: async () => {
      if (!projectId) return null;
      return yappcApi.projects.get(projectId) as Promise<CanvasProjectData>;
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
  const projectAccess = useMemo(
    () => [...ownedProjects, ...includedProjects].find((candidate) => candidate.id === projectId),
    [includedProjects, ownedProjects, projectId]
  );
  const canvasPolicyPhase = useMemo(
    () => normalizeCanvasPolicyPhase(project?.currentPhase ?? currentPhase),
    [currentPhase, project?.currentPhase]
  );
  const canvasPolicy = useMemo(
    () => deriveCanvasAccessPolicy(canvasPolicyPhase, projectAccess),
    [canvasPolicyPhase, projectAccess]
  );
  const canMutateCanvas = canvasPolicy.canMutateArtifacts;
  const lifecycleZones = useLifecycleZones(1200, 800, [
    'INTENT', 'SHAPE', 'VALIDATE', 'GENERATE', 'BUILD', 'RUN', 'IMPROVE',
  ]);
  const { isVisible: codePanelVisible, handleToggle: toggleCodePanel } = useInlineCodePanel();
  const { isStudioMode, toggleStudioMode } = useStudioMode();
  const { isHelpOpen, closeHelp } = useKeyboardShortcuts();
  const e2ePageDesignerSeededRef = useRef(false);

  useCanvasTelemetry();
  useCanvasCommands();

  // =========================================================================
  // CANVAS STATE
  // =========================================================================

  const canvas = useUnifiedCanvas(projectId || '');

  useEffect(() => {
    if (
      !import.meta.env.DEV ||
      !projectId ||
      e2ePageDesignerSeededRef.current ||
      window.localStorage.getItem('yappc:e2e:seed-page-designer') !== projectId
    ) {
      return;
    }

    e2ePageDesignerSeededRef.current = true;
    if (canvas.nodes.some((node) => node.type === 'page-designer')) {
      return;
    }

    const pageDocument = createPageArtifactDocument({
      artifactId: `artifact-${projectId}-page-designer-e2e`,
      name: 'E2E Page Designer',
      createdBy: 'page-designer-e2e',
    });

    canvas.addNode({
      id: `page-designer-${projectId}-e2e`,
      type: 'page-designer',
      position: { x: 80, y: 80 },
      size: { width: 1240, height: 720 },
      data: {
        label: 'E2E Page Designer',
        expanded: true,
        pageDocument: {
          ...pageDocument,
          syncStatus: 'synced',
        },
      },
    });
  }, [canvas, projectId]);

  // Sync zoom to chrome atom
  const [, setZoomLevel] = useAtom(chromeZoomLevelAtom);
  useEffect(() => {
    if (canvas.viewport?.zoom !== undefined) {
      setZoomLevel(canvas.viewport.zoom);
    }
  }, [canvas.viewport?.zoom, setZoomLevel]);



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
  const [copiedNodes, setCopiedNodes] = useState<KeyboardShortcutNode[]>([]);
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
  const warnReadOnly = useCallback(() => {
    showToast(canvasPolicy.readOnlyReason ?? 'Canvas edits are unavailable in this mode.', 'warning');
  }, [canvasPolicy.readOnlyReason, showToast]);
  const recordCanvasImportAudit = useCallback(
    async (event: CanvasImportAuditEvent): Promise<void> => {
      if (!currentUser?.id || !projectId || !currentWorkspaceId) {
        return;
      }

      await yappcApi.audit.emit({
        type: event.outcome === 'success' ? 'CANVAS_IMPORT_COMPLETED' : 'CANVAS_IMPORT_FAILED',
        userId: currentUser.id,
        projectId,
        flowStage: 'BUILD',
        phase: canvasPolicyPhase,
        description: event.message,
        metadata: {
          workspaceId: currentWorkspaceId,
          sourceName: event.sourceName,
          outcome: event.outcome,
          nodeCount: event.nodeCount,
          connectionCount: event.connectionCount,
          drawingCount: event.drawingCount,
          migratedFromVersion: event.migratedFromVersion,
          failureReason: event.failureReason,
        },
      });
    },
    [canvasPolicyPhase, currentUser?.id, currentWorkspaceId, projectId]
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
  const canvasSyncStatus: CanvasSyncStatus = 'local-only';

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

  useEffect(() => {
    if (!canMutateCanvas && canvas.activeTool !== 'select' && canvas.activeTool !== 'pan') {
      canvas.setActiveTool('select');
    }
  }, [canMutateCanvas, canvas]);

  // =========================================================================
  // NODE CREATION
  // =========================================================================

  const addNodeAtPosition = useCallback(
    (type: string, position: { x: number; y: number }) => {
      if (!canvasPolicy.canCreateArtifacts) {
        warnReadOnly();
        return;
      }
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
    [canvas, canvasPolicy.canCreateArtifacts, warnReadOnly]
  );

  // =========================================================================
  // DRAWING (extracted hook)
  // =========================================================================

  const drawing = useCanvasDrawing({
    canvas: canvas as Parameters<typeof useCanvasDrawing>[0]['canvas'],
    canvasRef,
    drawingCanvasRef,
  });

  // =========================================================================
  // EXPORT (extracted hook)
  // =========================================================================

  const { handleExportJSON, handleExportSVG, handleExportPNG, handleImportJSON } =
    useCanvasExport({
      canvas: canvas as Parameters<typeof useCanvasExport>[0]['canvas'],
      projectId,
      canvasRef,
      setExportMenuAnchor: () => setExportMenuAnchor(null),
      showFeedback: showToast,
      recordImportAudit: recordCanvasImportAudit,
    });

  // =========================================================================
  // KEYBOARD SHORTCUTS (extracted hook)
  // =========================================================================

  useCanvasKeyboardShortcuts({
    canvas: canvas as unknown as KeyboardShortcutCanvas,
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
    canMutateCanvas,
    readOnlyReason: canvasPolicy.readOnlyReason,
  });

  // =========================================================================
  // ROLE INFO (extracted hook)
  // =========================================================================

  const { roleInfo, phaseInfo, roleMap } = useCanvasRoleInfo(currentMode, project);

  // =========================================================================
  // REACTFLOW CONVERSION
  // =========================================================================

  const reactFlowNodes = useMemo<Node[]>(() => {
    return canvas.nodes.map((node) => ({
      id: node.id,
      type: node.type,
      position: node.position,
      data: {
        ...node.data,
        readOnly: !canMutateCanvas,
        readOnlyReason: canvasPolicy.readOnlyReason,
        onLabelChange: (label: string) => {
          if (!canMutateCanvas) {
            warnReadOnly();
            return;
          }
          canvas.updateNode(node.id, { data: { ...node.data, label } });
        },
        onTextChange: (text: string) => {
          if (!canMutateCanvas) {
            warnReadOnly();
            return;
          }
          canvas.updateNode(node.id, { data: { ...node.data, text } });
        },
        onStatusChange: (completed: boolean) => {
          if (!canMutateCanvas) {
            warnReadOnly();
            return;
          }
          canvas.updateNode(node.id, { data: { ...node.data, completed } });
        },
        onDataChange: (updates: Record<string, unknown>) => {
          if (!canMutateCanvas) {
            warnReadOnly();
            return;
          }
          canvas.updateNode(node.id, { data: { ...node.data, ...updates } });
        },
      },
      selected: canvas.selectedNodeIds.includes(node.id),
      draggable: canMutateCanvas,
      deletable: canMutateCanvas,
      style: getNodeSize(node.type),
    })) as Node[];
  }, [canMutateCanvas, canvas.nodes, canvas.selectedNodeIds, canvas, warnReadOnly]);

  const reactFlowEdges = useMemo<Edge[]>(() => {
    return canvas.connections.map((connection) => ({
      id: connection.id,
      source: connection.source,
      target: connection.target,
      type: 'dependency',
      animated: Boolean((connection as unknown as Record<string, unknown>).animated),
    })) as Edge[];
  }, [canvas.connections]);

  // =========================================================================
  // REACTFLOW HANDLERS
  // =========================================================================

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      changes.forEach((change) => {
        if (change.type === 'position' && 'position' in change && change.position) {
          if (!canMutateCanvas) return;
          canvas.updateNode(change.id, { position: change.position });
        } else if (change.type === 'select' && 'selected' in change) {
          if (change.selected) {
            canvas.selectNodes([...canvas.selectedNodeIds, change.id]);
          } else {
            canvas.selectNodes(canvas.selectedNodeIds.filter((id) => id !== change.id));
          }
        } else if (change.type === 'remove') {
          if (!canMutateCanvas) return;
          canvas.removeNode(change.id);
        }
      });
    },
    [canMutateCanvas, canvas]
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      changes.forEach((change) => {
        if (change.type === 'remove' && canMutateCanvas) canvas.removeConnection(change.id);
      });
    },
    [canMutateCanvas, canvas]
  );

  const onConnect = useCallback(
    (connection: RFConnection) => {
      if (!canMutateCanvas) {
        warnReadOnly();
        return;
      }
      if (connection.source && connection.target) {
        canvas.createConnection(connection.source, connection.target);
      }
    },
    [canMutateCanvas, canvas, warnReadOnly]
  );

  // =========================================================================
  // EVENT HANDLERS
  // =========================================================================

  const handleCanvasRightClick = useCallback((event: MouseEvent | React.MouseEvent<Element>) => {
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
        if (!canvasPolicy.canCreateArtifacts) {
          warnReadOnly();
          return;
        }
        const position = reactFlowInstance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
        let finalType: string = canvas.activeTool;
        if (finalType === 'sticky') finalType = 'sticky-note';
        if (finalType === 'ellipse') finalType = 'circle';
        addNodeAtPosition(finalType, position);
      }
    },
    [canvas.activeTool, canvasPolicy.canCreateArtifacts, reactFlowInstance, addNodeAtPosition, warnReadOnly, canvas]
  );

  const handleNodeClick = useCallback((_event: MouseEvent | React.MouseEvent<Element>, _node: Node) => {
    setShowShortcutHint(false);
  }, []);

  const handleNodeContextMenu = useCallback((event: MouseEvent | React.MouseEvent<Element>, node: { id: string }) => {
    event.preventDefault();
    event.stopPropagation();
    setNodeContextMenu({ x: event.clientX, y: event.clientY, nodeId: node.id });
  }, []);

  const handleAlign = useCallback(
    (alignment: AlignmentType) => {
      if (!canMutateCanvas) {
        warnReadOnly();
        setAlignMenuAnchor(null);
        return;
      }
      if (canvas.selectedNodeIds.length >= 2) canvas.alignNodes(alignment);
      setAlignMenuAnchor(null);
    },
    [canMutateCanvas, canvas, warnReadOnly]
  );

  const handleDistribute = useCallback(
    (axis: DistributionAxis) => {
      if (!canMutateCanvas) {
        warnReadOnly();
        setAlignMenuAnchor(null);
        return;
      }
      if (canvas.selectedNodeIds.length >= 3) canvas.distributeNodes(axis);
      setAlignMenuAnchor(null);
    },
    [canMutateCanvas, canvas, warnReadOnly]
  );

  // =========================================================================
  // PANELS
  // =========================================================================

  const leftRailContent = (
    <UnifiedLeftRail
      context={{
        mode: currentMode,
        role: roleInfo?.label,
        phase: phaseInfo?.phase as unknown as RailLifecyclePhase | undefined,
      }}
      nodes={canvas.nodes}
      selectedNodeIds={canvas.selectedNodeIds}
      hoveredNodeId={canvas.hoveredNodeId}
      onInsertNode={(nodeData, position) => {
        if (!canvasPolicy.canCreateArtifacts) {
          warnReadOnly();
          return;
        }
        let pos = position;
        if (!pos) {
          const center = reactFlowInstance.getViewport();
          pos = { x: -center.x / center.zoom + 100, y: -center.y / center.zoom + 100 };
        }
        const typedNodeData = nodeData as { type?: string };
        addNodeAtPosition(typedNodeData.type || 'rectangle', pos);
      }}
      onSelectNode={(nodeId) => canvas.selectNodes([nodeId])}
      onUpdateNode={(nodeId, updates) => {
        if (!canMutateCanvas) {
          warnReadOnly();
          return;
        }
        const existingNode = canvas.nodes.find((node) => node.id === nodeId);
        canvas.updateNode(nodeId, {
          data: {
            ...(existingNode?.data ?? {}),
            ...(updates as Record<string, unknown>),
          },
        });
      }}
      onDeleteNode={(nodeId) => {
        if (!canMutateCanvas) {
          warnReadOnly();
          return;
        }
        canvas.removeNode(nodeId);
      }}
      onToggleVisibility={(nodeId) => {
        if (!canMutateCanvas) {
          warnReadOnly();
          return;
        }
        const node = canvas.nodes.find((n) => n.id === nodeId);
        if (node) {
          canvas.updateNode(nodeId, {
            data: { ...node.data, hidden: !node.data.hidden },
          });
        }
      }}
      onToggleLock={(nodeId) => {
        if (!canMutateCanvas) {
          warnReadOnly();
          return;
        }
        const node = canvas.nodes.find((n) => n.id === nodeId);
        if (node) {
          canvas.updateNode(nodeId, {
            data: { ...node.data, locked: !node.data.locked },
          });
        }
      }}
    />
  );

  const inspectorPanel = (
    <UnifiedRightPanel
      selectedNodeIds={canvas.selectedNodeIds}
      nodes={canvas.nodes as Array<{ id: string; type: string; data: Record<string, unknown> }>}
      onUpdateNode={(id, data) => {
        if (!canMutateCanvas) {
          warnReadOnly();
          return;
        }
        const existingNode = canvas.nodes.find((node) => node.id === id);
        canvas.updateNode(id, {
          data: {
            ...(existingNode?.data ?? {}),
            ...data,
          },
        });
      }}
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

  const shareEnabled = import.meta.env.VITE_FEATURE_PROJECT_SHARE === 'true';
  const exportEnabled = import.meta.env.VITE_FEATURE_PROJECT_EXPORT === 'true';

  const contextActions = useMemo(
    () => {
      const actions: HeaderAction[] = [
        { id: 'undo', label: 'Undo', icon: Undo, onClick: () => canMutateCanvas ? canvas.undo() : warnReadOnly(), disabled: !canMutateCanvas || !canvas.canUndo, tooltip: 'Undo', shortcut: '⌘Z' },
        { id: 'redo', label: 'Redo', icon: Redo, onClick: () => canMutateCanvas ? canvas.redo() : warnReadOnly(), disabled: !canMutateCanvas || !canvas.canRedo, tooltip: 'Redo', shortcut: '⌘⇧Z' },
        { id: 'zoom-in', label: 'Zoom In', icon: ZoomIn, onClick: () => reactFlowInstance.zoomIn(), tooltip: 'Zoom in', shortcut: '⌘+' },
        { id: 'zoom-out', label: 'Zoom Out', icon: ZoomOut, onClick: () => reactFlowInstance.zoomOut(), tooltip: 'Zoom out', shortcut: '⌘-' },
        { id: 'settings', label: 'Settings', icon: Settings, onClick: () => navigate(`/p/${projectId}/settings`), tooltip: 'Project settings' },
      ];

      if (shareEnabled) {
        actions.splice(2, 0, {
          id: 'share',
          label: 'Share',
          icon: Share,
          onClick: () => console.log('Share project'),
          tooltip: 'Share with others',
        });
      }

      if (exportEnabled) {
        actions.push({
          id: 'export',
          label: 'Export',
          icon: FileDownload,
          onClick: () => setExportMenuAnchor(document.getElementById('root')),
          tooltip: 'Export canvas',
          divider: true,
        });
      }

      return actions;
    },
    [canMutateCanvas, canvas, reactFlowInstance, projectId, navigate, shareEnabled, exportEnabled, warnReadOnly]
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
        className="w-full h-full flex flex-col" style={{ backgroundColor: phaseTheme.canvasBg, transition: 'background-color 0.5s ease-in-out' }} >
        <LegacyRouteCompatibilityNotice
          projectId={projectId}
          legacySurface="Project canvas"
          canonicalPhase="shape"
          reason="Canvas design work is now part of Shape and Generate phase surfaces."
        />
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
                nodes={reactFlowNodes}
                edges={reactFlowEdges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={handleNodeClick}
                onNodeContextMenu={handleNodeContextMenu}
                onPaneClick={handleCanvasClick}
                onPaneContextMenu={handleCanvasRightClick}
                onPointerDown={(event) => {
                  if (canMutateCanvas) drawing.handlePointerDown(event);
                }}
                onPointerMove={(event) => {
                  if (canMutateCanvas) drawing.handlePointerMove(event);
                }}
                onPointerUp={() => {
                  if (canMutateCanvas) drawing.handlePointerUp();
                }}
                nodeTypes={nodeTypes as unknown as NodeTypes}
                edgeTypes={edgeTypes}
                fitView
                attributionPosition="bottom-left"
                panOnDrag={canvas.activeTool === 'pan'}
                selectionOnDrag={canvas.activeTool === 'select'}
                panOnScroll
                zoomOnScroll
              >
                <Background />
                {minimapVisible && (
                  <Box
                    className="transition-all duration-300"
                    data-testid="mounted-canvas-reactflow-minimap"
                    style={{
                      opacity: calmMode ? 0 : 1,
                      transform: calmMode ? 'scale(0.98)' : 'scale(1)',
                      pointerEvents: calmMode ? 'none' : 'auto',
                      transformOrigin: 'bottom left',
                    }}
                  >
                    <MiniMap />
                  </Box>
                )}
                <Box
                  className="transition-all duration-300" style={{ opacity: calmMode ? 0 : 1, transform: calmMode ? 'scale(0.98)' : 'scale(1)', pointerEvents: calmMode ? 'none' : 'auto', transformOrigin: 'bottom left' }} >
                  <Controls />
                </Box>
              </ReactFlow>

              {/* Shortcut hint */}
              <Box
                className="absolute left-[16px] bottom-[16px] px-3 py-1.5 bg-white dark:bg-surface border border-solid border-border dark:border-border rounded-md text-xs text-fg-muted dark:text-fg-muted shadow-sm transition-all duration-300" style={{ opacity: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 1 : 0, transform: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 'translateY(0)' : 'translateY(6px)', pointerEvents: calmMode && showShortcutHint && canvas.nodes.length === 0 ? 'auto' : 'none' }}
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
                    <DraggableBox sx={{ pointerEvents: 'auto', width: 'fit-content' }}>
                      <UnifiedToolbar
                        variant="floating"
                        activeTool={canvas.activeTool || 'select'}
                        onToolChange={(tool) => {
                          const mutatingTools = ['draw', 'rectangle', 'ellipse', 'diamond', 'text', 'sticky', 'frame', 'image', 'code', 'circle'];
                          if (!canMutateCanvas && mutatingTools.includes(tool)) {
                            warnReadOnly();
                            canvas.setActiveTool('select');
                            return;
                          }
                          canvas.setActiveTool(tool);
                        }}
                        onUndo={() => canMutateCanvas ? canvas.undo() : warnReadOnly()}
                        onRedo={() => canMutateCanvas ? canvas.redo() : warnReadOnly()}
                        canUndo={canMutateCanvas && canvas.canUndo}
                        canRedo={canMutateCanvas && canvas.canRedo}
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
              syncStatus={canvasSyncStatus}
            />

            {projectId && <CanvasCollaborationBanner projectId={projectId} />}
          </Box>

          {/* Node Context Menu */}
          <CanvasNodeContextMenu
            nodeContextMenu={nodeContextMenu}
            onClose={() => setNodeContextMenu(null)}
            canvas={canvas}
            currentMode={currentMode}
            codePanelVisible={codePanelVisible}
            toggleCodePanel={toggleCodePanel}
            canMutateCanvas={canMutateCanvas}
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
