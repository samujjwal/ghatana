/**
 * useCollectionData hook.
 *
 * @doc.type hook
 * @doc.purpose Fetches and manages collection data state for a given collection ID or all collections
 * @doc.layer product
 * @doc.pattern Data Hook
 */
import { useState, useEffect } from 'react';
import { dataCloudApi } from '../lib/api/data-cloud-api';
import type { Collection } from '../lib/api/collections';

export function useCollectionData(collectionId?: string) {
  const [data, setData] = useState<Collection | Collection[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = collectionId 
          ? await dataCloudApi.getCollectionById(collectionId)
          : await dataCloudApi.getCollections();
        setData(response.data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [collectionId]);

  return { data, loading, error };
}
