/**
 * Consolidated Canvas Mobile Hook
 * 
 * Replaces: useMobileCanvas + touch interactions
 * Provides: Mobile-specific features
 */

import { useCallback, useEffect, useState } from 'react';

export interface UseCanvasMobileOptions {
  canvasId: string;
  enableGestures?: boolean;
}

export interface UseCanvasMobileReturn {
  onPinchZoom: (scale: number) => void;
  onPan: (delta: { x: number; y: number }) => void;
  onDoubleTap: (position: { x: number; y: number }) => void;
  
  isMobileView: boolean;
  showMobileToolbar: boolean;
  setShowMobileToolbar: (show: boolean) => void;
  
  viewportSize: { width: number; height: number };
  orientation: 'portrait' | 'landscape';
}

export function useCanvasMobile(
  options: UseCanvasMobileOptions
): UseCanvasMobileReturn {
  const { canvasId, enableGestures = true } = options;

  const [showMobileToolbar, setShowMobileToolbar] = useState(true);
  const [viewportSize, setViewportSize] = useState({
    width: window.innerWidth,
    height: window.innerHeight,
  });
  const [orientation, setOrientation] = useState<'portrait' | 'landscape'>(
    window.innerWidth < window.innerHeight ? 'portrait' : 'landscape'
  );

  const isMobileView = viewportSize.width < 768;

  const onPinchZoom = useCallback((scale: number) => {
    console.log('Pinch zoom:', scale);
  }, []);

  const onPan = useCallback((delta: { x: number; y: number }) => {
    console.log('Pan:', delta);
  }, []);

  const onDoubleTap = useCallback((position: { x: number; y: number }) => {
    console.log('Double tap:', position);
  }, []);

  useEffect(() => {
    const handleResize = () => {
      setViewportSize({
        width: window.innerWidth,
        height: window.innerHeight,
      });
      setOrientation(window.innerWidth < window.innerHeight ? 'portrait' : 'landscape');
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return {
    onPinchZoom,
    onPan,
    onDoubleTap,
    isMobileView,
    showMobileToolbar,
    setShowMobileToolbar,
    viewportSize,
    orientation,
  };
}
