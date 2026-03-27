import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useImageOptimization, UseImageOptimizationProps } from '../useImageOptimization';

type HookSnapshot = ReturnType<typeof useImageOptimization>;

class MockImage {
  public onload: (() => void) | null = null;
  public onerror: (() => void) | null = null;
  public src = '';

  constructor(private readonly instances: MockImage[]) {
    instances.push(this);
  }

  triggerLoad() {
    this.onload?.();
  }

  triggerError() {
    this.onerror?.();
  }
}

class MockIntersectionObserver {
  public observe = vi.fn();
  public unobserve = vi.fn();
  public disconnect = vi.fn();

  constructor(
    private readonly callback: IntersectionObserverCallback,
    public readonly options?: IntersectionObserverInit
  ) {}

  trigger(entry: Partial<IntersectionObserverEntry>) {
    this.callback([entry as IntersectionObserverEntry], this as unknown as IntersectionObserver);
  }
}

function ImageOptimizationHarness(
  props: UseImageOptimizationProps & { onSnapshot: (snapshot: HookSnapshot) => void }
) {
  const snapshot = useImageOptimization(props);
  props.onSnapshot(snapshot);

  return <img alt={props.alt} data-testid="optimized-image" ref={snapshot.ref} src={snapshot.currentSrc} />;
}

describe('useImageOptimization', () => {
  const imageInstances: MockImage[] = [];
  const observers: MockIntersectionObserver[] = [];

  beforeEach(() => {
    imageInstances.length = 0;
    observers.length = 0;

    Object.defineProperty(globalThis, 'Image', {
      configurable: true,
      value: class extends MockImage {
        constructor() {
          super(imageInstances);
        }
      },
    });

    Object.defineProperty(globalThis, 'IntersectionObserver', {
      configurable: true,
      value: class extends MockIntersectionObserver {
        constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
          super(callback, options);
          observers.push(this);
        }
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads the full image immediately when lazy loading is disabled', () => {
    const onLoad = vi.fn();
    let snapshot: HookSnapshot | undefined;

    render(
      <ImageOptimizationHarness
        alt="Example"
        lazy={false}
        onLoad={onLoad}
        onSnapshot={(nextSnapshot) => {
          snapshot = nextSnapshot;
        }}
        placeholder="/placeholder.png"
        src="/image.png"
      />
    );

    expect(snapshot?.currentSrc).toBe('/placeholder.png');
    expect(snapshot?.isLoaded).toBe(false);
    expect(imageInstances).toHaveLength(1);
    expect(imageInstances[0]?.src).toBe('/image.png');

    act(() => {
      imageInstances[0]?.triggerLoad();
    });

    expect(snapshot?.currentSrc).toBe('/image.png');
    expect(snapshot?.isLoaded).toBe(true);
    expect(snapshot?.hasError).toBe(false);
    expect(onLoad).toHaveBeenCalledTimes(1);
  });

  it('sets an error state when the image fails to load', () => {
    const onError = vi.fn();
    let snapshot: HookSnapshot | undefined;

    render(
      <ImageOptimizationHarness
        alt="Broken"
        lazy={false}
        onError={onError}
        onSnapshot={(nextSnapshot) => {
          snapshot = nextSnapshot;
        }}
        placeholder="/placeholder.png"
        src="/broken.png"
      />
    );

    act(() => {
      imageInstances[0]?.triggerError();
    });

    expect(snapshot?.hasError).toBe(true);
    expect(snapshot?.isLoaded).toBe(false);
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('waits for intersection before starting a lazy image load', () => {
    let snapshot: HookSnapshot | undefined;

    render(
      <ImageOptimizationHarness
        alt="Lazy"
        lazy
        onSnapshot={(nextSnapshot) => {
          snapshot = nextSnapshot;
        }}
        placeholder="/placeholder.png"
        src="/lazy.png"
      />
    );

    const imageElement = screen.getByTestId('optimized-image');
    expect(observers).toHaveLength(1);
    expect(observers[0]?.observe).toHaveBeenCalledWith(imageElement);
    expect(imageInstances[0]?.src).toBe('');

    act(() => {
      observers[0]?.trigger({ isIntersecting: true, target: imageElement });
    });

    expect(imageInstances[0]?.src).toBe('/lazy.png');
    expect(observers[0]?.unobserve).toHaveBeenCalledWith(imageElement);

    act(() => {
      imageInstances[0]?.triggerLoad();
    });

    expect(snapshot?.currentSrc).toBe('/lazy.png');
    expect(snapshot?.isLoaded).toBe(true);
  });
});