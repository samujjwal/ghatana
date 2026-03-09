import { createMockYArray, createMockYMap, withMockYDoc } from '@ghatana/yappc-test-helpers';
import { describe, it, expect, vi } from 'vitest';

describe('yjsMocks helpers', () => {
  it('createMockYArray provides spying methods', () => {
    const arr = createMockYArray();
    expect(typeof arr.insert).toBe('function');
    expect(typeof arr.delete).toBe('function');
    // call methods and assert spies recorded calls
    arr.insert(0, [{ id: 'n1' }]);
    expect((arr.insert as unknown).mock.calls.length).toBe(1);
    arr.delete(0);
    expect((arr.delete as unknown).mock.calls.length).toBe(1);
  });

  it('createMockYMap provides spying methods', () => {
    const map = createMockYMap();
    expect(typeof map.set).toBe('function');
    map.set('k', 123);
    expect((map.set as unknown).mock.calls.length).toBe(1);
  });

  it('withMockYDoc patches result.current.ydoc with transact/getArray/getMap', () => {
    const result: any = { current: { ydoc: {} } };

    const { ydoc, nodesArray, edgesArray, map } = withMockYDoc(result);

    expect(typeof ydoc.transact).toBe('function');
    let ran = false;
    ydoc.transact(() => { ran = true; });
    expect(ran).toBe(true);
    expect((ydoc.transact as unknown).mock).toBeDefined();

    const n = ydoc.getArray('nodes');
    const e = ydoc.getArray('edges');
    expect(n).toBe(nodesArray);
    expect(e).toBe(edgesArray);

    const m = ydoc.getMap('map');
    expect(m).toBe(map);
  });
});
