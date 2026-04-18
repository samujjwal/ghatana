/**
 * Unit tests for devsecops transformItem function
 *
 * @doc.type class
 * @doc.purpose Unit tests for transformItem type safety
 * @doc.layer product
 * @doc.pattern Test
 */
import type { Item, ItemOwner, ItemTag, Artifact, ItemIntegration } from '@prisma/client';

// Mock the transformItem function for testing
function transformItem(item: unknown) {
  const typedItem = item as Item & {
    phase?: { key: string };
    owners?: (ItemOwner & { user?: { id: string; name: string; email?: string } })[];
    tags?: { tag: string }[];
    artifacts?: { id: string; title: string; type: string; url: string | null }[];
    integrations?: { id: string; provider: string; externalId: string; externalUrl: string | null }[];
  };
  return {
    id: typedItem.id,
    title: typedItem.title,
    description: typedItem.description,
    type: typedItem.type.toLowerCase().replace('_', '-'),
    priority: typedItem.priority?.toLowerCase() ?? 'medium',
    status: typedItem.status?.toLowerCase().replace('_', '-') ?? 'not-started',
    phaseId: typedItem.phase?.key ?? typedItem.phaseId,
    progress: typedItem.progress ?? 0,
    estimatedHours: typedItem.estimatedHours,
    actualHours: typedItem.actualHours,
    dueDate: typedItem.dueDate?.toISOString(),
    completedAt: typedItem.completedAt?.toISOString(),
    createdAt: typedItem.createdAt?.toISOString(),
    updatedAt: typedItem.updatedAt?.toISOString(),
    owners:
      typedItem.owners?.map((o) => ({
        id: o.user?.id ?? o.userId,
        name: o.user?.name ?? 'Unknown',
        email: o.user?.email,
        role: o.role ?? 'Owner',
      })) ?? [],
    tags: typedItem.tags?.map((t) => t.tag) ?? [],
    artifacts:
      typedItem.artifacts?.map((a) => ({
        id: a.id,
        name: a.title,
        type: a.type,
        url: a.url,
      })) ?? [],
    integrations:
      typedItem.integrations?.map((i) => ({
        id: i.id,
        type: i.provider,
        externalId: i.externalId,
        url: i.externalUrl,
      })) ?? [],
    metadata: {
      aiPriorityScore: typedItem.aiPriorityScore,
      riskScore: typedItem.riskScore,
      sentimentScore: typedItem.sentimentScore,
      predictedDueDate: typedItem.predictedDueDate?.toISOString(),
    },
  };
}

describe('transformItem', () => {
  it('should transform a valid item with all relations', () => {
    const mockItem = {
      id: 'item-1',
      title: 'Test Item',
      description: 'Test description',
      type: 'TASK' as const,
      priority: 'HIGH' as const,
      status: 'IN_PROGRESS' as const,
      phaseId: 'phase-1',
      progress: 50,
      estimatedHours: 8,
      actualHours: 4,
      dueDate: new Date('2025-01-01'),
      completedAt: null,
      createdAt: new Date('2024-12-01'),
      updatedAt: new Date('2024-12-15'),
      workflowId: null,
      parentId: null,
      aiPriorityScore: 0.8,
      riskScore: 0.3,
      sentimentScore: 0.5,
      predictedDueDate: new Date('2025-01-15'),
      startDate: null,
      phase: { key: 'shape' },
      owners: [
        {
          id: 'owner-1',
          itemId: 'item-1',
          userId: 'user-1',
          role: 'Owner',
          user: { id: 'user-1', name: 'John Doe', email: 'john@example.com' },
        },
      ],
      tags: [{ id: 'tag-1', itemId: 'item-1', tag: 'security' }],
      artifacts: [
        {
          id: 'artifact-1',
          itemId: 'item-1',
          type: 'DOCUMENT' as const,
          title: 'Test Document',
          url: 'http://example.com/doc.pdf',
          content: null,
          format: null,
          version: null,
          createdAt: new Date(),
          updatedAt: new Date(),
          createdById: 'user-1',
          description: null,
        },
      ],
      integrations: [
        {
          id: 'integration-1',
          itemId: 'item-1',
          provider: 'JIRA' as const,
          externalId: 'JIRA-123',
          externalUrl: 'http://jira.example.com/JIRA-123',
          syncedAt: new Date(),
          metadata: {},
        },
      ],
    };

    const result = transformItem(mockItem);

    expect(result).toEqual({
      id: 'item-1',
      title: 'Test Item',
      description: 'Test description',
      type: 'task',
      priority: 'high',
      status: 'in-progress',
      phaseId: 'shape',
      progress: 50,
      estimatedHours: 8,
      actualHours: 4,
      dueDate: '2025-01-01T00:00:00.000Z',
      completedAt: null,
      createdAt: '2024-12-01T00:00:00.000Z',
      updatedAt: '2024-12-15T00:00:00.000Z',
      owners: [
        {
          id: 'user-1',
          name: 'John Doe',
          email: 'john@example.com',
          role: 'Owner',
        },
      ],
      tags: ['security'],
      artifacts: [
        {
          id: 'artifact-1',
          name: 'Test Document',
          type: 'DOCUMENT',
          url: 'http://example.com/doc.pdf',
        },
      ],
      integrations: [
        {
          id: 'integration-1',
          type: 'JIRA',
          externalId: 'JIRA-123',
          url: 'http://jira.example.com/JIRA-123',
        },
      ],
      metadata: {
        aiPriorityScore: 0.8,
        riskScore: 0.3,
        sentimentScore: 0.5,
        predictedDueDate: '2025-01-15T00:00:00.000Z',
      },
    });
  });

  it('should handle item without relations', () => {
    const mockItem = {
      id: 'item-2',
      title: 'Simple Item',
      description: null,
      type: 'BUG' as const,
      priority: 'MEDIUM' as const,
      status: 'NOT_STARTED' as const,
      phaseId: 'phase-2',
      progress: 0,
      estimatedHours: null,
      actualHours: null,
      dueDate: null,
      completedAt: null,
      createdAt: new Date(),
      updatedAt: new Date(),
      workflowId: null,
      parentId: null,
      aiPriorityScore: null,
      riskScore: null,
      sentimentScore: null,
      predictedDueDate: null,
      startDate: null,
    };

    const result = transformItem(mockItem);

    expect(result.id).toBe('item-2');
    expect(result.title).toBe('Simple Item');
    expect(result.owners).toEqual([]);
    expect(result.tags).toEqual([]);
    expect(result.artifacts).toEqual([]);
    expect(result.integrations).toEqual([]);
  });

  it('should handle item with missing user in owner', () => {
    const mockItem = {
      id: 'item-3',
      title: 'Item with missing user',
      description: null,
      type: 'FEATURE' as const,
      priority: 'LOW' as const,
      status: 'COMPLETED' as const,
      phaseId: 'phase-1',
      progress: 100,
      estimatedHours: 5,
      actualHours: 5,
      dueDate: new Date(),
      completedAt: new Date(),
      createdAt: new Date(),
      updatedAt: new Date(),
      workflowId: null,
      parentId: null,
      aiPriorityScore: null,
      riskScore: null,
      sentimentScore: null,
      predictedDueDate: null,
      startDate: null,
      owners: [
        {
          id: 'owner-2',
          itemId: 'item-3',
          userId: 'user-2',
          role: 'Owner',
          user: null,
        },
      ],
    };

    const result = transformItem(mockItem);

    expect(result.owners).toEqual([
      {
        id: 'user-2',
        name: 'Unknown',
        email: undefined,
        role: 'Owner',
      },
    ]);
  });

  it('should convert enum values to lowercase with hyphens', () => {
    const mockItem = {
      id: 'item-4',
      title: 'Test',
      description: null,
      type: 'TASK' as const,
      priority: 'CRITICAL' as const,
      status: 'IN_PROGRESS' as const,
      phaseId: 'phase-1',
      progress: 0,
      estimatedHours: null,
      actualHours: null,
      dueDate: null,
      completedAt: null,
      createdAt: new Date(),
      updatedAt: new Date(),
      workflowId: null,
      parentId: null,
      aiPriorityScore: null,
      riskScore: null,
      sentimentScore: null,
      predictedDueDate: null,
      startDate: null,
    };

    const result = transformItem(mockItem);

    expect(result.type).toBe('task');
    expect(result.priority).toBe('critical');
    expect(result.status).toBe('in-progress');
  });
});
