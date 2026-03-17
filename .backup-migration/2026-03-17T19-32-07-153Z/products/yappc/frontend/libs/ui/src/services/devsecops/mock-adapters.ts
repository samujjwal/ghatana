/**
 * Mock Integration Adapters
 *
 * Mock implementations of integration adapters for development and testing.
 * These provide realistic fake data without requiring actual API connections.
 *
 * @module services/devsecops/mock-adapters
 */

import type {
  GitHubAdapter,
  JiraAdapter,
  SonarQubeAdapter,
} from './integration-adapter';
import type {
  GitHubCommit,
  GitHubIntegration,
  GitHubIssue,
  GitHubPullRequest,
  IntegrationConfig,
  JiraIntegration,
  SonarQubeIntegration,
} from '@ghatana/yappc-types/devsecops';


/**
 * Mock Jira Adapter
 */
export class MockJiraAdapter implements JiraAdapter {
  readonly provider = 'jira' as const;
  private config: IntegrationConfig;

  /**
   *
   */
  constructor(config?: Partial<IntegrationConfig>) {
    this.config = {
      id: 'mock-jira',
      provider: 'jira',
      name: 'Mock Jira',
      enabled: true,
      lastSyncedAt: new Date().toISOString(),
      syncStatus: 'success',
      ...config,
    };
  }

  /**
   *
   */
  isConfigured(): boolean {
    return this.config.enabled;
  }

  /**
   *
   */
  async testConnection(): Promise<boolean> {
    await new Promise((resolve) => setTimeout(resolve, 500));
    return true;
  }

  /**
   *
   */
  getConfig(): IntegrationConfig {
    return { ...this.config };
  }

  /**
   *
   */
  async updateConfig(config: Partial<IntegrationConfig>): Promise<void> {
    this.config = { ...this.config, ...config };
  }

  /**
   *
   */
  async syncItem(itemId: string, externalId: string): Promise<JiraIntegration> {
    await new Promise((resolve) => setTimeout(resolve, 300));

    const issueKey = externalId.startsWith('PROJ-') ? externalId : `PROJ-${Math.floor(Math.random() * 1000)}`;

    return {
      id: `jira-${itemId}`,
      itemId,
      provider: 'jira',
      externalId: issueKey,
      issueKey,
      projectKey: 'PROJ',
      issueType: 'Story',
      status: ['To Do', 'In Progress', 'Done'][Math.floor(Math.random() * 3)],
      assignee: 'john.doe@example.com',
      reporter: 'jane.smith@example.com',
      labels: ['devsecops', 'backend'],
      externalUrl: `https://jira.example.com/browse/${issueKey}`,
      syncedAt: new Date().toISOString(),
    };
  }

  /**
   *
   */
  async syncItems(items: Array<{ itemId: string; externalId: string }>): Promise<JiraIntegration[]> {
    return Promise.all(items.map((item) => this.syncItem(item.itemId, item.externalId)));
  }

  /**
   *
   */
  async getIssue(issueKey: string): Promise<JiraIntegration> {
    return this.syncItem(issueKey, issueKey);
  }

  /**
   *
   */
  async createIssue(data: {
    projectKey: string;
    summary: string;
    description?: string;
    issueType: string;
    priority?: string;
  }): Promise<JiraIntegration> {
    const issueKey = `${data.projectKey}-${Math.floor(Math.random() * 1000)}`;
    return this.syncItem(issueKey, issueKey);
  }

  /**
   *
   */
  async updateIssueStatus(issueKey: string, status: string): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 200));
    console.log(`Updated ${issueKey} to ${status}`);
  }

  /**
   *
   */
  async searchIssues(jql: string): Promise<JiraIntegration[]> {
    await new Promise((resolve) => setTimeout(resolve, 400));
    // Return mock results
    return Array.from({ length: 3 }, (_, i) => ({
      id: `jira-${i}`,
      itemId: `item-${i}`,
      provider: 'jira' as const,
      externalId: `PROJ-${100 + i}`,
      issueKey: `PROJ-${100 + i}`,
      projectKey: 'PROJ',
      issueType: 'Story',
      status: 'In Progress',
      externalUrl: `https://jira.example.com/browse/PROJ-${100 + i}`,
      syncedAt: new Date().toISOString(),
    }));
  }
}

/**
 * Mock GitHub Adapter
 */
export class MockGitHubAdapter implements GitHubAdapter {
  readonly provider = 'github' as const;
  private config: IntegrationConfig;

  /**
   *
   */
  constructor(config?: Partial<IntegrationConfig>) {
    this.config = {
      id: 'mock-github',
      provider: 'github',
      name: 'Mock GitHub',
      enabled: true,
      lastSyncedAt: new Date().toISOString(),
      syncStatus: 'success',
      ...config,
    };
  }

  /**
   *
   */
  isConfigured(): boolean {
    return this.config.enabled;
  }

  /**
   *
   */
  async testConnection(): Promise<boolean> {
    await new Promise((resolve) => setTimeout(resolve, 500));
    return true;
  }

  /**
   *
   */
  getConfig(): IntegrationConfig {
    return { ...this.config };
  }

  /**
   *
   */
  async updateConfig(config: Partial<IntegrationConfig>): Promise<void> {
    this.config = { ...this.config, ...config };
  }

  /**
   *
   */
  async syncItem(itemId: string, externalId: string): Promise<GitHubIntegration> {
    const [owner, repo] = externalId.split('/');
    const prs = await this.getPullRequests(owner || 'org', repo || 'repo', 'all');
    const commits = await this.getCommits(owner || 'org', repo || 'repo', 'main');

    return {
      id: `github-${itemId}`,
      itemId,
      provider: 'github',
      externalId,
      repository: externalId,
      pullRequests: prs.slice(0, 2),
      commits: commits.slice(0, 5),
      externalUrl: `https://github.com/${externalId}`,
      syncedAt: new Date().toISOString(),
    };
  }

  /**
   *
   */
  async syncItems(items: Array<{ itemId: string; externalId: string }>): Promise<GitHubIntegration[]> {
    return Promise.all(items.map((item) => this.syncItem(item.itemId, item.externalId)));
  }

  /**
   *
   */
  async getRepository(owner: string, repo: string) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    return {
      name: repo,
      description: `Mock repository for ${repo}`,
      url: `https://github.com/${owner}/${repo}`,
      defaultBranch: 'main',
    };
  }

  /**
   *
   */
  async getPullRequests(owner: string, repo: string, state: 'open' | 'closed' | 'all' = 'all'): Promise<GitHubPullRequest[]> {
    await new Promise((resolve) => setTimeout(resolve, 400));

    return Array.from({ length: 3 }, (_, i) => ({
      number: 100 + i,
      title: `Feature: Add new component #${100 + i}`,
      state: ['open', 'closed', 'merged'][i % 3] as 'open' | 'closed' | 'merged',
      url: `https://github.com/${owner}/${repo}/pull/${100 + i}`,
      author: `developer${i + 1}`,
      createdAt: new Date(Date.now() - i * 86400000).toISOString(),
      updatedAt: new Date(Date.now() - i * 43200000).toISOString(),
      reviewers: ['reviewer1', 'reviewer2'],
      checks: [
        { name: 'CI', status: 'success', conclusion: 'success' },
        { name: 'Tests', status: 'success', conclusion: 'success' },
      ],
    }));
  }

  /**
   *
   */
  async getCommits(owner: string, repo: string, ref: string): Promise<GitHubCommit[]> {
    await new Promise((resolve) => setTimeout(resolve, 300));

    return Array.from({ length: 5 }, (_, i) => ({
      sha: Math.random().toString(36).substring(2, 42),
      message: `feat: Implement feature ${i + 1}`,
      author: `developer${(i % 3) + 1}`,
      timestamp: new Date(Date.now() - i * 3600000).toISOString(),
      url: `https://github.com/${owner}/${repo}/commit/${Math.random().toString(36).substring(2, 10)}`,
    }));
  }

  /**
   *
   */
  async getIssues(owner: string, repo: string, state: 'open' | 'closed' | 'all' = 'all'): Promise<GitHubIssue[]> {
    await new Promise((resolve) => setTimeout(resolve, 300));

    return Array.from({ length: 2 }, (_, i) => ({
      number: 50 + i,
      title: `Issue: Fix bug in module ${i + 1}`,
      state: i % 2 === 0 ? 'open' : 'closed',
      url: `https://github.com/${owner}/${repo}/issues/${50 + i}`,
      assignees: ['developer1'],
      labels: ['bug', 'priority:high'],
    }));
  }

  /**
   *
   */
  async createPullRequest(data: {
    owner: string;
    repo: string;
    title: string;
    head: string;
    base: string;
    body?: string;
  }): Promise<GitHubPullRequest> {
    await new Promise((resolve) => setTimeout(resolve, 500));

    return {
      number: Math.floor(Math.random() * 1000),
      title: data.title,
      state: 'open',
      url: `https://github.com/${data.owner}/${data.repo}/pull/${Math.floor(Math.random() * 1000)}`,
      author: 'current-user',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      reviewers: [],
      checks: [],
    };
  }
}

/**
 * Mock SonarQube Adapter
 */
export class MockSonarQubeAdapter implements SonarQubeAdapter {
  readonly provider = 'sonarqube' as const;
  private config: IntegrationConfig;

  /**
   *
   */
  constructor(config?: Partial<IntegrationConfig>) {
    this.config = {
      id: 'mock-sonarqube',
      provider: 'sonarqube',
      name: 'Mock SonarQube',
      enabled: true,
      lastSyncedAt: new Date().toISOString(),
      syncStatus: 'success',
      ...config,
    };
  }

  /**
   *
   */
  isConfigured(): boolean {
    return this.config.enabled;
  }

  /**
   *
   */
  async testConnection(): Promise<boolean> {
    await new Promise((resolve) => setTimeout(resolve, 500));
    return true;
  }

  /**
   *
   */
  getConfig(): IntegrationConfig {
    return { ...this.config };
  }

  /**
   *
   */
  async updateConfig(config: Partial<IntegrationConfig>): Promise<void> {
    this.config = { ...this.config, ...config };
  }

  /**
   *
   */
  async syncItem(itemId: string, externalId: string): Promise<SonarQubeIntegration> {
    const metrics = await this.getProjectMetrics(externalId);

    return {
      id: `sonarqube-${itemId}`,
      itemId,
      provider: 'sonarqube',
      externalId,
      projectKey: externalId,
      metrics,
      lastAnalysis: new Date(Date.now() - 3600000).toISOString(),
      externalUrl: `https://sonarqube.example.com/dashboard?id=${externalId}`,
      syncedAt: new Date().toISOString(),
    };
  }

  /**
   *
   */
  async syncItems(items: Array<{ itemId: string; externalId: string }>): Promise<SonarQubeIntegration[]> {
    return Promise.all(items.map((item) => this.syncItem(item.itemId, item.externalId)));
  }

  /**
   *
   */
  async getProjectMetrics(projectKey: string): Promise<SonarQubeIntegration['metrics']> {
    await new Promise((resolve) => setTimeout(resolve, 400));

    return {
      coverage: 75 + Math.random() * 20,
      bugs: Math.floor(Math.random() * 10),
      vulnerabilities: Math.floor(Math.random() * 5),
      codeSmells: Math.floor(Math.random() * 50),
      technicalDebt: `${Math.floor(Math.random() * 10)}h`,
      qualityGateStatus: ['OK', 'WARN', 'ERROR'][Math.floor(Math.random() * 3)] as 'OK' | 'WARN' | 'ERROR',
    };
  }

  /**
   *
   */
  async getQualityGateStatus(projectKey: string): Promise<'OK' | 'ERROR' | 'WARN'> {
    const metrics = await this.getProjectMetrics(projectKey);
    return metrics.qualityGateStatus || 'OK';
  }

  /**
   *
   */
  async getIssues(projectKey: string, type?: 'BUG' | 'VULNERABILITY' | 'CODE_SMELL') {
    await new Promise((resolve) => setTimeout(resolve, 300));

    const count = Math.floor(Math.random() * 20);
    return {
      total: count,
      items: Array.from({ length: Math.min(count, 5) }, (_, i) => ({
        key: `${projectKey}-issue-${i}`,
        severity: ['MAJOR', 'MINOR', 'CRITICAL', 'INFO'][Math.floor(Math.random() * 4)],
        message: `${type || 'BUG'} found in component`,
        component: `src/components/Component${i}.tsx`,
      })),
    };
  }
}
