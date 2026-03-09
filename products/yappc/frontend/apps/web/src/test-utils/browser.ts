import { setupWorker } from 'msw/browser';

import { handlers } from './msw-handlers';

// This configures a Service Worker with the given request handlers.
export const worker = setupWorker(...handlers);

// Expose the worker instance on the window object for development purposes
if (import.meta.env.DEV && typeof window !== 'undefined') {
  // @ts-ignore
  window.msw = { worker };
}

// Start the worker when this module is imported
// This is useful for development with tools like Storybook
if (import.meta.env.DEV) {
  worker.start({
    onUnhandledRequest: 'bypass',
  });
}

export * from './msw-handlers';

export default worker;
