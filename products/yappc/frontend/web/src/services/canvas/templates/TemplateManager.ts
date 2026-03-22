/**
 * Template Manager Service
 * 
 * Manages canvas templates (save, load, delete)
 * 
 * @doc.type service
 * @doc.purpose Template CRUD operations
 * @doc.layer product
 * @doc.pattern Service
 */

import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';

// ============================================================================
// Types
// ============================================================================

export interface CanvasTemplate {
    id: string;
    name: string;
    description?: string;
    data: CanvasState;
    thumbnail?: string; // Base64 encoded image
    createdAt: number;
    updatedAt: number;
    tags?: string[];
    metadata?: {
        author?: string;
        version?: string;
        nodeCount?: number;
        connectionCount?: number;
    };
}

export interface TemplateListItem {
    id: string;
    name: string;
    description?: string;
    thumbnail?: string;
    nodeCount: number;
    connectionCount: number;
    createdAt: number;
    tags?: string[];
}

// ============================================================================
// Template Manager
// ============================================================================

export class TemplateManager {
    private storageKey = 'canvas-templates';

    /**
     * Get all templates
     */
    async getAll(): Promise<TemplateListItem[]> {
        try {
            const stored = localStorage.getItem(this.storageKey);
            if (!stored) return [];

            const templates: CanvasTemplate[] = JSON.parse(stored);

            return templates.map((t) => ({
                id: t.id,
                name: t.name,
                description: t.description,
                thumbnail: t.thumbnail,
                nodeCount: t.data.elements?.length || 0,
                connectionCount: t.data.connections?.length || 0,
                createdAt: t.createdAt,
                tags: t.tags,
            }));
        } catch (error) {
            console.error('Failed to get templates:', error);
            return [];
        }
    }

    /**
     * Get template by ID
     */
    async getById(id: string): Promise<CanvasTemplate | null> {
        try {
            const stored = localStorage.getItem(this.storageKey);
            if (!stored) return null;

            const templates: CanvasTemplate[] = JSON.parse(stored);
            return templates.find((t) => t.id === id) || null;
        } catch (error) {
            console.error('Failed to get template:', error);
            return null;
        }
    }

    /**
     * Save new template
     */
    async save(template: Omit<CanvasTemplate, 'id' | 'createdAt' | 'updatedAt'>): Promise<string> {
        try {
            const stored = localStorage.getItem(this.storageKey);
            const templates: CanvasTemplate[] = stored ? JSON.parse(stored) : [];

            const newTemplate: CanvasTemplate = {
                ...template,
                id: `template-${Date.now()}`,
                createdAt: Date.now(),
                updatedAt: Date.now(),
                metadata: {
                    ...template.metadata,
                    nodeCount: template.data.elements?.length || 0,
                    connectionCount: template.data.connections?.length || 0,
                },
            };

            templates.push(newTemplate);
            localStorage.setItem(this.storageKey, JSON.stringify(templates));

            return newTemplate.id;
        } catch (error) {
            console.error('Failed to save template:', error);
            throw error;
        }
    }

    /**
     * Update existing template
     */
    async update(id: string, updates: Partial<Omit<CanvasTemplate, 'id' | 'createdAt'>>): Promise<void> {
        try {
            const stored = localStorage.getItem(this.storageKey);
            if (!stored) throw new Error('No templates found');

            const templates: CanvasTemplate[] = JSON.parse(stored);
            const index = templates.findIndex((t) => t.id === id);

            if (index === -1) throw new Error('Template not found');

            templates[index] = {
                ...templates[index],
                ...updates,
                updatedAt: Date.now(),
            };

            if (updates.data) {
                templates[index].metadata = {
                    ...templates[index].metadata,
                    nodeCount: updates.data.elements?.length || 0,
                    connectionCount: updates.data.connections?.length || 0,
                };
            }

            localStorage.setItem(this.storageKey, JSON.stringify(templates));
        } catch (error) {
            console.error('Failed to update template:', error);
            throw error;
        }
    }

    /**
     * Delete template
     */
    async delete(id: string): Promise<void> {
        try {
            const stored = localStorage.getItem(this.storageKey);
            if (!stored) return;

            const templates: CanvasTemplate[] = JSON.parse(stored);
            const filtered = templates.filter((t) => t.id !== id);

            localStorage.setItem(this.storageKey, JSON.stringify(filtered));
        } catch (error) {
            console.error('Failed to delete template:', error);
            throw error;
        }
    }

    /**
     * Search templates by name or tags
     */
    async search(query: string): Promise<TemplateListItem[]> {
        const all = await this.getAll();
        const lowerQuery = query.toLowerCase();

        return all.filter(
            (t) =>
                t.name.toLowerCase().includes(lowerQuery) ||
                t.description?.toLowerCase().includes(lowerQuery) ||
                t.tags?.some((tag) => tag.toLowerCase().includes(lowerQuery))
        );
    }

    /**
     * Get templates by tag
     */
    async getByTag(tag: string): Promise<TemplateListItem[]> {
        const all = await this.getAll();
        return all.filter((t) => t.tags?.includes(tag));
    }

    /**
     * Export template to JSON file
     */
    exportToFile(template: CanvasTemplate): void {
        const json = JSON.stringify(template, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = `${template.name.replace(/\s+/g, '-')}.json`;
        a.click();

        URL.revokeObjectURL(url);
    }

    /**
     * Import template from JSON file
     */
    async importFromFile(file: File): Promise<string> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();

            reader.onload = async (e) => {
                try {
                    const json = e.target?.result as string;
                    const template: Omit<CanvasTemplate, 'id' | 'createdAt' | 'updatedAt'> =
                        JSON.parse(json);

                    const id = await this.save(template);
                    resolve(id);
                } catch (error) {
                    reject(error);
                }
            };

            reader.onerror = () => reject(reader.error);
            reader.readAsText(file);
        });
    }

    /**
     * Duplicate template
     */
    async duplicate(id: string, newName?: string): Promise<string> {
        const template = await this.getById(id);
        if (!template) throw new Error('Template not found');

        return this.save({
            ...template,
            name: newName || `${template.name} (Copy)`,
        });
    }

    /**
     * Generate thumbnail from canvas using html2canvas
     */
    async generateThumbnail(canvasElement: HTMLElement): Promise<string> {
        try {
            // Check if html2canvas is available
            if (typeof window === 'undefined' || !window.html2canvas) {
                console.warn('[TemplateManager] html2canvas not available, using fallback');
                return this.generateFallbackThumbnail();
            }

            // Use html2canvas for real thumbnail generation
            const canvas = await window.html2canvas(canvasElement, {
                width: 200,
                height: 150,
                scale: 1,
                useCORS: true,
                allowTaint: true,
                backgroundColor: '#ffffff',
                logging: false,
                removeContainer: false,
            });

            return canvas.toDataURL('image/png', 0.8);
        } catch (error) {
            console.error('[TemplateManager] Failed to generate thumbnail with html2canvas:', error);
            return this.generateFallbackThumbnail();
        }
    }

    /**
     * Fallback thumbnail generation for when html2canvas is not available
     */
    private generateFallbackThumbnail(): string {
        return new Promise((resolve) => {
            const canvas = document.createElement('canvas');
            canvas.width = 200;
            canvas.height = 150;
            const ctx = canvas.getContext('2d');

            if (ctx) {
                // Create a more sophisticated fallback thumbnail
                this.drawThumbnailPlaceholder(ctx);
            }

            resolve(canvas.toDataURL());
        });
    }

    /**
     * Draw a sophisticated placeholder thumbnail
     */
    private drawThumbnail(ctx: CanvasRenderingContext2D): void {
        // Background gradient
        const gradient = ctx.createLinearGradient(0, 0, 200, 150);
        gradient.addColorStop(0, '#f8f9fa');
        gradient.addColorStop(1, '#e9ecef');
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, 200, 150);

        // Draw canvas icon
        ctx.strokeStyle = '#6c757d';
        ctx.lineWidth = 2;
        ctx.setLineDash([5, 5]);
        ctx.strokeRect(20, 30, 160, 90);

        // Draw grid lines
        ctx.strokeStyle = '#dee2e6';
        ctx.lineWidth = 1;
        ctx.setLineDash([]);

        // Vertical lines
        for (let x = 40; x < 180; x += 20) {
            ctx.beginPath();
            ctx.moveTo(x, 40);
            ctx.lineTo(x, 110);
            ctx.stroke();
        }

        // Horizontal lines
        for (let y = 50; y < 110; y += 20) {
            ctx.beginPath();
            ctx.moveTo(30, y);
            ctx.lineTo(170, y);
            ctx.stroke();
        }

        // Draw sample elements
        // Sticky note
        ctx.fillStyle = '#fff3c4';
        ctx.fillRect(30, 50, 40, 30);
        ctx.strokeStyle = '#fbc02d';
        ctx.lineWidth = 2;
        ctx.strokeRect(30, 50, 40, 30);
        ctx.fillStyle = '#333';
        ctx.font = '10px Arial';
        ctx.fillText('Note', 35, 70);

        // Frame
        ctx.fillStyle = '#e3f2fd';
        ctx.fillRect(80, 60, 60, 40);
        ctx.strokeStyle = '#2196f3';
        ctx.lineWidth = 2;
        ctx.strokeRect(80, 60, 60, 40);
        ctx.fillStyle = '#1976d2';
        ctx.font = 'bold 10px Arial';
        ctx.fillText('Frame', 85, 80);

        // Circle shape
        ctx.fillStyle = '#fff3e0';
        ctx.beginPath();
        ctx.arc(150, 75, 15, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = '#ff9800';
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.fillStyle = '#e65100';
        ctx.font = '10px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Circle', 150, 78);

        // Text element
        ctx.fillStyle = '#333';
        ctx.font = '12px Arial';
        ctx.fillText('Text Element', 30, 105);

        // Add "Canvas Preview" text
        ctx.fillStyle = '#495057';
        ctx.font = 'bold 12px Arial';
        ctx.textAlign = 'center';
        ctx.fillText('Canvas Preview', 100, 25);
    }
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const templateManager = new TemplateManager();

// ============================================================================
// React Hook
// ============================================================================

import { useState, useEffect, useCallback } from 'react';

export interface UseTemplateManagerResult {
    templates: TemplateListItem[];
    loading: boolean;
    error: Error | null;
    saveTemplate: (template: Omit<CanvasTemplate, 'id' | 'createdAt' | 'updatedAt'>) => Promise<string>;
    loadTemplate: (id: string) => Promise<CanvasTemplate | null>;
    deleteTemplate: (id: string) => Promise<void>;
    searchTemplates: (query: string) => Promise<TemplateListItem[]>;
    refreshTemplates: () => Promise<void>;
}

export function useTemplateManager(): UseTemplateManagerResult {
    const [templates, setTemplates] = useState<TemplateListItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const refreshTemplates = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const all = await templateManager.getAll();
            setTemplates(all);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('Failed to load templates'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        refreshTemplates();
    }, [refreshTemplates]);

    const saveTemplate = useCallback(
        async (template: Omit<CanvasTemplate, 'id' | 'createdAt' | 'updatedAt'>) => {
            const id = await templateManager.save(template);
            await refreshTemplates();
            return id;
        },
        [refreshTemplates]
    );

    const loadTemplate = useCallback(async (id: string) => {
        return templateManager.getById(id);
    }, []);

    const deleteTemplate = useCallback(
        async (id: string) => {
            await templateManager.delete(id);
            await refreshTemplates();
        },
        [refreshTemplates]
    );

    const searchTemplates = useCallback(async (query: string) => {
        return templateManager.search(query);
    }, []);

    return {
        templates,
        loading,
        error,
        saveTemplate,
        loadTemplate,
        deleteTemplate,
        searchTemplates,
        refreshTemplates,
    };
}
