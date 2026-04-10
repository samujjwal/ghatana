# @ghatana/browser-events

Platform-level browser extension event abstractions for Ghatana applications.

## Overview

`@ghatana/browser-events` provides typed, reusable browser event primitives for browser extensions.
All events extend `PlatformEvent<T>` from `@ghatana/events`.

## Event Types

| Type | Description |
|------|-------------|
| `TabEvent` | Tab lifecycle events (created, updated, removed, activated, moved, detached) |
| `NavigationEvent` | Navigation lifecycle (before, committed, completed, error, replaced) |
| `NetworkEvent` | Fetch/XHR request, response, and error events |
| `WebRequestEvent` | webRequest API lifecycle events |
| `HistoryEvent` | Browser history events (visited, title-changed, deleted) |

All event types use `source: { type: 'browser', id: string }`.

## Usage

### Implementing a capture

```ts
import { AbstractBrowserEventCapture } from '@ghatana/browser-events/capture';
import type { BrowserEventFilter } from '@ghatana/browser-events';
import browser from 'webextension-polyfill';

class MyBrowserCapture extends AbstractBrowserEventCapture {
  captureTabEvents(filter?: BrowserEventFilter) {
    browser.tabs.onCreated.addListener(async (tab) => {
      const event = {
        id: this.newEventId(),
        type: 'tab.created' as const,
        timestamp: Date.now(),
        source: this.makeSource(),
        data: { action: 'created' as const, tabId: tab.id ?? -1, url: tab.url },
      };
      if (!filter || filter(event)) {
        await this.emit(event);
      }
    });
    this.activeCaptures.add('tabs');
  }

  // ...implement other abstract methods

  stop() {
    // remove listeners
    this.activeCaptures.clear();
  }
}

const capture = new MyBrowserCapture('my-extension-id');
capture.onEvent((event) => {
  console.log(event.type, event.data);
});
capture.captureAll();
```

### Filtering by domain

```ts
import { createDomainFilter, createUrlPatternFilter } from '@ghatana/browser-events';

const safeOnly = createDomainFilter('trusted.example.com');
capture.captureTabEvents(safeOnly);

const apiFilter = createUrlPatternFilter(/\/api\//);
capture.captureNetworkEvents(apiFilter);
```

### Type guards

```ts
import { isBrowserEvent, isBrowserEventOfType } from '@ghatana/browser-events';
import type { TabEvent } from '@ghatana/browser-events';

if (isBrowserEvent(unknown)) {
  // safely use as BrowserEvent
}

if (isBrowserEventOfType<TabEvent>(event, 'tab.created')) {
  console.log(event.data.tabId);
}
```

## Tests

```bash
pnpm --filter @ghatana/browser-events test
pnpm --filter @ghatana/browser-events test:coverage
```
