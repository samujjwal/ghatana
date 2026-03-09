/**
 * SkillsMatrix Component
 *
 * Organization-wide skills matrix showing competencies, proficiency levels,
 * skill gaps, and development plans. Enables strategic workforce planning.
 *
 * Features:
 * - Skills metrics dashboard (Total Skills, Avg Proficiency, Critical Gaps, Training Hours)
 * - 4-tab interface: Matrix, Gaps, Development, Analytics
 * - Proficiency tracking (1-5 scale)
 * - Skill gap analysis
 * - Development plan tracking
 * - Skill distribution visualization
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Box,
    Button,
    Card,
    Chip,
    Grid,
    KpiCard,
    LinearProgress,
    Stack,
    Tab,
    Tabs,
    Typography,
} from '@ghatana/ui';

// ============================================================================
// TypeScript Interfaces
// ============================================================================

/**
 * Skills metrics overview
 */
export interface SkillsMetrics {
    totalSkillsTracked: number;
    averageProficiency: number; // 1-5 scale
    criticalGaps: number;
    trainingHours: number;
}

/**
 * Skill definition
 */
export interface Skill {
    id: string;
    name: string;
    category: 'technical' | 'soft' | 'domain' | 'process';
    description: string;
    importance: 'critical' | 'high' | 'medium' | 'low';
}

/**
 * Team skill aggregate
 */
export interface TeamSkill {
    teamId: string;
    teamName: string;
    skills: {
        skillId: string;
        skillName: string;
        avgProficiency: number; // 1-5
        targetProficiency: number; // 1-5
        gap: number;
        expertCount: number;
    }[];
}

/**
 * Individual skill profile
 */
export interface IndividualSkill {
    personId: string;
    name: string;
    role: string;
    team: string;
    skills: {
        skillId: string;
        proficiency: number; // 1-5
        interest: 'high' | 'medium' | 'low';
        lastAssessed: string;
    }[];
}

/**
 * Identified skill gap
 */
export interface SkillGap {
    id: string;
    skillId: string;
    skillName: string;
    teamId: string;
    teamName: string;
    currentLevel: number;
    targetLevel: number;
    impact: 'critical' | 'high' | 'medium' | 'low';
    affectedProjects: string[];
    status: 'identified' | 'addressing' | 'resolved';
}

/**
 * Development plan
 */
export interface DevelopmentPlan {
    id: string;
    personId: string;
    personName: string;
    skillId: string;
    skillName: string;
    type: 'course' | 'mentorship' | 'project' | 'certification';
    status: 'planned' | 'in-progress' | 'completed';
    progress: number; // 0-100
    dueDate: string;
}

/**
 * Component props
 */
export interface SkillsMatrixProps {
    metrics: SkillsMetrics;
    skills: Skill[];
    teamSkills: TeamSkill[];
    individualSkills: IndividualSkill[];
    gaps: SkillGap[];
    plans: DevelopmentPlan[];
    onSkillClick?: (skillId: string) => void;
    onTeamClick?: (teamId: string) => void;
    onPersonClick?: (personId: string) => void;
    onGapClick?: (gapId: string) => void;
    onPlanClick?: (planId: string) => void;
    onAddSkill?: () => void;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get color for proficiency level (1-5)
 */
const getProficiencyColor = (level: number): 'error' | 'warning' | 'primary' | 'success' | 'default' => {
    if (level >= 4.5) return 'success'; // Expert
    if (level >= 3.5) return 'primary'; // Advanced
    if (level >= 2.5) return 'default'; // Intermediate
    if (level >= 1.5) return 'warning'; // Beginner
    return 'error'; // Novice
};

/**
 * Get label for proficiency level
 */
const getProficiencyLabel = (level: number): string => {
    if (level >= 4.5) return 'Expert';
    if (level >= 3.5) return 'Advanced';
    if (level >= 2.5) return 'Intermediate';
    if (level >= 1.5) return 'Beginner';
    return 'Novice';
};

/**
 * Get color for gap impact
 */
const getImpactColor = (impact: string): 'error' | 'warning' | 'default' | 'success' => {
    switch (impact) {
        case 'critical':
            return 'error';
        case 'high':
            return 'warning';
        case 'medium':
            return 'default';
        case 'low':
            return 'success';
        default:
            return 'default';
    }
};

/**
 * Get color for plan status
 */
const getPlanStatusColor = (status: string): 'default' | 'primary' | 'success' => {
    switch (status) {
        case 'planned':
            return 'default';
        case 'in-progress':
            return 'primary';
        case 'completed':
            return 'success';
        default:
            return 'default';
    }
};

/**
 * Format date
 */
const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
};

// ============================================================================
// Mock Data
// ============================================================================

export const mockSkillsData = {
    metrics: {
        totalSkillsTracked: 124,
        averageProficiency: 3.2,
        criticalGaps: 8,
        trainingHours: 450,
    } as SkillsMetrics,

    skills: [
        { id: 'skill-1', name: 'React', category: 'technical', description: 'Frontend library', importance: 'critical' },
        { id: 'skill-2', name: 'Java', category: 'technical', description: 'Backend language', importance: 'critical' },
        { id: 'skill-3', name: 'System Design', category: 'technical', description: 'Architecture', importance: 'high' },
        { id: 'skill-4', name: 'Communication', category: 'soft', description: 'Team collaboration', importance: 'high' },
        { id: 'skill-5', name: 'Agile', category: 'process', description: 'Development methodology', importance: 'medium' },
    ] as Skill[],

    teamSkills: [
        {
            teamId: 'team-1',
            teamName: 'Platform Engineering',
            skills: [
                { skillId: 'skill-2', skillName: 'Java', avgProficiency: 4.2, targetProficiency: 4.0, gap: 0, expertCount: 3 },
                { skillId: 'skill-3', skillName: 'System Design', avgProficiency: 3.8, targetProficiency: 4.0, gap: -0.2, expertCount: 2 },
                { skillId: 'skill-1', skillName: 'React', avgProficiency: 2.1, targetProficiency: 2.0, gap: 0, expertCount: 0 },
            ],
        },
        {
            teamId: 'team-2',
            teamName: 'Frontend Experience',
            skills: [
                { skillId: 'skill-1', skillName: 'React', avgProficiency: 4.5, targetProficiency: 4.5, gap: 0, expertCount: 4 },
                { skillId: 'skill-3', skillName: 'System Design', avgProficiency: 2.5, targetProficiency: 3.0, gap: -0.5, expertCount: 0 },
            ],
        },
    ] as TeamSkill[],

    individualSkills: [
        {
            personId: 'p-1',
            name: 'Sarah Chen',
            role: 'Senior Engineer',
            team: 'Platform Engineering',
            skills: [
                { skillId: 'skill-2', proficiency: 5, interest: 'high', lastAssessed: '2024-11-01' },
                { skillId: 'skill-3', proficiency: 4, interest: 'high', lastAssessed: '2024-11-01' },
            ],
        },
        {
            personId: 'p-2',
            name: 'Mike Ross',
            role: 'Frontend Dev',
            team: 'Frontend Experience',
            skills: [
                { skillId: 'skill-1', proficiency: 4, interest: 'high', lastAssessed: '2024-10-15' },
                { skillId: 'skill-3', proficiency: 2, interest: 'medium', lastAssessed: '2024-10-15' },
            ],
        },
    ] as IndividualSkill[],

    gaps: [
        {
            id: 'gap-1',
            skillId: 'skill-3',
            skillName: 'System Design',
            teamId: 'team-2',
            teamName: 'Frontend Experience',
            currentLevel: 2.5,
            targetLevel: 3.0,
            impact: 'high',
            affectedProjects: ['Dashboard Redesign'],
            status: 'addressing',
        },
        {
            id: 'gap-2',
            skillId: 'skill-5',
            skillName: 'Agile',
            teamId: 'team-1',
            teamName: 'Platform Engineering',
            currentLevel: 2.8,
            targetLevel: 3.5,
            impact: 'medium',
            affectedProjects: ['Sprint Velocity'],
            status: 'identified',
        },
    ] as SkillGap[],

    plans: [
        {
            id: 'plan-1',
            personId: 'p-2',
            personName: 'Mike Ross',
            skillId: 'skill-3',
            skillName: 'System Design',
            type: 'mentorship',
            status: 'in-progress',
            progress: 45,
            dueDate: '2025-01-30',
        },
        {
            id: 'plan-2',
            personId: 'p-1',
            personName: 'Sarah Chen',
            skillId: 'skill-4',
            skillName: 'Communication',
            type: 'course',
            status: 'planned',
            progress: 0,
            dueDate: '2025-02-15',
        },
    ] as DevelopmentPlan[],
};

// ============================================================================
// Component
// ============================================================================

/**
 * SkillsMatrix Component
 */
export const SkillsMatrix: React.FC<SkillsMatrixProps> = ({
    metrics,
    skills,
    teamSkills,
    individualSkills,
    gaps,
    plans,
    onSkillClick,
    onTeamClick,
    onPersonClick,
    onGapClick,
    onPlanClick,
    onAddSkill,
}) => {
    // State
    const [selectedTab, setSelectedTab] = useState<'matrix' | 'gaps' | 'development' | 'analytics'>('matrix');
    const [categoryFilter, setCategoryFilter] = useState<string>('all');

    // Handlers
    const handleTabChange = (_event: React.SyntheticEvent, newValue: 'matrix' | 'gaps' | 'development' | 'analytics') => {
        setSelectedTab(newValue);
    };

    // Filtering
    let filteredSkills = skills;
    if (categoryFilter !== 'all') {
        filteredSkills = filteredSkills.filter((skill) => skill.category === categoryFilter);
    }

    return (
        <Box className="p-6">
            {/* Header */}
            <Box className="mb-6">
                <Box className="flex items-center justify-between mb-2">
                    <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                        Skills Matrix
                    </Typography>
                    <Button variant="contained" color="primary" onClick={onAddSkill}>
                        Add Skill
                    </Button>
                </Box>
                <Typography variant="body1" className="text-slate-600 dark:text-neutral-400">
                    Strategic workforce planning and capability tracking
                </Typography>
            </Box>

            {/* Metrics */}
            <Grid container spacing={3} className="mb-6">
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Total Skills"
                        value={metrics.totalSkillsTracked.toString()}
                        trend="up"
                        trendValue="4%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Avg Proficiency"
                        value={metrics.averageProficiency.toFixed(1)}
                        trend="neutral"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Critical Gaps"
                        value={metrics.criticalGaps.toString()}
                        trend="down"
                        trendValue="2"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Training Hours"
                        value={metrics.trainingHours.toString()}
                        trend="up"
                        trendValue="12%"
                    />
                </Grid>
            </Grid>

            {/* Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={handleTabChange}>
                    <Tab label="Matrix" value="matrix" />
                    <Tab label="Skill Gaps" value="gaps" />
                    <Tab label="Development" value="development" />
                    <Tab label="Analytics" value="analytics" />
                </Tabs>

                <Box className="p-4">
                    {/* Matrix Tab */}
                    {selectedTab === 'matrix' && (
                        <Box>
                            {/* Filters */}
                            <Box className="mb-4">
                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                    Category
                                </Typography>
                                <Stack direction="row" spacing={1}>
                                    {['all', 'technical', 'soft', 'domain', 'process'].map((cat) => (
                                        <Chip
                                            key={cat}
                                            label={cat.charAt(0).toUpperCase() + cat.slice(1)}
                                            color={categoryFilter === cat ? 'primary' : 'default'}
                                            onClick={() => setCategoryFilter(cat)}
                                            className="cursor-pointer"
                                        />
                                    ))}
                                </Stack>
                            </Box>

                            {/* Team Skills Grid */}
                            <Grid container spacing={3}>
                                {teamSkills.map((team) => (
                                    <Grid item xs={12} key={team.teamId}>
                                        <Card
                                            className="cursor-pointer hover:shadow-md transition-shadow"
                                            onClick={() => onTeamClick?.(team.teamId)}
                                        >
                                            <Box className="p-4">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-3">
                                                    {team.teamName}
                                                </Typography>
                                                <Grid container spacing={2}>
                                                    {team.skills
                                                        .filter(s => categoryFilter === 'all' || skills.find(sk => sk.id === s.skillId)?.category === categoryFilter)
                                                        .map((skill) => (
                                                            <Grid item xs={12} sm={6} md={4} key={skill.skillId}>
                                                                <Box className="bg-slate-50 dark:bg-neutral-800 p-3 rounded">
                                                                    <Box className="flex justify-between items-center mb-1">
                                                                        <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                                            {skill.skillName}
                                                                        </Typography>
                                                                        <Chip
                                                                            label={getProficiencyLabel(skill.avgProficiency)}
                                                                            size="small"
                                                                            color={getProficiencyColor(skill.avgProficiency)}
                                                                        />
                                                                    </Box>
                                                                    <Box className="flex items-center gap-2 mb-1">
                                                                        <LinearProgress
                                                                            variant="determinate"
                                                                            value={(skill.avgProficiency / 5) * 100}
                                                                            className="flex-1"
                                                                            color={getProficiencyColor(skill.avgProficiency)}
                                                                        />
                                                                        <Typography variant="caption" className="text-slate-600 dark:text-neutral-400 w-8 text-right">
                                                                            {skill.avgProficiency.toFixed(1)}
                                                                        </Typography>
                                                                    </Box>
                                                                    <Box className="flex justify-between">
                                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                                            Target: {skill.targetProficiency.toFixed(1)}
                                                                        </Typography>
                                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                                            {skill.expertCount} Experts
                                                                        </Typography>
                                                                    </Box>
                                                                </Box>
                                                            </Grid>
                                                        ))}
                                                </Grid>
                                            </Box>
                                        </Card>
                                    </Grid>
                                ))}
                            </Grid>
                        </Box>
                    )}

                    {/* Gaps Tab */}
                    {selectedTab === 'gaps' && (
                        <Grid container spacing={3}>
                            {gaps.map((gap) => (
                                <Grid item xs={12} md={6} key={gap.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onGapClick?.(gap.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center justify-between mb-2">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {gap.skillName}
                                                </Typography>
                                                <Chip label={gap.impact.toUpperCase()} color={getImpactColor(gap.impact)} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                {gap.teamName} • Gap: {(gap.targetLevel - gap.currentLevel).toFixed(1)} levels
                                            </Typography>
                                            <Box className="mb-3">
                                                <Box className="flex justify-between mb-1">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Current: {gap.currentLevel}
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Target: {gap.targetLevel}
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={(gap.currentLevel / gap.targetLevel) * 100}
                                                    color="warning"
                                                />
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1 block">
                                                    Affected Projects
                                                </Typography>
                                                <Stack direction="row" spacing={1} className="flex-wrap gap-1">
                                                    {gap.affectedProjects.map((proj) => (
                                                        <Chip key={proj} label={proj} size="small" variant="outlined" />
                                                    ))}
                                                </Stack>
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Development Tab */}
                    {selectedTab === 'development' && (
                        <Grid container spacing={3}>
                            {plans.map((plan) => (
                                <Grid item xs={12} md={6} key={plan.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onPlanClick?.(plan.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center justify-between mb-2">
                                                <Box>
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {plan.personName}
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {plan.skillName} • {plan.type}
                                                    </Typography>
                                                </Box>
                                                <Chip label={plan.status} color={getPlanStatusColor(plan.status)} size="small" />
                                            </Box>
                                            <Box className="mb-3">
                                                <Box className="flex justify-between mb-1">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Progress
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                        {plan.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress variant="determinate" value={plan.progress} />
                                            </Box>
                                            <Box className="flex justify-between items-center">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                    Due: {formatDate(plan.dueDate)}
                                                </Typography>
                                                <Button size="small" variant="text">
                                                    View Details
                                                </Button>
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Analytics Tab */}
                    {selectedTab === 'analytics' && (
                        <Box className="text-center py-8">
                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-2">
                                Skills Analytics
                            </Typography>
                            <Typography variant="body1" className="text-slate-600 dark:text-neutral-400">
                                Detailed skill distribution and growth trends visualization coming soon.
                            </Typography>
                        </Box>
                    )}
                </Box>
            </Card>
        </Box>
    );
};

export default SkillsMatrix;
