import { useState, useEffect } from 'react';
import { dataCloudApi } from '@/lib/api/data-cloud-api';
import type { CollectionRecord } from '@/lib/api/collection-data-client';

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
export function useCollectionData(collectionId?: string, tenantId?: string, pageSize = 10) {
  const [records, setRecords] = useState<CollectionRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [total, setTotal] = useState<number>(0);

  useEffect(() => {
    let mounted = true;
    (async () => {
      setLoading(true);
      try {
        if (!collectionId) {
          setRecords([]);
          setTotal(0);
          return;
        }
        if (tenantId) {
          dataCloudApi.setTenantId(tenantId);
        }
        const res = await dataCloudApi.getCollectionEntities(collectionId, 0, pageSize);
        if (mounted) {
          const normalized = (res.data.items || []).map((item) => ({
            id: item.id,
            collectionId: item.collectionId,
            tenantId: item.tenantId ?? tenantId ?? 'default',
            data: item.data,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
            createdBy: item.createdBy ?? 'system',
            updatedBy: item.updatedBy ?? 'system',
            version: item.version ?? 1,
          }));
          setRecords(normalized);
          setTotal(res.data.total || 0);
        }
      } catch (err) {
        if (mounted) setError(err instanceof Error ? err.message : 'Failed');
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => { mounted = false; };
  }, [collectionId, tenantId, pageSize]);

  // Provide searchRecords to match earlier usage (simple filter)
  const searchRecords = (q: string) => {
    if (!q) return records;
    return records.filter((r) => JSON.stringify(r).toLowerCase().includes(q.toLowerCase()));
  };

  return { records, loading, error, total, searchRecords };
}
