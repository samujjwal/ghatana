/**
 * Peer Feedback Viewer Component
 *
 * Displays aggregated peer feedback for performance reviews.
 * Shows ratings, comments, strengths, and trends.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';

interface PeerFeedback {
    id: string;
    rating: number;
    strengths: string[];
    improvements: string[];
    comment: string;
    anonymous: boolean;
    submittedAt: string;
}

interface PeerFeedbackViewerProps {
    employeeId: string;
    reviewPeriod: string;
}

export const PeerFeedbackViewer: React.FC<PeerFeedbackViewerProps> = ({
    employeeId,
    reviewPeriod,
}) => {
    // Fetch peer feedback
    const { data: feedbackList = [], isLoading } = useQuery<PeerFeedback[]>({
        queryKey: ['/api/v1/peer-feedback', employeeId, reviewPeriod],
        queryFn: async () => {
            const response = await fetch(
                `/api/v1/peer-feedback?employeeId=${employeeId}&period=${reviewPeriod}`
            );
            if (!response.ok) throw new Error('Failed to fetch peer feedback');
            return response.json();
        },
    });

    // Calculate aggregated metrics
    const metrics = React.useMemo(() => {
        if (feedbackList.length === 0) {
            return {
                avgRating: 0,
                totalFeedback: 0,
                strengthsCount: {},
                improvementsCount: {},
                ratingDistribution: [0, 0, 0, 0, 0],
            };
        }

        const avgRating =
            feedbackList.reduce((sum, f) => sum + f.rating, 0) / feedbackList.length;

        // Count strengths
        const strengthsCount: Record<string, number> = {};
        feedbackList.forEach(f => {
            f.strengths?.forEach(s => {
                strengthsCount[s] = (strengthsCount[s] || 0) + 1;
            });
        });

        // Count improvements
        const improvementsCount: Record<string, number> = {};
        feedbackList.forEach(f => {
            f.improvements?.forEach(i => {
                improvementsCount[i] = (improvementsCount[i] || 0) + 1;
            });
        });

        // Rating distribution
        const ratingDistribution = [0, 0, 0, 0, 0];
        feedbackList.forEach(f => {
            const index = Math.floor(f.rating) - 1;
            if (index >= 0 && index < 5) {
                ratingDistribution[index]++;
            }
        });

        return {
            avgRating: avgRating.toFixed(1),
            totalFeedback: feedbackList.length,
            strengthsCount,
            improvementsCount,
            ratingDistribution,
        };
    }, [feedbackList]);

    // Get top strengths and improvements
    const topStrengths = React.useMemo(() => {
        return Object.entries(metrics.strengthsCount)
            .sort(([, a], [, b]) => b - a)
            .slice(0, 5);
    }, [metrics.strengthsCount]);

    const topImprovements = React.useMemo(() => {
        return Object.entries(metrics.improvementsCount)
            .sort(([, a], [, b]) => b - a)
            .slice(0, 5);
    }, [metrics.improvementsCount]);

    if (isLoading) {
        return (
            <div className="text-center py-12">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500"></div>
                <p className="text-gray-600 dark:text-gray-400 mt-4">Loading feedback...</p>
            </div>
        );
    }

    if (feedbackList.length === 0) {
        return (
            <div className="text-center py-12 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
                <div className="text-6xl mb-4">💬</div>
                <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                    No Peer Feedback Yet
                </h3>
                <p className="text-gray-600 dark:text-gray-400">
                    Peer feedback will appear here once submitted
                </p>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
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
                                className={`text-2xl ${star <= Number(metrics.avgRating)
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">Total Feedback</p>
                            <p className="text-3xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {metrics.totalFeedback}
                            </p>
                        </div>
                        <div className="text-4xl">👥</div>
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-4">
                        From peers and colleagues
                    </p>
                </div>

                <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Top Strength</p>
                            <p className="text-lg font-bold text-gray-900 dark:text-gray-100 mt-2">
                                {topStrengths[0]?.[0] || 'N/A'}
                            </p>
                        </div>
                        <div className="text-4xl">💪</div>
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-4">
                        Mentioned {topStrengths[0]?.[1] || 0} times
                    </p>
                </div>
            </div>

            {/* Rating Distribution */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
                    Rating Distribution
                </h3>
                <div className="space-y-3">
                    {[5, 4, 3, 2, 1].map(rating => {
                        const count = metrics.ratingDistribution[rating - 1];
                        const percentage =
                            metrics.totalFeedback > 0 ? (count / metrics.totalFeedback) * 100 : 0;

                        return (
                            <div key={rating} className="flex items-center gap-4">
                                <div className="w-12 text-sm text-gray-600 dark:text-gray-400">
                                    {rating} ⭐
                                </div>
                                <div className="flex-1 bg-gray-200 dark:bg-gray-700 rounded-full h-6">
                                    <div
                                        className="bg-yellow-400 h-6 rounded-full transition-all flex items-center justify-end pr-2"
                                        style={{ width: `${percentage}%` }}
                                    >
                                        {count > 0 && (
                                            <span className="text-xs font-medium text-gray-900">
                                                {count}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div className="w-12 text-sm text-gray-600 dark:text-gray-400 text-right">
                                    {percentage.toFixed(0)}%
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Strengths Word Cloud */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
                    💪 Top Strengths
                </h3>
                {topStrengths.length > 0 ? (
                    <div className="flex flex-wrap gap-3">
                        {topStrengths.map(([strength, count], index) => {
                            const fontSize = 20 + (5 - index) * 4;
                            return (
                                <div
                                    key={strength}
                                    className="inline-block px-4 py-2 bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200 rounded-full"
                                    style={{ fontSize: `${fontSize}px` }}
                                >
                                    {strength}
                                    <span className="ml-2 text-xs opacity-70">({count})</span>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <p className="text-gray-500 dark:text-gray-400 text-sm">No strengths mentioned</p>
                )}
            </div>

            {/* Improvement Areas */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
                    🎯 Areas for Improvement
                </h3>
                {topImprovements.length > 0 ? (
                    <div className="space-y-2">
                        {topImprovements.map(([improvement, count]) => (
                            <div
                                key={improvement}
                                className="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg"
                            >
                                <span className="text-gray-900 dark:text-gray-100">{improvement}</span>
                                <span className="px-2 py-1 bg-blue-100 dark:bg-blue-900/50 text-blue-800 dark:text-blue-200 rounded-full text-xs font-medium">
                                    {count} mention{count > 1 ? 's' : ''}
                                </span>
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="text-gray-500 dark:text-gray-400 text-sm">
                        No improvement areas mentioned
                    </p>
                )}
            </div>

            {/* Individual Comments */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
                    💬 Comments ({feedbackList.length})
                </h3>
                <div className="space-y-4">
                    {feedbackList.map((feedback, index) => (
                        <div
                            key={feedback.id}
                            className="border-l-4 border-blue-500 pl-4 py-2 bg-gray-50 dark:bg-gray-700/50 rounded-r-lg"
                        >
                            <div className="flex items-center gap-2 mb-2">
                                <div className="flex gap-1">
                                    {[1, 2, 3, 4, 5].map(star => (
                                        <span
                                            key={star}
                                            className={`text-sm ${star <= feedback.rating
                                                ? 'text-yellow-400'
                                                : 'text-gray-300 dark:text-gray-600'
                                                }`}
                                        >
                                            ★
                                        </span>
                                    ))}
                                </div>
                                <span className="text-xs text-gray-500 dark:text-gray-400">
                                    {feedback.anonymous ? 'Anonymous' : `Peer ${index + 1}`} •{' '}
                                    {new Date(feedback.submittedAt).toLocaleDateString()}
                                </span>
                            </div>
                            {feedback.comment && (
                                <p className="text-gray-700 dark:text-gray-300 text-sm">
                                    "{feedback.comment}"
                                </p>
                            )}
                        </div>
                    ))}
                </div>
            </div>

            {/* Trend Over Time (placeholder for future) */}
            <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
                    📈 Trend Over Time
                </h3>
                <div className="h-48 flex items-center justify-center text-gray-400 dark:text-gray-500">
                    <p className="text-sm">Trend chart will be available after multiple review cycles</p>
                </div>
            </div>
        </div>
    );
};

export default PeerFeedbackViewer;
