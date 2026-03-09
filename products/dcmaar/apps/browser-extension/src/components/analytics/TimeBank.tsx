/**
 * @fileoverview Time Bank Component
 * 
 * Visual representation of remaining screen time for child dashboard.
 */

import React from 'react';

export interface TimeBankProps {
    /** Total allowed time in milliseconds */
    totalAllowed: number;
    /** Time used in milliseconds */
    timeUsed: number;
    /** Whether to show detailed breakdown */
    showDetails?: boolean;
}

/**
 * Format milliseconds to readable time
 */
function formatTime(ms: number): string {
    if (ms < 0) ms = 0;
    const hours = Math.floor(ms / 3600000);
    const mins = Math.round((ms % 3600000) / 60000);

    if (hours === 0) return `${mins}m`;
    if (mins === 0) return `${hours}h`;
    return `${hours}h ${mins}m`;
}

/**
 * Get color based on remaining percentage
 */
function getProgressColor(percentage: number): string {
    if (percentage > 50) return 'bg-green-500';
    if (percentage > 25) return 'bg-yellow-500';
    if (percentage > 10) return 'bg-orange-500';
    return 'bg-red-500';
}

/**
 * TimeBank
 * 
 * Shows remaining screen time as a visual "bank" for children.
 */
export function TimeBank({
    totalAllowed,
    timeUsed,
    showDetails = true,
}: TimeBankProps) {
    const remaining = Math.max(0, totalAllowed - timeUsed);
    const percentage = totalAllowed > 0 ? (remaining / totalAllowed) * 100 : 0;
    const usedPercentage = 100 - percentage;

    const getMessage = () => {
        if (percentage > 75) return "Great job! Plenty of time left! 🌟";
        if (percentage > 50) return "You're doing well! Keep it up! 👍";
        if (percentage > 25) return "Time is running low. Choose wisely! ⏰";
        if (percentage > 10) return "Almost out of time! 🔔";
        return "Time's up for today! 🛑";
    };

    return (
        <div className="bg-gradient-to-br from-blue-50 to-purple-50 dark:from-gray-800 dark:to-gray-900 rounded-2xl p-6">
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
                    <span className="text-2xl">🏦</span>
                    Time Bank
                </h3>
                <span className="text-sm text-gray-500 dark:text-gray-400">
                    Daily Limit: {formatTime(totalAllowed)}
                </span>
            </div>

            {/* Main display */}
            <div className="text-center mb-6">
                <div className="text-5xl font-bold text-gray-900 dark:text-white mb-2">
                    {formatTime(remaining)}
                </div>
                <p className="text-gray-600 dark:text-gray-400">remaining today</p>
            </div>

            {/* Progress bar */}
            <div className="relative h-8 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden mb-4">
                <div
                    className={`absolute left-0 top-0 h-full transition-all duration-500 ${getProgressColor(percentage)}`}
                    style={{ width: `${percentage}%` }}
                />
                <div className="absolute inset-0 flex items-center justify-center">
                    <span className="text-sm font-medium text-white drop-shadow-md">
                        {Math.round(percentage)}% remaining
                    </span>
                </div>
            </div>

            {/* Message */}
            <p className="text-center text-gray-700 dark:text-gray-300 font-medium mb-4">
                {getMessage()}
            </p>

            {/* Details */}
            {showDetails && (
                <div className="grid grid-cols-2 gap-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                    <div className="text-center">
                        <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                            {formatTime(timeUsed)}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400">Used Today</div>
                    </div>
                    <div className="text-center">
                        <div className="text-2xl font-bold text-green-600 dark:text-green-400">
                            {formatTime(remaining)}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400">Remaining</div>
                    </div>
                </div>
            )}
        </div>
    );
}
