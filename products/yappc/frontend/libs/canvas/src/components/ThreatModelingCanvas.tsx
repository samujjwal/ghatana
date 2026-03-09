/**
 * ThreatModelingCanvas Component
 * 
 * Security threat modeling and analysis canvas using STRIDE framework
 * 
 * @doc.type component
 * @doc.purpose Security threat modeling and mitigation planning
 * @doc.layer product
 * @doc.pattern Canvas
 */

import React, { useState } from 'react';
import { Box, Surface as Paper, Typography, TextField, Button, Dialog, DialogTitle, DialogContent, DialogActions, Card, CardContent, Chip, IconButton, Select, MenuItem, FormControl, InputLabel, Stack, Alert, Divider, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Tooltip } from '@ghatana/ui';
import { Plus as AddIcon, Shield as SecurityIcon, AlertTriangle as WarningIcon, Shield as ShieldIcon, Bug as BugIcon, Lock as LockIcon, Key as KeyIcon, UserCircle as AccountIcon, BarChart3 as AssessmentIcon, X as CloseIcon, Trash2 as DeleteIcon, Pencil as EditIcon, Share2 as ShareIcon, Download as ExportIcon, Eye as VisibilityIcon, AlertCircle as ErrorIcon, CheckCircle as CheckCircleIcon } from 'lucide-react';
import { useThreatModeling, type ThreatCategory, type RiskLevel, type Threat, type Asset } from '../hooks/useThreatModeling';

/**
 * STRIDE threat categories
 */
const STRIDE_CATEGORIES = [
    { id: 'spoofing', label: 'Spoofing', icon: <AccountIcon />, color: '#f44336', description: 'Illegitimate access to identity' },
    { id: 'tampering', label: 'Tampering', icon: <EditIcon />, color: '#ff9800', description: 'Malicious data modification' },
    { id: 'repudiation', label: 'Repudiation', icon: <VisibilityIcon />, color: '#ff5722', description: 'Denying actions without proof' },
    { id: 'informationDisclosure', label: 'Information Disclosure', icon: <LockIcon />, color: '#9c27b0', description: 'Unauthorized information access' },
    { id: 'denialOfService', label: 'Denial of Service', icon: <ErrorIcon />, color: '#e91e63', description: 'Service unavailability' },
    { id: 'elevationOfPrivilege', label: 'Elevation of Privilege', icon: <KeyIcon />, color: '#3f51b5', description: 'Unauthorized privilege escalation' },
] as const;

/**
 * Risk severity levels
 */
const RISK_LEVELS = [
    { value: 'critical', label: 'Critical', color: '#d32f2f', icon: <ErrorIcon /> },
    { value: 'high', label: 'High', color: '#f57c00', icon: <WarningIcon /> },
    { value: 'medium', label: 'Medium', color: '#fbc02d', icon: <WarningIcon /> },
    { value: 'low', label: 'Low', color: '#388e3c', icon: <CheckCircleIcon /> },
] as const;

/**
 * Asset types
 */
const ASSET_TYPES = [
    { value: 'data', label: 'Data', icon: <AssessmentIcon /> },
    { value: 'service', label: 'Service', icon: <SecurityIcon /> },
    { value: 'infrastructure', label: 'Infrastructure', icon: <ShieldIcon /> },
    { value: 'user', label: 'User', icon: <AccountIcon /> },
] as const;

/**
 * Props for ThreatModelingCanvas
 */
export interface ThreatModelingCanvasProps {
    /**
     * Initial model name
     */
    initialModelName?: string;

    /**
     * Initial system description
     */
    initialSystemDescription?: string;

    /**
     * Callback when model changes
     */
    onChange?: () => void;
}

/**
 * Threat Modeling Canvas Component
 */
export const ThreatModelingCanvas: React.FC<ThreatModelingCanvasProps> = ({
    initialModelName,
    initialSystemDescription,
    onChange,
}) => {
    const {
        threats,
        assets,
        modelName,
        systemDescription,
        setModelName,
        setSystemDescription,
        addThreat,
        updateThreat,
        deleteThreat,
        addAsset,
        deleteAsset,
        addMitigation,
        deleteMitigation,
        updateMitigationStatus,
        analyzeSTRIDE,
        calculateRiskScore,
        exportModel,
        getThreatCount,
        getAssetCount,
        getMitigationCount,
    } = useThreatModeling({
        initialModelName,
        initialSystemDescription,
    });

    // Dialog states
    const [addThreatDialogOpen, setAddThreatDialogOpen] = useState(false);
    const [addAssetDialogOpen, setAddAssetDialogOpen] = useState(false);
    const [addMitigationDialogOpen, setAddMitigationDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [strideAnalysisOpen, setStrideAnalysisOpen] = useState(false);

    // Form states
    const [selectedThreatId, setSelectedThreatId] = useState<string>('');
    const [newThreat, setNewThreat] = useState({
        title: '',
        description: '',
        category: 'spoofing' as ThreatCategory,
        affectedAsset: '',
        attackVector: '',
    });
    const [newAsset, setNewAsset] = useState({
        name: '',
        type: 'data' as const,
        description: '',
    });
    const [newMitigation, setNewMitigation] = useState({
        strategy: '',
        implementation: '',
        owner: '',
    });

    // STRIDE analysis results
    const [strideResults, setStrideResults] = useState<ReturnType<typeof analyzeSTRIDE> | null>(null);

    // Handlers
    const handleAddThreat = () => {
        addThreat(newThreat);
        setAddThreatDialogOpen(false);
        setNewThreat({
            title: '',
            description: '',
            category: 'spoofing',
            affectedAsset: '',
            attackVector: '',
        });
        onChange?.();
    };

    const handleAddAsset = () => {
        addAsset(newAsset);
        setAddAssetDialogOpen(false);
        setNewAsset({
            name: '',
            type: 'data',
            description: '',
        });
        onChange?.();
    };

    const handleAddMitigation = () => {
        if (selectedThreatId) {
            addMitigation(selectedThreatId, newMitigation);
            setAddMitigationDialogOpen(false);
            setNewMitigation({
                strategy: '',
                implementation: '',
                owner: '',
            });
            setSelectedThreatId('');
            onChange?.();
        }
    };

    const handleRunStrideAnalysis = () => {
        const results = analyzeSTRIDE();
        setStrideResults(results);
        setStrideAnalysisOpen(true);
    };

    const handleExport = () => {
        const exported = exportModel();
        navigator.clipboard.writeText(exported);
        setExportDialogOpen(false);
    };

    const getCategoryConfig = (category: ThreatCategory) => {
        return STRIDE_CATEGORIES.find(c => c.id === category) || STRIDE_CATEGORIES[0];
    };

    const getRiskLevelConfig = (level: RiskLevel) => {
        return RISK_LEVELS.find(r => r.value === level) || RISK_LEVELS[3];
    };

    return (
        <Box className="h-full flex flex-col bg-[#fafafa]">
            {/* Toolbar */}
            <Paper
                variant="raised"
                className="p-4 rounded-none border-b border-solid border-b-[#e0e0e0]"
            >
                <Stack direction="row" spacing={2} alignItems="center">
                    <SecurityIcon className="text-[32px] text-[#1976d2]" />
                    <TextField
                        value={modelName}
                        onChange={(e) => setModelName(e.target.value)}
                        variant="standard"
                        placeholder="Threat Model Name"
                        className="grow text-2xl"
                    />
                    <Chip
                        icon={<BugIcon />}
                        label={`${getThreatCount()} Threats`}
                        tone="danger"
                        variant="outlined"
                    />
                    <Chip
                        icon={<ShieldIcon />}
                        label={`${getAssetCount()} Assets`}
                        tone="primary"
                        variant="outlined"
                    />
                    <Chip
                        icon={<CheckCircleIcon />}
                        label={`${getMitigationCount()} Mitigations`}
                        tone="success"
                        variant="outlined"
                    />
                    <Tooltip title="Run STRIDE Analysis">
                        <IconButton onClick={handleRunStrideAnalysis} tone="primary">
                            <AssessmentIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Export Model">
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
                    value={systemDescription}
                    onChange={(e) => setSystemDescription(e.target.value)}
                    variant="outlined"
                    placeholder="System description..."
                    fullWidth
                    multiline
                    rows={2}
                    className="mt-4"
                />
            </Paper>

            {/* Main Content */}
            <Box className="grow overflow-auto p-6">
                <Box className="flex gap-6 flex-wrap md:flex-nowrap">
                    {/* Left Column - Threats & Assets */}
                    <Box className="min-w-0" style={{ flex: '1 1 0' }}>
                        {/* Threats Section */}
                        <Card className="mb-6">
                            <CardContent>
                                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                                    <Typography as="h6" className="flex items-center gap-2">
                                        <BugIcon tone="danger" />
                                        Identified Threats
                                    </Typography>
                                    <Button
                                        variant="solid"
                                        startIcon={<AddIcon />}
                                        onClick={() => setAddThreatDialogOpen(true)}
                                        tone="danger"
                                    >
                                        Add Threat
                                    </Button>
                                </Stack>

                                {threats.length === 0 ? (
                                    <Alert severity="info">
                                        No threats identified yet. Click "Add Threat" to start your threat model.
                                    </Alert>
                                ) : (
                                    <Stack spacing={2}>
                                        {threats.map((threat) => {
                                            const categoryConfig = getCategoryConfig(threat.category);
                                            const riskConfig = getRiskLevelConfig(threat.riskLevel);
                                            const riskScore = calculateRiskScore(threat.id);

                                            return (
                                                <Paper
                                                    key={threat.id}
                                                    elevation={2}
                                                    className="p-4"
                                                    style={{ borderLeft: `4px solid ${categoryConfig.color}` }}
                                                >
                                                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                                                        <Box className="grow">
                                                            <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                                                                <Typography as="h6">{threat.title}</Typography>
                                                                <Chip
                                                                    icon={categoryConfig.icon}
                                                                    label={categoryConfig.label}
                                                                    size="sm"
                                                                    className="text-white" style={{ backgroundColor: categoryConfig.color }}
                                                                />
                                                                <Chip
                                                                    icon={riskConfig.icon}
                                                                    label={`${riskConfig.label} (${riskScore}/10)`}
                                                                    size="sm"
                                                                    className="text-white" style={{ backgroundColor: riskConfig.color }}
                                                                />
                                                            </Stack>
                                                            <Typography as="p" className="text-sm" color="text.secondary" mb={1}>
                                                                {threat.description}
                                                            </Typography>
                                                            {threat.attackVector && (
                                                                <Typography as="p" className="text-sm" color="error.main" mb={1}>
                                                                    <strong>Attack Vector:</strong> {threat.attackVector}
                                                                </Typography>
                                                            )}
                                                            {threat.affectedAsset && (
                                                                <Chip
                                                                    label={`Asset: ${threat.affectedAsset}`}
                                                                    size="sm"
                                                                    variant="outlined"
                                                                    tone="primary"
                                                                />
                                                            )}

                                                            {/* Mitigations */}
                                                            {threat.mitigations && threat.mitigations.length > 0 && (
                                                                <Box mt={2}>
                                                                    <Typography as="p" className="text-sm font-medium" mb={1}>
                                                                        Mitigations ({threat.mitigations.length})
                                                                    </Typography>
                                                                    <Stack spacing={1}>
                                                                        {threat.mitigations.map((mitigation) => (
                                                                            <Paper
                                                                                key={mitigation.id}
                                                                                className="p-2" style={{ backgroundColor: mitigation.status === 'implemented'
                                                                                        ? '#e8f5e9'
                                                                                        : mitigation.status === 'in-progress'
                                                                                            ? '#fff3e0'
                                                                                            : '#fafafa' }}
                                                                            >
                                                                                <Stack direction="row" spacing={1} alignItems="center">
                                                                                    <Chip
                                                                                        label={mitigation.status}
                                                                                        size="sm"
                                                                                        color={
                                                                                            mitigation.status === 'implemented'
                                                                                                ? 'success'
                                                                                                : mitigation.status === 'in-progress'
                                                                                                    ? 'warning'
                                                                                                    : 'default'
                                                                                        }
                                                                                    />
                                                                                    <Typography as="p" className="text-sm grow">
                                                                                        {mitigation.strategy}
                                                                                    </Typography>
                                                                                    {mitigation.owner && (
                                                                                        <Chip
                                                                                            label={mitigation.owner}
                                                                                            size="sm"
                                                                                            variant="outlined"
                                                                                        />
                                                                                    )}
                                                                                    <IconButton
                                                                                        size="sm"
                                                                                        onClick={() => {
                                                                                            const nextStatus =
                                                                                                mitigation.status === 'planned'
                                                                                                    ? 'in-progress'
                                                                                                    : mitigation.status === 'in-progress'
                                                                                                        ? 'implemented'
                                                                                                        : 'planned';
                                                                                            updateMitigationStatus(threat.id, mitigation.id, nextStatus);
                                                                                        }}
                                                                                    >
                                                                                        <EditIcon size={16} />
                                                                                    </IconButton>
                                                                                    <IconButton
                                                                                        size="sm"
                                                                                        onClick={() => deleteMitigation(threat.id, mitigation.id)}
                                                                                    >
                                                                                        <DeleteIcon size={16} />
                                                                                    </IconButton>
                                                                                </Stack>
                                                                            </Paper>
                                                                        ))}
                                                                    </Stack>
                                                                </Box>
                                                            )}
                                                        </Box>
                                                        <Stack direction="row" spacing={1}>
                                                            <IconButton
                                                                size="sm"
                                                                onClick={() => {
                                                                    setSelectedThreatId(threat.id);
                                                                    setAddMitigationDialogOpen(true);
                                                                }}
                                                                tone="success"
                                                            >
                                                                <AddIcon />
                                                            </IconButton>
                                                            <IconButton
                                                                size="sm"
                                                                onClick={() => deleteThreat(threat.id)}
                                                                tone="danger"
                                                            >
                                                                <DeleteIcon />
                                                            </IconButton>
                                                        </Stack>
                                                    </Stack>
                                                </Paper>
                                            );
                                        })}
                                    </Stack>
                                )}
                            </CardContent>
                        </Card>

                        {/* Assets Section */}
                        <Card>
                            <CardContent>
                                <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
                                    <Typography as="h6" className="flex items-center gap-2">
                                        <ShieldIcon tone="primary" />
                                        Protected Assets
                                    </Typography>
                                    <Button
                                        variant="solid"
                                        startIcon={<AddIcon />}
                                        onClick={() => setAddAssetDialogOpen(true)}
                                    >
                                        Add Asset
                                    </Button>
                                </Stack>

                                {assets.length === 0 ? (
                                    <Alert severity="info">
                                        No assets defined yet. Add assets that need protection.
                                    </Alert>
                                ) : (
                                    <Box className="flex flex-wrap gap-4">
                                        {assets.map((asset) => {
                                            const assetType = ASSET_TYPES.find(t => t.value === asset.type);
                                            return (
                                                <Box key={asset.id} style={{ flex: '1 1 calc(50% - 8px)' }}>
                                                    <Paper
                                                        elevation={2}
                                                        className="p-4 relative"
                                                    >
                                                        <IconButton
                                                            size="sm"
                                                            className="absolute top-[8px] right-[8px]"
                                                            onClick={() => deleteAsset(asset.id)}
                                                        >
                                                            <DeleteIcon size={16} />
                                                        </IconButton>
                                                        <Stack direction="row" spacing={1} alignItems="center" mb={1}>
                                                            {assetType?.icon}
                                                            <Typography as="h6">{asset.name}</Typography>
                                                        </Stack>
                                                        <Chip
                                                            label={assetType?.label}
                                                            size="sm"
                                                            tone="primary"
                                                            variant="outlined"
                                                            className="mb-2"
                                                        />
                                                        {asset.description && (
                                                            <Typography as="p" className="text-sm" color="text.secondary">
                                                                {asset.description}
                                                            </Typography>
                                                        )}
                                                    </Paper>
                                                </Box>
                                            );
                                        })}
                                    </Box>
                                )}
                            </CardContent>
                        </Card>
                    </Box>

                    {/* Right Column - STRIDE Framework */}
                    <Box className="min-w-0" style={{ flex: '0 0 360px' }}>
                        <Card className="sticky top-[16px]">
                            <CardContent>
                                <Typography as="h6" mb={2} className="flex items-center gap-2">
                                    <AssessmentIcon />
                                    STRIDE Framework
                                </Typography>
                                <Typography as="p" className="text-sm" color="text.secondary" mb={2}>
                                    Click on a category to filter threats
                                </Typography>
                                <Stack spacing={1}>
                                    {STRIDE_CATEGORIES.map((category) => {
                                        const count = threats.filter(t => t.category === category.id).length;
                                        return (
                                            <Paper
                                                key={category.id}
                                                className="p-3 cursor-pointer"
                                                style={{ borderLeft: `4px solid ${category.color}` }}
                                            >
                                                <Stack direction="row" spacing={1} alignItems="center">
                                                    {category.icon}
                                                    <Box className="grow">
                                                        <Typography as="p" className="text-sm font-medium">{category.label}</Typography>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                            {category.description}
                                                        </Typography>
                                                    </Box>
                                                    <Chip
                                                        label={count}
                                                        size="sm"
                                                        className="text-white" style={{ backgroundColor: count > 0 ? category.color : '#e0e0e0' }}
                                                    />
                                                </Stack>
                                            </Paper>
                                        );
                                    })}
                                </Stack>

                                <Divider className="my-4" />

                                <Typography as="h6" mb={1}>
                                    Risk Distribution
                                </Typography>
                                <Stack spacing={1}>
                                    {RISK_LEVELS.map((level) => {
                                        const count = threats.filter(t => t.riskLevel === level.value).length;
                                        return (
                                            <Stack key={level.value} direction="row" justifyContent="space-between" alignItems="center">
                                                <Chip
                                                    icon={level.icon}
                                                    label={level.label}
                                                    size="sm"
                                                    className="text-white" style={{ backgroundColor: level.color }}
                                                />
                                                <Typography as="p" className="text-sm">{count} threats</Typography>
                                            </Stack>
                                        );
                                    })}
                                </Stack>
                            </CardContent>
                        </Card>
                    </Box>
                </Box>
            </Box>

            {/* Add Threat Dialog */}
            <Dialog open={addThreatDialogOpen} onClose={() => setAddThreatDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add New Threat</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Threat Title"
                            value={newThreat.title}
                            onChange={(e) => setNewThreat({ ...newThreat, title: e.target.value })}
                            fullWidth
                            required
                        />
                        <TextField
                            label="Description"
                            value={newThreat.description}
                            onChange={(e) => setNewThreat({ ...newThreat, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={3}
                        />
                        <FormControl fullWidth>
                            <InputLabel>STRIDE Category</InputLabel>
                            <Select
                                value={newThreat.category}
                                onChange={(e) => setNewThreat({ ...newThreat, category: e.target.value as ThreatCategory })}
                                label="STRIDE Category"
                            >
                                {STRIDE_CATEGORIES.map((cat) => (
                                    <MenuItem key={cat.id} value={cat.id}>
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            {cat.icon}
                                            <span>{cat.label}</span>
                                        </Stack>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            label="Affected Asset"
                            value={newThreat.affectedAsset}
                            onChange={(e) => setNewThreat({ ...newThreat, affectedAsset: e.target.value })}
                            fullWidth
                        />
                        <TextField
                            label="Attack Vector"
                            value={newThreat.attackVector}
                            onChange={(e) => setNewThreat({ ...newThreat, attackVector: e.target.value })}
                            fullWidth
                            placeholder="How could this threat be exploited?"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddThreatDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddThreat} variant="solid" disabled={!newThreat.title}>
                        Add Threat
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Asset Dialog */}
            <Dialog open={addAssetDialogOpen} onClose={() => setAddAssetDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Protected Asset</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Asset Name"
                            value={newAsset.name}
                            onChange={(e) => setNewAsset({ ...newAsset, name: e.target.value })}
                            fullWidth
                            required
                        />
                        <FormControl fullWidth>
                            <InputLabel>Asset Type</InputLabel>
                            <Select
                                value={newAsset.type}
                                onChange={(e) => setNewAsset({ ...newAsset, type: e.target.value as unknown })}
                                label="Asset Type"
                            >
                                {ASSET_TYPES.map((type) => (
                                    <MenuItem key={type.value} value={type.value}>
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
                            value={newAsset.description}
                            onChange={(e) => setNewAsset({ ...newAsset, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={2}
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddAssetDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddAsset} variant="solid" disabled={!newAsset.name}>
                        Add Asset
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Add Mitigation Dialog */}
            <Dialog open={addMitigationDialogOpen} onClose={() => setAddMitigationDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Add Mitigation Strategy</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} className="mt-2">
                        <TextField
                            label="Mitigation Strategy"
                            value={newMitigation.strategy}
                            onChange={(e) => setNewMitigation({ ...newMitigation, strategy: e.target.value })}
                            fullWidth
                            required
                            placeholder="e.g., Implement rate limiting"
                        />
                        <TextField
                            label="Implementation Details"
                            value={newMitigation.implementation}
                            onChange={(e) => setNewMitigation({ ...newMitigation, implementation: e.target.value })}
                            fullWidth
                            multiline
                            rows={3}
                            placeholder="Technical details..."
                        />
                        <TextField
                            label="Owner"
                            value={newMitigation.owner}
                            onChange={(e) => setNewMitigation({ ...newMitigation, owner: e.target.value })}
                            fullWidth
                            placeholder="Team or person responsible"
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setAddMitigationDialogOpen(false)}>Cancel</Button>
                    <Button onClick={handleAddMitigation} variant="solid" disabled={!newMitigation.strategy}>
                        Add Mitigation
                    </Button>
                </DialogActions>
            </Dialog>

            {/* STRIDE Analysis Dialog */}
            <Dialog open={strideAnalysisOpen} onClose={() => setStrideAnalysisOpen(false)} size="md" fullWidth>
                <DialogTitle>STRIDE Analysis Results</DialogTitle>
                <DialogContent>
                    {strideResults && (
                        <Stack spacing={2}>
                            <Alert severity={strideResults.overallRisk === 'critical' ? 'error' : strideResults.overallRisk === 'high' ? 'warning' : 'info'}>
                                <Typography as="p" className="text-lg font-medium">
                                    Overall Risk Level: <strong>{strideResults.overallRisk.toUpperCase()}</strong>
                                </Typography>
                                <Typography as="p" className="text-sm">
                                    Average Risk Score: {strideResults.averageRiskScore.toFixed(1)}/10
                                </Typography>
                            </Alert>

                            <Typography as="h6">Category Breakdown</Typography>
                            {Object.entries(strideResults.categoryBreakdown).map(([category, data]) => {
                                const categoryConfig = getCategoryConfig(category as ThreatCategory);
                                return (
                                    <Paper key={category} className="p-4" style={{ borderLeft: `4px solid ${categoryConfig.color}` }}>
                                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                                            <Stack direction="row" spacing={1} alignItems="center">
                                                {categoryConfig.icon}
                                                <Typography as="p" className="text-lg font-medium">{categoryConfig.label}</Typography>
                                            </Stack>
                                            <Stack direction="row" spacing={2}>
                                                <Chip label={`${data.count} threats`} size="sm" />
                                                <Chip
                                                    label={`${data.mitigatedCount}/${data.count} mitigated`}
                                                    size="sm"
                                                    color={data.mitigatedCount === data.count ? 'success' : 'warning'}
                                                />
                                            </Stack>
                                        </Stack>
                                    </Paper>
                                );
                            })}

                            {strideResults.unmitigatedThreats.length > 0 && (
                                <>
                                    <Typography as="h6" tone="danger">
                                        Unmitigated Threats ({strideResults.unmitigatedThreats.length})
                                    </Typography>
                                    <List>
                                        {strideResults.unmitigatedThreats.map((threat) => (
                                            <ListItem key={threat.id}>
                                                <ListItemIcon>
                                                    <WarningIcon tone="danger" />
                                                </ListItemIcon>
                                                <ListItemText
                                                    primary={threat.title}
                                                    secondary={threat.description}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                </>
                            )}
                        </Stack>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setStrideAnalysisOpen(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Export Dialog */}
            <Dialog open={exportDialogOpen} onClose={() => setExportDialogOpen(false)} size="sm" fullWidth>
                <DialogTitle>Export Threat Model</DialogTitle>
                <DialogContent>
                    <Alert severity="success" className="mb-4">
                        Threat model ready to export
                    </Alert>
                    <Stack spacing={1}>
                        <Typography as="p" className="text-sm">
                            <strong>Threats:</strong> {getThreatCount()}
                        </Typography>
                        <Typography as="p" className="text-sm">
                            <strong>Assets:</strong> {getAssetCount()}
                        </Typography>
                        <Typography as="p" className="text-sm">
                            <strong>Mitigations:</strong> {getMitigationCount()}
                        </Typography>
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
