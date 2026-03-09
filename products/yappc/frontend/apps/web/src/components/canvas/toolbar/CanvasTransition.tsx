/**
 * Canvas Transition Component
 * 
 * Provides smooth animated transitions when switching between:
 * - Canvas modes (crossfade, 200ms)
 * - Abstraction levels (scale + fade, 300ms)
 * - Combined mode + level changes (crossfade + scale, 400ms)
 * 
 * Uses Framer Motion for performant animations.
 * 
 * @doc.type component
 * @doc.purpose Canvas transition animations
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { ReactNode, useMemo } from 'react';
import { motion, AnimatePresence, type Variants } from 'framer-motion';
import { useAtom } from 'jotai';
import { transitionStateAtom } from '../../../state/atoms/toolbarAtom';

// ============================================================================
// Types
// ============================================================================

export interface CanvasTransitionProps {
    /** Content to animate */
    children: ReactNode;
    /** Unique key for the current state (mode + level) */
    stateKey: string;
    /** Optional custom transition duration in ms */
    duration?: number;
    /** Optional custom easing */
    easing?: string;
    /** Additional CSS classes */
    className?: string;
}

export interface LevelTransitionProps {
    /** Content to animate */
    children: ReactNode;
    /** Current abstraction level */
    level: string;
    /** Direction of transition: 'in' (drill down) or 'out' (zoom out) */
    direction?: 'in' | 'out' | null;
    /** Additional CSS classes */
    className?: string;
}

export interface ModeTransitionProps {
    /** Content to animate */
    children: ReactNode;
    /** Current canvas mode */
    mode: string;
    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// Animation Variants
// ============================================================================

/**
 * Level transition variants (zoom in/out effect)
 */
const levelVariants: Variants = {
    initial: (direction: 'in' | 'out' | null) => ({
        opacity: 0,
        scale: direction === 'in' ? 0.8 : 1.2,
    }),
    animate: {
        opacity: 1,
        scale: 1,
    },
    exit: (direction: 'in' | 'out' | null) => ({
        opacity: 0,
        scale: direction === 'in' ? 1.2 : 0.8,
    }),
};

/**
 * Mode transition variants (crossfade effect)
 */
const modeVariants: Variants = {
    initial: {
        opacity: 0,
    },
    animate: {
        opacity: 1,
    },
    exit: {
        opacity: 0,
    },
};

/**
 * Combined transition variants (mode + level change)
 */
const combinedVariants: Variants = {
    initial: (direction: 'in' | 'out' | null) => ({
        opacity: 0,
        scale: direction === 'in' ? 0.9 : 1.1,
    }),
    animate: {
        opacity: 1,
        scale: 1,
    },
    exit: (direction: 'in' | 'out' | null) => ({
        opacity: 0,
        scale: direction === 'in' ? 1.1 : 0.9,
    }),
};

// ============================================================================
// Transition Configurations
// ============================================================================

const LEVEL_TRANSITION = {
    duration: 0.3,
    ease: 'easeInOut',
};

const MODE_TRANSITION = {
    duration: 0.2,
    ease: 'easeInOut',
};

const COMBINED_TRANSITION = {
    duration: 0.4,
    ease: 'easeInOut',
};

// ============================================================================
// Components
// ============================================================================

/**
 * Level Transition Wrapper
 * 
 * Animates content when abstraction level changes.
 * Uses scale + fade animation to simulate zooming.
 * 
 * @example
 * ```tsx
 * <LevelTransition level={currentLevel} direction={transitionDirection}>
 *   <CanvasContent />
 * </LevelTransition>
 * ```
 */
export function LevelTransition({
    children,
    level,
    direction = null,
    className = '',
}: LevelTransitionProps) {
    return (
        <AnimatePresence mode="wait" custom={direction}>
            <motion.div
                key={level}
                custom={direction}
                variants={levelVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={LEVEL_TRANSITION}
                className={`w-full h-full ${className}`}
            >
                {children}
            </motion.div>
        </AnimatePresence>
    );
}

/**
 * Mode Transition Wrapper
 * 
 * Animates content when canvas mode changes.
 * Uses crossfade animation for smooth mode switching.
 * 
 * @example
 * ```tsx
 * <ModeTransition mode={currentMode}>
 *   <ModeSpecificContent />
 * </ModeTransition>
 * ```
 */
export function ModeTransition({
    children,
    mode,
    className = '',
}: ModeTransitionProps) {
    return (
        <AnimatePresence mode="wait">
            <motion.div
                key={mode}
                variants={modeVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={MODE_TRANSITION}
                className={`w-full h-full ${className}`}
            >
                {children}
            </motion.div>
        </AnimatePresence>
    );
}

/**
 * Canvas Transition Wrapper
 * 
 * Unified transition component that handles both mode and level changes.
 * Automatically detects the type of transition and applies appropriate animation.
 * 
 * @example
 * ```tsx
 * <CanvasTransition stateKey={`${mode}-${level}`}>
 *   <CanvasContent mode={mode} level={level} />
 * </CanvasTransition>
 * ```
 */
export function CanvasTransition({
    children,
    stateKey,
    duration,
    easing = 'easeInOut',
    className = '',
}: CanvasTransitionProps) {
    const [transitionState] = useAtom(transitionStateAtom);
    
    const { direction, fromLevel, toLevel, fromMode, toMode } = transitionState;
    
    // Determine transition type
    const isCombined = (fromLevel && toLevel) && (fromMode && toMode);
    const isLevelChange = fromLevel && toLevel && !fromMode && !toMode;
    
    // Select appropriate variants and transition
    const variants = useMemo(() => {
        if (isCombined) return combinedVariants;
        if (isLevelChange) return levelVariants;
        return modeVariants;
    }, [isCombined, isLevelChange]);
    
    const transition = useMemo(() => {
        const base = isCombined 
            ? COMBINED_TRANSITION 
            : isLevelChange 
                ? LEVEL_TRANSITION 
                : MODE_TRANSITION;
        
        return {
            ...base,
            duration: duration ? duration / 1000 : base.duration,
            ease: easing,
        };
    }, [isCombined, isLevelChange, duration, easing]);

    return (
        <AnimatePresence mode="wait" custom={direction}>
            <motion.div
                key={stateKey}
                custom={direction}
                variants={variants}
                initial="initial"
                animate="animate"
                exit="exit"
                transition={transition}
                className={`w-full h-full ${className}`}
            >
                {children}
            </motion.div>
        </AnimatePresence>
    );
}

// ============================================================================
// Utility Components
// ============================================================================

/**
 * Fade In component for simple fade animations
 */
export function FadeIn({
    children,
    delay = 0,
    duration = 0.2,
    className = '',
}: {
    children: ReactNode;
    delay?: number;
    duration?: number;
    className?: string;
}) {
    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay, duration, ease: 'easeOut' }}
            className={className}
        >
            {children}
        </motion.div>
    );
}

/**
 * Scale In component for scale + fade animations
 */
export function ScaleIn({
    children,
    delay = 0,
    duration = 0.3,
    className = '',
}: {
    children: ReactNode;
    delay?: number;
    duration?: number;
    className?: string;
}) {
    return (
        <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay, duration, ease: 'easeOut' }}
            className={className}
        >
            {children}
        </motion.div>
    );
}

/**
 * Slide In component for slide + fade animations
 */
export function SlideIn({
    children,
    direction = 'up',
    delay = 0,
    duration = 0.3,
    className = '',
}: {
    children: ReactNode;
    direction?: 'up' | 'down' | 'left' | 'right';
    delay?: number;
    duration?: number;
    className?: string;
}) {
    const offsets = {
        up: { y: 20 },
        down: { y: -20 },
        left: { x: 20 },
        right: { x: -20 },
    };

    return (
        <motion.div
            initial={{ opacity: 0, ...offsets[direction] }}
            animate={{ opacity: 1, x: 0, y: 0 }}
            transition={{ delay, duration, ease: 'easeOut' }}
            className={className}
        >
            {children}
        </motion.div>
    );
}

// ============================================================================
// Exports
// ============================================================================

export default CanvasTransition;
