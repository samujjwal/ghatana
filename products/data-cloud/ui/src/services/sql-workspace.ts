/**
 * SQL Workspace Service Interface
 * 
 * @doc.type interface
 * @doc.purpose SQL editor, results, and query history
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface SQLWorkspaceService {
  /** Execute SQL query */
  executeQuery(query: string, options?: QueryOptions): Promise<QueryResult>;
  
  /** Validate SQL query */
  validateQuery(query: string): Promise<ValidationResult>;
  
  /** Get query execution plan */
  getQueryPlan(query: string): Promise<QueryPlan>;
  
  /** Get query history */
  getQueryHistory(options?: HistoryOptions): Promise<QueryHistoryItem[]>;
  
  /** Save query to history */
  saveQuery(query: string, name?: string): Promise<QueryHistoryItem>;
  
  /** Delete query from history */
  deleteQuery(queryId: string): Promise<void>;
  
  /** Get query suggestions */
  getSuggestions(partial: string, context?: QueryContext): Promise<QuerySuggestion[]>;
  
  /** Get table/column autocomplete */
  getAutocomplete(partial: string): Promise<AutocompleteItem[]>;
  
  /** Export query results */
  exportResults(queryId: string, format: ExportFormat): Promise<Blob>;
  
  /** Cancel running query */
  cancelQuery(queryId: string): Promise<void>;
}

/** Query options */
export interface QueryOptions {
  limit?: number;
  timeout?: number;
  params?: Record<string, unknown>;
  useCache?: boolean;
}

/** Query result */
export interface QueryResult {
  queryId: string;
  query: string;
  status: 'success' | 'error' | 'cancelled' | 'timeout';
  columns: QueryColumn[];
  rows: Record<string, unknown>[];
  rowCount: number;
  executionTime: number;
  totalRows?: number;
  truncated: boolean;
  error?: QueryError;
  warnings?: QueryWarning[];
}

/** Query column */
export interface QueryColumn {
  name: string;
  type: string;
  nullable: boolean;
  label?: string;
}

/** Query error */
export interface QueryError {
  code: string;
  message: string;
  line?: number;
  column?: number;
  hint?: string;
}

/** Query warning */
export interface QueryWarning {
  code: string;
  message: string;
}

/** Validation result */
export interface ValidationResult {
  valid: boolean;
  errors: QueryError[];
  warnings: QueryWarning[];
  suggestions?: string[];
}

/** Query plan */
export interface QueryPlan {
  query: string;
  plan: PlanNode;
  estimatedCost: number;
  estimatedRows: number;
  indexUsage: IndexUsage[];
}

/** Plan node */
export interface PlanNode {
  type: string;
  cost: number;
  rows: number;
  children: PlanNode[];
  details: Record<string, unknown>;
}

/** Index usage */
export interface IndexUsage {
  table: string;
  index: string;
  columns: string[];
  scanType: 'index' | 'seq' | 'bitmap';
}

/** History options */
export interface HistoryOptions {
  search?: string;
  startDate?: string;
  endDate?: string;
  status?: 'success' | 'error';
  page?: number;
  limit?: number;
}

/** Query history item */
export interface QueryHistoryItem {
  id: string;
  query: string;
  name?: string;
  status: 'success' | 'error' | 'cancelled' | 'timeout';
  executionTime: number;
  rowCount: number;
  timestamp: string;
  favorite: boolean;
  tags: string[];
}

/** Query suggestion */
export interface QuerySuggestion {
  type: 'table' | 'column' | 'function' | 'keyword' | 'snippet';
  label: string;
  description?: string;
  insertText: string;
  kind: string;
  detail?: string;
  documentation?: string;
}

/** Query context for suggestions */
export interface QueryContext {
  tables?: string[];
  columns?: string[];
  position: { line: number; column: number };
}

/** Autocomplete item */
export interface AutocompleteItem {
  type: 'table' | 'column' | 'schema' | 'function';
  name: string;
  parent?: string;
  description?: string;
  dataType?: string;
}

/** Export format */
export type ExportFormat = 'csv' | 'json' | 'jsonl' | 'xlsx';
