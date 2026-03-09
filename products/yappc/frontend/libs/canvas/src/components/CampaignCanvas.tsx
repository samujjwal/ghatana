/**
 * @doc.type component
 * @doc.purpose Campaign Planning canvas for marketing (Journey 25.1)
 * @doc.layer product
 * @doc.pattern Specialized Canvas Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Typography, IconButton, Tooltip, Surface as Paper, Chip, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Select, MenuItem, FormControl, InputLabel } from '@ghatana/ui';
import { Mail as Email, Globe as Public, Web, Plus as Add, TrendingUp } from 'lucide-react';

/**
 * Marketing channel types
 */
export type ChannelType = 'email' | 'social' | 'landing' | 'paid' | 'organic';

/**
 * Campaign node interface
 */
export interface CampaignNode {
    id: string;
    channel: ChannelType;
    label: string;
    kpis: {
        impressions?: number;
        clicks?: number;
        conversions?: number;
        roi?: number;
    };
}

/**
 * Props for CampaignCanvas
 */
export interface CampaignCanvasProps {
    nodes?: CampaignNode[];
    onAddNode?: (node: Omit<CampaignNode, 'id'>) => void;
    onUpdateNode?: (id: string, updates: Partial<CampaignNode>) => void;
    onDeleteNode?: (id: string) => void;
    showFunnel?: boolean;
}

/**
 * CampaignCanvas Component
 * 
 * Campaign planning for marketing with:
 * - Marketing channel nodes (Email, Social, Landing Page)
 * - Funnel visualization
 * - KPI per node
 */
export const CampaignCanvas: React.FC<CampaignCanvasProps> = ({
    nodes = [],
    onAddNode,
    onUpdateNode,
    onDeleteNode,
    showFunnel = false,
}) => {
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [newNode, setNewNode] = useState({
        channel: 'email' as ChannelType,
        label: '',
        kpis: {},
    });

    const getChannelIcon = (channel: ChannelType): React.ReactElement => {
        switch (channel) {
            case 'email':
                return <Email />;
            case 'social':
                return <Public />;
            case 'landing':
                return <Web />;
            default:
                return <TrendingUp />;
        }
    };

    const handleAddNode = useCallback(() => {
        if (!newNode.label.trim()) return;

        onAddNode?.(newNode);
        setNewNode({
            channel: 'email',
            label: '',
            kpis: {},
        });
        setShowAddDialog(false);
    }, [newNode, onAddNode]);

    const renderNode = (node: CampaignNode) => (
        <Paper
            key={node.id}
            className="p-4 mb-4" style={{ borderLeft: `4px solid ${node.channel === 'email' ? '#3b82f6' : node.channel === 'social' ? '#10b981' : '#f59e0b' }}
        >
            <Box className="flex items-start gap-2">
                <Box className="mt-1">{getChannelIcon(node.channel)}</Box>
                <Box className="flex-1">
                    <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                        {node.label}
                    </Typography>

                    <Box className="mt-2 flex gap-2 flex-wrap">
                        <Chip label={node.channel} size="sm" />
                        {node.kpis.impressions && <Chip label={`${node.kpis.impressions} impressions`} size="sm" />}
                        {node.kpis.clicks && <Chip label={`${node.kpis.clicks} clicks`} size="sm" />}
                        {node.kpis.conversions && <Chip label={`${node.kpis.conversions} conversions`} size="sm" tone="success" />}
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
                    <Typography as="h6">Campaign Planning</Typography>

                    <Box className="flex gap-2">
                        <Tooltip title="Add Email Channel">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setNewNode({ ...newNode, channel: 'email' });
                                    setShowAddDialog(true);
                                }}
                            >
                                <Email />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Add Social Channel">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setNewNode({ ...newNode, channel: 'social' });
                                    setShowAddDialog(true);
                                }}
                            >
                                <Public />
                            </IconButton>
                        </Tooltip>
                        <Tooltip title="Add Landing Page">
                            <IconButton
                                size="sm"
                                onClick={() => {
                                    setNewNode({ ...newNode, channel: 'landing' });
                                    setShowAddDialog(true);
                                }}
                            >
                                <Web />
                            </IconButton>
                        </Tooltip>
                    </Box>

                    <Button variant={showFunnel ? 'contained' : 'outlined'} size="sm">
                        {showFunnel ? 'Hide' : 'Show'} Funnel
                    </Button>
                </Box>
            </Paper>

            <Box className="flex-1 overflow-y-auto p-4">
                {nodes.length === 0 ? (
                    <Box className="text-center py-16">
                        <Typography as="h6" color="text.secondary">
                            No campaign channels yet
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Add marketing channels to plan your campaign
                        </Typography>
                    </Box>
                ) : (
                    nodes.map(renderNode)
                )}
            </Box>

            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} size="sm" fullWidth>
                <DialogTitle>Add Channel</DialogTitle>
                <DialogContent>
                    <Box className="flex flex-col gap-4 mt-2">
                        <TextField
                            label="Channel Name"
                            value={newNode.label}
                            onChange={(e) => setNewNode({ ...newNode, label: e.target.value })}
                            fullWidth
                            autoFocus
                        />

                        <FormControl fullWidth>
                            <InputLabel>Channel Type</InputLabel>
                            <Select
                                value={newNode.channel}
                                onChange={(e) => setNewNode({ ...newNode, channel: e.target.value as ChannelType })}
                                label="Channel Type"
                            >
                                <MenuItem value="email">Email</MenuItem>
                                <MenuItem value="social">Social Media</MenuItem>
                                <MenuItem value="landing">Landing Page</MenuItem>
                                <MenuItem value="paid">Paid Ads</MenuItem>
                                <MenuItem value="organic">Organic</MenuItem>
                            </Select>
                        </FormControl>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleAddNode} variant="solid" disabled={!newNode.label.trim()}>
                        Add
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
