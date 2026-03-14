/**
 * InnovationTracker Component
 *
 * Innovation and experimentation tracking system to foster an innovation culture.
 * Tracks ideas, active experiments, results, and shared learnings.
 *
 * Features:
 * - Innovation metrics dashboard (Total Ideas, Active Experiments, Success Rate, Impact Value)
 * - 4-tab interface: Ideas, Experiments, Results, Learnings
 * - Idea submission and voting
 * - Experiment progress tracking
 * - Result analysis and impact assessment
 * - Knowledge sharing via learnings
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
    Progress,
    Stack,
    Tabs,
    Typography,
} from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';

// ============================================================================
// TypeScript Interfaces
// ============================================================================

/**
 * Innovation metrics overview
 */
export interface InnovationMetrics {
    totalIdeas: number;
    activeExperiments: number;
    successRate: number; // percentage
    impactValue: string; // e.g., "$1.2M" or "High"
}

/**
 * Innovation idea
 */
export interface Idea {
    id: string;
    title: string;
    description: string;
    submitter: string;
    team: string;
    submittedDate: string;
    votes: number;
    status: 'new' | 'under-review' | 'approved' | 'rejected';
    category: 'product' | 'process' | 'technology' | 'culture';
    potentialImpact: 'high' | 'medium' | 'low';
}

/**
 * Active experiment
 */
export interface Experiment {
    id: string;
    ideaId: string;
    title: string;
    hypothesis: string;
    owner: string;
    startDate: string;
    endDate: string;
    progress: number; // 0-100
    status: 'planning' | 'in-progress' | 'analyzing';
    successCriteria: string;
    resources: string[];
}

/**
 * Experiment result
 */
export interface ExperimentResult {
    id: string;
    experimentId: string;
    title: string;
    outcome: 'success' | 'failure' | 'pivot';
    impactScore: number; // 1-10
    completionDate: string;
    summary: string;
    nextSteps: string;
    metrics: { name: string; value: string; change: string }[];
}

/**
 * Key learning from experiment
 */
export interface Learning {
    id: string;
    resultId: string;
    title: string;
    category: 'technical' | 'market' | 'user' | 'process';
    insight: string;
    applicability: string[]; // Teams/Areas where this applies
    sharedBy: string;
    date: string;
    likes: number;
}

/**
 * Component props
 */
export interface InnovationTrackerProps {
    metrics: InnovationMetrics;
    ideas: Idea[];
    experiments: Experiment[];
    results: ExperimentResult[];
    learnings: Learning[];
    onIdeaClick?: (ideaId: string) => void;
    onExperimentClick?: (experimentId: string) => void;
    onResultClick?: (resultId: string) => void;
    onLearningClick?: (learningId: string) => void;
    onSubmitIdea?: () => void;
    onVoteIdea?: (ideaId: string) => void;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get color for idea status
 */
const getIdeaStatusColor = (status: string): 'success' | 'warning' | 'primary' | 'error' | 'default' => {
    switch (status) {
        case 'new':
            return 'primary';
        case 'under-review':
            return 'warning';
        case 'approved':
            return 'success';
        case 'rejected':
            return 'error';
        default:
            return 'default';
    }
};

/**
 * Get color for experiment status
 */
const getExperimentStatusColor = (status: string): 'success' | 'warning' | 'primary' | 'default' => {
    switch (status) {
        case 'planning':
            return 'default';
        case 'in-progress':
            return 'primary';
        case 'analyzing':
            return 'warning';
        default:
            return 'default';
    }
};

/**
 * Get color for outcome
 */
const getOutcomeColor = (outcome: string): 'success' | 'error' | 'warning' | 'default' => {
    switch (outcome) {
        case 'success':
            return 'success';
        case 'failure':
            return 'error';
        case 'pivot':
            return 'warning';
        default:
            return 'default';
    }
};

/**
 * Get color for impact level
 */
const getImpactColor = (impact: string): 'error' | 'warning' | 'success' | 'default' => {
    switch (impact) {
        case 'high':
            return 'success'; // High impact is good
        case 'medium':
            return 'warning';
        case 'low':
            return 'default';
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

export const mockInnovationData = {
    metrics: {
        totalIdeas: 142,
        activeExperiments: 18,
        successRate: 65,
        impactValue: '$2.4M',
    } as InnovationMetrics,

    ideas: [
        {
            id: 'idea-1',
            title: 'AI-Powered Code Review Assistant',
            description: 'Implement a local LLM to pre-scan PRs for common style violations and potential bugs before human review.',
            submitter: 'Alex Johnson',
            team: 'Platform Engineering',
            submittedDate: '2024-12-01',
            votes: 45,
            status: 'approved',
            category: 'technology',
            potentialImpact: 'high',
        },
        {
            id: 'idea-2',
            title: 'Async Standups',
            description: 'Switch to text-based async standups to reduce meeting time and accommodate distributed teams.',
            submitter: 'Maria Garcia',
            team: 'Mobile App',
            submittedDate: '2024-12-05',
            votes: 28,
            status: 'under-review',
            category: 'process',
            potentialImpact: 'medium',
        },
        {
            id: 'idea-3',
            title: 'Gamified Bug Bounties',
            description: 'Internal bug bounty program with leaderboards and prizes to improve QA engagement.',
            submitter: 'Sam Smith',
            team: 'QA',
            submittedDate: '2024-12-08',
            votes: 12,
            status: 'new',
            category: 'culture',
            potentialImpact: 'medium',
        },
    ] as Idea[],

    experiments: [
        {
            id: 'exp-1',
            ideaId: 'idea-1',
            title: 'LLM Code Review Pilot',
            hypothesis: 'Using an LLM for pre-review will reduce PR turnaround time by 30%.',
            owner: 'Sarah Chen',
            startDate: '2024-11-15',
            endDate: '2024-12-15',
            progress: 85,
            status: 'analyzing',
            successCriteria: '30% reduction in review time, <5% false positive rate',
            resources: ['GPU Server', '2 Engineers'],
        },
        {
            id: 'exp-2',
            ideaId: 'idea-4',
            title: 'Micro-Frontend Architecture',
            hypothesis: 'Splitting the dashboard into micro-frontends will improve independent deployment frequency.',
            owner: 'James Wilson',
            startDate: '2024-11-01',
            endDate: '2025-01-30',
            progress: 40,
            status: 'in-progress',
            successCriteria: 'Zero downtime deployments, independent release cycles',
            resources: ['DevOps Engineer', 'Frontend Architect'],
        },
    ] as Experiment[],

    results: [
        {
            id: 'res-1',
            experimentId: 'exp-old-1',
            title: 'Redis Caching Layer',
            outcome: 'success',
            impactScore: 9,
            completionDate: '2024-10-15',
            summary: 'Implementing Redis caching reduced API latency by 60% for read-heavy endpoints.',
            nextSteps: 'Roll out to all services',
            metrics: [
                { name: 'Latency', value: '45ms', change: '-60%' },
                { name: 'DB Load', value: '20%', change: '-40%' },
            ],
        },
        {
            id: 'res-2',
            experimentId: 'exp-old-2',
            title: 'Pair Programming Fridays',
            outcome: 'pivot',
            impactScore: 6,
            completionDate: '2024-09-30',
            summary: 'Mandatory pairing was unpopular, but optional pairing sessions showed high engagement.',
            nextSteps: 'Make pairing optional but encouraged',
            metrics: [
                { name: 'Satisfaction', value: '3.5/5', change: '+10%' },
                { name: 'Code Quality', value: 'High', change: '+15%' },
            ],
        },
    ] as ExperimentResult[],

    learnings: [
        {
            id: 'learn-1',
            resultId: 'res-1',
            title: 'Cache Invalidation Strategies',
            category: 'technical',
            insight: 'Time-based expiry is insufficient for user settings; event-based invalidation is required.',
            applicability: ['Backend', 'Data'],
            sharedBy: 'David Kim',
            date: '2024-10-20',
            likes: 34,
        },
        {
            id: 'learn-2',
            resultId: 'res-2',
            title: 'Developer Autonomy in Process',
            category: 'culture',
            insight: 'Top-down process changes face resistance; opt-in pilots generate better buy-in.',
            applicability: ['Management', 'All Teams'],
            sharedBy: 'Michael Rodriguez',
            date: '2024-10-05',
            likes: 56,
        },
    ] as Learning[],
};

// ============================================================================
// Component
// ============================================================================

/**
 * InnovationTracker Component
 */
export const InnovationTracker: React.FC<InnovationTrackerProps> = ({
    metrics,
    ideas,
    experiments,
    results,
    learnings,
    onIdeaClick,
    onExperimentClick,
    onResultClick,
    onLearningClick,
    onSubmitIdea,
    onVoteIdea,
}) => {
    // State
    type InnovationTab = 'ideas' | 'experiments' | 'results' | 'learnings';
    const [selectedTab, setSelectedTab] = useState<InnovationTab>('ideas');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [categoryFilter, setCategoryFilter] = useState<string>('all');

    // Handlers
    const handleTabChange = (value: InnovationTab) => {
        setSelectedTab(value);
    };

    // Filtering
    let filteredIdeas = ideas;
    if (statusFilter !== 'all') {
        filteredIdeas = filteredIdeas.filter((idea) => idea.status === statusFilter);
    }
    if (categoryFilter !== 'all') {
        filteredIdeas = filteredIdeas.filter((idea) => idea.category === categoryFilter);
    }

    return (
        <Box className="p-6">
            {/* Header */}
            <Box className="mb-6">
                <Box className="flex items-center justify-between mb-2">
                    <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                        Innovation Tracker
                    </Typography>
                    <Button variant="contained" color="primary" onClick={onSubmitIdea}>
                        Submit Idea
                    </Button>
                </Box>
                <Typography variant="body1" className="text-slate-600 dark:text-neutral-400">
                    Track ideas, experiments, and learnings to drive organizational innovation
                </Typography>
            </Box>

            {/* Metrics */}
            <Grid container spacing={3} className="mb-6">
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Total Ideas"
                        value={metrics.totalIdeas.toString()}
                        trend="up"
                        trendValue="15%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Active Experiments"
                        value={metrics.activeExperiments.toString()}
                        trend="neutral"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Success Rate"
                        value={`${metrics.successRate}%`}
                        trend="up"
                        trendValue="5%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Impact Value"
                        value={metrics.impactValue}
                        trend="up"
                        trendValue="High"
                    />
                </Grid>
            </Grid>

            {/* Tabs */}
            <Card>
                <Box className="border-b">
                    <Tabs
                        value={selectedTab}
                        onChange={(value: InnovationTab) => handleTabChange(value)}
                        tabs={[
                            { label: 'Ideas', value: 'ideas' },
                            { label: 'Experiments', value: 'experiments' },
                            { label: 'Results', value: 'results' },
                            { label: 'Learnings', value: 'learnings' },
                        ]}
                    />
                </Box>

                <Box className="p-4">
                    {/* Ideas Tab */}
                    {selectedTab === 'ideas' && (
                        <Box>
                            {/* Filters */}
                            <Box className="mb-4">
                                <Stack direction="row" spacing={2} className="mb-2">
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                            Status
                                        </Typography>
                                        <Stack direction="row" spacing={1}>
                                            {['all', 'new', 'under-review', 'approved'].map((status) => (
                                                <Chip
                                                    key={status}
                                                    label={status.charAt(0).toUpperCase() + status.slice(1).replace('-', ' ')}
                                                    color={statusFilter === status ? 'primary' : 'default'}
                                                    onClick={() => setStatusFilter(status)}
                                                    className="cursor-pointer"
                                                />
                                            ))}
                                        </Stack>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 mb-1">
                                            Category
                                        </Typography>
                                        <Stack direction="row" spacing={1}>
                                            {['all', 'product', 'process', 'technology', 'culture'].map((cat) => (
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
                                </Stack>
                            </Box>

                            {/* Ideas Grid */}
                            <Grid container spacing={3}>
                                {filteredIdeas.map((idea) => (
                                    <Grid item xs={12} md={6} key={idea.id}>
                                        <Card
                                            className="cursor-pointer hover:shadow-md transition-shadow h-full"
                                            onClick={() => onIdeaClick?.(idea.id)}
                                        >
                                            <Box className="p-4">
                                                <Box className="flex items-start justify-between mb-2">
                                                    <Typography variant="h6" className="flex-1 text-slate-900 dark:text-neutral-100">
                                                        {idea.title}
                                                    </Typography>
                                                    <Chip label={idea.status} color={getIdeaStatusColor(idea.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                    {idea.description}
                                                </Typography>
                                                <Box className="mb-3">
                                                    <Stack direction="row" spacing={1}>
                                                        <Chip label={idea.category} size="small" variant="outlined" />
                                                        <Chip label={`Impact: ${idea.potentialImpact}`} size="small" color={getImpactColor(idea.potentialImpact)} variant="outlined" />
                                                    </Stack>
                                                </Box>
                                                <Grid container spacing={2} alignItems="center">
                                                    <Grid item xs={6}>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Submitted by
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                            {idea.submitter}
                                                        </Typography>
                                                    </Grid>
                                                    <Grid item xs={6} className="text-right">
                                                        <Button
                                                            size="small"
                                                            variant="outlined"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                onVoteIdea?.(idea.id);
                                                            }}
                                                        >
                                                            👍 {idea.votes} Votes
                                                        </Button>
                                                    </Grid>
                                                </Grid>
                                            </Box>
                                        </Card>
                                    </Grid>
                                ))}
                            </Grid>
                        </Box>
                    )}

                    {/* Experiments Tab */}
                    {selectedTab === 'experiments' && (
                        <Grid container spacing={3}>
                            {experiments.map((experiment) => (
                                <Grid item xs={12} key={experiment.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onExperimentClick?.(experiment.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center justify-between mb-2">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {experiment.title}
                                                </Typography>
                                                <Chip label={experiment.status} color={getExperimentStatusColor(experiment.status)} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3 italic">
                                                "Hypothesis: {experiment.hypothesis}"
                                            </Typography>
                                            <Box className="mb-3">
                                                <Box className="flex justify-between mb-1">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Progress
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                        {experiment.progress}%
                                                    </Typography>
                                                </Box>
                                                <Progress variant="linear" value={experiment.progress} />
                                            </Box>
                                            <Grid container spacing={2}>
                                                <Grid item xs={12} sm={4}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Owner
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {experiment.owner}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={12} sm={4}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Timeline
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {formatDate(experiment.startDate)} - {formatDate(experiment.endDate)}
                                                    </Typography>
                                                </Grid>
                                                <Grid item xs={12} sm={4}>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                        Resources
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                        {experiment.resources.length} assigned
                                                    </Typography>
                                                </Grid>
                                            </Grid>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Results Tab */}
                    {selectedTab === 'results' && (
                        <Grid container spacing={3}>
                            {results.map((result) => (
                                <Grid item xs={12} md={6} key={result.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow h-full"
                                        onClick={() => onResultClick?.(result.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-center justify-between mb-2">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {result.title}
                                                </Typography>
                                                <Chip label={result.outcome} color={getOutcomeColor(result.outcome)} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-3">
                                                {result.summary}
                                            </Typography>
                                            <Box className="mb-3 bg-slate-50 dark:bg-neutral-800 p-2 rounded">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500 block mb-1">
                                                    Key Metrics
                                                </Typography>
                                                <Grid container spacing={1}>
                                                    {result.metrics.map((metric, idx) => (
                                                        <Grid item xs={6} key={idx}>
                                                            <Box>
                                                                <Typography variant="caption" className="text-slate-700 dark:text-neutral-300 font-medium">
                                                                    {metric.name}
                                                                </Typography>
                                                                <Box className="flex items-center gap-1">
                                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100">
                                                                        {metric.value}
                                                                    </Typography>
                                                                    <Typography variant="caption" className={metric.change.startsWith('+') || metric.change.startsWith('-') ? 'text-green-600' : 'text-slate-500'}>
                                                                        ({metric.change})
                                                                    </Typography>
                                                                </Box>
                                                            </Box>
                                                        </Grid>
                                                    ))}
                                                </Grid>
                                            </Box>
                                            <Box className="flex justify-between items-center">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                    Completed: {formatDate(result.completionDate)}
                                                </Typography>
                                                <Chip label={`Impact: ${result.impactScore}/10`} size="small" variant="outlined" />
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}

                    {/* Learnings Tab */}
                    {selectedTab === 'learnings' && (
                        <Grid container spacing={3}>
                            {learnings.map((learning) => (
                                <Grid item xs={12} key={learning.id}>
                                    <Card
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onLearningClick?.(learning.id)}
                                    >
                                        <Box className="p-4">
                                            <Box className="flex items-start gap-3">
                                                <Box className="text-2xl">💡</Box>
                                                <Box className="flex-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-1">
                                                        {learning.title}
                                                    </Typography>
                                                    <Typography variant="body1" className="text-slate-800 dark:text-neutral-200 mb-2 font-medium">
                                                        "{learning.insight}"
                                                    </Typography>
                                                    <Box className="flex flex-wrap gap-2 mb-2">
                                                        <Chip label={learning.category} size="small" color="primary" variant="outlined" />
                                                        {learning.applicability.map((app) => (
                                                            <Chip key={app} label={`Applies to: ${app}`} size="small" variant="outlined" />
                                                        ))}
                                                    </Box>
                                                    <Box className="flex justify-between items-center mt-2">
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            Shared by {learning.sharedBy} • {formatDate(learning.date)}
                                                        </Typography>
                                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-500">
                                                            ❤️ {learning.likes} likes
                                                        </Typography>
                                                    </Box>
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Card>
                                </Grid>
                            ))}
                        </Grid>
                    )}
                </Box>
            </Card>
        </Box>
    );
};

export default InnovationTracker;
