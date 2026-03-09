/**
 * Microservices Extractor Canvas Component
 * 
 * @doc.type component
 * @doc.purpose Solution Architect - Microservices Extraction (Journey 14.1)
 * @doc.layer product
 * @doc.pattern Canvas
 * 
 * Helps solution architects analyze monolithic applications and extract microservices
 * by identifying bounded contexts, analyzing coupling/cohesion, and generating service boundaries.
 * 
 * Features:
 * - Monolith entity mapping (classes, tables, APIs)
 * - Bounded context identification with AI assistance
 * - Service boundary visualization with dependency arrows
 * - Coupling/cohesion metrics per context
 * - Strangler fig pattern recommendations
 * - Service extraction strategy (choreography vs orchestration)
 * - Export to architecture diagrams (C4 Model, service mesh)
 * 
 * @example
 * ```tsx
 * <MicroservicesExtractorCanvas
 *   onExport={(diagram) => console.log(diagram)}
 * />
 * ```
 */

import React, { useState } from 'react';
import { Box, Surface as Paper, Typography, Stack, Chip, Button, IconButton, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Select, MenuItem, FormControl, InputLabel, Card, CardContent, CardActions, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Divider, Alert, Tooltip, LinearProgress, Badge } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, Pencil as EditIcon, GitBranch as TreeIcon, HardDrive as DatabaseIcon, Plug as ApiIcon, Code as CodeIcon, Users as GroupIcon, Activity as TimelineIcon, CheckCircle as CheckIcon, AlertTriangle as WarningIcon, AlertCircle as ErrorIcon, Cloud as CloudIcon, Building2 as ArchIcon, Brain as AIIcon, Download as DownloadIcon, Share2 as ShareIcon, ZoomIn as ZoomInIcon, CompareArrows as CouplingIcon, Layers as CohesionIcon } from 'lucide-react';
import { useMicroservicesExtractor } from '../hooks/useMicroservicesExtractor';
import type {
    MonolithEntity,
    BoundedContext,
    ServiceBoundary,
    ExtractionStrategy,
    CouplingLevel,
} from '../hooks/useMicroservicesExtractor';

/**
 * Entity type configuration
 */
const ENTITY_TYPES = [
    { value: 'class', label: 'Class', icon: CodeIcon, color: '#2196f3' },
    { value: 'table', label: 'Database Table', icon: DatabaseIcon, color: '#4caf50' },
    { value: 'api', label: 'API Endpoint', icon: ApiIcon, color: '#ff9800' },
    { value: 'module', label: 'Module', icon: TreeIcon, color: '#9c27b0' },
] as const;

/**
 * Extraction strategy configuration
 */
const EXTRACTION_STRATEGIES = [
    { value: 'strangler_fig', label: 'Strangler Fig Pattern', description: 'Gradually replace monolith with microservices' },
    { value: 'choreography', label: 'Event Choreography', description: 'Services communicate via events' },
    { value: 'orchestration', label: 'Service Orchestration', description: 'Central orchestrator coordinates services' },
    { value: 'big_bang', label: 'Big Bang Migration', description: 'Complete rewrite (high risk)' },
] as const;

/**
 * Coupling level configuration
 */
const COUPLING_LEVELS: Record<CouplingLevel, { color: string; icon: React.ElementType }> = {
    low: { color: '#4caf50', icon: CheckIcon },
    medium: { color: '#ff9800', icon: WarningIcon },
    high: { color: '#f44336', icon: ErrorIcon },
};

/**
 * Component Props
 */
export interface MicroservicesExtractorCanvasProps {
    /**
     * Callback when architecture diagram is exported
     */
    onExport?: (diagram: string, format: 'c4' | 'mermaid' | 'plantuml') => void;

    /**
     * Callback when service boundaries are shared
     */
    onShare?: (boundaries: ServiceBoundary[]) => void;
}

/**
 * Microservices Extractor Canvas Component
 */
export const MicroservicesExtractorCanvas: React.FC<MicroservicesExtractorCanvasProps> = ({
    onExport,
    onShare,
}) => {
    const {
        // State
        monolithName,
        setMonolithName,
        entities,
        contexts,
        serviceBoundaries,

        // Entity operations
        addEntity,
        updateEntity,
        deleteEntity,
        getEntity,
        getEntityCount,
        getEntitiesByType,

        // Context operations
        addContext,
        updateContext,
        deleteContext,
        getContext,
        getContextCount,
        assignEntityToContext,
        removeEntityFromContext,

        // Service boundary operations
        createServiceBoundary,
        updateServiceBoundary,
        deleteServiceBoundary,
        getServiceBoundary,
        getServiceBoundaryCount,

        // Analysis
        analyzeCoupling,
        analyzeCohesion,
        calculateComplexity,
        identifyBoundedContexts,
        recommendStrategy,
        validateBoundaries,

        // Export
        exportToC4Model,
        exportToMermaid,
        exportToServiceMesh,
    } = useMicroservicesExtractor();

    // Dialog states
    const [addEntityDialogOpen, setAddEntityDialogOpen] = useState(false);
    const [addContextDialogOpen, setAddContextDialogOpen] = useState(false);
    const [createBoundaryDialogOpen, setCreateBoundaryDialogOpen] = useState(false);
    const [aiAnalysisDialogOpen, setAiAnalysisDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);

    // Form states
    const [newEntityName, setNewEntityName] = useState('');
    const [newEntityType, setNewEntityType] = useState<'class' | 'table' | 'api' | 'module'>('class');
    const [newEntityDescription, setNewEntityDescription] = useState('');
    const [newEntityDependencies, setNewEntityDependencies] = useState<string[]>([]);

    const [newContextName, setNewContextName] = useState('');
    const [newContextDescription, setNewContextDescription] = useState('');
    const [newContextDomain, setNewContextDomain] = useState('');

    const [newBoundaryName, setNewBoundaryName] = useState('');
    const [newBoundaryStrategy, setNewBoundaryStrategy] = useState<ExtractionStrategy>('strangler_fig');
    const [newBoundaryContextIds, setNewBoundaryContextIds] = useState<string[]>([]);

    const [exportFormat, setExportFormat] = useState<'c4' | 'mermaid' | 'plantuml'>('c4');
    const [aiAnalyzing, setAiAnalyzing] = useState(false);

    // Selected items for analysis
    const [selectedContextId, setSelectedContextId] = useState<string | null>(null);

    /**
     * Handle entity addition
     */
    const handleAddEntity = () => {
        if (!newEntityName.trim()) return;

        addEntity({
            name: newEntityName,
            type: newEntityType,
            description: newEntityDescription || undefined,
            dependencies: newEntityDependencies,
        });

        // Reset form
        setNewEntityName('');
        setNewEntityType('class');
        setNewEntityDescription('');
        setNewEntityDependencies([]);
        setAddEntityDialogOpen(false);
    };

    /**
     * Handle context addition
     */
    const handleAddContext = () => {
        if (!newContextName.trim()) return;

        addContext({
            name: newContextName,
            description: newContextDescription || undefined,
            domain: newContextDomain || undefined,
        });

        // Reset form
        setNewContextName('');
        setNewContextDescription('');
        setNewContextDomain('');
        setAddContextDialogOpen(false);
    };

    /**
     * Handle service boundary creation
     */
    const handleCreateBoundary = () => {
        if (!newBoundaryName.trim() || newBoundaryContextIds.length === 0) return;

        createServiceBoundary({
            name: newBoundaryName,
            contextIds: newBoundaryContextIds,
            strategy: newBoundaryStrategy,
        });

        // Reset form
        setNewBoundaryName('');
        setNewBoundaryStrategy('strangler_fig');
        setNewBoundaryContextIds([]);
        setCreateBoundaryDialogOpen(false);
    };

    /**
     * Handle AI-powered bounded context identification
     */
    const handleAIAnalysis = async () => {
        setAiAnalyzing(true);
        try {
            const suggestedContexts = await identifyBoundedContexts();
            // Contexts are automatically added by identifyBoundedContexts
            setAiAnalysisDialogOpen(false);
        } finally {
            setAiAnalyzing(false);
        }
    };

    /**
     * Handle export
     */
    const handleExport = () => {
        let exportedDiagram: string;

        switch (exportFormat) {
            case 'c4':
                exportedDiagram = exportToC4Model();
                break;
            case 'mermaid':
                exportedDiagram = exportToMermaid();
                break;
            case 'plantuml':
                exportedDiagram = exportToServiceMesh();
                break;
            default:
                exportedDiagram = exportToC4Model();
        }

        onExport?.(exportedDiagram, exportFormat);
        setExportDialogOpen(false);
    };

    /**
     * Handle share
     */
    const handleShare = () => {
        onShare?.(serviceBoundaries);
    };

    /**
     * Get entity type config
     */
    const getEntityTypeConfig = (type: string) => {
        return ENTITY_TYPES.find((t) => t.value === type) || ENTITY_TYPES[0];
    };

    /**
     * Get coupling level config
     */
    const getCouplingLevelConfig = (level: CouplingLevel) => {
        return COUPLING_LEVELS[level];
    };

    // Calculate overall metrics
    const complexity = calculateComplexity();
    const recommendedStrategy = recommendStrategy();
    const validationIssues = validateBoundaries();

    return (
        <Box className="h-full flex flex-col bg-[#f5f5f5]">
            {/* Toolbar */}
            <Paper className="p-4 rounded-none">
                <Stack direction="row" spacing={2} alignItems="center">
                    <ArchIcon className="text-blue-600 text-[32px]" />
                    <TextField
                        value={monolithName}
                        onChange={(e) => setMonolithName(e.target.value)}
                        placeholder="Monolith Application Name"
                        variant="standard"
                        className="text-2xl grow"
                    />

                    <Chip
                        icon={<CodeIcon />}
                        label={`${getEntityCount()} Entities`}
                        tone="primary"
                        variant="outlined"
                    />
                    <Chip
                        icon={<GroupIcon />}
                        label={`${getContextCount()} Contexts`}
                        tone="secondary"
                        variant="outlined"
                    />
                    <Chip
                        icon={<CloudIcon />}
                        label={`${getServiceBoundaryCount()} Services`}
                        tone="success"
                        variant="outlined"
                    />

                    <Tooltip title="AI Analysis">
                        <IconButton tone="primary" onClick={() => setAiAnalysisDialogOpen(true)}>
                            <AIIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Export">
                        <IconButton tone="primary" onClick={() => setExportDialogOpen(true)}>
                            <DownloadIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Share">
                        <IconButton tone="primary" onClick={handleShare}>
                            <ShareIcon />
                        </IconButton>
                    </Tooltip>
                </Stack>
            </Paper>

            {/* Main Content */}
            <Box className="flex flex-1 overflow-hidden">
                {/* Left Panel - Entities */}
                <Paper className="m-4 p-4 overflow-auto w-[320px]">
                    <Stack spacing={2}>
                        <Box display="flex" justifyContent="space-between" alignItems="center">
                            <Typography as="h6">Monolith Entities</Typography>
                            <IconButton size="sm" tone="primary" onClick={() => setAddEntityDialogOpen(true)}>
                                <AddIcon />
                            </IconButton>
                        </Box>

                        {ENTITY_TYPES.map(({ value, label, icon: Icon, color }) => {
                            const entitiesOfType = getEntitiesByType(value);
                            if (entitiesOfType.length === 0) return null;

                            return (
                                <Box key={value}>
                                    <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                                        {label} ({entitiesOfType.length})
                                    </Typography>
                                    <List dense>
                                        {entitiesOfType.map((entity) => (
                                            <ListItem
                                                key={entity.id}
                                                secondaryAction={
                                                    <IconButton
                                                        edge="end"
                                                        size="sm"
                                                        onClick={() => deleteEntity(entity.id)}
                                                    >
                                                        <DeleteIcon size={16} />
                                                    </IconButton>
                                                }
                                            >
                                                <ListItemIcon>
                                                    <Icon className="" size={16} />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={entity.name}
                                                    secondary={entity.contextId ? 'Assigned' : 'Unassigned'}
                                                    primaryTypographyProps={{ variant: 'body2' }}
                                                    secondaryTypographyProps={{ variant: 'caption' }}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                </Box>
                            );
                        })}

                        {entities.length === 0 && (
                            <Alert severity="info">
                                Add entities from your monolith to start analysis
                            </Alert>
                        )}
                    </Stack>
                </Paper>

                {/* Center Panel - Bounded Contexts */}
                <Box className="flex-1 m-4 overflow-auto">
                    <Stack spacing={2}>
                        {/* Contexts Header */}
                        <Paper className="p-4">
                            <Stack direction="row" spacing={2} alignItems="center">
                                <Typography as="h6" flexGrow={1}>
                                    Bounded Contexts
                                </Typography>
                                <Button
                                    startIcon={<AddIcon />}
                                    variant="solid"
                                    onClick={() => setAddContextDialogOpen(true)}
                                >
                                    Add Context
                                </Button>
                            </Stack>
                        </Paper>

                        {/* Context Cards */}
                        <Box
                            className="grid gap-4" style={{ gridTemplateColumns: {
                                    xs: '1fr',
                                    md: 'repeat(2, minmax(0, 1fr))',
                                    lg: 'repeat(3, minmax(0, 1fr))',
                                } }} >
                            {contexts.map((context) => {
                                const contextEntities = entities.filter((e) => e.contextId === context.id);
                                const coupling = analyzeCoupling(context.id);
                                const cohesion = analyzeCohesion(context.id);
                                const couplingConfig = getCouplingLevelConfig(coupling.level);
                                const CouplingIcon = couplingConfig.icon;

                                return (
                                    <Box key={context.id}>
                                        <Card
                                            className="h-full border-blue-600" style={{ border: selectedContextId === context.id ? 2 : 0 }}
                                        >
                                            <CardContent>
                                                <Stack spacing={1}>
                                                    <Box display="flex" justifyContent="space-between" alignItems="start">
                                                        <Typography as="h6" gutterBottom>
                                                            {context.name}
                                                        </Typography>
                                                        <IconButton
                                                            size="sm"
                                                            onClick={() => deleteContext(context.id)}
                                                        >
                                                            <DeleteIcon size={16} />
                                                        </IconButton>
                                                    </Box>

                                                    {context.description && (
                                                        <Typography as="p" className="text-sm" color="text.secondary">
                                                            {context.description}
                                                        </Typography>
                                                    )}

                                                    {context.domain && (
                                                        <Chip
                                                            label={context.domain}
                                                            size="sm"
                                                            tone="primary"
                                                            variant="outlined"
                                                        />
                                                    )}

                                                    <Divider />

                                                    <Stack direction="row" spacing={1} alignItems="center">
                                                        <CouplingIcon className="text-base" style={{ color: couplingConfig.color }} />
                                                        <Typography as="span" className="text-xs text-gray-500">
                                                            Coupling: {coupling.level}
                                                        </Typography>
                                                        <Chip
                                                            label={`${coupling.dependencyCount} deps`}
                                                            size="sm"
                                                            variant="outlined"
                                                        />
                                                    </Stack>

                                                    <Stack direction="row" spacing={1} alignItems="center">
                                                        <CohesionIcon className="text-base" />
                                                        <Typography as="span" className="text-xs text-gray-500">
                                                            Cohesion: {cohesion.score}/10
                                                        </Typography>
                                                    </Stack>

                                                    <Box>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                            Entities ({contextEntities.length}):
                                                        </Typography>
                                                        <Stack direction="row" spacing={0.5} mt={0.5} flexWrap="wrap">
                                                            {contextEntities.slice(0, 3).map((entity) => {
                                                                const config = getEntityTypeConfig(entity.type);
                                                                const Icon = config.icon;
                                                                return (
                                                                    <Chip
                                                                        key={entity.id}
                                                                        icon={<Icon />}
                                                                        label={entity.name}
                                                                        size="sm"
                                                                        className="mb-1"
                                                                    />
                                                                );
                                                            })}
                                                            {contextEntities.length > 3 && (
                                                                <Chip
                                                                    label={`+${contextEntities.length - 3}`}
                                                                    size="sm"
                                                                    className="mb-1"
                                                                />
                                                            )}
                                                        </Stack>
                                                    </Box>
                                                </Stack>
                                            </CardContent>
                                            <CardActions>
                                                <Button
                                                    size="sm"
                                                    onClick={() => setSelectedContextId(context.id)}
                                                >
                                                    View Details
                                                </Button>
                                            </CardActions>
                                        </Card>
                                    </Box>
                                );
                            })}
                        </Box>

                        {contexts.length === 0 && (
                            <Paper className="p-8">
                                <Alert severity="info">
                                    Add bounded contexts or use AI analysis to identify them automatically
                                </Alert>
                            </Paper>
                        )}
                    </Stack>
                </Box>

                {/* Right Panel - Service Boundaries & Metrics */}
                <Paper className="m-4 p-4 overflow-auto w-[320px]">
                    <Stack spacing={3}>
                        {/* Metrics Section */}
                        <Box>
                            <Typography as="h6" gutterBottom>
                                Metrics
                            </Typography>
                            <Stack spacing={2}>
                                <Box>
                                    <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                        Complexity Score
                                    </Typography>
                                    <Box display="flex" alignItems="center" gap={1}>
                                        <LinearProgress
                                            variant="determinate"
                                            value={Math.min(complexity.score * 10, 100)}
                                            className="flex-1"
                                            color={complexity.score > 7 ? 'error' : complexity.score > 4 ? 'warning' : 'success'}
                                        />
                                        <Typography as="span" className="text-xs text-gray-500">
                                            {complexity.score}/10
                                        </Typography>
                                    </Box>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        {complexity.recommendation}
                                    </Typography>
                                </Box>

                                <Divider />

                                <Box>
                                    <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                        Recommended Strategy
                                    </Typography>
                                    <Chip
                                        label={EXTRACTION_STRATEGIES.find((s) => s.value === recommendedStrategy.strategy)?.label}
                                        tone="primary"
                                        size="sm"
                                    />
                                    <Typography as="span" className="text-xs text-gray-500" display="block" mt={1}>
                                        {recommendedStrategy.rationale}
                                    </Typography>
                                </Box>
                            </Stack>
                        </Box>

                        <Divider />

                        {/* Service Boundaries Section */}
                        <Box>
                            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                                <Typography as="h6">
                                    Service Boundaries
                                </Typography>
                                <IconButton
                                    size="sm"
                                    tone="primary"
                                    onClick={() => setCreateBoundaryDialogOpen(true)}
                                    disabled={contexts.length === 0}
                                >
                                    <AddIcon />
                                </IconButton>
                            </Box>

                            <Stack spacing={1}>
                                {serviceBoundaries.map((boundary) => {
                                    const boundaryContexts = boundary.contextIds
                                        .map((id) => getContext(id))
                                        .filter((c): c is BoundedContext => c !== undefined);

                                    return (
                                        <Card key={boundary.id} variant="outlined">
                                            <CardContent className="pb-2">
                                                <Stack spacing={1}>
                                                    <Box display="flex" justifyContent="space-between" alignItems="start">
                                                        <Typography as="p" className="text-sm font-medium">
                                                            {boundary.name}
                                                        </Typography>
                                                        <IconButton
                                                            size="sm"
                                                            onClick={() => deleteServiceBoundary(boundary.id)}
                                                        >
                                                            <DeleteIcon size={16} />
                                                        </IconButton>
                                                    </Box>

                                                    <Chip
                                                        label={EXTRACTION_STRATEGIES.find((s) => s.value === boundary.strategy)?.label}
                                                        size="sm"
                                                        variant="outlined"
                                                    />

                                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                        Contexts: {boundaryContexts.map((c) => c.name).join(', ')}
                                                    </Typography>
                                                </Stack>
                                            </CardContent>
                                        </Card>
                                    );
                                })}

                                {serviceBoundaries.length === 0 && (
                                    <Alert severity="info" className="mt-2">
                                        Create service boundaries from bounded contexts
                                    </Alert>
                                )}
                            </Stack>
                        </Box>

                        {validationIssues.length > 0 && (
                            <>
                                <Divider />
                                <Box>
                                    <Typography as="h6" gutterBottom tone="danger">
                                        Validation Issues
                                    </Typography>
                                    <List dense>
                                        {validationIssues.map((issue, index) => (
                                            <ListItem key={index}>
                                                <ListItemIcon>
                                                    <ErrorIcon tone="danger" size={16} />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={issue}
                                                    primaryTypographyProps={{ variant: 'body2' }}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                </Box>
                            </>
                        )}
                    </Stack>
                </Paper>
            </Box>

            {/* Add Entity Dialog */}
            <Dialog open={addEntityDialogOpen} onClose={() => setAddEntityDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Monolith Entity</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Entity Name"
                            value={newEntityName}
                            onChange={(e) => setNewEntityName(e.target.value)}
                            placeholder="e.g., UserService, users table, /api/users"
                            fullWidth
                            autoFocus
                        />

                        <FormControl fullWidth>
                            <InputLabel>Entity Type</InputLabel>
                            <Select
                                value={newEntityType}
                                onChange={(e) => setNewEntityType(e.target.value as typeof newEntityType)}
                                label="Entity Type"
                            >
                                {ENTITY_TYPES.map(({ value, label, icon: Icon }) => (
                                    <MenuItem key={value} value={value}>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            <Icon size={16} />
                                            <span>{label}</span>
                                        </Stack>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <TextField
                            label="Description (Optional)"
                            value={newEntityDescription}
                            onChange={(e) => setNewEntityDescription(e.target.value)}
                            placeholder="Describe the entity's purpose"
                            multiline
                            rows={2}
                            fullWidth
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddEntityDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddEntity} variant="solid" disabled={!newEntityName.trim()}>
                        Add Entity
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Context Dialog */}
            <Dialog open={addContextDialogOpen} onClose={() => setAddContextDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Bounded Context</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Context Name"
                            value={newContextName}
                            onChange={(e) => setNewContextName(e.target.value)}
                            placeholder="e.g., User Management, Order Processing"
                            fullWidth
                            autoFocus
                        />

                        <TextField
                            label="Domain (Optional)"
                            value={newContextDomain}
                            onChange={(e) => setNewContextDomain(e.target.value)}
                            placeholder="e.g., Core, Supporting, Generic"
                            fullWidth
                        />

                        <TextField
                            label="Description (Optional)"
                            value={newContextDescription}
                            onChange={(e) => setNewContextDescription(e.target.value)}
                            placeholder="Describe the bounded context"
                            multiline
                            rows={3}
                            fullWidth
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddContextDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddContext} variant="solid" disabled={!newContextName.trim()}>
                        Add Context
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Create Service Boundary Dialog */}
            <Dialog open={createBoundaryDialogOpen} onClose={() => setCreateBoundaryDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Create Service Boundary</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Service Name"
                            value={newBoundaryName}
                            onChange={(e) => setNewBoundaryName(e.target.value)}
                            placeholder="e.g., User Service, Order Service"
                            fullWidth
                            autoFocus
                        />

                        <FormControl fullWidth>
                            <InputLabel>Extraction Strategy</InputLabel>
                            <Select
                                value={newBoundaryStrategy}
                                onChange={(e) => setNewBoundaryStrategy(e.target.value as ExtractionStrategy)}
                                label="Extraction Strategy"
                            >
                                {EXTRACTION_STRATEGIES.map(({ value, label, description }) => (
                                    <MenuItem key={value} value={value}>
                                        <Box>
                                            <Typography as="p" className="text-sm">{label}</Typography>
                                            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                {description}
                                            </Typography>
                                        </Box>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Bounded Contexts</InputLabel>
                            <Select
                                multiple
                                value={newBoundaryContextIds}
                                onChange={(e) => setNewBoundaryContextIds(e.target.value as string[])}
                                label="Bounded Contexts"
                                renderValue={(selected) => (
                                    <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                        {selected.map((id) => {
                                            const context = getContext(id);
                                            return context ? (
                                                <Chip key={id} label={context.name} size="sm" />
                                            ) : null;
                                        })}
                                    </Stack>
                                )}
                            >
                                {contexts.map((context) => (
                                    <MenuItem key={context.id} value={context.id}>
                                        {context.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateBoundaryDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleCreateBoundary}
                        variant="solid"
                        disabled={!newBoundaryName.trim() || newBoundaryContextIds.length === 0}
                    >
                        Create Service
                    </Button>
                </DialogActions>
            </Dialog>

            {/* AI Analysis Dialog */}
            <Dialog open={aiAnalysisDialogOpen} onClose={() => !aiAnalyzing && setAiAnalysisDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>AI-Powered Bounded Context Analysis</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <Alert severity="info">
                            AI will analyze your monolith entities to identify natural bounded contexts based on:
                            <ul>
                                <li>Entity dependencies and coupling</li>
                                <li>Domain terminology patterns</li>
                                <li>Common naming conventions</li>
                                <li>Data flow analysis</li>
                            </ul>
                        </Alert>

                        {aiAnalyzing && (
                            <Box>
                                <LinearProgress />
                                <Typography as="p" className="text-sm" align="center" className="mt-4">
                                    Analyzing {getEntityCount()} entities...
                                </Typography>
                            </Box>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAiAnalysisDialogOpen(false)} disabled={aiAnalyzing}>
                        Cancel
                    </Button>
                    <Button
                        onClick={handleAIAnalysis}
                        variant="solid"
                        disabled={aiAnalyzing || entities.length === 0}
                        startIcon={<AIIcon />}
                    >
                        {aiAnalyzing ? 'Analyzing...' : 'Run AI Analysis'}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Export Architecture Diagram</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <FormControl fullWidth>
                            <InputLabel>Export Format</InputLabel>
                            <Select
                                value={exportFormat}
                                onChange={(e) => setExportFormat(e.target.value as typeof exportFormat)}
                                label="Export Format"
                            >
                                <MenuItem value="c4">C4 Model (PlantUML)</MenuItem>
                                <MenuItem value="mermaid">Mermaid Diagram</MenuItem>
                                <MenuItem value="plantuml">Service Mesh (PlantUML)</MenuItem>
                            </Select>
                        </FormControl>

                        <Box>
                            <Typography as="p" className="text-sm" color="text.secondary" gutterBottom>
                                Summary:
                            </Typography>
                            <Stack spacing={0.5}>
                                <Typography as="p" className="text-sm">
                                    • {getEntityCount()} Entities
                                </Typography>
                                <Typography as="p" className="text-sm">
                                    • {getContextCount()} Bounded Contexts
                                </Typography>
                                <Typography as="p" className="text-sm">
                                    • {getServiceBoundaryCount()} Service Boundaries
                                </Typography>
                            </Stack>
                        </Box>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setExportDialogOpen(false)}>Cancel</Button>
                    <Button
                        onClick={handleExport}
                        variant="solid"
                        startIcon={<DownloadIcon />}
                        disabled={serviceBoundaries.length === 0}
                    >
                        Export
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default MicroservicesExtractorCanvas;

// Export types
export type {
    MonolithEntity,
    BoundedContext,
    ServiceBoundary,
    ExtractionStrategy,
    CouplingLevel,
};
