/**
 * Shared Canvas Views
 * These are generic view components that can be used with any canvas implementation
 */

// Core UI components from @ghatana/yappc-ui
import { Box, Typography, Grid, List, ListItem, Card, CardContent, Stack, Chip } from '@ghatana/ui';

import React from 'react';

import type { ViewModeProps, BaseItem } from '../core/types';

// Generic List View - displays items as a simple list
export const GenericListView = <TItem extends BaseItem>({
    items,
    selectedItems,
    onItemSelect,
    onItemUpdate,
    onItemDelete,
    readonly
}: ViewModeProps<TItem>) => {

    return (
        <Box className="p-4 h-full overflow-auto">
            <Typography variant="h6" gutterBottom>
                Items List ({items.length})
            </Typography>

            <List>
                {items.map(item => (
                    <ListItem
                        key={item.id}
                        button
                        selected={selectedItems.includes(item.id)}
                        onClick={() => onItemSelect(item.id)}
                        className="mb-2 rounded border border-gray-200 dark:border-gray-700"
                    >
                        <Box className="flex-1">
                            <Typography variant="subtitle1">
                                {(item as unknown).title || (item as unknown).label || item.type}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                {(item as unknown).description || `ID: ${item.id}`}
                            </Typography>

                            {item.metadata?.tags && (
                                <Stack direction="row" spacing={0.5} className="mt-2">
                                    {item.metadata.tags.map(tag => (
                                        <Chip key={tag} size="small" label={tag} />
                                    ))}
                                </Stack>
                            )}
                        </Box>
                    </ListItem>
                ))}
            </List>
        </Box>
    );
};

// Generic Grid View - displays items in a card grid
export const GenericGridView = <TItem extends BaseItem>({
    items,
    selectedItems,
    onItemSelect,
    onItemUpdate,
    onItemDelete,
    readonly
}: ViewModeProps<TItem>) => {
    return (
        <Box className="p-4 h-full overflow-auto">
            <Typography variant="h6" gutterBottom>
                Items Grid ({items.length})
            </Typography>

            <Grid container spacing={2}>
                {items.map(item => (
                    <Grid item xs={12} sm={6} md={4} key={item.id}>
                        <Card
                            className="cursor-pointer hover:shadow" style={{ border: selectedItems.includes(item.id) ? 2 : 1, borderColor: selectedItems.includes(item.id) ? 'primary.main' : 'divider' }}
                            onClick={() => onItemSelect(item.id)}
                        >
                            <CardContent>
                                <Typography variant="h6" gutterBottom>
                                    {(item as unknown).title || (item as unknown).label || item.type}
                                </Typography>

                                <Typography variant="body2" color="text.secondary" paragraph>
                                    {(item as unknown).description || 'No description available'}
                                </Typography>

                                <Stack direction="row" spacing={1} flexWrap="wrap">
                                    <Chip size="small" label={item.type} tone="primary" />

                                    {(item as unknown).status && (
                                        <Chip
                                            size="small"
                                            label={(item as unknown).status}
                                            tone={(item as unknown).status === 'completed' ? 'success' : 'default'}
                                        />
                                    )}

                                    {(item as unknown).priority && (
                                        <Chip
                                            size="small"
                                            label={(item as unknown).priority}
                                            tone={(item as unknown).priority === 'high' ? 'warning' : 'default'}
                                        />
                                    )}
                                </Stack>

                                {item.metadata?.createdAt && (
                                    <Typography variant="caption" color="text.secondary" className="mt-2 block">
                                        Created: {new Date(item.metadata.createdAt).toLocaleDateString()}
                                    </Typography>
                                )}
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
            </Grid>
        </Box>
    );
};

// Generic Table View - displays items in a sortable table
export const GenericTableView = <TItem extends BaseItem>({
    items,
    selectedItems,
    onItemSelect,
    onItemUpdate,
    onItemDelete,
    readonly
}: ViewModeProps<TItem>) => {

    // Extract common columns from items
    const getColumns = () => {
        if (items.length === 0) return [];

        const sampleItem = items[0];
        const commonColumns = ['id', 'type'];

        // Add columns that exist in the sample item
        if ((sampleItem as unknown).title) commonColumns.push('title');
        if ((sampleItem as unknown).status) commonColumns.push('status');
        if ((sampleItem as unknown).priority) commonColumns.push('priority');
        if ((sampleItem as unknown).assignee) commonColumns.push('assignee');
        if (sampleItem.metadata?.createdAt) commonColumns.push('createdAt');

        return commonColumns;
    };

    const columns = getColumns();

    const getCellValue = (item: TItem, column: string) => {
        switch (column) {
            case 'id':
                return item.id;
            case 'type':
                return item.type;
            case 'createdAt':
                return item.metadata?.createdAt
                    ? new Date(item.metadata.createdAt).toLocaleDateString()
                    : '-';
            default:
                return (item as unknown)[column] || '-';
        }
    };

    return (
        <Box className="p-4 h-full overflow-auto">
            <Typography variant="h6" gutterBottom>
                Items Table ({items.length})
            </Typography>

            <Box className="rounded overflow-hidden border border-gray-200 dark:border-gray-700">
                {/* Header */}
                <Box className="flex bg-gray-100 dark:bg-gray-800 border-gray-200 dark:border-gray-700 border-b" >
                    {columns.map(column => (
                        <Box
                            key={column}
                            className="p-2 font-bold capitalize" style={{ flex: column === 'id' ? '0 0 200px' : 1 }}
                        >
                            {column}
                        </Box>
                    ))}
                </Box>

                {/* Rows */}
                {items.map((item, index) => (
                    <Box
                        key={item.id}
                        className={`flex cursor-pointer border-gray-200 dark:border-gray-700 hover:bg-gray-100 hover:dark:bg-gray-800 border-b ${selectedItems.includes(item.id) ? 'bg-blue-50 dark:bg-blue-900/20' : 'bg-white dark:bg-gray-900'}`}
                        onClick={() => onItemSelect(item.id)}
                    >
                        {columns.map(column => (
                            <Box
                                key={column}
                                className="p-2 overflow-hidden text-ellipsis whitespace-nowrap" style={{ flex: column === 'id' ? '0 0 200px' : 1 }}
                            >
                                {getCellValue(item, column)}
                            </Box>
                        ))}
                    </Box>
                ))}
            </Box>
        </Box>
    );
};

// Generic Canvas View - free-form positioning (like original ReactFlow canvas)
export const GenericCanvasView = <TItem extends BaseItem>({
    items,
    selectedItems,
    onItemSelect,
    onItemUpdate,
    onItemDelete,
    readonly
}: ViewModeProps<TItem>) => {

    return (
        <Box className="relative h-full overflow-auto bg-gray-50 dark:bg-gray-800" >
            {items.map(item => (
                <Box
                    key={item.id}
                    className="absolute cursor-pointer transition-all duration-300" style={{ left: item.position.x, top: item.position.y, transform: selectedItems.includes(item.id) ? 'scale(1.02)' : 'scale(1)', zIndex: selectedItems.includes(item.id) ? 10 : 1 }}
                    onClick={() => onItemSelect(item.id)}
                >
                    <Card
                        className="min-w-[120px]" style={{ border: selectedItems.includes(item.id) ? 2 : 1, borderColor: selectedItems.includes(item.id) ? 'primary.main' : 'divider', boxShadow: selectedItems.includes(item.id) ? 3 : 1, transform: 'translate(-50%' }}
                    >
                        <CardContent className="p-4 last:pb-4">
                            <Typography variant="subtitle2" gutterBottom>
                                {(item as unknown).title || (item as unknown).label || item.type}
                            </Typography>

                            {(item as unknown).description && (
                                <Typography variant="caption" color="text.secondary">
                                    {(item as unknown).description}
                                </Typography>
                            )}

                            <Stack direction="row" spacing={0.5} className="mt-2">
                                <Chip size="small" label={item.type} variant="outlined" />

                                {(item as unknown).status && (
                                    <Chip
                                        size="small"
                                        label={(item as unknown).status}
                                        tone={(item as unknown).status === 'completed' ? 'success' : 'default'}
                                        variant="outlined"
                                    />
                                )}
                            </Stack>
                        </CardContent>
                    </Card>
                </Box>
            ))}

            {items.length === 0 && (
                <Box
                    className="absolute text-center top-[50%] left-[50%] text-gray-500 dark:text-gray-400" >
                    <Typography variant="h6" gutterBottom>
                        Canvas is empty
                    </Typography>
                    <Typography variant="body2">
                        Add items to start building your canvas
                    </Typography>
                </Box>
            )}
        </Box>
    );
};