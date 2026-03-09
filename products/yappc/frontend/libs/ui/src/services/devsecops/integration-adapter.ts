/**
 * DevSecOps Integration Adapter System
 *
 * Flexible adapter pattern for third-party integrations (Jira, GitHub, SonarQube, etc.)
 * Allows easy switching between mock and real implementations.
 *
 * @module services/devsecops/integration-adapter
 */

import type {
  GitHubCommit,
  GitHubIntegration,
  GitHubIssue,
  GitHubPullRequest,
  IntegrationConfig,
  IntegrationProvider,
  ItemIntegration,
  JiraIntegration,
  SonarQubeIntegration,
} from '@ghatana/yappc-types/devsecops';

/**
 * Base integration adapter interface
 */
export interface IntegrationAdapter<T extends ItemIntegration = ItemIntegration> {
  /** Provider identifier */
  readonly provider: IntegrationProvider;

  /** Check if the adapter is configured and ready */
  isConfigured(): boolean;

  /** Test the connection to the external service */
  testConnection(): Promise<boolean>;

  /** Sync data for a specific item */
  syncItem(itemId: string, externalId: string): Promise<T>;

  /** Batch sync multiple items */
  syncItems(items: Array<{ itemId: string; externalId: string }>): Promise<T[]>;

  /** Get integration configuration */
  getConfig(): IntegrationConfig;

  /** Update integration configuration */
  updateConfig(config: Partial<IntegrationConfig>): Promise<void>;
}

/**
 * Jira adapter interface
 */
export interface JiraAdapter extends IntegrationAdapter<JiraIntegration> {
  provider: 'jira';

  /** Get issue details from Jira */
  getIssue(issueKey: string): Promise<JiraIntegration>;

  /** Create a new Jira issue */
  createIssue(data: {
    projectKey: string;
    summary: string;
    description?: string;
    issueType: string;
    priority?: string;
  }): Promise<JiraIntegration>;

  /** Update issue status */
  updateIssueStatus(issueKey: string, status: string): Promise<void>;

  /** Search issues by JQL */
  searchIssues(jql: string): Promise<JiraIntegration[]>;
}

/**
 * GitHub adapter interface
 */
export interface GitHubAdapter extends IntegrationAdapter<GitHubIntegration> {
  provider: 'github';

  /** Get repository information */
  getRepository(owner: string, repo: string): Promise<{
    name: string;
    description?: string;
    url: string;
    defaultBranch: string;
  }>;

  /** Get pull requests */
  getPullRequests(
    owner: string,
    repo: string,
    state?: 'open' | 'closed' | 'all'
  ): Promise<GitHubPullRequest[]>;

  /** Get commits for a PR or branch */
  getCommits(
    owner: string,
    repo: string,
    ref: string
  ): Promise<GitHubCommit[]>;

  /** Get issues */
  getIssues(
    owner: string,
    repo: string,
    state?: 'open' | 'closed' | 'all'
  ): Promise<GitHubIssue[]>;

  /** Create a pull request */
  createPullRequest(data: {
    owner: string;
    repo: string;
    title: string;
    head: string;
    base: string;
    body?: string;
  }): Promise<GitHubPullRequest>;
}

/**
 * SonarQube adapter interface
 */
export interface SonarQubeAdapter extends IntegrationAdapter<SonarQubeIntegration> {
  provider: 'sonarqube';

  /** Get project metrics */
  getProjectMetrics(projectKey: string): Promise<SonarQubeIntegration['metrics']>;

  /** Get quality gate status */
  getQualityGateStatus(projectKey: string): Promise<'OK' | 'ERROR' | 'WARN'>;

  /** Get issues/vulnerabilities */
  getIssues(projectKey: string, type?: 'BUG' | 'VULNERABILITY' | 'CODE_SMELL'): Promise<{
    total: number;
    items: Array<{
      key: string;
      severity: string;
      message: string;
      component: string;
    }>;
  }>;
}

/**
 * Adapter registry for managing all integration adapters
 */
export class IntegrationAdapterRegistry {
  private adapters = new Map<IntegrationProvider, IntegrationAdapter>();

  /**
   * Register an adapter
   */
  register<T extends IntegrationAdapter>(adapter: T): void {
    this.adapters.set(adapter.provider, adapter);
  }

  /**
   * Get adapter by provider
   */
  get<T extends IntegrationAdapter = IntegrationAdapter>(
    provider: IntegrationProvider
  ): T | undefined {
    return this.adapters.get(provider) as T | undefined;
  }

  /**
   * Check if provider is registered
   */
  has(provider: IntegrationProvider): boolean {
    return this.adapters.has(provider);
  }

  /**
   * Get all registered adapters
   */
  getAll(): IntegrationAdapter[] {
    return Array.from(this.adapters.values());
  }

  /**
   * Remove adapter
   */
  unregister(provider: IntegrationProvider): boolean {
    return this.adapters.delete(provider);
  }

  /**
   * Clear all adapters
   */
  clear(): void {
    this.adapters.clear();
  }
}

/**
 * Global adapter registry instance
 */
export const adapterRegistry = new IntegrationAdapterRegistry();

/**
 * Hook to get adapter by provider
 */
export function getAdapter<T extends IntegrationAdapter = IntegrationAdapter>(
  provider: IntegrationProvider
): T | undefined {
  return adapterRegistry.get<T>(provider);
}

/**
 * Helper to create a typed adapter getter
 */
export function createAdapterGetter<T extends IntegrationAdapter>(
  provider: IntegrationProvider
): () => T | undefined {
  return () => getAdapter<T>(provider);
}
