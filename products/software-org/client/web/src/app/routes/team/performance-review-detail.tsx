/**
 * Performance Review Detail Route
 *
 * View and edit an existing performance review.
 *
 * @package @ghatana/software-org-web
 */

import { useParams, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { PerformanceReviewForm } from '../../../components/team/PerformanceReviewForm';
import { PeerFeedbackViewer } from '../../../components/team/PeerFeedbackViewer';
import { useState } from 'react';

interface PerformanceReview {
    id: string;
    employeeId: string;
    employeeName: string;
    employeeRole: string;
    reviewCycle: string;
    status: string;
    dueDate: string;
    overallRating?: number;
}

export default function PerformanceReviewDetailRoute() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState<'review' | 'feedback'>('review');

    const { data: review, isLoading, error } = useQuery<PerformanceReview>({
        queryKey: ['/api/v1/performance-reviews', id],
        queryFn: async () => {
            const response = await fetch(`/api/v1/performance-reviews/${id}`);
            if (!response.ok) throw new Error('Failed to fetch performance review');
            return response.json();
        },
        enabled: !!id,
    });

    if (isLoading) {
        return (
            <div className="p-6 text-center">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
                <p className="text-gray-600 dark:text-gray-400 mt-4">Loading review...</p>
            </div>
        );
    }

    if (error || !review) {
        return (
            <div className="p-6">
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                    <h3 className="text-red-800 dark:text-red-200 font-semibold">Review Not Found</h3>
                    <p className="text-red-700 dark:text-red-300 mt-2">
                        The performance review you are looking for does not exist.
                    </p>
                    <button
                        onClick={() => navigate('/team/performance-reviews')}
                        className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
                    >
                        Back to Reviews
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="p-6">
            <div className="max-w-5xl mx-auto">
                {/* Header */}
                <div className="mb-6">
                    <button
                        onClick={() => navigate('/team/performance-reviews')}
                        className="text-blue-600 dark:text-blue-400 hover:underline mb-4 flex items-center gap-2"
                    >
                        ← Back to Reviews
                    </button>
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100">
                        Performance Review
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-2">
                        {review.employeeName} • {review.employeeRole} • {review.reviewCycle}
                    </p>
                    <div className="flex items-center gap-4 mt-4">
                        <span
                            className={`px-3 py-1 rounded-full text-sm font-medium ${review.status === 'COMPLETED'
                                    ? 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200'
                                    : review.status === 'IN_PROGRESS'
                                        ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-200'
                                        : review.status === 'SUBMITTED'
                                            ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-200'
                                            : 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200'
                                }`}
                        >
                            {review.status.replace('_', ' ')}
                        </span>
                        <span className="text-sm text-gray-500 dark:text-gray-400">
                            Due: {new Date(review.dueDate).toLocaleDateString()}
                        </span>
                    </div>
                </div>

                {/* Tab Navigation */}
                <div className="border-b border-gray-200 dark:border-gray-700 mb-6">
                    <div className="flex gap-4">
                        <button
                            onClick={() => setActiveTab('review')}
                            className={`px-4 py-2 border-b-2 font-medium transition-colors ${activeTab === 'review'
                                    ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                    : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                                }`}
                        >
                            ✍️ Review Form
                        </button>
                        <button
                            onClick={() => setActiveTab('feedback')}
                            className={`px-4 py-2 border-b-2 font-medium transition-colors ${activeTab === 'feedback'
                                    ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                    : 'border-transparent text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
                                }`}
                        >
                            💬 Peer Feedback
                        </button>
                    </div>
                </div>

                {/* Tab Content */}
                {activeTab === 'review' ? (
                    <PerformanceReviewForm
                        reviewId={id}
                        employeeId={review.employeeId}
                        employeeName={review.employeeName}
                        onSuccess={() => navigate('/team/performance-reviews')}
                        onCancel={() => navigate('/team/performance-reviews')}
                    />
                ) : (
                    <PeerFeedbackViewer
                        employeeId={review.employeeId}
                        reviewPeriod={review.reviewCycle}
                    />
                )}
            </div>
        </div>
    );
}
