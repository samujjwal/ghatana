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
    FormControl,
    InputLabel,
    Grid,
    Rating,
    Chip,
    Divider,
    Alert,
    Switch,
    FormControlLabel,
    IconButton,
} from '@ghatana/design-system';

/**
 * Goal with rating
 */
export interface GoalRating {
    id: string;
    title: string;
    description: string;
    targetDate: string;
    rating?: number; // 1-5
    comment?: string;
    achieved: boolean;
}

/**
 * Competency rating
 */
export interface CompetencyRating {
    category: string;
    rating: number; // 1-5
    comment?: string;
}

/**
 * New goal for next cycle
 */
export interface NextCycleGoal {
    id: string;
    title: string;
    description: string;
    targetDate?: string;
}

/**
 * Performance review form data
 */
export interface PerformanceReviewFormData {
    employeeId: string;
    cycleId: string;
    goalRatings: GoalRating[];
    competencyRatings: CompetencyRating[];
    strengths: string;
    improvements: string;
    careerDevelopment: string;
    nextCycleGoals: NextCycleGoal[];
    overallRating?: number; // 1-5 (auto-calculated or manual override)
    manualOverride: boolean;
    promotionRecommended: boolean;
    salaryAdjustment: number; // Percentage
    nextReviewDate: string;
}

/**
 * Props for PerformanceReviewForm
 */
interface PerformanceReviewFormProps {
    employeeId: string;
    employeeName: string;
    employeeRole: string;
    employeeTenure?: string;
    cycleId: string;
    cycleName: string;
    previousGoals?: GoalRating[];
    initialData?: Partial<PerformanceReviewFormData>;
    onSubmit?: (data: PerformanceReviewFormData) => void;
    onSaveDraft?: (data: PerformanceReviewFormData) => void;
    onCancel?: () => void;
    isEditing?: boolean;
}

/**
 * Performance Review Form
 *
 * Comprehensive form for conducting employee performance reviews:
 * - Employee information display
 * - Goal achievement ratings
 * - Competency assessments
 * - Written feedback sections
 * - Next cycle goal setting
 * - Promotion & salary recommendations
 * - Overall rating calculation
 */
export const PerformanceReviewForm: React.FC<PerformanceReviewFormProps> = ({
    employeeId,
    employeeName,
    employeeRole,
    employeeTenure = '2 years 3 months',
    cycleId,
    cycleName,
    previousGoals: initialPreviousGoals,
    initialData,
    onSubmit,
    onSaveDraft,
    onCancel,
    isEditing = false,
}) => {
    // Mock previous goals if none provided
    const mockPreviousGoals: GoalRating[] = initialPreviousGoals || [
        {
            id: 'goal-1',
            title: 'Lead microservices migration',
            description: 'Migrate 3 monolith services to microservices architecture',
            targetDate: '2025-03-31',
            achieved: true,
        },
        {
            id: 'goal-2',
            title: 'Mentor 2 junior engineers',
            description: 'Provide weekly mentorship and code review guidance',
            targetDate: '2025-03-31',
            achieved: true,
        },
        {
            id: 'goal-3',
            title: 'Improve system reliability',
            description: 'Reduce production incidents by 30%',
            targetDate: '2025-03-31',
            achieved: false,
        },
    ];

    // Competency categories
    const competencyCategories = [
        'Technical Skills',
        'Problem Solving',
        'Communication',
        'Leadership & Mentorship',
        'Collaboration',
        'Initiative & Ownership',
    ];

    // Form state
    const [formData, setFormData] = useState<PerformanceReviewFormData>({
        employeeId,
        cycleId,
        goalRatings: mockPreviousGoals.map((goal) => ({
            ...goal,
            rating: initialData?.goalRatings?.find((g) => g.id === goal.id)?.rating,
            comment: initialData?.goalRatings?.find((g) => g.id === goal.id)?.comment || '',
        })),
        competencyRatings: competencyCategories.map((category) => ({
            category,
            rating: initialData?.competencyRatings?.find((c) => c.category === category)?.rating || 3,
            comment: initialData?.competencyRatings?.find((c) => c.category === category)?.comment || '',
        })),
        strengths: initialData?.strengths || '',
        improvements: initialData?.improvements || '',
        careerDevelopment: initialData?.careerDevelopment || '',
        nextCycleGoals: initialData?.nextCycleGoals || [],
        overallRating: initialData?.overallRating,
        manualOverride: initialData?.manualOverride || false,
        promotionRecommended: initialData?.promotionRecommended || false,
        salaryAdjustment: initialData?.salaryAdjustment || 0,
        nextReviewDate: initialData?.nextReviewDate || '',
    });

    const [newGoalTitle, setNewGoalTitle] = useState('');
    const [newGoalDescription, setNewGoalDescription] = useState('');

    // Calculate overall rating from goals and competencies
    const calculateOverallRating = (): number => {
        const goalAvg =
            formData.goalRatings.filter((g) => g.rating).length > 0
                ? formData.goalRatings.reduce((sum, g) => sum + (g.rating || 0), 0) /
                formData.goalRatings.filter((g) => g.rating).length
                : 0;

        const competencyAvg =
            formData.competencyRatings.reduce((sum, c) => sum + c.rating, 0) /
            formData.competencyRatings.length;

        // Weighted average: 60% competencies, 40% goals
        return competencyAvg * 0.6 + goalAvg * 0.4;
    };

    const autoCalculatedRating = calculateOverallRating();
    const displayRating = formData.manualOverride
        ? formData.overallRating || autoCalculatedRating
        : autoCalculatedRating;

    // Validation
    const isValid = (): boolean => {
        const hasGoalRatings = formData.goalRatings.every((g) => g.rating !== undefined);
        const hasFeedback =
            formData.strengths.length > 0 &&
            formData.improvements.length > 0 &&
            formData.careerDevelopment.length > 0;
        const hasNextReviewDate = formData.nextReviewDate.length > 0;

        return hasGoalRatings && hasFeedback && hasNextReviewDate;
    };

    // Handlers
    const handleGoalRatingChange = (goalId: string, rating: number) => {
        setFormData({
            ...formData,
            goalRatings: formData.goalRatings.map((g) =>
                g.id === goalId ? { ...g, rating } : g
            ),
        });
    };

    const handleGoalCommentChange = (goalId: string, comment: string) => {
        setFormData({
            ...formData,
            goalRatings: formData.goalRatings.map((g) =>
                g.id === goalId ? { ...g, comment } : g
            ),
        });
    };

    const handleCompetencyRatingChange = (category: string, rating: number) => {
        setFormData({
            ...formData,
            competencyRatings: formData.competencyRatings.map((c) =>
                c.category === category ? { ...c, rating } : c
            ),
        });
    };

    const handleCompetencyCommentChange = (category: string, comment: string) => {
        setFormData({
            ...formData,
            competencyRatings: formData.competencyRatings.map((c) =>
                c.category === category ? { ...c, comment } : c
            ),
        });
    };

    const handleAddNextCycleGoal = () => {
        if (newGoalTitle.trim()) {
            setFormData({
                ...formData,
                nextCycleGoals: [
                    ...formData.nextCycleGoals,
                    {
                        id: `goal-${Date.now()}`,
                        title: newGoalTitle,
                        description: newGoalDescription,
                    },
                ],
            });
            setNewGoalTitle('');
            setNewGoalDescription('');
        }
    };

    const handleRemoveNextCycleGoal = (goalId: string) => {
        setFormData({
            ...formData,
            nextCycleGoals: formData.nextCycleGoals.filter((g) => g.id !== goalId),
        });
    };

    const handleSubmit = () => {
        if (isValid()) {
            onSubmit?.(formData);
        }
    };

    const handleSaveDraft = () => {
        onSaveDraft?.(formData);
    };

    // Get rating label
    const getRatingLabel = (rating: number): string => {
        if (rating >= 4.5) return 'Exceptional';
        if (rating >= 3.5) return 'Exceeds Expectations';
        if (rating >= 2.5) return 'Meets Expectations';
        if (rating >= 1.5) return 'Needs Improvement';
        return 'Unsatisfactory';
    };

    // Get rating color
    const getRatingColor = (rating: number): string => {
        if (rating >= 4.5) return 'success';
        if (rating >= 3.5) return 'info';
        if (rating >= 2.5) return 'default';
        if (rating >= 1.5) return 'warning';
        return 'error';
    };

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h5">
                        Performance Review: {employeeName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {employeeRole} • {employeeTenure} • {cycleName}
                    </Typography>
                </Box>
                <Stack direction="row" spacing={1}>
                    <Button variant="outlined" onClick={onCancel}>
                        Cancel
                    </Button>
                    <Button variant="outlined" onClick={handleSaveDraft}>
                        Save Draft
                    </Button>
                    <Button variant="contained" onClick={handleSubmit} disabled={!isValid()}>
                        {isEditing ? 'Update Review' : 'Submit Review'}
                    </Button>
                </Stack>
            </Stack>

            {!isValid() && (
                <Alert severity="info" sx={{ mb: 3 }}>
                    Please complete all required sections: goal ratings, written feedback, and next review date.
                </Alert>
            )}

            {/* Goal Achievement Section */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    1. Goal Achievement
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    Rate each goal from the previous cycle (1 = Not Achieved, 5 = Exceeded)
                </Typography>

                <Stack spacing={3}>
                    {formData.goalRatings.map((goal) => (
                        <Box key={goal.id}>
                            <Stack direction="row" justifyContent="space-between" alignItems="start" sx={{ mb: 1 }}>
                                <Box sx={{ flex: 1 }}>
                                    <Typography variant="subtitle2">{goal.title}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {goal.description}
                                    </Typography>
                                    <Chip
                                        label={goal.achieved ? '✓ Achieved' : '✗ Not Achieved'}
                                        size="small"
                                        color={goal.achieved ? 'success' : 'default'}
                                        sx={{ mt: 0.5 }}
                                    />
                                </Box>
                                <Box>
                                    <Rating
                                        value={goal.rating || 0}
                                        onChange={(_, value) => handleGoalRatingChange(goal.id, value || 0)}
                                        size="large"
                                    />
                                </Box>
                            </Stack>
                            <TextField
                                fullWidth
                                placeholder="Add comments about this goal..."
                                value={goal.comment || ''}
                                onChange={(e) => handleGoalCommentChange(goal.id, e.target.value)}
                                multiline
                                rows={2}
                                size="small"
                            />
                            {formData.goalRatings.indexOf(goal) < formData.goalRatings.length - 1 && (
                                <Divider sx={{ mt: 2 }} />
                            )}
                        </Box>
                    ))}
                </Stack>
            </Card>

            {/* Competency Ratings Section */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    2. Competency Assessment
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    Rate each competency (1 = Needs Improvement, 5 = Exceptional)
                </Typography>

                <Grid container spacing={3}>
                    {formData.competencyRatings.map((competency) => (
                        <Grid item xs={12} md={6} key={competency.category}>
                            <Box
                                sx={{
                                    p: 2,
                                    border: 1,
                                    borderColor: 'divider',
                                    borderRadius: 1,
                                }}
                            >
                                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                                    <Typography variant="subtitle2">{competency.category}</Typography>
                                    <Rating
                                        value={competency.rating}
                                        onChange={(_, value) =>
                                            handleCompetencyRatingChange(competency.category, value || 0)
                                        }
                                    />
                                </Stack>
                                <TextField
                                    fullWidth
                                    placeholder="Optional comments..."
                                    value={competency.comment || ''}
                                    onChange={(e) =>
                                        handleCompetencyCommentChange(competency.category, e.target.value)
                                    }
                                    multiline
                                    rows={2}
                                    size="small"
                                />
                            </Box>
                        </Grid>
                    ))}
                </Grid>
            </Card>

            {/* Written Feedback Section */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    3. Written Feedback
                </Typography>

                <Stack spacing={3}>
                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Strengths *
                        </Typography>
                        <TextField
                            fullWidth
                            placeholder="What does this employee do exceptionally well? Provide specific examples..."
                            value={formData.strengths}
                            onChange={(e) => setFormData({ ...formData, strengths: e.target.value })}
                            multiline
                            rows={4}
                            required
                        />
                    </Box>

                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Areas for Improvement *
                        </Typography>
                        <TextField
                            fullWidth
                            placeholder="Where can this employee grow? Be specific and constructive..."
                            value={formData.improvements}
                            onChange={(e) => setFormData({ ...formData, improvements: e.target.value })}
                            multiline
                            rows={4}
                            required
                        />
                    </Box>

                    <Box>
                        <Typography variant="subtitle2" gutterBottom>
                            Career Development Suggestions *
                        </Typography>
                        <TextField
                            fullWidth
                            placeholder="What skills, training, or experiences would help this employee grow?"
                            value={formData.careerDevelopment}
                            onChange={(e) => setFormData({ ...formData, careerDevelopment: e.target.value })}
                            multiline
                            rows={4}
                            required
                        />
                    </Box>
                </Stack>
            </Card>

            {/* Next Cycle Goals Section */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    4. Goals for Next Cycle
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    Set goals for the upcoming review period
                </Typography>

                <Stack spacing={2}>
                    {formData.nextCycleGoals.map((goal) => (
                        <Box
                            key={goal.id}
                            sx={{
                                p: 2,
                                border: 1,
                                borderColor: 'divider',
                                borderRadius: 1,
                            }}
                        >
                            <Stack direction="row" justifyContent="space-between" alignItems="start">
                                <Box sx={{ flex: 1 }}>
                                    <Typography variant="subtitle2">{goal.title}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {goal.description}
                                    </Typography>
                                </Box>
                                <IconButton
                                    size="small"
                                    onClick={() => handleRemoveNextCycleGoal(goal.id)}
                                    color="error"
                                >
                                    ✕
                                </IconButton>
                            </Stack>
                        </Box>
                    ))}

                    <Divider />

                    <Grid container spacing={2}>
                        <Grid item xs={12} md={4}>
                            <TextField
                                fullWidth
                                placeholder="Goal title"
                                value={newGoalTitle}
                                onChange={(e) => setNewGoalTitle(e.target.value)}
                                size="small"
                            />
                        </Grid>
                        <Grid item xs={12} md={6}>
                            <TextField
                                fullWidth
                                placeholder="Goal description"
                                value={newGoalDescription}
                                onChange={(e) => setNewGoalDescription(e.target.value)}
                                size="small"
                            />
                        </Grid>
                        <Grid item xs={12} md={2}>
                            <Button
                                fullWidth
                                variant="outlined"
                                onClick={handleAddNextCycleGoal}
                                disabled={!newGoalTitle.trim()}
                            >
                                Add Goal
                            </Button>
                        </Grid>
                    </Grid>
                </Stack>
            </Card>

            {/* Overall Rating & Recommendations Section */}
            <Card sx={{ p: 3 }}>
                <Typography variant="h6" gutterBottom>
                    5. Overall Rating & Recommendations
                </Typography>

                <Grid container spacing={3}>
                    <Grid item xs={12} md={6}>
                        <Box sx={{ p: 2, bgcolor: 'action.hover', borderRadius: 1 }}>
                            <Typography variant="body2" color="text.secondary" gutterBottom>
                                Overall Performance Rating
                            </Typography>
                            <Stack direction="row" alignItems="center" spacing={2}>
                                <Rating
                                    value={displayRating}
                                    onChange={(_, value) =>
                                        setFormData({
                                            ...formData,
                                            overallRating: value || undefined,
                                            manualOverride: true,
                                        })
                                    }
                                    size="large"
                                    precision={0.5}
                                    readOnly={!formData.manualOverride}
                                />
                                <Typography variant="h6">{displayRating.toFixed(1)}</Typography>
                                <Chip
                                    label={getRatingLabel(displayRating)}
                                    color={getRatingColor(displayRating) as any}
                                />
                            </Stack>
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={formData.manualOverride}
                                        onChange={(e) =>
                                            setFormData({ ...formData, manualOverride: e.target.checked })
                                        }
                                    />
                                }
                                label="Manual override"
                            />
                            {!formData.manualOverride && (
                                <Typography variant="caption" color="text.secondary">
                                    Auto-calculated from goals (40%) and competencies (60%)
                                </Typography>
                            )}
                        </Box>
                    </Grid>

                    <Grid item xs={12} md={6}>
                        <Stack spacing={2}>
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={formData.promotionRecommended}
                                        onChange={(e) =>
                                            setFormData({ ...formData, promotionRecommended: e.target.checked })
                                        }
                                    />
                                }
                                label="Recommend for Promotion"
                            />

                            <Box>
                                <Typography variant="body2" gutterBottom>
                                    Salary Adjustment (%)
                                </Typography>
                                <TextField
                                    fullWidth
                                    type="number"
                                    value={formData.salaryAdjustment}
                                    onChange={(e) =>
                                        setFormData({ ...formData, salaryAdjustment: parseFloat(e.target.value) || 0 })
                                    }
                                    inputProps={{ min: 0, max: 50, step: 0.5 }}
                                    size="small"
                                />
                            </Box>

                            <Box>
                                <Typography variant="body2" gutterBottom>
                                    Next Review Date *
                                </Typography>
                                <TextField
                                    fullWidth
                                    type="date"
                                    value={formData.nextReviewDate}
                                    onChange={(e) => setFormData({ ...formData, nextReviewDate: e.target.value })}
                                    InputLabelProps={{ shrink: true }}
                                    size="small"
                                    required
                                />
                            </Box>
                        </Stack>
                    </Grid>
                </Grid>
            </Card>
        </Box>
    );
};
