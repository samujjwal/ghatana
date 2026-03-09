/**
 * Canvas Registry Provider
 * 
 * Provides typed node and edge type registrations for ReactFlow.
 * All custom node/edge components are registered here.
 * 
 * @doc.type component
 * @doc.purpose Canvas element type registry
 * @doc.layer product
 * @doc.pattern Registry
 */

import React, { createContext, useContext, useMemo } from 'react';
import type { NodeTypes, EdgeTypes, NodeProps, EdgeProps } from '@xyflow/react';
import { ArtifactNode } from '../nodes/ArtifactNode';
import { SimpleUnifiedNode } from '../unified/SimpleUnifiedNode';
import { DiagramNode } from '../nodes/DiagramNode';
import { MonacoNode } from '../nodes/MonacoNode';
import { GroupNode } from '../nodes/GroupNode';
import { DependencyEdge } from '../edges';

interface CanvasRegistryContextType {
  nodeTypes: NodeTypes;
  edgeTypes: EdgeTypes;
}

const CanvasRegistryContext = createContext<CanvasRegistryContextType | null>(null);

export const useCanvasRegistry = () => {
  const context = useContext(CanvasRegistryContext);
  if (!context) {
    throw new Error('useCanvasRegistry must be used within a CanvasRegistryProvider');
  }
  return context;
};

interface CanvasRegistryProviderProps {
  children: React.ReactNode;
  additionalNodeTypes?: NodeTypes;
  additionalEdgeTypes?: EdgeTypes;
}

export const CanvasRegistryProvider: React.FC<CanvasRegistryProviderProps> = ({ 
  children,
  additionalNodeTypes = {},
  additionalEdgeTypes = {}
}) => {
  const nodeTypes: NodeTypes = useMemo(() => ({
    artifact: ArtifactNode as React.ComponentType<NodeProps>,
    simpleUnified: SimpleUnifiedNode as React.ComponentType<NodeProps>,
    diagram: DiagramNode as React.ComponentType<NodeProps>,
    monaco: MonacoNode as React.ComponentType<NodeProps>,
    group: GroupNode as React.ComponentType<NodeProps>,
    ...additionalNodeTypes
  }), [additionalNodeTypes]);

  const edgeTypes: EdgeTypes = useMemo(() => ({
    dependency: DependencyEdge as React.ComponentType<EdgeProps>,
    ...additionalEdgeTypes
  }), [additionalEdgeTypes]);

  return (
    <CanvasRegistryContext.Provider value={{ nodeTypes, edgeTypes }}>
      {children}
    </CanvasRegistryContext.Provider>
  );
};
