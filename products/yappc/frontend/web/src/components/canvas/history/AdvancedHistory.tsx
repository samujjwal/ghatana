/**
 * Phase 3: Advanced Undo/Redo System with Branching
 * Sophisticated history management with branch support and conflict resolution
 */

import React, { useCallback, useState, useRef, useMemo } from 'react';

import type { BaseItem } from '../core/types';

// History system types
/**
 *
 */
export interface HistoryEntry<T extends BaseItem> {
    id: string;
    timestamp: number;
    action: HistoryAction<T>;
    description: string;
    userId?: string;
    metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface HistoryAction<T extends BaseItem> {
    type: 'create' | 'update' | 'delete' | 'batch' | 'move' | 'transform';
    items: T[];
    previousItems?: T[];
    changes?: Array<{
        itemId: string;
        before: Partial<T>;
        after: Partial<T>;
    }>;
}

/**
 *
 */
export interface HistoryBranch<T extends BaseItem> {
    id: string;
    name: string;
    parentBranchId?: string;
    branchPoint: string; // Entry ID where this branch diverged
    entries: HistoryEntry<T>[];
    createdAt: number;
    isActive: boolean;
}

/**
 *
 */
export interface HistoryState<T extends BaseItem> {
    branches: Map<string, HistoryBranch<T>>;
    activeBranchId: string;
    currentPosition: number; // Position in active branch
    maxHistorySize: number;
    enableBranching: boolean;
}

// Advanced history management hook
/**
 *
 */
export function useAdvancedHistory<T extends BaseItem>(
    initialItems: T[],
    options: {
        maxHistorySize?: number;
        enableBranching?: boolean;
        enableCompression?: boolean;
        autoSave?: boolean;
    } = {}
): {
    items: T[];
    historyState: HistoryState<T>;
    actions: {
        recordAction: (action: HistoryAction<T>, description: string) => void;
        undo: () => boolean;
        redo: () => boolean;
        createBranch: (name: string) => string;
        switchBranch: (branchId: string) => boolean;
        mergeBranch: (sourceBranchId: string, targetBranchId: string) => boolean;
        deleteBranch: (branchId: string) => boolean;
        getHistory: () => HistoryEntry<T>[];
        getBranches: () => HistoryBranch<T>[];
        canUndo: () => boolean;
        canRedo: () => boolean;
        reset: () => void;
        compressHistory: () => void;
    };
} {
    const {
        maxHistorySize = 100,
        enableBranching = false,
        enableCompression = true,
        autoSave = false
    } = options;

    // Initialize with main branch
    const [items, setItems] = useState<T[]>(initialItems);
    const [historyState, setHistoryState] = useState<HistoryState<T>>(() => {
        const mainBranch: HistoryBranch<T> = {
            id: 'main',
            name: 'Main',
            entries: [],
            createdAt: Date.now(),
            isActive: true
        };

        return {
            branches: new Map([['main', mainBranch]]),
            activeBranchId: 'main',
            currentPosition: -1,
            maxHistorySize,
            enableBranching
        };
    });

    const entryIdCounter = useRef(0);

    // Get current active branch
    const activeBranch = useMemo(() => {
        return historyState.branches.get(historyState.activeBranchId);
    }, [historyState.branches, historyState.activeBranchId]);

    // Record a new action in history
    const recordAction = useCallback((action: HistoryAction<T>, description: string) => {
        const entry: HistoryEntry<T> = {
            id: `entry_${entryIdCounter.current++}`,
            timestamp: Date.now(),
            action,
            description
        };

        setHistoryState(prev => {
            const branch = prev.branches.get(prev.activeBranchId);
            if (!branch) return prev;

            // If we're not at the end of history, truncate future entries
            const newEntries = branch.entries.slice(0, prev.currentPosition + 1);
            newEntries.push(entry);

            // Limit history size
            if (newEntries.length > prev.maxHistorySize) {
                newEntries.shift();
            }

            const updatedBranch: HistoryBranch<T> = {
                ...branch,
                entries: newEntries
            };

            return {
                ...prev,
                branches: new Map(prev.branches).set(prev.activeBranchId, updatedBranch),
                currentPosition: newEntries.length - 1
            };
        });

        // Apply the action to update items
        applyHistoryAction(action, 'forward');
    }, []);

    // Apply history action to items
    const applyHistoryAction = useCallback((action: HistoryAction<T>, direction: 'forward' | 'backward') => {
        setItems(prevItems => {
            switch (action.type) {
                case 'create':
                    return direction === 'forward'
                        ? [...prevItems, ...action.items]
                        : prevItems.filter(item => !action.items.find(actionItem => actionItem.id === item.id));

                case 'delete':
                    return direction === 'forward'
                        ? prevItems.filter(item => !action.items.find(actionItem => actionItem.id === item.id))
                        : [...prevItems, ...(action.previousItems || [])];

                case 'update':
                    if (direction === 'forward') {
                        return prevItems.map(item => {
                            const updatedItem = action.items.find(actionItem => actionItem.id === item.id);
                            return updatedItem ? updatedItem : item;
                        });
                    } else {
                        return prevItems.map(item => {
                            const previousItem = action.previousItems?.find(prevItem => prevItem.id === item.id);
                            return previousItem ? previousItem : item;
                        });
                    }

                case 'batch':
                    // Handle multiple operations
                    let result = prevItems;
                    action.changes?.forEach(change => {
                        if (direction === 'forward') {
                            result = result.map(item =>
                                item.id === change.itemId ? { ...item, ...change.after } : item
                            );
                        } else {
                            result = result.map(item =>
                                item.id === change.itemId ? { ...item, ...change.before } : item
                            );
                        }
                    });
                    return result;

                default:
                    return prevItems;
            }
        });
    }, []);

    // Undo last action
    const undo = useCallback((): boolean => {
        if (!activeBranch || historyState.currentPosition < 0) {
            return false;
        }

        const entry = activeBranch.entries[historyState.currentPosition];
        applyHistoryAction(entry.action, 'backward');

        setHistoryState(prev => ({
            ...prev,
            currentPosition: prev.currentPosition - 1
        }));

        return true;
    }, [activeBranch, historyState.currentPosition, applyHistoryAction]);

    // Redo next action
    const redo = useCallback((): boolean => {
        if (!activeBranch || historyState.currentPosition >= activeBranch.entries.length - 1) {
            return false;
        }

        const entry = activeBranch.entries[historyState.currentPosition + 1];
        applyHistoryAction(entry.action, 'forward');

        setHistoryState(prev => ({
            ...prev,
            currentPosition: prev.currentPosition + 1
        }));

        return true;
    }, [activeBranch, historyState.currentPosition, applyHistoryAction]);

    // Create new branch
    const createBranch = useCallback((name: string): string => {
        if (!enableBranching || !activeBranch) {
            return '';
        }

        const branchId = `branch_${Date.now()}`;
        const currentEntry = activeBranch.entries[historyState.currentPosition];

        const newBranch: HistoryBranch<T> = {
            id: branchId,
            name,
            parentBranchId: historyState.activeBranchId,
            branchPoint: currentEntry?.id || '',
            entries: [...activeBranch.entries.slice(0, historyState.currentPosition + 1)],
            createdAt: Date.now(),
            isActive: false
        };

        setHistoryState(prev => ({
            ...prev,
            branches: new Map(prev.branches).set(branchId, newBranch)
        }));

        return branchId;
    }, [enableBranching, activeBranch, historyState]);

    // Switch to different branch
    const switchBranch = useCallback((branchId: string): boolean => {
        const targetBranch = historyState.branches.get(branchId);
        if (!targetBranch) return false;

        // Set all branches to inactive
        const updatedBranches = new Map(historyState.branches);
        updatedBranches.forEach(branch => {
            updatedBranches.set(branch.id, { ...branch, isActive: false });
        });

        // Set target branch as active
        updatedBranches.set(branchId, { ...targetBranch, isActive: true });

        setHistoryState(prev => ({
            ...prev,
            branches: updatedBranches,
            activeBranchId: branchId,
            currentPosition: targetBranch.entries.length - 1
        }));

        // Reconstruct items from branch history
        reconstructItemsFromBranch(targetBranch);

        return true;
    }, [historyState.branches]);

    // Reconstruct items from branch history
    const reconstructItemsFromBranch = useCallback((branch: HistoryBranch<T>) => {
        let reconstructedItems: T[] = initialItems;

        branch.entries.forEach(entry => {
            switch (entry.action.type) {
                case 'create':
                    reconstructedItems = [...reconstructedItems, ...entry.action.items];
                    break;
                case 'delete':
                    reconstructedItems = reconstructedItems.filter(item =>
                        !entry.action.items.find(actionItem => actionItem.id === item.id)
                    );
                    break;
                case 'update':
                    reconstructedItems = reconstructedItems.map(item => {
                        const updatedItem = entry.action.items.find(actionItem => actionItem.id === item.id);
                        return updatedItem ? updatedItem : item;
                    });
                    break;
            }
        });

        setItems(reconstructedItems);
    }, [initialItems]);

    // Merge branch into another branch
    const mergeBranch = useCallback((sourceBranchId: string, targetBranchId: string): boolean => {
        const sourceBranch = historyState.branches.get(sourceBranchId);
        const targetBranch = historyState.branches.get(targetBranchId);

        if (!sourceBranch || !targetBranch) return false;

        // Find divergence point
        const branchPointEntry = targetBranch.entries.find(entry => entry.id === sourceBranch.branchPoint);
        if (!branchPointEntry) return false;

        const branchPointIndex = targetBranch.entries.indexOf(branchPointEntry);
        const sourceChanges = sourceBranch.entries.slice(branchPointIndex + 1);

        // Create merge entry
        const mergeEntry: HistoryEntry<T> = {
            id: `merge_${Date.now()}`,
            timestamp: Date.now(),
            action: {
                type: 'batch',
                items: [],
                changes: sourceChanges.flatMap(entry =>
                    entry.action.changes || []
                )
            },
            description: `Merge branch '${sourceBranch.name}' into '${targetBranch.name}'`
        };

        // Add merge to target branch
        const updatedTargetBranch: HistoryBranch<T> = {
            ...targetBranch,
            entries: [...targetBranch.entries, mergeEntry]
        };

        setHistoryState(prev => ({
            ...prev,
            branches: new Map(prev.branches).set(targetBranchId, updatedTargetBranch)
        }));

        return true;
    }, [historyState.branches]);

    // Delete branch
    const deleteBranch = useCallback((branchId: string): boolean => {
        if (branchId === 'main' || branchId === historyState.activeBranchId) {
            return false; // Cannot delete main branch or active branch
        }

        setHistoryState(prev => {
            const newBranches = new Map(prev.branches);
            newBranches.delete(branchId);
            return { ...prev, branches: newBranches };
        });

        return true;
    }, [historyState.activeBranchId]);

    // Get current history
    const getHistory = useCallback((): HistoryEntry<T>[] => {
        return activeBranch?.entries || [];
    }, [activeBranch]);

    // Get all branches
    const getBranches = useCallback((): HistoryBranch<T>[] => {
        return Array.from(historyState.branches.values());
    }, [historyState.branches]);

    // Check if can undo/redo
    const canUndo = useCallback(() => historyState.currentPosition >= 0, [historyState.currentPosition]);
    const canRedo = useCallback(() => {
        return activeBranch ? historyState.currentPosition < activeBranch.entries.length - 1 : false;
    }, [activeBranch, historyState.currentPosition]);

    // Reset history
    const reset = useCallback(() => {
        setItems(initialItems);
        setHistoryState(prev => ({
            ...prev,
            branches: new Map([['main', {
                id: 'main',
                name: 'Main',
                entries: [],
                createdAt: Date.now(),
                isActive: true
            }]]),
            activeBranchId: 'main',
            currentPosition: -1
        }));
    }, [initialItems]);

    // Compress history by removing redundant entries
    const compressHistory = useCallback(() => {
        if (!activeBranch || !enableCompression) return;

        const compressedEntries: HistoryEntry<T>[] = [];
        const itemStateMap = new Map<string, T>();

        // Process entries and compress consecutive updates to same items
        activeBranch.entries.forEach((entry, index) => {
            if (entry.action.type === 'update') {
                entry.action.items.forEach(item => {
                    itemStateMap.set(item.id, item);
                });

                // Only add entry if it's the last update to these items or if next entry affects different items
                const nextEntry = activeBranch.entries[index + 1];
                const shouldKeep = !nextEntry ||
                    nextEntry.action.type !== 'update' ||
                    !entry.action.items.some(item =>
                        nextEntry.action.items.some(nextItem => nextItem.id === item.id)
                    );

                if (shouldKeep) {
                    compressedEntries.push({
                        ...entry,
                        action: {
                            ...entry.action,
                            items: Array.from(itemStateMap.values()).filter(item =>
                                entry.action.items.some(actionItem => actionItem.id === item.id)
                            )
                        }
                    });
                }
            } else {
                compressedEntries.push(entry);
                // Clear state for non-update operations
                itemStateMap.clear();
            }
        });

        setHistoryState(prev => {
            const updatedBranch = { ...activeBranch, entries: compressedEntries };
            return {
                ...prev,
                branches: new Map(prev.branches).set(prev.activeBranchId, updatedBranch),
                currentPosition: Math.min(prev.currentPosition, compressedEntries.length - 1)
            };
        });
    }, [activeBranch, enableCompression]);

    return {
        items,
        historyState,
        actions: {
            recordAction,
            undo,
            redo,
            createBranch,
            switchBranch,
            mergeBranch,
            deleteBranch,
            getHistory,
            getBranches,
            canUndo,
            canRedo,
            reset,
            compressHistory
        }
    };
}

// History visualization component
export const HistoryVisualization: React.FC<{
    branches: HistoryBranch<unknown>[];
    activeBranchId: string;
    currentPosition: number;
    onSwitchBranch: (branchId: string) => void;
    onCreateBranch: (name: string) => void;
}> = ({ branches, activeBranchId, currentPosition, onSwitchBranch, onCreateBranch }) => {
    const [newBranchName, setNewBranchName] = useState('');

    return (
        <div style={{ padding: '16px', background: '#f5f5f5', borderRadius: '4px' }}>
            <div style={{ marginBottom: '12px', fontSize: '14px', fontWeight: 'bold' }}>
                History Branches
            </div>

            {branches.map(branch => (
                <div key={branch.id} style={{ marginBottom: '8px' }}>
                    <div
                        onClick={() => onSwitchBranch(branch.id)}
                        style={{
                            padding: '8px',
                            background: branch.isActive ? '#007acc' : '#fff',
                            color: branch.isActive ? '#fff' : '#333',
                            border: '1px solid #ddd',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '12px'
                        }}
                    >
                        <div>{branch.name} ({branch.entries.length} entries)</div>
                        {branch.isActive && (
                            <div>Position: {currentPosition + 1}/{branch.entries.length}</div>
                        )}
                    </div>
                </div>
            ))}

            <div style={{ marginTop: '12px' }}>
                <input
                    type="text"
                    placeholder="New branch name"
                    value={newBranchName}
                    onChange={(e) => setNewBranchName(e.target.value)}
                    style={{
                        padding: '4px',
                        marginRight: '8px',
                        border: '1px solid #ddd',
                        borderRadius: '2px',
                        fontSize: '12px'
                    }}
                />
                <button
                    onClick={() => {
                        if (newBranchName.trim()) {
                            onCreateBranch(newBranchName.trim());
                            setNewBranchName('');
                        }
                    }}
                    style={{
                        padding: '4px 8px',
                        background: '#007acc',
                        color: 'white',
                        border: 'none',
                        borderRadius: '2px',
                        cursor: 'pointer',
                        fontSize: '12px'
                    }}
                >
                    Create Branch
                </button>
            </div>
        </div>
    );
};