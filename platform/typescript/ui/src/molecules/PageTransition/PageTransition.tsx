import { ReactNode, useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { useReducedMotion } from '../../hooks/useReducedMotion';

export type TransitionType = 'fade' | 'slideLeft' | 'slideRight' | 'slideUp' | 'slideDown' | 'scale' | 'none';

export interface PageTransitionProps {
  /** Child content to animate */
  children: ReactNode;
  /** Transition animation type */
  type?: TransitionType;
  /** Transition duration in milliseconds */
  duration?: number;
  /** Custom class for transition */
  className?: string;
}

/**
 * Page Transition Component
 * 
 * Animates route transitions with configurable effects.
 * Respects prefers-reduced-motion for accessibility.
 * 
 * @doc.type component
 * @doc.purpose Smooth page transitions with accessibility support
 * @doc.layer core
 * @doc.pattern Animation Component
 * 
 * @example
 * ```tsx
 * // In App.tsx or layout component
 * <PageTransition type="fade">
 *   <Outlet />
 * </PageTransition>
 * ```
 */
export function PageTransition({
  children,
  type = 'fade',
  duration = 200,
  className = '',
}: PageTransitionProps) {
  const location = useLocation();
  const prefersReducedMotion = useReducedMotion();
  const [displayLocation, setDisplayLocation] = useState(location);
  const [transitionStage, setTransitionStage] = useState<'enter' | 'exit'>('enter');

  useEffect(() => {
    if (location !== displayLocation) {
      setTransitionStage('exit');
    }
  }, [location, displayLocation]);

  const handleTransitionEnd = () => {
    if (transitionStage === 'exit') {
      setDisplayLocation(location);
      setTransitionStage('enter');
    }
  };

  // Skip transitions if reduced motion preferred
  const effectiveType = prefersReducedMotion ? 'none' : type;
  const effectiveDuration = prefersReducedMotion ? 0 : duration;

  const transitionClasses = getTransitionClasses(effectiveType, transitionStage, effectiveDuration);

  return (
    <div
      className={`${transitionClasses} ${className}`}
      onTransitionEnd={handleTransitionEnd}
      style={{
        transitionDuration: `${effectiveDuration}ms`,
      }}
    >
      {children}
    </div>
  );
}

/**
 * Get CSS classes for transition type and stage
 */
function getTransitionClasses(
  type: TransitionType,
  stage: 'enter' | 'exit',
  duration: number
): string {
  if (type === 'none' || duration === 0) {
    return '';
  }

  const baseClasses = 'transition-all';

  switch (type) {
    case 'fade':
      return `${baseClasses} ${stage === 'enter' ? 'opacity-100' : 'opacity-0'}`;

    case 'slideLeft':
      return `${baseClasses} ${
        stage === 'enter' ? 'translate-x-0 opacity-100' : '-translate-x-8 opacity-0'
      }`;

    case 'slideRight':
      return `${baseClasses} ${
        stage === 'enter' ? 'translate-x-0 opacity-100' : 'translate-x-8 opacity-0'
      }`;

    case 'slideUp':
      return `${baseClasses} ${
        stage === 'enter' ? 'translate-y-0 opacity-100' : '-translate-y-8 opacity-0'
      }`;

    case 'slideDown':
      return `${baseClasses} ${
        stage === 'enter' ? 'translate-y-0 opacity-100' : 'translate-y-8 opacity-0'
      }`;

    case 'scale':
      return `${baseClasses} ${
        stage === 'enter' ? 'scale-100 opacity-100' : 'scale-95 opacity-0'
      }`;

    default:
      return baseClasses;
  }
}

/**
 * Hook for route-based page transitions
 * 
 * Returns transition type based on navigation direction.
 * Can be extended to detect forward/back navigation.
 */
export function usePageTransition(): TransitionType {
  const prefersReducedMotion = useReducedMotion();
  
  if (prefersReducedMotion) {
    return 'none';
  }

  // Default fade transition
  // TODO: Detect navigation direction (forward/back) and return appropriate type
  return 'fade';
}

export default PageTransition;
