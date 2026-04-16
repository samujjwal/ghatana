/**
 * Node Types Index
 *
 * Exports all custom node types for the unified canvas
 *
 * @doc.type module
 * @doc.purpose Central export for all node components
 * @doc.layer canvas/nodes
 * @doc.pattern Barrel
 */

// Core nodes (existing - in parent directory)
export { default as LineNode } from '../LineNode';
export type { LineNodeData } from '../LineNode';

export { default as ArrowNode } from '../ArrowNode';
export type { ArrowNodeData } from '../ArrowNode';

// AFFiNE-style nodes (new)
export {
  ConnectorNode,
  default as ConnectorNodeDefault,
} from './ConnectorNode';
export type { ConnectorNodeData } from './ConnectorNode';

export { FrameNode, default as FrameNodeDefault } from './FrameNode';
export type { FrameNodeData } from './FrameNode';

export { MindmapNode, default as MindmapNodeDefault } from './MindmapNode';
export type {
  MindmapNodeData,
  MindmapStyle,
  MindmapLayoutDirection,
} from './MindmapNode';

export { EmbedNode, default as EmbedNodeDefault } from './EmbedNode';
export type { EmbedNodeData, EmbedType } from './EmbedNode';

export { ImageNode, default as ImageNodeDefault } from './ImageNode';
export type { ImageNodeData } from './ImageNode';

export { CircleNode } from './CircleNode';
export type { CircleNodeData } from './CircleNode';

export { DiamondNode } from './DiamondNode';
export type { DiamondNodeData } from './DiamondNode';

export { TaskNode } from './TaskNode';
export type { TaskNodeData } from './TaskNode';

export { RectangleNode } from './RectangleNode';
export type { RectangleNodeData } from './RectangleNode';

// Node types registry for ReactFlow
import LineNode from '../LineNode';
import ArrowNode from '../ArrowNode';
import { ConnectorNode } from './ConnectorNode';
import { FrameNode } from './FrameNode';
import { MindmapNode } from './MindmapNode';
import { EmbedNode } from './EmbedNode';
import { ImageNode } from './ImageNode';
import { CircleNode } from './CircleNode';
import { DiamondNode } from './DiamondNode';
import { TaskNode } from './TaskNode';
import { RectangleNode } from './RectangleNode';

export const customNodeTypes = {
  line: LineNode,
  arrow: ArrowNode,
  connector: ConnectorNode,
  frame: FrameNode,
  mindmap: MindmapNode,
  embed: EmbedNode,
  image: ImageNode,
  circle: CircleNode,
  diamond: DiamondNode,
  task: TaskNode,
  rectangle: RectangleNode,
} as const;

export type CustomNodeType = keyof typeof customNodeTypes;
