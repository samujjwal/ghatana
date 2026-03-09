import { useEffect, useRef, useState } from "react";

export interface UseImageOptimizationProps {
  /** Image source URL */
  src: string;
  /** Alternative text for accessibility */
  alt: string;
  /** Placeholder image (low-quality or blurred) */
  placeholder?: string;
  /** Whether to lazy load the image */
  lazy?: boolean;
  /** Callback when image loads */
  onLoad?: () => void;
  /** Callback on load error */
  onError?: () => void;
}

export interface UseImageOptimizationResult {
  /** Reference to attach to img element */
  ref: React.RefObject<HTMLImageElement | null>;
  /** Current image source (might be placeholder initially) */
  currentSrc: string;
  /** Whether image is loaded */
  isLoaded: boolean;
  /** Whether loading error occurred */
  hasError: boolean;
}

/**
 * Hook for optimized image loading with lazy loading and progressive enhancement
 *
 * @example
 * ```tsx
 * function OptimizedImage() {
 *   const { ref, currentSrc, isLoaded } = useImageOptimization({
 *     src: '/path/to/image.jpg',
 *     alt: 'Description',
 *     placeholder: '/path/to/placeholder.jpg',
 *     lazy: true,
 *   });
 *
 *   return (
 *     <img
 *       ref={ref}
 *       src={currentSrc}
 *       alt="Description"
 *       className={isLoaded ? 'opacity-100' : 'opacity-50'}
 *     />
 *   );
 * }
 * ```
 */
export function useImageOptimization({
  src,
  alt,
  placeholder,
  lazy = true,
  onLoad,
  onError,
}: UseImageOptimizationProps): UseImageOptimizationResult {
  const ref = useRef<HTMLImageElement>(null);
  const [currentSrc, setCurrentSrc] = useState(placeholder || src);
  const [isLoaded, setIsLoaded] = useState(!placeholder && !lazy);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    const img = ref.current;
    if (!img) return;

    // Create a new image object to preload
    const imageLoader = new Image();

    const handleLoad = () => {
      setCurrentSrc(src);
      setIsLoaded(true);
      setHasError(false);
      onLoad?.();
    };

    const handleError = () => {
      setHasError(true);
      onError?.();
    };

    // If lazy loading is enabled, use Intersection Observer
    if (lazy && "IntersectionObserver" in window) {
      const observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              imageLoader.src = src;
              observer.unobserve(img);
            }
          });
        },
        {
          // Start loading slightly before image enters viewport
          rootMargin: "50px",
        },
      );

      observer.observe(img);

      imageLoader.onload = handleLoad;
      imageLoader.onerror = handleError;

      return () => {
        observer.disconnect();
      };
    } else {
      // Immediate loading if not lazy
      imageLoader.src = src;
      imageLoader.onload = handleLoad;
      imageLoader.onerror = handleError;
    }
  }, [src, lazy, onLoad, onError]);

  return {
    ref,
    currentSrc,
    isLoaded,
    hasError,
  };
}
