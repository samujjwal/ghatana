/**
 * Performance Review Dashboard Component
 *
 * Manager's dashboard for conducting performance reviews.
 * View review cycles, track completion, and manage team reviews.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';

interface ApiEmployee {
    id: string;
    name: string | null;
    role?: string | null;
}

interface ApiGoalSummary {
    completed?: boolean;
}

interface ApiReviewMetadata {
    goals?: ApiGoalSummary[];
    reviewDate?: string;
}

interface ApiPerformanceReview {
    id: string;
    employeeId: string;
    reviewerId: string;
    period: string;
    status: string;
    overallRating: number;
    metadata: ApiReviewMetadata | Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
    submittedAt: string | null;
    completedAt: string | null;
    employee?: ApiEmployee | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function getMetadata(value: unknown): ApiReviewMetadata {
    if (!isRecord(value)) return {};
    const goalsRaw = value.goals;
    const goals = Array.isArray(goalsRaw) ? goalsRaw.filter(isRecord).map((g) => ({
        completed: typeof g.completed === 'boolean' ? g.completed : undefined,
    })) : undefined;
    const reviewDate = typeof value.reviewDate === 'string' ? value.reviewDate : undefined;
    return { goals, reviewDate };
}

interface ReviewWithEmployee extends ApiPerformanceReview {
    employee?: ApiEmployee | null;
}

interface ReviewCycle {
    id: string;
    name: string;
    period: string;
    dueDate: string;
}

export const PerformanceReviewDashboard: React.FC = () => {
    const navigate = useNavigate();
    const [selectedCycle, setSelectedCycle] = useState<string>('Q1-2025');
    const [searchTerm, setSearchTerm] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('ALL');

    // Mock review cycles (would come from API)
    const reviewCycles: ReviewCycle[] = [
        { id: 'Q1-2025', name: 'Q1 2025', period: 'Jan - Mar 2025', dueDate: '2025-03-31' },
        { id: 'Q4-2024', name: 'Q4 2024', period: 'Oct - Dec 2024', dueDate: '2024-12-31' },
        { id: 'Q3-2024', name: 'Q3 2024', period: 'Jul - Sep 2024', dueDate: '2024-09-30' },
    ];

    // Fetch reviews for current user (manager)
    const { data: reviews = [], isLoading } = useQuery<ReviewWithEmployee[]>({
        queryKey: ['/api/v1/performance-reviews', { cycle: selectedCycle }],
        queryFn: async () => {
            const response = await fetch(`/api/v1/performance-reviews?cycle=${selectedCycle}`);
            if (!response.ok) throw new Error('Failed to fetch reviews');
            return response.json();
        },
    });

    // Calculate metrics
    const metrics = React.useMemo(() => {
        const total = reviews.length;
        const completed = reviews.filter(r => r.status === 'COMPLETED').length;
        const inProgress = reviews.filter(r => r.status === 'IN_PROGRESS').length;
        const notStarted = total - completed - inProgress;

        const totalRatings = reviews
            .reduce((sum, r) => sum + (typeof r.overallRating === 'number' ? r.overallRating : 0), 0);
        const avgRating = reviews.length > 0 ? totalRatings / reviews.length : 0;

        const currentCycle = reviewCycles.find(c => c.id === selectedCycle);
        const daysUntilDue = currentCycle
            ? Math.ceil((new Date(currentCycle.dueDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
            : 0;

        return {
            total,
            completed,
            inProgress,
            notStarted,
            completionRate: total > 0 ? (completed / total) * 100 : 0,
            avgRating: avgRating.toFixed(1),
            daysUntilDue,
        };
    }, [reviews, selectedCycle, reviewCycles]);

    // Filter reviews
    const filteredReviews = React.useMemo(() => {
        return reviews.filter(review => {
            const matchesSearch = searchTerm === '' ||
                review.employee?.name?.toLowerCase().includes(searchTerm.toLowerCase());
            const matchesStatus = statusFilter === 'ALL' || review.status === statusFilter;
            return matchesSearch && matchesStatus;
        });
    }, [reviews, searchTerm, statusFilter]);

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                        Performance Reviews
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-2">
                        Manage and track team performance reviews
                    </p>
                </div>

                {/* Review Cycle Selector */}
                <div className="flex items-center gap-4">
                    <select
                        value={selectedCycle}
                        onChange={(e) => setSelectedCycle(e.target.value)}
                        className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                    >
                        {reviewCycles.map(cycle => (
                            <option key={cycle.id} value={cycle.id}>
                                {cycle.name} ({cycle.period})
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Metrics Summary */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Completion Rate</p>
                            <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {metrics.completionRate.toFixed(0)}%
                            </p>
                        </div>
                        <div className="text-4xl">📊</div>
                    </div>
                    <div className="mt-4 bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                        <div
                            className="bg-green-500 h-2 rounded-full transition-all"
                            style={{ width: `${metrics.completionRate}%` }}
                        />
                    </div>
                </div>

                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Reviews Completed</p>
                            <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {metrics.completed}/{metrics.total}
                            </p>
                        </div>
                        <div className="text-4xl">✅</div>
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-4">
                        {metrics.inProgress} in progress, {metrics.notStarted} not started
                    </p>
                </div>

                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Average Rating</p>
                            <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {metrics.avgRating}/5.0
                            </p>
                        </div>
                        <div className="text-4xl">⭐</div>
                    </div>
                    <div className="flex gap-1 mt-4">
                        {[1, 2, 3, 4, 5].map(star => (
                            <div
                                key={star}
                                className={`w-6 h-6 ${star <= parseFloat(metrics.avgRating)
                                    ? 'text-yellow-400'
                                    : 'text-gray-300 dark:text-gray-600'
                                    }`}
                            >
                                ★
                            </div>
                        ))}
                    </div>
                </div>

                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Due In</p>
                            <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {metrics.daysUntilDue}
                            </p>
                        </div>
                        <div className="text-4xl">📅</div>
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-4">
                        days remaining
                    </p>
                </div>
            </div>

            {/* Filters and Search */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6 mb-6">
                <div className="flex flex-col md:flex-row gap-4">
                    {/* Search */}
                    <div className="flex-1">
                        <input
                            type="text"
                            placeholder="Search by employee name..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                        />
                    </div>

                    {/* Status Filter */}
                    <div>
                        <select
                            value={statusFilter}
                            onChange={(e) => setStatusFilter(e.target.value)}
                            className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                        >
                            <option value="ALL">All Status</option>
                            <option value="NOT_STARTED">Not Started</option>
                            <option value="IN_PROGRESS">In Progress</option>
                            <option value="SUBMITTED">Submitted</option>
                            <option value="COMPLETED">Completed</option>
                        </select>
                    </div>

                    {/* Start New Review Button */}
                    <button
                        onClick={() => navigate('/team/performance-reviews/new')}
                        className="px-6 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors font-medium whitespace-nowrap"
                    >
                        + Start New Review
                    </button>
                </div>
            </div>

            {/* Reviews List */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
                <div className="p-6 border-b border-gray-200 dark:border-gray-700">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                        Team Reviews ({filteredReviews.length})
                    </h2>
                </div>

                {isLoading ? (
                    <div className="text-center py-12">
                        <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
                        <p className="text-gray-600 dark:text-gray-400 mt-4">Loading reviews...</p>
                    </div>
                ) : filteredReviews.length === 0 ? (
                    <div className="text-center py-12">
                        <div className="text-6xl mb-4">📝</div>
                        <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                            No Reviews Found
                        </h3>
                        <p className="text-gray-600 dark:text-gray-400 mb-6">
                            {searchTerm || statusFilter !== 'ALL'
                                ? 'Try adjusting your filters'
                                : 'Click "Start New Review" to begin'}
                        </p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-200 dark:divide-gray-700">
                        {filteredReviews.map((review) => (
                            <ReviewCard
                                key={review.id}
                                review={review}
                                onViewClick={() => navigate(`/team/performance-reviews/${review.id}`)}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

// Review Card Component
interface ReviewCardProps {
    review: ReviewWithEmployee;
    onViewClick: () => void;
}

const ReviewCard: React.FC<ReviewCardProps> = ({ review, onViewClick }) => {
    const getStatusColor = (status: string) => {
        switch (status) {
            case 'COMPLETED':
                return 'bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-200';
            case 'IN_PROGRESS':
                return 'bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-200';
            case 'SUBMITTED':
                return 'bg-purple-100 text-purple-800 dark:bg-purple-900/50 dark:text-purple-200';
            default:
                return 'bg-gray-100 text-gray-800 dark:bg-gray-900/50 dark:text-gray-200';
        }
    };

    const getProgressPercentage = (status: string) => {
        switch (status) {
            case 'COMPLETED':
                return 100;
            case 'SUBMITTED':
                return 90;
            case 'IN_PROGRESS':
                return 50;
            default:
                return 0;
        }
    };

    const progress = getProgressPercentage(review.status);
    const metadata = getMetadata(review.metadata);
    const reviewDate = metadata.reviewDate ?? review.updatedAt;
    const goals = metadata.goals;

    return (
        <div
            className="p-6 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors cursor-pointer"
            onClick={onViewClick}
        >
            <div className="flex items-start justify-between">
                <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                            {review.employee?.name || `Employee ${review.employeeId}`}
                        </h3>
                        <span className={`px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(review.status)}`}>
                            {review.status.replace('_', ' ')}
                        </span>
                    </div>

                    <div className="flex items-center gap-6 text-sm text-gray-600 dark:text-gray-400">
                        <span>👤 Employee</span>
                        <span>📅 {new Date(reviewDate).toLocaleDateString()}</span>
                        {review.overallRating && (
                            <span className="flex items-center gap-1">
                                ⭐ {review.overallRating}/5.0
                            </span>
                        )}
                    </div>

                    {/* Progress Bar */}
                    <div className="mt-3">
                        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 mb-1">
                            <span>Progress</span>
                            <span>{progress}%</span>
                        </div>
                        <div className="bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                            <div
                                className={`h-2 rounded-full transition-all ${progress === 100 ? 'bg-green-500' : 'bg-blue-500'
                                    }`}
                                style={{ width: `${progress}%` }}
                            />
                        </div>
                    </div>

                    {/* Goals Summary (if available) */}
                    {Array.isArray(goals) && goals.length > 0 && (
                        <div className="mt-3 flex items-center gap-4 text-sm">
                            <span className="text-gray-600 dark:text-gray-400">
                                Goals: {goals.filter((g) => g.completed === true).length}/{goals.length} completed
                            </span>
                        </div>
                    )}
                </div>

                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onViewClick();
                    }}
                    className="ml-4 px-4 py-2 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-md transition-colors text-sm font-medium"
                >
                    View Details →
                </button>
            </div>
        </div>
    );
};

export default PerformanceReviewDashboard;
