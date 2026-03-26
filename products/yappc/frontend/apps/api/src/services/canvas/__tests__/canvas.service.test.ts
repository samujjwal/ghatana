/**
 * CanvasService Tests
 *
 * Unit tests for all canvas document and page domain operations with Prisma
 * mocked at the constructor boundary — no database connection required.
 */

import { CanvasService } from '../canvas.service';
import type {
  CreateDocumentInput,
  UpdateDocumentInput,
  CreatePageInput,
  UpdatePageInput,
} from '../canvas.service';

// ---------------------------------------------------------------------------
// Prisma Mock Factory
// ---------------------------------------------------------------------------

function makePrismaMock() {
  return {
    canvasDocument: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    page: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      findFirst: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
  };
}

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

const NOW = new Date('2025-03-01T12:00:00Z');

function docRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'doc-1',
    projectId: 'proj-1',
    createdById: 'user-1',
    name: 'My Canvas',
    description: null,
    content: '{"nodes":[]}',
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  };
}

function pageRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 'page-1',
    projectId: 'proj-1',
    createdById: 'user-1',
    name: 'Home',
    path: '/home',
    layout: 'default',
    content: '{"components":[]}',
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// getDocument
// ---------------------------------------------------------------------------

describe('CanvasService.getDocument', () => {
  it('returns null when document not found', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findUnique.mockResolvedValue(null);
    const svc = new CanvasService(prisma as never);

    expect(await svc.getDocument('nonexistent')).toBeNull();
    expect(prisma.canvasDocument.findUnique).toHaveBeenCalledWith({
      where: { id: 'nonexistent' },
      include: { project: true, createdBy: true },
    });
  });

  it('returns document with parsed content and ISO dates', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findUnique.mockResolvedValue(docRow());
    const svc = new CanvasService(prisma as never);

    const result = await svc.getDocument('doc-1');

    expect(result).not.toBeNull();
    expect(result!.content).toEqual({ nodes: [] });
    expect(result!.createdAt).toBe(NOW.toISOString());
    expect(result!.updatedAt).toBe(NOW.toISOString());
  });

  it('returns empty content object when content is null', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findUnique.mockResolvedValue(
      docRow({ content: null })
    );
    const svc = new CanvasService(prisma as never);

    const result = await svc.getDocument('doc-1');
    expect(result!.content).toEqual({});
  });
});

// ---------------------------------------------------------------------------
// listDocuments
// ---------------------------------------------------------------------------

describe('CanvasService.listDocuments', () => {
  it('returns an empty array when no documents exist', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findMany.mockResolvedValue([]);
    const svc = new CanvasService(prisma as never);

    expect(await svc.listDocuments('proj-1')).toEqual([]);
  });

  it('returns mapped documents with parsed content', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findMany.mockResolvedValue([
      docRow({ id: 'doc-1', content: '{"a":1}' }),
      docRow({ id: 'doc-2', content: null }),
    ]);
    const svc = new CanvasService(prisma as never);

    const results = await svc.listDocuments('proj-1');
    expect(results).toHaveLength(2);
    expect(results[0].content).toEqual({ a: 1 });
    expect(results[1].content).toEqual({});
  });

  it('queries with correct project filter and ordering', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.findMany.mockResolvedValue([]);
    const svc = new CanvasService(prisma as never);

    await svc.listDocuments('proj-xyz');
    expect(prisma.canvasDocument.findMany).toHaveBeenCalledWith({
      where: { projectId: 'proj-xyz' },
      orderBy: { updatedAt: 'desc' },
    });
  });
});

// ---------------------------------------------------------------------------
// createDocument
// ---------------------------------------------------------------------------

describe('CanvasService.createDocument', () => {
  it('creates document and stores content as JSON string', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.create.mockResolvedValue(
      docRow({ content: '{"x":42}' })
    );
    const svc = new CanvasService(prisma as never);

    const input: CreateDocumentInput = {
      projectId: 'proj-1',
      name: 'My Canvas',
      content: { x: 42 },
    };
    await svc.createDocument('user-1', input);

    expect(prisma.canvasDocument.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          content: '{"x":42}',
          createdById: 'user-1',
          name: 'My Canvas',
        }),
      })
    );
  });

  it('stores empty JSON string when no content provided', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.create.mockResolvedValue(docRow({ content: '{}' }));
    const svc = new CanvasService(prisma as never);

    await svc.createDocument('user-1', { projectId: 'proj-1', name: 'Empty' });

    expect(prisma.canvasDocument.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ content: '{}' }),
      })
    );
  });

  it('returns the raw content object on creation', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.create.mockResolvedValue(docRow());
    const svc = new CanvasService(prisma as never);

    const result = await svc.createDocument('user-1', {
      projectId: 'proj-1',
      name: 'New',
      content: { nodes: ['a'] },
    });
    expect(result.content).toEqual({ nodes: ['a'] });
  });
});

// ---------------------------------------------------------------------------
// updateDocument
// ---------------------------------------------------------------------------

describe('CanvasService.updateDocument', () => {
  it('only sends fields that are explicitly provided', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.update.mockResolvedValue(docRow({ name: 'Updated' }));
    const svc = new CanvasService(prisma as never);

    const input: UpdateDocumentInput = { name: 'Updated' };
    await svc.updateDocument('doc-1', input);

    const callData = prisma.canvasDocument.update.mock.calls[0][0].data;
    expect(callData.name).toBe('Updated');
    expect('content' in callData).toBe(false);
  });

  it('stringifies content when provided', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.update.mockResolvedValue(
      docRow({ content: '{"nodes":[1]}' })
    );
    const svc = new CanvasService(prisma as never);

    await svc.updateDocument('doc-1', { content: { nodes: [1] } });

    const callData = prisma.canvasDocument.update.mock.calls[0][0].data;
    expect(callData.content).toBe('{"nodes":[1]}');
  });

  it('returns document with parsed content', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.update.mockResolvedValue(
      docRow({ content: '{"updated":true}' })
    );
    const svc = new CanvasService(prisma as never);

    const result = await svc.updateDocument('doc-1', { name: 'New Name' });
    expect(result.content).toEqual({ updated: true });
  });
});

// ---------------------------------------------------------------------------
// deleteDocument
// ---------------------------------------------------------------------------

describe('CanvasService.deleteDocument', () => {
  it('deletes document and returns true', async () => {
    const prisma = makePrismaMock();
    prisma.canvasDocument.delete.mockResolvedValue(docRow());
    const svc = new CanvasService(prisma as never);

    expect(await svc.deleteDocument('doc-1')).toBe(true);
    expect(prisma.canvasDocument.delete).toHaveBeenCalledWith({
      where: { id: 'doc-1' },
    });
  });
});

// ---------------------------------------------------------------------------
// getPage
// ---------------------------------------------------------------------------

describe('CanvasService.getPage', () => {
  it('returns null when page not found', async () => {
    const prisma = makePrismaMock();
    prisma.page.findUnique.mockResolvedValue(null);
    const svc = new CanvasService(prisma as never);

    expect(await svc.getPage('missing')).toBeNull();
  });

  it('returns page with parsed content and ISO dates', async () => {
    const prisma = makePrismaMock();
    prisma.page.findUnique.mockResolvedValue(
      pageRow({ content: '{"components":[1,2]}' })
    );
    const svc = new CanvasService(prisma as never);

    const result = await svc.getPage('page-1');
    expect(result!.content).toEqual({ components: [1, 2] });
    expect(result!.createdAt).toBe(NOW.toISOString());
  });
});

// ---------------------------------------------------------------------------
// listPages
// ---------------------------------------------------------------------------

describe('CanvasService.listPages', () => {
  it('queries pages ordered by path asc', async () => {
    const prisma = makePrismaMock();
    prisma.page.findMany.mockResolvedValue([]);
    const svc = new CanvasService(prisma as never);

    await svc.listPages('proj-1');
    expect(prisma.page.findMany).toHaveBeenCalledWith({
      where: { projectId: 'proj-1' },
      orderBy: { path: 'asc' },
    });
  });

  it('returns pages with parsed content', async () => {
    const prisma = makePrismaMock();
    prisma.page.findMany.mockResolvedValue([
      pageRow({ id: 'p-1', content: '{"a":1}' }),
      pageRow({ id: 'p-2', content: null }),
    ]);
    const svc = new CanvasService(prisma as never);

    const results = await svc.listPages('proj-1');
    expect(results[0].content).toEqual({ a: 1 });
    expect(results[1].content).toEqual({});
  });
});

// ---------------------------------------------------------------------------
// pageByPath
// ---------------------------------------------------------------------------

describe('CanvasService.pageByPath', () => {
  it('returns null when no page matches the path', async () => {
    const prisma = makePrismaMock();
    prisma.page.findFirst.mockResolvedValue(null);
    const svc = new CanvasService(prisma as never);

    expect(await svc.pageByPath('proj-1', '/missing')).toBeNull();
  });

  it('queries with correct project + path filter', async () => {
    const prisma = makePrismaMock();
    prisma.page.findFirst.mockResolvedValue(pageRow());
    const svc = new CanvasService(prisma as never);

    await svc.pageByPath('proj-1', '/home');
    expect(prisma.page.findFirst).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { projectId: 'proj-1', path: '/home' },
      })
    );
  });

  it('returns page with parsed content', async () => {
    const prisma = makePrismaMock();
    prisma.page.findFirst.mockResolvedValue(
      pageRow({ content: '{"found":true}' })
    );
    const svc = new CanvasService(prisma as never);

    const result = await svc.pageByPath('proj-1', '/home');
    expect(result!.content).toEqual({ found: true });
  });
});

// ---------------------------------------------------------------------------
// createPage
// ---------------------------------------------------------------------------

describe('CanvasService.createPage', () => {
  it('stores content as JSON string and defaults layout to "default"', async () => {
    const prisma = makePrismaMock();
    prisma.page.create.mockResolvedValue(pageRow());
    const svc = new CanvasService(prisma as never);

    const input: CreatePageInput = {
      projectId: 'proj-1',
      name: 'Home',
      path: '/home',
      content: { components: [] },
    };
    await svc.createPage('user-1', input);

    const callData = prisma.page.create.mock.calls[0][0].data;
    expect(callData.content).toBe('{"components":[]}');
    expect(callData.layout).toBe('default');
    expect(callData.createdById).toBe('user-1');
  });

  it('respects explicit layout override', async () => {
    const prisma = makePrismaMock();
    prisma.page.create.mockResolvedValue(pageRow({ layout: 'sidebar' }));
    const svc = new CanvasService(prisma as never);

    await svc.createPage('user-1', {
      projectId: 'proj-1',
      name: 'Sidebar',
      path: '/side',
      layout: 'sidebar',
    });

    const callData = prisma.page.create.mock.calls[0][0].data;
    expect(callData.layout).toBe('sidebar');
  });
});

// ---------------------------------------------------------------------------
// updatePage
// ---------------------------------------------------------------------------

describe('CanvasService.updatePage', () => {
  it('only sends fields that are provided', async () => {
    const prisma = makePrismaMock();
    prisma.page.update.mockResolvedValue(pageRow({ name: 'Renamed' }));
    const svc = new CanvasService(prisma as never);

    const input: UpdatePageInput = { name: 'Renamed' };
    await svc.updatePage('page-1', input);

    const callData = prisma.page.update.mock.calls[0][0].data;
    expect(callData.name).toBe('Renamed');
    expect('content' in callData).toBe(false);
    expect('path' in callData).toBe(false);
  });

  it('stringifies content when provided', async () => {
    const prisma = makePrismaMock();
    prisma.page.update.mockResolvedValue(pageRow());
    const svc = new CanvasService(prisma as never);

    await svc.updatePage('page-1', { content: { v: 2 } });

    const callData = prisma.page.update.mock.calls[0][0].data;
    expect(callData.content).toBe('{"v":2}');
  });

  it('returns page with parsed content', async () => {
    const prisma = makePrismaMock();
    prisma.page.update.mockResolvedValue(pageRow({ content: '{"step":3}' }));
    const svc = new CanvasService(prisma as never);

    const result = await svc.updatePage('page-1', { name: 'X' });
    expect(result.content).toEqual({ step: 3 });
  });
});

// ---------------------------------------------------------------------------
// deletePage
// ---------------------------------------------------------------------------

describe('CanvasService.deletePage', () => {
  it('deletes page and returns true', async () => {
    const prisma = makePrismaMock();
    prisma.page.delete.mockResolvedValue(pageRow());
    const svc = new CanvasService(prisma as never);

    expect(await svc.deletePage('page-1')).toBe(true);
    expect(prisma.page.delete).toHaveBeenCalledWith({
      where: { id: 'page-1' },
    });
  });
});
