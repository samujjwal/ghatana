# @ghatana/state

Platform-level state management for Ghatana applications.

Built on [Jotai](https://jotai.org/) with type-safe atom creation, persistence, finite state machines, and React hooks.

## Installation

This is a private workspace package. Add it as a dependency in your `package.json`:

```json
{
  "dependencies": {
    "@ghatana/state": "workspace:*"
  }
}
```

## Features

- **Type-safe atoms** — `createAtom`, `createPersistentAtom`, `createDerivedAtom`, `createAsyncAtom`
- **AsyncState** — discriminated union for async operation lifecycles (`idle` → `loading` → `success` / `error`)
- **State machines** — minimal, immutable FSM with guard support (`createStateMachine`)
- **Persistence** — versioned read/write to localStorage / sessionStorage / memory with migration support
- **React hooks** — `useStateAtom`, `useStateValue`, `useStateSetter`, `useAsyncStateAtom`, `useBooleanAtom`
- **Atom registry** — every atom is registered with metadata for diagnostics

## Usage

### Basic atoms

```ts
import { createAtom, createPersistentAtom, createDerivedAtom } from '@ghatana/state';

// Simple read/write atom
const counterAtom = createAtom('counter', 0, 'App counter');

// Persistent atom (backed by localStorage)
const themeAtom = createPersistentAtom('theme', 'light', {
  storage: 'localStorage',
});

// Derived / computed atom
const doubleAtom = createDerivedAtom('double', (get) => get(counterAtom) * 2);
```

### AsyncState

```ts
import { createAsyncAtom, AsyncState } from '@ghatana/state';
import { createStore } from 'jotai';

const usersAtom = createAsyncAtom<User[]>('users');
const store = createStore();

store.set(usersAtom, AsyncState.loading());

try {
  const users = await fetchUsers();
  store.set(usersAtom, AsyncState.success(users));
} catch (err) {
  store.set(usersAtom, AsyncState.error(err as Error));
}
```

### React hooks

```tsx
import { useAsyncStateAtom, useBooleanAtom } from '@ghatana/state';

function UserList() {
  const [state, { setLoading, setSuccess, setError, reset }] = useAsyncStateAtom(usersAtom);
  const [isOpen, toggle] = useBooleanAtom(modalAtom);

  if (state.status === 'loading') return <Spinner />;
  if (state.status === 'error') return <Error message={state.error.message} />;
  if (state.status === 'success') return <ul>{state.data.map(u => <li key={u.id}>{u.name}</li>)}</ul>;
  return null;
}
```

### State machines

```ts
import { createStateMachine } from '@ghatana/state';

type OrderState = 'pending' | 'approved' | 'shipped' | 'cancelled';
type OrderEvent = 'approve' | 'ship' | 'cancel';

const machine = createStateMachine<OrderState, OrderEvent>('pending', [
  { from: 'pending',  to: 'approved', on: 'approve' },
  { from: 'approved', to: 'shipped',  on: 'ship' },
  { from: 'pending',  to: 'cancelled', on: 'cancel' },
  { from: 'approved', to: 'cancelled', on: 'cancel' },
]);

const next = machine.transition('approve'); // → 'approved'
```

### Persistence helpers

```ts
import { writeToStorage, readFromStorage } from '@ghatana/state';

writeToStorage('app:prefs', { fontSize: 16 }, { storage: 'localStorage', version: 1 });
const prefs = readFromStorage<{ fontSize: number }>('app:prefs', {
  storage: 'localStorage',
  version: 2,
  migrate: (data, fromVersion) => ({ ...(data as object), fontSize: 14 }),
});
```

## API Reference

### Atoms

| Function | Description |
|---|---|
| `createAtom(key, init, desc?)` | Simple writable atom |
| `createPersistentAtom(key, init, opts, desc?)` | Storage-backed atom |
| `createDerivedAtom(key, deriveFn)` | Read-only computed atom |
| `createAsyncAtom(key, desc?)` | `AsyncState<T>` atom |
| `createWritableAtom(key, read, write)` | Custom read/write atom |
| `getRegisteredAtoms()` | Returns all registered atom metadata |

### React hooks

| Hook | Description |
|---|---|
| `useStateAtom(atom)` | `[value, setter]` for writable atom |
| `useStateValue(atom)` | Read-only value |
| `useStateSetter(atom)` | Setter only (no re-render on read) |
| `useAsyncStateAtom(atom)` | `[state, { setLoading, setSuccess, setError, reset }]` |
| `useBooleanAtom(atom)` | `[value, toggle, setFalse, setTrue]` |

### State machine

| Function | Description |
|---|---|
| `createStateMachine(initial, transitions, ctx?)` | Creates an immutable FSM |
| `machine.canTransition(event)` | Returns true if transition is allowed |
| `machine.transition(event)` | Returns new machine in next state |
| `machine.validEvents()` | Lists valid events from current state |

### Persistence

| Function | Description |
|---|---|
| `writeToStorage(key, value, opts)` | Writes versioned envelope to storage |
| `readFromStorage(key, opts)` | Reads and optionally migrates stored value |
| `removeFromStorage(key, storage)` | Removes a key |
| `clearMemoryStore()` | Clears in-memory backend (tests) |
