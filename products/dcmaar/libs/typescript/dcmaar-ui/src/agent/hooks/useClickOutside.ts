import { RefObject, useEffect, useRef } from 'react';

export type Event = MouseEvent | TouchEvent;

/**
 * A custom hook that triggers a callback when a click is detected outside of the specified element.
 * 
 * @param ref - The ref of the element to detect outside clicks for
 * @param handler - The callback function to execute when a click outside is detected
 * @param options - Additional options for the hook
 * @param options.enabled - Whether the event listener is active (default: true)
 * @param options.eventType - The type of events to listen for (default: ['mousedown', 'touchstart'])
 * 
 * @example
 * const ref = useRef<HTMLDivElement>(null);
 * useClickOutside(ref, () => {
 *   // Handle click outside
 * });
 * 
 * return (
 *   <div ref={ref}>
 *     Click outside this element to trigger the callback
 *   </div>
 * );
 */
export function useClickOutside<T extends HTMLElement = HTMLElement>(
  ref: RefObject<T>,
  handler: (event: Event) => void,
  options: {
    enabled?: boolean;
    eventType?: string | string[];
  } = {}
) {
  const { enabled = true, eventType = ['mousedown', 'touchstart'] } = options;
  const handlerRef = useRef(handler);
  const isTouchEvent = useRef(false);

  // Update handler ref when handler changes
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const events = Array.isArray(eventType) ? eventType : [eventType];
    const eventListener = (event: Event) => {
      // Skip if the target is not an HTMLElement
      if (!(event.target instanceof HTMLElement)) {
        return;
      }

      // Skip if the ref is not set or the click was inside the ref element
      if (!ref.current || ref.current.contains(event.target)) {
        return;
      }

      // Skip if the click was on a portal element
      if (event.target.closest('[data-portal]')) {
        return;
      }

      // Skip if this is a touch event and the mouse event was already handled
      if (isTouchEvent.current && event.type === 'mousedown') {
        return;
      }

      // Set touch event flag for the current event
      if (event.type === 'touchstart') {
        isTouchEvent.current = true;
      } else if (event.type === 'mousedown') {
        isTouchEvent.current = false;
      }

      handlerRef.current(event);
    };

    // Add event listeners
    events.forEach((event) => {
      document.addEventListener(event, eventListener as EventListener, true);
    });

    // Clean up event listeners
    return () => {
      events.forEach((event) => {
        document.removeEventListener(event, eventListener as EventListener, true);
      });
    };
  }, [ref, eventType, enabled]);
}

/**
 * A more specialized version of useClickOutside that only triggers the handler
 * when clicking outside of all provided refs.
 * 
 * @param refs - An array of refs to check against
 * @param handler - The callback function to execute when a click outside is detected
 * @param options - Additional options for the hook
 * 
 * @example
 * const ref1 = useRef<HTMLDivElement>(null);
 * const ref2 = useRef<HTMLDivElement>(null);
 * 
 * useClickOutside([ref1, ref2], () => {
 *   // Handle click outside both ref1 and ref2
 * });
 */
export function useClickOutsideMultiple<T extends HTMLElement = HTMLElement>(
  refs: Array<RefObject<T>>,
  handler: (event: Event) => void,
  options: Omit<Parameters<typeof useClickOutside>[2], 'eventType'> = {}
) {
  const handlerRef = useRef(handler);

  // Update handler ref when handler changes
  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useClickOutside(
    // Create a dummy ref that's always null
    { current: null },
    (event) => {
      // Check if the click was outside all provided refs
      const isOutsideAll = refs.every(
        (ref) => !ref.current || !ref.current.contains(event.target as Node)
      );

      if (isOutsideAll) {
        handlerRef.current(event);
      }
    },
    {
      ...options,
      // Always use the default event types
      eventType: ['mousedown', 'touchstart'],
    }
  );
}
