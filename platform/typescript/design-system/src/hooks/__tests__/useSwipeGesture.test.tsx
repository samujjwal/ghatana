import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  SwipeState,
  useHorizontalSwipe,
  useSwipeGesture,
  useVerticalSwipe,
} from '../useSwipeGesture';

interface SwipeHarnessProps {
  onStateChange: (state: SwipeState) => void;
  onSwipeLeft?: () => void;
  onSwipeRight?: () => void;
  onSwipeUp?: () => void;
  onSwipeDown?: () => void;
  onSwipe?: (direction: 'left' | 'right' | 'up' | 'down') => void;
  threshold?: number;
  maxTime?: number;
  enabled?: boolean;
  preventDefault?: boolean;
  onSwipeStart?: () => void;
  onSwipeMove?: (deltaX: number, deltaY: number) => void;
  onSwipeEnd?: () => void;
}

function SwipeHarness({ onStateChange, ...props }: SwipeHarnessProps) {
  const { ref, state } = useSwipeGesture(
    {
      onSwipeLeft: props.onSwipeLeft,
      onSwipeRight: props.onSwipeRight,
      onSwipeUp: props.onSwipeUp,
      onSwipeDown: props.onSwipeDown,
      onSwipe: props.onSwipe,
    },
    {
      threshold: props.threshold,
      maxTime: props.maxTime,
      enabled: props.enabled,
      preventDefault: props.preventDefault,
      onSwipeStart: props.onSwipeStart,
      onSwipeMove: props.onSwipeMove,
      onSwipeEnd: props.onSwipeEnd,
    }
  );

  onStateChange(state);

  return <div data-testid="swipe-target" ref={ref} />;
}

function HorizontalSwipeHarness(props: { onSwipeLeft?: () => void; onSwipeRight?: () => void }) {
  const { ref } = useHorizontalSwipe(props.onSwipeLeft, props.onSwipeRight, { threshold: 40 });

  return <div data-testid="horizontal-target" ref={ref} />;
}

function VerticalSwipeHarness(props: { onSwipeUp?: () => void; onSwipeDown?: () => void }) {
  const { ref } = useVerticalSwipe(props.onSwipeUp, props.onSwipeDown, { threshold: 40 });

  return <div data-testid="vertical-target" ref={ref} />;
}

function createTouchEvent(type: string, coords: { clientX: number; clientY: number }) {
  const event = new Event(type, { bubbles: true, cancelable: true });

  Object.defineProperty(event, 'touches', {
    configurable: true,
    value: type === 'touchend' ? [] : [coords],
  });

  return event;
}

describe('useSwipeGesture', () => {
  let now = 0;

  beforeEach(() => {
    vi.spyOn(Date, 'now').mockImplementation(() => now);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('tracks swipe state and fires left-swipe callbacks', () => {
    const onSwipeLeft = vi.fn();
    const onSwipe = vi.fn();
    const onSwipeStart = vi.fn();
    const onSwipeMove = vi.fn();
    const onSwipeEnd = vi.fn();
    let latestState: SwipeState = {
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    };

    render(
      <SwipeHarness
        maxTime={300}
        onStateChange={(state) => {
          latestState = state;
        }}
        onSwipe={onSwipe}
        onSwipeEnd={onSwipeEnd}
        onSwipeLeft={onSwipeLeft}
        onSwipeMove={onSwipeMove}
        onSwipeStart={onSwipeStart}
        threshold={40}
      />
    );

    const target = screen.getByTestId('swipe-target');

    act(() => {
      target.dispatchEvent(createTouchEvent('touchstart', { clientX: 100, clientY: 20 }));
    });

    expect(onSwipeStart).toHaveBeenCalledTimes(1);
    expect(latestState.isSwiping).toBe(true);

    act(() => {
      target.dispatchEvent(createTouchEvent('touchmove', { clientX: 30, clientY: 30 }));
    });

    expect(latestState.deltaX).toBe(-70);
    expect(latestState.deltaY).toBe(10);
    expect(latestState.direction).toBe('left');
    expect(onSwipeMove).toHaveBeenCalledWith(-70, 10);

    now = 150;

    act(() => {
      target.dispatchEvent(createTouchEvent('touchend', { clientX: 30, clientY: 30 }));
    });

    expect(onSwipeLeft).toHaveBeenCalledTimes(1);
    expect(onSwipe).toHaveBeenCalledWith('left');
    expect(onSwipeEnd).toHaveBeenCalledTimes(1);
    expect(latestState).toEqual({
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    });
  });

  it('ignores swipes when disabled', () => {
    const onSwipeRight = vi.fn();
    let latestState: SwipeState = {
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    };

    render(
      <SwipeHarness
        enabled={false}
        onStateChange={(state) => {
          latestState = state;
        }}
        onSwipeRight={onSwipeRight}
      />
    );

    const target = screen.getByTestId('swipe-target');

    act(() => {
      target.dispatchEvent(createTouchEvent('touchstart', { clientX: 10, clientY: 10 }));
      target.dispatchEvent(createTouchEvent('touchmove', { clientX: 120, clientY: 12 }));
      target.dispatchEvent(createTouchEvent('touchend', { clientX: 120, clientY: 12 }));
    });

    expect(onSwipeRight).not.toHaveBeenCalled();
    expect(latestState).toEqual({
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    });
  });

  it('supports horizontal and vertical swipe helpers', () => {
    const onSwipeRight = vi.fn();
    const onSwipeUp = vi.fn();

    render(<HorizontalSwipeHarness onSwipeRight={onSwipeRight} />);
    const horizontalTarget = screen.getByTestId('horizontal-target');

    act(() => {
      horizontalTarget.dispatchEvent(createTouchEvent('touchstart', { clientX: 10, clientY: 20 }));
      horizontalTarget.dispatchEvent(createTouchEvent('touchmove', { clientX: 80, clientY: 24 }));
    });

    now = 100;

    act(() => {
      horizontalTarget.dispatchEvent(createTouchEvent('touchend', { clientX: 80, clientY: 24 }));
    });

    expect(onSwipeRight).toHaveBeenCalledTimes(1);

    render(<VerticalSwipeHarness onSwipeUp={onSwipeUp} />);
    const verticalTarget = screen.getByTestId('vertical-target');

    act(() => {
      verticalTarget.dispatchEvent(createTouchEvent('touchstart', { clientX: 20, clientY: 100 }));
      verticalTarget.dispatchEvent(createTouchEvent('touchmove', { clientX: 22, clientY: 30 }));
    });

    now = 180;

    act(() => {
      verticalTarget.dispatchEvent(createTouchEvent('touchend', { clientX: 22, clientY: 30 }));
    });

    expect(onSwipeUp).toHaveBeenCalledTimes(1);
  });

  it('can prevent default touch behavior during swipe interactions', () => {
    let latestState: SwipeState = {
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    };

    render(
      <SwipeHarness
        onStateChange={(state) => {
          latestState = state;
        }}
        preventDefault
      />
    );

    const target = screen.getByTestId('swipe-target');
    const startEvent = createTouchEvent('touchstart', { clientX: 50, clientY: 50 });
    const preventDefaultSpy = vi.spyOn(startEvent, 'preventDefault');

    act(() => {
      target.dispatchEvent(startEvent);
    });

    expect(preventDefaultSpy).toHaveBeenCalledTimes(1);
    expect(latestState.isSwiping).toBe(true);
  });
});