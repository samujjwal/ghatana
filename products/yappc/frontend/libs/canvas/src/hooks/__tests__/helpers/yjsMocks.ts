import { vi } from 'vitest';

/**
 * yjsMocks.ts
 *
 * Shared, minimal Yjs test helpers used across canvas tests.
 * These are intentionally small and only implement the methods our
 * hooks/tests call (e.g. transact, getArray, getMap, observe, insert, delete).
 *
 * Usage:
 *   // inside a test after rendering the hook:
 *   const { ydoc, nodesArray, edgesArray, map } = withMockYDoc(result, {
 *     nodesArray: createMockYArray(),
 *     edgesArray: createMockYArray(),
 *     map: createMockYMap(),
 *   });
 *
 *   // assert that a transactional update was made:
 *   expect(ydoc.transact).toHaveBeenCalled();
 *   // inspect node mutations:
 *   expect(nodesArray.insert).toHaveBeenCalled();
 *
 * Notes:
 * - Keep these helpers minimal. If a test needs additional Yjs behavior,
 *   expand the specific mock instance in that test rather than changing
 *   the shared helpers broadly.
 * - Prefer asserting against the live objects returned by hooks (e.g.
 *   result.current.ydoc) rather than trying to spy the module-level
 *   constructor, since test runner mock hoisting can make constructor
 *   identity brittle.
 */

export const createMockYArray = () => ({
  toArray: vi.fn(() => []),
  observe: vi.fn(),
  unobserve: vi.fn(),
  delete: vi.fn(),
  insert: vi.fn(),
});

export const createMockYMap = () => ({
  set: vi.fn(),
  forEach: vi.fn(),
  observe: vi.fn(),
  unobserve: vi.fn(),
});

/**
 *
 */
export function withMockYDoc(result: unknown, opts?: {
  nodesArray?: unknown,
  edgesArray?: unknown,
  map?: unknown
}) {
  const nodesArray = opts?.nodesArray ?? createMockYArray();
  const edgesArray = opts?.edgesArray ?? createMockYArray();
  const map = opts?.map ?? createMockYMap();

  const ydoc = (result.current as unknown).ydoc as unknown;
  // Provide a noop transact spy so tests can assert it was called
  ydoc.transact = vi.fn((fn: unknown) => fn());

  // getArray called for nodes then edges in many tests
  ydoc.getArray = vi.fn()
    .mockReturnValueOnce(nodesArray)
    .mockReturnValueOnce(edgesArray);

  ydoc.getMap = vi.fn(() => map);

  return { ydoc, nodesArray, edgesArray, map };
}
