/**
 * PageTransition Component Tests
 */

import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { AnimationProvider } from '../AnimationProvider';
import {
    PageTransition,
    FadeTransition,
    SlideLeftTransition,
    SlideRightTransition,
    SlideUpTransition,
    SlideDownTransition,
    ScaleTransition,
    ScaleFadeTransition,
    SlideFadeLeftTransition,
    SlideFadeRightTransition,
} from '../PageTransition';

// Mock framer-motion
vi.mock('framer-motion', () => ({
    motion: {
        div: ({ children, ...props }: unknown) => <div {...props}>{children}</div>,
    },
    MotionConfig: ({ children }: unknown) => <>{children}</>,
}));

describe('PageTransition', () => {
    beforeEach(() => {
        // Mock matchMedia for reduced motion
        Object.defineProperty(window, 'matchMedia', {
            writable: true,
            value: vi.fn().mockImplementation((query) => ({
                matches: false,
                media: query,
                onchange: null,
                addListener: vi.fn(),
                removeListener: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                dispatchEvent: vi.fn(),
            })),
        });
    });

    describe('Basic Rendering', () => {
        it('should render children', () => {
            render(
                <AnimationProvider>
                    <PageTransition>
                        <div>Page Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Page Content')).toBeInTheDocument();
        });

        it('should apply className', () => {
            const { container } = render(
                <AnimationProvider>
                    <PageTransition className="custom-class">
                        <div>Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(container.querySelector('.custom-class')).toBeInTheDocument();
        });

        it('should render without animation provider', () => {
            render(
                <PageTransition>
                    <div>Content</div>
                </PageTransition>
            );

            expect(screen.getByText('Content')).toBeInTheDocument();
        });
    });

    describe('Transition Types', () => {
        it('should render fade transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="fade">
                        <div>Fade Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Fade Content')).toBeInTheDocument();
        });

        it('should render slide-left transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-left">
                        <div>Slide Left Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Left Content')).toBeInTheDocument();
        });

        it('should render slide-right transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-right">
                        <div>Slide Right Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Right Content')).toBeInTheDocument();
        });

        it('should render slide-up transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-up">
                        <div>Slide Up Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Up Content')).toBeInTheDocument();
        });

        it('should render slide-down transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-down">
                        <div>Slide Down Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Down Content')).toBeInTheDocument();
        });

        it('should render scale transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="scale">
                        <div>Scale Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Scale Content')).toBeInTheDocument();
        });

        it('should render scale-fade transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="scale-fade">
                        <div>Scale Fade Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Scale Fade Content')).toBeInTheDocument();
        });

        it('should render rotate transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="rotate">
                        <div>Rotate Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Rotate Content')).toBeInTheDocument();
        });

        it('should render flip transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="flip">
                        <div>Flip Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Flip Content')).toBeInTheDocument();
        });

        it('should render zoom transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="zoom">
                        <div>Zoom Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Zoom Content')).toBeInTheDocument();
        });

        it('should render slide-fade-left transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-fade-left">
                        <div>Slide Fade Left Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Left Content')).toBeInTheDocument();
        });

        it('should render slide-fade-right transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-fade-right">
                        <div>Slide Fade Right Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Right Content')).toBeInTheDocument();
        });

        it('should render slide-fade-up transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-fade-up">
                        <div>Slide Fade Up Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Up Content')).toBeInTheDocument();
        });

        it('should render slide-fade-down transition', () => {
            render(
                <AnimationProvider>
                    <PageTransition type="slide-fade-down">
                        <div>Slide Fade Down Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Down Content')).toBeInTheDocument();
        });
    });

    describe('Custom Configuration', () => {
        it('should accept custom duration', () => {
            render(
                <AnimationProvider>
                    <PageTransition duration={1000}>
                        <div>Custom Duration</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Custom Duration')).toBeInTheDocument();
        });

        it('should accept custom delay', () => {
            render(
                <AnimationProvider>
                    <PageTransition delay={200}>
                        <div>Custom Delay</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Custom Delay')).toBeInTheDocument();
        });

        it('should accept animationKey', () => {
            render(
                <AnimationProvider>
                    <PageTransition animationKey="page-1">
                        <div>Keyed Animation</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Keyed Animation')).toBeInTheDocument();
        });

        it('should accept custom transition config', () => {
            render(
                <AnimationProvider>
                    <PageTransition transition={{ duration: 0.5, ease: 'linear' }}>
                        <div>Custom Transition</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Custom Transition')).toBeInTheDocument();
        });

        it('should accept custom variants', () => {
            const customVariants = {
                initial: { opacity: 0, scale: 0.5 },
                animate: { opacity: 1, scale: 1 },
                exit: { opacity: 0, scale: 0.5 },
            };

            render(
                <AnimationProvider>
                    <PageTransition variants={customVariants}>
                        <div>Custom Variants</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Custom Variants')).toBeInTheDocument();
        });

        it('should respect animateInitial prop', () => {
            render(
                <AnimationProvider>
                    <PageTransition animateInitial={false}>
                        <div>No Initial Animation</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('No Initial Animation')).toBeInTheDocument();
        });
    });

    describe('Preset Components', () => {
        it('should render FadeTransition', () => {
            render(
                <AnimationProvider>
                    <FadeTransition>
                        <div>Fade Preset</div>
                    </FadeTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Fade Preset')).toBeInTheDocument();
        });

        it('should render SlideLeftTransition', () => {
            render(
                <AnimationProvider>
                    <SlideLeftTransition>
                        <div>Slide Left Preset</div>
                    </SlideLeftTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Left Preset')).toBeInTheDocument();
        });

        it('should render SlideRightTransition', () => {
            render(
                <AnimationProvider>
                    <SlideRightTransition>
                        <div>Slide Right Preset</div>
                    </SlideRightTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Right Preset')).toBeInTheDocument();
        });

        it('should render SlideUpTransition', () => {
            render(
                <AnimationProvider>
                    <SlideUpTransition>
                        <div>Slide Up Preset</div>
                    </SlideUpTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Up Preset')).toBeInTheDocument();
        });

        it('should render SlideDownTransition', () => {
            render(
                <AnimationProvider>
                    <SlideDownTransition>
                        <div>Slide Down Preset</div>
                    </SlideDownTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Down Preset')).toBeInTheDocument();
        });

        it('should render ScaleTransition', () => {
            render(
                <AnimationProvider>
                    <ScaleTransition>
                        <div>Scale Preset</div>
                    </ScaleTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Scale Preset')).toBeInTheDocument();
        });

        it('should render ScaleFadeTransition', () => {
            render(
                <AnimationProvider>
                    <ScaleFadeTransition>
                        <div>Scale Fade Preset</div>
                    </ScaleFadeTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Scale Fade Preset')).toBeInTheDocument();
        });

        it('should render SlideFadeLeftTransition', () => {
            render(
                <AnimationProvider>
                    <SlideFadeLeftTransition>
                        <div>Slide Fade Left Preset</div>
                    </SlideFadeLeftTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Left Preset')).toBeInTheDocument();
        });

        it('should render SlideFadeRightTransition', () => {
            render(
                <AnimationProvider>
                    <SlideFadeRightTransition>
                        <div>Slide Fade Right Preset</div>
                    </SlideFadeRightTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Slide Fade Right Preset')).toBeInTheDocument();
        });
    });

    describe('Reduced Motion', () => {
        it('should respect reduced motion preference', () => {
            // Mock reduced motion
            Object.defineProperty(window, 'matchMedia', {
                writable: true,
                value: vi.fn().mockImplementation((query) => ({
                    matches: query === '(prefers-reduced-motion: reduce)',
                    media: query,
                    onchange: null,
                    addListener: vi.fn(),
                    removeListener: vi.fn(),
                    addEventListener: vi.fn(),
                    removeEventListener: vi.fn(),
                    dispatchEvent: vi.fn(),
                })),
            });

            render(
                <AnimationProvider>
                    <PageTransition>
                        <div>Reduced Motion Content</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Reduced Motion Content')).toBeInTheDocument();
        });
    });

    describe('Animation Configuration', () => {
        it('should work with custom animation config', () => {
            render(
                <AnimationProvider config={{ durationMultiplier: 2 }}>
                    <PageTransition>
                        <div>Custom Config</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Custom Config')).toBeInTheDocument();
        });

        it('should work with animations disabled', () => {
            render(
                <AnimationProvider config={{ enabled: false }}>
                    <PageTransition>
                        <div>Disabled Animations</div>
                    </PageTransition>
                </AnimationProvider>
            );

            expect(screen.getByText('Disabled Animations')).toBeInTheDocument();
        });
    });
});
