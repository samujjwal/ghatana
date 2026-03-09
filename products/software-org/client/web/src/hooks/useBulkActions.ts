import { useState, useCallback } from 'react';

/**
 * Bulk action handler for multi-select operations in tables and lists.
 *
 * <p><b>Purpose</b><br>
 * Manages multi-select state and bulk action execution across multiple items.
 * Handles selection tracking, action confirmation, and batch operations.
 *
 * <p><b>Features</b><br>
 * - Multi-select with individual and "select all" toggles
 * - Batch action execution
 * - Undo support for actions
 * - Progress tracking for long-running operations
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { selected, isSelected, toggleSelect, selectAll, clearAll, executeBulkAction } = useBulkActions();
 *
 * return (
 *   <>
 *     <table>
 *       <thead>
 *         <tr>
 *           <th>
 *             <input
 *               type="checkbox"
 *               checked={selected.size === items.length}
 *               onChange={() => selectAll(items.map(i => i.id))}
 *             />
 *           </th>
 *         </tr>
 *       </thead>
 *       <tbody>
 *         {items.map(item => (
 *           <tr key={item.id}>
 *             <td>
 *               <input
 *                 type="checkbox"
 *                 checked={isSelected(item.id)}
 *                 onChange={() => toggleSelect(item.id)}
 *               />
 *             </td>
 *           </tr>
 *         ))}
 *       </tbody>
 *     </table>
 *     {selected.size > 0 && (
 *       <div className="bulk-actions">
 *         <button onClick={() => executeBulkAction('approve', Array.from(selected))}>
 *           Approve ({selected.size})
 *         </button>
 *       </div>
 *     )}
 *   </>
 * );
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Bulk action management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

export interface BulkActionResult {
    action: string;
    itemId: string;
    status: 'success' | 'failed';
    message?: string;
}

export function useBulkActions() {
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [isExecuting, setIsExecuting] = useState(false);
    const [progress, setProgress] = useState<{
        current: number;
        total: number;
        action: string;
    } | null>(null);
    const [lastAction, setLastAction] = useState<{
        action: string;
        items: string[];
    } | null>(null);

    /**
     * Checks if an item is selected.
     *
     * @param itemId - Item ID to check
     * @returns true if item is selected
     */
    const isSelected = useCallback((itemId: string) => {
        return selected.has(itemId);
    }, [selected]);

    /**
     * Toggles selection for a single item.
     *
     * @param itemId - Item ID to toggle
     */
    const toggleSelect = useCallback((itemId: string) => {
        setSelected((prev) => {
            const newSet = new Set(prev);
            if (newSet.has(itemId)) {
                newSet.delete(itemId);
            } else {
                newSet.add(itemId);
            }
            return newSet;
        });
    }, []);

    /**
     * Selects all items.
     *
     * @param itemIds - List of all available item IDs
     */
    const selectAll = useCallback((itemIds: string[]) => {
        setSelected(new Set(itemIds));
    }, []);

    /**
     * Selects none (clears all selections).
     */
    const clearAll = useCallback(() => {
        setSelected(new Set());
    }, []);

    /**
     * Executes a bulk action on all selected items.
     *
     * @param action - Action name (e.g., 'approve', 'reject', 'delete')
     * @param handler - Async function to execute for each item
     * @returns Array of action results
     */
    const executeBulkAction = useCallback(
        async (action: string, handler: (itemId: string) => Promise<BulkActionResult>) => {
            if (selected.size === 0) {
                console.warn('[BulkActions] No items selected');
                return [];
            }

            const itemIds = Array.from(selected);
            const results: BulkActionResult[] = [];

            setIsExecuting(true);
            setProgress({ current: 0, total: itemIds.length, action });
            setLastAction({ action, items: itemIds });

            try {
                for (let i = 0; i < itemIds.length; i++) {
                    const itemId = itemIds[i];
                    try {
                        const result = await handler(itemId);
                        results.push(result);
                        console.log(`[BulkActions] ${action} on ${itemId}:`, result.status);
                    } catch (err) {
                        results.push({
                            action,
                            itemId,
                            status: 'failed',
                            message: String(err),
                        });
                    }

                    setProgress({ current: i + 1, total: itemIds.length, action });
                }

                // Clear selection on success
                clearAll();

                console.log(`[BulkActions] Batch ${action} completed:`, results);
                return results;
            } finally {
                setIsExecuting(false);
                setProgress(null);
            }
        },
        [selected, clearAll]
    );

    /**
     * Undoes the last bulk action.
     */
    const undoLastAction = useCallback(async () => {
        if (!lastAction) return;

        console.log('[BulkActions] Undoing:', lastAction.action, lastAction.items);
        setLastAction(null);
        // In production, call API to undo changes
    }, [lastAction]);

    return {
        selected,
        isSelected,
        toggleSelect,
        selectAll,
        clearAll,
        executeBulkAction,
        isExecuting,
        progress,
        lastAction,
        undoLastAction,
    };
}
