import { DOM_CONSTANTS, TIMEOUTS, WINDOW_PROPERTIES } from '@shared/config/constants';

type UnknownRecord = Record<string, unknown>;

function isWindow(value: unknown): value is Window &
  Partial<Record<typeof WINDOW_PROPERTIES[keyof typeof WINDOW_PROPERTIES], unknown[]>> {
  return typeof value === 'object' && value !== null && 'document' in (value as Window);
}

/**
 * Ensures the hidden container used to mirror events into the DOM exists.
 * Creates the container lazily so content scripts can call this without
 * depending on markup in the host page.
 */
export function ensureEventsContainer(): HTMLElement {
  const existing = document.getElementById(DOM_CONSTANTS.EVENTS_CONTAINER_ID);
  if (existing) {
    return existing;
  }

  const container = document.createElement('div');
  container.id = DOM_CONSTANTS.EVENTS_CONTAINER_ID;
  container.style.display = 'none';
  container.setAttribute(DOM_CONSTANTS.EVENTS_CONTAINER_ATTR, DOM_CONSTANTS.EVENTS_CONTAINER_VALUE);
  container.setAttribute('role', 'presentation');

  document.body.appendChild(container);
  return container;
}

/**
 * Mirrors arbitrary data into the DOM container so page scripts that do not
 * have access to the extension runtime can observe it.
 */
export function mirrorToDom(event: UnknownRecord | undefined): void {
  if (!event || typeof event !== 'object') {
    return;
  }

  try {
    const container = ensureEventsContainer();
    const node = document.createElement('pre');
    node.setAttribute(DOM_CONSTANTS.EVENT_NODE_ATTR, DOM_CONSTANTS.EVENT_NODE_VALUE);
    node.textContent = JSON.stringify(event);
    container.appendChild(node);

    // Keep container bounded to avoid runaway DOM growth.
    const maxNodes = TIMEOUTS.MAX_EVENTS_IN_CONTAINER;
    const nodes = container.querySelectorAll<HTMLElement>(`[${DOM_CONSTANTS.EVENT_NODE_ATTR}]`);
    const overflow = nodes.length - maxNodes;
    for (let i = 0; i < overflow; i++) {
      nodes[i].remove();
    }
  } catch {
    // Silently swallow—mirroring is diagnostic only.
  }
}

/**
 * Emits events into the page context using postMessage so in-page scripts can
 * listen without tight coupling to extension internals.
 */
export function mirrorToPage(event: UnknownRecord | undefined): void {
  if (!event || typeof event !== 'object') {
    return;
  }

  try {
    window.postMessage(
      {
        [DOM_CONSTANTS.EVENTS_CONTAINER_ATTR]: DOM_CONSTANTS.EVENTS_CONTAINER_VALUE,
        [DOM_CONSTANTS.EVENT_NODE_ATTR]: DOM_CONSTANTS.EVENT_NODE_VALUE,
        __dcmaar: true,
        event,
      },
      '*'
    );
  } catch {
    // PostMessage failures are non-fatal; suppress to keep content scripts resilient.
  }
}

export function setExtensionAttribute(): boolean {
  try {
    document.documentElement.setAttribute(
      DOM_CONSTANTS.EXTENSION_LOADED_ATTR,
      DOM_CONSTANTS.EXTENSION_LOADED_VALUE
    );
    if (isWindow(window)) {
      (window as Window & Record<string, unknown>)[WINDOW_PROPERTIES.CONTENT_SCRIPT_LOADED] = true;
    }
    return true;
  } catch {
    return false;
  }
}

export function ensureExtensionAttribute(): void {
  const apply = () => {
    setExtensionAttribute();
  };

  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    apply();
  } else {
    document.addEventListener('DOMContentLoaded', apply, { once: true });
  }
}

/**
 * Records an event both in the DOM container and via postMessage so consumers
 * can subscribe using their preferred mechanism.
 */
export function mirrorEvent(event: UnknownRecord): void {
  mirrorToDom(event);
  mirrorToPage(event);
}
