/**
 * Role Inheritance Tree Component
 * 
 * Export all public components and types
 */

export { RoleInheritanceTree } from './RoleInheritanceTree';
export { RoleNode } from './RoleNode';
export { InheritanceLink } from './InheritanceLink';
export { PermissionTooltip } from './PermissionTooltip';

export type {
    RoleInheritanceTreeProps,
    PersonaTreeNode,
    RoleNodeData,
    RoleFlowNode,
    InheritanceEdge,
} from './types';

export {
    buildInheritanceTree,
    treeToNodes,
    treeToEdges,
    exportAsJSON,
    calculateTreeStats,
} from './utils';
