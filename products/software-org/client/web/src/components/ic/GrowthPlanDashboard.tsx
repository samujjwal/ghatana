import React, { useEffect, useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    Chip,
    LinearProgress,
    Grid,
    Switch,
    Divider,
    IconButton,
} from '@ghatana/ui';

/**
 * Skill proficiency levels
 */
export type SkillLevel = 'beginner' | 'intermediate' | 'advanced' | 'expert';

/**
 * Goal status
 */
export type GoalStatus = 'not-started' | 'in-progress' | 'on-hold' | 'completed';

/**
 * Goal category
 */
export type GoalCategory = 'technical' | 'leadership' | 'business' | 'communication';

/**
 * Skill data structure
 */
export interface Skill {
    id: string;
    name: string;
    category: string;
    currentLevel: SkillLevel;
    targetLevel: SkillLevel;
    progress: number; // 0-100
    endorsements?: number;
    lastUpdated: string;
}

/**
 * Development goal
 */
export interface DevelopmentGoal {
    id: string;
    title: string;
    description: string;
    category: GoalCategory;
    status: GoalStatus;
    progress: number; // 0-100
    targetDate: string;
    milestones: Milestone[];
    relatedSkills?: string[];
    createdAt: string;
    updatedAt: string;
}

/**
 * Goal milestone
 */
export interface Milestone {
    id: string;
    title: string;
    completed: boolean;
    completedAt?: string;
    dueDate?: string;
}

/**
 * Competency area
 */
export interface Competency {
    id: string;
    name: string;
    description: string;
    currentScore: number; // 0-5
    targetScore: number; // 0-5
    skills: Skill[];
}

/**
 * Growth plan data
 */
export interface GrowthPlan {
    id: string;
    userId: string;
    managerId?: string;
    currentRole: string;
    targetRole?: string;
    startDate: string;
    reviewDate?: string;
    visibility: 'private' | 'manager' | 'public';
    competencies: Competency[];
    goals: DevelopmentGoal[];
    completedGoals: number;
    totalGoals: number;
    overallProgress: number; // 0-100
}

/**
 * Props for GrowthPlanDashboard
 */
interface GrowthPlanDashboardProps {
    plan?: GrowthPlan;
    onUpdateVisibility?: (visibility: 'private' | 'manager' | 'public') => void;
    onCreateGoal?: () => void;
    onEditGoal?: (goalId: string) => void;
    onUpdateSkill?: (skillId: string, newLevel: SkillLevel) => void;
    onCompleteMilestone?: (goalId: string, milestoneId: string) => void;
    isEditable?: boolean;
}

/**
 * Growth Plan Dashboard
 *
 * Displays an individual's professional development plan:
 * - Current role and career trajectory
 * - Competency areas with skill tracking
 * - Development goals with milestones
 * - Progress visualization
 * - Visibility controls (private/manager/public)
 */
export const GrowthPlanDashboard: React.FC<GrowthPlanDashboardProps> = ({
    plan: initialPlan,
    onUpdateVisibility,
    onCreateGoal,
    onEditGoal,
    onUpdateSkill,
    onCompleteMilestone,
    isEditable = true,
}) => {
    // Mock data if none provided
    const mockPlan: GrowthPlan = {
        id: 'plan-001',
        userId: 'user-123',
        managerId: 'manager-456',
        currentRole: 'Senior Software Engineer',
        targetRole: 'Staff Engineer',
        startDate: new Date(Date.now() - 180 * 86400000).toISOString(), // 6 months ago
        reviewDate: new Date(Date.now() + 90 * 86400000).toISOString(), // 3 months from now
        visibility: 'manager',
        competencies: [
            {
                id: 'comp-tech',
                name: 'Technical Excellence',
                description: 'Core technical skills and expertise',
                currentScore: 3.5,
                targetScore: 4.5,
                skills: [
                    {
                        id: 'skill-arch',
                        name: 'System Architecture',
                        category: 'Technical',
                        currentLevel: 'intermediate',
                        targetLevel: 'advanced',
                        progress: 60,
                        endorsements: 5,
                        lastUpdated: new Date(Date.now() - 7 * 86400000).toISOString(),
                    },
                    {
                        id: 'skill-scale',
                        name: 'Scalability & Performance',
                        category: 'Technical',
                        currentLevel: 'intermediate',
                        targetLevel: 'expert',
                        progress: 40,
                        endorsements: 3,
                        lastUpdated: new Date(Date.now() - 14 * 86400000).toISOString(),
                    },
                    {
                        id: 'skill-security',
                        name: 'Security Best Practices',
                        category: 'Technical',
                        currentLevel: 'advanced',
                        targetLevel: 'expert',
                        progress: 70,
                        endorsements: 8,
                        lastUpdated: new Date(Date.now() - 3 * 86400000).toISOString(),
                    },
                ],
            },
            {
                id: 'comp-lead',
                name: 'Leadership & Mentorship',
                description: 'Leading teams and mentoring junior engineers',
                currentScore: 2.5,
                targetScore: 4.0,
                skills: [
                    {
                        id: 'skill-mentor',
                        name: 'Mentoring',
                        category: 'Leadership',
                        currentLevel: 'intermediate',
                        targetLevel: 'advanced',
                        progress: 50,
                        endorsements: 4,
                        lastUpdated: new Date(Date.now() - 10 * 86400000).toISOString(),
                    },
                    {
                        id: 'skill-code-review',
                        name: 'Code Review & Feedback',
                        category: 'Leadership',
                        currentLevel: 'advanced',
                        targetLevel: 'expert',
                        progress: 65,
                        endorsements: 7,
                        lastUpdated: new Date(Date.now() - 5 * 86400000).toISOString(),
                    },
                ],
            },
            {
                id: 'comp-business',
                name: 'Business Impact',
                description: 'Understanding and driving business outcomes',
                currentScore: 2.0,
                targetScore: 3.5,
                skills: [
                    {
                        id: 'skill-product',
                        name: 'Product Thinking',
                        category: 'Business',
                        currentLevel: 'beginner',
                        targetLevel: 'intermediate',
                        progress: 30,
                        endorsements: 2,
                        lastUpdated: new Date(Date.now() - 20 * 86400000).toISOString(),
                    },
                    {
                        id: 'skill-metrics',
                        name: 'Data-Driven Decision Making',
                        category: 'Business',
                        currentLevel: 'intermediate',
                        targetLevel: 'advanced',
                        progress: 45,
                        endorsements: 3,
                        lastUpdated: new Date(Date.now() - 12 * 86400000).toISOString(),
                    },
                ],
            },
        ],
        goals: [
            {
                id: 'goal-001',
                title: 'Lead Migration to Microservices Architecture',
                description: 'Design and lead the migration of our monolithic application to a microservices architecture',
                category: 'technical',
                status: 'in-progress',
                progress: 60,
                targetDate: new Date(Date.now() + 90 * 86400000).toISOString(),
                milestones: [
                    { id: 'm1', title: 'Architecture design document', completed: true, completedAt: new Date(Date.now() - 60 * 86400000).toISOString() },
                    { id: 'm2', title: 'Proof of concept for 2 services', completed: true, completedAt: new Date(Date.now() - 30 * 86400000).toISOString() },
                    { id: 'm3', title: 'Team training on microservices patterns', completed: false, dueDate: new Date(Date.now() + 14 * 86400000).toISOString() },
                    { id: 'm4', title: 'Migrate first 3 services to production', completed: false, dueDate: new Date(Date.now() + 60 * 86400000).toISOString() },
                ],
                relatedSkills: ['skill-arch', 'skill-scale'],
                createdAt: new Date(Date.now() - 90 * 86400000).toISOString(),
                updatedAt: new Date(Date.now() - 7 * 86400000).toISOString(),
            },
            {
                id: 'goal-002',
                title: 'Mentor 2 Junior Engineers',
                description: 'Provide regular mentorship to 2 junior engineers, focusing on system design and code quality',
                category: 'leadership',
                status: 'in-progress',
                progress: 75,
                targetDate: new Date(Date.now() + 60 * 86400000).toISOString(),
                milestones: [
                    { id: 'm5', title: 'Establish weekly 1:1s', completed: true, completedAt: new Date(Date.now() - 80 * 86400000).toISOString() },
                    { id: 'm6', title: 'Create learning plan for each mentee', completed: true, completedAt: new Date(Date.now() - 70 * 86400000).toISOString() },
                    { id: 'm7', title: 'Conduct code review training', completed: true, completedAt: new Date(Date.now() - 40 * 86400000).toISOString() },
                    { id: 'm8', title: 'Mentees complete first major project', completed: false, dueDate: new Date(Date.now() + 45 * 86400000).toISOString() },
                ],
                relatedSkills: ['skill-mentor', 'skill-code-review'],
                createdAt: new Date(Date.now() - 100 * 86400000).toISOString(),
                updatedAt: new Date(Date.now() - 2 * 86400000).toISOString(),
            },
            {
                id: 'goal-003',
                title: 'Launch Data-Driven Feature Prioritization',
                description: 'Implement analytics and metrics to drive feature prioritization decisions',
                category: 'business',
                status: 'not-started',
                progress: 0,
                targetDate: new Date(Date.now() + 120 * 86400000).toISOString(),
                milestones: [
                    { id: 'm9', title: 'Define key product metrics', completed: false },
                    { id: 'm10', title: 'Set up analytics infrastructure', completed: false },
                    { id: 'm11', title: 'Create metrics dashboard', completed: false },
                    { id: 'm12', title: 'Present insights to leadership', completed: false },
                ],
                relatedSkills: ['skill-product', 'skill-metrics'],
                createdAt: new Date(Date.now() - 30 * 86400000).toISOString(),
                updatedAt: new Date(Date.now() - 30 * 86400000).toISOString(),
            },
        ],
        completedGoals: 1,
        totalGoals: 4,
        overallProgress: 52,
    };

    const [plan, setPlan] = useState<GrowthPlan>(() => initialPlan || mockPlan);
    const [visibility, setVisibility] = useState(plan.visibility);

    useEffect(() => {
        if (!initialPlan) return;
        setPlan(initialPlan);
        setVisibility(initialPlan.visibility);
    }, [initialPlan]);

    // Helper functions
    const getSkillLevelColor = (level: SkillLevel): string => {
        switch (level) {
            case 'beginner':
                return 'default';
            case 'intermediate':
                return 'info';
            case 'advanced':
                return 'success';
            case 'expert':
                return 'warning';
        }
    };

    const getGoalStatusColor = (status: GoalStatus): string => {
        switch (status) {
            case 'not-started':
                return 'default';
            case 'in-progress':
                return 'info';
            case 'on-hold':
                return 'warning';
            case 'completed':
                return 'success';
        }
    };

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

    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    };

    const getDaysUntil = (dateString: string): number => {
        const target = new Date(dateString);
        const now = new Date();
        return Math.ceil((target.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    };

    const handleVisibilityChange = (newVisibility: 'private' | 'manager' | 'public') => {
        setVisibility(newVisibility);
        onUpdateVisibility?.(newVisibility);
    };

    return (
        <Box>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="start" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4">My Growth Plan</Typography>
                    <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                        <Typography variant="body1" color="text.secondary">
                            {plan.currentRole}
                        </Typography>
                        {plan.targetRole && (
                            <>
                                <Typography variant="body1" color="text.secondary">→</Typography>
                                <Typography variant="body1" fontWeight="medium" color="primary.main">
                                    {plan.targetRole}
                                </Typography>
                            </>
                        )}
                    </Stack>
                </Box>
                <Stack direction="row" spacing={2}>
                    <Switch
                        label={
                            <Stack direction="row" spacing={1} alignItems="center">
                                <Typography variant="body2">
                                    {visibility === 'private'
                                        ? '🔒 Private'
                                        : visibility === 'manager'
                                            ? '👤 Manager'
                                            : '🌐 Public'}
                                </Typography>
                            </Stack>
                        }
                        checked={visibility !== 'private'}
                        onToggle={(checked) => handleVisibilityChange(checked ? 'manager' : 'private')}
                        disabled={!isEditable}
                    />
                    {isEditable && (
                        <Button variant="contained" onClick={onCreateGoal}>
                            Create Goal
                        </Button>
                    )}
                </Stack>
            </Stack>

            {/* Overview Stats */}
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Overall Progress
                        </Typography>
                        <Typography variant="h4">{plan.overallProgress}%</Typography>
                        <LinearProgress
                            variant="determinate"
                            value={plan.overallProgress}
                            sx={{ mt: 1 }}
                        />
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Active Goals
                        </Typography>
                        <Typography variant="h4">
                            {plan.goals.filter((g) => g.status === 'in-progress').length}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            of {plan.totalGoals} total
                        </Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Skills Tracked
                        </Typography>
                        <Typography variant="h4">
                            {plan.competencies.reduce((sum, c) => sum + c.skills.length, 0)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            across {plan.competencies.length} competencies
                        </Typography>
                    </Card>
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Next Review
                        </Typography>
                        <Typography variant="h4">
                            {plan.reviewDate ? getDaysUntil(plan.reviewDate) : '—'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            {plan.reviewDate ? 'days' : 'not scheduled'}
                        </Typography>
                    </Card>
                </Grid>
            </Grid>

            {/* Competencies Section */}
            <Typography variant="h5" gutterBottom sx={{ mt: 4, mb: 2 }}>
                Competency Areas
            </Typography>
            <Grid container spacing={3} sx={{ mb: 4 }}>
                {plan.competencies.map((competency) => (
                    <Grid item xs={12} md={6} key={competency.id}>
                        <Card sx={{ p: 3 }}>
                            <Stack direction="row" justifyContent="space-between" alignItems="start" sx={{ mb: 2 }}>
                                <Box>
                                    <Typography variant="h6">{competency.name}</Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {competency.description}
                                    </Typography>
                                </Box>
                                <Stack direction="row" spacing={0.5} alignItems="center">
                                    <Typography variant="h5" fontWeight="bold">
                                        {competency.currentScore.toFixed(1)}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        / {competency.targetScore}
                                    </Typography>
                                </Stack>
                            </Stack>

                            <Divider sx={{ my: 2 }} />

                            <Stack spacing={2}>
                                {competency.skills.map((skill) => (
                                    <Box key={skill.id}>
                                        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                                            <Stack direction="row" spacing={1} alignItems="center">
                                                <Typography variant="body2" fontWeight="medium">
                                                    {skill.name}
                                                </Typography>
                                                {skill.endorsements && skill.endorsements > 0 && (
                                                    <Chip
                                                        label={`${skill.endorsements} 👍`}
                                                        size="small"
                                                        variant="outlined"
                                                    />
                                                )}
                                            </Stack>
                                            <Stack direction="row" spacing={1}>
                                                <Chip
                                                    label={skill.currentLevel}
                                                    size="small"
                                                    color={getSkillLevelColor(skill.currentLevel) as any}
                                                />
                                                <Typography variant="caption" color="text.secondary">→</Typography>
                                                <Chip
                                                    label={skill.targetLevel}
                                                    size="small"
                                                    color={getSkillLevelColor(skill.targetLevel) as any}
                                                    variant="outlined"
                                                />
                                            </Stack>
                                        </Stack>
                                        <LinearProgress
                                            variant="determinate"
                                            value={skill.progress}
                                            sx={{ height: 6, borderRadius: 1 }}
                                        />
                                        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
                                            {skill.progress}% progress · Updated {formatDate(skill.lastUpdated)}
                                        </Typography>
                                    </Box>
                                ))}
                            </Stack>
                        </Card>
                    </Grid>
                ))}
            </Grid>

            {/* Development Goals Section */}
            <Typography variant="h5" gutterBottom sx={{ mt: 4, mb: 2 }}>
                Development Goals
            </Typography>
            <Stack spacing={3}>
                {plan.goals.map((goal) => (
                    <Card
                        key={goal.id}
                        sx={{
                            p: 3,
                            borderLeft: 4,
                            borderColor:
                                goal.status === 'completed'
                                    ? 'success.main'
                                    : goal.status === 'in-progress'
                                        ? 'info.main'
                                        : 'divider',
                        }}
                    >
                        <Stack direction="row" justifyContent="space-between" alignItems="start" sx={{ mb: 2 }}>
                            <Box sx={{ flex: 1 }}>
                                <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                                    <Typography variant="body1">{getCategoryIcon(goal.category)}</Typography>
                                    <Typography variant="h6">{goal.title}</Typography>
                                    <Chip
                                        label={goal.status.replace('-', ' ')}
                                        size="small"
                                        color={getGoalStatusColor(goal.status) as any}
                                    />
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                    {goal.description}
                                </Typography>
                            </Box>
                            {isEditable && (
                                <IconButton size="small" onClick={() => onEditGoal?.(goal.id)}>
                                    ✏️
                                </IconButton>
                            )}
                        </Stack>

                        {/* Progress Bar */}
                        <Box sx={{ mb: 2 }}>
                            <Stack direction="row" justifyContent="space-between" sx={{ mb: 1 }}>
                                <Typography variant="caption" color="text.secondary">
                                    Progress
                                </Typography>
                                <Typography variant="caption" fontWeight="medium">
                                    {goal.progress}%
                                </Typography>
                            </Stack>
                            <LinearProgress
                                variant="determinate"
                                value={goal.progress}
                                sx={{ height: 8, borderRadius: 1 }}
                            />
                        </Box>

                        {/* Milestones */}
                        <Box sx={{ mb: 2 }}>
                            <Typography variant="caption" color="text.secondary" gutterBottom>
                                Milestones
                            </Typography>
                            <Stack spacing={1} sx={{ mt: 1 }}>
                                {goal.milestones.map((milestone) => (
                                    <Stack key={milestone.id} direction="row" spacing={1} alignItems="center">
                                        <input
                                            type="checkbox"
                                            checked={milestone.completed}
                                            onChange={() =>
                                                isEditable && onCompleteMilestone?.(goal.id, milestone.id)
                                            }
                                            disabled={!isEditable}
                                            style={{ cursor: isEditable ? 'pointer' : 'default' }}
                                        />
                                        <Typography
                                            variant="body2"
                                            sx={{
                                                textDecoration: milestone.completed ? 'line-through' : 'none',
                                                color: milestone.completed ? 'text.secondary' : 'text.primary',
                                            }}
                                        >
                                            {milestone.title}
                                        </Typography>
                                        {milestone.completed && milestone.completedAt && (
                                            <Typography variant="caption" color="success.main">
                                                ✓ {formatDate(milestone.completedAt)}
                                            </Typography>
                                        )}
                                        {!milestone.completed && milestone.dueDate && (
                                            <Typography variant="caption" color="text.secondary">
                                                Due {formatDate(milestone.dueDate)}
                                            </Typography>
                                        )}
                                    </Stack>
                                ))}
                            </Stack>
                        </Box>

                        {/* Footer */}
                        <Stack
                            direction="row"
                            justifyContent="space-between"
                            alignItems="center"
                            sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}
                        >
                            <Stack direction="row" spacing={1}>
                                {goal.relatedSkills && goal.relatedSkills.length > 0 && (
                                    <Typography variant="caption" color="text.secondary">
                                        Related skills: {goal.relatedSkills.length}
                                    </Typography>
                                )}
                            </Stack>
                            <Typography variant="caption" color="text.secondary">
                                Target: {formatDate(goal.targetDate)} ({getDaysUntil(goal.targetDate)} days)
                            </Typography>
                        </Stack>
                    </Card>
                ))}
            </Stack>

            {/* Empty State */}
            {plan.goals.length === 0 && (
                <Card sx={{ p: 4, textAlign: 'center' }}>
                    <Typography variant="h6" gutterBottom>
                        No development goals yet
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Create your first goal to start tracking your professional growth
                    </Typography>
                    {isEditable && (
                        <Button variant="contained" onClick={onCreateGoal}>
                            Create Your First Goal
                        </Button>
                    )}
                </Card>
            )}
        </Box>
    );
};
