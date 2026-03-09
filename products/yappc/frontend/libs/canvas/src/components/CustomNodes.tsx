import { MoreVertical as MoreVertIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Users as GroupIcon, GitFork as UngroupIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Code as CodeIcon, Braces as DataIcon, SquareFunction as FunctionIcon, HardDrive as DatabaseIcon, Plug as ApiIcon, Globe as WebIcon, Cloud as CloudIcon, Shield as SecurityIcon, Play as PlayIcon, Pause as PauseIcon, Settings as SettingsIcon, ExternalLink as OpenInNewIcon } from 'lucide-react';
import { Handle, Position } from '@xyflow/react';
import {
  Box,
  Typography,
  IconButton,
  Menu,
  Chip,
  Avatar,
  Card,
  CardContent,
  CardActions,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  Badge,
  Tooltip,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import { TextField, MenuItem, Collapse } from '@ghatana/ui';
import { useMuiTheme as useTheme, resolveMuiColor } from '@ghatana/yappc-ui';
import React, { useState } from 'react';

// consolidate useTheme into the primary MUI import list to avoid duplicate imports

import type { Node, NodeTypes, NodeProps } from '@xyflow/react';

// Extended node data interface
/**
 *
 */
export interface AdvancedNodeData {
  label: string;
  type: 'default' | 'process' | 'decision' | 'data' | 'api' | 'database' | 'cloud' | 'security' | 'group';
  description?: string;
  properties?: Record<string, unknown>;
  status?: 'idle' | 'running' | 'completed' | 'error';
  progress?: number;
  tags?: string[];
  connections?: {
    input: number;
    output: number;
  };
  executionTime?: number;
  lastExecuted?: Date;
  config?: Record<string, unknown>;
  collapsed?: boolean;
  children?: string[]; // For groups
  parent?: string; // For grouped nodes
  layer?: number;
  locked?: boolean;
  drillDownTarget?: string; // Target view ID for drill-down navigation
  validation?: {
    isValid: boolean;
    errors: string[];
    warnings: string[];
  };
}

// Custom node component props
/**
 *
 */
interface CustomNodeProps {
  data: AdvancedNodeData;
  id: string;
  selected: boolean;
  dragging: boolean;
  onUpdate: (id: string, data: Partial<AdvancedNodeData>) => void;
  onDelete: (id: string) => void;
  onGroup: (nodeIds: string[]) => void;
  onUngroup: (groupId: string) => void;
  onExecute?: (id: string) => void;
  onDrillDown?: (id: string, target: string) => void;
}

// Node type icons
const getNodeIcon = (type: string) => {
  switch (type) {
    case 'process': return <FunctionIcon />;
    case 'decision': return <CodeIcon />;
    case 'data': return <DataIcon />;
    case 'api': return <ApiIcon />;
    case 'database': return <DatabaseIcon />;
    case 'cloud': return <CloudIcon />;
    case 'security': return <SecurityIcon />;
    case 'group': return <GroupIcon />;
    default: return <WebIcon />;
  }
};

// Status colors
const getStatusColor = (status?: string) => {
  switch (status) {
    case 'running': return 'primary';
    case 'completed': return 'success';
    case 'error': return 'error';
    default: return 'default';
  }
};

// Base Custom Node Component
const BaseCustomNode: React.FC<CustomNodeProps> = ({
  data,
  id,
  selected,
  dragging,
  onUpdate,
  onDelete,
  onGroup,
  onUngroup,
  onExecute,
  onDrillDown
}) => {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [label, setLabel] = useState(data.label);
  const [propertiesOpen, setPropertiesOpen] = useState(false);

  const handleMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLabelSave = () => {
    onUpdate(id, { label });
    setEditMode(false);
  };

  const handleExecute = () => {
    if (onExecute) {
      onExecute(id);
      onUpdate(id, { status: 'running', lastExecuted: new Date() });
    }
  };

  const handleDrillDown = () => {
    if (onDrillDown && data.drillDownTarget) {
      onDrillDown(id, data.drillDownTarget);
    }
  };

  const nodeColor = selected ? resolveMuiColor(theme, 'primary', 'primary') :
    dragging ? resolveMuiColor(theme, 'secondary', 'primary') :
      data.status === 'error' ? resolveMuiColor(theme, 'error', 'primary') : resolveMuiColor(theme, 'grey', 'primary');

  return (
    <>
      <Card
        className="min-w-[200px] max-w-[300px] border-[2px] relative" style={{ borderColor: nodeColor, backgroundColor: data.status === 'running' ? 'rgba(0,0,0,0.04)' : '#fff', opacity: data.locked ? 0.7 : 1 }}
        elevation={selected ? 8 : dragging ? 4 : 1}
      >
        {/* Input Handle */}
        <Handle
          type="target"
          position={Position.Left}
          style={{ left: -8, background: '#555' }}
        />

        <CardContent className="pb-2">
          {/* Header */}
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
            <Box display="flex" alignItems="center" gap={1}>
              <Avatar className="w-[24px] h-[24px]" >
                {getNodeIcon(data.type)}
              </Avatar>
              {data.status && (
                <Chip
                  label={data.status}
                  size="small"
                  color={resolveMuiColor(theme, String(getStatusColor(data.status)), 'default') as unknown}
                />
              )}
            </Box>
            <Box>
              {/* Drill Down Action in Header for visibility */}
              {data.drillDownTarget && (
                <Tooltip title="Drill down to details">
                  <IconButton size="small" onClick={handleDrillDown} color="primary">
                    <OpenInNewIcon size={16} />
                  </IconButton>
                </Tooltip>
              )}
              <IconButton size="small" onClick={handleMenuClick}>
                <MoreVertIcon />
              </IconButton>
            </Box>
          </Box>

          {/* Label */}
          {editMode ? (
            <TextField
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              onBlur={handleLabelSave}
              onKeyPress={(e) => e.key === 'Enter' && handleLabelSave()}
              size="small"
              fullWidth
              autoFocus
            />
          ) : (
            <Typography
              variant="subtitle2"
              noWrap
              onClick={() => setEditMode(true)}
              className="cursor-pointer"
            >
              {data.label}
            </Typography>
          )}

          {/* Description */}
          {data.description && (
            <Typography variant="caption" color="text.secondary" noWrap>
              {data.description}
            </Typography>
          )}

          {/* Progress bar for running nodes */}
          {data.status === 'running' && data.progress !== undefined && (
            <Box className="w-full mt-2">
              <Box className="flex items-center">
                <Box className="w-full mr-2">
                  <div
                    style={{
                      width: '100%',
                      height: 4,
                      backgroundColor: '#e0e0e0',
                      borderRadius: 2
                    }}
                  >
                    <div
                      style={{
                        width: `${data.progress}%`,
                        height: '100%',
                        backgroundColor: '#1976d2',
                        borderRadius: 2,
                        transition: 'width 0.3s ease'
                      }}
                    />
                  </div>
                </Box>
                <Typography variant="caption" color="text.secondary">
                  {Math.round(data.progress)}%
                </Typography>
              </Box>
            </Box>
          )}

          {/* Tags */}
          {data.tags && data.tags.length > 0 && (
            <Box display="flex" gap={0.5} mt={1} flexWrap="wrap">
              {data.tags.slice(0, 3).map((tag, index) => (
                <Chip key={index} label={tag} size="small" variant="outlined" />
              ))}
              {data.tags.length > 3 && (
                <Chip label={`+${data.tags.length - 3}`} size="small" variant="outlined" />
              )}
            </Box>
          )}

          {/* Validation errors */}
          {data.validation && !data.validation.isValid && (
            <Box mt={1}>
              <Chip
                label={`${data.validation.errors.length} errors`}
                size="small"
                color="error"
                variant="outlined"
              />
            </Box>
          )}
        </CardContent>

        {/* Actions */}
        <CardActions className="pt-0 pb-2">
          <Box display="flex" justifyContent="space-between" width="100%">
            <Box>
              {data.connections && (
                <Typography variant="caption" color="text.secondary">
                  {data.connections.input}→{data.connections.output}
                </Typography>
              )}
            </Box>
            <Box>
              {onExecute && data.type === 'process' && (
                <IconButton size="small" onClick={handleExecute}>
                  {data.status === 'running' ? <PauseIcon /> : <PlayIcon />}
                </IconButton>
              )}
              <IconButton size="small" onClick={() => setPropertiesOpen(true)}>
                <SettingsIcon />
              </IconButton>
            </Box>
          </Box>
        </CardActions>

        {/* Output Handle */}
        <Handle
          type="source"
          position={Position.Right}
          style={{ right: -8, background: '#555' }}
        />
      </Card>

      {/* Context Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={() => { setEditMode(true); handleMenuClose(); }}>
          <ListItemIcon><EditIcon size={16} /></ListItemIcon>
          <ListItemText>Edit</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { onDelete(id); handleMenuClose(); }}>
          <ListItemIcon><DeleteIcon size={16} /></ListItemIcon>
          <ListItemText>Delete</ListItemText>
        </MenuItem>
        {data.drillDownTarget && (
          <MenuItem onClick={() => { handleDrillDown(); handleMenuClose(); }}>
            <ListItemIcon><OpenInNewIcon size={16} /></ListItemIcon>
            <ListItemText>Open Details</ListItemText>
          </MenuItem>
        )}
        <Divider />
        <MenuItem onClick={() => { onGroup([id]); handleMenuClose(); }}>
          <ListItemIcon><GroupIcon size={16} /></ListItemIcon>
          <ListItemText>Create Group</ListItemText>
        </MenuItem>
        {data.type === 'group' && (
          <MenuItem onClick={() => { onUngroup(id); handleMenuClose(); }}>
            <ListItemIcon><UngroupIcon size={16} /></ListItemIcon>
            <ListItemText>Ungroup</ListItemText>
          </MenuItem>
        )}
      </Menu>

      {/* Properties Dialog */}
      <Dialog open={propertiesOpen} onClose={() => setPropertiesOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Node Properties</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} pt={1}>
            <TextField
              label="Label"
              value={data.label}
              onChange={(e) => onUpdate(id, { label: e.target.value })}
              fullWidth
            />
            <TextField
              label="Description"
              value={data.description || ''}
              onChange={(e) => onUpdate(id, { description: e.target.value })}
              fullWidth
              multiline
              rows={2}
            />
            <TextField
              label="Drill Down Target ID"
              value={data.drillDownTarget || ''}
              onChange={(e) => onUpdate(id, { drillDownTarget: e.target.value })}
              fullWidth
              placeholder="e.g., diagram-id-123"
              helperText="ID of the view to open when drilling down"
            />
            <TextField
              label="Type"
              value={data.type}
              onChange={(e) => onUpdate(id, { type: e.target.value as unknown })}
              fullWidth
              select
            >
              <MenuItem value="default">Default</MenuItem>
              <MenuItem value="process">Process</MenuItem>
              <MenuItem value="decision">Decision</MenuItem>
              <MenuItem value="data">Data</MenuItem>
              <MenuItem value="api">API</MenuItem>
              <MenuItem value="database">Database</MenuItem>
              <MenuItem value="cloud">Cloud</MenuItem>
              <MenuItem value="security">Security</MenuItem>
            </TextField>

            {/* Properties */}
            {data.properties && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>Properties</Typography>
                <List dense>
                  {Object.entries(data.properties).map(([key, value]) => (
                    <ListItem key={key}>
                      <ListItemText
                        primary={key}
                        secondary={String(value)}
                      />
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}

            {/* Validation */}
            {data.validation && (
              <Box>
                <Typography variant="subtitle2" gutterBottom>Validation</Typography>
                {data.validation.errors.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="error">Errors:</Typography>
                    <List dense>
                      {data.validation.errors.map((error, index) => (
                        <ListItem key={index}>
                          <ListItemText primary={error} />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                )}
                {data.validation.warnings.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="warning.main">Warnings:</Typography>
                    <List dense>
                      {data.validation.warnings.map((warning, index) => (
                        <ListItem key={index}>
                          <ListItemText primary={warning} />
                        </ListItem>
                      ))}
                    </List>
                  </Box>
                )}
              </Box>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPropertiesOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

// Group Node Component
const GroupNode: React.FC<CustomNodeProps> = (props) => {
  const [expanded, setExpanded] = useState(!props.data.collapsed);
  const theme = useTheme();

  return (
    <Paper
      className="min-w-[300px] min-h-[150px] border-[2px]"
      style={{ borderColor: props.selected ? resolveMuiColor(theme, 'primary', 'primary') : resolveMuiColor(theme, 'grey', 'primary') }}
      elevation={props.selected ? 4 : 1}
    >
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
        <Box display="flex" alignItems="center" gap={1}>
          <GroupIcon />
          <Typography variant="h6">{props.data.label}</Typography>
          <Badge badgeContent={props.data.children?.length || 0} color="primary">
            <GroupIcon />
          </Badge>
        </Box>
        <IconButton size="small" onClick={() => setExpanded(!expanded)}>
          {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
        </IconButton>
      </Box>

      <Collapse in={expanded}>
        <Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {props.data.description || 'Group container'}
          </Typography>
          {props.data.children && (
            <Typography variant="caption" color="text.secondary">
              Contains {props.data.children.length} nodes
            </Typography>
          )}
        </Box>
      </Collapse>

      {/* Group handles */}
      <Handle type="target" position={Position.Left} style={{ left: -8 }} />
      <Handle type="source" position={Position.Right} style={{ right: -8 }} />
    </Paper>
  );
};

// Process Node with execution capabilities
const ProcessNode: React.FC<CustomNodeProps> = (props) => {
  return <BaseCustomNode {...props} />;
};

// Decision Node with conditional logic
const DecisionNode: React.FC<CustomNodeProps> = (props) => {
  const theme = useTheme();
  return (
    <Box
      className="w-[120px] h-[120px] border-[2px]"
      style={{
        borderColor: props.selected ? resolveMuiColor(theme, 'primary', 'primary') : resolveMuiColor(theme, 'grey', 'primary'),
        transform: 'rotate(45deg)',
      }}
    >
      <Box className="text-center" >
        <CodeIcon />
        <Typography variant="caption" display="block">
          {props.data.label}
        </Typography>
      </Box>

      {/* Handles positioned for diamond shape */}
      <Handle type="target" position={Position.Top} style={{ top: -8, left: '50%', transform: 'translateX(-50%) rotate(-45deg)' }} />
      <Handle type="source" position={Position.Bottom} style={{ bottom: -8, left: '50%', transform: 'translateX(-50%) rotate(-45deg)' }} />
      <Handle type="source" position={Position.Right} style={{ right: -8, top: '50%', transform: 'translateY(-50%) rotate(-45deg)' }} />
    </Box>
  );
};

// Database Node
const DatabaseNode: React.FC<CustomNodeProps> = (props) => {
  const theme = useTheme();
  return (
    <Box
      className="w-[140px] h-[100px] border-[2px]"
      style={{ borderColor: props.selected ? resolveMuiColor(theme, 'primary', 'primary') : resolveMuiColor(theme, 'grey', 'primary') }}
    >
      <DatabaseIcon className="text-sky-600 text-[32px]" />
      <Typography variant="subtitle2" textAlign="center">
        {props.data.label}
      </Typography>
      {props.data.properties?.records && (
        <Typography variant="caption" color="text.secondary">
          {props.data.properties.records} records
        </Typography>
      )}

      <Handle type="target" position={Position.Left} style={{ left: -8 }} />
      <Handle type="source" position={Position.Right} style={{ right: -8 }} />
    </Box>
  );
};

// Wrapper components to match ReactFlow NodeProps interface
const createNodeWrapper = (Component: React.FC<CustomNodeProps>) => {
  return (props: NodeProps) => {
    // These would come from context or props in real implementation
    const onUpdate = (id: string, data: Partial<AdvancedNodeData>) => {
      console.log('Update node:', id, data);
    };
    const onDelete = (id: string) => {
      console.log('Delete node:', id);
    };
    const onGroup = (nodeIds: string[]) => {
      console.log('Group nodes:', nodeIds);
    };
    const onUngroup = (groupId: string) => {
      console.log('Ungroup:', groupId);
    };
    const onExecute = (id: string) => {
      console.log('Execute node:', id);
    };
    const onDrillDown = (id: string, target: string) => {
      console.log('Drill down:', id, target);
    };

    return (
      <Component
        {...props}
        data={props.data as AdvancedNodeData}
        onUpdate={onUpdate}
        onDelete={onDelete}
        onGroup={onGroup}
        onUngroup={onUngroup}
        onExecute={onExecute}
        onDrillDown={onDrillDown}
      />
    );
  };
};

// Import NodeGroup for PM workflow (Journey 1.1)
import { NodeGroup as WorkflowNodeGroup } from './NodeGroup';

// Export custom node types
export const customNodeTypes: NodeTypes = {
  default: createNodeWrapper(BaseCustomNode),
  process: createNodeWrapper(ProcessNode),
  decision: createNodeWrapper(DecisionNode),
  database: createNodeWrapper(DatabaseNode),
  group: createNodeWrapper(GroupNode),
  nodeGroup: WorkflowNodeGroup, // PM Handoff Workflow (Journey 1.1)
  data: createNodeWrapper(BaseCustomNode),
  api: createNodeWrapper(BaseCustomNode),
  cloud: createNodeWrapper(BaseCustomNode),
  security: createNodeWrapper(BaseCustomNode)
};

// Node creation helper
export const createCustomNode = (
  type: AdvancedNodeData['type'],
  position: { x: number; y: number },
  overrides: Partial<AdvancedNodeData> = {}
): Node<AdvancedNodeData> => {
  const baseData: AdvancedNodeData = {
    label: `${type.charAt(0).toUpperCase() + type.slice(1)} Node`,
    type,
    description: `A ${type} node`,
    status: 'idle',
    tags: [type],
    connections: { input: 0, output: 0 },
    validation: { isValid: true, errors: [], warnings: [] },
    ...overrides
  };

  return {
    id: `${type}-${Date.now()}`,
    type,
    position,
    data: baseData
  };
};
