import { Card } from "@/components/ui";
import {
    useGamificationProgress,
    useLeaderboard,
    useAchievements
} from "../../hooks/useGamification";
import { cardStyles, textStyles, cn } from "../../theme";

// Local type definitions
type AchievementCategory = "learning" | "streak" | "collaboration" | "mastery" | "special";

interface Badge {
    id: string;
    name: string;
    icon: string;
    description?: string;
    rarity: "common" | "rare" | "epic" | "legendary";
}

interface Achievement {
    id: string;
    badge: Badge;
    earnedAt: string;
    category: AchievementCategory;
}

/**
 * Shows user's gamification progress including XP, level, streak, and badges.
 */
export function GamificationProgress() {
    const { data: progress, isLoading } = useGamificationProgress();

    if (isLoading) {
        return (
            <Card className={cn(cardStyles.base, "p-4 animate-pulse")}
            >
                <div className="h-4 bg-gray-200 rounded w-1/2 mb-2" />
                <div className="h-8 bg-gray-200 rounded" />
            </Card>
        );
    }

    if (!progress) return null;

    const xpProgress = progress.xpToNextLevel > 0
        ? ((progress.totalPoints % getXpForLevel(progress.level)) / progress.xpToNextLevel) * 100
        : 100;

    return (
        <Card className={cn(cardStyles.base, "p-4")}>
            <div className="flex justify-between items-center mb-3">
                <div>
                    <span className={cn("text-2xl font-bold text-purple-600")}>
                        Level {progress.level}
                    </span>
                    <span className={cn(textStyles.muted, "ml-2")}>
                        {progress.totalPoints.toLocaleString()} XP
                    </span>
                </div>
                <div className="flex items-center gap-2 text-orange-500">
                    🔥 <span className="font-bold">{progress.currentStreak}</span>
                    <span className={cn(textStyles.small, "text-gray-500 ml-1")}>
                        day streak
                    </span>
                </div>
            </div>

            <div className="mb-2">
                <div className="flex justify-between mb-1">
                    <span className={textStyles.small}>
                        Progress to Level {progress.level + 1}
                    </span>
                    <span className={textStyles.small}>
                        {progress.xpToNextLevel} XP to go
                    </span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-3">
                    <div
                        className="bg-gradient-to-r from-purple-500 to-blue-500 h-3 rounded-full transition-all"
                        style={{ width: `${xpProgress}%` }}
                    />
                </div>
            </div>

            {progress.badges.length > 0 && (
                <div className="mt-4">
                    <h4 className="text-sm font-medium text-gray-700 mb-2">
                        Recent Badges
                    </h4>
                    <div className="flex gap-2">
                        {progress.badges.slice(0, 5).map((achievement) => (
                            <BadgeIcon key={achievement.id} achievement={achievement} />
                        ))}
                    </div>
                </div>
            )}
        </Card>
    );
}

/**
 * Leaderboard component showing top learners.
 */
export function Leaderboard() {
    const { data, isLoading } = useLeaderboard("weekly");

    if (isLoading) {
        return (
            <Card className="p-4">
                <h3 className="text-lg font-semibold mb-4">Leaderboard</h3>
                <div className="space-y-2">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="h-12 bg-gray-100 rounded animate-pulse" />
                    ))}
                </div>
            </Card>
        );
    }

    const leaderboard = data?.leaderboard ?? [];

    return (
        <Card className="p-4">
            <h3 className="text-lg font-semibold mb-4">Weekly Leaderboard 🏆</h3>

            {leaderboard.length === 0 ? (
                <p className="text-gray-500 text-center py-4">
                    No data yet. Start learning to get on the board!
                </p>
            ) : (
                <div className="space-y-2">
                    {leaderboard.slice(0, 10).map((entry) => (
                        <div
                            key={entry.userId}
                            className={`flex items-center gap-3 p-3 rounded-lg ${entry.rank <= 3 ? "bg-yellow-50" : "bg-gray-50"
                                }`}
                        >
                            <RankBadge rank={entry.rank} />
                            <div className="flex-1">
                                <p className="font-medium">{entry.displayName}</p>
                                <p className="text-sm text-gray-500">
                                    {entry.points.toLocaleString()} pts • {entry.badges} badges
                                </p>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </Card>
    );
}

/**
 * Badge collection view.
 */
export function BadgeCollection() {
    const { data, isLoading } = useAchievements();

    if (isLoading) {
        return (
            <Card className="p-4">
                <h3 className="text-lg font-semibold mb-4">Your Badges</h3>
                <div className="grid grid-cols-4 gap-4">
                    {[1, 2, 3, 4].map((i) => (
                        <div key={i} className="h-16 bg-gray-100 rounded animate-pulse" />
                    ))}
                </div>
            </Card>
        );
    }

    const achievements = data?.achievements ?? [];

    return (
        <Card className="p-4">
            <h3 className="text-lg font-semibold mb-4">Your Badges</h3>

            {achievements.length === 0 ? (
                <p className="text-gray-500 text-center py-4">
                    No badges yet. Complete modules to earn badges!
                </p>
            ) : (
                <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 gap-4">
                    {achievements.map((achievement) => (
                        <BadgeCard key={achievement.id} achievement={achievement} />
                    ))}
                </div>
            )}
        </Card>
    );
}

interface BadgeIconProps {
    achievement: Achievement;
    size?: "sm" | "md" | "lg";
}

function BadgeIcon({ achievement, size = "sm" }: BadgeIconProps) {
    const sizeClasses = {
        sm: "w-8 h-8 text-lg",
        md: "w-12 h-12 text-2xl",
        lg: "w-16 h-16 text-3xl"
    };

    return (
        <div
            className={`${sizeClasses[size]} flex items-center justify-center rounded-full bg-gradient-to-br from-yellow-200 to-yellow-400 shadow-sm`}
            title={achievement.badge.name}
        >
            {achievement.badge.icon}
        </div>
    );
}

interface BadgeCardProps {
    achievement: Achievement;
}

function BadgeCard({ achievement }: BadgeCardProps) {
    return (
        <div className="flex flex-col items-center text-center">
            <BadgeIcon achievement={achievement} size="md" />
            <p className="text-xs font-medium mt-1 line-clamp-2">
                {achievement.badge.name}
            </p>
            <p className="text-xs text-gray-500">
                {new Date(achievement.earnedAt).toLocaleDateString()}
            </p>
        </div>
    );
}

function RankBadge({ rank }: { rank: number }) {
    if (rank === 1) {
        return <span className="text-2xl">🥇</span>;
    }
    if (rank === 2) {
        return <span className="text-2xl">🥈</span>;
    }
    if (rank === 3) {
        return <span className="text-2xl">🥉</span>;
    }
    return (
        <span className="w-8 h-8 flex items-center justify-center bg-gray-200 rounded-full font-bold text-gray-600">
            {rank}
        </span>
    );
}

function getXpForLevel(level: number): number {
    const thresholds = [0, 100, 250, 500, 1000, 2000, 4000, 8000, 16000, 32000];
    return thresholds[level] ?? thresholds[thresholds.length - 1]! * 2;
}
