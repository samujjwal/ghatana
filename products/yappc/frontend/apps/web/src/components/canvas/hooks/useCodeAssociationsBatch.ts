/**
 * Batch Code Associations Hook
 *
 * Loads code associations for ALL artifacts in the workspace with a single
 * request, then stores the result in codeAssociationsAtom. This completely
 * eliminates the N+1 pattern where each ArtifactNode fired its own
 * useCodeAssociations(data.id) fetch.
 *
 * Usage:
 *   - Call once in CanvasWorkspace: `useCodeAssociationsBatch(projectId)`
 *   - In ArtifactNode: `useAtomValue(codeAssociationsAtom).get(data.id) ?? []`
 *
 * @doc.type hook
 * @doc.purpose Workspace-level batched code association loading
 * @doc.layer product
 * @doc.pattern Repository, Batch Query
 */

import { useEffect } from 'react';
import { useSetAtom, useAtomValue } from 'jotai';
import { useQuery } from '@tanstack/react-query';
import { nodesAtom, codeAssociationsAtom, type CodeLink } from '../workspace';

// ---------------------------------------------------------------------------
// API shape  (adapt to your actual API client)
// ---------------------------------------------------------------------------

interface BatchAssociationResponse {
    artifactId: string;
    links: CodeLink[];
}

async function fetchCodeAssociationsBatch(
    projectId: string,
    artifactIds: string[],
): Promise<BatchAssociationResponse[]> {
    if (artifactIds.length === 0) return [];

    const response = await fetch(
        `/api/projects/${projectId}/code-associations/batch`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ artifactIds }),
        }
    );

    if (!response.ok) {
        throw new Error(`Failed to fetch code associations: ${response.status}`);
    }

    return response.json() as Promise<BatchAssociationResponse[]>;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

export interface UseCodeAssociationsBatchOptions {
    /** How often to refetch (in ms). Default: 60s. */
    staleTime?: number;
    /** Auto-refresh interval (in ms). 0 = disabled. Default: 120_000. */
    refetchInterval?: number;
}

/**
 * Load code associations for all canvas nodes in a single batched request.
 * Call once at the workspace level — not inside individual node components.
 */
export function useCodeAssociationsBatch(
    projectId: string,
    options: UseCodeAssociationsBatchOptions = {}
) {
    const { staleTime = 60_000, refetchInterval = 120_000 } = options;

    const nodes = useAtomValue(nodesAtom);
    const setCodeAssociations = useSetAtom(codeAssociationsAtom);

    // Derive stable list of artifact IDs from node atoms
    const artifactIds = nodes.map((n) => n.id);
    // Stable cache key based on sorted IDs (order churn doesn't cause refetch)
    const stableKey = [...artifactIds].sort().join(',');

    const { data } = useQuery({
        queryKey: ['code-associations-batch', projectId, stableKey],
        queryFn: () => fetchCodeAssociationsBatch(projectId, artifactIds),
        staleTime,
        refetchInterval: refetchInterval === 0 ? false : refetchInterval,
        enabled: artifactIds.length > 0,
    });

    // Push results into the atom whenever query data arrives
    useEffect(() => {
        if (!data) return;
        const map = new Map<string, CodeLink[]>();
        data.forEach(({ artifactId, links }) => map.set(artifactId, links));
        setCodeAssociations(map);
    }, [data, setCodeAssociations]);
}
