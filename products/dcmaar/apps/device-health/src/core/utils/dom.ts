const EVENTS_CONTAINER_ID = 'dcmaar-events-container';

export function ensureEventsContainer(): HTMLElement {
  let container = document.getElementById(EVENTS_CONTAINER_ID);

  if (!container) {
    container = document.createElement('div');
    container.id = EVENTS_CONTAINER_ID;
    container.style.display = 'none';
    document.body.appendChild(container);
  }

  return container;
}

export function recordEvent(event: Record<string, unknown>): void {
  const container = ensureEventsContainer();
  const eventElement = document.createElement('div');
  eventElement.className = 'dcmaar-event';
  eventElement.dataset.timestamp = Date.now().toString();
  eventElement.textContent = JSON.stringify(event);
  container.appendChild(eventElement);
}
