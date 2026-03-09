/**
 * Collections Hooks
 * 
 * TanStack Query hooks for collection management.
 * Provides data fetching, caching, and mutation capabilities.
 * 
 * @doc.type hook
 * @doc.purpose Collection data management hooks
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions } from '@tanstack/react-query';
import {
    collectionsApi,
    Collection,
    CreateCollectionDto,
    UpdateCollectionDto,
    CollectionQueryParams,
    PaginatedResponse,
} from '../lib/api';

/**
 * Query keys for collections
 */
export const collectionKeys = {
    all: ['collections'] as const,
    lists: () => [...collectionKeys.all, 'list'] as const,
    list: (params?: CollectionQueryParams) => [...collectionKeys.lists(), params] as const,
    details: () => [...collectionKeys.all, 'detail'] as const,
    detail: (id: string) => [...collectionKeys.details(), id] as const,
    schema: (id: string) => [...collectionKeys.detail(id), 'schema'] as const,
    stats: (id: string) => [...collectionKeys.detail(id), 'stats'] as const,
};

/**
 * Hook to fetch all collections
 * 
 * @example
 * ```tsx
 * const { data, isLoading, error } = useCollections({ status: 'active' });
 * ```
 */
export function useCollections(
    params?: CollectionQueryParams,
    options?: Omit<UseQueryOptions<PaginatedResponse<Collection>>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: collectionKeys.list(params),
        queryFn: () => collectionsApi.list(params),
        ...options,
    });
}

/**
 * Hook to fetch a single collection
 * 
 * @example
 * ```tsx
 * const { data: collection, isLoading } = useCollection('collection-id');
 * ```
 */
export function useCollection(
    id: string,
    options?: Omit<UseQueryOptions<Collection>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: collectionKeys.detail(id),
        queryFn: () => collectionsApi.get(id),
        enabled: !!id,
        ...options,
    });
}

/**
 * Hook to fetch collection schema
 */
export function useCollectionSchema(
    id: string,
    options?: Omit<UseQueryOptions<Record<string, unknown>>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: collectionKeys.schema(id),
        queryFn: () => collectionsApi.getSchema(id),
        enabled: !!id,
        ...options,
    });
}

/**
 * Hook to fetch collection stats
 */
export function useCollectionStats(
    id: string,
    options?: Omit<UseQueryOptions<{ entityCount: number; storageSize: number; lastUpdated: string }>, 'queryKey' | 'queryFn'>
) {
    return useQuery({
        queryKey: collectionKeys.stats(id),
        queryFn: () => collectionsApi.getStats(id),
        enabled: !!id,
        ...options,
    });
}

/**
 * Hook to create a collection
 * 
 * @example
 * ```tsx
 * const { mutate: createCollection, isPending } = useCreateCollection();
 * createCollection({ name: 'New Collection', ... });
 * ```
 */
export function useCreateCollection() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (data: CreateCollectionDto) => collectionsApi.create(data),
        onSuccess: () => {
            // Invalidate collections list to refetch
            queryClient.invalidateQueries({ queryKey: collectionKeys.lists() });
        },
    });
}

/**
 * Hook to update a collection
 * 
 * @example
 * ```tsx
 * const { mutate: updateCollection } = useUpdateCollection();
 * updateCollection({ id: 'collection-id', data: { name: 'Updated Name' } });
 * ```
 */
export function useUpdateCollection() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, data }: { id: string; data: UpdateCollectionDto }) =>
            collectionsApi.update(id, data),
        onSuccess: (updatedCollection) => {
            // Update the specific collection in cache
            queryClient.setQueryData(
                collectionKeys.detail(updatedCollection.id),
                updatedCollection
            );
            // Invalidate list to refetch
            queryClient.invalidateQueries({ queryKey: collectionKeys.lists() });
        },
    });
}

/**
 * Hook to delete a collection
 * 
 * @example
 * ```tsx
 * const { mutate: deleteCollection } = useDeleteCollection();
 * deleteCollection('collection-id');
 * ```
 */
export function useDeleteCollection() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (id: string) => collectionsApi.delete(id),
        onSuccess: (_, deletedId) => {
            // Remove from cache
            queryClient.removeQueries({ queryKey: collectionKeys.detail(deletedId) });
            // Invalidate list
            queryClient.invalidateQueries({ queryKey: collectionKeys.lists() });
        },
    });
}

/**
 * Hook to update collection schema
 */
export function useUpdateCollectionSchema() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ id, schema }: { id: string; schema: Record<string, unknown> }) =>
            collectionsApi.updateSchema(id, schema),
        onSuccess: (updatedCollection) => {
            queryClient.setQueryData(
                collectionKeys.detail(updatedCollection.id),
                updatedCollection
            );
            queryClient.invalidateQueries({
                queryKey: collectionKeys.schema(updatedCollection.id)
            });
        },
    });
}
