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
import { readStorage, writeStorage } from '../services/storage';

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
            const data = readStorage<LastOpenedProjects>('yappc_last_opened_projects');
            if (!data) return null;
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
            const data = readStorage<LastOpenedProjects>('yappc_last_opened_projects') ?? {};

            data[workspaceId] = {
                projectId,
                timestamp: Date.now(),
            };

            writeStorage('yappc_last_opened_projects', data);
        } catch (error) {
            console.error('Failed to set last opened project:', error);
        }
    }, []);

    /**
     * Clear last opened project for a workspace
     */
    const clearLastOpenedProject = useCallback((workspaceId: string) => {
        try {
            const data = readStorage<LastOpenedProjects>('yappc_last_opened_projects');
            if (!data) return;

            delete data[workspaceId];

            writeStorage('yappc_last_opened_projects', data);
        } catch (error) {
            console.error('Failed to clear last opened project:', error);
        }
    }, []);

    /**
     * Get all last opened projects
     */
    const getAllLastOpenedProjects = useCallback((): LastOpenedProjects => {
        try {
            return readStorage<LastOpenedProjects>('yappc_last_opened_projects') ?? {};
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
