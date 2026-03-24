/**
 * Page Transition Component
 *
 * Provides smooth page transitions with various animation presets.
 * Integrates with React Router and respects animation preferences.
 *
 * @module animations/PageTransition
 */

import { motion, type Variants, type Transition } from 'framer-motion';
import React, { useMemo } from 'react';

import { useAnimationConfig, useAnimationsEnabled } from './AnimationProvider';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export type TransitionType =
    | 'fade'
    | 'slide-left'
    | 'slide-right'
    | 'slide-up'
    | 'slide-down'
    | 'scale'
    | 'scale-fade'
    | 'rotate'
    | 'flip'
    | 'zoom'
    | 'slide-fade-left'
    | 'slide-fade-right'
    | 'slide-fade-up'
    | 'slide-fade-down';

/**
 *
 */
export interface PageTransitionProps {
    /**
     * Child content to animate
     */
    children: React.ReactNode;

    /**
     * Transition type
     * @default 'fade'
     */
    type?: TransitionType;

    /**
     * Custom duration in milliseconds
     */
    duration?: number;

    /**
     * Custom delay in milliseconds
     */
    delay?: number;

    /**
     * Animation key for forcing re-animation
     */
    animationKey?: string | number;

    /**
     * Custom transition configuration
     */
    transition?: Transition;

    /**
     * Custom variants
     */
    variants?: Variants;

    /**
     * className for the wrapper
     */
    className?: string;

    /**
     * Whether to animate on initial mount
     * @default true
     */
    animateInitial?: boolean;
}

// ============================================================================
// Animation Variants
// ============================================================================

const fadeVariants: Variants = {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 },
};

const slideLeftVariants: Variants = {
    initial: { x: -100, opacity: 1 },
    animate: { x: 0, opacity: 1 },
    exit: { x: 100, opacity: 1 },
};

const slideRightVariants: Variants = {
    initial: { x: 100, opacity: 1 },
    animate: { x: 0, opacity: 1 },
    exit: { x: -100, opacity: 1 },
};

const slideUpVariants: Variants = {
    initial: { y: 100, opacity: 1 },
    animate: { y: 0, opacity: 1 },
    exit: { y: -100, opacity: 1 },
};

const slideDownVariants: Variants = {
    initial: { y: -100, opacity: 1 },
    animate: { y: 0, opacity: 1 },
    exit: { y: 100, opacity: 1 },
};

const scaleVariants: Variants = {
    initial: { scale: 0.8, opacity: 1 },
    animate: { scale: 1, opacity: 1 },
    exit: { scale: 0.8, opacity: 1 },
};

const scaleFadeVariants: Variants = {
    initial: { scale: 0.9, opacity: 0 },
    animate: { scale: 1, opacity: 1 },
    exit: { scale: 1.1, opacity: 0 },
};

const rotateVariants: Variants = {
    initial: { rotate: -10, opacity: 0 },
    animate: { rotate: 0, opacity: 1 },
    exit: { rotate: 10, opacity: 0 },
};

const flipVariants: Variants = {
    initial: { rotateX: 90, opacity: 0 },
    animate: { rotateX: 0, opacity: 1 },
    exit: { rotateX: -90, opacity: 0 },
};

const zoomVariants: Variants = {
    initial: { scale: 0, opacity: 0 },
    animate: { scale: 1, opacity: 1 },
    exit: { scale: 0, opacity: 0 },
};

const slideFadeLeftVariants: Variants = {
    initial: { x: -50, opacity: 0 },
    animate: { x: 0, opacity: 1 },
    exit: { x: 50, opacity: 0 },
};

const slideFadeRightVariants: Variants = {
    initial: { x: 50, opacity: 0 },
    animate: { x: 0, opacity: 1 },
    exit: { x: -50, opacity: 0 },
};

const slideFadeUpVariants: Variants = {
    initial: { y: 50, opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: -50, opacity: 0 },
};

const slideFadeDownVariants: Variants = {
    initial: { y: -50, opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: 50, opacity: 0 },
};

// ============================================================================
// Variant Map
// ============================================================================

const variantsMap: Record<TransitionType, Variants> = {
    fade: fadeVariants,
    'slide-left': slideLeftVariants,
    'slide-right': slideRightVariants,
    'slide-up': slideUpVariants,
    'slide-down': slideDownVariants,
    scale: scaleVariants,
    'scale-fade': scaleFadeVariants,
    rotate: rotateVariants,
    flip: flipVariants,
    zoom: zoomVariants,
    'slide-fade-left': slideFadeLeftVariants,
    'slide-fade-right': slideFadeRightVariants,
    'slide-fade-up': slideFadeUpVariants,
    'slide-fade-down': slideFadeDownVariants,
};

// ============================================================================
// Component
// ============================================================================

/**
 * PageTransition component for smooth page animations
 *
 * @example
 * // Basic usage
 * <PageTransition type="fade">
 *   <YourPage />
 * </PageTransition>
 *
 * @example
 * // With React Router
 * <Routes>
 *   <Route path="/" element={
 *     <PageTransition type="slide-fade-right" animationKey={location.pathname}>
 *       <HomePage />
 *     </PageTransition>
 *   } />
 * </Routes>
 *
 * @example
 * // Custom duration and delay
 * <PageTransition type="scale-fade" duration={500} delay={100}>
 *   <YourPage />
 * </PageTransition>
 */
export const PageTransition: React.FC<PageTransitionProps> = ({
    children,
    type = 'fade',
    duration,
    delay = 0,
    animationKey,
    transition: customTransition,
    variants: customVariants,
    className,
    animateInitial = true,
}) => {
    const config = useAnimationConfig();
    const animationsEnabled = useAnimationsEnabled();

    // Get variants
    const variants = customVariants || variantsMap[type];

    // Build transition config
    const transition = useMemo<Transition>(() => {
        if (customTransition) {
            return customTransition;
        }

        const baseDuration = duration
            ? duration / 1000
            : config.tokens.duration.normal / 1000;

        return {
            duration: baseDuration * config.durationMultiplier,
            delay: delay / 1000,
            ease: [0.4, 0, 0.2, 1], // easeInOut
        };
    }, [duration, delay, config, customTransition]);

    // If animations are disabled, render without animation
    if (!animationsEnabled) {
        return <div className={className}>{children}</div>;
    }

    return (
        <motion.div
            key={animationKey}
            initial={animateInitial ? 'initial' : false}
            animate="animate"
            exit="exit"
            variants={variants}
            transition={transition}
            className={className}
            style={{ width: '100%', height: '100%' }}
        >
            {children}
        </motion.div>
    );
};

// ============================================================================
// Preset Components
// ============================================================================

/**
 * Fade transition preset
 */
export const FadeTransition: React.FC<Omit<PageTransitionProps, 'type'>> = (
    props
) => <PageTransition {...props} type="fade" />;

/**
 * Slide left transition preset
 */
export const SlideLeftTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="slide-left" />;

/**
 * Slide right transition preset
 */
export const SlideRightTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="slide-right" />;

/**
 * Slide up transition preset
 */
export const SlideUpTransition: React.FC<Omit<PageTransitionProps, 'type'>> = (
    props
) => <PageTransition {...props} type="slide-up" />;

/**
 * Slide down transition preset
 */
export const SlideDownTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="slide-down" />;

/**
 * Scale transition preset
 */
export const ScaleTransition: React.FC<Omit<PageTransitionProps, 'type'>> = (
    props
) => <PageTransition {...props} type="scale" />;

/**
 * Scale fade transition preset
 */
export const ScaleFadeTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="scale-fade" />;

/**
 * Slide fade left transition preset
 */
export const SlideFadeLeftTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="slide-fade-left" />;

/**
 * Slide fade right transition preset
 */
export const SlideFadeRightTransition: React.FC<
    Omit<PageTransitionProps, 'type'>
> = (props) => <PageTransition {...props} type="slide-fade-right" />;
