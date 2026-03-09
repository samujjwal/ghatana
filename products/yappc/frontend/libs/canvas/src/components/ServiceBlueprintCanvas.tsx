/**
 * ServiceBlueprintCanvas Component
 * 
 * Service blueprint canvas for service design (Journey 20.1)
 * 
 * Features:
 * - Multi-layer swim lanes (customer/frontstage/backstage/support)
 * - Process nodes with cross-lane connections
 * - Touchpoint identification and analysis
 * - Line of visibility and interaction markers
 * - Service flow validation
 * - Export blueprints as SVG/JSON
 * 
 * @doc.type component
 * @doc.purpose Service blueprint mapping for service design
 * @doc.layer product
 * @doc.pattern Canvas
 */

import React, { useState } from 'react';
import { Box, Surface as Paper, Typography, Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Select, MenuItem, FormControl, InputLabel, IconButton, InteractiveList as List, ListItem, ListItemText, ListItemButton, Chip, Alert, Divider, Collapse, Tooltip, Stack, Card, CardContent } from '@ghatana/ui';
import { Plus as AddIcon, Settings as SettingsIcon, Trash2 as DeleteIcon, Download as DownloadIcon, Share2 as ShareIcon, Link as LinkIcon, Pointer as TouchpointIcon, Eye as VisibilityIcon, ArrowUpDown as InteractionIcon, Activity as FlowIcon, ChevronDown as ExpandMore, ChevronUp as ExpandLess, User as PersonIcon, Headset as SupportIcon, Building as BusinessIcon, Hammer as BuildIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';
import { useServiceBlueprint } from '../hooks/useServiceBlueprint';
import type {
    BlueprintLane,
    LaneType,
    ProcessNode,
    NodeConnection,
    Touchpoint,
} from '../hooks/useServiceBlueprint';

/**
 * Props for ServiceBlueprintCanvas
 */
export interface ServiceBlueprintCanvasProps {
    /**
     * Initial blueprint name
     */
    initialBlueprintName?: string;

    /**
     * Initial service description
     */
    initialServiceDescription?: string;

    /**
     * Callback when blueprint is exported
     */
    onExport?: (blueprint: string) => void;

    /**
     * Callback when blueprint is shared
     */
    onShare?: (blueprint: string) => void;
}

// Lane configuration
const LANE_CONFIGS: Array<{ type: LaneType; label: string; icon: React.ReactElement; color: string; description: string }> = [
    { type: 'customer', label: 'Customer Actions', icon: <PersonIcon />, color: '#2196f3', description: 'What customers do' },
    { type: 'frontstage', label: 'Frontstage', icon: <BusinessIcon />, color: '#4caf50', description: 'Visible interactions' },
    { type: 'backstage', label: 'Backstage', icon: <BuildIcon />, color: '#ff9800', description: 'Behind-the-scenes' },
    { type: 'support', label: 'Support Processes', icon: <SupportIcon />, color: '#9c27b0', description: 'Enabling systems' },
];

/**
 * ServiceBlueprintCanvas Component
 */
export const ServiceBlueprintCanvas: React.FC<ServiceBlueprintCanvasProps> = ({
    initialBlueprintName = 'Service Blueprint',
    initialServiceDescription = '',
    onExport,
    onShare,
}) => {
    const blueprint = useServiceBlueprint({
        initialBlueprintName,
        initialServiceDescription,
    });

    // UI State
    const [nodeDialogOpen, setNodeDialogOpen] = useState(false);
    const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
    const [touchpointDialogOpen, setTouchpointDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [libraryExpanded, setLibraryExpanded] = useState(true);

    // Node Dialog State
    const [selectedLane, setSelectedLane] = useState<LaneType>('customer');
    const [nodeName, setNodeName] = useState('');
    const [nodeDescription, setNodeDescription] = useState('');
    const [nodeDuration, setNodeDuration] = useState('');

    // Connection Dialog State
    const [connectionFrom, setConnectionFrom] = useState('');
    const [connectionTo, setConnectionTo] = useState('');
    const [connectionType, setConnectionType] = useState<'flow' | 'support'>('flow');

    // Touchpoint Dialog State
    const [touchpointNode, setTouchpointNode] = useState('');
    const [touchpointName, setTouchpointName] = useState('');
    const [touchpointChannel, setTouchpointChannel] = useState('');
    const [touchpointMetrics, setTouchpointMetrics] = useState('');

    // Handlers
    const handleAddNode = () => {
        if (nodeName.trim()) {
            blueprint.addNode(selectedLane, {
                name: nodeName.trim(),
                description: nodeDescription.trim() || undefined,
                duration: nodeDuration.trim() || undefined,
            });
            setNodeName('');
            setNodeDescription('');
            setNodeDuration('');
            setNodeDialogOpen(false);
        }
    };

    const handleAddConnection = () => {
        if (connectionFrom && connectionTo) {
            blueprint.addConnection({
                from: connectionFrom,
                to: connectionTo,
                type: connectionType,
            });
            setConnectionFrom('');
            setConnectionTo('');
            setConnectionDialogOpen(false);
        }
    };

    const handleAddTouchpoint = () => {
        if (touchpointNode && touchpointName.trim()) {
            blueprint.addTouchpoint(touchpointNode, {
                name: touchpointName.trim(),
                channel: touchpointChannel.trim() || undefined,
                metrics: touchpointMetrics.trim() || undefined,
            });
            setTouchpointName('');
            setTouchpointChannel('');
            setTouchpointMetrics('');
            setTouchpointDialogOpen(false);
        }
    };

    const handleExport = () => {
        const exported = blueprint.exportBlueprint();
        if (onExport) {
            onExport(exported);
        }
        navigator.clipboard.writeText(exported);
        setExportDialogOpen(false);
    };

    const handleShare = () => {
        const exported = blueprint.exportBlueprint();
        if (onShare) {
            onShare(exported);
        }
    };

    const getLaneConfig = (laneType: LaneType) => {
        return LANE_CONFIGS.find(c => c.type === laneType) || LANE_CONFIGS[0];
    };

    const getAllNodes = (): ProcessNode[] => {
        return blueprint.lanes.flatMap(lane => lane.nodes);
    };

    return (
        <Box className="flex h-screen bg-gray-50 dark:bg-gray-950">
            {/* Left Sidebar - Component Library */}
            <Paper
                elevation={2}
                className="flex flex-col w-[280px] rounded-none border-r border-gray-200 dark:border-gray-700"
            >
                <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex items-center justify-between">
                        <Typography as="h6">Blueprint Elements</Typography>
                        <IconButton size="sm" onClick={() => setLibraryExpanded(!libraryExpanded)}>
                            {libraryExpanded ? <ExpandLess /> : <ExpandMore />}
                        </IconButton>
                    </Box>
                </Box>

                <Collapse in={libraryExpanded}>
                    <List className="p-2">
                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setNodeDialogOpen(true)}>
                                <AddIcon className="mr-2 text-blue-600" />
                                <ListItemText primary="Add Process Node" secondary="Activity or step" />
                            </ListItemButton>
                        </ListItem>

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setConnectionDialogOpen(true)}>
                                <LinkIcon className="mr-2 text-sky-600" />
                                <ListItemText primary="Add Connection" secondary="Link processes" />
                            </ListItemButton>
                        </ListItem>

                        <ListItem disablePadding>
                            <ListItemButton onClick={() => setTouchpointDialogOpen(true)}>
                                <TouchpointIcon className="mr-2 text-green-600" />
                                <ListItemText primary="Add Touchpoint" secondary="Customer interaction" />
                            </ListItemButton>
                        </ListItem>

                        <Divider className="my-2" />

                        <ListItem>
                            <Box className="w-full">
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary" gutterBottom>
                                    Blueprint Layers
                                </Typography>
                                {LANE_CONFIGS.map((config) => (
                                    <Chip
                                        key={config.type}
                                        icon={config.icon}
                                        label={config.label}
                                        size="sm"
                                        className="m-1 text-white [&_.MuiChip-icon]:text-white" style={{ backgroundColor: config.color }}
                                    />
                                ))}
                            </Box>
                        </ListItem>
                    </List>
                </Collapse>
            </Paper>

            {/* Main Canvas Area */}
            <Box className="flex-1 flex flex-col">
                {/* Toolbar */}
                <Paper variant="raised" className="p-4 rounded-none border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex gap-4 items-center flex-wrap">
                        <Box className="flex-1 min-w-[240px]">
                            <TextField
                                size="sm"
                                label="Blueprint Name"
                                value={blueprint.blueprintName}
                                onChange={(e) => blueprint.setBlueprintName(e.target.value)}
                                className="w-[300px]"
                            />
                        </Box>
                        <Box>
                            <Chip label={`${blueprint.getNodeCount()} Nodes`} size="sm" className="mr-2" />
                            <Chip label={`${blueprint.getConnectionCount()} Connections`} size="sm" className="mr-2" />
                            <Chip label={`${blueprint.getTouchpointCount()} Touchpoints`} size="sm" tone="success" />
                        </Box>
                        <Box>
                            <Tooltip title="Export Blueprint">
                                <IconButton onClick={() => setExportDialogOpen(true)}>
                                    <DownloadIcon />
                                </IconButton>
                            </Tooltip>
                            <Tooltip title="Share Blueprint">
                                <IconButton onClick={handleShare}>
                                    <ShareIcon />
                                </IconButton>
                            </Tooltip>
                        </Box>
                    </Box>
                </Paper>

                {/* Blueprint Visualization */}
                <Box className="flex-1 overflow-auto p-6 bg-[#fafafa]">
                    {blueprint.lanes.length === 0 ? (
                        <Alert severity="info" className="mt-8 max-w-[600px] mx-auto">
                            <Typography as="p" gutterBottom>
                                <strong>Start creating your service blueprint</strong>
                            </Typography>
                            <Typography as="p" className="text-sm">
                                Service blueprints map the entire service ecosystem across 4 layers: Customer Actions, Frontstage (visible), Backstage (internal), and Support Processes.
                            </Typography>
                        </Alert>
                    ) : (
                        <Box>
                            {/* Description */}
                            {blueprint.serviceDescription && (
                                <Alert severity="info" className="mb-6">
                                    {blueprint.serviceDescription}
                                </Alert>
                            )}

                            {/* Lanes with Swim Lane Layout */}
                            {blueprint.lanes.map((lane, laneIndex) => {
                                const config = getLaneConfig(lane.type);

                                return (
                                    <Card
                                        key={lane.id}
                                        elevation={2}
                                        className="mb-4" >
                                        <CardContent>
                                            {/* Lane Header */}
                                            <Box className="flex items-center mb-4">
                                                <Box
                                                    className="flex items-center px-4 py-2 rounded mr-4 text-white" style={{ backgroundColor: config.color, borderLeft: '4px solid', borderColor: config.color }} >
                                                    {config.icon}
                                                    <Typography as="h6" className="ml-2">
                                                        {config.label}
                                                    </Typography>
                                                </Box>
                                                <Typography as="p" className="text-sm" color="text.secondary">
                                                    {config.description}
                                                </Typography>
                                            </Box>

                                            {/* Divider Lines */}
                                            {laneIndex === 0 && (
                                                <Box className="mb-4">
                                                    <Divider>
                                                        <Chip
                                                            icon={<InteractionIcon />}
                                                            label="Line of Interaction"
                                                            size="sm"
                                                            className="bg-[#e3f2fd]"
                                                        />
                                                    </Divider>
                                                </Box>
                                            )}
                                            {laneIndex === 1 && (
                                                <Box className="mb-4">
                                                    <Divider>
                                                        <Chip
                                                            icon={<VisibilityIcon />}
                                                            label="Line of Visibility"
                                                            size="sm"
                                                            className="bg-[#fff3e0]"
                                                        />
                                                    </Divider>
                                                </Box>
                                            )}

                                            {/* Process Nodes */}
                                            {lane.nodes.length === 0 ? (
                                                <Alert severity="info" variant="outlined">
                                                    No processes in this layer. Click "Add Process Node" to start.
                                                </Alert>
                                            ) : (
                                                <Box
                                                    className="grid gap-4" >
                                                    {lane.nodes.map((node) => (
                                                        <Box key={node.id}>
                                                            <Paper
                                                                variant="raised"
                                                                className="p-4 border-[2px] relative hover:shadow-lg" style={{ borderColor: config.color, gridTemplateColumns: {
                                                            xs: '1fr',
                                                            sm: 'repeat(2, minmax(0, 1fr))',
                                                            md: 'repeat(3, minmax(0, 1fr))',
                                                        } }}
                                                            >
                                                                <Box className="flex items-start justify-between">
                                                                    <Box className="flex-1">
                                                                        <Typography as="p" className="text-lg font-medium" fontWeight="bold">
                                                                            {node.name}
                                                                        </Typography>
                                                                        {node.description && (
                                                                            <Typography as="p" className="text-sm" color="text.secondary">
                                                                                {node.description}
                                                                            </Typography>
                                                                        )}
                                                                        {node.duration && (
                                                                            <Chip
                                                                                label={node.duration}
                                                                                size="sm"
                                                                                className="mt-2"
                                                                            />
                                                                        )}
                                                                    </Box>
                                                                    <IconButton
                                                                        size="sm"
                                                                        onClick={() => blueprint.deleteNode(node.id)}
                                                                        tone="danger"
                                                                    >
                                                                        <DeleteIcon size={16} />
                                                                    </IconButton>
                                                                </Box>

                                                                {/* Touchpoints */}
                                                                {node.touchpoints && node.touchpoints.length > 0 && (
                                                                    <Box className="mt-4 pt-2 border-gray-200 dark:border-gray-700 border-t" >
                                                                        <Typography as="span" className="text-xs text-gray-500" color="success.main" gutterBottom>
                                                                            <TouchpointIcon size={16} className="mr-1 align-middle" />
                                                                            Touchpoints
                                                                        </Typography>
                                                                        {node.touchpoints.map((tp) => (
                                                                            <Box key={tp.id} className="mt-1">
                                                                                <Typography as="p" className="text-sm" fontWeight="bold">
                                                                                    {tp.name}
                                                                                </Typography>
                                                                                {tp.channel && (
                                                                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                                                        {tp.channel}
                                                                                    </Typography>
                                                                                )}
                                                                                {tp.metrics && (
                                                                                    <Typography as="span" className="text-xs text-gray-500" display="block" color="info.main">
                                                                                        {tp.metrics}
                                                                                    </Typography>
                                                                                )}
                                                                            </Box>
                                                                        ))}
                                                                    </Box>
                                                                )}
                                                            </Paper>
                                                        </Box>
                                                    ))}
                                                </Box>
                                            )}
                                        </CardContent>
                                    </Card>
                                );
                            })}

                            {/* Connections Summary */}
                            {blueprint.connections.length > 0 && (
                                <Card elevation={2} className="mt-4">
                                    <CardContent>
                                        <Typography as="h6" gutterBottom>
                                            <FlowIcon className="mr-2 align-middle" />
                                            Process Connections
                                        </Typography>
                                        <List dense>
                                            {blueprint.connections.map((conn) => {
                                                const fromNode = getAllNodes().find(n => n.id === conn.from);
                                                const toNode = getAllNodes().find(n => n.id === conn.to);
                                                return (
                                                    <ListItem
                                                        key={conn.id}
                                                        secondaryAction={
                                                            <IconButton
                                                                edge="end"
                                                                size="sm"
                                                                onClick={() => blueprint.deleteConnection(conn.id)}
                                                            >
                                                                <DeleteIcon size={16} />
                                                            </IconButton>
                                                        }
                                                    >
                                                        <ListItemText
                                                            primary={`${fromNode?.name || 'Unknown'} → ${toNode?.name || 'Unknown'}`}
                                                            secondary={
                                                                <Chip
                                                                    label={conn.type === 'flow' ? 'Process Flow' : 'Support'}
                                                                    size="sm"
                                                                    color={conn.type === 'flow' ? 'primary' : 'secondary'}
                                                                    className="mt-1"
                                                                />
                                                            }
                                                        />
                                                    </ListItem>
                                                );
                                            })}
                                        </List>
                                    </CardContent>
                                </Card>
                            )}
                        </Box>
                    )}
                </Box>
            </Box>

            {/* Add Node Dialog */}
            <Dialog open={nodeDialogOpen} onClose={() => setNodeDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Process Node</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Lane</InputLabel>
                        <Select
                            value={selectedLane}
                            onChange={(e) => setSelectedLane(e.target.value as LaneType)}
                            label="Lane"
                        >
                            {LANE_CONFIGS.map((config) => (
                                <MenuItem key={config.type} value={config.type}>
                                    {config.icon} {config.label}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        autoFocus
                        margin="dense"
                        label="Process Name"
                        fullWidth
                        value={nodeName}
                        onChange={(e) => setNodeName(e.target.value)}
                        placeholder="e.g., Submit ticket, Agent responds"
                    />
                    <TextField
                        margin="dense"
                        label="Description"
                        fullWidth
                        multiline
                        rows={2}
                        value={nodeDescription}
                        onChange={(e) => setNodeDescription(e.target.value)}
                        placeholder="Optional description"
                    />
                    <TextField
                        margin="dense"
                        label="Duration"
                        fullWidth
                        value={nodeDuration}
                        onChange={(e) => setNodeDuration(e.target.value)}
                        placeholder="e.g., 2 minutes, 1 hour"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setNodeDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddNode} variant="solid" disabled={!nodeName.trim()}>
                        Add Node
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Connection Dialog */}
            <Dialog open={connectionDialogOpen} onClose={() => setConnectionDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Connection</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>From Node</InputLabel>
                        <Select
                            value={connectionFrom}
                            onChange={(e) => setConnectionFrom(e.target.value)}
                            label="From Node"
                        >
                            {getAllNodes().map((node) => (
                                <MenuItem key={node.id} value={node.id}>
                                    {node.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>To Node</InputLabel>
                        <Select
                            value={connectionTo}
                            onChange={(e) => setConnectionTo(e.target.value)}
                            label="To Node"
                        >
                            {getAllNodes().map((node) => (
                                <MenuItem key={node.id} value={node.id}>
                                    {node.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Connection Type</InputLabel>
                        <Select
                            value={connectionType}
                            onChange={(e) => setConnectionType(e.target.value as unknown)}
                            label="Connection Type"
                        >
                            <MenuItem value="flow">Process Flow</MenuItem>
                            <MenuItem value="support">Support/Dependency</MenuItem>
                        </Select>
                    </FormControl>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConnectionDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAddConnection}
                        variant="solid"
                        disabled={!connectionFrom || !connectionTo}
                    >
                        Add Connection
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Touchpoint Dialog */}
            <Dialog open={touchpointDialogOpen} onClose={() => setTouchpointDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Touchpoint</DialogTitle>
                <DialogContent>
                    <FormControl fullWidth margin="dense">
                        <InputLabel>Node</InputLabel>
                        <Select
                            value={touchpointNode}
                            onChange={(e) => setTouchpointNode(e.target.value)}
                            label="Node"
                        >
                            {getAllNodes().map((node) => (
                                <MenuItem key={node.id} value={node.id}>
                                    {node.name}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                    <TextField
                        margin="dense"
                        label="Touchpoint Name"
                        fullWidth
                        value={touchpointName}
                        onChange={(e) => setTouchpointName(e.target.value)}
                        placeholder="e.g., Email, Chat Widget, Phone"
                    />
                    <TextField
                        margin="dense"
                        label="Channel"
                        fullWidth
                        value={touchpointChannel}
                        onChange={(e) => setTouchpointChannel(e.target.value)}
                        placeholder="e.g., Web, Mobile, In-person"
                    />
                    <TextField
                        margin="dense"
                        label="Metrics"
                        fullWidth
                        value={touchpointMetrics}
                        onChange={(e) => setTouchpointMetrics(e.target.value)}
                        placeholder="e.g., 2min avg response time, 95% satisfaction"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setTouchpointDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleAddTouchpoint}
                        variant="solid"
                        disabled={!touchpointNode || !touchpointName.trim()}
                    >
                        Add Touchpoint
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="md" fullWidth>
                <DialogTitle>Export Service Blueprint</DialogTitle>
                <DialogContent>
                    <Alert severity="success" className="mb-4">
                        Blueprint copied to clipboard! You can also download as JSON.
                    </Alert>
                    <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                        Export includes all lanes, processes, connections, and touchpoints.
                    </Typography>
                    <Box className="mt-4">
                        <Typography as="p" className="text-sm font-medium" gutterBottom>
                            Summary:
                        </Typography>
                        <Chip label={`${blueprint.lanes.length} Lanes`} size="sm" className="mr-2" />
                        <Chip label={`${blueprint.getNodeCount()} Nodes`} size="sm" className="mr-2" />
                        <Chip label={`${blueprint.getConnectionCount()} Connections`} size="sm" className="mr-2" />
                        <Chip label={`${blueprint.getTouchpointCount()} Touchpoints`} size="sm" tone="success" />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)}>Close</Button>
                    <Button onClick={handleExport} variant="solid" startIcon={<DownloadIcon />}>
                        Copy to Clipboard
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default ServiceBlueprintCanvas;

// Type exports
export type {
    BlueprintLane,
    LaneType,
    ProcessNode,
    NodeConnection,
    Touchpoint,
};
