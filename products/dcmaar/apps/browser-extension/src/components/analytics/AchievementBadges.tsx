/**
 * @fileoverview Achievement Badges Component
 * 
 * Displays unlocked achievements and progress for gamification.
 */

import React from 'react';

export interface Achievement {
    id: string;
    name: string;
    description: string;
    icon: string;
    category: 'safety' | 'productivity' | 'learning' | 'streak';
    threshold: number;
    unlockedAt?: number;
}

export interface AchievementBadgesProps {
    /** Unlocked achievements */
    achievements: Achievement[];
    /** Available (locked) achievements */
    availableAchievements?: Achievement[];
    /** Current user level */
    level?: number;
    /** Current XP */
    xp?: number;
    /** Whether to show locked achievements */
    showLocked?: boolean;
}

/**
 * Category colors
 */
const CATEGORY_COLORS: Record<string, { bg: string; border: string }> = {
    safety: { bg: 'bg-green-100 dark:bg-green-900/30', border: 'border-green-500' },
    productivity: { bg: 'bg-blue-100 dark:bg-blue-900/30', border: 'border-blue-500' },
    learning: { bg: 'bg-purple-100 dark:bg-purple-900/30', border: 'border-purple-500' },
    streak: { bg: 'bg-orange-100 dark:bg-orange-900/30', border: 'border-orange-500' },
};

/**
 * Format timestamp to relative time
 */
function formatUnlockTime(timestamp: number): string {
    const now = Date.now();
    const diff = now - timestamp;

    if (diff < 86400000) return 'Today';
    if (diff < 172800000) return 'Yesterday';
    if (diff < 604800000) return `${Math.floor(diff / 86400000)} days ago`;
    return new Date(timestamp).toLocaleDateString();
}

/**
 * AchievementBadges
 * 
 * Displays achievement badges with unlock status.
 */
export function AchievementBadges({
    achievements,
    availableAchievements = [],
    level = 1,
    xp = 0,
    showLocked = true,
}: AchievementBadgesProps) {
    const xpForNextLevel = (level * level) * 100;
    const xpProgress = (xp % xpForNextLevel) / xpForNextLevel * 100;

    return (
        <div className="space-y-6">
            {/* Level Progress */}
            <div className="bg-gradient-to-r from-purple-500 to-pink-500 rounded-xl p-4 text-white">
                <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                        <span className="text-3xl">⭐</span>
                        <div>
                            <div className="text-lg font-bold">Level {level}</div>
                            <div className="text-sm opacity-80">{xp} XP</div>
                        </div>
                    </div>
                    <div className="text-right text-sm opacity-80">
                        {Math.round(xpForNextLevel - (xp % xpForNextLevel))} XP to next level
                    </div>
                </div>
                <div className="h-2 bg-white/30 rounded-full overflow-hidden">
                    <div
                        className="h-full bg-white rounded-full transition-all duration-500"
                        style={{ width: `${xpProgress}%` }}
                    />
                </div>
            </div>

            {/* Unlocked Achievements */}
            {achievements.length > 0 && (
                <div>
                    <h4 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">
                        Unlocked ({achievements.length})
                    </h4>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        {achievements.map((achievement) => {
                            const colors = CATEGORY_COLORS[achievement.category] || CATEGORY_COLORS.safety;
                            return (
                                <div
                                    key={achievement.id}
                                    className={`
                    ${colors.bg} border-2 ${colors.border}
                    rounded-xl p-3 text-center transition-transform hover:scale-105
                  `}
                                >
                                    <div className="text-3xl mb-1">{achievement.icon}</div>
                                    <div className="font-medium text-gray-900 dark:text-white text-sm">
                                        {achievement.name}
                                    </div>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        {achievement.unlockedAt && formatUnlockTime(achievement.unlockedAt)}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Locked Achievements */}
            {showLocked && availableAchievements.length > 0 && (
                <div>
                    <h4 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">
                        Available ({availableAchievements.length})
                    </h4>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        {availableAchievements.map((achievement) => (
                            <div
                                key={achievement.id}
                                className="bg-gray-100 dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 border-dashed rounded-xl p-3 text-center opacity-60"
                            >
                                <div className="text-3xl mb-1 grayscale">🔒</div>
                                <div className="font-medium text-gray-600 dark:text-gray-400 text-sm">
                                    {achievement.name}
                                </div>
                                <div className="text-xs text-gray-400 dark:text-gray-500 mt-1">
                                    {achievement.description}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Empty State */}
            {achievements.length === 0 && availableAchievements.length === 0 && (
                <div className="text-center py-8">
                    <div className="text-4xl mb-2">🏆</div>
                    <p className="text-gray-500 dark:text-gray-400">
                        Start browsing safely to unlock achievements!
                    </p>
                </div>
            )}
        </div>
    );
}
