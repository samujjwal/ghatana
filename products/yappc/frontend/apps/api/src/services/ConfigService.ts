/**
 * ConfigService - Thin Client for Java Backend Configuration API
 *
 * Delegates all configuration loading to the Java backend.
 * The Java backend (ConfigController + ConfigLoader) handles YAML parsing.
 * This avoids duplication of configuration loading logic.
 *
 * @see products/yappc/backend/api/.../ConfigController.java
 * @see products/yappc/backend/api/.../ConfigLoader.java
 */

// Define Types matching YAML structure
export interface PersonaConfig {
  id: string;
  label: string;
  description?: string;
  category?: string;
  icon?: string;
  color?: string;
  focusAreas: string[];
  permissions: string[];
}

export interface DomainConfig {
  id: string;
  name: string;
  description?: string;
  personas: string[];
  phases: string[];
  capabilities: string[];
  icon?: string;
  color?: string;
}

export interface TaskConfig {
  id: string;
  name: string;
  type: string;
  description: string;
  persona: string;
  domain: string;
  phase: string;
  agent: string;
  priority: string;
  estimated_duration: string;
  complexity: string;
  inputs: unknown[];
  outputs: unknown[];
  success_criteria: string[];
}

export class ConfigService {
  private static instance: ConfigService;
  private javaBackendUrl: string;
  private personasCache: PersonaConfig[] | null = null;
  private domainsCache: DomainConfig[] | null = null;

  private constructor() {
    // Java backend URL (configurable via environment)
    this.javaBackendUrl =
      process.env.JAVA_BACKEND_URL || 'http://localhost:7003';
  }

  public static getInstance(): ConfigService {
    if (!ConfigService.instance) {
      ConfigService.instance = new ConfigService();
    }
    return ConfigService.instance;
  }

  /**
   * Fetch data from Java backend API
   * Java ApiResponse format: { success: true, data: T, ... }
   */
  private async fetchFromJava<T>(endpoint: string): Promise<T> {
    try {
      const response = await fetch(`${this.javaBackendUrl}${endpoint}`);
      if (!response.ok) {
        throw new Error(
          `Java backend returned ${response.status}: ${response.statusText}`
        );
      }
      const json = await response.json();
      // Extract data from ApiResponse wrapper
      return json.data || json;
    } catch (error) {
      console.error(`Error fetching ${endpoint}:`, error);
      throw error;
    }
  }

  /**
   * Get all personas from Java backend
   * Endpoint: GET /api/config/personas
   */
  public async getPersonas(): Promise<PersonaConfig[]> {
    if (this.personasCache) return this.personasCache;
    try {
      this.personasCache = await this.fetchFromJava<PersonaConfig[]>(
        '/api/config/personas'
      );
      return this.personasCache;
    } catch (error) {
      console.error('Failed to fetch personas from Java backend:', error);
      return [];
    }
  }

  /**
   * Get persona by ID from Java backend
   * Endpoint: GET /api/config/personas/:id
   */
  public async getPersona(id: string): Promise<PersonaConfig | null> {
    try {
      return await this.fetchFromJava<PersonaConfig>(
        `/api/config/personas/${id}`
      );
    } catch (error) {
      console.error(`Failed to fetch persona ${id}:`, error);
      return null;
    }
  }

  /**
   * Get all domains from Java backend
   * Endpoint: GET /api/config/domains
   */
  public async getDomains(): Promise<DomainConfig[]> {
    if (this.domainsCache) return this.domainsCache;
    try {
      this.domainsCache = await this.fetchFromJava<DomainConfig[]>(
        '/api/config/domains'
      );
      return this.domainsCache;
    } catch (error) {
      console.error('Failed to fetch domains from Java backend:', error);
      return [];
    }
  }

  /**
   * Get domain by ID from Java backend
   * Endpoint: GET /api/config/domains/:id
   */
  public async getDomain(id: string): Promise<DomainConfig | null> {
    try {
      return await this.fetchFromJava<DomainConfig>(
        `/api/config/domains/${id}`
      );
    } catch (error) {
      console.error(`Failed to fetch domain ${id}:`, error);
      return null;
    }
  }

  /**
   * Get all capabilities from Java backend
   * Endpoint: GET /api/config/agents
   */
  public async getCapabilities(): Promise<unknown> {
    try {
      return await this.fetchFromJava<unknown>('/api/config/agents');
    } catch (error) {
      console.error('Failed to fetch capabilities from Java backend:', error);
      return { capabilities: [], mappings: {} };
    }
  }

  /**
   * Get capability by ID from Java backend
   */
  public async getCapability(id: string): Promise<any | null> {
    try {
      const caps = await this.getCapabilities();
      return caps.capabilities?.find((c: unknown) => c.id === id) || null;
    } catch (error) {
      console.error(`Failed to fetch capability ${id}:`, error);
      return null;
    }
  }
}
