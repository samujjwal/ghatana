/**
 * Resource Allocation Component
 *
 * Component for viewing and managing resource allocation across teams,
 * tracking capacity, skill distribution, and availability.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    Box,
    Chip,
    Tabs,
    Tab,
    Button,
    Typography,
    Stack,
    LinearProgress,
} from '@ghatana/design-system';

/**
 * Resource allocation metrics
 */
export interface ResourceMetrics {
    totalResources: number;
    availableResources: number;
    utilizationRate: number; // 0-100
    avgAllocationDays: number;
}

/**
 * Team resource allocation
 */
export interface TeamResource {
    id: string;
    teamName: string;
    department: string;
    headCount: number;
    availableCount: number;
    utilization: number; // 0-100
    capacity: 'over-capacity' | 'at-capacity' | 'under-capacity' | 'available';
    primarySkills: string[];
    ongoingProjects: number;
    avgProjectDuration: number; // days
}

/**
 * Individual resource
 */
export interface IndividualResource {
    id: string;
    name: string;
    role: string;
    layer: 'ic' | 'manager' | 'director' | 'vp' | 'cxo' | 'root' | 'agent';
    team: string;
    skills: string[];
    availability: 'available' | 'partially-available' | 'allocated' | 'unavailable';
    currentAllocation: number; // 0-100
    nextAvailable: string; // ISO date
    hourlyRate?: number;
}

/**
 * Skill distribution
 */
export interface SkillDistribution {
    id: string;
    skillName: string;
    category: 'technical' | 'design' | 'product' | 'management' | 'data';
    totalPeople: number;
    availablePeople: number;
    expertiseLevel: 'beginner' | 'intermediate' | 'advanced' | 'expert';
    demandScore: number; // 0-100
    supplyScore: number; // 0-100
}

/**
 * Resource request
 */
export interface ResourceRequest {
    id: string;
    projectName: string;
    requestedBy: string;
    requiredSkills: string[];
    requiredCount: number;
    startDate: string;
    duration: number; // days
    priority: 'urgent' | 'high' | 'normal' | 'low';
    status: 'pending' | 'approved' | 'fulfilled' | 'rejected';
    matchedResources?: string[];
}

/**
 * Resource Allocation Props
 */
export interface ResourceAllocationProps {
    /** Resource metrics */
    metrics: ResourceMetrics;
    /** Team resources */
    teamResources: TeamResource[];
    /** Individual resources */
    individualResources: IndividualResource[];
    /** Skill distribution */
    skillDistribution: SkillDistribution[];
    /** Resource requests */
    resourceRequests: ResourceRequest[];
    /** Callback when team is clicked */
    onTeamClick?: (teamId: string) => void;
    /** Callback when resource is clicked */
    onResourceClick?: (resourceId: string) => void;
    /** Callback when skill is clicked */
    onSkillClick?: (skillId: string) => void;
    /** Callback when request is clicked */
    onRequestClick?: (requestId: string) => void;
    /** Callback when allocate resource is clicked */
    onAllocateResource?: () => void;
}

/**
 * Resource Allocation Component
 *
 * Provides comprehensive resource allocation management with:
 * - Resource summary metrics
 * - Team capacity tracking with utilization rates
 * - Individual resource availability and skills
 * - Skill distribution and gap analysis
 * - Resource request management
 * - Tab-based navigation (Teams, People, Skills, Requests)
 *
 * Reuses @ghatana/design-system components:
 * - Card (team cards, resource cards, skill cards)
 * - LinearProgress (utilization, allocation, demand/supply bars)
 * - Chip (capacity, availability, skill, status indicators)
 *
 * @example
 * ```tsx
 * <ResourceAllocation
 *   metrics={resourceMetrics}
 *   teamResources={teams}
 *   individualResources={people}
 *   skillDistribution={skills}
 *   resourceRequests={requests}
 *   onTeamClick={(id) => navigate(`/teams/${id}`)}
 *   onAllocateResource={() => openAllocationDialog()}
 * />
 * ```
 */
export const ResourceAllocation: React.FC<ResourceAllocationProps> = ({
    metrics,
    teamResources,
    individualResources,
    skillDistribution,
    resourceRequests,
    onTeamClick,
    onResourceClick,
    onSkillClick,
    onRequestClick,
    onAllocateResource,
}) => {
    const [selectedTab, setSelectedTab] = useState<'teams' | 'people' | 'skills' | 'requests'>('teams');
    const [capacityFilter, setCapacityFilter] = useState<'all' | 'over-capacity' | 'at-capacity' | 'under-capacity' | 'available'>('all');
    const [availabilityFilter, setAvailabilityFilter] = useState<'all' | 'available' | 'partially-available' | 'allocated' | 'unavailable'>('all');

    // Get capacity color
    const getCapacityColor = (capacity: string): 'error' | 'warning' | 'success' | 'default' => {
        switch (capacity) {
            case 'over-capacity':
                return 'error';
            case 'at-capacity':
                return 'warning';
            case 'under-capacity':
            case 'available':
                return 'success';
            default:
                return 'default';
        }
    };

    // Get availability color
    const getAvailabilityColor = (availability: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (availability) {
            case 'available':
                return 'success';
            case 'partially-available':
                return 'warning';
            case 'allocated':
            case 'unavailable':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get priority color
    const getPriorityColor = (priority: string): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'urgent':
                return 'error';
            case 'high':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'fulfilled':
            case 'approved':
                return 'success';
            case 'pending':
                return 'warning';
            case 'rejected':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get category color
    const getCategoryColor = (category: string): 'error' | 'warning' | 'default' => {
        switch (category) {
            case 'technical':
            case 'data':
                return 'error';
            case 'design':
            case 'product':
                return 'warning';
            default:
                return 'default';
        }
    };

    // Format layer name
    const formatLayerName = (layer: string): string => {
        const layerMap: Record<string, string> = {
            ic: 'Individual Contributor',
            manager: 'Manager',
            director: 'Director',
            vp: 'Vice President',
            cxo: 'Executive',
            root: 'Root',
            agent: 'Agent',
        };
        return layerMap[layer] || layer;
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString();
    };

    // Calculate days until available
    const getDaysUntilAvailable = (nextAvailable: string): number => {
        const date = new Date(nextAvailable);
        const now = new Date();
        const diffMs = date.getTime() - now.getTime();
        return Math.ceil(diffMs / 86400000);
    };

    // Filter teams
    const filteredTeams = capacityFilter === 'all' ? teamResources : teamResources.filter((t) => t.capacity === capacityFilter);

    // Filter people
    const filteredPeople = availabilityFilter === 'all' ? individualResources : individualResources.filter((p) => p.availability === availabilityFilter);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Resource Allocation
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Team capacity, skill distribution, and resource availability
                    </Typography>
                </Box>
                {onAllocateResource && (
                    <Button variant="primary" size="md" onClick={onAllocateResource}>
                        Allocate Resource
                    </Button>
                )}
            </Box>

            {/* Metrics Summary */}
            <Grid columns={4} gap={4}>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Total Resources
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.totalResources}
                        </Typography>
                        <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                            {metrics.availableResources} available
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Utilization Rate
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${metrics.utilizationRate >= 90 ? 'text-red-600' : metrics.utilizationRate >= 75 ? 'text-orange-600' : 'text-green-600'}`}>
                            {metrics.utilizationRate}%
                        </Typography>
                        <Box className="mt-2">
                            <LinearProgress
                                variant="determinate"
                                value={metrics.utilizationRate}
                                color={metrics.utilizationRate >= 90 ? 'error' : metrics.utilizationRate >= 75 ? 'warning' : 'success'}
                            />
                        </Box>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Avg Allocation
                        </Typography>
                        <Typography variant="h5" className="text-slate-900 dark:text-neutral-100 mt-1">
                            {metrics.avgAllocationDays}d
                        </Typography>
                    </Box>
                </Card>
                <Card>
                    <Box className="p-4">
                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                            Pending Requests
                        </Typography>
                        <Typography variant="h5" className={`mt-1 ${resourceRequests.filter((r) => r.status === 'pending').length > 0 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                            {resourceRequests.filter((r) => r.status === 'pending').length}
                        </Typography>
                    </Box>
                </Card>
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Teams (${teamResources.length})`} value="teams" />
                    <Tab label={`People (${individualResources.length})`} value="people" />
                    <Tab label={`Skills (${skillDistribution.length})`} value="skills" />
                    <Tab label={`Requests (${resourceRequests.length})`} value="requests" />
                </Tabs>

                {/* Teams Tab */}
                {selectedTab === 'teams' && (
                    <Box className="p-4">
                        {/* Capacity Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${teamResources.length})`} color={capacityFilter === 'all' ? 'error' : 'default'} onClick={() => setCapacityFilter('all')} />
                            <Chip
                                label={`Over Capacity (${teamResources.filter((t) => t.capacity === 'over-capacity').length})`}
                                color={capacityFilter === 'over-capacity' ? 'error' : 'default'}
                                onClick={() => setCapacityFilter('over-capacity')}
                            />
                            <Chip
                                label={`At Capacity (${teamResources.filter((t) => t.capacity === 'at-capacity').length})`}
                                color={capacityFilter === 'at-capacity' ? 'warning' : 'default'}
                                onClick={() => setCapacityFilter('at-capacity')}
                            />
                            <Chip
                                label={`Under Capacity (${teamResources.filter((t) => t.capacity === 'under-capacity').length})`}
                                color={capacityFilter === 'under-capacity' ? 'success' : 'default'}
                                onClick={() => setCapacityFilter('under-capacity')}
                            />
                        </Stack>

                        {/* Team List */}
                        <Grid columns={2} gap={4}>
                            {filteredTeams.map((team) => (
                                <Card key={team.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onTeamClick?.(team.id)}>
                                    <Box className="p-4">
                                        {/* Team Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {team.teamName}
                                                    </Typography>
                                                    <Chip label={team.capacity} color={getCapacityColor(team.capacity)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {team.department}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Utilization
                                                </Typography>
                                                <Typography
                                                    variant="h6"
                                                    className={team.utilization >= 90 ? 'text-red-600' : team.utilization >= 75 ? 'text-orange-600' : 'text-green-600'}
                                                >
                                                    {team.utilization}%
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Utilization Bar */}
                                        <Box className="mb-3">
                                            <LinearProgress
                                                variant="determinate"
                                                value={team.utilization}
                                                color={team.utilization >= 90 ? 'error' : team.utilization >= 75 ? 'warning' : 'success'}
                                            />
                                        </Box>

                                        {/* Team Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={2}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Team Size
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.headCount} ({team.availableCount} available)
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Projects
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.ongoingProjects}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Avg Duration
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {team.avgProjectDuration}d
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>

                                        {/* Primary Skills */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Primary Skills
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {team.primarySkills.slice(0, 4).map((skill, i) => (
                                                    <Chip key={i} label={skill} size="small" />
                                                ))}
                                                {team.primarySkills.length > 4 && <Chip label={`+${team.primarySkills.length - 4}`} size="small" />}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* People Tab */}
                {selectedTab === 'people' && (
                    <Box className="p-4">
                        {/* Availability Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${individualResources.length})`} color={availabilityFilter === 'all' ? 'error' : 'default'} onClick={() => setAvailabilityFilter('all')} />
                            <Chip
                                label={`Available (${individualResources.filter((p) => p.availability === 'available').length})`}
                                color={availabilityFilter === 'available' ? 'success' : 'default'}
                                onClick={() => setAvailabilityFilter('available')}
                            />
                            <Chip
                                label={`Partially Available (${individualResources.filter((p) => p.availability === 'partially-available').length})`}
                                color={availabilityFilter === 'partially-available' ? 'warning' : 'default'}
                                onClick={() => setAvailabilityFilter('partially-available')}
                            />
                            <Chip
                                label={`Allocated (${individualResources.filter((p) => p.availability === 'allocated').length})`}
                                color={availabilityFilter === 'allocated' ? 'error' : 'default'}
                                onClick={() => setAvailabilityFilter('allocated')}
                            />
                        </Stack>

                        {/* People List */}
                        <Grid columns={3} gap={3}>
                            {filteredPeople.map((person) => (
                                <Card key={person.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onResourceClick?.(person.id)}>
                                    <Box className="p-4">
                                        {/* Person Header */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center gap-2 mb-1">
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {person.name}
                                                </Typography>
                                                <Chip label={person.availability} color={getAvailabilityColor(person.availability)} size="small" />
                                            </Box>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {person.role} • {formatLayerName(person.layer)}
                                            </Typography>
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                {person.team}
                                            </Typography>
                                        </Box>

                                        {/* Allocation Bar */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Current Allocation
                                                </Typography>
                                                <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                    {person.currentAllocation}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={person.currentAllocation}
                                                color={person.currentAllocation >= 100 ? 'error' : person.currentAllocation >= 75 ? 'warning' : 'success'}
                                            />
                                        </Box>

                                        {/* Next Available */}
                                        <Box className="mb-3">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                Next Available
                                            </Typography>
                                            <Typography
                                                variant="body2"
                                                className={`${getDaysUntilAvailable(person.nextAvailable) <= 7 ? 'text-green-600' : getDaysUntilAvailable(person.nextAvailable) <= 30 ? 'text-orange-600' : 'text-slate-900 dark:text-neutral-100'} font-medium`}
                                            >
                                                {getDaysUntilAvailable(person.nextAvailable) === 0
                                                    ? 'Today'
                                                    : getDaysUntilAvailable(person.nextAvailable) < 0
                                                        ? 'Now'
                                                        : `${getDaysUntilAvailable(person.nextAvailable)}d`}
                                            </Typography>
                                        </Box>

                                        {/* Skills */}
                                        <Box className="pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Skills
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {person.skills.slice(0, 3).map((skill, i) => (
                                                    <Chip key={i} label={skill} size="small" />
                                                ))}
                                                {person.skills.length > 3 && <Chip label={`+${person.skills.length - 3}`} size="small" />}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Skills Tab */}
                {selectedTab === 'skills' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Skill Distribution
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {skillDistribution.map((skill) => (
                                <Card key={skill.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onSkillClick?.(skill.id)}>
                                    <Box className="p-4">
                                        {/* Skill Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {skill.skillName}
                                                    </Typography>
                                                    <Chip label={skill.category} color={getCategoryColor(skill.category)} size="small" />
                                                    <Chip label={skill.expertiseLevel} size="small" />
                                                </Box>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    People
                                                </Typography>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {skill.totalPeople}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Supply vs Demand */}
                                        <Box className="space-y-3">
                                            {/* Demand */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Demand
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                        {skill.demandScore}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress variant="determinate" value={skill.demandScore} color="error" />
                                            </Box>

                                            {/* Supply */}
                                            <Box>
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Supply
                                                    </Typography>
                                                    <Typography variant="caption" className="text-slate-900 dark:text-neutral-100">
                                                        {skill.supplyScore}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress variant="determinate" value={skill.supplyScore} color="success" />
                                            </Box>
                                        </Box>

                                        {/* Gap Analysis */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-1 block">
                                                Gap Analysis
                                            </Typography>
                                            {skill.demandScore > skill.supplyScore ? (
                                                <Typography variant="body2" className="text-red-600 font-medium">
                                                    Shortage: {skill.demandScore - skill.supplyScore}% gap
                                                </Typography>
                                            ) : skill.demandScore < skill.supplyScore ? (
                                                <Typography variant="body2" className="text-green-600 font-medium">
                                                    Surplus: {skill.supplyScore - skill.demandScore}% excess
                                                </Typography>
                                            ) : (
                                                <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                    Balanced
                                                </Typography>
                                            )}
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Requests Tab */}
                {selectedTab === 'requests' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Resource Requests
                        </Typography>

                        <Stack spacing={3}>
                            {resourceRequests.map((request) => (
                                <Card key={request.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onRequestClick?.(request.id)}>
                                    <Box className="p-4">
                                        {/* Request Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {request.projectName}
                                                    </Typography>
                                                    <Chip label={request.status} color={getStatusColor(request.status)} size="small" />
                                                    <Chip label={request.priority} color={getPriorityColor(request.priority)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Requested by: {request.requestedBy}
                                                </Typography>
                                            </Box>
                                            <Box className="text-right">
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Resources
                                                </Typography>
                                                <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                    {request.requiredCount}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Request Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={3} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Start Date
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {formatDate(request.startDate)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Duration
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {request.duration}d
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Matched
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-900 dark:text-neutral-100 font-medium">
                                                        {request.matchedResources?.length || 0} / {request.requiredCount}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>

                                        {/* Required Skills */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Typography variant="caption" className="text-slate-500 dark:text-neutral-400 mb-2 block">
                                                Required Skills
                                            </Typography>
                                            <Stack direction="row" spacing={1} className="flex-wrap">
                                                {request.requiredSkills.map((skill, i) => (
                                                    <Chip key={i} label={skill} size="small" />
                                                ))}
                                            </Stack>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockResourceAllocationData = {
    metrics: {
        totalResources: 145,
        availableResources: 28,
        utilizationRate: 81,
        avgAllocationDays: 45,
    } as ResourceMetrics,

    teamResources: [
        {
            id: 'team-1',
            teamName: 'Platform Engineering',
            department: 'Engineering',
            headCount: 22,
            availableCount: 3,
            utilization: 86,
            capacity: 'at-capacity',
            primarySkills: ['Java', 'ActiveJ', 'Kubernetes', 'PostgreSQL', 'Redis'],
            ongoingProjects: 4,
            avgProjectDuration: 60,
        },
        {
            id: 'team-2',
            teamName: 'Product Design',
            department: 'Design',
            headCount: 12,
            availableCount: 5,
            utilization: 58,
            capacity: 'under-capacity',
            primarySkills: ['Figma', 'UX Research', 'Prototyping', 'Design Systems'],
            ongoingProjects: 2,
            avgProjectDuration: 30,
        },
        {
            id: 'team-3',
            teamName: 'Data Science',
            department: 'Analytics',
            headCount: 15,
            availableCount: 1,
            utilization: 93,
            capacity: 'over-capacity',
            primarySkills: ['Python', 'TensorFlow', 'SQL', 'Statistics', 'Machine Learning'],
            ongoingProjects: 5,
            avgProjectDuration: 45,
        },
    ] as TeamResource[],

    individualResources: [
        {
            id: 'res-1',
            name: 'Sarah Chen',
            role: 'Senior Software Engineer',
            layer: 'ic',
            team: 'Platform Engineering',
            skills: ['Java', 'ActiveJ', 'Spring Boot', 'Kubernetes'],
            availability: 'allocated',
            currentAllocation: 100,
            nextAvailable: '2026-01-15T00:00:00Z',
        },
        {
            id: 'res-2',
            name: 'Mike Rodriguez',
            role: 'Product Designer',
            layer: 'ic',
            team: 'Product Design',
            skills: ['Figma', 'UX Research', 'Prototyping'],
            availability: 'available',
            currentAllocation: 0,
            nextAvailable: '2025-12-11T00:00:00Z',
        },
        {
            id: 'res-3',
            name: 'Emily Johnson',
            role: 'Data Scientist',
            layer: 'ic',
            team: 'Data Science',
            skills: ['Python', 'TensorFlow', 'Statistics'],
            availability: 'partially-available',
            currentAllocation: 60,
            nextAvailable: '2025-12-20T00:00:00Z',
        },
        {
            id: 'res-4',
            name: 'David Park',
            role: 'Engineering Manager',
            layer: 'manager',
            team: 'Platform Engineering',
            skills: ['Team Leadership', 'Java', 'Architecture'],
            availability: 'allocated',
            currentAllocation: 80,
            nextAvailable: '2026-01-05T00:00:00Z',
        },
        {
            id: 'res-5',
            name: 'Lisa Thompson',
            role: 'Senior Designer',
            layer: 'ic',
            team: 'Product Design',
            skills: ['Figma', 'Design Systems', 'Accessibility'],
            availability: 'available',
            currentAllocation: 20,
            nextAvailable: '2025-12-12T00:00:00Z',
        },
        {
            id: 'res-6',
            name: 'James Wilson',
            role: 'ML Engineer',
            layer: 'ic',
            team: 'Data Science',
            skills: ['Python', 'PyTorch', 'MLOps'],
            availability: 'partially-available',
            currentAllocation: 75,
            nextAvailable: '2025-12-18T00:00:00Z',
        },
    ] as IndividualResource[],

    skillDistribution: [
        {
            id: 'skill-1',
            skillName: 'Java Development',
            category: 'technical',
            totalPeople: 35,
            availablePeople: 5,
            expertiseLevel: 'advanced',
            demandScore: 85,
            supplyScore: 65,
        },
        {
            id: 'skill-2',
            skillName: 'UX Design',
            category: 'design',
            totalPeople: 18,
            availablePeople: 7,
            expertiseLevel: 'expert',
            demandScore: 60,
            supplyScore: 75,
        },
        {
            id: 'skill-3',
            skillName: 'Machine Learning',
            category: 'data',
            totalPeople: 22,
            availablePeople: 2,
            expertiseLevel: 'advanced',
            demandScore: 90,
            supplyScore: 55,
        },
        {
            id: 'skill-4',
            skillName: 'Product Management',
            category: 'product',
            totalPeople: 12,
            availablePeople: 3,
            expertiseLevel: 'intermediate',
            demandScore: 70,
            supplyScore: 70,
        },
    ] as SkillDistribution[],

    resourceRequests: [
        {
            id: 'req-1',
            projectName: 'AI Analytics Platform',
            requestedBy: 'Sarah Johnson',
            requiredSkills: ['Python', 'Machine Learning', 'FastAPI'],
            requiredCount: 3,
            startDate: '2025-12-20T00:00:00Z',
            duration: 90,
            priority: 'urgent',
            status: 'pending',
            matchedResources: ['res-3'],
        },
        {
            id: 'req-2',
            projectName: 'Design System Overhaul',
            requestedBy: 'Mike Chen',
            requiredSkills: ['Figma', 'Design Systems', 'React'],
            requiredCount: 2,
            startDate: '2026-01-10T00:00:00Z',
            duration: 60,
            priority: 'high',
            status: 'approved',
            matchedResources: ['res-2', 'res-5'],
        },
        {
            id: 'req-3',
            projectName: 'Infrastructure Migration',
            requestedBy: 'David Park',
            requiredSkills: ['Java', 'Kubernetes', 'PostgreSQL'],
            requiredCount: 4,
            startDate: '2026-02-01T00:00:00Z',
            duration: 120,
            priority: 'normal',
            status: 'pending',
        },
    ] as ResourceRequest[],
};
