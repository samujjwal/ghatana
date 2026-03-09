import { DOM_CONSTANTS, WINDOW_PROPERTIES, TIMEOUTS } from '@shared/config/constants';

import { ensureEventsContainer, mirrorToDom } from './dom-bridge';

type EventPayload = Record<string, unknown>;

declare global {
  interface Window {
    __dcmaarEvents?: EventPayload[];
  }
}

function getEventsArray(): EventPayload[] {
  if (!window[WINDOW_PROPERTIES.EVENTS_ARRAY]) {
    window[WINDOW_PROPERTIES.EVENTS_ARRAY] = [];
  }
  if (!window.__dcmaarEvents) {
    window.__dcmaarEvents = window[WINDOW_PROPERTIES.EVENTS_ARRAY] as EventPayload[];
  }
  return window[WINDOW_PROPERTIES.EVENTS_ARRAY] as EventPayload[];
}

export function initEventTracking(): void {
  getEventsArray();
  ensureEventsContainer();
}

function shouldIgnore(target: Element | null): boolean {
  if (!target) {
    return false;
  }
  return Boolean(target.closest('[data-dcmaar-ignore="true"]'));
}

function normalizeEvent(event: Event): EventPayload | null {
  const target = (event.target as Element | null) ?? null;
  if (shouldIgnore(target)) {
    return null;
  }

  const payload: EventPayload = {
    type: event.type,
    timestamp: Date.now(),
  };

  if (target) {
    payload.tagName = target.tagName;
    if (target instanceof HTMLElement) {
      payload.id = target.id || undefined;
      payload.className = target.className || undefined;
      payload.text = (target.textContent || '').slice(0, 200);
      payload.attributes = Array.from(target.attributes).reduce<Record<string, string>>(
        (acc, attr) => {
          if (attr.name !== DOM_CONSTANTS.EVENT_NODE_ATTR) {
            acc[attr.name] = attr.value;
          }
          return acc;
        },
        {}
      );
    }
  }

  return payload;
}

export function trackEvent(event: Event): void {
  try {
    initEventTracking();
    const payload = normalizeEvent(event);
    if (!payload) {
      return;
    }

    const events = getEventsArray();
    events.push(payload);
    mirrorToDom(payload);

    // Keep an upper bound on the events buffer to avoid unbounded growth.
    const overflow = events.length - TIMEOUTS.MAX_EVENTS_IN_CONTAINER;
    if (overflow > 0) {
      events.splice(0, overflow);
    }
  } catch {
    // Swallow errors to keep event tracking from impacting host pages.
  }
}

export function trackPageView(href: string): void {
  try {
    initEventTracking();
    const payload: EventPayload = {
      type: 'pageview',
      href,
      tagName: 'document',
      title: document.title,
      value: document.title,
      referrer: document.referrer,
      timestamp: Date.now(),
    };

    const events = getEventsArray();
    events.push(payload);
    mirrorToDom(payload);
  } catch {
    // Ignore failures
  }
}
