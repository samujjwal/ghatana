/**
 * @doc.type component
 * @doc.purpose Dashboard Designer canvas for data analysts (Journey 22.1)
 * @doc.layer product
 * @doc.pattern Specialized Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, IconButton, Tooltip, Surface as Paper, Chip, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Select, MenuItem, FormControl, InputLabel, Divider } from '@ghatana/ui';
import { BarChart3 as ChartIcon, HardDrive as DataIcon, Filter as FilterIcon, BarChart3 as BarChart, ShowChart, PieChart, RefreshCw as Refresh, Publish } from 'lucide-react';

/**
 * Dashboard node types
 */
export type DashboardNodeType = 'datasource' | 'chart' | 'filter';

/**
 * Chart types for visualization
 */
export type ChartType = 'bar' | 'line' | 'pie' | 'area' | 'scatter';

/**
 * Filter types
 */
export type FilterType = 'daterange' | 'dropdown' | 'search' | 'slider';

/**
 * Dashboard node interface
 */
export interface DashboardNode {
    id: string;
    type: DashboardNodeType;
    label: string;
    position: { x: number; y: number };
    data: {
        query?: string;
        chartType?: ChartType;
        filterType?: FilterType;
        options?: Record<string, unknown>;
    };
}

/**
 * Props for DashboardCanvas
 */
export interface DashboardCanvasProps {
    nodes?: DashboardNode[];
    onAddNode?: (node: Omit<DashboardNode, 'id'>) => void;
    onUpdateNode?: (id: string, updates: Partial<DashboardNode>) => void;
    onDeleteNode?: (id: string) => void;
    onPublish?: () => void;
    livePreview?: boolean;
    onTogglePreview?: () => void;
}

/**
 * DashboardCanvas Component
 * 
 * Dashboard designer for data analysts with:
 * - Data source nodes (SQL editor)
 * - Chart nodes (bar, line, pie, area, scatter)
 * - Filter nodes (date range, dropdown, search, slider)
 * - Live preview mode
 * - BI tool integration
 */
export const DashboardCanvas: React.FC<DashboardCanvasProps> = ({
    nodes = [],
    onAddNode,
    onUpdateNode,
    onDeleteNode,
    onPublish,
    livePreview = false,
    onTogglePreview,
}) => {
    const [selectedNodeType, setSelectedNodeType] = useState<DashboardNodeType>('chart');
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [newNodeData, setNewNodeData] = useState({
        label: '',
        query: '',
        chartType: 'bar' as ChartType,
        filterType: 'daterange' as FilterType,
    });

    const getNodeIcon = (type: DashboardNodeType): React.ReactElement => {
        switch (type) {
            case 'datasource':
                return <DataIcon />;
            case 'chart':
                return <ChartIcon />;
            case 'filter':
                return <FilterIcon />;
        }
    };

    const getChartIcon = (chartType: ChartType): React.ReactElement => {
        switch (chartType) {
            case 'bar':
                return <BarChart />;
            case 'line':
                return <ShowChart />;
            case 'pie':
                return <PieChart />;
            default:
                return <ChartIcon />;
        }
    };

    const handleAddNode = useCallback(() => {
        if (!newNodeData.label.trim()) return;

        onAddNode?.({
            type: selectedNodeType,
            label: newNodeData.label,
            position: { x: 100, y: 100 },
            data: {
                query: selectedNodeType === 'datasource' ? newNodeData.query : undefined,
                chartType: selectedNodeType === 'chart' ? newNodeData.chartType : undefined,
                filterType: selectedNodeType === 'filter' ? newNodeData.filterType : undefined,
            },
        });

        setNewNodeData({
            label: '',
            query: '',
            chartType: 'bar',
            filterType: 'daterange',
        });
        setShowAddDialog(false);
    }, [selectedNodeType, newNodeData, onAddNode]);

    const renderNode = (node: DashboardNode) => (
        <Paper
            key={node.id}
            className="p-4 mb-2" style={{ borderLeft: `4px solid ${node.type === 'datasource' ? '#3b82f6' : node.type === 'chart' ? '#10b981' : '#f59e0b' }}
        >
            <Box className="flex items-start gap-2">
                <Box className="mt-1">{getNodeIcon(node.type)}</Box>
                <Box className="flex-1">
                    <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                        {node.label}
                    </Typography>
                    {node.data.query && (
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-1 block font-mono">
                            {node.data.query.substring(0, 50)}...
                        </Typography>
                    )}
                    <Box className="mt-2 flex gap-1 flex-wrap">
                        <Chip label={node.type} size="sm" />
                        {node.data.chartType && (
                            <Chip icon={getChartIcon(node.data.chartType)} label={node.data.chartType} size="sm" />
                        )}
                        {node.data.filterType && <Chip label={node.data.filterType} size="sm" />}
                    </Box>
                </Box>
                <IconButton size="sm" onClick={() => onDeleteNode?.(node.id)} className="text-red-600">
                    <Typography as="span" className="text-xs text-gray-500">×</Typography>
                </IconButton>
            </Box>
        </Paper>
    );

    return (
        <Box className="h-full flex flex-col">
            <Paper className="p-4 mb-4">
                <Box className="flex items-center gap-4 flex-wrap">
                    <Typography as="h6">Dashboard Designer</Typography>

                    <Box className="flex gap-2 flex-1">
                        <Tooltip title="Add Data Source">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setSelectedNodeType('datasource');
                                    setShowAddDialog(true);
                                }}
                                className="text-[#3b82f6]"
                            >
                                <DataIcon />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Add Chart">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setSelectedNodeType('chart');
                                    setShowAddDialog(true);
                                }}
                                className="text-[#10b981]"
                            >
                                <ChartIcon />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Add Filter">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setSelectedNodeType('filter');
                                    setShowAddDialog(true);
                                }}
                                className="text-[#f59e0b]"
                            >
                                <FilterIcon />
                            </IconButton>
                        </Tooltip>
                    </Box>

                    <Button variant={livePreview ? 'contained' : 'outlined'} size="sm" onClick={onTogglePreview} startIcon={<Refresh />}>
                        {livePreview ? 'Live' : 'Preview'}
                    </Button>

                    <Button variant="solid" size="sm" onClick={onPublish} startIcon={<Publish />}>
                        Publish
                    </Button>
                </Box>
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {nodes.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary" gutterBottom>
                            No dashboard components yet
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Add data sources, charts, or filters to build your dashboard
                        </Typography>
                    </Box>
                ) : (
                    nodes.map(renderNode)
                )}
            </Box>

            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} size="sm" fullWidth>
                <DialogTitle>Add {selectedNodeType}</DialogTitle>
                <DialogContent>
                    <Box className="flex flex-col gap-4 mt-2">
                        <TextField
                            label="Label"
                            value={newNodeData.label}
                            onChange={(e) => setNewNodeData({ ...newNodeData, label: e.target.value })}
                            fullWidth
                            autoFocus
                        />

                        {selectedNodeType === 'datasource' && (
                            <TextField
                                label="SQL Query"
                                value={newNodeData.query}
                                onChange={(e) => setNewNodeData({ ...newNodeData, query: e.target.value })}
                                fullWidth
                                multiline
                                rows={4}
                                placeholder="SELECT * FROM..."
                            />
                        )}

                        {selectedNodeType === 'chart' && (
                            <FormControl fullWidth>
                                <InputLabel>Chart Type</InputLabel>
                                <Select
                                    value={newNodeData.chartType}
                                    onChange={(e) => setNewNodeData({ ...newNodeData, chartType: e.target.value as ChartType })}
                                    label="Chart Type"
                                >
                                    <MenuItem value="bar">Bar Chart</MenuItem>
                                    <MenuItem value="line">Line Chart</MenuItem>
                                    <MenuItem value="pie">Pie Chart</MenuItem>
                                    <MenuItem value="area">Area Chart</MenuItem>
                                    <MenuItem value="scatter">Scatter Plot</MenuItem>
                                </Select>
                            </FormControl>
                        )}

                        {selectedNodeType === 'filter' && (
                            <FormControl fullWidth>
                                <InputLabel>Filter Type</InputLabel>
                                <Select
                                    value={newNodeData.filterType}
                                    onChange={(e) => setNewNodeData({ ...newNodeData, filterType: e.target.value as FilterType })}
                                    label="Filter Type"
                                >
                                    <MenuItem value="daterange">Date Range</MenuItem>
                                    <MenuItem value="dropdown">Dropdown</MenuItem>
                                    <MenuItem value="search">Search</MenuItem>
                                    <MenuItem value="slider">Slider</MenuItem>
                                </Select>
                            </FormControl>
                        )}
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleAddNode} variant="solid" disabled={!newNodeData.label.trim()}>
                        Add
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
