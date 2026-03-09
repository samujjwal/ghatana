import { vi } from 'vitest';

/** Minimal mock Y.Doc shape used by test helpers */
interface MockYDoc {
  clientId?: string | number | null;
  _currentTxnId?: string | null;
  _opCounter: number;
  __inTransaction?: boolean;
  _history?: unknown[];
}

export const createMockYArray = () => {
  const items: unknown[] = [];
  const observers: Array<(...args: unknown[]) => void> = [];
  // queued events while a ydoc transaction is active
  const queuedEvents: unknown[] = [];
  let attachedDoc: MockYDoc | undefined = undefined;
  let attachedCollection: string | null = null;

  const toArray = vi.fn(() => [...items]);

  const observe = vi.fn((cb: (...args: unknown[]) => void) => {
    observers.push(cb);
  });

  const unobserve = vi.fn((cb: (...args: unknown[]) => void) => {
    const i = observers.indexOf(cb);
    if (i >= 0) observers.splice(i, 1);
  });

  const _notify = (event: unknown) => {
    // deliver each event to observers
    observers.forEach(cb => {
      try { cb(event); } catch {}
    });
  };

  const _flushQueued = () => {
    if (queuedEvents.length === 0) return;
    const events = queuedEvents.splice(0, queuedEvents.length);
    // deliver events one-by-one to observers in order
    events.forEach(e => _notify(e));
  };

  // allow attaching with a collection name: _attachDoc(ydoc, 'nodes')
  const _attachDoc = (doc: unknown, collectionName?: string) => {
    attachedDoc = doc as MockYDoc;
    attachedCollection = collectionName ?? null;
  };

  const _delete = vi.fn((index: number, length = 1) => {
    const removed = items.slice(index, index + length);
    items.splice(index, length);
    const ev = {
      type: 'delete',
      index,
      length,
      delta: {
        index,
        added: 0,
        removed: [...removed],
      },
      items: [...items],
      metadata: {
        clientId: attachedDoc?.clientId ?? null,
        timestamp: Date.now(),
        txnId: attachedDoc?._currentTxnId ?? null,
        opSeq: attachedDoc ? (++attachedDoc._opCounter) : null,
  collection: attachedCollection ?? null,
      }
    };
    if (attachedDoc && attachedDoc.__inTransaction) queuedEvents.push(ev);
    else _notify(ev);
    // record into ydoc history when attached
    try { attachedDoc?._history?.push(ev); } catch {}
  });

  const _insert = vi.fn((index: number, values: unknown[]) => {
    const vals = Array.isArray(values) ? values : [values];
    items.splice(index, 0, ...vals);
    const ev = {
      type: 'insert',
      index,
      values: vals,
      delta: {
        index,
        added: vals.length,
        removed: [],
      },
      items: [...items],
      metadata: {
        clientId: attachedDoc?.clientId ?? null,
        timestamp: Date.now(),
        txnId: attachedDoc?._currentTxnId ?? null,
        opSeq: attachedDoc ? (++attachedDoc._opCounter) : null,
  collection: attachedCollection ?? null,
      }
    };
    if (attachedDoc && attachedDoc.__inTransaction) queuedEvents.push(ev);
    else _notify(ev);
    try { attachedDoc?._history?.push(ev); } catch {}
  });

  return {
    toArray,
    observe,
    unobserve,
    delete: _delete,
    insert: _insert,
    // internal helpers used by withMockYDoc
    _attachDoc,
    _flushQueued,
    // debug helpers for tests
    _getQueuedEvents: () => [...queuedEvents],
    _clearQueued: () => { queuedEvents.splice(0, queuedEvents.length); },
  };
};

export const createMockYMap = () => {
  const store: Record<string, unknown> = {};
  const observers: Array<(...args: unknown[]) => void> = [];
  const queuedEvents: unknown[] = [];
  let attachedDoc: MockYDoc | undefined = undefined;
  let attachedCollection: string | null = null;

  const toJSON = () => ({ ...store });

  const observe = vi.fn((cb: (...args: unknown[]) => void) => {
    observers.push(cb);
  });

  const unobserve = vi.fn((cb: (...args: unknown[]) => void) => {
    const i = observers.indexOf(cb);
    if (i >= 0) observers.splice(i, 1);
  });

  const _notify = (ev: unknown) => {
    observers.forEach(cb => { try { cb(ev); } catch {} });
  };

  const _flushQueued = () => {
    if (queuedEvents.length === 0) return;
    const events = queuedEvents.splice(0, queuedEvents.length);
    events.forEach(e => _notify(e));
  };

  // allow attaching with an optional collection name to mirror arrays
  const _attachDoc = (doc: unknown, collectionName?: string) => { attachedDoc = doc as MockYDoc; attachedCollection = collectionName ?? null; };

  const set = vi.fn((key: string, value: unknown) => {
    store[key] = value;
    const ev = {
      type: 'set',
      key,
      value,
      items: toJSON(),
      delta: { key, added: true },
      metadata: {
        clientId: attachedDoc?.clientId ?? null,
        timestamp: Date.now(),
        txnId: attachedDoc?._currentTxnId ?? null,
        opSeq: attachedDoc ? (++attachedDoc._opCounter) : null,
  collection: attachedCollection ?? null,
      }
    };
    if (attachedDoc && attachedDoc.__inTransaction) queuedEvents.push(ev);
    else _notify(ev);
    try { attachedDoc?._history?.push(ev); } catch {}
  });

  const get = vi.fn((key: string) => store[key]);

  const forEach = vi.fn((fn: (value: unknown, key: string) => void) => {
    Object.keys(store).forEach(k => fn(store[k], k));
  });

  return { set, get, forEach, observe, unobserve, toJSON, _attachDoc, _flushQueued, _getQueuedEvents: () => [...queuedEvents], _clearQueued: () => { queuedEvents.splice(0, queuedEvents.length); } };
};

// Note: createMockYMap above is the stateful implementation. No duplicate exports.

/**
 *
 */
export function withMockYDoc(result: unknown, opts?: {
  nodesArray?: unknown,
  edgesArray?: unknown,
  map?: unknown,
  clientId?: string,
}) {
  const nodesArray = opts?.nodesArray ?? createMockYArray();
  const edgesArray = opts?.edgesArray ?? createMockYArray();
  const map = opts?.map ?? createMockYMap();

  const ydoc = (result.current as unknown).ydoc as unknown;
  // Provide a noop transact spy so tests can assert it was called
  ydoc.__inTransaction = false;
  // a simple txn counter to produce txn ids
  ydoc._txnCounter = 0;
  // operation counter for opSeq metadata
  ydoc._opCounter = 0;
  // record of flushed events for tests
  ydoc._history = [];
  ydoc.clientId = opts?.clientId ?? `client-${Math.floor(Math.random()*10000)}`;

  ydoc.transact = vi.fn((fn: unknown) => {
    // mark transaction active for mocks that check it
    ydoc.__inTransaction = true;
    // create a txn id for this transaction
    ydoc._txnCounter += 1;
    ydoc._currentTxnId = `${ydoc.clientId}:${ydoc._txnCounter}`;
    try {
      const res = fn();
      return res;
    } finally {
      ydoc.__inTransaction = false;
      // flush queued events on arrays attached to the doc
      try { nodesArray._flushQueued?.(); } catch {}
      try { edgesArray._flushQueued?.(); } catch {}
      try { map._flushQueued?.(); } catch {}
      // clear current txn id after flushing
      delete ydoc._currentTxnId;
    }
  });

  // attach doc so arrays/maps can check for transaction state and include metadata
  try { nodesArray._attachDoc?.(ydoc); } catch {}
  try { edgesArray._attachDoc?.(ydoc); } catch {}
  try { map._attachDoc?.(ydoc); } catch {}

  // getArray called for nodes then edges in many tests
  ydoc.getArray = vi.fn()
    .mockReturnValueOnce(nodesArray)
    .mockReturnValueOnce(edgesArray);

  ydoc.getMap = vi.fn(() => map);

  return { ydoc, nodesArray, edgesArray, map };
}
