/**
 * Centralized Node Types Definition
 * 
 * Defines all node types for React Flow in a single, stable location.
 * This file is separate from components to prevent HMR from recreating the object.
 */

import type { NodeTypes } from '@xyflow/react';

import {
    ComponentNode,
    ApiNode,
    DataNode,
    FlowNode,
    PageNode,
    PageDesignerNode,
} from './nodes';
import { SimpleStickyNode } from './SimpleStickyNode';
import { SimpleFrameNode } from './SimpleFrameNode';
import { SimpleTextNode } from './SimpleTextNode';
import { SimpleShapeNode } from './SimpleShapeNode';
import { SimpleImageNode } from './SimpleImageNode';

const asNodeComponent = (component: unknown): NonNullable<NodeTypes[string]> => {
    return component as NonNullable<NodeTypes[string]>;
};

export const nodeTypes: NodeTypes = {
    // Basic drawing nodes (using simple working components)
    'sticky-note': asNodeComponent(SimpleStickyNode),
    sticky: asNodeComponent(SimpleStickyNode),
    frame: asNodeComponent(SimpleFrameNode),
    text: asNodeComponent(SimpleTextNode),
    'rectangle': asNodeComponent(SimpleShapeNode),
    'circle': asNodeComponent(SimpleShapeNode),
    'diamond': asNodeComponent(SimpleShapeNode),
    image: asNodeComponent(SimpleImageNode),

    // Component nodes
    component: asNodeComponent(ComponentNode),
    api: asNodeComponent(ApiNode),
    data: asNodeComponent(DataNode),
    flow: asNodeComponent(FlowNode),
    page: asNodeComponent(PageNode),
    'page-designer': asNodeComponent(PageDesignerNode),

    // Fallbacks
    infrastructure: asNodeComponent(ComponentNode),
    'backend-api': asNodeComponent(ApiNode),
};
