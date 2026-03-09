import { useState, useEffect, type RefObject } from 'react';

/**
 * Hook that detects if an element is visible on screen
 *
 * @param ref - Ref to the element to observe
 * @param rootMargin - Margin around the root (default: '0px')
 * @returns Whether the element is visible
 *
 * @example
 * ```tsx
 * const ref = useRef<HTMLDivElement>(null);
 * const isVisible = useOnScreen(ref);
 *
 * return (
 *   <div ref={ref}>
 *     {isVisible ? 'Visible!' : 'Not visible'}
 *   </div>
 * );
 * ```
 */
export function useOnScreen<T extends Element>(
  ref: RefObject<T>,
  rootMargin = '0px'
): boolean {
  const [isIntersecting, setIntersecting] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        setIntersecting(entry.isIntersecting);
      },
      { rootMargin }
    );

    observer.observe(element);

    return () => {
      observer.disconnect();
    };
  }, [ref, rootMargin]);

  return isIntersecting;
}
