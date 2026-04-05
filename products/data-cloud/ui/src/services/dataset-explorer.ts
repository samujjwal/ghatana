/**
 * Dataset Explorer Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Dataset search, filter, and pagination
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface DatasetExplorerService {
  /** Search datasets across collections */
  searchDatasets(query: string, options?: DatasetSearchOptions): Promise<DatasetSearchResult>;
  
  /** Get dataset by ID */
  getDataset(datasetId: string): Promise<Dataset | null>;
  
  /** Get dataset schema */
  getDatasetSchema(datasetId: string): Promise<DatasetSchema>;
  
  /** Preview dataset data */
  previewDataset(datasetId: string, options?: PreviewOptions): Promise<DatasetPreview>;
  
  /** Get dataset statistics */
  getDatasetStats(datasetId: string): Promise<DatasetStats>;
  
  /** Filter datasets by criteria */
  filterDatasets(filters: DatasetFilter[]): Promise<Dataset[]>;
  
  /** Sort datasets by field */
  sortDatasets(sort: DatasetSort): Promise<Dataset[]>;
  
  /** Get related datasets */
  getRelatedDatasets(datasetId: string): Promise<Dataset[]>;
  
  /** Export dataset preview */
  exportPreview(datasetId: string, format: ExportFormat): Promise<Blob>;
}

/** Dataset entity */
export interface Dataset {
  id: string;
  name: string;
  description: string;
  collectionId: string;
  collectionName: string;
  schema: DatasetSchema;
  rowCount: number;
  size: number;
  createdAt: string;
  updatedAt: string;
  tags: string[];
  metadata: Record<string, unknown>;
}

/** Dataset schema */
export interface DatasetSchema {
  fields: DatasetField[];
  primaryKey?: string[];
  indexes: IndexInfo[];
}

/** Dataset field */
export interface DatasetField {
  name: string;
  type: FieldType;
  nullable: boolean;
  defaultValue?: unknown;
  description?: string;
  sampleValues?: unknown[];
  stats?: FieldStats;
}

/** Field type */
export type FieldType = 
  | 'string' 
  | 'number' 
  | 'integer' 
  | 'boolean' 
  | 'date' 
  | 'datetime' 
  | 'json' 
  | 'array' 
  | 'binary';

/** Field statistics */
export interface FieldStats {
  nullCount: number;
  uniqueCount: number;
  min?: number | string;
  max?: number | string;
  avg?: number;
  distribution?: Record<string, number>;
}

/** Index info */
export interface IndexInfo {
  name: string;
  fields: string[];
  type: 'btree' | 'hash' | 'gin';
}

/** Dataset search options */
export interface DatasetSearchOptions {
  collections?: string[];
  tags?: string[];
  minSize?: number;
  maxSize?: number;
  createdAfter?: string;
  createdBefore?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  limit?: number;
}

/** Dataset search result */
export interface DatasetSearchResult {
  datasets: Dataset[];
  total: number;
  page: number;
  limit: number;
  facets: SearchFacet[];
}

/** Search facet */
export interface SearchFacet {
  field: string;
  values: { value: string; count: number }[];
}

/** Preview options */
export interface PreviewOptions {
  limit?: number;
  offset?: number;
  columns?: string[];
  sample?: boolean;
}

/** Dataset preview */
export interface DatasetPreview {
  datasetId: string;
  columns: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
  truncated: boolean;
}

/** Dataset statistics */
export interface DatasetStats {
  datasetId: string;
  rowCount: number;
  columnCount: number;
  size: number;
  createdAt: string;
  lastModified: string;
  fieldStats: Record<string, FieldStats>;
  sampleQuality: number;
}

/** Dataset filter */
export interface DatasetFilter {
  field: string;
  operator: FilterOperator;
  value: unknown;
}

/** Filter operator */
export type FilterOperator = 
  | 'eq' 
  | 'ne' 
  | 'gt' 
  | 'gte' 
  | 'lt' 
  | 'lte' 
  | 'in' 
  | 'nin' 
  | 'contains' 
  | 'startsWith' 
  | 'endsWith' 
  | 'isNull' 
  | 'isNotNull';

/** Dataset sort */
export interface DatasetSort {
  field: string;
  order: 'asc' | 'desc';
  nulls?: 'first' | 'last';
}

/** Export format */
export type ExportFormat = 'csv' | 'json' | 'jsonl' | 'xlsx';
