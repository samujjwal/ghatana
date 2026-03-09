/**
 * Last Opened Project Hook
 * 
 * Track and retrieve last opened project per workspace.
 * 
 * @doc.type hook
 * @doc.purpose Track last opened projects
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback } from 'react';

const STORAGE_KEY = 'yappc_last_opened_projects';

interface LastOpenedProjects {
    [workspaceId: string]: {
        projectId: string;
        timestamp: number;
    };
}

/**
 * Hook to manage last opened project per workspace
 */
export function useLastOpenedProject() {
    /**
     * Get last opened project for a workspace
     */
    const getLastOpenedProject = useCallback((workspaceId: string): string | null => {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            if (!stored) return null;

            const data: LastOpenedProjects = JSON.parse(stored);
            return data[workspaceId]?.projectId || null;
        } catch (error) {
            console.error('Failed to get last opened project:', error);
            return null;
        }
    }, []);

    /**
     * Set last opened project for a workspace
     */
    const setLastOpenedProject = useCallback((workspaceId: string, projectId: string) => {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            const data: LastOpenedProjects = stored ? JSON.parse(stored) : {};

            data[workspaceId] = {
                projectId,
                timestamp: Date.now(),
            };

            localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        } catch (error) {
            console.error('Failed to set last opened project:', error);
        }
    }, []);

    /**
     * Clear last opened project for a workspace
     */
    const clearLastOpenedProject = useCallback((workspaceId: string) => {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            if (!stored) return;

            const data: LastOpenedProjects = JSON.parse(stored);
            delete data[workspaceId];

            localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        } catch (error) {
            console.error('Failed to clear last opened project:', error);
        }
    }, []);

    /**
     * Get all last opened projects
     */
    const getAllLastOpenedProjects = useCallback((): LastOpenedProjects => {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            return stored ? JSON.parse(stored) : {};
        } catch (error) {
            console.error('Failed to get all last opened projects:', error);
            return {};
        }
    }, []);

    return {
        getLastOpenedProject,
        setLastOpenedProject,
        clearLastOpenedProject,
        getAllLastOpenedProjects,
    };
}
