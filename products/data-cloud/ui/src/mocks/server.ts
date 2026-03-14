/**
 * MSW Node Server Setup
 *
 * <p>Initializes Mock Service Worker for Vitest / Node.js test environment.
 * The test setup file imports this server and wires it to beforeAll / afterAll
 * lifecycle hooks automatically.
 *
 * @doc.type config
 * @doc.purpose MSW Node server for Vitest integration tests
 * @doc.layer frontend
 */

import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/**
 * MSW server instance configured with all application handlers.
 *
 * Individual tests may call {@code server.use(...overrideHandlers)} to inject
 * scenario-specific responses. Those overrides are cleared automatically after
 * each test by the global setup in {@code src/__tests__/setup.ts}.
 */
export const server = setupServer(...handlers);
