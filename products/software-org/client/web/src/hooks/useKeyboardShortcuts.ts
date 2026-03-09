import { useEffect, useCallback } from 'react';

/**
 * Hook for managing keyboard shortcuts in HITL Console and other pages.
 *
 * <p><b>Purpose</b><br>
 * Provides a reusable keyboard shortcut handler with pattern matching and
 * optional modifiers. Automatically cleans up listeners on unmount.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * useKeyboardShortcuts({
 *   'A': () => handleApprove(),
 *   'D': () => handleDefer(),
 *   'R': () => handleReject(),
 *   'Shift+S': () => handleSave(), // with modifiers
 * });
 * }</pre>
 *
 * @param shortcuts - Object mapping shortcut strings to handler functions
 * @param enabled - Whether shortcuts are active (default: true)
 * @param target - Target element for event listener (default: window)
 *
 * @doc.type hook
 * @doc.purpose Keyboard shortcut management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useKeyboardShortcuts(
    shortcuts: Record<string, () => void>,
    enabled: boolean = true,
    target: EventTarget = window
) {
    const handleKeyDown = useCallback(
        (e: KeyboardEvent) => {
            if (!enabled) return;

            // Build shortcut string from key and modifiers
            const parts: string[] = [];
            if (e.ctrlKey) parts.push('Ctrl');
            if (e.metaKey) parts.push('Meta');
            if (e.altKey) parts.push('Alt');
            if (e.shiftKey) parts.push('Shift');
            parts.push(e.key.toUpperCase());
            const shortcutString = parts.join('+');

            // Also check simple key for convenience (e.g., 'A' instead of just 'A')
            const simpleKey = e.key.toUpperCase();

            const handler = shortcuts[shortcutString] || shortcuts[simpleKey];
            if (handler) {
                e.preventDefault();
                handler();
            }
        },
        [shortcuts, enabled]
    );

    useEffect(() => {
        if (!enabled) return;

        target.addEventListener('keydown', handleKeyDown as EventListener);
        return () => {
            target.removeEventListener('keydown', handleKeyDown as EventListener);
        };
    }, [handleKeyDown, enabled, target]);
}

/**
 * Specialized hook for HITL Console shortcuts (Approve/Defer/Reject).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { shortcuts, isActive } = useHitlShortcuts(
 *   selectedActionId,
 *   handleApprove,
 *   handleDefer,
 *   handleReject
 * );
 * }</pre>
 *
 * @param enabled - Whether to enable shortcuts (only when action is selected)
 * @param onApprove - Handler for 'A' key
 * @param onDefer - Handler for 'D' key
 * @param onReject - Handler for 'R' key
 *
 * @doc.type hook
 * @doc.purpose HITL-specific keyboard shortcuts
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useHitlShortcuts(
    enabled: boolean,
    onApprove: () => void,
    onDefer: () => void,
    onReject: () => void
) {
    useKeyboardShortcuts(
        {
            'A': onApprove,
            'D': onDefer,
            'R': onReject,
        },
        enabled
    );

    return {
        isActive: enabled,
        shortcuts: {
            approve: 'A',
            defer: 'D',
            reject: 'R',
        },
    };
}
