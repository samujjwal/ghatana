import React, { useState } from 'react';
import {
    Box,
    Card,
    Typography,
    Stack,
    Chip,
    Tooltip,
    IconButton,
    Grid,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
} from '@ghatana/ui';

/**
 * Team member allocation
 */
export interface TeamMemberAllocation {
    memberId: string;
    memberName: string;
    role: string;
    weeklyAllocations: WeeklyAllocation[];
}

/**
 * Weekly allocation
 */
export interface WeeklyAllocation {
    weekStart: string; // ISO date
    totalPercentage: number; // 0-150 (can be overallocated)
    projects: ProjectAllocation[];
}

/**
 * Project allocation
 */
export interface ProjectAllocation {
    projectId: string;
    projectName: string;
    percentage: number;
    color?: string;
}

/**
 * Props for ResourceHeatmap
 */
interface ResourceHeatmapProps {
    teamMembers?: TeamMemberAllocation[];
    weeks?: number; // Number of weeks to display (default: 8)
    onCellClick?: (memberId: string, weekStart: string) => void;
    onReassign?: (memberId: string, weekStart: string, projectId: string, newPercentage: number) => void;
}

/**
 * Resource Heatmap
 *
 * Visual calendar-style grid showing team resource allocation:
 * - Rows: Team members
 * - Columns: Weeks (8-12 weeks visible)
 * - Cells: Allocation percentage (color-coded)
 * - Click cell to see project breakdown
 * - Drag-and-drop to reassign (future enhancement)
 * - Conflict warnings for overallocation
 */
export const ResourceHeatmap: React.FC<ResourceHeatmapProps> = ({
    teamMembers: initialTeamMembers,
    weeks = 8,
    onCellClick,
    onReassign,
}) => {
    // Generate week labels
    const generateWeekLabels = (count: number): string[] => {
        const labels: string[] = [];
        const today = new Date();
        const currentWeekStart = new Date(today);
        currentWeekStart.setDate(today.getDate() - today.getDay()); // Sunday

        for (let i = 0; i < count; i++) {
            const weekStart = new Date(currentWeekStart);
            weekStart.setDate(currentWeekStart.getDate() + i * 7);
            labels.push(weekStart.toISOString().split('T')[0]);
        }

        return labels;
    };

    const weekLabels = generateWeekLabels(weeks);

    // Mock data if none provided
    const mockTeamMembers: TeamMemberAllocation[] = initialTeamMembers || [
        {
            memberId: 'member-1',
            memberName: 'Alice Johnson',
            role: 'Senior Engineer',
            weeklyAllocations: weekLabels.map((weekStart, index) => ({
                weekStart,
                totalPercentage: index % 3 === 0 ? 110 : index % 2 === 0 ? 90 : 70,
                projects: [
                    { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 50, color: '#3b82f6' },
                    { projectId: 'proj-b', projectName: 'Project Beta', percentage: index % 3 === 0 ? 60 : 40, color: '#10b981' },
                ],
            })),
        },
        {
            memberId: 'member-2',
            memberName: 'Bob Smith',
            role: 'Engineer II',
            weeklyAllocations: weekLabels.map((weekStart, index) => ({
                weekStart,
                totalPercentage: index % 2 === 0 ? 100 : 80,
                projects: [
                    { projectId: 'proj-b', projectName: 'Project Beta', percentage: 60, color: '#10b981' },
                    { projectId: 'proj-c', projectName: 'Project Gamma', percentage: index % 2 === 0 ? 40 : 20, color: '#f59e0b' },
                ],
            })),
        },
        {
            memberId: 'member-3',
            memberName: 'Carol Williams',
            role: 'Engineer II',
            weeklyAllocations: weekLabels.map((weekStart, index) => ({
                weekStart,
                totalPercentage: 100,
                projects: [
                    { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 70, color: '#3b82f6' },
                    { projectId: 'proj-d', projectName: 'Maintenance', percentage: 30, color: '#6b7280' },
                ],
            })),
        },
        {
            memberId: 'member-4',
            memberName: 'David Brown',
            role: 'Senior Engineer',
            weeklyAllocations: weekLabels.map((weekStart, index) => ({
                weekStart,
                totalPercentage: index % 4 === 0 ? 120 : 95,
                projects: [
                    { projectId: 'proj-c', projectName: 'Project Gamma', percentage: 50, color: '#f59e0b' },
                    { projectId: 'proj-a', projectName: 'Project Alpha', percentage: 45, color: '#3b82f6' },
                    ...(index % 4 === 0 ? [{ projectId: 'proj-d', projectName: 'Meetings', percentage: 25, color: '#6b7280' }] : []),
                ],
            })),
        },
        {
            memberId: 'member-5',
            memberName: 'Eve Davis',
            role: 'Engineer I',
            weeklyAllocations: weekLabels.map((weekStart) => ({
                weekStart,
                totalPercentage: 80,
                projects: [
                    { projectId: 'proj-b', projectName: 'Project Beta', percentage: 50, color: '#10b981' },
                    { projectId: 'learning', projectName: 'Learning & Development', percentage: 30, color: '#8b5cf6' },
                ],
            })),
        },
    ];

    const [teamMembers] = useState<TeamMemberAllocation[]>(mockTeamMembers);
    const [selectedCell, setSelectedCell] = useState<{ memberId: string; weekStart: string } | null>(null);
    const [viewMode, setViewMode] = useState<'percentage' | 'projects'>('percentage');

    // Get cell color based on allocation
    const getCellColor = (percentage: number): string => {
        if (percentage > 100) return '#ef4444'; // Red - overallocated
        if (percentage >= 80) return '#fbbf24'; // Yellow - full
        if (percentage >= 50) return '#10b981'; // Green - healthy
        return '#3b82f6'; // Blue - underutilized
    };

    // Get cell opacity based on allocation
    const getCellOpacity = (percentage: number): number => {
        if (percentage === 0) return 0.1;
        if (percentage < 50) return 0.3;
        if (percentage < 80) return 0.6;
        if (percentage <= 100) return 0.9;
        return 1;
    };

    // Handle cell click
    const handleCellClick = (memberId: string, weekStart: string) => {
        setSelectedCell({ memberId, weekStart });
        onCellClick?.(memberId, weekStart);
    };

    // Format week label
    const formatWeekLabel = (dateString: string): string => {
        const date = new Date(dateString);
        const month = date.toLocaleDateString('en-US', { month: 'short' });
        const day = date.getDate();
        return `${month} ${day}`;
    };

    // Get allocation details for selected cell
    const getSelectedAllocation = (): WeeklyAllocation | null => {
        if (!selectedCell) return null;
        const member = teamMembers.find((m) => m.memberId === selectedCell.memberId);
        if (!member) return null;
        return member.weeklyAllocations.find((w) => w.weekStart === selectedCell.weekStart) || null;
    };

    const selectedAllocation = getSelectedAllocation();

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Typography variant="h5">Resource Allocation</Typography>
                <Stack direction="row" spacing={2} alignItems="center">
                    <Stack direction="row" spacing={1} alignItems="center">
                        <Box sx={{ width: 16, height: 16, bgcolor: '#3b82f6', opacity: 0.6, borderRadius: 0.5 }} />
                        <Typography variant="caption">0-50%</Typography>
                        <Box sx={{ width: 16, height: 16, bgcolor: '#10b981', opacity: 0.6, borderRadius: 0.5 }} />
                        <Typography variant="caption">50-80%</Typography>
                        <Box sx={{ width: 16, height: 16, bgcolor: '#fbbf24', opacity: 0.9, borderRadius: 0.5 }} />
                        <Typography variant="caption">80-100%</Typography>
                        <Box sx={{ width: 16, height: 16, bgcolor: '#ef4444', borderRadius: 0.5 }} />
                        <Typography variant="caption">&gt;100%</Typography>
                    </Stack>
                    <FormControl size="small" sx={{ minWidth: 150 }}>
                        <InputLabel>View</InputLabel>
                        <Select
                            value={viewMode}
                            label="View"
                            onChange={(e) => setViewMode(e.target.value as 'percentage' | 'projects')}
                        >
                            <MenuItem value="percentage">Percentage</MenuItem>
                            <MenuItem value="projects">Projects</MenuItem>
                        </Select>
                    </FormControl>
                </Stack>
            </Stack>

            <Grid container spacing={2}>
                <Grid item xs={12} lg={selectedCell ? 8 : 12}>
                    <Card sx={{ p: 2, overflowX: 'auto' }}>
                        <Box sx={{ minWidth: 800 }}>
                            {/* Header Row */}
                            <Grid container spacing={0.5}>
                                <Grid item xs={2}>
                                    <Box sx={{ p: 1, fontWeight: 'bold' }}>
                                        <Typography variant="subtitle2">Team Member</Typography>
                                    </Box>
                                </Grid>
                                {weekLabels.map((weekStart) => (
                                    <Grid item xs={10 / weeks} key={weekStart}>
                                        <Box
                                            sx={{
                                                p: 1,
                                                textAlign: 'center',
                                                borderLeft: 1,
                                                borderColor: 'divider',
                                            }}
                                        >
                                            <Typography variant="caption" fontWeight="bold">
                                                {formatWeekLabel(weekStart)}
                                            </Typography>
                                        </Box>
                                    </Grid>
                                ))}
                            </Grid>

                            {/* Team Member Rows */}
                            {teamMembers.map((member) => (
                                <Grid container spacing={0.5} key={member.memberId} sx={{ mt: 0.5 }}>
                                    <Grid item xs={2}>
                                        <Box
                                            sx={{
                                                p: 1,
                                                height: '100%',
                                                display: 'flex',
                                                flexDirection: 'column',
                                                justifyContent: 'center',
                                            }}
                                        >
                                            <Typography variant="body2" fontWeight="medium">
                                                {member.memberName}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                                {member.role}
                                            </Typography>
                                        </Box>
                                    </Grid>
                                    {member.weeklyAllocations.map((allocation) => (
                                        <Grid item xs={10 / weeks} key={allocation.weekStart}>
                                            <Tooltip
                                                title={
                                                    <Box>
                                                        <Typography variant="caption" fontWeight="bold">
                                                            {allocation.totalPercentage}% allocated
                                                        </Typography>
                                                        {allocation.projects.map((proj) => (
                                                            <Box key={proj.projectId}>
                                                                <Typography variant="caption">
                                                                    {proj.projectName}: {proj.percentage}%
                                                                </Typography>
                                                            </Box>
                                                        ))}
                                                    </Box>
                                                }
                                            >
                                                <Box
                                                    onClick={() => handleCellClick(member.memberId, allocation.weekStart)}
                                                    sx={{
                                                        p: 1,
                                                        height: 60,
                                                        bgcolor: getCellColor(allocation.totalPercentage),
                                                        opacity: getCellOpacity(allocation.totalPercentage),
                                                        borderLeft: 1,
                                                        borderColor: 'divider',
                                                        cursor: 'pointer',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        '&:hover': {
                                                            opacity: 1,
                                                            boxShadow: 2,
                                                        },
                                                        ...(selectedCell?.memberId === member.memberId &&
                                                            selectedCell?.weekStart === allocation.weekStart
                                                            ? { outline: '2px solid', outlineColor: 'primary.main' }
                                                            : {}),
                                                    }}
                                                >
                                                    {viewMode === 'percentage' ? (
                                                        <Typography variant="body2" fontWeight="bold" color="white">
                                                            {allocation.totalPercentage}%
                                                        </Typography>
                                                    ) : (
                                                        <Stack spacing={0.5} sx={{ width: '100%' }}>
                                                            {allocation.projects.slice(0, 2).map((proj) => (
                                                                <Box
                                                                    key={proj.projectId}
                                                                    sx={{
                                                                        height: 4,
                                                                        bgcolor: proj.color || '#fff',
                                                                        borderRadius: 1,
                                                                        width: `${(proj.percentage / allocation.totalPercentage) * 100}%`,
                                                                    }}
                                                                />
                                                            ))}
                                                        </Stack>
                                                    )}
                                                </Box>
                                            </Tooltip>
                                        </Grid>
                                    ))}
                                </Grid>
                            ))}
                        </Box>
                    </Card>
                </Grid>

                {/* Details Panel */}
                {selectedCell && selectedAllocation && (
                    <Grid item xs={12} lg={4}>
                        <Card sx={{ p: 3, position: 'sticky', top: 20 }}>
                            <Stack spacing={2}>
                                <Box>
                                    <Typography variant="h6" gutterBottom>
                                        Allocation Details
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        {teamMembers.find((m) => m.memberId === selectedCell.memberId)?.memberName}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        Week of {formatWeekLabel(selectedCell.weekStart)}
                                    </Typography>
                                </Box>

                                <Box
                                    sx={{
                                        p: 2,
                                        bgcolor: getCellColor(selectedAllocation.totalPercentage),
                                        opacity: 0.9,
                                        borderRadius: 1,
                                        color: 'white',
                                    }}
                                >
                                    <Typography variant="h4">{selectedAllocation.totalPercentage}%</Typography>
                                    <Typography variant="caption">
                                        {selectedAllocation.totalPercentage > 100
                                            ? 'Overallocated'
                                            : selectedAllocation.totalPercentage >= 80
                                                ? 'Fully Allocated'
                                                : selectedAllocation.totalPercentage >= 50
                                                    ? 'Healthy'
                                                    : 'Underutilized'}
                                    </Typography>
                                </Box>

                                <Box>
                                    <Typography variant="subtitle2" gutterBottom>
                                        Project Breakdown
                                    </Typography>
                                    <Stack spacing={1}>
                                        {selectedAllocation.projects.map((proj) => (
                                            <Box
                                                key={proj.projectId}
                                                sx={{
                                                    p: 1.5,
                                                    border: 1,
                                                    borderColor: 'divider',
                                                    borderRadius: 1,
                                                    borderLeft: 4,
                                                    borderLeftColor: proj.color || 'primary.main',
                                                }}
                                            >
                                                <Stack direction="row" justifyContent="space-between" alignItems="center">
                                                    <Typography variant="body2">{proj.projectName}</Typography>
                                                    <Chip label={`${proj.percentage}%`} size="small" />
                                                </Stack>
                                            </Box>
                                        ))}
                                    </Stack>
                                </Box>

                                {selectedAllocation.totalPercentage > 100 && (
                                    <Box
                                        sx={{
                                            p: 2,
                                            bgcolor: 'error.light',
                                            borderRadius: 1,
                                        }}
                                    >
                                        <Typography variant="body2" fontWeight="bold" color="error.dark">
                                            ⚠️ Overallocation Warning
                                        </Typography>
                                        <Typography variant="caption" color="error.dark">
                                            This team member is overallocated by{' '}
                                            {selectedAllocation.totalPercentage - 100}%. Consider reassigning work or
                                            requesting additional resources.
                                        </Typography>
                                    </Box>
                                )}

                                <IconButton
                                    size="small"
                                    onClick={() => setSelectedCell(null)}
                                    sx={{ alignSelf: 'flex-end' }}
                                >
                                    ✕
                                </IconButton>
                            </Stack>
                        </Card>
                    </Grid>
                )}
            </Grid>
        </Box>
    );
};
