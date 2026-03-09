/**
 * Plugin Dependency Graph Component
 *
 * Visualizes plugin dependencies, relationships, and potential conflicts.
 * Uses a force-directed graph layout for intuitive visualization.
 *
 * Features:
 * - Interactive node dragging
 * - Dependency type indicators (required, optional, conflicts)
 * - Zoom and pan controls
 * - Plugin details on hover
 * - Conflict highlighting
 *
 * @doc.type component
 * @doc.purpose Visualize plugin dependency relationships
 * @doc.layer frontend
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  GitBranch,
  AlertTriangle,
  CheckCircle,
  XCircle,
  ZoomIn,
  ZoomOut,
  Maximize2,
  Info,
} from 'lucide-react';
import { cn, cardStyles, textStyles, buttonStyles } from '../../lib/theme';

export interface PluginNode {
  id: string;
  name: string;
  version: string;
  status: 'active' | 'inactive' | 'error';
  category: string;
}

export interface PluginDependency {
  from: string;
  to: string;
  type: 'requires' | 'optional' | 'conflicts' | 'provides';
  version?: string;
  resolved: boolean;
}

export interface PluginDependencyGraphProps {
  /** List of plugin nodes */
  plugins: PluginNode[];
  /** List of dependencies between plugins */
  dependencies: PluginDependency[];
  /** Callback when a plugin is selected */
  onPluginSelect?: (pluginId: string) => void;
  /** Additional CSS classes */
  className?: string;
}

interface GraphNode extends PluginNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
}

/**
 * Plugin Dependency Graph Component
 */
export function PluginDependencyGraph({
  plugins,
  dependencies,
  onPluginSelect,
  className,
}: PluginDependencyGraphProps): React.ReactElement {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [graphNodes, setGraphNodes] = useState<GraphNode[]>([]);
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [hoveredNode, setHoveredNode] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const animationRef = useRef<number | undefined>(undefined);

  // Initialize graph nodes with random positions
  useEffect(() => {
    const width = canvasRef.current?.width || 800;
    const height = canvasRef.current?.height || 600;

    const nodes: GraphNode[] = plugins.map((plugin) => ({
      ...plugin,
      x: Math.random() * width,
      y: Math.random() * height,
      vx: 0,
      vy: 0,
    }));

    setGraphNodes(nodes);
  }, [plugins]);

  // Force-directed graph simulation
  useEffect(() => {
    if (graphNodes.length === 0) return;

    const simulate = () => {
      const nodes = [...graphNodes];
      const width = canvasRef.current?.width || 800;
      const height = canvasRef.current?.height || 600;

      // Apply forces
      nodes.forEach((node, i) => {
        // Repulsion between nodes
        nodes.forEach((other, j) => {
          if (i === j) return;
          const dx = other.x - node.x;
          const dy = other.y - node.y;
          const distance = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = 5000 / (distance * distance);
          node.vx -= (dx / distance) * force;
          node.vy -= (dy / distance) * force;
        });

        // Attraction along edges
        dependencies.forEach((dep) => {
          const isSource = dep.from === node.id;
          const isTarget = dep.to === node.id;
          if (!isSource && !isTarget) return;

          const other = nodes.find(
            (n) => n.id === (isSource ? dep.to : dep.from)
          );
          if (!other) return;

          const dx = other.x - node.x;
          const dy = other.y - node.y;
          const distance = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = distance * 0.001;
          node.vx += (dx / distance) * force;
          node.vy += (dy / distance) * force;
        });

        // Center gravity
        const cx = width / 2;
        const cy = height / 2;
        node.vx += (cx - node.x) * 0.0001;
        node.vy += (cy - node.y) * 0.0001;

        // Damping
        node.vx *= 0.9;
        node.vy *= 0.9;

        // Update position
        node.x += node.vx;
        node.y += node.vy;

        // Boundary constraints
        node.x = Math.max(40, Math.min(width - 40, node.x));
        node.y = Math.max(40, Math.min(height - 40, node.y));
      });

      setGraphNodes(nodes);
      animationRef.current = requestAnimationFrame(simulate);
    };

    animationRef.current = requestAnimationFrame(simulate);

    return () => {
      if (animationRef.current !== undefined) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [graphNodes, dependencies]);

  // Draw graph
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.save();

    // Apply zoom and pan
    ctx.translate(offset.x, offset.y);
    ctx.scale(zoom, zoom);

    // Draw edges
    dependencies.forEach((dep) => {
      const from = graphNodes.find((n) => n.id === dep.from);
      const to = graphNodes.find((n) => n.id === dep.to);
      if (!from || !to) return;

      ctx.beginPath();
      ctx.moveTo(from.x, from.y);
      ctx.lineTo(to.x, to.y);

      // Color by dependency type
      switch (dep.type) {
        case 'requires':
          ctx.strokeStyle = dep.resolved ? '#10b981' : '#ef4444';
          ctx.lineWidth = 2;
          break;
        case 'optional':
          ctx.strokeStyle = '#6b7280';
          ctx.lineWidth = 1;
          ctx.setLineDash([5, 5]);
          break;
        case 'conflicts':
          ctx.strokeStyle = '#f59e0b';
          ctx.lineWidth = 2;
          ctx.setLineDash([2, 2]);
          break;
        case 'provides':
          ctx.strokeStyle = '#3b82f6';
          ctx.lineWidth = 1;
          break;
      }

      ctx.stroke();
      ctx.setLineDash([]);

      // Draw arrow
      const angle = Math.atan2(to.y - from.y, to.x - from.x);
      const arrowSize = 10;
      ctx.beginPath();
      ctx.moveTo(
        to.x - arrowSize * Math.cos(angle - Math.PI / 6),
        to.y - arrowSize * Math.sin(angle - Math.PI / 6)
      );
      ctx.lineTo(to.x, to.y);
      ctx.lineTo(
        to.x - arrowSize * Math.cos(angle + Math.PI / 6),
        to.y - arrowSize * Math.sin(angle + Math.PI / 6)
      );
      ctx.stroke();
    });

    // Draw nodes
    graphNodes.forEach((node) => {
      const isSelected = selectedNode === node.id;
      const isHovered = hoveredNode === node.id;
      const nodeRadius = 30;

      // Node circle
      ctx.beginPath();
      ctx.arc(node.x, node.y, nodeRadius, 0, 2 * Math.PI);

      // Color by status
      switch (node.status) {
        case 'active':
          ctx.fillStyle = '#10b981';
          break;
        case 'inactive':
          ctx.fillStyle = '#6b7280';
          break;
        case 'error':
          ctx.fillStyle = '#ef4444';
          break;
      }

      if (isSelected || isHovered) {
        ctx.shadowBlur = 10;
        ctx.shadowColor = ctx.fillStyle;
      }

      ctx.fill();
      ctx.shadowBlur = 0;

      // Border
      ctx.strokeStyle = isSelected ? '#ffffff' : '#e5e7eb';
      ctx.lineWidth = isSelected ? 3 : 1;
      ctx.stroke();

      // Label
      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 10px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const label = node.name.slice(0, 10);
      ctx.fillText(label, node.x, node.y);
    });

    ctx.restore();
  }, [graphNodes, dependencies, selectedNode, hoveredNode, zoom, offset]);

  // Handle canvas mouse events
  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const x = (e.clientX - rect.left - offset.x) / zoom;
    const y = (e.clientY - rect.top - offset.y) / zoom;

    // Find clicked node
    const clickedNode = graphNodes.find((node) => {
      const dx = x - node.x;
      const dy = y - node.y;
      return Math.sqrt(dx * dx + dy * dy) < 30;
    });

    if (clickedNode) {
      setSelectedNode(clickedNode.id);
      onPluginSelect?.(clickedNode.id);
    } else {
      setSelectedNode(null);
    }
  };

  const handleCanvasMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (isDragging) {
      const dx = e.clientX - dragStart.x;
      const dy = e.clientY - dragStart.y;
      setOffset({ x: offset.x + dx, y: offset.y + dy });
      setDragStart({ x: e.clientX, y: e.clientY });
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const x = (e.clientX - rect.left - offset.x) / zoom;
    const y = (e.clientY - rect.top - offset.y) / zoom;

    // Find hovered node
    const hoveredNode = graphNodes.find((node) => {
      const dx = x - node.x;
      const dy = y - node.y;
      return Math.sqrt(dx * dx + dy * dy) < 30;
    });

    setHoveredNode(hoveredNode?.id || null);
  };

  const handleZoomIn = () => setZoom(Math.min(zoom * 1.2, 3));
  const handleZoomOut = () => setZoom(Math.max(zoom / 1.2, 0.5));
  const handleReset = () => {
    setZoom(1);
    setOffset({ x: 0, y: 0 });
  };

  // Get dependency stats
  const totalDeps = dependencies.length;
  const conflicts = dependencies.filter((d) => d.type === 'conflicts').length;
  const unresolved = dependencies.filter((d) => !d.resolved).length;

  const selectedPlugin = graphNodes.find((n) => n.id === selectedNode);

  return (
    <div className={cn('relative', className)} ref={containerRef}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <GitBranch className="h-5 w-5 text-blue-600" />
          <h3 className={textStyles.h4}>Dependency Graph</h3>
        </div>

        {/* Stats */}
        <div className="flex items-center gap-4 text-sm">
          <div className="flex items-center gap-1">
            <span className="text-gray-600">Plugins:</span>
            <span className="font-semibold">{plugins.length}</span>
          </div>
          <div className="flex items-center gap-1">
            <span className="text-gray-600">Dependencies:</span>
            <span className="font-semibold">{totalDeps}</span>
          </div>
          {conflicts > 0 && (
            <div className="flex items-center gap-1 text-orange-600">
              <AlertTriangle className="h-4 w-4" />
              <span className="font-semibold">{conflicts} conflicts</span>
            </div>
          )}
          {unresolved > 0 && (
            <div className="flex items-center gap-1 text-red-600">
              <XCircle className="h-4 w-4" />
              <span className="font-semibold">{unresolved} unresolved</span>
            </div>
          )}
        </div>
      </div>

      {/* Canvas */}
      <div className={cn(cardStyles.base, 'relative overflow-hidden')}>
        <canvas
          ref={canvasRef}
          width={800}
          height={600}
          className="w-full h-[600px] cursor-move"
          onClick={handleCanvasClick}
          onMouseMove={handleCanvasMouseMove}
          onMouseDown={(e) => {
            setIsDragging(true);
            setDragStart({ x: e.clientX, y: e.clientY });
          }}
          onMouseUp={() => setIsDragging(false)}
          onMouseLeave={() => setIsDragging(false)}
        />

        {/* Controls */}
        <div className="absolute top-4 right-4 flex flex-col gap-2">
          <button
            onClick={handleZoomIn}
            className={cn(buttonStyles.secondary, 'p-2')}
            title="Zoom In"
          >
            <ZoomIn className="h-4 w-4" />
          </button>
          <button
            onClick={handleZoomOut}
            className={cn(buttonStyles.secondary, 'p-2')}
            title="Zoom Out"
          >
            <ZoomOut className="h-4 w-4" />
          </button>
          <button
            onClick={handleReset}
            className={cn(buttonStyles.secondary, 'p-2')}
            title="Reset View"
          >
            <Maximize2 className="h-4 w-4" />
          </button>
        </div>

        {/* Legend */}
        <div className="absolute bottom-4 left-4 bg-white dark:bg-gray-800 p-3 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700">
          <div className="text-xs font-semibold mb-2">Legend</div>
          <div className="space-y-1 text-xs">
            <div className="flex items-center gap-2">
              <div className="w-8 h-0.5 bg-green-500" />
              <span>Required (resolved)</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-0.5 bg-red-500" />
              <span>Required (unresolved)</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-0.5 bg-gray-500" style={{ borderTop: '1px dashed' }} />
              <span>Optional</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-0.5 bg-orange-500" style={{ borderTop: '2px dashed' }} />
              <span>Conflicts</span>
            </div>
          </div>
        </div>

        {/* Selected plugin info */}
        {selectedPlugin && (
          <div className="absolute top-4 left-4 bg-white dark:bg-gray-800 p-4 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 max-w-xs">
            <div className="flex items-start justify-between mb-2">
              <div>
                <h4 className="font-semibold">{selectedPlugin.name}</h4>
                <p className="text-xs text-gray-600">v{selectedPlugin.version}</p>
              </div>
              <button
                onClick={() => setSelectedNode(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                ×
              </button>
            </div>
            <div className="flex items-center gap-2 text-xs">
              <span className={cn(
                'px-2 py-0.5 rounded',
                selectedPlugin.status === 'active' && 'bg-green-100 text-green-700',
                selectedPlugin.status === 'inactive' && 'bg-gray-100 text-gray-700',
                selectedPlugin.status === 'error' && 'bg-red-100 text-red-700'
              )}>
                {selectedPlugin.status}
              </span>
              <span className="text-gray-600">{selectedPlugin.category}</span>
            </div>

            {/* Dependencies */}
            <div className="mt-3 text-xs">
              <div className="font-semibold mb-1">Dependencies:</div>
              {dependencies.filter(d => d.from === selectedPlugin.id).length > 0 ? (
                <ul className="space-y-1">
                  {dependencies
                    .filter(d => d.from === selectedPlugin.id)
                    .map((dep, i) => (
                      <li key={i} className="flex items-center gap-1">
                        {dep.resolved ? (
                          <CheckCircle className="h-3 w-3 text-green-600" />
                        ) : (
                          <XCircle className="h-3 w-3 text-red-600" />
                        )}
                        <span>{graphNodes.find(n => n.id === dep.to)?.name}</span>
                      </li>
                    ))}
                </ul>
              ) : (
                <p className="text-gray-500">No dependencies</p>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Help text */}
      <div className="mt-2 flex items-center gap-2 text-xs text-gray-600">
        <Info className="h-4 w-4" />
        <span>Click nodes to view details. Drag to pan. Use controls to zoom.</span>
      </div>
    </div>
  );
}
