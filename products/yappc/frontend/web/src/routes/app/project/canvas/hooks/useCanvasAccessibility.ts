/**
 * @doc.type hook
 * @doc.purpose Manages accessibility checking and panel state
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import type { Node } from '@xyflow/react';

interface UseCanvasAccessibilityOptions {
  nodes: Node[];
}

/**
 * Hook to manage canvas accessibility checking
 */
export function useCanvasAccessibility({ nodes }: UseCanvasAccessibilityOptions) {
  const [accessibilityPanelOpen, setAccessibilityPanelOpen] = useState(false);
  const [accessibilityIssues, setAccessibilityIssues] = useState<string[]>([]);

  const runAccessibilityCheck = useCallback(() => {
    const issues = nodes.length
      ? nodes.map((node) => {
          const label =
            typeof node.data === 'object' &&
            node.data !== null &&
            'label' in node.data &&
            typeof node.data.label === 'string'
              ? node.data.label
              : node.id;
          return `Element "${label}" is missing a label`;
        })
      : ['No elements found. Add components to run accessibility checks.'];
    setAccessibilityIssues(issues);
    setAccessibilityPanelOpen(true);
  }, [nodes]);

  const closeAccessibilityPanel = useCallback(() => {
    setAccessibilityPanelOpen(false);
  }, []);

  return {
    accessibilityPanelOpen,
    setAccessibilityPanelOpen,
    accessibilityIssues,
    runAccessibilityCheck,
    closeAccessibilityPanel,
  };
}
