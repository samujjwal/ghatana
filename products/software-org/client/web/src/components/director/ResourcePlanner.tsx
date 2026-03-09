/**
 * Resource Planner Component
 *
 * Team resource allocation and workload visualization for directors.
 * Enables capacity planning, skill matrix view, and resource conflicts detection.
 *
 * REUSE: Grid, Card, KpiCard, LinearProgress, Chip, Avatar, AvatarGroup from @ghatana/ui
 * PATTERN: Following PortfolioDashboard and TeamOverviewPage patterns
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Box,
    Typography,
    Card,
    CardContent,
    Grid,
    KpiCard,
    Button,
    Chip,
    LinearProgress,
    Avatar,
    AvatarGroup,
    Tabs,
    Tab,
    Alert,
    Tooltip,
    IconButton,
} from '@ghatana/ui';

// ============================================================================
// TypeScript Interfaces
// ============================================================================

/**
 * Team member with allocation and capacity details
 */
interface TeamMember {
    id: string;
    name: string;
    role: string;
    email: string;
    avatar?: string;
    // Capacity metrics
    weeklyHours: number; // Available hours per week
    allocatedHours: number; // Currently allocated hours
    utilization: number; // Percentage (0-100)
    // Skills
    skills: Skill[];
    // Current assignments
    assignments: Assignment[];
    // Availability status
    status: 'available' | 'overallocated' | 'pto' | 'limited';
    nextAvailable?: Date;
}

/**
 * Skill with proficiency level
 */
interface Skill {
    name: string;
    category: 'technical' | 'domain' | 'soft-skill';
    proficiency: number; // 0-100
    certified?: boolean;
}

/**
 * Resource assignment to project
 */
interface Assignment {
    projectId: string;
    projectName: string;
    hoursPerWeek: number;
    startDate: Date;
    endDate: Date;
    role: string;
}

/**
 * Team capacity summary
 */
interface TeamCapacity {
    teamId: string;
    teamName: string;
    totalMembers: number;
    availableHours: number;
    allocatedHours: number;
    utilizationPercent: number;
    overallocatedCount: number;
    availableCount: number;
    members: TeamMember[];
}

/**
 * Skill gap analysis
 */
interface SkillGap {
    skill: string;
    required: number; // Number of people needed
    available: number; // Number of people with skill
    gap: number; // Difference
    priority: 'critical' | 'high' | 'medium' | 'low';
}

/**
 * Resource conflict
 */
interface ResourceConflict {
    id: string;
    memberId: string;
    memberName: string;
    type: 'overallocation' | 'skill-gap' | 'time-overlap';
    severity: 'critical' | 'warning' | 'info';
    description: string;
    affectedProjects: string[];
    suggestedAction: string;
}

/**
 * Component props
 */
export interface ResourcePlannerProps {
    /** Teams to display */
    teams?: TeamCapacity[];
    /** Selected team ID */
    selectedTeamId?: string;
    /** Callback when team is selected */
    onTeamSelect?: (teamId: string) => void;
    /** Callback when resource is allocated */
    onAllocateResource?: (memberId: string, projectId: string, hours: number) => void;
    /** Callback when requesting new resource */
    onRequestResource?: (skill: string, hours: number) => void;
    /** Callback when viewing team member details */
    onViewMember?: (memberId: string) => void;
    /** Callback when resolving conflict */
    onResolveConflict?: (conflictId: string) => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockTeams: TeamCapacity[] = [
    {
        teamId: 'team-backend',
        teamName: 'Backend Engineering',
        totalMembers: 8,
        availableHours: 320, // 8 people * 40 hours
        allocatedHours: 280,
        utilizationPercent: 88,
        overallocatedCount: 1,
        availableCount: 2,
        members: [
            {
                id: 'eng-1',
                name: 'Alice Johnson',
                role: 'Senior Engineer',
                email: 'alice@example.com',
                avatar: 'AJ',
                weeklyHours: 40,
                allocatedHours: 45,
                utilization: 113,
                status: 'overallocated',
                skills: [
                    { name: 'Java', category: 'technical', proficiency: 95, certified: true },
                    { name: 'Spring Boot', category: 'technical', proficiency: 90 },
                    { name: 'Microservices', category: 'technical', proficiency: 85 },
                ],
                assignments: [
                    {
                        projectId: 'proj-1',
                        projectName: 'Platform Modernization',
                        hoursPerWeek: 25,
                        startDate: new Date('2025-01-01'),
                        endDate: new Date('2025-06-30'),
                        role: 'Tech Lead',
                    },
                    {
                        projectId: 'proj-2',
                        projectName: 'Security Enhancement',
                        hoursPerWeek: 20,
                        startDate: new Date('2025-02-01'),
                        endDate: new Date('2025-04-30'),
                        role: 'Security SME',
                    },
                ],
            },
            {
                id: 'eng-2',
                name: 'Bob Smith',
                role: 'Engineer II',
                email: 'bob@example.com',
                avatar: 'BS',
                weeklyHours: 40,
                allocatedHours: 35,
                utilization: 88,
                status: 'available',
                skills: [
                    { name: 'Node.js', category: 'technical', proficiency: 85 },
                    { name: 'TypeScript', category: 'technical', proficiency: 80 },
                    { name: 'REST APIs', category: 'technical', proficiency: 90 },
                ],
                assignments: [
                    {
                        projectId: 'proj-3',
                        projectName: 'Mobile App 2.0',
                        hoursPerWeek: 35,
                        startDate: new Date('2025-01-15'),
                        endDate: new Date('2025-05-15'),
                        role: 'Backend Developer',
                    },
                ],
            },
            {
                id: 'eng-3',
                name: 'Carol Williams',
                role: 'Engineer I',
                email: 'carol@example.com',
                avatar: 'CW',
                weeklyHours: 40,
                allocatedHours: 25,
                utilization: 63,
                status: 'available',
                skills: [
                    { name: 'Python', category: 'technical', proficiency: 75 },
                    { name: 'Django', category: 'technical', proficiency: 70 },
                    { name: 'Data Analysis', category: 'technical', proficiency: 65 },
                ],
                assignments: [
                    {
                        projectId: 'proj-4',
                        projectName: 'Data Analytics Platform',
                        hoursPerWeek: 25,
                        startDate: new Date('2025-02-01'),
                        endDate: new Date('2025-07-31'),
                        role: 'Data Engineer',
                    },
                ],
            },
        ],
    },
    {
        teamId: 'team-frontend',
        teamName: 'Frontend Engineering',
        totalMembers: 6,
        availableHours: 240,
        allocatedHours: 210,
        utilizationPercent: 88,
        overallocatedCount: 0,
        availableCount: 3,
        members: [
            {
                id: 'fe-1',
                name: 'David Lee',
                role: 'Senior Frontend Engineer',
                email: 'david@example.com',
                avatar: 'DL',
                weeklyHours: 40,
                allocatedHours: 38,
                utilization: 95,
                status: 'available',
                skills: [
                    { name: 'React', category: 'technical', proficiency: 95, certified: true },
                    { name: 'TypeScript', category: 'technical', proficiency: 90 },
                    { name: 'UI/UX', category: 'domain', proficiency: 85 },
                ],
                assignments: [
                    {
                        projectId: 'proj-3',
                        projectName: 'Mobile App 2.0',
                        hoursPerWeek: 38,
                        startDate: new Date('2025-01-15'),
                        endDate: new Date('2025-05-15'),
                        role: 'Lead Frontend',
                    },
                ],
            },
        ],
    },
];

const mockSkillGaps: SkillGap[] = [
    {
        skill: 'Kubernetes',
        required: 3,
        available: 1,
        gap: 2,
        priority: 'critical',
    },
    {
        skill: 'React Native',
        required: 4,
        available: 2,
        gap: 2,
        priority: 'high',
    },
    {
        skill: 'GraphQL',
        required: 2,
        available: 1,
        gap: 1,
        priority: 'medium',
    },
];

const mockConflicts: ResourceConflict[] = [
    {
        id: 'conflict-1',
        memberId: 'eng-1',
        memberName: 'Alice Johnson',
        type: 'overallocation',
        severity: 'critical',
        description: 'Allocated 45 hours, only 40 hours available per week',
        affectedProjects: ['Platform Modernization', 'Security Enhancement'],
        suggestedAction: 'Reduce allocation by 5 hours or redistribute work',
    },
    {
        id: 'conflict-2',
        memberId: 'eng-2',
        memberName: 'Bob Smith',
        type: 'skill-gap',
        severity: 'warning',
        description: 'Assigned to mobile backend without React Native experience',
        affectedProjects: ['Mobile App 2.0'],
        suggestedAction: 'Provide React Native training or reassign',
    },
];

// ============================================================================
// Helper Functions
// ============================================================================

const getUtilizationColor = (utilization: number): 'success' | 'warning' | 'error' => {
    if (utilization < 80) return 'success';
    if (utilization <= 100) return 'warning';
    return 'error';
};

const getStatusColor = (status: string) => {
    switch (status) {
        case 'available':
            return 'success';
        case 'overallocated':
            return 'error';
        case 'pto':
            return 'default';
        case 'limited':
            return 'warning';
        default:
            return 'default';
    }
};

const getPriorityColor = (priority: string) => {
    switch (priority) {
        case 'critical':
            return 'error';
        case 'high':
            return 'warning';
        case 'medium':
            return 'info';
        case 'low':
            return 'default';
        default:
            return 'default';
    }
};

const getSeverityColor = (severity: string) => {
    switch (severity) {
        case 'critical':
            return 'error';
        case 'warning':
            return 'warning';
        case 'info':
            return 'info';
        default:
            return 'default';
    }
};

// ============================================================================
// Main Component
// ============================================================================

export const ResourcePlanner: React.FC<ResourcePlannerProps> = ({
    teams = mockTeams,
    selectedTeamId,
    onTeamSelect,
    onAllocateResource,
    onRequestResource,
    onViewMember,
    onResolveConflict,
}) => {
    const [activeTab, setActiveTab] = useState<number>(0);
    const [selectedTeam, setSelectedTeam] = useState<string>(
        selectedTeamId || teams[0]?.teamId || ''
    );

    const currentTeam = teams.find((t) => t.teamId === selectedTeam) || teams[0];

    const handleTeamChange = (teamId: string) => {
        setSelectedTeam(teamId);
        onTeamSelect?.(teamId);
    };

    const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
        setActiveTab(newValue);
    };

    // Calculate overall metrics across all teams
    const totalMembers = teams.reduce((sum, team) => sum + team.totalMembers, 0);
    const totalAvailable = teams.reduce((sum, team) => sum + team.availableHours, 0);
    const totalAllocated = teams.reduce((sum, team) => sum + team.allocatedHours, 0);
    const overallUtilization = totalAvailable > 0 ? (totalAllocated / totalAvailable) * 100 : 0;
    const totalOverallocated = teams.reduce((sum, team) => sum + team.overallocatedCount, 0);

    return (
        <Box className="p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <Typography variant="h4" className="font-bold text-slate-900 dark:text-neutral-100">
                        Resource Planning
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Team capacity, skill matrix, and resource allocation
                    </Typography>
                </div>
                <div className="flex gap-2">
                    <Button
                        variant="outlined"
                        onClick={() => onRequestResource?.('', 0)}
                    >
                        Request Resource
                    </Button>
                    <Button
                        variant="contained"
                        onClick={() => onAllocateResource?.('', '', 0)}
                    >
                        Allocate Resource
                    </Button>
                </div>
            </div>

            {/* Overall Capacity KPIs */}
            <Grid columns={4} gap={3}>
                <KpiCard
                    title="Total Resources"
                    value={totalMembers}
                    unit="people"
                    showProgress={false}
                />
                <KpiCard
                    title="Overall Utilization"
                    value={Math.round(overallUtilization)}
                    unit="%"
                    target={85}
                    trend={{ direction: 'up', value: 5 }}
                    showProgress={true}
                />
                <KpiCard
                    title="Available Hours"
                    value={totalAvailable - totalAllocated}
                    unit="hrs/week"
                    showProgress={false}
                />
                <KpiCard
                    title="Overallocated"
                    value={totalOverallocated}
                    unit="people"
                    showProgress={false}
                />
            </Grid>

            {/* Team Selector */}
            <Card>
                <CardContent>
                    <div className="flex gap-2 overflow-x-auto">
                        {teams.map((team) => (
                            <Chip
                                key={team.teamId}
                                label={`${team.teamName} (${team.totalMembers})`}
                                onClick={() => handleTeamChange(team.teamId)}
                                color={selectedTeam === team.teamId ? 'primary' : 'default'}
                                variant={selectedTeam === team.teamId ? 'filled' : 'outlined'}
                            />
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* Tabs */}
            <Card>
                <Tabs value={activeTab} onChange={handleTabChange}>
                    <Tab label="Team Capacity" />
                    <Tab label="Skill Matrix" />
                    <Tab label="Conflicts" />
                </Tabs>

                <CardContent>
                    {/* Tab 1: Team Capacity */}
                    {activeTab === 0 && (
                        <div className="space-y-4">
                            {/* Team Summary */}
                            <Card variant="outlined">
                                <CardContent>
                                    <Grid columns={4} gap={3}>
                                        <div>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                Team Size
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-slate-900 dark:text-neutral-100">
                                                {currentTeam.totalMembers}
                                            </Typography>
                                        </div>
                                        <div>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                Utilization
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-slate-900 dark:text-neutral-100">
                                                {currentTeam.utilizationPercent}%
                                            </Typography>
                                        </div>
                                        <div>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                Available
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-green-600">
                                                {currentTeam.availableCount}
                                            </Typography>
                                        </div>
                                        <div>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                Overallocated
                                            </Typography>
                                            <Typography variant="h5" className="font-bold text-red-600">
                                                {currentTeam.overallocatedCount}
                                            </Typography>
                                        </div>
                                    </Grid>
                                </CardContent>
                            </Card>

                            {/* Member Cards */}
                            <Grid columns={1} gap={3}>
                                {currentTeam.members.map((member) => (
                                    <Card key={member.id} variant="outlined">
                                        <CardContent>
                                            <div className="flex items-start justify-between mb-4">
                                                <div className="flex items-center gap-3">
                                                    <Avatar>{member.avatar || member.name[0]}</Avatar>
                                                    <div>
                                                        <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                            {member.name}
                                                        </Typography>
                                                        <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                            {member.role}
                                                        </Typography>
                                                    </div>
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <Chip
                                                        label={member.status.replace('-', ' ')}
                                                        color={getStatusColor(member.status)}
                                                        size="small"
                                                    />
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        onClick={() => onViewMember?.(member.id)}
                                                    >
                                                        View Details
                                                    </Button>
                                                </div>
                                            </div>

                                            {/* Capacity Bar */}
                                            <div className="mb-4">
                                                <div className="flex justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Capacity: {member.allocatedHours}h / {member.weeklyHours}h per week
                                                    </Typography>
                                                    <Typography
                                                        variant="body2"
                                                        className={`font-semibold ${member.utilization > 100
                                                                ? 'text-red-600'
                                                                : member.utilization > 80
                                                                    ? 'text-yellow-600'
                                                                    : 'text-green-600'
                                                            }`}
                                                    >
                                                        {member.utilization}%
                                                    </Typography>
                                                </div>
                                                <LinearProgress
                                                    value={Math.min(member.utilization, 100)}
                                                    color={getUtilizationColor(member.utilization)}
                                                />
                                                {member.utilization > 100 && (
                                                    <Typography variant="caption" className="text-red-600 mt-1">
                                                        ⚠️ Overallocated by {member.utilization - 100}%
                                                    </Typography>
                                                )}
                                            </div>

                                            {/* Skills */}
                                            <div className="mb-4">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                    Skills:
                                                </Typography>
                                                <div className="flex flex-wrap gap-2">
                                                    {member.skills.map((skill, idx) => (
                                                        <Tooltip key={idx} title={`Proficiency: ${skill.proficiency}%`}>
                                                            <Chip
                                                                label={skill.name}
                                                                size="small"
                                                                variant={skill.certified ? 'filled' : 'outlined'}
                                                                color={skill.proficiency >= 80 ? 'primary' : 'default'}
                                                            />
                                                        </Tooltip>
                                                    ))}
                                                </div>
                                            </div>

                                            {/* Current Assignments */}
                                            <div>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                    Current Assignments ({member.assignments.length}):
                                                </Typography>
                                                <div className="space-y-2">
                                                    {member.assignments.map((assignment, idx) => (
                                                        <div key={idx} className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-800 rounded">
                                                            <div className="flex-1">
                                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                                    {assignment.projectName}
                                                                </Typography>
                                                                <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                    {assignment.role} • {assignment.hoursPerWeek}h/week
                                                                </Typography>
                                                            </div>
                                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                                {assignment.startDate.toLocaleDateString()} - {assignment.endDate.toLocaleDateString()}
                                                            </Typography>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                ))}
                            </Grid>
                        </div>
                    )}

                    {/* Tab 2: Skill Matrix */}
                    {activeTab === 1 && (
                        <div className="space-y-4">
                            <Alert severity="info">
                                Skill matrix shows team capabilities and identifies gaps
                            </Alert>

                            {/* Skill Gaps */}
                            <div>
                                <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                    Skill Gaps
                                </Typography>
                                <Grid columns={1} gap={2}>
                                    {mockSkillGaps.map((gap, idx) => (
                                        <Card key={idx} variant="outlined">
                                            <CardContent>
                                                <div className="flex items-center justify-between">
                                                    <div className="flex-1">
                                                        <div className="flex items-center gap-2 mb-2">
                                                            <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                {gap.skill}
                                                            </Typography>
                                                            <Chip
                                                                label={gap.priority}
                                                                color={getPriorityColor(gap.priority)}
                                                                size="small"
                                                            />
                                                        </div>
                                                        <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                            Required: {gap.required} • Available: {gap.available} • Gap: {gap.gap}
                                                        </Typography>
                                                    </div>
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        onClick={() => onRequestResource?.(gap.skill, gap.gap)}
                                                    >
                                                        Request Hiring
                                                    </Button>
                                                </div>
                                            </CardContent>
                                        </Card>
                                    ))}
                                </Grid>
                            </div>

                            {/* Skill Coverage by Team */}
                            <div>
                                <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                    Team Skills Coverage
                                </Typography>
                                <Card variant="outlined">
                                    <CardContent>
                                        {currentTeam.members.map((member) => (
                                            <div key={member.id} className="mb-4 last:mb-0">
                                                <div className="flex items-center gap-2 mb-2">
                                                    <Avatar size="small">{member.avatar || member.name[0]}</Avatar>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {member.name}
                                                    </Typography>
                                                </div>
                                                <div className="flex flex-wrap gap-2 ml-10">
                                                    {member.skills.map((skill, idx) => (
                                                        <Chip
                                                            key={idx}
                                                            label={`${skill.name} (${skill.proficiency}%)`}
                                                            size="small"
                                                            color={skill.proficiency >= 80 ? 'primary' : 'default'}
                                                            variant={skill.certified ? 'filled' : 'outlined'}
                                                        />
                                                    ))}
                                                </div>
                                            </div>
                                        ))}
                                    </CardContent>
                                </Card>
                            </div>
                        </div>
                    )}

                    {/* Tab 3: Conflicts */}
                    {activeTab === 2 && (
                        <div className="space-y-4">
                            {mockConflicts.length === 0 ? (
                                <Alert severity="success">
                                    No resource conflicts detected
                                </Alert>
                            ) : (
                                <>
                                    <Alert severity="warning">
                                        {mockConflicts.length} resource conflict(s) require attention
                                    </Alert>

                                    <Grid columns={1} gap={3}>
                                        {mockConflicts.map((conflict) => (
                                            <Card key={conflict.id} variant="outlined">
                                                <CardContent>
                                                    <div className="flex items-start justify-between mb-3">
                                                        <div className="flex-1">
                                                            <div className="flex items-center gap-2 mb-2">
                                                                <Typography variant="h6" className="font-semibold text-slate-900 dark:text-neutral-100">
                                                                    {conflict.memberName}
                                                                </Typography>
                                                                <Chip
                                                                    label={conflict.severity}
                                                                    color={getSeverityColor(conflict.severity)}
                                                                    size="small"
                                                                />
                                                                <Chip
                                                                    label={conflict.type.replace('-', ' ')}
                                                                    size="small"
                                                                    variant="outlined"
                                                                />
                                                            </div>
                                                            <Typography variant="body2" className="text-slate-700 dark:text-neutral-300 mb-2">
                                                                {conflict.description}
                                                            </Typography>
                                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-2">
                                                                Affected Projects: {conflict.affectedProjects.join(', ')}
                                                            </Typography>
                                                            <Alert severity="info" className="mt-2">
                                                                <Typography variant="body2">
                                                                    💡 {conflict.suggestedAction}
                                                                </Typography>
                                                            </Alert>
                                                        </div>
                                                        <div className="flex gap-2 ml-4">
                                                            <Button
                                                                variant="outlined"
                                                                size="small"
                                                                onClick={() => onViewMember?.(conflict.memberId)}
                                                            >
                                                                View Member
                                                            </Button>
                                                            <Button
                                                                variant="contained"
                                                                size="small"
                                                                onClick={() => onResolveConflict?.(conflict.id)}
                                                            >
                                                                Resolve
                                                            </Button>
                                                        </div>
                                                    </div>
                                                </CardContent>
                                            </Card>
                                        ))}
                                    </Grid>
                                </>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
};

export default ResourcePlanner;
