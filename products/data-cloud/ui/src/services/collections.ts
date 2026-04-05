/**
 * Collection Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Collection management and CRUD operations
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface CollectionService {
  /** Get all collections for tenant */
  getCollections(tenantId: string, options?: CollectionQueryOptions): Promise<Collection[]>;
  
  /** Get single collection by ID */
  getCollection(collectionId: string, tenantId: string): Promise<Collection | null>;
  
  /** Create new collection */
  createCollection(data: CreateCollectionRequest): Promise<Collection>;
  
  /** Update existing collection */
  updateCollection(collectionId: string, data: UpdateCollectionRequest): Promise<Collection>;
  
  /** Delete collection */
  deleteCollection(collectionId: string, tenantId: string): Promise<void>;
  
  /** Get collection schema */
  getCollectionSchema(collectionId: string): Promise<CollectionSchema>;
  
  /** Validate collection configuration */
  validateCollection(data: CreateCollectionRequest): Promise<ValidationResult>;
  
  /** Get collection statistics */
  getCollectionStats(collectionId: string): Promise<CollectionStats>;
  
  /** Export collection data */
  exportCollection(collectionId: string, format: ExportFormat): Promise<Blob>;
  
  /** Import data into collection */
  importCollection(collectionId: string, file: File, options?: ImportOptions): Promise<ImportResult>;
}

/** Collection entity */
export interface Collection {
  id: string;
  tenantId: string;
  name: string;
  description: string;
  schema: CollectionSchema;
  settings: CollectionSettings;
  entityCount: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  version: number;
}

/** Collection schema definition */
export interface CollectionSchema {
  fields: SchemaField[];
  indexes: IndexDefinition[];
  validations: ValidationRule[];
}

/** Schema field definition */
export interface SchemaField {
  name: string;
  type: FieldType;
  required: boolean;
  defaultValue?: unknown;
  constraints?: FieldConstraints;
  description?: string;
}

/** Field types */
export type FieldType = 
  | 'string' 
  | 'number' 
  | 'boolean' 
  | 'date' 
  | 'datetime' 
  | 'json' 
  | 'reference' 
  | 'array';

/** Field constraints */
export interface FieldConstraints {
  min?: number;
  max?: number;
  pattern?: string;
  enum?: string[];
  unique?: boolean;
}

/** Index definition */
export interface IndexDefinition {
  name: string;
  fields: string[];
  unique: boolean;
  type: 'btree' | 'hash' | 'gin';
}

/** Validation rule */
export interface ValidationRule {
  field: string;
  rule: string;
  message: string;
}

/** Collection settings */
export interface CollectionSettings {
  versioning: boolean;
  softDelete: boolean;
  auditLog: boolean;
  caching: boolean;
  cacheTtl?: number;
}

/** Collection query options */
export interface CollectionQueryOptions {
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  page?: number;
  limit?: number;
  filters?: Record<string, unknown>;
}

/** Create collection request */
export interface CreateCollectionRequest {
  tenantId: string;
  name: string;
  description?: string;
  schema: CollectionSchema;
  settings?: CollectionSettings;
}

/** Update collection request */
export interface UpdateCollectionRequest {
  name?: string;
  description?: string;
  schema?: Partial<CollectionSchema>;
  settings?: Partial<CollectionSettings>;
}

/** Validation result */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

/** Validation error */
export interface ValidationError {
  field: string;
  code: string;
  message: string;
}

/** Validation warning */
export interface ValidationWarning {
  field: string;
  code: string;
  message: string;
}

/** Collection statistics */
export interface CollectionStats {
  entityCount: number;
  storageSize: number;
  indexSize: number;
  lastModified: string;
  avgEntitySize: number;
}

/** Export format */
export type ExportFormat = 'csv' | 'json' | 'jsonl' | 'parquet';

/** Import options */
export interface ImportOptions {
  skipValidation?: boolean;
  upsert?: boolean;
  batchSize?: number;
  onError?: 'skip' | 'abort' | 'log';
}

/** Import result */
export interface ImportResult {
  total: number;
  inserted: number;
  updated: number;
  failed: number;
  errors: ImportError[];
}

/** Import error */
export interface ImportError {
  row: number;
  field: string;
  message: string;
}
