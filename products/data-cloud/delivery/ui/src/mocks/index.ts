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

export { startMswBrowser, worker } from "./browser";
export { handlers, resetMockData } from "./handlers";
export { server } from "./server";
