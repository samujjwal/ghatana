import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

/**
 * Hook to get current user's gamification progress.
 */
export function useGamificationProgress() {
    return useQuery({
        queryKey: ["gamificationProgress"],
        queryFn: () => apiClient.getGamificationProgress()
    });
}

/**
 * Hook to get leaderboard.
 */
export function useLeaderboard(period?: "daily" | "weekly" | "monthly" | "allTime") {
    return useQuery({
        queryKey: ["leaderboard", period],
        queryFn: () => apiClient.getLeaderboard(period)
    });
}

/**
 * Hook to get user's achievements/badges.
 */
export function useAchievements() {
    return useQuery({
        queryKey: ["achievements"],
        queryFn: () => apiClient.getUserAchievements()
    });
}
