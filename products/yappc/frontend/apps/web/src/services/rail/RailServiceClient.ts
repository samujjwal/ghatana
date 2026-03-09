/**
 * Rail Data Service
 *
 * Centralized service for fetching data for Unified Left Rail panels.
 * Supports switching between mock and real API modes.
 */

import axios, { AxiosInstance } from 'axios';
import {
  ComponentLibraryItem,
  InfrastructureResource,
  HistoryEntry,
  FileItem,
  DataSource,
  AISuggestion,
  FavoriteItem,
} from '../../components/canvas/unified/panel-types';

export class RailDataService {
  private client: AxiosInstance;
  private useMock: boolean = false;
  private cache: Map<string, { data: unknown; timestamp: number }> = new Map();
  private cacheDuration: number = 60 * 1000; // 1 minute

  constructor(baseURL: string = '/api/rail', useMock: boolean = false) {
    this.client = axios.create({
      baseURL,
      timeout: 10000,
    });
    this.useMock = useMock;
  }

  private getCached<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (entry && Date.now() - entry.timestamp < this.cacheDuration) {
      return entry.data as T;
    }
    return null;
  }

  private setCache<T>(key: string, data: T) {
    this.cache.set(key, { data, timestamp: Date.now() });
  }

  // --- Components ---
  async getComponents(
    category?: string,
    query?: string
  ): Promise<ComponentLibraryItem[]> {
    const cacheKey = `components:${category || 'all'}:${query || ''}`;
    const cached = this.getCached<ComponentLibraryItem[]>(cacheKey);
    if (cached) return cached;

    let result: ComponentLibraryItem[];
    if (this.useMock) {
      result = await this.mockComponents();
    } else {
      const response = await this.client.get<ComponentLibraryItem[]>(
        '/components',
        {
          params: { category, query },
        }
      );
      result = response.data;
    }

    this.setCache(cacheKey, result);
    return result;
  }

  // --- Infrastructure ---
  async getInfrastructure(): Promise<InfrastructureResource[]> {
    const cacheKey = 'infrastructure';
    const cached = this.getCached<InfrastructureResource[]>(cacheKey);
    if (cached) return cached;

    let result: InfrastructureResource[];
    if (this.useMock) {
      result = await this.mockInfrastructure();
    } else {
      const response =
        await this.client.get<InfrastructureResource[]>('/infrastructure');
      result = response.data;
    }
    this.setCache(cacheKey, result);
    return result;
  }

  // --- History ---
  async getHistory(): Promise<HistoryEntry[]> {
    if (this.useMock) return this.mockHistory();

    const response = await this.client.get<HistoryEntry[]>('/history');
    // Convert string timestamps to Date objects
    return response.data.map((entry) => ({
      ...entry,
      timestamp: new Date(entry.timestamp),
    }));
  }

  // --- Files ---
  async getFiles(path: string = '/'): Promise<FileItem[]> {
    const cacheKey = `files:${path}`;
    const cached = this.getCached<FileItem[]>(cacheKey);
    if (cached) return cached;

    let result: FileItem[];
    if (this.useMock) {
      result = await this.mockFiles(path);
    } else {
      const response = await this.client.get<FileItem[]>('/files', {
        params: { path },
      });
      result = response.data.map((file) => ({
        ...file,
        modified: new Date(file.modified),
      }));
    }
    this.setCache(cacheKey, result);
    return result;
  }

  // --- Data Sources ---
  async getDataSources(): Promise<DataSource[]> {
    const cacheKey = 'datasources';
    const cached = this.getCached<DataSource[]>(cacheKey);
    if (cached) return cached;

    let result: DataSource[];
    if (this.useMock) {
      result = await this.mockDataSources();
    } else {
      const response = await this.client.get<DataSource[]>('/datasources');
      result = response.data;
    }
    this.setCache(cacheKey, result);
    return result;
  }

  // --- AI Suggestions ---
  async getSuggestions(
    context?:
      | Record<string, unknown>
      | {
          context: Record<string, unknown>;
          selectedNodeIds: string[];
          forceRefresh?: boolean;
        }
  ): Promise<AISuggestion[]> {
    if (this.useMock) return this.mockSuggestions();

    const response = await this.client.post<AISuggestion[]>(
      '/ai/suggestions',
      context
    );
    return response.data;
  }

  // --- Favorites ---
  async getFavorites(): Promise<FavoriteItem[]> {
    const cacheKey = 'favorites';
    const cached = this.getCached<FavoriteItem[]>(cacheKey);
    if (cached) return cached;

    let result: FavoriteItem[];
    if (this.useMock) {
      result = await this.mockFavorites();
    } else {
      const response = await this.client.get<FavoriteItem[]>('/favorites');
      result = response.data.map((fav) => ({
        ...fav,
        dateAdded: new Date(fav.dateAdded),
      }));
    }
    this.setCache(cacheKey, result);
    return result;
  }

  // --- Mock Implementations (Moved from components) ---

  private async mockComponents(): Promise<ComponentLibraryItem[]> {
    await new Promise((r) => setTimeout(r, 300));
    return [
      {
        id: 'c1',
        name: 'Primary Button',
        category: 'Inputs',
        description: 'Standard primary action button',
        tags: ['ui', 'button', 'action'],
        usage: 1250,
      },
      {
        id: 'c2',
        name: 'User Card',
        category: 'Display',
        description: 'Profile card with avatar',
        tags: ['ui', 'card', 'profile'],
        usage: 850,
      },
      // ... more mock data could be added here
    ];
  }

  private async mockInfrastructure(): Promise<InfrastructureResource[]> {
    await new Promise((r) => setTimeout(r, 400));
    return [
      {
        id: 'res-1',
        name: 'app-server-prod-01',
        type: 'compute',
        status: 'running',
        region: 'us-east-1',
        cost: 45.2,
      },
      {
        id: 'res-2',
        name: 'primary-db-cluster',
        type: 'database',
        status: 'running',
        region: 'us-east-1',
        cost: 120.5,
      },
      {
        id: 'res-3',
        name: 'assets-bucket',
        type: 'storage',
        status: 'running',
        region: 'us-east-1',
        cost: 12.0,
      },
    ];
  }

  private async mockHistory(): Promise<HistoryEntry[]> {
    await new Promise((r) => setTimeout(r, 200));
    return [
      {
        id: 'h1',
        action: 'Add Node',
        timestamp: new Date(Date.now() - 1000 * 60 * 2), // 2 mins ago
        details: 'Added "User Service" to canvas',
        canUndo: true,
        canRedo: false,
      },
      {
        id: 'h2',
        action: 'Move Node',
        timestamp: new Date(Date.now() - 1000 * 60 * 15), // 15 mins ago
        details: 'Moved "Database" to (240, 500)',
        canUndo: true,
        canRedo: true,
      },
    ];
  }

  private async mockFiles(_path: string): Promise<FileItem[]> {
    console.debug('Fetching files for path:', _path); // Prevent unused var warning
    await new Promise((r) => setTimeout(r, 300));
    return [
      {
        id: 'f1',
        name: 'assets',
        type: 'folder',
        modified: new Date(),
        path: '/assets',
      },
      {
        id: 'f2',
        name: 'schema.json',
        type: 'file',
        size: 1024 * 25, // 25KB
        modified: new Date(),
        path: '/schema.json',
        extension: 'json',
      },
    ];
  }

  private async mockDataSources(): Promise<DataSource[]> {
    await new Promise((r) => setTimeout(r, 500));
    return [
      {
        id: 'ds1',
        name: 'Production DB',
        type: 'database',
        provider: 'PostgreSQL',
        status: 'connected',
        tables: 45,
      },
      {
        id: 'ds2',
        name: 'Stripe API',
        type: 'api',
        provider: 'REST',
        status: 'connected',
        endpoints: 12,
      },
      {
        id: 'ds3',
        name: 'Legacy Auth',
        type: 'service',
        provider: 'SOAP',
        status: 'error',
      },
    ];
  }

  private async mockSuggestions(): Promise<AISuggestion[]> {
    await new Promise((r) => setTimeout(r, 1500));
    return [
      {
        id: 's1',
        title: 'Optimize Database Connection',
        description:
          'Connection pooling is recommended for high-load services.',
        type: 'optimization',
        confidence: 0.89,
        action: 'apply-pool',
      },
      {
        id: 's2',
        title: 'Missing Error Handler',
        description: 'The auth service lacks a fallback error queue.',
        type: 'improvement',
        confidence: 0.75,
        action: 'add-dlq',
      },
    ];
  }

  private async mockFavorites(): Promise<FavoriteItem[]> {
    await new Promise((r) => setTimeout(r, 300));
    return [
      {
        id: 'fav1',
        name: 'Microservice Pattern',
        type: 'pattern',
        dateAdded: new Date(),
        usageCount: 42,
      },
      {
        id: 'fav2',
        name: 'Redis Cache',
        type: 'component',
        dateAdded: new Date(),
        usageCount: 15,
      },
    ];
  }
}

// Singleton instance
export const railService = new RailDataService(undefined, true); // Default to mock for now as requested by user context where backend might not be up
