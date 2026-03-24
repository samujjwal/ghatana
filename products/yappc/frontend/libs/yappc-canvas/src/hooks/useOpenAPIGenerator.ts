/**
 * useOpenAPIGenerator Hook
 * 
 * React hook for managing OpenAPI specification generation.
 * Handles dialog state, node filtering, and export actions.
 * 
 * @doc.type hook
 * @doc.purpose OpenAPI generation state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';
import { useNodes } from '@xyflow/react';
import type { Node } from '@xyflow/react';
import type { APINodeData } from '../services/OpenAPIService';

/**
 * useOpenAPIGenerator options
 */
export interface UseOpenAPIGeneratorOptions {
    /** Filter nodes by type */
    nodeType?: string;
    /** Auto-open dialog on node selection */
    autoOpen?: boolean;
}

/**
 * useOpenAPIGenerator result
 */
export interface UseOpenAPIGeneratorResult {
    /** Whether dialog is open */
    isOpen: boolean;
    /** Open dialog */
    open: () => void;
    /** Close dialog */
    close: () => void;
    /** API nodes available for generation */
    apiNodes: Node<APINodeData>[];
    /** Whether any API nodes exist */
    hasAPINodes: boolean;
    /** Number of API nodes */
    apiNodeCount: number;
    /** Generate spec for specific nodes */
    generateForNodes: (nodeIds: string[]) => void;
    /** Selected node IDs for generation */
    selectedNodeIds: string[];
}

/**
 * Check if node is an API node
 */
function isAPINode(node: Node): node is Node<APINodeData> {
    const data = node.data as APINodeData;
    return Boolean(data.apiPath && data.method);
}

/**
 * useOpenAPIGenerator hook
 */
export function useOpenAPIGenerator(
    options: UseOpenAPIGeneratorOptions = {}
): UseOpenAPIGeneratorResult {
    const { nodeType = 'api', autoOpen = false } = options;

    const [isOpen, setIsOpen] = useState(false);
    const [selectedNodeIds, setSelectedNodeIds] = useState<string[]>([]);

    const allNodes = useNodes();

    /**
     * Filter API nodes
     */
    const apiNodes = useMemo(() => {
        return allNodes.filter((node) => {
            // Type filter
            if (nodeType && node.type !== nodeType) {
                return false;
            }

            // Must have API properties
            return isAPINode(node);
        }) as Node<APINodeData>[];
    }, [allNodes, nodeType]);

    /**
     * Get selected API nodes or all if none selected
     */
    const availableNodes = useMemo(() => {
        if (selectedNodeIds.length === 0) {
            return apiNodes;
        }

        return apiNodes.filter((node) => selectedNodeIds.includes(node.id));
    }, [apiNodes, selectedNodeIds]);

    /**
     * Open dialog
     */
    const open = useCallback(() => {
        setIsOpen(true);
    }, []);

    /**
     * Close dialog
     */
    const close = useCallback(() => {
        setIsOpen(false);
        setSelectedNodeIds([]);
    }, []);

    /**
     * Generate for specific nodes
     */
    const generateForNodes = useCallback(
        (nodeIds: string[]) => {
            setSelectedNodeIds(nodeIds);
            if (autoOpen) {
                setIsOpen(true);
            }
        },
        [autoOpen]
    );

    return {
        isOpen,
        open,
        close,
        apiNodes: availableNodes,
        hasAPINodes: apiNodes.length > 0,
        apiNodeCount: apiNodes.length,
        generateForNodes,
        selectedNodeIds,
    };
}
