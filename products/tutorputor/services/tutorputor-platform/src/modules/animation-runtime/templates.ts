/**
 * @doc.type service
 * @doc.purpose Animation template library with pre-built animations
 * @doc.layer product
 * @doc.pattern Template Library
 */

import type { AnimationSpec } from './service';
import { createAnimationSpec } from './service';

export interface AnimationTemplate {
    id: string;
    name: string;
    description: string;
    category: 'physics' | 'chemistry' | 'biology' | 'math' | 'general';
    difficulty: 'beginner' | 'intermediate' | 'advanced';
    tags: string[];
    thumbnail?: string;
    spec: AnimationSpec;
}

/**
 * Animation Template Library
 * Pre-built animations for common educational scenarios
 */
export class AnimationTemplateLibrary {
    private templates: Map<string, AnimationTemplate> = new Map();

    constructor() {
        this.initializeDefaultTemplates();
    }

    /**
     * Get all templates
     */
    getAllTemplates(): AnimationTemplate[] {
        return Array.from(this.templates.values());
    }

    /**
     * Get template by ID
     */
    getTemplate(id: string): AnimationTemplate | undefined {
        return this.templates.get(id);
    }

    /**
     * Get templates by category
     */
    getTemplatesByCategory(category: AnimationTemplate['category']): AnimationTemplate[] {
        return this.getAllTemplates().filter(t => t.category === category);
    }

    /**
     * Get templates by difficulty
     */
    getTemplatesByDifficulty(difficulty: AnimationTemplate['difficulty']): AnimationTemplate[] {
        return this.getAllTemplates().filter(t => t.difficulty === difficulty);
    }

    /**
     * Search templates by tag
     */
    searchByTag(tag: string): AnimationTemplate[] {
        return this.getAllTemplates().filter(t => t.tags.includes(tag));
    }

    /**
     * Add custom template
     */
    addTemplate(template: AnimationTemplate): void {
        this.templates.set(template.id, template);
    }

    /**
     * Remove template
     */
    removeTemplate(id: string): boolean {
        return this.templates.delete(id);
    }

    /**
     * Initialize default templates
     */
    private initializeDefaultTemplates(): void {
        // Physics: Projectile Motion
        this.templates.set('physics-projectile', {
            id: 'physics-projectile',
            name: 'Projectile Motion',
            description: 'Demonstrates parabolic trajectory of a projectile',
            category: 'physics',
            difficulty: 'beginner',
            tags: ['motion', 'trajectory', 'gravity', 'kinematics'],
            spec: createAnimationSpec({
                id: 'physics-projectile',
                title: 'Projectile Motion',
                description: 'Ball following parabolic path',
                duration: 3,
                keyframes: [
                    { time: 0, properties: { x: 50, y: 300, rotation: 0 }, easing: 'linear' },
                    { time: 1.5, properties: { x: 200, y: 100, rotation: 180 }, easing: 'easeOutQuad' },
                    { time: 3, properties: { x: 350, y: 300, rotation: 360 }, easing: 'easeInQuad' },
                ],
            }),
        });

        // Physics: Simple Harmonic Motion
        this.templates.set('physics-shm', {
            id: 'physics-shm',
            name: 'Simple Harmonic Motion',
            description: 'Oscillating motion like a pendulum or spring',
            category: 'physics',
            difficulty: 'intermediate',
            tags: ['oscillation', 'pendulum', 'spring', 'periodic'],
            spec: createAnimationSpec({
                id: 'physics-shm',
                title: 'Simple Harmonic Motion',
                description: 'Object oscillating back and forth',
                duration: 4,
                loop: true,
                keyframes: [
                    { time: 0, properties: { x: 100, y: 200 }, easing: 'easeInOutQuad' },
                    { time: 1, properties: { x: 300, y: 200 }, easing: 'easeInOutQuad' },
                    { time: 2, properties: { x: 100, y: 200 }, easing: 'easeInOutQuad' },
                    { time: 3, properties: { x: 300, y: 200 }, easing: 'easeInOutQuad' },
                    { time: 4, properties: { x: 100, y: 200 }, easing: 'easeInOutQuad' },
                ],
            }),
        });

        // Physics: Circular Motion
        this.templates.set('physics-circular', {
            id: 'physics-circular',
            name: 'Circular Motion',
            description: 'Object moving in a circular path',
            category: 'physics',
            difficulty: 'intermediate',
            tags: ['circular', 'rotation', 'centripetal', 'angular'],
            spec: createAnimationSpec({
                id: 'physics-circular',
                title: 'Circular Motion',
                description: 'Object rotating in a circle',
                duration: 4,
                loop: true,
                keyframes: [
                    { time: 0, properties: { x: 250, y: 100, rotation: 0 }, easing: 'linear' },
                    { time: 1, properties: { x: 350, y: 200, rotation: 90 }, easing: 'linear' },
                    { time: 2, properties: { x: 250, y: 300, rotation: 180 }, easing: 'linear' },
                    { time: 3, properties: { x: 150, y: 200, rotation: 270 }, easing: 'linear' },
                    { time: 4, properties: { x: 250, y: 100, rotation: 360 }, easing: 'linear' },
                ],
            }),
        });

        // Chemistry: Molecular Vibration
        this.templates.set('chemistry-vibration', {
            id: 'chemistry-vibration',
            name: 'Molecular Vibration',
            description: 'Atoms vibrating in a molecule',
            category: 'chemistry',
            difficulty: 'beginner',
            tags: ['molecule', 'vibration', 'bonds', 'energy'],
            spec: createAnimationSpec({
                id: 'chemistry-vibration',
                title: 'Molecular Vibration',
                description: 'Atoms oscillating around equilibrium',
                duration: 2,
                loop: true,
                keyframes: [
                    { time: 0, properties: { x: 200, scale: 1 }, easing: 'easeInOutQuad' },
                    { time: 0.5, properties: { x: 220, scale: 1.1 }, easing: 'easeInOutQuad' },
                    { time: 1, properties: { x: 200, scale: 1 }, easing: 'easeInOutQuad' },
                    { time: 1.5, properties: { x: 180, scale: 0.9 }, easing: 'easeInOutQuad' },
                    { time: 2, properties: { x: 200, scale: 1 }, easing: 'easeInOutQuad' },
                ],
            }),
        });

        // Biology: Cell Division
        this.templates.set('biology-cell-division', {
            id: 'biology-cell-division',
            name: 'Cell Division',
            description: 'Cell splitting into two daughter cells',
            category: 'biology',
            difficulty: 'intermediate',
            tags: ['mitosis', 'cell', 'division', 'reproduction'],
            spec: createAnimationSpec({
                id: 'biology-cell-division',
                title: 'Cell Division',
                description: 'Cell undergoing mitosis',
                duration: 5,
                keyframes: [
                    { time: 0, properties: { x: 200, y: 200, scale: 1 }, easing: 'linear' },
                    { time: 1, properties: { x: 200, y: 200, scale: 1.2 }, easing: 'easeInQuad' },
                    { time: 2, properties: { x: 200, y: 200, scale: 1.5 }, easing: 'linear' },
                    { time: 3, properties: { x: 180, y: 200, scale: 1 }, easing: 'easeOutQuad' },
                    { time: 5, properties: { x: 150, y: 200, scale: 0.8 }, easing: 'linear' },
                ],
            }),
        });

        // Math: Function Transformation
        this.templates.set('math-transformation', {
            id: 'math-transformation',
            name: 'Function Transformation',
            description: 'Visualizing function transformations',
            category: 'math',
            difficulty: 'advanced',
            tags: ['function', 'transformation', 'translation', 'scaling'],
            spec: createAnimationSpec({
                id: 'math-transformation',
                title: 'Function Transformation',
                description: 'Graph transforming through translations and scaling',
                duration: 6,
                keyframes: [
                    { time: 0, properties: { x: 100, y: 200, scaleX: 1, scaleY: 1 }, easing: 'linear' },
                    { time: 2, properties: { x: 150, y: 200, scaleX: 1, scaleY: 1 }, easing: 'easeInOutQuad' },
                    { time: 4, properties: { x: 150, y: 150, scaleX: 1, scaleY: 1.5 }, easing: 'easeInOutQuad' },
                    { time: 6, properties: { x: 150, y: 150, scaleX: 2, scaleY: 1.5 }, easing: 'easeInOutQuad' },
                ],
            }),
        });

        // General: Fade In/Out
        this.templates.set('general-fade', {
            id: 'general-fade',
            name: 'Fade In/Out',
            description: 'Simple fade in and fade out animation',
            category: 'general',
            difficulty: 'beginner',
            tags: ['fade', 'opacity', 'transition', 'basic'],
            spec: createAnimationSpec({
                id: 'general-fade',
                title: 'Fade Animation',
                description: 'Object fading in and out',
                duration: 3,
                keyframes: [
                    { time: 0, properties: { opacity: 0 }, easing: 'easeInQuad' },
                    { time: 1.5, properties: { opacity: 1 }, easing: 'linear' },
                    { time: 3, properties: { opacity: 0 }, easing: 'easeOutQuad' },
                ],
            }),
        });

        // General: Zoom In/Out
        this.templates.set('general-zoom', {
            id: 'general-zoom',
            name: 'Zoom In/Out',
            description: 'Scaling animation for emphasis',
            category: 'general',
            difficulty: 'beginner',
            tags: ['zoom', 'scale', 'emphasis', 'basic'],
            spec: createAnimationSpec({
                id: 'general-zoom',
                title: 'Zoom Animation',
                description: 'Object zooming in and out',
                duration: 2,
                keyframes: [
                    { time: 0, properties: { scale: 0.5 }, easing: 'easeOutQuad' },
                    { time: 1, properties: { scale: 1.5 }, easing: 'easeInQuad' },
                    { time: 2, properties: { scale: 1 }, easing: 'linear' },
                ],
            }),
        });

        // General: Slide In
        this.templates.set('general-slide', {
            id: 'general-slide',
            name: 'Slide In',
            description: 'Object sliding in from the side',
            category: 'general',
            difficulty: 'beginner',
            tags: ['slide', 'entrance', 'transition', 'basic'],
            spec: createAnimationSpec({
                id: 'general-slide',
                title: 'Slide Animation',
                description: 'Object sliding into view',
                duration: 1.5,
                keyframes: [
                    { time: 0, properties: { x: -100, opacity: 0 }, easing: 'easeOutCubic' },
                    { time: 1.5, properties: { x: 200, opacity: 1 }, easing: 'linear' },
                ],
            }),
        });

        // General: Bounce
        this.templates.set('general-bounce', {
            id: 'general-bounce',
            name: 'Bounce',
            description: 'Bouncing animation with realistic physics',
            category: 'general',
            difficulty: 'intermediate',
            tags: ['bounce', 'physics', 'elastic', 'rebound'],
            spec: createAnimationSpec({
                id: 'general-bounce',
                title: 'Bounce Animation',
                description: 'Object bouncing with decreasing amplitude',
                duration: 3,
                keyframes: [
                    { time: 0, properties: { y: 100 }, easing: 'easeInQuad' },
                    { time: 0.5, properties: { y: 300 }, easing: 'easeOutQuad' },
                    { time: 1, properties: { y: 150 }, easing: 'easeInQuad' },
                    { time: 1.5, properties: { y: 300 }, easing: 'easeOutQuad' },
                    { time: 2, properties: { y: 200 }, easing: 'easeInQuad' },
                    { time: 2.5, properties: { y: 300 }, easing: 'easeOutQuad' },
                    { time: 3, properties: { y: 250 }, easing: 'linear' },
                ],
            }),
        });
    }

    /**
     * Create animation from template
     */
    createFromTemplate(templateId: string, customizations?: Partial<AnimationSpec>): AnimationSpec | null {
        const template = this.templates.get(templateId);
        if (!template) return null;

        return {
            ...template.spec,
            ...customizations,
            animationId: customizations?.animationId || `${templateId}-${Date.now()}`,
        };
    }

    /**
     * Get template categories
     */
    getCategories(): AnimationTemplate['category'][] {
        return ['physics', 'chemistry', 'biology', 'math', 'general'];
    }

    /**
     * Get all tags
     */
    getAllTags(): string[] {
        const tags = new Set<string>();
        this.getAllTemplates().forEach(template => {
            template.tags.forEach(tag => tags.add(tag));
        });
        return Array.from(tags).sort();
    }
}

/**
 * Singleton instance
 */
export const animationTemplateLibrary = new AnimationTemplateLibrary();
