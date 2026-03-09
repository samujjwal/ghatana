/**
 * React Router v7 loaders for persona management
 *
 * Purpose:
 * Provides data loaders for SSR-ready persona routes. Loaders prefetch
 * data before component render, enabling fast page loads and SEO.
 *
 * Loaders:
 * - personasLoader: Prefetch role definitions + persona preference
 * - workspacePersonaLoader: Prefetch persona for specific workspace
 *
 * Integration:
 * - Works with React Query for automatic caching
 * - Integrates with React Router v7 loader pattern
 * - Compatible with SSR (when backend supports it)
 */

import { QueryClient } from '@tanstack/react-query';
import { personaKeys } from '@/lib/hooks/usePersonaQueries';
import * as personaService from '@/lib/api/persona.service';
import type { LoaderFunctionArgs } from 'react-router';

/**
 * Loader for personas page
 *
 * Prefetches:
 * - All role definitions (cached for 1 hour)
 * - Current workspace's persona preference (if workspaceId in params)
 *
 * @example
 * // In Router.tsx
 * {
 *   path: '/personas/:workspaceId?',
 *   element: <PersonasPage />,
 *   loader: personasLoader(queryClient)
 * }
 */
export function personasLoader(queryClient: QueryClient) {
    return async ({ params }: LoaderFunctionArgs) => {
        const workspaceId = params.workspaceId || 'default';

        // Prefetch role definitions (will use cache if available)
        const rolesPromise = queryClient.fetchQuery({
            queryKey: personaKeys.roles(),
            queryFn: personaService.getAllRoles,
            staleTime: 1000 * 60 * 60, // 1 hour
        });

        // Prefetch persona preference (will use cache if available)
        const preferencePromise = queryClient.fetchQuery({
            queryKey: personaKeys.preference(workspaceId),
            queryFn: () => personaService.getPersonaPreference(workspaceId),
            staleTime: 1000 * 60 * 5, // 5 minutes
        });

        // Wait for both to complete (parallel)
        await Promise.all([rolesPromise, preferencePromise]);

        return null; // Data is in React Query cache
    };
}

/**
 * Loader for workspace-specific persona page
 *
 * Prefetches:
 * - All role definitions
 * - Workspace's persona preference
 * - Workspace access verification (optional)
 *
 * @example
 * // In Router.tsx
 * {
 *   path: '/workspaces/:workspaceId/personas',
 *   element: <PersonasPage />,
 *   loader: workspacePersonaLoader(queryClient)
 * }
 */
export function workspacePersonaLoader(queryClient: QueryClient) {
    return async ({ params }: LoaderFunctionArgs) => {
        const { workspaceId } = params;

        if (!workspaceId) {
            throw new Response('Workspace ID required', { status: 400 });
        }

        // Prefetch role definitions
        const rolesPromise = queryClient.fetchQuery({
            queryKey: personaKeys.roles(),
            queryFn: personaService.getAllRoles,
            staleTime: 1000 * 60 * 60,
        });

        // Prefetch workspace persona preference
        const preferencePromise = queryClient.fetchQuery({
            queryKey: personaKeys.preference(workspaceId),
            queryFn: () => personaService.getPersonaPreference(workspaceId),
            staleTime: 1000 * 60 * 5,
        });

        // Optional: Verify workspace access
        // const accessPromise = queryClient.fetchQuery({
        //   queryKey: ['workspace', workspaceId, 'access'],
        //   queryFn: () => personaService.verifyWorkspaceAccess(workspaceId),
        // });

        await Promise.all([rolesPromise, preferencePromise]);

        return { workspaceId };
    };
}
