/**
 * Content Generation Progress Component
 * 
 * Displays real-time progress of content generation including
 * examples, simulations, and animations for each claim.
 */

import React, { useEffect, useState } from 'react';
import { CheckCircle, Circle, Loader, AlertCircle, TrendingUp } from 'lucide-react';

interface ProgressData {
    experienceId: string;
    status: string;
    totalClaims: number;
    claimsProcessed: number;
    percentComplete: number;
    contentCounts: {
        examples: number;
        simulations: number;
        animations: number;
    };
    isComplete: boolean;
    updatedAt: string;
}

interface ContentGenerationProgressProps {
    experienceId: string;
    onComplete?: () => void;
    pollInterval?: number;
}

export const ContentGenerationProgress: React.FC<ContentGenerationProgressProps> = ({
    experienceId,
    onComplete,
    pollInterval = 2000,
}) => {
    const [progress, setProgress] = useState<ProgressData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isPolling, setIsPolling] = useState(true);

    useEffect(() => {
        let intervalId: NodeJS.Timeout;

        const fetchProgress = async () => {
            try {
                const response = await fetch(
                    `/api/content-studio/experiences/${experienceId}/progress`
                );

                if (!response.ok) {
                    throw new Error('Failed to fetch progress');
                }

                const data: ProgressData = await response.json();
                setProgress(data);
                setError(null);

                // Stop polling if complete
                if (data.isComplete) {
                    setIsPolling(false);
                    onComplete?.();
                }
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Unknown error');
                setIsPolling(false);
            }
        };

        // Initial fetch
        fetchProgress();

        // Set up polling
        if (isPolling) {
            intervalId = setInterval(fetchProgress, pollInterval);
        }

        return () => {
            if (intervalId) {
                clearInterval(intervalId);
            }
        };
    }, [experienceId, pollInterval, isPolling, onComplete]);

    if (error) {
        return (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <div className="flex items-center gap-2 text-red-700">
                    <AlertCircle className="w-5 h-5" />
                    <span className="font-medium">Error loading progress</span>
                </div>
                <p className="text-sm text-red-600 mt-1">{error}</p>
            </div>
        );
    }

    if (!progress) {
        return (
            <div className="bg-gray-50 border border-gray-200 rounded-lg p-6">
                <div className="flex items-center justify-center gap-2 text-gray-600">
                    <Loader className="w-5 h-5 animate-spin" />
                    <span>Loading progress...</span>
                </div>
            </div>
        );
    }

    const { totalClaims, claimsProcessed, percentComplete, contentCounts, isComplete } = progress;

    return (
        <div className="bg-white border border-gray-200 rounded-lg p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-gray-900">
                    Content Generation Progress
                </h3>
                {isComplete ? (
                    <div className="flex items-center gap-2 text-green-600">
                        <CheckCircle className="w-5 h-5" />
                        <span className="font-medium">Complete</span>
                    </div>
                ) : (
                    <div className="flex items-center gap-2 text-blue-600">
                        <Loader className="w-5 h-5 animate-spin" />
                        <span className="font-medium">Generating...</span>
                    </div>
                )}
            </div>

            {/* Overall Progress Bar */}
            <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Overall Progress</span>
                    <span className="font-medium text-gray-900">{percentComplete}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-3 overflow-hidden">
                    <div
                        className={`h-full transition-all duration-500 ${isComplete ? 'bg-green-500' : 'bg-blue-500'
                            }`}
                        style={{ width: `${percentComplete}%` }}
                    />
                </div>
                <div className="text-sm text-gray-500">
                    {claimsProcessed} of {totalClaims} claims processed
                </div>
            </div>

            {/* Content Type Breakdown */}
            <div className="grid grid-cols-3 gap-4">
                <ContentTypeCard
                    label="Examples"
                    count={contentCounts.examples}
                    icon="📝"
                    color="blue"
                />
                <ContentTypeCard
                    label="Simulations"
                    count={contentCounts.simulations}
                    icon="🔬"
                    color="purple"
                />
                <ContentTypeCard
                    label="Animations"
                    count={contentCounts.animations}
                    icon="🎬"
                    color="green"
                />
            </div>

            {/* Claim Status List */}
            <div className="space-y-2">
                <h4 className="text-sm font-medium text-gray-700">Claim Status</h4>
                <div className="space-y-1">
                    {Array.from({ length: totalClaims }).map((_, index) => (
                        <ClaimStatusRow
                            key={index}
                            claimNumber={index + 1}
                            isProcessed={index < claimsProcessed}
                            isCurrent={index === claimsProcessed}
                        />
                    ))}
                </div>
            </div>

            {/* Stats Footer */}
            {isComplete && (
                <div className="pt-4 border-t border-gray-200">
                    <div className="flex items-center gap-2 text-sm text-gray-600">
                        <TrendingUp className="w-4 h-4" />
                        <span>
                            Generated {contentCounts.examples + contentCounts.simulations + contentCounts.animations} total content items
                        </span>
                    </div>
                </div>
            )}
        </div>
    );
};

interface ContentTypeCardProps {
    label: string;
    count: number;
    icon: string;
    color: 'blue' | 'purple' | 'green';
}

const ContentTypeCard: React.FC<ContentTypeCardProps> = ({ label, count, icon, color }) => {
    const colorClasses = {
        blue: 'bg-blue-50 border-blue-200 text-blue-700',
        purple: 'bg-purple-50 border-purple-200 text-purple-700',
        green: 'bg-green-50 border-green-200 text-green-700',
    };

    return (
        <div className={`border rounded-lg p-4 ${colorClasses[color]}`}>
            <div className="text-2xl mb-1">{icon}</div>
            <div className="text-2xl font-bold">{count}</div>
            <div className="text-sm font-medium">{label}</div>
        </div>
    );
};

interface ClaimStatusRowProps {
    claimNumber: number;
    isProcessed: boolean;
    isCurrent: boolean;
}

const ClaimStatusRow: React.FC<ClaimStatusRowProps> = ({
    claimNumber,
    isProcessed,
    isCurrent,
}) => {
    return (
        <div
            className={`flex items-center gap-3 px-3 py-2 rounded ${isCurrent
                    ? 'bg-blue-50 border border-blue-200'
                    : isProcessed
                        ? 'bg-green-50'
                        : 'bg-gray-50'
                }`}
        >
            {isProcessed ? (
                <CheckCircle className="w-4 h-4 text-green-600 flex-shrink-0" />
            ) : isCurrent ? (
                <Loader className="w-4 h-4 text-blue-600 animate-spin flex-shrink-0" />
            ) : (
                <Circle className="w-4 h-4 text-gray-400 flex-shrink-0" />
            )}
            <span
                className={`text-sm ${isProcessed
                        ? 'text-green-700 font-medium'
                        : isCurrent
                            ? 'text-blue-700 font-medium'
                            : 'text-gray-600'
                    }`}
            >
                Claim {claimNumber}
                {isCurrent && ' (generating...)'}
                {isProcessed && ' ✓'}
            </span>
        </div>
    );
};

export default ContentGenerationProgress;
