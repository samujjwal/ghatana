/**
 * Canvas Accessibility Hook
 *
 * Provides full WCAG 2.2 keyboard navigation for the canvas:
 *   - Tab / Shift+Tab: traverse nodes in order, move DOM focus to the node element
 *   - prefers-reduced-motion: dynamically updates the atom when OS setting changes
 *   - ARIA live announcements for every traversal action
 *
 * DOM focus:
 *   ReactFlow renders node wrappers with [data-id="<nodeId>"]. After updating
 *   selectedNodesAtom, this hook calls .focus() on that wrapper so hardware
 *   keyboard focus and screen-reader focus align with the visual selection.
 *   The wrapper must have tabIndex (set via ReactFlow's `nodesFocusable` prop
 *   or individually via node data — see CanvasWorkspace nodesFocusable prop).
 *
 * @doc.type hook
 * @doc.purpose Canvas accessibility and WCAG 2.2 compliance
 * @doc.layer product
 * @doc.pattern Accessibility
 */

import { useCallback, useRef, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { nodesAtom, selectedNodesAtom, canvasAnnouncementAtom, prefersReducedMotionAtom, prefersDarkModeAtom } from '../workspace';

/**
 * Focus the ReactFlow node wrapper DOM element for the given node ID.
 * ReactFlow renders wrappers with `data-id` attributes and assigns
 * tabIndex when `nodesFocusable` is true on the ReactFlow instance.
 */
function focusNodeWrapper(nodeId: string): void {
    // ReactFlow sets data-id on node wrapper divs
    const wrapper = document.querySelector<HTMLElement>(
        `.react-flow__node[data-id="${CSS.escape(nodeId)}"]`
    );
    if (wrapper) {
        // Ensure the wrapper can receive focus
        if (!wrapper.getAttribute('tabindex')) {
            wrapper.setAttribute('tabindex', '-1');
        }
        wrapper.focus({ preventScroll: false });
    }
}

/** Manages Tab-based focus traversal between canvas nodes. */
export function useCanvasAccessibility() {
    const nodes = useAtomValue(nodesAtom);
    const [selectedNodes, setSelectedNodes] = useAtom(selectedNodesAtom);
    const announce = useSetAtom(canvasAnnouncementAtom);
    const setReducedMotion = useSetAtom(prefersReducedMotionAtom);
    const setPrefersDark = useSetAtom(prefersDarkModeAtom);
    const focusIndexRef = useRef(0);

    // Sync focus index when external selection changes (e.g., clicking a node)
    useEffect(() => {
        if (selectedNodes.length === 1) {
            const idx = nodes.findIndex((n) => n.id === selectedNodes[0]);
            if (idx >= 0) focusIndexRef.current = idx;
        }
    }, [selectedNodes, nodes]);

    // Live prefers-reduced-motion — updates the atom when the OS setting changes
    useEffect(() => {
        if (typeof window === 'undefined') return;
        const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
        const handler = (e: MediaQueryListEvent) => setReducedMotion(e.matches);
        mql.addEventListener('change', handler);
        return () => mql.removeEventListener('change', handler);
    }, [setReducedMotion]);

    // Live dark-mode detection — updates atom on OS media change OR Tailwind class toggle
    useEffect(() => {
        if (typeof window === 'undefined') return;

        const isDark = () =>
            document.documentElement.classList.contains('dark') ||
            window.matchMedia('(prefers-color-scheme: dark)').matches;

        // React to OS-level changes
        const osMql = window.matchMedia('(prefers-color-scheme: dark)');
        const osHandler = () => setPrefersDark(isDark());
        osMql.addEventListener('change', osHandler);

        // React to Tailwind `dark` class being toggled programmatically
        const observer = new MutationObserver(() => setPrefersDark(isDark()));
        observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });

        return () => {
            osMql.removeEventListener('change', osHandler);
            observer.disconnect();
        };
    }, [setPrefersDark]);

    /** Move focus to the next node (Tab) */
    const focusNextNode = useCallback(() => {
        if (nodes.length === 0) return;
        focusIndexRef.current = (focusIndexRef.current + 1) % nodes.length;
        const node = nodes[focusIndexRef.current];
        setSelectedNodes([node.id]);
        focusNodeWrapper(node.id);
        announce(
            `Node ${focusIndexRef.current + 1} of ${nodes.length}: ` +
            `${(node.data as Record<string, unknown>)?.title ?? node.id}`
        );
    }, [nodes, setSelectedNodes, announce]);

    /** Move focus to the previous node (Shift+Tab) */
    const focusPrevNode = useCallback(() => {
        if (nodes.length === 0) return;
        focusIndexRef.current = (focusIndexRef.current - 1 + nodes.length) % nodes.length;
        const node = nodes[focusIndexRef.current];
        setSelectedNodes([node.id]);
        focusNodeWrapper(node.id);
        announce(
            `Node ${focusIndexRef.current + 1} of ${nodes.length}: ` +
            `${(node.data as Record<string, unknown>)?.title ?? node.id}`
        );
    }, [nodes, setSelectedNodes, announce]);

    return { focusNextNode, focusPrevNode };
}
