/**
 * API Module Index
 * 
 * Re-exports all API clients and types.
 * 
 * @doc.type module
 * @doc.purpose API module exports
 * @doc.layer frontend
 */

export * from './client';
export * from './collections';
export * from './workflows';
export type {
    CollectionRecord,
    CreateRecordRequest,
    UpdateRecordRequest,
    ListRecordsRequest,
    ListRecordsResponse,
    BulkCreateRequest,
    BulkCreateResponse,
} from './collection-data-client';
export { dataCloudApi } from './data-cloud-api';

// Re-export default clients
export { default as apiClient } from './client';
export { default as collectionsApi } from './collections';
export { default as workflowsApi } from './workflows';
export { default as collectionDataClient } from './collection-data-client';
