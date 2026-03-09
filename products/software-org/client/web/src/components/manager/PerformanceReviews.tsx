import React, { useState } from 'react';
import {
    Card,
    CardContent,
    CardHeader,
    Chip,
    Avatar,
    IconButton,
    Tabs,
    Tab,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Rating,
    LinearProgress,
    Alert,
    Divider,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
} from '@ghatana/ui';
import {
    Assessment,
    Add,
    Edit,
    Star,
    TrendingUp,
    TrendingDown,
    CheckCircle,
    Schedule,
    EmojiEvents,
    Flag,
    Person,
    Comment,
} from '@ghatana/ui/icons';

// ============================================================================
// Type Definitions
// ============================================================================

interface PerformanceReview {
    id: string;
    employeeId: string;
    employeeName: string;
    employeeAvatar?: string;
    employeeRole: string;
    reviewPeriod: string; // e.g., "Q4 2024"
    reviewType: 'quarterly' | 'annual' | 'probation' | 'promotion';
    status: 'not_started' | 'in_progress' | 'completed' | 'submitted';
    dueDate: Date;
    completedDate?: Date;
    overallRating?: number; // 1-5
    ratings: {
        technicalSkills?: number;
        communication?: number;
        leadership?: number;
        problemSolving?: number;
        teamwork?: number;
        initiative?: number;
    };
    goals: ReviewGoal[];
    feedback: {
        strengths?: string;
        areasForImprovement?: string;
        careerDevelopment?: string;
    };
    recommendations: {
        promotion?: boolean;
        promotionLevel?: string;
        salaryAdjustment?: number; // percentage
        nextReviewDate?: Date;
    };
}

interface ReviewGoal {
    id: string;
    title: string;
    description?: string;
    targetDate?: Date;
    status: 'not_started' | 'in_progress' | 'achieved' | 'missed';
    rating?: number; // 1-5
    notes?: string;
}

interface ReviewCycle {
    id: string;
    name: string;
    period: string;
    startDate: Date;
    endDate: Date;
    dueDate: Date;
    status: 'upcoming' | 'active' | 'completed';
}

export interface PerformanceReviewsProps {
    reviews?: PerformanceReview[];
    cycles?: ReviewCycle[];
    onCreateReview?: (review: Partial<PerformanceReview>) => void;
    onUpdateReview?: (reviewId: string, updates: Partial<PerformanceReview>) => void;
    onSubmitReview?: (reviewId: string) => void;
    onDeleteReview?: (reviewId: string) => void;
}

// ============================================================================
// Mock Data
// ============================================================================

const mockCycles: ReviewCycle[] = [
    {
        id: 'cycle-1',
        name: 'Q1 2025 Reviews',
        period: 'Q1 2025',
        startDate: new Date(2025, 0, 1),
        endDate: new Date(2025, 2, 31),
        dueDate: new Date(2025, 3, 15),
        status: 'upcoming',
    },
    {
        id: 'cycle-2',
        name: 'Q4 2024 Reviews',
        period: 'Q4 2024',
        startDate: new Date(2024, 9, 1),
        endDate: new Date(2024, 11, 31),
        dueDate: new Date(2025, 0, 15),
        status: 'active',
    },
];

const mockReviews: PerformanceReview[] = [
    {
        id: '1',
        employeeId: 'emp-1',
        employeeName: 'Sarah Johnson',
        employeeRole: 'Senior Software Engineer',
        reviewPeriod: 'Q4 2024',
        reviewType: 'quarterly',
        status: 'completed',
        dueDate: new Date(2025, 0, 15),
        completedDate: new Date(2024, 11, 20),
        overallRating: 4.5,
        ratings: {
            technicalSkills: 5,
            communication: 4,
            leadership: 5,
            problemSolving: 5,
            teamwork: 4,
            initiative: 5,
        },
        goals: [
            {
                id: 'g1',
                title: 'Complete React 18 Migration',
                description: 'Lead team migration to React 18',
                targetDate: new Date(2024, 11, 31),
                status: 'achieved',
                rating: 5,
                notes: 'Completed ahead of schedule with zero downtime',
            },
            {
                id: 'g2',
                title: 'Mentor 2 Junior Engineers',
                status: 'achieved',
                rating: 4,
                notes: 'Excellent mentorship, both engineers promoted',
            },
        ],
        feedback: {
            strengths:
                'Exceptional technical leadership. Sarah consistently delivers high-quality work and mentors team members effectively. Her communication with stakeholders is clear and professional.',
            areasForImprovement:
                'Could delegate more effectively to balance workload. Sometimes takes on too many tasks personally.',
            careerDevelopment:
                'Ready for Principal Engineer role. Recommend leadership training and architecture design course.',
        },
        recommendations: {
            promotion: true,
            promotionLevel: 'Principal Engineer',
            salaryAdjustment: 12,
            nextReviewDate: new Date(2025, 3, 1),
        },
    },
    {
        id: '2',
        employeeId: 'emp-2',
        employeeName: 'Michael Chen',
        employeeRole: 'Software Engineer',
        reviewPeriod: 'Q4 2024',
        reviewType: 'quarterly',
        status: 'in_progress',
        dueDate: new Date(2025, 0, 15),
        overallRating: 4.0,
        ratings: {
            technicalSkills: 4,
            communication: 4,
            leadership: 3,
            problemSolving: 4,
            teamwork: 5,
            initiative: 4,
        },
        goals: [
            {
                id: 'g3',
                title: 'Implement API Performance Improvements',
                description: 'Reduce average response time by 30%',
                targetDate: new Date(2024, 11, 31),
                status: 'in_progress',
                rating: 4,
            },
            {
                id: 'g4',
                title: 'Complete TypeScript Certification',
                status: 'achieved',
                rating: 5,
            },
        ],
        feedback: {
            strengths: 'Strong technical skills and great team player. Consistently delivers quality code.',
            areasForImprovement: 'Could take more initiative in proposing solutions. Work on presentation skills.',
        },
        recommendations: {},
    },
    {
        id: '3',
        employeeId: 'emp-3',
        employeeName: 'David Kim',
        employeeRole: 'Junior Software Engineer',
        reviewPeriod: 'Q4 2024',
        reviewType: 'quarterly',
        status: 'not_started',
        dueDate: new Date(2025, 0, 15),
        goals: [],
        ratings: {},
        feedback: {},
        recommendations: {},
    },
];

// ============================================================================
// Component
// ============================================================================

export const PerformanceReviews: React.FC<PerformanceReviewsProps> = ({
    reviews = mockReviews,
    cycles = mockCycles,
    onCreateReview,
    onUpdateReview,
    onSubmitReview,
    onDeleteReview,
}) => {
    const [selectedTab, setSelectedTab] = useState<number>(0);
    const [selectedReview, setSelectedReview] = useState<PerformanceReview | null>(null);
    const [reviewDialogOpen, setReviewDialogOpen] = useState(false);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);

    // Form state
    const [selectedEmployee, setSelectedEmployee] = useState<string>('');
    const [selectedCycle, setSelectedCycle] = useState<string>('');
    const [selectedReviewType, setSelectedReviewType] = useState<PerformanceReview['reviewType']>('quarterly');

    const notStartedReviews = reviews.filter((r) => r.status === 'not_started');
    const inProgressReviews = reviews.filter((r) => r.status === 'in_progress');
    const completedReviews = reviews.filter((r) => r.status === 'completed' || r.status === 'submitted');

    const activeCycle = cycles.find((c) => c.status === 'active');

    const handleOpenReviewDialog = (review: PerformanceReview) => {
        setSelectedReview(review);
        setReviewDialogOpen(true);
    };

    const handleCreateReview = () => {
        if (!selectedEmployee || !selectedCycle) return;

        const cycle = cycles.find((c) => c.id === selectedCycle);
        const employee = reviews.find((r) => r.employeeId === selectedEmployee);

        const newReview: Partial<PerformanceReview> = {
            employeeId: selectedEmployee,
            employeeName: employee?.employeeName || '',
            employeeRole: employee?.employeeRole || '',
            reviewPeriod: cycle?.period || '',
            reviewType: selectedReviewType,
            status: 'not_started',
            dueDate: cycle?.dueDate || new Date(),
            ratings: {},
            goals: [],
            feedback: {},
            recommendations: {},
        };

        onCreateReview?.(newReview);
        setCreateDialogOpen(false);
        resetCreateForm();
    };

    const resetCreateForm = () => {
        setSelectedEmployee('');
        setSelectedCycle('');
        setSelectedReviewType('quarterly');
    };

    const handleSaveReview = () => {
        if (!selectedReview) return;
        onUpdateReview?.(selectedReview.id, selectedReview);
        setReviewDialogOpen(false);
    };

    const handleSubmitReview = () => {
        if (!selectedReview) return;
        onSubmitReview?.(selectedReview.id);
        setReviewDialogOpen(false);
    };

    const getStatusColor = (
        status: PerformanceReview['status']
    ): 'default' | 'info' | 'warning' | 'success' => {
        switch (status) {
            case 'not_started':
                return 'default';
            case 'in_progress':
                return 'info';
            case 'completed':
                return 'warning';
            case 'submitted':
                return 'success';
            default:
                return 'default';
        }
    };

    const getStatusLabel = (status: PerformanceReview['status']): string => {
        switch (status) {
            case 'not_started':
                return 'Not Started';
            case 'in_progress':
                return 'In Progress';
            case 'completed':
                return 'Completed';
            case 'submitted':
                return 'Submitted';
            default:
                return status;
        }
    };

    const getGoalStatusColor = (status: ReviewGoal['status']): 'default' | 'info' | 'success' | 'error' => {
        switch (status) {
            case 'not_started':
                return 'default';
            case 'in_progress':
                return 'info';
            case 'achieved':
                return 'success';
            case 'missed':
                return 'error';
            default:
                return 'default';
        }
    };

    const calculateOverallRating = (ratings: PerformanceReview['ratings']): number => {
        const values = Object.values(ratings).filter((v) => v !== undefined) as number[];
        if (values.length === 0) return 0;
        return values.reduce((sum, v) => sum + v, 0) / values.length;
    };

    const formatDate = (date: Date): string => {
        return date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const getDaysUntilDue = (dueDate: Date): number => {
        const now = new Date();
        const diffMs = dueDate.getTime() - now.getTime();
        return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    };

    // Get unique employees
    const employees = Array.from(
        new Map(
            reviews.map((r) => [r.employeeId, { id: r.employeeId, name: r.employeeName, role: r.employeeRole }])
        ).values()
    );

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <Assessment fontSize="large" className="text-primary" />
                    <div>
                        <h1 className="text-3xl font-bold dark:text-white">Performance Reviews</h1>
                        <p className="text-sm text-gray-600 dark:text-gray-400">
                            {activeCycle ? `${activeCycle.name} - Due ${formatDate(activeCycle.dueDate)}` : 'No active cycle'}
                        </p>
                    </div>
                </div>
                <Button variant="contained" color="primary" startIcon={<Add />} onClick={() => setCreateDialogOpen(true)}>
                    Start Review
                </Button>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <Schedule fontSize="large" className="text-warning" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Not Started</p>
                            <p className="text-2xl font-bold dark:text-white">{notStartedReviews.length}</p>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <Edit fontSize="large" className="text-info" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">In Progress</p>
                            <p className="text-2xl font-bold dark:text-white">{inProgressReviews.length}</p>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <CheckCircle fontSize="large" className="text-success" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Completed</p>
                            <p className="text-2xl font-bold dark:text-white">{completedReviews.length}</p>
                        </div>
                    </CardContent>
                </Card>
                <Card>
                    <CardContent className="flex items-center gap-4 p-4">
                        <Star fontSize="large" className="text-warning" />
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Avg Rating</p>
                            <p className="text-2xl font-bold dark:text-white">
                                {completedReviews.length > 0
                                    ? (
                                        completedReviews.reduce((sum, r) => sum + (r.overallRating || 0), 0) / completedReviews.length
                                    ).toFixed(1)
                                    : '—'}
                            </p>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Active Cycle Alert */}
            {activeCycle && (
                <Alert severity="info">
                    <strong>{activeCycle.name}</strong> is active. Reviews due by {formatDate(activeCycle.dueDate)} (
                    {getDaysUntilDue(activeCycle.dueDate)} days remaining).
                </Alert>
            )}

            {/* Tabs */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, newValue) => setSelectedTab(newValue)}>
                    <Tab label={`Not Started (${notStartedReviews.length})`} />
                    <Tab label={`In Progress (${inProgressReviews.length})`} />
                    <Tab label={`Completed (${completedReviews.length})`} />
                </Tabs>

                <CardContent className="p-6">
                    {/* Not Started Tab */}
                    {selectedTab === 0 && (
                        <div className="space-y-4">
                            {notStartedReviews.length === 0 ? (
                                <Alert severity="success">All reviews have been started! 🎉</Alert>
                            ) : (
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>Employee</TableCell>
                                            <TableCell>Role</TableCell>
                                            <TableCell>Period</TableCell>
                                            <TableCell>Type</TableCell>
                                            <TableCell>Due Date</TableCell>
                                            <TableCell>Actions</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {notStartedReviews.map((review) => (
                                            <TableRow
                                                key={review.id}
                                                className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800"
                                                onClick={() => handleOpenReviewDialog(review)}
                                            >
                                                <TableCell>
                                                    <div className="flex items-center gap-3">
                                                        <Avatar src={review.employeeAvatar}>
                                                            {review.employeeName
                                                                .split(' ')
                                                                .map((n) => n[0])
                                                                .join('')}
                                                        </Avatar>
                                                        <span className="font-semibold dark:text-white">{review.employeeName}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="dark:text-white">{review.employeeRole}</span>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="dark:text-white">{review.reviewPeriod}</span>
                                                </TableCell>
                                                <TableCell>
                                                    <Chip label={review.reviewType.toUpperCase()} size="small" />
                                                </TableCell>
                                                <TableCell>
                                                    <div className="dark:text-white">
                                                        {formatDate(review.dueDate)}
                                                        <div className="text-xs text-gray-600 dark:text-gray-400">
                                                            {getDaysUntilDue(review.dueDate)} days left
                                                        </div>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <Button size="small" variant="contained" color="primary" startIcon={<Edit />}>
                                                        Start
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </div>
                    )}

                    {/* In Progress Tab */}
                    {selectedTab === 1 && (
                        <div className="space-y-4">
                            {inProgressReviews.length === 0 ? (
                                <Alert severity="info">No reviews in progress.</Alert>
                            ) : (
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>Employee</TableCell>
                                            <TableCell>Role</TableCell>
                                            <TableCell>Period</TableCell>
                                            <TableCell>Progress</TableCell>
                                            <TableCell>Due Date</TableCell>
                                            <TableCell>Actions</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {inProgressReviews.map((review) => {
                                            const completedSections =
                                                (review.overallRating ? 1 : 0) +
                                                (Object.keys(review.ratings).length > 0 ? 1 : 0) +
                                                (review.goals.length > 0 ? 1 : 0) +
                                                (review.feedback.strengths ? 1 : 0);
                                            const progress = (completedSections / 4) * 100;

                                            return (
                                                <TableRow
                                                    key={review.id}
                                                    className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800"
                                                    onClick={() => handleOpenReviewDialog(review)}
                                                >
                                                    <TableCell>
                                                        <div className="flex items-center gap-3">
                                                            <Avatar src={review.employeeAvatar}>
                                                                {review.employeeName
                                                                    .split(' ')
                                                                    .map((n) => n[0])
                                                                    .join('')}
                                                            </Avatar>
                                                            <span className="font-semibold dark:text-white">{review.employeeName}</span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell>
                                                        <span className="dark:text-white">{review.employeeRole}</span>
                                                    </TableCell>
                                                    <TableCell>
                                                        <span className="dark:text-white">{review.reviewPeriod}</span>
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className="w-32">
                                                            <div className="flex items-center justify-between text-xs mb-1">
                                                                <span className="dark:text-white">{Math.round(progress)}%</span>
                                                            </div>
                                                            <LinearProgress variant="determinate" value={progress} color="info" />
                                                        </div>
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className="dark:text-white">
                                                            {formatDate(review.dueDate)}
                                                            <div className="text-xs text-gray-600 dark:text-gray-400">
                                                                {getDaysUntilDue(review.dueDate)} days left
                                                            </div>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Button size="small" variant="outlined" color="primary" startIcon={<Edit />}>
                                                            Continue
                                                        </Button>
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })}
                                    </TableBody>
                                </Table>
                            )}
                        </div>
                    )}

                    {/* Completed Tab */}
                    {selectedTab === 2 && (
                        <div className="space-y-4">
                            {completedReviews.length === 0 ? (
                                <Alert severity="info">No completed reviews yet.</Alert>
                            ) : (
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>Employee</TableCell>
                                            <TableCell>Role</TableCell>
                                            <TableCell>Period</TableCell>
                                            <TableCell>Rating</TableCell>
                                            <TableCell>Completed</TableCell>
                                            <TableCell>Status</TableCell>
                                            <TableCell>Actions</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {completedReviews.map((review) => (
                                            <TableRow
                                                key={review.id}
                                                className="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800"
                                                onClick={() => handleOpenReviewDialog(review)}
                                            >
                                                <TableCell>
                                                    <div className="flex items-center gap-3">
                                                        <Avatar src={review.employeeAvatar}>
                                                            {review.employeeName
                                                                .split(' ')
                                                                .map((n) => n[0])
                                                                .join('')}
                                                        </Avatar>
                                                        <div>
                                                            <span className="font-semibold dark:text-white">{review.employeeName}</span>
                                                            {review.recommendations.promotion && (
                                                                <div className="flex items-center gap-1 text-xs text-warning">
                                                                    <EmojiEvents fontSize="inherit" />
                                                                    Promotion Recommended
                                                                </div>
                                                            )}
                                                        </div>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="dark:text-white">{review.employeeRole}</span>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="dark:text-white">{review.reviewPeriod}</span>
                                                </TableCell>
                                                <TableCell>
                                                    <div className="flex items-center gap-2">
                                                        <Rating value={review.overallRating || 0} precision={0.5} readOnly size="small" />
                                                        <span className="dark:text-white">{review.overallRating?.toFixed(1)}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <span className="dark:text-white">
                                                        {review.completedDate ? formatDate(review.completedDate) : '—'}
                                                    </span>
                                                </TableCell>
                                                <TableCell>
                                                    <Chip label={getStatusLabel(review.status)} size="small" color={getStatusColor(review.status)} />
                                                </TableCell>
                                                <TableCell>
                                                    <Button size="small" variant="outlined" startIcon={<Assessment />}>
                                                        View
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Review Detail/Edit Dialog */}
            <Dialog open={reviewDialogOpen} onClose={() => setReviewDialogOpen(false)} maxWidth="md" fullWidth>
                {selectedReview && (
                    <>
                        <DialogTitle>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <Avatar src={selectedReview.employeeAvatar}>
                                        {selectedReview.employeeName
                                            .split(' ')
                                            .map((n) => n[0])
                                            .join('')}
                                    </Avatar>
                                    <div>
                                        <h2 className="text-xl font-bold dark:text-white">{selectedReview.employeeName}</h2>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">
                                            {selectedReview.employeeRole} · {selectedReview.reviewPeriod}
                                        </p>
                                    </div>
                                </div>
                                <Chip label={getStatusLabel(selectedReview.status)} color={getStatusColor(selectedReview.status)} />
                            </div>
                        </DialogTitle>
                        <DialogContent>
                            <div className="space-y-6">
                                {/* Overall Rating */}
                                <div>
                                    <h3 className="text-lg font-semibold dark:text-white mb-3">Overall Rating</h3>
                                    <div className="flex items-center gap-4">
                                        <Rating
                                            value={selectedReview.overallRating || 0}
                                            precision={0.5}
                                            size="large"
                                            onChange={(_, newValue) => {
                                                setSelectedReview({ ...selectedReview, overallRating: newValue || undefined });
                                            }}
                                            disabled={selectedReview.status === 'submitted'}
                                        />
                                        <span className="text-2xl font-bold dark:text-white">
                                            {selectedReview.overallRating?.toFixed(1) || '—'}/5.0
                                        </span>
                                    </div>
                                </div>

                                <Divider />

                                {/* Competency Ratings */}
                                <div>
                                    <h3 className="text-lg font-semibold dark:text-white mb-3">Competency Ratings</h3>
                                    <div className="grid grid-cols-2 gap-4">
                                        {[
                                            { key: 'technicalSkills', label: 'Technical Skills' },
                                            { key: 'communication', label: 'Communication' },
                                            { key: 'leadership', label: 'Leadership' },
                                            { key: 'problemSolving', label: 'Problem Solving' },
                                            { key: 'teamwork', label: 'Teamwork' },
                                            { key: 'initiative', label: 'Initiative' },
                                        ].map(({ key, label }) => (
                                            <div key={key}>
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">{label}</p>
                                                <Rating
                                                    value={selectedReview.ratings[key as keyof typeof selectedReview.ratings] || 0}
                                                    onChange={(_, newValue) => {
                                                        setSelectedReview({
                                                            ...selectedReview,
                                                            ratings: { ...selectedReview.ratings, [key]: newValue || undefined },
                                                            overallRating: calculateOverallRating({
                                                                ...selectedReview.ratings,
                                                                [key]: newValue || undefined,
                                                            }),
                                                        });
                                                    }}
                                                    disabled={selectedReview.status === 'submitted'}
                                                />
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                <Divider />

                                {/* Goals */}
                                <div>
                                    <h3 className="text-lg font-semibold dark:text-white mb-3">Goals Assessment</h3>
                                    {selectedReview.goals.length === 0 ? (
                                        <Alert severity="info">No goals set for this period.</Alert>
                                    ) : (
                                        <div className="space-y-3">
                                            {selectedReview.goals.map((goal) => (
                                                <Card key={goal.id}>
                                                    <CardContent className="p-4">
                                                        <div className="flex items-start justify-between mb-2">
                                                            <div className="flex-1">
                                                                <p className="font-semibold dark:text-white">{goal.title}</p>
                                                                {goal.description && (
                                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{goal.description}</p>
                                                                )}
                                                            </div>
                                                            <Chip label={goal.status.replace('_', ' ').toUpperCase()} size="small" color={getGoalStatusColor(goal.status)} />
                                                        </div>
                                                        <div className="flex items-center gap-4 mt-3">
                                                            <div>
                                                                <p className="text-xs text-gray-600 dark:text-gray-400 mb-1">Rating</p>
                                                                <Rating value={goal.rating || 0} size="small" readOnly />
                                                            </div>
                                                            {goal.notes && (
                                                                <div className="flex-1">
                                                                    <p className="text-xs text-gray-600 dark:text-gray-400 mb-1">Notes</p>
                                                                    <p className="text-sm dark:text-white">{goal.notes}</p>
                                                                </div>
                                                            )}
                                                        </div>
                                                    </CardContent>
                                                </Card>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                <Divider />

                                {/* Feedback */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold dark:text-white">Written Feedback</h3>

                                    <TextField
                                        fullWidth
                                        multiline
                                        rows={3}
                                        label="Strengths"
                                        value={selectedReview.feedback.strengths || ''}
                                        onChange={(e) =>
                                            setSelectedReview({
                                                ...selectedReview,
                                                feedback: { ...selectedReview.feedback, strengths: e.target.value },
                                            })
                                        }
                                        disabled={selectedReview.status === 'submitted'}
                                        placeholder="What does this employee do exceptionally well?"
                                    />

                                    <TextField
                                        fullWidth
                                        multiline
                                        rows={3}
                                        label="Areas for Improvement"
                                        value={selectedReview.feedback.areasForImprovement || ''}
                                        onChange={(e) =>
                                            setSelectedReview({
                                                ...selectedReview,
                                                feedback: { ...selectedReview.feedback, areasForImprovement: e.target.value },
                                            })
                                        }
                                        disabled={selectedReview.status === 'submitted'}
                                        placeholder="Where can this employee grow?"
                                    />

                                    <TextField
                                        fullWidth
                                        multiline
                                        rows={3}
                                        label="Career Development"
                                        value={selectedReview.feedback.careerDevelopment || ''}
                                        onChange={(e) =>
                                            setSelectedReview({
                                                ...selectedReview,
                                                feedback: { ...selectedReview.feedback, careerDevelopment: e.target.value },
                                            })
                                        }
                                        disabled={selectedReview.status === 'submitted'}
                                        placeholder="Career guidance and development recommendations"
                                    />
                                </div>

                                <Divider />

                                {/* Recommendations */}
                                <div className="space-y-4">
                                    <h3 className="text-lg font-semibold dark:text-white">Recommendations</h3>

                                    <FormControl fullWidth>
                                        <InputLabel>Promotion Recommendation</InputLabel>
                                        <Select
                                            value={selectedReview.recommendations.promotion ? 'yes' : 'no'}
                                            onChange={(e) =>
                                                setSelectedReview({
                                                    ...selectedReview,
                                                    recommendations: {
                                                        ...selectedReview.recommendations,
                                                        promotion: e.target.value === 'yes',
                                                    },
                                                })
                                            }
                                            disabled={selectedReview.status === 'submitted'}
                                            label="Promotion Recommendation"
                                        >
                                            <MenuItem value="no">No</MenuItem>
                                            <MenuItem value="yes">Yes</MenuItem>
                                        </Select>
                                    </FormControl>

                                    {selectedReview.recommendations.promotion && (
                                        <TextField
                                            fullWidth
                                            label="Promotion to Level"
                                            value={selectedReview.recommendations.promotionLevel || ''}
                                            onChange={(e) =>
                                                setSelectedReview({
                                                    ...selectedReview,
                                                    recommendations: { ...selectedReview.recommendations, promotionLevel: e.target.value },
                                                })
                                            }
                                            disabled={selectedReview.status === 'submitted'}
                                            placeholder="e.g., Senior Engineer, Principal Engineer"
                                        />
                                    )}

                                    <TextField
                                        fullWidth
                                        type="number"
                                        label="Salary Adjustment (%)"
                                        value={selectedReview.recommendations.salaryAdjustment || ''}
                                        onChange={(e) =>
                                            setSelectedReview({
                                                ...selectedReview,
                                                recommendations: {
                                                    ...selectedReview.recommendations,
                                                    salaryAdjustment: parseFloat(e.target.value) || undefined,
                                                },
                                            })
                                        }
                                        disabled={selectedReview.status === 'submitted'}
                                        placeholder="0"
                                    />
                                </div>
                            </div>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setReviewDialogOpen(false)}>Close</Button>
                            {selectedReview.status !== 'submitted' && (
                                <>
                                    <Button onClick={handleSaveReview} variant="outlined" color="primary">
                                        Save Draft
                                    </Button>
                                    <Button onClick={handleSubmitReview} variant="contained" color="primary">
                                        Submit Review
                                    </Button>
                                </>
                            )}
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Create Review Dialog */}
            <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Start New Performance Review</DialogTitle>
                <DialogContent>
                    <div className="space-y-4 pt-2">
                        <FormControl fullWidth>
                            <InputLabel>Employee</InputLabel>
                            <Select value={selectedEmployee} onChange={(e) => setSelectedEmployee(e.target.value as string)} label="Employee">
                                {employees.map((emp) => (
                                    <MenuItem key={emp.id} value={emp.id}>
                                        {emp.name} - {emp.role}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Review Cycle</InputLabel>
                            <Select value={selectedCycle} onChange={(e) => setSelectedCycle(e.target.value as string)} label="Review Cycle">
                                {cycles.map((cycle) => (
                                    <MenuItem key={cycle.id} value={cycle.id}>
                                        {cycle.name} - Due {formatDate(cycle.dueDate)}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <FormControl fullWidth>
                            <InputLabel>Review Type</InputLabel>
                            <Select
                                value={selectedReviewType}
                                onChange={(e) => setSelectedReviewType(e.target.value as PerformanceReview['reviewType'])}
                                label="Review Type"
                            >
                                <MenuItem value="quarterly">Quarterly</MenuItem>
                                <MenuItem value="annual">Annual</MenuItem>
                                <MenuItem value="probation">Probation</MenuItem>
                                <MenuItem value="promotion">Promotion</MenuItem>
                            </Select>
                        </FormControl>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button
                        onClick={() => {
                            setCreateDialogOpen(false);
                            resetCreateForm();
                        }}
                    >
                        Cancel
                    </Button>
                    <Button
                        onClick={handleCreateReview}
                        variant="contained"
                        color="primary"
                        disabled={!selectedEmployee || !selectedCycle}
                    >
                        Start Review
                    </Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default PerformanceReviews;
