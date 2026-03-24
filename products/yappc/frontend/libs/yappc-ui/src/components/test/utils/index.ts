/**
 * Re-export all test utilities for backward compatibility
 * @module test/utils
 */

export {
  renderWithProviders,
  renderWithTheme,
  renderWithToast,
  renderWithMockTheme,
  renderWithMockToast,
  waitForElementToBeRemoved,
  waitForElementToBeVisible,
  getWithin,
  findByText,
  getByText,
  queryByText,
  getByRole,
  getByTestId,
} from './render-helpers';

export {
  createMockResizeObserver,
  createMockIntersectionObserver,
  createMockMediaQueryList,
  createMockMutationObserver,
  createMockPerformanceObserver,
  createMockObserver,
} from './mock-observers';

export {
  submitForm,
  typeIntoInput,
  clickElement,
  wait,
  createMockEvent,
  createMockFormEvent,
  createMockKeyboardEvent,
  createMockMouseEvent,
  createMockTouchEvent,
} from './mock-events';

export {
  createMockFile,
  createMockFileList,
  createMockWebWorker,
  createMockServiceWorker,
  createMockWebSocket,
  createMockNotification,
  createMockGeolocation,
  createMockSpeechSynthesis,
  createMockSpeechRecognition,
  createMockAudioContext,
} from './mock-storage';

export {
  createMockResponse,
  createMockFetch,
  createMockRejectedFetch,
  createMockStorage,
  createMockConsole,
  createMockFn,
  createMockPromise,
  createMockRejectedPromise,
} from './mock-api';

export {
  createMockTimer,
  createMockRAF,
  createMockCancelRAF,
  createMockPerformanceNow,
} from './mock-timers';
