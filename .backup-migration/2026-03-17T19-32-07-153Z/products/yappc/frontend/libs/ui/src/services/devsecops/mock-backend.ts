/**
 * Mock Backend Service
 *
 * Provides a complete mock backend for DevSecOps Canvas development.
 * Simulates realistic API responses with configurable delays.
 *
 * @module services/devsecops/mock-backend
 */

import type {
  ApiResponse,
  Artifact,
  BulkOperationResult,
  ImplementationPlan,
  Item,
  ItemFilter,
  KPI,
  PaginatedResponse,
  Phase,
  SortConfig,
  User,
} from '@ghatana/yappc-types/devsecops';

/**
 * Mock data generator utilities
 */
class MockDataGenerator {
  private itemIdCounter = 1000;
  private artifactIdCounter = 5000;

  /**
   * Generate mock users
   */
  generateUsers(count: number = 10): User[] {
    const roles: User['role'][] = ['Developer', 'PM', 'Security', 'DevOps', 'QA'];
    const names = [
      'John Doe',
      'Jane Smith',
      'Bob Wilson',
      'Alice Johnson',
      'Carol Martinez',
      'David Chen',
      'Emma Davis',
      'Frank Miller',
      'Grace Lee',
      'Henry Taylor',
    ];

    return Array.from({ length: count }, (_, i) => ({
      id: `user-${i + 1}`,
      name: names[i] || `User ${i + 1}`,
      email: `user${i + 1}@example.com`,
      avatar: `https://i.pravatar.cc/150?img=${i + 1}`,
      role: roles[i % roles.length],
      teams: [`team-${(i % 3) + 1}`],
    }));
  }

  /**
   * Generate mock phases
   */
  generatePhases(): Phase[] {
    const phases = [
      { key: 'ideation', title: 'Ideation', description: 'Brainstorming and idea generation', color: '#8b5cf6' },
      { key: 'planning', title: 'Planning', description: 'Requirements and architecture', color: '#3b82f6' },
      { key: 'development', title: 'Development', description: 'Code implementation', color: '#10b981' },
      { key: 'security', title: 'Security', description: 'Security reviews and testing', color: '#ef4444' },
      { key: 'testing', title: 'Testing', description: 'QA and validation', color: '#f59e0b' },
      { key: 'deployment', title: 'Deployment', description: 'Release to production', color: '#06b6d4' },
      { key: 'operations', title: 'Operations', description: 'Monitoring and maintenance', color: '#6366f1' },
    ] as const;

    return phases.map((p, i) => ({
      id: `phase-${i + 1}`,
      key: p.key,
      title: p.title,
      description: p.description,
      order: i + 1,
      color: p.color,
      icon: `${p.key}-icon`,
      milestones: [],
      kpis: [],
    }));
  }

  /**
   * Generate mock items
   */
  generateItems(count: number, users: User[], phases: Phase[]): Item[] {
    const titles = [
      'User Authentication System',
      'Payment Gateway Integration',
      'Real-time Notifications',
      'Database Migration',
      'API Rate Limiting',
      'Performance Optimization',
      'Mobile App Redesign',
      'Security Audit',
      'CI/CD Pipeline',
      'Monitoring Dashboard',
    ];

    const types: Item['type'][] = ['feature', 'story', 'task', 'bug'];
    const priorities: Item['priority'][] = ['low', 'medium', 'high', 'critical'];
    const statuses: Item['status'][] = ['not-started', 'in-progress', 'in-review', 'completed'];

    return Array.from({ length: count }, (_, i) => {
      const id = `item-${this.itemIdCounter++}`;
      const title = `${titles[i % titles.length]  } #${i + 1}`;
      const phaseIndex = Math.floor((i / count) * phases.length);

      return {
        id,
        title,
        description: `Description for ${title}`,
        type: types[i % types.length],
        priority: priorities[i % priorities.length],
        status: statuses[i % statuses.length],
        phaseId: phases[phaseIndex]?.id || phases[0].id,
        owners: [users[i % users.length]],
        tags: [`tag-${(i % 3) + 1}`, 'devsecops'],
        createdAt: new Date(Date.now() - i * 86400000).toISOString(),
        updatedAt: new Date(Date.now() - i * 43200000).toISOString(),
        progress: Math.floor(Math.random() * 100),
        artifacts: this.generateArtifacts(2, id),
        estimatedHours: Math.floor(Math.random() * 40) + 8,
      };
    });
  }

  /**
   * Generate mock artifacts
   */
  generateArtifacts(count: number, itemId: string): Artifact[] {
    const types: Artifact['type'][] = ['diagram', 'document', 'code', 'design'];
    const titles = ['Architecture Diagram', 'Requirements Doc', 'Implementation', 'UI Mockups'];

    return Array.from({ length: count }, (_, i) => ({
      id: `artifact-${this.artifactIdCounter++}`,
      itemId,
      type: types[i % types.length],
      title: titles[i % titles.length],
      description: `Artifact description ${i + 1}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      createdBy: { id: 'user-1', name: 'John Doe', email: 'john@example.com', role: 'Developer' },
    }));
  }

  /**
   * Generate mock KPIs
   */
  generateKPIs(): KPI[] {
    return [
      {
        id: 'kpi-1',
        name: 'System Uptime',
        category: 'operations',
        value: 99.8,
        unit: '%',
        target: 99.9,
        trend: { direction: 'up', percentage: 0.2 },
      },
      {
        id: 'kpi-2',
        name: 'Deployment Frequency',
        category: 'velocity',
        value: 24,
        unit: ' per week',
        trend: { direction: 'up', percentage: 12.5 },
      },
      {
        id: 'kpi-3',
        name: 'Mean Time to Recovery',
        category: 'operations',
        value: 45,
        unit: ' min',
        trend: { direction: 'down', percentage: -8.3 },
      },
      {
        id: 'kpi-4',
        name: 'Code Coverage',
        category: 'quality',
        value: 87.5,
        unit: '%',
        target: 90,
        trend: { direction: 'up', percentage: 2.1 },
      },
      {
        id: 'kpi-5',
        name: 'Security Vulnerabilities',
        category: 'security',
        value: 5,
        threshold: { warning: 10, critical: 20 },
        trend: { direction: 'down', percentage: -3.2 },
      },
    ];
  }
}

/**
 * Mock Backend Service
 */
export class MockBackendService {
  private dataGenerator = new MockDataGenerator();
  private users: User[];
  private phases: Phase[];
  private items: Item[];
  private kpis: KPI[];
  private delay: number;

  /**
   *
   */
  constructor(delay: number = 300) {
    this.delay = delay;
    this.users = this.dataGenerator.generateUsers(10);
    this.phases = this.dataGenerator.generatePhases();
    this.items = this.dataGenerator.generateItems(30, this.users, this.phases);
    this.kpis = this.dataGenerator.generateKPIs();
  }

  /**
   * Simulate network delay
   */
  private async simulateDelay(ms?: number): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, ms ?? this.delay));
  }

  /**
   * Wrap response in API format
   */
  private wrapResponse<T>(data: T): ApiResponse<T> {
    return {
      data,
      success: true,
      metadata: {
        timestamp: new Date().toISOString(),
        requestId: Math.random().toString(36).substring(2),
      },
    };
  }

  // ============================================================================
  // Phase APIs
  // ============================================================================

  /**
   *
   */
  async getPhases(): Promise<ApiResponse<Phase[]>> {
    await this.simulateDelay();
    return this.wrapResponse(this.phases);
  }

  /**
   *
   */
  async getPhase(phaseId: string): Promise<ApiResponse<Phase | null>> {
    await this.simulateDelay();
    const phase = this.phases.find((p) => p.id === phaseId);
    return this.wrapResponse(phase || null);
  }

  // ============================================================================
  // Item APIs
  // ============================================================================

  /**
   *
   */
  async getItems(filter?: ItemFilter): Promise<ApiResponse<Item[]>> {
    await this.simulateDelay();

    let filtered = [...this.items];

    if (filter?.phaseIds?.length) {
      filtered = filtered.filter((item) => filter.phaseIds?.includes(item.phaseId));
    }

    if (filter?.status?.length) {
      filtered = filtered.filter((item) => filter.status?.includes(item.status));
    }

    if (filter?.priority?.length) {
      filtered = filtered.filter((item) => filter.priority?.includes(item.priority));
    }

    if (filter?.tags?.length) {
      filtered = filtered.filter((item) =>
        filter.tags?.some((tag) => item.tags.includes(tag))
      );
    }

    if (filter?.search) {
      const search = filter.search.toLowerCase();
      filtered = filtered.filter(
        (item) =>
          item.title.toLowerCase().includes(search) ||
          item.description?.toLowerCase().includes(search)
      );
    }

    return this.wrapResponse(filtered);
  }

  /**
   *
   */
  async getItem(itemId: string): Promise<ApiResponse<Item | null>> {
    await this.simulateDelay();
    const item = this.items.find((i) => i.id === itemId);
    return this.wrapResponse(item || null);
  }

  /**
   *
   */
  async createItem(data: Partial<Item>): Promise<ApiResponse<Item>> {
    await this.simulateDelay();

    const newItem: Item = {
      id: `item-${Date.now()}`,
      title: data.title || 'New Item',
      type: data.type || 'task',
      priority: data.priority || 'medium',
      status: data.status || 'not-started',
      phaseId: data.phaseId || this.phases[0].id,
      owners: data.owners || [this.users[0]],
      tags: data.tags || [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      progress: 0,
      artifacts: [],
      ...data,
    };

    this.items.push(newItem);
    return this.wrapResponse(newItem);
  }

  /**
   *
   */
  async updateItem(itemId: string, data: Partial<Item>): Promise<ApiResponse<Item>> {
    await this.simulateDelay();

    const index = this.items.findIndex((i) => i.id === itemId);
    if (index === -1) {
      throw new Error(`Item ${itemId} not found`);
    }

    this.items[index] = {
      ...this.items[index],
      ...data,
      updatedAt: new Date().toISOString(),
    };

    return this.wrapResponse(this.items[index]);
  }

  /**
   *
   */
  async deleteItem(itemId: string): Promise<ApiResponse<boolean>> {
    await this.simulateDelay();

    const index = this.items.findIndex((i) => i.id === itemId);
    if (index === -1) {
      return this.wrapResponse(false);
    }

    this.items.splice(index, 1);
    return this.wrapResponse(true);
  }

  /**
   *
   */
  async bulkUpdateItems(
    itemIds: string[],
    data: Partial<Item>
  ): Promise<ApiResponse<BulkOperationResult>> {
    await this.simulateDelay(this.delay * 2);

    let successCount = 0;
    const errors: BulkOperationResult['errors'] = [];

    for (const itemId of itemIds) {
      try {
        await this.updateItem(itemId, data);
        successCount++;
      } catch (error) {
        errors.push({
          itemId,
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    }

    return this.wrapResponse({
      successCount,
      failureCount: itemIds.length - successCount,
      errors: errors.length > 0 ? errors : undefined,
    });
  }

  // ============================================================================
  // KPI APIs
  // ============================================================================

  /**
   *
   */
  async getKPIs(phaseId?: string): Promise<ApiResponse<KPI[]>> {
    await this.simulateDelay();

    let kpis = [...this.kpis];
    if (phaseId) {
      kpis = kpis.filter((kpi) => kpi.phaseId === phaseId);
    }

    return this.wrapResponse(kpis);
  }

  // ============================================================================
  // User APIs
  // ============================================================================

  /**
   *
   */
  async getUsers(): Promise<ApiResponse<User[]>> {
    await this.simulateDelay();
    return this.wrapResponse(this.users);
  }

  /**
   *
   */
  async getCurrentUser(): Promise<ApiResponse<User>> {
    await this.simulateDelay();
    return this.wrapResponse(this.users[0]); // Return first user as current
  }
}

/**
 * Global mock backend instance
 */
export const mockBackend = new MockBackendService();
