/* eslint-env jest */
import { diffJson } from './jsonDiff';

describe('diffJson', () => {
  test('detects added keys', () => {
    const a = { a: 1 };
    const b = { a: 1, b: 2 };
    const changes = diffJson(a, b);
    expect(changes).toEqual([{ path: 'b', type: 'added', newValue: 2 }]);
  });

  test('detects removed keys', () => {
    const a = { a: 1, b: 2 };
    const b = { a: 1 };
    const changes = diffJson(a, b);
    expect(changes).toEqual([{ path: 'b', type: 'removed', oldValue: 2 }]);
  });

  test('detects changed values', () => {
    const a = { a: 1 };
    const b = { a: 2 };
    const changes = diffJson(a, b);
    expect(changes).toEqual([{ path: 'a', type: 'changed', oldValue: 1, newValue: 2 }]);
  });
});
