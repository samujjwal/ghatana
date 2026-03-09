/**
 * CICDPipelineCanvas Component
 * 
 * DevOps CI/CD pipeline visualization and configuration
 * 
 * @doc.type component
 * @doc.purpose CI/CD pipeline design and export
 * @doc.layer product
 * @doc.pattern Canvas
 */

import React, { useState } from 'react';
import { Box, Surface as Paper, Typography, TextField, Button, Dialog, DialogTitle, DialogContent, DialogActions, Card, CardContent, Chip, IconButton, Select, MenuItem, FormControl, InputLabel, Stack, Alert, Divider, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Tooltip, FormControlLabel, Checkbox } from '@ghatana/ui';
import { Plus as AddIcon, Play as PlayIcon, Hammer as BuildIcon, CheckCircle as CheckCircleIcon, AlertCircle as ErrorIcon, Clock as ScheduleIcon, HardDrive as StorageIcon, Cloud as CloudIcon, Code as CodeIcon, Shield as SecurityIcon, Bug as BugIcon, Send as PublishIcon, Trash2 as DeleteIcon, Download as ExportIcon, Share2 as ShareIcon, Settings as SettingsIcon, Timer as TimerIcon, Github as GitHubIcon, GitBranch as JenkinsIcon, Cloud as CircleCIIcon } from 'lucide-react';
import { useCICDPipeline, type StageType, type PipelineStage, type StageStatus } from '../hooks/useCICDPipeline';

/**
 * Stage types with metadata
 */
const STAGE_TYPES = [
    { id: 'build', label: 'Build', icon: <BuildIcon />, color: '#2196f3', description: 'Compile and package code' },
    { id: 'test', label: 'Test', icon: <BugIcon />, color: '#4caf50', description: 'Run automated tests' },
    { id: 'security', label: 'Security', icon: <SecurityIcon />, color: '#ff9800', description: 'Security scanning' },
    { id: 'deploy', label: 'Deploy', icon: <PublishIcon />, color: '#9c27b0', description: 'Deploy to environment' },
    { id: 'approval', label: 'Approval', icon: <CheckCircleIcon />, color: '#f44336', description: 'Manual approval gate' },
] as const;

/**
 * Stage status types
 */
const STATUS_TYPES = [
    { value: 'pending', label: 'Pending', color: '#9e9e9e', icon: <ScheduleIcon /> },
    { value: 'running', label: 'Running', color: '#2196f3', icon: <PlayIcon /> },
    { value: 'success', label: 'Success', color: '#4caf50', icon: <CheckCircleIcon /> },
    { value: 'failed', label: 'Failed', color: '#f44336', icon: <ErrorIcon /> },
] as const;

/**
 * CI/CD platforms
 */
const PLATFORMS = [
    { id: 'github', label: 'GitHub Actions', icon: <GitHubIcon /> },
    { id: 'jenkins', label: 'Jenkins', icon: <JenkinsIcon /> },
    { id: 'circleci', label: 'CircleCI', icon: <CircleCIIcon /> },
] as const;

/**
 * Props for CICDPipelineCanvas
 */
export interface CICDPipelineCanvasProps {
    /**
     * Initial pipeline name
     */
    initialPipelineName?: string;

    /**
     * Initial repository
     */
    initialRepository?: string;

    /**
     * Callback when pipeline changes
     */
    onChange?: () => void;
}

/**
 * CI/CD Pipeline Canvas Component
 */
export const CICDPipelineCanvas: React.FC<CICDPipelineCanvasProps> = ({
    initialPipelineName,
    initialRepository,
    onChange,
}) => {
    const {
        stages,
        pipelineName,
        repository,
        setPipelineName,
        setRepository,
        addStage,
        updateStage,
        deleteStage,
        addStep,
        deleteStep,
        addEnvironmentVariable,
        deleteEnvironmentVariable,
        validatePipeline,
        calculateDuration,
        exportToGitHubActions,
        exportToJenkins,
        getStageCount,
        getStepCount,
    } = useCICDPipeline({
        initialPipelineName,
        initialRepository,
    });

    // Dialog states
    const [addStageDialogOpen, setAddStageDialogOpen] = useState(false);
    const [addStepDialogOpen, setAddStepDialogOpen] = useState(false);
    const [addEnvVarDialogOpen, setAddEnvVarDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [validationDialogOpen, setValidationDialogOpen] = useState(false);

    // Form states
    const [selectedStageId, setSelectedStageId] = useState<string>('');
    const [selectedPlatform, setSelectedPlatform] = useState<'github' | 'jenkins' | 'circleci'>('github');
    const [newStage, setNewStage] = useState({
        name: '',
        type: 'build' as StageType,
        description: '',
    });
    const [newStep, setNewStep] = useState({
        name: '',
        command: '',
        workingDirectory: '',
    });
    const [newEnvVar, setNewEnvVar] = useState({
        key: '',
        value: '',
    });

    // Validation results
    const [validationResults, setValidationResults] = useState<string[]>([]);

    // Handlers
    const handleAddStage = () => {
        addStage(newStage);
        setAddStageDialogOpen(false);
        setNewStage({
            name: '',
            type: 'build',
            description: '',
        });
        onChange?.();
    };

    const handleAddStep = () => {
        if (selectedStageId) {
            addStep(selectedStageId, newStep);
            setAddStepDialogOpen(false);
            setNewStep({
                name: '',
                command: '',
                workingDirectory: '',
            });
            setSelectedStageId('');
            onChange?.();
        }
    };

    const handleAddEnvVar = () => {
        if (selectedStageId) {
            addEnvironmentVariable(selectedStageId, newEnvVar.key, newEnvVar.value);
            setAddEnvVarDialogOpen(false);
            setNewEnvVar({
                key: '',
                value: '',
            });
            setSelectedStageId('');
            onChange?.();
        }
    };

    const handleValidate = () => {
        const results = validatePipeline();
        setValidationResults(results);
        setValidationDialogOpen(true);
    };

    const handleExport = () => {
        let exported = '';
        if (selectedPlatform === 'github') {
            exported = exportToGitHubActions();
        } else if (selectedPlatform === 'jenkins') {
            exported = exportToJenkins();
        }
        navigator.clipboard.writeText(exported);
        setExportDialogOpen(false);
    };

    const getStageTypeConfig = (type: StageType) => {
        return STAGE_TYPES.find(s => s.id === type) || STAGE_TYPES[0];
    };

    const getStatusConfig = (status: StageStatus) => {
        return STATUS_TYPES.find(s => s.value === status) || STATUS_TYPES[0];
    };

    const totalDuration = calculateDuration();

    return (
        <Box className="h-full flex flex-col bg-[#fafafa]">
            {/* Toolbar */}
            <Paper
                variant="raised"
                className="p-4 rounded-none border-b border-solid border-b-[#e0e0e0]"
            >
                <Stack direction="row" spacing={2} alignItems="center">
                    <PlayIcon className="text-[32px] text-[#1976d2]" />
                    <TextField
                        value={pipelineName}
                        onChange={(e) => setPipelineName(e.target.value)}
                        variant="standard"
                        placeholder="Pipeline Name"
                        className="grow text-2xl"
                    />
                    <Chip
                        icon={<BuildIcon />}
                        label={`${getStageCount()} Stages`}
                        tone="primary"
                        variant="outlined"
                    />
                    <Chip
                        icon={<CodeIcon />}
                        label={`${getStepCount()} Steps`}
                        tone="secondary"
                        variant="outlined"
                    />
                    <Chip
                        icon={<TimerIcon />}
                        label={`~${totalDuration}min`}
                        variant="outlined"
                    />
                    <Tooltip title="Validate Pipeline">
                        <IconButton onClick={handleValidate} tone="primary">
                            <CheckCircleIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Export Pipeline">
                        <IconButton onClick={() => setExportDialogOpen(true)}>
                            <ExportIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Share">
                        <IconButton>
                            <ShareIcon />
                        </IconButton>
                    </Tooltip>
                </Stack>
                <TextField
                    value={repository}
                    onChange={(e) => setRepository(e.target.value)}
                    variant="outlined"
                    placeholder="Repository URL (e.g., github.com/org/repo)"
                    fullWidth
                    className="mt-4"
                />
            </Paper>

            {/* Main Content */}
            <Box className="grow overflow-auto p-6">
                <Box
                    className="grid gap-6" style={{ gridTemplateColumns: {
                            xs: '1fr',
                            md: 'minmax(0, 3fr) minmax(0, 1fr)',
                        } }} >
                    {/* Left Column - Pipeline Stages */}
                    <Box>
                        <Card>
                            <CardContent>
                                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                                    <Typography as="h6" className="flex items-center gap-2">
                                        <BuildIcon />
                                        Pipeline Stages
                                    </Typography>
                                    <Button
                                        variant="solid"
                                        startIcon={<AddIcon />}
                                        onClick={() => setAddStageDialogOpen(true)}
                                    >
                                        Add Stage
                                    </Button>
                                </Stack>

                                {stages.length === 0 ? (
                                    <Alert severity="info">
                                        No stages defined yet. Click "Add Stage" to build your pipeline.
                                    </Alert>
                                ) : (
                                    <Box className="relative">
                                        {/* Pipeline flow line */}
                                        <Box
                                            className="absolute top-[40px] left-[0px] right-[0px] h-[2px] bg-[#e0e0e0] z-0"
                                        />

                                        <Stack direction="row" spacing={2} className="relative overflow-x-auto pb-4 z-[1]">
                                            {stages.map((stage, index) => {
                                                const typeConfig = getStageTypeConfig(stage.type);
                                                const statusConfig = getStatusConfig(stage.status);

                                                return (
                                                    <Paper
                                                        key={stage.id}
                                                        elevation={3}
                                                        className="min-w-[250px] p-4"
                                                        style={{ borderTop: `4px solid ${typeConfig.color}` }}
                                                    >
                                                        <IconButton
                                                            size="sm"
                                                            className="absolute top-[8px] right-[8px]"
                                                            onClick={() => deleteStage(stage.id)}
                                                        >
                                                            <DeleteIcon size={16} />
                                                        </IconButton>

                                                        <Stack spacing={1}>
                                                            <Stack direction="row" spacing={1} alignItems="center">
                                                                <Chip
                                                                    label={index + 1}
                                                                    size="sm"
                                                                    className="text-white" style={{ backgroundColor: typeConfig.color }}
                                                                />
                                                                {typeConfig.icon}
                                                                <Typography as="h6">{stage.name}</Typography>
                                                            </Stack>

                                                            <Chip
                                                                icon={typeConfig.icon}
                                                                label={typeConfig.label}
                                                                size="sm"
                                                                className="text-white" style={{ backgroundColor: typeConfig.color }}
                                                            />

                                                            <Chip
                                                                icon={statusConfig.icon}
                                                                label={statusConfig.label}
                                                                size="sm"
                                                                className="text-white" style={{ backgroundColor: statusConfig.color }}
                                                            />

                                                            {stage.description && (
                                                                <Typography as="p" className="text-sm" color="text.secondary">
                                                                    {stage.description}
                                                                </Typography>
                                                            )}

                                                            {stage.estimatedDuration && (
                                                                <Chip
                                                                    icon={<TimerIcon />}
                                                                    label={`${stage.estimatedDuration}min`}
                                                                    size="sm"
                                                                    variant="outlined"
                                                                />
                                                            )}

                                                            <Divider />

                                                            {/* Steps */}
                                                            <Box>
                                                                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
                                                                    <Typography as="p" className="text-sm font-medium">
                                                                        Steps ({stage.steps?.length || 0})
                                                                    </Typography>
                                                                    <IconButton
                                                                        size="sm"
                                                                        onClick={() => {
                                                                            setSelectedStageId(stage.id);
                                                                            setAddStepDialogOpen(true);
                                                                        }}
                                                                    >
                                                                        <AddIcon size={16} />
                                                                    </IconButton>
                                                                </Stack>
                                                                {stage.steps && stage.steps.length > 0 && (
                                                                    <List dense>
                                                                        {stage.steps.map((step) => (
                                                                            <ListItem
                                                                                key={step.id}
                                                                                className="pl-0 pr-0"
                                                                                secondaryAction={
                                                                                    <IconButton
                                                                                        edge="end"
                                                                                        size="sm"
                                                                                        onClick={() => deleteStep(stage.id, step.id)}
                                                                                    >
                                                                                        <DeleteIcon size={16} />
                                                                                    </IconButton>
                                                                                }
                                                                            >
                                                                                <ListItemIcon className="min-w-[32px]">
                                                                                    <CodeIcon size={16} />
                                                                                </ListItemIcon>
                                                                                <ListItemText
                                                                                    primary={step.name}
                                                                                    secondary={step.command}
                                                                                    primaryTypographyProps={{ variant: 'body2' }}
                                                                                    secondaryTypographyProps={{ variant: 'caption' }}
                                                                                />
                                                                            </ListItem>
                                                                        ))}
                                                                    </List>
                                                                )}
                                                            </Box>

                                                            {/* Environment Variables */}
                                                            {stage.environmentVariables && Object.keys(stage.environmentVariables).length > 0 && (
                                                                <>
                                                                    <Divider />
                                                                    <Box>
                                                                        <Typography as="p" className="text-sm font-medium" mb={1}>
                                                                            Environment Variables
                                                                        </Typography>
                                                                        <Stack spacing={0.5}>
                                                                            {Object.entries(stage.environmentVariables).map(([key, value]) => (
                                                                                <Stack key={key} direction="row" spacing={1} alignItems="center">
                                                                                    <Chip
                                                                                        label={`${key}=${value}`}
                                                                                        size="sm"
                                                                                        variant="outlined"
                                                                                        onDelete={() => deleteEnvironmentVariable(stage.id, key)}
                                                                                    />
                                                                                </Stack>
                                                                            ))}
                                                                        </Stack>
                                                                    </Box>
                                                                </>
                                                            )}

                                                            <Button
                                                                size="sm"
                                                                startIcon={<SettingsIcon />}
                                                                onClick={() => {
                                                                    setSelectedStageId(stage.id);
                                                                    setAddEnvVarDialogOpen(true);
                                                                }}
                                                            >
                                                                Add Env Var
                                                            </Button>
                                                        </Stack>
                                                    </Paper>
                                                );
                                            })}
                                        </Stack>
                                    </Box>
                                )}
                            </CardContent>
                        </Card>
                    </Box>

                    {/* Right Column - Stage Types & Platforms */}
                    <Box>
                        <Stack spacing={2}>
                            {/* Stage Types */}
                            <Card>
                                <CardContent>
                                    <Typography as="h6" mb={2}>
                                        Stage Types
                                    </Typography>
                                    <Stack spacing={1}>
                                        {STAGE_TYPES.map((type) => {
                                            const count = stages.filter(s => s.type === type.id).length;
                                            return (
                                                <Paper
                                                    key={type.id}
                                                    className="p-3"
                                                    style={{ borderLeft: `4px solid ${type.color}` }}
                                                >
                                                    <Stack direction="row" spacing={1} alignItems="center">
                                                        {type.icon}
                                                        <Box className="grow">
                                                            <Typography as="p" className="text-sm font-medium">{type.label}</Typography>
                                                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                                {type.description}
                                                            </Typography>
                                                        </Box>
                                                        <Chip
                                                            label={count}
                                                            size="sm"
                                                            className="text-white" style={{ backgroundColor: count > 0 ? type.color : '#e0e0e0' }}
                                                        />
                                                    </Stack>
                                                </Paper>
                                            );
                                        })}
                                    </Stack>
                                </CardContent>
                            </Card>

                            {/* Export Platforms */}
                            <Card>
                                <CardContent>
                                    <Typography as="h6" mb={2}>
                                        Export To
                                    </Typography>
                                    <Stack spacing={1}>
                                        {PLATFORMS.map((platform) => (
                                            <Button
                                                key={platform.id}
                                                variant="outlined"
                                                startIcon={platform.icon}
                                                fullWidth
                                                onClick={() => {
                                                    setSelectedPlatform(platform.id as unknown);
                                                    setExportDialogOpen(true);
                                                }}
                                            >
                                                {platform.label}
                                            </Button>
                                        ))}
                                    </Stack>
                                </CardContent>
                            </Card>
                        </Stack>
                    </Box>
                </Box>
            </Box>

            {/* Add Stage Dialog */}
            <Dialog open={addStageDialogOpen} onClose={() => setAddStageDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Pipeline Stage</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Stage Name"
                            value={newStage.name}
                            onChange={(e) => setNewStage({ ...newStage, name: e.target.value })}
                            fullWidth
                            required
                        />
                        <FormControl fullWidth>
                            <InputLabel>Stage Type</InputLabel>
                            <Select
                                value={newStage.type}
                                onChange={(e) => setNewStage({ ...newStage, type: e.target.value as StageType })}
                                label="Stage Type"
                            >
                                {STAGE_TYPES.map((type) => (
                                    <MenuItem key={type.id} value={type.id}>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            {type.icon}
                                            <span>{type.label}</span>
                                        </Stack>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            label="Description"
                            value={newStage.description}
                            onChange={(e) => setNewStage({ ...newStage, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={2}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddStageDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddStage} variant="solid" disabled={!newStage.name}>
                        Add Stage
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Step Dialog */}
            <Dialog open={addStepDialogOpen} onClose={() => setAddStepDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Step</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Step Name"
                            value={newStep.name}
                            onChange={(e) => setNewStep({ ...newStep, name: e.target.value })}
                            fullWidth
                            required
                        />
                        <TextField
                            label="Command"
                            value={newStep.command}
                            onChange={(e) => setNewStep({ ...newStep, command: e.target.value })}
                            fullWidth
                            required
                            placeholder="npm run build"
                        />
                        <TextField
                            label="Working Directory (optional)"
                            value={newStep.workingDirectory}
                            onChange={(e) => setNewStep({ ...newStep, workingDirectory: e.target.value })}
                            fullWidth
                            placeholder="./src"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddStepDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddStep} variant="solid" disabled={!newStep.name || !newStep.command}>
                        Add Step
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Environment Variable Dialog */}
            <Dialog open={addEnvVarDialogOpen} onClose={() => setAddEnvVarDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Environment Variable</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Key"
                            value={newEnvVar.key}
                            onChange={(e) => setNewEnvVar({ ...newEnvVar, key: e.target.value })}
                            fullWidth
                            required
                            placeholder="NODE_ENV"
                        />
                        <TextField
                            label="Value"
                            value={newEnvVar.value}
                            onChange={(e) => setNewEnvVar({ ...newEnvVar, value: e.target.value })}
                            fullWidth
                            required
                            placeholder="production"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddEnvVarDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddEnvVar} variant="solid" disabled={!newEnvVar.key || !newEnvVar.value}>
                        Add Variable
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Validation Dialog */}
            <Dialog open={validationDialogOpen} onClose={() => setValidationDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Pipeline Validation</DialogTitle>
                <DialogContent>
                    {validationResults.length === 0 ? (
                        <Alert severity="success">
                            Pipeline is valid! No issues found.
                        </Alert>
                    ) : (
                        <Stack spacing={1}>
                            <Alert severity="warning">
                                Found {validationResults.length} issue(s):
                            </Alert>
                            <List>
                                {validationResults.map((issue, index) => (
                                    <ListItem key={index}>
                                        <ListItemIcon>
                                            <ErrorIcon tone="warning" />
                                        </ListItemIcon>
                                        <ListItemText primary={issue} />
                                    </ListItem>
                                ))}
                            </List>
                        </Stack>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setValidationDialogOpen(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Export Pipeline</DialogTitle>
                <DialogContent>
                    <Stack spacing={2}>
                        <Alert severity="info">
                            Export pipeline configuration for {selectedPlatform === 'github' ? 'GitHub Actions' : selectedPlatform === 'jenkins' ? 'Jenkins' : 'CircleCI'}
                        </Alert>
                        <FormControl fullWidth>
                            <InputLabel>Platform</InputLabel>
                            <Select
                                value={selectedPlatform}
                                onChange={(e) => setSelectedPlatform(e.target.value as unknown)}
                                label="Platform"
                            >
                                {PLATFORMS.map((platform) => (
                                    <MenuItem key={platform.id} value={platform.id}>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            {platform.icon}
                                            <span>{platform.label}</span>
                                        </Stack>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <Stack spacing={1}>
                            <Typography as="p" className="text-sm">
                                <strong>Stages:</strong> {getStageCount()}
                            </Typography>
                            <Typography as="p" className="text-sm">
                                <strong>Steps:</strong> {getStepCount()}
                            </Typography>
                            <Typography as="p" className="text-sm">
                                <strong>Est. Duration:</strong> ~{totalDuration}min
                            </Typography>
                        </Stack>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleExport} variant="solid" startIcon={<ExportIcon />}>
                        Copy to Clipboard
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
