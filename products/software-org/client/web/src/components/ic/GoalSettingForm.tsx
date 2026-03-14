import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    MenuItem,
    Chip,
    FormControl,
    InputLabel,
    Divider,
    IconButton,
    Alert,
} from '@ghatana/design-system';
import type { GoalCategory, GoalStatus, Milestone } from './GrowthPlanDashboard';

/**
 * SMART goal criteria
 */
interface SmartCriteria {
    specific: boolean;
    measurable: boolean;
    achievable: boolean;
    relevant: boolean;
    timeBound: boolean;
}

/**
 * Goal form data
 */
export interface GoalFormData {
    title: string;
    description: string;
    category: GoalCategory;
    status: GoalStatus;
    targetDate: string;
    milestones: Milestone[];
    relatedSkills: string[];
    successCriteria: string[];
}

/**
 * Props for GoalSettingForm
 */
interface GoalSettingFormProps {
    onSubmit?: (goal: GoalFormData) => void;
    onCancel?: () => void;
    initialData?: Partial<GoalFormData>;
    availableSkills?: Array<{ id: string; name: string }>;
    isEditing?: boolean;
}

/**
 * Goal Setting Form
 *
 * Form for creating/editing development goals with:
 * - SMART goal template
 * - Category selection (technical/leadership/business/communication)
 * - Target date picker
 * - Milestone tracking
 * - Success criteria
 * - Related skills selection
 * - Validation and helper text
 */
export const GoalSettingForm: React.FC<GoalSettingFormProps> = ({
    onSubmit,
    onCancel,
    initialData,
    availableSkills = [],
    isEditing = false,
}) => {
    const [formData, setFormData] = useState<GoalFormData>({
        title: initialData?.title || '',
        description: initialData?.description || '',
        category: initialData?.category || 'technical',
        status: initialData?.status || 'not-started',
        targetDate: initialData?.targetDate || '',
        milestones: initialData?.milestones || [],
        relatedSkills: initialData?.relatedSkills || [],
        successCriteria: initialData?.successCriteria || [],
    });

    const [newMilestone, setNewMilestone] = useState('');
    const [newCriterion, setNewCriterion] = useState('');
    const [selectedSkill, setSelectedSkill] = useState('');
    const [showSmartHelper, setShowSmartHelper] = useState(false);

    // Mock skills if none provided
    const mockSkills = availableSkills.length > 0 ? availableSkills : [
        { id: 'skill-arch', name: 'System Architecture' },
        { id: 'skill-scale', name: 'Scalability & Performance' },
        { id: 'skill-security', name: 'Security Best Practices' },
        { id: 'skill-mentor', name: 'Mentoring' },
        { id: 'skill-code-review', name: 'Code Review & Feedback' },
        { id: 'skill-product', name: 'Product Thinking' },
        { id: 'skill-metrics', name: 'Data-Driven Decision Making' },
        { id: 'skill-communication', name: 'Technical Communication' },
    ];

    // SMART criteria validation
    const validateSmart = (): SmartCriteria => {
        return {
            specific: formData.title.length > 10 && formData.description.length > 20,
            measurable: formData.successCriteria.length > 0 || formData.milestones.length > 0,
            achievable: formData.milestones.length > 0 && formData.milestones.length <= 8,
            relevant: formData.category !== null && formData.relatedSkills.length > 0,
            timeBound: formData.targetDate !== '',
        };
    };

    const smartCriteria = validateSmart();
    const isSmartGoal = Object.values(smartCriteria).every((v) => v);

    // Form validation
    const isValid = (): boolean => {
        return (
            formData.title.trim() !== '' &&
            formData.description.trim() !== '' &&
            formData.targetDate !== '' &&
            formData.milestones.length > 0
        );
    };

    // Handlers
    const handleAddMilestone = () => {
        if (newMilestone.trim()) {
            const milestone: Milestone = {
                id: `m${Date.now()}`,
                title: newMilestone.trim(),
                completed: false,
            };
            setFormData({
                ...formData,
                milestones: [...formData.milestones, milestone],
            });
            setNewMilestone('');
        }
    };

    const handleRemoveMilestone = (milestoneId: string) => {
        setFormData({
            ...formData,
            milestones: formData.milestones.filter((m) => m.id !== milestoneId),
        });
    };

    const handleAddCriterion = () => {
        if (newCriterion.trim()) {
            setFormData({
                ...formData,
                successCriteria: [...formData.successCriteria, newCriterion.trim()],
            });
            setNewCriterion('');
        }
    };

    const handleRemoveCriterion = (index: number) => {
        setFormData({
            ...formData,
            successCriteria: formData.successCriteria.filter((_, i) => i !== index),
        });
    };

    const handleAddSkill = () => {
        if (selectedSkill && !formData.relatedSkills.includes(selectedSkill)) {
            setFormData({
                ...formData,
                relatedSkills: [...formData.relatedSkills, selectedSkill],
            });
            setSelectedSkill('');
        }
    };

    const handleRemoveSkill = (skillId: string) => {
        setFormData({
            ...formData,
            relatedSkills: formData.relatedSkills.filter((s) => s !== skillId),
        });
    };

    const handleSubmit = () => {
        if (isValid()) {
            onSubmit?.(formData);
        }
    };

    // Get category icon
    const getCategoryIcon = (category: GoalCategory): string => {
        switch (category) {
            case 'technical':
                return '💻';
            case 'leadership':
                return '👥';
            case 'business':
                return '📊';
            case 'communication':
                return '💬';
        }
    };

    // Goal templates
    const templates = [
        {
            category: 'technical' as GoalCategory,
            title: 'Master [Technology/Framework]',
            description: 'Gain deep expertise in [specific technology] by completing courses, building projects, and applying it in production',
            milestones: ['Complete online course', 'Build side project', 'Apply in production', 'Share knowledge with team'],
        },
        {
            category: 'leadership' as GoalCategory,
            title: 'Mentor Junior Engineers',
            description: 'Provide regular mentorship to junior team members, focusing on code quality, system design, and career growth',
            milestones: ['Establish regular 1:1s', 'Create learning plans', 'Conduct training sessions', 'Track mentee progress'],
        },
        {
            category: 'business' as GoalCategory,
            title: 'Drive Product Feature Success',
            description: 'Own a major product feature from conception to launch, ensuring business impact and user satisfaction',
            milestones: ['Define success metrics', 'Design solution', 'Implement and test', 'Launch and measure impact'],
        },
        {
            category: 'communication' as GoalCategory,
            title: 'Improve Technical Communication',
            description: 'Enhance ability to explain complex technical concepts to diverse audiences through presentations and documentation',
            milestones: ['Present at team meeting', 'Write technical blog post', 'Create documentation', 'Present at company all-hands'],
        },
    ];

    const applyTemplate = (template: typeof templates[0]) => {
        setFormData({
            ...formData,
            title: template.title,
            description: template.description,
            category: template.category,
            milestones: template.milestones.map((m, i) => ({
                id: `m${Date.now()}-${i}`,
                title: m,
                completed: false,
            })),
        });
    };

    return (
        <Box sx={{ maxWidth: 800, mx: 'auto' }}>
            <Card sx={{ p: 4 }}>
                <Typography variant="h5" gutterBottom>
                    {isEditing ? 'Edit Goal' : 'Create New Goal'}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                    Set a SMART goal to guide your professional development
                </Typography>

                {/* SMART Helper */}
                <Box sx={{ mb: 3 }}>
                    <Button
                        size="small"
                        variant="outlined"
                        onClick={() => setShowSmartHelper(!showSmartHelper)}
                    >
                        {showSmartHelper ? '−' : '+'} SMART Goal Framework
                    </Button>
                    {showSmartHelper && (
                        <Alert severity="info" sx={{ mt: 2 }}>
                            <Typography variant="subtitle2" gutterBottom>
                                SMART Goals:
                            </Typography>
                            <ul style={{ margin: 0, paddingLeft: 20 }}>
                                <li>
                                    <strong>Specific:</strong> Clear and well-defined (
                                    {smartCriteria.specific ? '✓' : '✗'})
                                </li>
                                <li>
                                    <strong>Measurable:</strong> Has criteria or milestones (
                                    {smartCriteria.measurable ? '✓' : '✗'})
                                </li>
                                <li>
                                    <strong>Achievable:</strong> Realistic number of milestones (
                                    {smartCriteria.achievable ? '✓' : '✗'})
                                </li>
                                <li>
                                    <strong>Relevant:</strong> Aligned with skills and role (
                                    {smartCriteria.relevant ? '✓' : '✗'})
                                </li>
                                <li>
                                    <strong>Time-bound:</strong> Has a deadline (
                                    {smartCriteria.timeBound ? '✓' : '✗'})
                                </li>
                            </ul>
                        </Alert>
                    )}
                </Box>

                {/* Templates */}
                <Box sx={{ mb: 3 }}>
                    <Typography variant="subtitle2" gutterBottom>
                        Quick Templates
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" gap={1}>
                        {templates.map((template, index) => (
                            <Chip
                                key={index}
                                label={`${getCategoryIcon(template.category)} ${template.category}`}
                                onClick={() => applyTemplate(template)}
                                variant="outlined"
                                clickable
                            />
                        ))}
                    </Stack>
                </Box>

                <Divider sx={{ mb: 3 }} />

                {/* Basic Information */}
                <Stack spacing={3}>
                    <TextField
                        fullWidth
                        label="Goal Title"
                        placeholder="e.g., Lead migration to microservices architecture"
                        value={formData.title}
                        onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                        helperText="Be specific and action-oriented"
                        required
                    />

                    <TextField
                        fullWidth
                        label="Description"
                        placeholder="Describe what you want to achieve and why it matters"
                        value={formData.description}
                        onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                        multiline
                        rows={3}
                        helperText="Explain the goal's purpose and expected impact"
                        required
                    />

                    {/* Category and Target Date */}
                    <Stack direction="row" spacing={2}>
                        <FormControl fullWidth>
                            <InputLabel>Category</InputLabel>
                            <Select
                                value={formData.category}
                                label="Category"
                                onChange={(e) =>
                                    setFormData({ ...formData, category: e.target.value as GoalCategory })
                                }
                            >
                                <MenuItem value="technical">💻 Technical</MenuItem>
                                <MenuItem value="leadership">👥 Leadership</MenuItem>
                                <MenuItem value="business">📊 Business</MenuItem>
                                <MenuItem value="communication">💬 Communication</MenuItem>
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Status</InputLabel>
                            <Select
                                value={formData.status}
                                label="Status"
                                onChange={(e) =>
                                    setFormData({ ...formData, status: e.target.value as GoalStatus })
                                }
                            >
                                <MenuItem value="not-started">Not Started</MenuItem>
                                <MenuItem value="in-progress">In Progress</MenuItem>
                                <MenuItem value="on-hold">On Hold</MenuItem>
                                <MenuItem value="completed">Completed</MenuItem>
                            </Select>
                        </FormControl>

                        <TextField
                            fullWidth
                            label="Target Date"
                            type="date"
                            value={formData.targetDate.split('T')[0] || ''}
                            onChange={(e) =>
                                setFormData({ ...formData, targetDate: new Date(e.target.value).toISOString() })
                            }
                            InputLabelProps={{ shrink: true }}
                            required
                        />
                    </Stack>

                    <Divider />

                    {/* Milestones */}
                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Milestones
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                            Break your goal into smaller, actionable steps
                        </Typography>

                        <Stack spacing={2}>
                            {formData.milestones.map((milestone) => (
                                <Stack key={milestone.id} direction="row" spacing={1} alignItems="center">
                                    <Typography variant="body2" sx={{ flex: 1 }}>
                                        • {milestone.title}
                                    </Typography>
                                    <IconButton size="small" onClick={() => handleRemoveMilestone(milestone.id)}>
                                        ✕
                                    </IconButton>
                                </Stack>
                            ))}

                            <Stack direction="row" spacing={1}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    placeholder="Add milestone..."
                                    value={newMilestone}
                                    onChange={(e) => setNewMilestone(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleAddMilestone()}
                                />
                                <Button variant="outlined" onClick={handleAddMilestone}>
                                    Add
                                </Button>
                            </Stack>
                        </Stack>
                    </Box>

                    <Divider />

                    {/* Success Criteria */}
                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Success Criteria
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                            How will you know when you've achieved this goal?
                        </Typography>

                        <Stack spacing={2}>
                            {formData.successCriteria.map((criterion, index) => (
                                <Stack key={index} direction="row" spacing={1} alignItems="center">
                                    <Typography variant="body2" sx={{ flex: 1 }}>
                                        ✓ {criterion}
                                    </Typography>
                                    <IconButton size="small" onClick={() => handleRemoveCriterion(index)}>
                                        ✕
                                    </IconButton>
                                </Stack>
                            ))}

                            <Stack direction="row" spacing={1}>
                                <TextField
                                    fullWidth
                                    size="small"
                                    placeholder="Add success criterion..."
                                    value={newCriterion}
                                    onChange={(e) => setNewCriterion(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleAddCriterion()}
                                />
                                <Button variant="outlined" onClick={handleAddCriterion}>
                                    Add
                                </Button>
                            </Stack>
                        </Stack>
                    </Box>

                    <Divider />

                    {/* Related Skills */}
                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Related Skills
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                            Link this goal to skills you want to develop
                        </Typography>

                        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 2 }} gap={1}>
                            {formData.relatedSkills.map((skillId) => {
                                const skill = mockSkills.find((s) => s.id === skillId);
                                return (
                                    <Chip
                                        key={skillId}
                                        label={skill?.name || skillId}
                                        onDelete={() => handleRemoveSkill(skillId)}
                                        color="primary"
                                    />
                                );
                            })}
                        </Stack>

                        <Stack direction="row" spacing={1}>
                            <FormControl fullWidth size="small">
                                <InputLabel>Select Skill</InputLabel>
                                <Select
                                    value={selectedSkill}
                                    label="Select Skill"
                                    onChange={(e) => setSelectedSkill(e.target.value)}
                                >
                                    {mockSkills
                                        .filter((s) => !formData.relatedSkills.includes(s.id))
                                        .map((skill) => (
                                            <MenuItem key={skill.id} value={skill.id}>
                                                {skill.name}
                                            </MenuItem>
                                        ))}
                                </Select>
                            </FormControl>
                            <Button variant="outlined" onClick={handleAddSkill} disabled={!selectedSkill}>
                                Add
                            </Button>
                        </Stack>
                    </Box>

                    {/* SMART Validation */}
                    {!isSmartGoal && (
                        <Alert severity="warning">
                            Your goal is missing some SMART criteria. Click "SMART Goal Framework" above to see
                            what's needed.
                        </Alert>
                    )}

                    {isSmartGoal && (
                        <Alert severity="success">✓ This is a SMART goal! Ready to submit.</Alert>
                    )}

                    {/* Actions */}
                    <Stack direction="row" justifyContent="space-between" sx={{ mt: 3 }}>
                        <Button onClick={onCancel}>Cancel</Button>
                        <Button variant="contained" onClick={handleSubmit} disabled={!isValid()}>
                            {isEditing ? 'Update Goal' : 'Create Goal'}
                        </Button>
                    </Stack>
                </Stack>
            </Card>
        </Box>
    );
};
