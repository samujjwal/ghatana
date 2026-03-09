/**
 * Custom Dashboard Builder Component
 * 
 * Drag-and-drop dashboard builder with widget library, layout management,
 * and user configuration persistence.
 */

import { LayoutDashboard as DashboardIcon, Plus as AddIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Save as SaveIcon, Settings as SettingsIcon, Component as WidgetsIcon, GripVertical as DragIcon, Activity as TimelineIcon, BarChart3 as BarChartIcon, PieChart as PieChartIcon, Table as TableChartIcon } from 'lucide-react';
import { Box, Grid, Typography, Card, CardContent, Button, IconButton, Drawer, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Fab, Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem, AppBar, Toolbar } from '@ghatana/ui';
import React, { useState, useCallback, useMemo } from 'react';

import { wrapForTooltip } from '../../utils/accessibility';

/** @ts-ignore - MUI Grid item prop typing issue in test config */
const GridItem = Grid as unknown;

/**
 *
 */
export interface DashboardWidget {
    id: string;
    type: 'chart' | 'metric' | 'table' | 'text' | 'custom';
    title: string;
    config: Record<string, unknown>;
    position: {
        x: number;
        y: number;
        width: number;
        height: number;
    };
}

/**
 *
 */
export interface DashboardLayout {
    id: string;
    name: string;
    description: string;
    widgets: DashboardWidget[];
    settings: {
        columns: number;
        rowHeight: number;
        padding: number;
    };
    createdAt: Date;
    updatedAt: Date;
}

/**
 *
 */
export interface CustomDashboardBuilderProps {
    /** Initial dashboard layout */
    initialLayout?: DashboardLayout;
    /** Available widget types */
    availableWidgets?: WidgetType[];
    /** Custom widget renderers */
    widgetRenderers?: Record<string, React.ComponentType<unknown>>;
    /** Save callback */
    onSave?: (layout: DashboardLayout) => void;
    /** Custom styling */
    className?: string;
}

/**
 *
 */
export interface WidgetType {
    id: string;
    name: string;
    description: string;
    icon: React.ReactNode;
    category: string;
    defaultConfig: Record<string, unknown>;
    configSchema?: Array<{
        key: string;
        label: string;
        type: 'text' | 'number' | 'select' | 'boolean';
        options?: string[];
        required?: boolean;
    }>;
}

const defaultWidgetTypes: WidgetType[] = [
    {
        id: 'performance-chart',
        name: 'Performance Chart',
        description: 'Real-time performance trending chart',
        icon: <TimelineIcon />,
        category: 'Performance',
        defaultConfig: {
            metric: 'buildTime',
            timeRange: '24h',
            showTrend: true
        },
        configSchema: [
            { key: 'metric', label: 'Metric', type: 'select', options: ['buildTime', 'deployTime', 'testTime'], required: true },
            { key: 'timeRange', label: 'Time Range', type: 'select', options: ['1h', '6h', '24h', '7d'], required: true },
            { key: 'showTrend', label: 'Show Trend', type: 'boolean' }
        ]
    },
    {
        id: 'build-status',
        name: 'Build Status',
        description: 'Current build status and metrics',
        icon: <BarChartIcon />,
        category: 'Build',
        defaultConfig: {
            showHistory: true,
            maxItems: 10
        }
    },
    {
        id: 'deployment-overview',
        name: 'Deployment Overview',
        description: 'Deployment status and history',
        icon: <PieChartIcon />,
        category: 'Deployment',
        defaultConfig: {
            environment: 'all',
            showRisk: true
        }
    },
    {
        id: 'test-results',
        name: 'Test Results',
        description: 'Test execution results and coverage',
        icon: <TableChartIcon />,
        category: 'Testing',
        defaultConfig: {
            showCoverage: true,
            groupBy: 'suite'
        }
    }
];

// Simple widget renderer component
const WidgetRenderer: React.FC<{ widget: DashboardWidget; onEdit: () => void; onDelete: () => void }> = ({
    widget,
    onEdit,
    onDelete
}) => {
    const renderContent = () => {
        switch (widget.type) {
            case 'chart':
                return (
                    <Box className="flex items-center justify-center h-[200px] bg-gray-50 dark:bg-gray-950">
                        <Typography color="text.secondary">Chart: {widget.config.metric}</Typography>
                    </Box>
                );
            case 'metric':
                return (
                    <Box className="text-center py-6">
                        <Typography as="h3" tone="primary">42</Typography>
                        <Typography color="text.secondary">Sample Metric</Typography>
                    </Box>
                );
            case 'table':
                return (
                    <Box className="flex items-center justify-center h-[200px] bg-gray-50 dark:bg-gray-950">
                        <Typography color="text.secondary">Table Widget</Typography>
                    </Box>
                );
            default:
                return (
                    <Box className="flex items-center justify-center h-[200px] bg-gray-50 dark:bg-gray-950">
                        <Typography color="text.secondary">Custom Widget</Typography>
                    </Box>
                );
        }
    };

    return (
        <Card className="h-full relative">
            <Box className="absolute flex gap-1 top-[8px] right-[8px] z-[1]">
                {wrapForTooltip(
                    <IconButton size="sm" onClick={onEdit}>
                        <EditIcon aria-hidden={true} />
                    </IconButton>,
                    { 'aria-describedby': `widget-edit-tooltip-${widget.id}` }
                )}
                {wrapForTooltip(
                    <IconButton size="sm" onClick={onDelete}>
                        <DeleteIcon aria-hidden={true} />
                    </IconButton>,
                    { 'aria-describedby': `widget-delete-tooltip-${widget.id}` }
                )}
                {wrapForTooltip(
                    <IconButton size="sm" className="cursor-grab">
                        <DragIcon aria-hidden={true} />
                    </IconButton>,
                    { 'aria-describedby': `widget-drag-tooltip-${widget.id}` }
                )}
            </Box>

            <CardContent className="pb-2">
                <Typography as="h6" gutterBottom>
                    {widget.title}
                </Typography>
                {renderContent()}
            </CardContent>
        </Card>
    );
};

export const CustomDashboardBuilder: React.FC<CustomDashboardBuilderProps> = ({
    initialLayout,
    availableWidgets = defaultWidgetTypes,
    onSave,
    className
}) => {
    const [layout, setLayout] = useState<DashboardLayout>(
        initialLayout || {
            id: 'default',
            name: 'My Dashboard',
            description: 'Custom dashboard',
            widgets: [],
            settings: {
                columns: 12,
                rowHeight: 100,
                padding: 16
            },
            createdAt: new Date(),
            updatedAt: new Date()
        }
    );

    const [isWidgetLibraryOpen, setIsWidgetLibraryOpen] = useState(false);
    const [editingWidget, setEditingWidget] = useState<DashboardWidget | null>(null);
    const [isSettingsOpen, setIsSettingsOpen] = useState(false);

    // Add new widget
    const addWidget = useCallback((widgetType: WidgetType) => {
        const newWidget: DashboardWidget = {
            id: `widget_${Date.now()}`,
            type: widgetType.id as unknown,
            title: widgetType.name,
            config: { ...widgetType.defaultConfig },
            position: {
                x: 0,
                y: 0,
                width: 6,
                height: 4
            }
        };

        setLayout(prev => ({
            ...prev,
            widgets: [...prev.widgets, newWidget],
            updatedAt: new Date()
        }));
        setIsWidgetLibraryOpen(false);
    }, []);

    // Edit widget
    const editWidget = useCallback((widget: DashboardWidget) => {
        setEditingWidget(widget);
    }, []);

    // Update widget
    const updateWidget = useCallback((updatedWidget: DashboardWidget) => {
        setLayout(prev => ({
            ...prev,
            widgets: prev.widgets.map(w => w.id === updatedWidget.id ? updatedWidget : w),
            updatedAt: new Date()
        }));
        setEditingWidget(null);
    }, []);

    // Delete widget
    const deleteWidget = useCallback((widgetId: string) => {
        setLayout(prev => ({
            ...prev,
            widgets: prev.widgets.filter(w => w.id !== widgetId),
            updatedAt: new Date()
        }));
    }, []);

    // Save dashboard
    const handleSave = useCallback(() => {
        if (onSave) {
            onSave(layout);
        }
    }, [layout, onSave]);

    // Group widgets by category
    const widgetsByCategory = useMemo(() => {
        const categories: Record<string, WidgetType[]> = {};
        availableWidgets.forEach(widget => {
            if (!categories[widget.category]) {
                categories[widget.category] = [];
            }
            categories[widget.category].push(widget);
        });
        return categories;
    }, [availableWidgets]);

    return (
        <Box className={className} className="h-screen flex flex-col">
            {/* Header */}
            <AppBar position="static" color="default" variant="raised">
                <Toolbar>
                    <DashboardIcon className="mr-4" />
                    <Typography as="h6" className="grow">
                        {layout.name}
                    </Typography>

                    <Button
                        startIcon={<WidgetsIcon />}
                        onClick={() => setIsWidgetLibraryOpen(true)}
                        className="mr-2"
                    >
                        Add Widget
                    </Button>

                    <Button
                        startIcon={<SettingsIcon />}
                        onClick={() => setIsSettingsOpen(true)}
                        className="mr-2"
                    >
                        Settings
                    </Button>

                    <Button
                        startIcon={<SaveIcon />}
                        onClick={handleSave}
                        variant="solid"
                    >
                        Save
                    </Button>
                </Toolbar>
            </AppBar>

            {/* Dashboard Grid */}
            <Box className="grow p-4 overflow-auto">
                {layout.widgets.length === 0 && (
                    <Box
                        className="h-full flex flex-col items-center justify-center text-gray-500 dark:text-gray-400"
                    >
                        <DashboardIcon className="mb-4 text-[64px]" />
                        <Typography as="h5" gutterBottom>
                            Empty Dashboard
                        </Typography>
                        <Typography as="p" gutterBottom>
                            Add widgets to customize your dashboard
                        </Typography>
                        <Button
                            startIcon={<AddIcon />}
                            variant="solid"
                            onClick={() => setIsWidgetLibraryOpen(true)}
                            className="mt-4"
                        >
                            Add Your First Widget
                        </Button>
                    </Box>
                )}
                {layout.widgets.length > 0 && (
                    <Grid container spacing={2}>
                        {layout.widgets.map(widget => (
                            <GridItem
                                item
                                key={widget.id}
                                xs={12}
                                sm={widget.position.width as unknown}
                                style={{ minHeight: widget.position.height * 50 }}
                            >
                                <WidgetRenderer
                                    widget={widget}
                                    onEdit={() => editWidget(widget)}
                                    onDelete={() => deleteWidget(widget.id)}
                                />
                            </GridItem>
                        ))}
                    </Grid>
                )}
            </Box>

            {/* Add Widget FAB */}
            <Fab
                tone="primary"
                className="fixed bottom-[16px] right-[16px]"
                onClick={() => setIsWidgetLibraryOpen(true)}
            >
                <AddIcon />
            </Fab>

            {/* Widget Library Drawer */}
            <Drawer
                anchor="right"
                open={isWidgetLibraryOpen}
                onClose={() => setIsWidgetLibraryOpen(false)}
                className="w-[400px]"
            >
                <Box className="p-4">
                    <Typography as="h6" gutterBottom>
                        Widget Library
                    </Typography>

                    {Object.entries(widgetsByCategory).map(([category, widgets]) => (
                        <Box key={category} className="mb-6">
                            <Typography as="p" className="text-sm font-medium" tone="primary" gutterBottom>
                                {category}
                            </Typography>

                            <List>
                                {widgets.map(widget => (
                                    <ListItem
                                        key={widget.id}
                                        component="button"
                                        onClick={() => addWidget(widget)}
                                        className="rounded mb-2 flex w-full cursor-pointer border border-gray-200 dark:border-gray-700"
                                    >
                                        <ListItemIcon>{widget.icon}</ListItemIcon>
                                        <ListItemText
                                            primary={widget.name}
                                            secondary={widget.description}
                                        />
                                    </ListItem>
                                ))}
                            </List>
                        </Box>
                    ))}
                </Box>
            </Drawer>

            {/* Widget Edit Dialog */}
            <Dialog
                open={!!editingWidget}
                onClose={() => setEditingWidget(null)}
                size="sm"
                fullWidth
            >
                <DialogTitle>Edit Widget</DialogTitle>
                <DialogContent>
                    {editingWidget && (
                        <Box className="pt-2">
                            <TextField
                                fullWidth
                                label="Widget Title"
                                value={editingWidget.title}
                                onChange={(e) => setEditingWidget(prev => prev ? { ...prev, title: e.target.value } : null)}
                                className="mb-4"
                            />

                            {/* Widget-specific config fields would go here */}
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Widget configuration options will be available based on widget type.
                            </Typography>
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setEditingWidget(null)}>Cancel</Button>
                    <Button
                        onClick={() => editingWidget && updateWidget(editingWidget)}
                        variant="solid"
                    >
                        Save
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Dashboard Settings Dialog */}
            <Dialog
                open={isSettingsOpen}
                onClose={() => setIsSettingsOpen(false)}
                size="sm"
                fullWidth
            >
                <DialogTitle>Dashboard Settings</DialogTitle>
                <DialogContent>
                    <Box className="pt-2">
                        <TextField
                            fullWidth
                            label="Dashboard Name"
                            value={layout.name}
                            onChange={(e) => setLayout(prev => ({ ...prev, name: e.target.value }))}
                            className="mb-4"
                        />

                        <TextField
                            fullWidth
                            label="Description"
                            value={layout.description}
                            onChange={(e) => setLayout(prev => ({ ...prev, description: e.target.value }))}
                            multiline
                            rows={3}
                            className="mb-4"
                        />

                        <FormControl fullWidth className="mb-4">
                            <InputLabel>Grid Columns</InputLabel>
                            <Select
                                value={layout.settings.columns}
                                onChange={(e) => setLayout(prev => ({
                                    ...prev,
                                    settings: { ...prev.settings, columns: e.target.value as number }
                                }))}
                                label="Grid Columns"
                            >
                                <MenuItem value={8}>8 Columns</MenuItem>
                                <MenuItem value={12}>12 Columns</MenuItem>
                                <MenuItem value={16}>16 Columns</MenuItem>
                            </Select>
                        </FormControl>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setIsSettingsOpen(false)}>Cancel</Button>
                    <Button onClick={() => setIsSettingsOpen(false)} variant="solid">Save</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};