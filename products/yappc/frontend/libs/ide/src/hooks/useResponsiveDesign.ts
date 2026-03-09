/**
 * @ghatana/yappc-ide - Responsive Design Hook
 * 
 * Responsive design utilities for IDE layout adaptation
 * across different screen sizes and device types.
 * 
 * @doc.type hook
 * @doc.purpose Responsive design for IDE layout adaptation
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';

/**
 * Breakpoint definitions
 */
export const BREAKPOINTS = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
  '3xl': 1920,
} as const;

export type Breakpoint = keyof typeof BREAKPOINTS;

/**
 * Device type definitions
 */
export type DeviceType = 'mobile' | 'tablet' | 'desktop' | 'large-desktop';

/**
 * Orientation types
 */
export type Orientation = 'portrait' | 'landscape';

/**
 * Responsive design state
 */
interface ResponsiveState {
  width: number;
  height: number;
  breakpoint: Breakpoint;
  deviceType: DeviceType;
  orientation: Orientation;
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isLargeDesktop: boolean;
  isTouchDevice: boolean;
  pixelRatio: number;
}

/**
 * Hook for responsive design
 */
export const useResponsiveDesign = () => {
  const [state, setState] = useState<ResponsiveState>(() => getInitialState());

  /**
   * Get initial state
   */
  function getInitialState(): ResponsiveState {
    if (typeof window === 'undefined') {
      return {
        width: 1024,
        height: 768,
        breakpoint: 'lg',
        deviceType: 'desktop',
        orientation: 'landscape',
        isMobile: false,
        isTablet: false,
        isDesktop: true,
        isLargeDesktop: false,
        isTouchDevice: false,
        pixelRatio: 1,
      };
    }

    const width = window.innerWidth;
    const windowHeight = window.innerHeight;
    const breakpoint = getCurrentBreakpoint(width);
    const deviceType = getDeviceType(width);
    const orientation = getOrientation(width, windowHeight);

    return {
      width,
      height: windowHeight,
      breakpoint,
      deviceType,
      orientation,
      isMobile: deviceType === 'mobile',
      isTablet: deviceType === 'tablet',
      isDesktop: deviceType === 'desktop',
      isLargeDesktop: deviceType === 'large-desktop',
      isTouchDevice: 'ontouchstart' in window,
      pixelRatio: window.devicePixelRatio || 1,
    };
  }

  /**
   * Get current breakpoint
   */
  function getCurrentBreakpoint(width: number): Breakpoint {
    const entries = Object.entries(BREAKPOINTS) as [Breakpoint, number][];
    
    for (let i = entries.length - 1; i >= 0; i--) {
      const [key, value] = entries[i];
      if (width >= value) {
        return key;
      }
    }
    
    return 'xs';
  }

  /**
   * Get device type based on screen dimensions
   */
  function getDeviceType(width: number): DeviceType {
    if (width < BREAKPOINTS.sm) {
      return 'mobile';
    } else if (width < BREAKPOINTS.lg) {
      return 'tablet';
    } else if (width < BREAKPOINTS['2xl']) {
      return 'desktop';
    } else {
      return 'large-desktop';
    }
  }

  /**
   * Get screen orientation
   */
  function getOrientation(width: number, height: number): Orientation {
    return width > height ? 'landscape' : 'portrait';
  }

  /**
   * Handle window resize
   */
  const handleResize = useCallback(() => {
    const width = window.innerWidth;
    const height = window.innerHeight;
    const breakpoint = getCurrentBreakpoint(width);
    const deviceType = getDeviceType(width);
    const orientation = getOrientation(width, height);

    setState(prev => ({
      ...prev,
      width,
      height,
      breakpoint,
      deviceType,
      orientation,
      isMobile: deviceType === 'mobile',
      isTablet: deviceType === 'tablet',
      isDesktop: deviceType === 'desktop',
      isLargeDesktop: deviceType === 'large-desktop',
    }));
  }, []);

  /**
   * Setup resize listener
   */
  useEffect(() => {
    if (typeof window === 'undefined') return;

    let resizeTimer: NodeJS.Timeout;
    
    const debouncedResize = () => {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(handleResize, 100);
    };

    window.addEventListener('resize', debouncedResize);
    window.addEventListener('orientationchange', debouncedResize);

    return () => {
      window.removeEventListener('resize', debouncedResize);
      window.removeEventListener('orientationchange', debouncedResize);
      clearTimeout(resizeTimer);
    };
  }, [handleResize]);

  /**
   * Media query helpers
   */
  const mediaQuery = useMemo(() => ({
    /**
     * Check if current breakpoint matches or is larger
     */
    up: (breakpoint: Breakpoint): boolean => {
      return state.width >= BREAKPOINTS[breakpoint];
    },

    /**
     * Check if current breakpoint matches or is smaller
     */
    down: (breakpoint: Breakpoint): boolean => {
      return state.width < BREAKPOINTS[breakpoint];
    },

    /**
     * Check if current breakpoint is exactly
     */
    only: (breakpoint: Breakpoint): boolean => {
      return state.breakpoint === breakpoint;
    },

    /**
     * Check if current breakpoint is in range
     */
    between: (min: Breakpoint, max: Breakpoint): boolean => {
      return state.width >= BREAKPOINTS[min] && state.width < BREAKPOINTS[max];
    },
  }), [state.width, state.breakpoint]);

  /**
   * Layout configuration based on screen size
   */
  const layout = useMemo(() => {
    const baseConfig = {
      sidebar: {
        width: state.isMobile ? '100%' : state.isTablet ? '280px' : '320px',
        collapsible: !state.isMobile,
        defaultCollapsed: state.isMobile,
        overlay: state.isMobile,
      },
      header: {
        height: state.isMobile ? '56px' : '64px',
        showBreadcrumb: !state.isMobile,
        showSearch: !state.isMobile,
      },
      footer: {
        height: state.isMobile ? '40px' : '48px',
        show: !state.isMobile,
      },
      editor: {
        fontSize: state.isMobile ? '14px' : state.isTablet ? '15px' : '16px',
        lineHeight: state.isMobile ? '1.4' : '1.5',
        tabSize: state.isMobile ? 2 : 4,
        minimap: !state.isMobile && !state.isTablet,
        lineNumbers: true,
        wordWrap: state.isMobile,
      },
      panels: {
        maxWidth: state.isMobile ? '100%' : '400px',
        defaultHeight: state.isMobile ? '200px' : '300px',
        resizable: !state.isMobile,
        collapsible: true,
      },
    };

    return baseConfig;
  }, [state.isMobile, state.isTablet]);

  /**
   * Touch-specific adjustments
   */
  const touch = useMemo(() => ({
    /**
     * Get appropriate touch targets
     */
    getTouchTarget: (size: 'small' | 'medium' | 'large') => {
      const sizes = {
        small: state.isTouchDevice ? '44px' : '32px',
        medium: state.isTouchDevice ? '48px' : '40px',
        large: state.isTouchDevice ? '52px' : '48px',
      };
      return sizes[size];
    },

    /**
     * Get spacing for touch interfaces
     */
    getSpacing: (type: 'tight' | 'normal' | 'loose') => {
      const spacing = {
        tight: state.isTouchDevice ? '8px' : '4px',
        normal: state.isTouchDevice ? '16px' : '8px',
        loose: state.isTouchDevice ? '24px' : '16px',
      };
      return spacing[type];
    },

    /**
     * Check if hover interactions should be disabled
     */
    disableHover: state.isTouchDevice,
  }), [state.isTouchDevice]);

  /**
   * Performance optimizations
   */
  const performance = useMemo(() => ({
    /**
     * Reduce animations on low-end devices
     */
    reduceAnimations: state.pixelRatio < 2 || state.width < BREAKPOINTS.md,

    /**
     * Enable virtual scrolling for large lists
     */
    enableVirtualScrolling: state.width < BREAKPOINTS.lg,

    /**
     * Lazy load components
     */
    lazyLoadComponents: state.width < BREAKPOINTS.lg,

    /**
     * Debounce resize events
     */
    debounceResize: true,
  }), [state.pixelRatio, state.width]);

  /**
   * Accessibility adjustments
   */
  const accessibility = useMemo(() => ({
    /**
     * Increase touch targets for accessibility
     */
    largeTouchTargets: state.isTouchDevice || state.pixelRatio > 1,

    /**
     * High contrast mode detection
     */
    highContrast: () => {
      if (typeof window === 'undefined') return false;
      return window.matchMedia('(prefers-contrast: high)').matches;
    },

    /**
     * Reduced motion preference
     */
    reducedMotion: () => {
      if (typeof window === 'undefined') return false;
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    },
  }), [state.isTouchDevice, state.pixelRatio]);

  /**
   * Container queries simulation
   */
  const container = useMemo(() => ({
    /**
     * Get container class based on available width
     */
    getContainerClass: (availableWidth: number) => {
      if (availableWidth < BREAKPOINTS.sm) return 'container-xs';
      if (availableWidth < BREAKPOINTS.md) return 'container-sm';
      if (availableWidth < BREAKPOINTS.lg) return 'container-md';
      if (availableWidth < BREAKPOINTS.xl) return 'container-lg';
      if (availableWidth < BREAKPOINTS['2xl']) return 'container-xl';
      return 'container-2xl';
    },

    /**
     * Get column count for grid layouts
     */
    getColumns: (minWidth: number, availableWidth: number) => {
      return Math.floor(availableWidth / minWidth);
    },

    /**
     * Get optimal item size for masonry layout
     */
    getMasonryItemWidth: (columns: number, gap: number = 16) => {
      return `calc((100% - ${(columns - 1) * gap}px) / ${columns})`;
    },
  }), []);

  return {
    // Current state
    ...state,

    // Media queries
    mediaQuery,

    // Layout configuration
    layout,

    // Touch adjustments
    touch,

    // Performance optimizations
    performance,

    // Accessibility
    accessibility,

    // Container queries
    container,

    // Utilities
    utils: {
      /**
       * Convert responsive value to CSS
       */
      responsiveValue: (values: Partial<Record<Breakpoint, string>>, defaultValue: string) => {
        const breakpoint = state.breakpoint;
        const breakpointKeys = Object.keys(BREAKPOINTS) as Breakpoint[];
        
        for (let i = breakpointKeys.indexOf(breakpoint); i >= 0; i--) {
          const key = breakpointKeys[i];
          if (values[key]) {
            return values[key];
          }
        }
        
        return defaultValue;
      },

      /**
       * Get responsive spacing
       */
      spacing: (multiplier: number = 1) => {
        const base = state.isMobile ? 4 : 8;
        return `${base * multiplier}px`;
      },

      /**
       * Get responsive font size
       */
      fontSize: (size: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl') => {
        const sizes = {
          xs: state.isMobile ? '12px' : '11px',
          sm: state.isMobile ? '14px' : '13px',
          md: state.isMobile ? '16px' : '14px',
          lg: state.isMobile ? '18px' : '16px',
          xl: state.isMobile ? '20px' : '18px',
          '2xl': state.isMobile ? '24px' : '20px',
          '3xl': state.isMobile ? '30px' : '24px',
        };
        return sizes[size];
      },
    },
  };
};

/**
 * Hook for container queries (simulated)
 */
export const useContainerQuery = (ref: React.RefObject<HTMLElement>) => {
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setWidth(entry.contentRect.width);
      }
    });

    resizeObserver.observe(element);

    return () => {
      resizeObserver.disconnect();
    };
  }, [ref]);

  const breakpoint = useMemo(() => {
    if (width < BREAKPOINTS.sm) return 'xs';
    if (width < BREAKPOINTS.md) return 'sm';
    if (width < BREAKPOINTS.lg) return 'md';
    if (width < BREAKPOINTS.xl) return 'lg';
    if (width < BREAKPOINTS['2xl']) return 'xl';
    return '2xl';
  }, [width]);

  return {
    width,
    breakpoint,
    isSmall: width < BREAKPOINTS.md,
    isMedium: width >= BREAKPOINTS.md && width < BREAKPOINTS.lg,
    isLarge: width >= BREAKPOINTS.lg,
  };
};

export default useResponsiveDesign;
