/**
 * API Module Index
 *
 * Re-exports all API clients and types.
 *
 * @doc.type module
 * @doc.purpose API module exports
 * @doc.layer frontend
 */

export * from "./client";
export type {
  BulkCreateRequest,
  BulkCreateResponse,
  CollectionRecord,
  CreateRecordRequest,
  ListRecordsRequest,
  ListRecordsResponse,
  UpdateRecordRequest,
} from "./collection-data-client";
export * from "./collections";
export * from "./context";
export * from "./workflows";

// Re-export default clients
export { default as apiClient } from "./client";
export { default as collectionDataClient } from "./collection-data-client";
export { default as collectionsApi } from "./collections";
export { default as workflowsApi } from "./workflows";
