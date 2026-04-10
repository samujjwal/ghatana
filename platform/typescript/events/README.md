# @ghatana/events

Platform-level event abstractions for Ghatana applications.

## Overview

`@ghatana/events` is the canonical source of truth for the platform event system.
It provides typed, versioned, and validated event primitives used across all Ghatana products.

## Installation

```bash
# Already in the workspace — no install needed
```

## Core Concepts

### PlatformEvent\<T\>

The base event contract. Every event in the Ghatana system must structurally match this interface.

```ts
interface PlatformEvent<T = unknown> {
  id: string;          // UUID v4
  type: string;        // 'domain.action' e.g. 'user.logged-in'
  timestamp: number;   // Unix ms
  source: EventSource; // { type: 'client' | 'server' | ..., id: string }
  data: T;             // Domain-specific payload
  correlationId?: string;
  schemaVersion?: string;
}
```

### EventDispatcher

Type-safe event routing with support for wildcard subscriptions and per-subscription filters.

```ts
import { EventDispatcher, type PlatformEvent } from '@ghatana/events';

interface UserLoggedIn extends PlatformEvent<{ userId: string }> {
  type: 'user.logged-in';
}

const dispatcher = new EventDispatcher();

// Subscribe
const token = dispatcher.subscribe<UserLoggedIn>('user.logged-in', async (event) => {
  console.log('User logged in:', event.data.userId);
});

// Subscribe to all events
dispatcher.subscribe('*', (event) => {
  console.log('Any event:', event.type);
});

// Subscribe with a filter
dispatcher.subscribe(
  'order.placed',
  (event) => console.log('Large order', event.data),
  (event) => (event.data as { amount: number }).amount > 100
);

// Dispatch
await dispatcher.dispatch({
  id: crypto.randomUUID(),
  type: 'user.logged-in',
  timestamp: Date.now(),
  source: { type: 'client', id: 'web-app' },
  data: { userId: 'u-123' },
});

// Unsubscribe
dispatcher.unsubscribe(token);
```

### Serialization

```ts
import { serializeEvent, deserializeEvent } from '@ghatana/events';

const json = serializeEvent(event);    // → JSON string with _v: 1 envelope
const restored = deserializeEvent(json); // → PlatformEvent<T>
```

### Validation

```ts
import { validatePlatformEvent, validatePlatformEventWithData, safeParsePlatformEvent } from '@ghatana/events';
import { z } from 'zod';

// Throws EventValidationError on failure
const event = validatePlatformEvent(rawInput);

// Validates both the event shape and the data payload
const typed = validatePlatformEventWithData(rawInput, z.object({ userId: z.string() }));

// Safe variant — returns null on failure
const maybeEvent = safeParsePlatformEvent(rawInput);
```

### Type Guards

```ts
import { isPlatformEvent, isEventOfType } from '@ghatana/events';

if (isPlatformEvent(unknown)) {
  // unknown is now PlatformEvent
}

if (isEventOfType<TabCreatedEvent>(event, 'tab.created')) {
  // event is now TabCreatedEvent
}
```

## Directory

```
src/
  types.ts       — PlatformEvent, EventSource, EventHandler, EventFilter, Zod schemas, type guards
  dispatcher.ts  — EventDispatcher class
  serializer.ts  — serialize/deserialize helpers with _v:1 envelope
  validation.ts  — validatePlatformEvent, validatePlatformEventWithData, safeParse
  index.ts       — public API barrel
  __tests__/     — unit tests
```

## Tests

```bash
pnpm --filter @ghatana/events test
pnpm --filter @ghatana/events test:coverage
```

Coverage target: ≥ 85% lines/functions, ≥ 80% branches.
