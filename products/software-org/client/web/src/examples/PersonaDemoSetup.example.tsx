/**
 * Example: Initialize Demo Persona Data on App Mount
 *
 * Copy this code to your App.tsx or main.tsx to enable persona dashboard testing.
 */

import { useEffect } from 'react';
import { useSetAtom } from 'jotai';
import {
    userProfileAtom,
    pendingTasksAtom,
    recentActivitiesAtom,
    pinnedFeaturesAtom,
} from '@/state/jotai/atoms';
import { initializeDemoData, clearDemoData } from '@/config/initializeDemoData';

/**
 * Example App Component with Demo Data Initialization
 */
function App() {
    const setUserProfile = useSetAtom(userProfileAtom);
    const setPendingTasks = useSetAtom(pendingTasksAtom);
    const setRecentActivities = useSetAtom(recentActivitiesAtom);
    const setPinnedFeatures = useSetAtom(pinnedFeaturesAtom);

    useEffect(() => {
        // Initialize with engineer persona
        // Change to 'admin', 'lead', or 'viewer' to test other personas
        initializeDemoData(
            'engineer', // <-- Change this to test different personas
            setUserProfile,
            setPendingTasks,
            setRecentActivities,
            setPinnedFeatures
        );

        // Cleanup on unmount (optional)
        return () => {
            clearDemoData(
                setUserProfile,
                setPendingTasks,
                setRecentActivities,
                setPinnedFeatures
            );
        };
    }, [setUserProfile, setPendingTasks, setRecentActivities, setPinnedFeatures]);

    return (
        <div>
            {/* Your app content */}
            <YourRouterOrContent />
        </div>
    );
}

/**
 * Example: Persona Switcher Dev Tool
 *
 * Add this component to your app during development to switch personas on the fly.
 */
import { useState } from 'react';

function PersonaSwitcherDevTool() {
    const [currentRole, setCurrentRole] = useState<'admin' | 'lead' | 'engineer' | 'viewer'>('engineer');
    const setUserProfile = useSetAtom(userProfileAtom);
    const setPendingTasks = useSetAtom(pendingTasksAtom);
    const setRecentActivities = useSetAtom(recentActivitiesAtom);
    const setPinnedFeatures = useSetAtom(pinnedFeaturesAtom);

    const handleSwitch = (role: typeof currentRole) => {
        initializeDemoData(
            role,
            setUserProfile,
            setPendingTasks,
            setRecentActivities,
            setPinnedFeatures
        );
        setCurrentRole(role);
    };

    const handleLogout = () => {
        clearDemoData(
            setUserProfile,
            setPendingTasks,
            setRecentActivities,
            setPinnedFeatures
        );
        setCurrentRole('engineer');
    };

    return (
        <div className="fixed bottom-4 right-4 bg-white dark:bg-neutral-800 p-4 rounded-lg shadow-2xl border-2 border-slate-200 dark:border-neutral-600 z-50">
            <p className="text-xs font-semibold mb-3 text-slate-900 dark:text-neutral-200">
                🎭 Persona Switcher
            </p>
            <div className="flex flex-col gap-2">
                <button
                    onClick={() => handleSwitch('admin')}
                    className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${currentRole === 'admin'
                            ? 'bg-red-600 text-white'
                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-red-100 dark:hover:bg-red-900'
                        }`}
                >
                    👑 Admin
                </button>
                <button
                    onClick={() => handleSwitch('lead')}
                    className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${currentRole === 'lead'
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-blue-100 dark:hover:bg-blue-900'
                        }`}
                >
                    📊 Lead
                </button>
                <button
                    onClick={() => handleSwitch('engineer')}
                    className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${currentRole === 'engineer'
                            ? 'bg-green-600 text-white'
                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-green-100 dark:hover:bg-green-900'
                        }`}
                >
                    🛠️ Engineer
                </button>
                <button
                    onClick={() => handleSwitch('viewer')}
                    className={`px-3 py-1.5 rounded text-sm font-medium transition-colors ${currentRole === 'viewer'
                            ? 'bg-purple-600 text-white'
                            : 'bg-slate-100 dark:bg-neutral-700 text-slate-700 dark:text-neutral-300 hover:bg-purple-100 dark:hover:bg-purple-900'
                        }`}
                >
                    👁️ Viewer
                </button>
                <hr className="my-1 border-slate-300 dark:border-neutral-600" />
                <button
                    onClick={handleLogout}
                    className="px-3 py-1.5 rounded text-sm font-medium bg-slate-200 dark:bg-slate-600 text-slate-700 dark:text-neutral-300 hover:bg-slate-300 dark:hover:bg-slate-500 transition-colors"
                >
                    🚪 Logout
                </button>
            </div>
        </div>
    );
}

/**
 * Usage in App.tsx:
 *
 * import PersonaSwitcherDevTool from './PersonaSwitcherDevTool';
 *
 * function App() {
 *   return (
 *     <>
 *       <YourAppContent />
 *       {process.env.NODE_ENV === 'development' && <PersonaSwitcherDevTool />}
 *     </>
 *   );
 * }
 */
