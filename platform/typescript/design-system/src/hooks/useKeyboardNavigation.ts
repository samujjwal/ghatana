import { useEffect, RefObject } from 'react';

export interface UseKeyboardNavigationOptions {
  /** Ref to the container element */
  containerRef: RefObject<HTMLElement>;
  /** CSS selector for navigable items */
  itemSelector: string;
  /** Orientation of navigation */
  orientation?: 'vertical' | 'horizontal' | 'both';
  /** Whether navigation should loop */
  loop?: boolean;
  /** Callback when an item is activated */
  onActivate?: (element: HTMLElement, index: number) => void;
}

/**
 * Keyboard Navigation Hook
 * 
 * Provides arrow key navigation for lists, menus, and grids.
 * Supports vertical, horizontal, and grid navigation patterns.
 * 
 * @doc.type hook
 * @doc.purpose Enable accessible keyboard navigation
 * @doc.layer core
 * @doc.pattern Accessibility Hook
 * 
 * @example
 * ```tsx
 * function Menu({ items }) {
 *   const listRef = useRef<HTMLUListElement>(null);
 *   
 *   useKeyboardNavigation({
 *     containerRef: listRef,
 *     itemSelector: '[role="menuitem"]',
 *     orientation: 'vertical',
 *     loop: true,
 *     onActivate: (el, index) => handleSelect(items[index]),
 *   });
 *   
 *   return (
 *     <ul ref={listRef} role="menu">
 *       {items.map(item => (
 *         <li key={item.id} role="menuitem" tabIndex={-1}>
 *           {item.label}
 *         </li>
 *       ))}
 *     </ul>
 *   );
 * }
 * ```
 */
export function useKeyboardNavigation({
  containerRef,
  itemSelector,
  orientation = 'vertical',
  loop = false,
  onActivate,
}: UseKeyboardNavigationOptions) {
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const items = Array.from(container.querySelectorAll<HTMLElement>(itemSelector)).filter(
        (el) => !el.hasAttribute('disabled') && el.offsetParent !== null
      );

      if (items.length === 0) return;

      const currentIndex = items.findIndex((item) => item === document.activeElement);
      let nextIndex = currentIndex;

      // Determine next index based on key and orientation
      switch (e.key) {
        case 'ArrowDown':
          if (orientation === 'vertical' || orientation === 'both') {
            e.preventDefault();
            nextIndex = currentIndex + 1;
          }
          break;

        case 'ArrowUp':
          if (orientation === 'vertical' || orientation === 'both') {
            e.preventDefault();
            nextIndex = currentIndex - 1;
          }
          break;

        case 'ArrowRight':
          if (orientation === 'horizontal' || orientation === 'both') {
            e.preventDefault();
            nextIndex = currentIndex + 1;
          }
          break;

        case 'ArrowLeft':
          if (orientation === 'horizontal' || orientation === 'both') {
            e.preventDefault();
            nextIndex = currentIndex - 1;
          }
          break;

        case 'Home':
          e.preventDefault();
          nextIndex = 0;
          break;

        case 'End':
          e.preventDefault();
          nextIndex = items.length - 1;
          break;

        case 'Enter':
        case ' ':
          if (currentIndex >= 0 && currentIndex < items.length) {
            e.preventDefault();
            const currentElement = items[currentIndex];
            if (onActivate) {
              onActivate(currentElement, currentIndex);
            } else {
              currentElement.click();
            }
          }
          return;

        default:
          return;
      }

      // Handle looping
      if (loop) {
        if (nextIndex < 0) {
          nextIndex = items.length - 1;
        } else if (nextIndex >= items.length) {
          nextIndex = 0;
        }
      } else {
        nextIndex = Math.max(0, Math.min(nextIndex, items.length - 1));
      }

      // Focus next item
      if (nextIndex >= 0 && nextIndex < items.length && nextIndex !== currentIndex) {
        items[nextIndex].focus();
      }
    };

    container.addEventListener('keydown', handleKeyDown);
    return () => container.removeEventListener('keydown', handleKeyDown);
  }, [containerRef, itemSelector, orientation, loop, onActivate]);
}

/**
 * Focus Management Hook
 * 
 * Restores focus to a specified element when conditions change.
 * Useful for modals, dropdowns, and dynamic content.
 * 
 * @example
 * ```tsx
 * const triggerRef = useRef<HTMLButtonElement>(null);
 * const [isOpen, setIsOpen] = useState(false);
 * 
 * useFocusRestore(triggerRef, !isOpen); // Restore focus when closed
 * ```
 */
export function useFocusRestore(elementRef: RefObject<HTMLElement>, shouldRestore: boolean) {
  useEffect(() => {
    if (shouldRestore && elementRef.current) {
      elementRef.current.focus();
    }
  }, [shouldRestore, elementRef]);
}

/**
 * Focus Visible Hook
 * 
 * Detects keyboard vs mouse focus to apply :focus-visible-like behavior.
 * Returns true if user is navigating with keyboard.
 * 
 * @example
 * ```tsx
 * const isKeyboardFocus = useFocusVisible();
 * 
 * <button className={cn('btn', isKeyboardFocus && 'ring-2')}>
 *   Click me
 * </button>
 * ```
 */
export function useFocusVisible() {
  useEffect(() => {
    let hadKeyboardEvent = false;

    const handleKeyDown = () => {
      hadKeyboardEvent = true;
    };

    const handleMouseDown = () => {
      hadKeyboardEvent = false;
    };

    const handleFocus = (e: FocusEvent) => {
      if (hadKeyboardEvent) {
        (e.target as HTMLElement).setAttribute('data-focus-visible', 'true');
      }
    };

    const handleBlur = (e: FocusEvent) => {
      (e.target as HTMLElement).removeAttribute('data-focus-visible');
    };

    document.addEventListener('keydown', handleKeyDown, true);
    document.addEventListener('mousedown', handleMouseDown, true);
    document.addEventListener('focus', handleFocus, true);
    document.addEventListener('blur', handleBlur, true);

    return () => {
      document.removeEventListener('keydown', handleKeyDown, true);
      document.removeEventListener('mousedown', handleMouseDown, true);
      document.removeEventListener('focus', handleFocus, true);
      document.removeEventListener('blur', handleBlur, true);
    };
  }, []);
}

export default useKeyboardNavigation;
