import { useEffect, useRef, ReactNode } from 'react';

export interface FocusTrapProps {
  /** Content to trap focus within */
  children: ReactNode;
  /** Whether the trap is active */
  active?: boolean;
  /** Element to focus when trap activates */
  initialFocus?: HTMLElement | null;
  /** Callback when escape key is pressed */
  onEscape?: () => void;
}

/**
 * Focus Trap Component
 * 
 * Traps keyboard focus within a container (useful for modals, dialogs).
 * Prevents tab navigation from leaving the trapped area.
 * 
 * @doc.type component
 * @doc.purpose Trap keyboard focus for accessible modals
 * @doc.layer core
 * @doc.pattern Accessibility Component
 * 
 * @example
 * ```tsx
 * <FocusTrap active={isModalOpen} onEscape={closeModal}>
 *   <div role="dialog" aria-modal="true">
 *     <h2>Modal Title</h2>
 *     <button>Action</button>
 *     <button onClick={closeModal}>Close</button>
 *   </div>
 * </FocusTrap>
 * ```
 */
export function FocusTrap({ children, active = true, initialFocus, onEscape }: FocusTrapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const previousActiveElement = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!active || !containerRef.current) return;

    // Store previously focused element
    previousActiveElement.current = document.activeElement as HTMLElement;

    // Focus initial element or first focusable element
    const focusElement = initialFocus || findFirstFocusable(containerRef.current);
    focusElement?.focus();

    // Handle keyboard events
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && onEscape) {
        onEscape();
        return;
      }

      if (e.key !== 'Tab') return;

      const focusableElements = getFocusableElements(containerRef.current!);
      if (focusableElements.length === 0) return;

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const activeElement = document.activeElement;

      // Shift + Tab on first element -> focus last
      if (e.shiftKey && activeElement === firstElement) {
        e.preventDefault();
        lastElement.focus();
      }
      // Tab on last element -> focus first
      else if (!e.shiftKey && activeElement === lastElement) {
        e.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      
      // Restore focus to previously focused element
      if (previousActiveElement.current && document.body.contains(previousActiveElement.current)) {
        previousActiveElement.current.focus();
      }
    };
  }, [active, initialFocus, onEscape]);

  return (
    <div ref={containerRef}>
      {children}
    </div>
  );
}

/**
 * Get all focusable elements within a container
 */
function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const selector = [
    'a[href]',
    'button:not([disabled])',
    'textarea:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ');

  return Array.from(container.querySelectorAll<HTMLElement>(selector)).filter(
    (el) => !el.hasAttribute('disabled') && !el.getAttribute('aria-hidden')
  );
}

/**
 * Find the first focusable element in a container
 */
function findFirstFocusable(container: HTMLElement): HTMLElement | null {
  const elements = getFocusableElements(container);
  return elements[0] || null;
}

export default FocusTrap;
