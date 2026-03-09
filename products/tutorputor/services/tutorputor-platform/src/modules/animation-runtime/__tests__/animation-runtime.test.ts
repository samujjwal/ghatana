/**
 * @doc.type test
 * @doc.purpose Comprehensive tests for Animation Runtime
 * @doc.layer product
 * @doc.pattern Unit Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AnimationRuntime, AnimationManager, createAnimationSpec, validateAnimationSpec } from '../service';
import type { AnimationRenderer, AnimationSpec } from '../service';

// Mock browser animation APIs unavailable in Node test environment
let rafId = 0;
(globalThis as any).requestAnimationFrame = vi.fn(() => ++rafId);
(globalThis as any).cancelAnimationFrame = vi.fn();

// Mock renderer for testing
class MockRenderer implements AnimationRenderer {
    private properties: Record<string, any> = {};
    public renderCalls: number = 0;

    setProperty(property: string, value: any): void {
        this.properties[property] = value;
    }

    getProperty(property: string): any {
        return this.properties[property];
    }

    applyTransform(transform: Record<string, any>): void {
        Object.assign(this.properties, transform);
    }

    render(): void {
        this.renderCalls++;
    }

    getProperties(): Record<string, any> {
        return { ...this.properties };
    }
}

describe('AnimationRuntime', () => {
    let runtime: AnimationRuntime;
    let renderer: MockRenderer;
    let testAnimation: AnimationSpec;

    beforeEach(() => {
        runtime = new AnimationRuntime();
        renderer = new MockRenderer();

        testAnimation = createAnimationSpec({
            id: 'test-animation',
            title: 'Test Animation',
            description: 'Test description',
            duration: 2,
            keyframes: [
                { time: 0, properties: { x: 0, y: 0 } },
                { time: 1, properties: { x: 100, y: 50 } },
                { time: 2, properties: { x: 200, y: 100 } },
            ],
        });

        runtime.setRenderer(renderer);
        runtime.loadAnimation(testAnimation);
    });

    describe('Animation Loading', () => {
        it('should load animation spec correctly', () => {
            const loadedAnimation = runtime.getAnimation();
            expect(loadedAnimation).toEqual(testAnimation);
        });

        it('should initialize state correctly', () => {
            const state = runtime.getState();
            expect(state.currentTime).toBe(0);
            expect(state.duration).toBe(2000); // 2 seconds in ms
            expect(state.isPlaying).toBe(false);
            expect(state.isPaused).toBe(false);
            expect(state.isCompleted).toBe(false);
        });

        it('should handle loop setting', () => {
            const loopAnimation = createAnimationSpec({
                ...testAnimation,
                loop: true,
            });
            runtime.loadAnimation(loopAnimation);
            const state = runtime.getState();
            expect(state.loop).toBe(true);
        });
    });

    describe('Playback Controls', () => {
        it('should start playing', () => {
            runtime.play();
            const state = runtime.getState();
            expect(state.isPlaying).toBe(true);
            expect(state.isPaused).toBe(false);
        });

        it('should pause playback', () => {
            runtime.play();
            runtime.pause();
            const state = runtime.getState();
            expect(state.isPlaying).toBe(false);
            expect(state.isPaused).toBe(true);
        });

        it('should resume from pause', () => {
            runtime.play();
            runtime.pause();
            runtime.resume();
            const state = runtime.getState();
            expect(state.isPlaying).toBe(true);
            expect(state.isPaused).toBe(false);
        });

        it('should stop and reset', () => {
            runtime.play();
            runtime.stop();
            const state = runtime.getState();
            expect(state.isPlaying).toBe(false);
            expect(state.currentTime).toBe(0);
            expect(state.progress).toBe(0);
        });
    });

    describe('Seeking', () => {
        it('should seek to specific time', () => {
            runtime.seek(1000); // 1 second
            const state = runtime.getState();
            expect(state.currentTime).toBe(1000);
            expect(state.progress).toBe(0.5); // 50% of 2 seconds
        });

        it('should clamp seek to valid range', () => {
            runtime.seek(-100);
            expect(runtime.getState().currentTime).toBe(0);

            runtime.seek(5000);
            expect(runtime.getState().currentTime).toBe(2000);
        });

        it('should update renderer on seek', () => {
            const initialRenderCalls = renderer.renderCalls;
            runtime.seek(1000);
            expect(renderer.renderCalls).toBeGreaterThan(initialRenderCalls);
        });
    });

    describe('Event System', () => {
        it('should emit start event', () => {
            const startHandler = vi.fn();
            runtime.addEventListener('start', startHandler);
            runtime.play();
            expect(startHandler).toHaveBeenCalled();
        });

        it('should emit pause event', () => {
            const pauseHandler = vi.fn();
            runtime.addEventListener('pause', pauseHandler);
            runtime.play();
            runtime.pause();
            expect(pauseHandler).toHaveBeenCalled();
        });

        it('should emit keyframe events', () => {
            const keyframeHandler = vi.fn();
            runtime.addEventListener('keyframe', keyframeHandler);
            runtime.seek(1000);
            expect(keyframeHandler).toHaveBeenCalled();
        });

        it('should remove event listeners', () => {
            const handler = vi.fn();
            runtime.addEventListener('start', handler);
            runtime.removeEventListener('start', handler);
            runtime.play();
            expect(handler).not.toHaveBeenCalled();
        });
    });

    describe('Property Interpolation', () => {
        it('should interpolate numeric properties', () => {
            runtime.seek(1000); // Midpoint
            const props = renderer.getProperties();
            expect(props.x).toBeCloseTo(100, 1);
            expect(props.y).toBeCloseTo(50, 1);
        });

        it('should handle easing functions', () => {
            const easedAnimation = createAnimationSpec({
                id: 'eased',
                title: 'Eased',
                description: 'Test easing',
                duration: 1,
                keyframes: [
                    { time: 0, properties: { x: 0 }, easing: 'linear' },
                    { time: 1, properties: { x: 100 }, easing: 'easeInQuad' },
                ],
            });
            runtime.loadAnimation(easedAnimation);
            runtime.seek(500);
            const props = renderer.getProperties();
            // With easeInQuad, value at 50% should be less than 50
            expect(props.x).toBeLessThan(50);
        });
    });
});

describe('AnimationManager', () => {
    let manager: AnimationManager;

    beforeEach(() => {
        manager = new AnimationManager();
    });

    it('should create and retrieve animations', () => {
        const animation = manager.createAnimation('test-1');
        expect(animation).toBeInstanceOf(AnimationRuntime);
        expect(manager.getAnimation('test-1')).toBe(animation);
    });

    it('should register and retrieve renderers', () => {
        const renderer = new MockRenderer();
        manager.registerRenderer('renderer-1', renderer);
        expect(manager.getRenderer('renderer-1')).toBe(renderer);
    });

    it('should play all animations', () => {
        const anim1 = manager.createAnimation('anim-1');
        const anim2 = manager.createAnimation('anim-2');

        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test',
            description: 'Test',
            duration: 1,
            keyframes: [{ time: 0, properties: {} }],
            autoplay: true,
        });

        anim1.loadAnimation(spec);
        anim2.loadAnimation(spec);
        anim1.setRenderer(new MockRenderer());
        anim2.setRenderer(new MockRenderer());

        manager.playAll();
        expect(anim1.getState().isPlaying).toBe(true);
        expect(anim2.getState().isPlaying).toBe(true);
    });

    it('should pause all animations', () => {
        const anim1 = manager.createAnimation('anim-1');
        const renderer = new MockRenderer();
        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test',
            description: 'Test',
            duration: 1,
            keyframes: [{ time: 0, properties: {} }],
        });

        anim1.loadAnimation(spec);
        anim1.setRenderer(renderer);
        anim1.play();

        manager.pauseAll();
        expect(anim1.getState().isPaused).toBe(true);
    });

    it('should dispose all resources', () => {
        manager.createAnimation('anim-1');
        manager.createAnimation('anim-2');
        manager.dispose();
        expect(manager.getAnimation('anim-1')).toBeUndefined();
        expect(manager.getAnimation('anim-2')).toBeUndefined();
    });
});

describe('createAnimationSpec', () => {
    it('should create valid animation spec', () => {
        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test Animation',
            description: 'Test description',
            duration: 3,
            keyframes: [
                { time: 0, properties: { x: 0 } },
                { time: 3, properties: { x: 100 } },
            ],
        });

        expect(spec.animationId).toBe('test');
        expect(spec.title).toBe('Test Animation');
        expect(spec.durationSeconds).toBe(3);
        expect(spec.keyframes).toHaveLength(2);
        expect(spec.keyframes[0].timeMs).toBe(0);
        expect(spec.keyframes[1].timeMs).toBe(3000);
    });

    it('should apply default values', () => {
        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test',
            description: 'Test',
            duration: 1,
            keyframes: [{ time: 0, properties: {} }],
        });

        expect(spec.type).toBe('2d');
        expect(spec.loop).toBeUndefined();
        expect(spec.autoplay).toBeUndefined();
    });
});

describe('validateAnimationSpec', () => {
    it('should validate correct spec', () => {
        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test',
            description: 'Test',
            duration: 1,
            keyframes: [
                { time: 0, properties: {} },
                { time: 1, properties: {} },
            ],
        });

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
    });

    it('should detect missing animation ID', () => {
        const spec = createAnimationSpec({
            id: '',
            title: 'Test',
            description: 'Test',
            duration: 1,
            keyframes: [{ time: 0, properties: {} }],
        });

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(false);
        expect(result.errors).toContain('Animation ID is required');
    });

    it('should detect invalid duration', () => {
        const spec = createAnimationSpec({
            id: 'test',
            title: 'Test',
            description: 'Test',
            duration: 0,
            keyframes: [{ time: 0, properties: {} }],
        });

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(false);
        expect(result.errors).toContain('Duration must be greater than 0');
    });

    it('should detect missing keyframes', () => {
        const spec: AnimationSpec = {
            animationId: 'test',
            title: 'Test',
            description: 'Test',
            type: '2d',
            durationSeconds: 1,
            keyframes: [],
            config: {},
        };

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(false);
        expect(result.errors).toContain('At least one keyframe is required');
    });

    it('should detect incorrect keyframe ordering', () => {
        const spec: AnimationSpec = {
            animationId: 'test',
            title: 'Test',
            description: 'Test',
            type: '2d',
            durationSeconds: 2,
            keyframes: [
                { timeMs: 0, description: 'Start', properties: {} },
                { timeMs: 2000, description: 'End', properties: {} },
                { timeMs: 1000, description: 'Middle', properties: {} },
            ],
            config: {},
        };

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(false);
        expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should detect keyframe exceeding duration', () => {
        const spec: AnimationSpec = {
            animationId: 'test',
            title: 'Test',
            description: 'Test',
            type: '2d',
            durationSeconds: 1,
            keyframes: [
                { timeMs: 0, description: 'Start', properties: {} },
                { timeMs: 2000, description: 'End', properties: {} },
            ],
            config: {},
        };

        const result = validateAnimationSpec(spec);
        expect(result.valid).toBe(false);
        expect(result.errors).toContain('Last keyframe time cannot exceed animation duration');
    });
});
