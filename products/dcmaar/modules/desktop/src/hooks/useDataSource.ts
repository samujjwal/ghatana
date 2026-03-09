/**
 * React hook for Data Source Service integration
 *
 * Provides unified access to multiple external data sources including
 * Prometheus, Elasticsearch, GraphQL, REST APIs, and databases.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  DataSourceService,
  DataSourceConfig,
  DataSourceType,
  QueryOptions,
  DataSourceResult,
} from '../services/dataSourceService';

// Singleton instance
let dataSourceService: DataSourceService | null = null;

const getDataSourceService = (): DataSourceService => {
  if (!dataSourceService) {
    dataSourceService = new DataSourceService();
  }
  return dataSourceService;
};

/**
 * Hook to register a new data source
 */
export const useRegisterDataSource = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (config: DataSourceConfig) => getDataSourceService().registerSource(config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
    },
  });
};

/**
 * Hook to remove a data source
 */
export const useRemoveDataSource = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => getDataSourceService().removeSource(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
    },
  });
};

/**
 * Hook to update a data source
 */
export const useUpdateDataSource = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, config }: { id: string; config: Partial<DataSourceConfig> }) =>
      getDataSourceService().updateSource(id, config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
    },
  });
};

/**
 * Hook to list all data sources
 */
export const useDataSources = () => {
  return useQuery({
    queryKey: ['dataSources'],
    queryFn: () => getDataSourceService().getSources(),
    staleTime: 30_000,
  });
};

/**
 * Hook to get a specific data source
 */
export const useDataSource = (id: string) => {
  return useQuery({
    queryKey: ['dataSource', id],
    queryFn: () => getDataSourceService().getSource(id),
    enabled: !!id,
    staleTime: 30_000,
  });
};

/**
 * Hook to test data source connection
 */
export const useTestDataSourceConnection = () => {
  return useMutation({
    mutationFn: (id: string) => getDataSourceService().testConnection(id),
  });
};

/**
 * Hook to query a data source
 */
export const useDataSourceQuery = (
  sourceId: string,
  query: string,
  options?: QueryOptions,
  enabled: boolean = true
) => {
  return useQuery({
    queryKey: ['dataSourceQuery', sourceId, query, options],
    queryFn: () => getDataSourceService().query(sourceId, query, options),
    enabled: enabled && !!sourceId && !!query,
    staleTime: 60_000,
  });
};

/**
 * Hook to execute a data source query (mutation)
 */
export const useExecuteDataSourceQuery = () => {
  return useMutation({
    mutationFn: ({ sourceId, query, options }: { sourceId: string; query: string; options?: QueryOptions }) =>
      getDataSourceService().query(sourceId, query, options),
  });
};

/**
 * Hook to query Prometheus specifically
 */
export const usePrometheusQuery = (sourceId: string, promQL: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, promQL, options);
};

/**
 * Hook to query Elasticsearch specifically
 */
export const useElasticsearchQuery = (sourceId: string, elasticQuery: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, elasticQuery, options);
};

/**
 * Hook to query GraphQL specifically
 */
export const useGraphQLQuery = (sourceId: string, graphqlQuery: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, graphqlQuery, options);
};

/**
 * Hook to query REST API specifically
 */
export const useRESTQuery = (sourceId: string, endpoint: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, endpoint, options);
};

/**
 * Hook to query PostgreSQL specifically
 */
export const usePostgreSQLQuery = (sourceId: string, sql: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, sql, options);
};

/**
 * Hook to query MySQL specifically
 */
export const useMySQLQuery = (sourceId: string, sql: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, sql, options);
};

/**
 * Hook to query MongoDB specifically
 */
export const useMongoDBQuery = (sourceId: string, mongoQuery: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, mongoQuery, options);
};

/**
 * Hook to query Redis specifically
 */
export const useRedisQuery = (sourceId: string, command: string, options?: QueryOptions) => {
  return useDataSourceQuery(sourceId, command, options);
};

/**
 * Hook to get data sources by type
 */
export const useDataSourcesByType = (type: DataSourceType) => {
  const { data: sources } = useDataSources();
  return sources?.filter((source: DataSourceConfig) => source.type === type) || [];
};

/**
 * Hook to get connection status for all data sources
 */
export const useDataSourcesHealth = () => {
  const { data: sources } = useDataSources();

  return useQuery({
    queryKey: ['dataSourcesHealth', sources?.map((s: DataSourceConfig) => s.id).join(',')],
    queryFn: async () => {
      if (!sources) return {};

      const service = getDataSourceService();
      const results: Record<string, boolean> = {};

      await Promise.all(
        sources.map(async (source: DataSourceConfig) => {
          try {
            results[source.id] = await service.testConnection(source.id);
          } catch {
            results[source.id] = false;
          }
        })
      );

      return results;
    },
    enabled: !!sources && sources.length > 0,
    staleTime: 30_000,
    refetchInterval: 60_000, // Recheck every minute
  });
};

/**
 * Hook for multi-source aggregation
 */
export const useMultiSourceQuery = (
  queries: Array<{ sourceId: string; query: string; options?: QueryOptions }>
) => {
  return useQuery({
    queryKey: ['multiSourceQuery', JSON.stringify(queries)],
    queryFn: async () => {
      const service = getDataSourceService();
      const results = await Promise.all(
        queries.map((q) => service.query(q.sourceId, q.query, q.options))
      );
      return results;
    },
    enabled: queries.length > 0,
    staleTime: 60_000,
  });
};

/**
 * Export the service instance for direct access if needed
 */
export const getDataSource = getDataSourceService;
