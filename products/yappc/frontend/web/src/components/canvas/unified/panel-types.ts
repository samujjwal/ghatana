/**
 * Rail Panel Data Types
 * Shared interfaces for left rail panel data models
 */

export interface ComponentLibraryItem {
  id: string;
  name: string;
  category: string;
  description: string;
  tags: string[];
  usage: number;
  thumbnail?: string;
}

export interface InfrastructureResource {
  id: string;
  name: string;
  type: 'compute' | 'storage' | 'database' | 'network' | 'security';
  provider?: string;
  status: 'running' | 'stopped' | 'pending' | 'error';
  region?: string;
  cost?: number; // Monthly cost
}

export interface HistoryEntry {
  id: string;
  action: string;
  timestamp: Date | string; // Allow string for serialization
  details: string;
  canUndo: boolean;
  canRedo: boolean;
}

export interface FileItem {
  id: string;
  name: string;
  type: 'file' | 'folder';
  size?: number; // bytes
  modified: Date | string;
  path: string;
  extension?: string;
  mimeType?: string;
}

export interface DataSource {
  id: string;
  name: string;
  type: 'database' | 'api' | 'service';
  provider?: string;
  status: 'connected' | 'disconnected' | 'error';
  tables?: number;
  endpoints?: number;
}

export interface AISuggestion {
  id: string;
  title: string;
  description: string;
  type: 'optimization' | 'pattern' | 'improvement' | 'pattern-match';
  confidence: number;
  action: string;
}

export interface FavoriteItem {
  id: string;
  name: string;
  type: 'asset' | 'component' | 'design' | 'pattern';
  thumbnail?: string;
  dateAdded: Date | string;
  usageCount: number;
}
