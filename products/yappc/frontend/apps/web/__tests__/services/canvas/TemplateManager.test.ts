/**
 * TemplateManager Tests
 * 
 * @doc.type test
 * @doc.purpose Test template CRUD operations
 * @doc.layer product
 */

import { describe, it, expect, beforeEach, afterEach } from '@jest/globals';
import { renderHook, act, waitFor } from '@testing-library/react';
import { TemplateManager, useTemplateManager } from '../../../src/services/canvas/templates/TemplateManager';
import type { CanvasTemplate } from '../../../src/services/canvas/templates/TemplateManager';

describe('TemplateManager', () => {
    let manager: TemplateManager;

    beforeEach(() => {
        manager = new TemplateManager();
        localStorage.clear();
    });

    afterEach(() => {
        localStorage.clear();
    });

    describe('CRUD Operations', () => {
        it('should save a new template', async () => {
            const template = {
                name: 'Test Template',
                description: 'A test template',
                data: { elements: [], connections: [] },
                tags: ['test'],
            };

            const id = await manager.save(template);

            expect(id).toBeDefined();
            expect(id).toMatch(/^template-\d+$/);
        });

        it('should retrieve all templates', async () => {
            await manager.save({
                name: 'Template 1',
                data: { elements: [], connections: [] },
            });

            await manager.save({
                name: 'Template 2',
                data: { elements: [], connections: [] },
            });

            const templates = await manager.getAll();
            expect(templates).toHaveLength(2);
            expect(templates[0].name).toBe('Template 1');
            expect(templates[1].name).toBe('Template 2');
        });

        it('should retrieve template by ID', async () => {
            const id = await manager.save({
                name: 'Test Template',
                data: { elements: [], connections: [] },
            });

            const template = await manager.getById(id);

            expect(template).not.toBeNull();
            expect(template?.name).toBe('Test Template');
            expect(template?.id).toBe(id);
        });

        it('should update existing template', async () => {
            const id = await manager.save({
                name: 'Original Name',
                data: { elements: [], connections: [] },
            });

            await manager.update(id, {
                name: 'Updated Name',
                description: 'New description',
            });

            const template = await manager.getById(id);
            expect(template?.name).toBe('Updated Name');
            expect(template?.description).toBe('New description');
        });

        it('should delete template', async () => {
            const id = await manager.save({
                name: 'To Delete',
                data: { elements: [], connections: [] },
            });

            await manager.delete(id);

            const template = await manager.getById(id);
            expect(template).toBeNull();
        });
    });

    describe('Search Operations', () => {
        beforeEach(async () => {
            await manager.save({
                name: 'API Template',
                description: 'Backend API design',
                data: { elements: [], connections: [] },
                tags: ['api', 'backend'],
            });

            await manager.save({
                name: 'UI Template',
                description: 'Frontend UI components',
                data: { elements: [], connections: [] },
                tags: ['ui', 'frontend'],
            });

            await manager.save({
                name: 'Full Stack',
                description: 'Complete application',
                data: { elements: [], connections: [] },
                tags: ['api', 'ui'],
            });
        });

        it('should search by name', async () => {
            const results = await manager.search('API');
            expect(results).toHaveLength(1);
            expect(results[0].name).toBe('API Template');
        });

        it('should search by description', async () => {
            const results = await manager.search('Frontend');
            expect(results).toHaveLength(1);
            expect(results[0].name).toBe('UI Template');
        });

        it('should search by tags', async () => {
            const results = await manager.search('api');
            expect(results).toHaveLength(2);
        });

        it('should get templates by tag', async () => {
            const results = await manager.getByTag('api');
            expect(results).toHaveLength(2);
        });
    });

    describe('Advanced Operations', () => {
        it('should duplicate template', async () => {
            const id = await manager.save({
                name: 'Original',
                data: { elements: [], connections: [] },
            });

            const newId = await manager.duplicate(id, 'Copy of Original');

            const original = await manager.getById(id);
            const copy = await manager.getById(newId);

            expect(copy).not.toBeNull();
            expect(copy?.name).toBe('Copy of Original');
            expect(copy?.data).toEqual(original?.data);
        });

        it('should export template to file', async () => {
            const id = await manager.save({
                name: 'Export Test',
                data: { elements: [], connections: [] },
            });

            const template = await manager.getById(id);

            // Mock document.createElement and click
            const createElementSpy = jest.spyOn(document, 'createElement');
            const clickMock = jest.fn();

            createElementSpy.mockReturnValue({
                click: clickMock,
                href: '',
                download: '',
            } as unknown);

            if (template) {
                manager.exportToFile(template);
            }

            expect(clickMock).toHaveBeenCalled();
            createElementSpy.mockRestore();
        });

        it('should track metadata', async () => {
            const id = await manager.save({
                name: 'Metadata Test',
                data: {
                    elements: [{ id: '1', type: 'component' }, { id: '2', type: 'api' }],
                    connections: [{ id: 'c1', source: '1', target: '2' }],
                },
            });

            const template = await manager.getById(id);

            expect(template?.metadata?.nodeCount).toBe(2);
            expect(template?.metadata?.connectionCount).toBe(1);
        });
    });
});

describe('useTemplateManager Hook', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    afterEach(() => {
        localStorage.clear();
    });

    it('should load templates on mount', async () => {
        const manager = new TemplateManager();
        await manager.save({
            name: 'Test',
            data: { elements: [], connections: [] },
        });

        const { result } = renderHook(() => useTemplateManager());

        await waitFor(() => {
            expect(result.current.templates).toHaveLength(1);
        });
    });

    it('should save template via hook', async () => {
        const { result } = renderHook(() => useTemplateManager());

        let id: string = '';
        await act(async () => {
            id = await result.current.saveTemplate({
                name: 'Hook Test',
                data: { elements: [], connections: [] },
            });
        });

        expect(id).toBeDefined();

        await waitFor(() => {
            expect(result.current.templates).toHaveLength(1);
        });
    });

    it('should delete template via hook', async () => {
        const { result } = renderHook(() => useTemplateManager());

        let id: string = '';
        await act(async () => {
            id = await result.current.saveTemplate({
                name: 'To Delete',
                data: { elements: [], connections: [] },
            });
        });

        await waitFor(() => {
            expect(result.current.templates).toHaveLength(1);
        });

        await act(async () => {
            await result.current.deleteTemplate(id);
        });

        await waitFor(() => {
            expect(result.current.templates).toHaveLength(0);
        });
    });

    it('should handle errors gracefully', async () => {
        const { result } = renderHook(() => useTemplateManager());

        // Mock localStorage to throw error
        const setItemSpy = jest.spyOn(Storage.prototype, 'setItem');
        setItemSpy.mockImplementation(() => {
            throw new Error('Storage full');
        });

        await act(async () => {
            try {
                await result.current.saveTemplate({
                    name: 'Error Test',
                    data: { elements: [], connections: [] },
                });
            } catch (error) {
                expect(error).toBeDefined();
            }
        });

        setItemSpy.mockRestore();
    });
});
