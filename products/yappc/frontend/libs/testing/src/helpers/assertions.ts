// Small assertion helpers for yjs mock events
export const assertOpSequence = (events: unknown[], start = 1) => {
  if (!Array.isArray(events)) throw new Error('events must be an array');
  const seq = events
    .map(e => {
      const v = e?.metadata?.opSeq;
      if (v == null) throw new Error(`missing opSeq on event ${JSON.stringify(e)}`);
      return v;
    })
    .sort((a, b) => a - b);

  let expected = start;
  for (const v of seq) {
    if (v !== expected) throw new Error(`expected opSeq ${expected} but got ${v}`);
    expected += 1;
  }
  return true;
};

export const findEventsByCollection = (events: unknown[], collectionName: string) => {
  return (events || []).filter(e => (e?.metadata?.collection ?? null) === collectionName);
};

export const assertDeltaContains = (ev: unknown, expected: Record<string, unknown>) => {
  if (!ev?.delta) throw new Error('event has no delta');
  for (const k of Object.keys(expected)) {
    if (ev.delta[k] !== expected[k]) throw new Error(`delta.${k} expected ${expected[k]} but found ${ev.delta[k]}`);
  }
  return true;
};

export const assertEventsForCollection = (events: unknown[], collectionName: string, expectedCount?: number) => {
  const found = findEventsByCollection(events, collectionName);
  if (expectedCount != null && found.length !== expectedCount) {
    throw new Error(`expected ${expectedCount} events for collection "${collectionName}" but found ${found.length}`);
  }
  return found;
};

export const assertNoEventsForCollection = (events: unknown[], collectionName: string) => {
  const found = findEventsByCollection(events, collectionName);
  if (found.length > 0) throw new Error(`expected no events for collection "${collectionName}" but found ${found.length}`);
  return true;
};

export const assertEventsContainDelta = (events: unknown[], expectedDelta: Record<string, unknown>) => {
  if (!Array.isArray(events)) throw new Error('events must be an array');
  const matches = events.some(ev => {
    if (!ev?.delta) return false;
    for (const k of Object.keys(expectedDelta)) {
      if (ev.delta[k] !== expectedDelta[k]) return false;
    }
    return true;
  });
  if (!matches) throw new Error(`no event contains expected delta: ${JSON.stringify(expectedDelta)}`);
  return true;
};

export const assertHistoryIncludes = (history: unknown[], expected: Record<string, unknown>) => {
  if (!Array.isArray(history)) throw new Error('history must be an array');
  const matches = history.some(ev => {
    for (const k of Object.keys(expected)) {
      const want = expected[k];
      if (ev[k] === want) continue;
      if (ev?.metadata?.[k] === want) continue;
      if (ev?.delta?.[k] === want) continue;
      return false;
    }
    return true;
  });
  if (!matches) throw new Error(`history does not include expected event: ${JSON.stringify(expected)}`);
  return true;
};

export default {
  assertOpSequence,
  findEventsByCollection,
  assertDeltaContains,
  assertEventsForCollection,
  assertNoEventsForCollection,
  assertEventsContainDelta,
  assertHistoryIncludes,
};

