/**
 * Centralized Node Types Definition
 * 
 * Defines all node types for React Flow in a single, stable location.
 * This file is separate from components to prevent HMR from recreating the object.
 */

import {
    ComponentNode,
    ApiNode,
    DataNode,
    FlowNode,
    PageNode,
} from './nodes';
import { SimpleStickyNode } from './SimpleStickyNode';
import { SimpleFrameNode } from './SimpleFrameNode';
import { SimpleTextNode } from './SimpleTextNode';
import { SimpleShapeNode } from './SimpleShapeNode';
import { SimpleImageNode } from './SimpleImageNode';

export const nodeTypes = {
    // Basic drawing nodes (using simple working components)
    'sticky-note': SimpleStickyNode,
    sticky: SimpleStickyNode,
    frame: SimpleFrameNode,
    text: SimpleTextNode,
    'rectangle': SimpleShapeNode,
    'circle': SimpleShapeNode,
    'diamond': SimpleShapeNode,
    image: SimpleImageNode,

    // Component nodes
    component: ComponentNode,
    api: ApiNode,
    data: DataNode,
    flow: FlowNode,
    page: PageNode,

    // Fallbacks
    infrastructure: ComponentNode,
    'backend-api': ApiNode,
} as const;
