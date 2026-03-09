/**
 * Demo Data Initialization for Persona Landing Page
 *
 * <p><b>Purpose</b><br>
 * Initializes Jotai atoms with mock persona data for testing and demonstration.
 * Call this function on app mount to populate user profile, activities, and tasks.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { initializeDemoData } from '@/config/initializeDemoData';
 *
 * // In App.tsx or main.tsx
 * useEffect(() => {
 *   initializeDemoData('engineer', store); // Initialize as engineer persona
 * }, []);
 * }</pre>
 *
 * @doc.type utility
 * @doc.purpose Initialize atoms with demo data
 * @doc.layer product
 * @doc.pattern Initialization
 */

import type { UserProfile, Activity, PendingTasks } from '@/state/jotai/atoms';
import type { Feature } from '@/shared/components/FeatureGrid';
import {
    getMockUserProfile,
    getMockPendingTasks,
    getMockRecentActivities,
    getMockPinnedFeatures,
} from './mockPersonaData';

/**
 * Initializes demo data for a specific persona.
 *
 * @param role - User role to initialize (admin, lead, engineer, viewer)
 * @param setUserProfile - Setter function for userProfileAtom
 * @param setPendingTasks - Setter function for pendingTasksAtom
 * @param setRecentActivities - Setter function for recentActivitiesAtom
 * @param setPinnedFeatures - Setter function for pinnedFeaturesAtom (expects Feature[])
 */
export function initializeDemoData(
    role: 'admin' | 'lead' | 'engineer' | 'viewer',
    setUserProfile: (profile: UserProfile) => void,
    setPendingTasks: (tasks: PendingTasks) => void,
    setRecentActivities: (activities: Activity[]) => void,
    setPinnedFeatures: (features: Feature[]) => void
): void {
    // Set user profile
    const userProfile = getMockUserProfile(role);
    setUserProfile(userProfile);

    // Set pending tasks (customize by role)
    const pendingTasks = getRolePendingTasks(role);
    setPendingTasks(pendingTasks);

    // Set recent activities (last 5)
    const activities = getMockRecentActivities(5);
    setRecentActivities(activities);

    // Set pinned features (4 features) - convert to Feature objects
    const pinnedFeatures = getMockPinnedFeatures(4);
    setPinnedFeatures(pinnedFeatures);

    console.log(`✅ Demo data initialized for ${role} persona`);
}

/**
 * Returns role-specific pending task counts.
 */
function getRolePendingTasks(role: 'admin' | 'lead' | 'engineer' | 'viewer'): PendingTasks {
    const tasksByRole: Record<string, PendingTasks> = {
        admin: getMockPendingTasks(6, 4, 0, 2), // High security alerts
        lead: getMockPendingTasks(8, 1, 2, 1), // High HITL approvals
        engineer: getMockPendingTasks(2, 0, 3, 4), // High failed workflows, model alerts
        viewer: getMockPendingTasks(0, 0, 0, 0), // No actionable tasks
    };

    return tasksByRole[role];
}

/**
 * Clears all demo data (useful for logout or switching users).
 */
export function clearDemoData(
    setUserProfile: (profile: UserProfile | null) => void,
    setPendingTasks: (tasks: PendingTasks) => void,
    setRecentActivities: (activities: Activity[]) => void,
    setPinnedFeatures: (features: Feature[]) => void
): void {
    setUserProfile(null);
    setPendingTasks({ hitlApprovals: 0, securityAlerts: 0, failedWorkflows: 0, modelAlerts: 0 });
    setRecentActivities([]);
    setPinnedFeatures([]);

    console.log('🗑️ Demo data cleared');
}

/**
 * Switches to a different persona (useful for testing).
 */
export function switchPersona(
    newRole: 'admin' | 'lead' | 'engineer' | 'viewer',
    setUserProfile: (profile: UserProfile) => void,
    setPendingTasks: (tasks: PendingTasks) => void
): void {
    const userProfile = getMockUserProfile(newRole);
    setUserProfile(userProfile);

    const pendingTasks = getRolePendingTasks(newRole);
    setPendingTasks(pendingTasks);

    console.log(`🔄 Switched to ${newRole} persona`);
}
