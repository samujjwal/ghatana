/**
 * MSW mocks barrel.
 *
 * Re-exports the server (for Node/Vitest), browser worker,
 * handlers array, and the resetMockData helper.
 *
 * @doc.type module
 * @doc.purpose MSW mocks barrel export
 * @doc.layer frontend
 */

export { server } from './server';
export { worker, startMswBrowser } from './browser';
export { handlers, resetMockData } from './handlers';
