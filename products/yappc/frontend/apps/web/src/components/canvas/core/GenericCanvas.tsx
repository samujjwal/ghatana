/**
 * Generic Canvas Core Component
 * Provides a composable, extensible foundation for all canvas implementations
 */

import { Box } from '@ghatana/ui';
import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';

import {
    CanvasAPI,
    CanvasEvent,
    CanvasEventHandler
} from './types';
import { useGenericCanvas } from './useGenericCanvas';

import type {
    BaseItem,
    CanvasCapabilities,
    ViewModeDefinition,
    ItemRendererProps,
    ToolbarRendererProps,
    SidebarRendererProps,
    CanvasState,
    CanvasActionContext,
    TemplateDefinition,
    FilterDefinition,
    SortDefinition,
    CanvasPlugin
} from './types';

/**
 *
 */
export interface GenericCanvasProps<TItem extends BaseItem> {
    // Core data
    items: TItem[];
    onItemsChange: (items: TItem[]) => void;

    // Rendering components
    itemRenderer: React.ComponentType<ItemRendererProps<TItem>>;
    toolbarRenderer?: React.ComponentType<ToolbarRendererProps<TItem>>;
    sidebarRenderer?: React.ComponentType<SidebarRendererProps<TItem>>;

    // Configuration
    capabilities: CanvasCapabilities;
    viewModes: ViewModeDefinition<TItem>[];
    defaultViewMode?: string;

    // Optional features
    templates?: TemplateDefinition<TItem>[];
    filters?: FilterDefinition<TItem>[];
    sorts?: SortDefinition<TItem>[];
    plugins?: CanvasPlugin<TItem>[];

    // State management
    persistenceKey?: string;
    initialState?: Partial<CanvasState<TItem>>;

    // Event handlers
    onStateChange?: (state: CanvasState<TItem>) => void;
    onSelectionChange?: (selectedIds: string[]) => void;
    onViewModeChange?: (mode: string) => void;

    // Styling
    className?: string;
    style?: React.CSSProperties;
    readonly?: boolean;
}

export const GenericCanvas = <TItem extends BaseItem>({
    items,
    onItemsChange,
    itemRenderer: ItemRenderer,
    toolbarRenderer: ToolbarRenderer,
    sidebarRenderer: SidebarRenderer,
    capabilities,
    viewModes,
    defaultViewMode,
    templates,
    filters,
    sorts,
    plugins,
    persistenceKey,
    initialState,
    onStateChange,
    onSelectionChange,
    onViewModeChange,
    className,
    style,
    readonly = false
}: GenericCanvasProps<TItem>) => {

    // Use the generic canvas hook for state management
    const {
        canvasState,
        selectedItems,
        currentViewMode,
        filteredItems,
        canvasAPI,
        actions
    } = useGenericCanvas<TItem>({
        items,
        onItemsChange,
        capabilities,
        viewModes,
        defaultViewMode,
        templates,
        filters,
        sorts,
        plugins,
        persistenceKey,
        initialState,
        onStateChange,
        onSelectionChange,
        onViewModeChange
    });

    const canvasRef = useRef<HTMLDivElement>(null);
    const [sidebarOpen, setSidebarOpen] = useState(false);

    // Get current view mode definition
    const currentView = useMemo(() => {
        return viewModes.find(vm => vm.id === currentViewMode) || viewModes[0];
    }, [viewModes, currentViewMode]);

    // Get selected item for sidebar
    const selectedItem = useMemo(() => {
        if (selectedItems.length === 1) {
            return items.find(item => item.id === selectedItems[0]);
        }
        return undefined;
    }, [items, selectedItems]);

    // Handle item selection
    const handleItemSelect = useCallback((itemId: string, multi = false) => {
        actions.selectItem(itemId, multi);
        setSidebarOpen(true);
    }, [actions]);

    // Handle item updates
    const handleItemUpdate = useCallback((itemId: string, updates: Partial<TItem>) => {
        actions.updateItem(itemId, updates);
    }, [actions]);

    // Handle item deletion
    const handleItemDelete = useCallback((itemId: string) => {
        actions.deleteItem(itemId);
        setSidebarOpen(false);
    }, [actions]);

    // Handle item creation
    const handleItemCreate = useCallback((item: Omit<TItem, 'id'>) => {
        actions.createItem(item);
    }, [actions]);

    // Handle bulk actions
    const handleBulkAction = useCallback((action: string, itemIds: string[]) => {
        switch (action) {
            case 'delete':
                itemIds.forEach(id => actions.deleteItem(id));
                break;
            case 'duplicate':
                itemIds.forEach(id => {
                    const item = items.find(i => i.id === id);
                    if (item) {
                        const { id: _, ...itemData } = item;
                        actions.createItem({
                            ...itemData,
                            position: {
                                x: item.position.x + 20,
                                y: item.position.y + 20
                            }
                        } as Omit<TItem, 'id'>);
                    }
                });
                break;
        }
    }, [actions, items]);

    // Create action context for toolbar and plugins
    const actionContext: CanvasActionContext<TItem> = useMemo(() => ({
        items: filteredItems,
        selectedItems,
        viewMode: currentViewMode,
        canvasAPI
    }), [filteredItems, selectedItems, currentViewMode, canvasAPI]);

    // Render current view
    const CurrentViewComponent = currentView.component;

    return (
        <Box
            ref={canvasRef}
            className={className}
            style={style}
            className="h-full w-full flex flex-col overflow-hidden"
        >
            {/* Toolbar */}
            {ToolbarRenderer && (
                <Box className="shrink-0">
                    <ToolbarRenderer
                        selectedItems={selectedItems}
                        viewMode={currentViewMode}
                        onViewModeChange={actions.setViewMode}
                        onItemCreate={capabilities.dragDrop ? handleItemCreate : undefined}
                        onBulkAction={handleBulkAction}
                        customActions={plugins?.flatMap(p => p.toolbarActions || [])}
                    />
                </Box>
            )}

            {/* Main content area */}
            <Box className="flex-1 flex overflow-hidden">
                {/* Canvas view */}
                <Box className="flex-1 relative overflow-hidden">
                    <CurrentViewComponent
                        items={filteredItems}
                        selectedItems={selectedItems}
                        onItemSelect={handleItemSelect}
                        onItemUpdate={handleItemUpdate}
                        onItemDelete={handleItemDelete}
                        onItemCreate={handleItemCreate}
                        readonly={readonly}
                    />

                    {/* Render item decorators from plugins */}
                    {plugins?.map(plugin =>
                        plugin.itemDecorators?.map(decorator => (
                            <React.Fragment key={`${plugin.id}-${decorator.id}`}>
                                {filteredItems.map(item => {
                                    if (decorator.shouldRender?.(item) !== false) {
                                        return (
                                            <Box
                                                key={`${item.id}-${decorator.id}`}
                                                className="absolute pointer-events-none z-[1000 + decorator.priority]" style={{ left: item.position.x, top: item.position.y }} >
                                                {decorator.render(item, {
                                                    item,
                                                    selected: selectedItems.includes(item.id),
                                                    canvasAPI
                                                })}
                                            </Box>
                                        );
                                    }
                                    return null;
                                })}
                            </React.Fragment>
                        ))
                    )}
                </Box>

                {/* Sidebar */}
                {SidebarRenderer && sidebarOpen && selectedItem && (
                    <Box className="shrink-0 w-[320px] border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 border-l" >
                        <SidebarRenderer
                            selectedItem={selectedItem}
                            onItemUpdate={handleItemUpdate}
                            onClose={() => setSidebarOpen(false)}
                        />
                    </Box>
                )}
            </Box>

            {/* Plugin panels */}
            {plugins?.map(plugin =>
                plugin.panels?.map(panel => (
                    <Box
                        key={`${plugin.id}-${panel.id}`}
                        className="fixed"
                    >
                        <panel.component
                            items={filteredItems}
                            selectedItems={selectedItems}
                            canvasAPI={canvasAPI}
                        />
                    </Box>
                ))
            )}
        </Box>
    );
};

export default GenericCanvas;