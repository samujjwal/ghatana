/**
 * Lineage Graph Component
 *
 * Interactive data lineage visualization using ReactFlow.
 * Part of Journey 9: Lineage Explorer & Root Cause Analysis
 *
 * @doc.type component
 * @doc.purpose Interactive lineage graph visualization
 * @doc.layer frontend
 */

import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Node,
  Edge,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  MarkerType,
  Position,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Database, Table, FileText, BarChart3, Workflow } from 'lucide-react';
import { LineageNode, LineageEdge } from '../../api/lineage.service';

interface LineageGraphProps {
  nodes: LineageNode[];
  edges: LineageEdge[];
  rootNode?: string;
  onNodeClick?: (node: LineageNode) => void;
  height?: string;
}

const nodeTypes = {
  dataset: DatasetNode,
  transformation: TransformationNode,
  query: QueryNode,
  dashboard: DashboardNode,
  mlModel: MLModelNode,
};

export function LineageGraph({
  nodes: lineageNodes,
  edges: lineageEdges,
  rootNode,
  onNodeClick,
  height = '600px',
}: LineageGraphProps) {
  // Convert lineage nodes to ReactFlow nodes
  const initialNodes: Node[] = useMemo(() => {
    return lineageNodes.map((node, index) => {
      const isRoot = node.id === rootNode;

      return {
        id: node.id,
        type: node.type.toLowerCase(),
        position: calculateNodePosition(index, lineageNodes.length),
        data: {
          label: node.name,
          type: node.type,
          metadata: node.metadata,
          isRoot,
        },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      };
    });
  }, [lineageNodes, rootNode]);

  // Convert lineage edges to ReactFlow edges
  const initialEdges: Edge[] = useMemo(() => {
    return lineageEdges.map((edge, index) => ({
      id: `e-${index}`,
      source: edge.source,
      target: edge.target,
      type: 'smoothstep',
      animated: edge.type === 'TRANSFORMS',
      label: edge.type,
      markerEnd: {
        type: MarkerType.ArrowClosed,
      },
      style: {
        stroke: getEdgeColor(edge.type),
        strokeWidth: 2,
      },
    }));
  }, [lineageEdges]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      const lineageNode = lineageNodes.find((n) => n.id === node.id);
      if (lineageNode) {
        onNodeClick?.(lineageNode);
      }
    },
    [lineageNodes, onNodeClick]
  );

  return (
    <div style={{ height }} className="border border-gray-300 rounded-lg overflow-hidden bg-gray-50">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        fitView
        attributionPosition="bottom-left"
      >
        <Background />
        <Controls />
      </ReactFlow>
    </div>
  );
}

// Helper function to calculate node positions (simple layout)
function calculateNodePosition(index: number, total: number): { x: number; y: number } {
  const cols = Math.ceil(Math.sqrt(total));
  const row = Math.floor(index / cols);
  const col = index % cols;

  return {
    x: col * 250,
    y: row * 150,
  };
}

// Helper function to get edge color based on type
function getEdgeColor(type: string): string {
  switch (type) {
    case 'DERIVES_FROM':
      return '#3b82f6'; // blue
    case 'FEEDS_INTO':
      return '#10b981'; // green
    case 'TRANSFORMS':
      return '#8b5cf6'; // purple
    default:
      return '#6b7280'; // gray
  }
}

// Custom Node Components
function DatasetNode({ data }: { data: any }) {
  return (
    <div
      className={`px-4 py-3 rounded-lg border-2 shadow-md bg-white min-w-[180px] ${
        data.isRoot ? 'border-primary-500' : 'border-blue-400'
      }`}
    >
      <div className="flex items-center gap-2 mb-1">
        <Database className="h-4 w-4 text-blue-600" />
        <span className="text-xs font-semibold text-gray-600">DATASET</span>
      </div>
      <div className="text-sm font-medium text-gray-900">{data.label}</div>
      {data.isRoot && (
        <div className="mt-1 text-xs text-primary-600 font-semibold">ROOT</div>
      )}
    </div>
  );
}

function TransformationNode({ data }: { data: any }) {
  return (
    <div className="px-4 py-3 rounded-lg border-2 border-purple-400 shadow-md bg-white min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <Workflow className="h-4 w-4 text-purple-600" />
        <span className="text-xs font-semibold text-gray-600">TRANSFORM</span>
      </div>
      <div className="text-sm font-medium text-gray-900">{data.label}</div>
    </div>
  );
}

function QueryNode({ data }: { data: any }) {
  return (
    <div className="px-4 py-3 rounded-lg border-2 border-green-400 shadow-md bg-white min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <FileText className="h-4 w-4 text-green-600" />
        <span className="text-xs font-semibold text-gray-600">QUERY</span>
      </div>
      <div className="text-sm font-medium text-gray-900">{data.label}</div>
    </div>
  );
}

function DashboardNode({ data }: { data: any }) {
  return (
    <div className="px-4 py-3 rounded-lg border-2 border-orange-400 shadow-md bg-white min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <BarChart3 className="h-4 w-4 text-orange-600" />
        <span className="text-xs font-semibold text-gray-600">DASHBOARD</span>
      </div>
      <div className="text-sm font-medium text-gray-900">{data.label}</div>
    </div>
  );
}

function MLModelNode({ data }: { data: any }) {
  return (
    <div className="px-4 py-3 rounded-lg border-2 border-pink-400 shadow-md bg-white min-w-[180px]">
      <div className="flex items-center gap-2 mb-1">
        <Table className="h-4 w-4 text-pink-600" />
        <span className="text-xs font-semibold text-gray-600">ML MODEL</span>
      </div>
      <div className="text-sm font-medium text-gray-900">{data.label}</div>
    </div>
  );
}

export default LineageGraph;
