import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
    Typography,
    Box,
    Chip,
    Button,
    IconButton,
    LinearProgress,
    TextField,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    List,
    ListItem,
    ListItemText,
    Divider,
    Alert,
    Tabs,
    Tab,
    Stepper,
    Step,
    StepLabel,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Checkbox,
    FormControlLabel,
} from '@ghatana/ui';
import {
    Add as AddIcon,
    Edit as EditIcon,
    Delete as DeleteIcon,
    CheckCircle as CheckCircleIcon,
    RadioButtonUnchecked as RadioButtonUncheckedIcon,
    Flag as FlagIcon,
    TrendingUp as TrendingUpIcon,
    TrendingDown as TrendingDownIcon,
    Remove as RemoveIcon,
    Timeline as TimelineIcon,
    EmojiEvents as EmojiEventsIcon,
    Assignment as AssignmentIcon,
    PlayArrow as PlayArrowIcon,
    CheckCircleOutline as CheckCircleOutlineIcon,
} from '@ghatana/ui/icons';

// ==================== TYPES ====================

export interface KeyResult {
    id: string;
    description: string;
    targetValue: number;
    currentValue: number;
    unit: string; // e.g., '%', 'ms', 'count', '$'
    progress: number; // 0-100
    status: 'on_track' | 'at_risk' | 'behind' | 'completed';
}

export interface Objective {
    id: string;
    title: string;
    description: string;
    type: 'individual' | 'team' | 'company';
    category: 'performance' | 'learning' | 'innovation' | 'collaboration' | 'impact';
    priority: 'low' | 'medium' | 'high' | 'critical';
    startDate: string;
    endDate: string;
    progress: number; // 0-100 (calculated from key results)
    status: 'not_started' | 'in_progress' | 'completed' | 'cancelled';
    keyResults: KeyResult[];
    owner?: string;
    alignedWith?: string; // Parent OKR ID
    milestones?: Milestone[];
}

export interface Milestone {
    id: string;
    title: string;
    description?: string;
    dueDate: string;
    completed: boolean;
    completedDate?: string;
}

export interface GoalTrackerProps {
    objectives?: Objective[];
    onCreateObjective?: (objective: Omit<Objective, 'id' | 'progress'>) => void;
    onUpdateObjective?: (objectiveId: string, updates: Partial<Objective>) => void;
    onDeleteObjective?: (objectiveId: string) => void;
    onUpdateKeyResult?: (objectiveId: string, keyResultId: string, currentValue: number) => void;
    onCompleteMilestone?: (objectiveId: string, milestoneId: string) => void;
}

// ==================== MOCK DATA ====================

const mockObjectives: Objective[] = [
    {
        id: 'obj-1',
        title: 'Reduce API Response Time',
        description: 'Improve overall API performance to enhance user experience',
        type: 'individual',
        category: 'performance',
        priority: 'high',
        startDate: '2025-10-01T00:00:00Z',
        endDate: '2025-12-31T23:59:59Z',
        progress: 75,
        status: 'in_progress',
        owner: 'You',
        keyResults: [
            {
                id: 'kr-1',
                description: 'Reduce average API latency',
                targetValue: 100,
                currentValue: 75,
                unit: 'ms',
                progress: 75,
                status: 'on_track',
            },
            {
                id: 'kr-2',
                description: 'Increase cache hit rate',
                targetValue: 90,
                currentValue: 70,
                unit: '%',
                progress: 78,
                status: 'on_track',
            },
            {
                id: 'kr-3',
                description: 'Reduce database query time',
                targetValue: 50,
                currentValue: 38,
                unit: 'ms',
                progress: 76,
                status: 'on_track',
            },
        ],
        milestones: [
            {
                id: 'ms-1',
                title: 'Implement Redis caching',
                dueDate: '2025-10-31T23:59:59Z',
                completed: true,
                completedDate: '2025-10-28T14:30:00Z',
            },
            {
                id: 'ms-2',
                title: 'Optimize database queries',
                dueDate: '2025-11-30T23:59:59Z',
                completed: true,
                completedDate: '2025-11-25T16:45:00Z',
            },
            {
                id: 'ms-3',
                title: 'Deploy CDN for static assets',
                dueDate: '2025-12-15T23:59:59Z',
                completed: false,
            },
        ],
    },
    {
        id: 'obj-2',
        title: 'Complete AWS Certification',
        description: 'Earn AWS Solutions Architect Associate certification',
        type: 'individual',
        category: 'learning',
        priority: 'medium',
        startDate: '2025-09-01T00:00:00Z',
        endDate: '2026-03-31T23:59:59Z',
        progress: 45,
        status: 'in_progress',
        owner: 'You',
        keyResults: [
            {
                id: 'kr-4',
                description: 'Complete training modules',
                targetValue: 100,
                currentValue: 60,
                unit: '%',
                progress: 60,
                status: 'on_track',
            },
            {
                id: 'kr-5',
                description: 'Pass practice exams with score',
                targetValue: 80,
                currentValue: 72,
                unit: '%',
                progress: 90,
                status: 'on_track',
            },
        ],
        milestones: [
            {
                id: 'ms-4',
                title: 'Complete EC2 and VPC modules',
                dueDate: '2025-11-30T23:59:59Z',
                completed: true,
                completedDate: '2025-11-20T10:00:00Z',
            },
            {
                id: 'ms-5',
                title: 'Complete S3 and CloudFront modules',
                dueDate: '2025-12-31T23:59:59Z',
                completed: false,
            },
            {
                id: 'ms-6',
                title: 'Take certification exam',
                dueDate: '2026-03-15T23:59:59Z',
                completed: false,
            },
        ],
    },
    {
        id: 'obj-3',
        title: 'Launch Mobile App v2.0',
        description: 'Deliver next generation mobile experience',
        type: 'team',
        category: 'impact',
        priority: 'critical',
        startDate: '2025-09-01T00:00:00Z',
        endDate: '2026-01-15T23:59:59Z',
        progress: 60,
        status: 'in_progress',
        owner: 'Engineering Team',
        alignedWith: 'company-okr-q4',
        keyResults: [
            {
                id: 'kr-6',
                description: 'Complete feature development',
                targetValue: 100,
                currentValue: 75,
                unit: '%',
                progress: 75,
                status: 'at_risk',
            },
            {
                id: 'kr-7',
                description: 'Achieve test coverage',
                targetValue: 80,
                currentValue: 60,
                unit: '%',
                progress: 75,
                status: 'at_risk',
            },
            {
                id: 'kr-8',
                description: 'Beta user satisfaction score',
                targetValue: 4.5,
                currentValue: 2.0,
                unit: '/5',
                progress: 44,
                status: 'behind',
            },
        ],
    },
    {
        id: 'obj-4',
        title: 'Mentor Junior Developers',
        description: 'Provide mentorship to help team members grow',
        type: 'individual',
        category: 'collaboration',
        priority: 'medium',
        startDate: '2025-10-01T00:00:00Z',
        endDate: '2025-12-31T23:59:59Z',
        progress: 50,
        status: 'in_progress',
        owner: 'You',
        keyResults: [
            {
                id: 'kr-9',
                description: 'Conduct weekly 1-on-1 sessions',
                targetValue: 12,
                currentValue: 8,
                unit: 'sessions',
                progress: 67,
                status: 'on_track',
            },
            {
                id: 'kr-10',
                description: 'Review code contributions',
                targetValue: 50,
                currentValue: 20,
                unit: 'PRs',
                progress: 40,
                status: 'behind',
            },
        ],
    },
];

// ==================== COMPONENT ====================

export const GoalTracker: React.FC<GoalTrackerProps> = ({
    objectives = mockObjectives,
    onCreateObjective,
    onUpdateObjective,
    onDeleteObjective,
    onUpdateKeyResult,
    onCompleteMilestone,
}) => {
    const [selectedTab, setSelectedTab] = useState(0);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [editDialogOpen, setEditDialogOpen] = useState(false);
    const [selectedObjective, setSelectedObjective] = useState<Objective | null>(null);
    const [updateKRDialogOpen, setUpdateKRDialogOpen] = useState(false);
    const [selectedKR, setSelectedKR] = useState<{ objectiveId: string; keyResult: KeyResult } | null>(null);

    // Form state for create/edit
    const [formData, setFormData] = useState<Partial<Objective>>({
        title: '',
        description: '',
        type: 'individual',
        category: 'performance',
        priority: 'medium',
        startDate: new Date().toISOString(),
        endDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString(), // 90 days from now
        status: 'not_started',
        keyResults: [],
    });

    // Helper functions
    const getStatusColor = (status: Objective['status'] | KeyResult['status']): 'success' | 'info' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'in_progress':
            case 'on_track':
                return 'info';
            case 'at_risk':
                return 'warning';
            case 'behind':
                return 'error';
            case 'not_started':
            case 'cancelled':
            default:
                return 'default';
        }
    };

    const getPriorityColor = (priority: Objective['priority']): 'error' | 'warning' | 'info' | 'default' => {
        switch (priority) {
            case 'critical':
                return 'error';
            case 'high':
                return 'warning';
            case 'medium':
                return 'info';
            case 'low':
            default:
                return 'default';
        }
    };

    const getCategoryIcon = (category: Objective['category']) => {
        switch (category) {
            case 'performance':
                return <TrendingUpIcon />;
            case 'learning':
                return <AssignmentIcon />;
            case 'innovation':
                return <EmojiEventsIcon />;
            case 'collaboration':
                return <FlagIcon />;
            case 'impact':
                return <CheckCircleIcon />;
        }
    };

    const formatDate = (dateString: string): string => {
        return new Date(dateString).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const calculateDaysRemaining = (endDate: string): number => {
        const end = new Date(endDate);
        const now = new Date();
        return Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    };

    const handleCreateObjective = () => {
        if (onCreateObjective && formData.title && formData.description) {
            onCreateObjective(formData as Omit<Objective, 'id' | 'progress'>);
            setCreateDialogOpen(false);
            setFormData({
                title: '',
                description: '',
                type: 'individual',
                category: 'performance',
                priority: 'medium',
                startDate: new Date().toISOString(),
                endDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString(),
                status: 'not_started',
                keyResults: [],
            });
        }
    };

    const handleUpdateObjective = () => {
        if (onUpdateObjective && selectedObjective && formData) {
            onUpdateObjective(selectedObjective.id, formData);
            setEditDialogOpen(false);
            setSelectedObjective(null);
        }
    };

    const handleUpdateKeyResult = (newValue: number) => {
        if (onUpdateKeyResult && selectedKR) {
            onUpdateKeyResult(selectedKR.objectiveId, selectedKR.keyResult.id, newValue);
            setUpdateKRDialogOpen(false);
            setSelectedKR(null);
        }
    };

    const filterObjectivesByTab = (obj: Objective): boolean => {
        switch (selectedTab) {
            case 0: // All
                return true;
            case 1: // In Progress
                return obj.status === 'in_progress';
            case 2: // Completed
                return obj.status === 'completed';
            case 3: // Team
                return obj.type === 'team';
            default:
                return true;
        }
    };

    const filteredObjectives = objectives.filter(filterObjectivesByTab);

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                <Typography variant="h4">Goals & OKRs</Typography>
                <Button
                    variant="contained"
                    color="primary"
                    startIcon={<AddIcon />}
                    onClick={() => setCreateDialogOpen(true)}
                >
                    New Objective
                </Button>
            </Box>

            {/* Tabs */}
            <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)} sx={{ mb: 3 }}>
                <Tab label={`All (${objectives.length})`} />
                <Tab label={`In Progress (${objectives.filter(o => o.status === 'in_progress').length})`} />
                <Tab label={`Completed (${objectives.filter(o => o.status === 'completed').length})`} />
                <Tab label={`Team Goals (${objectives.filter(o => o.type === 'team').length})`} />
            </Tabs>

            {/* Objectives List */}
            {filteredObjectives.length === 0 ? (
                <Alert severity="info">
                    No objectives found. Create your first objective to get started!
                </Alert>
            ) : (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                    {filteredObjectives.map((objective) => {
                        const daysRemaining = calculateDaysRemaining(objective.endDate);
                        const completedMilestones = objective.milestones?.filter(m => m.completed).length || 0;
                        const totalMilestones = objective.milestones?.length || 0;

                        return (
                            <Card key={objective.id}>
                                <CardHeader
                                    avatar={getCategoryIcon(objective.category)}
                                    title={
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                                            <Typography variant="h6">{objective.title}</Typography>
                                            <Chip
                                                label={objective.type.toUpperCase()}
                                                size="small"
                                                variant="outlined"
                                                color={objective.type === 'team' ? 'secondary' : 'default'}
                                            />
                                            <Chip
                                                label={objective.priority.toUpperCase()}
                                                size="small"
                                                color={getPriorityColor(objective.priority)}
                                            />
                                            <Chip
                                                label={objective.status.replace('_', ' ').toUpperCase()}
                                                size="small"
                                                color={getStatusColor(objective.status)}
                                            />
                                        </Box>
                                    }
                                    subheader={
                                        <Box>
                                            <Typography variant="body2" color="text.secondary">
                                                {objective.description}
                                            </Typography>
                                            <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                                                <Typography variant="caption" color="text.secondary">
                                                    📅 {formatDate(objective.startDate)} - {formatDate(objective.endDate)}
                                                </Typography>
                                                <Typography variant="caption" color={daysRemaining < 7 ? 'error' : 'text.secondary'}>
                                                    ⏰ {daysRemaining} days remaining
                                                </Typography>
                                                {objective.owner && (
                                                    <Typography variant="caption" color="text.secondary">
                                                        👤 {objective.owner}
                                                    </Typography>
                                                )}
                                            </Box>
                                        </Box>
                                    }
                                    action={
                                        <Box>
                                            <IconButton
                                                onClick={() => {
                                                    setSelectedObjective(objective);
                                                    setFormData(objective);
                                                    setEditDialogOpen(true);
                                                }}
                                            >
                                                <EditIcon />
                                            </IconButton>
                                            <IconButton
                                                color="error"
                                                onClick={() => onDeleteObjective?.(objective.id)}
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        </Box>
                                    }
                                />
                                <CardContent>
                                    {/* Overall Progress */}
                                    <Box sx={{ mb: 3 }}>
                                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                                            <Typography variant="subtitle2">Overall Progress</Typography>
                                            <Typography variant="h6" color="primary">
                                                {objective.progress}%
                                            </Typography>
                                        </Box>
                                        <LinearProgress
                                            variant="determinate"
                                            value={objective.progress}
                                            sx={{ height: 8, borderRadius: 1 }}
                                            color={getStatusColor(objective.status)}
                                        />
                                    </Box>

                                    {/* Key Results */}
                                    <Typography variant="subtitle2" gutterBottom>
                                        Key Results
                                    </Typography>
                                    <List>
                                        {objective.keyResults.map((kr, index) => (
                                            <React.Fragment key={kr.id}>
                                                {index > 0 && <Divider />}
                                                <ListItem
                                                    sx={{ px: 0 }}
                                                    secondaryAction={
                                                        <IconButton
                                                            edge="end"
                                                            size="small"
                                                            onClick={() => {
                                                                setSelectedKR({ objectiveId: objective.id, keyResult: kr });
                                                                setUpdateKRDialogOpen(true);
                                                            }}
                                                        >
                                                            <EditIcon fontSize="small" />
                                                        </IconButton>
                                                    }
                                                >
                                                    <ListItemText
                                                        primary={
                                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                                                                <Typography variant="body1">{kr.description}</Typography>
                                                                <Chip label={kr.status.replace('_', ' ').toUpperCase()} size="small" color={getStatusColor(kr.status)} />
                                                            </Box>
                                                        }
                                                        secondary={
                                                            <Box>
                                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                                                                    <LinearProgress
                                                                        variant="determinate"
                                                                        value={kr.progress}
                                                                        sx={{ flex: 1, height: 6, borderRadius: 1 }}
                                                                        color={getStatusColor(kr.status)}
                                                                    />
                                                                    <Typography variant="caption" color="text.secondary" sx={{ minWidth: 45 }}>
                                                                        {kr.progress}%
                                                                    </Typography>
                                                                </Box>
                                                                <Typography variant="caption" color="text.secondary">
                                                                    Current: {kr.currentValue}{kr.unit} / Target: {kr.targetValue}{kr.unit}
                                                                </Typography>
                                                            </Box>
                                                        }
                                                    />
                                                </ListItem>
                                            </React.Fragment>
                                        ))}
                                    </List>

                                    {/* Milestones */}
                                    {objective.milestones && objective.milestones.length > 0 && (
                                        <Box sx={{ mt: 3 }}>
                                            <Typography variant="subtitle2" gutterBottom>
                                                Milestones ({completedMilestones}/{totalMilestones})
                                            </Typography>
                                            <Stepper orientation="vertical">
                                                {objective.milestones.map((milestone) => (
                                                    <Step key={milestone.id} completed={milestone.completed}>
                                                        <StepLabel
                                                            optional={
                                                                <Typography variant="caption" color="text.secondary">
                                                                    Due {formatDate(milestone.dueDate)}
                                                                    {milestone.completed && milestone.completedDate && (
                                                                        <> • Completed {formatDate(milestone.completedDate)}</>
                                                                    )}
                                                                </Typography>
                                                            }
                                                            StepIconComponent={() =>
                                                                milestone.completed ? (
                                                                    <CheckCircleIcon color="success" />
                                                                ) : (
                                                                    <RadioButtonUncheckedIcon color="action" />
                                                                )
                                                            }
                                                        >
                                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                                <Typography variant="body2">{milestone.title}</Typography>
                                                                {!milestone.completed && (
                                                                    <Button
                                                                        size="small"
                                                                        onClick={() => onCompleteMilestone?.(objective.id, milestone.id)}
                                                                    >
                                                                        Complete
                                                                    </Button>
                                                                )}
                                                            </Box>
                                                            {milestone.description && (
                                                                <Typography variant="caption" color="text.secondary">
                                                                    {milestone.description}
                                                                </Typography>
                                                            )}
                                                        </StepLabel>
                                                    </Step>
                                                ))}
                                            </Stepper>
                                        </Box>
                                    )}
                                </CardContent>
                            </Card>
                        );
                    })}
                </Box>
            )}

            {/* Create Objective Dialog */}
            <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="md" fullWidth>
                <DialogTitle>Create New Objective</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
                        <TextField
                            label="Objective Title"
                            fullWidth
                            required
                            value={formData.title}
                            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                        />

                        <TextField
                            label="Description"
                            fullWidth
                            required
                            multiline
                            rows={3}
                            value={formData.description}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                        />

                        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                            <FormControl fullWidth>
                                <InputLabel>Type</InputLabel>
                                <Select
                                    value={formData.type}
                                    label="Type"
                                    onChange={(e) => setFormData({ ...formData, type: e.target.value as Objective['type'] })}
                                >
                                    <MenuItem value="individual">Individual</MenuItem>
                                    <MenuItem value="team">Team</MenuItem>
                                    <MenuItem value="company">Company</MenuItem>
                                </Select>
                            </FormControl>

                            <FormControl fullWidth>
                                <InputLabel>Category</InputLabel>
                                <Select
                                    value={formData.category}
                                    label="Category"
                                    onChange={(e) => setFormData({ ...formData, category: e.target.value as Objective['category'] })}
                                >
                                    <MenuItem value="performance">Performance</MenuItem>
                                    <MenuItem value="learning">Learning</MenuItem>
                                    <MenuItem value="innovation">Innovation</MenuItem>
                                    <MenuItem value="collaboration">Collaboration</MenuItem>
                                    <MenuItem value="impact">Impact</MenuItem>
                                </Select>
                            </FormControl>
                        </Box>

                        <FormControl fullWidth>
                            <InputLabel>Priority</InputLabel>
                            <Select
                                value={formData.priority}
                                label="Priority"
                                onChange={(e) => setFormData({ ...formData, priority: e.target.value as Objective['priority'] })}
                            >
                                <MenuItem value="low">Low</MenuItem>
                                <MenuItem value="medium">Medium</MenuItem>
                                <MenuItem value="high">High</MenuItem>
                                <MenuItem value="critical">Critical</MenuItem>
                            </Select>
                        </FormControl>

                        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                            <TextField
                                label="Start Date"
                                type="date"
                                fullWidth
                                InputLabelProps={{ shrink: true }}
                                value={formData.startDate?.split('T')[0]}
                                onChange={(e) => setFormData({ ...formData, startDate: new Date(e.target.value).toISOString() })}
                            />

                            <TextField
                                label="End Date"
                                type="date"
                                fullWidth
                                InputLabelProps={{ shrink: true }}
                                value={formData.endDate?.split('T')[0]}
                                onChange={(e) => setFormData({ ...formData, endDate: new Date(e.target.value).toISOString() })}
                            />
                        </Box>

                        <Alert severity="info">
                            You can add key results and milestones after creating the objective
                        </Alert>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleCreateObjective}
                        disabled={!formData.title || !formData.description}
                    >
                        Create Objective
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Edit Objective Dialog */}
            <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="md" fullWidth>
                <DialogTitle>Edit Objective</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
                        <TextField
                            label="Objective Title"
                            fullWidth
                            required
                            value={formData.title}
                            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                        />

                        <TextField
                            label="Description"
                            fullWidth
                            required
                            multiline
                            rows={3}
                            value={formData.description}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                        />

                        <FormControl fullWidth>
                            <InputLabel>Status</InputLabel>
                            <Select
                                value={formData.status}
                                label="Status"
                                onChange={(e) => setFormData({ ...formData, status: e.target.value as Objective['status'] })}
                            >
                                <MenuItem value="not_started">Not Started</MenuItem>
                                <MenuItem value="in_progress">In Progress</MenuItem>
                                <MenuItem value="completed">Completed</MenuItem>
                                <MenuItem value="cancelled">Cancelled</MenuItem>
                            </Select>
                        </FormControl>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
                    <Button variant="contained" onClick={handleUpdateObjective}>
                        Save Changes
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Update Key Result Dialog */}
            <Dialog open={updateKRDialogOpen} onClose={() => setUpdateKRDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Update Key Result</DialogTitle>
                <DialogContent>
                    {selectedKR && (
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
                            <Typography variant="body1">{selectedKR.keyResult.description}</Typography>
                            <TextField
                                label={`Current Value (${selectedKR.keyResult.unit})`}
                                type="number"
                                fullWidth
                                defaultValue={selectedKR.keyResult.currentValue}
                                InputProps={{
                                    inputProps: {
                                        min: 0,
                                        max: selectedKR.keyResult.targetValue,
                                    },
                                }}
                                onChange={(e) => {
                                    const newValue = parseFloat(e.target.value);
                                    if (!isNaN(newValue)) {
                                        handleUpdateKeyResult(newValue);
                                    }
                                }}
                            />
                            <Alert severity="info">
                                Target: {selectedKR.keyResult.targetValue}{selectedKR.keyResult.unit}
                            </Alert>
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setUpdateKRDialogOpen(false)}>Close</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};

export default GoalTracker;
