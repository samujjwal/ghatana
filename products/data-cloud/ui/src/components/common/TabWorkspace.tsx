/**
 * TabWorkspace Component
 * 
 * Multi-tabbed workspace for managing multiple open items.
 * Supports tab reordering, closing, and persistence.
 * 
 * @doc.type component
 * @doc.purpose Multi-tab workspace management
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useState, useCallback, useRef } from 'react';
import { X, Plus, MoreHorizontal, Pin, PinOff } from 'lucide-react';
import { cn, textStyles, bgStyles } from '../../lib/theme';

/**
 * Tab item interface
 */
export interface TabItem {
    id: string;
    title: string;
    icon?: React.ReactNode;
    isPinned?: boolean;
    isDirty?: boolean;
    closable?: boolean;
    data?: unknown;
}

/**
 * Tab context menu action
 */
export interface TabContextAction {
    id: string;
    label: string;
    icon?: React.ReactNode;
    onClick: (tab: TabItem) => void;
    disabled?: boolean;
}

interface TabWorkspaceProps {
    tabs: TabItem[];
    activeTabId: string;
    onTabChange: (tabId: string) => void;
    onTabClose?: (tabId: string) => void;
    onTabReorder?: (tabs: TabItem[]) => void;
    onNewTab?: () => void;
    onTabPin?: (tabId: string, pinned: boolean) => void;
    contextActions?: TabContextAction[];
    maxTabs?: number;
    children: (activeTab: TabItem | undefined) => React.ReactNode;
    className?: string;
}

/**
 * TabWorkspace Component
 */
export function TabWorkspace({
    tabs,
    activeTabId,
    onTabChange,
    onTabClose,
    onTabReorder,
    onNewTab,
    onTabPin,
    contextActions,
    maxTabs = 20,
    children,
    className,
}: TabWorkspaceProps): React.ReactElement {
    const [contextMenuTab, setContextMenuTab] = useState<string | null>(null);
    const [contextMenuPosition, setContextMenuPosition] = useState({ x: 0, y: 0 });
    const [draggedTab, setDraggedTab] = useState<string | null>(null);
    const tabsRef = useRef<HTMLDivElement>(null);

    const activeTab = tabs.find((t) => t.id === activeTabId);

    // Handle tab close
    const handleClose = useCallback((e: React.MouseEvent, tabId: string) => {
        e.stopPropagation();
        onTabClose?.(tabId);
    }, [onTabClose]);

    // Handle context menu
    const handleContextMenu = useCallback((e: React.MouseEvent, tabId: string) => {
        e.preventDefault();
        setContextMenuTab(tabId);
        setContextMenuPosition({ x: e.clientX, y: e.clientY });
    }, []);

    // Close context menu
    const closeContextMenu = useCallback(() => {
        setContextMenuTab(null);
    }, []);

    // Handle drag start
    const handleDragStart = useCallback((e: React.DragEvent, tabId: string) => {
        setDraggedTab(tabId);
        e.dataTransfer.effectAllowed = 'move';
    }, []);

    // Handle drag over
    const handleDragOver = useCallback((e: React.DragEvent, targetTabId: string) => {
        e.preventDefault();
        if (!draggedTab || draggedTab === targetTabId) return;

        const draggedIndex = tabs.findIndex((t) => t.id === draggedTab);
        const targetIndex = tabs.findIndex((t) => t.id === targetTabId);

        if (draggedIndex === -1 || targetIndex === -1) return;

        const newTabs = [...tabs];
        const [removed] = newTabs.splice(draggedIndex, 1);
        newTabs.splice(targetIndex, 0, removed);

        onTabReorder?.(newTabs);
    }, [draggedTab, tabs, onTabReorder]);

    // Handle drag end
    const handleDragEnd = useCallback(() => {
        setDraggedTab(null);
    }, []);

    // Handle pin toggle
    const handlePinToggle = useCallback((tabId: string) => {
        const tab = tabs.find((t) => t.id === tabId);
        if (tab) {
            onTabPin?.(tabId, !tab.isPinned);
        }
        closeContextMenu();
    }, [tabs, onTabPin, closeContextMenu]);

    // Sort tabs: pinned first, then by order
    const sortedTabs = [...tabs].sort((a, b) => {
        if (a.isPinned && !b.isPinned) return -1;
        if (!a.isPinned && b.isPinned) return 1;
        return 0;
    });

    return (
        <div className={cn('flex flex-col h-full', className)}>
            {/* Tab Bar */}
            <div className={cn(
                'flex items-center border-b border-gray-200 dark:border-gray-700',
                bgStyles.surface
            )}>
                {/* Tabs */}
                <div
                    ref={tabsRef}
                    className="flex-1 flex items-center overflow-x-auto scrollbar-hide"
                >
                    {sortedTabs.map((tab) => (
                        <div
                            key={tab.id}
                            draggable={!tab.isPinned}
                            onDragStart={(e) => handleDragStart(e, tab.id)}
                            onDragOver={(e) => handleDragOver(e, tab.id)}
                            onDragEnd={handleDragEnd}
                            onContextMenu={(e) => handleContextMenu(e, tab.id)}
                            onClick={() => onTabChange(tab.id)}
                            className={cn(
                                'group flex items-center gap-2 px-3 py-2 border-r border-gray-200 dark:border-gray-700',
                                'cursor-pointer select-none transition-colors min-w-[120px] max-w-[200px]',
                                tab.id === activeTabId
                                    ? 'bg-white dark:bg-gray-800 border-b-2 border-b-blue-500 -mb-px'
                                    : 'bg-gray-50 dark:bg-gray-900 hover:bg-gray-100 dark:hover:bg-gray-800',
                                draggedTab === tab.id && 'opacity-50'
                            )}
                        >
                            {/* Icon */}
                            {tab.icon && (
                                <span className="flex-shrink-0 text-gray-500">
                                    {tab.icon}
                                </span>
                            )}

                            {/* Pin indicator */}
                            {tab.isPinned && (
                                <Pin className="h-3 w-3 text-blue-500 flex-shrink-0" />
                            )}

                            {/* Title */}
                            <span className={cn(
                                'flex-1 truncate text-sm',
                                tab.id === activeTabId
                                    ? 'text-gray-900 dark:text-white font-medium'
                                    : 'text-gray-600 dark:text-gray-400'
                            )}>
                                {tab.title}
                                {tab.isDirty && <span className="text-blue-500 ml-1">•</span>}
                            </span>

                            {/* Close button */}
                            {tab.closable !== false && !tab.isPinned && (
                                <button
                                    onClick={(e) => handleClose(e, tab.id)}
                                    className={cn(
                                        'flex-shrink-0 p-0.5 rounded hover:bg-gray-200 dark:hover:bg-gray-700',
                                        'opacity-0 group-hover:opacity-100 transition-opacity'
                                    )}
                                >
                                    <X className="h-3.5 w-3.5" />
                                </button>
                            )}
                        </div>
                    ))}
                </div>

                {/* New Tab Button */}
                {onNewTab && tabs.length < maxTabs && (
                    <button
                        onClick={onNewTab}
                        className={cn(
                            'flex-shrink-0 p-2 hover:bg-gray-100 dark:hover:bg-gray-800',
                            'transition-colors'
                        )}
                        title="New Tab"
                    >
                        <Plus className="h-4 w-4 text-gray-500" />
                    </button>
                )}
            </div>

            {/* Tab Content */}
            <div className="flex-1 overflow-hidden">
                {children(activeTab)}
            </div>

            {/* Context Menu */}
            {contextMenuTab && (
                <>
                    <div
                        className="fixed inset-0 z-40"
                        onClick={closeContextMenu}
                    />
                    <div
                        className={cn(
                            'fixed z-50 w-48 rounded-lg shadow-lg overflow-hidden',
                            'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700'
                        )}
                        style={{ left: contextMenuPosition.x, top: contextMenuPosition.y }}
                    >
                        {/* Pin/Unpin */}
                        {onTabPin && (
                            <button
                                onClick={() => handlePinToggle(contextMenuTab)}
                                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                            >
                                {tabs.find((t) => t.id === contextMenuTab)?.isPinned ? (
                                    <>
                                        <PinOff className="h-4 w-4" />
                                        Unpin Tab
                                    </>
                                ) : (
                                    <>
                                        <Pin className="h-4 w-4" />
                                        Pin Tab
                                    </>
                                )}
                            </button>
                        )}

                        {/* Close */}
                        {onTabClose && (
                            <button
                                onClick={() => {
                                    onTabClose(contextMenuTab);
                                    closeContextMenu();
                                }}
                                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                            >
                                <X className="h-4 w-4" />
                                Close Tab
                            </button>
                        )}

                        {/* Close Others */}
                        {onTabClose && tabs.length > 1 && (
                            <button
                                onClick={() => {
                                    tabs.forEach((t) => {
                                        if (t.id !== contextMenuTab && !t.isPinned) {
                                            onTabClose(t.id);
                                        }
                                    });
                                    closeContextMenu();
                                }}
                                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                            >
                                Close Other Tabs
                            </button>
                        )}

                        {/* Custom actions */}
                        {contextActions?.map((action) => (
                            <button
                                key={action.id}
                                onClick={() => {
                                    const tab = tabs.find((t) => t.id === contextMenuTab);
                                    if (tab) action.onClick(tab);
                                    closeContextMenu();
                                }}
                                disabled={action.disabled}
                                className={cn(
                                    'w-full flex items-center gap-2 px-3 py-2 text-sm',
                                    action.disabled
                                        ? 'opacity-50 cursor-not-allowed'
                                        : 'hover:bg-gray-50 dark:hover:bg-gray-700'
                                )}
                            >
                                {action.icon}
                                {action.label}
                            </button>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
}

export default TabWorkspace;
