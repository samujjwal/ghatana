import { AlertTriangle as WarningIcon, Merge as MergeIcon, ArrowLeftRight as SwapIcon, Hammer as ManualIcon, User as PersonIcon, Clock as ScheduleIcon, CompareArrows as CompareIcon, Undo2 as UndoIcon, Redo2 as RedoIcon, History as HistoryIcon, Eye as VisibilityIcon, Pencil as EditIcon, Trash2 as DeleteIcon, Plus as AddIcon, ChevronDown as ExpandMoreIcon, X as CloseIcon, Check as CheckIcon, AlertCircle as ErrorIcon, Info as InfoIcon } from 'lucide-react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  Tab,
  Tabs,
  Alert,
  IconButton,
  Tooltip,
  Card,
  CardContent,
  CardActions,
  Grid,
  Avatar,
  Select,
  FormControl,
  InputLabel,
  Switch,
  FormControlLabel,
  Surface as Paper,
  InteractiveList as List,
} from '@ghatana/ui';
import { MenuItem } from '@ghatana/ui';
import { Accordion, AccordionSummary, AccordionDetails } from '@ghatana/yappc-ui';
import { formatDistanceToNow } from 'date-fns';
import React, { useState, useCallback } from 'react';

import {
  OperationalTransform
} from '../hooks/useAdvancedCollaboration';

import type {
  ConflictResolution,
  VersionHistory,
  CollaborativeUser,
  CollaborationMetrics
} from '../hooks/useAdvancedCollaboration';

/**
 *
 */
interface ConflictResolutionDialogProps {
  open: boolean;
  onClose: () => void;
  conflicts: ConflictResolution[];
  onResolveConflict: (conflictId: string, resolution: 'merge' | 'overwrite' | 'manual', data?: unknown) => void;
  participants: CollaborativeUser[];
}

/**
 *
 */
interface VersionHistoryDialogProps {
  open: boolean;
  onClose: () => void;
  versions: VersionHistory[];
  onRestoreVersion: (versionId: string) => void;
  onCompareVersions: (v1: string, v2: string) => void;
  currentUser: CollaborativeUser | null;
}

/**
 *
 */
interface CollaborationMetricsDialogProps {
  open: boolean;
  onClose: () => void;
  metrics: CollaborationMetrics;
  participants: CollaborativeUser[];
  onGetLatencyMetrics: () => Promise<number>;
  onGetConflictStats: () => Promise<unknown>;
}

// Operation type icons
const getOperationIcon = (operation: string, target: string) => {
  switch (operation) {
    case 'insert':
      return <AddIcon color="success" />;
    case 'delete':  
      return <DeleteIcon color="error" />;
    case 'update':
      return <EditIcon color="warning" />;
    case 'move':
      return <SwapIcon color="info" />;
    default:
      return <InfoIcon />;
  }
};

// Conflict type colors
const getConflictTypeColor = (type: ConflictResolution['type']) => {
  switch (type) {
    case 'concurrent_edit':
      return 'warning';
    case 'version_mismatch':
      return 'error';
    case 'merge_conflict':
      return 'info';
    default:
      return 'default';
  }
};

// Conflict Resolution Dialog
export const ConflictResolutionDialog: React.FC<ConflictResolutionDialogProps> = ({
  open,
  onClose,
  conflicts,
  onResolveConflict,
  participants
}) => {
  const [selectedConflict, setSelectedConflict] = useState<ConflictResolution | null>(null);
  const [resolutionStrategy, setResolutionStrategy] = useState<'merge' | 'overwrite' | 'manual'>('merge');
  const [tabValue, setTabValue] = useState(0);

  const handleResolveConflict = useCallback((conflictId: string) => {
    onResolveConflict(conflictId, resolutionStrategy);
    setSelectedConflict(null);
  }, [onResolveConflict, resolutionStrategy]);

  const getParticipantName = useCallback((userId: string) => {
    const participant = participants.find(p => p.id === userId);
    return participant?.name || `User ${userId}`;
  }, [participants]);

  return (
    <Dialog 
      open={open} 
      onClose={onClose} 
      maxWidth="lg" 
      fullWidth
      PaperProps={{ sx: { minHeight: '80vh' } }}
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <WarningIcon color="warning" />
          <Typography variant="h6">
            Conflict Resolution ({conflicts.length} conflicts)
          </Typography>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Box className="mb-4 border-gray-200 dark:border-gray-700 border-b" >
          <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)}>
            <Tab label={`Active Conflicts (${conflicts.filter(c => c.resolution === 'manual').length})`} />
            <Tab label={`Resolved (${conflicts.filter(c => c.resolution !== 'manual').length})`} />
            <Tab label="Resolution Preview" />
          </Tabs>
        </Box>

        {/* Active Conflicts Tab */}
        {tabValue === 0 && (
          <Box>
            {conflicts.filter(c => c.resolution === 'manual').length === 0 ? (
              <Alert severity="success">No active conflicts! 🎉</Alert>
            ) : (
              <List>
                {conflicts
                  .filter(c => c.resolution === 'manual')
                  .map((conflict) => (
                    <React.Fragment key={conflict.conflictId}>
                      <ListItem alignItems="flex-start">
                        <ListItemIcon>
                          <Chip
                            icon={<WarningIcon />}
                            label={conflict.type.replace('_', ' ')}
                            color={getConflictTypeColor(conflict.type)}
                            size="small"
                          />
                        </ListItemIcon>
                        <ListItemText
                          primary={
                            <Box display="flex" alignItems="center" gap={1}>
                              <Typography variant="subtitle1">
                                Conflict in {conflict.operations[0].target}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {formatDistanceToNow(conflict.timestamp)} ago
                              </Typography>
                            </Box>
                          }
                          secondary={
                            <Box mt={1}>
                              <Typography variant="body2" color="text.secondary" gutterBottom>
                                {conflict.operations.length} conflicting operations:
                              </Typography>
                              {conflict.operations.map((op, index) => (
                                <Paper key={op.id} className="p-2 mb-2 bg-gray-50 dark:bg-gray-800">
                                  <Box display="flex" alignItems="center" gap={1}>
                                    {getOperationIcon(op.operation, op.target)}
                                    <Typography variant="body2">
                                      <strong>{getParticipantName(op.author)}</strong> {op.operation}d {op.target}
                                    </Typography>
                                    <Chip 
                                      label={formatDistanceToNow(op.timestamp)} 
                                      size="small" 
                                      variant="outlined"
                                    />
                                  </Box>
                                </Paper>
                              ))}
                              <Box display="flex" gap={1} mt={2}>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  startIcon={<MergeIcon />}
                                  onClick={() => handleResolveConflict(conflict.conflictId)}
                                >
                                  Auto Merge
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  startIcon={<ManualIcon />}
                                  onClick={() => setSelectedConflict(conflict)}
                                >
                                  Manual Resolution
                                </Button>
                              </Box>
                            </Box>
                          }
                        />
                      </ListItem>
                      <Divider />
                    </React.Fragment>
                  ))}
              </List>
            )}
          </Box>
        )}

        {/* Resolved Conflicts Tab */}
        {tabValue === 1 && (
          <List>
            {conflicts
              .filter(c => c.resolution !== 'manual')
              .map((conflict) => (
                <ListItem key={conflict.conflictId}>
                  <ListItemIcon>
                    <CheckIcon color="success" />
                  </ListItemIcon>
                  <ListItemText
                    primary={`${conflict.type.replace('_', ' ')} - ${conflict.resolution}`}
                    secondary={`Resolved ${formatDistanceToNow(conflict.timestamp)} ago`}
                  />
                </ListItem>
              ))}
          </List>
        )}

        {/* Resolution Preview Tab */}  
        {tabValue === 2 && selectedConflict && (
          <Box>
            <Typography variant="h6" gutterBottom>
              Manual Resolution Preview
            </Typography>
            <Paper className="p-4">
              <FormControl fullWidth className="mb-4">
                <InputLabel>Resolution Strategy</InputLabel>
                <Select
                  value={resolutionStrategy}
                  onChange={(e) => setResolutionStrategy(e.target.value as unknown)}
                >
                  <MenuItem value="merge">Smart Merge</MenuItem>
                  <MenuItem value="overwrite">Overwrite (Latest Wins)</MenuItem>
                  <MenuItem value="manual">Manual Selection</MenuItem>
                </Select>
              </FormControl>

              <Typography variant="subtitle2" gutterBottom>
                Preview of {resolutionStrategy} strategy:
              </Typography>
              <Alert severity="info">
                This would apply the {resolutionStrategy} resolution to the conflicting operations.
              </Alert>
            </Paper>
          </Box>
        )}
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {conflicts.filter(c => c.resolution === 'manual').length > 0 && (
          <Button
            variant="contained"
            onClick={() => {
              conflicts
                .filter(c => c.resolution === 'manual')
                .forEach(c => handleResolveConflict(c.conflictId));
            }}
          >
            Resolve All Conflicts
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

// Version History Dialog
export const VersionHistoryDialog: React.FC<VersionHistoryDialogProps> = ({
  open,
  onClose,
  versions,
  onRestoreVersion,
  onCompareVersions,
  currentUser
}) => {
  const [selectedVersions, setSelectedVersions] = useState<string[]>([]);
  const [compareMode, setCompareMode] = useState(false);

  const handleVersionSelect = useCallback((versionId: string) => {
    if (compareMode) {
      setSelectedVersions(prev => {
        if (prev.includes(versionId)) {
          return prev.filter(id => id !== versionId);
        }
        return prev.length >= 2 ? [prev[1], versionId] : [...prev, versionId];
      });
    } else {
      onRestoreVersion(versionId);
    }
  }, [compareMode, onRestoreVersion]);

  const handleCompare = useCallback(() => {
    if (selectedVersions.length === 2) {
      onCompareVersions(selectedVersions[0], selectedVersions[1]);
    }
  }, [selectedVersions, onCompareVersions]);

  return (
    <Dialog 
      open={open} 
      onClose={onClose} 
      maxWidth="md" 
      fullWidth
      PaperProps={{ sx: { minHeight: '70vh' } }}
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Box display="flex" alignItems="center" gap={1}>
            <HistoryIcon />
            <Typography variant="h6">Version History ({versions.length})</Typography>
          </Box>
          <FormControlLabel
            control={
              <Switch
                checked={compareMode}
                onChange={(e) => {
                  setCompareMode(e.target.checked);
                  setSelectedVersions([]);
                }}
              />
            }
            label="Compare Mode"
          />
        </Box>
      </DialogTitle>

      <DialogContent>
        <Box>
          {versions.map((version, index) => (
            <Box key={version.id} display="flex" mb={2}>
              <Box display="flex" flexDirection="column" alignItems="center" mr={2}>
                <Avatar 
                  className="w-[32px] h-[32px]" style={{ backgroundColor: selectedVersions.includes(version.id) ? '#1976d2' : 
                             index === 0 ? '#2e7d32' : '#bdbdbd' }}
                >
                  {index === 0 ? <CheckIcon /> : <HistoryIcon />}
                </Avatar>
                {index < versions.length - 1 && (
                  <Box style={{ width: 2, height: 40, backgroundColor: '#e0e0e0', marginTop: 8 }} />
                )}
              </Box>
              <Box flex={1}>
                <Card 
                  className="mb-4 border-blue-600 cursor-pointer" style={{ border: selectedVersions.includes(version.id) ? 2 : 0 }}
                  onClick={() => handleVersionSelect(version.id)}
                >
                  <CardContent>
                    <Box display="flex" alignItems="center" justifyContent="between" mb={1}>
                      <Typography variant="subtitle1">
                        {version.description}
                      </Typography>
                      {index === 0 && (
                        <Chip label="Current" color="success" size="small" />
                      )}
                    </Box>
                    
                    <Box display="flex" alignItems="center" gap={2} mb={1}>
                      <Box display="flex" alignItems="center" gap={0.5}>
                        <PersonIcon size={16} />
                        <Typography variant="body2">{version.author}</Typography>
                      </Box>
                      <Box display="flex" alignItems="center" gap={0.5}>
                        <ScheduleIcon size={16} />
                        <Typography variant="body2">
                          {formatDistanceToNow(version.timestamp)} ago
                        </Typography>
                      </Box>
                    </Box>

                    <Typography variant="body2" color="text.secondary">
                      Version {version.version} • {version.changes.length} operations
                    </Typography>

                    <Box display="flex" gap={1} mt={1}>
                      <Chip 
                        label={`${version.snapshot.nodes.length} nodes`} 
                        size="small" 
                        variant="outlined"
                      />
                      <Chip 
                        label={`${version.snapshot.edges.length} edges`} 
                        size="small" 
                        variant="outlined"
                      />
                    </Box>
                  </CardContent>
                  
                  {!compareMode && (
                    <CardActions>
                      <Button 
                        size="small" 
                        startIcon={<UndoIcon />}
                        disabled={index === 0}
                      >
                        Restore
                      </Button>
                      <Button size="small" startIcon={<VisibilityIcon />}>
                        Preview
                      </Button>
                    </CardActions>
                  )}
                </Card>
              </Box>
            </Box>
          ))}
        </Box>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {compareMode && selectedVersions.length === 2 && (
          <Button 
            variant="contained" 
            startIcon={<CompareIcon />}
            onClick={handleCompare}
          >
            Compare Selected Versions
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

// Collaboration Metrics Dialog
export const CollaborationMetricsDialog: React.FC<CollaborationMetricsDialogProps> = ({
  open,
  onClose,
  metrics,
  participants,
  onGetLatencyMetrics,
  onGetConflictStats
}) => {
  const [latency, setLatency] = useState<number | null>(null);
  const [conflictStats, setConflictStats] = useState<unknown>(null);

  React.useEffect(() => {
    if (open) {
      onGetLatencyMetrics().then(setLatency);
      onGetConflictStats().then(setConflictStats);
    }
  }, [open, onGetLatencyMetrics, onGetConflictStats]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return 'success';
      case 'away': return 'warning';
      case 'offline': return 'error';
      default: return 'default';
    }
  };

  const getSyncStatusIcon = (status: string) => {
    switch (status) {
      case 'synced': return <CheckIcon color="success" />;
      case 'syncing': return <InfoIcon color="info" />;
      case 'error': return <ErrorIcon color="error" />;
      default: return <InfoIcon />;
    }
  };

  return (
    <Dialog 
      open={open} 
      onClose={onClose} 
      maxWidth="md" 
      fullWidth
      PaperProps={{ sx: { minHeight: '60vh' } }}
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <InfoIcon />
          <Typography variant="h6">Collaboration Metrics</Typography>
        </Box>
      </DialogTitle>

      <DialogContent>
        <Grid container spacing={3}>
          {/* Overview Metrics */}
          <Grid item xs={12}>
            <Paper className="p-4">
              <Typography variant="h6" gutterBottom>Overview</Typography>
              <Grid container spacing={2}>
                <Grid item xs={3}>
                  <Box textAlign="center">
                    <Typography variant="h4" color="primary">
                      {metrics.activeUsers}
                    </Typography>
                    <Typography variant="body2">Active Users</Typography>
                  </Box>
                </Grid>
                <Grid item xs={3}>
                  <Box textAlign="center">
                    <Typography variant="h4" color="secondary">
                      {metrics.totalOperations}
                    </Typography>
                    <Typography variant="body2">Total Operations</Typography>
                  </Box>
                </Grid>
                <Grid item xs={3}>
                  <Box textAlign="center">
                    <Typography variant="h4" color="success.main">
                      {metrics.conflictsResolved}
                    </Typography>
                    <Typography variant="body2">Conflicts Resolved</Typography>
                  </Box>
                </Grid>
                <Grid item xs={3}>
                  <Box textAlign="center">
                    <Typography variant="h4" color="info.main">
                      {latency || '--'}ms
                    </Typography>
                    <Typography variant="body2">Latency</Typography>
                  </Box>
                </Grid>
              </Grid>
            </Paper>
          </Grid>

          {/* Sync Status */}
          <Grid item xs={12} md={6}>
            <Paper className="p-4">
              <Typography variant="h6" gutterBottom>Sync Status</Typography>
              <Box display="flex" alignItems="center" gap={1} mb={1}>
                {getSyncStatusIcon(metrics.syncStatus)}
                <Typography variant="body1" className="capitalize">
                  {metrics.syncStatus}
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Last sync: {formatDistanceToNow(metrics.lastSync)} ago
              </Typography>
            </Paper>
          </Grid>

          {/* Conflict Statistics */}
          <Grid item xs={12} md={6}>
            <Paper className="p-4">
              <Typography variant="h6" gutterBottom>Conflict Statistics</Typography>
              {conflictStats ? (
                <Box>
                  <Typography variant="body2">
                    Total: {conflictStats.totalConflicts}
                  </Typography>
                  <Typography variant="body2">
                    Resolved: {conflictStats.resolvedConflicts}
                  </Typography>
                  <Typography variant="body2">
                    Pending: {conflictStats.pendingConflicts}
                  </Typography>
                </Box>
              ) : (
                <Typography variant="body2">Loading...</Typography>
              )}
            </Paper>
          </Grid>

          {/* Active Participants */}
          <Grid item xs={12}>
            <Paper className="p-4">
              <Typography variant="h6" gutterBottom>
                Active Participants ({participants.length})
              </Typography>
              <List>
                {participants.map((participant) => (
                  <ListItem key={participant.id}>
                    <ListItemIcon>
                      <Avatar 
                        src={participant.avatar} 
                        className="w-[32px] h-[32px]"
                      >
                        {participant.name.charAt(0)}
                      </Avatar>
                    </ListItemIcon>
                    <ListItemText
                      primary={participant.name}
                      secondary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Chip
                            label={participant.status}
                            color={getStatusColor(participant.status)}
                            size="small"
                          />
                          <Typography variant="caption">
                            {participant.role}
                          </Typography>
                          <Typography variant="caption">
                            • Last seen {formatDistanceToNow(participant.lastSeen)} ago
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Grid>
        </Grid>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button 
          onClick={() => {
            onGetLatencyMetrics().then(setLatency);
            onGetConflictStats().then(setConflictStats);
          }}
        >
          Refresh
        </Button>
      </DialogActions>
    </Dialog>
  );
};