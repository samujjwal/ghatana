/**
 * Project Canvas Component
 *
 * @description Interactive canvas for visual project architecture design
 * using React Flow. Supports drag-and-drop, connections, and real-time
 * collaboration via CRDT sync.
 *
 * @doc.type component
 * @doc.purpose Visual architecture editor
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, {
  useState,
  useCallback,
  useMemo,
  useRef,
  useEffect,
  forwardRef,
  useImperativeHandle,
} from 'react';
import {
  ReactFlow,
  Node,
  Edge,
  Connection,
  NodeChange,
  EdgeChange,
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  Panel,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType,
  useReactFlow,
  ReactFlowProvider,
  NodeTypes,
  EdgeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ZoomIn,
  ZoomOut,
  Maximize2,
  Lock,
  Unlock,
  Grid3X3,
  Download,
  Upload,
  Undo2,
  Redo2,
  Trash2,
  Copy,
  Clipboard,
  MousePointer2,
  Hand,
  PenTool,
  Plus,
  Database,
  Server,
  Globe,
  Shield,
  Cpu,
  Cloud,
  Boxes,
  GitBranch,
  MessageSquare,
  Bell,
  Mail,
  Users,
  FileCode,
  Layers,
} from 'lucide-react';
import { useAtom, useSetAtom } from 'jotai';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
  DropdownMenuLabel,
} from '@ghatana/yappc-ui';

import {
  canvasStateAtom,
  canvasNodesAtom,
  canvasEdgesAtom,
  selectedCanvasNodeAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export interface CanvasNode extends Node {
  data: {
    label: string;
    type: NodeCategory;
    icon?: string;
    description?: string;
    properties?: Record<string, unknown>;
    status?: 'pending' | 'configured' | 'error';
  };
}

export interface CanvasEdge extends Edge {
  data?: {
    label?: string;
    type?: 'data' | 'event' | 'dependency' | 'http';
    animated?: boolean;
  };
}

export type NodeCategory =
  | 'service'
  | 'database'
  | 'api'
  | 'queue'
  | 'storage'
  | 'auth'
  | 'cache'
  | 'cdn'
  | 'container'
  | 'serverless'
  | 'notification'
  | 'analytics'
  | 'custom';

export type CanvasTool = 'select' | 'pan' | 'connect' | 'add';

interface ProjectCanvasProps {
  sessionId: string;
  onNodesChange?: (nodes: CanvasNode[]) => void;
  onEdgesChange?: (edges: CanvasEdge[]) => void;
  onNodeSelect?: (nodeId: string | null) => void;
  onNodeDoubleClick?: (nodeId: string) => void;
  onSave?: () => void;
  readOnly?: boolean;
  showMiniMap?: boolean;
  showControls?: boolean;
  className?: string;
}

export interface ProjectCanvasRef {
  fitView: () => void;
  zoomIn: () => void;
  zoomOut: () => void;
  addNode: (node: Partial<CanvasNode>) => void;
  removeSelectedNodes: () => void;
  undo: () => void;
  redo: () => void;
  exportCanvas: () => { nodes: CanvasNode[]; edges: CanvasEdge[] };
  importCanvas: (data: { nodes: CanvasNode[]; edges: CanvasEdge[] }) => void;
}

// =============================================================================
// Node Templates
// =============================================================================

const nodeTemplates: Array<{
  category: string;
  items: Array<{
    type: NodeCategory;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    defaultData?: Record<string, unknown>;
  }>;
}> = [
  {
    category: 'Compute',
    items: [
      { type: 'service', label: 'Service', icon: Server },
      { type: 'serverless', label: 'Function', icon: Cpu },
      { type: 'container', label: 'Container', icon: Boxes },
    ],
  },
  {
    category: 'Data',
    items: [
      { type: 'database', label: 'Database', icon: Database },
      { type: 'cache', label: 'Cache', icon: Layers },
      { type: 'storage', label: 'Storage', icon: Cloud },
      { type: 'queue', label: 'Queue', icon: GitBranch },
    ],
  },
  {
    category: 'Network',
    items: [
      { type: 'api', label: 'API Gateway', icon: Globe },
      { type: 'cdn', label: 'CDN', icon: Globe },
      { type: 'auth', label: 'Auth', icon: Shield },
    ],
  },
  {
    category: 'Integration',
    items: [
      { type: 'notification', label: 'Notification', icon: Bell },
      { type: 'analytics', label: 'Analytics', icon: FileCode },
      { type: 'custom', label: 'Custom', icon: Boxes },
    ],
  },
];

// =============================================================================
// Custom Node Component
// =============================================================================

const CustomNode = React.memo(({ data, selected }: { data: CanvasNode['data']; selected: boolean }) => {
  const IconComponent = useMemo(() => {
    const iconMap: Record<NodeCategory, React.ComponentType<{ className?: string }>> = {
      service: Server,
      database: Database,
      api: Globe,
      queue: GitBranch,
      storage: Cloud,
      auth: Shield,
      cache: Layers,
      cdn: Globe,
      container: Boxes,
      serverless: Cpu,
      notification: Bell,
      analytics: FileCode,
      custom: Boxes,
    };
    return iconMap[data.type] || Boxes;
  }, [data.type]);

  const statusColor = useMemo(() => {
    switch (data.status) {
      case 'configured':
        return 'border-green-500 bg-green-500/10';
      case 'error':
        return 'border-red-500 bg-red-500/10';
      default:
        return 'border-zinc-600 bg-zinc-800';
    }
  }, [data.status]);

  return (
    <div
      className={cn(
        'relative px-4 py-3 rounded-lg border-2 min-w-[140px] transition-all',
        statusColor,
        selected && 'ring-2 ring-violet-500 ring-offset-2 ring-offset-zinc-900'
      )}
    >
      {/* Status indicator */}
      {data.status === 'configured' && (
        <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-green-500 border-2 border-zinc-900" />
      )}
      {data.status === 'error' && (
        <div className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-red-500 border-2 border-zinc-900" />
      )}

      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-zinc-700/50 flex items-center justify-center">
          <IconComponent className="w-5 h-5 text-zinc-300" />
        </div>
        <div>
          <div className="font-medium text-sm text-zinc-100">{data.label}</div>
          <div className="text-xs text-zinc-500 capitalize">{data.type}</div>
        </div>
      </div>

      {data.description && (
        <div className="mt-2 text-xs text-zinc-400 line-clamp-2">
          {data.description}
        </div>
      )}

      {/* Connection handles */}
      <div className="absolute top-1/2 -left-2 w-4 h-4 rounded-full bg-zinc-700 border-2 border-zinc-600 transform -translate-y-1/2" />
      <div className="absolute top-1/2 -right-2 w-4 h-4 rounded-full bg-zinc-700 border-2 border-zinc-600 transform -translate-y-1/2" />
    </div>
  );
});

CustomNode.displayName = 'CustomNode';

// =============================================================================
// Toolbar Component
// =============================================================================

const CanvasToolbar = React.memo(({
  activeTool,
  onToolChange,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
  isLocked,
  onToggleLock,
  onExport,
  onImport,
  onDeleteSelected,
  hasSelection,
}: {
  activeTool: CanvasTool;
  onToolChange: (tool: CanvasTool) => void;
  onUndo: () => void;
  onRedo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  isLocked: boolean;
  onToggleLock: () => void;
  onExport: () => void;
  onImport: () => void;
  onDeleteSelected: () => void;
  hasSelection: boolean;
}) => {
  const tools: Array<{ id: CanvasTool; icon: React.ComponentType<{ className?: string }>; label: string }> = [
    { id: 'select', icon: MousePointer2, label: 'Select' },
    { id: 'pan', icon: Hand, label: 'Pan' },
    { id: 'connect', icon: PenTool, label: 'Connect' },
  ];

  return (
    <div className="flex items-center gap-1 p-1 bg-zinc-900/90 backdrop-blur-sm rounded-lg border border-zinc-800">
      {/* Tool selection */}
      {tools.map((tool) => (
        <Tooltip key={tool.id}>
          <TooltipTrigger asChild>
            <Button
              variant={activeTool === tool.id ? 'secondary' : 'ghost'}
              size="icon"
              className="h-8 w-8"
              onClick={() => onToolChange(tool.id)}
            >
              <tool.icon className="w-4 h-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>{tool.label}</TooltipContent>
        </Tooltip>
      ))}

      <div className="w-px h-6 bg-zinc-700 mx-1" />

      {/* Add node dropdown */}
      <DropdownMenu>
        <Tooltip>
          <TooltipTrigger asChild>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8">
                <Plus className="w-4 h-4" />
              </Button>
            </DropdownMenuTrigger>
          </TooltipTrigger>
          <TooltipContent>Add Node</TooltipContent>
        </Tooltip>
        <DropdownMenuContent align="start" className="w-48">
          {nodeTemplates.map((category) => (
            <React.Fragment key={category.category}>
              <DropdownMenuLabel className="text-xs text-zinc-500">
                {category.category}
              </DropdownMenuLabel>
              {category.items.map((item) => (
                <DropdownMenuItem
                  key={item.type}
                  onClick={() => onToolChange('add')}
                >
                  <item.icon className="w-4 h-4 mr-2" />
                  {item.label}
                </DropdownMenuItem>
              ))}
              <DropdownMenuSeparator />
            </React.Fragment>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>

      <div className="w-px h-6 bg-zinc-700 mx-1" />

      {/* Undo/Redo */}
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={onUndo}
            disabled={!canUndo}
          >
            <Undo2 className="w-4 h-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Undo (⌘Z)</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={onRedo}
            disabled={!canRedo}
          >
            <Redo2 className="w-4 h-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Redo (⌘⇧Z)</TooltipContent>
      </Tooltip>

      <div className="w-px h-6 bg-zinc-700 mx-1" />

      {/* Delete */}
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={onDeleteSelected}
            disabled={!hasSelection}
          >
            <Trash2 className="w-4 h-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Delete (⌫)</TooltipContent>
      </Tooltip>

      <div className="w-px h-6 bg-zinc-700 mx-1" />

      {/* Lock */}
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant={isLocked ? 'secondary' : 'ghost'}
            size="icon"
            className="h-8 w-8"
            onClick={onToggleLock}
          >
            {isLocked ? <Lock className="w-4 h-4" /> : <Unlock className="w-4 h-4" />}
          </Button>
        </TooltipTrigger>
        <TooltipContent>{isLocked ? 'Unlock canvas' : 'Lock canvas'}</TooltipContent>
      </Tooltip>

      {/* Export/Import */}
      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onExport}>
            <Download className="w-4 h-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Export</TooltipContent>
      </Tooltip>

      <Tooltip>
        <TooltipTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onImport}>
            <Upload className="w-4 h-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Import</TooltipContent>
      </Tooltip>
    </div>
  );
});

CanvasToolbar.displayName = 'CanvasToolbar';

// =============================================================================
// Main Component (Inner)
// =============================================================================

const ProjectCanvasInner = forwardRef<ProjectCanvasRef, ProjectCanvasProps>(
  (
    {
      sessionId,
      onNodesChange: onNodesChangeProp,
      onEdgesChange: onEdgesChangeProp,
      onNodeSelect,
      onNodeDoubleClick,
      onSave,
      readOnly = false,
      showMiniMap = true,
      showControls = true,
      className,
    },
    ref
  ) => {
    const reactFlowInstance = useReactFlow();

    const [canvasState, setCanvasState] = useAtom(canvasStateAtom);
    const setSelectedNode = useSetAtom(selectedCanvasNodeAtom);

    const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<CanvasEdge>([]);

    const [activeTool, setActiveTool] = useState<CanvasTool>('select');
    const [isLocked, setIsLocked] = useState(false);
    const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);

    // History for undo/redo
    const [history, setHistory] = useState<Array<{ nodes: CanvasNode[]; edges: CanvasEdge[] }>>([]);
    const [historyIndex, setHistoryIndex] = useState(-1);

    // Custom node types
    const nodeTypes: NodeTypes = useMemo(
      () => ({
        custom: CustomNode,
      }),
      []
    );

    // Push to history
    const pushToHistory = useCallback(() => {
      const newHistory = history.slice(0, historyIndex + 1);
      newHistory.push({ nodes: [...nodes], edges: [...edges] });
      setHistory(newHistory.slice(-50)); // Keep last 50 states
      setHistoryIndex(newHistory.length - 1);
    }, [nodes, edges, history, historyIndex]);

    // Undo
    const undo = useCallback(() => {
      if (historyIndex > 0) {
        const prevState = history[historyIndex - 1];
        setNodes(prevState.nodes);
        setEdges(prevState.edges);
        setHistoryIndex(historyIndex - 1);
      }
    }, [history, historyIndex, setNodes, setEdges]);

    // Redo
    const redo = useCallback(() => {
      if (historyIndex < history.length - 1) {
        const nextState = history[historyIndex + 1];
        setNodes(nextState.nodes);
        setEdges(nextState.edges);
        setHistoryIndex(historyIndex + 1);
      }
    }, [history, historyIndex, setNodes, setEdges]);

    // Handle connections
    const onConnect = useCallback(
      (connection: Connection) => {
        if (readOnly || isLocked) return;
        pushToHistory();
        setEdges((eds) =>
          addEdge(
            {
              ...connection,
              type: 'smoothstep',
              animated: true,
              markerEnd: { type: MarkerType.ArrowClosed },
              style: { stroke: '#6366f1' },
            },
            eds
          )
        );
      },
      [readOnly, isLocked, setEdges, pushToHistory]
    );

    // Handle node changes
    const handleNodesChange = useCallback(
      (changes: NodeChange[]) => {
        if (readOnly || isLocked) return;
        onNodesChange(changes);
        onNodesChangeProp?.(nodes);
      },
      [readOnly, isLocked, onNodesChange, onNodesChangeProp, nodes]
    );

    // Handle edge changes
    const handleEdgesChange = useCallback(
      (changes: EdgeChange[]) => {
        if (readOnly || isLocked) return;
        onEdgesChange(changes);
        onEdgesChangeProp?.(edges);
      },
      [readOnly, isLocked, onEdgesChange, onEdgesChangeProp, edges]
    );

    // Handle selection
    const onSelectionChange = useCallback(
      ({ nodes: selectedNodes }: { nodes: Node[] }) => {
        const ids = selectedNodes.map((n) => n.id);
        setSelectedNodeIds(ids);
        if (ids.length === 1) {
          setSelectedNode(selectedNodes[0] as CanvasNode);
          onNodeSelect?.(ids[0]);
        } else {
          setSelectedNode(null);
          onNodeSelect?.(null);
        }
      },
      [setSelectedNode, onNodeSelect]
    );

    // Add new node
    const addNode = useCallback(
      (nodeData: Partial<CanvasNode>) => {
        if (readOnly || isLocked) return;
        pushToHistory();

        const position = reactFlowInstance.screenToFlowPosition({
          x: window.innerWidth / 2,
          y: window.innerHeight / 2,
        });

        const newNode: CanvasNode = {
          id: `node-${Date.now()}`,
          type: 'custom',
          position,
          data: {
            label: 'New Node',
            type: 'service',
            status: 'pending',
            ...nodeData.data,
          },
          ...nodeData,
        };

        setNodes((nds) => [...nds, newNode]);
      },
      [readOnly, isLocked, reactFlowInstance, setNodes, pushToHistory]
    );

    // Remove selected nodes
    const removeSelectedNodes = useCallback(() => {
      if (readOnly || isLocked || selectedNodeIds.length === 0) return;
      pushToHistory();
      setNodes((nds) => nds.filter((n) => !selectedNodeIds.includes(n.id)));
      setEdges((eds) =>
        eds.filter(
          (e) =>
            !selectedNodeIds.includes(e.source) &&
            !selectedNodeIds.includes(e.target)
        )
      );
    }, [readOnly, isLocked, selectedNodeIds, setNodes, setEdges, pushToHistory]);

    // Export canvas
    const exportCanvas = useCallback(() => {
      return { nodes, edges };
    }, [nodes, edges]);

    // Import canvas
    const importCanvas = useCallback(
      (data: { nodes: CanvasNode[]; edges: CanvasEdge[] }) => {
        pushToHistory();
        setNodes(data.nodes);
        setEdges(data.edges);
        setTimeout(() => reactFlowInstance.fitView(), 100);
      },
      [setNodes, setEdges, reactFlowInstance, pushToHistory]
    );

    // Expose methods via ref
    useImperativeHandle(ref, () => ({
      fitView: () => reactFlowInstance.fitView(),
      zoomIn: () => reactFlowInstance.zoomIn(),
      zoomOut: () => reactFlowInstance.zoomOut(),
      addNode,
      removeSelectedNodes,
      undo,
      redo,
      exportCanvas,
      importCanvas,
    }));

    // Keyboard shortcuts
    useEffect(() => {
      const handleKeyDown = (e: KeyboardEvent) => {
        if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) {
          return;
        }

        if ((e.metaKey || e.ctrlKey) && e.key === 'z') {
          e.preventDefault();
          if (e.shiftKey) {
            redo();
          } else {
            undo();
          }
        }

        if ((e.metaKey || e.ctrlKey) && e.key === 's') {
          e.preventDefault();
          onSave?.();
        }

        if (e.key === 'Delete' || e.key === 'Backspace') {
          if (!readOnly && !isLocked) {
            removeSelectedNodes();
          }
        }
      };

      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }, [undo, redo, removeSelectedNodes, onSave, readOnly, isLocked]);

    return (
      <div className={cn('w-full h-full relative', className)}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={handleNodesChange}
          onEdgesChange={handleEdgesChange}
          onConnect={onConnect}
          onSelectionChange={onSelectionChange}
          onNodeDoubleClick={(_, node) => onNodeDoubleClick?.(node.id)}
          fitView
          panOnDrag={activeTool === 'pan'}
          selectionOnDrag={activeTool === 'select'}
          connectOnClick={activeTool === 'connect'}
          nodesDraggable={!readOnly && !isLocked}
          nodesConnectable={!readOnly && !isLocked}
          elementsSelectable={!readOnly && !isLocked}
          snapToGrid
          snapGrid={[15, 15]}
          defaultEdgeOptions={{
            type: 'smoothstep',
            markerEnd: { type: MarkerType.ArrowClosed },
            style: { stroke: '#6366f1' },
          }}
          proOptions={{ hideAttribution: true }}
        >
          <Background
            variant={BackgroundVariant.Dots}
            gap={20}
            size={1}
            color="#3f3f46"
          />

          {showMiniMap && (
            <MiniMap
              nodeStrokeWidth={3}
              zoomable
              pannable
              className="bg-zinc-900 border border-zinc-800 rounded-lg"
            />
          )}

          {showControls && (
            <Controls
              showInteractive={false}
              className="bg-zinc-900/90 backdrop-blur-sm border border-zinc-800 rounded-lg"
            />
          )}

          {/* Custom toolbar */}
          <Panel position="top-left">
            <CanvasToolbar
              activeTool={activeTool}
              onToolChange={setActiveTool}
              onUndo={undo}
              onRedo={redo}
              canUndo={historyIndex > 0}
              canRedo={historyIndex < history.length - 1}
              isLocked={isLocked}
              onToggleLock={() => setIsLocked(!isLocked)}
              onExport={() => {
                const data = exportCanvas();
                const blob = new Blob([JSON.stringify(data, null, 2)], {
                  type: 'application/json',
                });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `canvas-${sessionId}.json`;
                a.click();
              }}
              onImport={() => {
                const input = document.createElement('input');
                input.type = 'file';
                input.accept = '.json';
                input.onchange = (e) => {
                  const file = (e.target as HTMLInputElement).files?.[0];
                  if (file) {
                    const reader = new FileReader();
                    reader.onload = (e) => {
                      try {
                        const data = JSON.parse(e.target?.result as string);
                        importCanvas(data);
                      } catch (err) {
                        console.error('Failed to import canvas:', err);
                      }
                    };
                    reader.readAsText(file);
                  }
                };
                input.click();
              }}
              onDeleteSelected={removeSelectedNodes}
              hasSelection={selectedNodeIds.length > 0}
            />
          </Panel>

          {/* Selection info */}
          {selectedNodeIds.length > 0 && (
            <Panel position="bottom-center">
              <div className="px-4 py-2 bg-zinc-900/90 backdrop-blur-sm rounded-lg border border-zinc-800 text-sm">
                {selectedNodeIds.length === 1
                  ? `1 node selected`
                  : `${selectedNodeIds.length} nodes selected`}
              </div>
            </Panel>
          )}
        </ReactFlow>
      </div>
    );
  }
);

ProjectCanvasInner.displayName = 'ProjectCanvasInner';

// =============================================================================
// Main Component (with Provider)
// =============================================================================

export const ProjectCanvas = forwardRef<ProjectCanvasRef, ProjectCanvasProps>(
  (props, ref) => (
    <ReactFlowProvider>
      <ProjectCanvasInner ref={ref} {...props} />
    </ReactFlowProvider>
  )
);

ProjectCanvas.displayName = 'ProjectCanvas';

export default ProjectCanvas;
