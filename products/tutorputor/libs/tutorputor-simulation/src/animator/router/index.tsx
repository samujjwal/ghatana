// @ts-nocheck
/**
 * React Router v7 Integration for Animator
 * 
 * Provides route-based animation triggers, transitions, and navigation-aware
 * animation effects using React Router v7's new data APIs and navigation state.
 */

import { useEffect, useRef, useCallback, useState } from 'react';
import {
  useLocation,
  useNavigation,
  useMatches,
  useNavigate,
  type Location,
  type Navigation,
} from 'react-router';
import type { AnimationTrack, Animator } from '../index';
import { AnimationPresets } from '../index';

// =============================================================================
// Types
// =============================================================================

export interface RouteAnimationConfig {
  /** Route path pattern to match */
  path: string;
  /** Animation to play when entering this route */
  enterAnimation?: AnimationTrack | AnimationTrack[];
  /** Animation to play when leaving this route */
  exitAnimation?: AnimationTrack | AnimationTrack[];
  /** Whether to animate on initial page load */
  animateOnMount?: boolean;
  /** Animation direction based on navigation depth */
  direction?: 'forward' | 'backward' | 'auto';
  /** Delay before animation starts (ms) */
  delay?: number;
  /** Custom matcher function */
  match?: (location: Location) => boolean;
}

export interface NavigationTransition {
  /** Previous location */
  from: Location | null;
  /** Current location */
  to: Location;
  /** Navigation direction */
  direction: 'forward' | 'backward' | 'unknown';
  /** Navigation state */
  state: 'loading' | 'submitting' | 'idle';
}

export interface PageTransitionProps {
  /** Children to wrap with transition */
  children: React.ReactNode;
  /** Default enter animation */
  defaultEnter?: AnimationTrack[];
  /** Default exit animation */
  defaultExit?: AnimationTrack[];
  /** Transition duration in ms */
  duration?: number;
  /** Whether to use layout animation */
  layout?: boolean;
}

// =============================================================================
// Route Animation Hook
// =============================================================================

export function useRouteAnimation(configs: RouteAnimationConfig[]): any {
  const location = useLocation();
  const navigation = useNavigation();
  const matches = useMatches();
  const navigate = useNavigate();
  
  const [isAnimating, setIsAnimating] = useState(false);
  const [currentDirection, setCurrentDirection] = useState<'forward' | 'backward' | 'unknown'>('unknown');
  const previousLocation = useRef<Location | null>(null);
  const activeAnimator = useRef<Animator | null>(null);

  // Determine navigation direction
  const getDirection = useCallback((from: Location | null, to: Location): 'forward' | 'backward' | 'unknown' => {
    if (!from) return 'unknown';
    
    // Check navigation state for direction hints
    const navState = navigation.state;
    const historyAction = (navigation as any).historyAction;
    
    if (historyAction === 'PUSH') return 'forward';
    if (historyAction === 'POP') {
      // Check if we're going back
      const fromDepth = from.pathname.split('/').length;
      const toDepth = to.pathname.split('/').length;
      return toDepth < fromDepth ? 'backward' : 'forward';
    }
    
    return 'unknown';
  }, [navigation]);

  // Find matching config for location
  const findConfig = useCallback((loc: Location): RouteAnimationConfig | null => {
    return configs.find((config) => {
      if (config.match) {
        return config.match(loc);
      }
      // Simple path matching
      const pathRegex = new RegExp(
        '^' + config.path.replace(/:\w+/g, '\\w+').replace(/\*/g, '.*') + '$'
      );
      return pathRegex.test(loc.pathname);
    }) || null;
  }, [configs]);

  // Play animation
  const playAnimation = useCallback((tracks: AnimationTrack[]) => {
    // Import dynamically to avoid circular dependency
    import('../index').then(({ Animator }) => {
      if (activeAnimator.current) {
        activeAnimator.current.stop();
      }
      
      const animator = new Animator({
        duration: Math.max(...tracks.map((t) => (t.delay || 0) + t.duration)),
        autoplay: true,
      });
      
      tracks.forEach((track) => animator.addTrack(track));
      
      animator.onPlay(() => setIsAnimating(true));
      animator.onComplete(() => setIsAnimating(false));
      
      activeAnimator.current = animator;
    });
  }, []);

  // Handle route changes
  useEffect(() => {
    const direction = getDirection(previousLocation.current, location);
    setCurrentDirection(direction);
    
    const config = findConfig(location);
    if (config) {
      const tracks = direction === 'backward' && config.exitAnimation
        ? Array.isArray(config.exitAnimation) ? config.exitAnimation : [config.exitAnimation]
        : config.enterAnimation
        ? Array.isArray(config.enterAnimation) ? config.enterAnimation : [config.enterAnimation]
        : [];
      
      if (tracks.length > 0) {
        const delayedTracks = config.delay
          ? tracks.map((t) => ({ ...t, delay: (t.delay || 0) + config.delay }))
          : tracks;
        
        playAnimation(delayedTracks);
      }
    }
    
    previousLocation.current = location;
  }, [location, configs, findConfig, getDirection, playAnimation]);

  return {
    isAnimating,
    direction: currentDirection,
    location,
    previousLocation: previousLocation.current,
  };
}

// =============================================================================
// Navigation Transition Hook
// =============================================================================

export function useNavigationTransition(): NavigationTransition {
  const location = useLocation();
  const navigation = useNavigation();
  const previousLocation = useRef<Location | null>(null);

  const direction: 'forward' | 'backward' | 'unknown' = (() => {
    if (!previousLocation.current) return 'unknown';
    
    const fromDepth = previousLocation.current.pathname.split('/').filter(Boolean).length;
    const toDepth = location.pathname.split('/').filter(Boolean).length;
    
    if (toDepth > fromDepth) return 'forward';
    if (toDepth < fromDepth) return 'backward';
    return 'unknown';
  })();

  useEffect(() => {
    if (navigation.state === 'idle') {
      previousLocation.current = location;
    }
  }, [location, navigation.state]);

  return {
    from: previousLocation.current,
    to: location,
    direction,
    state: navigation.state as 'loading' | 'submitting' | 'idle',
  };
}

// =============================================================================
// Page Transition Component
// =============================================================================

export const PageTransition: React.FC<PageTransitionProps> = ({
  children,
  defaultEnter = [AnimationPresets.fadeIn('.page-content', 300)],
  defaultExit = [AnimationPresets.fadeOut('.page-content', 200)],
  duration = 300,
  layout = false,
}) => {
  const { isAnimating, direction } = useNavigationTransition();
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // Apply enter animation
    import('../index').then(({ Animator }) => {
      const animator = new Animator({
        duration,
        autoplay: true,
      });

      const tracks = direction === 'backward' ? defaultExit : defaultEnter;
      tracks.forEach((track) => {
        animator.addTrack({
          ...track,
          target: containerRef.current?.querySelector(track.target) ? track.target : '.page-content',
        });
      });
    });
  }, [direction, defaultEnter, defaultExit, duration]);

  return (
    <div
      ref={containerRef}
      className="page-content"
      style={{
        opacity: isAnimating ? 0 : 1,
        transition: layout ? undefined : `opacity ${duration}ms ease-in-out`,
      }}
    >
      {children}
    </div>
  );
};

// =============================================================================
// Animated Outlet Component
// =============================================================================

import { Outlet } from 'react-router';

export interface AnimatedOutletProps {
  /** Animation configuration for child routes */
  animations?: RouteAnimationConfig[];
  /** Default animation preset */
  preset?: 'fade' | 'slide' | 'scale' | 'none';
  /** Transition duration */
  duration?: number;
}

export const AnimatedOutlet: React.FC<AnimatedOutletProps> = ({
  animations = [],
  preset = 'fade',
  duration = 300,
}) => {
  const outletRef = useRef<HTMLDivElement>(null);
  const { isAnimating } = useRouteAnimation(animations);

  const getPresetAnimations = (): AnimationTrack[] => {
    switch (preset) {
      case 'fade':
        return [AnimationPresets.fadeIn('.outlet-content', duration)];
      case 'slide':
        return [AnimationPresets.slideIn('.outlet-content', 'right', 50, duration)];
      case 'scale':
        return [AnimationPresets.scale('.outlet-content', 0.9, 1, duration)];
      case 'none':
        return [];
      default:
        return [AnimationPresets.fadeIn('.outlet-content', duration)];
    }
  };

  return (
    <div
      ref={outletRef}
      className="outlet-content"
      style={{
        opacity: isAnimating ? 0 : 1,
        transform: preset === 'scale' && isAnimating ? 'scale(0.95)' : undefined,
        transition: `opacity ${duration}ms ease, transform ${duration}ms ease`,
      }}
    >
      <Outlet />
    </div>
  );
};

// =============================================================================
// Route-Aware Animation Components
// =============================================================================

export interface AnimatedLinkProps {
  to: string;
  children: React.ReactNode;
  /** Animation to play before navigation */
  preNavigateAnimation?: AnimationTrack[];
  /** Animation duration */
  duration?: number;
  className?: string;
}

export const AnimatedLink: React.FC<AnimatedLinkProps> = ({
  to,
  children,
  preNavigateAnimation,
  duration = 200,
  className,
}) => {
  const navigate = useNavigate();
  const [isPending, setIsPending] = useState(false);

  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      
      if (preNavigateAnimation && preNavigateAnimation.length > 0) {
        setIsPending(true);
        
        import('../index').then(({ Animator }) => {
          const animator = new Animator({
            duration,
            autoplay: true,
          });
          
          preNavigateAnimation.forEach((track) => animator.addTrack(track));
          
          animator.onComplete(() => {
            navigate(to);
            setIsPending(false);
          });
        });
      } else {
        navigate(to);
      }
    },
    [to, navigate, preNavigateAnimation, duration]
  );

  return (
    <a
      href={to}
      onClick={handleClick}
      className={className}
      style={{
        opacity: isPending ? 0.5 : 1,
        pointerEvents: isPending ? 'none' : undefined,
      }}
    >
      {children}
    </a>
  );
};

// =============================================================================
// Scroll-Triggered Animations
// =============================================================================

export interface ScrollAnimationProps {
  children: React.ReactNode;
  /** Animation to play when element enters viewport */
  animation: AnimationTrack | AnimationTrack[];
  /** Threshold for triggering (0-1) */
  threshold?: number;
  /** Root margin for intersection observer */
  rootMargin?: string;
  /** Only animate once */
  once?: boolean;
}

export const ScrollAnimation: React.FC<ScrollAnimationProps> = ({
  children,
  animation,
  threshold = 0.2,
  rootMargin = '0px',
  once = true,
}) => {
  const elementRef = useRef<HTMLDivElement>(null);
  const [hasAnimated, setHasAnimated] = useState(false);

  useEffect(() => {
    const element = elementRef.current;
    if (!element) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && (!once || !hasAnimated)) {
            const tracks = Array.isArray(animation) ? animation : [animation];
            
            import('../index').then(({ Animator }) => {
              const animator = new Animator({
                duration: Math.max(...tracks.map((t) => t.duration)),
                autoplay: true,
              });
              
              tracks.forEach((track) => animator.addTrack(track));
            });
            
            if (once) {
              setHasAnimated(true);
            }
          }
        });
      },
      { threshold, rootMargin }
    );

    observer.observe(element);
    
    return () => observer.disconnect();
  }, [animation, threshold, rootMargin, once, hasAnimated]);

  return <div ref={elementRef}>{children}</div>;
};

// =============================================================================
// Loading Animation Integration
// =============================================================================

export interface LoadingAnimationProps {
  /** Whether navigation is in progress */
  loading?: boolean;
  /** Children content */
  children: React.ReactNode;
  /** Loading animation preset */
  preset?: 'spinner' | 'pulse' | 'skeleton' | 'custom';
  /** Custom animation tracks */
  customAnimation?: AnimationTrack[];
}

export const LoadingAnimation: React.FC<LoadingAnimationProps> = ({
  loading = false,
  children,
  preset = 'spinner',
  customAnimation,
}) => {
  const { state } = useNavigationTransition();
  const isLoading = loading || state === 'loading' || state === 'submitting';

  const getLoadingAnimation = (): AnimationTrack[] => {
    if (customAnimation) return customAnimation;
    
    switch (preset) {
      case 'spinner':
        return [AnimationPresets.rotate('.loading-indicator', 0, 360, 1000)];
      case 'pulse':
        return [AnimationPresets.pulse('.loading-indicator', 1000)];
      case 'skeleton':
        return [
          {
            id: 'skeleton-shimmer',
            target: '.loading-skeleton',
            property: 'background-position',
            from: '-200% 0',
            to: '200% 0',
            duration: 1500,
            easing: 'linear',
            repeat: -1,
          },
        ];
      default:
        return [];
    }
  };

  return (
    <div style={{ position: 'relative' }}>
      {isLoading && (
        <div
          className={`loading-indicator loading-${preset}`}
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'rgba(255, 255, 255, 0.8)',
            zIndex: 10,
          }}
        >
          <div className="loading-content">
            {preset === 'spinner' && (
              <div
                style={{
                  width: 40,
                  height: 40,
                  border: '3px solid #ccc',
                  borderTopColor: '#4ecdc4',
                  borderRadius: '50%',
                  animation: 'spin 1s linear infinite',
                }}
              />
            )}
            {preset === 'pulse' && (
              <div
                style={{
                  width: 40,
                  height: 40,
                  backgroundColor: '#4ecdc4',
                  borderRadius: '50%',
                  animation: 'pulse 1.5s ease-in-out infinite',
                }}
              />
            )}
          </div>
        </div>
      )}
      <div style={{ opacity: isLoading ? 0.5 : 1, transition: 'opacity 200ms' }}>
        {children}
      </div>
    </div>
  );
};

// =============================================================================
// Utility Functions
// =============================================================================

export function createRouteTransition(
  from: string,
  to: string,
  direction: 'forward' | 'backward' = 'forward'
): AnimationTrack[] {
  const enterDirection = direction === 'forward' ? 'right' : 'left';
  const exitDirection = direction === 'forward' ? 'left' : 'right';

  return [
    AnimationPresets.slideIn(to, enterDirection, 100, 300),
    AnimationPresets.fadeIn(to, 300),
  ];
}

export function createNestedRouteTransition(depth: number): AnimationTrack[] {
  const direction = depth > 0 ? 'right' : 'left';
  return [
    AnimationPresets.slideIn('.nested-route', direction, 50, 250),
    AnimationPresets.fadeIn('.nested-route', 250),
  ];
}

export function usePrefetchAnimation(href: string, delay: number = 100): {
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onFocus: () => void;
} {
  const navigate = useNavigate();
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);

  const prefetch = useCallback(() => {
    // Trigger React Router prefetch
    navigate(href, { preload: 'intent' });
  }, [navigate, href]);

  return {
    onMouseEnter: () => {
      timeoutRef.current = setTimeout(prefetch, delay);
    },
    onMouseLeave: () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    },
    onFocus: () => {
      prefetch();
    },
  };
}
