import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    TextField,
    LinearProgress,
    Chip,
    Grid,
    Avatar,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    ListItemButton,
    Divider,
    InputAdornment,
} from '@ghatana/ui';

/**
 * Review cycle
 */
export interface ReviewCycle {
    id: string;
    name: string;
    startDate: string;
    endDate: string;
    dueDate: string;
    status: 'upcoming' | 'active' | 'completed';
}

/**
 * Employee review status
 */
export interface EmployeeReviewStatus {
    employeeId: string;
    employeeName: string;
    employeeEmail: string;
    employeeRole: string;
    avatarUrl?: string;
    reviewId?: string;
    status: 'not-started' | 'in-progress' | 'completed';
    progress: number; // 0-100
    lastUpdated?: string;
    dueDate: string;
    overallRating?: number; // 1-5
}

/**
 * Review metrics
 */
export interface ReviewMetrics {
    totalReviews: number;
    completedReviews: number;
    inProgressReviews: number;
    notStartedReviews: number;
    averageRating?: number;
    daysRemaining: number;
    onTimeRate: number; // Percentage
}

/**
 * Props for PerformanceReviewDashboard
 */
interface PerformanceReviewDashboardProps {
    cycles?: ReviewCycle[];
    selectedCycleId?: string;
    onCycleChange?: (cycleId: string) => void;
    employeeReviews?: EmployeeReviewStatus[];
    metrics?: ReviewMetrics;
    onStartReview?: (employeeId: string) => void;
    onContinueReview?: (reviewId: string) => void;
    onViewReview?: (reviewId: string) => void;
}

/**
 * Performance Review Dashboard
 *
 * Manager dashboard for tracking team performance reviews:
 * - Review cycle selection
 * - Employee review status list
 * - Progress tracking
 * - Metrics summary
 * - Quick actions (start/continue/view review)
 */
export const PerformanceReviewDashboard: React.FC<PerformanceReviewDashboardProps> = ({
    cycles: initialCycles,
    selectedCycleId: initialSelectedCycleId,
    onCycleChange,
    employeeReviews: initialEmployeeReviews,
    metrics: initialMetrics,
    onStartReview,
    onContinueReview,
    onViewReview,
}) => {
    // Mock data if none provided
    const mockCycles: ReviewCycle[] = initialCycles || [
        {
            id: 'cycle-q1-2025',
            name: 'Q1 2025',
            startDate: '2025-01-01',
            endDate: '2025-03-31',
            dueDate: '2025-04-15',
            status: 'active',
        },
        {
            id: 'cycle-q4-2024',
            name: 'Q4 2024',
            startDate: '2024-10-01',
            endDate: '2024-12-31',
            dueDate: '2025-01-15',
            status: 'completed',
        },
        {
            id: 'cycle-q3-2024',
            name: 'Q3 2024',
            startDate: '2024-07-01',
            endDate: '2024-09-30',
            dueDate: '2024-10-15',
            status: 'completed',
        },
    ];

    const mockEmployeeReviews: EmployeeReviewStatus[] = initialEmployeeReviews || [
        {
            employeeId: 'emp-1',
            employeeName: 'Alice Johnson',
            employeeEmail: 'alice@company.com',
            employeeRole: 'Senior Software Engineer',
            reviewId: 'review-1',
            status: 'completed',
            progress: 100,
            lastUpdated: '2025-03-28',
            dueDate: '2025-04-15',
            overallRating: 4.5,
        },
        {
            employeeId: 'emp-2',
            employeeName: 'Bob Smith',
            employeeEmail: 'bob@company.com',
            employeeRole: 'Software Engineer II',
            reviewId: 'review-2',
            status: 'in-progress',
            progress: 60,
            lastUpdated: '2025-04-05',
            dueDate: '2025-04-15',
        },
        {
            employeeId: 'emp-3',
            employeeName: 'Carol Williams',
            employeeEmail: 'carol@company.com',
            employeeRole: 'Software Engineer II',
            reviewId: 'review-3',
            status: 'in-progress',
            progress: 30,
            lastUpdated: '2025-04-01',
            dueDate: '2025-04-15',
        },
        {
            employeeId: 'emp-4',
            employeeName: 'David Brown',
            employeeEmail: 'david@company.com',
            employeeRole: 'Senior Software Engineer',
            status: 'not-started',
            progress: 0,
            dueDate: '2025-04-15',
        },
        {
            employeeId: 'emp-5',
            employeeName: 'Eve Davis',
            employeeEmail: 'eve@company.com',
            employeeRole: 'Software Engineer I',
            status: 'not-started',
            progress: 0,
            dueDate: '2025-04-15',
        },
        {
            employeeId: 'emp-6',
            employeeName: 'Frank Miller',
            employeeEmail: 'frank@company.com',
            employeeRole: 'Staff Software Engineer',
            reviewId: 'review-6',
            status: 'completed',
            progress: 100,
            lastUpdated: '2025-03-25',
            dueDate: '2025-04-15',
            overallRating: 4.8,
        },
        {
            employeeId: 'emp-7',
            employeeName: 'Grace Lee',
            employeeEmail: 'grace@company.com',
            employeeRole: 'Software Engineer II',
            reviewId: 'review-7',
            status: 'completed',
            progress: 100,
            lastUpdated: '2025-03-30',
            dueDate: '2025-04-15',
            overallRating: 4.2,
        },
        {
            employeeId: 'emp-8',
            employeeName: 'Henry Wilson',
            employeeEmail: 'henry@company.com',
            employeeRole: 'Senior Software Engineer',
            reviewId: 'review-8',
            status: 'in-progress',
            progress: 75,
            lastUpdated: '2025-04-08',
            dueDate: '2025-04-15',
        },
    ];

    const calculateMetrics = (reviews: EmployeeReviewStatus[]): ReviewMetrics => {
        const completed = reviews.filter((r) => r.status === 'completed').length;
        const inProgress = reviews.filter((r) => r.status === 'in-progress').length;
        const notStarted = reviews.filter((r) => r.status === 'not-started').length;
        const completedWithRatings = reviews.filter((r) => r.overallRating !== undefined);
        const avgRating =
            completedWithRatings.length > 0
                ? completedWithRatings.reduce((sum, r) => sum + (r.overallRating || 0), 0) /
                completedWithRatings.length
                : undefined;

        const now = new Date();
        const dueDate = new Date('2025-04-15');
        const daysRemaining = Math.ceil((dueDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
        const onTimeRate = reviews.length > 0 ? (completed / reviews.length) * 100 : 0;

        return {
            totalReviews: reviews.length,
            completedReviews: completed,
            inProgressReviews: inProgress,
            notStartedReviews: notStarted,
            averageRating: avgRating,
            daysRemaining,
            onTimeRate,
        };
    };

    const [selectedCycleId, setSelectedCycleId] = useState<string>(
        initialSelectedCycleId || mockCycles[0].id
    );
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState<string>('all');

    const employeeReviews = initialEmployeeReviews || mockEmployeeReviews;
    const metrics = initialMetrics || calculateMetrics(employeeReviews);

    // Filter employees based on search and status
    const filteredEmployees = employeeReviews.filter((emp) => {
        const matchesSearch =
            emp.employeeName.toLowerCase().includes(searchQuery.toLowerCase()) ||
            emp.employeeEmail.toLowerCase().includes(searchQuery.toLowerCase()) ||
            emp.employeeRole.toLowerCase().includes(searchQuery.toLowerCase());

        const matchesFilter = filterStatus === 'all' || emp.status === filterStatus;

        return matchesSearch && matchesFilter;
    });

    // Status badge color
    const getStatusColor = (status: EmployeeReviewStatus['status']): string => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'in-progress':
                return 'warning';
            case 'not-started':
                return 'error';
            default:
                return 'default';
        }
    };

    // Status label
    const getStatusLabel = (status: EmployeeReviewStatus['status']): string => {
        switch (status) {
            case 'completed':
                return 'Completed';
            case 'in-progress':
                return 'In Progress';
            case 'not-started':
                return 'Not Started';
            default:
                return status;
        }
    };

    // Get initials from name
    const getInitials = (name: string): string => {
        return name
            .split(' ')
            .map((part) => part[0])
            .join('')
            .toUpperCase()
            .substring(0, 2);
    };

    // Handle cycle change
    const handleCycleChange = (cycleId: string) => {
        setSelectedCycleId(cycleId);
        onCycleChange?.(cycleId);
    };

    // Handle employee click
    const handleEmployeeClick = (emp: EmployeeReviewStatus) => {
        if (emp.status === 'completed' && emp.reviewId) {
            onViewReview?.(emp.reviewId);
        } else if (emp.status === 'in-progress' && emp.reviewId) {
            onContinueReview?.(emp.reviewId);
        } else {
            onStartReview?.(emp.employeeId);
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    };

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Typography variant="h4">Performance Reviews</Typography>
                <FormControl sx={{ minWidth: 200 }}>
                    <InputLabel>Review Cycle</InputLabel>
                    <Select
                        value={selectedCycleId}
                        label="Review Cycle"
                        onChange={(e) => handleCycleChange(e.target.value)}
                    >
                        {mockCycles.map((cycle) => (
                            <MenuItem key={cycle.id} value={cycle.id}>
                                {cycle.name}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </Stack>

            {/* Metrics Summary */}
            <Grid container spacing={3} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Reviews Completed
                        </Typography>
                        <Typography variant="h4">
                            {metrics.completedReviews}/{metrics.totalReviews}
                        </Typography>
                        <LinearProgress
                            variant="determinate"
                            value={(metrics.completedReviews / metrics.totalReviews) * 100}
                            sx={{ mt: 1 }}
                        />
                        <Typography variant="caption" color="text.secondary">
                            {((metrics.completedReviews / metrics.totalReviews) * 100).toFixed(0)}% complete
                        </Typography>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Due In
                        </Typography>
                        <Typography variant="h4">{metrics.daysRemaining} days</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                            {formatDate('2025-04-15')}
                        </Typography>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Average Rating
                        </Typography>
                        <Typography variant="h4">
                            {metrics.averageRating ? metrics.averageRating.toFixed(1) : '—'}
                            {metrics.averageRating && (
                                <Typography component="span" variant="h6" color="text.secondary">
                                    /5.0
                                </Typography>
                            )}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                            {metrics.completedReviews} reviews
                        </Typography>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            In Progress
                        </Typography>
                        <Typography variant="h4">{metrics.inProgressReviews}</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                            {metrics.notStartedReviews} not started
                        </Typography>
                    </Card>
                </Grid>
            </Grid>

            {/* Filters */}
            <Card sx={{ p: 2, mb: 3 }}>
                <Grid container spacing={2} alignItems="center">
                    <Grid item xs={12} sm={6} md={4}>
                        <TextField
                            fullWidth
                            placeholder="Search employees..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            InputProps={{
                                startAdornment: (
                                    <InputAdornment position="start">🔍</InputAdornment>
                                ),
                            }}
                        />
                    </Grid>
                    <Grid item xs={12} sm={6} md={3}>
                        <FormControl fullWidth>
                            <InputLabel>Status</InputLabel>
                            <Select
                                value={filterStatus}
                                label="Status"
                                onChange={(e) => setFilterStatus(e.target.value)}
                            >
                                <MenuItem value="all">All Statuses</MenuItem>
                                <MenuItem value="not-started">Not Started</MenuItem>
                                <MenuItem value="in-progress">In Progress</MenuItem>
                                <MenuItem value="completed">Completed</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                </Grid>
            </Card>

            {/* Employee Review List */}
            <Card>
                <List>
                    {filteredEmployees.length === 0 ? (
                        <ListItem>
                            <ListItemText
                                primary="No employees found"
                                secondary="Try adjusting your search or filter criteria"
                            />
                        </ListItem>
                    ) : (
                        filteredEmployees.map((emp, index) => (
                            <React.Fragment key={emp.employeeId}>
                                {index > 0 && <Divider />}
                                <ListItem
                                    disablePadding
                                    secondaryAction={
                                        <Stack direction="row" spacing={1} alignItems="center">
                                            {emp.status === 'completed' && emp.overallRating && (
                                                <Chip label={`${emp.overallRating.toFixed(1)} ⭐`} color="success" />
                                            )}
                                            <Chip
                                                label={getStatusLabel(emp.status)}
                                                color={getStatusColor(emp.status) as any}
                                                size="small"
                                            />
                                            <Button
                                                variant={emp.status === 'not-started' ? 'contained' : 'outlined'}
                                                size="small"
                                                onClick={() => handleEmployeeClick(emp)}
                                            >
                                                {emp.status === 'completed'
                                                    ? 'View'
                                                    : emp.status === 'in-progress'
                                                        ? 'Continue'
                                                        : 'Start Review'}
                                            </Button>
                                        </Stack>
                                    }
                                >
                                    <ListItemButton onClick={() => handleEmployeeClick(emp)}>
                                        <ListItemAvatar>
                                            <Avatar src={emp.avatarUrl} alt={emp.employeeName}>
                                                {getInitials(emp.employeeName)}
                                            </Avatar>
                                        </ListItemAvatar>
                                        <ListItemText
                                            primary={emp.employeeName}
                                            secondary={
                                                <Stack spacing={0.5}>
                                                    <Typography variant="body2" color="text.secondary">
                                                        {emp.employeeRole}
                                                    </Typography>
                                                    {emp.status !== 'not-started' && (
                                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                            <LinearProgress
                                                                variant="determinate"
                                                                value={emp.progress}
                                                                sx={{ flex: 1, height: 6, borderRadius: 1 }}
                                                            />
                                                            <Typography variant="caption" color="text.secondary">
                                                                {emp.progress}%
                                                            </Typography>
                                                        </Box>
                                                    )}
                                                    {emp.lastUpdated && (
                                                        <Typography variant="caption" color="text.secondary">
                                                            Last updated: {formatDate(emp.lastUpdated)}
                                                        </Typography>
                                                    )}
                                                </Stack>
                                            }
                                        />
                                    </ListItemButton>
                                </ListItem>
                            </React.Fragment>
                        ))
                    )}
                </List>
            </Card>
        </Box>
    );
};
