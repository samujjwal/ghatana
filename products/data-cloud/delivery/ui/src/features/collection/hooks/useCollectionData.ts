import {
  collectionDataClient,
  type CollectionRecord,
} from "@/lib/api/collection-data-client";
import SessionBootstrap from "@/lib/auth/session";
import { useQuery } from "@tanstack/react-query";

/**
 * Collection data hook for fetching entity records.
 *
 * @param collectionId - The collection ID to fetch records from
 * @param tenantId - The tenant ID (optional, uses default if not provided)
 * @param pageSize - Number of records per page (default: 10)
 * @returns Object with records, loading state, error, total count, and search function
 *
 * @doc.type hook
 * @doc.purpose Fetch collection entity records with pagination
 * @doc.layer frontend
 */
export function useCollectionData(
  collectionId?: string,
  tenantId?: string,
  pageSize = 10,
) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["collection-data", collectionId, tenantId, pageSize],
    enabled: Boolean(collectionId),
    staleTime: 30_000,
    queryFn: async () => {
      if (!collectionId) return { records: [] as CollectionRecord[], total: 0 };
      const resolvedTenantId = tenantId ?? SessionBootstrap.requireTenantId();
      const res = await collectionDataClient.listRecords(
        resolvedTenantId,
        collectionId,
        {
          offset: 0,
          limit: pageSize,
        },
      );
      const records: CollectionRecord[] = (res.items ?? []).map((item) => ({
        id: item.id,
        collectionId: item.collectionId,
        tenantId: item.tenantId,
        data: item.data,
        createdAt: item.createdAt,
        updatedAt: item.updatedAt,
        createdBy: item.createdBy,
        updatedBy: item.updatedBy,
        version: item.version,
      }));
      return { records, total: res.total ?? 0 };
    },
  });

  const records = data?.records ?? [];
  const total = data?.total ?? 0;

  const searchRecords = (q: string) => {
    if (!q) return records;
    return records.filter((r) =>
      JSON.stringify(r).toLowerCase().includes(q.toLowerCase()),
    );
  };

  return {
    records,
    loading: isLoading,
    error: error instanceof Error ? error.message : error ? "Failed" : null,
    total,
    searchRecords,
  };
}
