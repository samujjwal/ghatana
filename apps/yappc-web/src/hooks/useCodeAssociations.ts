/**
 * Code Association Types and Hooks
 * 
 * @doc.type module
 * @doc.purpose Types and hooks for linking code to artifacts
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

/**
 * Code relationship types
 */
export type CodeRelationshipType =
    | 'IMPLEMENTATION'
    | 'TEST'
    | 'DOCUMENTATION'
    | 'MOCK';

/**
 * Code association data structure
 */
export interface CodeAssociation {
    id: string;
    artifactId: string;
    codeArtifactId: string;
    relationship: CodeRelationshipType;
    metadata?: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;

    // Populated fields
    codeArtifact?: {
        id: string;
        title: string;
        description?: string;
        content?: string;
        format?: string;
        type: 'CODE' | 'TEST' | 'SCRIPT';
    };
}

/**
 * Input for creating code associations
 */
export interface CreateCodeAssociationInput {
    artifactId: string;
    codeArtifactId: string;
    relationship: CodeRelationshipType;
    metadata?: Record<string, unknown>;
}

/**
 * Hook to fetch code associations for an artifact
 */
export function useCodeAssociations(artifactId: string) {
    return useQuery({
        queryKey: ['codeAssociations', artifactId],
        queryFn: async () => {
            const response = await fetch(`/api/artifacts/${artifactId}/code-associations`);
            if (!response.ok) throw new Error('Failed to fetch code associations');
            return response.json() as Promise<CodeAssociation[]>;
        },
        enabled: !!artifactId,
        staleTime: 30 * 1000, // 30 seconds
    });
}

/**
 * Hook to create a code association
 */
export function useCreateCodeAssociation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (input: CreateCodeAssociationInput) => {
            const response = await fetch(`/api/code-associations`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(input),
            });
            if (!response.ok) throw new Error('Failed to create code association');
            return response.json() as Promise<CodeAssociation>;
        },
        onSuccess: (data) => {
            // Invalidate queries for both artifacts
            queryClient.invalidateQueries({ queryKey: ['codeAssociations', data.artifactId] });
            queryClient.invalidateQueries({ queryKey: ['codeAssociations', data.codeArtifactId] });
            queryClient.invalidateQueries({ queryKey: ['artifacts'] });
        },
    });
}

/**
 * Hook to delete a code association
 */
export function useDeleteCodeAssociation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (associationId: string) => {
            const response = await fetch(`/api/code-associations/${associationId}`, {
                method: 'DELETE',
            });
            if (!response.ok) throw new Error('Failed to delete code association');
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['codeAssociations'] });
            queryClient.invalidateQueries({ queryKey: ['artifacts'] });
        },
    });
}

/**
 * Get icon for association relationship type
 */
export function getAssociationIcon(relationship: CodeRelationshipType): string {
    switch (relationship) {
        case 'IMPLEMENTATION':
            return '⚙️';
        case 'TEST':
            return '🧪';
        case 'DOCUMENTATION':
            return '📄';
        case 'MOCK':
            return '🎭';
        default:
            return '🔗';
    }
}

/**
 * Get color for association relationship type
 */
export function getAssociationColor(relationship: CodeRelationshipType): string {
    switch (relationship) {
        case 'IMPLEMENTATION':
            return 'primary';
        case 'TEST':
            return 'success';
        case 'DOCUMENTATION':
            return 'info';
        case 'MOCK':
            return 'warning';
        default:
            return 'default';
    }
}
